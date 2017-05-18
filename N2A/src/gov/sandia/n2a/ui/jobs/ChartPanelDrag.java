/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.Pannable;

public class ChartPanelDrag extends org.jfree.chart.ChartPanel
{
    public ChartPanelDrag (JFreeChart chart)
    {
        super (chart);
        setMouseWheelEnabled (true);
    }

    @Override
    public void mousePressed (MouseEvent e)
    {
        if (chart == null) return;
        org.jfree.chart.plot.Plot plot = chart.getPlot ();
        if (e.getButton () == MouseEvent.BUTTON1)
        {
            // can we pan this plot?
            if (plot instanceof Pannable)
            {
                Pannable pannable = (Pannable) plot;
                if (pannable.isDomainPannable ()  ||  pannable.isRangePannable ())
                {
                    Rectangle2D screenDataArea = getScreenDataArea (e.getX (), e.getY ());
                    if (screenDataArea != null  &&  screenDataArea.contains (e.getPoint ()))
                    {
                        panW = screenDataArea.getWidth ();
                        panH = screenDataArea.getHeight ();
                        panLast = e.getPoint ();
                        setCursor (Cursor.getPredefinedCursor (Cursor.MOVE_CURSOR));
                    }
                }
            }
        }
        else if (zoomRectangle == null)
        {
            Rectangle2D screenDataArea = getScreenDataArea (e.getX (), e.getY ());
            if (screenDataArea != null) zoomPoint = getPointInRectangle (e.getX (), e.getY (), screenDataArea);
            else                        zoomPoint = null;
            if (e.isPopupTrigger ()  &&  popup != null) displayPopupMenu (e.getX (), e.getY ());
        }
    }
}
