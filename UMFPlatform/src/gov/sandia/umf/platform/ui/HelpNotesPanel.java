/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.IconButton;
import replete.util.Lay;

// TODO: Maybe can change the label to a transparent text area/pane
// to get scrolling in case the help message is too long.
// TODO: Implement resize and dragging?

public class HelpNotesPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    // Const

    public static final int INIT_WIDTH = 250;
    public static final int INIT_HEIGHT = 200;

    // Core

    private UIController uiController;

    // UI

    private JLabel lblHeader;
    private JLabel lblContent;


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier hideHelpNotifier = new ChangeNotifier(this);
    public void addHideHelpListener(ChangeListener listener) {
        hideHelpNotifier.addListener(listener);
    }
    protected void fireHideHelpNotifier() {
        hideHelpNotifier.fireStateChanged();
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public HelpNotesPanel(UIController uic) {
        uiController = uic;

        IconButton btnClose = new IconButton(ImageUtil.getImage("close.gif"));
        btnClose.toImageOnly();
        btnClose.setRolloverIcon(ImageUtil.getImage("closehover.gif"));
        btnClose.setPressedIcon(ImageUtil.getImage("closehoverp.gif"));
        btnClose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fireHideHelpNotifier();
            }
        });

        IconButton btnMore = new IconButton(ImageUtil.getImage("forward.gif"));
        btnMore.toImageOnly();
        btnMore.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.notImpl();
            }
        });

        Lay.BLtg(this,
            "N", Lay.BL(
                "C", lblHeader = new JLabel(""),
                "E", btnClose,
                "eb=5,augb=mb(1b,[255,149,28]),augb=mb(1b,[188,110,20]),opaque=false"
            ),
            "C", Lay.BL("N", lblContent = new JLabel(""), "opaque=false,eb=5"),
            "S", Lay.BL("E",
                Lay.BL("C", Lay.lb("More "), "E", btnMore, "opaque=false"),
                    "eb=5,augb=mb(1t,[255,149,28]),augb=mb(1t,[188,110,20]),opaque=false"),
            "bg=[255,255,180],size=[" + INIT_WIDTH + "," + INIT_HEIGHT + "],augb=mb(2,[255,149,28]),augb=mb(2,[188,110,20]),augb=mb(2,[127,74,14])"
        );

        lblHeader.setIcon(ImageUtil.getImage("help.gif"));
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public void setHelpNotes(String topic, String content) {
        lblHeader.setText("<html><i>" + topic + "</i></html>");
        lblContent.setText("<html>" + content + "</html>");
    }
}
