/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.jobs.NodeJob;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class XyceBackend extends Backend
{
    @Override
    public String getName ()
    {
        return "Xyce";
    }

    @Override
    public void start (final MNode job)
    {
        Thread t = new Thread ()
        {
            @Override
            public void run ()
            {
                Path localJobDir = Host.getJobDir (Host.getLocalResourceDir (), job);
                Path errPath = localJobDir.resolve ("err");
                try {err.set (new PrintStream (new FileOutputStream (errPath.toFile (), true), false, "UTF-8"));}
                catch (Exception e) {}

                try
                {
                    Files.createFile (localJobDir.resolve ("started"));
                    MNode model = NodeJob.getModel (job);

                    // set up job info
                    Host env = Host.get (job);
                    String xyce  = env.config.getOrDefault ("Xyce", "xyce", "command");
                    Path jobDir  = Host.getJobDir (env.getResourceDir (), job);  // local or remote
                    Path cirFile = jobDir.resolve ("model.cir");
                    Path prnFile = jobDir.resolve ("out");  // "prn" doesn't work, at least on Windows

                    EquationSet digestedModel = new EquationSet (model);
                    Simulator simulator = InternalBackend.constructStaticNetwork (digestedModel);
                    analyze (digestedModel);

                    String duration = digestedModel.metadata.getOrDefault ("1.0", "duration");
                    job.set (duration, "duration");

                    long seed = digestedModel.metadata.getOrDefault (System.currentTimeMillis (), "seed");
                    job.set (seed, "seed");

                    MNode integrator = digestedModel.metadata.child ("backend", "xyce", "integrator");
                    job.set (integrator, "integrator");

                    try (BufferedWriter writer = Files.newBufferedWriter (cirFile))
                    {
                        generateNetlist (job, simulator, writer);
                    }

                    PrintStream ps = Backend.err.get ();
                    if (ps != System.err)
                    {
                        ps.close ();
                        Backend.err.remove ();
                        job.set (Host.size (errPath), "errSize");
                    }

                    env.submitJob (job, false, xyce, env.quote (cirFile), "-o",  env.quote (prnFile));
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
    public double currentSimTime (MNode job)
    {
        return getSimTimeFromOutput (job, "out", 1);
    }

    public void analyze (EquationSet s)
    {
        for (EquationSet p : s.parts) analyze (p);
        XyceBackendData bed = new XyceBackendData ();
        bed.internal = (InternalBackendData) s.backendData;
        s.backendData = bed;
        bed.analyze (s);
    }

    public void generateNetlist (MNode job, Simulator simulator, BufferedWriter writer) throws Exception
    {
        Population toplevel = (Population) simulator.wrapper.valuesObject[0];
        XyceRenderer renderer = new XyceRenderer (simulator);

        // Header
        writer.append (toplevel.equations.name + "\n");
        writer.append ("\n");
        writer.append ("* seed: " + job.get ("seed") + "\n");
        writer.append (".tran 0 " + job.get ("duration") + "\n");

        MNode integrator = job.child ("integrator");
        if (integrator != null)
        {
            String method = integrator.get ();
            if (! method.isEmpty ())
            {
                writer.append (".options timeint method=" + method + "\n");
            }
            // TODO: add other integrator options
        }

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
                            traces.add (((Output) op).operands[1]);
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
                        if (e.expression instanceof Output  &&  ((Output) e.expression).operands[1] == trace)  // this trace wraps the entire equation
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