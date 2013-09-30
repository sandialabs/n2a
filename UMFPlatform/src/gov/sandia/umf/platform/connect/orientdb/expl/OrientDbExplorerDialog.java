/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.expl;

import gov.sandia.umf.platform.connect.orientdb.expl.images.ImageUtil;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientConnectDetails;

import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.EscapeDialog;
import replete.util.Lay;

public class OrientDbExplorerDialog extends EscapeDialog {


    ////////////
    // FIELDS //
    ////////////

    private OrientDbExplorerPanel pnlMain;


    //////////////////
    // CONSTRUCTORS //
    //////////////////

    public OrientDbExplorerDialog(Dialog owner, boolean modal) {
        super(owner, "Orient DB Explorer", modal);
        buildUI();
    }

    public OrientDbExplorerDialog(Dialog owner, boolean modal, OrientConnectDetails details) {
        super(owner, "Orient DB Explorer", modal);
        buildUI();
//        pnlMain.addSource(details);
    }

    public OrientDbExplorerDialog(Dialog owner, boolean modal, List<OrientConnectDetails> details) {
        super(owner, "Orient DB Explorer", modal);
        buildUI();
        for(OrientConnectDetails d : details) {
//            pnlMain.addSource(d);
        }
    }

    private void buildUI() {
        setIconImage(ImageUtil.getImage("orient.png").getImage());

        Lay.BLtg(this,
            "C", pnlMain = new OrientDbExplorerPanel(null),
            "S", Lay.FL("R",
                new MButton("&Close", ImageUtil.getImage("cancel.gif"), new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        closeDialog();
                    }
                })
            ),
            "size=[1000,700],center"
        );

        getRootPane().setDefaultButton(pnlMain.getDefaultButton());
    }


    //////////
    // TEST //
    //////////

    public static void main(String[] args) {
        String loc = "local:C:/Users/dtrumbo/Desktop/orient/tesasdftz";
        if(args != null && args.length != 0) {
            loc = args[0];
        }
        OrientConnectDetails initDetails =
            new OrientConnectDetails(
                "Test", loc,
                "admin", "admin");
        OrientDbExplorerDialog frame = new OrientDbExplorerDialog(null, true, initDetails);
        frame.setVisible(true);
    }
}
