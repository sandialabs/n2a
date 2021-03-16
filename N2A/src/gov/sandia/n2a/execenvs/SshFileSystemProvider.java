/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

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
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.execenvs.Host.AnyProcess;
import gov.sandia.n2a.execenvs.Host.AnyProcessBuilder;
import gov.sandia.n2a.execenvs.SshPath.WrapperSFTP;

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
        result.uri        = uri;
        result.connection = connection;
        result.defaultDir = new SshPath (result, uri.getPath ());
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
                Connection connection;
                String address = uri.getHost ();
                Host host = Host.getByAddress (address);
                if (host instanceof RemoteUnix)
                {
                    @SuppressWarnings("resource")
                    RemoteUnix remote = (RemoteUnix) host;
                    remote.connect ();
                    connection = remote.connection;
                }
                else
                {
                    MNode config = new MVolatile ();
                    config.set (address,             "address");
                    config.set (uri.getUserInfo (),  "username");
                    config.set (uri.getAuthority (), "password");
                    connection = new Connection (config);
                }

                Map<String,Object> env = new HashMap<String,Object> ();
                env.put ("connection", connection);
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

        SshPath A = (SshPath) path;
        try
        {
            AnyProcess proc = A.fileSystem.connection.build ("cat", A.quote ()).start ();
            InputStream stream = proc.getInputStream ();
            return new InputStream ()  // Wrap the stream, so that when it is closed the channel is closed as well.
            {
                public void close () throws IOException
                {
                    proc.close ();
                }

                public int read () throws IOException
                {
                    return stream.read ();
                }

                public int read (byte b[], int off, int len) throws IOException
                {
                    return stream.read (b, off, len);
                }

                public int available () throws IOException
                {
                    return stream.available ();
                }
            };
        }
        catch (JSchException e)
        {
            throw new IOException (e);
        }
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
            }
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
                    stream.flush ();
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

    @SuppressWarnings("unchecked")
    public void createDirectory (Path dir, FileAttribute<?>... attrs) throws IOException
    {
        Set<PosixFilePermission> permissions = null;
        for (FileAttribute<?> fileAttribute : attrs)
        {
            if (fileAttribute.name ().equals ("posix:permissions"))
            {
                permissions = (Set<PosixFilePermission>) fileAttribute.value();
            }
        }

        SshPath A = (SshPath) dir;
        String name = A.toAbsolutePath ().toString ();
        try (WrapperSFTP wrapper = A.getSftp ())
        {
            wrapper.sftp.mkdir (name);
            if (permissions != null) wrapper.sftp.chmod (sftpPermissions (permissions), name);
        }
        catch (SftpException e)
        {
            if (A.exists ()) throw new FileAlreadyExistsException (name);
            throw new IOException (e);
        }
    }

    @SuppressWarnings("unchecked")
    PosixFileAttributes createFile (SshPath path, FileAttribute<?>... attributes) throws IOException
    {
        execute (path, "touch", path.quote ());

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

        return view.readAttributes ();
    }

    public void delete (Path path) throws IOException
    {
        String name = path.toAbsolutePath ().toString ();
        try (WrapperSFTP wrapper = ((SshPath) path).getSftp ())
        {
            SftpATTRS attributes = wrapper.sftp.lstat (name);  // doesn't follow links
            if (attributes.isDir ()) wrapper.sftp.rmdir (name);
            else                     wrapper.sftp.rm    (name);
        }
        catch (SftpException e)
        {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) throw new NoSuchFileException (name);
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
            return false;  // They can't be the same of either of them does not exist.
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
        try (WrapperSFTP wrapper = A.getSftp ())
        {
            SftpATTRS attributes = wrapper.sftp.stat (name);
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
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) throw new NoSuchFileException (name);
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
        protected SshPath                     parent;
        protected Filter<? super Path>        filter;
        protected Vector<ChannelSftp.LsEntry> entries;  // LsEntry also stat information. Sadly, we won't use it here.

        @SuppressWarnings("unchecked")
        public SshDirectoryStream (SshPath parent, Filter<? super Path> filter) throws IOException
        {
            this.parent = parent;
            this.filter = filter;

            String name = parent.toAbsolutePath ().toString ();
            try (WrapperSFTP wrapper = parent.getSftp ())
            {
                entries = wrapper.sftp.ls (name);
            }
            catch (SftpException e)
            {
                throw new IOException (e);
            }
        }

        public void close () throws IOException
        {
        }

        public Iterator<Path> iterator ()
        {
            return new Iterator<Path> ()
            {
                Iterator<ChannelSftp.LsEntry> it = entries.iterator ();
                Path next = findNext ();

                public Path findNext ()
                {
                    while (it.hasNext ())
                    {
                        String name = it.next ().getFilename ();
                        if (name.equals (".")  ||  name.equals ("..")) continue;  // These cause trouble during directory walking, and generally the user doesn't want them anyway.
                        Path result = parent.resolve (name);
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
        protected SftpATTRS attributes;
        protected SshPath   path;        // In case we want to retrieve file key.
        protected boolean   followLinks; // ditto

        public FileTime lastModifiedTime ()
        {
            return FileTime.from (attributes.getMTime (), TimeUnit.SECONDS);
        }

        public FileTime lastAccessTime ()
        {
            return FileTime.from (attributes.getATime (), TimeUnit.SECONDS);
        }

        public FileTime creationTime ()
        {
            // Create time is not supported by SFTP, so punt to last-modified time.
            return lastModifiedTime ();
        }

        public boolean isRegularFile ()
        {
            return attributes.isReg ();
        }

        public boolean isDirectory ()
        {
            return attributes.isDir ();
        }

        public boolean isSymbolicLink ()
        {
            return attributes.isLink ();
        }

        public boolean isOther ()
        {
            return ! (attributes.isReg ()  ||  attributes.isDir ()  ||  attributes.isLink ());
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
                return new Long (line);
            }
            catch (Exception e) {}
            return null;
        }

        public UserPrincipal owner ()
        {
            SshPrincipal result = new SshPrincipal ();
            result.id = attributes.getUId ();
            return result;
        }

        public GroupPrincipal group ()
        {
            SshPrincipal result = new SshPrincipal ();
            result.id = attributes.getGId ();
            return result;
        }

        public Set<PosixFilePermission> permissions ()
        {
            return PosixFilePermissions.fromString (attributes.getPermissionsString ().substring (1).replace ("s", "x"));
        }
    }

    public static class SshFileAttributeView implements PosixFileAttributeView
    {
        protected SshPath path;
        protected boolean followLinks;

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

        public PosixFileAttributes readAttributes () throws IOException
        {
            String name = path.toAbsolutePath ().toString ();
            try (WrapperSFTP wrapper = path.getSftp ())
            {
                SshFileAttributes result = new SshFileAttributes ();
                if (followLinks) result.attributes = wrapper.sftp. stat (name);
                else             result.attributes = wrapper.sftp.lstat (name);
                result.path        = path;
                result.followLinks = followLinks;
                return result;
            }
            catch (SftpException e)
            {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) throw new NoSuchFileException (name);
                throw new IOException (e);
            }
        }

        public void setTimes (FileTime modify, FileTime access, FileTime create) throws IOException
        {
            String name = path.toAbsolutePath ().toString ();
            try (WrapperSFTP wrapper = path.getSftp ())
            {
                SftpATTRS attributes = wrapper.sftp.stat (name);
                int atime =  access == null ? attributes.getATime () : (int) access.to (TimeUnit.SECONDS);
                int mtime =  modify == null ? attributes.getMTime () : (int) modify.to (TimeUnit.SECONDS);
                attributes.setACMODTIME (atime, mtime);
                wrapper.sftp.setStat (name, attributes);
            }
            catch (SftpException e)
            {
                throw new IOException (e);
            }
        }

        public UserPrincipal getOwner () throws IOException
        {
            return readAttributes ().owner ();
        }

        public void setOwner (UserPrincipal owner) throws IOException
        {
            String name = path.toAbsolutePath ().toString ();
            try (WrapperSFTP wrapper = path.getSftp ())
            {
                wrapper.sftp.chown (((SshPrincipal) owner).id, name);
            }
            catch (SftpException e)
            {
                throw new IOException (e);
            }
        }

        public void setGroup (GroupPrincipal group) throws IOException
        {
            String name = path.toAbsolutePath ().toString ();
            try (WrapperSFTP wrapper = path.getSftp ())
            {
                wrapper.sftp.chgrp (((SshPrincipal) group).id, name);
            }
            catch (SftpException e)
            {
                throw new IOException (e);
            }
        }

        public void setPermissions (Set<PosixFilePermission> permissions) throws IOException
        {
            String name = path.toAbsolutePath ().toString ();
            try (WrapperSFTP wrapper = path.getSftp ())
            {
                wrapper.sftp.chmod (sftpPermissions (permissions), name);
            }
            catch (SftpException e)
            {
                throw new IOException (e);
            }
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
        catch (JSchException je)
        {
            throw new IOException (je);
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
