/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.AppState;
import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.connect.orientdb.ui.ConnectionManager;
import gov.sandia.umf.platform.connect.orientdb.ui.ConnectionSelectionDialog;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientDatasource;
import gov.sandia.umf.platform.ensemble.params.ParameterSet;
import gov.sandia.umf.platform.ensemble.params.groups.ParameterSpecGroup;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.PlatformRecord;
import gov.sandia.umf.platform.plugins.Run;
import gov.sandia.umf.platform.plugins.RunEnsemble;
import gov.sandia.umf.platform.plugins.RunState;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.plugins.extpoints.Exporter;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;
import gov.sandia.umf.platform.runs.RunQueue;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;
import gov.sandia.umf.platform.ui.export.ExportDialog;
import gov.sandia.umf.platform.ui.export.ExportParameters;
import gov.sandia.umf.platform.ui.export.ExportParametersDialog;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.run.CreateRunEnsembleDialog;
import gov.sandia.umf.platform.ui.search.SearchDialogOrient;
import gov.sandia.umf.platform.ui.search.SearchSelectionValidator;
import gov.sandia.umf.platform.ui.search.SearchType;
import gov.sandia.umf.platform.ui.wp.WorkpaneModel;

import java.awt.Component;
import java.awt.Cursor;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.log4j.Logger;

import replete.event.ChangeNotifier;
import replete.gui.windows.Dialogs;
import replete.gui.windows.common.CommonWindow;
import replete.logging.LogViewer;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;
import replete.plugins.ui.PluginDialog;
import replete.threads.CommonRunnable;
import replete.threads.CommonThread;
import replete.threads.CommonThreadContext;
import replete.threads.CommonThreadResult;
import replete.threads.CommonThreadShutdownException;
import replete.util.GUIUtil;
import replete.util.Lay;
import replete.util.ReflectionUtil;
import replete.util.User;
import replete.xstream.XStreamWrapper;

import com.orientechnologies.orient.core.record.impl.ODocument;


// TODO: will probably need a UI model (kinda like a "selected model")
// to handle in a nice way the open tabs.

public class UIController {


    ////////////
    // FIELDS //
    ////////////

    // Const

//    private static Logger logger = Logger.getLogger(Main.class);  // ui

    // Core

    public ConnectionManager dataModelMgr2;  // Orient stuff
    private WorkpaneModel workpaneModel;

    // UI

    private MainFrame parentRef;
    private MainTabbedPane tabs;  // In lieu of more complicated "tab model" concept.
    private UIActionManager actions;

    // Misc

    private Map<String, String[]> popupHelp;
    private static Logger logger = Logger.getLogger(UIController.class);


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier propNotifier = new ChangeNotifier(this);
    public void addPropListener(ChangeListener listener) {
        propNotifier.addListener(listener);
    }
    protected void firePropNotifier() {
        propNotifier.fireStateChanged();
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public UIController(WorkpaneModel wpm, ConnectionManager mgr2) {
        workpaneModel = wpm;
        dataModelMgr2 = mgr2;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessor

    // To be used ONLY for a dialog's parent!!!
    public MainFrame getParentRef() {
        return parentRef;
    }
    public ConnectionManager getDMM() {
        return dataModelMgr2;
    }
    public MainTabbedPane getTabs() {
        return tabs;
    }

    // Mutators

    public void setTabbedPane(MainTabbedPane t) {
        tabs = t;
    }
    public void setParentReference(MainFrame par) {
        parentRef = par;
        actions = new UIActionManager(parentRef);
    }


    ////////////
    // SEARCH //
    ////////////


    /////////////
    // EXPLORE //
    /////////////

    public void openExplore() {
        startAction("Opening", new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                tabs.openExplore();
            }
            public void cleanUp() {}
        }, null, "opening the explore database tab");
    }


    ///////////////////
    // RECORD PANELS //
    ///////////////////

    public void openDuplicate(final NDoc record) {
        String beanType = record.getHandler().getRecordTypeDisplayName(record);
        startAction("Duplicating", new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                NDoc newRecord = record.copy();
                // TODO: untested
                newRecord.setOwner(User.getName());
                tabs.openRecordTabOrient(newRecord);
            }
            public void cleanUp() {}
        }, null, "duplicating the " + beanType);
    }

    public void saveSynchronous(NDoc record) {  // Not an UI operation but here for consistency.
        record.save();
        //updateWorkpane(bean); // Make a notifier/listener pair someday TODO
    }

    public void saveSynchronousRecursive(NDoc record) {  // Not an UI operation but here for consistency.
        record.saveRecursive();
        //updateWorkpane(bean); // Make a notifier/listener pair someday TODO
    }

    public void save(final NDoc bean, final ChangeListener onSuccessCallback) {
        String beanType = bean.getClassName();
        startAction("Saving", new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                bean.save();
                //updateWorkpane(bean); // Make a notifier/listener pair someday TODO
                GUIUtil.safeSync(new Runnable() {
                    public void run() {
                        onSuccessCallback.stateChanged(null);
                    }
                });
            }
            public void cleanUp() {}
        }, null, "saving the " + beanType);
    }

    public void saveRecursive(final NDoc bean, final ChangeListener onSuccessCallback) {
        String beanType = bean.getClassName();
        startAction("Saving", new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                bean.saveRecursive();
                //updateWorkpane(bean); // Make a notifier/listener pair someday TODO
                GUIUtil.safeSync(new Runnable() {
                    public void run() {
                        onSuccessCallback.stateChanged(null);
                    }
                });
            }
            public void cleanUp() {}
        }, null, "saving the " + beanType);
    }

    public void discardNew(final NDoc record) {
        String beanType = record.getClassName();
        if(Dialogs.showConfirm("Are you sure you want to discard this new (unsaved) " + beanType + "?")) {
            startAction("Discarding", new CommonRunnable() {
                public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                    tabs.closeTab(record);
                }
                public void cleanUp() {}
            }, null, "discarding the " + beanType);
        }
    }

    public void openProfileFromUserId(String userId) {
//        List<Profile> profiles = Profile.get(Query.create().eq("UserName", userId));
//        if(profiles.size() == 0) {
//            Dialogs.showMessage("No profile for this user could be found.");
//        } else {
//            openExisting(Profile.class, profiles.get(0).getId());
//        }
        notImpl();
    }

    // TODO? Kinda pretends that delete isn't only called from the button
    // on the active tab, so doesn't use getSelectedIndex().
    public void delete(final NDoc record) {
        String beanType = record.getClassName();
        // TODO: Nice delete message, including name/details of bean
        if(Dialogs.showConfirm("Are you sure you want to delete this " + beanType + "?")) {
            startAction("Deleting", new CommonRunnable() {
                public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                    record.delete();
                    //workpaneModel.removeRecord(bean); // Make a notifier/listener pair someday
                    tabs.closeTab(record);
                }
                public void cleanUp() {}
            }, null, "deleting the " + beanType);
        }
    }

    ////////
    // DB //
    ////////

    // These two could also be combined if a thread could some how return a value on success/failure.
    public void testConnect(final ChangeListener onSuccessCallback) {
        startAction("Connecting", new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                try {
                    if(dataModelMgr2.hasDetails()) {
                        dataModelMgr2.connect();
                    }
//                    workpaneModel.init();  // TODO: Rethink the workpane.
                    onSuccessCallback.stateChanged(null);

                } catch(Exception e) {
                    throw new RuntimeException("Could not connect to database.", e);
                }
            }
            public void cleanUp() {}
        }, null, "connecting to the database");
    }

    public boolean disconnect2() {
        if(!okToCloseAllTabs("disconnect from this data source")) {
            return false;
        }
        tabs.closeAllTabsNoSave();
        dataModelMgr2.disconnect();
        return true;
    }


    /////////////
    // ACTIONS //
    /////////////

    private CommonThread currentAction = null;

    public void startAction(String actionStr, CommonRunnable runnable, ChangeListener callbackListener, final String operation) {
        startAction(actionStr, false, runnable, callbackListener, operation);
    }

    public void startAction(String actionStr, boolean suppressMouseChange,
                             CommonRunnable runnable, final ChangeListener callbackListener, final String operation) {

        if(currentAction != null) {
            Dialogs.showWarning("Please wait for previous action to finish.");
            return;
        }

        // UI Start Up
        parentRef.getStatusBar().setProgressBarIndeterminate(true);
        parentRef.getStatusBar().setShowProgressBar(true);
        parentRef.getStatusBar().setStatusMessage(" " + actionStr + "...");
        if(!suppressMouseChange) {
            parentRef.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }

        currentAction = new CommonThread(runnable);
        currentAction.addProgressListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                CommonThreadResult r = (CommonThreadResult) e.getSource();
                if(r.isDone()) {
                    currentAction = null;

                    // UI Tear Down
                    parentRef.getStatusBar().setShowProgressBar(false);
                    parentRef.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    parentRef.getStatusBar().setStatusMessage("");

                    if(callbackListener != null) {
                        callbackListener.stateChanged(null);
                    }
                }
                if(r.isError()) {
                    UMF.handleUnexpectedError(null, r.getPrimaryError(), "An error occurred while " + operation + ".");
                }
            }
        });

        currentAction.start();
    }

    public List<NDoc> searchRecordOrient(CommonWindow win, SearchType searchType, String title, SearchSelectionValidator validator, Icon selectIcon, int sel) {
        return searchRecordOrient(win, searchType, title, validator, selectIcon, sel, null);
    }
    public List<NDoc> searchRecordOrient(CommonWindow win, SearchType searchType, String title, SearchSelectionValidator validator, Icon selectIcon, int sel, List<NDoc> given) {
        SearchDialogOrient dlg;
        if(win instanceof JFrame) {
            dlg = new SearchDialogOrient((JFrame) win, title, this, searchType, validator, selectIcon, sel, given);
        } else {
            dlg = new SearchDialogOrient((JDialog) win, title, this, searchType, validator, selectIcon, sel, given);
        }
        dlg.setVisible(true);
        if(dlg.getResult() == SearchDialogOrient.SEARCH) {
            return dlg.getSelectedRecords();
        }
        return null;
    }

    public void closeMainFrame() {
        parentRef.closeWindow();
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

    public void openChildWindow(String string, String uniqueId) {
        parentRef.openChildWindow(string, uniqueId);
    }

    public void showLogViewer() {
        LogViewer viewer = new LogViewer(parentRef);
        viewer.setVisible(true);
    }

    public void showAbout() {
        AboutDialog dlg = new AboutDialog(parentRef);
        dlg.setVisible(true);
    }

    public void historyBackTab() {
        tabs.historyBackTab();
    }
    public void historyForwardTab() {
        tabs.historyForwardTab();
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


    ////////////////////////////////
    // DIRTY, CLOSE, SAVE, AND OK //
    ////////////////////////////////

    public boolean isDirty() {
        return tabs.isDirty();
    }

    public boolean closeCurrentTab() {
        if(okToCloseCurrentTab()) {
            tabs.closeCurrentTabNoSave();
            return true;
        }
        return false;
    }

    public void closeAllTabs() {
        UIActionBuilder builder = new UIActionBuilder()
            .setOnUIThread(true)
            .setErrorMessage("closing the records")
            .setOnUIThread(true)
            .setInProgressCaption("Closing")
            .setTask(new CommonRunnable() {
                public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                    // TODO: leave only those invalid tabs open.
                    if(okToCloseAllTabs("close all of the tabs")) {
                        tabs.closeAllTabsNoSave();
                    }
                }
                public void cleanUp() {}
            });

        actions.submit(builder.getAction());

        /*startAction("Closing", new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                // TODO: leave only those invalid tabs open.
                if(okToCloseAllTabs("close all of the tabs")) {
                    tabs.closeAllTabsNoSave();
                }
            }
            public void cleanUp() {}
        }, null, "closing the records");*/
    }

    public boolean okToCloseAllTabs(String action) {
        if(isDirty()) {
            String[] opts = {"&Save", "&Discard", "&Cancel"};
            int choice = Dialogs.showMulti("One or more records contain unsaved changes.  " +
                "Do you wish to\nsave all changes before you " + action + "?", "Save Changes?",
                opts, JOptionPane.QUESTION_MESSAGE);
            if(choice == 2 || choice == -1) {
                return false;
            } else if(choice == 0) {
                if(!saveAllTabs()) {
                    return false;
                }
            }
        }
        return true;
    }

    /*
    public boolean okToCloseAllTabs2(String action, final SimpleCallback callback) {
        if(isDirty()) {
            String[] opts = {"&Save", "&Discard", "&Cancel"};
            int choice = Dialogs.showMulti("One or more records contain unsaved changes.  " +
                "Do you wish to\nsave all changes before you " + action + "?", "Save Changes?",
                opts, JOptionPane.QUESTION_MESSAGE);
            if(choice == 2 || choice == -1) {
                return false;
            } else if(choice == 0) {
                startAction("Saving", new CommonRunnable() {
                    public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                        saveAllTabs();
                    }
                    public void cleanUp() {}
                }, new ChangeListener() {
                    public void stateChanged(ChangeEvent e) {
                        callback.callback();   /////// THIS GETS CALLED IN EITHER SUCCESS OR FAILURE CASE......ANNOYING.
                        // need mechanism with this whole threading thing to return a simple
                        // value from the run method... some addition to the thread context?
                    }
                }, "saving the records");
                return false;
            }
        }
        return true;
    }*/

    public boolean okToCloseCurrentTab() {
        if(tabs.isDirtyAt(tabs.getSelectedIndex())) {
            String[] opts = {"&Save", "&Discard", "&Cancel"};
            int choice = Dialogs.showMulti("This record contains unsaved changes.  Do you wish to save?",
                "Save Changes?", opts);
            if(choice == 2 || choice == -1) {
                return false;
            } else if(choice == 0) {
                if(!saveCurrentTab()) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean saveCurrentTab() {
        String msg = tabs.saveCurrentTab();
        if(msg != null) {
            Dialogs.showError("The record could not be saved due to a validation error.\n\n" + msg);
            return false;
        }
        return true;
    }

    public boolean saveAllTabs() {
        if(!tabs.saveAllTabs()) {
            Dialogs.showError("One or more records could not be saved due to a validation error.");
            return false;
        }
        return true;
    }

    public void toggleWorkpane() {
        parentRef.toggleWorkpane();
    }

    public void removeMyWork(int[] selectedIndices) {
//        workpaneModel.removeMyWork(selectedIndices);
    }
    public void removeRecent(int[] selectedIndices) {
//        workpaneModel.removeRecent(selectedIndices);
    }

//    public void addRecent(BeanBase bean) {
//        workpaneModel.addRecent(bean, true);
//    }

    public void hideWorkpane() {
        parentRef.hideWorkpane();
    }
//    public boolean isInMyWork(BeanBase b) {
//        return workpaneModel.isInMyWork(b);
//    }
//    public void setInMyWork(BeanBase record, boolean selected) {
//        workpaneModel.setInMyWork(record, selected);
//    }

    public void showPreferences() {
        PreferencesDialog dlg = new PreferencesDialog(parentRef);
        dlg.setVisible(true);
        if(dlg.getResult() == PreferencesDialog.OK) {
            firePropNotifier();
        }
    }

    public void showPluginDialog() {
        PluginDialog dlg = new PluginDialog(parentRef);
        dlg.setVisible(true);
    }

    public void openNewRun(Object initModel) {
/*
        if(initModel instanceof Model || initModel == null) {
            Model model = (Model) initModel;
            CreateRunDialog dlg = new CreateRunDialog(parentRef, this, model);
            dlg.setVisible(true);
            if(dlg.getResult() == CreateRunDialog.CREATE) {
                Run run = dlg.getRun();

                // Connect to execution environment
                // Ask for working dir


                // TODO: Cleaner flow, more consistent try/catch structure, messages.

                // TODO: Should come from dialog, not hard coded.
                RunState runState;
    //            UMFPlugin plugin = PluginManager.get("gov.sandia.umf.plugins.n2a");
                List<ExtensionPoint> simulators = PluginManager.getExtensionsForPoint(Simulator.class);
                Simulator simulator = (Simulator) simulators.get(0);
                try {

                    runState = simulator.getInitialRunState(run);
                    run.setState(XStreamWrapper.writeToString(runState));
                    run.save();  // TODO: Permissions check
                    model.getRuns().add(run);
                    //reload();
                } catch(Exception e1) {
                    UMF.handleUnexpectedError(null, e1, "Could not create the run.  An error occurred.");
                    return;
                }

                try {
                    ExecutionEnv env = new LocalMachineEnv();
                    simulator.submitJob(env, runState);

                    int which = Dialogs.showMulti("Run for simulator 'Xyce' submitted to '" + env.getName() + "'.",
                        "Success!", new String[]{"OK", "Proceed To &Run Manager >>"},
                        JOptionPane.INFORMATION_MESSAGE);

                    if(which == 1) {
                        openChildWindow("jobs", null);
                    }

                } catch(Exception e1) {
                    e1.printStackTrace();
                    Dialogs.showDetails("An error occurred while submitting the job.",
                        ExceptionUtil.toCompleteString(e1, 4));
                }

                openExisting(Run.class, run.getId());
            }
        } else {

            BiaModel model = (BiaModel) initModel;
            final CreateRunDialog dlg = new CreateRunDialog(parentRef, this, model);
            dlg.setVisible(true);
            if(dlg.getResult() == CreateRunDialog.CREATE) {
                final BiaRun run = dlg.getRun();

                // TODO: Cleaner flow, more consistent try/catch structure, messages.

                // TODO: Should come from dialog, not hard coded.
                String biaPluginId = PluginManager.getPluginId(new BiaPlugin());
                List<ExtensionPoint> simulators = UMFPluginManager.getExtensionsForPoint(biaPluginId, Simulator.class);
                final Simulator simulator = (Simulator) simulators.get(0);
                getParentRef().waitOn();
                final CommonThread t = new CommonThread() {
                    @Override
                    public void runThread() throws CommonThreadShutdownException {
                        RunState runState = null;
                        try {
                            runState = simulator.getInitialRunState(run);
                            if(runState == null) {
                                run.setState("BIA");
                            } else {
                                run.setState(XStreamWrapper.writeToString(runState));
                            }
                            run.save();  // TODO: Permissions check
                            model.getRuns().add(run);
//                            reload();
                        } catch(Exception e1) {
                            UMF.handleUnexpectedError(null, e1, "Could not create the run.  An error occurred.");
                            return;
                        }

                        try {
                            ExecutionEnv env = dlg.getEnvironment();
                            simulator.submitJob(env, runState);

                            int which = Dialogs.showMulti("Run for simulator 'Xyce' submitted to '" + env.getName() + "'.",
                                "Success!", new String[]{"OK", "Proceed To &Run Manager >>"},
                                JOptionPane.INFORMATION_MESSAGE);

                            if(which == 1) {
                                openChildWindow("jobs", null);
                            }

                        } catch(Exception e1) {
                            e1.printStackTrace();
                            Dialogs.showDetails("An error occurred while submitting the job.",
                                ExceptionUtil.toCompleteString(e1, 4));
                        }

                        openExisting(BiaRun.class, run.getId());
                    }
                };
                t.addProgressListener(new ChangeListener() {
                    public void stateChanged(ChangeEvent e) {
                        if(t.getResult().isDone()) {
                            getParentRef().waitOff();
                        }
                    }
                });
                t.start();
            }
        }*/
    }

    public void openNewAnalysis(Run initRun) {
//        CommonFrame parent = parentRef;
//        String searchTitle = "Select Runs for New Analysis...";
//        List<BeanBase> chosen = searchRecord(parent,
//            SearchType.RUN, searchTitle, null, ImageUtil.getImage("complete.gif"),
//            ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
//        if(chosen != null) {
//            List<ExtensionPoint> x = PluginManager.getExtensionsForPoint(Analyzer.class);
//            Object o = JOptionPane.showInputDialog(parent, "Choose which analyzer to use.", "Select Analyzer", 0, null, x.toArray(), null);
//            if(o != null) {
//                for(BeanBase chosenRecord : chosen) {
//                    Run run = (Run) chosenRecord;
//                    RunState state = run.getDeserializedRunState();
//                }
//            }
//        }
    }

    public void openOrientDbConnect() {

        final ConnectionSelectionDialog dlg = new ConnectionSelectionDialog(
            parentRef,
            AppState.getState().getConnectModel().getList(),
            AppState.getState().getConnectModel().getSelected());

        dlg.setVisible(true);

        if(dlg.getState() == ConnectionSelectionDialog.SAVE) {
            if(!disconnect2()) {
                return;
            }
            // TODO: if disconnect fails, then you lose your edits in the dialog.
            // If we save the list but not the selected when we might save a list
            // in which the original selected connection might not exist.... conumdrum.
            AppState.getState().getConnectModel().setList(dlg.getConnections());
            AppState.getState().getConnectModel().setSelected(dlg.getChosenConnection());
            if(dlg.getChosenConnection() == null) {
                return;
            }
            startAction("Connecting", new CommonRunnable() {
                public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                    try {
                        dataModelMgr2.connect(dlg.getChosenConnection());
                        Dialogs.showMessage("Connection successful.", "Connect");
                    } catch(Exception e1) {
                        Dialogs.showDetails("Connection failed.", "Connect", e1);
                    }
                }
                public void cleanUp() {}
            }, null, "connecting to the database");
        }
    }

    public void searchDb(final String query, final ChangeListener onSuccessCallback) {
        startAction("Searching", new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                OrientDatasource ds = getDMM().getDataModel();
                List<NDoc> docs = ds.search(query);
                onSuccessCallback.stateChanged(new ChangeEvent(docs));
            }
            public void cleanUp() {}
        }, null, "searching the database");
    }

    public void openSearchOrient() {
        startAction("Opening", new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                tabs.openSearchOrient();
            }
            public void cleanUp() {}
        }, null, "opening the search tab");
    }


    public void saveSynchronous(ODocument bean) {  // Not an UI operation but here for consistency.
        bean.save();
//        updateWorkpane(bean); // Make a notifier/listener pair someday
    }

    public void save(final ODocument bean, final ChangeListener onSuccessCallback) {
        String beanType = bean.getClassName();
        startAction("Saving", new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                bean.save();
//                updateWorkpane(bean); // Make a notifier/listener pair someday
                onSuccessCallback.stateChanged(null);
            }
            public void cleanUp() {}
        }, null, "saving the " + beanType);
    }

    public void openRecord(String className, String id) {
        NDoc record = dataModelMgr2.getDataModel().getRecord(className, id);
        openRecord(record);
    }
    public void openRecord(final NDoc doc) {
        if(doc != null && doc.getHandler() != null) {
            startAction("Opening", new CommonRunnable() {
                public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                    // Need a generic way to have tasks run on the UI thread.
                    GUIUtil.safeSync(new Runnable() {
                        public void run() {
                            tabs.openRecordTabOrient(doc);
                        }
                    });
                }
                public void cleanUp() {}
            }, null, "opening the " + doc.getHandler().getRecordTypeDisplayName(doc));
        }
    }

    public void openExportDialog() {
        List<NDoc> results = searchRecordOrient(parentRef, SearchType.COMPARTMENT, "Choose Model", null, null, ListSelectionModel.SINGLE_SELECTION);
        if(results != null) {
            ExportDialog dlg = new ExportDialog(parentRef);
            dlg.setVisible(true);
            if(dlg.getState() == ExportDialog.OK) {
                Exporter exporter = dlg.getExporter();
                ExportParametersDialog dlg2 = new ExportParametersDialog(parentRef, exporter);
                dlg2.setVisible(true);
                if(dlg2.getState() == ExportParametersDialog.OK) {
                    ExportParameters params = dlg2.getParameters();
                    try {
                        exporter.export(results.get(0), params);
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    public void openImportDialog() {
    }
    public void initTabsFromAppState() {
        tabs.initFromAppState();
    }


    ///////////////////
    // RUN ENSEMBLES //
    ///////////////////

    public boolean prepareAndSubmitRunEnsemble(Component parentComponent, PlatformRecord model) throws Exception {

        // Set up simulators.
        List<ExtensionPoint> simEP = PluginManager.getExtensionsForPoint(Simulator.class);
        Simulator[] simulators = new Simulator[simEP.size()];
        int s = 0;
        for(ExtensionPoint ep : simEP) {
            simulators[s++] = (Simulator) ep;
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
            Simulator simulator = dlg.getSimulator();
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
            RunEnsemble re = model.addRunEnsemble(label,
                    env.toString(), PluginManager.getExtensionId(simulator),
                    groups, simHandledGroups, outputExpressions);

            // TODO - submit to RunQueue here and have it take care of the rest
            RunQueue runQueue = RunQueue.getInstance();
            runQueue.submitEnsemble(model, re);
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
    private ParameterSpecGroupSet divideEnsembleParams(Object model,
            ParameterSpecGroupSet origSet, 
            Simulator sim) {
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
