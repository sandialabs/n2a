/*
Copyright 2013,2016 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Color;
import java.nio.file.Paths;

import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class Plot extends OutputParser
{
    public Plot (String path)
    {
    	parse (Paths.get (path));
    }

    public JPanel createGraphPanel ()
    {
        XYDataset dataset = createDataset ();
        JFreeChart chart = createChart (dataset);
        return new ChartPanelDrag (chart);
    }

    public XYDataset createDataset ()
    {
        XYSeriesCollection dataset = new XYSeriesCollection();
    	for (Column c : columns)
        {
        	if (c == time) continue;
        	if (isXycePRN  &&  c == columns.get (0)) continue;

        	XYSeries series = new XYSeries (c.header);
            for (int r = 0; r < c.values.size (); r++)
            {
                Double value = c.values.get (r);
                if (value.isInfinite ()) value = 0.0;  // JFreeChart chokes on infinity (how to determine a vertical scale for that?)
                series.add (time.values.get (r + c.startRow), value);
            }
            dataset.addSeries (series);
        }
        return dataset;
    }

    public JFreeChart createChart (final XYDataset dataset)
    {
        final JFreeChart chart = ChartFactory.createXYLineChart
        (
            null,                     // chart title
            null,                     // x axis label
            null,                     // y axis label
            dataset,                  // data
            PlotOrientation.VERTICAL,
            true,                     // include legend
            true,                     // tooltips
            false                     // urls
        );

        LegendTitle legend = chart.getLegend ();
        legend.setVisible (dataset.getSeriesCount () <= 5);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint     (Color.white);
        plot.setDomainGridlinePaint (Color.lightGray);
        plot.setRangeGridlinePaint  (Color.lightGray);
        plot.setDomainPannable (true);
        plot.setRangePannable  (true);
        ValueAxis axis = plot.getRangeAxis ();
        if (axis instanceof NumberAxis) ((NumberAxis) axis).setAutoRangeIncludesZero (false);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        for (int i = 0; i < dataset.getSeriesCount (); i++) renderer.setSeriesShapesVisible (i, false);
        plot.setRenderer(renderer);

        return chart;
    }
}
