/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.threads.CommonThread;
import replete.threads.CommonThreadResult;
import replete.util.GUIUtil;

public class UIActionManager {
    private Map<UIAction, CommonThread> tasks = new ConcurrentHashMap<UIAction, CommonThread>();
    private int barTasks;

    private MainFrame parentRef;

    public synchronized void startProgressBar(String actionStr) {
        barTasks++;
        parentRef.getStatusBar().setProgressBarIndeterminate(true);
        parentRef.getStatusBar().setShowProgressBar(true);
        parentRef.getStatusBar().setStatusMessage(" " + actionStr + "...");
    }


    public UIActionManager(MainFrame p) {
        parentRef = p;
    }

    public void submit(final UIAction action) {
/*        if(currentAction != null) {
            Dialogs.showWarning("Please wait for previous action to finish.");
            return;
        }*/

        if(action.useProgressBar()) {
            startProgressBar(action.getInProgressCaption());
        }

        // UI Start Up
        /*parentRef.getStatusBar().setProgressBarIndeterminate(true);
        parentRef.getStatusBar().setShowProgressBar(true);
        parentRef.getStatusBar().setStatusMessage(" " + actionStr + "...");
        if(!suppressMouseChange) {
            parentRef.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }*/

        final CommonThread currentAction = new CommonThread(action.getTask());
        tasks.put(action, currentAction);

        currentAction.addProgressListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                CommonThreadResult r = (CommonThreadResult) e.getSource();
                if(r.isDone()) {
                    /*currentAction = null;

                    // UI Tear Down
                    parentRef.getStatusBar().setShowProgressBar(false);
                    parentRef.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    parentRef.getStatusBar().setStatusMessage("");

                    if(callbackListener != null) {
                        callbackListener.stateChanged(null);
                    }*/
                    tasks.remove(action);
                }
                /*if(r.isError()) {
                    UMF.handleUnexpectedError(null, r.getPrimaryError(), "An error occurred while " + operation + ".");
                }*/
            }
        });

        if(action.isOnUIThread()) {
            GUIUtil.safe(new Runnable() {
                public void run() {
                    currentAction.run();
                }
            });
        } else {
            currentAction.start();
        }
    }
}
