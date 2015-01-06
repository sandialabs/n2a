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
import gov.sandia.n2a.parsing.functions.ExponentiationFunction;
import gov.sandia.n2a.parsing.functions.Function;
import gov.sandia.n2a.parsing.functions.ListSubscriptExpression;
import gov.sandia.n2a.parsing.gen.ASTConstant;
import gov.sandia.n2a.parsing.gen.ASTNodeBase;
import gov.sandia.n2a.parsing.gen.ASTNodeRenderer;
import gov.sandia.n2a.parsing.gen.ASTOpNode;
import gov.sandia.n2a.parsing.gen.ASTRenderingContext;
import gov.sandia.n2a.parsing.gen.ASTVarNode;
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
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

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
        e.findConstants ();
        e.fillIntegratedVariables ();
        e.findIntegrated ();
        e.addInit ();
        e.resolveLHS ();
        e.resolveRHS ();
        e.removeUnused ();  // especially get rid of unneeded $variables created by addSpecials()
        e.collectSplits ();
        e.findDeath ();
        findPathToContainer (e);
        e.findAccountableConnections ();
        e.findTemporary ();
        e.determineOrder ();
        e.findDerivative ();
        e.findInitOnly ();
        setFunctions (e);
        e.setLiveAttributes ();
        e.addAttribute    ("global",       0, false, new String[] {"$max", "$min", "$k", "$n", "$radius", "$from"});  // population-level variables
        e.addAttribute    ("transient",    1, true,  new String[] {"$p", "$xyz"}); // variables which we prefer to calculate on-the-fly rather than store
        e.addAttribute    ("transient",    1, false, new String[] {"$from"});
        e.addAttribute    ("preexistent", -1, false, new String[] {"$index"});     // variables that already exist, either in the superclass or another class, so they don't require local storage 
        e.addAttribute    ("preexistent",  0, true,  new String[] {"$dt", "$n", "$t"});
        e.addAttribute    ("simulator",    0, true,  new String[] {"$dt", "$t"});  // variables that live in the simulator object
        e.removeAttribute ("constant",     0, true,  new String[] {"$dt", "$n", "$t"});  // simple assignment to a $ variable makes it look like a constant
        e.setInit (false);
        System.out.println (e.flatList (true));

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
        s.append ("class Wrapper\n");
        s.append ("{\n");
        s.append ("public:\n");
        s.append (generateClasses (e, "  "));
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
        s.append ("    simulator.enqueue (&wrapper._Model_Population_Instance);\n");
        s.append ("    wrapper._Model_Population_Instance.init (simulator);\n");
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
        for (EquationSet p : s.parts)
        {
            result.append (generateClasses (p, pad2));
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
        for (Variable v : s.ordered)  // we want the sub-lists to be ordered correctly
        {
            System.out.println (v.nameString () + " " + v.attributeString ());
            if (v.name.equals ("$type")) type = v;
            if (v.hasAttribute ("global"))
            {
                if (! v.hasAny (new String[] {"constant", "transient"}))
                {
                    boolean initOnly = v.hasAttribute ("initOnly");
                    if (! initOnly) globalUpdate.add (v);
                    boolean derivativeOrDependency = v.hasAttribute ("derivativeOrDependency");
                    if (derivativeOrDependency) globalDerivative.add (v);
                    if (! v.hasAttribute ("reference"))
                    {
                        globalInit.add (v);
                        if (! v.hasAttribute ("temporary"))
                        {
                            if (! v.hasAny (new String [] {"preexistent", "output"})) globalMembers.add (v);
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
                if (! v.hasAny (new String[] {"constant", "transient"}))
                {
                    boolean initOnly = v.hasAttribute ("initOnly");
                    if (! initOnly) localUpdate.add (v);
                    boolean derivativeOrDependency = v.hasAttribute ("derivativeOrDependency");
                    if (derivativeOrDependency) localDerivative.add (v);
                    if (! v.hasAttribute ("reference"))
                    {
                        if (! v.name.equals ("$index")) localInit.add (v);
                        if (! v.hasAttribute ("temporary"))
                        {
                            if (! v.hasAny (new String [] {"preexistent", "output"})) localMembers.add (v);
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

        // Determine how to access my container at run time
        EquationSet pathToContainer = null;
        if (s.backendData instanceof EquationSet) pathToContainer = (EquationSet) s.backendData;

        // Determine reference population
        // This is extremely inefficient, but we are solving a very small problem
        List<String> from = new ArrayList<String> ();
        if (s.connectionBindings != null)
        {
            Variable v = s.find (new Variable ("$from"));
            if (v != null)
            {
                EquationEntry e = v.equations.first ();
                if (e != null  &&  e.expression != null)
                {
                    // TODO: load "from" with list in $from
                }
            }
            // Assign positions to any remaining references
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                if (! from.contains (n.getKey ())) from.add (n.getKey ());
            }
        }
        
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
            // TODO: a match between variables should be marked as a dependency. This might change some "output" variables into stored values.
            String [] forbiddenAttributes = new String [] {"global", "constant", "transient", "reference", "temporary", "output", "preexistent"};
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
            for (EquationSet e : s.accountableConnections)
            {
                result.append (pad2 + "int " + prefix (s.container, e, "_") + "_count;\n");
            }
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
        if (localStackDerivative.size () > 0  ||  localIntegrated.size () > 0  ||  ! s.parts.isEmpty ()  ||  s.accountableConnections != null)
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
                for (EquationSet e : s.accountableConnections)
                {
                    result.append (pad3 + prefix (s.container, e, "_") + "_count = 0;\n");
                }
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

        // Unit die
        if (s.canDie ())
        {
            result.append (pad2 + "virtual void die ()\n");
            result.append (pad2 + "{\n");

            // tag part as dead
            Variable live = s.find (new Variable ("$live"));
            if (live != null  &&  ! live.hasAny (new String[] {"constant", "transient"}))  // $live is stored in this part
            {
                result.append (pad3 + resolve (live.reference, context, true) + " = 0;\n");
            }

            // instance counting
            Variable n = s.find (new Variable ("$n"));
            if (n != null) result.append (pad3 + resolve (n.reference, context, false) + "--;\n");  // $n must be an rvalue to avoid getting "next" prefix

            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit enqueue

        // Unit dequeue
        {
            result.append (pad2 + "virtual void dequeue ()\n");
            result.append (pad2 + "{\n");
            String container = "container->";
            if (pathToContainer != null) container = mangle (pathToContainer.name) + "->" + container;
            result.append (pad3 + container + mangle (s.name) + "_Population_Instance.remove (this);\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit isFree

        // Unit init
        if (s.connectionBindings == null  ||  localInit.size () > 0)
        {
            result.append (pad2 + "virtual void init (Simulator & simulator)\n");
            result.append (pad2 + "{\n");
            s.setInit (true);
            // Zero out members
            for (Variable v : localMembers)
            {
                result.append (pad3 + mangle (v) + " = 0;\n");
            }
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

            // add contained populations
            for (EquationSet e : s.parts)
            {
                String PopulationInstance = mangle (e.name) + "_Population_Instance"; 
                result.append (pad3 + "simulator.enqueue (&" + PopulationInstance + ");\n");
                result.append (pad3 + PopulationInstance + ".init (simulator);\n");
            }

            s.setInit (false);
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit integrate
        if (localIntegrated.size () > 0)
        {
            result.append (pad2 + "virtual void integrate (Simulator & simulator)\n");
            result.append (pad2 + "{\n");
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
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit prepare
        if (localBufferedExternalWrite.size () > 0)
        {
            result.append (pad2 + "virtual void prepare ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : localBufferedExternalWrite)
            {
                result.append (pad3 + mangle ("next_", v) + " = 0;\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit update
        if (localUpdate.size () > 0)
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
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit finalize
        if (localBufferedExternal.size () > 0  ||  type != null  ||  s.canDie ())
        {
            result.append (pad2 + "virtual bool finalize (Simulator & simulator)\n");
            result.append (pad2 + "{\n");
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
                            if (pathToContainer != null) container = mangle (pathToContainer.name) + "->" + container;
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
                        result.append (pad5 + "return 0;\n");
                    }
                    result.append (pad4 + "}\n");
                }
                result.append (pad3 + "}\n");
            }

            if (s.lethalP)
            {
                // TODO: be more clever about determining lethaP; current version thinks HHmodHHmod has lethalP because of expression involving $index
                Variable p = s.find (new Variable ("$p")); // lethalP implies that $p exists, so no need to check for null
                result.append (pad3 + "float create = " + resolve (p.reference, context, false) + ";\n");
                result.append (pad3 + "if (create == 0  ||  create < 1  &&  create < randf ())\n");
                result.append (pad3 + "{\n");
                result.append (pad4 + "die ();\n");
                result.append (pad4 + "return 0;\n");
                result.append (pad3 + "}\n");
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
                result.append (pad3 + "return " + resolve (r, context, false) + ";\n");
            }
            else
            {
                result.append (pad3 + "return true;\n");
            }

            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit prepareDerivative
        if (localBufferedExternalWriteDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void prepareDerivative ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : localBufferedExternalWriteDerivative)
            {
                result.append (pad3 + mangle ("next_", v) + " = 0;\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit updateDerivative
        if (localDerivative.size () > 0)
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
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit finalizeDerivative
        if (localBufferedExternalDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void finalizeDerivative ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : localBufferedExternalDerivative)
            {
                result.append (pad3 + mangle (v) + " = " + mangle ("next_", v) + ";\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit pushIntegrated
        if (localIntegrated.size () > 0)
        {
            result.append (pad2 + "virtual void pushIntegrated ()\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "Integrated * temp = new Integrated;\n");
            result.append (pad3 + "temp->next = stackIntegrated;\n");
            result.append (pad3 + "stackIntegrated = temp;\n");
            for (Variable v : localIntegrated)
            {
                result.append (pad3 + "temp->" + mangle (v) + " = " + mangle (v) + ";\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit popIntegrated
        if (localIntegrated.size () > 0)
        {
            result.append (pad2 + "virtual void popIntegrated ()\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "Integrated * temp = stackIntegrated;\n");
            result.append (pad3 + "stackIntegrated = stackIntegrated->next;\n");
            result.append (pad3 + "delete temp;\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit pushDerivative
        if (localStackDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void pushDerivative ()\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "Derivative * temp = new Derivative;\n");
            result.append (pad3 + "temp->next = stackDerivative;\n");
            result.append (pad3 + "stackDerivative = temp;\n");
            for (Variable v : localStackDerivative)
            {
                result.append (pad3 + "temp->" + mangle (v) + " = " + mangle (v) + ";\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit multiplyAddToStack
        if (localStackDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void multiplyAddToStack (float scalar)\n");
            result.append (pad2 + "{\n");
            for (Variable v : localStackDerivative)
            {
                result.append (pad3 + "stackDerivative->" + mangle (v) + " += " + mangle (v) + " * scalar;\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit multiply
        if (localStackDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void multiply (float scalar)\n");
            result.append (pad2 + "{\n");
            for (Variable v : localStackDerivative)
            {
                result.append (pad3 + mangle (v) + " *= scalar;\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit addToMembers
        if (localStackDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void addToMembers ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : localStackDerivative)
            {
                result.append (pad3 + mangle (v) + " += stackDerivative->" + mangle (v) + ";\n");
            }
            result.append (pad3 + "Derivative * temp = stackDerivative;\n");
            result.append (pad3 + "stackDerivative = stackDerivative->next;\n");
            result.append (pad3 + "delete temp;\n");
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
            if (live != null  &&  live.hasUsers)
            {
                result.append (pad2 + "virtual float getLive ()\n");
                result.append (pad2 + "{\n");
                if (live.hasAttribute ("transient"))
                {
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
                        result.append (pad3 + "return " + resolve (r, context, false) + ";\n");
                    }
                    else
                    {
                        result.append (pad3 + "return 1;\n");
                    }
                }
                else  // stored somewhere  (We are unlikely to be "constant" if hasUsers is true.)
                {
                    result.append (pad3 + "return " + resolve (live.reference, context, false) + ";\n");
                }
                result.append (pad2 + "}\n");
                result.append ("\n");
            }
        }

        // Unit getP
        {
            Variable p = s.find (new Variable ("$p", 0));
            if (p != null)
            {
                result.append (pad2 + "virtual float getP (float " + mangle ("$live") + ")\n");
                result.append (pad2 + "{\n");

                if (p.hasAttribute ("constant"))
                {
                    result.append (pad3 + "return " + resolve (p.reference, context, false) + ";\n");
                }
                else
                {
                    Variable init = s.find (new Variable ("$init"));
                    Variable live = s.find (new Variable ("$live"));

                    Set<String> liveAttributes = live.attributes;
                    live.attributes = null;
                    live.addAttribute ("preexistent");

                    if (p.dependsOn (init) != null)
                    {
                        result.append (pad3 + "float " + mangle (init) + " = 1.0f - " + mangle (live) + ";\n");
                    }

                    String whichPad;
                    boolean hasTransient = p.hasAttribute ("transient");
                    if (hasTransient)
                    {
                        result.append (pad3 + "float " + mangle (p) + " = 1;\n");  // TODO: eliminate this assignment; there should be a default values assigned by the multiconditional below
                        p.removeAttribute ("transient");
                        init.addAttribute ("preexistent");
                        init.removeAttribute ("constant");
                        whichPad = pad3;
                    }
                    else
                    {
                        result.append (pad3 + "if (" + mangle ("$live") + " == 0)\n");
                        result.append (pad3 + "{\n");
                        s.setInit (true);
                        whichPad = pad4;
                    }

                    // Generate any temporaries needed by $p
                    for (Variable t : s.variables)
                    {
                        if (t.hasAttribute ("temporary")  &&  p.dependsOn (t) != null)
                        {
                            multiconditional (s, t, context, whichPad, result);
                        }
                    }
                    multiconditional (s, p, context, whichPad, result);

                    if (hasTransient)
                    {
                        p.addAttribute ("transient");
                        init.removeAttribute ("preexistent");
                        init.addAttribute ("constant");
                    }
                    else  // stored in object ($variables always have defaults and therefore never resolve up to container)
                    {
                        s.setInit (false);
                        result.append (pad3 + "}\n");
                    }

                    live.attributes = liveAttributes;

                    result.append (pad3 + "return " + mangle (p) + ";\n");
                }
                result.append (pad2 + "}\n");
                result.append ("\n");
            }
        }

        // Unit getXYZ
        {
            Variable xyz = s.find (new Variable ("$xyz", 0));
            if (xyz != null  ||  s.connectionBindings != null)
            {
                result.append (pad2 + "virtual void getXYZ (float " + mangle ("$live") + ", Vector3 " + mangle ("xyz") + ")\n");
                result.append (pad2 + "{\n");
                if (xyz == null)  // This must therefore be a Connection, so we defer $xyz to our reference part.
                {
                    result.append (pad3 + mangle (from.get (0)) + "->getXYZ (" + mangle ("$live") + ", " + mangle ("xyz") + ");\n");
                }
                else
                {
                    if (xyz.hasAttribute ("constant"))
                    {
                        // TODO: either modify resolve() to set Vector3 constants correctly, or fix this code
                        result.append (pad3 + resolve (xyz.reference, context, false) + ";\n");
                    }
                    else
                    {
                        Variable init = s.find (new Variable ("$init"));
                        Variable live = s.find (new Variable ("$live"));

                        Set<String> liveAttributes = live.attributes;
                        live.attributes = null;
                        live.addAttribute ("preexistent");

                        if (xyz.dependsOn (init) != null)
                        {
                            result.append (pad3 + "float " + mangle (init) + " = 1.0f - " + mangle (live) + ";\n");
                        }

                        boolean hasTransient = xyz.hasAttribute ("transient");
                        if (hasTransient)
                        {
                            xyz.removeAttribute ("transient");
                            init.addAttribute ("preexistent");
                            init.removeAttribute ("constant");
                        }
                        else
                        {
                            result.append (pad3 + "if (" + mangle ("$live") + " == 0)\n");
                            result.append (pad3 + "{\n");
                            s.setInit (true);
                        }

                        // Generate any temporaries needed by $xyz
                        for (Variable t : s.variables)
                        {
                            if (t.hasAttribute ("temporary")  &&  xyz.dependsOn (t) != null)
                            {
                                multiconditional (s, t, context, pad4, result);
                            }
                        }
                        multiconditional (s, xyz, context, pad4, result);

                        if (hasTransient)
                        {
                            xyz.addAttribute ("transient");
                            init.addAttribute ("constant");
                            init.removeAttribute ("preexistent");
                        }
                        else
                        {
                            s.setInit (false);
                            result.append (pad3 + "}\n");
                        }

                        live.attributes = liveAttributes;
                    }
                }
                result.append (pad2 + "}\n");
                result.append ("\n");
            }
        }

        // Unit getPart and setPart
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

            result.append (pad2 + "virtual Part * getPart (int i)\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "switch (i)\n");
            result.append (pad3 + "{\n");
            i = 0;
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
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                // TODO: Need a function to permute all descending paths to a class of populations.
                // In the simplest form, it is a peer in our current container, so no iteration at all.
                result.append (pad4 + "case " + from.indexOf (e.getKey ()) + ": return & container->" + mangle (e.getValue ().name) + "_Population_Instance;\n");
            }
            result.append (pad4 + "default: return 0;\n");
            result.append (pad3 + "}\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Population init
        result.append (pad2 + "virtual void init (Simulator & simulator)\n");
        result.append (pad2 + "{\n");
        s.setInit (true);
        // Zero out members
        for (Variable v : globalMembers)
        {
            result.append (pad3 + mangle (v) + " = 0;\n");
        }
        for (Variable v : globalBufferedExternal)
        {
            result.append (pad3 + mangle ("next_", v) + " = 0;\n");
        }
        // declare buffer variables
        for (Variable v : globalBufferedInternal)
        {
            result.append (pad3 + "float " + mangle ("next_", v) + ";\n");
        }
        // no separate $ and non-$ phases, because only $variables work at the population level
        for (Variable v : globalInit)
        {
            multiconditional (s, v, context, pad3, result);
        }
        // finalize
        for (Variable v : globalBuffered)
        {
            if (! v.name.equals ("$n")) result.append (pad3 + mangle (v) + " = " + mangle ("next_", v) + ";\n");
        }
        // create instances
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
        // make connections
        if (s.connectionBindings != null)
        {
            result.append (pad3 + "simulator.connect (this);\n");  // queue to evaluate our connections
        }
        s.setInit (false);
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
        if (globalBufferedExternal.size () > 0  ||  s.container == null  ||  s.lethalContainer)
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
            if (s.lethalContainer)
            {
                VariableReference r = s.resolveReference ("$up.$live");
                result.append (pad3 + "return " + resolve (r, context, false) + ";\n");
            }
            else
            {
                result.append (pad3 + "return true;\n");
            }
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
// if a $variable is dynamic, it ceases to be transitory, and requires different code; should test for this in each getXXX() generator
// need a more in-depth test for dynamic in EquationSet::findDynamic()

        // Population getK
        if (s.connectionBindings != null)
        {
            Variable v = s.find (new Variable ("$k"));
            if (v != null)
            {
                EquationEntry e = v.equations.first ();
                if (e != null)
                {
                    result.append (pad2 + "virtual int getK ()\n");
                    result.append (pad2 + "{\n");
                    result.append (pad3 + "return " + context.render (e.expression) + ";\n");
                    result.append (pad2 + "}\n");
                    result.append ("\n");
                }
            }
        }

        // Population getLive
        if (s.connectionBindings != null)
        {
            result.append (pad2 + "virtual float getLive ()\n");
            result.append (pad2 + "{\n");
            if (s.container == null) result.append (pad3 + "return 1;\n");  // TODO: Not strictly accurate for top-level population. Our finalize() should return 0 when the last part (usually a singleton) dies.
            else                     result.append (pad3 + "return container->getLive ();\n");  // TODO: should use resolve()
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Population getMax
        if (s.connectionBindings != null)
        {
            result.append (pad2 + "virtual int getMax (int i)\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "switch (i)\n");
            result.append (pad3 + "{\n");
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (n.getKey () + ".$max"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null) result.append (pad4 + "case " + from.indexOf (n.getKey ()) + ": return " + context.render (e.expression) + ";\n");
            }
            result.append (pad3 + "}\n");
            result.append (pad3 + "return 0;\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Population getMin
        if (s.connectionBindings != null)
        {
            result.append (pad2 + "virtual int getMin (int i)\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "switch (i)\n");
            result.append (pad3 + "{\n");
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                Variable v = s.find (new Variable (n.getKey () + ".$min"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null) result.append (pad4 + "case " + from.indexOf (n.getKey ()) + ": return " + context.render (e.expression) + ";\n");
            }
            result.append (pad3 + "}\n");
            result.append (pad3 + "return 0;\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Population getRadius
        if (s.connectionBindings != null)
        {
            Variable v = s.find (new Variable ("$radius"));
            if (v != null)
            {
                EquationEntry e = v.equations.first ();
                if (e != null)
                {
                    result.append (pad2 + "virtual int getRadius ()\n");
                    result.append (pad2 + "{\n");
                    result.append (pad3 + "return " + context.render (e.expression) + ";\n");
                    result.append (pad2 + "}\n");
                    result.append ("\n");
                }
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
        boolean initConstant = s.find (new Variable ("$init")).hasAttribute ("constant");
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
            if (initConstant  &&  init  &&  e.ifString.equals ("$init"))
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
            if (initConstant)
            {
                if (init)
                {
                    if (e.ifString.length () == 0) continue;
                }
                else  // not init
                {
                    if (e.ifString.equals ("$init")) continue;
                }
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
                if (v.hasAttribute ("output"))
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
                if (v.hasAttribute ("output"))
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

        // Compute series of pointers to get to v.reference
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
                    else  // descend to an instance of the population
                    {
                        // similar to prefix(), but goes all the way up containment hierarchy
                        String typeName = mangle (s.name);
                        EquationSet p = s.container;
                        while (p != null)
                        {
                            typeName = mangle (p.name) + "::" + typeName;
                            p = p.container;
                        }

                        // cast PopulationCompartment.live->after, because it is declared in runtime.h as simply a Compartment,
                        // but we need it to be the specific type of compartment we have generated.
                        containers = "((" + typeName + " *) " + containers + mangle (s.name) + "_Population_Instance.live->after)->";
                    }
                }
                else  // ascend to our container
                {
                    if (current.backendData instanceof EquationSet)  // we are a Connection without a container pointer, so we must go through one of our referenced parts
                    {
                        EquationSet pathToContainer = (EquationSet) current.backendData;
                        containers += mangle (pathToContainer.name) + "->";
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
            if (current.backendData instanceof EquationSet)
            {
                EquationSet pathToContainer = (EquationSet) current.backendData;
                containers += mangle (pathToContainer.name) + "->";
            }
            containers += "container->" + mangle (current.name) + "_Population_Instance.";
        }

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
        if (r.variable.hasAttribute ("transient"))
        {
            if (lvalue) return "unresolved";

            // TODO: Change the interface to these functions
            if      (r.variable.name.equals ("$p"  )) name = "getP (1)";  // TODO: get actual value of $live
            else if (r.variable.name.equals ("$xyz")) name = "getXYZ ()";
            else if (r.variable.name.equals ("$ref")) name = "getRef ()";
            else return "unresolved";
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

    public void findPathToContainer (EquationSet s)
    {
        for (EquationSet p : s.parts)
        {
            findPathToContainer (p);
        }

        s.backendData = null;
        if (s.connectionBindings != null  &&  s.lethalContainer)  // and therefore needs to check its container
        {
            for (Entry<String, EquationSet> c : s.connectionBindings.entrySet ())
            {
                if (c.getValue ().container == s.container)
                {
                    s.backendData = c.getValue ();
                    break;
                }
            }
        }
    }

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
                // The idea is to force variables that get set via a function call to use a "next" value.
                if (v.hasAttribute ("initOnly")) v.addAttribute ("cycle");
                else                             v.addAttribute ("externalRead");
            }
        }
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
            if (c == ExponentiationFunction.class)
            {
                return "pow (" + context.render (op.getChild (0)) + ", " + context.render (op.getChild (1)) + ")";
            }
            else if (c == ListSubscriptExpression.class)
            {
                return context.render (op.getChild (0));  // suppress subscripts
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
