/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import javax.swing.event.ChangeListener;

import replete.threads.CommonRunnable;

public class UIActionBuilder {

    private UIAction action = new UIAction();  // One-time use builder.

    public UIActionBuilder() {}

    public UIActionBuilder setInProgressCaption(String inProgressCaption) {
        action.setInProgressCaption(inProgressCaption);
        return this;
    }
    public UIActionBuilder setErrorMessage(String errorMessage) {
        action.setErrorMessage(errorMessage);
        return this;
    }
    public UIActionBuilder setSuppressMouse(boolean suppressMouse) {
        action.setSuppressMouse(suppressMouse);
        return this;
    }
    public UIActionBuilder setBlockedBy(String blockedBy) {
        action.setBlockedBy(blockedBy);
        return this;
    }
    public UIActionBuilder setChangeListener(ChangeListener changeListener) {
        action.setChangeListener(changeListener);
        return this;
    }
    public UIActionBuilder setOnUIThread(boolean onUIThread) {
        action.setOnUIThread(onUIThread);
        return this;
    }
    public UIActionBuilder setTask(CommonRunnable runnable) {
        action.setTask(runnable);
        return this;
    }
    public UIActionBuilder setUseProgressBar(boolean use) {
        action.setUseProgressBar(use);
        return this;
    }

    public UIAction getAction() {
        return action;
    }
}
