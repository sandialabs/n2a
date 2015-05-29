/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

import gov.sandia.n2a.backend.internal.Euler;
import gov.sandia.n2a.backend.internal.InstanceTemporaries;
import gov.sandia.n2a.backend.internal.InternalBackendData;
import gov.sandia.n2a.backend.internal.InternalSimulation;
import gov.sandia.n2a.backend.internal.Population;
import gov.sandia.n2a.backend.xyce.parsing.XyceRenderer;
import gov.sandia.n2a.backend.xyce.symbol.SymbolDef;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.Trace;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.RunOrient;
import gov.sandia.umf.platform.plugins.RunState;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class XyceSimulation implements Simulation
{
    public String       duration = "1.0";  // default
    public long         seed     = System.currentTimeMillis ();  // default
    public String       intMethodValue;
    public ExecutionEnv execEnv;
    public RunOrient    runRecord;

    public ParameterDomain getAllParameters ()
    {
        ParameterDomain inputs = new ParameterDomain ();
        // TODO:  add real integration options, etc. - also need code to make sure they get in netlist!
        // and perhaps that's how run duration should get there too?
        inputs.addParameter (new Parameter ("duration",        duration));
        inputs.addParameter (new Parameter ("seed",            seed));
        inputs.addParameter (new Parameter ("xyce.integrator", "trapezoid"));
        return inputs;
    }

    @Override
    public void setSelectedParameters (ParameterDomain domain)
    {
        Map<Object, Parameter> params = domain.getParameterMap ();
        if (params.containsKey ("duration"))
        {
            Double dur = (Double) params.get ("duration").getDefaultValue ();
            duration = dur.toString ();
        }
        if (params.containsKey ("seed"))
        {
            seed = ((Number) params.get ("seed").getDefaultValue ()).longValue ();
        }
        if (params.containsKey ("xyce.integrator"))
        {
            intMethodValue = (String) params.get ("xyce.integrator").getDefaultValue ();
        }
    }

    public RunState prepare (Object run, ParameterSpecGroupSet groups, ExecutionEnv env) throws Exception
    {
        // handle any parameters that still need to be set
        runRecord = (RunOrient) run;
        // don't want to overwrite sim duration if it was set in RunOrient by RunDetailPanel
        // but if we created this the 'new' way, it won't exist in RunOrient yet
        if (runRecord.getSource ().get ("duration") == null) runRecord.setSimDuration (Double.valueOf (duration));

        // set up job info
        String xyce    = env.getNamedValue ("xyce.binary");
        String jobDir  = env.createJobDir ();
        String cirFile = env.file (jobDir, "model");
        String prnFile = env.file (jobDir, "result");  // "prn" doesn't work, at least on windows

        BufferedWriter writer = new BufferedWriter (new FileWriter (cirFile));

        EquationSet e = new EquationSet (runRecord.getModel ());
        if (e.name.length () < 1) e.name = "Model";  // because the default is for top-level equation set to be anonymous
        Euler simulator = InternalSimulation.constructStaticNetwork (e);
        analyze (e);
        generateNetlist (simulator, writer);

        // save job info 
        XyceRunState runState = new XyceRunState();
        runState.jobDir = jobDir;
        runState.command = xyce + " " + env.quotePath (cirFile) + " -o " + env.quotePath (prnFile);
        execEnv = env;
        return runState;
    }

    public boolean resourcesAvailable()
    {
        // TODO - estimate what memory and CPU resources this sim needs
        // getting good estimates could be very difficult...
        // maybe do this during prepare, through ModelInstance 
        // for now, just hard code
        // check that the required resources are available
        // if there are already xyce sims running, use them to estimate needs
        // if not, assume we have space for one
        try {
            Set<Integer> procs = execEnv.getActiveProcs();
            int numRunning = procs.size();
            if (numRunning == 0) {
                System.out.println("resourcesAvailable:  numRunning=0");
                return true;
            }
            // check CPU usage
            // assume n2a processes are responsible for all current load;
            // evaluate whether there's room for another process of size load/numRunning
            System.out.println("num procs: " + numRunning);
            double cpuLoad = execEnv.getProcessorLoad();
            int cpuTotal = execEnv.getProcessorTotal();
            double cpuPerProc = cpuLoad/numRunning;
            boolean cpuAvailable = (cpuTotal-cpuLoad) > cpuPerProc;
            System.out.println("cpuLoad: " + cpuLoad + 
                    "; cpuPerProc: " + cpuPerProc +
                    "; cpuAvailable: " + cpuAvailable
                    );
            // Memory estimates using approach above thrown off by N2A using a lot of memory
            // Instead, ask system for memory usage per proc
            // TODO - use this approach for CPU usage also?
            long maxMem = -1;
            for (Integer procNum : procs) {
                long procMem = execEnv.getProcMem(procNum);
                if (procMem>maxMem) {
                    maxMem = procMem;
                }
            }
            long memLoad = execEnv.getMemoryPhysicalTotal() - execEnv.getMemoryPhysicalFree();
            double memPerProc;
            if (maxMem>0) {
                System.out.println("using individual process memory estimate");
                memPerProc = maxMem;
            }
            else {
                System.out.println("processor memory usage negative; using memLoad/numRunning");
                memPerProc = memLoad/numRunning;
            }
            boolean memAvailable = execEnv.getMemoryPhysicalFree() > memPerProc;
            System.out.println("memLoad: " + memLoad + 
                    "; memPerProc: " + memPerProc +
                    "; memAvailable: " + memAvailable
                    );
            return memAvailable && cpuAvailable;
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }

    public void submit () throws Exception
    {
        execEnv.submitJob (runRecord.getState ());
    }

    public void submit (ExecutionEnv env, RunState runState) throws Exception
    {
        env.submitJob (runState);
    }
    
    @Override
    public RunState execute (Object run, ParameterSpecGroupSet groups, ExecutionEnv env) throws Exception
    {
        RunState runState = prepare (run, groups, env);
        env.submitJob (runState);
        return runState;
    }

    public void analyze (EquationSet s)
    {
        for (EquationSet p : s.parts) analyze (p);
        XyceBackendData bed = new XyceBackendData ();
        s.backendData = bed;
        bed.analyze (s);
    }

    public void generateNetlist (Euler simulator, BufferedWriter writer) throws Exception
    {
        Population toplevel = simulator.wrapper.populations[0];
        XyceRenderer renderer = new XyceRenderer (simulator);

        // Header
        writer.append (toplevel.equations.name + "\n");
        writer.append ("\n");
        writer.append ("* seed: " + seed + "\n");
        writer.append (".tran 0 " + duration + "\n");

        // Equations
        for (Instance i : simulator.queue)
        {
            if (i == simulator.wrapper) continue;

            writer.append ("\n");
            writer.append ("* " + i + "\n");

            renderer.pi         = i;
            renderer.exceptions = null;
            XyceBackendData bed = (XyceBackendData) i.equations.backendData;

            if (bed.deviceSymbol != null)
            {
                writer.append (bed.deviceSymbol.getDefinition (renderer));
            }

            InstanceTemporaries temp = new InstanceTemporaries (i, simulator, false);
            for (final Variable v : i.equations.variables)
            {
                // Compute variable v
                // TODO: how to switch between multiple conditions that can be true during normal operation? IE: how to make Xyce code conditional?
                // Perhaps gate each condition (through a transistor?) and sum them at a single node.
                EquationEntry e = v.select (temp);  // e can be null
                SymbolDef def = bed.equationSymbols.get (e);
                if (def == null) continue;
                writer.append (def.getDefinition (renderer));

                // Initial condition
                // TODO: output an ".ic" line for any var with nonzero value (since they all just came from the init cycle)

                // Trace
                class TraceFinder extends Visitor
                {
                    List<Operator> traces = new ArrayList<Operator> ();
                    public boolean visit (Operator op)
                    {
                        if (op instanceof Trace)
                        {
                            traces.add (((Trace) op).operands[0]);
                            return false;
                        }
                        return true;
                    }
                }
                TraceFinder traceFinder = new TraceFinder ();
                e.expression.visit (traceFinder);
                for (Operator trace : traceFinder.traces)
                {
                    Instance targetInstance = i;
                    if (traceFinder.target.variable.container != i.equations)
                    {
                        targetInstance = (Instance) i.valuesType[traceFinder.target.index];
                    }
                    XyceBackendData targetBed = (XyceBackendData) targetInstance.equations.backendData;
                    if (targetBed.deviceSymbol != null)
                    {
                        writer.append (targetBed.deviceSymbol.getTracer (traceFinder.target.variable, targetInstance));
                    }
                    else
                    {
                        XyceRenderer xlator = new XyceRenderer (targetBed, targetInstance, null, false);
                        // TODO: we can actually trace an arbitrary expression, so full support for trace() is possible
                        writer.append ("{" + xlator.change (traceFinder.target.variable.name) + "} ");
                        //writer.append (Xyceisms.referenceStateVar (traceFinder.target.variable.name, targetInstance.hashCode ()));
                    }
                }
            }
        }

        // Trailer
        writer.append (".end\n");
    }
}