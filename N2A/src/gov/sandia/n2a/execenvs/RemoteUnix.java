/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MPasswordField;
import gov.sandia.n2a.ui.MTextField;
import gov.sandia.n2a.ui.jobs.PanelRun;

/**
    Suitable for a unix-like system that runs jobs on its own processors,
    as opposed to queuing them on a cluster or specialized hardware.
**/
public class RemoteUnix extends Unix implements Remote
{
    protected Connection connection;
    protected JPanel     panel;

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String className ()
            {
                return "RemoteUnix";
            }

            public boolean isRemote ()
            {
                return true;
            }

            public Host createInstance ()
            {
                return new RemoteUnix ();
            }
        };
    }

    public JPanel getEditor ()
    {
        if (panel != null) return panel;

        JButton buttonRestart = new JButton ("Restart Monitor Thread");
        buttonRestart.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                restartMonitorThread ();
            }
        });

        JButton buttonZombie = new JButton ("Scan for Leaked Job Resources");
        buttonZombie.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                Thread thread = new Thread ("Scan " + name)
                {
                    public void run ()
                    {
                        try
                        {
                            Path localJobsDir = getLocalResourceDir ().resolve ("jobs");
                            Path remoteJobsDir = getResourceDir ().resolve ("jobs");
                            try (DirectoryStream<Path> stream = Files.newDirectoryStream (remoteJobsDir))
                            {
                                for (Path remoteDir : stream)
                                {
                                    if (! Files.isDirectory (remoteDir)) continue;
                                    String key = remoteDir.getFileName ().toString ();
                                    Path localDir = localJobsDir.resolve (key);
                                    if (Files.exists (localDir)) continue;

                                    EventQueue.invokeLater (new Runnable ()
                                    {
                                        public void run ()
                                        {
                                            try
                                            {
                                                // Re-create the local placeholder
                                                MNode job = AppData.runs.childOrCreate (key);
                                                job.set (name, "$metadata", "host");
                                                ((MDoc) job).save ();

                                                // Add to UI and monitor thread.
                                                PanelRun.instance.addNewRun (job);
                                            }
                                            catch (Exception e) {e.printStackTrace ();}
                                        }
                                    });
                                }
                            }
                        }
                        catch (Exception e) {e.printStackTrace ();}
                    }
                };
                thread.setDaemon (true);
                thread.start ();
            }
        });

        panel = Lay.BL ("N",
            Lay.BxL ("V",
                Lay.BL ("W", Lay.FL ("H", new JLabel ("Address"), new MTextField (config, "address", name))),
                Lay.BL ("W", Lay.FL ("H", new JLabel ("Username"), new MTextField (config, "username", System.getProperty ("user.name")))),
                Lay.BL ("W", Lay.FL ("H", new JLabel ("Password"), new MPasswordField (config, "password"))),
                Lay.BL ("W", Lay.FL ("H", new JLabel ("<html>WARNING: Passoword is stored in plain text.<br>If this is a security concern, then you can leave the field blank.<br>You will be prompted for a password once per session.<br>That password will only be held in volatile memory.</html>"))),
                Lay.BL ("W", Lay.FL ("H", buttonRestart, buttonZombie))
            )
        );
        return panel;
    }

    public synchronized void connect () throws Exception
    {
        if (connection == null) connection = new Connection (config);
        connection.connect ();
    }

    public synchronized void close ()
    {
        if (connection != null) connection.close ();
    }

    @Override
    public synchronized void enable ()
    {
        if (connection == null) connection = new Connection (config);
        connection.passwords.allowDialogs = true;

        // This function is called on the EDT, but connection.connect() could take a long time,
        // so spawn a thread for it.
        Thread thread = new Thread ()
        {
            public void run ()
            {
                try {connection.connect ();}
                catch (Exception e) {}
            }
        };
        thread.setDaemon (true);
        thread.start ();
    }

    @Override
    public boolean isEnabled ()
    {
        return  connection != null  &&  connection.passwords.allowDialogs;
    }

    @Override
    public boolean isConnected ()
    {
        return  connection != null  &&  connection.isConnected ();
    }

    @Override
    public Set<String> messages ()
    {
        if (connection == null) return new HashSet<String> ();
        return connection.passwords.messages;
    }

    @Override
    public AnyProcessBuilder build (String... command) throws Exception
    {
        connect ();
        return connection.build (command);
    }

    @Override
    public Path getResourceDir () throws Exception
    {
        connect ();
        return connection.getFileSystem ().getPath ("n2a");  // assumes that filesystem default directory is where n2a dir should reside
    }

    @Override
    public String quote (Path path)
    {
        String result = path.toAbsolutePath ().toString ();
        if (result.contains (" ")) return "\'" + result + "\'";
        return result;
    }
}
