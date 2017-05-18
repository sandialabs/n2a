/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
    Identifies the serialization method used by this application.
    Reads/writes the header on serialized objects.
**/
public class Schema
{
    public int    version;  // of schema
    public String type;

    public Schema ()
    {
        version = 1;
        type    = "";
    }

    public Schema (int version, String type)
    {
        this.version = version;
        this.type    = type;
    }

    /**
        Convenience method which reads the header and loads all the objects as children of the given node.
    **/
    public void readAll (Reader reader, MNode node) throws IOException
    {
        BufferedReader br;
        boolean alreadyBuffered = reader instanceof BufferedReader;
        if (alreadyBuffered) br =    (BufferedReader) reader;
        else                 br = new BufferedReader (reader);
        read (br);
        node.read (br);
        if (! alreadyBuffered) br.close ();
    }

    public void read (BufferedReader reader) throws IOException
    {
        String line = reader.readLine ().trim ();
        String[] pieces = line.split ("=", 2);
        if (pieces.length < 2  ||  ! pieces[0].equals ("N2A.schema"))
        {
            throw new IOException ("Schema line not found.");
        }
        pieces = pieces[1].trim ().split (",", 2);
        version = Integer.parseInt (pieces[0]);
        if (pieces.length >= 2) type = pieces[1].trim ();
    }

    /**
        Convenience method which writes the header and all the children of the given node.
        The node itself (that is, its key and value) are not written out. The node simply acts
        as a container for the nodes that get written.
    **/
    public void writeAll (Writer writer, MNode node) throws IOException
    {
        write (writer);
        for (MNode c : node) c.write (writer, "");
    }

    public void write (Writer writer) throws IOException
    {
        writer.write ("N2A.schema=" + version);
        if (! type.isEmpty ()) writer.write ("," + type);
        writer.write (String.format ("%n"));
    }
}
