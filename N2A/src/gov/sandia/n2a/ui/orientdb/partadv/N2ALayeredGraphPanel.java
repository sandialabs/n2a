/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.partadv;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.util.Lay;

import com.mxgraph.layout.mxFastOrganicLayout;
import com.mxgraph.layout.mxGraphLayout;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxGeometry;
import com.mxgraph.model.mxICell;
import com.mxgraph.swing.mxGraphOutline;
import com.mxgraph.swing.util.mxMorphing;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource.mxIEventListener;
import com.mxgraph.view.mxEdgeStyle;
import com.mxgraph.view.mxStylesheet;


public class N2ALayeredGraphPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private JLayeredPane layeredPane;             // Layered Pane
    private N2AGraph graph;                       // JGraph
    private N2AGraphComponent graphComponent;     // JGraph
    private CollapsibleOutlinePanel pnlOutline;   // Floating
    private CollapsiblePartStackPanel pnlStack;   // Floating
    private PartGraphContext context;             // Reference to containing panel

    // Model
    private Map<String, Object> aliasToVertex = new HashMap<String, Object>();


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public N2ALayeredGraphPanel(NDoc model, final PartGraphContext context) {
        this.context = context;

        graph = new N2AGraph();
        graphComponent = createGraphComponent();
        context.addPartChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                morphToTopChild();//?? when do this... not on cancel, but rather on dive and center.
                updateGraphWithModel(context.getDivedPart());
                graph.setSelectionCell(null);
            }
        });

        // Floating
        pnlOutline = new CollapsibleOutlinePanel(new mxGraphOutline(graphComponent));
        pnlStack = new CollapsiblePartStackPanel();
        pnlOutline.collapse();
        pnlStack.collapse();
        pnlOutline.addExpandCollapseListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateFloatingPanelBounds();
            }
        });
        pnlStack.addExpandCollapseListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateFloatingPanelBounds();
            }
        });

        //configureGraphGlobalStyles();
        updateGraphWithModel(model);

        Lay.BLtg(this,
            "C", layeredPane = createLayeredPane()
        );

        graph.addListener("moveCells", moveResizeListener);
        graph.addListener("resizeCells", moveResizeListener);
    }

    mxIEventListener moveResizeListener = new mxIEventListener() {
        public void invoke(Object sender, mxEventObject evt) {
            Object[] cells = (Object[]) evt.getProperty("cells");
            for(Object cell : cells) {
                updateLocationAndSizeFromGraph((mxCell) cell);
            }
        }
    };

    private void updateLocationAndSizeFromGraph(mxICell cell) {
        CellValueDocumentGlob wrapper =
            (CellValueDocumentGlob) cell.getValue();
        NDoc dWrapper = wrapper.getWrapper();
        mxGeometry geom = cell.getGeometry();
        NDoc layout = dWrapper.getAndSetValid(
            "layout", new NDoc(), NDoc.class);
        layout.set("x", geom.getX());
        layout.set("y", geom.getY());
        layout.set("w", geom.getWidth());
        layout.set("h", geom.getHeight());
        fireChangeNotifier();
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessors

    public N2AGraph getGraph() {
        return graph;
    }
    public N2AGraphComponent getGraphComponent() {
        return graphComponent;
    }

    private JLayeredPane createLayeredPane() {
        final JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.add(graphComponent ,JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(pnlOutline, JLayeredPane.PALETTE_LAYER);
//        layeredPane.add(pnlStack, JLayeredPane.PALETTE_LAYER);

        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // This allows the graph component to pretend like it's
                // in a BorderLayout in the CENTER position.
                graphComponent.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());

                // Move the outline panel accordingly.
                updateFloatingPanelBounds();

                N2ALayeredGraphPanel.this.updateUI();
            }
        });

        return layeredPane;
    }

    private void updateFloatingPanelBounds() {
        int margin = 20;
        int more = 0;
        if(graphComponent.getHorizontalScrollBar().isVisible()) {
            more = graphComponent.getHorizontalScrollBar().getHeight();
        }
        if(pnlOutline.isExpanded()) {
            int wh = 150;
            pnlOutline.setBounds(margin, layeredPane.getHeight() - wh - margin - more, wh, wh);
        } else {
            int wh = 40;
            pnlOutline.setBounds(margin, layeredPane.getHeight() - wh - margin - more, wh, wh);
        }

        margin = 20;
        more = 0;
        if(graphComponent.getHorizontalScrollBar().isVisible()) {
            more = graphComponent.getHorizontalScrollBar().getHeight();
        }
        if(pnlStack.isExpanded()) {
            int wh = 150;
            int ww = 100;
            pnlStack.setBounds(layeredPane.getWidth() - ww - margin, layeredPane.getHeight() - wh - margin - more, ww, wh);
        } else {
            int wh = 40;
            pnlStack.setBounds(layeredPane.getWidth() - wh - margin, layeredPane.getHeight() - wh - margin - more, wh, wh);
        }
    }

    public void configureGraphGlobalStyles() {
        mxStylesheet stylesheet = graph.getStylesheet();
        Hashtable<String, Object> style = new Hashtable<String, Object>();
        style.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_CLOUD);
        style.put(mxConstants.STYLE_OPACITY, 50);
        style.put(mxConstants.STYLE_FONTCOLOR, "#774400");
        style.put(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_LEFT);
        stylesheet.putCellStyle("ROUNDED", style);

        Hashtable<String, Object> style2 = new Hashtable<String, Object>();
        style2.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_CURVE); // not being respected for edge shapes. does work after a hierarchical layout operation, but doesn't stick when you move a vertex.  is being respected if you're just dragging the edge or it's control (i.e. absolute) points.
        style2.put(mxConstants.STYLE_EDGE, mxEdgeStyle.EntityRelation/* mxConstants.EDGESTYLE_ORTHOGONAL*/); // not being respected for edge shapes.
        style2.put(mxConstants.STYLE_STROKECOLOR, "#FA4143");
        stylesheet.putCellStyle("CRVEDGE", style2);

        //Map<String, Object> dfltEdge = graph.getStylesheet().getDefaultEdgeStyle(); // Can also do that.
    }

    //graph.getView().setScale(graph.getView().getScale() * 0.9);   // use view for zooming!
    // HOW TO GET CELL "STATE":
    // mxGraph.getView().getState().

    private N2AGraphComponent createGraphComponent() {
        final N2AGraphComponent graphComponent = new N2AGraphComponent(graph);
        graphComponent.getGraphControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                mxCell cell = (mxCell) graphComponent.getCellAt(e.getX(), e.getY());
                System.out.println(Arrays.toString(graph.getSelectionCells()));
                if(cell != null) {
                    System.out.println("cell="+graph.getLabel(cell));
                    if(e.getClickCount() > 1) {
                        CellValueDocumentGlob glob = (CellValueDocumentGlob) cell.getValue();
                        NDoc child = glob.getWrapper();
                        child.dumpDebug("child");
                        if(context.getCenteredPart() != null) {
                            context.getCenteredPart().dumpDebug("context");
                        } else {
                            System.out.println("centered PART = null");
                        }
                        if(context.getCenteredPart() != null && glob.getChild().getId().equals(context.getCenteredPart().getId())) {
                            fireDiveNotifier(glob.getChild());
                        } else {
                            fireCenterNotifier(glob.getChild());
                        }
//                        System.out.println("  double "+glob);
                    } else {
//                        System.out.println("  single");
                    }
                } else {
                    morphToTopChild();
                    System.out.println("cell=null");
                }
            }
        });

        // Keep outline panel a fixed amount above scroll
        // bar or bottom of panel.
        graphComponent.getHorizontalScrollBar().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                updateFloatingPanelBounds();
            }
            @Override
            public void componentHidden(ComponentEvent e) {
                updateFloatingPanelBounds();
            }
        });

        return graphComponent;
    }

    private void morphToTopChild() {
        int i = 0;
        for(DiveCenterStep step : context.getSteps()) {
            System.out.println((++i) + ". " + step);
        }
    }

    public void updateGraphWithModel(NDoc model) {
        graph.getModel().beginUpdate();
        List<NDoc> children = model.getAndSetValid("children", new ArrayList<NDoc>(), List.class);
//        List<NDoc> bridges = model.getAndSetValid("bridges", new ArrayList<NDoc>(), List.class);
        try {
            mxCell parent = (mxCell) graph.getDefaultParent();  // Root Cell has null value.
            for(int i = parent.getChildCount() - 1; i >= 0; i--) {
                parent.remove(i);
            }
            for(int c = 0; c < children.size(); c++) {
                NDoc child = children.get(c);
                String alias = child.get("alias");
                NDoc layout = child.get("layout");
                double x, y, w, h;
                if(layout == null) {
                    x = new Random().nextInt(100);
                    y = new Random().nextInt(100);
                    System.out.println("x="+x+",y="+y+",id="+alias);
                    w = 100;
                    h = 30;
                } else {
                    double UNK = Double.MIN_VALUE;
                    x = ((Number) layout.get("x", UNK)).doubleValue();
                    y = ((Number) layout.get("y", UNK)).doubleValue();
                    w = ((Number) layout.get("w", UNK)).doubleValue();
                    h = ((Number) layout.get("h", UNK)).doubleValue();
                    if(x == UNK) {x = new Random().nextInt(100);}
                    if(y == UNK) {y = new Random().nextInt(100);}
                    if(w == UNK) {w = 100;}
                    if(h == UNK) {h = 30;}
                }
                // new vertices and edges just get a continually incrementing ID.
                String vStyle = "ROUNDED;shapexxx=timer;strokeColor=red;fillColor=green;strokeWidth=10";
                vStyle = "";
                NDoc dest = child.get("dest");
                String dName = dest.get("name");
                mxCell vertex = (mxCell) graph.insertVertex(parent,
                    null, new CellValueDocumentGlob(child, dest),
                    x, y, w, h, vStyle);
                aliasToVertex.put(alias, vertex);
            }
//            for(int b = 0; b < bridges.size(); b++) {
//                NDoc bridge = bridges.get(b);
//                List<NDoc> connects = bridge.getAndSetValid("connects", new ArrayList<NDoc>(), List.class);
//                NDoc c0 = connects.get(0);
//                NDoc c1 = connects.get(1);
//                String alias1 = c0.get("alias");
//                String alias2 = c1.get("alias");
//                Object vertex1 = aliasToVertex.get(alias1);
//                Object vertex2 = aliasToVertex.get(alias2);
//                String styleE = "CRVEDGE";//;startArrow=none;endArrow=none;strokeWidth=3;strokeColor=#234928";//shape=curved;startSize=10;endSize=10";
////                    String styleE = "edgeStyle=elbowEdgeStyle;elbow=horizontal;orthogonal=0;";
//                mxCell edge = (mxCell) graph.insertEdge(parent, null,
//                    new CellValueDocumentGlob(bridge), vertex1, vertex2, styleE);
//            }
        } finally {
            graph.getModel().endUpdate();
        }
        updateUI();
        pnlOutline.updateUI();
    }

    private void morph(boolean b) {
        mxGraphLayout layout;
        if(b) {
            mxFastOrganicLayout layout2 = new mxFastOrganicLayout(graph);
            layout2.setMinDistanceLimit(10);
            layout2.setInitialTemp(10);
            layout2.setForceConstant(10);
            layout = layout2;
        } else {
//            mxParallelEdgeLayout layout2 = new mxParallelEdgeLayout(graph);
//            layout2.execute(graph.getDefaultParent());
            mxHierarchicalLayout layout2 = new mxHierarchicalLayout(graph);
            layout2.setInterRankCellSpacing(50);
            layout2.setParallelEdgeSpacing(10);
            layout2.setInterHierarchySpacing(20);
            layout2.setDisableEdgeStyle(true);
            layout2.setFineTuning(true);
            layout2.setMoveParent(true);
            layout2.setParentBorder(10);
            layout2.setUseBoundingBox(true);
            //layout2.setVertexLocation(v, 100, 100);
            layout = layout2;
        }

//        ManualTimeProfiler p = new ManualTimeProfiler();
        // layout using morphing
        graph.getModel().beginUpdate();
        try {
            layout.execute(graph.getDefaultParent());
//            p.print();
        } finally {
            // a lot to understand about this animation stuff
            //why doesn't the morph object have to know about the layout object?
            // how does the morph object know what the cells' previous locations
            // were?  why aren't cell locations already in their new locations.
            if(true) {
                final Object[] sel = graph.getSelectionCells();
                graph.setSelectionCells(new Object[]{});
                mxMorphing morph = new mxMorphing(graphComponent, 20, 1.2, 20);
                morph.addListener(com.mxgraph.util.mxEvent.DONE, new mxIEventListener() {
                    public void invoke(Object arg0, mxEventObject arg1) {
                        graph.getModel().endUpdate();
                        graph.setSelectionCells(sel);
                    }
                });
                morph.startAnimation();
            } else {
                graph.getModel().endUpdate();
            }
        }
    }


    ///////////////
    // NOTIFIERS //
    ///////////////

    protected ChangeNotifier centerNotifier = new ChangeNotifier(this);
    protected ChangeNotifier diveNotifier = new ChangeNotifier(this);
    protected ChangeNotifier changeNotifier = new ChangeNotifier(this);
    public void addCenterListener(ChangeListener listener) {
        centerNotifier.addListener(listener);
    }
    public void addDiveListener(ChangeListener listener) {
        diveNotifier.addListener(listener);
    }
    public void addChangeListener(ChangeListener listener) {
        changeNotifier.addListener(listener);
    }
    protected void fireCenterNotifier(NDoc child) {
        centerNotifier.setSource(child);
        centerNotifier.fireStateChanged();
    }
    protected void fireDiveNotifier(NDoc child) {
        System.out.println("DIVE="+child);
        diveNotifier.setSource(child);
        diveNotifier.fireStateChanged();
    }
    protected void fireChangeNotifier() {
        changeNotifier.fireStateChanged();
    }


    ///////////////////
    // INNER CLASSES //
    ///////////////////

    private class CellValueDocumentGlob {
        private NDoc dWrapper;
        private NDoc dChild;
        public CellValueDocumentGlob(NDoc dWrapper, NDoc dChild) {
            this.dWrapper = dWrapper;
            this.dChild = dChild;
            dWrapper.dumpDebug("wrapper");
        }
        public NDoc getWrapper() {
            return dWrapper;
        }
        public NDoc getChild() {
            return dChild;
        }
        @Override
        public String toString() {
            String alias = dWrapper.get("alias");
            NDoc dest = dWrapper.get("dest");
            String dName;
            if(dest != null) {
                dName = dest.get("name");
            } else {
                dName = "UNK";
            }
            return "<html><b>"+alias+"</b><br><i>(" + dName + ")</i></html>";
        }
    }

    public void doGraphLayout() {
        morph(false);
        // No move events fired here, update manually.
        mxCell parent = (mxCell) graph.getDefaultParent();  // Root Cell has null value.
        for(int i = 0; i < parent.getChildCount(); i++) {
            mxICell cell = parent.getChildAt(i);
            updateLocationAndSizeFromGraph(cell);
        }
    }
}
