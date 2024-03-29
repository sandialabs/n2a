/*
Copyright 2013-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.host.Connection.MessageListener;
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
    protected Connection  connection;
    protected EditorPanel panel;

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
        if (panel == null)
        {
            panel = new EditorPanel ();
            panel.arrange ();
        }
        return panel;
    }

    @SuppressWarnings("serial")
    public class EditorPanel extends JPanel implements NameChangeListener, MessageListener
    {
        public MTextField     fieldAddress     = new MTextField (config, "address", name);
        public MTextField     fieldUsername    = new MTextField (config, "username", Connection.getDefaultUsername (name));
        public MPasswordField fieldPassword    = new MPasswordField (config, "password");
        public JLabel         labelWarning     = new JLabel ("<html>WARNING: Passoword is stored in plain text.<br>For more security, you can leave the field blank.<br>You will be prompted for a password once per session.<br>That password will be held only in volatile memory.</html>");
        public MTextField     fieldHome        = new MTextField (config, "home", "/home/" + config.getOrDefault (Connection.getDefaultUsername (name), "username"));
        public MTextField     fieldTimeout     = new MTextField (config, "timeout", "20");
        public MTextField     fieldMaxChannels = new MTextField (config, "maxChannels", "10");
        public JButton        buttonConnect    = new JButton ("Reset Connection");
        public JButton        buttonRestart    = new JButton ("Restart Monitor Thread");
        public JButton        buttonZombie     = new JButton ("Scan for Zombie Jobs");
        public JTextArea      textMessages;

        public void arrange ()
        {
            Lay.BLtg (this, "N",
                Lay.BxL ("V",
                    Lay.FL (new JLabel ("Address"), fieldAddress),
                    Lay.FL (new JLabel ("Username"), fieldUsername),
                    Lay.FL (new JLabel ("Password"), fieldPassword),
                    Lay.FL (Box.createHorizontalStrut (30), labelWarning),
                    Lay.FL (new JLabel ("Home Directory"), fieldHome),
                    Lay.FL (new JLabel ("Timeout (seconds)"), fieldTimeout),
                    Lay.FL (new JLabel ("Max Channels"), fieldMaxChannels),
                    Lay.FL (buttonConnect, buttonRestart, buttonZombie),
                    Lay.FL (new JLabel ("Messages:")),
                    Lay.FL (textMessages)
                )
            );
        }

        public EditorPanel ()
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

            String messages = "";
            if (connection != null) messages = connection.getMessages ();
            textMessages = new JTextArea (messages)
            {
                public void updateUI ()
                {
                    super.updateUI ();

                    Font f = UIManager.getFont ("TextArea.font");
                    if (f == null) return;
                    setFont (new Font (Font.MONOSPACED, Font.PLAIN, f.getSize ()));
                }
            };
        }

        @Override
        public void nameChanged (String newName)
        {
            fieldAddress.setDefault (name);
            String defaultUsername = Connection.getDefaultUsername (name);
            fieldUsername.setDefault (defaultUsername);
            fieldHome.setDefault ("/home/" + config.getOrDefault (defaultUsername, "username"));
        }

        public void messageReceived ()
        {
            textMessages.setText (connection.getMessages ());
        }
    }

    public synchronized void connect (boolean enableDialogs) throws Exception
    {
        if (connection == null) connection = new Connection (this);
        if (enableDialogs) connection.allowDialogs = true;

        // connection.connect() could take a long time, so don't run on EDT.
        // This should only be on EDT when the call comes through enable(),
        // but enable() may also be called outside of EDT.
        if (EventQueue.isDispatchThread ())
        {
            // Spawn a new thread for connection.connect().
            Thread thread = new Thread ("Connect to " + name)
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
        else  // Not on EDT, so run directly.
        {
            connection.connect ();
        }
    }

    public synchronized void close ()
    {
        if (connection != null) connection.close ();
        connection = null;
    }

    @Override
    public synchronized void enable ()
    {
        try {connect (true);}
        catch (Exception e) {}
    }

    @Override
    public boolean isEnabled ()
    {
        return  connection != null  &&  connection.allowDialogs;
    }

    @Override
    public boolean isConnected ()
    {
        return  connection != null  &&  connection.isConnected ();
    }

    @Override
    public AnyProcessBuilder build (String... command) throws Exception
    {
        connect (false);
        return connection.build (command);
    }

    @Override
    public Path getResourceDir () throws Exception
    {
        connect (false);
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
