/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.export;

import gov.sandia.umf.platform.plugins.extpoints.Exporter;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;

import replete.gui.controls.iconlist.IconList;
import replete.gui.controls.iconlist.Iconable;
import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.Dialogs;
import replete.gui.windows.EscapeDialog;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;
import replete.util.Lay;

public class ExportDialog extends EscapeDialog {


    ////////////
    // FIELDS //
    ////////////

    public static final int OK = 0;
    public static final int CANCEL = 1;

    private int state = CANCEL;

    private Exporter chosen;
    private JList lst;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ExportDialog(JFrame parent) {
        super(parent, "Export", true);
        setIconImage(ImageUtil.getImage("export.gif").getImage());

        JButton btnOk = new MButton("&Next", ImageUtil.getImage("forward.gif"), new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                goNext();
            }
        });
        JButton btnCancel = new MButton("&Cancel", ImageUtil.getImage("cancel.gif"), new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                closeDialog();
            }
        });

        DefaultListModel mdl = new DefaultListModel();

        List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint(Exporter.class);
        for(ExtensionPoint exp : exps) {
            Exporter ex = (Exporter) exp;
            ExporterGlob glob = new ExporterGlob(ex);
            mdl.addElement(glob);
        }

        getRootPane().setDefaultButton(btnOk);

        Lay.BLtg(this,
            "N", Lay.lb("<html>Please select a desired format to which to export the chosen records.</html>", "eb=5tlr"),
            "C", Lay.p(Lay.sp(lst = new IconList(mdl)), "eb=5"),
            "S", Lay.FL("R", btnOk, btnCancel, "xb=5"),
            "size=[300,400],center"
        );

        lst.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() > 1 && lst.getSelectedIndex() != -1) {
                    goNext();
                }
            }
        });
    }

    public int getState() {
        return state;
    }
    public Exporter getExporter() {
        return chosen;
    }
    private void goNext() {
        if(lst.getSelectedIndex() == -1) {
            Dialogs.showWarning("Please select an exporter first.");
            return;
        }
        chosen = ((ExporterGlob) lst.getSelectedValue()).getExporter();
        state = OK;
        closeDialog();
    }


    /////////////////
    // INNER CLASS //
    /////////////////

    private class ExporterGlob implements Iconable {
        private Exporter exporter;
        public ExporterGlob(Exporter e) {
            exporter = e;
        }

        public Exporter getExporter() {
            return exporter;
        }
        public Icon getIcon() {
            return exporter.getIcon();
        }
        public String getDescription() {
            return exporter.getDescription();
        }
        @Override
        public String toString() {
            return exporter.getName();
        }
    }
}
