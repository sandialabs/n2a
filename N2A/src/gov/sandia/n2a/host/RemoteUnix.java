/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

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
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MPasswordField;
import gov.sandia.n2a.ui.MTextField;
import gov.sandia.n2a.ui.jobs.NodeJob;
import gov.sandia.n2a.ui.jobs.PanelRun;
import gov.sandia.n2a.ui.settings.SettingsHost.NameChangeListener;

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
        return new FactoryRemote ()
        {
            public String className ()
            {
                return "RemoteUnix";
            }

            public Host createInstance ()
            {
                return new RemoteUnix ();
            }
        };
    }

    @Override
    public JPanel getEditor ()
    {
        if (panel == null) panel = new EditorPanel ();
        return panel;
    }

    @SuppressWarnings("serial")
    public class EditorPanel extends JPanel implements NameChangeListener
    {
        public MTextField     fieldAddress  = new MTextField (config, "address", name);
        public MTextField     fieldUsername = new MTextField (config, "username", System.getProperty ("user.name"));
        public MPasswordField fieldPassword = new MPasswordField (config, "password");
        public JLabel         labelWarning  = new JLabel ("<html>WARNING: Passoword is stored in plain text.<br>If this is a security concern, then you can leave the field blank.<br>You will be prompted for a password once per session.<br>That password will only be held in volatile memory.</html>");
        public MTextField     fieldHome     = new MTextField (config, "home", "/home/" + config.getOrDefault (System.getProperty ("user.name"), "username"));
        public JButton        buttonConnect = new JButton ("Reset Connection");
        public JButton        buttonRestart = new JButton ("Restart Monitor Thread");
        public JButton        buttonZombie  = new JButton ("Scan for Zombie Jobs");

        public EditorPanel ()
        {
            prepare ();
            Lay.BLtg (this, "N",
                Lay.BxL ("V",
                    Lay.BL ("W", Lay.FL ("H", new JLabel ("Address"), fieldAddress)),
                    Lay.BL ("W", Lay.FL ("H", new JLabel ("Username"), fieldUsername)),
                    Lay.BL ("W", Lay.FL ("H", new JLabel ("Password"), fieldPassword)),
                    Lay.BL ("W", Lay.FL ("H", labelWarning)),
                    Lay.BL ("W", Lay.FL ("H", new JLabel ("Home Directory"), fieldHome)),
                    Lay.BL ("W", Lay.FL ("H", buttonConnect, buttonRestart, buttonZombie))
                )
            );
        }

        public void prepare ()
        {
            fieldUsername.addChangeListener (new ChangeListener ()
            {
                public void stateChanged (ChangeEvent e)
                {
                    fieldHome.setDefault ("/home/" + fieldUsername.getText ());
                }
            });

            buttonConnect.setToolTipText ("Updated settings will be used for next connection attempt.");
            buttonConnect.addActionListener (new ActionListener ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    Thread shutdownThread = new Thread ("Reset " + name)
                    {
                        public void run ()
                        {
                            close ();
                        }
                    };
                    shutdownThread.setDaemon (true);
                    shutdownThread.start ();
                }
            });

            buttonRestart.setToolTipText ("In case the thread crashed.");
            buttonRestart.addActionListener (new ActionListener ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    restartMonitorThread ();
                }
            });

            buttonZombie.setToolTipText ("Updates local list of jobs to include any lingering job directories on remote host.");
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
                                                    MDoc job = (MDoc) AppData.runs.childOrCreate (key);
                                                    job.set (name, "host");
                                                    job.save ();

                                                    // Add to UI and monitor thread.
                                                    NodeJob node = PanelRun.instance.addNewRun (job, false);
                                                    monitor (node);
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
        }

        @Override
        public void nameChanged (String newName)
        {
            fieldAddress.setDefault (name);
        }
    }

    public synchronized void connect () throws Exception
    {
        if (connection == null) connection = new Connection (config);
        connection.connect ();
    }

    public synchronized void close ()
    {
        if (connection != null) connection.close ();
        connection = null;
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
        return connection.getFileSystem ().getPath ("/").getRoot ().resolve (connection.home).resolve ("n2a");
    }

    @Override
    public String quote (Path path)
    {
        String result = path.toAbsolutePath ().toString ();
        if (result.contains (" ")) return "\'" + result + "\'";
        return result;
    }
}
