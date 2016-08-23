/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ensemble.params.groups.ParameterSpecGroup;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.PlatformRecord;
import gov.sandia.umf.platform.plugins.extpoints.Exporter;
import gov.sandia.umf.platform.plugins.extpoints.Backend;
import gov.sandia.umf.platform.runs.RunEnsemble;
import gov.sandia.umf.platform.ui.export.ExportDialog;
import gov.sandia.umf.platform.ui.export.ExportParameters;
import gov.sandia.umf.platform.ui.export.ExportParametersDialog;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.run.CreateRunEnsembleDialog;

import java.awt.Component;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.log4j.Logger;

import replete.gui.windows.Dialogs;
import replete.logging.LogViewer;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;
import replete.plugins.ui.PluginDialog;
import replete.util.GUIUtil;
import replete.util.Lay;
import replete.util.ReflectionUtil;


// TODO: will probably need a UI model (kinda like a "selected model")
// to handle in a nice way the open tabs.

public class UIController
{
    private MainFrame parentRef;

    private Map<String, String[]> popupHelp;
    private static Logger logger = Logger.getLogger(UIController.class);


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessor

    // To be used ONLY for a dialog's parent!!!
    public MainFrame getParentRef() {
        return parentRef;
    }

    // Mutators

    public void setParentReference(MainFrame par) {
        parentRef = par;
    }

    public void closeMainFrame ()
    {
        parentRef.closeWindow ();
    }

    public void showHelp(HelpCapableWindow win, String key) {
        String[] topicContent = popupHelp.get(key);
        if(topicContent != null) {
            win.showHelp(topicContent[0], topicContent[1]);
        } else {
            Dialogs.showError("Could not find help for this topic!\n\nSEEK PROFESSIONAL ASSISTANCE INSTEAD", "Your best recourse...");
        }
    }

    public void setPopupHelp(Map<String, String[]> ph) {
        popupHelp = ph;
    }

    public Component selectTab (String tabName)
    {
        MainTabbedPane mtp = parentRef.getTabbedPane ();
        for (int i = 0; i < mtp.getTabCount (); i++)
        {
            if (mtp.getTitleAt (i).equals (tabName))
            {
                mtp.setSelectedIndex (i);
                return mtp.getComponent (i);
            }
        }
        return null;
    }

    public void showLogViewer() {
        LogViewer viewer = new LogViewer(parentRef);
        viewer.setVisible(true);
    }

    public void showAbout() {
        AboutDialog dlg = new AboutDialog(parentRef);
        dlg.setVisible(true);
    }

    public void startProgressIndeterminate(String message) {
        parentRef.getStatusBar().setProgressBarIndeterminate(true);
        parentRef.getStatusBar().setShowProgressBar(true);
        parentRef.getStatusBar().setStatusMessage(" " + message + "...");
    }
    public void stopProgressIndeterminate() {
        parentRef.getStatusBar().setShowProgressBar(false);
        parentRef.getStatusBar().setStatusMessage("");
    }


    public void notImpl() {
//        Dialogs.showWarning("This feature is not yet implemented.\n\nCheck back soon!", "Sorry...");
        JLabel lbl = Lay.lb("<html>This feature is not yet implemented, but don't give up hope...</html>");
        lbl.setPreferredSize(GUIUtil.getHTMLJLabelPreferredSize(lbl, 300, true));
        Dialogs.show(Lay.BL("C", "hgap=5,vgap=5", Lay.lb(ImageUtil.getImage("mario3.gif")), "S", lbl, "eb=3"), "You Lose...", JOptionPane.WARNING_MESSAGE, null /*ImageUtil.getImage("mario3.gif")*/);
    }

    public void couldNotFind() {
        Dialogs.showWarning("Could not find the requested record.  Please refresh any search results you may have displayed and/or reopen any currently open records.");
    }

    public void showPluginDialog() {
        PluginDialog dlg = new PluginDialog(parentRef);
        dlg.setVisible(true);
    }

    public void save ()
    {
        AppData.save ();
    }

    public void openExportDialog (MNode document)
    {
        ExportDialog dlg = new ExportDialog (parentRef);
        dlg.setVisible (true);
        if (dlg.getState () == ExportDialog.OK)
        {
            Exporter exporter = dlg.getExporter ();
            ExportParametersDialog dlg2 = new ExportParametersDialog (parentRef, exporter);
            dlg2.setVisible (true);
            if (dlg2.getState () == ExportParametersDialog.OK)
            {
                ExportParameters params = dlg2.getParameters ();
                try
                {
                    exporter.export (document, params);
                }
                catch (IOException e)
                {
                    e.printStackTrace ();
                }
            }
        }
    }

    public void backup ()
    {
        new BackupDialog (parentRef).setVisible (true);
    }


    ///////////////////
    // RUN ENSEMBLES //
    ///////////////////

    public boolean prepareAndSubmitRunEnsemble(Component parentComponent, MNode model) throws Exception {

        // Set up simulators.
        List<ExtensionPoint> simEP = PluginManager.getExtensionsForPoint(Backend.class);
        Backend[] simulators = new Backend[simEP.size()];
        int s = 0;
        for(ExtensionPoint ep : simEP) {
            simulators[s++] = (Backend) ep;
        }

        // Set up execution environments.
        ExecutionEnv[] envs = ExecutionEnv.envs.toArray(new ExecutionEnv[0]);

        // TODO: Fix this with appropriate interfaces.
        String name = (String) ReflectionUtil.invoke("getName", model);
        String owner = (String) ReflectionUtil.invoke("getOwner", model);

        CreateRunEnsembleDialog dlg = new CreateRunEnsembleDialog(
            (JFrame) SwingUtilities.getRoot(parentComponent),
            this, -1, // TODO change from -1
            /*TEMP*/ name, owner, 12342347483L,/*TEMP until appropriate interfaces*/
            model, simulators, simulators[0], envs, envs[0], false);

        dlg.setVisible(true);

        if(dlg.getResult() == CreateRunEnsembleDialog.CREATE) {

            String label = dlg.getLabel();
            ExecutionEnv env = dlg.getEnvironment();
            Backend simulator = dlg.getSimulator();
            ParameterSpecGroupSet groups = dlg.getParameterSpecGroupSet();
            ParameterSpecGroupSet simHandledGroups;
            try {
                // next line modifies groups to remove any that the Simulator will handle
                simHandledGroups = divideEnsembleParams(model, groups, simulator);
            } catch (RuntimeException e) {
                Dialogs.showDetails(getParentRef(), "could not create run ensemble", e);
                return false;
            }
            List<String> outputExpressions = dlg.getSelectedOutputExpressions();

            logger.debug(System.currentTimeMillis() + " calling addRunEnsemble");
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

//    public void submitRunEnsemble(PlatformRecord model, String label, ExecutionEnv env,
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
