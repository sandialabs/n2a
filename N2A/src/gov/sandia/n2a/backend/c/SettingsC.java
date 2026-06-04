/*
Copyright 2020-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Windows;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MCheckBox;
import gov.sandia.n2a.ui.MTextField;
import gov.sandia.n2a.ui.TextPaneANSI;
import gov.sandia.n2a.ui.settings.SettingsBackend;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class SettingsC extends SettingsBackend
{
    public static SettingsC instance;

    protected MTextField   fieldCpp      = new MTextField (40);
    protected MTextField   fieldFFmpeg   = new MTextField (40);
    protected MTextField   fieldHDF      = new MTextField (40);
    protected MTextField   fieldJNI      = new MTextField (40);
    protected MTextField   fieldGL       = new MTextField (40);
    protected MCheckBox    fieldShowCC   = new MCheckBox ("Show source files (.cc)");
    protected JButton      buttonRebuild = new JButton ("Rebuild Runtime");
    protected JLabel       labelMessages = new JLabel ("JNI support for video I/O:");
    protected TextPaneANSI textMessages;

    protected HashSet<String> forbiddenSuffixes = new HashSet<String> (Arrays.asList ("bin", "exe", "lib", "dll", "a", "so", "o", "obj", "pdb", "mod", "exp"));

    public SettingsC ()
    {
        instance = this;

        key          = "c";
        iconFileName = "c-16.png";

        fieldCpp.addChangeListener (new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                Host h = (Host) list.getSelectedValue ();
                h.objects.remove ("cxx");
                h.objects.remove ("ffmpegLibDir");  // Changing compiler may affect our ability to see FFmpeg libraries.
                h.objects.remove ("ffmpegIncDir");
                h.objects.remove ("ffmpegBinDir");
                h.objects.remove ("glLibs");        // ditto for OpenGL
                // Once a DLL is loaded, the user must restart JVM to get an updated version.
                // Therefore, no point in removing "ffmpegJNI" or "JNI".
                h.config.set ("", "backend", "c", "compilerChanged");
                clearMessage (h);
            }
        });

        fieldFFmpeg.addChangeListener (new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                Host h = (Host) list.getSelectedValue ();
                h.objects.remove ("ffmpegLibDir");
                h.objects.remove ("ffmpegIncDir");
                h.objects.remove ("ffmpegBinDir");
                h.config.set ("", "backend", "c", "compilerChanged");  // Not exactly true, but sufficient to force rebuild.
                clearMessage (h);
            }
        });

        fieldHDF.addChangeListener (new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                Host h = (Host) list.getSelectedValue ();
                h.objects.remove ("hdfLibDir");
                h.objects.remove ("hdfIncDir");
                h.objects.remove ("hdfBinDir");
                h.config.set ("", "backend", "c", "compilerChanged");
                clearMessage (h);
            }
        });

        fieldJNI.addChangeListener (new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                Host h = (Host) list.getSelectedValue ();
                h.objects.remove ("jniIncMdDir");
                h.objects.remove ("jniIncDir");
                h.config.set ("", "backend", "c", "compilerChanged");
                clearMessage (h);
            }
        });

        fieldGL.addChangeListener (new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                Host h = (Host) list.getSelectedValue ();
                h.objects.remove ("glLibs");
                h.config.set ("", "backend", "c", "compilerChanged");
                clearMessage (h);
            }
        });

        Host localhost = Host.get ();
        MNode parent = localhost.config.child ("backend", "c");
        fieldShowCC.bind (parent, "showCC", false);  // "showCC" applies to all hosts, so bind to "localhost".
        fieldShowCC.addChangeListener (new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                if (fieldShowCC.isSelected ()) forbiddenSuffixes.remove ("cc");
                else                           forbiddenSuffixes.add    ("cc");
            }
        });
        fieldShowCC.notifyChange ();

        buttonRebuild.setToolTipText ("<html>Removes all intermediate object files.<br>The C runtime will be fully rebuilt next time it is checked.<br>On Windows it is also necessary to restart the app.</html>");
        buttonRebuild.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                Host h = (Host) list.getSelectedValue ();
                h.objects.remove ("cxx");
                h.objects.remove ("ffmpegLibDir");
                h.objects.remove ("ffmpegIncDir");
                h.objects.remove ("ffmpegBinDir");
                h.objects.remove ("hdfLibDir");
                h.objects.remove ("hdfIncDir");
                h.objects.remove ("hdfBinDir");
                h.objects.remove ("jniIncMdDir");
                h.objects.remove ("jniIncDir");
                h.objects.remove ("glLibs");
                h.config.set ("", "backend", "c", "compilerChanged");
                clearMessage (h);
            }
        });

        textMessages = new TextPaneANSI ()
        {
            public void updateUI ()
            {
                super.updateUI ();

                Font f = UIManager.getFont ("TextArea.font");
                if (f == null) return;
                setFont (new Font (Font.MONOSPACED, Font.PLAIN, f.getSize ()));
            }
        };
        textMessages.setEditable (false);
    }

    protected void clearMessage (Host h)
    {
        if (h.name.equals ("localhost")) textMessages.setText (h instanceof Windows ? "Restart needed" : "");
    }

    @Override
    public String getName ()
    {
        return "Backend C";
    }

    @Override
    public void bind (MNode parent)
    {
        Host h = (Host) list.getSelectedValue ();

        CompilerFactory cxx          = (CompilerFactory) h.objects.get ("cxx");
        Path            ffmpegLibDir = (Path)            h.objects.get ("ffmpegLibDir");
        Path            hdfLibDir    = (Path)            h.objects.get ("hdfLibDir");
        Path            jniIncMdDir  = (Path)            h.objects.get ("jniIncMdDir");
        Path            glLib        = (Path)            h.objects.get ("glLib");

        fieldCpp   .bind (parent, "cxx",    cxx          == null ? "g++" : cxx.path () .toString ());
        fieldFFmpeg.bind (parent, "ffmpeg", ffmpegLibDir == null ? ""    : ffmpegLibDir.toString ());
        fieldHDF   .bind (parent, "hdf",    hdfLibDir    == null ? ""    : hdfLibDir   .toString ());
        fieldJNI   .bind (parent, "jni_md", jniIncMdDir  == null ? ""    : jniIncMdDir .toString ());
        fieldGL    .bind (parent, "gl",     glLib        == null ? ""    : glLib       .toString ());

        boolean localhost = h.name.equals ("localhost");
        labelMessages.setVisible (localhost);
        textMessages .setVisible (localhost);
    }

    public void rebind (Host h)
    {
        // Only bind if the updated host (h) is currently selected.
        if (list.getSelectedValue () == h) bind (h.config.childOrCreate ("backend", key));
    }

    @Override
    public JPanel getEditor ()
    {
        return Lay.BxL (
            Lay.FL (new JLabel ("Compiler path"), fieldCpp),
            Lay.FL (new JLabel ("Directory that contains FFmpeg libraries"), fieldFFmpeg),
            Lay.FL (new JLabel ("Directory that contains HDF libraries"), fieldHDF),
            Lay.FL (new JLabel ("Directory that contains jni_md.h"), fieldJNI),
            Lay.FL (new JLabel ("OpenGL link library"), fieldGL),
            Lay.FL (fieldShowCC),
            Lay.FL (buttonRebuild),
            Lay.FL (labelMessages),
            Lay.FL (textMessages)
        );
    }

    public void setMessage (String message)
    {
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                textMessages.setText (message);
            }
        });
    }

    public void addMessage (String message)
    {
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                textMessages.append (message);
            }
        });
    }
}
