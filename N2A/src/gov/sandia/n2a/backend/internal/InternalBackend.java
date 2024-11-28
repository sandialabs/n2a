/*
Copyright 2013-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.jobs.NodeJob;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class InternalBackend extends Backend
{
    @Override
    public String getName ()
    {
        return "Internal";
    }

    @Override
    public void start (MNode job)
    {
        Thread simulationThread = new SimulationThread (job);
        simulationThread.setDaemon (true);
        simulationThread.start ();
    }

    public SimulationThread getThread (MNode job)
    {
        Thread[] threads = new Thread[Thread.activeCount ()];
        int count = Thread.enumerate (threads);
        for (int i = 0; i < count; i++)
        {
            Thread t = threads[i];
            if (t instanceof SimulationThread)
            {
                SimulationThread s = (SimulationThread) t;
                if (s.job == job) return s;
            }
        }
        return null;
    }

    @Override
    public boolean isAlive (MNode job)
    {
        SimulationThread s = getThread (job);
        return  s != null  &&  s.isAlive ();
    }

    @SuppressWarnings("removal")
    @Override
    public void kill (MNode job, boolean force)
    {
        SimulationThread s = getThread (job);
        if (s == null) return;
        if (s.simulator == null) return;
        if (force) s.stop ();
        else       s.simulator.stop = true;
    }

    public class SimulationThread extends Thread
    {
        MNode job;
        Simulator simulator;

        public SimulationThread (MNode job)
        {
            super ("Internal Simulation");
            this.job = job;
        }

        public void run ()
        {
            Path localJobDir = Host.getJobDir (Host.getLocalResourceDir (), job);
            try {err.set (new PrintStream (new FileOutputStream (localJobDir.resolve ("err").toFile (), true), false, "UTF-8"));}
            catch (Exception e) {}

            long startTime = 0;
            long stopTime  = 0;
            try
            {
                job.set ("Preparing", "status");
                job.set (System.currentTimeMillis (), "started");

                MNode model = NodeJob.getModel (job);
                EquationSet digestedModel = new EquationSet (model);
                digestModel (digestedModel);
                prepareToRun (digestedModel);
                Files.copy (new ByteArrayInputStream (digestedModel.dump (false).getBytes ("UTF-8")), localJobDir.resolve ("model.flat"));
                //dumpBackendData (digestedModel);

                // Any new metadata generated after MPart is collated must be injected back into job
                String duration = digestedModel.metadata.get ("duration");
                if (! duration.isEmpty ()) job.set (duration, "duration");

                long seed = model.getOrDefault (System.currentTimeMillis (), "$meta", "seed");
                job.set (seed, "seed");

                simulator = new Simulator (new Wrapper (digestedModel), seed, localJobDir);
                String e = model.get ("$meta", "backend", "all", "event");
                switch (e)
                {
                    case "before":
                        simulator.during    = false;
                        simulator.sortEvent = -1;
                        break;
                    case "after":
                        simulator.during    = false;
                        simulator.sortEvent = 1;
                    default:  // during
                        simulator.during    = true;  // Use latch-type spike events.
                        simulator.sortEvent = -1;  // Spike events come before step events, so that latches can be set before update() is called.
                }

                job.clear ("status");
                startTime = System.nanoTime ();
                simulator.init ();
                simulator.run ();  // Does not return until simulation is finished.
                stopTime = System.nanoTime ();
                if (simulator.stop  &&  ! simulator.fastExit)
                {
                    Files.copy (new ByteArrayInputStream ("killed" .getBytes ("UTF-8")), localJobDir.resolve ("finished"));
                }
                else
                {
                    Files.copy (new ByteArrayInputStream ("success".getBytes ("UTF-8")), localJobDir.resolve ("finished"));
                }
            }
            catch (Exception e)
            {
                stopTime = System.nanoTime ();
                if (e instanceof AbortRun)
                {
                    String message = e.getMessage ();
                    if (message != null) err.get ().println (message);
                }
                else e.printStackTrace (err.get ());

                try {Files.copy (new ByteArrayInputStream ("failure".getBytes ("UTF-8")), localJobDir.resolve ("finished"));}
                catch (Exception f) {}

                if (simulator != null) simulator.closeStreams ();
            }
            long elapsedTime = 0;
            if (startTime != 0  &&  stopTime != 0) elapsedTime = stopTime - startTime;

            PrintStream e = err.get ();
            e.println ("Execution time: " + elapsedTime / 1e9 + " seconds");
            if (e != System.err) e.close ();
            // Both the err stream and the simulator object are held in thread-local storage.
            // This thread is now ending, so it should become eligible for garbage collection.
        }
    }

    @Override
    public double currentSimTime (MNode job)
    {
        SimulationThread s = getThread (job);
        if (s != null  &&  s.simulator != null  &&  s.simulator.currentEvent != null) return s.simulator.currentEvent.t;
        return 0;
    }

    public interface Analyzer
    {
        public void analyzeMiddle (EquationSet e);
    }

    /**
        Utility function to enable other backends to use Internal to prepare static network structures.
        @return A Simulator object which contains the constructed network.
    **/
    public static Simulator constructStaticNetwork (EquationSet e, Analyzer a) throws Exception
    {
        digestModel (e);
        if (a != null) a.analyzeMiddle (e);
        prepareToRun (e);
        long seed = e.metadata.getOrDefault (System.currentTimeMillis (), "seed");
        Simulator result = new Simulator (new Wrapper (e), seed);
        result.init ();
        return result;
    }

    public static void digestModel (EquationSet e) throws Exception
    {
        String backend = e.metadata.getOrDefault ("internal", "backend");

        if (e.source.containsKey ("pin"))  // crude heuristic that may save some time for regular (non-dataflow) models
        {
            e.collectPins ();
            e.fillAutoPins ();
            e.resolvePins ();
            e.purgePins ();
        }
        e.resolveConnectionBindings ();
        e.addGlobalConstants ();
        e.addSpecials ();  // $connect, $index, $init, $n, $t, $t'
        e.addAttribute ("global",       false, true,  "$max", "$min", "$k", "$radius");
        e.addAttribute ("global",       false, false, "$n");
        e.addAttribute ("state",        true,  false, "$n");  // Forbid $n from being temporary, even if it meets the criteria.
        e.addAttribute ("preexistent",  true,  false, "$t'", "$t");  // variables that are not stored because Instance.get/set intercepts them
        e.addAttribute ("readOnly",     true,  false, "$t");
        e.addAttribute ("externalRead", false, false, "$type");  // Force $type to be double-buffered. Because this is combined with REPLACE, we need to explicitly zero $type in finish().
        e.resolveLHS ();
        e.fillIntegratedVariables ();
        e.findIntegrated ();
        e.resolveRHS ();
        e.flatten (backend);
        e.findExternal ();
        e.sortParts ();
        e.checkUnits ();
        e.findConstants ();
        e.determineTraceVariableName ();
        e.collectSplits ();
        e.addDrawDependencies ();
        e.removeUnused ();  // especially get rid of unneeded $variables created by addSpecials()
        e.findAccountableConnections ();
        e.findTemporary ();
        e.determineOrder ();
        e.findDerivative ();
        e.findInitOnly ();
        e.findDeath ();
        e.determinePoll ();
        e.purgeInitOnlyTemporary ();
        e.setAttributesLive ();
        e.forceTemporaryStorageForSpecials ();
        e.determineTypes ();
        e.findConnectionMatrix ();
        e.determineDuration ();
    }

    /**
        Separate from digestModel() so that other backends can use an alternate/abbreviated compilation
        process, but still use the Internal backend to run the init cycle.
    **/
    public static void prepareToRun (EquationSet e)
    {
        createBackendData (e);
        analyzeEvents (e);
        analyze (e);
        analyzeConversions (e);
        analyzeLastT (e);
        e.clearVariables ();
        e.assignParents ();
    }

    public static void createBackendData (EquationSet s)
    {
        Object o = s.backendData;
        if (! (o instanceof InternalBackendData))
        {
            InternalBackendData bed = new InternalBackendData (s);
            s.backendData = bed;
            bed.backendData = o;  // Chain backend data, rather than simply blowing it away. This works even if o is null.
        }
        for (EquationSet p : s.parts) createBackendData (p);
    }

    public static void analyzeEvents (EquationSet s) throws Backend.AbortRun
    {
        InternalBackendData bed = (InternalBackendData) s.backendData;
        bed.analyzeEvents (s);
        for (EquationSet p : s.parts) analyzeEvents (p);
    }

    public static void analyze (EquationSet s)
    {
        InternalBackendData bed = (InternalBackendData) s.backendData;
        bed.analyze (s);
        for (EquationSet p : s.parts) analyze (p);
    }

    public static void analyzeConversions (EquationSet s)
    {
        InternalBackendData bed = (InternalBackendData) s.backendData;
        bed.analyzeConversions (s);
        for (EquationSet p : s.parts) analyzeConversions (p);
    }

    public static void analyzeLastT (EquationSet s)
    {
        InternalBackendData bed = (InternalBackendData) s.backendData;
        bed.analyzeLastT (s);
        for (EquationSet p : s.parts) analyzeLastT (p);
    }

    public void dumpBackendData (EquationSet s)
    {
        System.out.println ("Backend data for: " + (s.container == null ? s.name : s.prefix ()));
        ((InternalBackendData) s.backendData).dump ();
        for (EquationSet p : s.parts) dumpBackendData (p);
    }
}
