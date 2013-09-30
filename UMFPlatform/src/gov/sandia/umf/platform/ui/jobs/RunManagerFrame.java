/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.jobs;

import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.execenvs.beans.AllJobInfo;
import gov.sandia.umf.platform.execenvs.beans.DateGroup;
import gov.sandia.umf.platform.execenvs.beans.Job;
import gov.sandia.umf.platform.execenvs.beans.Resource;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;

import replete.gui.controls.WComboBox;
import replete.gui.controls.mnemonics.MButton;
import replete.gui.controls.mnemonics.MCheckBox;
import replete.gui.controls.mnemonics.MMenuItem;
import replete.gui.controls.simpletree.NodeBase;
import replete.gui.controls.simpletree.TNode;
import replete.gui.controls.vsstree.VisualStateSavingTree;
import replete.gui.fc.CommonFileChooser;
import replete.gui.windows.Dialogs;
import replete.gui.windows.EscapeFrame;
import replete.threads.CommonRunnable;
import replete.threads.CommonThread;
import replete.threads.CommonThreadContext;
import replete.threads.CommonThreadResult;
import replete.threads.CommonThreadShutdownException;
import replete.util.ExceptionUtil;
import replete.util.GUIUtil;
import replete.util.Lay;

public class RunManagerFrame extends EscapeFrame {


    ////////////
    // FIELDS //
    ////////////

    private VisualStateSavingTree treJobs;
    private TNode root = new TNode(new NodeRoot("All Jobs"));
    private DefaultTreeModel model = new DefaultTreeModel(root);
    private JTextArea txtView;
    private JPopupMenu mnuFile;
    private JPopupMenu mnuJob;
    private JLabel lblStatus;
    private JComboBox cboEnvs;
    private ExecutionEnv env;
    private JProgressBar pgb;
    private JLabel lblViewFile;
///    private JButton btnSubmitNewJob;
    private JButton btnRefresh;
    private JPanel pnlViewFileHeader;

    private CommonThread currentAction;
    private boolean refresh = false;
    private Thread refreshThread;
    private long lastRefreshTime;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public RunManagerFrame(JFrame parent) {
        super("Run Manager");

        btnRefresh = new MButton("&Refresh", ImageUtil.getImage("refresh.gif"));
        btnRefresh.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doRefresh();
                if(refreshThread == null) {
                    refreshThread = new Thread() {
                        @Override
                        public void run() {
                            while(true) {
                                try {
                                    Thread.sleep(1000);
                                    if(System.currentTimeMillis() - lastRefreshTime > 20000 &&
                                                    refresh && currentAction == null) {
                                        doRefresh(true);
                                    }
                                } catch(Exception e) {}
                            }
                        }
                    };
                    refreshThread.start();
                }
                refresh = true;
            }
        });

        treJobs = new VisualStateSavingTree(model);
        TNode nInfo = new TNode("(Click Refresh Button)");
        root.add(nInfo);
        treJobs.expandPath(new TreePath(root));
        //treJobs.setCellRenderer(new TreeRenderer());
        treJobs.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    TreePath path = treJobs.getPathForLocation(e.getX(), e.getY());
                    if(path != null) {
                        TNode node = (TNode) path.getLastPathComponent();
                        if(node.isLeaf() && node.getUserObject() instanceof NodeFile) {
                            doView();
                        }
                    }
                }
                if(SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
                    TreePath path = treJobs.getPathForLocation(e.getX(), e.getY());
                    if(path == null) {
                        return;
                    }
                    TNode node = (TNode) path.getLastPathComponent();
                    if(node.getUserObject() instanceof NodeFile) {
                        treJobs.setSelectionPath(path);
                        treJobs.requestFocusInWindow();
                        mnuFile.show(treJobs, e.getX(), e.getY());
                    } else if(node.getUserObject() instanceof NodeJob) {
                        treJobs.setSelectionPath(path);
                        treJobs.requestFocusInWindow();
                        mnuJob.show(treJobs, e.getX(), e.getY());
                    }
                }
            }
        });
        treJobs.addTreeWillExpandListener(new TreeWillExpandListener() {
            public void treeWillCollapse(TreeExpansionEvent e) throws ExpandVetoException {
                TNode nObj = (TNode) e.getPath().getLastPathComponent();
                NodeBase uObj = (NodeBase) nObj.getUserObject();
                if(!uObj.isCollapsible()) {
                    throw new ExpandVetoException(e);
                }
            }
            public void treeWillExpand(TreeExpansionEvent arg0) throws ExpandVetoException {}
        });

        JButton btnClose = new MButton("&Close");
        btnClose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
                refresh = false;
            }
        });

        txtView = new JTextArea();
        txtView.setEditable(false);
        JLabel lblE = new JLabel("Execution Environment:");
        cboEnvs = new WComboBox(new DefaultComboBoxModel(ExecutionEnv.envs.toArray()));
        cboEnvs.setPreferredSize(new Dimension(200, cboEnvs.getPreferredSize().height));
        pgb = new JProgressBar();
        pgb.setString(" ");
        pgb.setStringPainted(true);
        pgb.setIndeterminate(true);
        pgb.setVisible(false);

        final JCheckBox chkFixedWidth = new MCheckBox("&Fixed-Width Font?");
        chkFixedWidth.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if(chkFixedWidth.isSelected()) {
                    txtView.setFont(new Font("Courier New", txtView.getFont().getStyle(), txtView.getFont().getSize()));
                } else {
                    txtView.setFont(new Font("Arial", txtView.getFont().getStyle(), txtView.getFont().getSize()));
                }
            }
        });
/*
        btnSubmitNewJob = new MButton("&Submit New Xyce Run...", ImageUtil.getImage("add.gif"));
        btnSubmitNewJob.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    CommonFileChooser chooser = CommonFileChooser.getChooser("Choose Xyce Netlist File...");
                    FilterBuilder builder = new FilterBuilder(chooser, false);
                    builder.append("Xyce Netlist Files (*.cir)", "cir");
                    refresh = false;
                    if(chooser.showOpen(RunManagerFrame.this)) {
                        doSubmit(chooser.getSelectedFile());
                    }
                    refresh = true;

                } catch(Exception ee) {
                    ee.printStackTrace();
                    Dialogs.showDetails("An error occurred while submitting the job.",
                        ExceptionUtil.toCompleteString(ee, 4));
                }
            }
        });
*/
        buildPopupMenus();

        Lay.BLtg(this,
            "N", Lay.FL("L", lblE, cboEnvs, btnRefresh),
            "C", Lay.SPL(
                Lay.augb(
                    Lay.BL(
                        "C", Lay.sp(treJobs),
//                        "S", Lay.hn(btnSubmitNewJob, "enabled=false"),
                        "vgap=5"
                    ),
                    Lay.eb("5")
                ),
                Lay.augb(
                    Lay.BL(
                        "N", pnlViewFileHeader = Lay.BL(
                            "C", Lay.eb(lblViewFile = new JLabel(), "3"),
                            "E", chkFixedWidth,
                            "visible=false"
                        ),
                        "C", Lay.sp(txtView)
                    ),
                    Lay.eb("5")
                ),
                "divpixel=250"
            ),
            "S", Lay.BL(
                "W", Lay.FL(pgb, lblStatus = new JLabel()),
                "C", new JLabel(),
                "E", Lay.FL(btnClose)
            ),
            "size=[900,700]"
        );

        setIconImage(ImageUtil.getImage("redsky2.gif").getImage());
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setLocationRelativeTo(parent);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                refresh = false;
            }
            @Override
            public void windowClosed(WindowEvent e) {
                refresh = false;
            }
        });
    }

    private void buildPopupMenus() {
        JMenuItem mnuDownload = new MMenuItem("&Download", ImageUtil.getImage("download.gif"));
        mnuDownload.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doDownload();
            }
        });
        JMenuItem mnuView = new MMenuItem("&View", ImageUtil.getImage("view.gif"));
        mnuView.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doView();
            }
        });
        mnuFile = new JPopupMenu() {
            @Override
            public void setEnabled(boolean state) {
                super.setEnabled(state);
                for(int c = 0; c < getComponentCount(); c++) {
                    getComponent(c).setEnabled(state);
                }
            }
        };
        mnuFile.add(mnuView);
        mnuFile.add(mnuDownload);

        JMenuItem mnuDelete = new MMenuItem("&Delete", ImageUtil.getImage("remove.gif"));
        mnuDelete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doDelete();
            }
        });
        mnuJob = new JPopupMenu() {
            @Override
            public void setEnabled(boolean state) {
                super.setEnabled(state);
                for(int c = 0; c < getComponentCount(); c++) {
                    getComponent(c).setEnabled(state);
                }
            }
        };
        mnuJob.add(mnuDelete);
    }


    ///////////////////
    // ACTIVE THREAD //
    ///////////////////

    private void startAction(String actionStr, CommonRunnable runnable, ChangeListener callbackListener) {
        startAction(actionStr, false, runnable, callbackListener);
    }
    private void startAction(final String actionStr, final boolean suppressMouseChange, CommonRunnable runnable,
                             final ChangeListener callbackListener) {

        if(currentAction != null) {
            Dialogs.showWarning(this, "Please wait for previous action to finish.");
            return;
        }

        // DUH!
        GUIUtil.safeSync(new Runnable() {
            @Override
            public void run() {
                // UI Start Up
                pgb.setVisible(true);
                if(!suppressMouseChange) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                }

                btnRefresh.setEnabled(false);
                mnuFile.setEnabled(false);
                mnuJob.setEnabled(false);
                lblStatus.setText("  " + actionStr + "...");
            }
        });

        currentAction = new CommonThread(runnable);
        currentAction.addProgressListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                CommonThreadResult r = (CommonThreadResult) e.getSource();
                if(r.isDone()) {
                    // DUH!
                    GUIUtil.safeSync(new Runnable() {
                        public void run() {
                            treJobs.updateUI();
                            currentAction = null;

                            // UI Tear Down
                            pgb.setVisible(false);
                            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                            lblStatus.setText("");
                            btnRefresh.setEnabled(true);
                            mnuFile.setEnabled(true);
                            mnuJob.setEnabled(true);
                        }
                    });

                    if(callbackListener != null) {
                        callbackListener.stateChanged(null);
                    }
                }
            }
        });

        currentAction.start();
    }


    /////////////
    // ACTIONS //
    /////////////

    private void doRefresh() {
        doRefresh(false);
    }
    private void doRefresh(boolean suppressMouseChange) {
        startAction("Refreshing", suppressMouseChange, new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                GUIUtil.safeSync(new Runnable() {
                    public void run() {
                        env = (ExecutionEnv) cboEnvs.getSelectedItem();
                        try {
                            Set<Integer> activeProcs = env.getActiveProcs();
                            treJobs.saveState();
                            root.removeAllChildren();
                            ((NodeRoot) root.getUserObject()).setName("All " + env + " Jobs");
                            TNode actNode = new TNode(new NodeActiveProcs(activeProcs.size()));
                            root.add(actNode);
                            for(Integer proc : activeProcs) {
                                TNode dateNode = new TNode(new NodeActiveProc(proc.toString ()));
                                actNode.add(dateNode);
                            }

                            AllJobInfo allJobInfo = env.getJobs();
                            for(DateGroup group : allJobInfo.getDateGroups()) {
                                TNode dateNode = new TNode(new NodeDate(group.getName()));
                                root.add(dateNode);
                                for(Job job : group.getJobs()) {
                                    TNode jobNode = new TNode(new NodeJob(job.getName()));
                                    dateNode.add(jobNode);
                                    for(Resource resource : job.getResources()) {
                                        NodeBase uRes;
                                        String path = resource.getRemotePath();

                                        if      (path.endsWith ("model")) {
                                            uRes = new NodeFile (NodeFile.Type.Model,   path);
                                        } else if (path.endsWith ("out")) {
                                            uRes = new NodeFile (NodeFile.Type.Output,  path);
                                        } else if (path.endsWith ("err")) {
                                            uRes = new NodeFile (NodeFile.Type.Error,   path);
                                        } else if (path.endsWith ("result")) {
                                            uRes = new NodeFile (NodeFile.Type.Result,  path);
                                        } else if (path.endsWith ("console")) {
                                            uRes = new NodeFile (NodeFile.Type.Console, path);
                                        } else {
                                            continue;
                                        }
                                        TNode nRes = new TNode(uRes);
                                        jobNode.add(nRes);
                                    }
                                }
                            }

                            treJobs.restoreState();
                            lastRefreshTime = System.currentTimeMillis();
        //                    btnSubmitNewJob.setEnabled(true);
                        } catch(Exception e1) {
                            e1.printStackTrace();
                            Dialogs.showDetails(RunManagerFrame.this, "An error occurred while refreshing the job tree.",
                                ExceptionUtil.toCompleteString(e1, 4));
                        }
                    }
                });
            }
            public void cleanUp() {}
        }, null);
    }

    private void doView() {
        startAction("Fetching", new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                TNode node = (TNode) treJobs.getSelectionPaths()[0].getLastPathComponent();
                NodeFile nf = (NodeFile) node.getUserObject();
                try {
                    txtView.setText(env.getFileContents(nf.getRemotePath()));
                    txtView.setCaretPosition(0);
                    pnlViewFileHeader.setVisible(true);
                    lblViewFile.setText("<html>Viewing File: <b>" + nf.getRemotePath() + "</b></html>");
                } catch(Exception e1) {
                    e1.printStackTrace();
                    Dialogs.showDetails(RunManagerFrame.this, "An error occurred viewing the file.",
                        ExceptionUtil.toCompleteString(e1, 4));
                }
            }
            public void cleanUp() {}
        }, null);
    }

    private void doDownload() {
        startAction("Downloading", new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                TNode node = (TNode) treJobs.getSelectionPaths()[0].getLastPathComponent();
                NodeFile nf = (NodeFile) node.getUserObject();
                String remotePath = nf.getRemotePath();
                int lastSlash = remotePath.lastIndexOf('/');
                String name = remotePath.substring(lastSlash + 1);
                CommonFileChooser fc = CommonFileChooser.getChooser("Save " + nf.getType().getLabel());
                fc.setSelectedFile(new File(name));
                if(fc.showSave(RunManagerFrame.this)) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    try {
                        File dst = fc.getSelectedFile();
                        env.downloadFile(remotePath, dst);
                        Dialogs.showMessage(RunManagerFrame.this, "File downloaded.");
                    } catch(Exception e1) {
                        e1.printStackTrace();
                        Dialogs.showDetails(RunManagerFrame.this, "An error occurred while downloading file.",
                            ExceptionUtil.toCompleteString(e1, 4));
                    }
                }
            }
            public void cleanUp() {}
        }, null);
    }

    private void doDelete() {
        startAction("Deleting", new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                TNode node = (TNode) treJobs.getSelectionPaths()[0].getLastPathComponent();
                NodeJob nf = (NodeJob) node.getUserObject();
                TNode nodep = (TNode) node.getParent();
                NodeDate nd = (NodeDate) nodep.getUserObject();
                String jobName = nf.toString();
                String date = nd.toString();
                if(Dialogs.showConfirm(RunManagerFrame.this, "Are you sure you want to delete the job '" + jobName + "' from '" + date + "'?")) {
                    try {
                        env.deleteJob(jobName);
                        nodep.remove(node);
                        Dialogs.showMessage(RunManagerFrame.this, "Job Deleted");
                    } catch(Exception e1) {
                        e1.printStackTrace();
                        Dialogs.showDetails(RunManagerFrame.this, "An error occurred deleting the file.",
                            ExceptionUtil.toCompleteString(e1, 4));
                    }
                }
            }
            public void cleanUp() {}
        }, null);
    }
/*
    private void doSubmit(final File netlistFile) {
        startAction("Submitting", new CommonRunnable() {
            public void runThread(CommonThreadContext context) throws CommonThreadShutdownException {
                try {
                    env.submitJob(netlistFile);
                    Dialogs.showMessage("Xyce job submitted to RedSky.", "Success!");
                } catch(Exception e1) {
                    e1.printStackTrace();
                    Dialogs.showDetails("An error occurred submitting the job.",
                        ExceptionUtil.toCompleteString(e1, 4));
                }
            }
            public void cleanUp() {}
        }, new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if(currentAction == null) {
                    doRefresh(true);
                }
            }
        });
    }
*/
}
