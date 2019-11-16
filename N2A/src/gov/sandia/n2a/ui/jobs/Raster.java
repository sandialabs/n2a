/*
Copyright 2013-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.nio.file.Path;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
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
    public XYSeriesCollection dataset;

    public Raster (Path path)
    {
    	parse (path);
        createDataset ();
    }

    public void createDataset ()
    {
        dataset = new XYSeriesCollection ();
        XYSeries series = new XYSeries ("Spikes");
        dataset.addSeries (series);

        // Convert column indices.
        if (raw)
        {
            int i = 0;
            for (Column c : columns)
            {
                if (! timeFound  ||  c != time) c.index = i++;
            }
        }
        else
        {
            int nextColumn = -1;
            for (Column c : columns)
            {
                try
                {
                    c.index = Integer.parseInt (c.header);
                }
                catch (NumberFormatException e)
                {
                    c.index = nextColumn--;
                }
            }
        }

        // Generate dateset
        for (Column c : columns)
        {
            if (timeFound)
            {
                if (c == time) continue;
                for (int r = 0; r < c.values.size (); r++)
                {
                    if (c.values.get (r) != 0) series.add (time.values.get (r + c.startRow).doubleValue (), c.index);
                }
            }
            else
            {
                for (int r = 0; r < c.values.size (); r++)
                {
                    if (c.values.get (r) != 0) series.add (r + c.startRow, c.index);
                }
            }
        }
    }

    public JFreeChart createChart ()
    {
        final JFreeChart chart = ChartFactory.createScatterPlot
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
        plot.setRenderer (new TickRenderer ());

        return chart;
    }

    @SuppressWarnings("serial")
    public static class TickRenderer extends XYDotRenderer
    {
        public TickRenderer ()
        {
            setDotWidth (1);
        }

        public XYItemRendererState initialise (Graphics2D g2, Rectangle2D dataArea, XYPlot plot, XYDataset dataset, PlotRenderingInfo info)
        {
            double rasterLines = plot.getRangeAxis ().getRange ().getLength ();
            int    pixels      = g2.getClipBounds ().height;
            double height = pixels / rasterLines;
            if      (height > 10) height -= 2;
            else if (height > 2 ) height -= 1;
            setDotHeight ((int) Math.min (20, Math.max (1, Math.floor (height))));

            return super.initialise (g2, dataArea, plot, dataset, info);
        }
    }
}
