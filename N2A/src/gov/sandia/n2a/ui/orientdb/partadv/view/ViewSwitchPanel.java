/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.orientdb.partadv.view;


import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.GradientPanel;
import replete.util.Lay;

public class ViewSwitchPanel extends JPanel {

    private GradientPanel pnlActive;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ViewSwitchPanel() {
        JLabel lblGraph =
            Lay.lb(ImageUtil.getImage("advobjects.png"),
                "cursor=hand");
        JLabel lblInherits =
            Lay.lb(ImageUtil.getImage("advinherits.png"),
                "cursor=hand");
        JLabel lblConnects =
            Lay.lb(ImageUtil.getImage("advconnects.png"),
                "cursor=hand");

        GradientPanel pnlGraph;
        GradientPanel pnlInherits;
        GradientPanel pnlConnects;

        String panelStyle = "gradient,gradclr1=white,gradclr2=C6E1FF,eb=5,dim=[48,48],bg=9099AE,cursor=hand";

        Lay.GLtg(this, 3, 1,
            pnlGraph = (GradientPanel) Lay.p(lblGraph, panelStyle),
            pnlInherits = (GradientPanel) Lay.p(lblInherits, panelStyle),
            pnlConnects = (GradientPanel) Lay.p(lblConnects, panelStyle),
            "mb=[2,#000066],prefh=200,bg=9099AE"
        );

        pnlGraph.setToolTipText("Contains");
        pnlInherits.setToolTipText("Inherits");
        pnlConnects.setToolTipText("Connects");

        attach(pnlGraph, lblGraph, View.GRAPH);
        attach(pnlInherits, lblInherits, View.INHERITS);
        attach(pnlConnects, lblConnects , View.CONNECTS);

        pnlInherits.setGradientDisabled(true);
        pnlConnects.setGradientDisabled(true);

        pnlActive = pnlGraph;
    }

    private void attach(final GradientPanel pnl, final JLabel lbl, final View view) {
        pnl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                if(pnl == pnlActive) {
                    pnl.setGradientDisabled(false);
                } else {
                    pnl.setGradientDisabled(true);
                    pnl.setBackground(Lay.clr("9099AE"));
                }
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                if(pnl == pnlActive) {
                    pnl.setGradientDisabled(false);
                } else {
                    pnl.setGradientDisabled(true);
                    pnl.setBackground(Lay.clr("FFFFAA"));
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                pnlActive.setGradientDisabled(true);
                pnl.setBackground(Lay.clr("9099AE"));

                pnl.setGradientDisabled(false);
                pnlActive = pnl;
                fireSwitchNotifier(view);
            }
        });
    }

    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier switchNotifier = new ChangeNotifier(this);

    public void addSwitchListener(ChangeListener listener) {
        switchNotifier.addListener(listener);
    }

    protected void fireSwitchNotifier(View view) {
        switchNotifier.setSource(view);
        switchNotifier.fireStateChanged();
    }
}
