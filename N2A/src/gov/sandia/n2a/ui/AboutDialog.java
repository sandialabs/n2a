/*
Copyright 2013,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;

public class AboutDialog extends JDialog
{
    static JLabel lblTitle;
    static JLabel lblCopy;
    static JLabel lblLicense;
    static JLabel lblContrib;
    static JLabel lblVersion;
    static JLabel lblSupport;

    // Needed because HTML labels are slow to construct!
    // So this is called in main
    public static void initializeLabels ()
    {
        Font f1 = new Font("Helvetica", Font.PLAIN, 16);
        Font f2 = new Font("Helvetica", Font.PLAIN, 12);

        MNode pc = AppData.properties;

        lblTitle = new JLabel("<html>" + pc.get ("name") + "</html>");
        lblTitle.setFont(f1);
        lblTitle.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)));

        Dimension d = null;
        View view = (View) lblTitle.getClientProperty (BasicHTML.propertyKey);
        if (view != null)
        {
            view.setSize (300, 0);
            float w = view.getPreferredSpan (View.X_AXIS);
            float h = view.getPreferredSpan (View.Y_AXIS);
            d = new Dimension ((int) Math.ceil (w), (int) Math.ceil (h));
        }
        lblTitle.setPreferredSize (d);

        lblCopy = new JLabel("<html>" + pc.get ("copyright") + "</html>");
        lblCopy.setFont(f2);

        lblLicense = new JLabel("<html>" + pc.get ("license") + "</html>");
        lblLicense.setFont(f2);

        lblContrib = new JLabel("<html><B>" + pc.get ("abbreviation") + "</B> was developed by " + pc.get ("developers") + ".</html>");
        lblContrib.setFont(f2);

        lblVersion = new JLabel("Version: " + pc.get ("version"));
        lblVersion.setFont(f2);

        lblSupport = new JLabel("<html>For technical support send e-mail to: <font color='blue'><u>" + pc.get ("support") + "</u></font></html>");
        lblSupport.setFont(f2);
    }

    public AboutDialog (JFrame parent)
    {
        super(parent, "About " + AppData.properties.get ("name"), true);

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

        Color background = new JPanel ().getBackground ();
        Color darker = background.darker ();
        GradientPanel gp = new GradientPanel (background, darker);

        Lay.BLtg(gp, "C", pnlText, "eb=10");

        Lay.BLtg(this,
            "N", Lay.lb (ImageUtil.getImage ("n2a-splash.png")),
            "C", gp,
            "resizable=false,size=[520,380],center"
        );
    }
}