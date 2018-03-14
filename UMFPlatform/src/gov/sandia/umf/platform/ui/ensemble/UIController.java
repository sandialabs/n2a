/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.execenvs.HostSystem;
import gov.sandia.n2a.parms.ParameterSpecification;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.umf.platform.ensemble.params.groups.ParameterSpecGroup;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.ui.ensemble.run.CreateRunEnsembleDialog;

import java.awt.Component;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import replete.gui.windows.Dialogs;
import replete.util.ReflectionUtil;


public class UIController
{
    public boolean prepareAndSubmitRunEnsemble(Component parentComponent, MNode model) throws Exception {

        // Set up simulators.
        List<ExtensionPoint> simEP = PluginManager.getExtensionsForPoint(Backend.class);
        Backend[] simulators = new Backend[simEP.size()];
        int s = 0;
        for(ExtensionPoint ep : simEP) {
            simulators[s++] = (Backend) ep;
        }

        // Set up execution environments.
        HostSystem[] envs = HostSystem.envs.toArray(new HostSystem[0]);

        // TODO: Fix this with appropriate interfaces.
        String name = (String) ReflectionUtil.invoke("getName", model);
        String owner = (String) ReflectionUtil.invoke("getOwner", model);

        CreateRunEnsembleDialog dlg = new CreateRunEnsembleDialog(
            (JFrame) SwingUtilities.getRoot(parentComponent),
            -1, // TODO change from -1
            /*TEMP*/ name, owner, 12342347483L,/*TEMP until appropriate interfaces*/
            model, simulators, simulators[0], envs, envs[0], false);

        dlg.setVisible(true);

        if(dlg.getResult() == CreateRunEnsembleDialog.CREATE) {

            String label = dlg.getLabel();
            HostSystem env = dlg.getEnvironment();
            Backend simulator = dlg.getSimulator();
            ParameterSpecGroupSet groups = dlg.getParameterSpecGroupSet();
            ParameterSpecGroupSet simHandledGroups;
            try {
                // next line modifies groups to remove any that the Simulator will handle
                simHandledGroups = divideEnsembleParams(model, groups, simulator);
            } catch (RuntimeException e) {
                Dialogs.showDetails(MainFrame.instance, "could not create run ensemble", e);
                return false;
            }
            List<String> outputExpressions = dlg.getSelectedOutputExpressions();

            //logger.debug(System.currentTimeMillis() + " calling addRunEnsemble");
            //RunEnsemble re = model.addRunEnsemble(label,
            //        env.toString(), PluginManager.getExtensionId(simulator),
            //        groups, simHandledGroups, outputExpressions);

            // TODO - submit to RunQueue here and have it take care of the rest
            //RunQueue runQueue = RunQueue.getInstance();
            //runQueue.submitEnsemble(model, re);
/*            
            int runNum = 0;
            for(ParameterSet set : groups.generateAllSetsFromSpecs(false)) {
                ParameterSet modelParamSet = set.subset("Model");
                ParameterSet simParamSet = set.subset("Simulator");
                modelParamSet.sliceKeyPathKeys();
                simParamSet.sliceKeyPathKeys();

                Run run = model.addRun(modelParamSet, re);
                re.addRun(run);

                Simulation simulation = simulator.createSimulation();
                ParameterDomain domain = new ParameterDomain(simParamSet);
                simulation.setSelectedParameters(domain);
                try {
                    RunState runState;
                    logger.debug(System.currentTimeMillis() + " before execute for run " +
                            runNum++);
                    // quick and dirty attempt at batch script version of doing ensemble
                    runState = simulation.prepare(run, simHandledGroups, env);
//                    runState = simulation.execute(run, simHandledGroups, env);
                    run.setState(XStreamWrapper.writeToString(runState));
                    run.save();
                } catch(Exception e1) {
                    UMF.handleUnexpectedError(null, e1, "Could not create the run.  An error occurred.");
                    return false;
                }
            }
            env.submitBatch(re);
 */
            return true;
        }

        return false;
    }
    
    // Any group in origSet for which the Simulator can handle parameterization
    // is removed from origSet and added to the returned set
    private ParameterSpecGroupSet divideEnsembleParams(MNode model,
            ParameterSpecGroupSet origSet, 
            Backend sim) {
        // Three cases:
        // 1) framework handles all in group
        // 2) simulator handles all in group
        // 3) (changed) sim can only handle some of group; so have framework handle group instead
        ParameterSpecGroupSet result = new ParameterSpecGroupSet();
        for (ParameterSpecGroup group : origSet) {
            if (group == origSet.getDefaultValueGroup()) {
                // don't want to transfer default value group to simulator groups
                continue;
            }
            int numHandled = 0;
            ParameterSpecification spec = null;
            Object errorKey = null;
            for (Object key : group.keySet()) {
                spec = group.get(key);
                if (sim.canHandleRunEnsembleParameter(model, key, spec)) {
                    numHandled++;
                }
                else if (numHandled != 0) {
                    errorKey = key;
                    break;
                }
            }
            if (numHandled != group.size() && numHandled != 0) {
                System.out.println("this simulator cannot handle '" + errorKey + 
                        "' with specification '" + spec.getShortName());
            }
            else if (numHandled != 0) {
                result.add(group);
            }
        }
        for (ParameterSpecGroup group : result) {
            origSet.remove(group);
        }
        return result;
    }

//    public void submitRunEnsemble(PlatformRecord model, String label, HostSystem env,
//                                  Simulator simulator, ParameterSpecGroupSet groups,
//                                  List<String> outputExpressions) {
//
//        // Copy model record for frozen run ensemble template model.
//        PlatformRecord modelCopy = model.copy();
//        NDoc modelCopySource = modelCopy.getSource();
//        modelCopySource.set("copied-from", model.getSource());
//        modelCopySource.set("model-template-for-run-ensemble", true);
//        modelCopySource.save();
//
//        // Submit the template model and all of the dialog outputs to the run queue.
//        RunQueue runQueue = RunQueue.getInstance();
////        NDoc reDoc = runQueue.submitEnsemble(modelCopySource, label,
////            env, simulator, groups, outputExpressions);
//
//        NDoc doc = new NDoc("gov.sandia.umf.platform$RunEnsemble");
//        doc.set("templateModel", modelCopySource);
//        doc.set("label", label);
//        doc.set("environment", env.toString());
//        doc.set("simulator", PluginManager.getExtensionId(simulator));
//        doc.set("paramSpecs", XStreamWrapper.writeToString(groups));
//        doc.set("outputExpressions", outputExpressions);
//        doc.set("runCount", groups.getRunCount());
//        doc.save();
//        doc.dumpDebug("submitEns");
//
//        NDoc reDoc = doc;
//
//        // Save the run ensembles to the source model.
//        // No reference to ModelOrient here!  Need new interface?
//        List<RunEnsemble> res = (List<RunEnsemble>) ReflectionUtil.invoke("getRunEnsembles", model);
//        res.add(new RunEnsembleOrient(reDoc));
//        ReflectionUtil.invoke("setRunEnsembles", model, res);
//
//        List<RunEnsemble> res2 = (List<RunEnsemble>) ReflectionUtil.invoke("getRunEnsembles", model);
//
//        for(RunEnsemble re : res2) {
//        	re.getSource().dumpDebug("uicontroller loop");
//        }
//
//        model.save();
//    }
}
