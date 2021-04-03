/*
Copyright 2020-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Pattern;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;

import gov.sandia.n2a.host.Host.AnyProcess;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

public class SshFileSystem extends FileSystem
{
    protected URI          uri;            // For convenience in answering Path.getURI() call.
    protected Connection   connection;
    protected boolean      ownConnection;  // Indicates that connection belongs directly to this object, rather than a host.
    protected SshPath      rootDir = new SshPath (this);
    protected SshPath      defaultDir;
    protected SshFileStore fileStore;
    protected WrapperSftp  sftp;

    public FileSystemProvider provider ()
    {
        return SshFileSystemProvider.instance;
    }

    public void close ()
    {
        SshFileSystemProvider.instance.fileSystems.remove (uri.getHost ());
        if (connection.sshfs == this) connection.sshfs = null;
        if (ownConnection) connection.close ();
    }

    public boolean isOpen ()
    {
        return connection.isConnected ();
    }

    public boolean isReadOnly ()
    {
        return false;
    }

    public String getSeparator ()
    {
        return "/";
    }

    public Iterable<Path> getRootDirectories ()
    {
        List<Path> result = new ArrayList<Path> ();
        result.add (rootDir);
        return result;
    }

    public Iterable<FileStore> getFileStores ()
    {
        List<FileStore> result = new ArrayList<FileStore> ();
        result.add (getFileStore ());
        return result;
    }

    public Set<String> supportedFileAttributeViews ()
    {
        Set<String> result = new HashSet<String>();
        result.add ("basic");
        result.add ("posix");
        return result;
    }

    public Path getPath (String first, String... more)
    {
        boolean absolute = first.startsWith ("/");
        List<String> path = new ArrayList<String> ();
        SshPath.addElements (first, path);
        for (String p : more) SshPath.addElements (p, path);
        return new SshPath (this, absolute, path.toArray (new String[path.size ()]));
    }

    public PathMatcher getPathMatcher (String syntaxAndPattern)
    {
        String[] pieces = syntaxAndPattern.split (":", 2);
        String syntax = pieces[0].toLowerCase ();
        String pattern = pieces[1];

        if (syntax.equals ("glob"))
        {
            // Convert a glob pattern into a Java regex pattern.
            // The following code is taken from https://stackoverflow.com/questions/1247772/is-there-an-equivalent-of-java-util-regex-for-glob-type-patterns
            // The original was written by https://stackoverflow.com/users/213525/neil-traft and placed in the Public Domain.
            // He referenced other code in the thread, as well as POSIX documentation at http://pubs.opengroup.org/onlinepubs/009695399/utilities/xcu_chap02.html#tag_02_13_01
            StringBuilder sb = new StringBuilder (pattern.length ());
            int inGroup = 0;
            int inClass = 0;
            int firstIndexInClass = -1;
            char[] arr = pattern.toCharArray ();
            for (int i = 0; i < arr.length; i++)
            {
                char ch = arr[i];
                switch (ch)
                {
                    case '\\':
                        if (++i >= arr.length)
                        {
                            sb.append('\\');
                        }
                        else
                        {
                            char next = arr[i];
                            switch (next)
                            {
                                case ',':
                                    // escape not needed
                                    break;
                                case 'Q':
                                case 'E':
                                    // extra escape needed
                                    sb.append('\\');
                                default:
                                    sb.append('\\');
                            }
                            sb.append(next);
                        }
                        break;
                    case '*':
                        if (inClass == 0) sb.append(".*");
                        else              sb.append('*');
                        break;
                    case '?':
                        if (inClass == 0) sb.append('.');
                        else              sb.append('?');
                        break;
                    case '[':
                        inClass++;
                        firstIndexInClass = i + 1;
                        sb.append('[');
                        break;
                    case ']':
                        inClass--;
                        sb.append(']');
                        break;
                    case '.':
                    case '(':
                    case ')':
                    case '+':
                    case '|':
                    case '^':
                    case '$':
                    case '@':
                    case '%':
                        if (inClass == 0  ||  firstIndexInClass == i  &&  ch == '^') sb.append('\\');
                        sb.append(ch);
                        break;
                    case '!':
                        if (firstIndexInClass == i) sb.append('^');
                        else                        sb.append('!');
                        break;
                    case '{':
                        inGroup++;
                        sb.append('(');
                        break;
                    case '}':
                        inGroup--;
                        sb.append(')');
                        break;
                    case ',':
                        if (inGroup > 0) sb.append('|');
                        else             sb.append(',');
                        break;
                    default:
                        sb.append (ch);
                }
            }
            pattern = sb.toString ();
        }
        else if (! syntax.equals ("regex"))
        {
            throw new IllegalArgumentException ("Must specify syntax as regex or glob");
        }

        final Pattern regex = Pattern.compile (pattern);
        return new PathMatcher ()
        {
            public boolean matches (Path path)
            {
                return regex.matcher (path.toString ()).matches ();
            }
        };
    }

    public UserPrincipalLookupService getUserPrincipalLookupService ()
    {
        throw new UnsupportedOperationException ();
    }

    public WatchService newWatchService () throws IOException
    {
        throw new UnsupportedOperationException ();
    }

    public synchronized SshFileStore getFileStore ()
    {
        if (fileStore == null) fileStore = new SshFileStore ();
        return fileStore;
    }

    public class SshFileStore extends FileStore
    {
        public String name ()
        {
            return connection.hostname;
        }

        public String type ()
        {
            return "ssh";
        }

        public boolean isReadOnly ()
        {
            return false;
        }

        public long getTotalSpace () throws IOException
        {
            try
            {
                connection.connect ();
            }
            catch (JSchException e)
            {
                throw new IOException (e);
            }

            try (AnyProcess proc = connection.build ("df", "-h", defaultDir.toString ()).start ();
                 BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
            {
                String line = reader.readLine ();  // Ignore header line.
                line = reader.readLine ();
                String[] pieces = line.split ("\\s+");
                return extractSize (pieces[1]);
            }
            catch (Exception e)
            {
                throw new IOException (e);
            }
        }

        // Sometimes less than unallocated space, due to such things a the 5% margin on unix file systems
        // which only root can access.
        public long getUsableSpace () throws IOException
        {
            try
            {
                connection.connect ();
            }
            catch (JSchException e)
            {
                throw new IOException (e);
            }

            try (AnyProcess proc = connection.build ("df", "-h", defaultDir.toString ()).start ();
                 BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
            {
                String line = reader.readLine ();  // Ignore header line.
                line = reader.readLine ();
                String[] pieces = line.split ("\\s+");
                return extractSize (pieces[3]);
            }
            catch (Exception e)
            {
                throw new IOException (e);
            }
        }

        public long getUnallocatedSpace () throws IOException
        {
            try
            {
                connection.connect ();
            }
            catch (JSchException e)
            {
                throw new IOException (e);
            }

            try (AnyProcess proc = connection.build ("df", "-h", defaultDir.toString ()).start ();
                 BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ())))
            {
                String line = reader.readLine ();  // Ignore header line.
                line = reader.readLine ();
                String[] pieces = line.split ("\\s+");
                long total = extractSize (pieces[1]);
                long used  = extractSize (pieces[2]);
                return total - used;
            }
            catch (Exception e)
            {
                throw new IOException (e);
            }
        }

        public boolean supportsFileAttributeView (Class<? extends FileAttributeView> type)
        {
            return type.isAssignableFrom (SshFileSystemProvider.SshFileAttributeView.class);
        }

        public boolean supportsFileAttributeView (String name)
        {
            return  name.equals ("basic")  ||  name.equals ("posix");
        }

        public <V extends FileStoreAttributeView> V getFileStoreAttributeView (Class<V> type)
        {
            return null;
        }

        public Object getAttribute (String attribute) throws IOException
        {
            return null;
        }

        public long extractSize (String value)
        {
            int last = value.length () - 1;
            long result = Long.valueOf (value.substring (0, last));
            switch (value.substring (last).toUpperCase ())
            {
                case "T": result *= 0x1l << 40; break;
                case "G": result *= 0x1l << 30; break;
                case "M": result *= 0x1l << 20; break;
                case "K": result *= 0x1l << 10; break;
            }
            return result;
        }
    }

    public synchronized WrapperSftp getSftp () throws IOException
    {
        if (sftp == null) sftp = new WrapperSftp ();
        return sftp;
    }

    /**
        Make sftp safe to use.
        If any protocol-level error occurs, we need to detect it and make a new sftp connection.
        ChannelSftp does not appear to be thread-safe, so all methods of this class are synchronized.
    **/
    public class WrapperSftp implements Closeable
    {
        public ChannelSftp sftp;

        public synchronized void connect () throws IOException
        {
            if (sftp != null  &&  sftp.isConnected ())
            {
                if (! sftp.isClosed ()  &&  ! sftp.isEOF ()) return;  // channel is still good
                close ();
            }

            try
            {
                connection.connect ();
                sftp = (ChannelSftp) connection.session.openChannel ("sftp");  // Best I can tell, openChannel() is thread-safe.
                sftp.connect (connection.timeout);
            }
            catch (JSchException e)
            {
                throw new IOException (e);
            }
        }

        public synchronized void close ()
        {
            if (sftp != null) sftp.disconnect ();
            sftp = null;
        }

        public synchronized SftpATTRS stat (String path) throws IOException, SftpException
        {
            try
            {
                connect ();
                return sftp.stat (path);
            }
            catch (SftpException e)
            {
                if (e.id >= ChannelSftp.SSH_FX_BAD_MESSAGE) close ();
                throw e;
            }
        }

        public synchronized SftpATTRS lstat (String path) throws IOException, SftpException
        {
            try
            {
                connect ();
                return sftp.lstat (path);
            }
            catch (SftpException e)
            {
                if (e.id >= ChannelSftp.SSH_FX_BAD_MESSAGE) close ();
                throw e;
            }
        }

        @SuppressWarnings("unchecked")  // ChannelSftp.ls() does not specify the exact type of the return vector, but we do.
        public synchronized Vector<LsEntry> ls (String path) throws IOException, SftpException
        {
            try
            {
                connect ();
                return sftp.ls (path);
            }
            catch (SftpException e)
            {
                if (e.id >= ChannelSftp.SSH_FX_BAD_MESSAGE) close ();
                throw e;
            }
        }

        public synchronized void setStat (String path, SftpATTRS attr) throws IOException, SftpException
        {
            try
            {
                connect ();
                sftp.setStat (path, attr);
            }
            catch (SftpException e)
            {
                if (e.id >= ChannelSftp.SSH_FX_BAD_MESSAGE) close ();
                throw e;
            }
        }

        public synchronized void chmod (int permissions, String path) throws IOException, SftpException
        {
            try
            {
                connect ();
                sftp.chmod (permissions, path);
            }
            catch (SftpException e)
            {
                if (e.id >= ChannelSftp.SSH_FX_BAD_MESSAGE) close ();
                throw e;
            }
        }

        public synchronized void chown (int uid, String path) throws IOException, SftpException
        {
            try
            {
                connect ();
                sftp.chown (uid, path);
            }
            catch (SftpException e)
            {
                if (e.id >= ChannelSftp.SSH_FX_BAD_MESSAGE) close ();
                throw e;
            }
        }

        public synchronized void chgrp (int gid, String path) throws IOException, SftpException
        {
            try
            {
                connect ();
                sftp.chgrp (gid, path);
            }
            catch (SftpException e)
            {
                if (e.id >= ChannelSftp.SSH_FX_BAD_MESSAGE) close ();
                throw e;
            }
        }

        public synchronized void rm (String path) throws IOException, SftpException
        {
            try
            {
                connect ();
                sftp.rm (path);
            }
            catch (SftpException e)
            {
                if (e.id >= ChannelSftp.SSH_FX_BAD_MESSAGE) close ();
                throw e;
            }
        }

        public synchronized void mkdir (String path) throws IOException, SftpException
        {
            try
            {
                connect ();
                sftp.mkdir (path);
            }
            catch (SftpException e)
            {
                if (e.id >= ChannelSftp.SSH_FX_BAD_MESSAGE) close ();
                throw e;
            }
        }

        public synchronized void rmdir (String path) throws IOException, SftpException
        {
            try
            {
                connect ();
                sftp.rmdir (path);
            }
            catch (SftpException e)
            {
                if (e.id >= ChannelSftp.SSH_FX_BAD_MESSAGE) close ();
                throw e;
            }
        }
    }
}
