/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import replete.gui.fc.CommonFileChooser;
import replete.gui.fc.FilterBuilder;
import replete.gui.windows.EscapeFrame;
import replete.util.FileUtil;
import replete.util.Lay;

public class XycePlotPrnFile extends EscapeFrame {

    JPanel pnlCurrent;

    File[] f = null;

    public XycePlotPrnFile() {
        super("Xyce PRN File Plot");
        JButton btnChoose = new JButton("Select PRN File...");
        btnChoose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                File startDir = new File(System.getProperty("user.home"), ".n2a_jobs");
                final CommonFileChooser chooser = CommonFileChooser.getChooser("Select PRN File");
                FilterBuilder builder = new FilterBuilder(chooser, true);
                builder.append("Xyce PRN Files (*.prn)", false, "prn");
                chooser.setCurrentDirectory(startDir);
                if(chooser.showOpen(XycePlotPrnFile.this)) {
                    waitOn();
                    new Thread() {
                        @Override
                        public void run() {
                            System.out.println("starting parsing");
                            PrnFile prnFile = parsePrnFile(chooser.getSelectedFile());
                            XycePlotPrnFile.this.remove(pnlCurrent);
                            pnlCurrent = createGraphPanel(prnFile);
                            XycePlotPrnFile.this.add(pnlCurrent, BorderLayout.CENTER);
                            pnlCurrent.updateUI();
                            System.out.println("done");
                            waitOff();
                            // TODO: Derek give the run config more memory.
                            // check to see if this thread thing with timer still
                            // works with the simple ones.
                        }
                    }.start();
                }
            }
        });
        Lay.BLtg(this,
            "N", btnChoose,
            "C", pnlCurrent = Lay.p(),
            "bg=white,augb=mb(1,black)"
        );
        setSize(500,500);
        setLocationRelativeTo(null);
    }

    private PrnFile parsePrnFile(File f) {
        PrnFile prnFile = new PrnFile();
        prnFile.outputs = new ArrayList<double[]>();
        prnFile.time = new ArrayList<Double>();
        String s = FileUtil.getTextContent(f);
        String[] lines = s.split("\n");
        for(String line : lines) {
            if(line.startsWith("Index")) {
                String[] parts = line.split("\\s+");
                prnFile.cols = parts.length - 2;
                prnFile.headers = new ArrayList<String>();
                for(int p = 2; p < parts.length; p++) {
                    prnFile.headers.add(parts[p]);
                }
            } else if(line.startsWith("End of")) {
                // Nothing
            } else {
                String[] parts = line.split("\\s+");
                double[] values = new double[prnFile.cols];
                prnFile.time.add(Double.parseDouble(parts[1]));
                for(int p = 2; p < parts.length; p++) {
                    values[p - 2] = Double.parseDouble(parts[p]);
                }
                prnFile.outputs.add(values);
            }
        }
        return prnFile;
    }

    public static void main(String[] args) {
        XycePlotPrnFile frame = new XycePlotPrnFile();
        frame.setVisible(true);
    }

    private JPanel createGraphPanel(PrnFile prnFile) {
        XYDataset dataset = createDataset(prnFile);
        JFreeChart chart = createChart(dataset);
        ChartPanel chartPanel = new ChartPanel(chart);
        return chartPanel;
    }

    private XYDataset createDataset(PrnFile prnFile) {
        XYSeriesCollection dataset = new XYSeriesCollection();
        for(int c = 0; c < prnFile.cols; c++) {
            XYSeries series = new XYSeries(prnFile.headers.get(c));
            int r = 0;
            for(double[] row : prnFile.outputs) {
                series.add(prnFile.time.get(r++).doubleValue(), row[c]);
            }
            dataset.addSeries(series);
        }
        return dataset;
    }

    private JFreeChart createChart(final XYDataset dataset) {

        // create the chart...
        final JFreeChart chart = ChartFactory.createXYLineChart(
            null,      // chart title
            "Time",                      // x axis label
            "Voltage",                      // y axis label
            dataset,                  // data
            PlotOrientation.VERTICAL,
            true,                     // include legend
            true,                     // tooltips
            false                     // urls
        );

        // NOW DO SOME OPTIONAL CUSTOMISATION OF THE CHART...
        chart.setBackgroundPaint(Color.white);

//        final StandardLegend legend = (StandardLegend) chart.getLegend();
//        legend.setDisplaySeriesShapes(true);

        // get a reference to the plot for further customisation...
        final XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.lightGray);
    //    plot.setAxisOffset(new Spacer(Spacer.ABSOLUTE, 5.0, 5.0, 5.0, 5.0));
        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinePaint(Color.white);

        final XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesShapesVisible(0, false);
        renderer.setSeriesShapesVisible(1, false);
        plot.setRenderer(renderer);

        // change the auto tick unit selection to integer units only...
        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        // OPTIONAL CUSTOMISATION COMPLETED.

        return chart;

    }

    private static class PrnFile {
        public int cols;
        public List<String> headers;
        public List<double[]> outputs;
        public List<Double> time;
    }
}
