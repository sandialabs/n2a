/*
Copyright 2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gov.sandia.n2a.backend.internal.Simulator;

/**
    Serves as the root node of a tree, providing a user-defined set of files
    as the repo.
**/
public class MPartRepo extends MPart
{
    protected MNode repo;

    /**
        Creates MPart tree with the default "models" repo.
    **/
    public MPartRepo (MNode source)
    {
        super (null, null, source);
        build (AppData.docs.childOrCreate ("models"));
    }

    /**
        Creates MPart tree with explicit repo.
    **/
    public MPartRepo (MNode source, MNode repo)
    {
        super (null, null, source);
        build (repo);
    }

    /**
        Creates MPart tree with repo constructed from a list of paths.
    **/
    public MPartRepo (MNode source, List<Path> paths)
    {
        super (null, null, source);
        build (paths);
    }

    /**
        Creates MPart tree with repo constructed from a path string.
    **/
    public MPartRepo (MNode source, String paths)
    {
        super (null, null, source);
        build (paths);
    }

    public void build (MNode repo)
    {
        this.repo = repo;
        underrideChildren (null, source);
        expand ();
    }

    public void build (List<Path> paths)
    {
        // Assemble search path into a repo.
        List<MNode> containers = new ArrayList<MNode> ();
        for (Path path : paths)
        {
            if (Files.isDirectory (path))
            {
                containers.add (new MDir (path));
            }
            else
            {
                MDocGroupKey group = new MDocGroupKey ();
                group.set (path, path.getFileName ().toString ());
                containers.add (group);
            }
        }
        build (new MCombo (null, containers));
    }

    public void build (String paths)
    {
        Simulator simulator = Simulator.instance.get ();
        String[] pieces = paths.split (":");
        List<Path> parsedPaths = new ArrayList<Path> (pieces.length);
        for (String p : pieces) parsedPaths.add (simulator.jobDir.resolve (Paths.get (p)));
        build (parsedPaths);
    }

    protected MNode getRepo ()
    {
        return repo;  // That's why we're here.
    }

    /**
        A variant of MDocGroup where the key must be a simple name rather than path.
        This does not handle move() or set() that changes path.
    **/
    public static class MDocGroupKey extends MDocGroup
    {
        protected Map<String,Path> paths = new HashMap<String,Path> ();

        public Path pathForDoc (String key)
        {
            return paths.get (key).toAbsolutePath ();
        }

        public synchronized MNode set (Path value, String key)
        {
            paths.put (key, value);
            return super.set (null, key);  // value is ignored
        }
    }
}
