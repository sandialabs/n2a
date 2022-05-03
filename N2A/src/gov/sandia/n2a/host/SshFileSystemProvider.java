/*
Copyright 2020-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.sftp.client.SftpClient.Attributes;
import org.apache.sshd.sftp.client.SftpClient.DirEntry;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;

import gov.sandia.n2a.host.Host.AnyProcess;
import gov.sandia.n2a.host.Host.AnyProcessBuilder;
import gov.sandia.n2a.host.SshFileSystem.WrapperSftp;

public class SshFileSystemProvider extends FileSystemProvider
{
    protected static SshFileSystemProvider instance;
    static
    {
        instance = new SshFileSystemProvider ();
    }

    // Map between remote host names and file system instances.
    protected Map<String,SshFileSystem> fileSystems = new HashMap<String,SshFileSystem> ();

    public String getScheme ()
    {
        return "ssh";
    }

    public synchronized FileSystem newFileSystem (URI uri, Map<String,?> env) throws IOException
    {
        String address = uri.getHost ();
        if (fileSystems.containsKey (address)) throw new FileSystemAlreadyExistsException ();

        Object o = env.get ("connection");
        if (! (o instanceof Connection)) throw new IllegalArgumentException ();
        @SuppressWarnings("resource")
        Connection connection = (Connection) o;

        SshFileSystem result = new SshFileSystem ();
        result.uri           = uri;
        result.connection    = connection;
        result.ownConnection = env.get ("ownConnection") != null;  // We don't check the value, only whether the key exists.
        result.defaultDir    = new SshPath (result, uri.getPath ());
        fileSystems.put (address, result);
        return result;
    }

    public synchronized FileSystem getFileSystem (URI uri)
    {
        SshFileSystem result = fileSystems.get (uri.getHost ());
        if (result == null) throw new FileSystemNotFoundException ();
        return result;
    }

    public Path getPath (URI uri)
    {
        SshFileSystem fileSystem;
        try
        {
            fileSystem = (SshFileSystem) getFileSystem (uri);
        }
        catch (FileSystemNotFoundException e)
        {
            try
            {
                Map<String,Object> env = new HashMap<String,Object> ();

                String address = uri.getHost ();
                Host host = Host.getByAddress (address);
                if (! (host instanceof RemoteUnix)) throw new Exception ("URI must have an associated host already defined.");

                @SuppressWarnings("resource")
                RemoteUnix remote = (RemoteUnix) host;
                env.put ("connection", remote.connection);
                fileSystem = (SshFileSystem) newFileSystem (uri, env);
            }
            catch (Exception e2)
            {
                throw e;
            }
        }

        return new SshPath (fileSystem, uri.getPath ());
    }

    public InputStream newInputStream (Path path, OpenOption... options) throws IOException
    {
        for (OpenOption opt : options)
        {
            if (opt == StandardOpenOption.APPEND  ||  opt == StandardOpenOption.WRITE)
            {
                throw new UnsupportedOperationException ();
            }
        }

        return new InitialSkipStream ((SshPath) path);
    }

    public OutputStream newOutputStream (Path path, OpenOption... options) throws IOException
    {
        boolean create    = true;
        boolean createNew = false;
        boolean truncate  = true;
        boolean append    = false;
        if (options != null  &&  options.length > 0)
        {
            Set<OpenOption> optionSet = new HashSet<OpenOption> (Arrays.asList (options));
            create    = optionSet.contains (StandardOpenOption.CREATE);
            createNew = optionSet.contains (StandardOpenOption.CREATE_NEW);
            truncate  = optionSet.contains (StandardOpenOption.TRUNCATE_EXISTING);
            append    = optionSet.contains (StandardOpenOption.APPEND);
            if (optionSet.contains (StandardOpenOption.READ))            throw new IllegalArgumentException ();
            if (optionSet.contains (StandardOpenOption.DELETE_ON_CLOSE)) throw new UnsupportedOperationException ();
        }

        SshPath A = (SshPath) path;
        if (! create  ||  createNew)
        {
            try
            {
                checkAccess (A);
                if (createNew) throw new FileAlreadyExistsException (A.toString ());
            }
            catch (NoSuchFileException e)
            {
                if (! create  &&  ! createNew) throw e;
                checkAccess (A.getParent ());
            }
        }
        else
        {
            checkAccess (A.getParent ());
        }

        try
        {
            // cat performs as well or better than sftp put on files up to 1M.
            // For small files cat is even better, because it has almost no protocol setup.

            List<String> args = new ArrayList<String> ();
            args.add ("cat");
            if (append  &&  ! truncate) args.add (">>");
            else                        args.add (">");
            args.add (A.quote ());
            AnyProcess proc = A.fileSystem.connection.build (args).start ();
            OutputStream stream = proc.getOutputStream ();
            return new OutputStream ()  // Wrap the stream, so that when it is closed the channel is closed as well.
            {
                public void close () throws IOException
                {
                    stream.close ();  // Causes cat command to exit.
                    try {proc.waitFor (1, TimeUnit.SECONDS);}
                    catch (InterruptedException e) {}
                    proc.close ();
                }

                public void write (int b) throws IOException
                {
                    stream.write (b);
                }

                public void write (byte b[], int off, int len) throws IOException
                {
                    stream.write (b, off, len);
                }

                public void flush () throws IOException
                {
                    stream.flush ();
                }
            };
        }
        catch (Exception e)
        {
            throw new IOException (e);
        }
    }

    public SeekableByteChannel newByteChannel (Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException
    {
        return new SshSeekableByteChannel ((SshPath) path, options, attrs);
    }

    public DirectoryStream<Path> newDirectoryStream (Path dir, Filter<? super Path> filter) throws IOException
    {
        return new SshDirectoryStream ((SshPath) dir, filter);
    }

    public void createDirectory (Path dir, FileAttribute<?>... attributes) throws IOException
    {
        SshPath A = (SshPath) dir;
        String name = A.toAbsolutePath ().toString ();
        try
        {
            WrapperSftp sftp = A.getSftp ();
            sftp.mkdir (name);
            applyAttributes (A, attributes);
        }
        catch (SftpException e)
        {
            // SSH_FX_FAILURE is returned when dir already exists.
            // Not sure what other conditions it might be returned under,
            // so the following conclusion might not be accurate ...
            if (e.getStatus () == SftpConstants.SSH_FX_FAILURE) throw new FileAlreadyExistsException (name);
            throw new IOException (e);
        }
    }

    public void createSymbolicLink (Path link, Path target, FileAttribute<?>... attributes) throws IOException
    {
        if (isSameFile (link, target)) throw new IOException ("Can't link to self");

        SshPath A = (SshPath) link;
        SshPath B = (SshPath) target;
        if (A.fileSystem != B.fileSystem) throw new IOException ("Can't link across file systems");
        String Astring = A.toAbsolutePath ().toString ();
        String Bstring = B.toAbsolutePath ().toString ();

        List<String> args = new ArrayList<String> ();
        args.add ("ln");
        args.add ("-s");
        args.add (Bstring);
        args.add (Astring);
        execute (A, args);
        applyAttributes (A, attributes);
    }

    @SuppressWarnings("unchecked")
    protected SshFileAttributeView applyAttributes (SshPath path, FileAttribute<?>... attributes) throws IOException
    {
        SshFileAttributeView view = new SshFileAttributeView ((SshPath) path);
        for (FileAttribute<?> a : attributes)
        {
            switch (a.name ())
            {
                case "posix:permissions":
                    view.setPermissions ((Set<PosixFilePermission>) a.value ());
                    break;
                case "posix:owner":
                    view.setOwner ((UserPrincipal) a.value ());
                    break;
                case "posix:group":
                    view.setGroup ((GroupPrincipal) a.value());
            }
        }
        return view;
    }

    protected PosixFileAttributes createFile (SshPath path, FileAttribute<?>... attributes) throws IOException
    {
        execute (path, "touch", path.quote ());
        return applyAttributes (path, attributes).readAttributes ();
    }

    public void delete (Path path) throws IOException
    {
        String name = path.toAbsolutePath ().toString ();
        try
        {
            WrapperSftp sftp = ((SshPath) path).getSftp ();
            Attributes attributes = sftp.lstat (name);  // doesn't follow links
            if (attributes.isDirectory ()) sftp.rmdir (name);
            else                           sftp.rm    (name);
        }
        catch (SftpException e)
        {
            if (e.getStatus () == SftpConstants.SSH_FX_NO_SUCH_FILE) throw new NoSuchFileException (name);
            throw new IOException (e);
        }
    }

    public void copy (Path source, Path target, CopyOption... options) throws IOException
    {
        moveOrCopy ("cp", source, target, options);
    }

    public void move (Path source, Path target, CopyOption... options) throws IOException
    {
        moveOrCopy ("mv", source, target, options);
    }

    public void moveOrCopy (String mvcp, Path source, Path target, CopyOption... options) throws IOException
    {
        if (isSameFile (source, target)) return;

        SshPath A = (SshPath) source;
        SshPath B = (SshPath) target;
        String Astring = A.toAbsolutePath ().toString ();
        String Bstring = B.toAbsolutePath ().toString ();

        Set<CopyOption> optionSet = toSet (options);

        if (A.fileSystem == B.fileSystem)  // moving on same host
        {
            List<String> args = new ArrayList<String> ();
            args.add (mvcp);
            if (optionSet.contains (StandardCopyOption.REPLACE_EXISTING))
            {
                args.add ("-f");
            }
            else if (B.exists ())
            {
                throw new FileAlreadyExistsException (Bstring);
            }
            args.add (Astring);
            args.add (Bstring);
            execute (A, args);
        }
        else  // Crossing between hosts. Stream A -> localhost -> B, then delete A.
        {
            // TODO: implement streaming
            // For now, we give up ...
            throw new UnsupportedOperationException ();
        }
    }

    public boolean isSameFile (Path path, Path path2) throws IOException
    {
        SshPath A = (SshPath) path;
        SshPath B = (SshPath) path2;
        if (A.fileSystem != B.fileSystem) return false;
        try
        {
            Object Akey = readAttributes (A, PosixFileAttributes.class).fileKey ();
            Object Bkey = readAttributes (B, PosixFileAttributes.class).fileKey ();
            return Akey.equals (Bkey);
        }
        catch (NoSuchFileException e)
        {
            return false;  // They can't be the same if either of them does not exist.
        }
    }

    public boolean isHidden (Path path) throws IOException
    {
        return path.getFileName ().toString ().startsWith( "." );
    }

    public FileStore getFileStore (Path path) throws IOException
    {
        return ((SshPath) path).fileSystem.getFileStore ();
    }

    public void checkAccess (Path path, AccessMode... modes) throws IOException
    {
        SshPath A = (SshPath) path;
        String name = A.toAbsolutePath ().toString ();
        try
        {
            Attributes attributes = A.getSftp ().stat (name);
            int permissions = attributes.getPermissions ();
            for (AccessMode mode : modes)
            {
                switch (mode)
                {
                    case READ:
                        if ((permissions & 0444) == 0) throw new AccessDeniedException ("READ " + A);
                        break;
                    case WRITE:
                        if ((permissions & 0222) == 0) throw new AccessDeniedException ("WRITE " + A);
                        break;
                    case EXECUTE:
                        if ((permissions & 0111) == 0) throw new AccessDeniedException ("EXECUTE " + A);
                        break;
                    default:
                        throw new UnsupportedOperationException ();
                }
            }
        }
        catch (SftpException e)
        {
            if (e.getStatus () == SftpConstants.SSH_FX_NO_SUCH_FILE) throw new NoSuchFileException (name);
            throw new IOException (e);
        }
    }

    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V getFileAttributeView (Path path, Class<V> type, LinkOption... options)
    {
        if (! type.isAssignableFrom (SshFileAttributeView.class)) return null;
        return (V) new SshFileAttributeView ((SshPath) path, options);
    }

    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes (Path path, Class<A> type, LinkOption... options) throws IOException
    {
        if (! type.isAssignableFrom (SshFileAttributes.class)) return null;
        return (A) new SshFileAttributeView ((SshPath) path, options).readAttributes ();
    }

    public Map<String,Object> readAttributes (Path path, String attributes, LinkOption... options) throws IOException
    {
        Map<String,Object> result = new HashMap<String,Object> ();
        PosixFileAttributes pfa = new SshFileAttributeView ((SshPath) path, options).readAttributes ();
        for (String a : attributes.split (","))
        {
            if (a.contains (":")) a = a.split (":", -1)[1];
            switch (a)
            {
                case "creationTime":
                    result.put (a, pfa.creationTime ());
                    break;
                case "lastModifiedTime":
                case "lastChangedTime":
                    result.put (a, pfa.lastModifiedTime ());
                    break;
                case "lastAccessTime":
                    result.put (a, pfa.lastAccessTime ());
                    break;
                case "owner":
                    result.put (a, pfa.owner ());
                    break;
                case "group":
                    result.put (a, pfa.group ());
                    break;
                case "permissions":
                    result.put (a, pfa.permissions ());
                    break;
                case "size":
                    result.put (a, pfa.size ());
                    break;
                case "fileKey":
                    result.put (a, pfa.fileKey ());
                    break;
                case "isRegularFile":
                    result.put (a, pfa.isRegularFile ());
                    break;
                case "isDirectory":
                    result.put (a, pfa.isDirectory ());
                    break;
                case "isSymbolicLink":
                    result.put (a, pfa.isSymbolicLink ());
                    break;
                case "isOther":
                    result.put (a, pfa.isOther ());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public void setAttribute (Path path, String attribute, Object value, LinkOption... options) throws IOException
    {
        PosixFileAttributeView view = new SshFileAttributeView ((SshPath) path, options);
        switch (attribute)
        {
            case "creationTime":
                view.setTimes (null, null, (FileTime) value);
                break;
            case "lastModifiedTime":
            case "lastChangedTime":
                view.setTimes ((FileTime) value, null, null);
                break;
            case "lastAccessTime":
                view.setTimes (null, (FileTime) value, null);
                break;
            case "owner":
                view.setOwner ((UserPrincipal) value);
                break;
            case "group":
                view.setGroup ((GroupPrincipal) value);
                break;
            case "permissions":
                view.setPermissions ((Set<PosixFilePermission>) value);
                break;
        }
    }

    public class InitialSkipStream extends InputStream
    {
        protected SshPath     path;
        protected long        position;  // Where to start reading
        protected AnyProcess  proc;
        protected InputStream stream;

        public InitialSkipStream (SshPath path)
        {
            this.path = path;
        }

        protected void start () throws IOException
        {
            if (proc != null) return;

            List<String> command = new ArrayList<String> ();
            if (position == 0)
            {
                command.add ("cat");
                command.add (path.quote ());
            }
            else
            {
                command.add ("dd");
                command.add ("bs=1");
                command.add ("skip=" + position);
                command.add ("if=" + path.quote ());
            }
            proc = path.fileSystem.connection.build (command).start ();
            stream = proc.getInputStream ();
        }

        public void close () throws IOException
        {
            if (proc != null) proc.close ();
        }

        public int read () throws IOException
        {
            start ();
            return stream.read ();
        }

        public int read (byte b[], int off, int len) throws IOException
        {
            start ();
            return stream.read (b, off, len);
        }

        public long skip (long n) throws IOException
        {
            if (proc != null) return super.skip (n);
            position += n;
            return n;  // Somewhat of a lie, since we don't know if this exceeds EOF.
        }

        public int available () throws IOException
        {
            start ();
            return stream.available ();
        }
    }

    public class SshSeekableByteChannel implements SeekableByteChannel
    {
        protected SshPath path;
        protected boolean append;
        protected boolean readable;
        protected boolean writeable;
        protected boolean open;
        protected long    position;
        protected long    size;  // This number can change in multiple ways.

        public SshSeekableByteChannel (SshPath path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException
        {
            this.path = path.toAbsolutePath();
            append    = options.contains (StandardOpenOption.APPEND);
            readable  = options.isEmpty ()  ||  options.contains (StandardOpenOption.READ);
            writeable = options.contains (StandardOpenOption.WRITE)  ||  append;

            PosixFileAttributes attributes = null;
            try
            {
                attributes = readAttributes (path, PosixFileAttributes.class);
            }
            catch (NoSuchFileException e) {}

            boolean create = false;
            if (options.contains (StandardOpenOption.CREATE_NEW))
            {
                if (attributes != null) throw new FileAlreadyExistsException (path.toString ());
                create = true;
            }
            else if (attributes == null)
            {
                if (options.contains (StandardOpenOption.CREATE)) create = true;
                else throw new NoSuchFileException (path.toString ());
            }
            if (create) attributes = createFile (path, attrs);

            size = attributes.size ();
            open = true;
        }

        public void close () throws IOException
        {
            open = false;
        }

        public boolean isOpen ()
        {
            return open;
        }

        public long position () throws IOException
        {
            return position;
        }

        public SshSeekableByteChannel position (long position) throws IOException
        {
            this.position = position;
            return this;
        }

        public int read (ByteBuffer buffer) throws IOException
        {
            if (! readable) throw new NonReadableChannelException ();
            int count = buffer.remaining ();
            if (count == 0) return 0;

            int result = 0;
            Connection connection = path.fileSystem.connection;
            AnyProcessBuilder b = connection.build ("dd", "bs=1", "count=" + count, "skip=" + position, "if=" + path.quote ());
            try (AnyProcess proc = b.start ();
                 InputStream in = proc.getInputStream ();
                 ReadableByteChannel channel = Channels.newChannel (in))
            {
                int received;
                while (buffer.hasRemaining ()  &&  (received = channel.read (buffer)) > 0) result += received;
            }
            catch (IOException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                throw new IOException (e);
            }

            if (result == 0  &&  position >= size) return -1;
            position += result;
            if (position > size) size = position;
            return result;
        }

        public long size () throws IOException
        {
            PosixFileAttributes attributes = readAttributes (path, PosixFileAttributes.class);
            return size = attributes.size ();
        }

        public SshSeekableByteChannel truncate (long newSize) throws IOException
        {
            if (! writeable) throw new NonWritableChannelException ();
            if (newSize < 0) throw new IllegalArgumentException ("Size must be non-negative.");

            if (position > newSize) position = newSize;
            if (size () > newSize)
            {
                execute (path, "truncate", "-s", String.valueOf (newSize), path.quote ());
                size = newSize;
            }
            return this;
        }

        public int write (ByteBuffer buffer) throws IOException
        {
            if (! writeable) throw new NonWritableChannelException ();
            if (append) position = size ();

            int result = 0;
            AnyProcessBuilder b = path.fileSystem.connection.build ("dd", "conv=notrunc", "bs=1", "seek=" + position, "of=" + path.quote ());
            try (AnyProcess proc = b.start ();
                 OutputStream out = proc.getOutputStream ();
                 WritableByteChannel channel = Channels.newChannel (out);)
            {
                result = channel.write (buffer);
                channel.close ();  // Closes "out", which causes dd command to exit.
                proc.waitFor (1, TimeUnit.SECONDS);  // Ensure that all bytes are written before this thread moves on.
            }
            catch (IOException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                throw new IOException (e);
            }

            position += result;
            if (position > size) size = position;
            return result;
        }
    }

    public static class SshDirectoryStream implements DirectoryStream<Path>
    {
        protected SshPath              parent;
        protected Filter<? super Path> filter;
        protected Collection<DirEntry> entries;  // also contains stat information

        public SshDirectoryStream (SshPath parent, Filter<? super Path> filter) throws IOException
        {
            this.parent = parent;
            this.filter = filter;

            String name = parent.toAbsolutePath ().toString ();
            entries = parent.getSftp ().ls (name);
        }

        public void close () throws IOException
        {
        }

        public Iterator<Path> iterator ()
        {
            return new Iterator<Path> ()
            {
                Iterator<DirEntry> it = entries.iterator ();
                Path next = findNext ();

                public Path findNext ()
                {
                    while (it.hasNext ())
                    {
                        DirEntry entry = it.next ();
                        String name = entry.getFilename ();
                        if (name.equals (".")  ||  name.equals ("..")) continue;  // These cause trouble during directory walking, and generally the user doesn't want them anyway.
                        SshPath result = (SshPath) parent.resolve (name);
                        result.dirEntry = entry;
                        try
                        {
                            if (filter == null  ||  filter.accept (result)) return result;
                        }
                        catch (IOException e) {}
                    }
                    return null;
                }

                public boolean hasNext ()
                {
                    return next != null;
                }

                public Path next ()
                {
                    if (next == null) throw new NoSuchElementException ();
                    Path result = next;
                    next = findNext ();
                    return result;
                }
            };
        }

        /**
            Returns the raw number of director entries, without any filtering applied.
            This is mainly to help measure progress when processing through a directory.
        **/
        public int count ()
        {
            if (entries.size () <= 2) return entries.size ();
            return entries.size () - 2;  // subtract 2 for . and ..
        }
    }

    public static class SshPrincipal implements UserPrincipal, GroupPrincipal
    {
        int id;

        public SshPrincipal (int id)
        {
            this.id = id;
        }

        public boolean equals (Object o)
        {
            if (! (o instanceof SshPrincipal)) return false;
            SshPrincipal that = (SshPrincipal) o;
            return id == that.id;
        }

        public String toString ()
        {
            return getName ();
        }

        public int hashCode ()
        {
            return id;
        }

        public String getName ()
        {
            return String.valueOf (id);
        }
    }

    public static class SshFileAttributes implements PosixFileAttributes
    {
        protected Attributes attributes;
        protected SshPath    path;        // In case we want to retrieve file key.
        protected boolean    followLinks; // ditto

        public FileTime lastModifiedTime ()
        {
            return attributes.getModifyTime ();
        }

        public FileTime lastAccessTime ()
        {
            return attributes.getAccessTime ();
        }

        public FileTime creationTime ()
        {
            return attributes.getCreateTime ();
        }

        public boolean isRegularFile ()
        {
            return attributes.isRegularFile ();
        }

        public boolean isDirectory ()
        {
            return attributes.isDirectory ();
        }

        public boolean isSymbolicLink ()
        {
            return attributes.isSymbolicLink ();
        }

        public boolean isOther ()
        {
            return attributes.isOther ();
        }

        public long size ()
        {
            return attributes.getSize ();
        }

        public Object fileKey ()
        {
            // Retrieve the inode number.
            Connection connection = path.fileSystem.connection;
            AnyProcessBuilder b = connection.build ("stat", "--format=%i", path.toAbsolutePath ().toString ());
            try (AnyProcess proc = b.start ();
                 BufferedReader reader = new BufferedReader (new InputStreamReader (proc.getInputStream ()));)
            {
                String line = reader.readLine ();
                if (line == null) return null;
                return Long.valueOf (line);
            }
            catch (Exception e) {}
            return null;
        }

        public UserPrincipal owner ()
        {
            return new SshPrincipal (attributes.getUserId ());
        }

        public GroupPrincipal group ()
        {
            return new SshPrincipal (attributes.getGroupId ());
        }

        public Set<PosixFilePermission> permissions ()
        {
            Set<PosixFilePermission> result = new HashSet<PosixFilePermission> ();
            int p = attributes.getPermissions ();
            if ((p & 0400) != 0) result.add (PosixFilePermission.OWNER_READ);
            if ((p & 0200) != 0) result.add (PosixFilePermission.OWNER_WRITE);
            if ((p & 0100) != 0) result.add (PosixFilePermission.OWNER_EXECUTE);
            if ((p & 0040) != 0) result.add (PosixFilePermission.GROUP_READ);
            if ((p & 0020) != 0) result.add (PosixFilePermission.GROUP_WRITE);
            if ((p & 0010) != 0) result.add (PosixFilePermission.GROUP_EXECUTE);
            if ((p & 0004) != 0) result.add (PosixFilePermission.OTHERS_READ);
            if ((p & 0002) != 0) result.add (PosixFilePermission.OTHERS_WRITE);
            if ((p & 0001) != 0) result.add (PosixFilePermission.OTHERS_EXECUTE);
            return result;
        }
    }

    public static class SshFileAttributeView implements PosixFileAttributeView
    {
        protected SshPath    path;
        protected boolean    followLinks;
        protected Attributes attributes;

        public SshFileAttributeView (SshPath path, LinkOption... options)
        {
            this.path = path;
            followLinks = true;
            for (LinkOption option : options)
            {
                if (option == LinkOption.NOFOLLOW_LINKS) followLinks = false;
            }
        }

        public String name ()
        {
            return "posix";
        }

        protected void fetch () throws IOException
        {
            if (attributes != null) return;
            if (path.dirEntry != null)
            {
                Attributes temp = path.dirEntry.getAttributes ();
                if (! followLinks  ||  ! temp.isSymbolicLink ())
                {
                    attributes = temp;
                    return;
                }
            }

            String name = path.toAbsolutePath ().toString ();
            try
            {
                WrapperSftp sftp = path.getSftp ();
                if (followLinks) attributes = sftp. stat (name);
                else             attributes = sftp.lstat (name);
            }
            catch (SftpException e)
            {
                if (e.getStatus () == SftpConstants.SSH_FX_NO_SUCH_FILE) throw new NoSuchFileException (name);
                throw new IOException (e);
            }
        }

        public PosixFileAttributes readAttributes () throws IOException
        {
            fetch ();
            SshFileAttributes result = new SshFileAttributes ();
            result.attributes  = attributes;
            result.path        = path;
            result.followLinks = followLinks;
            return result;
        }

        public void setTimes (FileTime modify, FileTime access, FileTime create) throws IOException
        {
            fetch ();
            if (modify != null) attributes.modifyTime (modify);
            if (access != null) attributes.accessTime (access);
            if (create != null) attributes.createTime (create);

            String name = path.toAbsolutePath ().toString ();
            path.getSftp ().setStat (name, attributes);
        }

        public UserPrincipal getOwner () throws IOException
        {
            fetch ();
            return new SshPrincipal (attributes.getUserId ());
        }

        public void setOwner (UserPrincipal owner) throws IOException
        {
            fetch ();
            attributes.setOwner (((SshPrincipal) owner).getName ());
            String name = path.toAbsolutePath ().toString ();
            path.getSftp ().setStat (name, attributes);
        }

        public void setGroup (GroupPrincipal group) throws IOException
        {
            fetch ();
            attributes.setGroup (((SshPrincipal) group).getName ());
            String name = path.toAbsolutePath ().toString ();
            path.getSftp ().setStat (name, attributes);
        }

        public void setPermissions (Set<PosixFilePermission> permissions) throws IOException
        {
            fetch ();
            attributes.setPermissions (sftpPermissions (permissions));
            String name = path.toAbsolutePath ().toString ();
            path.getSftp ().setStat (name, attributes);
        }
    }

    /**
        Convenience wrapper for remote process.
        All exceptions are converted to IOException.
        @param path Gives access to the underlying connection.
        @throws IOException
    **/
    public static void execute (SshPath path, String... command) throws IOException
    {
        try
        {
            Connection connection = path.fileSystem.connection;
            try (AnyProcess proc = connection.build (command).start ())
            {
                proc.waitFor ();
            }
        }
        catch (InterruptedException ie)
        {
            throw new IOException (ie);
        }
    }

    public static void execute (SshPath path, List<String> command) throws IOException
    {
        execute (path, command.toArray (new String[command.size ()]));
    }

    public static int sftpPermissions (Set<PosixFilePermission> permissions)
    {
        int result = 0;
        for (PosixFilePermission p : permissions)
        {
            switch (p)
            {
                // octal constants ...
                case OWNER_READ:     result |= 0400; break;
                case OWNER_WRITE:    result |= 0200; break;
                case OWNER_EXECUTE:  result |= 0100; break;
                case GROUP_READ:     result |= 0040; break;
                case GROUP_WRITE:    result |= 0020; break;
                case GROUP_EXECUTE:  result |= 0010; break;
                case OTHERS_READ:    result |= 0004; break;
                case OTHERS_WRITE:   result |= 0002; break;
                case OTHERS_EXECUTE: result |= 0001; break;
            }
        }
        return result;
    }

    public static <T> Set<T> toSet (T[] array)
    {
        return new HashSet<T> (Arrays.asList (array));
    }
}
