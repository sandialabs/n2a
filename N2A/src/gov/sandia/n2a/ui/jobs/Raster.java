/*
Copyright 2013-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
    protected double                timeQuantum = 1;  // The closest spacing between two spikes on a single row.
    protected boolean               needXmin;  // indicates that xmin was not provided explicitly, and therefore should be calculated from data
    protected boolean               needXmax;  // ditto for xmax
    protected DatasetGroup          group;
    protected DatasetChangeListener listener;  // no need to keep a list, because it is always only our own chart
    protected int                   startRow;  // row index of first newly-added value during a refresh cycle

    public static final Color red = Color.getHSBColor (0.0f, 1.0f, 0.8f);

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

            if (needXmin) xmin = time.values.get (0);
            if (needXmax) xmax = time.values.get (count - 1);
            int lastRow = nextRow - 1;
            if (lastRow < 0) lastRow = 0;
            double previousTime = time.values.get (lastRow);
            for (int r = lastRow + 1; r < count; r++)
            {
                double thisTime = time.values.get (r);
                timeQuantum = Math.min (timeQuantum, thisTime - previousTime);
                previousTime = thisTime;
            }

            time.data = count;
        }

        int columnCount = columns.size ();
        for (int i = colors.size (); i < columnCount; i++)
        {
            Column c = columns.get (i);
            if (c.color == null) colors.add (red);
            else                 colors.add (c.color);
        }

        // Convert data to event times
        for (Column c : columns)
        {
            if (timeFound  &&  c == time) continue;

            int count = c.values.size ();
            int i = Math.max (0, startRow - c.startRow);  // Destination index for next converted value.
            for (int r = i; r < count; r++)
            {
                if (c.values.get (r) == 0) continue;
                int step = r + c.startRow;
                float t = timeFound ? time.values.get (step) : step;
                c.values.set (i++, t);
            }
            c.values.subList (i, count).clear ();  // "i" is effectively the new count
            c.startRow = rows - i;
        }
        startRow = rows;
        listener.datasetChanged (new DatasetChangeEvent (this, this));

        // Lower limit on size of timeQuantum
        // Small timesteps could be due to jittering for "before" or "after" event delivery.
        int totalCount = 0;
        for (Column c : columns) if (! timeFound  ||  c != time) totalCount += c.values.size ();
        double minTimeQuantum = (xmax - xmin) / totalCount;
        timeQuantum = Math.max (timeQuantum, minTimeQuantum);
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
            int    height      = (int) Math.floor (clipBounds.height / rasterLines);
            if      (height > 10) height -= 2;
            else if (height > 2 ) height -= 1;
            height = Math.min (20, height);
            height = Math.max (1,  height);
            setDotHeight ((int) height);

            double timeSteps = plot.getDomainAxis ().getRange ().getLength () / timeQuantum + 1;
            int    width     = (int) Math.floor (clipBounds.width / timeSteps);
            width = Math.min (height / 2, width);
            width = Math.max (1,          width);
            setDotWidth ((int) width);

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
