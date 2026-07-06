/*
Copyright 2013-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
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
import gov.sandia.n2a.host.SshFileSystem.WrapperSftp;
import gov.sandia.n2a.ui.MainFrame;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.keyboard.UserInteraction;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.ClientSession.ClientSessionEvent;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.channel.exception.SshChannelOpenException;
import org.apache.sshd.common.session.SessionHeartbeatController.HeartbeatType;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.core.CoreModuleProperties;

public class Connection implements Closeable, UserInteraction
{
    protected Host          host;
    protected ClientSession session;
    protected Semaphore     channels;
    protected int           channelRetries = 3; // If open fails when only one channel is available, keep trying this many times.
    protected FileSystem    sshfs;
    protected String        hostname;
    protected String        username;
    protected String        password;
    protected int           timeout;
    protected String        home;          // Path of user's home directory on remote system. Includes leading slash.
    protected boolean       allowDialogs;  // Initially false. Dialogs must be specifically enabled by the user.
    protected boolean       failedAuth;    // Last authentication attempt failed. Indicates that user password is necessary, so don't keep trying when not interactive.
    protected Set<String>   messages = new HashSet<String> ();  // Remember messages, so we only display them once per session.

    public static SshClient client = SshClient.setUpDefaultClient ();  // shared between remote execution system and git wrapper
    static
    {
        CoreModuleProperties.REQUEST_EXEC_REPLY.set (client, true);  // Ensure that remote command has started before pumping data to its stdin. (We rarely ever pump data to stdin.)
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
        username = getDefaultUsername (hostname);
        username = host.config.getOrDefault (username, "username");  // Overrides the above, if present.

        home = host.config.getOrDefault ("/home/" + username, "home");
    }

    /**
        Returns the username from ssh config, or if not in config, from system property.
        The return value is never blank.
    **/
    public static String getDefaultUsername (String hostname)
    {
        String result = null;
        try
        {
            HostConfigEntry entry = client.getHostConfigEntryResolver ().resolveEffectiveHost (hostname, 0, null, null, null, null);  // hostname is the only important value for this query
            if (entry != null) result = entry.getUsername ();
        }
        catch (IOException e) {}
        if (result == null  ||  result.isBlank ()) result = System.getProperty ("user.name");
        return result;
    }

    /**
        @throws IOException This function promises to throw an exception
        if the connection is not fully completed.
    **/
    public synchronized void connect () throws IOException
    {
        if (isConnected ()) return;
        if (failedAuth  &&  ! allowDialogs) throw new IOException ("Avoiding silent login hammer");
        close ();
        try
        {
            HostConfigEntry entry = client.getHostConfigEntryResolver ().resolveEffectiveHost (hostname, 0, null, null, null, null);
            entry.setUsername (username);  // We fully determined username above, so just use it, regardless of whether there is a value in entry.
            int port = entry.getPort ();
            if (port <= 0) port = SshConstants.DEFAULT_PORT;
            entry.setPort (host.config.getOrDefault (port, "port"));

            session = client.connect (entry).verify (timeout).getSession ();
            session.setUserInteraction (this);
            if (session.getSessionHeartbeatType () == HeartbeatType.NONE)  // Does client get keepalive information from config file?
            {
                int keepalive = host.config.getOrDefault (60, "keepalive");
                if (keepalive > 0) session.setSessionHeartbeat (HeartbeatType.IGNORE, Duration.ofSeconds (keepalive));
            }
            if (! password.isEmpty ()) session.addPasswordIdentity (password);  // This assumes that you never use an empty password. Thus, empty indicates to ask for password.
            failedAuth = true;  // This will get immediately reversed if we succeed ...
            session.auth ().verify (120000);  // Two minutes.
            failedAuth = false;

            int maxChannels = host.config.getOrDefault (10, "maxChannels");
            channels = new Semaphore (maxChannels);
        }
        catch (Exception e)
        {
            if (session != null) session.close (true);
            session = null;
            if (e instanceof IOException) throw e;
            throw new IOException (e);
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
        channels = null;
        session = null;
    }

    /**
        @return true only if the connection is up and fully usable.
    **/
    public boolean isConnected ()
    {
        // This function is not synchronized, so calls from EDT will not stall.
        // If this is called from connect(), then state of session will still be
        // protected by the synchronization of that function.
        // For calls from EDT, we need to handle mid-flight changes in session.
        try
        {
            if (session == null) return false;  // This line isn't strictly necessary, since we trap NPE.
            if (! session.isOpen ()) return false;
        
            // We could simply check isAuthenticated(), but the following also verifies no error condition is set.
            Set<ClientSessionEvent> state = session.getSessionState ();
            if (state.size () != 1) return false;
            if (state.contains (ClientSessionEvent.AUTHED)) return true;
        }
        catch (Exception e) {}  // in particular, a NullPointerException
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
            // It is possible for two hosts to share the exact same filesystem.
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
            if (channels.availablePermits () < 1  &&  sshfs instanceof SshFileSystem)
            {
                WrapperSftp sftp = ((SshFileSystem) sshfs).sftp;
                if (sftp != null) sftp.close ();  // Because all methods are synchronized, this won't interrupt an ongoing operation.
            }
            try
            {
                channels.acquire ();
            }
            catch (InterruptedException e)
            {
                // Failed to acquire
                throw new IOException (e);
            }

            int tries = 0;
            while (true)
            {
                RemoteProcess process = null;
                try
                {
                    process = new RemoteProcess (command);

                    if (environment != null)
                    {
                        for (Entry<String,String> e : environment.entrySet ())
                        {
                            process.channel.setEnv (e.getKey (), e.getValue ());
                        }
                    }

                    process.channel.open ().verify (timeout);  // This actually starts the remote process.

                    // Redirect streams.
                    if (fileIn  != null) process.stdin  = new Pump (Files.newInputStream (fileIn),     process.channel.getInvertedIn (), true);
                    if (fileOut != null) process.stdout = new Pump (process.channel.getInvertedOut (), Files.newOutputStream (fileOut),  false);
                    if (fileErr != null) process.stderr = new Pump (process.channel.getInvertedErr (), Files.newOutputStream (fileErr),  false);

                    return process;
                }
                catch (IOException e)
                {
                    // We only retry if it was an SSH open exception AND we have used the last available permit.
                    // Otherwise, we clean up and re-throw the exception.
                    if (   ! (e.getCause () instanceof SshChannelOpenException)
                        || channels.availablePermits () > 0
                        || tries >= channelRetries)
                    {
                        // Presumably, if channel failed to open, then it does not count against the channel limit.
                        // Also, if we throw an exception here, then try-with-resources in client code will not view
                        // "process" as opened, so it won't call process.close().
                        if (process == null) channels.release ();
                        else                 process.close ();  // Releases permit and closes SSH exec streams.
                        throw e;
                    }
                }
                tries++;
            }
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
        protected Integer     result;

        // The following are named from the perspective of the remote process.
        // IE: the stdin of the remote process will receive input from us.
        protected Pump stdin;
        protected Pump stdout;
        protected Pump stderr;

        public RemoteProcess (String command) throws IOException
        {
            connect ();
            channel = session.createExecChannel (command);
        }

        public void close ()
        {
            if (channel == null) return;
            try {channel.close (false).await (timeout);}
            catch (IOException e) {}  // Even if there is an exception, we still release our internal channel count ...
            channels.release ();
            try {if (stdin != null) stdin.in.close ();}
            catch (IOException e) {}
            channel = null;
        }

        public OutputStream getOutputStream ()
        {
            if (stdin == null) return channel.getInvertedIn ();
            return OutputStream.nullOutputStream ();
        }

        public InputStream getInputStream ()
        {
            if (stdout == null) return channel.getInvertedOut ();
            return InputStream.nullInputStream ();
        }

        public InputStream getErrorStream ()
        {
            if (stderr == null) return channel.getInvertedErr ();
            return InputStream.nullInputStream ();
        }

        public int waitFor () throws InterruptedException
        {
            channel.waitFor (EnumSet.of (ClientChannelEvent.CLOSED), 0);  // Wait forever, until server closes channel.
            result = channel.getExitStatus ();
            if (result == null) result = -1;
            return result;
        }

        public int exitValue () throws IllegalThreadStateException
        {
            if (result == null) result = channel.getExitStatus ();
            if (result == null) throw new IllegalThreadStateException ("Remote process has not yet returned exit code.");
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

    public static class Pump extends Thread
    {
        protected InputStream  in;
        protected OutputStream out;
        public    boolean      flush;

        public Pump (InputStream in, OutputStream out, boolean flush) throws IOException
        {
            super ("SSH Exec Pump");

            this.in    = in;
            this.out   = out;
            this.flush = flush;

            setDaemon (true);
            start ();
        }

        public void run ()
        {
            // The main signal for this thread to stop is that "in" is closed by another thread.
            try
            {
                final int bufferSize = 0x4000;  // 16K
                byte[] buffer = new byte[bufferSize];
                int count;
                while ((count = in.read (buffer, 0, bufferSize)) >= 0)
                {
                    out.write (buffer, 0, count);
                    if (flush) out.flush ();
                }
            }
            catch (Exception e) {}

            try
            {
                out.close ();
                in .close ();  // Very likely already closed by SSH.
            }
            catch (Exception e) {}
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
