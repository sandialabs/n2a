/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.part;

import gov.sandia.n2a.ui.orientdb.eq.tree.NodeSummaryRoot;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import replete.util.GUIUtil;

public class SummaryGraphPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    // Const

    private static final Color CT  = new Color(158, 221, 255);
    private static final Color CTB = new Color(84, 121, 255);
    private static final Color CC  = new Color(185, 242, 181);
    private static final Color CCB = new Color(109, 142, 107);
    private static final Color CI  = new Color(255, 150, 155);
    private static final Color CIB = new Color(168, 60, 50);
    private static final Color CP  = new Color(219, 191, 255);
    private static final Color CPB = new Color(184, 127, 255);

    // Core

    private NDoc part;

    // State

    private boolean error;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public SummaryGraphPanel(NDoc p) {
        part = p;

        setBackground(Color.gray);
        setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, Color.black));
    }


    ////////////////
    // PAINT COMP //
    ////////////////

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        if(error) {
            g2.setColor(Color.white);
            g2.setFont(new Font("Courier New", Font.BOLD, 14));
            g2.drawString(NodeSummaryRoot.ERROR, 10, GUIUtil.stringHeight(g2) + 5);
            return;
        } else if(part == null) {
            g2.setColor(Color.white);
            g2.setFont(new Font("Courier New", Font.BOLD, 14));
            g2.drawString(NodeSummaryRoot.CALC, 10, GUIUtil.stringHeight(g2) + 5);
            return;
        }

        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;

        int partRadius = w / 7;

        int y1_4 = (int) (h * 0.25);
        int y3_4 = (int) (h * 0.75);
        int x1_4 = (int) (w * 0.25);
        int x3_4 = (int) (w * 0.75);

        List<NDoc> assocs = (List<NDoc>) part.getAndSetValid("associations", new ArrayList<NDoc>(), List.class);
        List<NDoc> includes = new ArrayList<NDoc>();
        List<NDoc> connects = new ArrayList<NDoc>();
        for (NDoc doc : assocs) {
            if (doc.get("type").toString().equalsIgnoreCase("connect")) {
                connects.add(doc);
            }
            else {
                includes.add(doc);
            }
        }
        if(connects != null) {
            if(connects.size() > 0) {
                g2.setStroke(new BasicStroke(4));
                g2.setColor(Color.black);
                g2.drawLine(cx, cy, x1_4, y3_4);
            }
            if(connects.size() > 1) {
                g2.setStroke(new BasicStroke(4));
                g2.setColor(Color.black);
                g2.drawLine(cx, cy, x3_4, y3_4);
            }
        }
        if(part.get("parent") != null) {
            g2.setStroke(new BasicStroke(4));
            g2.setColor(Color.black);
            g2.drawLine(cx, cy, x1_4, y1_4);
        }

        String curName = part.get("name");
        String nm;
        if(!part.isPersisted()) {
            if(curName.equals("")) {
                nm = "<NEW>";
            } else {
                nm = curName;
            }
        } else {
            if(curName.equals("")) {
                nm = part.get("name");
            } else {
                nm = curName;
            }
        }

        circ(g2, cx, cy, partRadius, CT, CTB, nm);

        if(connects != null) {
            if(connects.size() > 0) {
                NDoc pa = connects.get(0);
                circ(g2, x1_4, y3_4, w / 10, CC, CCB, (String)((NDoc) pa.get("dest")).get("name"));
            }
            if(connects.size() > 1) {
                NDoc pa = connects.get(1);
                circ(g2, x3_4, y3_4, w / 10, CC, CCB, (String)((NDoc) pa.get("dest")).get("name"));
            }
        }
        if(includes.size() != 0) {
            int innerRadius = (int)(partRadius * 0.75);
            double angle = Math.PI / 3;
            for(NDoc pa : includes) {
                int nx = (int) (cx + innerRadius * Math.cos(angle));
                int ny = (int) (cy - innerRadius * Math.sin(angle));
                circ(g2, nx, ny, partRadius / 4, CI, CIB, (String) ((NDoc) pa.get("dest")).get("name"));
                angle -= Math.PI / 3;
            }
        }
        if(part.get("parent") != null) {
            circ(g2, x1_4, y1_4, w / 10, CP, CPB, (String) ((NDoc) part.get("parent")).get("name"));
        }
    }

    private void circ(Graphics2D g2, int ox, int oy, int rad, Color cin, Color cout, String label) {
        g2.setColor(cin);
        g2.fillOval(ox - rad, oy - rad, rad * 2, rad * 2);
        g2.setColor(cout);
        g2.setStroke(new BasicStroke(4));
        g2.drawOval(ox - rad, oy - rad, rad * 2, rad * 2);
        g2.setColor(Color.black);
        g2.setFont(new Font("Arial", Font.PLAIN, 14));
        int lw = GUIUtil.stringWidth(g2, label);
        int lh = GUIUtil.stringHeight(g2);
        g2.drawString(label, ox - lw / 2, oy + lh / 2);
    }


    //////////////
    // MUTATORS //
    //////////////

    public void setStateClear() {
        error = false;
        repaint();
    }
    public void setStateError() {
        error = true;
        repaint();
    }

    public void rebuild() {
        repaint();
    }
}
