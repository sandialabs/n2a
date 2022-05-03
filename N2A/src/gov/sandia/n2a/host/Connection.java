/*
Copyright 2013-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import gov.sandia.n2a.host.Host.AnyProcess;
import gov.sandia.n2a.host.Host.AnyProcessBuilder;
import gov.sandia.n2a.ui.MainFrame;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.keyboard.UserInteraction;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.ClientSession.ClientSessionEvent;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.util.buffer.Buffer;

public class Connection implements Closeable, UserInteraction
{
    protected Host          host;
    protected ClientSession session;
    protected FileSystem    sshfs;
    protected String        hostname;
    protected String        username;
    protected String        password;
    protected int           timeout;
    protected String        home;          // Path of user's home directory on remote system. Includes leading slash.
    protected boolean       allowDialogs;  // Initially false. Dialogs must be specifically enabled by the user.
    protected boolean       failedAuth;    // Failed to authenticate while in interactive mode. Indicates that user password is necessary, so don't keep trying when not interactive.
    protected Set<String>   messages = new HashSet<String> ();  // Remember messages, so we only display them once per session.

    public static SshClient client = SshClient.setUpDefaultClient ();  // shared between remote execution system and git wrapper
    static
    {
        client.start ();
    }

    public interface MessageListener
    {
        public void messageReceived ();
    }

    public Connection (Host host)
    {
        this.host = host;
        hostname = host.config.getOrDefault (host.name, "address");  // Value actually passed to ssh. Could be an alias at the ssh level.
        timeout  = host.config.getOrDefault (20,        "timeout") * 1000;
        password = host.config.get ("password");  // may be empty

        // Retrieve username from ssh/config, defaulting to system property.
        // TODO: integrate ssh hop UI (in RemoteUnix) with ssh/config
        username = System.getProperty ("user.name");
        try
        {
            HostConfigEntry entry = client.getHostConfigEntryResolver ().resolveEffectiveHost (hostname, 22, null, username, null, null);  // hostname and username are the only important entries for this query
            if (entry != null) username = entry.getUsername ();
        }
        catch (IOException e) {}

        home = host.config.getOrDefault ("/home/" + username, "home");
    }

    /**
        @throws IOException This function promises to throw an exception
        if the connection is not fully completed.
    **/
    public synchronized void connect () throws IOException
    {
        if (failedAuth  &&  ! allowDialogs) throw new IOException ("Avoiding silent login hammer");
        if (isConnected ()) return;
        close ();
        try
        {
            int port = host.config.getOrDefault (22, "port");
            session = client.connect (username, hostname, port).verify (timeout).getSession ();
            session.setUserInteraction (this);
            if (! password.isEmpty ()) session.addPasswordIdentity (password);  // This assumes that you never use an empty password. Thus, empty indicates to ask for password.
            if (allowDialogs) failedAuth = true;  // This will get immediately reversed if we succeed ...
            session.auth ().verify (120000);  // Two minutes.
            failedAuth = false;
        }
        catch (IOException e)
        {
            if (session != null) session.close (true);
            session = null;
            throw e;
        }
    }

    public synchronized void close ()
    {
        if (session == null) return;
        try
        {
            if (sshfs instanceof SshFileSystem)
            {
                SshFileSystem s = (SshFileSystem) sshfs;
                if (s.sftp != null) s.sftp.close ();
            }
            session.close ();  // TODO: try graceful close here?
        }
        catch (IOException e) {}
        session = null;
    }

    /**
        @return true only if the connection is up and fully usable.
    **/
    public synchronized boolean isConnected ()
    {
        if (session == null) return false;
        if (! session.isOpen ()) return false;

        // We could simply check isAuthenticated(), but the following also verifies no error condition is set.
        Set<ClientSessionEvent> state = session.getSessionState ();
        if (state.size () != 1) return false;
        if (state.contains (ClientSessionEvent.AUTHED)) return true;
        return false;
    }

    /**
        Constructs a compendium of unique messages from all the sessions.
    **/
    public String getMessages ()
    {
        StringBuilder result = new StringBuilder ();
        boolean firstMessage = true;
        for (String m : messages)
        {
            if (! firstMessage) result.append ("\n------------------------\n");
            firstMessage = false;
            result.append (m);
        }
        return result.toString ();
    }

    /**
        @return A file system bound to the remote host. Default directory for relative paths
        is the user's home. Absolute paths are with respect to the usual root directory.
        @throws JSchException
    **/
    public synchronized FileSystem getFileSystem () throws Exception
    {
        connect ();
        if (sshfs != null) return sshfs;
        URI uri = new URI ("ssh://" + hostname + home);
        try
        {
            Map<String,Object> env = new HashMap<String,Object> ();
            env.put ("connection", this);
            sshfs = FileSystems.newFileSystem (uri, env);
        }
        catch (FileSystemAlreadyExistsException e)
        {
            // It is possible for two host to share the exact same filesystem.
            // The host could be an alias with an alternate configuration.
            sshfs = FileSystems.getFileSystem (uri);
        }
        return sshfs;
    }

    public RemoteProcessBuilder build (String... command)
    {
        return new RemoteProcessBuilder (command);
    }

    public RemoteProcessBuilder build (List<String> command)
    {
        return new RemoteProcessBuilder (command.toArray (new String[command.size ()]));
    }

    public class RemoteProcessBuilder implements AnyProcessBuilder
    {
        protected String             command;
        protected Path               fileIn;
        protected Path               fileOut;
        protected Path               fileErr;
        protected Map<String,String> environment;

        public RemoteProcessBuilder (String... command)
        {
            this.command = host.combine (command);
        }

        public RemoteProcessBuilder redirectInput (Path file)
        {
            fileIn = file;
            return this;
        }

        public RemoteProcessBuilder redirectOutput (Path file)
        {
            fileOut = file;
            return this;
        }

        public RemoteProcessBuilder redirectError (Path file)
        {
            fileErr = file;
            return this;
        }

        public Map<String,String> environment ()
        {
            if (environment == null) environment = new HashMap<String,String> ();
            return environment;
        }

        public RemoteProcess start () throws IOException
        {
            RemoteProcess process = new RemoteProcess (command);

            // Streams must be configured before connect.
            // A redirected stream is of the opposite type from what we would read directly.
            // IE: stdout (from the perspective of the remote process) must feed into something
            // on our side. We either read it directly, in which case it is an input stream,
            // or we redirect it to file, in which case it is an output stream.
            // Can this get any more confusing?
            if (fileIn  != null) process.channel.setIn  (Files.newInputStream  (fileIn));
            if (fileOut != null) process.channel.setOut (Files.newOutputStream (fileOut));
            if (fileErr != null) process.channel.setErr (Files.newOutputStream (fileErr));

            if (environment != null)
            {
                for (Entry<String,String> e : environment.entrySet ())
                {
                    process.channel.setEnv (e.getKey (), e.getValue ());
                }
            }

            process.channel.open ().verify (timeout);  // This actually starts the remote process.
            if (fileIn  == null) process.stdin  = process.channel.getInvertedIn  ();
            if (fileOut == null) process.stdout = process.channel.getInvertedOut ();
            if (fileErr == null) process.stderr = process.channel.getInvertedErr ();
            return process;
        }
    }

    /**
        Drop-in equivalent to Process that works for a remote process executed via ssh.
        The one difference is that this should be created within a try-with-resources so that
        it will be automatically closed and release the ssh channel.
    **/
    public class RemoteProcess extends Process implements AnyProcess
    {
        protected ChannelExec channel;

        // The following streams are named from the perspective of the remote process.
        // IE: the stdin of the remote process will receive input from us.
        protected OutputStream stdin;  // From our perspective, transmitting data, this needs to be an output stream.
        protected InputStream  stdout;
        protected InputStream  stderr;

        public RemoteProcess (String command) throws IOException
        {
            connect ();
            channel = session.createExecChannel (command);
        }

        public void close ()
        {
            if (channel == null) return;
            try
            {
                channel.close (false).await (timeout);
            }
            catch (IOException e) {}  // TODO: failure to close the exec channel may prevent future exec channels from opening
        }

        public OutputStream getOutputStream ()
        {
            if (stdin == null) stdin = new NullOutputStream ();
            return stdin;
        }

        public InputStream getInputStream ()
        {
            if (stdout == null) stdout = new NullInputStream ();
            return stdout;
        }

        public InputStream getErrorStream ()
        {
            if (stderr == null) stderr = new NullInputStream ();
            return stderr;
        }

        public int waitFor () throws InterruptedException
        {
            channel.waitFor
            (
                EnumSet.of (ClientChannelEvent.CLOSED,
                            ClientChannelEvent.EXIT_STATUS,
                            ClientChannelEvent.EXIT_SIGNAL),
                0  // wait forever
            );
            Integer result = channel.getExitStatus ();
            if (result == null) return -1;
            return result;
        }

        public int exitValue () throws IllegalThreadStateException
        {
            Integer result = channel.getExitStatus ();
            if (result == null) throw new IllegalThreadStateException ();
            return result;
        }

        public void destroy ()
        {
            sendSignal ("TERM");
        }

        public RemoteProcess destroyForcibly ()
        {
            sendSignal ("KILL");
            return this;
        }

        protected void sendSignal (String signal)
        {
            // TODO: this code has never been tested, because signal is usually delivered another way.
            Buffer buf = session.createBuffer (SshConstants.SSH_MSG_CHANNEL_REQUEST, 20);
            buf.putInt (channel.getRecipient ());
            buf.putString ("signal");
            buf.putByte ((byte) 0);  // Don't reply
            buf.putString (signal);
            try {channel.writePacket (buf);}
            catch (IOException e) {}
        }

        public boolean isAlive ()
        {
            Set<ClientChannelEvent> state = channel.getChannelState ();
            if (state.contains (ClientChannelEvent.CLOSED     )) return false;
            if (state.contains (ClientChannelEvent.EXIT_SIGNAL)) return false;
            if (state.contains (ClientChannelEvent.EXIT_STATUS)) return false;
            if (state.contains (ClientChannelEvent.OPENED     )) return true;
            return false;
        }
    }

    // The following null streams were copied from ProcessBuilder.
    // Unfortunately, they are private to that class, so can't be used here.

    public static class NullInputStream extends InputStream
    {
        public int read ()
        {
            return -1;
        }

        public int available ()
        {
            return 0;
        }
    }

    public static class NullOutputStream extends OutputStream
    {
        public void write (int b) throws IOException
        {
            throw new IOException ("Stream closed");
        }
    }

    public boolean isInteractionAllowed (ClientSession session)
    {
        return allowDialogs;
    }

    public void welcome (ClientSession session, String banner, String lang)
    {
        if (messages.contains (banner)) return;
        messages.add (banner);
    }

    public String[] interactive (ClientSession session, String name, String instruction, String lang, String[] prompt, boolean[] echo)
    {
        JPanel panel = new JPanel ();
        panel.setLayout (new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints
        (
            0, 0, 1, 1, 1, 1,
            GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
            new Insets (0, 0, 0, 0),
            0, 0
        );

        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.gridx = 0;
        panel.add (new JLabel (instruction), gbc);
        gbc.gridy++;

        gbc.gridwidth = GridBagConstraints.RELATIVE;

        JTextField[] texts = new JTextField[prompt.length];
        for (int i = 0; i < prompt.length; i++)
        {
            gbc.fill = GridBagConstraints.NONE;
            gbc.gridx = 0;
            gbc.weightx = 1;
            panel.add (new JLabel (prompt[i]), gbc);

            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 1;
            if (echo[i]) texts[i] = new JTextField(20);
            else         texts[i] = new JPasswordField(20);
            panel.add (texts[i], gbc);
            gbc.gridy++;
        }

        if (prompt.length > 0)
        {
            texts[0].addAncestorListener (new AncestorListener ()
            {
                public void ancestorAdded (AncestorEvent event)
                {
                    texts[0].requestFocusInWindow ();
                }

                public void ancestorRemoved (AncestorEvent event)
                {
                }

                public void ancestorMoved (AncestorEvent event)
                {
                }
            });
        }

        int result = JOptionPane.showConfirmDialog
        (
            MainFrame.instance,
            panel,
            hostname + ": " + name,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        if (result == JOptionPane.OK_OPTION)
        {
            String[] response = new String[prompt.length];
            for (int i = 0; i < prompt.length; i++) response[i] = texts[i].getText ();
            return response;
        }
        allowDialogs = false;
        return null; // cancel
    }

    public String getUpdatedPassword (ClientSession session, String prompt, String lang)
    {
        JTextField field = makePasswordField (password);
        Object[] contents = {field};
        int result = JOptionPane.showConfirmDialog (MainFrame.instance, contents, prompt, JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION)
        {
            session.removePasswordIdentity (password);
            password = field.getText ();
            session.addPasswordIdentity (password);
            return password;
        }
        allowDialogs = false;  // If the user cancels, then they want to stop trying.
        return null;
    }

    public String resolveAuthPasswordAttempt (ClientSession session) throws Exception
    {
        JTextField field = makePasswordField (password);
        Object[] contents = {field};
        int result = JOptionPane.showConfirmDialog (MainFrame.instance, contents, hostname + ": Password", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION)
        {
            session.removePasswordIdentity (password);
            password = field.getText ();
            session.addPasswordIdentity (password);  // For next time
            return password;
        }
        allowDialogs = false;  // If the user cancels, then they want to stop trying.
        return null;
    }

    public JPasswordField makePasswordField (String value)
    {
        JPasswordField result = new JPasswordField (value, 20);
        result.addAncestorListener (new AncestorListener ()
        {
            public void ancestorAdded (AncestorEvent event)
            {
                result.requestFocusInWindow ();
            }

            public void ancestorRemoved (AncestorEvent event)
            {
            }

            public void ancestorMoved (AncestorEvent event)
            {
            }
        });
        return result;
    }
}
