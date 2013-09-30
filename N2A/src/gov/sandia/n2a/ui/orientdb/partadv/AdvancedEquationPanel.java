/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.partadv;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import java.awt.Color;

import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.GradientPanel;
import replete.util.GUIUtil;
import replete.util.Lay;

public class AdvancedEquationPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    private AdvancedEquationTableModel eqMdl;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public AdvancedEquationPanel(NDoc part, final PartGraphContext context) {
        eqMdl = new AdvancedEquationTableModel();
        eqMdl.setEquations(part);

        context.addPartChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                eqMdl.setEquations(context.getDivedPart());
            }
        });

        Color gradclr2 = GUIUtil.deriveColor(GradientPanel.INIT_COLOR, 0, 0, 10);

        Lay.BLtg(this,
            "N", Lay.BL(
                "W", Lay.lb("<html>Equations for <font color='blue'>" + part.getTitle() + "</font></html>", "eb=5"),
                "E", Lay.FL("R",
                    Lay.lb("Include:"),
                    Lay.chk("Inherited", "opaque=false"),
                    Lay.chk("Overridden", "opaque=false"),
                    Lay.chk("Children", "opaque=false"),
                    "opaque=false"
                ),
                "gradient,gradclr2=" + Lay.clr(gradclr2)
            ),
            "C", Lay.sp(new AdvancedEquationTable(eqMdl))
        );

        eqMdl.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                fireChangeNotifier();
            }
        });
    }


    protected ChangeNotifier changeNotifier = new ChangeNotifier(this);
    public void addChangeListener(ChangeListener listener) {
        changeNotifier.addListener(listener);
    }
    protected void fireChangeNotifier() {
        changeNotifier.fireStateChanged();
    }

    public void setPart(NDoc part) {
        eqMdl.setEquations(part);
    }
}
