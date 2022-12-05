/*
Copyright 2020-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MTextField;
import gov.sandia.n2a.ui.settings.SettingsBackend;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class SettingsC extends SettingsBackend
{
    protected MTextField fieldCpp    = new MTextField (40);
    protected MTextField fieldFFmpeg = new MTextField (40);
    protected MTextField fieldJNI    = new MTextField (40);

    public SettingsC ()
    {
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
                h.config.set ("", "backend", "c", "compilerChanged");
                // Once a DLL is loaded, the user needs to restart JVM to get an updated version.
                // Therefore, no point in removing "ffmpegJNI" or "JNI".
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
            }
        });
    }

    @Override
    public String getName ()
    {
        return "Backend C";
    }

    @Override
    public void bind (MNode parent)
    {
        fieldCpp   .bind (parent, "cxx",    "g++");
        fieldFFmpeg.bind (parent, "ffmpeg", "");
        fieldJNI   .bind (parent, "jni_md", "");
    }

    @Override
    public JPanel getEditor ()
    {
        return Lay.BxL (
            Lay.FL (new JLabel ("Compiler path"), fieldCpp),
            Lay.FL (new JLabel ("Directory that contains FFmpeg libraries"), fieldFFmpeg),
            Lay.FL (new JLabel ("Directory that contains jni_md.h"), fieldJNI)
        );
    }
}
