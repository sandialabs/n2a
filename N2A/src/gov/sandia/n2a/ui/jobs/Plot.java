/*
Copyright 2013-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.BasicStroke;
import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.DomainOrder;
import org.jfree.data.general.DatasetChangeEvent;
import org.jfree.data.general.DatasetChangeListener;
import org.jfree.data.general.DatasetGroup;
import org.jfree.data.xy.XYDataset;

public class Plot extends OutputParser
{
    protected Path         path;
    protected int          columnCount;
    protected JFDataset    dataset0 = new JFDataset ();
    protected JFDataset    dataset1;
    protected double       range0;
    protected double       range1;
    protected List<Column> left;  // dataset0
    protected List<Column> right; // dataset1

    public Plot (Path path)
    {
        this.path = path;
    }

    public void updateDatasets ()
    {
        parse (path);

        // Convert units
        for (Column c : columns)  // Includes time column, which can also be scaled.
        {
            if (c.data == null) c.data = 0;
            int count = c.values.size ();
            if (c.scale != null)
            {
                double scale = c.scale.get ();  // Conversion factor. Only works for simple scaling, not offset. For example, converting from degrees C to F does not work, while kilograms to pounds does.
                //if (! raw) c.header += "(" + c.scale + ")";
                if (scale != 1)
                {
                    for (int i = (Integer) c.data; i < count; i++)
                    {
                        Float value = c.values.get (i);
                        if (value.isInfinite ()  ||  value.isNaN ()) c.values.set (i, 0.0f);  // JFreeChart chokes on infinity (how to determine a vertical scale for that?)
                        else                                         c.values.set (i, (float) (value / scale));
                    }
                    c.data = count;  // Prevents the loop below from running.
                }
            }
            for (int i = (Integer) c.data; i < count; i++)
            {
                Float value = c.values.get (i);
                if (value.isInfinite ()  ||  value.isNaN ()) c.values.set (i, 0.0f);
            }
            c.data = count;
        }

        // Determine range of x axis
        if (! Double.isNaN (xmin)  ||  ! Double.isNaN (xmax))  // If either limit has been explicitly set ...
        {
            // then supply any missing data so that they are both set.
            time.computeStats ();
            if (Double.isNaN (xmin)) xmin = time.min;
            if (Double.isNaN (xmax)) xmax = time.max;
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
        boolean rangeLocked =  ! Double.isNaN (ymin)  ||  ! Double.isNaN (ymax);
        if (! rangeLocked)
        {
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
        }

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
                // Note that since c.range is zero, c.min==c.max and either contains the one and only value of c.
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

        dataset0.columns = left;
        dataset0.listener.datasetChanged (new DatasetChangeEvent (dataset0, dataset0));
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
    	for (Column c : left)
        {
            min = Math.min (min, c.min);
            max = Math.max (max, c.max);
        }
        if (rangeLocked)
        {
            if (Double.isNaN (ymin)) ymin = min;
            else                     min  = ymin;
            if (Double.isNaN (ymax)) ymax = max;
            else                     max  = ymax;
        }
    	range0 = max - min;

    	if (right == null)
    	{
    	    dataset1 = null;
    	}
    	else
    	{
    	    if (dataset1 == null) dataset1 = new JFDataset ();
    	    dataset1.columns = right;
            min = Double.POSITIVE_INFINITY;
            max = Double.NEGATIVE_INFINITY;
            for (Column c : right)
            {
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

        XYPlot plot = chart.getXYPlot ();
        plot.setBackgroundPaint     (Color.white);
        plot.setDomainGridlinePaint (Color.lightGray);
        plot.setRangeGridlinePaint  (Color.lightGray);
        plot.setDomainPannable (true);
        plot.setRangePannable  (true);

        updateChart (chart);
        return chart;
    }

    public void updateChart (JFreeChart chart)
    {
        XYPlot plot = chart.getXYPlot ();
        plot.setNotify (false);

        updateDatasets ();

        LegendTitle legend = chart.getLegend ();
        legend.setVisible (columnCount <= 10);

        NumberAxis axis0 = (NumberAxis) plot.getRangeAxis ();
        axis0.setAutoRangeIncludesZero (false);
        if (range0 > 0) axis0.setAutoRangeMinimumSize (range0 / 2);
        else            axis0.setAutoRangeMinimumSize (1);
        if (! Double.isNaN (ymin)) axis0.setRange (ymin, ymax);  // range locked

        ValueAxis x = plot.getDomainAxis ();
        if (! Double.isNaN (xmin))
        {
            x.setRange (xmin, xmax);
        }
        else if (duration > 0)
        {
            double max = duration;
            if (time.scale != null) max /= time.scale.get ();
            x.setRange (0, max);
        }

        int count = dataset0.getSeriesCount ();
        float shift = 0;
        if (dataset1 != null)
        {
            count *= 2;  // So we use only half of the color range
            shift = 0.75f + 0.5f / count;
        }

        XYLineAndShapeRenderer renderer;
        XYItemRenderer ir = plot.getRenderer ();
        if (ir instanceof XYLineAndShapeRenderer)
        {
            renderer = (XYLineAndShapeRenderer) ir;
        }
        else
        {
            renderer = new XYLineAndShapeRenderer ();
            plot.setRenderer (renderer);
        }
        for (int i = 0; i < dataset0.getSeriesCount (); i++)
        {
            Column column = left.get (i);
            styleSeries (renderer, i, column, count, shift);  // does not fire renderer change event
        }
        renderer.setDrawSeriesLineAsPath (true);  // fires renderer change event

        if (dataset1 == null)
        {
            axis0.setTickMarkPaint  (Color.black);
            axis0.setTickLabelPaint (Color.black);
            axis0.setAxisLinePaint  (Color.black);

            plot.setDataset   (1, null);
            plot.setRangeAxis (1, null);
            plot.setRenderer  (1, null);
        }
        else
        {
            Color color0 = Color.getHSBColor (0.0f, 1.0f, 0.8f);
            axis0.setTickMarkPaint  (color0);
            axis0.setTickLabelPaint (color0);
            axis0.setAxisLinePaint  (color0);

            plot.setDataset (1, dataset1);
            plot.mapDatasetToRangeAxis (1, 1);

            NumberAxis axis1 = (NumberAxis) plot.getRangeAxis (1);
            if (axis1 == null)
            {
                axis1 = new NumberAxis ();
                axis1.setAutoRangeIncludesZero (false);
                axis1.setTickLabelFont (axis0.getTickLabelFont ());
                Color color1 = Color.getHSBColor (0.5f, 1.0f, 0.8f);
                axis1.setTickMarkPaint  (color1);
                axis1.setTickLabelPaint (color1);
                axis1.setAxisLinePaint  (color1);
                plot.setRangeAxis (1, axis1);
            }
            // else we created axis1, so all the initial settings are correct
            if (range1 > 0) axis1.setAutoRangeMinimumSize (range1 / 2);
            else            axis1.setAutoRangeMinimumSize (1);

            count = dataset1.getSeriesCount () * 2;
            shift = 0.25f + 0.5f / count;

            ir = plot.getRenderer (1);
            if (ir instanceof XYLineAndShapeRenderer)
            {
                renderer = (XYLineAndShapeRenderer) ir;
            }
            else
            {
                renderer = new XYLineAndShapeRenderer ();
                plot.setRenderer (1, renderer);
            }
            for (int i = 0; i < dataset1.getSeriesCount (); i++)
            {
                Column column = right.get (i);
                styleSeries (renderer, i, column, count, shift);
            }
            renderer.setDrawSeriesLineAsPath (true);

            plot.setDatasetRenderingOrder (DatasetRenderingOrder.REVERSE);
        }

        plot.setNotify (true);
    }

    public void styleSeries (XYLineAndShapeRenderer renderer, int i, Column column, int count, float shift)
    {
        renderer.setSeriesShapesVisible (i, false);

        Color c = column.color;
        // TODO: avoid assigning a color close to any that the user explicitly specified for some other plot line.
        if (c == null) c = Color.getHSBColor ((float) i / count + shift, 1.0f, 0.9f);
        renderer.setSeriesPaint (i, c);
        renderer.setLegendTextPaint (i, c);

        if (column.width != 1.0f  ||  column.dash != null)
        {
            renderer.setSeriesStroke (i, new BasicStroke (column.width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, column.dash, 0), false);
        }
    }

    public class JFDataset implements XYDataset
    {
        protected List<Column>          columns;
        protected DatasetChangeListener listener;
        protected DatasetGroup          group;

        public int getSeriesCount ()
        {
            if (columns == null) return 0;
            return columns.size ();
        }

        public Comparable<?> getSeriesKey (int series)
        {
            Column c = columns.get (series);
            return c.header;
        }

        @SuppressWarnings("rawtypes")
        public int indexOf (Comparable seriesKey)
        {
            int count = columns.size ();
            for (int i = 0; i < count; i++) if (columns.get (i).header.equals (seriesKey)) return i;
            return -1;
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
            return c.values.size ();
        }

        public Number getX (int series, int item)
        {
            return getXValue (series, item);
        }

        public double getXValue (int series, int item)
        {
            Column c = columns.get (series);
            return time.values.get (item + c.startRow);  // assumes time.startRow == 0
        }

        public Number getY (int series, int item)
        {
            return getYValue (series, item);
        }

        public double getYValue (int series, int item)
        {
            Column c = columns.get (series);
            return c.values.get (item);
        }
    }
}
