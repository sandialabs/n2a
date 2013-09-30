/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.partadv;

import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.orientdb.partadv.path.PathSegment;
import gov.sandia.n2a.ui.orientdb.partadv.path.SegmentPanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.IconButton;
import replete.util.Lay;


public class CollapsiblePartStackPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    private JButton btnCollapse;
    private JButton btnExpand;
    private boolean expanded;
    private List<PathSegment> segments = new ArrayList<PathSegment>();
    private Object clickedKey;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public CollapsiblePartStackPanel() {

        btnCollapse = new IconButton(ImageUtil.getImage("diagarrowDR.gif"), "Collapse Stack View");
        btnCollapse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                collapse();
            }
        });

        btnExpand = new IconButton(ImageUtil.getImage("diagarrowUL.gif"), "Expand Stack View");
        btnExpand.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                expand();
            }
        });

        expand();  // Starts out expanded.
    }


    ///////////////
    // NOTIFIERS //
    ///////////////

    private ChangeNotifier expandCollapseNotifier = new ChangeNotifier(this);
    public void addExpandCollapseListener(ChangeListener listener) {
        expandCollapseNotifier.addListener(listener);
    }
    private void fireExpandCollapseNotifier() {
        expandCollapseNotifier.fireStateChanged();
    }

    protected ChangeNotifier clickNotifier = new ChangeNotifier(this);
    public void addClickListener(ChangeListener listener) {
        clickNotifier.addListener(listener);
    }
    protected void fireClickNotifier() {
        clickNotifier.fireStateChanged();
    }


    //////////
    // PUSH //
    //////////

    public void push(Object key, String text, ImageIcon icon) {
        final SegmentPanel pnlSeg = new SegmentPanel(key, text, icon);
        pnlSeg.addClickListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                clickedKey = pnlSeg.getKey();
                fireClickNotifier();
            }
        });
        PathSegment segment = new PathSegment(key, text, pnlSeg);
        segments.add(segment);
        repaint();
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    public Object getClickedKey() {
        return clickedKey;
    }
    public boolean isExpanded() {
        return expanded;
    }


    ///////////////////////
    // EXPAND / COLLAPSE //
    ///////////////////////

    public void expand() {
        removeAll();
        Lay.BLtg(this,
            "N", Lay.BL(
                "C", Lay.lb("Stack"),
                "E", btnCollapse,
                "bg=BAE1FF,eb=2,augb=mb(1b,black)"
            ),
            "C", Lay.lb("hi"),
            "mb=[2,#000066]"
        );
        updateUI();
        expanded = true;
        fireExpandCollapseNotifier();
    }

    public void collapse() {
        removeAll();
        Lay.BLtg(this,
            "C", btnExpand,
            "mb=[2,#000066]"
        );
        updateUI();
        expanded = false;
        fireExpandCollapseNotifier();
    }
}
