/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble;

import gov.sandia.n2a.parms.ParameterBundle;
import gov.sandia.n2a.parms.ParameterDomain;
import gov.sandia.n2a.parms.ParameterKeyPath;
import gov.sandia.n2a.parms.ParameterSpecification;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.GradientPanel;
import replete.gui.controls.IconButton;
import replete.util.Lay;

public class ParameterSpecPanel extends GradientPanel {


    ////////////
    // FIELDS //
    ////////////

    private ParameterBundle bundle;
    private ParameterSpecification spec;
    private JLabel lblSpec;
    private boolean readOnly;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ParameterSpecPanel(ParameterBundle bundle, ParameterSpecification spec, boolean readOnly) {
        this.readOnly = readOnly;
        this.bundle = bundle;
        this.spec = spec;
        IconButton btnEdit = new IconButton(ImageUtil.getImage("edit.gif"), "Edit Specification...", 2);
        IconButton btnRemove = new IconButton(ImageUtil.getImage("remove.gif"), "Remove Specification", 2);
        btnEdit.toImageOnly();
        btnRemove.toImageOnly();
        btnEdit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                edit();
            }
        });
        btnRemove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fireRemoveNotifier();
            }
        });

        JComponent cmpEdit;
        if(readOnly) {
            cmpEdit = Lay.p();
        } else {
            cmpEdit = listen(
                Lay.FL(btnEdit, btnRemove, "opaque=false")
            );
        }

        Lay.BLtg(this,
            "C", Lay.BxL("Y",
                Lay.BL(
                    "W", listen(
                        Lay.lb("<html><u>Parameter:</u></html>", "dimw=90,eb=5l")
                    ),
                    "C", listen(
                        Lay.lb(constructParamLabelText())
                    ),
                    "opaque=false,nodexbug"
                ),
                Lay.BL(
                    "W", listen(
                        Lay.lb("<html><u>Specification:</u></html>", "dimw=90,eb=5l")
                    ),
                    "C", listen(
                        lblSpec = Lay.lb()
                    ),
                    "opaque=false,nodexbug"
                ),
                "opaque=false,nodexbug"
            ),
            "E", cmpEdit,
            "gradient,gradclr1=E2FBFF,gradclr2=DDDDFF,minh=50,prefh=50,maxh=90000"
        );
        if(!readOnly) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        Lay.hn(this, "mb=[1,black]");
        setAngle(45);

        listen(this);

        updateFromSpec();
    }

    private JComponent listen(JComponent cmp) {
        if(!readOnly) {
            cmp.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    edit();
                }
            });
        }
        return cmp;
    }

    private Object constructParamLabelText() {
        ParameterKeyPath P = new ParameterKeyPath();
        for(ParameterDomain domain : bundle.getDomains()) {
            P.add(domain.getName());
        }
        P.add(bundle.getParameter().getKey());
        return "<html>" + P.toHtml(true) + "</html>";
    }


    ///////////////
    // NOTIFIERS //
    ///////////////

    protected ChangeNotifier removeNotifier = new ChangeNotifier(this);
    public void addRemoveListener(ChangeListener listener) {
        removeNotifier.addListener(listener);
    }
    protected void fireRemoveNotifier() {
        removeNotifier.fireStateChanged();
    }
    protected ChangeNotifier editNotifier = new ChangeNotifier(this);
    public void addEditListener(ChangeListener listener) {
        editNotifier.addListener(listener);
    }
    protected void fireEditNotifier() {
        editNotifier.fireStateChanged();
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessors

    public ParameterBundle getParamBundle() {
        return bundle;
    }
    public ParameterSpecification getSpecification() {
        return spec;
    }

    // Mutators

    public void setSpecification(ParameterSpecification s) {
        spec = s;
        updateFromSpec();
    }


    //////////
    // MISC //
    //////////

    private void edit() {
        JDialog parent = (JDialog) SwingUtilities.getRoot(ParameterSpecPanel.this);
        ParameterSpecEditDialog dlg = new ParameterSpecEditDialog(parent, spec);
        dlg.setVisible(true);
        if(dlg.getResult() == ParameterSpecEditDialog.CHANGE) {
            spec = dlg.getSpecification();
            updateFromSpec();
            fireEditNotifier();
        }
    }

    private void updateFromSpec() {
        lblSpec.setText("<html>" + spec.toShortString().replaceAll("<", "&lt;") + "</html>");
    }
}
