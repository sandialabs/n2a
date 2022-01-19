/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Component;
import java.awt.EventQueue;
import java.nio.file.Path;

import javax.swing.JScrollPane;

import gov.sandia.n2a.ui.jobs.PanelRun.DisplayThread;

@SuppressWarnings("serial")
public class NodeSTACS extends NodeFile
{
    public NodeSTACS (Path path)
    {
        super (path);
        priority = 1;
        icon     = iconOut;
        setUserObject ("Output");
    }

    @Override
    public boolean couldHaveColumns ()
    {
        return false;
    }

    @Override
    public boolean isGraphable ()
    {
        return false;
    }

    @Override
    public boolean render (DisplayThread dt)
    {
        JScrollPane displayPane  = PanelRun.instance.displayPane;
        PanelChart  displayChart = PanelRun.instance.displayChart;
        if (dt.refresh)
        {
            Component current = displayPane.getViewport ().getView ();
            if (current == displayChart  &&  displayChart.source instanceof RasterSTACS)
            {
                ((Raster) displayChart.source).updateChart (displayChart.chart);
                displayChart.offscreen = true;
                dt.stop = true;
            }
        }
        if (! dt.stop)
        {
            RasterSTACS raster = new RasterSTACS (path);
            displayChart.setChart (raster.createChart (), raster);
            displayChart.offscreen = false;

            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    if (dt.stop) return;
                    displayChart.buttonBar.setVisible (true);
                    displayPane.setViewportView (displayChart);
                }
            });
        }
        return true;
    }
}
