/*
Copyright 2013-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

import java.awt.EventQueue;
import java.awt.Font;
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
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.host.Connection.MessageListener;
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
        public JTextArea      textMessages;

        public void arrange ()
        {
            Lay.BLtg (this, "N",
                Lay.BxL ("V",
                    Lay.FL (new JLabel ("Address"), fieldAddress),
                    Lay.FL (new JLabel ("Username"), fieldUsername),
                    Lay.FL (new JLabel ("Password"), fieldPassword),
                    Lay.FL (labelWarning),
                    Lay.FL (new JLabel ("Home Directory"), fieldHome),
                    Lay.FL (panelRelays),
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

            JLabel labelRelayAdd = new JLabel ("Add SSH Relay");
            Lay.BxLtg (panelRelays, "V",
                Lay.FL (buttonRelayAdd, labelRelayAdd)
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
                Lay.FL (buttonRemove, labelRelay),
                Lay.FL (
                    Box.createHorizontalStrut (30),
                    Lay.BxL ("V",
                        Lay.FL (new JLabel ("Address"), fieldRelayAddress),
                        Lay.FL (new JLabel ("Username"), fieldRelayUsername),
                        Lay.FL (new JLabel ("Password"), fieldRelayPassword)
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
        if (! EventQueue.isDispatchThread ())  // Not on EDT, so run directly.
        {
            connection.connect ();
            return;
        }
        // On EDT, so spawn a new thread for connection.connect().
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
