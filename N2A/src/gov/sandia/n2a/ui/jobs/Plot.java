/*
Copyright 2013-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Color;
import java.nio.file.Path;
import java.util.Arrays;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class Plot extends OutputParser
{
    protected int                columnCount;
    protected XYSeriesCollection dataset0;
    protected XYSeriesCollection dataset1;
    protected double             range0;
    protected double             range1;

    public Plot (Path path)
    {
    	parse (path);
        createDatasets ();
    }

    public void createDatasets ()
    {
        // Decide between one or two axis display

        columnCount = columns.size () - 1;   // Subtract 1 to account for time column.
        if (isXycePRN  &&  time != columns.get (0)) columnCount--;  // Account for Xyce step column.

        Column[] sorted = new Column[columnCount];
        int i = 0;
        for (Column c : columns)
        {
            if (c == time) continue;
            if (isXycePRN  &&  c == columns.get (0)) continue;

            c.computeStats ();
            sorted[i++] = c;
        }
        Arrays.sort (sorted, new ColumnComparator ());

        int bestIndex = -1;
        double largestRatio = 1;
        for (i = 0; i < columnCount - 1; i++)
        {
            if (sorted[i].range == 0) continue;
            double ratio = sorted[i+1].range / sorted[i].range;
            if (ratio > largestRatio)
            {
                largestRatio = ratio;
                bestIndex = i;
            }
        }

        Column[] left  = sorted;
        Column[] right = null;
        if (bestIndex >= 0  &&  largestRatio >= 10)
        {
            int rightCount = bestIndex + 1;
            right = new Column[rightCount];
            left  = new Column[columnCount - rightCount];
            for (i = 0; i < rightCount ; i++) right[i             ] = sorted[i];
            for (     ; i < columnCount; i++) left [i - rightCount] = sorted[i];
        }

        // Generate data series

        dataset0 = new XYSeriesCollection();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
    	for (Column c : left)
        {
        	XYSeries series = new XYSeries (c.header);
            for (int r = 0; r < c.values.size (); r++)
            {
                Double value = c.values.get (r);
                if (value.isInfinite ()  ||  value.isNaN ()) value = 0.0;  // JFreeChart chokes on infinity (how to determine a vertical scale for that?)
                series.add (time.values.get (r + c.startRow), value);
            }
            dataset0.addSeries (series);
            min = Math.min (min, c.min);
            max = Math.max (max, c.max);
        }
    	range0 = max - min;

    	if (right != null)
    	{
            dataset1 = new XYSeriesCollection();
            min = Double.POSITIVE_INFINITY;
            max = Double.NEGATIVE_INFINITY;
            for (Column c : right)
            {
                XYSeries series = new XYSeries (c.header);
                for (int r = 0; r < c.values.size (); r++)
                {
                    Double value = c.values.get (r);
                    if (value.isInfinite ()  ||  value.isNaN ()) value = 0.0;  // JFreeChart chokes on infinity (how to determine a vertical scale for that?)
                    series.add (time.values.get (r + c.startRow), value);
                }
                dataset1.addSeries (series);
                min = Math.min (min, c.min);
                max = Math.max (max, c.max);
            }
            range1 = max - min;
    	}
    }

    public JFreeChart createChart ()
    {
        JFreeChart chart = ChartFactory.createXYLineChart
        (
            null,                     // chart title
            null,                     // x axis label
            null,                     // y axis label
            dataset0,                 // data
            PlotOrientation.VERTICAL,
            true,                     // include legend
            true,                     // tooltips
            false                     // urls
        );

        LegendTitle legend = chart.getLegend ();
        legend.setVisible (columnCount <= 5);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint     (Color.white);
        plot.setDomainGridlinePaint (Color.lightGray);
        plot.setRangeGridlinePaint  (Color.lightGray);
        plot.setDomainPannable (true);
        plot.setRangePannable  (true);
        NumberAxis axis0 = (NumberAxis) plot.getRangeAxis ();
        axis0.setAutoRangeIncludesZero (false);
        if (range0 > 0) axis0.setAutoRangeMinimumSize (range0 / 2);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        for (int i = 0; i < dataset0.getSeriesCount (); i++) renderer.setSeriesShapesVisible (i, false);
        plot.setRenderer (renderer);

        if (dataset1 != null)
        {
            plot.setDataset (1, dataset1);
            plot.mapDatasetToRangeAxis (1, 1);

            NumberAxis axis1 = new NumberAxis ();
            axis1.setAutoRangeIncludesZero (false);
            if (range1 > 0) axis1.setAutoRangeMinimumSize (range1 / 2);
            axis1.setTickLabelFont (axis0.getTickLabelFont ());
            plot.setRangeAxis (1, axis1);

            renderer = new XYLineAndShapeRenderer();
            for (int i = 0; i < dataset1.getSeriesCount (); i++) renderer.setSeriesShapesVisible (i, false);
            plot.setRenderer (1, renderer);
            plot.setDatasetRenderingOrder (DatasetRenderingOrder.REVERSE);
        }

        return chart;
    }
}
