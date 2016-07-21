/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.model;

import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.Run;
import gov.sandia.umf.platform.plugins.RunEnsemble;
import gov.sandia.umf.platform.plugins.RunState;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

import replete.gui.controls.IconButton;
import replete.gui.controls.mnemonics.MButton;
import replete.gui.controls.mnemonics.MMenuItem;
import replete.gui.controls.simpletree.SimpleTree;
import replete.gui.controls.simpletree.TNode;
import replete.gui.windows.Dialogs;
import replete.threads.CommonThread;
import replete.threads.CommonThreadShutdownException;
import replete.util.ExceptionUtil;
import replete.util.GUIUtil;
import replete.util.Lay;
import replete.xstream.XStreamWrapper;

public class RunDetailPanel extends ModelEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    // TODO: Merge functionality of this panel with the RunManagerFrame, and make it a single tab in the main frame.
    //private SearchTable tblRuns;
    //private SearchResultTableModelOrient modelRuns;
    private JPopupMenu mnuContext;
    private JMenuItem mnuOpen;
    private SimpleTree treRunEnsembles;
    private TNode nRoot = new TNode();


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public RunDetailPanel(UIController uic, ModelOrient m, final ModelEditPanel pnlParent) {
        super(uic, m);

        MButton btnCreateRun = new MButton("Create New Run...", ImageUtil.getImage("runadd.gif"));
        btnCreateRun.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if(!model.isPersisted()) {
                    if(Dialogs.showConfirm("This model record must be saved before any runs can be created from it.  Save model now and continue?")) {
                        // TODO: Permissions check
                        String msg = pnlParent.attemptToSaveSynchronous();
                        if(msg != null) {
                            Dialogs.showWarning("Could not save model record.  Could not create run.\n" + msg);
                            return;
                        }
                    } else {
                        return;
                    }
                }

                final CreateRunDialog dlg = new CreateRunDialog((JFrame) SwingUtilities.getRoot(RunDetailPanel.this), uiController, model);
                dlg.setVisible(true);
                if(dlg.getResult() == CreateRunDialog.CREATE) {
                    final Run run = dlg.getRun();

                    // TODO: Cleaner flow, more consistent try/catch structure, messages.

                    final Simulator simulator = run.getSimulator();
                    uiController.getParentRef().waitOn();
                    final CommonThread t = new CommonThread() {
                        @Override
                        public void runThread() throws CommonThreadShutdownException {
                            RunState runState;
                            Simulation simulation = simulator.createSimulation();
                            try {
                                ExecutionEnv env = dlg.getEnvironment();
                                runState = simulation.execute(run, null, env);
                                run.setState(XStreamWrapper.writeToString(runState));
                                run.save();  // TODO: Permissions check
                                model.addRun(run);
                                reload();

                                int which = Dialogs.showMulti("Run submitted",
                                    "Success!", new String[]{"OK", "Proceed To &Run Manager >>"},
                                    JOptionPane.INFORMATION_MESSAGE);

                                if(which == 1) {
                                    uiController.openChildWindow("jobs", null);
                                }

                            } catch(Exception e1) {
                                e1.printStackTrace();
                                Dialogs.showDetails("An error occurred while submitting the job.",
                                    ExceptionUtil.toCompleteString(e1, 4));
                            }

                            uiController.openRecord(run.getSource());
                        }
                    };
                    t.addProgressListener(new ChangeListener() {
                        public void stateChanged(ChangeEvent e) {
                            if(t.getResult().isDone()) {
                                uiController.getParentRef().waitOff();
                            }
                        }
                    });
                    t.start();
                }
            }
        });

        MButton btnCreateRun2 = new MButton("Create New Run Ensemble...", ImageUtil.getImage("runensadd.gif"));
        btnCreateRun2.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                // This could be UI controller as well technically.
                if(!model.isPersisted()) {
                    if(Dialogs.showConfirm("This model record must be saved before any runs can be created from it.  Save model now and continue?")) {
                        // TODO: Permissions check
                        String msg = pnlParent.attemptToSaveSynchronous();
                        if(msg != null) {
                            Dialogs.showWarning("Could not save model record.  Could not create run.\n" + msg);
                            return;
                        }
                    } else {
                        return;
                    }
                }

                // Model is saved to DB at this point.

                try {
                    if(!uiController.prepareAndSubmitRunEnsemble(RunDetailPanel.this, model /* send in previous parameters */)) {
                        return;
                    }
                    reload();

                    int which = Dialogs.showMulti("Run submitted",
                        "Success!", new String[]{"OK", "Proceed To &Run Manager >>"},
                        JOptionPane.INFORMATION_MESSAGE);

                    if(which == 1) {
                        uiController.openChildWindow("jobs", null);
                    }

                } catch(Exception e1) {
                    e1.printStackTrace();
                    Dialogs.showDetails("An error occurred while submitting the job.",
                        ExceptionUtil.toCompleteString(e1, 4));
                }
            }
        });

//        modelRuns = new SearchResultTableModelOrient();
//        tblRuns = new SearchTable(modelRuns, ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
//        tblRuns.addMouseListener(new MouseAdapter() {
//            @Override
//            public void mouseClicked(MouseEvent e) {
//                if(SwingUtilities.isLeftMouseButton(e) &&
//                        e.getClickCount() == 2 &&
//                            tblRuns.rowAtPoint(e.getPoint()) != -1 &&
//                            tblRuns.getSelectedRowCount() != 0) {
//                    doOpen();
//                }
//                if(SwingUtilities.isRightMouseButton(e) &&
//                        e.getClickCount() == 1 &&
//                        tblRuns.rowAtPoint(e.getPoint()) != -1) {
//                    int idxUnderMouse = tblRuns.rowAtPoint(e.getPoint());
//                    if(tblRuns.getSelectedRowCount() == 0) {
//                        tblRuns.getSelectionModel().setSelectionInterval(idxUnderMouse, idxUnderMouse);
//                    } else if(!containsIndex(tblRuns.getSelectedRows(), idxUnderMouse)) {
//                        tblRuns.getSelectionModel().setSelectionInterval(idxUnderMouse, idxUnderMouse);
//                    }
//                    mnuOpen.setEnabled(tblRuns.getSelectedRows().length == 1);
//                    mnuTableCtx.show(tblRuns, e.getX(), e.getY());
//                }
//            }
//        });
//        tblRuns.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteKey");
//        tblRuns.getActionMap().put("deleteKey", new AbstractAction() {
//            public void actionPerformed(ActionEvent e) {
//                doRemove();
//            }
//        });

        JMenuItem mnuRemove = new MMenuItem("&Remove", ImageUtil.getImage("remove.gif"));
        mnuRemove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doRemove();
            }
        });
        mnuOpen = new MMenuItem("&Open", ImageUtil.getImage("open.gif"));
        mnuOpen.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doOpen();
            }
        });
        mnuContext = new JPopupMenu();
        mnuContext.add(mnuRemove);
        mnuContext.add(mnuOpen);

        final JButton btnRemove = new IconButton(ImageUtil.getImage("remove.gif"), "Remove Run(s)", 2,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    doRemove();
                }
            }
        );
        final JButton btnOpen = new IconButton(ImageUtil.getImage("open.gif"), "Open Run", 2,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    doOpen();
                }
            }
        );
        btnOpen.setEnabled(false);

        treRunEnsembles = new SimpleTree(nRoot);
        treRunEnsembles.setRootVisible(false);
        treRunEnsembles.setShowsRootHandles(true);
        treRunEnsembles.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                btnRemove.setEnabled(treRunEnsembles.getSelectionCount() > 0);
                btnOpen.setEnabled(treRunEnsembles.getSelectionCount() == 1);
            }
        });

        Lay.BLtg(this,
            "N",  Lay.FL("L",
                btnCreateRun, Lay.p(btnCreateRun2, "eb=5l"),
                Lay.p(createHelpIcon("model-create-run"), "eb=3l"),
                "eb=3b,hgap=0,vgap=0"
            ),
            "C", Lay.BL(
                "N", Lay.hn(createLabelPanel("Runs", "model-runs"), "pref=[10,25]"),
                "C", Lay.BL(
//                    "C", Lay.p(Lay.sp(tblRuns), "eb=5r"),
                    "C", Lay.p(Lay.sp(treRunEnsembles), "eb=5r"),
                    "E", Lay.BxL("Y",
                        Lay.BL(btnRemove, "eb=5b,alignx=0.5,maxH=20"),
                        Lay.hn(btnOpen, "alignx=0.5"),
                        Box.createVerticalGlue()
                    )
                )
            ),
            "eb=10"
        );

        treRunEnsembles.addDoubleClickListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                doOpen();
            }
        });
    }

    private boolean containsIndex(int[] idxs, int idx) {
        for(int i = 0; i < idxs.length; i++) {
            if(idxs[i] == idx) {
                return true;
            }
        }
        return false;
    }


    //////////
    // MISC //
    //////////

    // TODO: Permissions check.
    protected void doRemove ()
    {
        /*
        int[] idxs = tblRuns.getSelectedRows();
        String x = idxs.length == 1 ? "this run" : "these " + idxs.length + " runs";
        if(Dialogs.showConfirm("Are you sure you want to remove " + x + "?")) {
            for(int i = idxs.length - 1; i >= 0; i--) {
                NDoc r = modelRuns.getResult(idxs[i]);
                r.delete();
                modelRuns.remove(idxs[i]);
            }
        }
        */
    }

    private void doOpen() {
//        int row = tblRuns.getSelectedRow();
//        if(row != -1) {
//            NDoc run = modelRuns.getResult(row);
//            uiController.openRecord(run);
//        }
        TNode nSel = treRunEnsembles.getTSelectionNode();
        if(nSel != null) {
            NDoc doc = null;
            if(nSel.type(NodeRunEnsemble.class)) {
                doc = ((NodeRunEnsemble) nSel.getObject()).getEnsemble();
            } else if(nSel.type(NodeRun.class)) {
                doc = ((NodeRun) nSel.getObject()).getRun();
            }
            if(doc != null) {
                uiController.openRecord(doc);
            }
        }
    }

    @Override
    public void reload() {
        GUIUtil.safe(new Runnable() {
            public void run() {
                nRoot.removeAllChildren();
                for(RunEnsemble ensemble : model.getRunEnsembles()) {
                    TNode nEns = nRoot.add(new NodeRunEnsemble(ensemble));
                    for(Run run : ensemble.getRuns()) {
                        nEns.add(new NodeRun(run));
                    }
                }
                treRunEnsembles.update();
            }
        });
    }
}
