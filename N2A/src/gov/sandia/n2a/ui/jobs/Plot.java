/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        // Convert units
        for (Column c : columns)  // Includes time column, which can also be scaled.
        {
            if (c.scale == null) continue;
            double scale = c.scale.get ();  // Conversion factor. Only works for simple scaling, not offset. For example, converting from degrees C to F would not work, but kilograms to pounds does work.
            //if (! raw) c.header += "(" + c.scale + ")";
            if (scale == 1) continue;
            int count = c.values.size ();
            for (int i = 0; i < count; i++) c.values.set (i, (float) (c.values.get (i) / scale));
        }

        // Decide between one or two axis display

        columnCount = columns.size () - 1;   // Subtract 1 to account for time column.
        Column[] sorted = new Column[columnCount];
        int i = 0;
        for (Column c : columns)
        {
            if (c == time) continue;
            c.computeStats ();
            if (raw) c.header = Integer.toString (i);
            sorted[i++] = c;
        }
        Arrays.sort (sorted, new ColumnComparator ());

        int bestIndex = -1;  // Breakpoint between left and right column sets. This is the index just before the separation.
        double largestRatio = 1;
        int flatCount = 0;
        for (i = 0; i < columnCount - 1; i++)
        {
            if (sorted[i].range == 0)
            {
                flatCount++;
                continue;
            }
            double ratio = sorted[i+1].range / sorted[i].range;
            if (ratio > largestRatio)
            {
                largestRatio = ratio;
                bestIndex = i;
            }
        }

        List<Column> left  = null;
        List<Column> right = null;
        if (bestIndex < 0  ||  largestRatio < 10)
        {
            left = Arrays.asList (sorted);
        }
        else
        {
            right             = new ArrayList<Column> (bestIndex + 1);  // Right side gets the smaller ranges, which come first in the sorted list.
            left              = new ArrayList<Column> (columnCount - bestIndex);
            List<Column> flat = new ArrayList<Column> (flatCount);

            double leftMin  = Double.POSITIVE_INFINITY;
            double leftMax  = Double.NEGATIVE_INFINITY;
            double rightMin = Double.POSITIVE_INFINITY;
            double rightMax = Double.NEGATIVE_INFINITY;

            for (i = 0; i <= bestIndex ; i++)
            {
                Column c = sorted[i];
                if (c.range == 0)
                {
                    flat.add (c);
                    continue;
                }
                right.add (c);
                rightMin = Math.min (rightMin, c.min);
                rightMax = Math.max (rightMax, c.max);
            }
            for (; i < columnCount; i++)
            {
                Column c = sorted[i];
                if (c.range == 0)
                {
                    flat.add (c);
                    continue;
                }
                left.add (c);
                leftMin = Math.min (leftMin, c.min);
                leftMax = Math.max (leftMax, c.max);
            }
            for (Column c : flat)
            {
                // Note that since c.range is zero, c.min==c.max and either one contains the one and only value of c.
                if (c.min >= leftMin  &&  c.min <= leftMax)
                {
                    left.add (c);
                }
                else if (c.min >= rightMin  &&  c.min <= rightMax)
                {
                    right.add (c);
                }
                else
                {
                    double leftRange  = leftMax  - leftMin;
                    double rightRange = rightMax - rightMin;
                    double leftDistance  = Math.min (Math.abs (c.min - leftMin ), Math.abs (c.min - leftMax ));
                    double rightDistance = Math.min (Math.abs (c.min - rightMin), Math.abs (c.min - rightMax));
                    if (leftRange  > 0) leftDistance  /= leftRange;
                    if (rightRange > 0) rightDistance /= rightRange;
                    if (leftDistance <= rightDistance) left .add (c);
                    else                               right.add (c);
                }
            }
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
                Float value = c.values.get (r);
                if (value.isInfinite ()  ||  value.isNaN ()) value = 0.0f;  // JFreeChart chokes on infinity (how to determine a vertical scale for that?)
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
                    Float value = c.values.get (r);
                    if (value.isInfinite ()  ||  value.isNaN ()) value = 0.0f;  // JFreeChart chokes on infinity (how to determine a vertical scale for that?)
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
        legend.setVisible (columnCount <= 10);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint     (Color.white);
        plot.setDomainGridlinePaint (Color.lightGray);
        plot.setRangeGridlinePaint  (Color.lightGray);
        plot.setDomainPannable (true);
        plot.setRangePannable  (true);

        NumberAxis axis0 = (NumberAxis) plot.getRangeAxis ();
        axis0.setAutoRangeIncludesZero (false);
        if (range0 > 0) axis0.setAutoRangeMinimumSize (range0 / 2);

        int count = dataset0.getSeriesCount ();
        float shift = 0;
        if (dataset1 != null)
        {
            count *= 3;  // So we use only a third of the color range
            shift = 1.0f - 1.0f / 6.0f;
        }

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        for (int i = 0; i < dataset0.getSeriesCount (); i++)
        {
            renderer.setSeriesShapesVisible (i, false);
            renderer.setSeriesPaint (i, Color.getHSBColor ((float) i / count + shift, 1.0f, 0.9f));
        }
        plot.setRenderer (renderer);

        if (dataset1 != null)
        {
            Color color0 = Color.getHSBColor (0.0f, 1.0f, 0.8f);
            axis0.setTickMarkPaint (color0);
            axis0.setTickLabelPaint (color0);
            axis0.setAxisLinePaint (color0);

            plot.setDataset (1, dataset1);
            plot.mapDatasetToRangeAxis (1, 1);

            NumberAxis axis1 = new NumberAxis ();
            axis1.setAutoRangeIncludesZero (false);
            if (range1 > 0) axis1.setAutoRangeMinimumSize (range1 / 2);
            axis1.setTickLabelFont (axis0.getTickLabelFont ());
            Color color1 = Color.getHSBColor (0.5f, 1.0f, 0.6f);
            axis1.setTickMarkPaint (color1);
            axis1.setTickLabelPaint (color1);
            axis1.setAxisLinePaint (color1);
            plot.setRangeAxis (1, axis1);

            count = dataset1.getSeriesCount () * 3;
            shift = 1.0f / 3.0f;

            renderer = new XYLineAndShapeRenderer();
            for (int i = 0; i < dataset1.getSeriesCount (); i++)
            {
                renderer.setSeriesShapesVisible (i, false);
                renderer.setSeriesPaint (i, Color.getHSBColor ((float) i / count + shift, 1.0f, 0.9f));
            }
            plot.setRenderer (1, renderer);
            plot.setDatasetRenderingOrder (DatasetRenderingOrder.REVERSE);
        }

        return chart;
    }
}
