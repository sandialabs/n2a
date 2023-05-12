/*
Copyright 2013-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import javax.swing.ImageIcon;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

@SuppressWarnings("serial")
public class SettingsAbout extends JPanel implements Settings
{
    Listener hyperlinkListener = new Listener ();

    public SettingsAbout ()
    {
        setName ("About");  // Necessary to fulfill Settings interface.

        MNode pc = AppData.properties;
        String message = 
            "<html><body>" +
            "<p><span style='font-size:150%'>" + pc.get ("name") + "</span> version " + pc.get ("version") + "</p>" +
            "<hr/>" +
            "<p>Programmers: Fred Rothganger, Derek Trumbo, Christy Warrender</p>" +
            "<p>Concept development: Brad Aimone, Corinne Teeter, Steve Verzi, Asmeret Bier, Brandon Rohrer, Ann Speed, Felix Wang</p>" +
            "<p>Technical support: <a href=\"mailto:frothga@sandia.gov\">frothga@sandia.gov</a></p>" +
            "<p>Licenses:</p>" +
            "</body></html>";

        JEditorPane html = new JEditorPane ("text/html", message)
        {
            public void updateUI ()
            {
                super.updateUI ();

                Font f = UIManager.getFont ("EditorPane.font");
                if (f == null) return;
                setFont (f);
                putClientProperty (JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
            }
        };
        html.setEditable (false);
        html.setOpaque (false);
        html.addHyperlinkListener (hyperlinkListener);

        JTabbedPane licenses = new JTabbedPane ();
        addLicense (licenses, "N2A", loadResource ("LICENSE"));

        // Units of Measurement
        // Combine several license files into one big list for display.
        StringBuilder content = new StringBuilder ();
        content.append (loadResource ("unit-api"));
        content.append ("\n");
        content.append ("\n");
        content.append ("===============================================================================\n");
        content.append ("\n");
        content.append (loadResource ("uom-lib"));
        content.append ("\n");
        content.append ("\n");
        content.append ("===============================================================================\n");
        content.append ("\n");
        content.append (loadResource ("indriya"));
        content.append ("\n");
        content.append ("\n");
        content.append ("===============================================================================\n");
        content.append ("\n");
        content.append ("uom-systems\n");
        content.append (loadResource ("uom-systems"));
        content.append ("\n");
        content.append ("===============================================================================\n");
        content.append ("\n");
        content.append ("si-units\n");
        content.append (loadResource ("si-units"));
        addLicense (licenses, "Units of Measurement", content.toString ());

        addLicense     (licenses, "JFreeChart",    loadResource ("jfreechart"));
        addLicense     (licenses, "JGit",          loadResource ("jgit"));
        addLicense     (licenses, "Apache SSHD",   loadResource ("apache-sshd"));
        addLicense     (licenses, "JavaEWAH",      loadResource ("JavaEWAH"));
        addLicense     (licenses, "OpenGL",        loadResource ("opengl"));
        addLicense     (licenses, "JOGL",          loadResource ("JOGL"));
        addLicense     (licenses, "miniz",         loadResource ("miniz"));
        addLicense     (licenses, "pugixml",       loadResource ("pugixml"));
        addLicense     (licenses, "SLF4J",         loadResource ("slf4j"));
        addLicense     (licenses, "EdDSA",         loadResource ("eddsa"));
        addLicenseHTML (licenses, "Eclipse",       loadResource ("eclipse"));

        Lay.BLtg (this,
            "N", html,
            "C", licenses
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

    public void addLicenseHTML (JTabbedPane licenses, String name, String content)
    {
        JEditorPane editorPane = new JEditorPane ("text/html", content)
        {
            public void updateUI ()
            {
                super.updateUI ();

                Font f = UIManager.getFont ("EditorPane.font");
                if (f == null) return;
                setFont (f);
                putClientProperty (JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
            }
        };
        editorPane.setEditable (false);
        editorPane.addHyperlinkListener (hyperlinkListener);
        JScrollPane scrollPane = new JScrollPane (editorPane);
        licenses.addTab (name, null, scrollPane, null);
    }

    public void addLicense (JTabbedPane licenses, String name, String content)
    {
        JTextArea textArea = new JTextArea (content)
        {
            public void updateUI ()
            {
                super.updateUI ();

                Font f = UIManager.getFont ("TextArea.font");
                if (f == null) return;
                setFont (new Font (Font.MONOSPACED, Font.PLAIN, f.getSize ()));
            }
        };
        JScrollPane scrollPane = new JScrollPane (textArea);
        licenses.addTab (name, null, scrollPane, null);
    }

    public String loadResource (String fileName)
    {
        try (InputStream stream = SettingsAbout.class.getResource ("licenses/" + fileName).openStream ())
        {
            return Host.streamToString (stream);
        }
        catch (IOException e)
        {
            return "";
        }
    }

    public static class Listener implements HyperlinkListener
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
    }
}