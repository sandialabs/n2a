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
import java.util.List;

import org.jfree.data.DomainOrder;
import org.jfree.data.general.DatasetChangeEvent;
import org.jfree.data.general.DatasetChangeListener;
import org.jfree.data.general.DatasetGroup;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;

/**
    Create a spike-raster plot.
**/
public class RasterSTACS extends Raster implements XYDataset
{
    protected List<ComputeNode>     nodes = new ArrayList<ComputeNode> ();
    protected Column                empty;
    protected DatasetGroup          group;
    protected DatasetChangeListener listener;  // no need to keep a list, because it is always only our own chart

    public static class AuxData
    {
        public int      nextRow;          // that should be analyzed by updateDataset()
        public XYSeries series;
    }

    public static class ComputeNode
    {
        public List<Path> paths = new ArrayList<Path> ();
        public int  lastStep;     // Index in paths. Presumably, this is the only file that might be partially processed.
        public long nextPosition; // Position in file where read should resume when more data arrives.
    }

    public RasterSTACS (Path path)
    {
        super (path);
        dataset     = this;
        timeQuantum = 1e-3;

        empty = new Column ();
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

                        //xmin = Math.min (xmin, time);
                        //xmax = Math.max (xmax, time);
                        //totalCount++;
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

        //timeQuantum = (xmax - xmin) * columns.size () / totalCount;
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

        listener.datasetChanged (new DatasetChangeEvent (this, this));
    }

    public int getSeriesCount ()
    {
        return columns.size ();
    }

    public Comparable<?> getSeriesKey (int series)
    {
        return series;
    }

    @SuppressWarnings("rawtypes")
    public int indexOf (Comparable seriesKey)
    {
        return (Integer) seriesKey;
    }

    public void addChangeListener (DatasetChangeListener listener)
    {
        this.listener = listener;
    }

    public void removeChangeListener (DatasetChangeListener listener)
    {
    }

    public DatasetGroup getGroup ()
    {
        return group;
    }

    public void setGroup (DatasetGroup group)
    {
        this.group = group;
    }

    public DomainOrder getDomainOrder ()
    {
        return DomainOrder.ASCENDING;
    }

    public int getItemCount (int series)
    {
        Column c = columns.get (series);
        if (c == null) return 0;
        return c.values.size ();
    }

    public Number getX (int series, int item)
    {
        return getXValue (series, item);
    }

    public double getXValue (int series, int item)
    {
        Column c = columns.get (series);
        if (c == null) return 0;
        return c.values.get (item);
    }

    public Number getY (int series, int item)
    {
        return getYValue (series, item);
    }

    public double getYValue (int series, int item)
    {
        Column c = columns.get (series);
        if (c == null) return 0;
        return c.index;
    }
}
