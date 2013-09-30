/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.umf.platform.ui.home;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.BevelBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.event.SimpleCallback;
import replete.gui.controls.IconButton;
import replete.plugins.PluginManager;
import replete.util.GUIUtil;
import replete.util.Lay;
import replete.util.ReflectionUtil;

public class HomeTabPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    // Core

    private UIController uiController;

    // State

    private boolean isConnectedPostgres = false;
    private boolean isConnectedOrient = false;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public HomeTabPanel(UIController uic) {
        uiController = uic;

        uiController.getDMM().addConnectListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                checkUp();
            }
        });

        checkUp();
    }

    private void checkUp() {
        isConnectedOrient = uiController.getDMM().isConnected();
        repaint();
        removeAll();
        if(isConnectedPostgres || isConnectedOrient) {
            rebuildConnectedPanels();
        }
        updateUI();
    }


    ///////////
    // PAINT //
    ///////////

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setPaint(getPaint());  // Must be calculated every time b/c depends on dimensions.
        g2.fillRect(0, 0, getWidth(), getHeight());
    }

    public Paint getPaint() {
        Color bg = getBackground();
        int x = -30;
        int y = -80;
        Color darker0 = GUIUtil.deriveColor(bg, (int)(1.5*x), x, x);
        Color darker1 = GUIUtil.deriveColor(darker0, y, y, y);
        Color[] c = new Color[] {darker0, bg, darker1, bg};
        float[] f = new float[] {0F, 0.2F, 0.75F, 1F};
        return new LinearGradientPaint(0, 0, getWidth(), getHeight(), f, c);
    }


    ///////////
    // BUILD //
    ///////////

    private void rebuildConnectedPanels() {
        final JPanel pnlSearch;
        final JPanel pnlSearch2;
        final JPanel pnlNewPart;
        JLabel lblSearch2;
        JLabel lblNewPart;

        IconButton btnJobs;
        JPanel pnlRunBtns = Lay.BL(
            "C", btnJobs = new IconButton(ImageUtil.getImage("redsky2.gif")),
            "opaque=false"
        );
        btnJobs.setToolTipText("Open Run Manager");
        btnJobs.setFocusable(false);
        btnJobs.toImageOnly();
        btnJobs.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.openChildWindow("jobs", null);
            }
        });

        String d = "[300,90]";
        Lay.GBLtg(this,
            Lay.GL(2, 2,
                Lay.GBL(
                    pnlSearch2 = Lay.BL(
                        "W", Lay.lb(ImageUtil.getImage("bigmag3orient.gif"), "opaque=false"),
                        "C", lblSearch2 = Lay.lb("<html>Search the Orient Database</html>", "opaque=false,eb=13l"),
                        "eb=10,dim=" + d
                    ),
                    "opaque=false"
                ),
                Lay.GBL(
                    pnlNewPart = Lay.BL(
                        "W", Lay.lb(ImageUtil.getImage("plus4part.png"), "opaque=false"),
                        "C", lblNewPart = Lay.lb("<html>Add a New Part</html>", "opaque=false,eb=13l"),
                        "eb=10,dim=" + d
                    ),
                    "opaque=false"
                ),
                createInfoPanel("<html>Statistics <i>(work in progress)</i></html>", new StatisticsPanel(), null),
                createInfoPanel(
                    "<html>Recent Activity <i>(work in progress)</i></html>",
                    new RecentActivityPanel(), null),
                /*createInfoPanel(
                    "<html>Run Overview <i>(work in progress)</i></html>",
                    new RunOverviewPanel(uiController), pnlRunBtns),
                Lay.lb(),*/
                "pref=[700,300],opaque=false,hgap=10,vgap=10"
            )
        );

        configLabel(lblSearch2);
        configLabel(lblNewPart);

        configPanel(pnlSearch2, new SimpleCallback() {
            public void callback() {
                uiController.openSearchOrient();
            }
        });
        configPanel(pnlNewPart, new SimpleCallback() {
            public void callback() {
                // TODO: Plugin-capable
                Object handler = PluginManager.getExtensionById("gov.sandia.umf.plugins.n2a.records.PartRecordHandler");
                if(handler != null) {
                    NDoc doc = (NDoc) ReflectionUtil.invoke("createNewPart", handler, "compartment");
                    uiController.openRecord(doc);
                }
            }
        });
    }

    private Object createInfoPanel(String name, JPanel pnl, JPanel pnlBtns) {
        if(pnlBtns == null) {
            return Lay.BL(
                "N", Lay.BL(
                    "C", Lay.lb(name),
                    "bg=[255,200,200],eb=5,augb=mb(1tlr,black)"
                ),
                "C", pnl,
                "opaque=false"
            );
        }
        return Lay.BL(
            "N", Lay.BL(
                "C", Lay.lb(name),
                "E", pnlBtns,
                "bg=[255,200,200],eb=5,augb=mb(1tlr,black)"
            ),
            "C", pnl,
            "opaque=false"
        );
    }

    private void configLabel(JLabel lbl) {
        lbl.setFont(lbl.getFont().deriveFont(15.0F));
    }

    private void configPanel(final JPanel pnl, final SimpleCallback clickCallback) {
        pnl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Lay.augb(pnl, BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.gray, Color.white));

        pnl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                pnl.setBackground(new JPanel().getBackground());  // TODO: don't construct new jpanel every time (done so that keeps consistent with L&F changes).
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                pnl.setBackground(GUIUtil.deriveColor(new JPanel().getBackground(), -15, -15, -15));
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                clickCallback.callback();
            }
        });
    }
}
