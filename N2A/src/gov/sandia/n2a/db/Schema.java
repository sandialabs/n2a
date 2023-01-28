/*
Copyright 2017-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
    Encapsulates the serialization method used for a particular file.
    This encompasses two related mechanisms:
    <ol>
    <li>The serialization method. Everything past the first line of the file is expressed in this format.
    <li>The general interpretation of the data.
    </ol>

    Each subclass provides a particular implementation of the serialization/deserialization process,
    and also informs the data consumer of the expected interpretation. This base class defines the
    interface and provides some utility functions.
**/
public class Schema
{
    public int    version;  // of schema. Version 0 means unknown. Version -1 means no-care. Otherwise, version is always positive and increments by 1 with each significant change.
    public String type;

    public Schema (int version, String type)
    {
        this.version = version;
        this.type    = type;
    }

    public static Schema latest ()
    {
        return new Schema2 (3, "");
    }

    /**
        Convenience method which reads the header and loads all the objects as children of the given node.
    **/
    public static Schema readAll (MNode node, Reader reader) throws IOException
    {
        BufferedReader br;
        boolean alreadyBuffered = reader instanceof BufferedReader;
        if (alreadyBuffered) br =    (BufferedReader) reader;
        else                 br = new BufferedReader (reader);
        Schema result = read (br);
        result.read (node, br);
        if (! alreadyBuffered) br.close ();
        return result;
    }

    public static Schema read (BufferedReader reader) throws IOException
    {
        String line = reader.readLine ();
        if (line == null) throw new IOException ("File is empty.");
        line = line.trim ();
        if (! line.startsWith ("N2A.schema")) throw new IOException ("Schema line not found.");
        if (line.length () < 12) throw new IOException ("Malformed schema line.");
        char delimiter = line.charAt (10);
        if (delimiter != '=') throw new IOException ("Malformed schema line.");
        line = line.substring (11);
        String[] pieces = line.split (",", 2);
        int version = Integer.parseInt (pieces[0]);
        String type = "";
        if (pieces.length >= 2) type = pieces[1].trim ();

        // Note: A single schema subclass could handle multiple versions.
        if (version == 1) return new Schema1 (version, type);
        return new Schema2 (version, type);
    }

    public void read (MNode node, Reader reader)
    {
        throw new RuntimeException ("Must use specific schema to read file.");
    }

    /**
        Convenience method which writes the header and all the children of the given node.
        The node itself (that is, its key and value) are not written out. The node simply acts
        as a container for the nodes that get written.
    **/
    public void writeAll (MNode node, Writer writer) throws IOException
    {
        write (writer);
        for (MNode c : node) write (c, writer, "");
    }

    public void write (Writer writer) throws IOException
    {
        writer.write ("N2A.schema=" + version);
        if (! type.isEmpty ()) writer.write ("," + type);
        writer.write (String.format ("%n"));
    }

    /**
        Convenience function for calling write(MNode,Writer,String) with no initial indent.
    **/
    public void write (MNode node, Writer writer)
    {
        try {write (node, writer, "");}
        catch (IOException e) {}
    }

    public void write (MNode node, Writer writer, String indent) throws IOException
    {
        throw new RuntimeException ("Must use specific schema to write file.");
    }
}
