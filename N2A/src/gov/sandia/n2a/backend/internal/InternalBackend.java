/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.parms.Parameter;
import gov.sandia.n2a.parms.ParameterDomain;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class InternalBackend extends Backend
{
    @Override
    public String getName ()
    {
        return "Internal";
    }

    @Override
    public ParameterDomain getSimulatorParameters ()
    {
        ParameterDomain result = new ParameterDomain ();
        result.addParameter (new Parameter ("duration",            "1.0"  ));  // default is 1 second
        result.addParameter (new Parameter ("internal.integrator", "Euler"));  // alt is "RungeKutta"
        return result;
    }

    @Override
    public void start (MNode job)
    {
        Thread simulationThread = new SimulationThread (job);
        simulationThread.setDaemon (true);
        simulationThread.start ();
    }

    @Override
    public void kill (MNode job)
    {
        Thread[] threads = new Thread[Thread.activeCount ()];
        int count = Thread.enumerate (threads);
        for (int i = 0; i < count; i++)
        {
            Thread t = threads[i];
            if (t instanceof SimulationThread)
            {
                SimulationThread s = (SimulationThread) t;
                if (s.job != job) continue;
                if (s.simulator != null) s.simulator.stop = true;
                return;
            }
        }
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
            Path jobDir = Paths.get (job.get ()).getParent ();  // assumes the MNode "job" is really an MDoc. In any case, the value of the node should point to a file on disk where it is stored in a directory just for it.
            try {err.set (new PrintStream (new FileOutputStream (jobDir.resolve ("err").toFile (), true), false, "UTF-8"));}
            catch (Exception e) {}

            long elapsedTime = 0;
            try
            {
                Files.createFile (jobDir.resolve ("started"));
                EquationSet digestedModel = new EquationSet (job);
                digestModel (digestedModel);
                Files.copy (new ByteArrayInputStream (digestedModel.dump (false).getBytes ("UTF-8")), jobDir.resolve ("model.flat"));
                //dumpBackendData (digestedModel);

                // Any new metadata generated after MPart is collated must be injected back into job
                String duration = digestedModel.metadata.get ("duration");
                if (! duration.isEmpty ()) job.set (duration, "$metadata", "duration");

                long seed = job.getOrDefault (-1l, "$metadata", "seed");
                if (seed < 0)
                {
                    seed = System.currentTimeMillis ();
                    job.set (seed, "$metadata", "seed");
                }

                String e = job.get ("$metadata", "backend", "internal", "event");
                int                      eventMode = Simulator.DURING;
                if (e.equals ("after"))  eventMode = Simulator.AFTER;
                if (e.equals ("before")) eventMode = Simulator.BEFORE;

                simulator = new Simulator (new Wrapper (digestedModel), seed, jobDir);
                simulator.eventMode = eventMode;
                elapsedTime = System.nanoTime ();
                simulator.init ();
                simulator.run ();  // Does not return until simulation is finished.
                elapsedTime = System.nanoTime () - elapsedTime;
                if (simulator.stop) Files.copy (new ByteArrayInputStream ("killed" .getBytes ("UTF-8")), jobDir.resolve ("finished"));
                else                Files.copy (new ByteArrayInputStream ("success".getBytes ("UTF-8")), jobDir.resolve ("finished"));
            }
            catch (Exception e)
            {
                if (! (e instanceof AbortRun)) e.printStackTrace (err.get ());

                try {Files.copy (new ByteArrayInputStream ("failure".getBytes ("UTF-8")), jobDir.resolve ("finished"));}
                catch (Exception f) {}
            }

            PrintStream e = err.get ();
            e.println ("Execution time: " + elapsedTime / 1e9 + " seconds");
            if (e != System.err)
            {
                e.close ();
                err.remove ();
            }
        }
    }

    @Override
    public double currentSimTime (MNode job)
    {
        Thread[] threads = new Thread[Thread.activeCount ()];
        int count = Thread.enumerate (threads);
        for (int i = 0; i < count; i++)
        {
            Thread t = threads[i];
            if (t instanceof SimulationThread)
            {
                SimulationThread s = (SimulationThread) t;
                if (s.job != job) continue;
                if (s.simulator != null  &&  s.simulator.currentEvent != null) return s.simulator.currentEvent.t;
                return 0;
            }
        }
        return 0;
    }

    public static double getSimTimeFromOutput (MNode job)
    {
        Path jobDir = Paths.get (job.get ()).getParent ();
        Path out = jobDir.resolve ("out");
        return getSimTimeFromOutput (out);
    }

    /**
        Assumes that $t is output in first column.
        Note that this does not hold true for Xyce.
    **/
    public static double getSimTimeFromOutput (Path out)
    {
        double result = 0;
        try (RandomAccessFile raf = new RandomAccessFile (out.toFile (), "r"))
        {
            long lineLength = 16;  // Initial guess. About long enough to catch two columns. Smaller initial value gives more accurate result, but costs more in terms of repeated scans.
            while (true)
            {
                String column = "";
                boolean gotNL = false;
                boolean gotTab = false;
                long length = raf.length ();
                if (length < lineLength) break;
                raf.seek (length - lineLength);
                for (long i = 0; i < lineLength; i++)
                {
                    char c = (char) raf.read ();  // Technically, the file is in UTF-8, but this will only matter in column headings. We are looking for a float string, which will be in all lower ASCII.
                    if (c == '\n'  ||  c == '\r')
                    {
                        gotNL = true;
                        continue;
                    }
                    if (gotNL)
                    {
                        if (c == '\t')
                        {
                            gotTab = true;
                            break;
                        }
                        column = column + c;
                    }
                }
                if (gotNL  &&  gotTab)
                {
                    result = Double.parseDouble (column);
                    break;
                }
                lineLength *= 2;
            }
        }
        catch (Exception e) {}
        return result;
    }

    /**
        Utility function to enable other backends to use Internal to prepare static network structures.
        @return A Simulator object which contains the constructed network.
    **/
    public static Simulator constructStaticNetwork (EquationSet e) throws Exception
    {
        digestModel (e);
        long seed = e.metadata.getOrDefault (0l, "seed");
        Simulator result = new Simulator (new Wrapper (e), seed);
        result.init ();
        return result;
    }

    public static void digestModel (EquationSet e) throws Exception
    {
        String backend = e.metadata.getOrDefault ("internal", "backend");
        if (backend.isEmpty ()) backend = "none";  // Should not match any backend metadata entries, since they are all supposed to start with "backend".
        else                    backend = "backend." + backend;

        e.resolveConnectionBindings ();
        e.flatten (backend);
        e.addGlobalConstants ();
        e.addSpecials ();  // $connect, $index, $init, $n, $t, $t'
        e.addAttribute ("global",        0, false, new String[] {"$max", "$min", "$k", "$n", "$radius"});
        e.addAttribute ("preexistent",   0, true,  new String[] {"$t'", "$t"});  // variables that are not stored because Instance.get/set intercepts them
        e.addAttribute ("readOnly",      0, true,  new String[] {"$t"});
        e.addAttribute ("externalWrite", 0, true,  new String[] {"$type"});  // force $type to be double-buffered, with reset to zero each cycle
        e.resolveLHS ();
        e.fillIntegratedVariables ();
        e.findIntegrated ();
        e.resolveRHS ();
        e.sortParts ();
        e.checkUnits ();
        e.findConstants ();
        e.determineTraceVariableName ();
        e.collectSplits ();
        e.findDeath ();
        e.removeUnused ();  // especially get rid of unneeded $variables created by addSpecials()
        e.findAccountableConnections ();
        e.findTemporary ();
        e.determineOrder ();
        e.findDerivative ();
        e.makeConstantDtInitOnly ();
        e.findInitOnly ();
        e.purgeInitOnlyTemporary ();
        e.setAttributesLive ();
        e.forceTemporaryStorageForSpecials ();
        e.determineTypes ();
        e.findConnectionMatrix ();
        e.determineDuration ();

        prepareToRun (e);
    }

    public static void prepareToRun (EquationSet e)
    {
        createBackendData (e);
        analyzeEvents (e);
        analyze (e);
        analyzeConversions (e);
        analyzeLastT (e);
        e.clearVariables ();
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
        System.out.println ("Backend data for: " + s.name);
        ((InternalBackendData) s.backendData).dump ();
        for (EquationSet p : s.parts) dumpBackendData (p);
    }
}
