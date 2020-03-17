/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.backend.internal.InstanceTemporaries;
import gov.sandia.n2a.backend.internal.InternalBackendData;
import gov.sandia.n2a.backend.internal.InternalBackend;
import gov.sandia.n2a.backend.internal.Population;
import gov.sandia.n2a.backend.xyce.netlist.Symbol;
import gov.sandia.n2a.backend.xyce.netlist.XyceRenderer;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.execenvs.HostSystem;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.parms.Parameter;
import gov.sandia.n2a.parms.ParameterDomain;
import gov.sandia.n2a.plugins.extpoints.Backend;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class XyceBackend extends Backend
{
    @Override
    public String getName ()
    {
        return "Xyce";
    }

    @Override
    public ParameterDomain getSimulatorParameters ()
    {
        ParameterDomain inputs = new ParameterDomain ();
        // TODO:  add real integration options, etc. - also need code to make sure they get in netlist!
        inputs.addParameter (new Parameter ("duration",        1.0));
        inputs.addParameter (new Parameter ("seed",            0));
        inputs.addParameter (new Parameter ("xyce.integrator", "trapezoid"));
        return inputs;
    }

    @Override
    public ParameterDomain getOutputVariables (MNode model)
    {
        try
        {
            MNode n = (MNode) model;
            if (n == null) return null;
            EquationSet s = new EquationSet (n);
            if (s.name.length () < 1) s.name = "Model";
            s.resolveLHS ();
            return s.getOutputParameters ();
        }
        catch (Exception error)
        {
            return null;
        }
    }

    @Override
    public boolean canRunNow (MNode job)
    {
        HostSystem execEnv = HostSystem.get (job.getOrDefault ("localhost", "$metadata", "host"));

        // TODO - estimate what memory and CPU resources this sim needs
        // getting good estimates could be very difficult...
        // maybe do this during prepare, through ModelInstance 
        // for now, just hard code
        // check that the required resources are available
        // if there are already xyce sims running, use them to estimate needs
        // if not, assume we have space for one
        try {
            Set<Long> procs = execEnv.getActiveProcs();
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
            for (Long procNum : procs) {
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

    @Override
    public void start (final MNode job)
    {
        Thread t = new Thread ()
        {
            @Override
            public void run ()
            {
                Path jobDir = Paths.get (job.get ()).getParent ();
                try {Backend.err.set (new PrintStream (jobDir.resolve ("err").toFile ()));}
                catch (FileNotFoundException e) {}

                try
                {
                    Files.createFile (jobDir.resolve ("started"));

                    // Ensure essential metadata is set
                    if (job.child ("$metadata", "duration"                     ) == null) job.set ("1.0",                       "$metadata", "duration");
                    if (job.child ("$metadata", "seed"                         ) == null) job.set (System.currentTimeMillis (), "$metadata", "seed");
                    if (job.child ("$metadata", "backend", "xyce", "integrator") == null) job.set ("trapezoid",                 "$metadata", "backend", "xyce", "integrator");

                    // set up job info
                    HostSystem env = HostSystem.get (job.getOrDefault ("localhost", "$metadata", "host"));
                    String xyce  = env.getNamedValue ("xyce.binary");
                    Path cirFile = jobDir.resolve ("model.cir");
                    Path prnFile = jobDir.resolve ("result");  // "prn" doesn't work, at least on windows

                    EquationSet e = new EquationSet (job);
                    Simulator simulator = InternalBackend.constructStaticNetwork (e);
                    analyze (e);

                    // Just in case a $p expression says something different than $metadata.duration
                    String duration = e.metadata.get ("duration");
                    if (! duration.isEmpty ()) job.set (duration, "$metadata", "duration");

                    FileWriter writer = new FileWriter (cirFile.toFile ());
                    generateNetlist (job, simulator, writer);
                    writer.close ();

                    PrintStream ps = Backend.err.get ();
                    if (ps != System.err)
                    {
                        ps.close ();
                        Backend.err.remove ();
                    }
                    env.submitJob (job, xyce + " " + env.quotePath (cirFile) + " -o " + env.quotePath (prnFile));
                }
                catch (AbortRun a)
                {
                }
                catch (Exception e)
                {
                    e.printStackTrace (Backend.err.get ());
                }

                PrintStream ps = err.get ();
                if (ps != System.err) ps.close ();
            }
        };
        t.setDaemon (true);
        t.start ();
    }

    @Override
    public void kill (MNode job)
    {
        long pid = job.getOrDefault (0l, "$metadata", "pid");
        if (pid != 0)
        {
            try
            {
                HostSystem.get (job.getOrDefault ("localhost", "$metadata", "host")).killJob (pid);
                String jobDir = new File (job.get ()).getParent ();
                Files.copy (new ByteArrayInputStream ("killed".getBytes ("UTF-8")), Paths.get (jobDir, "finished"));
            }
            catch (Exception e) {}
        }
    }

    @Override
    public double currentSimTime (MNode job)
    {
        // TODO: Write a pareser than can handle Xyce output.
        // Need to search for second column.
        // Does it use spaces or tabs?
        return 0;
    }

    public void analyze (EquationSet s)
    {
        for (EquationSet p : s.parts) analyze (p);
        XyceBackendData bed = new XyceBackendData ();
        bed.internal = (InternalBackendData) s.backendData;
        s.backendData = bed;
        bed.analyze (s);
    }

    public void generateNetlist (MNode job, Simulator simulator, FileWriter writer) throws Exception
    {
        Population toplevel = (Population) simulator.wrapper.valuesObject[0];
        XyceRenderer renderer = new XyceRenderer (simulator);

        // Header
        writer.append (toplevel.equations.name + "\n");
        writer.append ("\n");
        writer.append ("* seed: " + job.get ("$metadata", "seed") + "\n");
        writer.append (".tran 0 " + job.get ("$metadata", "duration") + "\n");
        //job.get ("$metadata", "xyce", "integrator")  // TODO: add this to netlist

        // Equations
        for (Instance i : simulator)
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

            InstanceTemporaries temp = new InstanceTemporaries (i, simulator, bed.internal);
            for (final Variable v : i.equations.variables)
            {
                // Compute variable v
                // TODO: how to switch between multiple conditions that can be true during normal operation? IE: how to make Xyce code conditional?
                // Perhaps gate each condition (through a transistor?) and sum them at a single node.
                EquationEntry e = v.select (temp);  // e can be null
                Symbol def = bed.equationSymbols.get (e);
                if (def == null) continue;
                writer.append (def.getDefinition (renderer));

                // Initial condition
                // TODO: output an ".ic" line for any var with nonzero value (since they all just came from the init cycle)

                // Trace
                class TraceFinder implements Visitor
                {
                    List<Operator> traces = new ArrayList<Operator> ();
                    public boolean visit (Operator op)
                    {
                        if (op instanceof Output)
                        {
                            traces.add (((Output) op).operands[0]);
                            return false;
                        }
                        return true;
                    }
                }
                TraceFinder traceFinder = new TraceFinder ();
                e.expression.visit (traceFinder);
                for (Operator trace : traceFinder.traces)
                {
                    writer.append (".print tran {");  // We don't know if contents is .func, expression or a node, so always wrap in braces.
                    if (trace instanceof AccessVariable)
                    {
                        AccessVariable av = (AccessVariable) trace;
                        writer.append (renderer.change (av.reference));
                    }
                    else  // trace is an expression
                    {
                        if (e.expression instanceof Output  &&  ((Output) e.expression).operands[0] == trace)  // this trace wraps the entire equation
                        {
                            // simply print the LHS variable, similar to the AccessVariable case above
                            writer.append (renderer.change (v.reference));
                        }
                        else
                        {
                            // arbitrary expression
                            writer.append (renderer.change (trace));
                        }
                    }
                    writer.append ("}\n");  // one .print line per variable
                }
            }
        }

        // Trailer
        writer.append (".end\n");
    }
}