/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.partadv;

import gov.sandia.umf.platform.connect.orientdb.ui.ConnectionManager;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientConnectDetails;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientDatasource;
import gov.sandia.umf.platform.plugins.extpoints.AbstractRecordHandler;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.gui.controls.mnemonics.MButton;
import replete.gui.tabbed.AdvancedTabbedPane;
import replete.gui.windows.EscapeFrame;
import replete.util.Lay;
import replete.util.RandomUtil;

public class AdvancedMain {
    public static void main(String[] args) {
        OrientConnectDetails details = new OrientConnectDetails(
            "N2A Remote", "remote:dtrumbo1.srn.sandia.gov/n2a", "admin", "admin"
        );
        ConnectionManager.getInstance().connect(details);
        final OrientDatasource source = ConnectionManager.getInstance().getDataModel();
//        NDoc doc = new NDoc("gov.sandia.umf.n2a$PartX");
//        doc.save();
//        source.deleteClass("gov.sandia.umf.n2a$PartWrapper");
//        source.deleteAllRecords("gov.sandia.umf.n2a$PartX");
//        source.deleteAllRecords("gov.sandia.umf.n2a$PartXWrapper");
        List<NDoc> parts = source.getByQuery("gov.sandia.umf.n2a$Part", "where name = 'HHmod (Advanced)'");
        final NDoc part;
        if(parts.size() == 0) {
            NDoc parent = new NDoc("gov.sandia.umf.n2a$Part");
            parent.set("name", "passive (Advanced)");
            parent.set("parent", null);
            parent.set("type", "compartment");

            part = new NDoc("gov.sandia.umf.n2a$Part");
            part.set("name", "HHmod (Advanced)");
            part.set("parent", parent);
            part.set("type", "compartment");

            NDoc parentIonCh = new NDoc("gov.sandia.umf.n2a$Part");
            parentIonCh.set("name", "ion channel base (Advanced)");
            parentIonCh.set("parent", null);
            parentIonCh.set("type", "compartment");

            NDoc partNA = new NDoc("gov.sandia.umf.n2a$Part");
            partNA.set("name", "NA_Koch (Advanced)");
            partNA.set("parent", parentIonCh);
            partNA.set("type", "compartment");

            NDoc partNAChild = new NDoc("gov.sandia.umf.n2a$Part");
            partNAChild.set("name", "NA_Koch Internal (Advanced)");
            partNAChild.set("parent", partNA);
            partNAChild.set("internal-part", true);
            partNAChild.set("type", "compartment");

            NDoc wrapper = new NDoc("gov.sandia.umf.n2a$PartWrapper");
            wrapper.set("alias", "Na");
            wrapper.set("dest", partNAChild);
            NDoc layout = new NDoc();
            layout.set("x", 10);
            layout.set("y", 20);
            layout.set("w", 100);
            layout.set("h", 40);
            wrapper.set("layout", layout);

            NDoc partK = new NDoc("gov.sandia.umf.n2a$Part");
            partK.set("name", "K_Koch (Advanced)");
            partK.set("parent", parentIonCh);
            partK.set("type", "compartment");

            NDoc partKChild = new NDoc("gov.sandia.umf.n2a$Part");
            partKChild.set("name", "K_Koch Internal (Advanced)");
            partKChild.set("parent", partK);
            partKChild.set("internal-part", true);
            partKChild.set("type", "compartment");

            NDoc wrapper2 = new NDoc("gov.sandia.umf.n2a$PartWrapper");
            wrapper2.set("alias", "K");
            wrapper2.set("dest", partKChild);
            layout = new NDoc();
            layout.set("x", 150);
            layout.set("y", 20);
            layout.set("w", 100);
            layout.set("h", 40);
            wrapper2.set("layout", layout);

            List<NDoc> children = part.getAndSetValid("children",
                new ArrayList<NDoc>(), List.class);
            children.add(wrapper);
            children.add(wrapper2);
            part.set("children", children);

            part.save();
        } else {
            part = parts.get(0);
        }

        part.setHandler(new AbstractRecordHandler() {
            @Override
            public String getTitle(NDoc doc) {
                return doc.get("name");
            }
            @Override
            public ImageIcon getIcon(NDoc doc) {
                return RandomUtil.flip(ImageUtil.getImage("comp.gif"), ImageUtil.getImage("conn.gif"));
            }
        });

        TestFrame frame = new TestFrame(part);
        frame.addClosingListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                part.saveRecursive();
            }
        });
        frame.setVisible(true);
    }

    private class ChildWrapper {
        private String alias;
        private NDoc child;
        private Rectangle layout;
    }

    private static class TestFrame extends EscapeFrame {
        private AdvancedTabbedPane tabs;
        public TestFrame(final NDoc part) {
            super("N2A Advanced Part Edit Panel Test Application");
            setIcon(ImageUtil.getImage("n2a.gif"));
            JButton btn = new MButton("&Save Record", ImageUtil.getImage("save.gif"), new ActionListener() {
                public void actionPerformed(ActionEvent e) {
//                    part.dump();
//                    List<NDoc> children = part.get("children");
//                    for(NDoc child : children) {
//                        System.out.println(child);
//                        child.dumpDebug(null);
//                        NDoc layout = child.get("layout");
//                        layout.dumpDebug("layout");
//                    }
                    part.saveRecursive();
                }
            });
            Lay.BLtg(this,
                "N", Lay.FL("R", btn, "bg=AAA,mb=[1b,black]"),
                "C", tabs = Lay.TBL(part.getAndSet("name", "Americas"),
                    ImageUtil.getImage("comp.gif"),
                    new AdvancedDetailPanel(new UIController(null, null), part)),
                "size=[600,600],center=2"
            );
        }
    }
}
