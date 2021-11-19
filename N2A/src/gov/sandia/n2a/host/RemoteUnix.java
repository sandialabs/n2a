/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

import java.awt.EventQueue;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MPasswordField;
import gov.sandia.n2a.ui.MTextField;
import gov.sandia.n2a.ui.images.ImageUtil;
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
        public MTextField     fieldAddress   = new MTextField (config, "address", name);
        public MTextField     fieldUsername  = new MTextField (config, "username", System.getProperty ("user.name"));
        public MPasswordField fieldPassword  = new MPasswordField (config, "password");
        public JLabel         labelWarning   = new JLabel ("<html>WARNING: Passoword is stored in plain text.<br>For more security, you can leave the field blank.<br>You will be prompted for a password once per session.<br>That password will be held only in volatile memory.</html>");
        public MTextField     fieldHome      = new MTextField (config, "home", "/home/" + config.getOrDefault (System.getProperty ("user.name"), "username"));
        public JPanel         panelRelays    = new JPanel ();
        public List<JLabel>   labelsRelay    = new ArrayList<JLabel> ();
        public JButton        buttonConnect  = new JButton ("Reset Connection");
        public JButton        buttonRestart  = new JButton ("Restart Monitor Thread");
        public JButton        buttonZombie   = new JButton ("Scan for Zombie Jobs");

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
                    Lay.BL ("W", panelRelays),
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

            JButton buttonRelayAdd = new JButton (ImageUtil.getImage ("add.gif"));
            buttonRelayAdd.setMargin (new Insets (2, 2, 2, 2));
            buttonRelayAdd.setFocusable (false);
            buttonRelayAdd.addActionListener (new ActionListener ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    MNode relays = config.childOrEmpty ("relay");
                    addRelay (relays.size () + 1);
                }
            });

            JLabel  labelRelayAdd  = new JLabel ("Add SSH Relay");
            Lay.BxLtg (panelRelays, "V",
                Lay.BL ("W",
                    Lay.FL ("H", buttonRelayAdd, labelRelayAdd)
                )
            );

            MNode relays = config.childOrEmpty ("relay");
            for (int i = 1; i <= relays.size (); i++) addRelay (i);

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

        /**
            @param n The 1-based index of the relay. Relays are both displayed and stored
            with a 1-based index.
        **/
        public void addRelay (int n)
        {
            JButton buttonRemove = new JButton (ImageUtil.getImage ("subtract.gif"));
            buttonRemove.setMargin (new Insets (2, 2, 2, 2));
            buttonRemove.setFocusable (false);
            buttonRemove.setToolTipText ("Remove this SSH relay");

            JLabel labelRelay = new JLabel ("Relay " + n);
            labelsRelay.add (labelRelay);

            MNode relay = config.childOrCreate ("relay", n);
            MTextField     fieldRelayAddress  = new MTextField (relay, "address");
            MTextField     fieldRelayUsername = new MTextField (relay, "username", fieldUsername.getText ());
            MPasswordField fieldRelayPassword = new MPasswordField (relay, "password", fieldPassword.getText ());

            ChangeListener changeUsername = new ChangeListener ()
            {
                public void stateChanged (ChangeEvent e)
                {
                    fieldRelayUsername.setDefault (fieldUsername.getText ());
                }
            };
            fieldUsername.addChangeListener (changeUsername);

            ChangeListener changePassword = new ChangeListener ()
            {
                public void stateChanged (ChangeEvent e)
                {
                    fieldRelayPassword.setDefault (fieldPassword.getText ());
                }
            };
            fieldPassword.addChangeListener (changePassword);

            buttonRemove.addActionListener (new ActionListener ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    MNode relays = config.childOrEmpty ("relay");
                    int count = relays.size ();
                    for (int i = n; i <= count; i++) relays.move (String.valueOf (i + 1), String.valueOf (i));  // Moving a non-existent node over an existing one erases it, so last node will disappear.

                    for (int i = n; i < count; i++) labelsRelay.get (i).setText ("Relay " + i);
                    labelsRelay.remove (n - 1);

                    fieldUsername.removeChangeListener (changeUsername);
                    fieldPassword.removeChangeListener (changePassword);

                    panelRelays.remove (n - 1);
                    panelRelays.revalidate ();
                }
            });
            
            JPanel panelRelay = Lay.BxL ("V",
                Lay.BL ("W", Lay.FL ("H", buttonRemove, labelRelay)),
                Lay.BL ("W",
                    Lay.FL ("H",
                        Box.createHorizontalStrut (30),
                        Lay.BxL ("V",
                            Lay.BL ("W", Lay.FL ("H", new JLabel ("Address"), fieldRelayAddress)),
                            Lay.BL ("W", Lay.FL ("H", new JLabel ("Username"), fieldRelayUsername)),
                            Lay.BL ("W", Lay.FL ("H", new JLabel ("Password"), fieldRelayPassword))
                        )
                    )
                )
            );

            panelRelays.add (panelRelay, null, n - 1);
            panelRelays.revalidate ();
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
        connection.allowDialogs = true;

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
