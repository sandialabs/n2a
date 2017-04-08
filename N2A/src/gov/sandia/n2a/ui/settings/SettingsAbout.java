/*
Copyright 2013,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.GradientPanel;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;

public class SettingsAbout extends JPanel implements Settings
{
    protected JLabel lblTitle;
    protected JLabel lblCopy;
    protected JLabel lblLicense;
    protected JLabel lblContrib;
    protected JLabel lblVersion;
    protected JLabel lblSupport;

    public SettingsAbout ()
    {
        setName ("About");  // Necessary to fulfill Settings interface.

        Font f1 = new Font ("Helvetica", Font.PLAIN, 16);
        Font f2 = new Font ("Helvetica", Font.PLAIN, 12);

        MNode pc = AppData.properties;

        lblTitle = new JLabel ("<html>" + pc.get ("name") + "</html>");
        lblTitle.setFont (f1);
        lblTitle.setBorder (BorderFactory.createMatteBorder (0, 0, 1, 0, new Color (200, 200, 200)));

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

        lblCopy = new JLabel ("<html>" + pc.get ("copyright") + "</html>");
        lblCopy.setFont(f2);

        lblLicense = new JLabel ("<html>" + pc.get ("license") + "</html>");
        lblLicense.setFont(f2);

        lblContrib = new JLabel ("<html><B>" + pc.get ("abbreviation") + "</B> was developed by " + pc.get ("developers") + ".</html>");
        lblContrib.setFont(f2);

        lblVersion = new JLabel ("Version: " + pc.get ("version"));
        lblVersion.setFont(f2);

        lblSupport = new JLabel ("<html>For technical support send e-mail to: <font color='blue'><u>" + pc.get ("support") + "</u></font></html>");
        lblSupport.setFont(f2);

        JPanel pnlText = Lay.BxL ("Y", "opaque=false");
        pnlText.add (Box.createVerticalStrut (8));
        pnlText.add (lblTitle);
        pnlText.add (Box.createVerticalStrut (8));
        pnlText.add (lblVersion);
        pnlText.add (Box.createVerticalStrut (8));
        pnlText.add (lblCopy);
        pnlText.add (Box.createVerticalStrut (8));
        pnlText.add (lblLicense);
        pnlText.add (Box.createVerticalStrut (8));
        pnlText.add (lblContrib);
        pnlText.add (Box.createVerticalStrut (8));
        pnlText.add (lblSupport);
        pnlText.add (Box.createVerticalGlue ());

        Color background = new JPanel ().getBackground ();
        Color darker = background.darker ();
        GradientPanel gp = new GradientPanel (background, darker);

        Lay.BLtg (gp, "C", pnlText, "eb=10");

        Lay.BLtg (this,
            "N", Lay.lb (ImageUtil.getImage ("n2a-splash.png")),
            "C", gp,
            "resizable=false,size=[520,380],center"
        );
    }

    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("n2a-16.png");
    }

    @Override
    public Component getPanel ()
    {
        return this;
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return panel;
    }
}