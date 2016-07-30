/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.AppState;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import replete.gui.controls.GradientPanel;
import replete.gui.controls.iconlist.IconList;
import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.EscapeDialog;
import replete.util.Lay;

public class PreferencesDialog extends EscapeDialog {
    public static final int OK = 0;
    public static final int CANCEL = 1;

    private int result = CANCEL;


    private static Map<String, JPanel> detailPanels = new HashMap<String, JPanel>();

    private static final String SEC_GN = "General";
    private static final String SEC_CMP = "Compartment";
    private static final String SEC_MOD = "Model";
    private static final String SEC_SEA = "Search";

    private JCheckBox chkShowUids;
    private JCheckBox chkEqnFormat;
    private JCheckBox chkShowTestRecords;
    private JPanel pnlSectionDetail;

    private static final String[] sections = new String[] {
        SEC_GN, SEC_CMP, SEC_MOD, SEC_SEA
    };

    public PreferencesDialog(JFrame owner) {
        super(owner, "N2A Preferences", true);
        setIconImage(ImageUtil.getImage("properties.gif").getImage());

        constructSectionPanels();

        JButton btnOK = new MButton("&OK", ImageUtil.getImage("complete.gif"));
        btnOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                save();
                result = OK;
                dispose();
            }
        });
        JButton btnCancel = new MButton("&Cancel", ImageUtil.getImage("cancel.gif"));
        btnCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        getRootPane().setDefaultButton(btnOK);

        pnlSectionDetail = Lay.BL();

        Map<String, Icon> icons = new HashMap<String, Icon>();
        icons.put(SEC_GN, ImageUtil.getImage("globe.gif"));
        icons.put(SEC_CMP, ImageUtil.getImage("comp.gif"));
        icons.put(SEC_MOD, ImageUtil.getImage("model.gif"));
        icons.put(SEC_SEA, ImageUtil.getImage("mag.gif"));
        final IconList lstCategories = new IconList(sections, icons);
        lstCategories.setIconInsets(new Insets(3, 2, 3, 4));
        lstCategories.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent arg0) {
                if(!arg0.getValueIsAdjusting()) {
                    pnlSectionDetail.removeAll();
                    pnlSectionDetail.add(
                        detailPanels.get(lstCategories.getSelectedValue()),
                        BorderLayout.CENTER);
                    pnlSectionDetail.updateUI();
                }
            }
        });
        lstCategories.setSelectedIndex(0);
        lstCategories.requestFocusInWindow();

        JPanel pnlHeader = new GradientPanel();
        Lay.FLtg(pnlHeader, "L", Lay.lb("Preferences"), "augb=mb(1b,black)");

        Lay.BLtg(this,
            "C", Lay.SPL("X",
                Lay.sp((JComponent) Lay.hn(lstCategories, "eb=3")),
                pnlSectionDetail
            ),
            "S", Lay.FL("R", btnOK, btnCancel),
            "size=[600,400],center"
        );

        Lay.match(btnCancel, btnOK);

        load();
    }

    public int getResult() {
        return result;
    }

    protected void save ()
    {
        MNode state = AppState.getState ();
        state.set (chkShowUids       .isSelected (), "ShowUids");
        state.set (chkEqnFormat      .isSelected (), "EqnFormat");
        state.set (chkShowTestRecords.isSelected (), "ShowTestRecords");
    }

    protected void load ()
    {
        MNode state = AppState.getState ();
        chkShowUids       .setSelected (state.getOrDefault (false, "ShowUids"));
        chkEqnFormat      .setSelected (state.getOrDefault (false, "EqnFormat"));
        chkShowTestRecords.setSelected (state.getOrDefault (false, "ShowTestRecords"));
    }

    private void constructSectionPanels() {
        createSectionPanel(SEC_GN, createGeneralPanel());
        createSectionPanel(SEC_CMP, Lay.p());
        createSectionPanel(SEC_MOD, Lay.p());
        createSectionPanel(SEC_SEA, Lay.p());
    }

    private void createSectionPanel(String secName, JPanel pnlCenter) {
        JPanel pnlHeader = new GradientPanel();
        Lay.FLtg(pnlHeader, "L", Lay.lb(secName), "augb=mb(1b,black)");
        JPanel pnlSection = Lay.BL(
            "N", pnlHeader,
            "C", pnlCenter
        );
        detailPanels.put(secName, pnlSection);
    }

    private JPanel createGeneralPanel() {
        return Lay.BxL("Y",
            chkShowUids = new JCheckBox("Show record UIDs in tables"),
            chkEqnFormat = new JCheckBox("Show equations in normalized format"),
            chkShowTestRecords = new JCheckBox("Show test records in search results"),
            "eb=5"
        );
    }

    public static void main(String[] args) {
        new PreferencesDialog(null).setVisible(true);
    }
}
