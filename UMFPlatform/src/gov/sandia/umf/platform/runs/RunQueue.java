/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.runs;

import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ensemble.params.ParameterSet;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.PlatformRecord;
import gov.sandia.umf.platform.plugins.Run;
import gov.sandia.umf.platform.plugins.RunEnsemble;
import gov.sandia.umf.platform.plugins.RunState;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.Timer;

import org.apache.log4j.Logger;

import replete.plugins.PluginManager;
import replete.xstream.XStreamWrapper;


public class RunQueue {


    ////////////
    // FIELDS //
    ////////////

    public static final int MAX = 3;

    private CheckerThread checkerThread;
    private Timer timer;
    private UIController uiController;
//    private Map<PlatformRecord, List<RunEnsemble>> toCheck;
    private static Logger logger = Logger.getLogger(RunQueue.class);
    private LinkedList<Simulation> simsToRun;


    ///////////////
    // SINGLETON //
    ///////////////

    private static RunQueue instance;
    public static RunQueue getInstance() {
        if(instance == null) {
            instance = new RunQueue();
        }
        return instance;
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    private RunQueue() {
//        toCheck = new LinkedHashMap<PlatformRecord, List<RunEnsemble>>();
        simsToRun = new LinkedList<Simulation>();
        checkerThread = new CheckerThread();
        checkerThread.start();
        timer = new Timer(5000, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                checkerThread.unpause();
//                System.out.println("Checking...");
            }
        });
        timer.start();
    }

    public void setUiController(UIController uic) {
        uiController = uic;
    }

    public void submitEnsemble(PlatformRecord model, RunEnsemble re) {
        // Create all the Simulations here; keep queue of Simulations to submit
        prepareRuns(model, re);
        // TODO - how do we want to manage checking for submissions?
        // Next line won't unpause thread until all sims have been created
        // but Timer wakes it up every 5 seconds, so we could
        // start submitting early sims while preparing later ones
        // except that doesn't seem to be happening - run isn't checking during prepareRuns
        // could have prepareRuns call unpause after it's created one or more Simulations
        checkerThread.unpause();
    }

//    public NDoc submitEnsemble(NDoc modelCopySource, String label,
//            ExecutionEnv env, Simulator sim, ParameterSpecGroupSet groups,
//            List<String> outputExpressions) {
//
//        NDoc doc = new NDoc("gov.sandia.umf.platform$RunEnsemble");
//        doc.set("templateModel", modelCopySource);
//        doc.set("label", label);
//        doc.set("environment", env.toString());
//        doc.set("simulator", PluginManager.getExtensionId(sim));
//        doc.set("paramSpecs", XStreamWrapper.writeToString(groups));
//        doc.set("outputExpressions", outputExpressions);
//        doc.set("runCount", groups.getRunCount());
//        doc.save();
//        doc.dumpDebug("submitEns");
//
//        checkerThread.unpause();
//
//        return doc;
//    }

    private class CheckerThread extends Thread {
        private boolean paused = true;
        private boolean stopped = false;
        public synchronized void unpause() {
            paused = false;
            notifyAll();
        }
        private synchronized void checkPaused() {
            while(paused) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
        }
        public synchronized void pause() {
            paused = true;
        }
        public synchronized void stopThread() {
            timer.stop();
            paused = false;
            stopped = true;
            notifyAll();
        }

        // CheckerThread now responsible only for submitting Simulations in queue
        // as resources become available; Simulations are created and prepared elsewhere
        @Override
        public void run() {
            while(!stopped) {
                checkPaused();
                if(stopped) {
                    break;
                }
                try {
                    if (!simsToRun.isEmpty()) {
                        if (simsToRun.peek().resourcesAvailable()) {
                            logger.debug(System.currentTimeMillis() + " submitting run");
                            submitRun(simsToRun.remove());
                        }
                    }
                    // pause after each pass, regardless of whether run submitted or not
                    // Timer will wake Thread up again
                    paused=true;
                    // TODO - temporarily doing away with use of DB to figure out 
                    // what needs to be run; using toCheck map updated by alternate
                    // submitEnsemble instead
//                    OrientDatasource ds = uiController.getDMM().getDataModel();
//                    List<NDoc> dEnss = ds.getAll(new RunEnsembleRecordHandler().getHandledRecordTypes()[0]);
//                    Map<PlatformRecord, List<PendingRun>> runMe = 
//                            new LinkedHashMap<PlatformRecord, List<PendingRun>>();
//                    int enqueued = 0;   //int x = 0;
//                    for (PlatformRecord model : toCheck.keySet()) {
//                      for (RunEnsemble re : toCheck.get(model)) {  
//                    for(NDoc dEns : dEnss) {
//                        try {
//                            dEns.save();
//                        } catch(Exception e) {
//                            dEns.dumpDebug("lookup loop["+(x++)+"]");
//                            System.out.println("ERROR - " + StringUtil.max(dEns.toString(), 60));
//                            throw e;
//                        }
//                        List<Run> runs = re.getRuns();
//                        long runCount = re.getFrameworkRunCount();
//                        if(runCount > runs.size()) {
//                            int running = 0;
                            // TODO Need to find out how many runs are currently running.
                            // for now, assume all runs were submitted this session; just need to find out which finished
//                            for(NDoc run : runs) {
//                                String state = run.getAndSetValid("state", "unknown", String.class);
//                                if(state.equals("running")) {
//                                    // might have to look into the run directories of each run to look for goober files.
//                                }
//                            }
                            //
//                            enqueued += running;
//                            List<PendingRun> ensembleRuns = new ArrayList<PendingRun>();
//                            runMe.put(model, ensembleRuns);
//                            // kick off more runs for this ensemble.
//                            for(int i = runs.size(); enqueued < MAX && i < runCount; i++) {
//                                PendingRun run = new PendingRun(re, i);
//                                ensembleRuns.add(run);
//                                enqueued++;
//                            }
//                            if(enqueued == MAX) {
//                                break;
//                            }
//                        }
//                      }
//                    }
//                    if(enqueued > 0) {
//                        runThese(runMe);
//                    }
//                    pause();
                } catch(Exception e) {
                    e.printStackTrace();
                    stopThread();
                }
            }
        }
    }

    private PendingRunEnsemble convertToDomainObjects(NDoc dEnsemble) {
        NDoc templateModel = dEnsemble.get("model");
        String label = dEnsemble.get("label");
        String envName = dEnsemble.get("environment");
        String simType = dEnsemble.get("simulator");

        ExecutionEnv chosenEnv = null;
        for(ExecutionEnv env : ExecutionEnv.envs) {
            if(envName.equals(env.getNamedValue("name"))) {
                chosenEnv = env;
                break;
            }
        }
        if(chosenEnv == null) {
            // error
        }

        Simulator chosenSim = (Simulator) PluginManager.getExtensionById(simType);
        if(chosenSim == null) {
            // error
        }

        ParameterSpecGroupSet groups = convert((String) dEnsemble.get("paramSpecs"));
        List<String> output = dEnsemble.get("outputExpressions");

        return new PendingRunEnsemble(templateModel, label, chosenEnv,
            chosenSim, groups, output);
    }

    private ParameterSpecGroupSet convert(String paramSpecs) {
        return (ParameterSpecGroupSet) XStreamWrapper.loadTargetFromString(paramSpecs);
    }

    public void stop() {
        checkerThread.stopThread();
        timer.stop();
    }

    private void prepareRuns(PlatformRecord model, RunEnsemble re)
    {
        int runNum = 0;
        for(ParameterSet set : re.getGroups().generateAllSetsFromSpecs(false)) {
            ParameterSet modelParamSet = set.subset("Model");
            ParameterSet simParamSet = set.subset("Simulator");
            modelParamSet.sliceKeyPathKeys();
            simParamSet.sliceKeyPathKeys();

            Run run = model.addRun(modelParamSet, re);
            re.addRun(run);

            Simulation simulation = re.getSimulator().createSimulation();
            ParameterDomain domain = new ParameterDomain(simParamSet);
            simulation.setSelectedParameters(domain);
            try {
                RunState runState;
                logger.debug(System.currentTimeMillis() + " before simulation.prepare " +
                        runNum++);
                runState = simulation.prepare(run, re.getSimHandledGroups(), re.getEnvironment());
                run.setState(XStreamWrapper.writeToString(runState));
                run.save();
            } catch(Exception e1) {
                UMF.handleUnexpectedError(null, e1, "Could not create the simulation.  An error occurred.");
            }
            simsToRun.add(simulation);
        }
    }

    public void runThese(Map<PlatformRecord, List<PendingRun>> runMe) throws Exception {
        for(PlatformRecord record : runMe.keySet()) {
            final PlatformRecord model = record;
            List<PendingRun> ensembleRuns = runMe.get(record);
            for(PendingRun pRun : ensembleRuns) {
                System.out.println("preparing run " + pRun.getRun());
                final PendingRun fRun = pRun;
                final RunEnsemble re = pRun.getEnsemble();

                // These are just the parameter sets that the framework manages
                ParameterSet set = fRun.getEnsemble().getParameterSets().get(fRun.getRun());
                ParameterSet modelSet = set.subset("Model");
                final ParameterSet simSet = set.subset("Simulator");
                modelSet.sliceKeyPathKeys();
                simSet.sliceKeyPathKeys();

                logger.debug(System.currentTimeMillis() + " run " + fRun.getRun() +
                        " runThese before model.addRun ");
                final Run run = model.addRun(modelSet, re);
                logger.debug(System.currentTimeMillis() + " run " + fRun.getRun() +
                        " runThese before re.addRun ");
                re.addRun(run);

                System.out.println("submitting run #" + fRun.getRun());

                Thread t = new Thread(new Runnable() {
                    public void run() {
                        Simulation simulation = re.getSimulator().createSimulation();
                        ParameterDomain domain = new ParameterDomain(simSet);
                        logger.debug(System.currentTimeMillis() + " run " + fRun.getRun() +
                                " run Thread before sim.setSelectedParameters ");
                        simulation.setSelectedParameters(domain);
                        try {
                            logger.debug(System.currentTimeMillis() + " run " + fRun.getRun() +
                                    " run Thread before sim.execute ");
                            RunState runState = simulation.execute(run, fRun.getEnsemble().getSimHandledGroups(), 
                                    re.getEnvironment());
                            run.setState(XStreamWrapper.writeToString(runState));
                            run.save();
                        } catch(Exception e1) {
                            UMF.handleUnexpectedError(null, e1, "Could not create the run.  An error occurred.");
                        }
                             }
                });
                t.start();
            }
        }
    }

    private void submitRun(final Simulation sim) 
    {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    sim.submit();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }
}
