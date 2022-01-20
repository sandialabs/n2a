/*
Copyright 2013-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
    Create a spike-raster plot.
**/
public class Raster extends OutputParser
{
    protected Path        path;
    protected XYDataset   dataset;
    protected List<Color> colors      = new ArrayList<Color> ();  // correspond 1-to-1 with series added to dataset
    protected double      timeQuantum = 1;  // The closest spacing between two spikes on a single row.
    protected boolean     needXmin;  // indicates that xmin was not provided explicitly, and therefore should be calculated from data
    protected boolean     needXmax;  // ditto for xmax

    public static final Color red = Color.getHSBColor (0.0f, 1.0f, 0.8f);

    public Raster (Path path)
    {
        this.path = path;
    }

    public void updateDataset ()
    {
        parse (path);
        assignSpikeIndices ();  // This won't change spike indices that have already been assigned, because column order remains constant.

        int totalCount = 0;
        for (Column c : columns) if (! timeFound  ||  c != time) totalCount += c.values.size ();

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
            double minTimeQuantum = (xmax - xmin) / totalCount;
            int lastRow = nextRow - 1;
            if (lastRow < 0) lastRow = 0;
            double previousTime = time.values.get (lastRow);
            for (int r = lastRow + 1; r < count; r++)
            {
                double thisTime = time.values.get (r);
                double diff = thisTime - previousTime;
                // If diff is less than minTimeQuantum, it could be due to jittering for "before" or "after" event delivery.
                if (diff >= minTimeQuantum) timeQuantum = Math.min (timeQuantum, diff);
                previousTime = thisTime;
            }

            time.data = count;
        }

        // Generate dataset
        for (Column c : columns)
        {
            if (timeFound  &&  c == time) continue;

            XYSeries series;
            int newRow;
            if (c.data == null)
            {
                series = new XYSeries (c.header);
                newRow = 0;
            }
            else
            {
                series = (XYSeries) c.data;
                newRow = series.getItemCount ();
            }
            int count = c.values.size ();

            if (timeFound)
            {
                for (int r = newRow; r < count; r++)
                {
                    // This assumes that time.startRow == 0
                    if (c.values.get (r) != 0) series.add (time.values.get (r + c.startRow).doubleValue (), c.index, false);
                }
            }
            else
            {
                for (int r = newRow; r < count; r++)
                {
                    if (c.values.get (r) != 0) series.add (r + c.startRow, c.index, false);
                }
            }

            if (c.data == null)
            {
                ((XYSeriesCollection) dataset).addSeries (series);
                c.data = series;

                if (c.color == null) colors.add (red);
                else                 colors.add (c.color);
            }
            else
            {
                series.fireSeriesChanged ();
            }
        }
    }

    public JFreeChart createChart ()
    {
        if (dataset == null) dataset = new XYSeriesCollection ();

        JFreeChart chart = ChartFactory.createScatterPlot
        (
            null,                     // chart title
            null,                     // x axis label
            null,                     // y axis label
            dataset,                  // data
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

        if (! Double.isNaN (xmin))
        {
            ValueAxis x = plot.getDomainAxis ();
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
}
