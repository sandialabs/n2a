/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.AppState;
import gov.sandia.umf.platform.plugins.extpoints.ProductCustomization;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import replete.gui.controls.GradientPanel;
import replete.gui.windows.EscapeDialog;
import replete.util.GUIUtil;
import replete.util.Lay;

public class AboutDialog extends EscapeDialog {

    private static JLabel lblTitle;
    private static JLabel lblCopy;
    private static JLabel lblLicense;
    private static JLabel lblContrib;
    private static JLabel lblVersion;
    private static JLabel lblSupport;

    // Needed because HTML labels are slow to construct!
    // So this is called in Main.main(String[] args).
    public static void initializeLabels() {
        Font f1 = new Font("Helvetica", Font.PLAIN, 16);
        Font f2 = new Font("Helvetica", Font.PLAIN, 12);

        ProductCustomization pc = AppState.getInstance ().prodCustomization;
        String shortName;
        if(pc != null) {
            shortName = pc.getProductShortName();
        } else {
            shortName = "UMF";
        }

        lblTitle = new JLabel("<html>" + markUp(pc.getProductLongName()) + "</html>");
        lblTitle.setFont(f1);
        lblTitle.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)));
        Dimension d = GUIUtil.getHTMLJLabelPreferredSize(lblTitle, 300, true);
        lblTitle.setPreferredSize(d);

        String copy = pc.getCopyright();
        if(copy != null) {
            lblCopy = new JLabel("<html>" + copy + "</html>");
            lblCopy.setFont(f2);
        }

        String lic = pc.getLicense();
        if(lic != null) {
            lblLicense = new JLabel("<html>" + lic + "</html>");
            lblLicense.setFont(f2);
        }

        String contrib = pc.getProductDevelopedBy();
        if(contrib != null) {
            lblContrib = new JLabel("<html><B>" + shortName + "</B> was developed by " + contrib + ".</html>");
            lblContrib.setFont(f2);
        }

        lblVersion = new JLabel("Version: " + pc.getProductVersion());
        lblVersion.setFont(f2);

        String email = pc.getSupportEmail();
        if(email != null) {
            lblSupport = new JLabel("<html>For technical support send e-mail to: <font color='blue'><u>" + email + "</u></font></html>");
            lblSupport.setFont(f2);
        }
    }

    public static String markUp(String s) {
        // <b>N</b>eurons <b>T</b>o <b>A</b>lgorithms
        return s.replaceAll("([A-Z])", "<b>$1</b>");
    }

    public AboutDialog(JFrame parent) {
        super(parent, "About " + AppState.getInstance().prodCustomization.getProductLongName(), true);

        // Technically possible for this to execute before the thread
        // invoking initializeLabels finishes.  However, that is such
        // a small possibility, not going to waste energy on the
        // synchronization.

        JPanel pnlText = Lay.BxL("Y", "opaque=false");
        pnlText.add(Box.createVerticalStrut(8));
        pnlText.add(lblTitle);
        pnlText.add(Box.createVerticalStrut(8));
        pnlText.add(lblVersion);
        if(lblCopy != null) {
            pnlText.add(Box.createVerticalStrut(8));
            pnlText.add(lblCopy);
        }
        if(lblLicense != null) {
            pnlText.add(Box.createVerticalStrut(8));
            pnlText.add(lblLicense);
        }
        if(lblContrib != null) {
            pnlText.add(Box.createVerticalStrut(8));
            pnlText.add(lblContrib);
        }
        if(lblSupport != null) {
            pnlText.add(Box.createVerticalStrut(8));
            pnlText.add(lblSupport);
        }
        pnlText.add(Box.createVerticalGlue());

        int x = -40;
        GradientPanel gp = new GradientPanel(
            new JPanel().getBackground(),
            GUIUtil.deriveColor(new JPanel().getBackground(), x, x, x));

        Lay.BLtg(gp, "C", pnlText, "eb=10");

        Lay.BLtg(this,
            "N", Lay.lb(AppState.getInstance().prodCustomization.getAboutImage()),
            "C", gp,
            "resizable=false,size=[520,380],center"
        );
    }
}