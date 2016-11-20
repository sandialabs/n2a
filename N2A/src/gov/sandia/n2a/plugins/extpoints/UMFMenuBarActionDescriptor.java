/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.plugins.extpoints;

import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JMenuItem;

import replete.gui.uiaction.MenuBarActionDescriptor;

public class UMFMenuBarActionDescriptor extends MenuBarActionDescriptor {
    private UMFMenuActionListener umfListener;
    public UMFMenuActionListener getUmfListener() {
        return umfListener;
    }

    public UMFMenuBarActionDescriptor() {
    }

    public UMFMenuBarActionDescriptor(String text, int mnemonic,
                                      Map<String, Boolean> enabledStateMap) {
        super(text, mnemonic, enabledStateMap);
    }

    public UMFMenuBarActionDescriptor(String path, JMenuItem mnu,
                                      Map<String, Boolean> enabledStateMap) {
        super(path, mnu, enabledStateMap);
    }

    public UMFMenuBarActionDescriptor(String path, String text, int mnemonic,
                                      Map<String, Boolean> enabledStateMap) {
        super(path, text, mnemonic, enabledStateMap);
    }

    public UMFMenuBarActionDescriptor(String path, String text, int mnemonic, ImageIcon icon,
                                      Map<String, Boolean> enabledStateMap, UMFMenuActionListener listener) {
        super(path, text, mnemonic, icon, enabledStateMap, null);
        umfListener = listener;
    }

    public UMFMenuBarActionDescriptor(String path, String text, int mnemonic, ImageIcon icon,
                                      Map<String, Boolean> enabledStateMap, boolean isCheckMenu,
                                      int accKey, boolean accCtrl, UMFMenuActionListener listener) {
        super(path, text, mnemonic, icon, enabledStateMap, isCheckMenu, accKey, accCtrl, null);
        umfListener = listener;
    }

    public UMFMenuBarActionDescriptor(String path, String text, int mnemonic, ImageIcon icon,
                                      Map<String, Boolean> enabledStateMap, boolean isCheckMenu,
                                      int accKey, boolean accCtrl, boolean accShift,
                                      UMFMenuActionListener listener) {
        super(path, text, mnemonic, icon, enabledStateMap, isCheckMenu, accKey, accCtrl, accShift,
            null);
        umfListener = listener;
    }

}
