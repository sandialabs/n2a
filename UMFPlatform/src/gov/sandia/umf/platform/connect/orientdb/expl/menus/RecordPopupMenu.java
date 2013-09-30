/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.expl.menus;

import gov.sandia.umf.platform.connect.orientdb.expl.OrientDbExplorerPanelUIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import replete.gui.controls.mnemonics.MMenuItem;
import replete.gui.controls.simpletree.TNode;

public class RecordPopupMenu extends JPopupMenu {
    private static OrientDbExplorerPanelUIController uiController;

    private TNode nActive;

    public RecordPopupMenu(OrientDbExplorerPanelUIController uic) {
        uiController = uic;
        init();
    }

    public void show(Component cmp, int x, int y, TNode nAct) {
        nActive = nAct;
        super.show(cmp, x, y);
    }

    private void init() {
        JMenuItem mnuDelete = new MMenuItem("&Delete", ImageUtil.getImage("remove.gif"));
        mnuDelete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.recordDelete(nActive);
            }
        });
        JMenuItem mnuRefresh = new MMenuItem("&Refresh", ImageUtil.getImage("refresh.gif"));
        mnuRefresh.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.recordRefresh(nActive);
            }
        });

        add(mnuDelete);
        add(mnuRefresh);
    }
}
