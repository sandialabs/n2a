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
import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.Dialogs;
import replete.gui.windows.EscapeFrame;
import replete.util.Lay;
import replete.util.RandomUtil;

public class PathPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    private List<PathSegment> segments = new ArrayList<PathSegment>();
    private Object clickedKey;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public PathPanel() {
        Lay.WLtg(this, "L", "hgap=0,vgap=0");
    }


    //////////////
    // NOTIFIER //
    //////////////

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

    public void clear() {
        segments.clear();
        rebuildSegments();
    }

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
        rebuildSegments();
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    public Object getClickedKey() {
        return clickedKey;
    }

    public void popAfter(Object key) {
        for(int i = segments.size() - 1; i >= 0; i--) {
            PathSegment segment = segments.get(i);
            if(segment.key.equals(key)) {
                break;
            }
            segments.remove(i);
        }
        rebuildSegments();
    }

    private void rebuildSegments() {
        removeAll();
        for(int seg = 0; seg < segments.size(); seg++) {
            add(segments.get(seg).panel);
            if(seg != segments.size() - 1) {
                add(Lay.lb(ImageUtil.getImage("pathsep.gif"), "bxg=200,eb=5rl"));
            }
        }
        updateUI();
    }


    //////////
    // TEST //
    //////////

    public static void main(String[] args) {
        final PathPanel pnlPaths = new PathPanel();
        pnlPaths.addClickListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                System.out.println(pnlPaths.getClickedKey());
            }
        });
        final EscapeFrame frame = new EscapeFrame("HI");
        JButton btn = new MButton("PUSH", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String s = Dialogs.showInput(frame, "Key;Title", "Enter", "AAA;BBB");
                if(s==null) {
                    return;
                }
                String[] s2 = s.split(";");
                pnlPaths.push(s2[0], s2[1],
                    RandomUtil.flip(
                        ImageUtil.getImage("comp.gif"),
                        ImageUtil.getImage("conn.gif")
                    )
                );
            }
        });
        Lay.BLtg(frame,
            "N", pnlPaths,
            "C", btn, "size=[600,600],center=2"
        );
        frame.setVisible(true);
    }
}
