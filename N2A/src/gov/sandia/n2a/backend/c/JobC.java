/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.backend.internal.InternalBackendData.EventSource;
import gov.sandia.n2a.backend.internal.InternalBackendData.EventTarget;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.EquationSet.Conversion;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.execenvs.ExecutionEnv;
import gov.sandia.n2a.language.AccessElement;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.BuildMatrix;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Split;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.Gaussian;
import gov.sandia.n2a.language.function.Input;
import gov.sandia.n2a.language.function.Norm;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.function.ReadMatrix;
import gov.sandia.n2a.language.function.Uniform;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.operator.Power;
import gov.sandia.n2a.language.type.Instance;
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
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class JobC
{
    static boolean rebuildRuntime = true;  // always rebuild runtime once per session

    // These values are unique across the whole simulation, so they go here rather than BackendDataC.
    // Where possible, the key is a String. Otherwise, it is an Operator which is specific to one expression.
    public HashMap<Object,String> matrixNames = new HashMap<Object,String> ();
    public HashMap<Object,String> inputNames  = new HashMap<Object,String> ();
    public HashMap<Object,String> outputNames = new HashMap<Object,String> ();
    public HashMap<Object,String> stringNames = new HashMap<Object,String> ();

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
            Backend.err.get ().println ("Couldn't determine runtime directory");
            throw new Backend.AbortRun ();
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
        e.flatten ("backend.c");
        e.addGlobalConstants ();
        e.addSpecials ();  // $dt, $index, $init, $live, $n, $t, $type
        e.fillIntegratedVariables ();
        e.findIntegrated ();
        e.resolveLHS ();
        e.resolveRHS ();
        e.findConstants ();
        e.determineTraceVariableName ();
        e.removeUnused ();  // especially get rid of unneeded $variables created by addSpecials()
        e.collectSplits ();
        findPathToContainer (e);
        e.findAccountableConnections ();
        e.findTemporary ();  // for connections, makes $p and $project "temporary" under some circumstances. TODO: make sure this doesn't violate evaluation order rules
        e.determineOrder ();
        e.findDerivative ();
        e.addAttribute ("global",      0, false, new String[] {"$max", "$min", "$k", "$n", "$radius"});
        e.addAttribute ("preexistent", 0, true,  new String[] {"$t'", "$t"});
        e.makeConstantDtInitOnly ();
        e.findInitOnly ();  // propagate initOnly through ASTs
        e.findDeath ();
        e.setAttributesLive ();
        e.forceTemporaryStorageForSpecials ();
        findLiveReferences (e);
        e.determineTypes ();
        analyze (e);

        e.determineDuration ();
        String duration = e.getNamedValue ("duration");
        if (! duration.isEmpty ()) job.set ("$metadata", "duration", duration);

        e.setInit (0);
        System.out.println (e.dump (false));

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
        s.append ("class Wrapper : public WrapperBase\n");
        s.append ("{\n");
        s.append ("public:\n");
        s.append ("  " + prefix (e) + "_Population " + mangle (e.name) + ";\n");
        s.append ("\n");
        s.append ("  Wrapper ()\n");
        s.append ("  {\n");
        s.append ("    population = &" + mangle (e.name) + ";\n");
        s.append ("    " + mangle (e.name) + ".container = this;\n");
        s.append ("  }\n");
        s.append ("};\n");
        s.append ("\n");
        generateDefinitions (e, s);
        s.append ("\n");

        // Main
        s.append ("int main (int argc, char * argv[])\n");
        s.append ("{\n");
        s.append ("  try\n");
        s.append ("  {\n");
        String integrator = e.getNamedValue ("c.integrator", "Euler");
        if (! integrator.equals ("Euler")  &&  ! integrator.isEmpty ())
        {
            s.append ("    simulator.integrator = new " + integrator + ";\n");
        }
        s.append ("    Wrapper wrapper;\n");
        s.append ("    EventStep * event = (EventStep *) simulator.currentEvent;\n");
        s.append ("    event->enqueue (&wrapper);\n");  // no need for wrapper->enterSimulation()
        s.append ("    wrapper.init ();\n");  // This and the next line implement the init cycle at t=0
        s.append ("    simulator.updatePopulations ();\n");
        s.append ("    if (wrapper.visitor->event == event) event->requeue ();\n");
        s.append ("    simulator.run ();\n");  // Now begin stepping forward in time
        s.append ("    outputClose ();\n");
        s.append ("  }\n");
        s.append ("  catch (const char * message)\n");
        s.append ("  {\n");
        s.append ("    cerr << \"Exception: \" << message << endl;\n");
        s.append ("    return 1;\n");
        s.append ("  }\n");
        s.append ("  return 0;\n");
        s.append ("}\n");

        env.setFileContents (sourceFileName, s.toString ());
        String command = env.quotePath (env.build (sourceFileName, runtime));

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

    public void analyze (EquationSet s)
    {
        if (! (s.backendData instanceof BackendDataC)) s.backendData = new BackendDataC ();
        BackendDataC bed = (BackendDataC) s.backendData;
        bed.analyze (s, this);
        for (EquationSet p : s.parts) analyze (p);
    }

    /**
        Declares all classes, along with their member variables and functions.

        This is a very long monolithic function. Use the comments as guideposts on
        the current thing being generated. First we analyze all variables in the
        current equations set, then generate two classes: one for the instances ("local")
        and one for the population as a whole ("global"). Within each class we
        declare buffer classes for integration and derivation, then member variables,
        and finally member functions as appropriate based on the analysis done at the
        beginning.
    **/
    public void generateDeclarations (final EquationSet s, final StringBuilder result)
    {
        final BackendDataC bed = (BackendDataC) s.backendData;

        // Generate static definitions
        class CheckStatic extends Visitor
        {
            public boolean global;
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
                    Function f = (Function) op;
                    if (f instanceof Output  &&  f.operands.length < 3)  // We need to auto-generate the column name.
                    {
                        String stringName = "columnName" + stringNames.size ();
                        stringNames.put (op, stringName);
                        if (global)
                        {
                            bed.setGlobalNeedPath (s);
                            bed.globalColumns.add (stringName);
                        }
                        else
                        {
                            bed.setLocalNeedPath  (s);
                            bed.localColumns.add (stringName);
                        }
                    }
                    // Detect functions that need static handles
                    if (f.operands.length > 0)
                    {
                        Operator operand0 = f.operands[0];
                        if (operand0 instanceof Constant)
                        {
                            Constant c = (Constant) operand0;
                            Type o = c.value;
                            if (o instanceof Text)
                            {
                                String fileName = ((Text) o).value;
                                if (op instanceof ReadMatrix)
                                {
                                    if (! matrixNames.containsKey (fileName))
                                    {
                                        String matrixName = "Matrix" + matrixNames.size ();
                                        matrixNames.put (fileName, matrixName);
                                        result.append ("MatrixInput * " + matrixName + " = matrixHelper (\"" + fileName + "\");\n");
                                    }
                                }
                                else if (f instanceof Input)
                                {
                                    if (! inputNames.containsKey (fileName))
                                    {
                                        String inputName = "Input" + inputNames.size ();
                                        inputNames.put (fileName, inputName);
                                        result.append ("InputHolder * " + inputName + " = inputHelper (\"" + fileName + "\");\n");
                                    }
                                }
                                else if (f instanceof Output)
                                {
                                    if (! outputNames.containsKey (fileName))
                                    {
                                        String outputName = "Output" + outputNames.size ();
                                        outputNames.put (fileName, outputName);
                                        result.append ("OutputHolder * " + outputName + " = outputHelper (\"" + fileName + "\");\n");
                                    }
                                }
                            }
                        }
                        else  // Dynamic file name (no static handle)
                        {
                            if (f instanceof ReadMatrix)
                            {
                                matrixNames.put (op,       "Matrix"   + matrixNames.size ());
                                stringNames.put (operand0, "fileName" + stringNames.size ());
                            }
                            else if (f instanceof Input)
                            {
                                inputNames .put (op,       "Input"    + inputNames .size ());
                                stringNames.put (operand0, "fileName" + stringNames.size ());
                            }
                            else if (f instanceof Output)
                            {
                                outputNames.put (op,       "Output"   + outputNames.size ());
                                stringNames.put (operand0, "fileName" + stringNames.size ());
                            }
                        }
                    }
                    return true;   // Functions could be nested, so continue descent.
                }
                return true;
            }
        }
        CheckStatic checkStatic = new CheckStatic ();
        for (Variable v : s.ordered)
        {
            checkStatic.global = v.hasAttribute ("global");
            v.visit (checkStatic);
        }

        // Sub-parts
        for (EquationSet p : s.parts) generateDeclarations (p, result);

        // -------------------------------------------------------------------

        // Population class header
        result.append ("class " + prefix (s) + "_Population : public Population\n");
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
        if (bed.needGlobalPreserve)
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
        if (bed.n != null)
        {
            result.append ("  int n;\n");
        }
        if (bed.index != null)
        {
            result.append ("  int nextIndex;\n");
        }
        if (bed.globalDerivative.size () > 0)
        {
            result.append ("  Derivative * stackDerivative;\n");
        }
        if (bed.needGlobalPreserve)
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
        for (String columnName : bed.globalColumns)
        {
            result.append ("  string " + columnName + ";\n");
        }
        result.append ("\n");

        // Population functions
        if (bed.needGlobalCtor)
        {
            result.append ("  " + prefix (s) + "_Population ();\n");
        }
        if (bed.needGlobalDtor)
        {
            result.append ("  virtual ~" + prefix (s) + "_Population ();\n");
        }
        result.append ("  virtual Part * create ();\n");
        if (bed.index != null  ||  bed.trackInstances)
        {
            result.append ("  virtual void add (Part * part);\n");
            if (bed.trackInstances)
            {
                result.append ("  virtual void remove (Part * part);\n");
            }
        }
        result.append ("  virtual void init ();\n");
        if (bed.globalIntegrated.size () > 0)
        {
            result.append ("  virtual void integrate ();\n");
        }
        if (bed.globalUpdate.size () > 0)
        {
            result.append ("  virtual void update ();\n");
        }
        if (bed.needGlobalFinalize)
        {
            result.append ("  virtual bool finalize ();\n");
        }
        if (bed.n != null)
        {
            result.append ("  virtual void resize (int n);\n");
        }
        if (bed.globalDerivativeUpdate.size () > 0)
        {
            result.append ("  virtual void updateDerivative ();\n");
        }
        if (bed.globalBufferedExternalDerivative.size () > 0)
        {
            result.append ("  virtual void finalizeDerivative ();\n");
        }
        if (bed.needGlobalPreserve)
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
            if (bed.needK)
            {
                result.append ("  virtual int getK (int i);\n");
            }
            if (bed.needMax)
            {
                result.append ("  virtual int getMax (int i);\n");
            }
            if (bed.needMin)
            {
                result.append ("  virtual int getMin (int i);\n");
            }
            if (bed.needRadius)
            {
                result.append ("  virtual int getRadius (int i);\n");
            }
        }
        if (bed.needGlobalPath)
        {
            result.append ("  virtual void path (string & result);\n");
        }

        // Population class trailer
        result.append ("};\n");
        result.append ("\n");

        // -------------------------------------------------------------------

        // Unit class
        result.append ("class " + prefix (s) + " : public PartTime\n");
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
        if (bed.needLocalPreserve)
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
        if (bed.needLocalPreserve)
        {
            result.append ("  Preserve * preserve;\n");
        }
        if (bed.pathToContainer == null)
        {
            result.append ("  " + prefix (s.container) + " * container;\n");
        }
        if (s.connectionBindings != null)
        {
            for (ConnectionBinding c : s.connectionBindings)
            {
                // we should be able to assume that s.container is non-null; ie: a connection should always operate in some larger container
                result.append ("  " + prefix (c.endpoint) + " * " + mangle (c.alias) + ";\n");
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
        if (bed.index != null)
        {
            result.append ("  int __24index;\n");
        }
        for (Variable v : bed.localMembers)
        {
            result.append ("  " + type (v) + " " + mangle (v) + ";\n");
        }
        for (Variable v : bed.localBufferedExternal)
        {
            result.append ("  " + type (v) + " " + mangle ("next_", v) + ";\n");
        }
        for (EquationSet p : s.parts)
        {
            result.append ("  " + prefix (p) + "_Population " + mangle (p.name) + ";\n");
        }
        for (String columnName : bed.localColumns)
        {
            result.append ("  string " + columnName + ";\n");
        }
        for (EventSource es : bed.eventSources)
        {
            result.append ("  std::vector<Part *> " + "eventMonitor_" + prefix (es.target.container) + ";\n");
        }
        for (EventTarget et : bed.eventTargets)
        {
            if (et.track != null  &&  et.track.name.startsWith ("eventAux"))
            {
                result.append ("  float " + et.track.name + ";\n");
            }
            if (et.timeIndex >= 0)
            {
                result.append ("  float eventTime" + et.timeIndex + ";\n");
            }
        }
        if (bed.eventTargets.size () > 0)
        {
            // This should come last, because it can affect alignment.
            // Since bool is typically stored as a single byte, this approach is suitable for 4 or fewer event types.
            // For larger quantities, a bit-mapped int is better.
            result.append ("  bool eventLatch[" + bed.eventTargets.size () + "];\n");
        }
        result.append ("\n");

        // Unit functions
        if (bed.needLocalCtor  ||  s.parts.size () > 0)
        {
            result.append ("  " + prefix (s) + " ();\n");
        }
        if (bed.needLocalDtor)
        {
            result.append ("  virtual ~" + prefix (s) + " ();\n");
        }
        if (bed.localMembers.size () > 0)
        {
            result.append ("  virtual void clear ();\n");
        }
        if (s.container == null)
        {
            result.append ("  virtual void setPeriod (float dt);\n");
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
        if (bed.needLocalInit  ||  s.parts.size () > 0)
        {
            result.append ("  virtual void init ();\n");
        }
        if (bed.localIntegrated.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("  virtual void integrate ();\n");
        }
        if (bed.localUpdate.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("  virtual void update ();\n");
        }
        if (bed.needLocalFinalize  ||  s.parts.size () > 0)
        {
            result.append ("  virtual bool finalize ();\n");
        }
        if (bed.localDerivativeUpdate.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("  virtual void updateDerivative ();\n");
        }
        if (bed.localBufferedExternalDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("  virtual void finalizeDerivative ();\n");
        }
        if (bed.needLocalPreserve  ||  s.parts.size () > 0)
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
        if (bed.live != null  &&  ! bed.live.hasAttribute ("constant"))
        {
            result.append ("  virtual float getLive ();\n");
        }
        if (s.connectionBindings == null)
        {
            if (bed.xyz != null)
            {
                result.append ("  virtual void getXYZ (Vector3 & xyz);\n");
            }
        }
        else
        {
            if (bed.p != null)
            {
                result.append ("  virtual float getP ();\n");
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
        if (bed.needLocalPath)
        {
            result.append ("  virtual void path (string & result);\n");
        }

        // Conversions
        Set<Conversion> conversions = s.getConversions ();
        for (Conversion pair : conversions)
        {
            EquationSet source = pair.from;
            EquationSet dest   = pair.to;
            result.append ("  void " + mangle (source.name) + "_2_" + mangle (dest.name) + " (" + mangle (source.name) + " * from, int " + mangle ("$type") + ");\n");
        }

        // Unit class trailer
        result.append ("};\n");
        result.append ("\n");
    }

    public void generateDefinitions (EquationSet s, StringBuilder result) throws Exception
    {
        CRenderer context = new CRenderer (result, s);

        // Access backend data
        BackendDataC bed = (BackendDataC) s.backendData;

        context.global = true;
        String ns = prefix (s) + "_Population::";  // namespace for all functions associated with part s

        // Population ctor
        if (bed.needGlobalCtor)
        {
            result.append (ns + prefix (s) + "_Population ()\n");
            result.append ("{\n");
            if (bed.n != null)
            {
                result.append ("  n = 0;\n");
            }
            if (bed.index != null)
            {
                result.append ("  nextIndex = 0;\n");
            }
            if (bed.globalDerivative.size () > 0)
            {
                result.append ("  stackDerivative = 0;\n");
            }
            if (bed.needGlobalPreserve)
            {
                result.append ("  preserve = 0;\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Population dtor
        if (bed.needGlobalDtor)
        {
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
            if (bed.needGlobalPreserve)
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

        // Population add / remove
        if (bed.index != null)
        {
            result.append ("void " + ns + "add (Part * part)\n");
            result.append ("{\n");
            result.append ("  " + prefix (s) + " * p = (" + prefix (s) + " *) part;\n");
            result.append ("  if (p->__24index < 0) p->__24index = nextIndex++;\n");
            if (bed.trackInstances)
            {
                result.append ("  p->before        = &live;\n");
                result.append ("  p->after         =  live.after;\n");
                result.append ("  p->before->after = p;\n");
                result.append ("  p->after->before = p;\n");
            }
            result.append ("}\n");
            result.append ("\n");

            if (bed.trackInstances)
            {
                result.append ("void " + ns + "remove (Part * part)\n");
                result.append ("{\n");
                result.append ("  " + prefix (s) + " * p = (" + prefix (s) + " *) part;\n");
                result.append ("  if (p == old) old = old->after;\n");
                result.append ("  p->before->after = p->after;\n");
                result.append ("  p->after->before = p->before;\n");
                result.append ("  Population::remove (part);\n");
                result.append ("}\n");
                result.append ("\n");
            }
        }

        // Population getTarget
        if (s.connectionBindings != null)
        {
            result.append ("Population * " + ns + "getTarget (int i)\n");
            result.append ("{\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            int i = 0;
            for (ConnectionBinding c : s.connectionBindings)
            {
                // TODO: Need a function to permute all descending paths to a class of populations.
                // In the simplest form, it is a peer in our current container, so no iteration at all.
                result.append ("    case " + i++ + ": return & container->" + mangle (c.endpoint.name) + ";\n");
            }
            result.append ("    default: return 0;\n");
            result.append ("  }\n");
            result.append ("}\n");
            result.append ("\n");
        }

        // Population init
        result.append ("void " + ns + "init ()\n");
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
        if (bed.n != null)
        {
            if (s.connectionBindings != null)
            {
                Backend.err.get ().println ("$n is not applicable to connections");
                throw new Backend.AbortRun ();
            }
            result.append ("  resize (" + resolve (bed.n.reference, context, false) + ");\n");
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
            result.append ("void " + ns + "integrate ()\n");
            result.append ("{\n");
            result.append ("  float dt = getEvent ()->dt;\n");
            result.append ("  if (preserve)\n");
            result.append ("  {\n");
            for (Variable v : bed.globalIntegrated)
            {
                result.append ("    " + resolve (v.reference, context, false) + " = preserve->" + mangle (v) + " + " + resolve (v.derivative.reference, context, false) + " * dt;\n");
            }
            result.append ("  }\n");
            result.append ("  else\n");
            result.append ("  {\n");
            for (Variable v : bed.globalIntegrated)
            {
                result.append ("    " + resolve (v.reference, context, false) + " += " + resolve (v.derivative.reference, context, false) + " * dt;\n");
            }
            result.append ("  }\n");
            result.append ("};\n");
            result.append ("\n");
        }

        // Population update
        if (bed.globalUpdate.size () > 0)
        {
            result.append ("void " + ns + "update ()\n");
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
        if (bed.needGlobalFinalize)
        {
            result.append ("bool " + ns + "finalize ()\n");
            result.append ("{\n");

            if (bed.canResize  &&  bed.n.derivative == null  &&  bed.canGrowOrDie)  // $n shares control with other specials, so must coordinate with them
            {
                if (bed.n.hasAttribute ("initOnly"))  // $n is explicitly assigned only once, so no need to monitor it for assigned values.
                {
                    result.append ("  simulator.resize (this, -1);\n");  // -1 means to update $n from n. This can only be done after other parts are finalized, as they may impose structural dynamics via $p or $type.
                }
                else  // $n may be assigned during the regular update cycle, so we need to monitor it.
                {
                    result.append ("  if (" + mangle ("$n") + " != " + mangle ("next_", "$n") + ") simulator.resize (this, " + mangle ("next_", "$n") + ");\n");
                    result.append ("  else simulator.resize (this, -1);\n");
                }
            }

            for (Variable v : bed.globalBufferedExternal)
            {
                result.append ("  " + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : bed.globalBufferedExternalWrite)
            {
                result.append ("  " + mangle ("next_", v) + zero (v) + ";\n");
            }

            // Return value is generally ignored, except for top-level population.
            boolean returnN = bed.needGlobalFinalizeN;
            if (bed.canResize)  
            {
                if (bed.canGrowOrDie)
                {
                    if (bed.n.derivative != null)  // $n' exists
                    {
                        // the rate of change in $n is pre-determined, so it relentlessly overrides any other structural dynamics
                        if (returnN)
                        {
                            result.append ("  if (n == 0) return false;\n");
                            returnN = false;
                        }
                        result.append ("  simulator.resize (this, " + mangle ("$n") + ");\n");
                    }
                }
                else  // $n is the only kind of structural dynamics, so simply do a resize() when needed
                {
                    if (! bed.n.hasAttribute ("initOnly"))
                    {
                        if (returnN)
                        {
                            result.append ("  if (n == 0) return false;\n");
                            returnN = false;
                        }
                        result.append ("  if (n != (int) " + mangle ("$n") + ") simulator.resize (this, " + mangle ("$n") + ");\n");
                    }
                }
            }

            if (returnN)
            {
                result.append ("  return n;\n");
            }
            else
            {
                result.append ("  return true;\n");
            }
            result.append ("};\n");
            result.append ("\n");
        }

        // Population resize()
        if (bed.n != null)
        {
            result.append ("void " + ns + "resize (int n)\n");
            result.append ("{\n");
            if (bed.canResize  &&  bed.canGrowOrDie  &&  bed.n.derivative == null)
            {
                result.append ("  if (n < 0)\n");
                result.append ("  {\n");
                result.append ("    " + mangle ("$n") + " = this->n;\n");
                result.append ("    return;\n");
                result.append ("  }\n");
                result.append ("\n");
            }
            result.append ("  EventStep * event = container->getEvent ();\n");
            result.append ("  while (this->n < n)\n");
            result.append ("  {\n");
            result.append ("    Part * p = allocate ();\n");
            result.append ("    p->enterSimulation ();\n");
            result.append ("    event->enqueue (p);\n");
            result.append ("    p->init ();\n");
            result.append ("  }\n");
            result.append ("\n");
            result.append ("  Part * p = live.before;\n");
            result.append ("  while (this->n > n)\n");
            result.append ("  {\n");
            result.append ("    if (p == &live) throw \"Inconsistent $n\";\n");
            result.append ("    if (p->getLive ()) p->die ();\n");
            result.append ("    p = p->before;\n");
            result.append ("  }\n");
            result.append ("};\n");
            result.append ("\n");
        }

        // Population updateDerivative
        if (bed.globalDerivativeUpdate.size () > 0)
        {
            result.append ("void " + ns + "updateDerivative ()\n");
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

        if (bed.needGlobalPreserve)
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
            for (ConnectionBinding c : s.connectionBindings)
            {
                Variable v = s.find (new Variable (c.alias + ".$k"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    result.append ("    case " + c.index + ": return ");
                    e.expression.render (context);
                    result.append (";\n");
                }
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
            for (ConnectionBinding c : s.connectionBindings)
            {
                Variable v = s.find (new Variable (c.alias + ".$max"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    result.append ("    case " + c.index + ": return ");
                    e.expression.render (context);
                    result.append (";\n");
                }
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
            for (ConnectionBinding c : s.connectionBindings)
            {
                Variable v = s.find (new Variable (c.alias + ".$min"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    result.append ("    case " + c.index + ": return ");
                    e.expression.render (context);
                    result.append (";\n");
                }
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
            for (ConnectionBinding c : s.connectionBindings)
            {
                Variable v = s.find (new Variable (c.alias + ".$radius"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    result.append ("    case " + c.index + ": return ");
                    e.expression.render (context);
                    result.append (";\n");
                }
            }
            result.append ("  }\n");
            result.append ("  return 0;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        if (bed.needGlobalPath)
        {
            result.append ("void " + ns + "path (string & result)\n");
            result.append ("{\n");
            if (((BackendDataC) s.container.backendData).needLocalPath)  // Will our container provide a non-empty path?
            {
                result.append ("  container->path (result);\n");
                result.append ("  result += \"." + s.name + "\";\n");
            }
            else
            {
                result.append ("  result = \"" + s.name + "\";\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // -------------------------------------------------------------------

        context.global = false;
        ns = prefix (s) + "::";

        // Unit ctor
        if (bed.needLocalCtor  ||  s.parts.size () > 0)
        {
            result.append (ns + prefix (s) + " ()\n");
            result.append ("{\n");
            if (bed.localDerivative.size () > 0)
            {
                result.append ("  stackDerivative = 0;\n");
            }
            if (bed.needLocalPreserve)
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
            if (bed.index != null)
            {
                result.append ("  __24index = -1;\n");  // -1 indicates that an index needs to be assigned. This should only be done once.
            }
            if (bed.localMembers.size () > 0)
            {
                result.append ("  clear ();\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit dtor
        if (bed.needLocalDtor)
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
            if (bed.needLocalPreserve)
            {
                result.append ("  if (preserve) delete preserve;\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit clear
        if (bed.localMembers.size () > 0)
        {
            result.append ("void " + ns + "clear ()\n");
            result.append ("{\n");
            for (Variable v : bed.localMembers)
            {
                result.append ("  " + mangle (v) + zero (v) + ";\n");
            }
            for (EventSource es : bed.eventSources)
            {
                result.append ("  " + mangle ("eventMonitor_", es.target.container.name) + ".clear ();\n");
            }
            for (EventTarget et : bed.eventTargets)
            {
                if (et.track != null  &&  et.track.name.startsWith ("eventAux"))
                {
                    result.append ("  " + et.track.name + " = 0;\n");
                }
                if (et.timeIndex >= 0)
                {
                    result.append ("  eventTime" + et.timeIndex + " = 10;\n");  // Normal values are modulo 1 second. This initial value guarantees no match.
                }
            }
            int eventCount = bed.eventTargets.size ();
            if (eventCount > 0)
            {
                result.append ("  for (int i = 0; i < " + eventCount + "; i++) eventLatch[i] = false;\n");
            }
            for (EquationSet p : s.parts)
            {
                result.append ("  " + prefix (p) + "_Population " + mangle (p.name) + ";\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit setPeriod
        if (s.container == null)  // instance of top-level population, so set period on wrapper whenever our period changes
        {
            result.append ("void " + ns + "setPeriod (float dt)\n");
            result.append ("{\n");
            result.append ("  PartTime::setPeriod (dt);\n");
            result.append ("  if (container->visitor->event != visitor->event) container->setPeriod (dt);\n");
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

            // release event monitors
            for (EventTarget et : bed.eventTargets)
            {
                for (EventSource es : et.sources)
                {
                    String part = "";
                    if (es.reference != null) part = resolveContainer (es.reference, context, "");
                    result.append ("  removeMonitor (" + part + "eventMonitor_" + prefix (s) + ", this);\n");
                }
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
        if (bed.needLocalInit  ||  s.parts.size () > 0)
        {
            result.append ("void " + ns + "init ()\n");
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
                if (v.name.equals ("$type"))
                {
                    Backend.err.get ().println ("$type must be conditional, and it must never be assigned during init.");  // TODO: Work out logic of $type better. This trap should not be here.
                    throw new Backend.AbortRun ();
                }
                multiconditional (v, context, "  ");
            }
            // finalize $variables
            for (Variable v : bed.localBuffered)  // more than just localBufferedInternal, because we must finalize members as well
            {
                if (! v.name.startsWith ("$")) continue;
                if (v.name.equals ("$t")  &&  v.order == 1)  // $t'
                {
                    result.append ("  if (" + mangle ("next_", v) + " != getEvent ()->dt) setPeriod (" + mangle ("next_", v) + ");\n");
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

            // Request event monitors
            for (EventTarget et : bed.eventTargets)
            {
                for (EventSource es : et.sources)
                {
                    String part = "";
                    if (es.reference != null) part = resolveContainer (es.reference, context, "");
                    result.append ("  " + part + "eventMonitor_" + prefix (s) + ".push_back (this);\n");
                }
            }

            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".init ();\n");
            }

            s.setInit (0);
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit integrate
        if (bed.localIntegrated.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("void " + ns + "integrate ()\n");
            result.append ("{\n");
            if (bed.localIntegrated.size () > 0)
            {
                // Note the resolve() call on the left-hand-side below has lvalue==false.
                // Integration always takes place in the primary storage of a variable.
                result.append ("  float dt = getEvent ()->dt;\n");  // TODO: handle spike events, which produce variable integration times, even for fixed-period models
                result.append ("  if (preserve)\n");
                result.append ("  {\n");
                for (Variable v : bed.localIntegrated)
                {
                    result.append ("    " + resolve (v.reference, context, false) + " = preserve->" + mangle (v) + " + " + resolve (v.derivative.reference, context, false) + " * dt;\n");
                }
                result.append ("  }\n");
                result.append ("  else\n");
                result.append ("  {\n");
                for (Variable v : bed.localIntegrated)
                {
                    result.append ("    " + resolve (v.reference, context, false) + " += " + resolve (v.derivative.reference, context, false) + " * dt;\n");
                }
                result.append ("  }\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".integrate ();\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit update
        if (bed.localUpdate.size () > 0  ||  s.parts.size () > 0)
        {
            result.append ("void " + ns + "update ()\n");
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
                result.append ("  " + mangle (e.name) + ".update ();\n");
            }
            result.append ("}\n");
            result.append ("\n");
        }

        // Unit finalize
        if (bed.needLocalFinalize  ||  s.parts.size () > 0)
        {
            result.append ("bool " + ns + "finalize ()\n");
            result.append ("{\n");

            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append ("  " + mangle (e.name) + ".finalize ();\n");  // ignore return value
            }

            // Early-out if we are already dead
            Variable live = s.find (new Variable ("$live"));
            if (live != null  &&  ! live.hasAny (new String[] {"constant", "accessor"}))  // $live is stored in this part
            {
                result.append ("  if (" + resolve (live.reference, context, false) + " == 0) return false;\n");  // early-out if we are already dead, to avoid another call to die()
            }

            // Events
            for (EventSource es : bed.eventSources)
            {
                EventTarget et = es.target;
                String eventMonitor = "eventMonitor_" + prefix (et.container);

                if (es.testEach)
                {
                    result.append ("  for (Part * p : " + eventMonitor + ")\n");
                    result.append ("  {\n");
                    result.append ("    if (! p  ||  ! p->eventTest (" + et.valueIndex + ")) continue;\n");
                    eventGenerate ("    ", et, context, false);
                    result.append ("  }\n");
                }
                else  // All monitors share same condition, so only test one.
                {
                    result.append ("  if (" + eventMonitor + ".size ())\n");
                    result.append ("  {\n");
                    result.append ("    Part * p = 0;\n");
                    result.append ("    for (p : " + eventMonitor + ") if (p) break;\n");  // Find first non-null part.
                    result.append ("    if (p  &&  p->eventTest (" + et.valueIndex + "))\n");
                    result.append ("    {\n");
                    if (es.delayEach)  // Each target instance may require a different delay.
                    {
                        result.append ("      for (p : " + eventMonitor + ")\n");
                        result.append ("      {\n");
                        result.append ("        if (! p) continue;\n");
                        eventGenerate ("        ", et, context, false);
                        result.append ("      }\n");
                    }
                    else  // All delays are the same.
                    {
                        eventGenerate ("      ", et, context, true);
                    }
                    result.append ("    }\n");
                    result.append ("  }\n");
                }
            }
            int eventCount = bed.eventTargets.size ();
            if (eventCount > 0)
            {
                result.append ("  for (int i = 0; i < " + eventCount + "; i++) eventLatch[i] = false;\n");
            }

            // Finalize variables
            for (Variable v : bed.localBufferedExternal)
            {
                if (v.name.equals ("$t")  &&  v.order == 1)
                {
                    result.append ("  if (" + mangle ("next_", v) + " != getEvent ()->dt) setPeriod (" + mangle ("next_", v) + ");\n");
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
                            result.append ("      " + container + mangle (s.name) + "_2_" + mangle (to.name) + " (this, " + (j + 1) + ");\n");
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
                    if (pvalue != 0) result.append ("  if (" + resolve (p.reference, context, false) + " < uniform ())\n");
                }
                else
                {
                    result.append ("  if (" + mangle ("$p") + " == 0  ||  " + mangle ("$p") + " < 1  &&  " + mangle ("$p") + " < uniform ())\n");
                }
                result.append ("  {\n");
                result.append ("    die ();\n");
                result.append ("    return false;\n");
                result.append ("  }\n");
            }

            if (s.lethalConnection)
            {
                for (ConnectionBinding c : s.connectionBindings)
                {
                	VariableReference r = s.resolveReference (c.alias + ".$live");
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
            result.append ("void " + ns + "updateDerivative ()\n");
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
                result.append ("  " + mangle (e.name) + ".updateDerivative ();\n");
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

        if (bed.needLocalPreserve  ||  s.parts.size () > 0)
        {
            // Unit snapshot
            result.append ("void " + ns + "snapshot ()\n");
            result.append ("{\n");
            if (bed.needLocalPreserve)
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
            if (bed.needLocalPreserve)
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
                    for (ConnectionBinding c : s.connectionBindings)
                    {
                        VariableReference r = s.resolveReference (c.alias + ".$live");
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
                result.append ("float " + ns + "getP ()\n");
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

        // Unit events
        if (bed.eventTargets.size () > 0)
        {
            result.append ("bool " + ns + "eventTest (int i)\n");
            result.append ("{\n");
            result.append ("  switch (i)\n");
            result.append ("  {\n");
            for (EventTarget et : bed.eventTargets)
            {
                result.append ("    case " + et.valueIndex + ":\n");
                result.append ("    {\n");
                for (Variable v : et.dependencies)
                {
                    multiconditional (v, context, "      ");
                }
                if (et.edge != EventTarget.NONZERO)
                {
                    result.append ("      float before = " + resolve (et.track.reference, context, false) + ";\n");
                }
                if (et.trackOne)  // This is a single variable, so check its value directly.
                {
                    result.append ("      float after = " + resolve (et.track.reference, context, true) + ";\n");
                }
                else  // This is an expression, so use our private auxiliary variable.
                {
                    result.append ("      float after = ");
                    et.event.operands[0].render (context);
                    result.append (";\n");
                    if (et.edge != EventTarget.NONZERO)
                    {
                        result.append ("      " + mangle (et.track) + " = after;\n");
                    }
                }
                switch (et.edge)
                {
                    case EventTarget.NONZERO:
                        if (et.timeIndex >= 0)
                        {
                            // Guard against multiple events in a given cycle.
                            // Note that other trigger types don't need this because they set the auxiliary variable,
                            // so the next test in the same cycle will no longer see change.
                            result.append ("      if (after == 0) return false;\n");
                            result.append ("      float moduloTime = (float) fmod (getEvent ().t, 1);\n");  // Wrap time at 1 second, to fit in float precision.
                            result.append ("      if (eventTime" + et.timeIndex + " == moduloTime) return false;\n");
                            result.append ("      eventTime" + et.timeIndex + " = moduloTime;\n");
                            result.append ("      return true;\n");
                        }
                        else
                        {
                            result.append ("      return after != 0;\n");
                        }
                        break;
                    case EventTarget.CHANGE:
                        result.append ("      return before != after;\n");
                        break;
                    case EventTarget.FALL:
                        result.append ("      return before != 0  &&  after == 0;\n");
                        break;
                    case EventTarget.RISE:
                    default:
                        result.append ("      return before == 0  &&  after != 0;\n");
                }
                result.append ("    }\n");
            }
            result.append ("  }\n");
            result.append ("}\n");
            result.append ("\n");

            int nonconstantCount = 0;
            for (EventTarget et : bed.eventTargets)
            {
                if (et.delay < -1) nonconstantCount++;
            }
            if (nonconstantCount > 0)
            {
                result.append ("float " + ns + "eventDelay (int i)\n");
                result.append ("{\n");
                result.append ("  switch (i)\n");
                result.append ("  {\n");
                for (EventTarget et : bed.eventTargets)
                {
                    if (et.delay >= -1) continue;

                    // Need to evaluate expression
                    result.append ("    case " + et.valueIndex + ":\n");
                    result.append ("    {\n");
                    for (Variable v : et.dependencies)
                    {
                        multiconditional (v, context, "      ");
                    }
                    result.append ("      float result = ");
                    et.event.operands[1].render (context);
                    result.append (";\n");
                    result.append ("      if (result < 0) return -1;\n");
                    result.append ("      return result;\n");
                    result.append ("    }\n");
                }
                result.append ("  }\n");
                result.append ("}\n");
                result.append ("\n");
            }

            result.append ("void " + ns + "setLatch (int i)\n");
            result.append ("{\n");
            result.append ("  eventLatch[i] = value;\n");
            result.append ("}\n");
            result.append ("\n");
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
            for (ConnectionBinding c : s.connectionBindings)
            {
                Variable projectFrom = s.find (new Variable (c.alias + ".$projectFrom"));
                if (projectFrom == null)
                {
                    VariableReference fromXYZ = s.resolveReference (c.alias + ".$xyz");
                    if (fromXYZ.variable == null)
                    {
                        needDefault = true;
                    }
                    else
                    {
                        result.append ("    case " + c.index + ": " + localXYZ + " = " + resolve (fromXYZ, context, false) + "; break;\n");
                    }
                }
                else
                {
                    result.append ("    case " + c.index + ":\n");
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
                for (ConnectionBinding c : s.connectionBindings)
                {
                    Variable projectTo = s.find (new Variable (c.alias + ".$projectTo"));
                    if (projectTo == null)
                    {
                        needDefault = true;
                    }
                    else
                    {
                        result.append ("    case " + c.index + ":\n");
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
            for (ConnectionBinding c : s.connectionBindings)
            {
                if (bed.accountableEndpoints.contains (c.alias))
                {
                    result.append ("    case " + c.index + ": return " + mangle (c.alias) + "->" + prefix (s) + "_" + mangle (c.alias) + "_count;\n");
                }
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
            for (ConnectionBinding c : s.connectionBindings)
            {
                // TODO: This assumes that all the parts are children of the same container as the connection. Need to generalize so connections can cross branches of the containment hierarchy.
                result.append ("    case " + c.index + ": " + mangle (c.alias) + " = (" + prefix (c.endpoint) + " *) part; return;\n");
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
            for (ConnectionBinding c : s.connectionBindings)
            {
                result.append ("    case " + c.index + ": return " + mangle (c.alias) + ";\n");
            }
            result.append ("  }\n");
            result.append ("  return 0;\n");
            result.append ("}\n");
            result.append ("\n");
        }

        if (bed.needLocalPath)
        {
            result.append ("void " + ns + "path (string & result)\n");
            result.append ("{\n");
            if (s.connectionBindings == null)
            {
                // We assume that result is passed in as the empty string.
                if (s.container != null)
                {
                    if (((BackendDataC) s.container.backendData).needLocalPath)  // Will our container provide a non-empty path?
                    {
                        result.append ("  container->path (result);\n");
                        result.append ("  result += \"." + s.name + "\";\n");
                    }
                    else
                    {
                        result.append ("  result = \"" + s.name + "\";\n");
                    }
                }
                result.append ("  char index[32];\n");
                result.append ("  sprintf (index, \"%i\", __24index);\n");
                result.append ("  result += index;\n");
            }
            else
            {
                boolean first = true;
                for (ConnectionBinding c : s.connectionBindings)
                {
                    if (first)
                    {
                        result.append ("  " + mangle (c.alias) + ".path (result);\n");
                        result.append ("  string temp;\n");
                        first = false;
                    }
                    else
                    {
                        result.append ("  " + mangle (c.alias) + ".path (temp);\n");
                        result.append ("  result += \"-\" + temp;\n");
                    }
                }
            }
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
                Backend.err.get ().println ("Can't change $type between connection and non-connection.");
                throw new Backend.AbortRun ();
                // Why not? Because a connection *must* know the instances it connects, while
                // a compartment cannot know those instances. Thus, one can never be converted
                // to the other.
            }

            // The "2" functions only have local meaning, so they are never virtual.
            // Must do everything init() normally does, including increment $n.
            // Parameters:
            //   from -- the source part
            //   visitor -- the one managing the source part
            //   $type -- The integer index, in the $type expression, of the current target part. The target part's $type field will be initialized with this number (and zeroed after one cycle).
            result.append ("void " + ns + mangle (source.name) + "_2_" + mangle (dest.name) + " (" + mangle (source.name) + " * from, int " + mangle ("$type") + ")\n");
            result.append ("{\n");
            result.append ("  " + mangle (dest.name) + " * to = " + mangle (dest.name) + ".allocate ();\n");
            if (connectionDest)
            {
                // Match connection bindings
                for (ConnectionBinding c : dest.connectionBindings)
                {
                    ConnectionBinding d = source.findConnection (c.alias);
                    if (d == null)
                    {
                        Backend.err.get ().println ("Unfulfilled connection binding during $type change.");
                        throw new Backend.AbortRun ();
                    }
                    result.append ("  to->" + mangle (c.alias) + " = from->" + mangle (c.alias) + ";\n");
                }
            }
            result.append ("  to->enterSimulation ();\n");
            result.append ("  getEvent ()->enqueue (to);\n");
            result.append ("  to->init ();\n");  // sets all variables, so partially redundant with the following code ...
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

    public void eventGenerate (String pad, EventTarget et, CRenderer context, boolean multi)
    {
        String eventSpike = "EventSpike";
        if (multi) eventSpike += "Multi";
        else       eventSpike += "Single";
        String eventSpikeLatch = eventSpike + "Latch";

        StringBuilder result = context.result;
        result.append (pad + "EventStep * event = getEvent ();\n");
        if (et.delay >= -1)  // delay is a constant, so do all tests at the Java level
        {
            if (et.delay < 0)  // timing is no-care
            {
                result.append (pad + eventSpike + " * spike = new " + eventSpikeLatch + ";\n");
                result.append (pad + "spike->t = event->t;\n");  // queue immediately after current cycle, so latches get set for next full cycle
            }
            else if (et.delay == 0)  // process as close to current cycle as possible
            {
                result.append (pad + eventSpike + " * spike = new " + eventSpike + ";\n");  // fully execute the event (not latch it)
                result.append (pad + "spike->t = event->t;\n");  // queue immediately
            }
            else
            {
                // Is delay an quantum number of $t' steps?
                result.append (pad + eventSpike + " * spike;\n");
                result.append (pad + "float delay = " + context.print (et.delay) + ";\n");
                result.append (pad + "float ratio = delay / event->dt;\n");
                result.append (pad + "int step = (int) round (ratio);\n");
                result.append (pad + "if (abs (ratio - step) < 1e-3)\n");  // Is delay close enough to a time-quantum?
                result.append (pad + "{\n");
                result.append (pad + "  if (simulator.eventMode == Simulator::DURING) spike = new " + eventSpikeLatch + ";\n");
                result.append (pad + "  else spike = new " + eventSpike + ";\n");
                result.append (pad + "  if (simulator.eventMode == Simulator::AFTER) delay = (step + 1e-6) * event->dt;\n");
                result.append (pad + "  else delay = (step - 1e-6) * event->dt;\n");
                result.append (pad + "}\n");
                result.append (pad + "else\n");
                result.append (pad + "{\n");
                result.append (pad + "  spike = new " + eventSpike + ";\n");
                result.append (pad + "}\n");
                result.append (pad + "spike->t = event->t + delay;\n");
            }
        }
        else  // delay must be evaluated, so emit tests at C level
        {
            result.append (pad + "float delay = p->eventDelay (" + et.valueIndex + ");\n");
            result.append (pad + eventSpike + " * spike;\n");
            result.append (pad + "if (delay < 0)\n");
            result.append (pad + "{\n");
            result.append (pad + "  " + eventSpike + " * spike = new " + eventSpikeLatch + ";\n");
            result.append (pad + "  spike->t = event->t;\n");
            result.append (pad + "}\n");
            result.append (pad + "else if (delay == 0)\n");
            result.append (pad + "{\n");
            result.append (pad + "  " + eventSpike + " * spike = new " + eventSpike + ";\n");
            result.append (pad + "  spike->t = event->t;\n");
            result.append (pad + "}\n");
            result.append (pad + "else\n");
            result.append (pad + "{\n");
            result.append (pad + "  float ratio = delay / event->dt;\n");
            result.append (pad + "  int step = (int) round (ratio);\n");
            result.append (pad + "  if (abs (ratio - step) < 1e-3)\n");  // Is delay close enough to a time-quantum?
            result.append (pad + "  {\n");
            result.append (pad + "    if (simulator.eventMode == Simulator::DURING) spike = new " + eventSpikeLatch + ";\n");
            result.append (pad + "    else spike = new " + eventSpike + ";\n");
            result.append (pad + "    if (simulator.eventMode == Simulator::AFTER) delay = (step + 1e-6) * event->dt;\n");
            result.append (pad + "    else delay = (step - 1e-6) * event->dt;\n");
            result.append (pad + "  }\n");
            result.append (pad + "  else\n");
            result.append (pad + "  {\n");
            result.append (pad + "    spike = new " + eventSpike + ";\n");
            result.append (pad + "  }\n");
            result.append (pad + "  spike->t = event->t + delay;\n");
            result.append (pad + "}\n");
        }

        result.append (pad + "spike->index = " + et.valueIndex + ";\n");
        if (multi) result.append (pad + "spike->targets = &eventMonitor_" + et.container.name + ";\n");
        else       result.append (pad + "spike->target = p;\n");
        result.append (pad + "simulator.queueEvent.push (spike);\n");
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
            if (e.condition != null) prepareMatrices (e.condition, context, init, pad);
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
                if (! (e.expression instanceof Split))
                {
                    Backend.err.get ().println ("Unexpected expression for $type");
                    throw new Backend.AbortRun ();
                }
                int index = context.part.splits.indexOf (((Split) e.expression).parts);
                context.result.append (padIf + resolve (v.reference, context, true) + " = " + (index + 1) + ";\n");
            }
            else
            {
                prepareMatrices (e.expression, context, init, pad);
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
                if (v.reference.variable == v  &&  v.equations.size () > 0  &&  v.hasAny ("cycle", "externalRead")  &&  ! v.hasAttribute ("initOnly"))
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
                prepareMatrices (defaultEquation.expression, context, init, pad);
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

    /**
        Build complex sub-expressions into a single local variable that can be referenced by the equation.
    **/
    public void prepareMatrices (Operator op, final CRenderer context, final boolean init, final String pad) throws Exception
    {
        // Pass 1 -- Strings and matrix expressions
        Visitor visitor1 = new Visitor ()
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
                if (op instanceof Add)
                {
                    Add a = (Add) op;
                    String stringName = stringNames.get (a);
                    if (stringName != null)
                    {
                        context.result.append (pad + "ostringstream " + stringName + ";\n");
                        context.result.append (pad + stringName);
                        for (Operator o : flattenAdd (a))
                        {
                            boolean needParen = ! (o instanceof Constant);
                            context.result.append (" << ");
                            if (needParen) context.result.append ("(");
                            o.render (context);
                            if (needParen) context.result.append (")");
                        }
                        context.result.append (";\n");
                        return false;
                    }
                }
                if (op instanceof Output)
                {
                    Output o = (Output) op;
                    if (o.operands.length < 3  &&  init)  // column name is generated; only generate during init phase
                    {
                        String stringName = stringNames.get (op);
                        BackendDataC bed = (BackendDataC) context.part.backendData;
                        if (context.global ? bed.needGlobalPath : bed.needLocalPath)
                        {
                            context.result.append (pad + "path (" + stringName + ");\n");
                            context.result.append (pad + stringName + " += \"." + o.variableName + "\";\n");
                        }
                        else
                        {
                            context.result.append (pad + stringName + " = \"" + o.variableName + "\";\n");
                        }
                    }
                    // Fall through to return true, because we also want to visit all the operands of Output.
                }
                return true;
            }
        };
        op.visit (visitor1);

        // Pass 2 -- Input functions
        Visitor visitor2 = new Visitor ()
        {
            public boolean visit (Operator op)
            {
                if (op instanceof ReadMatrix)
                {
                    ReadMatrix r = (ReadMatrix) op;
                    if (! (r.operands[0] instanceof Constant))
                    {
                        String matrixName = matrixNames.get (r);
                        context.result.append (pad + "MatrixInput * " + matrixName + " = matrixHelper (");
                        String stringName = stringNames.get (r.operands[0]);
                        context.result.append (stringName + ".str ()");
                        context.result.append (");\n");
                    }
                    return false;
                }
                if (op instanceof Input)
                {
                    Input i = (Input) op;
                    if (! (i.operands[0] instanceof Constant))
                    {
                        String inputName = inputNames.get (i);
                        context.result.append (pad + "InputHolder * " + inputName + " = inputHelper (");
                        String stringName = stringNames.get (i.operands[0]);
                        context.result.append (stringName + ".str ()");
                        context.result.append (");\n");
                    }
                    return false;
                }
                if (op instanceof Output)
                {
                    Output o = (Output) op;
                    String outputName;
                    if (o.operands[0] instanceof Constant)
                    {
                        outputName = outputNames.get (o.operands[0].render ());
                    }
                    else
                    {
                        outputName = outputNames.get (o);
                        context.result.append (pad + "OutputHolder * " + outputName + " = outputHelper (");
                        String stringName = stringNames.get (o.operands[0]);
                        context.result.append (stringName + ".str ()");
                        context.result.append (");\n");
                    }

                    // Detect raw flag
                    if (o.operands.length > 3)
                    {
                        Instance bypass = new Instance ()
                        {
                            public Type get (VariableReference r) throws EvaluationException
                            {
                                return r.variable.type;
                            }
                        };
                        if (o.operands[3].eval (bypass).toString ().contains ("raw"))
                        {
                            context.result.append (pad + outputName + "->raw = true;\n");
                        }
                    }

                    return false;
                }
                return true;
            }
        };
        op.visit (visitor2);
    }

    public List<Operator> flattenAdd (Add add)
    {
        ArrayList<Operator> result = new ArrayList<Operator> ();
        if (add.operand0 instanceof Add) result.addAll (flattenAdd ((Add) add.operand0));
        else                             result.add (add.operand0);
        if (add.operand1 instanceof Add) result.addAll (flattenAdd ((Add) add.operand1));
        else                             result.add (add.operand1);
        return result;
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
            if (m.columns () == 1  &&  m.rows () == 3) return "Vector3";
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
        else
        {
            Backend.err.get ().println ("Unknown Type");
            throw new Backend.AbortRun ();
        }
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

        String containers = resolveContainer (r, context, base);

        if (r.variable.name.equals ("(connection)"))
        {
            if (containers.endsWith ("->")) containers = containers.substring (0, containers.length () - 2);
            if (containers.endsWith ("." )) containers = containers.substring (0, containers.length () - 1);
            return containers;
        }

        String name = "";
        if (r.variable.hasAttribute ("preexistent"))
        {
            if (! lvalue)
            {
                if (r.variable.name.equals ("$t"))
                {
                    if      (r.variable.order == 0) name = "getEvent ()->t";
                    else if (r.variable.order == 1) name = "getEvent ()->dt";
                    // TODO: what about higher orders of $t?
                }
                else
                {
                    return "simulator." + r.variable.name.substring (1);  // strip the $ and expect it to be a member of simulator
                }
            }
            // for lvalue, fall through to the main case below
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
                && (   r.variable.hasAny ("cycle", "externalWrite")
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
                        // Note: we can only descend a chain of singletons, as indexing is now removed from the N2A language.
                        // This restriction does not apply to connections, as they have direct pointers to their targets.
                        String typeName = prefix (s);  // fully qualified
                        // cast Population.live->after, because it is declared in runtime.h as simply a Compartment,
                        // but we need it to be the specific type of compartment we have generated.
                        containers = "((" + typeName + " *) " + containers + mangle (s.name) + ".live->after)->";
                    }
                }
                else  // ascend to our container
                {
                    BackendDataC bed = (BackendDataC) current.backendData;
                    if (bed.pathToContainer != null)  // we are a Connection without a container pointer, so we must go through one of our referenced parts
                    {
                        containers += mangle (bed.pathToContainer) + "->";
                    }
                    containers += "container->";
                }
                current = s;
            }
            else if (o instanceof ConnectionBinding)  // We are following a part reference (which means we are a connection)
            {
                ConnectionBinding c = (ConnectionBinding) o;
                containers += mangle (c.alias) + "->";
                current = c.endpoint;
            }
        }

        if (r.resolution.isEmpty ()  &&  r.variable.hasAttribute ("global")  &&  ! context.global)
        {
            BackendDataC bed = (BackendDataC) current.backendData;
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
            for (ConnectionBinding c : s.connectionBindings)
            {
                if (c.endpoint.container == s.container)
                {
                    if (! (s.backendData instanceof BackendDataC)) s.backendData = new BackendDataC ();
                    ((BackendDataC) s.backendData).pathToContainer = c.alias;
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
            ArrayList<Object>         resolution     = new ArrayList<Object> ();
            NavigableSet<EquationSet> touched        = new TreeSet<EquationSet> ();
            if (! (s.backendData instanceof BackendDataC)) s.backendData = new BackendDataC ();
            findLiveReferences (s, resolution, touched, ((BackendDataC) s.backendData).localReference, false);
        }
    }

    @SuppressWarnings("unchecked")
    public void findLiveReferences (EquationSet s, ArrayList<Object> resolution, NavigableSet<EquationSet> touched, List<VariableReference> localReference, boolean terminate)
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
                    result.resolution = (ArrayList<Object>) resolution.clone ();
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
            resolution.remove (resolution.size () - 1);
        }

        // Recurse into connections
        if (s.lethalConnection)
        {
            for (ConnectionBinding c : s.connectionBindings)
            {
                resolution.add (c);
                findLiveReferences (c.endpoint, resolution, touched, localReference, true);
                resolution.remove (resolution.size () - 1);
            }
        }
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
            // TODO: for "3 letter" functions (sin, cos, pow, etc) on matrices, render as visitor which produces a matrix result
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
                result.append ("gaussian (");
                if (g.operands.length > 0)
                {
                    g.operands[0].render (this);
                }
                result.append (")");
                return true;
            }
            if (op instanceof Uniform)
            {
                Uniform u = (Uniform) op;
                result.append ("gaussian (");
                if (u.operands.length > 0)
                {
                    u.operands[0].render (this);
                }
                result.append (")");
                return true;
            }
            if (op instanceof ReadMatrix)
            {
                ReadMatrix r = (ReadMatrix) op;

                String mode = "";
                int lastParm = r.operands.length - 1;
                if (lastParm > 0)
                {
                    if (r.operands[lastParm] instanceof Constant)
                    {
                        Constant c = (Constant) r.operands[lastParm];
                        if (c.value instanceof Text)
                        {
                            mode = ((Text) c.value).value;
                        }
                    }
                }

                String matrixName;
                if (r.operands[0] instanceof Constant) matrixName = matrixNames.get (r.operands[0].toString ());
                else                                   matrixName = matrixNames.get (r);
                result.append (matrixName + "->");
                if (mode.equals ("rows"))
                {
                    result.append ("rows ()");
                }
                else if (mode.equals ("columns"))
                {
                    result.append ("columns ()");
                }
                else
                {
                    result.append ("get");
                    if (mode.equals ("raw")) result.append ("Raw");
                    result.append (" (");
                    r.operands[1].render (this);
                    result.append (", ");
                    r.operands[2].render (this);
                    result.append (")");
                }
                return true;
            }
            if (op instanceof Output)
            {
                Output o = (Output) op;
                String outputName;
                if (o.operands[0] instanceof Constant) outputName = outputNames.get (o.operands[0].toString ());
                else                                   outputName = outputNames.get (o);
                result.append (outputName + "->trace (getEvent ()->t, ");

                if (o.operands.length > 2)  // column name is explicit
                {
                    o.operands[2].render (this);
                }
                else  // column name is generated, so use prepared string value
                {
                    String stringName = stringNames.get (op);  // generated column name is associated with Output function itself, rather than one of its operands
                    result.append (stringName);
                }
                result.append (", ");

                o.operands[1].render (this);
                result.append (")");
                return true;
            }
            if (op instanceof Input)
            {
                Input i = (Input) op;
                String inputName;
                if (i.operands[0] instanceof Constant) inputName = inputNames.get (i.operands[0].toString ());
                else                                   inputName = inputNames.get (i);

                String mode = "";
                if (i.operands.length > 3)
                {
                    mode = i.operands[3].toString ();  // just assuming it's a constant string
                }
                else if (i.operands[1] instanceof Constant)
                {
                    Constant c = (Constant) i.operands[1];
                    if (c.value instanceof Text) mode = c.toString ();
                }
                boolean time = mode.contains ("time");

                if (mode.contains ("columns"))
                {
                    result.append (inputName + "->getColumns (" + time + ")");
                }
                else
                {
                    Operator op2 = i.operands[2];
                    result.append (inputName + "->get");
                    if (   mode.contains ("raw")   // select raw mode, but only if column is not identified by a string
                        && !stringNames.containsKey (op2)
                        && !(op2 instanceof Constant  &&  ((Constant) op2).value instanceof Text))
                    {
                        result.append ("Raw");
                    }
                    result.append (" (");
                    i.operands[1].render (this);
                    result.append (", " + time + ", ");
                    op2.render (this);
                    result.append (")");
                }

                return true;
            }
            if (op instanceof Constant)
            {
                Constant c = (Constant) op;
                Type o = c.value;
                if (o instanceof Scalar)
                {
                    result.append (print (((Scalar) o).value));
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
            if (op instanceof Add)
            {
                // Check if this is a string expression
                String stringName = stringNames.get (op);
                if (stringName != null)
                {
                    result.append (stringName + ".str ()");
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

        public String print (double d)
        {
            String result = Scalar.print (d);
            if ((int) d != d) result += "f";  // Tell C compiler that our type is float, not double. TODO: let user select numeric type of runtime
            return result;
        }
    }
}
