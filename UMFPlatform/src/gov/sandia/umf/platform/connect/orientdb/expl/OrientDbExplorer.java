/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.expl;

import gov.sandia.umf.platform.connect.orientdb.expl.images.ImageUtil;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientConnectDetails;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingWorker;

import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.EscapeFrame;
import replete.util.GUIUtil;
import replete.util.Lay;

public class OrientDbExplorer extends EscapeFrame {


    ////////////
    // FIELDS //
    ////////////

    private OrientDbExplorerPanel pnlMain;
    private List<OrientConnectDetails> detailList;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public OrientDbExplorer() {
        super("Orient DB Explorer");
        buildUI();
    }
    public OrientDbExplorer(OrientConnectDetails details) {
        super("Orient DB Explorer");
        buildUI();
        detailList = new ArrayList<OrientConnectDetails>();
        detailList.add(details);
    }
    public OrientDbExplorer(List<OrientConnectDetails> details) {
        super("Orient DB Explorer");
        buildUI();
        detailList = details;
    }

    public void init() {
        GUIUtil.safeSync(new Runnable() {
            public void run() {
                waitOn();
            }
        });

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                for(OrientConnectDetails d : detailList) {
//                    pnlMain.addSource(d);
                }
                return null;
            }

            @Override
            protected void done() {
                waitOff();
            };
        };
        worker.execute();
    }

    private void buildUI() {
        setIconImage(ImageUtil.getImage("orient.png").getImage());

        Lay.BLtg(this,
            "C", pnlMain = new OrientDbExplorerPanel(null),
            "S", Lay.FL("R",
                new MButton("&Close", ImageUtil.getImage("cancel.gif"), new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        closeFrame();
                    }
                })
            ),
            "size=[1000,700],center"
        );

        getRootPane().setDefaultButton(pnlMain.getDefaultButton());
    }

    public void addDefaultSource() {
        pnlMain.addDefaultSource();
    }


    //////////
    // TEST //
    //////////

    public static void main(String[] args) {

//        String loc = "remote:localhost/test";
//        String loc = "local:C:/Users/dtrumbo/Desktop/orient/A";
        String loc = "remote:dtrumbo1.srn.sandia.gov/n2a";
        if(args != null && args.length != 0) {
            loc = args[0];
        }
        OrientConnectDetails initDetails =
            new OrientConnectDetails(
                "Test", loc,
                "admin", "admin");
        OrientDbExplorer frame = new OrientDbExplorer(initDetails);
        frame.addDefaultSource();
        frame.setVisible(true);
        frame.init();
    }
}
