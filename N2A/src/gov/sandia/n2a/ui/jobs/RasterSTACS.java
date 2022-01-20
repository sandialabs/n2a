/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jfree.data.general.DatasetChangeEvent;

/**
    Create a spike-raster plot.
**/
public class RasterSTACS extends Raster
{
    protected List<ComputeNode> nodes = new ArrayList<ComputeNode> ();

    public static class ComputeNode
    {
        public List<Path> paths = new ArrayList<Path> ();
        public int  lastStep;     // Index in paths. Presumably, this is the only file that might be partially processed.
        public long nextPosition; // Position in file where read should resume when more data arrives.
    }

    public RasterSTACS (Path path)
    {
        super (path);
        timeQuantum = 1e-3;
    }

    public void parse (Path path)
    {
        // Read entire local directory
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream (path))
        {
            for (Path p : dirStream)
            {
                // Sort files into nodes

                String name = p.getFileName ().toString ();
                if (name.startsWith (".")) continue;
                String[] pieces = name.split ("\\.");
                if (pieces.length != 4) return;  // name has wrong format, so probably not a STACS output dir
                int nodeID = Integer.valueOf (pieces[3]);

                while (nodes.size () <= nodeID) nodes.add (new ComputeNode ());
                ComputeNode node = nodes.get (nodeID);
                node.paths.add (p);  // Path object will be repeatedly replaced, but it will still point to the same actual file.
            }
        }
        catch (IOException e) {}

        // For each node, iterate over files that haven't been processed yet, in step order.
        for (ComputeNode node : nodes)
        {
            if (node.paths.isEmpty ()) continue;
            while (true)
            {
                // Process file
                Path p = node.paths.get (node.lastStep);
                try (SeekableByteChannel channel = Files.newByteChannel (p);
                     Reader reader = Channels.newReader (channel, "UTF-8");
                     BufferedReader br = new BufferedReader (reader))
                {
                    // Adjust start position to be first character after last end-of-line character previously read.
                    // Compare with code in OutputParser.parse(File)
                    if (node.nextPosition > 0)  // Note that "nextPosition" refers to bytes, not characters.
                    {
                        channel.position (node.nextPosition - 1);
                        ByteBuffer buffer = ByteBuffer.allocate (1);
                        channel.read (buffer);
                        byte b = buffer.get (0);
                        if (b != 13  &&  b != 10)  // Last character is not CR or LF
                        {
                            // Step backwards until we find a CR or LF
                            boolean found = false;
                            node.nextPosition--;  // Because we already checked the last byte.
                            int step = 32;
                            while (node.nextPosition > 0  &&  ! found)
                            {
                                node.nextPosition -= step;
                                if (node.nextPosition < 0)
                                {
                                    step += node.nextPosition;
                                    node.nextPosition = 0;
                                }

                                channel.position (node.nextPosition);
                                if (buffer.array ().length != step) buffer = ByteBuffer.allocate (step);
                                else                                buffer.clear ();
                                channel.read (buffer);
                                for (int i = step - 1; i >= 0; i--)
                                {
                                    b = buffer.get (i);
                                    if (b == 13  ||  b == 10)
                                    {
                                        found = true;
                                        node.nextPosition += i + 1;
                                        break;
                                    }
                                }
                            }
                        }
                        channel.position (node.nextPosition);
                    }

                    // Read lines and process
                    while (true)
                    {
                        String line = br.readLine ();
                        if (line == null) break;  // indicates end of stream
                        if (line.isBlank ()) continue;

                        String[] pieces = line.split (" ");
                        if (pieces.length < 3) break;  // Partial read, so presumably at end of file. This is not sufficient to trap the case where the vertex ID is cut off part way.
                        double time   = Long.parseLong   (pieces[1], 16) / 1e9;
                        int    vertex = Integer.parseInt (pieces[2]);

                        while (columns.size () <= vertex) columns.add (null);
                        Column c = columns.get (vertex);
                        if (c == null)
                        {
                            c = new Column ();
                            columns.set (vertex, c);
                            c.header = String.valueOf (vertex);
                            c.index = vertex;
                        }
                        c.values.add ((float) time);  // loses precision
                    }

                    node.nextPosition = Files.size (p);
                }
                catch (IOException e) {}

                // Advance to next file, if it exists
                if (node.lastStep + 1 >= node.paths.size ()) break;
                node.lastStep++;
                node.nextPosition = 0;
            }
        }
    }

    public void updateDataset ()
    {
        parse (path);

        int current = colors.size ();
        int count   = columns.size ();
        for (int i = current; i < count; i++)
        {
            Column c = columns.get (i);
            if (c == null  ||  c.color == null) colors.add (red);
            else                                colors.add (c.color);
        }

        // Sort data series in each column
        // Apparently, JFreeChart assumes a sorted list, and takes advantage of this for
        // early-out when displaying a portion of the graph.
        for (Column c : columns)
        {
            if (c == null) continue;
            Collections.sort (c.values);
        }

        listener.datasetChanged (new DatasetChangeEvent (this, this));
    }
}
