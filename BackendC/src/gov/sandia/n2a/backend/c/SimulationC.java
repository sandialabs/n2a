/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.operator.Power;
import gov.sandia.n2a.language.parse.ASTConstant;
import gov.sandia.n2a.language.parse.ASTNodeBase;
import gov.sandia.n2a.language.parse.ASTNodeRenderer;
import gov.sandia.n2a.language.parse.ASTOpNode;
import gov.sandia.n2a.language.parse.ASTRenderingContext;
import gov.sandia.n2a.language.parse.ASTVarNode;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.RunOrient;
import gov.sandia.umf.platform.plugins.RunState;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import replete.util.FileUtil;

public class SimulationC implements Simulation
{
    static boolean rebuildRuntime = true;  // always rebuild runtime once per session
    public TreeMap<String, String> metadata = new TreeMap<String, String> ();
    /** @deprecated **/
    private ExecutionEnv execEnv;
    /** @deprecated **/
    private RunState runState;

    @Override
    public ParameterDomain getAllParameters ()
    {
        return null;
    }

    @Override
    public void setSelectedParameters (ParameterDomain domain)
    {
        for (Entry<Object, Parameter> p : domain.getParameterMap ().entrySet ())
        {
            String name  = p.getKey ().toString ();
            String value = p.getValue ().getDefaultValue ().toString ();
            metadata.put (name, value);
        }
    }

    /** @deprecated Please separate job-related information from backend classes. RunState or something similar to it should be sufficient for this. **/
    @Override
    public void submit () throws Exception
    {
        execEnv.submitJob (runState);
    }

    @Override
    public void submit (ExecutionEnv env, RunState runState) throws Exception
    {
        env.submitJob (runState);
    }

    @Override
    public boolean resourcesAvailable()
    {
        return true;
    }

    @Override
    public RunState prepare (Object run, ParameterSpecGroupSet groups, ExecutionEnv env) throws Exception
    {
        // from prepare method
        RunStateC result = new RunStateC ();
        result.model = ((RunOrient) run).getModel ();

        // Ensure runtime is built
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
                String contents = FileUtil.getTextContent (SimulatorC.class.getResource ("runtime/" + s).openStream ());
                env.setFileContents (env.file (runtimeDir, s), contents);
            }

            String flDir = env.file (runtimeDir, "fl");
            env.createDir (flDir);
            sourceFiles = new String [] {"archive.h", "blasproto.h", "math.h", "matrix.h", "Matrix.tcc", "MatrixFixed.tcc", "neighbor.h", "pointer.h", "string.h", "Vector.tcc"};
            for (String s : sourceFiles)
            {
                String contents = FileUtil.getTextContent (SimulatorC.class.getResource ("runtime/fl/" + s).openStream ());
                env.setFileContents (env.file (flDir, s), contents);
            }

            runtime = env.buildRuntime (env.file (runtimeDir, "runtime.cc"));
        }

        // Create model-specific executable
        result.jobDir = env.createJobDir ();
        String sourceFileName = env.file (result.jobDir, "model");

        EquationSet e = new EquationSet (result.model);
        e.name = "Model";  // because the default is for top-level equation set to be anonymous

        // TODO: fix run ensembles to put metadata directly in a special derived part
        e.metadata.putAll (metadata);  // parameters pushed by run system override any we already have

        e.flatten ();
        e.addSpecials ();  // $dt, $index, $init, $live, $n, $t, $type
        e.fillIntegratedVariables ();
        e.findIntegrated ();
        e.resolveLHS ();
        e.resolveRHS ();
        // TODO: check for unresolved variables, and stop with a full list of them to the user
        e.findConstants ();
        e.removeUnused ();  // especially get rid of unneeded $variables created by addSpecials()
        e.collectSplits ();
        findPathToContainer (e);
        e.findAccountableConnections ();
        e.findTemporary ();  // for connections, makes $p and $project "temporary" under some circumstances
        e.determineOrder ();
        e.findDerivative ();
        e.addAttribute ("global",       0, false, new String[] {"$max", "$min", "$k", "$n", "$radius"});
        e.addAttribute ("preexistent", -1, false, new String[] {"$index"});
        e.addAttribute ("preexistent",  0, true,  new String[] {"$dt", "$n", "$t"});
        e.addAttribute ("simulator",    0, true,  new String[] {"$dt", "$t"});
        replaceConstantWithInitOnly (e);  // for "preexistent" $variables ($dt, $n, $t)
        e.findInitOnly ();  // propagate initOnly through ASTs
        e.findDeath ();
        e.setAttributesLive ();
        setFunctions (e);
        findReferences (e);

        e.setInit (0);
        System.out.println (e.flatList (false));

        StringBuilder s = new StringBuilder ();

        s.append ("#include \"" + env.file (runtimeDir, "runtime.h") + "\"\n");
        s.append ("\n");
        s.append ("#include <iostream>\n");
        s.append ("#include <vector>\n");
        s.append ("#include <cmath>\n");
        s.append ("\n");
        s.append ("using namespace std;\n");
        s.append ("using namespace fl;\n");
        s.append ("\n");
        s.append ("class Wrapper : public Part\n");
        s.append ("{\n");
        s.append ("public:\n");
        s.append (generateClasses (e, "  "));
        s.append ("  virtual void init (Simulator & simulator)\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.init (simulator);\n");
        s.append ("    writeTrace ();\n");  // After the above init() call will create all initial parts. There may be some calls to trace() in the init() functions, so we dump time step 0 now.
        s.append ("  }\n");
        s.append ("\n");
        s.append ("  virtual void integrate (Simulator & simulator)\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.integrate (simulator);\n");
        s.append ("  }\n");
        s.append ("\n");
        s.append ("  virtual void prepare ()\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.prepare ();\n");
        s.append ("  }\n");
        s.append ("\n");
        s.append ("  virtual void update (Simulator & simulator)\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.update (simulator);\n");
        s.append ("  }\n");
        s.append ("\n");
        s.append ("  virtual bool finalize (Simulator & simulator)\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.finalize (simulator);\n");
        s.append ("    return _Model_Population_Instance.__24n;\n");  // The simulation stops when the last model instance dies.
        s.append ("  }\n");
        s.append ("\n");
        s.append ("  virtual void prepareDerivative ()\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.prepareDerivative ();\n");
        s.append ("  }\n");
        s.append ("\n");
        s.append ("  virtual void updateDerivative (Simulator & simulator)\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.updateDerivative (simulator);\n");
        s.append ("  }\n");
        s.append ("\n");
        s.append ("  virtual void finalizeDerivative ()\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.finalizeDerivative ();\n");
        s.append ("  }\n");
        s.append ("\n");
        s.append ("  virtual void pushIntegrated ()\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.pushIntegrated ();\n");
        s.append ("  }\n");
        s.append ("\n");
        s.append ("  virtual void popIntegrated ()\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.popIntegrated ();\n");
        s.append ("  }\n");
        s.append ("\n");
        s.append ("  virtual void pushDerivative ()\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.pushDerivative ();\n");
        s.append ("  }\n");
        s.append ("\n");
        s.append ("  virtual void multiplyAddToStack (float scalar)\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.multiplyAddToStack (scalar);\n");
        s.append ("  }\n");
        s.append ("\n");
        s.append ("  virtual void multiply (float scalar)\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.multiply (scalar);\n");
        s.append ("  }\n");
        s.append ("\n");
        s.append ("  virtual void addToMembers ()\n");
        s.append ("  {\n");
        s.append ("    _Model_Population_Instance.addToMembers ();\n");
        s.append ("  }\n");
        s.append ("};\n");
        s.append ("\n");
        s.append ("Wrapper wrapper;\n");
        s.append ("\n");

        // Main
        s.append ("int main (int argc, char * argv[])\n");
        s.append ("{\n");
        String integrator = e.metadata.get ("c.integrator");
        if (integrator == null)
        {
            integrator = "Euler";
        }
        s.append ("  try\n");
        s.append ("  {\n");
        s.append ("    " + integrator + " simulator;\n");
        s.append ("    wrapper._Model_Population_Instance.container = &wrapper;\n");
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
        result.command = env.quotePath (env.build (sourceFileName, runtime));
        env.submitJob (result);
        return result;
    }

    @Override
    public RunState execute (Object run, ParameterSpecGroupSet groups, ExecutionEnv env) throws Exception
    {
        RunState result = prepare (run, groups, env);
        submit (env, result);
        return result;
    }

    public String generateClasses (EquationSet s, String pad) throws Exception
    {
        StringBuilder result = new StringBuilder ();

        String pad2 = pad  + "  ";
        String pad3 = pad2 + "  ";
        String pad4 = pad3 + "  ";
        String pad5 = pad4 + "  ";

        // Unit class header
        result.append (pad + "class " + mangle (s.name) + " : public ");
        if (s.connectionBindings == null) result.append ("Compartment\n");
        else                              result.append ("Connection\n");
        result.append (pad + "{\n");
        result.append (pad + "public:\n");

        // Unit sub-parts
        // TODO: handle c++ declaration problem more systematically
        // Right now, this is hacked to emit compartments first, then connections
        for (EquationSet p : s.parts)
        {
            if (p.connectionBindings == null) result.append (generateClasses (p, pad2));
        }
        for (EquationSet p : s.parts)
        {
            if (p.connectionBindings != null) result.append (generateClasses (p, pad2));
        }

        CRenderingContext context = new CRenderingContext (s);
        context.add (ASTVarNode .class, new VariableMangler ());
        context.add (ASTOpNode  .class, new OpSubstituter ());
        context.add (ASTConstant.class, new ConstantRenderer ());
        context.global = false;

        // Separate variables into logically useful lists
        List<Variable> localUpdate                           = new ArrayList<Variable> ();  // updated during regular call to update()
        List<Variable> localInit                             = new ArrayList<Variable> ();  // set by init()
        List<Variable> localMembers                          = new ArrayList<Variable> ();  // stored inside the object
        List<Variable> localStackDerivative                  = new ArrayList<Variable> ();  // must be pushed onto the derivative stack
        List<Variable> localBuffered                         = new ArrayList<Variable> ();  // needs buffering (temporaries)
        List<Variable> localBufferedInternal                 = new ArrayList<Variable> ();  // subset of buffered that are due to dependencies strictly within the current equation-set
        List<Variable> localBufferedInternalDerivative       = new ArrayList<Variable> ();  // subset of buffered internal that are derivatives or their dependencies
        List<Variable> localBufferedInternalUpdate           = new ArrayList<Variable> ();  // subset of buffered internal that can execute outside of init()
        List<Variable> localBufferedExternal                 = new ArrayList<Variable> ();  // subset of buffered that are due to some external access
        List<Variable> localBufferedExternalDerivative       = new ArrayList<Variable> ();  // subset of external that are derivatives
        List<Variable> localBufferedExternalWrite            = new ArrayList<Variable> ();  // subset of external that are due to external write
        List<Variable> localBufferedExternalWriteDerivative  = new ArrayList<Variable> ();  // subset of external write that are derivatives
        List<Variable> localIntegrated                       = new ArrayList<Variable> ();  // must be pushed onto the integral stack
        List<Variable> localDerivative                       = new ArrayList<Variable> ();  // changed by updateDerivative
        List<VariableReference> localReference               = new ArrayList<VariableReference> ();  // references to other equation sets which can die
        List<Variable> globalUpdate                          = new ArrayList<Variable> ();
        List<Variable> globalInit                            = new ArrayList<Variable> ();
        List<Variable> globalMembers                         = new ArrayList<Variable> ();
        List<Variable> globalStackDerivative                 = new ArrayList<Variable> ();
        List<Variable> globalBuffered                        = new ArrayList<Variable> ();
        List<Variable> globalBufferedInternal                = new ArrayList<Variable> ();
        List<Variable> globalBufferedInternalDerivative      = new ArrayList<Variable> ();
        List<Variable> globalBufferedInternalUpdate          = new ArrayList<Variable> ();
        List<Variable> globalBufferedExternal                = new ArrayList<Variable> ();
        List<Variable> globalBufferedExternalDerivative      = new ArrayList<Variable> ();
        List<Variable> globalBufferedExternalWrite           = new ArrayList<Variable> ();
        List<Variable> globalBufferedExternalWriteDerivative = new ArrayList<Variable> ();
        List<Variable> globalIntegrated                      = new ArrayList<Variable> ();
        List<Variable> globalDerivative                      = new ArrayList<Variable> ();
        Variable type = null;  // type is always a local (instance) variable; a population *is* a type, so it can't change itself!
        System.out.println (s.name);
        for (Variable v : s.ordered)  // we want the sub-lists to be ordered correctly
        {
            System.out.println ("  " + v.nameString () + " " + v.attributeString ());
            if (v.name.equals ("$type")) type = v;
            if (v.hasAttribute ("global"))
            {
                if (! v.hasAny (new String[] {"constant", "accessor"}))
                {
                    boolean initOnly               = v.hasAttribute ("initOnly");
                    boolean derivativeOrDependency = v.hasAttribute ("derivativeOrDependency");
                    if (! initOnly) globalUpdate.add (v);
                    if (derivativeOrDependency) globalDerivative.add (v);
                    if (! v.hasAttribute ("reference"))
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        boolean unusedTemporary = temporary  &&  ! v.hasUsers;
                        if (! unusedTemporary) globalInit.add (v);
                        if (! temporary)
                        {
                            if (! v.hasAny (new String [] {"preexistent", "dummy"})) globalMembers.add (v);
                            if (v.order > 0) globalStackDerivative.add (v);

                            boolean external = false;
                            if (v.hasAttribute ("externalWrite"))
                            {
                                external = true;
                                globalBufferedExternalWrite.add (v);
                                if (derivativeOrDependency) globalBufferedExternalWriteDerivative.add (v);
                            }
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  v.equations.size () > 0))
                            {
                                external = true;
                                globalBufferedExternal.add (v);
                                if (derivativeOrDependency) globalBufferedExternalDerivative.add (v);
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                globalBuffered.add (v);
                                if (! external)
                                {
                                    globalBufferedInternal.add (v);
                                    if (! initOnly) globalBufferedInternalUpdate.add (v);
                                    if (derivativeOrDependency) globalBufferedInternalDerivative.add (v);
                                }
                            }
                        }
                    }
                }
            }
            else
            {
                if (! v.hasAny (new String[] {"constant", "accessor"}))
                {
                    boolean initOnly               = v.hasAttribute ("initOnly");
                    boolean derivativeOrDependency = v.hasAttribute ("derivativeOrDependency");
                    if (! initOnly) localUpdate.add (v);
                    if (derivativeOrDependency) localDerivative.add (v);
                    if (v.hasAttribute ("reference"))
                    {
                        if (v.reference.variable.container.canDie ()) localReference.add (v.reference);
                    }
                    else
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        boolean unusedTemporary = temporary  &&  ! v.hasUsers;
                        if (! unusedTemporary  &&  ! v.name.equals ("$index")) localInit.add (v);
                        if (! temporary)
                        {
                            if (! v.hasAny (new String [] {"preexistent", "dummy"})) localMembers.add (v);
                            if (v.order > 0) localStackDerivative.add (v);

                            boolean external = false;
                            if (v.hasAttribute ("externalWrite"))
                            {
                                external = true;
                                localBufferedExternalWrite.add (v);
                                if (derivativeOrDependency) localBufferedExternalWriteDerivative.add (v);
                            }
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  v.equations.size () > 0))
                            {
                                external = true;
                                localBufferedExternal.add (v);
                                if (derivativeOrDependency) localBufferedExternalDerivative.add (v);
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                localBuffered.add (v);
                                if (! external)
                                {
                                    localBufferedInternal.add (v);
                                    if (! initOnly) localBufferedInternalUpdate.add (v);
                                    if (derivativeOrDependency) localBufferedInternalDerivative.add (v);
                                }
                            }
                        }
                    }
                }
            }
        }
        for (Variable v : s.variables)  // we need these to be in order by differential level, not by dependency
        {
            if (v.hasAttribute ("integrated"))  // Do we need to guard against reference, constant, transient?
            {
                if (v.hasAttribute ("global"))
                {
                    globalIntegrated.add (v);
                }
                else
                {
                    localIntegrated.add (v);
                }
            }
        }

        // Access backend data
        String pathToContainer = null;
        if (s.backendData instanceof BackendData)
        {
            BackendData bed = (BackendData) s.backendData;
            pathToContainer = bed.pathToContainer;
            if (bed.liveReferences != null) localReference.addAll (bed.liveReferences);
        }

        List<String> accountableEndpoints = new ArrayList<String> ();
        if (s.connectionBindings != null)
        {
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                String alias = n.getKey ();
                Variable       v = s.find (new Variable (alias + ".$max"));
                if (v == null) v = s.find (new Variable (alias + ".$min"));
                if (v != null) accountableEndpoints.add (alias);
            }
        }

        boolean refcount = s.referenced  &&  s.canDie ();

        // Unit conversions
        Set<ArrayList<EquationSet>> conversions = s.getConversions ();
        for (ArrayList<EquationSet> pair : conversions)
        {
            EquationSet source = pair.get (0);
            EquationSet dest   = pair.get (1);
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
            result.append (pad2 + "void " + mangle (source.name) + "_2_" + mangle (dest.name) + " (" + mangle (source.name) + " * from, Simulator & simulator, int " + mangle ("$type") + ")\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + mangle (dest.name) + " * to = " + mangle (dest.name) + "_Population_Instance->allocate ();\n");
            result.append (pad3 + "simulator.enqueue (to);\n");
            result.append (pad3 + "to->init (simulator);\n");  // may be redundant with the following code ...
            // TODO: Convert contained populations from matching populations in the source part?

            // Match variables between the two sets.
            // TODO: a match between variables should be marked as a dependency. This might change some "dummy" variables into stored values.
            String [] forbiddenAttributes = new String [] {"global", "constant", "accessor", "reference", "temporary", "dummy", "preexistent"};
            for (Variable v : dest.variables)
            {
                if (v.name.equals ("$type"))
                {
                    result.append (pad3 + "to->" + mangle (v) + " = " + mangle ("$type") + ";\n");  // initialize new part with its position in the $type split
                    continue;
                }
                if (v.hasAny (forbiddenAttributes))
                {
                    continue;
                }
                Variable v2 = source.find (v);
                if (v2 != null  &&  v2.equals (v))
                {
                    result.append (pad3 + "to->" + mangle (v) + " = " + resolve (v2.reference, context, false, "from->") + ";\n");
                }
            }
            // Match connection bindings
            if (connectionDest)
            {
                for (Entry<String, EquationSet> c : dest.connectionBindings.entrySet ())
                {
                    String name = c.getKey ();
                    Entry<String, EquationSet> d = source.connectionBindings.floorEntry (name);
                    if (d == null  ||  ! d.getKey ().equals (name)) throw new Exception ("Unfulfilled connection binding during $type change.");
                    result.append (pad3 + "to->" + mangle (name) + " = from->" + mangle (name) + ";\n");
                }
            }

            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit buffers
        if (localStackDerivative.size () > 0)
        {
            result.append (pad2 + "class Derivative\n");
            result.append (pad2 + "{\n");
            result.append (pad2 + "public:\n");
            for (Variable v : localStackDerivative)
            {
                result.append (pad3 + "float " + mangle (v) + ";\n");
            }
            result.append (pad3 + "Derivative * next;\n");
            result.append (pad2 + "};\n");
            result.append ("\n");
        }
        if (localIntegrated.size () > 0)
        {
            result.append (pad2 + "class Integrated\n");
            result.append (pad2 + "{\n");
            result.append (pad2 + "public:\n");
            for (Variable v : localIntegrated)
            {
                result.append (pad3 + "float " + mangle (v) + ";\n");
            }
            result.append (pad3 + "Integrated * next;\n");
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Unit variables
        if (localStackDerivative.size () > 0)
        {
            result.append (pad2 + "Derivative * stackDerivative;\n");
        }
        if (localIntegrated.size () > 0)
        {
            result.append (pad2 + "Integrated * stackIntegrated;\n");
        }
        if (pathToContainer == null)
        {
            if (s.container == null) result.append (pad2 + "Wrapper * container;\n");
            else                     result.append (pad2 + mangle (s.container.name) + " * container;\n");
        }
        if (s.connectionBindings != null)
        {
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                // we should be able to assume that s.container is non-null; ie: a connection should always operate in some larger container
                result.append (pad2 + prefix (s.container, e.getValue ()) + " * " + mangle (e.getKey ()) + ";\n");
            }
        }
        if (s.accountableConnections != null)
        {
            for (EquationSet.AccountableConnection ac : s.accountableConnections)
            {
                result.append (pad2 + "int " + prefix (null, ac.connection, "_") + "_" + mangle (ac.alias) + "_count;\n");
            }
        }
        if (refcount)
        {
            result.append (pad2 + "int refcount;\n");
        }
        for (Variable v : localMembers)
        {
            result.append (pad2 + "float " + mangle (v) + ";\n");
        }
        for (Variable v : localBufferedExternal)
        {
            result.append (pad2 + "float " + mangle ("next_", v) + ";\n");
        }
        result.append ("\n");

        // Unit ctor
        if (localStackDerivative.size () > 0  ||  localIntegrated.size () > 0  ||  ! s.parts.isEmpty ()  ||  s.accountableConnections != null  ||  refcount)
        {
            result.append (pad2 + mangle (s.name) + " ()\n");
            result.append (pad2 + "{\n");
            if (localStackDerivative.size () > 0)
            {
                result.append (pad3 + "stackDerivative = 0;\n");
            }
            if (localIntegrated.size () > 0)
            {
                result.append (pad3 + "stackIntegrated = 0;\n");
            }
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.container = this;\n");
            }
            if (s.accountableConnections != null)
            {
                for (EquationSet.AccountableConnection ac : s.accountableConnections)
                {
                    result.append (pad3 + "int " + prefix (null, ac.connection, "_") + "_" + mangle (ac.alias) + "_count = 0;\n");
                }
            }
            if (refcount)
            {
                result.append (pad3 + "refcount = 0;\n");
            }
            for (Variable v : localMembers)
            {
                result.append (pad3 + mangle (v) + " = 0;\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit dtor
        if (localStackDerivative.size () > 0  ||  localIntegrated.size () > 0)
        {
            result.append (pad2 + "virtual ~" + mangle (s.name) + " ()\n");
            result.append (pad2 + "{\n");
            if (localStackDerivative.size () > 0)
            {
                result.append (pad3 + "while (stackDerivative)\n");
                result.append (pad3 + "{\n");
                result.append (pad4 + "Derivative * temp = stackDerivative;\n");
                result.append (pad4 + "stackDerivative = stackDerivative->next;\n");
                result.append (pad4 + "delete temp;\n");
                result.append (pad3 + "}\n");
            }
            if (localIntegrated.size () > 0)
            {
                result.append (pad3 + "while (stackIntegrated)\n");
                result.append (pad3 + "{\n");
                result.append (pad4 + "Integrated * temp = stackIntegrated;\n");
                result.append (pad4 + "stackIntegrated = stackIntegrated->next;\n");
                result.append (pad4 + "delete temp;\n");
                result.append (pad3 + "}\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit clear
        if (localMembers.size () > 0  ||  localBufferedExternal.size () > 0)
        {
            result.append (pad2 + "virtual void clear ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : localMembers)
            {
                result.append (pad3 + mangle (v) + " = 0;\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit die
        if (s.canDie ())
        {
            result.append (pad2 + "virtual void die ()\n");
            result.append (pad2 + "{\n");

            // tag part as dead
            Variable live = s.find (new Variable ("$live"));
            if (live != null  &&  ! live.hasAny (new String[] {"constant", "accessor"}))  // $live is stored in this part
            {
                result.append (pad3 + resolve (live.reference, context, true) + " = 0;\n");
            }

            // instance counting
            Variable n = s.find (new Variable ("$n"));
            if (n != null) result.append (pad3 + resolve (n.reference, context, false) + "--;\n");  // $n must be an rvalue to avoid getting "next" prefix

            for (String alias : accountableEndpoints)
            {
                result.append (pad3 + mangle (alias) + "->" + prefix (null, s, "_") + "_" + mangle (alias) + "_count--;\n");
            }

            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit enterSimulation
        if (localReference.size () > 0)
        {
            result.append (pad2 + "virtual void enterSimulation ()\n");
            result.append (pad2 + "{\n");
            TreeSet<String> touched = new TreeSet<String> ();  // String rather than EquationSet, because we may have references to several different instances of the same EquationSet, and all must be accounted
            for (VariableReference r : localReference)
            {
                String container = resolveContainer (r, context, "");
                if (touched.add (container)) result.append (pad3 + container + "refcount++;\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit leaveSimulation
        {
            result.append (pad2 + "virtual void leaveSimulation ()\n");
            result.append (pad2 + "{\n");
            String container = "container->";
            if (pathToContainer != null) container = mangle (pathToContainer) + "->" + container;
            result.append (pad3 + container + mangle (s.name) + "_Population_Instance.remove (this);\n");
            TreeSet<String> touched = new TreeSet<String> ();
            for (VariableReference r : localReference)
            {
                container = resolveContainer (r, context, "");
                if (touched.add (container)) result.append (pad3 + container + "refcount--;\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit isFree
        if (refcount)
        {
            result.append (pad2 + "virtual bool isFree ()\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "return refcount == 0;\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit init
        if (s.connectionBindings == null  ||  localInit.size () > 0  ||  s.parts.size () > 0  ||  accountableEndpoints.size () > 0)
        {
            result.append (pad2 + "virtual void init (Simulator & simulator)\n");
            result.append (pad2 + "{\n");
            s.setInit (1);
            for (Variable v : localBufferedExternal)
            {
                result.append (pad3 + mangle ("next_", v) + " = 0;\n");
            }
            // declare buffer variables
            for (Variable v : localBufferedInternal)
            {
                result.append (pad3 + "float " + mangle ("next_", v) + ";\n");
            }
            // $variables
            for (Variable v : localInit)
            {
                if (! v.name.startsWith ("$")) continue;
                if (v.name.equals ("$type")) throw new Exception ("$type must be conditional, and it must never be assigned during init.");
                multiconditional (s, v, context, pad3, result);
            }
            // finalize $variables
            for (Variable v : localBuffered)  // more than just localBufferedInternal, because we must finalize members as well
            {
                if (! v.name.startsWith ("$")) continue;
                if (v.name.equals ("$dt"))
                {
                    result.append (pad3 + "if (" + mangle ("next_", v) + " != simulator.dt) simulator.move (" + mangle ("next_", v) + ");\n");
                }
                else
                {
                    result.append (pad3 + mangle (v) + " = " + mangle ("next_", v) + ";\n");
                }
            }
            // non-$variables
            for (Variable v : localInit)
            {
                if (v.name.startsWith ("$")) continue;
                multiconditional (s, v, context, pad3, result);
            }
            // finalize non-$variables
            for (Variable v : localBuffered)
            {
                if (v.name.startsWith ("$")) continue;
                result.append (pad3 + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }

            // instance counting
            Variable n = s.find (new Variable ("$n"));
            if (n != null) result.append (pad3 + resolve (n.reference, context, false) + "++;\n");

            for (String alias : accountableEndpoints)
            {
                result.append (pad3 + mangle (alias) + "->" + prefix (null, s, "_") + "_" + mangle (alias) + "_count++;\n");
            }

            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.init (simulator);\n");
            }

            s.setInit (0);
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit integrate
        if (localIntegrated.size () > 0  ||  s.parts.size () > 0)
        {
            result.append (pad2 + "virtual void integrate (Simulator & simulator)\n");
            result.append (pad2 + "{\n");
            if (localIntegrated.size () > 0)
            {
                // Note the resolve() call on the left-hand-side below has lvalue==false.
                // Integration always takes place in the primary storage of a variable.
                result.append (pad3 + "if (stackIntegrated)\n");
                result.append (pad3 + "{\n");
                for (Variable v : localIntegrated)
                {
                    Variable vo = s.variables.floor (new Variable (v.name, v.order + 1));  // pre-processing should guarantee that this exists
                    result.append (pad4 + resolve (v.reference, context, false) + " = stackIntegrated->" + mangle (v) + " + " + resolve (vo.reference, context, false) + " * simulator.dt;\n");
                }
                result.append (pad3 + "}\n");
                result.append (pad3 + "else\n");
                result.append (pad3 + "{\n");
                for (Variable v : localIntegrated)
                {
                    Variable vo = s.variables.floor (new Variable (v.name, v.order + 1));
                    result.append (pad4 + resolve (v.reference, context, false) + " += " + resolve (vo.reference, context, false) + " * simulator.dt;\n");
                }
                result.append (pad3 + "}\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.integrate (simulator);\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit prepare
        if (localBufferedExternalWrite.size () > 0  ||  s.parts.size () > 0)
        {
            result.append (pad2 + "virtual void prepare ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : localBufferedExternalWrite)
            {
                result.append (pad3 + mangle ("next_", v) + " = 0;\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.prepare ();\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit update
        if (localUpdate.size () > 0  ||  s.parts.size () > 0)
        {
            result.append (pad2 + "virtual void update (Simulator & simulator)\n");
            result.append (pad2 + "{\n");
            for (Variable v : localBufferedInternalUpdate)
            {
                result.append (pad3 + "float " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : localUpdate)
            {
                multiconditional (s, v, context, pad3, result);
            }
            for (Variable v : localBufferedInternalUpdate)
            {
                result.append (pad3 + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.update (simulator);\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit finalize
        if (localBufferedExternal.size () > 0  ||  type != null  ||  s.canDie ()  ||  s.parts.size () > 0)
        {
            result.append (pad2 + "virtual bool finalize (Simulator & simulator)\n");
            result.append (pad2 + "{\n");

            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.finalize (simulator);\n");  // ignore return value
            }

            Variable live = s.find (new Variable ("$live"));
            if (live != null  &&  ! live.hasAny (new String[] {"constant", "accessor"}))  // $live is stored in this part
            {
                result.append (pad3 + "if (" + resolve (live.reference, context, false) + " == 0) return false;\n");  // early-out if we are already dead, to avoid another call to die()
            }

            for (Variable v : localBufferedExternal)
            {
                if (v.name.equals ("$dt"))
                {
                    result.append (pad3 + "if (" + mangle ("next_", v) + " != simulator.dt)\n");
                    result.append (pad3 + "{\n");
                    result.append (pad4 + "simulator.move (" + mangle ("next_", v) + ");\n");
                    result.append (pad3 + "}\n");
                }
                else
                {
                    result.append (pad3 + mangle (v) + " = " + mangle ("next_", v) + ";\n");
                }
            }

            if (type != null)
            {
                result.append (pad3 + "switch (" + mangle ("$type") + ")\n");
                result.append (pad3 + "{\n");
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

                    result.append (pad4 + "case " + i + ":\n");
                    result.append (pad4 + "{\n");
                    boolean used = false;  // indicates that this instance is one of the resulting parts
                    int countParts = split.size ();
                    for (int j = 0; j < countParts; j++)
                    {
                        EquationSet to = split.get (j);
                        if (to == s  &&  ! used)
                        {
                            used = true;
                            result.append (pad5 + mangle ("$type") + " = " + (j + 1) + ";\n");
                        }
                        else
                        {
                            String container = "container->";
                            if (pathToContainer != null) container = mangle (pathToContainer) + "->" + container;
                            result.append (pad5 + container + mangle (s.name) + "_2_" + mangle (to.name) + " (this, simulator, " + (j + 1) + ");\n");
                        }
                    }
                    if (used)
                    {
                        result.append (pad5 + "break;\n");
                    }
                    else
                    {
                        result.append (pad5 + "die ();\n");
                        result.append (pad5 + "return false;\n");
                    }
                    result.append (pad4 + "}\n");
                }
                result.append (pad3 + "}\n");
            }

            if (s.lethalP)
            {
                Variable p = s.find (new Variable ("$p")); // lethalP implies that $p exists, so no need to check for null
                if (p.hasAttribute ("temporary"))
                {
                    multiconditional (s, p, context, pad3, result);
                }
                result.append (pad3 + "if (" + mangle ("$p") + " == 0  ||  " + mangle ("$p") + " < 1  &&  " + mangle ("$p") + " < randf ())\n");
                result.append (pad3 + "{\n");
                result.append (pad4 + "die ();\n");
                result.append (pad4 + "return false;\n");
                result.append (pad3 + "}\n");
            }

            if (s.lethalConnection)
            {
                for (Entry<String, EquationSet> c : s.connectionBindings.entrySet ())
                {
                	VariableReference r = s.resolveReference (c.getKey () + ".$live");
                	if (! r.variable.hasAttribute ("constant"))
                	{
                        result.append (pad3 + "if (" + resolve (r, context, false) + " == 0)\n");
                        result.append (pad3 + "{\n");
                        result.append (pad4 + "die ();\n");
                        result.append (pad4 + "return false;\n");
                        result.append (pad3 + "}\n");
                	}
                }
            }

            if (s.lethalContainer)
            {
                VariableReference r = s.resolveReference ("$up.$live");
                if (! r.variable.hasAttribute ("constant"))
                {
                    result.append (pad3 + "if (" + resolve (r, context, false) + " == 0)\n");
                    result.append (pad3 + "{\n");
                    result.append (pad4 + "die ();\n");
                    result.append (pad4 + "return false;\n");
                    result.append (pad3 + "}\n");
                }
            }

            result.append (pad3 + "return true;\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit prepareDerivative
        if (localBufferedExternalWriteDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            result.append (pad2 + "virtual void prepareDerivative ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : localBufferedExternalWriteDerivative)
            {
                result.append (pad3 + mangle ("next_", v) + " = 0;\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.prepareDerivative ();\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit updateDerivative
        if (localDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            result.append (pad2 + "virtual void updateDerivative (Simulator & simulator)\n");
            result.append (pad2 + "{\n");
            for (Variable v : localBufferedInternalDerivative)
            {
                result.append (pad3 + "float " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : localDerivative)
            {
                multiconditional (s, v, context, pad3, result);
            }
            for (Variable v : localBufferedInternalDerivative)
            {
                result.append (pad3 + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.updateDerivative (simulator);\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit finalizeDerivative
        if (localBufferedExternalDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            result.append (pad2 + "virtual void finalizeDerivative ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : localBufferedExternalDerivative)
            {
                result.append (pad3 + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.finalizeDerivative ();\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit pushIntegrated
        if (localIntegrated.size () > 0  ||  s.parts.size () > 0)
        {
            result.append (pad2 + "virtual void pushIntegrated ()\n");
            result.append (pad2 + "{\n");
            if (localIntegrated.size () > 0)
            {
                result.append (pad3 + "Integrated * temp = new Integrated;\n");
                result.append (pad3 + "temp->next = stackIntegrated;\n");
                result.append (pad3 + "stackIntegrated = temp;\n");
                for (Variable v : localIntegrated)
                {
                    result.append (pad3 + "temp->" + mangle (v) + " = " + mangle (v) + ";\n");
                }
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.pushIntegrated ();\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit popIntegrated
        if (localIntegrated.size () > 0  ||  s.parts.size () > 0)
        {
            result.append (pad2 + "virtual void popIntegrated ()\n");
            result.append (pad2 + "{\n");
            if (localIntegrated.size () > 0)
            {
                result.append (pad3 + "Integrated * temp = stackIntegrated;\n");
                result.append (pad3 + "stackIntegrated = stackIntegrated->next;\n");
                result.append (pad3 + "delete temp;\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.popIntegrated ();\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit pushDerivative
        if (localStackDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            result.append (pad2 + "virtual void pushDerivative ()\n");
            result.append (pad2 + "{\n");
            if (localStackDerivative.size () > 0)
            {
                result.append (pad3 + "Derivative * temp = new Derivative;\n");
                result.append (pad3 + "temp->next = stackDerivative;\n");
                result.append (pad3 + "stackDerivative = temp;\n");
                for (Variable v : localStackDerivative)
                {
                    result.append (pad3 + "temp->" + mangle (v) + " = " + mangle (v) + ";\n");
                }
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.pushDerivative ();\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit multiplyAddToStack
        if (localStackDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            result.append (pad2 + "virtual void multiplyAddToStack (float scalar)\n");
            result.append (pad2 + "{\n");
            for (Variable v : localStackDerivative)
            {
                result.append (pad3 + "stackDerivative->" + mangle (v) + " += " + mangle (v) + " * scalar;\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.multiplyAddToStack (scalar);\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit multiply
        if (localStackDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            result.append (pad2 + "virtual void multiply (float scalar)\n");
            result.append (pad2 + "{\n");
            for (Variable v : localStackDerivative)
            {
                result.append (pad3 + mangle (v) + " *= scalar;\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.multiply (scalar);\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit addToMembers
        if (localStackDerivative.size () > 0  ||  s.parts.size () > 0)
        {
            result.append (pad2 + "virtual void addToMembers ()\n");
            result.append (pad2 + "{\n");
            if (localStackDerivative.size () > 0)
            {
                for (Variable v : localStackDerivative)
                {
                    result.append (pad3 + mangle (v) + " += stackDerivative->" + mangle (v) + ";\n");
                }
                result.append (pad3 + "Derivative * temp = stackDerivative;\n");
                result.append (pad3 + "stackDerivative = stackDerivative->next;\n");
                result.append (pad3 + "delete temp;\n");
            }
            // contained populations
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + mangle (e.name) + "_Population_Instance.addToMembers ();\n");
            }
            result.append (pad2 + "}\n");
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
                result.append (pad2 + "virtual float getLive ()\n");
                result.append (pad2 + "{\n");
                if (! live.hasAttribute ("accessor"))
                {
                    result.append (pad3 + "if (" + resolve (live.reference, context, false) + " == 0) return 0;\n");
                }
                if (s.lethalConnection)
                {
                    for (Entry<String, EquationSet> c : s.connectionBindings.entrySet ())
                    {
                        VariableReference r = s.resolveReference (c.getKey () + ".$live");
                        if (! r.variable.hasAttribute ("constant"))
                        {
                            result.append (pad3 + "if (" + resolve (r, context, false) + " == 0) return 0;\n");
                        }
                    }
                }
                if (s.lethalContainer)
                {
                    VariableReference r = s.resolveReference ("$up.$live");
                    if (! r.variable.hasAttribute ("constant"))
                    {
                        result.append (pad3 + "if (" + resolve (r, context, false) + " == 0) return 0;\n");
                    }
                }
                result.append (pad3 + "return 1;\n");
                result.append (pad2 + "}\n");
                result.append ("\n");
            }
        }

        // Unit getP
        if (s.connectionBindings != null)
        {
            Variable p = s.find (new Variable ("$p", 0));
            if (p != null)
            {
                result.append (pad2 + "virtual float getP (Simulator & simulator)\n");
                result.append (pad2 + "{\n");

                s.setInit (1);

                // set $live to 0
                Variable live = s.find (new Variable ("$live"));
                Set<String> liveAttributes = live.attributes;
                live.attributes = null;
                live.addAttribute ("constant");
                EquationEntry e = live.equations.first ();  // this should always be an equation we create; the user cannot declare $live (or $init for that matter)
                ASTConstant c = (ASTConstant) e.expression;
                c.setValue (new Float (0.0));

                if (! p.hasAttribute ("constant"))
                {
                    // Generate any temporaries needed by $p
                    for (Variable t : s.variables)
                    {
                        if (t.hasAttribute ("temporary")  &&  p.dependsOn (t) != null)
                        {
                            multiconditional (s, t, context, pad3, result);
                        }
                    }
                    multiconditional (s, p, context, pad3, result);  // $p is always calculated, because we are in a pseudo-init phase
                }
                result.append (pad3 + "return " + resolve (p.reference, context, false) + ";\n");

                // restore $live
                live.attributes = liveAttributes;
                c.setValue (new Float (1.0));

                s.setInit (0);

                result.append (pad2 + "}\n");
                result.append ("\n");
            }
        }

        // Unit getXYZ
        if (s.connectionBindings == null)  // Connections can also have $xyz, but only compartments need to provide an accessor.
        {
            Variable xyz = s.find (new Variable ("$xyz", 0));
            if (xyz != null)
            {
                result.append (pad2 + "virtual void getXYZ (Vector3 & xyz)\n");
                result.append (pad2 + "{\n");
                if (xyz.hasAttribute ("temporary"))
                {
                    // Generate any temporaries needed by $xyz
                    for (Variable t : s.variables)
                    {
                        if (t.hasAttribute ("temporary")  &&  xyz.dependsOn (t) != null)
                        {
                            multiconditional (s, t, context, pad4, result);
                        }
                    }
                    multiconditional (s, xyz, context, pad4, result);
                }
                result.append (pad3 + "xyz = " + resolve (xyz.reference, context, false) + ";\n");  // TODO: resolve() needs to handle vector constants
                result.append (pad2 + "}\n");
                result.append ("\n");
            }
        }

        // Unit project
        if (s.connectionBindings != null)
        {
            boolean hasProjectFrom = false;
            boolean hasProjectTo   = false;
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                if (s.find (new Variable (e.getKey () + ".$projectFrom")) != null) hasProjectFrom = true;
                if (s.find (new Variable (e.getKey () + ".$projectTo"  )) != null) hasProjectTo   = true;
                // TODO: if only one of a pair of projections is present, create the other using point sampling
            }

            if (hasProjectFrom  ||  hasProjectTo)
            {
                Variable xyz = s.find (new Variable ("$xyz", 0));
                boolean xyzStored    = false;
                boolean xyzTemporary = false;
                if (xyz != null)
                {
                    xyzTemporary = xyz.hasAttribute ("temporary");
                    xyzStored = ! xyzTemporary;
                }

                result.append (pad2 + "virtual void project (int i, int j, Vector3 & xyz)\n");
                result.append (pad2 + "{\n");

                String localXYZ = "xyz";
                if (hasProjectTo)
                {
                    localXYZ = "__24xyz";
                    if (! xyzStored) result.append (pad3 + "Vector3 " + mangle (xyz) + ";\n");  // local temporary storage
                }

                // TODO: Handle the case where $xyz is explicitly specified with an equation.
                // This should override all instances of $projectFrom.
                // Or should it merely be the default when $projectFrom is missing?
                result.append (pad3 + "switch (i)\n");
                result.append (pad3 + "{\n");
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
                            result.append (pad4 + "case " + i + ": " + localXYZ + " = " + resolve (fromXYZ, context, false) + "; break;\n");
                        }
                    }
                    else
                    {
                        result.append (pad4 + "case " + i + ":\n");
                        result.append (pad4 + "{\n");
                        if (projectFrom.hasAttribute ("temporary"))  // it could also be "constant", but no other type
                        {
                            for (Variable t : s.variables)
                            {
                                if (t.hasAttribute ("temporary")  &&  projectFrom.dependsOn (t) != null)
                                {
                                    multiconditional (s, t, context, pad5, result);
                                }
                            }
                            multiconditional (s, projectFrom, context, pad5, result);
                        }
                        result.append (pad5 + localXYZ + " = " + resolve (projectFrom.reference, context, false) + ";\n");
                        result.append (pad5 + "break;\n");
                        result.append (pad4 + "}\n");
                    }
                    i++;
                }
                if (needDefault)
                {
                    result.append (pad4 + "default:\n");
                    result.append (pad5 + localXYZ + "[0] = 0;\n");
                    result.append (pad5 + localXYZ + "[1] = 0;\n");
                    result.append (pad5 + localXYZ + "[2] = 0;\n");
                }
                result.append (pad3 + "}\n");
                result.append ("\n");

                if (xyzStored  &&  ! localXYZ.equals ("__24xyz"))
                {
                    result.append (pad3 + "__24xyz = " + localXYZ + ";\n");
                }

                if (hasProjectTo)
                {
                    if (xyzTemporary) xyz.removeAttribute ("temporary");
                    result.append (pad3 + "switch (j)\n");
                    result.append (pad3 + "{\n");
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
                            result.append (pad4 + "case " + j + ":\n");
                            result.append (pad4 + "{\n");
                            if (projectTo.hasAttribute ("temporary"))
                            {
                                for (Variable t : s.variables)
                                {
                                    if (t.hasAttribute ("temporary")  &&  projectTo.dependsOn (t) != null)
                                    {
                                        multiconditional (s, t, context, pad5, result);
                                    }
                                }
                                multiconditional (s, projectTo, context, pad5, result);
                            }
                            result.append (pad5 + "xyz = " + resolve (projectTo.reference, context, false) + ";\n");
                            result.append (pad5 + "break;\n");
                            result.append (pad4 + "}\n");
                        }
                        j++;
                    }
                    if (needDefault)
                    {
                        result.append (pad4 + "default:\n");
                        result.append (pad5 + "xyz = __24xyz;\n");
                    }
                    result.append (pad3 + "}\n");
                    if (xyzTemporary) xyz.addAttribute ("temporary");
                }

                result.append (pad2 + "}\n");
                result.append ("\n");
            }
        }

        // Unit getCount
        if (accountableEndpoints.size () > 0)
        {
            result.append (pad2 + "virtual int getCount (int i)\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "switch (i)\n");
            result.append (pad3 + "{\n");
            int i = 0;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                String alias = n.getKey ();
                if (accountableEndpoints.contains (alias))
                {
                    result.append (pad4 + "case " + i + ": return " + mangle (alias) + "->" + prefix (null, s, "_") + "_" + mangle (alias) + "_count;\n");
                }
                i++;
            }
            result.append (pad3 + "}\n");
            result.append (pad3 + "return 0;\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit setPart
        if (s.connectionBindings != null)
        {
            result.append (pad2 + "virtual void setPart (int i, Part * part)\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "switch (i)\n");
            result.append (pad3 + "{\n");
            int i = 0;
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                // TODO: This assumes that all the parts are children of the same container as the connection. Need to generalize so connections can cross branches of the containment hierarchy.
                result.append (pad4 + "case " + i++ + ": " + mangle (e.getKey ()) + " = (" + prefix (s.container, e.getValue ()) + " *) part; return;\n");
            }
            result.append (pad3 + "}\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit getPart
        if (s.connectionBindings != null)
        {
            result.append (pad2 + "virtual Part * getPart (int i)\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "switch (i)\n");
            result.append (pad3 + "{\n");
            int i = 0;
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                result.append (pad4 + "case " + i++ + ": return " + mangle (e.getKey ()) + ";\n");
            }
            result.append (pad3 + "}\n");
            result.append (pad3 + "return 0;\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit class trailer
        result.append (pad + "};\n");
        result.append ("\n");

        // -------------------------------------------------------------------

        // Population class header
        context.global = true;
        result.append (pad + "class " + mangle (s.name) + "_Population : public ");
        if (s.connectionBindings == null) result.append ("PopulationCompartment\n");
        else                              result.append ("PopulationConnection\n");
        result.append (pad + "{\n");
        result.append (pad + "public:\n");

        // Population buffers
        if (globalStackDerivative.size () > 0)
        {
            result.append (pad2 + "class Derivative\n");
            result.append (pad2 + "{\n");
            result.append (pad2 + "public:\n");
            for (Variable v : globalStackDerivative)
            {
                result.append (pad3 + "float " + mangle (v) + ";\n");
            }
            result.append (pad3 + "Derivative * next;\n");
            result.append (pad2 + "};\n");
            result.append ("\n");
        }
        if (globalIntegrated.size () > 0)
        {
            result.append (pad2 + "class Integrated\n");
            result.append (pad2 + "{\n");
            result.append (pad2 + "public:\n");
            for (Variable v : globalIntegrated)
            {
                result.append (pad3 + "float " + mangle (v) + ";\n");
            }
            result.append (pad3 + "Integrated * next;\n");
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population variables
        if (globalStackDerivative.size () > 0)
        {
            result.append (pad2 + "Derivative * stackDerivative;\n");
        }
        if (globalIntegrated.size () > 0)
        {
            result.append (pad2 + "Integrated * stackIntegrated;\n");
        }
        if (s.container == null) result.append (pad2 + "Wrapper * container;\n");
        else                     result.append (pad2 + mangle (s.container.name) + " * container;\n");
        for (Variable v : globalMembers)
        {
            result.append (pad2 + "float " + mangle (v) + ";\n");
        }
        for (Variable v : globalBufferedExternal)
        {
            result.append (pad2 + "float " + mangle ("next_", v) + ";\n");
        }
        result.append ("\n");

        if (globalStackDerivative.size () > 0  ||  globalIntegrated.size () > 0)
        {
            // Population ctor
            result.append (pad2 + mangle (s.name) + "_Population ()\n");
            result.append (pad2 + "{\n");
            if (globalStackDerivative.size () > 0)
            {
                result.append (pad3 + "stackDerivative = 0;\n");
            }
            if (globalIntegrated.size () > 0)
            {
                result.append (pad3 + "stackIntegrated = 0;\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");

            // Population dtor
            result.append (pad2 + "virtual ~" + mangle (s.name) + "_Population ()\n");
            result.append (pad2 + "{\n");
            if (globalStackDerivative.size () > 0)
            {
                result.append (pad3 + "while (stackDerivative)\n");
                result.append (pad3 + "{\n");
                result.append (pad4 + "Derivative * temp = stackDerivative;\n");
                result.append (pad4 + "stackDerivative = stackDerivative->next;\n");
                result.append (pad4 + "delete temp;\n");
                result.append (pad3 + "}\n");
            }
            if (globalIntegrated.size () > 0)
            {
                result.append (pad3 + "while (stackIntegrated)\n");
                result.append (pad3 + "{\n");
                result.append (pad4 + "Integrated * temp = stackIntegrated;\n");
                result.append (pad4 + "stackIntegrated = stackIntegrated->next;\n");
                result.append (pad4 + "delete temp;\n");
                result.append (pad3 + "}\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Population create
        result.append (pad2 + "virtual Part * create ()\n");
        result.append (pad2 + "{\n");
        result.append (pad3 + mangle (s.name) + " * p = new " + mangle (s.name) + ";\n");
        if (pathToContainer == null) result.append (pad3 + "p->container = container;\n");
        result.append (pad3 + "return p;\n");
        result.append (pad2 + "}\n");
        result.append ("\n");

        // Population getTarget
        if (s.connectionBindings != null)
        {
            result.append (pad2 + "virtual Population * getTarget (int i)\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "switch (i)\n");
            result.append (pad3 + "{\n");
            int i = 0;
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                // TODO: Need a function to permute all descending paths to a class of populations.
                // In the simplest form, it is a peer in our current container, so no iteration at all.
                result.append (pad4 + "case " + i++ + ": return & container->" + mangle (e.getValue ().name) + "_Population_Instance;\n");
            }
            result.append (pad4 + "default: return 0;\n");
            result.append (pad3 + "}\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Population init
        result.append (pad2 + "virtual void init (Simulator & simulator)\n");
        result.append (pad2 + "{\n");
        s.setInit (1);
        //   Zero out members
        for (Variable v : globalMembers)
        {
            result.append (pad3 + mangle (v) + " = 0;\n");
        }
        for (Variable v : globalBufferedExternal)
        {
            result.append (pad3 + mangle ("next_", v) + " = 0;\n");
        }
        //   declare buffer variables
        for (Variable v : globalBufferedInternal)
        {
            result.append (pad3 + "float " + mangle ("next_", v) + ";\n");
        }
        //   no separate $ and non-$ phases, because only $variables work at the population level
        for (Variable v : globalInit)
        {
            multiconditional (s, v, context, pad3, result);
        }
        //   finalize
        for (Variable v : globalBuffered)
        {
            if (! v.name.equals ("$n")) result.append (pad3 + mangle (v) + " = " + mangle ("next_", v) + ";\n");
        }
        //   create instances
        {
            Variable n = s.find (new Variable ("$n", 0));
            if (n != null)
            {
                if (s.connectionBindings != null) throw new Exception ("$n is not applicable to connections");
                if (n.hasAttribute ("constant"))
                {
                    result.append (pad3 + "resize (simulator, " + resolve (n.reference, context, false) + ");\n");
                }
                else
                {
                    result.append (pad3 + "resize (simulator, " + mangle ("next_", n) + ");\n");
                }
            }
        }
        //   make connections
        if (s.connectionBindings != null)
        {
            result.append (pad3 + "simulator.connect (this);\n");  // queue to evaluate our connections
        }
        s.setInit (0);
        result.append (pad2 + "};\n");
        result.append ("\n");

        // Population integrate
        if (globalIntegrated.size () > 0)
        {
            result.append (pad2 + "virtual void integrate (Simulator & simulator)\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "if (stackIntegrated)\n");
            result.append (pad3 + "{\n");
            for (Variable v : globalIntegrated)
            {
                Variable vo = s.variables.floor (new Variable (v.name, v.order + 1));
                result.append (pad4 + resolve (v.reference, context, false) + " = stackIntegrated->" + mangle (v) + " + " + resolve (vo.reference, context, false) + " * simulator.dt;\n");
            }
            result.append (pad3 + "}\n");
            result.append (pad3 + "else\n");
            result.append (pad3 + "{\n");
            for (Variable v : globalIntegrated)
            {
                Variable vo = s.variables.floor (new Variable (v.name, v.order + 1));
                result.append (pad4 + resolve (v.reference, context, false) + " += " + resolve (vo.reference, context, false) + " * simulator.dt;\n");
            }
            result.append (pad3 + "}\n");
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population prepare
        if (globalBufferedExternalWrite.size () > 0)
        {
            result.append (pad2 + "virtual void prepare ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalBufferedExternalWrite)
            {
                result.append (pad3 + mangle ("next_", v) + " = 0;\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population update
        if (globalUpdate.size () > 0)
        {
            result.append (pad2 + "virtual void update (Simulator & simulator)\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalBufferedInternalUpdate)
            {
                result.append (pad3 + "float " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : globalUpdate)
            {
                multiconditional (s, v, context, pad3, result);
            }
            for (Variable v : globalBufferedInternalUpdate)
            {
                result.append (pad3 + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population finalize
        if (globalBufferedExternal.size () > 0  ||  s.container == null)
        {
            result.append (pad2 + "virtual bool finalize (Simulator & simulator)\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalBufferedExternal)
            {
                if (v.name.equals ("$n"))
                {
                    result.append (pad3 + "resize (simulator, " + mangle ("next_", v) + ");\n");  // each new instance will add to $n
                }
                else
                {
                    result.append (pad3 + mangle (v) + " = " + mangle ("next_", v) + ";\n");
                }
            }
            if (s.container == null)  // This is the top-level population
            {
                result.append (pad3 + "writeTrace ();\n");
            }
            result.append (pad3 + "return true;\n");  // Doesn't matter what we return, because the value is always ignored.
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population prepareDerivative
        if (globalBufferedExternalWriteDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void prepareDerivative ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalBufferedExternalWriteDerivative)
            {
                result.append (pad3 + mangle ("next_", v) + " = 0;\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population updateDerivative
        if (globalDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void updateDerivative (Simulator & simulator)\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalBufferedInternalDerivative)
            {
                result.append (pad3 + "float " + mangle ("next_", v) + ";\n");
            }
            for (Variable v : globalDerivative)
            {
                multiconditional (s, v, context, pad3, result);
            }
            for (Variable v : globalBufferedInternalDerivative)
            {
                result.append (pad3 + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population finalizeDerivative
        if (globalBufferedExternalDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void finalizeDerivative ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalBufferedExternalDerivative)
            {
                result.append (pad3 + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population pushIntegrated
        if (globalIntegrated.size () > 0)
        {
            result.append (pad2 + "virtual void pushIntegrated ()\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "Integrated * temp = new Integrated;\n");
            result.append (pad3 + "temp->_next = stackIntegrated;\n");
            result.append (pad3 + "stackIntegrated = temp;\n");
            for (Variable v : globalIntegrated)
            {
                result.append (pad3 + "temp->" + mangle (v) + " = " + mangle (v) + ";\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population popIntegrated
        if (globalIntegrated.size () > 0)
        {
            result.append (pad2 + "virtual void popIntegrated ()\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "Integrated * temp = stackIntegrated;\n");
            result.append (pad3 + "stackIntegrated = stackIntegrated->next;\n");
            result.append (pad3 + "delete temp;\n");
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population pushDerivative
        if (globalStackDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void pushDerivative ()\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "Derivative * temp = new Derivative;\n");
            result.append (pad3 + "temp->_next = stackDerivative;\n");
            result.append (pad3 + "stackDerivative = temp;\n");
            for (Variable v : globalStackDerivative)
            {
                result.append (pad3 + "temp->" + mangle (v) + " = " + mangle (v) + ";\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population multiplyAddToStack
        if (globalStackDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void multiplyAddToStack (float scalar)\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalStackDerivative)
            {
                result.append (pad3 + "stackDerivative->" + mangle (v) + " += " + mangle (v) + " * scalar;\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population multiply
        if (globalStackDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void multiply (float scalar)\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalStackDerivative)
            {
                result.append (pad3 + mangle (v) + " *= scalar;\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population addToMembers
        if (globalStackDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void addToMembers ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalStackDerivative)
            {
                result.append (pad3 + mangle (v) + " += stackDerivative->" + mangle (v) + ";\n");
            }
            result.append (pad3 + "Derivative * temp = stackDerivative;\n");
            result.append (pad3 + "stackDerivative = stackDerivative->next;\n");
            result.append (pad3 + "delete temp;\n");
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

// TODO: study how conditionals interact with getXXX() functions
// start with assumption that all $variables are static, then add dynamics later

        // Population getK
        if (s.connectionBindings != null)
        {
            boolean needK = false;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (n.getKey () + ".$k"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    needK = true;
                    break;
                }
            }
            if (needK)
            {
                result.append (pad2 + "virtual int getK (int i)\n");
                result.append (pad2 + "{\n");
                result.append (pad3 + "switch (i)\n");
                result.append (pad3 + "{\n");
                int i = 0;
                for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
                {
                    Variable v = s.find (new Variable (n.getKey () + ".$k"));
                    EquationEntry e = null;
                    if (v != null) e = v.equations.first ();
                    if (e != null) result.append (pad4 + "case " + i + ": return " + context.render (e.expression) + ";\n");
                    i++;
                }
                result.append (pad3 + "}\n");
                result.append (pad3 + "return 0;\n");
                result.append (pad2 + "}\n");
                result.append ("\n");
            }
        }

        // Population getMax
        if (s.connectionBindings != null)
        {
            boolean needMax = false;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (n.getKey () + ".$max"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    needMax = true;
                    break;
                }
            }
            if (needMax)
            {
                result.append (pad2 + "virtual int getMax (int i)\n");
                result.append (pad2 + "{\n");
                result.append (pad3 + "switch (i)\n");
                result.append (pad3 + "{\n");
                int i = 0;
                for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
                {
                    Variable v = s.find (new Variable (n.getKey () + ".$max"));
                    EquationEntry e = null;
                    if (v != null) e = v.equations.first ();
                    if (e != null) result.append (pad4 + "case " + i + ": return " + context.render (e.expression) + ";\n");
                    i++;
                }
                result.append (pad3 + "}\n");
                result.append (pad3 + "return 0;\n");
                result.append (pad2 + "}\n");
                result.append ("\n");
            }
        }

        // Population getMin
        if (s.connectionBindings != null)
        {
            boolean needMin = false;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (n.getKey () + ".$min"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    needMin = true;
                    break;
                }
            }
            if (needMin)
            {
                result.append (pad2 + "virtual int getMin (int i)\n");
                result.append (pad2 + "{\n");
                result.append (pad3 + "switch (i)\n");
                result.append (pad3 + "{\n");
                int i = 0;
                for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
                {
                    Variable v = s.find (new Variable (n.getKey () + ".$min"));
                    EquationEntry e = null;
                    if (v != null) e = v.equations.first ();
                    if (e != null) result.append (pad4 + "case " + i + ": return " + context.render (e.expression) + ";\n");
                    i++;
                }
                result.append (pad3 + "}\n");
                result.append (pad3 + "return 0;\n");
                result.append (pad2 + "}\n");
                result.append ("\n");
            }
        }

        // Population getRadius
        if (s.connectionBindings != null)
        {
            boolean needRadius = false;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (n.getKey () + ".$radius"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    needRadius = true;
                    break;
                }
            }
            if (needRadius)
            {
                result.append (pad2 + "virtual int getRadius (int i)\n");
                result.append (pad2 + "{\n");
                result.append (pad3 + "switch (i)\n");
                result.append (pad3 + "{\n");
                int i = 0;
                for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
                {
                    Variable v = s.find (new Variable (n.getKey () + ".$radius"));
                    EquationEntry e = null;
                    if (v != null) e = v.equations.first ();
                    if (e != null) result.append (pad4 + "case " + i + ": return " + context.render (e.expression) + ";\n");
                    i++;
                }
                result.append (pad3 + "}\n");
                result.append (pad3 + "return 0;\n");
                result.append (pad2 + "}\n");
                result.append ("\n");
            }
        }

        // Population getNamedValue
        // note: metadata is always at the population level
        if (s.metadata.size () > 0)
        {
            result.append (pad2 + "virtual void getNamedValue (const string & name, string & value)\n");
            result.append (pad2 + "{\n");
            for (Entry<String, String> m : s.metadata.entrySet ())
            {
                result.append (pad3 + "if (name == \"" + m.getKey () + "\")\n");
                result.append (pad4 + "value = \"" + m.getValue () + "\";\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Population class trailer
        result.append (pad + "};\n");
        result.append ("\n");

        // Population instance
        result.append (pad + mangle (s.name) + "_Population " + mangle (s.name) + "_Population_Instance;\n");
        result.append ("\n");

        return result.toString ();
    }

    public void multiconditional (EquationSet s, Variable v, CRenderingContext context, String pad, StringBuilder result) throws Exception
    {
        boolean init = s.getInit ();
        boolean isType = v.name.equals ("$type");

        if (v.hasAttribute ("temporary"))
        {
            result.append (pad + "float " + mangle (v) + ";\n");
        }

        // Select the default equation
        EquationEntry defaultEquation = null;
        for (EquationEntry e : v.equations)
        {
            if (init  &&  e.ifString.equals ("$init"))  // TODO: also handle $init==1, or any other equivalent expression
            {
                defaultEquation = e;
                break;
            }
            if (e.ifString.length () == 0)
            {
                defaultEquation = e;
            }
        }

        // Write the conditional equations
        boolean haveIf = false;
        for (EquationEntry e : v.equations)
        {
            if (e == defaultEquation)
            {
                continue;
            }
            if (init)
            {
                if (e.ifString.length () == 0) continue;
            }
            else  // not init
            {
                if (e.ifString.equals ("$init")) continue;
            }
            if (e.conditional != null)
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
                }
                result.append (pad + ifString + context.render (e.conditional) + ")\n  ");
            }
            if (isType)
            {
                // Set $type to an integer index indicating which of the splits statements in this equation set
                // was actually triggered. During finalize(), this will select a piece of code that implements
                // this particular split. Afterward, $type will be set to an appropriate index within the split,
                // per the N2A language document.
                ArrayList<EquationSet> split = EquationSet.getSplitFrom (e.expression);
                int index = s.splits.indexOf (split);
                result.append (pad + resolve (v.reference, context, true) + " = " + (index + 1) + ";\n");
            }
            else
            {
                if (v.hasAttribute ("dummy"))
                    result.append (pad +                                                                   context.render (e.expression) + ";\n");
                else
                    result.append (pad + resolve (v.reference, context, true) + " " + e.assignment + " " + context.render (e.expression) + ";\n");
            }
        }

        // Write the default equation
        if (defaultEquation == null)
        {
            if (isType)
            {
                if (haveIf) result.append (pad + "else\n  ");
                result.append (pad + resolve (v.reference, context, true) + " = 0;\n");  // always reset $type to 0
            }
            else
            {
                // externalWrite variables already have a default action in the prepare() method, so only deal with cycle and externalRead
                if (v.reference.variable == v  &&  v.equations.size () > 0  &&  v.hasAny (new String[] {"cycle", "externalRead"})  &&  ! v.hasAttribute ("initOnly"))
                {
                    if (haveIf) result.append (pad + "else\n  ");
                    result.append (pad + resolve (v.reference, context, true) + " = " + resolve (v.reference, context, false) + ";\n");  // copy previous value
                }
            }
        }
        else
        {
            if (haveIf) result.append (pad + "else\n  ");
            if (isType)
            {
                ArrayList<EquationSet> split = EquationSet.getSplitFrom (defaultEquation.expression);
                int index = s.splits.indexOf (split);
                result.append (pad + resolve (v.reference, context, true) + " = " + (index + 1) + ";\n");
            }
            else
            {
                if (v.hasAttribute ("dummy"))
                    result.append (pad +                                                                                 context.render (defaultEquation.expression) + ";\n");
                else
                    result.append (pad + resolve (v.reference, context, true) + " " + defaultEquation.assignment + " " + context.render (defaultEquation.expression) + ";\n");
            }
        }
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

    public static String prefix (EquationSet p, EquationSet t)
    {
        return prefix (p, t, "::");
    }

    public static String prefix (EquationSet p, EquationSet t, String separator)
    {
        String result = mangle (t.name);
        while (t != p)
        {
            t = t.container;
            if (t == null) break;
            result = mangle (t.name) + separator + result;
        }
        return result;
    }

    public String resolve (VariableReference r, CRenderingContext context)
    {
        return resolve (r, context, false, "");
    }

    public String resolve (VariableReference r, CRenderingContext context, boolean lvalue)
    {
        return resolve (r, context, lvalue, "");
    }

    /**
        @param v A variable to convert into C++ code that can access it at runtime.
        @param context For the AST rendering system.
        @param lvalue Indicates that this will receive a value assignment. The other case is an rvalue, which will simply be read.
        @param base Injects a pointer at the beginning of the resolution path.
    **/
    public String resolve (VariableReference r, CRenderingContext context, boolean lvalue, String base)
    {
        if (r == null  ||  r.variable == null) return "unresolved";

        if (r.variable.hasAttribute ("constant"))
        {
            EquationEntry e = r.variable.equations.first ();
            return context.render (e.expression);  // should simply render an ASTConstant
        }
        if (r.variable.name.equals ("$dt")  &&  ! lvalue  &&  context.part.getInit ())  // force $dt==0 during init phase
        {
            return "0";
        }

        String containers = resolveContainer (r, context, base);
        String name = "";
        if (r.variable.hasAttribute ("simulator"))
        {
            // We can't read $t or $dt from another object's simulator, unless is exactly the same as ours, in which case there is no point.
            // We can't directly write $dt of another object.
            // TODO: Need a way to tell another part to immediately accelerate
            if (containers.length () > 0) return "unresolved";
            if (! lvalue) return "simulator." + r.variable.name.substring (1);  // strip the $ and expect it to be a member of simulator, which must be passed into the current function
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
            name = mangle (alias) + "->" + prefix (null, r.variable.container, "_") + "_" + mangle (alias) + "_count";
        }
        if (name.length () == 0)
        {
            if (lvalue  &&  r.variable.hasAny (new String[] {"cycle", "externalRead", "externalWrite"}))
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
        Computer a series of pointers to get from current part to r.
        Result does not include the variable name itself.
    **/
    public String resolveContainer (VariableReference r, CRenderingContext context, String base)
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
                        containers += mangle (s.name) + "_Population_Instance.";
                    }
                    else  // descend to an instance of the population.
                    {
                        // Note: we can only descend a chain of singletons, as indexing is now removed from the N2A langauge.
                        // This restriction does not apply to connections, as they have direct pointers to their targets.
                        String typeName = prefix (null, s);  // fully qualified
                        // cast PopulationCompartment.live->after, because it is declared in runtime.h as simply a Compartment,
                        // but we need it to be the specific type of compartment we have generated.
                        containers = "((" + typeName + " *) " + containers + mangle (s.name) + "_Population_Instance.live->after)->";
                    }
                }
                else  // ascend to our container
                {
                    if (current.backendData instanceof String)  // we are a Connection without a container pointer, so we must go through one of our referenced parts
                    {
                        String pathToContainer = (String) current.backendData;
                        containers += mangle (pathToContainer) + "->";
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
            if (current.backendData instanceof String)
            {
                String pathToContainer = (String) current.backendData;
                containers += mangle (pathToContainer) + "->";
            }
            containers += "container->" + mangle (current.name) + "_Population_Instance.";
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

    /**
        Convert "preexistent" $variables that appear to be "constant" into "initOnly",
        so that they will be evaluated during init()
    **/
    public void replaceConstantWithInitOnly (EquationSet s)
    {
        for (EquationSet p : s.parts)
        {
            replaceConstantWithInitOnly (p);
        }

        for (Variable v : s.variables)
        {
            if (   v.order == 0
                && v.name.startsWith ("$")
                && v.hasAttribute ("preexistent")
                && v.hasAttribute ("constant"))
            {
                v.removeAttribute ("constant");
                v.addAttribute    ("initOnly");
            }
        }
    }

    /**
        Tag variables that must be set via a function call so that they have a "next_" value.
    **/
    public void setFunctions (EquationSet s)
    {
        for (EquationSet p : s.parts)
        {
            setFunctions (p);
        }

        for (Variable v : s.variables)
        {
            if (v.order == 0  &&  (v.name.equals ("$n")  ||  v.name.equals ("$dt")))
            {
                if (v.hasAttribute ("initOnly")) v.addAttribute ("cycle");
                else                             v.addAttribute ("externalRead");
            }
        }
    }

    public void findReferences (EquationSet s)
    {
        for (EquationSet p : s.parts)
        {
            findReferences (p);
        }

        if (s.lethalConnection  ||  s.lethalContainer)
        {
            LinkedList<Object>        resolution     = new LinkedList<Object> ();
            NavigableSet<EquationSet> touched        = new TreeSet<EquationSet> ();
            List<VariableReference>   liveReferences = new ArrayList<VariableReference> ();
            findReferences (s, resolution, touched, liveReferences, false);
            if (! (s.backendData instanceof BackendData)) s.backendData = new BackendData ();
            ((BackendData) s.backendData).liveReferences = liveReferences;
        }
    }

    @SuppressWarnings("unchecked")
    public void findReferences (EquationSet s, LinkedList<Object> resolution, NavigableSet<EquationSet> touched, List<VariableReference> liveReferences, boolean terminate)
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
                    liveReferences.add (result);
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
            findReferences (s.container, resolution, touched, liveReferences, true);
            resolution.removeLast ();
        }

        // Recurse into connections
        if (s.lethalConnection)
        {
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                resolution.add (e);
                findReferences (e.getValue (), resolution, touched, liveReferences, true);
                resolution.removeLast ();
            }
        }
    }

    public class BackendData
    {
        public String pathToContainer;
        public List<VariableReference> liveReferences;  ///< references to all $live values that can kill us
    }

    class CRenderingContext extends ASTRenderingContext
    {
        public EquationSet part;
        public boolean global;  ///< Whether this is in the population object (true) or a part object (false)
        public CRenderingContext (EquationSet part)
        {
            super (true);
            this.part = part;
        }
    }

    class VariableMangler implements ASTNodeRenderer
    {
        public String render (ASTNodeBase node, ASTRenderingContext context)
        {
            ASTVarNode vn = (ASTVarNode) node;
            return resolve (vn.reference, (CRenderingContext) context, false);
        }
    }

    class OpSubstituter implements ASTNodeRenderer
    {
        public String render (ASTNodeBase node, ASTRenderingContext context)
        {
            ASTOpNode op = (ASTOpNode) node;
            Class<? extends Function> c = op.getFunction ().getClass ();
            if (c == Power.class)
            {
                return "pow (" + context.render (op.getChild (0)) + ", " + context.render (op.getChild (1)) + ")";
            }
            else
            {
                return op.render (context);
            }
        }
    }

    class ConstantRenderer implements ASTNodeRenderer
    {
        public String render (ASTNodeBase node, ASTRenderingContext context)
        {
            ASTConstant c = (ASTConstant) node;
            Object o = c.getValue ();
            if (o instanceof Float  ||  o instanceof Double  ||  o instanceof BigDecimal)
            {
                return o.toString () + "f";  // Tell c compiler that our type is float, not double. TODO: let user select numeric type of runtime
            }
            else if (o instanceof String)
            {
                return "\"" + o.toString () + "\"";
            }
            // We should only be an explicit integer if the type is allowed by the N2A language at this point.
            return o.toString ();
        }
    }
}
