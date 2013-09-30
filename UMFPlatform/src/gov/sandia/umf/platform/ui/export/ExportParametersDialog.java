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

import javax.swing.JButton;
import javax.swing.JFrame;

import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.Dialogs;
import replete.gui.windows.EscapeDialog;
import replete.util.Lay;

public class ExportParametersDialog extends EscapeDialog {


    ////////////
    // FIELDS //
    ////////////

    public static final int OK = 0;
    public static final int CANCEL = 1;

    private int state = CANCEL;

    private ExportParametersPanel pnlParams;
    private ExportParameters params;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ExportParametersDialog(JFrame parent, Exporter exporter) {
        super(parent, exporter.getName() + " Export Parameters", true);
        setIconImage(exporter.getIcon().getImage());

        JButton btnExport = new MButton("E&xport", ImageUtil.getImage("export.gif"), new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String validation = pnlParams.getValidation();
                if(validation != null) {
                    Dialogs.showWarning(validation);
                    return;
                }
                params = pnlParams.getParams();
                state = OK;
                closeDialog();
            }
        });
        JButton btnCancel = new MButton("&Cancel", ImageUtil.getImage("cancel.gif"), new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                closeDialog();
            }
        });

        Lay.BLtg(this,
            "N", Lay.lb("<html>Please provide the needed parameters for this exporter.</html>", "eb=5tlr"),
            "C", Lay.p(pnlParams = exporter.getParametersPanel(), "eb=5"),
            "S", Lay.FL("R", btnExport, btnCancel),
            "size=[400,300],center"
        );

        getRootPane().setDefaultButton(btnExport);
    }

    public int getState() {
        return state;
    }
    public ExportParameters getParameters() {
        return params;
    }
}
