/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.operator.LE;
import gov.sandia.n2a.language.operator.LT;
import gov.sandia.n2a.language.parse.ParseException;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.plugins.extpoints.Backend;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
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
    public void execute (MNode job)
    {
        Thread simulationThread = new SimulationThread (job);  // ctor starts the thread, if construction is successful
        simulationThread.setDaemon (true);
        simulationThread.start ();
    }

    public class SimulationThread extends Thread
    {
        MNode job;

        public SimulationThread (MNode job)
        {
            this.job = job;
        }

        public void run ()
        {
            String jobDir = new File (job.get ()).getParent ();  // assumes the MNode "job" is really an MDoc. In any case, the value of the node should point to a file on disk where it is stored in a directory just for it.
            try {err.set (new PrintStream (new File (jobDir, "err")));}
            catch (FileNotFoundException e) {}

            try
            {
                Files.createFile (Paths.get (jobDir, "started"));
                EquationSet digestedModel = new EquationSet (job);
                digestModel (digestedModel, jobDir);
                Files.copy (new ByteArrayInputStream (digestedModel.dump (false).getBytes ("UTF-8")), Paths.get (jobDir, "model.flat"));
                //dumpBackendData (digestedModel);
                Simulator simulator = new Simulator (new Wrapper (digestedModel));
                simulator.run ();  // Does not return until simulation is finished.
                Files.copy (new ByteArrayInputStream ("success".getBytes ("UTF-8")), Paths.get (jobDir, "finished"));
            }
            catch (Exception e)
            {
                if (! (e instanceof AbortRun)) e.printStackTrace (err.get ());

                try
                {
                    Files.copy (new ByteArrayInputStream ("failure".getBytes ("UTF-8")), Paths.get (jobDir, "finished"));
                }
                catch (Exception f) {}
            }

            PrintStream e = err.get ();
            if (e != System.err)
            {
                e.close ();
                err.remove ();
            }
        }
    }

    @Override
    public double expectedDuration (MNode job)
    {
        return getDurationFromP (job);
    }

    @Override
    public double currentSimTime (MNode job)
    {
        return getSimTimeFromOutput (job);
    }

    public static double getDurationFromP (MNode job)
    {
        String p = job.get ("$p");
        if (p.isEmpty ()) return 1;  // We assume that job got augmented with a default setting of "$p=$t<1"
        Operator expression = null;
        try
        {
            expression = Operator.parse (p);
            if (expression instanceof LT  ||  expression instanceof LE)
            {
                OperatorBinary comparison = (OperatorBinary) expression;
                if (comparison.operand0 instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) comparison.operand0;
                    if (av.name.equals ("$t"))
                    {
                        if (comparison.operand1 instanceof Constant)
                        {
                            Constant c = (Constant) comparison.operand1;
                            if (c.value instanceof Scalar) return ((Scalar) c.value).value;
                        }
                    }
                }
            }
        }
        catch (ParseException e)
        {
        }
        return 0;
    }

    /**
        Assumes that $t is output in first column.
        Note that this does not hold true for Xyce.
    **/
    public static double getSimTimeFromOutput (MNode job)
    {
        double result = 0;
        File out = new File (new File (job.get ()).getParentFile (), "out");
        RandomAccessFile raf;
        try
        {
            raf = new RandomAccessFile (out, "r");
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

            raf.close ();
        }
        catch (Exception e)
        {
        }
        return result;
    }

    /**
        Utility function to enable other backends to use Internal to prepare static network structures.
        @return An Euler (simulator) object which contains the constructed network.
    **/
    public static Simulator constructStaticNetwork (EquationSet e, String jobDir) throws Exception
    {
        digestModel (e, jobDir);
        return new Simulator (new Wrapper (e));
    }

    public static void digestModel (EquationSet e, String jobDir) throws Exception
    {
        // Make paths relative to job directory
        System.setProperty ("user.dir", new File (jobDir).getAbsolutePath ());

        e.resolveConnectionBindings ();
        e.flatten ();
        e.addSpecials ();  // $index, $init, $live, $n, $t, $t', $type
        e.fillIntegratedVariables ();
        e.findIntegrated ();
        e.resolveLHS ();
        e.resolveRHS ();
        e.determineTraceVariableName ();
        e.findConstants ();
        e.removeUnused ();  // especially get rid of unneeded $variables created by addSpecials()
        e.collectSplits ();
        e.findAccountableConnections ();
        e.findTemporary ();
        e.determineOrder ();
        e.findDerivative ();
        e.addAttribute ("global",      0, false, new String[] {"$max", "$min", "$k", "$n", "$radius"});
        e.addAttribute ("preexistent", 0, true,  new String[] {"$t'", "$t"});  // variables that are not stored because Instance.get/set intercepts them
        e.addAttribute ("readOnly",    0, true,  new String[] {"$t"});
        // We don't really need the "simulator" attribute, because it has no impact on the behavior of Internal
        e.makeConstantDtInitOnly ();
        e.findInitOnly ();
        e.findDeath ();
        e.setAttributesLive ();
        e.forceTemporaryStorageForSpecials ();
        e.determineTypes ();

        createBackendData (e);
        analyzeEvents (e);
        analyze (e);
        clearVariables (e);
    }

    public static void createBackendData (EquationSet s)
    {
        if (! (s.backendData instanceof InternalBackendData)) s.backendData = new InternalBackendData ();
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
        bed.analyzeLastT (s);
    }

    public static void clearVariables (EquationSet s)
    {
        for (EquationSet p : s.parts) clearVariables (p);
        for (Variable v : s.variables)
        {
            if (! v.hasAttribute ("constant")) v.type = v.type.clear ();  // So we can use these as backup when stored value is null.
        }
    }

    public void dumpBackendData (EquationSet s)
    {
        System.out.println ("Backend data for: " + s.name);
        ((InternalBackendData) s.backendData).dump ();
        for (EquationSet p : s.parts) dumpBackendData (p);
    }
}
