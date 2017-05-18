/*
Copyright 2013,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
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
            "<p>Coders: Fred Rothganger, Derek Trumbo, Christy Warrender</p>" +
            "<p>Concept development: Brad Aimone, Corinne Teeter, Steve Verzi, Asmeret Bier, Brandon Rohrer, Ann Speed, Felix Wang</p>" +
            "<p>Technical support: <a href=\"mailto:frothga@sandia.gov\">frothga@sandia.gov</a></p>" +
            "<hr/>" +
            "<p>&copy;2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).</p>" +
            "<p>Under the terms of Contract DE-NA0003525 with NTESS, the U.S. Government retains certain rights in this software.</p>" +
            "<p>Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:</p>" +
            "<ul>" +
            "<li>Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.</li>" +
            "<li>Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.</li>" +
            "<li>Neither the name of NTESS nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.</li>" +
            "</ul>" +
            "<p>THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND" +
            "ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED" +
            "WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE" +
            "DISCLAIMED. IN NO EVENT SHALL SANDIA CORPORATION BE LIABLE FOR ANY" +
            "DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES" +
            "(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;" +
            "LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND" +
            "ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT" +
            "(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS" +
            "SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.</p>" +
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