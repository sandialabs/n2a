/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.MouseInputAdapter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.ChartTransferable;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.editor.ChartEditor;
import org.jfree.chart.editor.ChartEditorManager;
import org.jfree.chart.event.ChartChangeEvent;
import org.jfree.chart.event.ChartChangeListener;
import org.jfree.chart.event.ChartProgressEvent;
import org.jfree.chart.event.ChartProgressListener;
import org.jfree.chart.plot.Pannable;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.Zoomable;

import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.images.ImageUtil;


@SuppressWarnings("serial")
public class PanelChart extends JPanel implements ChartChangeListener, ChartProgressListener, Printable
{
    protected JFreeChart         chart;
    protected Plot               plot;
    protected ChartRenderingInfo info;

    protected BufferedImage buffer;
    protected DrawThread    drawThread;

    protected Point       zoomPoint;
    protected Rectangle2D zoomRectangle;
    protected Paint       zoomFillPaint = new Color (0, 0, 255, 63);  // blue with transparency

    protected JPanel buttonBar;

    public PanelChart ()
    {
        setOpaque (false);

        MouseInputAdapter mouseListener = new PanelChartMouseListener ();
        addMouseListener       (mouseListener);
        addMouseMotionListener (mouseListener);

        JButton buttonProperties = new JButton (ImageUtil.getImage ("properties.gif"));
        buttonProperties.setMargin (new Insets (2, 2, 2, 2));
        buttonProperties.setFocusable (false);
        buttonProperties.setToolTipText ("Properties");
        buttonProperties.addActionListener (actionProperties);

        JButton buttonCopy = new JButton (ImageUtil.getImage ("copy.png"));
        buttonCopy.setMargin (new Insets (2, 2, 2, 2));
        buttonCopy.setFocusable (false);
        buttonCopy.setToolTipText ("Copy");
        buttonCopy.addActionListener (actionCopy);

        JButton buttonSave = new JButton (ImageUtil.getImage ("save.gif"));
        buttonSave.setMargin (new Insets (2, 2, 2, 2));
        buttonSave.setFocusable (false);
        buttonSave.setToolTipText ("Save");
        buttonSave.addActionListener (actionSave);

        JButton buttonPrint = new JButton (ImageUtil.getImage ("print.png"));
        buttonPrint.setMargin (new Insets (2, 2, 2, 2));
        buttonPrint.setFocusable (false);
        buttonPrint.setToolTipText ("Print");
        buttonPrint.addActionListener (actionPrint);

        JButton buttonReset = new JButton (ImageUtil.getImage ("zoom_reset.png"));
        buttonReset.setMargin (new Insets (2, 2, 2, 2));
        buttonReset.setFocusable (false);
        buttonReset.setToolTipText ("Reset Zoom");
        buttonReset.addActionListener (actionReset);

        buttonBar = Lay.FL ("L", "hgap=5,vgap=1",
            buttonProperties,
            buttonCopy,
            buttonSave,
            buttonPrint,
            buttonReset,
            Box.createHorizontalStrut (15)
        );
    }

    /**
        @param newChart If null, then releases current chart so it can be garbage collected. However, does not
        put this panel in a state where it can be displayed. When this panel is visible, it must always have a valid chart.
    **/
    public void setChart (JFreeChart newChart)
    {
        if (chart != null)
        {
            chart.removeChangeListener (this);
            chart.removeProgressListener (this);
        }
        chart = newChart;
        if (drawThread != null) drawThread.stop = true;
        drawThread = null;
        buffer = null;
        if (chart == null)
        {
            plot = null;
            info = null;
            return;
        }

        plot = chart.getPlot ();
        info = new ChartRenderingInfo ();

        chart.addChangeListener (this);
        chart.addProgressListener (this);
    }

    public class PanelChartMouseListener extends MouseInputAdapter
    {
        public double panW;
        public double panH;
        public Point  panLast;

        public void mousePressed (MouseEvent e)
        {
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
                if (! (plot instanceof Zoomable)) return;  // Don't even initiate a zoom region unless it can have an effect. This reduces need to check for Zoomable elsewhere.

                int x = e.getX ();
                int y = e.getY ();
                Rectangle area = getScreenDataArea (x, y);
                if (area == null)
                {
                    zoomPoint = null;
                }
                else
                {
                    zoomPoint = new Point ();
                    zoomPoint.x = (int) Math.max (area.getMinX (), Math.min (x, area.getMaxX ()));
                    zoomPoint.y = (int) Math.max (area.getMinY (), Math.min (y, area.getMaxY ()));
                }
            }
        }

        public void mouseDragged (MouseEvent e)
        {
            // Handle pan, if active
            if (panLast != null)
            {
                double dx = e.getX () - panLast.getX();
                double dy = e.getY () - panLast.getY();
                if (dx == 0  &&  dy == 0) return;

                double wPercent = -dx / panW;
                double hPercent =  dy / panH;

                Pannable p = (Pannable) plot;
                plot.setNotify (false);
                if (p.getOrientation () == PlotOrientation.VERTICAL)
                {
                    p.panDomainAxes (wPercent, info.getPlotInfo (), panLast);
                    p.panRangeAxes  (hPercent, info.getPlotInfo (), panLast);
                }
                else
                {
                    p.panDomainAxes (hPercent, info.getPlotInfo (), panLast);
                    p.panRangeAxes  (wPercent, info.getPlotInfo (), panLast);
                }
                plot.setNotify (true);
                panLast = e.getPoint ();
                return;
            }

            // Handle zoom, if active
            if (zoomPoint == null) return;

            Zoomable z = (Zoomable) plot;
            boolean hZoom;
            boolean vZoom;
            if (z.getOrientation () == PlotOrientation.HORIZONTAL)
            {
                hZoom = z.isRangeZoomable ();
                vZoom = z.isDomainZoomable ();
            }
            else
            {
                hZoom = z.isDomainZoomable ();
                vZoom = z.isRangeZoomable ();
            }

            Rectangle2D nextZoom = null;
            double x = zoomPoint.getX ();
            double y = zoomPoint.getY ();
            Rectangle2D dataArea = getScreenDataArea ((int) x, (int) y);
            double xmax = Math.min (e.getX (), dataArea.getMaxX ());
            double ymax = Math.min (e.getY (), dataArea.getMaxY ());
            if (hZoom && vZoom)
            {
                nextZoom = new Rectangle2D.Double (x, y, xmax - x, ymax - y);
            }
            else if (hZoom)
            {
                nextZoom = new Rectangle2D.Double (x, dataArea.getMinY (), xmax - x, dataArea.getHeight ());
            }
            else if (vZoom)
            {
                nextZoom = new Rectangle2D.Double (dataArea.getMinX(), y, dataArea.getWidth (), ymax - y);
            }

            // Update using XOR method
            Graphics2D g2 = (Graphics2D) getGraphics ();
            drawZoomRectangle (g2, true);
            zoomRectangle = nextZoom;
            drawZoomRectangle (g2, true);
            g2.dispose ();
        }

        public void mouseReleased (MouseEvent e)
        {
            if (panLast != null)
            {
                panLast = null;
                setCursor (Cursor.getDefaultCursor ());
            }
            else if (zoomRectangle != null)
            {
                Zoomable z = (Zoomable) plot;
                boolean hZoom;
                boolean vZoom;
                if (z.getOrientation () == PlotOrientation.HORIZONTAL)
                {
                    hZoom = z.isRangeZoomable ();
                    vZoom = z.isDomainZoomable ();
                }
                else
                {
                    hZoom = z.isDomainZoomable ();
                    vZoom = z.isRangeZoomable ();
                }

                int ex = e.getX ();
                int ey = e.getY ();
                int zx = zoomPoint.x;
                int zy = zoomPoint.y;
                boolean zoomTrigger1 = hZoom  &&  Math.abs (ex - zx) >= 10;
                boolean zoomTrigger2 = vZoom  &&  Math.abs (ey - zy) >= 10;
                if (zoomTrigger1  ||  zoomTrigger2)
                {
                    if ((hZoom  &&  ex < zx)  ||  (vZoom  &&  (ey < zy)))
                    {
                        resetZoom ();
                    }
                    else
                    {
                        Rectangle dataArea = getScreenDataArea (zx, zy);
                        double maxX = dataArea.getMaxX ();
                        double maxY = dataArea.getMaxY ();
                        int w = (int) Math.min (zoomRectangle.getWidth (),  maxX - zx);
                        int h = (int) Math.min (zoomRectangle.getHeight (), maxY - zy);
                        // At least one of vZoom and hZoom must be true.
                        if (! vZoom)  // hZoom only
                        {
                            zy = (int) dataArea.getMinY ();
                            h  = (int) dataArea.getHeight ();
                        }
                        else if (! hZoom)  // vZoom only
                        {
                            zx = (int) dataArea.getMinX ();
                            w  = (int) dataArea.getWidth ();
                        }
                        Rectangle selection = new Rectangle (zx, zy, w, h);

                        if (selection.getHeight () > 0  &&  selection.getWidth () > 0)
                        {
                            Point2D selectOrigin = translateScreenToJava2D (selection.getLocation ());
                            PlotRenderingInfo plotInfo = info.getPlotInfo ();

                            double hLower = (selection.getMinX () - dataArea.getMinX ()) / dataArea.getWidth ();
                            double hUpper = (selection.getMaxX () - dataArea.getMinX ()) / dataArea.getWidth ();
                            double vLower = (dataArea.getMaxY () - selection.getMaxY ()) / dataArea.getHeight ();
                            double vUpper = (dataArea.getMaxY () - selection.getMinY ()) / dataArea.getHeight ();

                            plot.setNotify (false);
                            if (z.getOrientation () == PlotOrientation.HORIZONTAL)
                            {
                                z.zoomDomainAxes (vLower, vUpper, plotInfo, selectOrigin);
                                z.zoomRangeAxes  (hLower, hUpper, plotInfo, selectOrigin);
                            }
                            else
                            {
                                z.zoomDomainAxes (hLower, hUpper, plotInfo, selectOrigin);
                                z.zoomRangeAxes  (vLower, vUpper, plotInfo, selectOrigin);
                            }
                            plot.setNotify (true);
                        }
                    }
                }
                else
                {
                    // erase the zoom rectangle
                    Graphics2D g2 = (Graphics2D) getGraphics ();
                    drawZoomRectangle (g2, true);
                    g2.dispose();
                }
                zoomPoint = null;
                zoomRectangle = null;
            }
        }

        public void mouseWheelMoved (MouseWheelEvent e)
        {
            if (! (plot instanceof Zoomable)) return;
            Zoomable z = (Zoomable) plot;

            boolean domain = true;
            boolean range  = true;
            boolean shift   = e.isShiftDown ();
            boolean control = e.isControlDown ();
            if (shift  ||  control)
            {
                if (z.getOrientation () == PlotOrientation.HORIZONTAL)
                {
                    domain = shift;
                    range  = control;
                }
                else
                {
                    domain = control;
                    range  = shift;
                }
            }
            domain = domain  &&  z.isDomainZoomable ();
            range  = range   &&  z.isRangeZoomable ();

            PlotRenderingInfo pri = info.getPlotInfo ();
            Point2D c = translateScreenToJava2D (e.getPoint ());
            if (! pri.getDataArea ().contains (c)) return;

            int clicks = e.getWheelRotation ();
            double factor = 1.1;
            if (clicks < 0) factor = 1 / factor;

            plot.setNotify (false);
            if (domain) z.zoomDomainAxes (factor, pri, c, true);
            if (range)  z.zoomRangeAxes  (factor, pri, c, true);
            plot.setNotify (true);
        }

        public void drawZoomRectangle (Graphics2D g2, boolean xor)
        {
            if (zoomRectangle == null) return;

            if (xor) g2.setXORMode (Color.GRAY);
            g2.setPaint (zoomFillPaint);
            g2.fill (zoomRectangle);
            if (xor) g2.setPaintMode ();
        }

        public Rectangle getScreenDataArea (int x, int y)
        {
            Insets insets = getInsets ();
            PlotRenderingInfo plotInfo = info.getPlotInfo ();
            Rectangle2D dataArea;
            if (plotInfo.getSubplotCount () == 0)
            {
                dataArea = plotInfo.getDataArea ();
            }
            else
            {
                Point2D selectOrigin = translateScreenToJava2D (new Point (x, y));
                int subplotIndex = plotInfo.getSubplotIndex (selectOrigin);
                if (subplotIndex == -1) return null;
                dataArea = plotInfo.getSubplotInfo (subplotIndex).getDataArea ();
            }
            Rectangle result = dataArea.getBounds ();
            result.translate (insets.left, insets.top);
            return result;
        }

        /**
            Transforms screen pixel coordinate to plot coordinate.
        **/
        public Point2D translateScreenToJava2D (Point screenPoint)
        {
            Insets insets = getInsets ();
            double x = screenPoint.getX () - insets.left;
            double y = screenPoint.getY () - insets.top;
            return new Point2D.Double (x, y);
        }
    }

    public void resetZoom ()
    {
        if (! (plot instanceof Zoomable)) return;
        Zoomable z = (Zoomable) plot;

        Point2D zp = zoomPoint;
        if (zp == null) zp = new Point ();
        PlotRenderingInfo pri = info.getPlotInfo ();
        plot.setNotify (false);
        z.zoomDomainAxes (0, pri, zp);
        z.zoomRangeAxes  (0, pri, zp);
        plot.setNotify (true);
    }

    public Dimension getMinimumSize ()
    {
        Insets insets = getInsets ();
        Dimension result = new Dimension ();
        result.width  = 300 + insets.left + insets.right;
        result.height = 200 + insets.top  + insets.bottom;
        return result;
    }

    public void setSize (int width, int height)
    {
        super.setSize (width, height);

        Insets insets = getInsets ();
        width  -= insets.left + insets.right;
        height -= insets.top  + insets.bottom;

        if (buffer == null  ||  buffer.getWidth () != width  ||  buffer.getHeight () != height)
        {
            if (drawThread != null) drawThread.stop = true;

            Graphics2D g2 = (Graphics2D) getGraphics ();
            GraphicsConfiguration gc = g2.getDeviceConfiguration ();
            buffer = gc.createCompatibleImage (width, height, Transparency.TRANSLUCENT);  // By changing buffer on the EDT, we guarantee that the old buffer will never get painted. At this point it no longer matters what any previous draw threads are doing.

            drawThread = new DrawThread ();
            drawThread.start ();
        }
    }

    @Override
    public void chartChanged (ChartChangeEvent event)
    {
        if (drawThread != null) drawThread.stop = true;
        drawThread = new DrawThread ();
        drawThread.start ();
    }

    public class DrawThread extends Thread
    {
        public boolean stop;

        public DrawThread ()
        {
            super ("PanelChart Draw");
            setDaemon (true);
        }

        public void run ()
        {
            // Determine buffer bounds
            int w = buffer.getWidth ();
            int h = buffer.getHeight ();
            Rectangle2D bufferArea = new Rectangle2D.Double (0, 0, w, h);

            // Clear buffer with a transparent background
            if (buffer == null) return;  // This is not enough to prevent a race condition with setChart(). However, in practice the timing will never be close enough to create one.
            Graphics2D bg = (Graphics2D) buffer.getGraphics ();  // buffer in our containing class may change, but that does no harm to bg. It merely means that we may waste some work painting a buffer that will never be used.
            Composite savedComposite = bg.getComposite ();
            bg.setComposite (AlphaComposite.getInstance (AlphaComposite.CLEAR, 0.0f));
            bg.fill (bufferArea);
            bg.setComposite (savedComposite);

            if (! stop) chart.draw (bg, bufferArea, null, info);  // The most important line of code in this entire class.
            bg.dispose ();

            if (! stop) repaint ();  // The final repaint. Several other calls may be made by chartProgress() to show intermediate results.
        }
    }

    @Override
    public void chartProgress (ChartProgressEvent event)
    {
        int percent = event.getPercent ();
        if (percent > 0  &&  percent < 100) repaint (1000);  // Throttle repaint rate.
    }

    public void paintComponent (Graphics g)
    {
        // Don't call super, because we fully handle the component area.
        Graphics2D g2 = (Graphics2D) g;
        Insets insets = getInsets ();
        g2.drawImage (buffer, insets.left, insets.top, null);
    }

    ActionListener actionProperties = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            ChartEditor editor = ChartEditorManager.getChartEditor (chart);
            int result = JOptionPane.showConfirmDialog (PanelChart.this, editor, "Chart_Properties", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) editor.updateChart (chart);
        }
    };

    ActionListener actionCopy = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            Clipboard systemClipboard = Toolkit.getDefaultToolkit ().getSystemClipboard ();
            Insets insets = getInsets ();
            int w = getWidth () - insets.left - insets.right;
            int h = getHeight () - insets.top - insets.bottom;
            ChartTransferable selection = new ChartTransferable (chart, w, h, 320, 180, 1920, 1080, true);  // Min and max draw sizes are something we don't really care about, but they are required parameters here. These are made up values for a 16:9 screen.
            systemClipboard.setContents (selection, null);
        }
    };

    ActionListener actionSave = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            JFileChooser fileChooser = new JFileChooser ();
            FileNameExtensionFilter filter = new FileNameExtensionFilter ("PNG", "png");
            fileChooser.addChoosableFileFilter (filter);
            fileChooser.setFileFilter (filter);
            if (ChartUtils.isJFreeSVGAvailable ()) fileChooser.addChoosableFileFilter (new FileNameExtensionFilter ("SVG", "svg"));
            if (ChartUtils.isOrsonPDFAvailable ()) fileChooser.addChoosableFileFilter (new FileNameExtensionFilter ("PDF", "pdf"));

            int option = fileChooser.showSaveDialog (PanelChart.this);
            if (option != JFileChooser.APPROVE_OPTION) return;

            String fileName = fileChooser.getSelectedFile ().getPath ();
            filter = (FileNameExtensionFilter) fileChooser.getFileFilter ();
            String suffix = filter.getExtensions ()[0];
            if (! fileName.endsWith ("." + suffix)) fileName += "." + suffix;

            Path file = Paths.get (fileName);
            if (Files.exists (file))
            {
                int response = JOptionPane.showConfirmDialog (PanelChart.this, "Overwrite existing file " + fileName, "Save Image", JOptionPane.OK_CANCEL_OPTION);
                if (response == JOptionPane.CANCEL_OPTION) return;
            }

            int w = getWidth ();
            int h = getHeight ();
            switch (suffix)
            {
                case "png":
                    try {ChartUtils.saveChartAsPNG (new File (fileName), chart, w, h);}
                    catch (IOException ex) {}
                    return;
                case "svg":
                    try (BufferedWriter writer = Files.newBufferedWriter (file))
                    {
                        Class<?> svgGraphics2d = Class.forName ("org.jfree.graphics2d.svg.SVGGraphics2D");
                        Constructor<?> ctor = svgGraphics2d.getConstructor (int.class, int.class);
                        Method         m    = svgGraphics2d.getMethod ("getSVGElement");

                        Graphics2D g2 = (Graphics2D) ctor.newInstance(w, h);
                        g2.setRenderingHint (JFreeChart.KEY_SUPPRESS_SHADOW_GENERATION, true);
                        Rectangle2D drawArea = new Rectangle2D.Double (0, 0, w, h);
                        chart.draw (g2, drawArea);

                        writer.write ("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n");
                        writer.write (m.invoke (g2) + "\n");
                        writer.flush ();
                    }
                    catch (Exception ex) {}
                    return;
                case "pdf":
                    try
                    {
                        Class<?> pdfDocClass = Class.forName ("com.orsonpdf.PDFDocument");
                        Object pdfDoc = pdfDocClass.newInstance ();

                        Method m = pdfDocClass.getMethod ("createPage", Rectangle2D.class);
                        Object page = m.invoke (pdfDoc, new Rectangle (w, h));

                        Method m2 = page.getClass ().getMethod ("getGraphics2D");
                        Graphics2D g2 = (Graphics2D) m2.invoke (page);
                        g2.setRenderingHint (JFreeChart.KEY_SUPPRESS_SHADOW_GENERATION, true);
                        Rectangle2D drawArea = new Rectangle2D.Double (0, 0, w, h);
                        chart.draw (g2, drawArea);

                        Method m3 = pdfDocClass.getMethod ("writeToFile", File.class);
                        m3.invoke (pdfDoc, file);
                    }
                    catch (Exception ex) {}
                    return;
            }
        }
    };

    ActionListener actionReset = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            resetZoom ();
        }
    };

    ActionListener actionPrint = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            PrinterJob job = PrinterJob.getPrinterJob ();
            PageFormat pf  = job.defaultPage ();
            PageFormat pf2 = job.pageDialog (pf);
            if (pf2 != pf)
            {
                job.setPrintable (PanelChart.this, pf2);
                if (job.printDialog ())
                {
                    try {job.print ();}
                    catch (PrinterException ex) {}
                }
            }
        }
    };

    @Override
    public int print (Graphics g, PageFormat pf, int pageIndex)
    {
        if (pageIndex != 0) return NO_SUCH_PAGE;

        Graphics2D g2 = (Graphics2D) g;
        double x = pf.getImageableX ();
        double y = pf.getImageableY ();
        double w = pf.getImageableWidth ();
        double h = pf.getImageableHeight ();
        chart.draw (g2, new Rectangle2D.Double (x, y, w, h));

        return PAGE_EXISTS;
    }
}
