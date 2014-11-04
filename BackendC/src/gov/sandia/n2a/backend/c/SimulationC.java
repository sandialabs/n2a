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
        e.addSpecials ();  // $dt, $index, $init, $t, $type
        e.findConstants ();
        e.fillIntegratedVariables ();
        e.findIntegrated ();
        e.addInit ();
        e.resolveLHS ();
        e.resolveRHS ();
        e.removeUnused ();  // especially get rid of unneeded $variables created by addSpecials()
        e.collectSplits ();
        e.findTemporary ();
        e.determineOrder ();
        e.findDerivative ();
        e.addAttribute    ("global",       0, false, new String[] {"$max", "$min", "$k", "$n", "$radius", "$ref"});
        e.addAttribute    ("transient",    1, true,  new String[] {"$p", "$ref", "$xyz"});
        e.addAttribute    ("preexistent",  0, true,  new String[] {"$dt", "$index", "$t"});
        e.removeAttribute ("constant",     0, true,  new String[] {"$dt"});
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
        s.append ("\n");

        s.append (generateClasses (e, ""));

        // Main
        s.append ("int main (int argc, char * argv[])\n");
        s.append ("{\n");
        String integrator = e.metadata.get ("c.integrator");
        if (integrator == null)
        {
            integrator = "Euler";
        }
        if (integrator.equals ("RungeKutta"))
        {
            s.append ("  _RungeKutta simulator;\n");
        }
        else
        {
            s.append ("  _Euler simulator;\n");
        }
        s.append ("  simulator.populations.push_back (&Model_Population_Instance);\n");
        s.append ("  Model_Population_Instance.init (0, simulator.dt);\n");
        s.append ("  simulator.run ();\n");
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
        result.append (pad + "class " + mangle (s.name) + " : public " + (s.connectionBindings == null ? "_Compartment" : "_Connection") + "\n");
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
        context.add (ASTConstant.class, new FloatRenderer ());

        // Separate variables into logically useful lists
        List<Variable> local                         = new ArrayList<Variable> ();
        List<Variable> localInit                     = new ArrayList<Variable> ();
        List<Variable> localMembers                  = new ArrayList<Variable> ();
        List<Variable> localStackDerivative          = new ArrayList<Variable> ();
        List<Variable> localBuffered                 = new ArrayList<Variable> ();  // need buffering
        List<Variable> localBufferedInternal         = new ArrayList<Variable> ();  // subset of buffered that are due to dependencies strictly within the current equation-set
        List<Variable> localBufferedDerivative       = new ArrayList<Variable> ();  // subset of buffered internal that are derivatives or their dependencies
        List<Variable> localExternal                 = new ArrayList<Variable> ();  // subset of buffered that are due to some external access
        List<Variable> localExternalDerivative       = new ArrayList<Variable> ();  // subset of external that are derivatives
        List<Variable> localExternalWrite            = new ArrayList<Variable> ();  // subset of external that are due to external write
        List<Variable> localExternalWriteDerivative  = new ArrayList<Variable> ();  // subset of external write that are derivatives
        List<Variable> localIntegrated               = new ArrayList<Variable> ();
        List<Variable> localDerivative               = new ArrayList<Variable> ();
        List<Variable> global                        = new ArrayList<Variable> ();
        List<Variable> globalInit                    = new ArrayList<Variable> ();
        List<Variable> globalMembers                 = new ArrayList<Variable> ();
        List<Variable> globalStackDerivative         = new ArrayList<Variable> ();
        List<Variable> globalBuffered                = new ArrayList<Variable> ();
        List<Variable> globalBufferedInternal        = new ArrayList<Variable> ();
        List<Variable> globalBufferedDerivative      = new ArrayList<Variable> ();
        List<Variable> globalExternal                = new ArrayList<Variable> ();
        List<Variable> globalExternalDerivative      = new ArrayList<Variable> ();
        List<Variable> globalExternalWrite           = new ArrayList<Variable> ();
        List<Variable> globalExternalWriteDerivative = new ArrayList<Variable> ();
        List<Variable> globalIntegrated              = new ArrayList<Variable> ();
        List<Variable> globalDerivative              = new ArrayList<Variable> ();
        Variable type = null;  // type is always a local (instance) variable; a population *is* a type, so it can't change itself!
        boolean hasOutputs = false;
        for (Variable v : s.ordered)  // we want the sub-lists to be ordered correctly
        {
            System.out.println (v.nameString () + " " + v.attributeString ());
            if (v.hasAttribute ("output"))
            {
                hasOutputs = true;
            }
            if (v.name.equals ("$type"))
            {
                type = v;
            }
            if (v.hasAttribute ("global"))
            {
                if (! v.hasAny (new String[] {"constant", "transient"}))
                {
                    global.add (v);
                    boolean derivativeOrDependency = v.hasAttribute ("derivativeOrDependency");
                    if (derivativeOrDependency)
                    {
                        globalDerivative.add (v);
                    }
                    if (! v.hasAttribute ("reference"))
                    {
                        globalInit.add (v);
                        if (! v.hasAny (new String[] {"temporary", "output"}))
                        {
                            globalMembers.add (v);
                            if (v.order > 0)
                            {
                                globalStackDerivative.add (v);
                            }
    
                            boolean external = false;
                            if (v.hasAttribute ("externalWrite"))
                            {
                                external = true;
                                globalExternalWrite.add (v);
                                if (derivativeOrDependency)
                                {
                                    globalExternalWriteDerivative.add (v);
                                }
                            }
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  v.equations.size () > 0))
                            {
                                external = true;
                                globalExternal.add (v);
                                if (derivativeOrDependency)
                                {
                                    globalExternalDerivative.add (v);
                                }
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                globalBuffered.add (v);
                                if (! external)
                                {
                                    globalBufferedInternal.add (v);
                                    if (derivativeOrDependency)
                                    {
                                        globalBufferedDerivative.add (v);
                                    }
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
                    local.add (v);
                    boolean derivativeOrDependency = v.hasAttribute ("derivativeOrDependency");
                    if (derivativeOrDependency)
                    {
                        localDerivative.add (v);
                    }
                    if (! v.hasAttribute ("reference"))
                    {
                        if (! v.name.equals ("$index"))
                        {
                            localInit.add (v);
                        }
                        if (! v.hasAny (new String[] {"temporary", "output"}))
                        {
                            if (! v.hasAttribute ("preexistent"))
                            {
                                localMembers.add (v);
                            }
                            if (v.order > 0)
                            {
                                localStackDerivative.add (v);
                            }
    
                            boolean external = false;
                            if (v.hasAttribute ("externalWrite"))
                            {
                                external = true;
                                localExternalWrite.add (v);
                                if (derivativeOrDependency)
                                {
                                    localExternalWriteDerivative.add (v);
                                }
                            }
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  v.equations.size () > 0))
                            {
                                external = true;
                                localExternal.add (v);
                                if (derivativeOrDependency)
                                {
                                    localExternalDerivative.add (v);
                                }
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                localBuffered.add (v);
                                if (! external)
                                {
                                    localBufferedInternal.add (v);
                                    if (derivativeOrDependency)
                                    {
                                        localBufferedDerivative.add (v);
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

        // Unit conversions
        Set<ArrayList<EquationSet>> conversions = s.getConversions ();
        for (ArrayList<EquationSet> pair : conversions)
        {
            EquationSet from = pair.get (0);
            EquationSet to   = pair.get (1);
            boolean connectionFrom = from.connectionBindings != null;
            boolean connectionTo   = to  .connectionBindings != null;
            if (connectionFrom != connectionTo)
            {
                throw new Exception ("Can't change $type between connection and non-connection.");
                // Why not? Because a connection *must* know the instances it connects, while
                // a compartment cannot know those instances. Thus, one can never be converted
                // to the other.
            }
            result.append (pad2 + "void " + from.name + "_2_" + to.name + " (" + from.name + " * _from, float " + mangle ("$t") + ", float & " + mangle ("$dt") + ", int " + mangle ("$type") + ")\n");  // these functions only have local meaning, so they are never virtual
            result.append (pad2 + "{\n");
            result.append (pad3 + to.name + " * to = " + to.name + "_Population_Instance->allocate (t, dt);\n");
            // Match variables between the two sets.
            String [] forbiddenAttributes = new String [] {"global", "constant", "transient", "reference", "temporary", "output", "preexistent"};
            for (Variable v : to.variables)
            {
                if (v.name.equals ("$type"))
                {
                    result.append (pad3 + "to->" + mangle (v) + " = " + mangle ("$type") + ";\n");
                    continue;
                }
                if (v.hasAny (forbiddenAttributes))
                {
                    continue;
                }
                Variable v2 = from.find (v);
                if (v2 == null  ||  ! v2.equals (v))
                {
                    continue;
                }
                // This needs to be more sophisticated. We need to resolve the source, in case it is not a direct member of "from".
                result.append (pad3 + "to->" + mangle (v) + " = from->" + mangle (v) + ";\n");
            }
            // Match connection bindings
            if (connectionTo)
            {
                for (Entry<String, EquationSet> c : to.connectionBindings.entrySet ())
                {
                    String name = c.getKey ();
                    Entry<String, EquationSet> d = from.connectionBindings.floorEntry (name);
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
            result.append (pad2 + "class _Derivative\n");
            result.append (pad2 + "{\n");
            result.append (pad2 + "public:\n");
            for (Variable v : localStackDerivative)
            {
                result.append (pad3 + "float " + mangle (v) + ";\n");
            }
            result.append (pad3 + "_Derivative * _next;\n");
            result.append (pad2 + "};\n");
            result.append ("\n");
        }
        if (localIntegrated.size () > 0)
        {
            result.append (pad2 + "class _Integrated\n");
            result.append (pad2 + "{\n");
            result.append (pad2 + "public:\n");
            for (Variable v : localIntegrated)
            {
                result.append (pad3 + "float " + mangle (v) + ";\n");
            }
            result.append (pad3 + "_Integrated * _next;\n");
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Unit variables
        if (localStackDerivative.size () > 0)
        {
            result.append (pad2 + "_Derivative * _stackDerivative;\n");
        }
        if (localIntegrated.size () > 0)
        {
            result.append (pad2 + "_Integrated * _stackIntegrated;\n");
        }
        if (s.container != null  &&  s.connectionBindings == null)
        {
            result.append (pad2 + mangle (s.container.name) + " * _container;\n");
        }
        if (s.connectionBindings != null)
        {
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                // we should be able to assume that s.container is non-null; ie: a connection should always operate in some larger container
                result.append (pad2 + prefix (s.container, e.getValue ()) + " * " + mangle (e.getKey ()) + ";\n");
            }
        }
        for (Variable v : localMembers)
        {
            result.append (pad2 + "float " + mangle (v) + ";\n");
        }
        for (Variable v : localExternal)
        {
            result.append (pad2 + "float _next" + mangle (v) + ";\n");
        }
        result.append ("\n");

        // Unit ctor
        result.append (pad2 + mangle (s.name) + " ()\n");
        result.append (pad2 + "{\n");
        if (localStackDerivative.size () > 0)
        {
            result.append (pad3 + "_stackDerivative = 0;\n");
        }
        if (localIntegrated.size () > 0)
        {
            result.append (pad3 + "_stackIntegrated = 0;\n");
        }
        for (Variable v : localMembers)
        {
            result.append (pad3 + mangle (v) + " = 0;\n");
        }
        for (Variable v : localExternal)
        {
            result.append (pad3 + "_next" + mangle (v) + " = 0;\n");
        }
        for (EquationSet e : s.parts)
        {
            result.append (pad3 + mangle (e.name) + "_Population_Instance._container = this;\n");
        }
        result.append (pad2 + "}\n");
        result.append ("\n");

        // Unit dtor
        result.append (pad2 + "virtual ~" + mangle (s.name) + " ()\n");
        result.append (pad2 + "{\n");
        if (localStackDerivative.size () > 0)
        {
            result.append (pad3 + "while (_stackDerivative)\n");
            result.append (pad3 + "{\n");
            result.append (pad4 + "_Derivative * temp = _stackDerivative;\n");
            result.append (pad4 + "_stackDerivative = _stackDerivative->_next;\n");
            result.append (pad4 + "delete temp;\n");
            result.append (pad3 + "}\n");
        }
        if (localIntegrated.size () > 0)
        {
            result.append (pad3 + "while (_stackIntegrated)\n");
            result.append (pad3 + "{\n");
            result.append (pad4 + "_Integrated * temp = _stackIntegrated;\n");
            result.append (pad4 + "_stackIntegrated = _stackIntegrated->_next;\n");
            result.append (pad4 + "delete temp;\n");
            result.append (pad3 + "}\n");
        }
        result.append (pad2 + "}\n");
        result.append ("\n");

        // Unit getPopulations
        if (s.parts.size () > 0)
        {
            result.append (pad2 + "virtual void getPopulations (vector<_Population *> & result)\n");
            result.append (pad2 + "{\n");
            for (EquationSet e : s.parts)
            {
                result.append (pad3 + "result.push_back (&" + mangle (e.name) + "_Population_Instance);\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit init
        if (s.connectionBindings == null  ||  localInit.size () > 0)
        {
            result.append (pad2 + "virtual void init (float " + mangle ("$t") + ", float & " + mangle ("$dt") + ")\n");
            result.append (pad2 + "{\n");
            s.setInit (true);
            //   declare buffer variables
            for (Variable v : localBufferedInternal)
            {
                result.append (pad3 + "float _next" + mangle (v) + ";\n");
            }
            //   $variables
            for (Variable v : localInit)
            {
                if (! v.name.startsWith ("$")) continue;
                multiconditional (s, v, context, true, pad3, result);
            }
            //   finalize $variables
            for (Variable v : localBuffered)  // more than just localBufferedInternal, because we must finalize members as well
            {
                if (! v.name.startsWith ("$")) continue;
                result.append (pad3 + mangle (v) + " = _next" + mangle (v) + ";\n");
            }
            //   non-$variables
            for (Variable v : localInit)
            {
                if (v.name.startsWith ("$")) continue;
                multiconditional (s, v, context, true, pad3, result);
            }
            //   finalize non-$variables
            for (Variable v : localBuffered)
            {
                if (v.name.startsWith ("$")) continue;
                result.append (pad3 + mangle (v) + " = _next" + mangle (v) + ";\n");
            }
            s.setInit (false);
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit integrate
        if (localIntegrated.size () > 0)
        {
            result.append (pad2 + "virtual void integrate (float " + mangle ("$t") + ", float " + mangle ("$dt") + ")\n");
            result.append (pad2 + "{\n");
            // Note the resolve() call on the left-hand-side below has lvalue==false.
            // Integration always takes place in the primary storage of a variable.
            result.append (pad3 + "if (_stackIntegrated)\n");
            result.append (pad3 + "{\n");
            for (Variable v : localIntegrated)
            {
                Variable vo = s.variables.floor (new Variable (v.name, v.order + 1));  // pre-processing should guarantee that this exists
                result.append (pad4 + resolve (context, v.reference, false, 0) + " = _stackIntegrated->" + mangle (v) + " + " + resolve (context, vo.reference, false, 0) + " * " + mangle ("$dt") + ";\n");
            }
            result.append (pad3 + "}\n");
            result.append (pad3 + "else\n");
            result.append (pad3 + "{\n");
            for (Variable v : localIntegrated)
            {
                Variable vo = s.variables.floor (new Variable (v.name, v.order + 1));
                result.append (pad4 + resolve (context, v.reference, false, 0) + " += " + resolve (context, vo.reference, false, 0) + " * " + mangle ("$dt") + ";\n");
            }
            result.append (pad3 + "}\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit prepare
        if (localExternalWrite.size () > 0)
        {
            result.append (pad2 + "virtual void prepare ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : localExternalWrite)
            {
                result.append (pad3 + "_next" + mangle (v) + " = 0;\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit update
        if (local.size () > 0)
        {
            result.append (pad2 + "virtual void update (float " + mangle ("$t") + ", float & " + mangle ("$dt") + ")\n");
            result.append (pad2 + "{\n");
            for (Variable v : localBufferedInternal)
            {
                result.append (pad3 + "float _next" + mangle (v) + ";\n");
            }
            for (Variable v : local)
            {
                multiconditional (s, v, context, false, pad3, result);
            }
            for (Variable v : localBufferedInternal)
            {
                result.append (pad3 + mangle (v) + " = _next" + mangle (v) + ";\n");
            }
            if (hasOutputs)
            {
                result.append (pad3 + "cout << endl;\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit finalize
        if (localExternal.size () > 0  ||  type != null)
        {
            result.append (pad2 + "virtual void finalize (float " + mangle ("$t") + ", float & " + mangle ("$dt") + ")\n");
            result.append (pad2 + "{\n");
            for (Variable v : localExternal)
            {
                result.append (pad3 + mangle (v) + " = _next" + mangle (v) + ";\n");
            }
            if (type != null)
            {
                result.append (pad3 + "switch (" + mangle ("$type") + ")\n");
                result.append (pad3 + "{\n");
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
                            result.append (pad5 + "_container->" + s.name + "_2_" + to.name + " (this, " + mangle ("$t") + ", " + mangle ("$dt") + ", " + (j + 1) + ");\n");
                        }
                    }
                    if (! used)
                    {
// STOPPED HERE ----------------------------------------------------------------------------------------
                        result.append (pad5 + "\n");  // must stage this instance for destruction
                    }
                    result.append (pad5 + "break;\n");
                    result.append (pad4 + "}\n");
                }
                result.append (pad3 + "}\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit prepareDerivative
        if (localExternalWriteDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void prepareDerivative ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : localExternalWriteDerivative)
            {
                result.append (pad3 + "_next" + mangle (v) + " = 0;\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit updateDerivative
        if (localDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void updateDerivative (float " + mangle ("$t") + ", float " + mangle ("$dt") + ")\n");
            result.append (pad2 + "{\n");
            for (Variable v : localBufferedDerivative)
            {
                result.append (pad3 + "float _next" + mangle (v) + ";\n");
            }
            for (Variable v : localDerivative)
            {
                multiconditional (s, v, context, false, pad3, result);
            }
            for (Variable v : localBufferedDerivative)
            {
                result.append (pad3 + mangle (v) + " = _next" + mangle (v) + ";\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit finalizeDerivative
        if (localExternalDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void finalizeDerivative ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : localExternalDerivative)
            {
                result.append (pad3 + mangle (v) + " = _next" + mangle (v) + ";\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit pushIntegrated
        if (localIntegrated.size () > 0)
        {
            result.append (pad2 + "virtual void pushIntegrated ()\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "_Integrated * temp = new _Integrated;\n");
            result.append (pad3 + "temp->_next = _stackIntegrated;\n");
            result.append (pad3 + "_stackIntegrated = temp;\n");
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
            result.append (pad3 + "_Integrated * temp = _stackIntegrated;\n");
            result.append (pad3 + "_stackIntegrated = _stackIntegrated->_next;\n");
            result.append (pad3 + "delete temp;\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit pushDerivative
        if (localStackDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void pushDerivative ()\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "_Derivative * temp = new _Derivative;\n");
            result.append (pad3 + "temp->_next = _stackDerivative;\n");
            result.append (pad3 + "_stackDerivative = temp;\n");
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
                result.append (pad3 + "_stackDerivative->" + mangle (v) + " += " + mangle (v) + " * scalar;\n");
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
                result.append (pad3 + mangle (v) + " += _stackDerivative->" + mangle (v) + ";\n");
            }
            result.append (pad3 + "_Derivative * temp = _stackDerivative;\n");
            result.append (pad3 + "_stackDerivative = _stackDerivative->_next;\n");
            result.append (pad3 + "delete temp;\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Unit getP
        {
            Variable v = s.find (new Variable ("$p", 0));
            if (v != null)
            {
                result.append (pad2 + "virtual float getP (const Vector3 & " + mangle ("$xyz") + ")\n");
                result.append (pad2 + "{\n");
                if (v.hasAttribute ("transient"))
                {
                    // If $p is transient, then it is only called during init, after most other $variables are assigned
                    s.setInit (true);
                    multiconditionalTransient (s, v, context, "return", "return 1", pad3, result);
                    s.setInit (false);
                }
                else  // stored somewhere
                {
                    result.append (pad3 + "return " + resolve (context, v.reference, false, 0) + ";\n");
                }
                result.append (pad2 + "}\n");
                result.append ("\n");
            }
        }

        // determine reference population
        int refIndex = 0;  // used both here and in population class below
        String refName = "";
        if (s.connectionBindings != null)
        {
            refName = s.connectionBindings.firstKey ();

            Variable v = s.find (new Variable ("$ref"));
            if (v != null)
            {
                EquationEntry e = v.equations.first ();
                if (e != null  &&  e.expression != null)
                {
                    refName = e.expression.toString ();
                    for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
                    {
                        if (n.getKey ().equals (refName)) break;
                        refIndex++;
                    }
                }
            }
        }

        // Unit getXYZ
        {
            Variable v = s.find (new Variable ("$xyz", 0));
            if (v != null  ||  s.connectionBindings != null)
            {
                result.append (pad2 + "virtual void getXYZ (Vector3 & " + mangle ("$xyz") + ")\n");
                result.append (pad2 + "{\n");
                if (v == null)  // This must therefore be a Connection, so we defer $xyz to our reference part.
                {
                    result.append (pad3 + refName + "->getXYZ (" + mangle ("$xyz") + ");\n");
                }
                else if (v.hasAttribute ("transient"))
                {
                    s.setInit (true);
                    String returnString  = mangle ("$xyz") + " =";
                    String defaultString = "_Part::getXYZ (" + mangle ("$xyz") + ")";
                    multiconditionalTransient (s, v, context, returnString, defaultString, pad3, result);
                    s.setInit (false);
                }
                else  // stored somewhere
                {
                    result.append (pad3 + mangle ("$xyz") + " = " + resolve (context, v.reference, false, 0) + ";\n");
                }
                result.append (pad2 + "}\n");
                result.append ("\n");
            }
        }

        // Unit getPart and setPart
        if (s.connectionBindings != null)
        {
            result.append (pad2 + "virtual void setPart (int i, _Part * part)\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "switch (i)\n");
            result.append (pad3 + "{\n");
            int i = 0;
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                result.append (pad4 + "case " + i++ + ": " + mangle (e.getKey ()) + " = (" + prefix (s.container, e.getValue ()) + " *) part; return;\n");
            }
            result.append (pad3 + "}\n");
            result.append (pad2 + "}\n");
            result.append ("\n");

            result.append (pad2 + "virtual _Part * getPart (int i)\n");
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
        result.append (pad + "class " + mangle (s.name) + "_Population : public _Population\n");
        result.append (pad + "{\n");
        result.append (pad + "public:\n");

        // Population buffers
        if (globalStackDerivative.size () > 0)
        {
            result.append (pad2 + "class _Derivative\n");
            result.append (pad2 + "{\n");
            result.append (pad2 + "public:\n");
            for (Variable v : globalStackDerivative)
            {
                result.append (pad3 + "float " + mangle (v) + ";\n");
            }
            result.append (pad3 + "_Derivative * _next;\n");
            result.append (pad2 + "};\n");
            result.append ("\n");
        }
        if (globalIntegrated.size () > 0)
        {
            result.append (pad2 + "class _Integrated\n");
            result.append (pad2 + "{\n");
            result.append (pad2 + "public:\n");
            for (Variable v : globalIntegrated)
            {
                result.append (pad3 + "float " + mangle (v) + ";\n");
            }
            result.append (pad3 + "_Integrated * _next;\n");
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population variables
        if (globalStackDerivative.size () > 0)
        {
            result.append (pad2 + "_Derivative * _stackDerivative;\n");
        }
        if (globalIntegrated.size () > 0)
        {
            result.append (pad2 + "_Integrated * _stackIntegrated;\n");
        }
        if (s.container != null)
        {
            result.append (pad2 + mangle (s.container.name) + " * _container;\n");
        }
        for (Variable v : globalMembers)
        {
            result.append (pad2 + "float " + mangle (v) + ";\n");
        }
        for (Variable v : globalExternal)
        {
            result.append (pad2 + "float _next" + mangle (v) + ";\n");
        }
        result.append ("\n");

        // Population ctor
        result.append (pad2 + mangle (s.name) + "_Population ()\n");
        result.append (pad2 + "{\n");
        if (globalStackDerivative.size () > 0)
        {
            result.append (pad3 + "_stackDerivative = 0;\n");
        }
        if (globalIntegrated.size () > 0)
        {
            result.append (pad3 + "_stackIntegrated = 0;\n");
        }
        for (Variable v : globalMembers)
        {
            result.append (pad3 + mangle (v) + " = 0;\n");
        }
        for (Variable v : globalExternal)
        {
            result.append (pad3 + "_next" + mangle (v) + " = 0;\n");
        }
        result.append (pad2 + "}\n");
        result.append ("\n");

        // Population dtor
        result.append (pad2 + "virtual ~" + mangle (s.name) + "_Population ()\n");
        result.append (pad2 + "{\n");
        if (globalStackDerivative.size () > 0)
        {
            result.append (pad3 + "while (_stackDerivative)\n");
            result.append (pad3 + "{\n");
            result.append (pad4 + "_Derivative * temp = _stackDerivative;\n");
            result.append (pad4 + "_stackDerivative = _stackDerivative->_next;\n");
            result.append (pad4 + "delete temp;\n");
            result.append (pad3 + "}\n");
        }
        if (globalIntegrated.size () > 0)
        {
            result.append (pad3 + "while (_stackIntegrated)\n");
            result.append (pad3 + "{\n");
            result.append (pad4 + "_Integrated * temp = _stackIntegrated;\n");
            result.append (pad4 + "_stackIntegrated = _stackIntegrated->_next;\n");
            result.append (pad4 + "delete temp;\n");
            result.append (pad3 + "}\n");
        }
        result.append (pad2 + "}\n");
        result.append ("\n");

        // Population create
        result.append (pad2 + "virtual _Part * create ()\n");
        result.append (pad2 + "{\n");
        result.append (pad3 + mangle (s.name) + " * p = new " + mangle (s.name) + ";\n");
        if (s.container != null  &&  s.connectionBindings == null)
        {
            result.append (pad3 + "p->_container = _container;\n");
        }
        result.append (pad3 + "return p;\n");
        result.append (pad2 + "}\n");
        result.append ("\n");

        // Population getPopulations
        // This function changes meaning here. Now it returns an ordered list
        // of populations associated with references in a connection.
        if (s.connectionBindings != null)
        {
            result.append (pad2 + "virtual void getPopulations (vector<_Population *> & result)\n");
            result.append (pad2 + "{\n");
            for (Entry<String, EquationSet> e : s.connectionBindings.entrySet ())
            {
                // TODO: This does not resolve connections outside the current containing part. Need more sophisticated resolution method.
                result.append (pad3 + "result.push_back (& _container->" + mangle (e.getValue ().name) + "_Population_Instance);\n");
            }
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Population init
        if (globalMembers.size () > 0)
        {
            result.append (pad2 + "virtual void init (float " + mangle ("$t") + ", float & " + mangle ("$dt") + ")\n");
            result.append (pad2 + "{\n");
            s.setInit (true);
            //   declare buffer variables
            for (Variable v : globalBufferedInternal)
            {
                result.append (pad3 + "float _next" + mangle (v) + ";\n");
            }
            //   no separate $ and non-$ phases, because only $variables work at the population level
            for (Variable v : globalInit)
            {
                multiconditional (s, v, context, true, pad3, result);
            }
            //   finalize
            for (Variable v : globalBuffered)
            {
                result.append (pad3 + mangle (v) + " = _next" + mangle (v) + ";\n");
            }
            s.setInit (false);
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population integrate
        if (globalIntegrated.size () > 0)
        {
            result.append (pad2 + "virtual void integrate (float " + mangle ("$t") + ", float " + mangle ("$dt") + ")\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "if (_stackIntegrated)\n");
            result.append (pad3 + "{\n");
            for (Variable v : globalIntegrated)
            {
                Variable vo = s.variables.floor (new Variable (v.name, v.order + 1));
                result.append (pad4 + resolve (context, v.reference, false, 0) + " = _stackIntegrated->" + mangle (v) + " + " + resolve (context, vo.reference, false, 0) + " * " + mangle ("$dt") + ";\n");
            }
            result.append (pad3 + "}\n");
            result.append (pad3 + "else\n");
            result.append (pad3 + "{\n");
            for (Variable v : globalIntegrated)
            {
                Variable vo = s.variables.floor (new Variable (v.name, v.order + 1));
                result.append (pad4 + resolve (context, v.reference, false, 0) + " += " + resolve (context, vo.reference, false, 0) + " * " + mangle ("$dt") + ";\n");
            }
            result.append (pad3 + "}\n");
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population prepare
        if (globalExternalWrite.size () > 0)
        {
            result.append (pad2 + "virtual void prepare ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalExternalWrite)
            {
                result.append (pad3 + "_next" + mangle (v) + " = 0;\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population update
        if (global.size () > 0)
        {
            result.append (pad2 + "virtual void update (float " + mangle ("$t") + ", float " + mangle ("$dt") + ")\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalBufferedInternal)
            {
                result.append (pad3 + "float _next" + mangle (v) + ";\n");
            }
            for (Variable v : global)
            {
                multiconditional (s, v, context, false, pad3, result);
            }
            for (Variable v : globalBufferedInternal)
            {
                result.append (pad3 + mangle (v) + " = _next" + mangle (v) + ";\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population finalize
        if (globalExternal.size () > 0)
        {
            result.append (pad2 + "virtual void finalize ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalExternal)
            {
                result.append (pad3 + mangle (v) + " = _next" + mangle (v) + ";\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population prepareDerivative
        if (globalExternalWriteDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void prepareDerivative ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalExternalWriteDerivative)
            {
                result.append (pad3 + "_next" + mangle (v) + " = 0;\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population updateDerivative
        if (globalDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void updateDerivative (float " + mangle ("$t") + ", float " + mangle ("$dt") + ")\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalBufferedDerivative)
            {
                result.append (pad3 + "float _next" + mangle (v) + ";\n");
            }
            for (Variable v : globalDerivative)
            {
                multiconditional (s, v, context, false, pad3, result);
            }
            for (Variable v : globalBufferedDerivative)
            {
                result.append (pad3 + mangle (v) + " = _next" + mangle (v) + ";\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population finalizeDerivative
        if (globalExternalDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void finalizeDerivative ()\n");
            result.append (pad2 + "{\n");
            for (Variable v : globalExternalDerivative)
            {
                result.append (pad3 + mangle (v) + " = _next" + mangle (v) + ";\n");
            }
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population pushIntegrated
        if (globalIntegrated.size () > 0)
        {
            result.append (pad2 + "virtual void pushIntegrated ()\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "_Integrated * temp = new _Integrated;\n");
            result.append (pad3 + "temp->_next = _stackIntegrated;\n");
            result.append (pad3 + "_stackIntegrated = temp;\n");
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
            result.append (pad3 + "_Integrated * temp = _stackIntegrated;\n");
            result.append (pad3 + "_stackIntegrated = _stackIntegrated->_next;\n");
            result.append (pad3 + "delete temp;\n");
            result.append (pad2 + "};\n");
            result.append ("\n");
        }

        // Population pushDerivative
        if (globalStackDerivative.size () > 0)
        {
            result.append (pad2 + "virtual void pushDerivative ()\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "_Derivative * temp = new _Derivative;\n");
            result.append (pad3 + "temp->_next = _stackDerivative;\n");
            result.append (pad3 + "_stackDerivative = temp;\n");
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
                result.append (pad3 + "_stackDerivative->" + mangle (v) + " += " + mangle (v) + " * scalar;\n");
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
                result.append (pad3 + mangle (v) + " += _stackDerivative->" + mangle (v) + ";\n");
            }
            result.append (pad3 + "_Derivative * temp = _stackDerivative;\n");
            result.append (pad3 + "_stackDerivative = _stackDerivative->_next;\n");
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

        // Population getMax
        if (s.connectionBindings != null)
        {
            result.append (pad2 + "virtual int getMax (int i)\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "switch (i)\n");
            result.append (pad3 + "{\n");
            int i = 0;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                result.append (pad4 + "case " + i++ + ": ");
                Variable v = s.find (new Variable (n.getKey () + ".$max"));
                EquationEntry e = null;
                if (v != null)
                {
                    e = v.equations.first ();
                }
                if (e == null)
                {
                    result.append ("return 0;\n");
                }
                else
                {
                    result.append ("return " + context.render (e.expression) + ";\n");
                }
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
            int i = 0;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                result.append (pad4 + "case " + i++ + ": ");
                Variable v = s.find (new Variable (n.getKey () + ".$min"));
                EquationEntry e = null;
                if (v != null)
                {
                    e = v.equations.first ();
                }
                if (e == null)
                {
                    result.append ("return 0;\n");
                }
                else
                {
                    result.append ("return " + context.render (e.expression) + ";\n");
                }
            }
            result.append (pad3 + "}\n");
            result.append (pad3 + "return 0;\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
        }

        // Population getN
        // If the simulator asks for $n, it gets the aspirational value, that is, the one set
        // by some equation in order to request a certain population size.
        // TODO: If an equation reads $n, it *should* get the actual number of live parts.
        if (s.connectionBindings == null)  // $n is only defined for parts, not connections  
        {
            Variable v = s.find (new Variable ("$n"));
            if (v != null)
            {
                result.append (pad2 + "virtual float getN ()\n");
                result.append (pad2 + "{\n");
                result.append (pad3 + "return " + resolve (context, v.reference, false, 0) + ";\n");
                result.append (pad2 + "}\n");
                result.append ("\n");
            }
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

        // Population getRef
        if (s.connectionBindings != null)
        {
            result.append (pad2 + "virtual int getRef ()\n");
            result.append (pad2 + "{\n");
            result.append (pad3 + "return " + refIndex + ";\n");
            result.append (pad2 + "}\n");
            result.append ("\n");
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

        // Instances
        result.append (pad + mangle (s.name) + "_Population " + mangle (s.name) + "_Population_Instance;\n");
        result.append ("\n");

        return result.toString ();
    }

    public void multiconditional (EquationSet s, Variable v, CRenderingContext context, boolean init, String pad, StringBuilder result) throws Exception
    {
        if (v.hasAttribute ("temporary"))
        {
            result.append (pad + "float " + mangle (v) + ";\n");
        }

        boolean isType = v.name.equals ("$type");

        // Select the default equation
        EquationEntry defaultEquation = null;
        for (EquationEntry e : v.equations)
        {
            if (init  &&  e.ifString.equals ("$init"))
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
            if (init  &&  e.ifString.length () == 0)
            {
                continue;
            }
            if (! init  &&  e.ifString.equals ("$init"))
            {
                continue;
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
            if (e.assignment.length () > 0)
            {
                if (isType)
                {
                    ArrayList<EquationSet> split = EquationSet.getSplitFrom (e.expression);
                    int index = s.splits.indexOf (split);
                    result.append (pad + resolve (context, v.reference, true, 0) + " = " + (index + 1) + ";\n");
                }
                else
                {
                    result.append (pad + resolve (context, v.reference, true, 0) + " " + e.assignment + " " + context.render (e.expression) + ";\n");
                }
            }
            else if (! init)  // an old-style tracer
            {
                result.append (pad + "cout << (" + context.render (e.expression) + ") << \" \";\n");
            }
        }

        // Write the default equation
        if (defaultEquation == null)
        {
            if (isType)
            {
                if (haveIf)
                {
                    result.append (pad + "else\n  ");
                }
                result.append (pad + resolve (context, v.reference, true, 0) + " = 0;\n");  // always reset $type to 0
            }
            else
            {
                // externalWrite variables already have a default action in the prepare() method, so only deal with cycle and externalRead
                if (v.reference.variable == v  &&  v.equations.size () > 0  &&  v.hasAny (new String[] {"cycle", "externalRead"}))
                {
                    if (haveIf)
                    {
                        result.append (pad + "else\n  ");
                    }
                    result.append (pad + resolve (context, v.reference, true, 0) + " = " + resolve (context, v.reference, false, 0) + ";\n");  // copy previous value
                }
            }
        }
        else
        {
            if (haveIf)
            {
                result.append (pad + "else\n  ");
            }
            if (defaultEquation.assignment.length () > 0)
            {
                if (isType)
                {
                    ArrayList<EquationSet> split = EquationSet.getSplitFrom (defaultEquation.expression);
                    int index = s.splits.indexOf (split);
                    result.append (pad + resolve (context, v.reference, true, 0) + " = " + (index + 1) + ";\n");
                }
                else
                {
                    result.append (pad + resolve (context, v.reference, true, 0) + " " + defaultEquation.assignment + " " + context.render (defaultEquation.expression) + ";\n");
                }
            }
            else if (! init)  // an old-style tracer
            {
                result.append (pad + "cout << (" + context.render (defaultEquation.expression) + ") << \" \";\n");
            }
        }
    }

    public void multiconditionalTransient (EquationSet s, Variable v, CRenderingContext context, String returnString, String defaultString, String pad, StringBuilder result)
    {
        boolean haveIf = false;
        for (EquationEntry e : v.equations)
        {
            if (e.conditional != null  &&  ! e.ifString.equals ("$init"))
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
            result.append (pad + returnString + " " + context.render (e.expression) + ";\n");
        }
        if (haveIf)
        {
            result.append (pad + "else\n  ");  // copy previous value
            result.append (pad + defaultString + ";\n");
        }
    }

    public static String mangle (String input)
    {
        StringBuilder result = new StringBuilder ();
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

    public static String mangle (Variable v)
    {
        return mangle (v.nameString ());
    }

    public static String prefix (EquationSet p, EquationSet t)
    {
        String result = mangle (t.name);
        while (t != p)
        {
            t = t.container;
            result = mangle (t.name) + "::" + result;
        }
        return result;
    }

    // TODO: Any read of a transient should be resolved to a special getter function.
    public String resolve (CRenderingContext context, VariableReference r, boolean lvalue, int index)
    {
        if (r == null  ||  r.variable == null) return "unresolved";

        if (r.variable.hasAttribute ("constant"))
        {
            EquationEntry e = r.variable.equations.first ();
            return context.render (e.expression);  // should simply render an ASTConstant
        }

        // Compute series of pointers to get to v.reference
        String containers = "";
        EquationSet current = context.part;
        Iterator<Object> it = r.resolution.iterator ();
        while (it.hasNext ())
        {
            Object o = it.next ();
            if (o instanceof EquationSet)
            {
                EquationSet s = (EquationSet) o;
                if (s.container == current)  // going down
                {
                    String typeName = s.name;
                    EquationSet p = s.container;
                    while (p != null)
                    {
                        typeName = p.name + "::" + typeName;
                        p = p.container;
                    }
                    containers = "((" + typeName + " *) " + containers + s.name + "_Population_Instance._parts[" + (it.hasNext () ? 0 : index) + "])->";
                }
                else  // going up
                {
                    containers += "_container->";
                }
                current = s;
            }
            else if (o instanceof Entry<?, ?>)
            {
                containers += mangle ((String) ((Entry<?, ?>) o).getKey ()) + "->";
                current = (EquationSet) ((Entry<?, ?>) o).getValue ();
            }
        }

        String name = mangle (r.variable);
        if (lvalue  &&  r.variable.hasAny (new String[] {"cycle", "externalRead", "externalWrite"}))
        {
            name = "_next" + name;
        }
        return containers + name;
    }

    class CRenderingContext extends ASTRenderingContext
    {
        public EquationSet part;
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

            int index = 0;
            try
            {
                // TODO: check for multiple subscripts. Postprocessing in the parser should collapse these to a single set of children.
                ASTConstant c = (ASTConstant) vn.getChild (0);
                index = ((Number) c.getValue ()).intValue ();
            }
            catch (Exception error)
            {
                // This is not a variable with a subscript, but no big deal.
            }

            return resolve ((CRenderingContext) context, vn.reference, false, index);
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

    class FloatRenderer implements ASTNodeRenderer
    {
        public String render (ASTNodeBase node, ASTRenderingContext context)
        {
            ASTConstant c = (ASTConstant) node;
            Object o = c.getValue ();
            if (o instanceof Float  ||  o instanceof Double  ||  o instanceof BigDecimal)
            {
                return o.toString () + "f";  // Tell c compiler that our type is float, not double. TODO: let user select numeric type of runtime
            }
            // We should only be an explicit integer if the type is allowed by the N2A language at this point.
            return o.toString ();
        }
    }
}
