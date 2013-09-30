/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.partadv;

import java.awt.Cursor;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.handler.mxRubberband;

import replete.util.GUIUtil;

public class N2AGraphComponent extends mxGraphComponent
{
    public static Cursor openHandCursor;
    public static Cursor grabHandCursor;

    static
    {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Image cursorImage = GUIUtil.getImageLocal ("openhand.gif").getImage ();
        Point cursorHotSpot = new Point (7, 7);
        openHandCursor = toolkit.createCustomCursor(cursorImage, cursorHotSpot, "OpenHand");
        cursorImage = GUIUtil.getImageLocal ("grabhand.gif").getImage ();
        grabHandCursor = toolkit.createCustomCursor(cursorImage, cursorHotSpot, "GrabHand");
    }

    public N2AGraphComponent(N2AGraph gr)
    {
        super(gr);

        setGridVisible(true);
        setGridStyle(mxGraphComponent.GRID_STYLE_LINE);
//        setGridColor(Color.black);
        setAutoExtend(true); // ??
        setAutoScroll(true); // ??
        setAutoscrolls(true); // ??
        //setZoomFactor(200);  //??
        setDragEnabled(false);  // Disables NATIVE-only drag/drop behavior.  This implies
                                // NO integration with external components.  This also
                                // prevents serialization of the cells' values.  However,
                                // diagram dragging on vertices/edges still works and is
                                // unrelated.
        getVerticalScrollBar().setUnitIncrement(16);

        // Mouse wheel zoom.
        addMouseWheelListener(new MouseWheelListener() {
            public void mouseWheelMoved(MouseWheelEvent e) {
                if(e.isControlDown()) {
                    if(e.getWheelRotation() < 0) {
                        zoomIn();
                    } else {
                        zoomOut();
                    }
                }
            }
        });

        // Select all, and keyboard zoom in/out.
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                // Ctrl+?
                if(e.isControlDown()) {
                    if(e.getKeyCode() == KeyEvent.VK_A) {
                        graph.selectAll();
                    } else if(e.getKeyCode() == KeyEvent.VK_EQUALS) { // Handles Ctrl+= & Ctrl++
                        zoomIn();
                    } else if(e.getKeyCode() == KeyEvent.VK_MINUS) {
                        zoomOut();
                    }
                }
            }
        });

        // Adds lasso selection capability.
        new mxRubberband(this) {
            @Override
            public boolean isRubberbandTrigger(MouseEvent e) {
                // Don't start lasso if Ctrl is being held so canvas
                // dragging can proceed unhindered.
                return !e.isControlDown();
            }
        };
    }

    protected Point canvasDragPoint = null;

    @Override
    protected void createHandlers() {
        getGraphControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                canvasDragPoint = e.getPoint();
            }
        });
        getGraphControl().addMouseMotionListener(new MouseMotionListener() {
            public void mouseMoved(MouseEvent e) {
                if(e.isControlDown() && !e.isConsumed()) {
                    getGraphControl().setCursor(openHandCursor);
                    e.consume();
                }
            }
            public void mouseDragged(MouseEvent e) {
                int dx = e.getPoint().x - canvasDragPoint.x;
                int dy = e.getPoint().y - canvasDragPoint.y;
                if(e.isControlDown() && !e.isConsumed()) {
                    getGraphControl().setCursor(grabHandCursor);
                    getHorizontalScrollBar().setValue(getHorizontalScrollBar().getValue() - dx);
                    getVerticalScrollBar().setValue(getVerticalScrollBar().getValue() - dy);
                    e.consume();
                } else {
                    getGraphControl().setCursor(Cursor.getDefaultCursor());
                }
                canvasDragPoint = new Point(e.getPoint().x - dx, e.getPoint().y - dy);
            }
        });

        // Need to add our own canvas drag lisener before the
        // built-in ones so that our e.consume() can prevent
        // built-in mxGraphHandler.mouseMoved/mouseDragged listeners.
        super.createHandlers();
    }

    @Override
    protected mxGraphControl createGraphControl() {
        return new N2AGraphControl();
    }

    // Exists so we can easily override and inspect parts of the
    // graph control.
    public class N2AGraphControl extends mxGraphControl {
    }
}
