/*
Copyright 2013,2016,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.EquationSet.Conversion;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.execenvs.ExecutionEnv;
import gov.sandia.n2a.language.AccessElement;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.BuildMatrix;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Split;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.Gaussian;
import gov.sandia.n2a.language.function.Norm;
import gov.sandia.n2a.language.function.ReadMatrix;
import gov.sandia.n2a.language.function.Uniform;
import gov.sandia.n2a.language.operator.Power;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.plugins.extpoints.Backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class JobC
{
    static boolean rebuildRuntime = true;  // always rebuild runtime once per session
    public HashMap<Operator,String> matrixNames;  // TODO: this is not thread-safe. Should have a C-run-specific object to carry this during generation.

    public void execute (MNode job) throws Exception
    {
        String jobDir = new File (job.get ()).getParent ();
        Files.createFile (Paths.get (jobDir, "started"));

        // Ensure runtime is built
        // TODO: Generalize file handling for remote jobs. This present code will only work with a local directory.
        ExecutionEnv env = ExecutionEnv.factory (job.getOrDefault ("$metadata", "host", "localhost"));
        String runtimeDir = env.getNamedValue ("c.directory");
        if (runtimeDir.length () == 0)
        {
            throw new Exception ("Couldn't determine runtime directory");
        }
        String runtime;
        try
        {
            if (rebuildRuntime)
            {
                rebuildRuntime = false;
                throw new Exception ();
            }
            runtime = env.buildRuntime (env.file (runtimeDir, "runtime.cc"));
        }
        catch (Exception e)
        {
            // Presumably we failed to build runtime because files aren't present

            env.createDir (runtimeDir);
            String[] sourceFiles = {"runtime.cc", "runtime.h", "Neighbor.cc"};
            for (String s : sourceFiles)
            {
                BufferedReader reader = new BufferedReader (new InputStreamReader (JobC.class.getResource ("runtime/" + s).openStream ()));
                String contents = reader.lines ().collect (Collectors.joining ("\n"));
                env.setFileContents (env.file (runtimeDir, s), contents);
            }

            String flDir = env.file (runtimeDir, "fl");
            env.createDir (flDir);
            sourceFiles = new String [] {"archive.h", "blasproto.h", "math.h", "matrix.h", "Matrix.tcc", "MatrixFixed.tcc", "neighbor.h", "pointer.h", "string.h", "Vector.tcc"};
            for (String s : sourceFiles)
            {
                BufferedReader reader = new BufferedReader (new InputStreamReader (JobC.class.getResource ("runtime/fl/" + s).openStream ()));
                String contents = reader.lines ().collect (Collectors.joining ("\n"));
                env.setFileContents (env.file (flDir, s), contents);
            }

            runtime = env.buildRuntime (env.file (runtimeDir, "runtime.cc"));
        }

        // Create model-specific executable
        String sourceFileName = env.file (jobDir, "model.cc");

        EquationSet e = new EquationSet (job);
        e.resolveConnectionBindings ();
        e.flatten ();
        e.addSpecials ();  // $dt, $index, $init, $live, $n, $t, $type
        e.fillIntegratedVariables ();
        e.findIntegrated ();
        e.resolveLHS ();
        e.resolveRHS ();
        e.findConstants ();
        e.removeUnused ();  // especially get rid of unneeded $variables created by addSpecials()
        e.collectSplits ();
        findPathToContainer (e);
        e.findAccountableConnections ();
        e.findTemporary ();  // for connections, makes $p and $project "temporary" under some circumstances. TODO: make sure this doesn't violate evaluation order rules
        e.determineOrder ();
        e.findDerivative ();
        e.addAttribute ("global",       0, false, new String[] {"$max", "$min", "$k", "$n", "$radius"});
        e.addAttribute ("preexistent", -1, false, new String[] {"$index"});
        e.addAttribute ("preexistent",  0, true,  new String[] {"$t'", "$t"});
        e.addAttribute ("simulator",    0, true,  new String[] {"$t'", "$t"});
        e.makeConstantDtInitOnly ();
        e.findInitOnly ();  // propagate initOnly through ASTs
        e.findDeath ();
        e.setAttributesLive ();
        e.forceTemporaryStorageForSpecials ();
        findLiveReferences (e);
        e.determineTypes ();

        e.determineDuration ();
        String duration = e.getNamedValue ("duration");
        if (! duration.isEmpty ()) job.set ("$metadata", "duration", duration);

        e.setInit (0);
        System.out.println (e.dump (false));

        matrixNames = new HashMap<Operator,String> ();

        StringBuilder s = new StringBuilder ();

        s.append ("#include \"" + env.file (runtimeDir, "runtime.h") + "\"\n");
        s.append ("\n");
        s.append ("#include <iostream>\n");
        s.append ("#include <vector>\n");
        s.append ("#include <cmath>\n");
        s.append ("#include <string>\n");
        s.append ("\n");
        s.append ("using namespace std;\n");
        s.append ("using namespace fl;\n");
        s.append ("\n");
        s.append ("class Wrapper;\n");
        generateClassList (e, s);
        s.append ("\n");
        generateDeclarations (e, s);
        s.append ("class Wrapper : public Part\n");
        s.append ("{\n");
        s.append ("public:\n");
        s.append ("  virtual void init (Simulator & simulator);\n");
        s.append ("  virtual void integrate (Simulator & simulator);\n");
        s.append ("  virtual void update (Simulator & simulator);\n");
        s.append ("  virtual bool finalize (Simulator & simulator);\n");
        s.append ("  virtual void updateDerivative (Simulator & simulator);\n");
        s.append ("  virtual void finalizeDerivative ();\n");
        s.append ("  virtual void snapshot ();\n");
        s.append ("  virtual void restore ();\n");
        s.append ("  virtual void pushDerivative ();\n");
        s.append ("  virtual void multiplyAddToStack (float scalar);\n");
        s.append ("  virtual void multiply (float scalar);\n");
        s.append ("  virtual void addToMembers ();\n");
        s.append ("  " + prefix (e) + "_Population " + mangle (e.name) + ";\n");
        s.append ("};\n");
        s.append ("\n");
        s.append ("void Wrapper::init (Simulator & simulator)\n");
        s.append ("{\n");
        s.append ("  " + mangle (e.name) + ".init (simulator);\n");
        s.append ("  writeTrace ();\n");  // After the above init() call will create all initial parts. There may be some calls to trace() in the init() functions, so we dump time step 0 now.
        s.append ("}\n");
        s.append ("\n");
        s.append ("void Wrapper::integrate (Simulator & simulator)\n");
        s.append ("{\n");
        s.append ("  " + mangle (e.name) + ".integrate (simulator);\n");
        s.append ("}\n");
        s.append ("\n");
        s.append ("void Wrapper::update (Simulator & simulator)\n");
        s.append ("{\n");
        s.append ("  " + mangle (e.name) + ".update (simulator);\n");
        s.append ("}\n");
        s.append ("\n");
        s.append ("bool Wrapper::finalize (Simulator & simulator)\n");
        s.append ("{\n");
        s.append ("  " + mangle (e.name) + ".finalize (simulator);\n");
        s.append ("  writeTrace ();\n");
        s.append ("  return " + mangle (e.name) + ".n;\n");  // The simulation stops when the last model instance dies.
        s.append ("}\n");
        s.append ("\n");
        s.append ("void Wrapper::updateDerivative (Simulator & simulator)\n");
        s.append ("{\n");
        s.append ("  " + mangle (e.name) + ".updateDerivative (simulator);\n");
        s.append ("}\n");
        s.append ("\n");
        s.append ("void Wrapper::finalizeDerivative ()\n");
        s.append ("{\n");
        s.append ("  " + mangle (e.name) + ".finalizeDerivative ();\n");
        s.append ("}\n");
        s.append ("\n");
        s.append ("void Wrapper::snapshot ()\n");
        s.append ("{\n");
        s.append ("  " + mangle (e.name) + ".snapshot ();\n");
        s.append ("}\n");
        s.append ("\n");
        s.append ("void Wrapper::restore ()\n");
        s.append ("{\n");
        s.append ("  " + mangle (e.name) + ".restore ();\n");
        s.append ("}\n");
        s.append ("\n");
        s.append ("void Wrapper::pushDerivative ()\n");
        s.append ("{\n");
        s.append ("  " + mangle (e.name) + ".pushDerivative ();\n");
        s.append ("}\n");
        s.append ("\n");
        s.append ("void Wrapper::multiplyAddToStack (float scalar)\n");
        s.append ("{\n");
        s.append ("  " + mangle (e.name) + ".multiplyAddToStack (scalar);\n");
        s.append ("}\n");
        s.append ("\n");
        s.append ("void Wrapper::multiply (float scalar)\n");
        s.append ("{\n");
        s.append ("  " + mangle (e.name) + ".multiply (scalar);\n");
        s.append ("}\n");
        s.append ("\n");
        s.append ("void Wrapper::addToMembers ()\n");
        s.append ("{\n");
        s.append ("  " + mangle (e.name) + ".addToMembers ();\n");
        s.append ("}\n");
        s.append ("\n");
        generateDefinitions (e, s);
        s.append ("\n");

        // Main
        s.append ("int main (int argc, char * argv[])\n");
        s.append ("{\n");
        String integrator = e.getNamedValue ("c.integrator", "Euler");
        s.append ("  try\n");
        s.append ("  {\n");
        s.append ("    " + integrator + " simulator;\n");
        s.append ("    Wrapper wrapper;\n");
        s.append ("    wrapper." + mangle (e.name) + ".container = &wrapper;\n");
        s.append ("    simulator.enqueue (&wrapper);\n");
        s.append ("    wrapper.init (simulator);\n");
        s.append ("    simulator.run ();\n");
        s.append ("    writeHeaders ();\n");
        s.append ("  }\n");
        s.append ("  catch (const char * message)\n");
        s.append ("  {\n");
        s.append ("    cerr << \"Exception: \" << message << endl;\n");
        s.append ("    return 1;\n");
        s.append ("  }\n");
        s.append ("  return 0;\n");
        s.append ("}\n");

        env.setFileContents (sourceFileName, s.toString ());
        String command = env.quotePath (env.build (sourceFileName, runtime));  // TODO: route build errors to Backend.err

        PrintStream ps = Backend.err.get ();
        if (ps != System.err)
        {
            ps.close ();
            Backend.err.remove ();
        }
        long pid = env.submitJob (job, command);
        job.set ("$metadata", "pid", pid);
    }

    public void generateClassList (EquationSet s, StringBuilder result)
    {
        result.append ("class " + prefix (s) + "_Population;\n");
        result.append ("class " + prefix (s) + ";\n");
        for (EquationSet p : s.parts) generateClassList (p, result);
    }

    /**
        Declares all classes, along with their member variables and functions.
        Declarations are recursively composed, so that everything below this level
        is contained in the current class.
        This is a very long monolithic function. Use the comments as guideposts on
        the current thing being generated. First we analyze all variables in the
        current equations set, then generate two classes: one for the instances ("local")
        and one for the population as a whole ("global"). Within each class we
        declare buffer classes for integration and derivation, then member variables,
        and finally member functions as appropriate based on the analysis done at the
        beginning.
        Note that the analysis is cached in s.backendData for later use when generating function
        definitions.
    **/
    public void generateDeclarations (EquationSet s, StringBuilder result)
    {
        // Analyze variables
        if (! (s.backendData instanceof BackendData)) s.backendData = new BackendData ();
        BackendData bed = (BackendData) s.backendData;

        System.out.println (s.name);
        for (Variable v : s.ordered)  // we want the sub-lists to be ordered correctly
        {
            System.out.println ("  " + v.nameString () + " " + v.attributeString ());
            generateStatic (v, result);
            if (v.name.equals ("$type")) bed.type = v;
            if (v.hasAttribute ("global"))
            {
                if (! v.hasAny (new String[] {"constant", "accessor"}))
                {
                    boolean initOnly               = v.hasAttribute ("initOnly");
                    boolean derivativeOrDependency = v.hasAttribute ("derivativeOrDependency");
                    if (! initOnly) bed.globalUpdate.add (v);
                    if (derivativeOrDependency) bed.globalDerivativeUpdate.add (v);
                    if (! v.hasAttribute ("reference"))
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        boolean unusedTemporary = temporary  &&  ! v.hasUsers ();  // here only to make the following line of code more clear
                        if (! unusedTemporary) bed.globalInit.add (v);
                        if (! temporary  &&  ! v.hasAttribute ("dummy"))
                        {
                            if (! v.hasAttribute ("preexistent"))
                            {
                                bed.globalMembers.add (v);

                                // If v is merely a derivative, not used by anything but its own integral, then no reason to preserve it.
                                // check if v.usedBy contains any variable that is not v's integral
                                if (derivativeOrDependency  &&  v.derivative == null  &&  v.usedBy != null)
                                {
                                    for (Object o : v.usedBy)
                                    {
                                        if (o instanceof Variable  &&  ((Variable) o).derivative != v)
                                        {
                                            bed.globalDerivativePreserve.add (v);
                                            break;
                                        }
                                    }
                                }
                            }

                            boolean external = false;
                            if (! initOnly)
                            {
                                if (v.name.equals ("$t"))
                                {
                                    if (v.order > 1) bed.globalDerivative.add (v);
                                }
                                else
                                {
                                    if (v.order > 0) bed.globalDerivative.add (v);
                                }

                                if (v.hasAttribute ("externalWrite"))
                                {
                                    external = true;
                                    bed.globalBufferedExternalWrite.add (v);
                                    if (derivativeOrDependency) bed.globalBufferedExternalWriteDerivative.add (v);
                                }
                                if (external  ||  (v.hasAttribute ("externalRead")  &&  v.equations.size () > 0))
                                {
                                    external = true;
                                    bed.globalBufferedExternal.add (v);
                                    if (derivativeOrDependency) bed.globalBufferedExternalDerivative.add (v);
                                }
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                bed.globalBuffered.add (v);
                                if (! external)
                                {
                                    bed.globalBufferedInternal.add (v);
                                    if (! initOnly)
                                    {
                                        bed.globalBufferedInternalUpdate.add (v);
                                        if (derivativeOrDependency) bed.globalBufferedInternalDerivative.add (v);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else  // local
            {
                if (! v.hasAny (new String[] {"constant", "accessor"}))
                {
                    boolean initOnly               = v.hasAttribute ("initOnly");
                    boolean derivativeOrDependency = v.hasAttribute ("derivativeOrDependency");
                    if (! initOnly) bed.localUpdate.add (v);  // TODO: ensure that initOnly temporary variables are handled correctly. IE: their value doesn't change after init, but they still need to be calculated on the fly.
                    if (derivativeOrDependency) bed.localDerivativeUpdate.add (v);
                    if (v.hasAttribute ("reference"))
                    {
                        if (v.reference.variable.container.canDie ()) bed.localReference.add (v.reference);
                    }
                    else
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        boolean unusedTemporary = temporary  &&  ! v.hasUsers ();
                        if (! unusedTemporary  &&  ! v.name.equals ("$index")) bed.localInit.add (v);
                        if (! temporary  &&  ! v.hasAttribute ("dummy"))
                        {
                            if (! v.hasAttribute ("preexistent"))
                            {
                                bed.localMembers.add (v);

                                if (derivativeOrDependency  &&  v.derivative == null  &&  v.usedBy != null)
                                {
                                    for (Object o : v.usedBy)
                                    {
                                        if (o instanceof Variable  &&  ((Variable) o).derivative != v)
                                        {
                                            bed.localDerivativePreserve.add (v);
                                            break;
                                        }
                                    }
                                }
                            }

                            boolean external = false;
                            if (! initOnly)
                            {
                                if (v.name.equals ("$t"))
                                {
                                    if (v.order > 1) bed.localDerivative.add (v);
                                }
                                else  // any other variable
                                {
                                    if (v.order > 0) bed.localDerivative.add (v);
                                }

                                if (v.hasAttribute ("externalWrite"))
                                {
                                    external = true;
                                    bed.localBufferedExternalWrite.add (v);
                                    if (derivativeOrDependency) bed.localBufferedExternalWriteDerivative.add (v);
                                }
                                if (external  ||  (v.hasAttribute ("externalRead")  &&  v.equations.size () > 0))
                                {
                                    external = true;
                                    bed.localBufferedExternal.add (v);
                                    if (derivativeOrDependency) bed.localBufferedExternalDerivative.add (v);
                                }
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                bed.localBuffered.add (v);
                                if (! external)
                                {
                                    bed.localBufferedInternal.add (v);
                                    if (! initOnly)
                                    {
                                        bed.localBufferedInternalUpdate.add (v);
                                        if (derivativeOrDependency) bed.localBufferedInternalDerivative.add (v);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (Variable v : s.variables)  // we need these to be in order by differential level, not by dependency
        {
            if (v.derivative != null  &&  ! v.hasAny (new String[] {"constant", "initOnly"}))
            {
                if (v.hasAttribute ("global")) bed.globalIntegrated.add (v);
                else                           bed. localIntegrated.add (v);
            }
        }

        if (s.connectionBindings != null)
        {
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                String alias = n.getKey ();
                Variable       v = s.find (new Variable (alias + ".$max"));
                if (v == null) v = s.find (new Variable (alias + ".$min"));
                if (v != null) bed.accountableEndpoints.add (alias);
            }
        }

        bed.refcount = s.referenced  &&  s.canDie ();

        // Sub-parts
        for (EquationSet p : s.parts) generateDeclarations (p, result);

        // -------------------------------------------------------------------

        // Population class header
        result.append ("class " + prefix (s) + "_Population : public ");
        if (s.connectionBindings == null) result.append ("PopulationCompartment\n");
        else                              result.append ("PopulationConnection\n");
        result.append ("{\n");
        result.append ("public:\n");

        // Population buffers
        if (bed.globalDerivative.size () > 0)
        {
            result.append ("  class Derivative\n");
            result.append ("  {\n");
            result.append ("  public:\n");
            for (Variable v : bed.globalDerivative)
            {
                result.append ("    " + type (v) + " " + mangle (v) + ";\n");
            }
            result.append ("    Derivative * next;\n");
            result.append ("  };\n");
            result.append ("\n");
        }
        if (bed.globalIntegrated.size () > 0  ||  bed.globalDerivativePreserve.size () > 0  ||  bed.globalBufferedExternalWriteDerivative.size () > 0)
        {
            result.append ("  class Preserve\n");
            result.append ("  {\n");
            result.append ("  public:\n");
            for (Variable v : bed.globalIntegrated)
            {
                result.append ("    " + type (v) + " " + mangle (v) + ";\n");
            }
            for (Variable v : bed.globalDerivativePreserve)
            {
                result.append ("    " + type (v) + " " + mangle (v) + ";\n");
            }
            for (Variable v : bed.globalBufferedExternalWriteDerivative)
            {
                result.append ("    " + type (v) + " " + mangle ("next_", v) + ";\n");
            }
            result.append ("  };\n");
            result.append ("\n");
        }

        // Population variables
        if (bed.globalDerivative.size () > 0)
        {
            result.append ("  Derivative * stackDerivative;\n");
        }
        if (bed.globalIntegrated.size () > 0  ||  bed.globalDerivativePreserve.size () > 0  ||  bed.globalBufferedExternalWriteDerivative.size () > 0)
        {
            result.append ("  Preserve * preserve;\n");
        }
        result.append ("  " + prefix (s.container) + " * container;\n");
        for (Variable v : bed.globalMembers)
        {
            result.append ("  " + type (v) + " " + mangle (v) + ";\n");
        }
        for (Variable v : bed.globalBufferedExternal)
        {
            result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
        }
        result.append ("\n");

        // Population functions
        if (bed.globalDerivative.size () > 0  ||  bed.globalIntegrated.size () > 0  ||  bed.globalDerivativePreserve.size () > 0  ||  bed.globalBufferedExternalWriteDerivative.size () > 0)
        {
            result.append ("  " + prefix (s) + "_Population ();\n");
            result.append ("  virtual ~" + prefix (s) + "_Population ();\n");
        }
        result.append ("  virtual Part * create ();\n");
        result.append ("  virtual void init (Simulator & simulator);\n");
        if (bed.globalIntegrated.size () > 0)
        {
            result.append ("  virtual void integrate (Simulator & simulator);\n");
        }
        if (bed.globalUpdate.size () > 0)
        {
            result.append ("  virtual void update (Simulator & simulator);\n");
        }
        bed.n = s.find (new Variable ("$n", 0));
        if (bed.n != null  &&  ! bed.globalMembers.contains (bed.n)) bed.n = null;  // force bed.n to null if $n is not a stored member; used as an indicator
        if (bed.globalBufferedExternal.size () > 0  ||  bed.n != null)
        {
            result.append ("  virtual bool finalize (Simulator & simulator);\n");
        }
        bed.canGrowOrDie =  s.lethalP  ||  s.lethalType  ||  s.canGrow ();
        if (bed.n != null  &&  bed.n.derivative == null  &&  bed.canGrowOrDie)
        {
            result.append ("  virtual void resize (Simulator & simulator, int n);\n");
        }
        if (bed.globalDerivativeUpdate.size () > 0)
        {
            result.append ("  virtual void updateDerivative (Simulator & simulator);\n");
        }
        if (bed.globalBufferedExternalDerivative.size () > 0)
        {
            result.append ("  virtual void finalizeDerivative ();\n");
        }
        if (bed.globalIntegrated.size () > 0  ||  bed.globalDerivativePreserve.size () > 0  ||  bed.globalBufferedExternalWriteDerivative.size () > 0)
        {
            result.append ("  virtual void snapshot ();\n");
            result.append ("  virtual void restore ();\n");
        }
        if (bed.globalDerivative.size () > 0)
        {
            result.append ("  virtual void pushDerivative ();\n");
            result.append ("  virtual void multiplyAddToStack (float scalar);\n");
            result.append ("  virtual void multiply (float scalar);\n");
            result.append ("  virtual void addToMembers ();\n");
        }
        if (s.connectionBindings != null)
        {
            result.append ("  virtual Population * getTarget (int i);\n");

            for (Entry<String, EquationSet> cb : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (cb.getKey () + ".$k"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    bed.needK = true;
                    break;
                }
            }
            if (bed.needK)
            {
                result.append ("  virtual int getK (int i);\n");
            }

            for (Entry<String, EquationSet> cb : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (cb.getKey () + ".$max"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    bed.needMax = true;
                    break;
                }
            }
            if (bed.needMax)
            {
                result.append ("  virtual int getMax (int i);\n");
            }

            for (Entry<String, EquationSet> cb : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (cb.getKey () + ".$min"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    bed.needMin = true;
                    break;
                }
            }
            if (bed.needMin)
            {
                result.append ("  virtual int getMin (int i);\n");
            }

            for (Entry<String, EquationSet> cb : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (cb.getKey () + ".$radius"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    bed.needRadius = true;
                    break;
                }
            }
            if (bed.needRadius)
            {
                result.append ("  virtual int getRadius (int i);\n");
            }
        }

        // Population class trailer
        result.append ("};\n");
        result.append ("\n");

        // -------------------------------------------------------------------

        // Unit class
        result.append ("class " + prefix (s) + " : public ");
        if (s.connectionBindings == null) result.append ("Compartment\n");
        else                              result.append ("Connection\n");
        result.append ("{\n");
        result.append ("public:\n");

        // Unit buffers
        if (bed.localDerivative.size () > 0)
        {
            result.append ("  class Derivative\n");
            result.append ("  {\n");
            result.append ("  public:\n");
            for (Variable v : bed.localDerivative)
            {
                result.append ("    " + type (v) + " " + mangle (v) + ";\n");
            }
            result.append ("    Derivative * next;\n");
            result.append ("  };\n");
            result.append ("\n");
        }
        if (bed.localIntegrated.size () > 0  ||  bed.localDerivativePreserve.size () > 0  ||  bed.localBufferedExternalWriteDerivative.size () > 0)
        {
            result.append ("  class Preserve\n");
            result.append ("  {\n");
            result.append ("  public:\n");
            for (Variable v : bed.localIntegrated)
            {
                result.append ("    " + type (v) + " " + mangle (v) + ";\n");
            }
            for (Variable v : bed.localDerivativePreserve)
            {
                result.append ("    " + type (v) + " " + mangle (v) + ";\n");
            }
            for (Variable v : bed.localBufferedExternalWriteDerivative)
            {
                result.append ("    " + type (v) + " " + mangle ("next_", v) + ";\n");
            }
            result.append ("  };\n");
            result.append ("\n");
        }

        // Unit variables
        if (bed.localDerivative.size () > 0)
        {
            result.append ("  Derivative * stackDerivative;\n");
        }
        if (bed.localIntegrated.size () > 0  ||  bed.localDerivativePreserve.size () > 0  ||  bed.localBufferedExternalWriteDerivative.size () > 0)
        {
            result.append ("  Preserve * preserve;\n");
        }
        if (bed.pathToContainer == null)
        {
            result.append ("  " + prefix (s.container) + " * container;\n");
        }
        if (s.connectionBindings != null)
        {
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                // we should be able to assume that s.container is non-null; ie: a connection should always operate in some larger container
                result.append ("  " + prefix (e.getValue ()) + " * " + mangle (e.getKey ()) + ";\n");
            }
        }
        if (s.accountableConnections != null)
        {
            for (EquationSet.AccountableConnection ac : s.accountableConnections)
            {
                result.append ("  int " + prefix (ac.connection) + "_" + mangle (ac.alias) + "_count;\n");
            }
        }
        if (bed.refcount)
        {
            result.append ("  int refcount;\n");
        }
        for (Variable v : bed.localMembers)
        {
            result.append ("  " + type (v) + " " + mangle (v) + ";\n");
        }
        for (Variable v : bed.localBufferedExternal)
        {
            result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
        }
        result.append ("\n");

        // Unit functions
        if (bed.localDerivative.size () > 0  ||  bed.localIntegrated.size () > 0  ||  bed.localDerivativePreserve.size () > 0  ||  bed.localBufferedExternalWriteDerivative.size () > 0  ||  ! s.parts.isEmpty ()  ||  s.accountableConnections != null  ||  bed.refcount)
        {
            result.append ("  " + prefix (s) + " ();\n");
        }
        if (bed.localDerivative.size () > 0  ||  bed.localIntegrated.size () > 0  ||  bed.localDerivativePreserve.size () > 0  ||  bed.localBufferedExternalWriteDerivative.size () > 0)
        {
            result.append ("  virtual ~" + prefix (s) + " ();\n");
        }
        if (bed.localMembers.size () > 0  ||  bed.localBufferedExternal.size () > 0)
        {
            result.append ("  virtual void clear ();\n");
        }
        if (s.canDie ())
        {
            result.append ("  virtual void die ();\n");
        }
        if (bed.localReference.size () > 0)
        {
            result.append ("  virtual void enterSimulation ();\n");
        }
        result.append ("  virtual void leaveSimulation ();\n");
        if (bed.refcount)
        {
            result.append ("  virtual bool isFree ();\n");
        }
        if (s.connectionBindings == null  ||  bed.localInit.size () > 0  ||  s.parts.size () > 0  ||  bed.accountableEndpoints.size () > 0)
        {
            result.append ("  virtual void init (Simulator & simulator);\n");
        }
        if (bed.localIntegrated.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("  virtual void integrate (Simulator & simulator);\n");
        }
        if (bed.localUpdate.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("  virtual void update (Simulator & simulator);\n");
        }
        if (bed.localBufferedExternal.size () > 0  ||  bed.type != null  ||  s.canDie ()  ||  s.parts.size () > 0)
        {
            result.append ("  virtual bool finalize (Simulator & simulator);\n");
        }
        if (bed.localDerivativeUpdate.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("  virtual void updateDerivative (Simulator & simulator);\n");
        }
        if (bed.localBufferedExternalDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("  virtual void finalizeDerivative ();\n");
        }
        if (bed.localIntegrated.size () > 0  ||  bed.localDerivativePreserve.size () > 0  ||  bed.localBufferedExternalWriteDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("  virtual void snapshot ();\n");
            result.append ("  virtual void restore ();\n");
        }
        if (bed.localDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("  virtual void pushDerivative ();\n");
            result.append ("  virtual void multiplyAddToStack (float scalar);\n");
            result.append ("  virtual void multiply (float scalar);\n");
            result.append ("  virtual void addToMembers ();\n");
        }
        Variable live = s.find (new Variable ("$live"));
        if (live != null  &&  ! live.hasAttribute ("constant"))
        {
            result.append ("  virtual float getLive ();\n");
        }
        if (s.connectionBindings == null)
        {
            Variable xyz = s.find (new Variable ("$xyz", 0));  // Connections can also have $xyz, but only compartments need to provide an accessor.
            if (xyz != null)
            {
                result.append ("  virtual void getXYZ (Vector3 & xyz);\n");
            }
        }
        else
        {
            Variable p = s.find (new Variable ("$p", 0));
            if (p != null)
            {
                result.append ("  virtual float getP (Simulator & simulator);\n");
            }

            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                if (s.find (new Variable (e.getKey () + ".$projectFrom")) != null) bed.hasProjectFrom = true;
                if (s.find (new Variable (e.getKey () + ".$projectTo"  )) != null) bed.hasProjectTo   = true;
                // TODO: if only one of a pair of projections is present, create the other using point sampling
            }
            if (bed.hasProjectFrom  ||  bed.hasProjectTo)
            {
                result.append ("  virtual void project (int i, int j, Vector3 & xyz);\n");
            }

            result.append ("  virtual void setPart (int i, Part * part);\n");
            result.append ("  virtual Part * getPart (int i);\n");
        }
        if (bed.accountableEndpoints.size () > 0)
        {
            result.append ("  virtual int getCount (int i);\n");
        }

        // Conversions
        Set<Conversion> conversions = s.getConversions ();
        for (Conversion pair : conversions)
        {
            EquationSet source = pair.from;
            EquationSet dest   = pair.to;
            result.append ("  void " + mangle (source.name) + "_2_" + mangle (dest.name) + " (" + mangle (source.name) + " * from, Simulator & simulator, int " + mangle ("$type") + ");\n");
        }

        // Sub-parts
        for (EquationSet p : s.parts) result.append ("  " + prefix (p) + "_Population " + mangle (p.name) + ";\n");

        // Unit class trailer
        result.append ("};\n");
        result.append ("\n");
    }

    public void generateStatic (Variable v, final StringBuilder result)
    {
        Visitor visitor = new Visitor ()
        {
            public boolean visit (Operator op)
            {
                if (op instanceof Constant)
                {
                    Type m = ((Constant) op).value;
                    if (m instanceof Matrix)
                    {
                        Matrix A = (Matrix) m;
                        int rows = A.rows ();
                        int cols = A.columns ();
                        String matrixName = "Matrix" + matrixNames.size ();
                        matrixNames.put (op, matrixName);
                        if (rows == 3  &&  cols == 1) result.append ("Vector3 " + matrixName + " = Matrix<float>");
                        else                          result.append ("Matrix<float> " + matrixName);
                        result.append (" (\"" + A + "\");\n");
                    }
                    return false;  // Don't try to descend tree from here
                }
                if (op instanceof Function)
                {
                    // Handle all functions that need static handles
                    Function f = (Function) op;
                    if (f instanceof ReadMatrix)
                    {
                        if (f.operands.length > 0)
                        {
                            Operator c = f.operands[0];
                            if (c instanceof Constant)
                            {
                                Type o = ((Constant) c).value;
                                if (o instanceof Text)
                                {
                                    String matrixName = "Matrix" + matrixNames.size ();
                                    matrixNames.put (c, matrixName);
                                    result.append ("Matrix<float> * " + matrixName + " = matrixHelper (\"" + o + "\");\n");
                                }
                            }
                        }
                        return false;
                    }
                }
                return true;
            }
        };

        for (EquationEntry e : v.equations)
        {
            if (e.expression  != null) e.expression .visit (visitor);
            if (e.condition != null) e.condition.visit (visitor);
        }
    }

    public void generateDefinitions (EquationSet s, StringBuilder result) throws Exception
    {
        CRenderer context = new CRenderer (result, s);

        // Access backend data
        BackendData bed = (BackendData) s.backendData;

        context.global = true;
        String ns = prefix (s) + "_Population::";  // namespace for all functions associated with part s

        if (bed.globalDerivative.size () > 0  ||  bed.globalIntegrated.size () > 0  ||  bed.globalDerivativePreserve.size () > 0  ||  bed.globalBufferedExternalWriteDerivative.size () > 0)
        {
            // Population ctor
            result.append (ns + prefix (s) + "_Population ()\n");
            result.append ("{\n");
            if (bed.globalDerivative.size () > 0)
            {
                result.append ("  stackDerivative = 0;\n");
            }
            if (bed.globalIntegrated.size () > 0  ||  bed.globalDerivativePreserve.size () > 0  ||  bed.globalBufferedExternalWriteDerivative.size () > 0)
            {
                result.append ("  preserve = 0;\n");
            }
            result.append ("}\n");
            result.append ("\n");

            // Population dtor
            result.append (ns + "~" + prefix (s) + "_Population ()\n");
            result.append ("{\n");
            if (bed.globalDerivative.size () > 0)
            {
                result.append ("  while (stackDerivative)\n");
                result.append ("  {\n");
                result.append ("    Derivative * temp = stackDerivative;\n");
                result.append ("    stackDerivative = stackDerivative->next;\n");
                result.append ("    delete temp;\n");
                result.append ("  }\n");
            }
            if (bed.globalIntegrated.size () > 0  ||  bed.globalDerivativePreserve.size () > 0  ||  bed.globalBufferedExternalWriteDerivative.size () > 0)
            {
                result.append ("  if (preserve) delete preserve;\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Population create
        result.append ("Part * " + ns + "create ()\n");
        result.append ("{\n");
        result.append ("  " + prefix (s) + " * p = new " + prefix (s) + ";\n");
        if (bed.pathToContainer == null) result.append ("  p->container = container;\n");
        result.append ("  return p;\n");
        result.append ("}\n");
        result.append ("\n");

        // Population getTarget
        if (s.connectionBindings != null)
        {
            result.append ("Population * " + ns + "getTarget (int i)\n");
            result.append ("{\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            int i = 0;
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                // TODO: Need a function to permute all descending paths to a class of populations.
                // In the simplest form, it is a peer in our current container, so no iteration at all.
                result.append ("    case " + i++ + ": return & container->" + mangle (e.getValue ().name) + ";\n");
            }
            result.append ("    default: return 0;\n");
            result.append ("  }\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Population init
        result.append ("void " + ns + "init (Simulator & simulator)\n");
        result.append ("{\n");
        s.setInit (1);
        //   Zero out members
        for (Variable v : bed.globalMembers)
        {
            result.append ("  " + mangle (v) + zero (v) + ";\n");
        }
        for (Variable v : bed.globalBufferedExternal)
        {
            result.append ("  " + mangle ("next_", v) + zero (v) + ";\n");
        }
        //   declare buffer variables
        for (Variable v : bed.globalBufferedInternal)
        {
            result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
        }
        //   no separate $ and non-$ phases, because only $variables work at the population level
        for (Variable v : bed.globalInit)
        {
            multiconditional (v, context, "  ");
        }
        //   finalize
        for (Variable v : bed.globalBuffered)
        {
            result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
        }
        //   clear variables that may be written externally before first finalize()
        for (Variable v : bed.globalBufferedExternalWrite)
        {
            result.append ("  " + mangle ("next_", v) + zero (v) + ";\n");
        }
        //   create instances
        {
            Variable n = s.find (new Variable ("$n", 0));
            if (n != null)
            {
                if (s.connectionBindings != null) throw new Exception ("$n is not applicable to connections");
                result.append ("  resize (simulator, " + resolve (n.reference, context, false) + ");\n");
            }
        }
        //   make connections
        if (s.connectionBindings != null)
        {
            result.append ("  simulator.connect (this);\n");  // queue to evaluate our connections
        }
        s.setInit (0);
        result.append ("};\n");
        result.append ("\n");

        // Population integrate
        if (bed.globalIntegrated.size () > 0)
        {
            result.append ("void " + ns + "integrate (Simulator & simulator)\n");
            result.append ("{\n");
            result.append ("  if (preserve)\n");
            result.append ("  {\n");
            for (Variable v : bed.globalIntegrated)
            {
                result.append ("    " + resolve (v.reference, context, false) + " = preserve->" + mangle (v) + " + " + resolve (v.derivative.reference, context, false) + " * simulator.dt;\n");
            }
            result.append ("  }\n");
            result.append ("  else\n");
            result.append ("  {\n");
            for (Variable v : bed.globalIntegrated)
            {
                result.append ("    " + resolve (v.reference, context, false) + " += " + resolve (v.derivative.reference, context, false) + " * simulator.dt;\n");
            }
            result.append ("  }\n");
            result.append ("};\n");
            result.append ("\n");
        }

        // Population update
        if (bed.globalUpdate.size () > 0)
        {
            result.append ("void " + ns + "update (Simulator & simulator)\n");
            result.append ("{\n");

            for (Variable v : bed.globalBufferedInternalUpdate)
            {
                result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : bed.globalUpdate)
            {
                multiconditional (v, context, "  ");
            }
            for (Variable v : bed.globalBufferedInternalUpdate)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }

            if (s.connectionBindings == null)
            {
                result.append ("  old = live.after;\n");  // TODO: find a better way to keep track of "new" parts. Problem: what if a part is "new" relative to several different connection types, and what if those connections are tested at different times?
            }

            result.append ("};\n");
            result.append ("\n");
        }

        // Population finalize
        if (bed.globalBufferedExternal.size () > 0  ||  bed.n != null)
        {
            result.append ("bool " + ns + "finalize (Simulator & simulator)\n");
            result.append ("{\n");

            if (bed.n != null  &&  bed.n.derivative == null  &&  bed.canGrowOrDie)  // $n shares control with other specials, so must coordinate them
            {
                result.append ("  if (" + mangle ("$n") + " != " + mangle ("next_", "$n") + ") simulator.resize (this, " + mangle ("next_", "$n") + ");\n");
                result.append ("  else simulator.resize (this, -1);\n");  // -1 means to update $n from n. This can only be done after other parts are finalized, as they may impose structural dynamics via $p or $type.
            }

            for (Variable v : bed.globalBufferedExternal)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : bed.globalBufferedExternalWrite)
            {
                result.append ("  " + mangle ("next_", v) + zero (v) + ";\n");
            }

            if (bed.n != null)  
            {
                if (bed.canGrowOrDie)
                {
                    if (bed.n.derivative != null)  // $n' exists
                    {
                        // the rate of change in $n is pre-determined, so it relentlessly overrides any other structural dynamics
                        result.append ("  simulator.resize (this, " + mangle ("$n") + ");\n");
                    }
                }
                else  // $n is the only kind of structural dynamics, so simply do a resize() when needed
                {
                    result.append ("  if (n != (int) " + mangle ("$n") + ") simulator.resize (this, " + mangle ("$n") + ");\n");
                }
            }

            result.append ("  return true;\n");  // Doesn't matter what we return, because the value is always ignored.
            result.append ("};\n");
            result.append ("\n");
        }

        // Population resize()
        if (bed.n != null  &&  bed.n.derivative == null  &&  bed.canGrowOrDie)
        {
            result.append ("virtual void " + ns + "resize (Simulator & simulator, int n)\n");
            result.append ("{\n");
            result.append ("  if (n >= 0) PopulationCompartment::resize (simulator, n);\n");
            result.append ("  else " + mangle ("$n") + " = this.n;\n");
            result.append ("};\n");
            result.append ("\n");
        }

        // Population updateDerivative
        if (bed.globalDerivativeUpdate.size () > 0)
        {
            result.append ("void " + ns + "updateDerivative (Simulator & simulator)\n");
            result.append ("{\n");
            for (Variable v : bed.globalBufferedInternalDerivative)
            {
                result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : bed.globalDerivativeUpdate)
            {
                multiconditional (v, context, "  ");
            }
            for (Variable v : bed.globalBufferedInternalDerivative)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            result.append ("};\n");
            result.append ("\n");
        }

        // Population finalizeDerivative
        if (bed.globalBufferedExternalDerivative.size () > 0)
        {
            result.append ("void " + ns + "finalizeDerivative ()\n");
            result.append ("{\n");
            for (Variable v : bed.globalBufferedExternalDerivative)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : bed.globalBufferedExternalWriteDerivative)
            {
                result.append ("  " + mangle ("next_", v) + zero (v) + ";\n");
            }
            result.append ("};\n");
            result.append ("\n");
        }

        if (bed.globalIntegrated.size () > 0  ||  bed.globalDerivativePreserve.size () > 0  ||  bed.globalBufferedExternalWriteDerivative.size () > 0)
        {
            // Population snapshot
            result.append ("void " + ns + "snapshot ()\n");
            result.append ("{\n");
            result.append ("  preserve = new Preserve;\n");
            for (Variable v : bed.globalIntegrated)
            {
                result.append ("  preserve->" + mangle (v) + " = " + mangle (v) + ";\n");
            }
            for (Variable v : bed.globalDerivativePreserve)
            {
                result.append ("  preserve->" + mangle (v) + " = " + mangle (v) + ";\n");
            }
            for (Variable v : bed.globalBufferedExternalWriteDerivative)
            {
                result.append ("  preserve->" + mangle ("next_", v) + " = " + mangle ("next_", v) + ";\n");
                result.append ("  " + mangle ("next_", v) + zero (v) + ";\n");
            }
            result.append ("};\n");
            result.append ("\n");

            // Population restore
            result.append ("void " + ns + "restore ()\n");
            result.append ("{\n");
            for (Variable v : bed.globalDerivativePreserve)
            {
                result.append ("  " + mangle (v) + " = preserve->" + mangle (v) + ";\n");
            }
            for (Variable v : bed.globalBufferedExternalWriteDerivative)
            {
                result.append ("  " + mangle ("next_", v) + " = preserve->" + mangle ("next_", v) + ";\n");
            }
            result.append ("  delete preserve;\n");
            result.append ("  preserve = 0;\n");
            result.append ("};\n");
            result.append ("\n");
        }

        if (bed.globalDerivative.size () > 0)
        {
            // Population pushDerivative
            result.append ("void " + ns + "pushDerivative ()\n");
            result.append ("{\n");
            result.append ("  Derivative * temp = new Derivative;\n");
            result.append ("  temp->_next = stackDerivative;\n");
            result.append ("  stackDerivative = temp;\n");
            for (Variable v : bed.globalDerivative)
            {
                result.append ("  temp->" + mangle (v) + " = " + mangle (v) + ";\n");
            }
            result.append ("};\n");
            result.append ("\n");

            // Population multiplyAddToStack
            result.append ("void " + ns + "multiplyAddToStack (float scalar)\n");
            result.append ("{\n");
            for (Variable v : bed.globalDerivative)
            {
                result.append ("  stackDerivative->" + mangle (v) + " += " + mangle (v) + " * scalar;\n");
            }
            result.append ("};\n");
            result.append ("\n");

            // Population multiply
            result.append ("void " + ns + "multiply (float scalar)\n");
            result.append ("{\n");
            for (Variable v : bed.globalDerivative)
            {
                result.append ("  " + mangle (v) + " *= scalar;\n");
            }
            result.append ("};\n");
            result.append ("\n");

            // Population addToMembers
            result.append ("void " + ns + "addToMembers ()\n");
            result.append ("{\n");
            for (Variable v : bed.globalDerivative)
            {
                result.append ("  " + mangle (v) + " += stackDerivative->" + mangle (v) + ";\n");
            }
            result.append ("  Derivative * temp = stackDerivative;\n");
            result.append ("  stackDerivative = stackDerivative->next;\n");
            result.append ("  delete temp;\n");
            result.append ("};\n");
            result.append ("\n");
        }

        // Population getK
        if (bed.needK)
        {
            result.append ("int " + ns + "getK (int i)\n");
            result.append ("{\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            int i = 0;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (n.getKey () + ".$k"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    result.append ("    case " + i + ": return ");
                    e.expression.render (context);
                    result.append (";\n");
                }
                i++;
            }
            result.append ("  }\n");
            result.append ("  return 0;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Population getMax
        if (bed.needMax)
        {
            result.append ("int " + ns + "getMax (int i)\n");
            result.append ("{\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            int i = 0;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (n.getKey () + ".$max"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    result.append ("    case " + i + ": return ");
                    e.expression.render (context);
                    result.append (";\n");
                }
                i++;
            }
            result.append ("  }\n");
            result.append ("  return 0;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Population getMin
        if (bed.needMin)
        {
            result.append ("int " + ns + "getMin (int i)\n");
            result.append ("{\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            int i = 0;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (n.getKey () + ".$min"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    result.append ("    case " + i + ": return ");
                    e.expression.render (context);
                    result.append (";\n");
                }
                i++;
            }
            result.append ("  }\n");
            result.append ("  return 0;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Population getRadius
        if (bed.needRadius)
        {
            result.append ("int " + ns + "getRadius (int i)\n");
            result.append ("{\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            int i = 0;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (n.getKey () + ".$radius"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    result.append ("    case " + i + ": return ");
                    e.expression.render (context);
                    result.append (";\n");
                }
                i++;
            }
            result.append ("  }\n");
            result.append ("  return 0;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // -------------------------------------------------------------------

        context.global = false;
        ns = prefix (s) + "::";

        // Unit ctor
        if (bed.localDerivative.size () > 0  ||  bed.localIntegrated.size () > 0  ||  bed.localDerivativePreserve.size () > 0  ||  bed.localBufferedExternalWriteDerivative.size () > 0  ||  ! s.parts.isEmpty ()  ||  s.accountableConnections != null  ||  bed.refcount)
        {
            result.append (ns + prefix (s) + " ()\n");
            result.append ("{\n");
            if (bed.localDerivative.size () > 0)
            {
                result.append ("  stackDerivative = 0;\n");
            }
            if (bed.localIntegrated.size () > 0  ||  bed.localDerivativePreserve.size () > 0  ||  bed.localBufferedExternalWriteDerivative.size () > 0)
            {
                result.append ("  preserve = 0;\n");
            }
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".container = this;\n");
            }
            if (s.accountableConnections != null)
            {
                for (EquationSet.AccountableConnection ac : s.accountableConnections)
                {
                    result.append ("  int " + prefix (ac.connection) + "_" + mangle (ac.alias) + "_count = 0;\n");
                }
            }
            if (bed.refcount)
            {
                result.append ("  refcount = 0;\n");
            }
            for (Variable v : bed.localMembers)
            {
                result.append ("  " + mangle (v) + zero (v) + ";\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit dtor
        if (bed.localDerivative.size () > 0  ||  bed.localIntegrated.size () > 0  ||  bed.localBufferedExternalWriteDerivative.size () > 0)
        {
            result.append (ns + "~" + prefix (s) + " ()\n");
            result.append ("{\n");
            if (bed.localDerivative.size () > 0)
            {
                result.append ("  while (stackDerivative)\n");
                result.append ("  {\n");
                result.append ("    Derivative * temp = stackDerivative;\n");
                result.append ("    stackDerivative = stackDerivative->next;\n");
                result.append ("    delete temp;\n");
                result.append ("  }\n");
            }
            if (bed.localIntegrated.size () > 0  ||  bed.localDerivativePreserve.size () > 0  ||  bed.localBufferedExternalWriteDerivative.size () > 0)
            {
                result.append ("  if (preserve) delete preserve;\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit clear
        if (bed.localMembers.size () > 0  ||  bed.localBufferedExternal.size () > 0)
        {
            result.append ("void " + ns + "clear ()\n");
            result.append ("{\n");
            for (Variable v : bed.localMembers)
            {
                result.append ("  " + mangle (v) + zero (v) + ";\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit die
        if (s.canDie ())
        {
            result.append ("void " + ns + "die ()\n");
            result.append ("{\n");

            // tag part as dead
            Variable live = s.find (new Variable ("$live"));
            if (live != null  &&  ! live.hasAny (new String[] {"constant", "accessor"}))  // $live is stored in this part
            {
                result.append ("  " + resolve (live.reference, context, true) + " = 0;\n");
            }

            // instance counting
            if (s.connectionBindings == null) result.append ("  container->" + mangle (s.name) + ".n--;\n");

            for (String alias : bed.accountableEndpoints)
            {
                result.append ("  " + mangle (alias) + "->" + prefix (s) + "_" + mangle (alias) + "_count--;\n");
            }

            result.append ("}\n");
            result.append ("\n");
        }

        // Unit enterSimulation
        if (bed.localReference.size () > 0)
        {
            result.append ("void " + ns + "enterSimulation ()\n");
            result.append ("{\n");
            TreeSet<String> touched = new TreeSet<String> ();  // String rather than EquationSet, because we may have references to several different instances of the same EquationSet, and all must be accounted
            for (VariableReference r : bed.localReference)
            {
                String container = resolveContainer (r, context, "");
                if (touched.add (container)) result.append ("  " + container + "refcount++;\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit leaveSimulation
        {
            result.append ("void " + ns + "leaveSimulation ()\n");
            result.append ("{\n");
            String container = "container->";
            if (bed.pathToContainer != null) container = mangle (bed.pathToContainer) + "->" + container;
            result.append ("  " + container + mangle (s.name) + ".remove (this);\n");
            TreeSet<String> touched = new TreeSet<String> ();
            for (VariableReference r : bed.localReference)
            {
                container = resolveContainer (r, context, "");
                if (touched.add (container)) result.append ("  " + container + "refcount--;\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit isFree
        if (bed.refcount)
        {
            result.append ("bool " + ns + "isFree ()\n");
            result.append ("{\n");
            result.append ("  return refcount == 0;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit init
        if (s.connectionBindings == null  ||  bed.localInit.size () > 0  ||  s.parts.size () > 0  ||  bed.accountableEndpoints.size () > 0)
        {
            result.append ("void " + ns + "init (Simulator & simulator)\n");
            result.append ("{\n");
            s.setInit (1);
            for (Variable v : bed.localBufferedExternal)
            {
                result.append ("  " + mangle ("next_", v) + zero (v) + ";\n");
            }
            // declare buffer variables
            for (Variable v : bed.localBufferedInternal)
            {
                result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
            }
            // $variables
            for (Variable v : bed.localInit)
            {
                if (v.name.equals ("$live")) multiconditional (v, context, "  ");  // force $live to be ahead of everything else, because $live must be true during all of init cycle
            }
            for (Variable v : bed.localInit)
            {
                if (! v.name.startsWith ("$")) continue;  // TODO: This doesn't allow in temporaries that a $variable may depend on. See InternalBackendData sorting section for example of how to handle this better.
                if (v.name.equals ("$live")) continue;
                if (v.name.equals ("$type")) throw new Exception ("$type must be conditional, and it must never be assigned during init.");  // TODO: Work out logic of $type better. This trap should not be here.
                multiconditional (v, context, "  ");
            }
            // finalize $variables
            for (Variable v : bed.localBuffered)  // more than just localBufferedInternal, because we must finalize members as well
            {
                if (! v.name.startsWith ("$")) continue;
                if (v.name.equals ("$t")  &&  v.order == 1)  // $t'
                {
                    result.append ("  if (" + mangle ("next_", v) + " != simulator.dt) simulator.move (" + mangle ("next_", v) + ");\n");
                }
                else
                {
                    result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
                }
            }
            // non-$variables
            for (Variable v : bed.localInit)
            {
                if (v.name.startsWith ("$")) continue;
                multiconditional (v, context, "  ");
            }
            // finalize non-$variables
            for (Variable v : bed.localBuffered)
            {
                if (v.name.startsWith ("$")) continue;
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }

            // clear variables that may be written externally before first finalize()
            for (Variable v : bed.localBufferedExternalWrite)
            {
                result.append ("  " + mangle ("next_", v) + zero (v) + ";\n");
            }

            // instance counting
            if (s.connectionBindings == null) result.append ("  container->" + mangle (s.name) + ".n++;\n");

            for (String alias : bed.accountableEndpoints)
            {
                result.append ("  " + mangle (alias) + "->" + prefix (s) + "_" + mangle (alias) + "_count++;\n");
            }

            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".init (simulator);\n");
            }

            s.setInit (0);
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit integrate
        if (bed.localIntegrated.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("void " + ns + "integrate (Simulator & simulator)\n");
            result.append ("{\n");
            if (bed.localIntegrated.size () > 0)
            {
                // Note the resolve() call on the left-hand-side below has lvalue==false.
                // Integration always takes place in the primary storage of a variable.
                result.append ("  if (preserve)\n");
                result.append ("  {\n");
                for (Variable v : bed.localIntegrated)
                {
                    result.append ("    " + resolve (v.reference, context, false) + " = preserve->" + mangle (v) + " + " + resolve (v.derivative.reference, context, false) + " * simulator.dt;\n");
                }
                result.append ("  }\n");
                result.append ("  else\n");
                result.append ("  {\n");
                for (Variable v : bed.localIntegrated)
                {
                    result.append ("    " + resolve (v.reference, context, false) + " += " + resolve (v.derivative.reference, context, false) + " * simulator.dt;\n");
                }
                result.append ("  }\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".integrate (simulator);\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit update
        if (bed.localUpdate.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("void " + ns + "update (Simulator & simulator)\n");
            result.append ("{\n");
            for (Variable v : bed.localBufferedInternalUpdate)
            {
                result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : bed.localUpdate)
            {
                multiconditional (v, context, "  ");
            }
            for (Variable v : bed.localBufferedInternalUpdate)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".update (simulator);\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit finalize
        if (bed.localBufferedExternal.size () > 0  ||  bed.type != null  ||  s.canDie ()  ||  s.parts.size () > 0)
        {
            result.append ("bool " + ns + "finalize (Simulator & simulator)\n");
            result.append ("{\n");

            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".finalize (simulator);\n");  // ignore return value
            }

            Variable live = s.find (new Variable ("$live"));
            if (live != null  &&  ! live.hasAny (new String[] {"constant", "accessor"}))  // $live is stored in this part
            {
                result.append ("  if (" + resolve (live.reference, context, false) + " == 0) return false;\n");  // early-out if we are already dead, to avoid another call to die()
            }

            for (Variable v : bed.localBufferedExternal)
            {
                if (v.name.equals ("$t")  &&  v.order == 1)
                {
                    result.append ("  if (" + mangle ("next_", v) + " != simulator.dt) simulator.move (" + mangle ("next_", v) + ");\n");
                }
                else
                {
                    result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
                }
            }
            for (Variable v : bed.localBufferedExternalWrite)
            {
                result.append ("  " + mangle ("next_", v) + zero (v) + ";\n");
            }

            if (bed.type != null)
            {
                result.append ("  switch (" + mangle ("$type") + ")\n");
                result.append ("  {\n");
                // Each "split" is one particular set of new parts to transform into.
                // Each combination requires a separate piece of code. Thus, the outer
                // structure here is a switch statement. Each case within the switch implements
                // a particular combination of new parts. At this point, $type merely indicates
                // which combination to process. Afterward, it will be set to an index within that
                // combination, per the N2A language document.
                int countSplits = s.splits.size ();
                for (int i = 0; i < countSplits; i++)
                {
                    ArrayList<EquationSet> split = s.splits.get (i);

                    // Check if $type = me. Ignore this particular case, since it is a null operation
                    if (split.size () == 1  &&  split.get (0) == s)
                    {
                        continue;
                    }

                    result.append ("    case " + i + ":\n");
                    result.append ("    {\n");
                    boolean used = false;  // indicates that this instance is one of the resulting parts
                    int countParts = split.size ();
                    for (int j = 0; j < countParts; j++)
                    {
                        EquationSet to = split.get (j);
                        if (to == s  &&  ! used)
                        {
                            used = true;
                            result.append ("      " + mangle ("$type") + " = " + (j + 1) + ";\n");
                        }
                        else
                        {
                            String container = "container->";
                            if (bed.pathToContainer != null) container = mangle (bed.pathToContainer) + "->" + container;
                            result.append ("      " + container + mangle (s.name) + "_2_" + mangle (to.name) + " (this, simulator, " + (j + 1) + ");\n");
                        }
                    }
                    if (used)
                    {
                        result.append ("      break;\n");
                    }
                    else
                    {
                        result.append ("      die ();\n");
                        result.append ("      return false;\n");
                    }
                    result.append ("    }\n");
                }
                result.append ("  }\n");
            }

            if (s.lethalP)
            {
                Variable p = s.find (new Variable ("$p")); // lethalP implies that $p exists, so no need to check for null
                if (p.hasAttribute ("temporary"))
                {
                    multiconditional (p, context, "  ");
                }
                if (p.hasAttribute ("constant"))
                {
                    double pvalue = ((Scalar) ((Constant) p.equations.first ().expression).value).value;
                    if (pvalue != 0) result.append ("  if (" + resolve (p.reference, context, false) + " < uniform1 ())\n");
                }
                else
                {
                    result.append ("  if (" + mangle ("$p") + " == 0  ||  " + mangle ("$p") + " < 1  &&  " + mangle ("$p") + " < uniform1 ())\n");
                }
                result.append ("  {\n");
                result.append ("    die ();\n");
                result.append ("    return false;\n");
                result.append ("  }\n");
            }

            if (s.lethalConnection)
            {
                for (Entry<String, EquationSet> c : s.connectionBindings.entrySet ())
                {
                	VariableReference r = s.resolveReference (c.getKey () + ".$live");
                	if (! r.variable.hasAttribute ("constant"))
                	{
                        result.append ("  if (" + resolve (r, context, false) + " == 0)\n");
                        result.append ("  {\n");
                        result.append ("    die ();\n");
                        result.append ("    return false;\n");
                        result.append ("  }\n");
                	}
                }
            }

            if (s.lethalContainer)
            {
                VariableReference r = s.resolveReference ("$up.$live");
                if (! r.variable.hasAttribute ("constant"))
                {
                    result.append ("  if (" + resolve (r, context, false) + " == 0)\n");
                    result.append ("  {\n");
                    result.append ("    die ();\n");
                    result.append ("    return false;\n");
                    result.append ("  }\n");
                }
            }

            result.append ("  return true;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit updateDerivative
        if (bed.localDerivativeUpdate.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("void " + ns + "updateDerivative (Simulator & simulator)\n");
            result.append ("{\n");
            for (Variable v : bed.localBufferedInternalDerivative)
            {
                result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : bed.localDerivativeUpdate)
            {
                multiconditional (v, context, "  ");
            }
            for (Variable v : bed.localBufferedInternalDerivative)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".updateDerivative (simulator);\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit finalizeDerivative
        if (bed.localBufferedExternalDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("void " + ns + "finalizeDerivative ()\n");
            result.append ("{\n");
            for (Variable v : bed.localBufferedExternalDerivative)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : bed.localBufferedExternalWriteDerivative)
            {
                result.append ("  " + mangle ("next_", v) + zero (v) + ";\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".finalizeDerivative ();\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        if (bed.localIntegrated.size () > 0  ||  bed.localDerivativePreserve.size () > 0  ||  bed.localBufferedExternalWriteDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            // Unit snapshot
            result.append ("void " + ns + "snapshot ()\n");
            result.append ("{\n");
            if (bed.localIntegrated.size () > 0  ||  bed.localDerivativePreserve.size () > 0  ||  bed.localBufferedExternalWriteDerivative.size () > 0)
            {
                result.append ("  preserve = new Preserve;\n");
                for (Variable v : bed.localIntegrated)
                {
                    result.append ("  preserve->" + mangle (v) + " = " + mangle (v) + ";\n");
                }
                for (Variable v : bed.localDerivativePreserve)
                {
                    result.append ("  preserve->" + mangle (v) + " = " + mangle (v) + ";\n");
                }
                for (Variable v : bed.localBufferedExternalWriteDerivative)
                {
                    result.append ("  preserve->" + mangle ("next_", v) + " = " + mangle ("next_", v) + ";\n");
                    result.append ("  " + mangle ("next_", v) + zero (v) + ";\n");
                }
            }
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".snapshot ();\n");
            }
            result.append ("}\n");
            result.append ("\n");

            // Unit restore
            result.append ("void " + ns + "restore ()\n");
            result.append ("{\n");
            if (bed.localIntegrated.size () > 0  ||  bed.localDerivativePreserve.size () > 0  ||  bed.localBufferedExternalWriteDerivative.size () > 0)
            {
                for (Variable v : bed.localDerivativePreserve)
                {
                    result.append ("  " + mangle (v) + " = preserve->" + mangle (v) + ";\n");
                }
                for (Variable v : bed.localBufferedExternalWriteDerivative)
                {
                    result.append ("  " + mangle ("next_", v) + " = preserve->" + mangle ("next_", v) + ";\n");
                }
                result.append ("  delete preserve;\n");
                result.append ("  preserve = 0;\n");
            }
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".restore ();\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        if (bed.localDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            // Unit pushDerivative
            result.append ("void " + ns + "pushDerivative ()\n");
            result.append ("{\n");
            if (bed.localDerivative.size () > 0)
            {
                result.append ("  Derivative * temp = new Derivative;\n");
                result.append ("  temp->next = stackDerivative;\n");
                result.append ("  stackDerivative = temp;\n");
                for (Variable v : bed.localDerivative)
                {
                    result.append ("  temp->" + mangle (v) + " = " + mangle (v) + ";\n");
                }
            }
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".pushDerivative ();\n");
            }
            result.append ("}\n");
            result.append ("\n");

            // Unit multiplyAddToStack
            result.append ("void " + ns + "multiplyAddToStack (float scalar)\n");
            result.append ("{\n");
            for (Variable v : bed.localDerivative)
            {
                result.append ("  stackDerivative->" + mangle (v) + " += " + mangle (v) + " * scalar;\n");
            }
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".multiplyAddToStack (scalar);\n");
            }
            result.append ("}\n");
            result.append ("\n");

            // Unit multiply
            result.append ("void " + ns + "multiply (float scalar)\n");
            result.append ("{\n");
            for (Variable v : bed.localDerivative)
            {
                result.append ("  " + mangle (v) + " *= scalar;\n");
            }
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".multiply (scalar);\n");
            }
            result.append ("}\n");
            result.append ("\n");

            // Unit addToMembers
            result.append ("void " + ns + "addToMembers ()\n");
            result.append ("{\n");
            if (bed.localDerivative.size () > 0)
            {
                for (Variable v : bed.localDerivative)
                {
                    result.append ("  " + mangle (v) + " += stackDerivative->" + mangle (v) + ";\n");
                }
                result.append ("  Derivative * temp = stackDerivative;\n");
                result.append ("  stackDerivative = stackDerivative->next;\n");
                result.append ("  delete temp;\n");
            }
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".addToMembers ();\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Note: There is a difference between these accessors and transients.
        // * Accessors exist specifically to provide the C runtime engine a way to get values of
        // certain $variables without knowing explicitly what is in the equation set.
        // These accessors adapt around whatever is there, and give back a useful answer.
        // * Transients are variables in the equation set that should be calculated whenever
        // needed, rather than stored. A stored variable may have an accessor, but that alone
        // does not make it transient. Furthermore, calculations within the class
        // shouldn't use the accessors.

        // Unit getLive
        {
            Variable live = s.find (new Variable ("$live"));
            if (live != null  &&  ! live.hasAttribute ("constant"))
            {
                result.append ("float " + ns + "getLive ()\n");
                result.append ("{\n");
                if (! live.hasAttribute ("accessor"))
                {
                    result.append ("  if (" + resolve (live.reference, context, false) + " == 0) return 0;\n");
                }
                if (s.lethalConnection)
                {
                    for (Entry<String, EquationSet> c : s.connectionBindings.entrySet ())
                    {
                        VariableReference r = s.resolveReference (c.getKey () + ".$live");
                        if (! r.variable.hasAttribute ("constant"))
                        {
                            result.append ("  if (" + resolve (r, context, false) + " == 0) return 0;\n");
                        }
                    }
                }
                if (s.lethalContainer)
                {
                    VariableReference r = s.resolveReference ("$up.$live");
                    if (! r.variable.hasAttribute ("constant"))
                    {
                        result.append ("  if (" + resolve (r, context, false) + " == 0) return 0;\n");
                    }
                }
                result.append ("  return 1;\n");
                result.append ("}\n");
                result.append ("\n");
            }
        }

        // Unit getP
        if (s.connectionBindings != null)
        {
            Variable p = s.find (new Variable ("$p", 0));
            if (p != null)
            {
                result.append ("float " + ns + "getP (Simulator & simulator)\n");
                result.append ("{\n");

                s.setInit (1);

                // set $live to 0
                Variable live = s.find (new Variable ("$live"));
                Set<String> liveAttributes = live.attributes;
                live.attributes = null;
                live.addAttribute ("constant");
                EquationEntry e = live.equations.first ();  // this should always be an equation we create; the user cannot declare $live (or $init for that matter)
                Scalar liveValue = (Scalar) ((Constant) e.expression).value;
                liveValue.value = 0;

                if (! p.hasAttribute ("constant"))
                {
                    // Generate any temporaries needed by $p
                    for (Variable t : s.variables)
                    {
                        if (t.hasAttribute ("temporary")  &&  p.dependsOn (t) != null)
                        {
                            multiconditional (t, context, "  ");
                        }
                    }
                    multiconditional (p, context, "  ");  // $p is always calculated, because we are in a pseudo-init phase
                }
                result.append ("  return " + resolve (p.reference, context, false) + ";\n");

                // restore $live
                live.attributes = liveAttributes;
                liveValue.value = 1;

                s.setInit (0);

                result.append ("}\n");
                result.append ("\n");
            }
        }

        // Unit getXYZ
        if (s.connectionBindings == null)  // Connections can also have $xyz, but only compartments need to provide an accessor.
        {
            Variable xyz = s.find (new Variable ("$xyz", 0));
            if (xyz != null)
            {
                result.append ("void " + ns + "getXYZ (Vector3 & xyz)\n");
                result.append ("{\n");
                // $xyz is either stored, "temporary", or "constant"
                // If "temporary", then we compute it on the spot.
                // If "constant", then we use the static matrix created during variable analysis
                // If stored, then simply copy into the return value.
                if (xyz.hasAttribute ("temporary"))
                {
                    // Generate any temporaries needed by $xyz
                    for (Variable t : s.variables)
                    {
                        if (t.hasAttribute ("temporary")  &&  xyz.dependsOn (t) != null)
                        {
                            multiconditional (t, context, "    ");
                        }
                    }
                    multiconditional (xyz, context, "    ");
                }
                result.append ("  xyz = " + resolve (xyz.reference, context, false) + ";\n");
                result.append ("}\n");
                result.append ("\n");
            }
        }

        // Unit project
        if (bed.hasProjectFrom  ||  bed.hasProjectTo)
        {
            Variable xyz = s.find (new Variable ("$xyz", 0));
            boolean xyzStored    = false;
            boolean xyzTemporary = false;
            if (xyz != null)
            {
                xyzTemporary = xyz.hasAttribute ("temporary");
                xyzStored = ! xyzTemporary;
            }

            result.append ("void " + ns + "project (int i, int j, Vector3 & xyz)\n");
            result.append ("{\n");

            String localXYZ = "xyz";
            if (bed.hasProjectTo)
            {
                localXYZ = "__24xyz";
                if (! xyzStored) result.append ("  Vector3 " + mangle (xyz) + ";\n");  // local temporary storage
            }

            // TODO: Handle the case where $xyz is explicitly specified with an equation.
            // This should override all instances of $projectFrom.
            // Or should it merely be the default when $projectFrom is missing?
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            boolean needDefault = false;
            int i = 0;
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                Variable projectFrom = s.find (new Variable (e.getKey () + ".$projectFrom"));
                if (projectFrom == null)
                {
                    VariableReference fromXYZ = s.resolveReference (e.getKey () + ".$xyz");
                    if (fromXYZ.variable == null)
                    {
                        needDefault = true;
                    }
                    else
                    {
                        result.append ("    case " + i + ": " + localXYZ + " = " + resolve (fromXYZ, context, false) + "; break;\n");
                    }
                }
                else
                {
                    result.append ("    case " + i + ":\n");
                    result.append ("    {\n");
                    if (projectFrom.hasAttribute ("temporary"))  // it could also be "constant", but no other type
                    {
                        for (Variable t : s.variables)
                        {
                            if (t.hasAttribute ("temporary")  &&  projectFrom.dependsOn (t) != null)
                            {
                                multiconditional (t, context, "      ");
                            }
                        }
                        multiconditional (projectFrom, context, "      ");
                    }
                    result.append ("      " + localXYZ + " = " + resolve (projectFrom.reference, context, false) + ";\n");
                    result.append ("      break;\n");
                    result.append ("    }\n");
                }
                i++;
            }
            if (needDefault)
            {
                result.append ("    default:\n");
                result.append ("      " + localXYZ + "[0] = 0;\n");
                result.append ("      " + localXYZ + "[1] = 0;\n");
                result.append ("      " + localXYZ + "[2] = 0;\n");
            }
            result.append ("  }\n");
            result.append ("\n");

            if (xyzStored  &&  ! localXYZ.equals ("__24xyz"))
            {
                result.append ("  __24xyz = " + localXYZ + ";\n");
            }

            if (bed.hasProjectTo)
            {
                if (xyzTemporary) xyz.removeAttribute ("temporary");
                result.append ("  switch (j)\n");
                result.append ("  {\n");
                needDefault = false;
                int j = 0;
                for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
                {
                    Variable projectTo = s.find (new Variable (e.getKey () + ".$projectTo"));
                    if (projectTo == null)
                    {
                        needDefault = true;
                    }
                    else
                    {
                        result.append ("    case " + j + ":\n");
                        result.append ("    {\n");
                        if (projectTo.hasAttribute ("temporary"))
                        {
                            for (Variable t : s.variables)
                            {
                                if (t.hasAttribute ("temporary")  &&  projectTo.dependsOn (t) != null)
                                {
                                    multiconditional (t, context, "      ");
                                }
                            }
                            multiconditional (projectTo, context, "      ");
                        }
                        result.append ("      xyz = " + resolve (projectTo.reference, context, false) + ";\n");
                        result.append ("      break;\n");
                        result.append ("    }\n");
                    }
                    j++;
                }
                if (needDefault)
                {
                    result.append ("    default:\n");
                    result.append ("      xyz = __24xyz;\n");
                }
                result.append ("  }\n");
                if (xyzTemporary) xyz.addAttribute ("temporary");
            }

            result.append ("}\n");
            result.append ("\n");
        }

        // Unit getCount
        if (bed.accountableEndpoints.size () > 0)
        {
            result.append ("int " + ns + "getCount (int i)\n");
            result.append ("{\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            int i = 0;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                String alias = n.getKey ();
                if (bed.accountableEndpoints.contains (alias))
                {
                    result.append ("    case " + i + ": return " + mangle (alias) + "->" + prefix (s) + "_" + mangle (alias) + "_count;\n");
                }
                i++;
            }
            result.append ("  }\n");
            result.append ("  return 0;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit setPart
        if (s.connectionBindings != null)
        {
            result.append ("void " + ns + "setPart (int i, Part * part)\n");
            result.append ("{\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            int i = 0;
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                // TODO: This assumes that all the parts are children of the same container as the connection. Need to generalize so connections can cross branches of the containment hierarchy.
                result.append ("    case " + i++ + ": " + mangle (e.getKey ()) + " = (" + prefix (e.getValue ()) + " *) part; return;\n");
            }
            result.append ("  }\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit getPart
        if (s.connectionBindings != null)
        {
            result.append ("Part * " + ns + "getPart (int i)\n");
            result.append ("{\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            int i = 0;
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                result.append ("    case " + i++ + ": return " + mangle (e.getKey ()) + ";\n");
            }
            result.append ("  }\n");
            result.append ("  return 0;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit conversions
        Set<Conversion> conversions = s.getConversions ();
        for (Conversion pair : conversions)
        {
            EquationSet source = pair.from;
            EquationSet dest   = pair.to;
            boolean connectionSource = source.connectionBindings != null;
            boolean connectionDest   = dest  .connectionBindings != null;
            if (connectionSource != connectionDest)
            {
                throw new Exception ("Can't change $type between connection and non-connection.");
                // Why not? Because a connection *must* know the instances it connects, while
                // a compartment cannot know those instances. Thus, one can never be converted
                // to the other.
            }

            // The "2" functions only have local meaning, so they are never virtual.
            // Must do everything init() normally does, including increment $n.
            // Parameters:
            //   from -- the source part
            //   simulator -- the one managing the source part
            //   $type -- The integer index, in the $type expression, of the current target part. The target part's $type field will be initialized with this number (and zeroed after one cycle).
            result.append ("void " + ns + mangle (source.name) + "_2_" + mangle (dest.name) + " (" + mangle (source.name) + " * from, Simulator & simulator, int " + mangle ("$type") + ")\n");
            result.append ("{\n");
            result.append ("  " + mangle (dest.name) + " * to = " + mangle (dest.name) + ".allocate ();\n");
            if (connectionDest)
            {
                // Match connection bindings
                for (Entry<String, EquationSet> c : dest.connectionBindings.entrySet ())
                {
                    String name = c.getKey ();
                    Entry<String, EquationSet> d = source.connectionBindings.floorEntry (name);
                    if (d == null  ||  ! d.getKey ().equals (name)) throw new Exception ("Unfulfilled connection binding during $type change.");
                    result.append ("  to->" + mangle (name) + " = from->" + mangle (name) + ";\n");
                }
            }
            result.append ("  simulator.enqueue (to);\n");
            result.append ("  to->init (simulator);\n");  // sets all variables, so partially redundant with the following code ...
            // TODO: Convert contained populations from matching populations in the source part?

            // Match variables between the two sets.
            // TODO: a match between variables should be marked as a dependency. This might change some "dummy" variables into stored values.
            String [] forbiddenAttributes = new String [] {"global", "constant", "accessor", "reference", "temporary", "dummy", "preexistent"};
            for (Variable v : dest.variables)
            {
                if (v.name.equals ("$type"))
                {
                    result.append ("  to->" + mangle (v) + " = " + mangle ("$type") + ";\n");  // initialize new part with its position in the $type split
                    continue;
                }
                if (v.hasAny (forbiddenAttributes))
                {
                    continue;
                }
                Variable v2 = source.find (v);
                if (v2 != null  &&  v2.equals (v))
                {
                    result.append ("  to->" + mangle (v) + " = " + resolve (v2.reference, context, false, "from->") + ";\n");
                }
            }

            result.append ("}\n");
            result.append ("\n");
        }

        // Unit sub-parts
        for (EquationSet p : s.parts) generateDefinitions (p, result);
    }

    public void multiconditional (Variable v, CRenderer context, String pad) throws Exception
    {
        boolean init = context.part.getInit ();
        boolean isType = v.name.equals ("$type");

        if (v.hasAttribute ("temporary")) context.result.append (pad + type (v) + " " + mangle (v) + ";\n");

        // Select the default equation
        EquationEntry defaultEquation = null;
        for (EquationEntry e : v.equations)
        {
            if (init  &&  e.ifString.equals ("$init"))  // TODO: also handle $init==1, or any other equivalent expression
            {
                defaultEquation = e;
                break;
            }
            if (e.ifString.length () == 0) defaultEquation = e;
        }

        // Dump matrices needed by conditions
        for (EquationEntry e : v.equations)
        {
            if (e.condition != null) prepareMatrices (e.condition, context, pad);
        }

        // Write the conditional equations
        boolean haveIf = false;
        String padIf = pad;
        for (EquationEntry e : v.equations)
        {
            if (e == defaultEquation) continue;
            if (init)
            {
                if (e.ifString.length () == 0) continue;
            }
            else  // not init
            {
                if (e.ifString.equals ("$init")) continue;
            }
            if (e.condition != null)
            {
                String ifString;
                if (haveIf)
                {
                    ifString = "elseif (";
                }
                else
                {
                    ifString = "if (";
                    haveIf = true;
                    padIf = pad + "  ";
                }
                context.result.append (pad + ifString);
                e.condition.render (context);
                context.result.append (")\n");
                context.result.append (pad + "{\n");
            }
            if (isType)
            {
                // Set $type to an integer index indicating which of the splits statements in this equation set
                // was actually triggered. During finalize(), this will select a piece of code that implements
                // this particular split. Afterward, $type will be set to an appropriate index within the split,
                // per the N2A language document.
                if (! (e.expression instanceof Split)) throw new Exception ("Unexpected expression for $type");
                int index = context.part.splits.indexOf (((Split) e.expression).parts);
                context.result.append (padIf + resolve (v.reference, context, true) + " = " + (index + 1) + ";\n");
            }
            else
            {
                prepareMatrices (e.expression, context, pad);
                context.result.append (padIf);
                renderEquation (context, e);
            }
            if (haveIf) context.result.append (pad + "}\n");
        }

        // Write the default equation
        if (defaultEquation == null)
        {
            if (isType)
            {
                if (haveIf) context.result.append (pad + "else\n  ");
                context.result.append (padIf + resolve (v.reference, context, true) + " = 0;\n");  // always reset $type to 0
            }
            else
            {
                // externalWrite variables already have a default action in the prepare() method, so only deal with cycle and externalRead
                if (v.reference.variable == v  &&  v.equations.size () > 0  &&  v.hasAny (new String[] {"cycle", "externalRead"})  &&  ! v.hasAttribute ("initOnly"))
                {
                    if (haveIf) context.result.append (pad + "else\n  ");
                    context.result.append (padIf + resolve (v.reference, context, true) + " = " + resolve (v.reference, context, false) + ";\n");  // copy previous value
                }
            }
        }
        else
        {
            if (haveIf)
            {
                context.result.append (pad + "else\n");
                context.result.append (pad + "{\n");
            }
            if (isType)
            {
                ArrayList<EquationSet> split = ((Split) defaultEquation.expression).parts;
                int index = context.part.splits.indexOf (split);
                context.result.append (padIf + resolve (v.reference, context, true) + " = " + (index + 1) + ";\n");
            }
            else
            {
                prepareMatrices (defaultEquation.expression, context, pad);
                context.result.append (padIf);
                renderEquation (context, defaultEquation);
            }
            if (haveIf) context.result.append (pad + "}\n");
        }
    }

    public void renderEquation (CRenderer context, EquationEntry e)
    {
        if (e.variable.hasAttribute ("dummy"))
        {
            e.expression.render (context);
        }
        else
        {
            switch (e.variable.assignment)
            {
                case Variable.REPLACE:
                    context.result.append (resolve (e.variable.reference, context, true) + " = ");
                    e.expression.render (context);
                    break;
                case Variable.ADD:
                    context.result.append (resolve (e.variable.reference, context, true) + " += ");
                    e.expression.render (context);
                    break;
                case Variable.MULTIPLY:
                    context.result.append (resolve (e.variable.reference, context, true) + " *= ");
                    e.expression.render (context);
                    break;
                case Variable.MIN:
                    context.result.append (resolve (e.variable.reference, context, true) + " = min (" + resolve (e.variable.reference, context, true) + ", ");
                    e.expression.render (context);
                    context.result.append (")");
                    break;
                case Variable.MAX:
                    context.result.append (resolve (e.variable.reference, context, true) + " = max (" + resolve (e.variable.reference, context, true) + ", ");
                    e.expression.render (context);
                    context.result.append (")");
            }
        }
        context.result.append (";\n");
    }

    public void prepareMatrices (Operator op, final CRenderer context, final String pad) throws Exception
    {
        Visitor visitor = new Visitor ()
        {
            public boolean visit (Operator op)
            {
                if (op instanceof BuildMatrix)
                {
                    BuildMatrix m = (BuildMatrix) op;
                    int rows = m.getRows ();
                    int cols = m.getColumns ();

                    String matrixName = "Matrix" + matrixNames.size ();
                    matrixNames.put (m, matrixName);
                    if (rows == 3  &&  cols == 1) context.result.append (pad + "Vector3 " + matrixName + ";\n");
                    else                          context.result.append (pad + "Matrix<float> " + matrixName + " (" + rows + ", " + cols + ");\n");
                    for (int r = 0; r < rows; r++)
                    {
                        if (cols == 1)
                        {
                            context.result.append (pad + matrixName + "[" + r + "] = ");
                            m.operands[0][r].render (context);
                            context.result.append (";\n");
                        }
                        else
                        {
                            for (int c = 0; c < cols; c++)
                            {
                                context.result.append (pad + matrixName + "(" + r + "," + c + ") = ");
                                m.operands[c][r].render (context);
                                context.result.append (";\n");
                            }
                        }
                    }
                    return false;
                }
                return true;
            }
        };
        op.visit (visitor);
    }

    public static String mangle (Variable v)
    {
        return mangle (v.nameString ());
    }

    public static String mangle (String prefix, Variable v)
    {
        return mangle (prefix, v.nameString ());
    }

    public static String mangle (String input)
    {
        return mangle ("_", input);
    }

    public static String mangle (String prefix, String input)
    {
        StringBuilder result = new StringBuilder (prefix);
        for (char c : input.toCharArray ())
        {
            // Even though underscore (_) is a legitimate character in a C identifier,
            // we don't use it.  Instead they are used as an escape for unicode.
            // We use variable length unicode values because there is no need to parse
            // the identifiers back into wide characters.
            if (   ('a' <= c && c <= 'z')
                || ('A' <= c && c <= 'Z')
                || ('0' <= c && c <= '9'))
            {
                result.append (c);
            }
            else
            {
                result.append ("_" + Integer.toHexString (c));
            }
        }
        return result.toString ();
    }

    public static String type (Variable v)
    {
        if (v.type instanceof Matrix)
        {
            Matrix m = (Matrix) v.type;
            if (m.value.length == 1  &&  m.value[0].length == 3) return "Vector3";
            return "Matrix<float>";
        }
        if (v.type instanceof Text) return "string";
        return "float";
    }

    public static String zero (Variable v) throws Exception
    {
        if      (v.type instanceof Scalar) return " = 0";
        else if (v.type instanceof Matrix) return ".clear ()";
        else if (v.type instanceof Text  ) return ".clear ()";
        else throw new Exception ("Unknown Type");
    }

    public static String prefix (EquationSet t)
    {
        if (t == null) return "Wrapper";
        String result = mangle (t.name);
        while (t != null)
        {
            t = t.container;
            if (t != null) result = mangle (t.name) + "_" + result;
        }
        return result;
    }

    public String resolve (VariableReference r, CRenderer context, boolean lvalue)
    {
        return resolve (r, context, lvalue, "");
    }

    /**
        @param v A variable to convert into C++ code that can access it at runtime.
        @param context For the AST rendering system.
        @param lvalue Indicates that this will receive a value assignment. The other case is an rvalue, which will simply be read.
        @param base Injects a pointer at the beginning of the resolution path.
    **/
    public String resolve (VariableReference r, CRenderer context, boolean lvalue, String base)
    {
        if (r == null  ||  r.variable == null) return "unresolved";

        if (r.variable.hasAttribute ("constant"))
        {
            EquationEntry e = r.variable.equations.first ();
            StringBuilder temp = context.result;
            StringBuilder result = new StringBuilder ();
            context.result = result;
            e.expression.render (context);
            context.result = temp;
            return result.toString ();
        }
        if (r.variable.name.equals ("$t")  &&  r.variable.order == 1  &&  ! lvalue  &&  context.part.getInit ())  // force $t'==0 during init phase
        {
            return "0";
        }

        String containers = resolveContainer (r, context, base);

        if (r.variable.name.equals ("(connection)"))
        {
            if (containers.endsWith ("->")) containers = containers.substring (0, containers.length () - 2);
            if (containers.endsWith ("." )) containers = containers.substring (0, containers.length () - 1);
            return containers;
        }

        String name = "";
        if (r.variable.hasAttribute ("simulator"))
        {
            // We can't read $t or $t' from another object's simulator, unless is exactly the same as ours, in which case there is no point.
            // We can't directly write $t' of another object.
            // TODO: Need a way to tell another part to immediately accelerate
            if (containers.length () > 0) return "unresolved";
            if (! lvalue)
            {
                if (r.variable.name.equals ("$t'")) return "simulator.dt";
                return "simulator." + r.variable.name.substring (1);  // strip the $ and expect it to be a member of simulator, which must be passed into the current function
            }
            // if lvalue, then fall through to the main case below 
        }
        if (r.variable.hasAttribute ("accessor"))
        {
            if (lvalue) return "unresolved";

            if (r.variable.name.equals ("$live")) name = "getLive ()";
            else return "unresolved";
        }
        if (r.variable.name.endsWith (".$count"))
        {
            if (lvalue) return "unresolved";
            String alias = r.variable.name.substring (0, r.variable.name.lastIndexOf ("."));
            name = mangle (alias) + "->" + prefix (r.variable.container) + "_" + mangle (alias) + "_count";
        }
        if (name.length () == 0)
        {
            if (   lvalue
                && (   r.variable.hasAny (new String[] {"cycle", "externalWrite"})
                    || (r.variable.hasAttribute ("externalRead")  &&  ! r.variable.hasAttribute ("initOnly"))))
            {
                name = mangle ("next_", r.variable);
            }
            else
            {
                name = mangle (r.variable);
            }
        }
        return containers + name;
    }

    /**
        Compute a series of pointers to get from current part to r.
        Result does not include the variable name itself.
    **/
    public String resolveContainer (VariableReference r, CRenderer context, String base)
    {
        String containers = base;
        EquationSet current = context.part;
        Iterator<Object> it = r.resolution.iterator ();
        while (it.hasNext ())
        {
            Object o = it.next ();
            if (o instanceof EquationSet)  // We are following the containment hierarchy.
            {
                EquationSet s = (EquationSet) o;
                if (s.container == current)  // descend into one of our contained populations
                {
                    if (! it.hasNext ()  &&  r.variable.hasAttribute ("global"))  // descend to the population object
                    {
                        // No need to cast the population instance, because it is explicitly typed
                        containers += mangle (s.name) + ".";
                    }
                    else  // descend to an instance of the population.
                    {
                        // Note: we can only descend a chain of singletons, as indexing is now removed from the N2A langauge.
                        // This restriction does not apply to connections, as they have direct pointers to their targets.
                        String typeName = prefix (s);  // fully qualified
                        // cast PopulationCompartment.live->after, because it is declared in runtime.h as simply a Compartment,
                        // but we need it to be the specific type of compartment we have generated.
                        containers = "((" + typeName + " *) " + containers + mangle (s.name) + ".live->after)->";
                    }
                }
                else  // ascend to our container
                {
                    BackendData bed = (BackendData) current.backendData;
                    if (bed.pathToContainer != null)  // we are a Connection without a container pointer, so we must go through one of our referenced parts
                    {
                        containers += mangle (bed.pathToContainer) + "->";
                    }
                    containers += "container->";
                }
                current = s;
            }
            else if (o instanceof Entry<?, ?>)  // We are following a part reference (which means we are a connection)
            {
                containers += mangle ((String) ((Entry<?, ?>) o).getKey ()) + "->";
                current = (EquationSet) ((Entry<?, ?>) o).getValue ();
            }
        }

        if (r.resolution.isEmpty ()  &&  r.variable.hasAttribute ("global")  &&  ! context.global)
        {
            BackendData bed = (BackendData) current.backendData;
            if (bed.pathToContainer != null)
            {
                containers += mangle (bed.pathToContainer) + "->";
            }
            containers += "container->" + mangle (current.name) + ".";
        }

        return containers;
    }

    public void findPathToContainer (EquationSet s)
    {
        for (EquationSet p : s.parts)
        {
            findPathToContainer (p);
        }

        if (s.connectionBindings != null  &&  s.lethalContainer)  // and therefore needs to check its container
        {
            for (Entry<String, EquationSet> c : s.connectionBindings.entrySet ())
            {
                if (c.getValue ().container == s.container)
                {
                    if (! (s.backendData instanceof BackendData)) s.backendData = new BackendData ();
                    ((BackendData) s.backendData).pathToContainer = c.getKey ();
                    break;
                }
            }
        }
    }

    public void findLiveReferences (EquationSet s)
    {
        for (EquationSet p : s.parts)
        {
            findLiveReferences (p);
        }

        if (s.lethalConnection  ||  s.lethalContainer)
        {
            LinkedList<Object>        resolution     = new LinkedList<Object> ();
            NavigableSet<EquationSet> touched        = new TreeSet<EquationSet> ();
            if (! (s.backendData instanceof BackendData)) s.backendData = new BackendData ();
            findLiveReferences (s, resolution, touched, ((BackendData) s.backendData).localReference, false);
        }
    }

    @SuppressWarnings("unchecked")
    public void findLiveReferences (EquationSet s, LinkedList<Object> resolution, NavigableSet<EquationSet> touched, List<VariableReference> localReference, boolean terminate)
    {
        if (terminate)
        {
            Variable live = s.find (new Variable ("$live"));
            if (live == null  ||  live.hasAttribute ("constant")) return;
            if (live.hasAttribute ("initOnly"))
            {
                if (touched.add (s))
                {
                    VariableReference result = new VariableReference ();
                    result.variable = live;
                    result.resolution = (LinkedList<Object>) resolution.clone ();
                    localReference.add (result);
                    s.referenced = true;
                }
                return;
            }
            // The third possibility is "accessor", in which case we fall through ...
        }

        // Recurse up to container
        if (s.lethalContainer)
        {
            resolution.add (s.container);
            findLiveReferences (s.container, resolution, touched, localReference, true);
            resolution.removeLast ();
        }

        // Recurse into connections
        if (s.lethalConnection)
        {
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                resolution.add (e);
                findLiveReferences (e.getValue (), resolution, touched, localReference, true);
                resolution.removeLast ();
            }
        }
    }

    public class BackendData
    {
        public List<Variable>          localUpdate                           = new ArrayList<Variable> ();  // updated during regular call to update()
        public List<Variable>          localInit                             = new ArrayList<Variable> ();  // set by init()
        public List<Variable>          localMembers                          = new ArrayList<Variable> ();  // stored inside the object
        public List<Variable>          localBuffered                         = new ArrayList<Variable> ();  // needs buffering (temporaries)
        public List<Variable>          localBufferedInternal                 = new ArrayList<Variable> ();  // subset of buffered that are due to dependencies strictly within the current equation-set
        public List<Variable>          localBufferedInternalDerivative       = new ArrayList<Variable> ();  // subset of buffered internal that are derivatives or their dependencies
        public List<Variable>          localBufferedInternalUpdate           = new ArrayList<Variable> ();  // subset of buffered internal that can execute outside of init()
        public List<Variable>          localBufferedExternal                 = new ArrayList<Variable> ();  // subset of buffered that are due to some external access
        public List<Variable>          localBufferedExternalDerivative       = new ArrayList<Variable> ();  // subset of external that are derivatives
        public List<Variable>          localBufferedExternalWrite            = new ArrayList<Variable> ();  // subset of external that are due to external write
        public List<Variable>          localBufferedExternalWriteDerivative  = new ArrayList<Variable> ();  // subset of external write that are derivatives
        public List<Variable>          localIntegrated                       = new ArrayList<Variable> ();  // variables that have derivatives, and thus change their value via integration
        public List<Variable>          localDerivative                       = new ArrayList<Variable> ();  // variables that are derivatives of other variables
        public List<Variable>          localDerivativeUpdate                 = new ArrayList<Variable> ();  // every variable that must be calculated to update derivatives, including their dependencies
        public List<Variable>          localDerivativePreserve               = new ArrayList<Variable> ();  // subset of derivative update that must be restored after integration is done
        public List<VariableReference> localReference                        = new ArrayList<VariableReference> ();  // references to other equation sets which can die
        public List<Variable>          globalUpdate                          = new ArrayList<Variable> ();
        public List<Variable>          globalInit                            = new ArrayList<Variable> ();
        public List<Variable>          globalMembers                         = new ArrayList<Variable> ();
        public List<Variable>          globalBuffered                        = new ArrayList<Variable> ();
        public List<Variable>          globalBufferedInternal                = new ArrayList<Variable> ();
        public List<Variable>          globalBufferedInternalDerivative      = new ArrayList<Variable> ();
        public List<Variable>          globalBufferedInternalUpdate          = new ArrayList<Variable> ();
        public List<Variable>          globalBufferedExternal                = new ArrayList<Variable> ();
        public List<Variable>          globalBufferedExternalDerivative      = new ArrayList<Variable> ();
        public List<Variable>          globalBufferedExternalWrite           = new ArrayList<Variable> ();
        public List<Variable>          globalBufferedExternalWriteDerivative = new ArrayList<Variable> ();
        public List<Variable>          globalIntegrated                      = new ArrayList<Variable> ();
        public List<Variable>          globalDerivative                      = new ArrayList<Variable> ();
        public List<Variable>          globalDerivativeUpdate                = new ArrayList<Variable> ();
        public List<Variable>          globalDerivativePreserve              = new ArrayList<Variable> ();

        public String pathToContainer;
        public Variable type;
        public Variable n;  // only non-null if $n is actually stored as a member
        public List<String> accountableEndpoints = new ArrayList<String> ();
        public boolean refcount;
        public boolean hasProjectFrom;
        public boolean hasProjectTo;
        public boolean canGrowOrDie;  // via $p or $type
        public boolean needK;
        public boolean needMax;
        public boolean needMin;
        public boolean needRadius;
    }

    class CRenderer extends Renderer
    {
        public EquationSet part;
        public boolean global;  ///< Whether this is in the population object (true) or a part object (false)

        public CRenderer (StringBuilder result, EquationSet part)
        {
            super (result);
            this.part = part;
        }

        public boolean render (Operator op)
        {
            if (op instanceof AccessVariable)
            {
                AccessVariable av = (AccessVariable) op;
                result.append (resolve (av.reference, this, false));
                return true;
            }
            if (op instanceof AccessElement)
            {
                AccessElement ae = (AccessElement) op;
                ae.operands[0].render (this);
                if (ae.operands.length == 2)
                {
                    result.append ("[");
                    ae.operands[1].render (this);
                    result.append ("]");
                }
                else if (ae.operands.length == 3)
                {
                    result.append ("(");
                    ae.operands[1].render (this);
                    result.append (",");
                    ae.operands[2].render (this);
                    result.append (")");
                }
                return true;
            }
            if (op instanceof Power)
            {
                Power p = (Power) op;
                result.append ("pow (");
                p.operand0.render (this);
                result.append (", ");
                p.operand1.render (this);
                result.append (")");
                return true;
            }
            if (op instanceof Norm)
            {
                Norm n = (Norm) op;
                result.append ("(");
                n.operands[1].render (this);
                result.append (").norm (");
                n.operands[0].render (this);
                result.append (")");
                return true;
            }
            if (op instanceof Gaussian)
            {
                Gaussian g = (Gaussian) op;
                if (g.operands.length >= 1)
                {
                    int dimension = 3;  // We don't render this number directly. It is merely an indicator.
                    Operator o = g.operands[0];
                    if (o instanceof Constant)
                    {
                        Type d = ((Constant) o).value;
                        if (d instanceof Scalar) dimension = (int) ((Scalar) d).value;
                    }
                    if (dimension > 1)
                    {
                        result.append ("gaussian (");
                        o.render (this);
                        result.append (")");
                        return true;
                    }
                }
                result.append ("gaussian1 ()");
                return true;
            }
            if (op instanceof Uniform)
            {
                Uniform u = (Uniform) op;
                if (u.operands.length >= 1)
                {
                    int dimension = 3;
                    Operator o = u.operands[0];
                    if (o instanceof Constant)
                    {
                        Type d = ((Constant) o).value;
                        if (d instanceof Scalar) dimension = (int) ((Scalar) d).value;
                    }
                    if (dimension > 1)
                    {
                        result.append ("uniform (");
                        o.render (this);
                        result.append (")");
                        return true;
                    }
                }
                result.append ("uniform1 ()");
                return true;
            }
            if (op instanceof ReadMatrix)
            {
                ReadMatrix r = (ReadMatrix) op;
                boolean raw = false;
                if (r.operands.length > 3)
                {
                    if (r.operands[3] instanceof Constant)
                    {
                        Constant c = (Constant) r.operands[3];
                        if (c.value instanceof Text)
                        {
                            String method = ((Text) c.value).value;
                            if (method.equals ("raw")) raw = true;
                        }
                    }
                }
                if (raw) result.append ("matrixRaw (" + matrixNames.get (r.operands[0]) + ", ");
                else     result.append ("matrix ("    + matrixNames.get (r.operands[0]) + ", ");
                r.operands[1].render (this);
                result.append (", ");
                r.operands[2].render (this);
                result.append (")");
                return true;
            }
            if (op instanceof Constant)
            {
                Constant c = (Constant) op;
                Type o = c.value;
                if (o instanceof Scalar)
                {
                    result.append (o.toString ());
                    double value = ((Scalar) o).value;
                    if ((int) value != value) result.append ("f");  // Tell c compiler that our type is float, not double. TODO: let user select numeric type of runtime
                    return true;
                }
                if (o instanceof Text)
                {
                    result.append ("\"" + o.toString () + "\"");
                    return true;
                }
                if (o instanceof Matrix)
                {
                    result.append (matrixNames.get (op));
                    return true;
                }
                return false;
            }
            if (op instanceof BuildMatrix)
            {
                result.append (matrixNames.get (op));
                return true;
            }
            return false;
        }
    }
}
