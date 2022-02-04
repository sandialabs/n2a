/*
Copyright 2020-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.SftpException;

import gov.sandia.n2a.host.SshFileSystem.WrapperSftp;

public class SshPath implements Path
{
    protected SshFileSystem fileSystem;
    protected boolean       absolute;  // indicates that the first path element is relative to root
    protected String[]      path;
    protected LsEntry       lsEntry;  // Stashed if this path was produced by a directory stream. Used to reduce communication if file info is subsequently retrieved.

    /**
        Analyzes the given list of strings to decompose them into proper path elements and
        to determine if the path is absolute or relative. If you're not sure, use this constructor.
    **/
    public SshPath (SshFileSystem fileSystem, String... path)
    {
        this.fileSystem = fileSystem;
        absolute        = path == null  ||  path.length == 0  ||  path[0].startsWith ("/");

        // path parameter should never be null, but we defend against it anyway.
        if (path == null)
        {
            this.path = new String[0];
            return;
        }

        List<String> elements = new ArrayList<String> ();
        for (String p : path) addElements (p, elements);
        int count = elements.size ();
        //absolute = absolute  ||  count == 0;  // Path is effectively empty. Not sure if this really reflects a user intention to make root path, or it was just a mistake.
        this.path = elements.toArray (new String[count]);
    }

    public SshPath (SshFileSystem fileSystem, List<String> path)
    {
        this (fileSystem, path.toArray (new String[path.size ()]));
    }

    /**
        A faster constructor that does not do any analysis on parameters.
        This assumes you specified a properly-decomposed path and know exactly what you're doing.
        @param path Must be a properly decomposed path. A "/" must not appear in any element.
    **/
    public SshPath (SshFileSystem fileSystem, boolean absolute, String... path)
    {
        this.fileSystem = fileSystem;
        this.absolute   = absolute;
        this.path       = path;
    }

    /**
        Splits given string into path elements and appends them to list.
        This is a utility function to assist in constructing path object.
    **/
    public static void addElements (String element, List<String> result)
    {
        String[] pieces = element.split ("/");  // Skips empty elements. It's possible for pieces to have no elements at all (array of zero length).
        for (String p : pieces) result.add (p);
    }

    public FileSystem getFileSystem ()
    {
        return fileSystem;
    }

    public boolean isAbsolute ()
    {
        return absolute;
    }

    public Path getRoot ()
    {
        if (absolute) return fileSystem.rootDir;
        return null;
    }

    public Path getFileName ()
    {
        if (path.length == 0) return null;
        return new SshPath (fileSystem, false, path[path.length-1]);
    }

    public Path getParent ()
    {
        if (path.length == 0) return null;
        if (path.length == 1)
        {
            if (! absolute) return null;
            return fileSystem.rootDir;
        }
        return new SshPath (fileSystem, absolute, Arrays.copyOfRange (path, 0, path.length - 1));
    }

    public int getNameCount ()
    {
        return path.length;
    }

    public Path getName (int index)
    {
        return new SshPath (fileSystem, false, path[index]);
    }

    public Path subpath (int beginIndex, int endIndex)
    {
        return new SshPath (fileSystem, false, Arrays.copyOfRange (path, beginIndex, endIndex));
    }

    public boolean startsWith (Path other)
    {
        if (! (other instanceof SshPath)) return false;
        SshPath B = (SshPath) other;
        if (fileSystem != B.fileSystem) return false;
        if (absolute != B.absolute) return false;
        if (B.path.length > path.length) return false;
        for (int i = 0; i < B.path.length; i++) if (! path[i].equals (B.path[i])) return false;
        return true;
    }

    public boolean startsWith (String other)
    {
        return startsWith (new SshPath (fileSystem, other));
    }

    public boolean endsWith (Path other)
    {
        if (! (other instanceof SshPath)) return false;
        SshPath B = (SshPath) other;

        int count = B.path.length;
        int start = path.length - count;  // where to start checking in A path
        if (start < 0) return false;  // B is longer, so we can't end with it
        if (start == 0  &&  absolute != B.absolute) return false;  // Same length, so must agree on absolute vs. relative.
        if (start > 0  &&  B.absolute) return false;  // B is shorter, so it can't be absolute.
        for (int i = 0; i < count; i++)  // Iterate through B path elements and compare with A.
        {
            if (! B.path[i].equals (path[start+i])) return false;
        }
        return true;
    }

    public boolean endsWith (String other)
    {
        return endsWith (new SshPath (fileSystem, other));
    }

    public Path normalize ()
    {
        List<String> newPath = new ArrayList<String> ();
        for (String p : path)
        {
            if (p.equals (".")) continue;
            if (p.equals (".."))
            {
                int size = newPath.size ();
                if (size > 0) newPath.remove (size - 1);
            }
            else
            {
                newPath.add (p);
            }
        }
        return new SshPath (fileSystem, absolute, newPath.toArray (new String[newPath.size ()]));
    }

    public SshPath resolve (Path other)
    {
        if (other.isAbsolute ())
        {
            if (other instanceof SshPath) return (SshPath) other;
            return new SshPath (fileSystem, other.toString ());
        }
        int count = other.getNameCount ();
        if (count == 0) return this;

        SshPath B = (SshPath) other;
        String[] combined = new String[path.length + count];
        System.arraycopy (path, 0, combined, 0, path.length);
        int i = path.length;
        for (String p : B.path) combined[i++] = p;
        return new SshPath (fileSystem, absolute, combined);
    }

    public Path resolve (String other)
    {
        return resolve (new SshPath (fileSystem, other));
    }

    public Path resolveSibling (Path other)
    {
        return getParent ().resolve (other);
    }

    public Path resolveSibling (String other)
    {
        return resolveSibling (new SshPath (fileSystem, other));
    }

    public Path relativize (Path other)
    {
        if (! (other instanceof SshPath)) throw new ProviderMismatchException ();
        SshPath B = (SshPath) other;

        if (absolute != B.absolute) throw new IllegalArgumentException ("Paths must agree on absolute or relative");
        if (path.length == 0) return other;

        int min = Math.min (path.length, B.path.length);
        int fda = 0;  // first divergent ancestor. Index of the first element where the paths disagree.
        while (fda < min)
        {
            if (! path[fda].equals (B.path[fda])) break;
            fda++;
        }

        List<String> newPath = new ArrayList<String> ();
        for (int i = fda; i < path.length; i++) newPath.add ("..");
        for (int i = fda; i < B.path.length; i++) newPath.add (B.path[i]);
        return new SshPath (fileSystem, false, newPath.toArray (new String[newPath.size ()]));
    }

    public URI toUri ()
    {
        return fileSystem.uri.resolve (toAbsolutePath ().toString ());
    }

    public SshPath toAbsolutePath ()
    {
        if (absolute) return this;
        return fileSystem.defaultDir.resolve (this);
    }

    public Path toRealPath (LinkOption... options) throws IOException
    {
        // TODO
        return null;
    }

    public File toFile ()
    {
        throw new UnsupportedOperationException ();
    }

    public WatchKey register (WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException
    {
        throw new UnsupportedOperationException ();
    }

    public WatchKey register (WatchService watcher, Kind<?>... events) throws IOException
    {
        return register (watcher, events, new WatchEvent.Modifier[0]);
    }

    public Iterator<Path> iterator ()
    {
        return new Iterator<Path> ()
        {
            int index = 0;
            int count = getNameCount ();

            public boolean hasNext ()
            {
                return index < count;
            }

            public Path next ()
            {
                return getName (index++);
            }
        };
    }

    public int compareTo (Path other)
    {
        return toString().compareTo (other.toString ());
    }

    public String toString ()
    {
        StringBuilder result = new StringBuilder ();
        if (path.length > 0) result.append (path[0]);
        for (int i = 1; i < path.length; i++) result.append ("/").append (path[i]);
        return result.toString ();
    }

    /**
        Make this path suitable for transmission over ssh.
        This includes making the path absolute, escaping special characters and surrounding with quote marks.
        The current implementation uses single quotes, which allow any character except single quote.
        If single-quotes are needed, then it will be necessary to use double-quotes and character escapes for $ and ".
    **/
    public String quote ()
    {
        String result = toAbsolutePath ().toString ();
        return "'" + result + "'";
    }

    public WrapperSftp getSftp () throws IOException
    {
        return fileSystem.getSftp ();
    }

    /**
        Utility to check if file exists. Does not follow symbolic links, so this can be used to check
        existence of the link itself, rather than its target.
    **/
    public boolean exists () throws IOException
    {
        String name = toAbsolutePath ().toString ();
        try
        {
            getSftp ().lstat (name);
            return true;
        }
        catch (SftpException e)
        {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return false;
            throw new IOException (e);
        }
    }

    /**
        Convenience wrapper for FileSystempProvider.checkAccess that follows links.
    **/
    public boolean existsResolved () throws IOException
    {
        try
        {
            SshFileSystemProvider.instance.checkAccess (this);
            return true;
        }
        catch (NoSuchFileException e)
        {
            return false;
        }
    }
}
