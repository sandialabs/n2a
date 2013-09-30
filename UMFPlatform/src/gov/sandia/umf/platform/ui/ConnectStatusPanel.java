/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.util.Lay;

public class ConnectStatusPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    // Core

    private UIController uiController;

    // UI

    private JLabel lblDbStatus;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ConnectStatusPanel(UIController uic) {
        uiController = uic;

        lblDbStatus = Lay.lb("Not connected", ImageUtil.getImage("repodown2-sk.gif"), "xeb=5");

        Lay.BLtg(this,
            "C", lblDbStatus,
            "eb=5rl"
        );

        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                uiController.openOrientDbConnect();
            }
        });

        uiController.getDMM().addConnectListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if(uiController.getDMM().isConnected()) {
                    lblDbStatus.setIcon(ImageUtil.getImage("repo-sk.gif"));
                    lblDbStatus.setText("<html>Connected to:  <font color='#008800'>" + uiController.getDMM().getConnectDetails().getName() + "</font></html>");
                } else {
                    lblDbStatus.setIcon(ImageUtil.getImage("repodown2-sk.gif"));
                    lblDbStatus.setText("Not connected");
                }
            }
        });
    }
}
