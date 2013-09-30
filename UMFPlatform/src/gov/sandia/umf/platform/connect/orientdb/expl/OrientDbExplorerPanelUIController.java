/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.expl;

import gov.sandia.umf.platform.connect.orientdb.ui.OrientConnectDetails;
import gov.sandia.umf.platform.ui.UIController;

import java.awt.Cursor;
import java.util.List;

import javax.swing.SwingWorker;

import replete.gui.controls.simpletree.TNode;
import replete.gui.windows.Dialogs;
import replete.util.GUIUtil;

public class OrientDbExplorerPanelUIController {
    private OrientDbExplorerPanel panel;
    private OrientDbExplorerPanelTreeModel model;
    private UIController platUiController;

    public OrientDbExplorerPanelUIController(OrientDbExplorerPanel p,
            OrientDbExplorerPanelTreeModel mdl, UIController platUiController) {
        panel = p;
        model = mdl;
        this.platUiController = platUiController;
    }

    public void databaseRefresh(final TNode nActive) {
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<Void, TNode> task = new SwingWorker<Void, TNode>() {
            @Override
            protected Void doInBackground() throws Exception {
                model.dbRefresh(nActive);
                publish(nActive);
                return null;
            }
            @Override
            protected void process(List<TNode> chunks) {
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                panel.expandAll(chunks.get(0));
            }
        };
        task.execute();
    }

    public void databaseDelete(TNode nActive) {
        model.dbDelete(nActive);
    }

    public void databaseAdd(final TNode nActive) {
        final OrientConnectDetails details;
        if(platUiController != null) {
            details = platUiController.getDMM().getConnectDetails();
        } else {
            String loc = Dialogs.showInput("Enter connection location:", "Location", "remote:localhost/test");
            if(loc == null) {
                return;
            }
            details = new OrientConnectDetails("DB!", loc, "admin", "admin");
        }
        if(details != null) {
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            SwingWorker<Void, TNode> task = new SwingWorker<Void, TNode>() {
                @Override
                protected Void doInBackground() throws Exception {
                    TNode nDb = model.addSource(new OrientConnectDetails(
                        details.getName(), details.getLocation(),
                        details.getUser(), details.getPassword()));
                    publish(nDb);
                    return null;
                }
                @Override
                protected void process(List<TNode> chunks) {
                    panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    panel.expandAll(chunks.get(0));
                }
                @Override
                protected void done() {
                    try {
                        get();
                    } catch(final Exception e) {
                        GUIUtil.safe(new Runnable() {
                            public void run() {
                                Dialogs.showDetails("An error occurred executing this action.", e);
                                panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                            }
                        });
                    }
                }

            };
            task.execute();
        }
    }

    public void classRefresh(TNode nActive) {
        classLoad(nActive);
    }

    public void classLoad(final TNode nActive) {
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<Void, Void> task = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                model.classRefresh(nActive);
                publish();
                return null;
            }
            @Override
            protected void process(List<Void> chunks) {
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                panel.expand(nActive);
            }
        };
        task.execute();
    }

    public void recordDelete(final TNode nActive) {
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<Void, Void> task = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                model.recordDelete(nActive);
                publish();
                return null;
            }
            @Override
            protected void process(List<Void> chunks) {
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                panel.expand(nActive);
            }
        };
        task.execute();
    }
    public void recordRefresh(final TNode nActive) {
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<Void, Void> task = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                model.recordRefresh(nActive);
                publish();
                return null;
            }
            @Override
            protected void process(List<Void> chunks) {
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                panel.expand(nActive);
            }
        };
        task.execute();
    }
    /*private class LocalSwingWorker<T, V> extends SwingWorker<T, V> {
        private Runnable operation;
        public LocalSwingWorker<T, V>(Runnable operation) {
        }
    }*/

    public void fieldDelete(final TNode nActive) {
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<Void, Void> task = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                model.fieldDelete(nActive);
                publish();
                return null;
            }
            @Override
            protected void process(List<Void> chunks) {
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                //panel.expand(nActive.get`); node doesn't exists anymore
            }
        };
        task.execute();
    }
}
