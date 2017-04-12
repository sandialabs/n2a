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
import java.awt.Desktop;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.swing.ImageIcon;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

public class SettingsAbout extends JPanel implements Settings
{
    public SettingsAbout ()
    {
        setName ("About");  // Necessary to fulfill Settings interface.

        MNode pc = AppData.properties;

        JEditorPane html = new JEditorPane
        (
            "text/html",

            "<html><body>" +
            "<p><span style='font-size:150%'>" + pc.get ("name") + "</span> version " + pc.get ("version") + "</p>" +
            "<hr/>" +
            "<p>&copy;2013,2015-2017 Sandia Corporation. Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation, the U.S. Government retains certain rights in this software.</p>" +
            "<p>This software is released under the BSD license.  Please refer to the license information provided in this distribution for the complete text.</p>" +
            "<p>Coders: Fred Rothganger, Derek Trumbo, Christy Warrender, Felix Wang</p>" +
            "<p>Concept development: Brad Aimone, Corinne Teeter, Steve Verzi, Asmeret Bier, Brandon Rohrer, Ann Speed</p>" +
            "<p>Technical support: <a href=\"mailto:frothga@sandia.gov\">frothga@sandia.gov</a></p>" +
            "</body></html>"
        );
        html.setEditable (false);
        html.setOpaque (false);
        html.addHyperlinkListener (new HyperlinkListener ()
        {
            public void hyperlinkUpdate (HyperlinkEvent e)
            {
                InputEvent ie = e.getInputEvent ();
                if (ie instanceof MouseEvent  &&  ((MouseEvent) ie).getClickCount () > 0)
                {
                    if (Desktop.isDesktopSupported ())
                    {
                        Desktop desktop = Desktop.getDesktop ();
                        try
                        {
                            desktop.browse (e.getURL ().toURI ());
                        }
                        catch (IOException | URISyntaxException exception)
                        {
                        }
                    }
                }
            }
        });

        Color background = new JPanel ().getBackground ();
        Color darker = background.darker ();
        GradientPanel gp = new GradientPanel (background, darker);

        Lay.BLtg (gp, "C", html, "eb=10");

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