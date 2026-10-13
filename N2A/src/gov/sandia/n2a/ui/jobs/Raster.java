/*
Copyright 2013-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.event.RendererChangeEvent;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYDotRenderer;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.data.DomainOrder;
import org.jfree.data.general.DatasetChangeEvent;
import org.jfree.data.general.DatasetChangeListener;
import org.jfree.data.general.DatasetGroup;
import org.jfree.data.xy.XYDataset;

/**
    Create a spike-raster plot.
**/
public class Raster extends OutputParser implements XYDataset
{
    protected Path                  path;
    protected List<Color>           colors      = new ArrayList<Color> ();  // correspond 1-to-1 with series added to dataset
    protected float                 timeQuantum = 1;  // In seconds, or if there are no time-stamps, rows. The closest spacing between two spikes on a single series.
    protected float                 currentTime;      // Of most recent spike event.
    protected long                  totalSpikes;      // Across all series.
    protected boolean               needXmin;         // indicates that xmin was not provided explicitly, and therefore should be calculated from data
    protected boolean               needXmax;         // ditto for xmax
    protected DatasetGroup          group;
    protected DatasetChangeListener listener;         // no need to keep a list, because it is always only our own chart
    protected int                   startRow;         // row index of first newly-added value during a refresh cycle

    public Raster (Path path)
    {
        this.path = path;
    }

    public void updateDataset ()
    {
        parse (path);
        assignSpikeIndices ();  // This won't change spike indices that have already been assigned, because column order remains constant.

        if (timeFound)
        {
            if (time.data == null)
            {
                time.data = 0;
                needXmin = Double.isNaN (xmin);
                needXmax = Double.isNaN (xmax);
            }
            int nextRow = (Integer) time.data;

            int count = time.values.size ();
            if (time.scale != null)
            {
                double scale = time.scale.get ();
                for (int i = nextRow; i < count; i++) time.values.set (i, (float) (time.values.get (i) / scale));
            }
            time.data = count;

            if (needXmin) xmin = time.values.get (0);
            if (needXmax) xmax = time.values.get (count - 1);
            int lastRow = nextRow - 1;
            if (lastRow < 0) lastRow = 0;
        }

        int columnCount = columns.size ();
        for (int i = colors.size (); i < columnCount; i++)
        {
            Column c = columns.get (i);
            if (c.color == null) colors.add (Color.black);
            else                 colors.add (c.color);
        }

        // Convert data to event times.
        // Take incoming spike position data and convert it to compact list of spike times.
        // This removes zero entries. Each column's start row is hacked so that the compacted
        // data all appears to stop at exactly at the end of current lock of incoming positional data.
        // This makes it ready to receive the next block.
        totalSpikes = 0;
        for (Column c : columns)
        {
            if (timeFound  &&  c == time) continue;

            int count = c.values.size ();
            int i = Math.max (0, startRow - c.startRow);  // Destination index for next converted value.
            float previousTime = count > 0 ? c.values.get (i) : Float.POSITIVE_INFINITY;
            for (int r = i; r < count; r++)
            {
                if (c.values.get (r) == 0) continue;
                int step = r + c.startRow;
                float t = timeFound ? time.values.get (step) : step;
                c.values.set (i++, t);
                if (t > previousTime) timeQuantum = Math.min (timeQuantum, t - previousTime);
                previousTime = t;
            }
            c.values.subList (i, count).clear ();  // "i" is effectively the new count
            c.startRow = rows - i;
            count = c.values.size ();
            totalSpikes += count;
            if (count > 0) currentTime = Math.max (currentTime, c.values.get (count - 1));
        }
        startRow = rows;
        listener.datasetChanged (new DatasetChangeEvent (this, this));
    }

    public JFreeChart createChart ()
    {
        JFreeChart chart = ChartFactory.createScatterPlot
        (
            null,                     // chart title
            null,                     // x axis label
            null,                     // y axis label
            this,                     // data
            PlotOrientation.VERTICAL,
            false,                    // include legend
            true,                     // tooltips
            false                     // urls
        );

        XYPlot plot = chart.getXYPlot ();
        plot.setBackgroundPaint    (Color.white);
        plot.setRangeGridlinePaint (Color.lightGray);
        plot.setDomainPannable (true);
        plot.setRangePannable  (true);
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis ();
        rangeAxis.setStandardTickUnits (NumberAxis.createIntegerTickUnits ());  // Integer units only
        TickRenderer renderer = new TickRenderer ();
        plot.setRenderer (renderer);

        updateChart (chart);
        return chart;
    }

    public void updateChart (JFreeChart chart)
    {
        XYPlot plot = chart.getXYPlot ();
        plot.setNotify (false);

        int newRow = colors.size ();
        updateDataset ();
        int count = colors.size ();
        if (newRow < count)
        {
            TickRenderer tr = (TickRenderer) plot.getRenderer ();
            for (int i = newRow; i < count; i++) tr.setSeriesPaint (i, colors.get (i), false);
            tr.notifyListeners (new RendererChangeEvent (tr));  // This is the same event as issued by setSeriesPaint() when notify is true.
        }

        ValueAxis x = plot.getDomainAxis ();
        if (needXmin  &&  needXmax  &&  duration > 0)
        {
            double max = duration;
            if (time.scale != null) max /= time.scale.get ();
            x.setRange (0, max);
        }
        else if (xmin < xmax)
        {
            x.setRange (xmin, xmax);
        }

        plot.setNotify (true);
    }

    @SuppressWarnings("serial")
    public class TickRenderer extends XYDotRenderer
    {
        public XYItemRendererState initialise (Graphics2D g2, Rectangle2D dataArea, XYPlot plot, XYDataset dataset, PlotRenderingInfo info)
        {
            Rectangle clipBounds = g2.getClipBounds ();

            double rasterLines = plot.getRangeAxis ().getRange ().getLength ();
            int    height      = (int) Math.ceil (clipBounds.height / rasterLines);
            if      (height > 10) height -= 2;
            else if (height > 2 ) height -= 1;
            height = Math.min (height, 20);
            height = Math.max (height, 1);

            double timeSteps = plot.getDomainAxis ().getRange ().getLength () / timeQuantum;
            int    width     = (int) Math.ceil (clipBounds.width / timeSteps);
            if      (width > 10) width -= 2;
            else if (width > 2 ) width -= 1;
            if (height >= 6) width = Math.min (width, height / 2);  // Enforce rectangular ticks at larger sizes. Looks better.
            else             width = Math.min (width, 3);  // When there's a lot of horizontal space, but not much vertical space, don't go crazy on how wide the ticks get.
            width = Math.max (width, 1);

            setDotHeight (height);
            setDotWidth (width);

            return super.initialise (g2, dataArea, plot, dataset, info);
        }
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
        if (timeFound  &&  c == time) return 0;
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
