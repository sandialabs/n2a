/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.partadv.path;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.util.Lay;

public class SegmentPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    private static final int PANEL_BG = 238;
    private static final int LABEL_FG = 51;
    private static final int PANEL_BG_HL = 50;
    private static final int LABEL_FG_HL = 255;
    private final Color panelBg = new Color(PANEL_BG, PANEL_BG, PANEL_BG);
    private final Color labelFg = new Color(LABEL_FG, LABEL_FG, LABEL_FG);
    private final Color panelBgHl = new Color(PANEL_BG_HL, PANEL_BG_HL, PANEL_BG_HL);
    private final Color labelFgHl = new Color(LABEL_FG_HL, LABEL_FG_HL, LABEL_FG_HL);

    private Object key;
    private String text;
    private JLabel lbl;
    private int ticks;

    private Timer highlightTimer = new Timer(30, new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            tick();
        }
    });


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public SegmentPanel(Object key, String text, ImageIcon icon) {
        this.key = key;
        this.text = text;
        Lay.BLtg(this,
            "C", lbl = Lay.lb(text, icon),
            "eb=5,prefh=36,opaque=false"
        );
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(panelBg);
                lbl.setForeground(labelFg);
                setOpaque(false);
                highlightTimer.stop();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
//                    setBackground(Lay.clr("50"));
//                    lbl.setForeground(Lay.clr("255"));
                setOpaque(true);
                ticks = 0;
                tick();
                highlightTimer.restart();
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                fireClickNotifier();
            }
        });
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


    //////////////
    // ACCESSOR //
    //////////////

    public Object getKey() {
        return key;
    }


    //////////
    // MISC //
    //////////

    private void tick() {
        int delta = 20;
        int clr = PANEL_BG - ticks * delta;
        int lclr = LABEL_FG + ticks * delta;
        if(clr <= PANEL_BG_HL || lclr >= LABEL_FG_HL ) {
            setBackground(panelBgHl);
            lbl.setForeground(labelFgHl);
            highlightTimer.stop();
            return;
        }
        setBackground(new Color(clr, clr, clr));
        lbl.setForeground(new Color(lclr, lclr, lclr));
        ticks++;
    }
}
