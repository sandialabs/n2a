/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import javax.swing.event.ChangeListener;

import replete.threads.CommonRunnable;

public class UIAction {
    private CommonRunnable task;
    private String inProgressCaption = "Processing";
    private String errorMessage = "performing this task";
    private boolean suppressMouse = false;
    private String blockedBy;
    private ChangeListener changeListener;
    private boolean onUIThread = false;
    private boolean usep = false;

    public UIAction() {}

    public CommonRunnable getTask() {
        return task;
    }
    public String getInProgressCaption() {
        return inProgressCaption;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    public boolean isSuppressMouse() {
        return suppressMouse;
    }
    public String getBlockedBy() {
        return blockedBy;
    }
    public ChangeListener getChangeListener() {
        return changeListener;
    }
    public boolean isOnUIThread() {
        return onUIThread;
    }
    public boolean useProgressBar() {
        return usep;
    }

    public void setTask(CommonRunnable task) {
        this.task = task;
    }
    public void setInProgressCaption(String inProgressCaption) {
        this.inProgressCaption = inProgressCaption;
    }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    public void setSuppressMouse(boolean suppressMouse) {
        this.suppressMouse = suppressMouse;
    }
    public void setBlockedBy(String blockedBy) {
        this.blockedBy = blockedBy;
    }
    public void setChangeListener(ChangeListener changeListener) {
        this.changeListener = changeListener;
    }
    public void setOnUIThread(boolean onUIThread) {
        this.onUIThread = onUIThread;
    }
    public void setUseProgressBar(boolean use) {
        usep = use;
    }

}
