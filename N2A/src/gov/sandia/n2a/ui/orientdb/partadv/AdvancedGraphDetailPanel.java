/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.partadv;

import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.orientdb.partadv.view.View;
import gov.sandia.n2a.ui.orientdb.partadv.view.ViewSwitchPanel;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.javadev.AnimatingCardLayout;
import org.javadev.effects.CubeAnimation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import replete.event.ChangeNotifier;
import replete.gui.controls.GradientPanel;
import replete.gui.controls.IconButton;
import replete.gui.controls.nofire.NoFireComboBox;
import replete.gui.windows.Dialogs;
import replete.gui.windows.EscapeFrame;
import replete.util.GUIUtil;
import replete.util.Lay;
import replete.util.NumUtil;
import replete.util.StringUtil;

import com.mxgraph.canvas.mxGraphics2DCanvas;
import com.mxgraph.canvas.mxGraphicsCanvas2D;
import com.mxgraph.model.mxIGraphModel;
import com.mxgraph.shape.mxStencil;
import com.mxgraph.shape.mxStencilRegistry;
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource;
import com.mxgraph.util.mxEventSource.mxIEventListener;
import com.mxgraph.util.mxUtils;
import com.mxgraph.util.mxXmlUtils;

public class AdvancedGraphDetailPanel extends JPanel {

    private View view = View.GRAPH;
    public View getView() {
        return view;
    }

    ////////////
    // FIELDS //
    ////////////

    private N2ALayeredGraphPanel pnlInner;
    private NoFireComboBox cboZoom;
    private JButton btnUp;
    private JButton btnAdd;
    private JButton btnLayoutHier;
    private PathPanel pnlPath;

//    private JSONObject model;
    private String prevZoomStr;
    private int prevGoodZoom;

    //http://forum.jgraph.com/questions/4740/applying-a-fastorganic-layout
    //http://forum.jgraph.com/questions/5362/how-to-prevent-edges-to-be-reconnected
    //http://forum.jgraph.com/questions/4810/how-to-layout-nodes-automatically-using-fast-organic-layout
    //http://touchflow.googlecode.com/hg-history/75fada644b2a19c744130923cbd34747fba861a2/doc/jgraphmanual.pdf
    //http://jgraph.github.com/mxgraph/docs/manual_javavis.html#3.1.1.1
    //file:///C:/Users/dtrumbo/work/eclipse-main/jgraphtest/docs/manual/index.html
    //file:///C:/Users/dtrumbo/work/eclipse-main/jgraphtest/docs/api/index.html
    // TODO http://forum.jgraph.com/questions/3894/how-to-zoom-with-anchor-point-different-than-center-of-screen
    // TODO http://forum.jgraph.com/questions/3865/zoom-with-mouse-wheel


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public AdvancedGraphDetailPanel(NDoc doc, final PartGraphContext context) {
        try {
            addStencilReg();
        } catch(IOException e) {
            e.printStackTrace();
        }
        DefaultComboBoxModel mdlZoomValues = new DefaultComboBoxModel();
        mdlZoomValues.addElement("10%");
        mdlZoomValues.addElement("25%");
        mdlZoomValues.addElement("50%");
        mdlZoomValues.addElement("75%");
        mdlZoomValues.addElement("100%");
        mdlZoomValues.addElement("125%");
        mdlZoomValues.addElement("150%");
        mdlZoomValues.addElement("200%");
        mdlZoomValues.addElement("300%");
        mdlZoomValues.addElement("400%");
        prevGoodZoom = 100;
        prevZoomStr = "100%";
        final AnimatingCardLayout layout = new AnimatingCardLayout(new CubeAnimation());
        layout.setAnimationDuration(1000);
        final JPanel pnlTop = new JPanel(layout);
        Lay.hn(pnlTop, "bg=150");
        pnlTop.add(pnlInner = new N2ALayeredGraphPanel(doc, context), "graph");
        pnlTop.add(Lay.FL("L", Lay.lb("<html><u>Inherits From These Parts:</u></html>", "eb=15,size=14"),
            "gradient,gradclr1=C1E3FF,mb=[1,black]"), "inherits");
        pnlTop.add(Lay.FL("L", Lay.lb("<html><u>Connects These Parts:</u></html>", "eb=15,size=14"),
            "gradient,gradclr1=C1FFDF,mb=[1,black]"), "connects");
        JLayeredPane pnlLayeredTop = createLayeredPane(pnlTop, layout);
        Color gradclr2 = GUIUtil.deriveColor(GradientPanel.INIT_COLOR, 0, 10, 0);
        Lay.BLtg(this,
            "N", Lay.BL(
                "C", Lay.hn(pnlPath = new PathPanel(), "opaque=false"),
                "E", Lay.FL("R",
                    btnUp = new IconButton(ImageUtil.getImage("up.gif"), "Add", 2),
                    btnAdd = new IconButton(ImageUtil.getImage("add.gif"), "Add", 2),
                    btnLayoutHier = new IconButton(ImageUtil.getImage("layouthi.gif"), "Hierarchical Layout", 2),
                    Lay.hn(cboZoom = new NoFireComboBox(mdlZoomValues), "prefw=100"),
                    "opaque=false"
                ),
                "gradient,gradclr2=" + Lay.clr(gradclr2)
            ),
            "C", pnlLayeredTop
        );
        pnlPath.push(doc, doc.getTitle(), doc.getIcon());
        pnlInner.addCenterListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                fireCenterNotifier((NDoc) e.getSource());
            }
        });
        pnlInner.addDiveListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                fireDiveNotifier((NDoc) e.getSource());
            }
        });
        pnlInner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                fireChangeNotifier();
            }
        });

        cboZoom.setSelectedItem(prevZoomStr);
        cboZoom.setEditable(true);
        cboZoom.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String val = (String) cboZoom.getSelectedItem();
                if(val.equals(prevZoomStr)) {
                    return;
                }
                String valPreCut = val;
                if(val.endsWith("%")) {
                    val = StringUtil.cut(val, 1);
                }
                Double d = NumUtil.d(val);
                if(d == null || d < 1) {
                    Dialogs.showWarning("Please enter a valid zoom value.");
                    cboZoom.setSelectedItemNoFire(prevGoodZoom + "%");
                } else {
                    int i = d.intValue();
                    pnlInner.getGraph().getView().setScale(i / 100.0);
                    prevGoodZoom = i;
                    cboZoom.setSelectedItemNoFire(prevGoodZoom + "%");
                }
                prevZoomStr = valPreCut;
            }
        });
        mxIEventListener zoomListener = new mxIEventListener() {
            public void invoke(Object sender, mxEventObject evt) {
                double newZoom = pnlInner.getGraph().getView().getScale();
                prevGoodZoom = (int) (newZoom * 100);
                cboZoom.setSelectedItemNoFire(prevGoodZoom + "%");
                prevZoomStr = prevGoodZoom + "%";
            }
        };
        pnlInner.getGraph().getView().addListener(mxEvent.SCALE, zoomListener);
        pnlInner.getGraph().getView().addListener(mxEvent.SCALE_AND_TRANSLATE, zoomListener);
        btnLayoutHier.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
//                if(Dialogs.showConfirm(AdvancedGraphDetailPanel.this, "This action will change the position of the existing children.  Proceed?",
//                        "Perform Layout?")) {  // TODO: Ask?
                    pnlInner.doGraphLayout();
//                }
            }
        });
        btnAdd.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.out.println(context.getDivedPart());
                System.out.println(context.getCenteredPart());

                NDoc part = context.getDivedPart();
                String name = Dialogs.showInput(AdvancedGraphDetailPanel.this,
                    "Please enter 'ALIAS; Name' of the new child part.",
                    "New Part", "NA; NA_Koch");
                if(name == null) {
                    return;
                }
                String[] parts = name.trim().split("\\s*;\\s*");

                NDoc partNAChild = new NDoc("gov.sandia.umf.n2a$PartX");
                partNAChild.set("name", parts[1]);
                partNAChild.set("parent", null);
                partNAChild.set("internal-part", true);

                NDoc wrapper = new NDoc("gov.sandia.umf.n2a$PartXWrapper");
                wrapper.set("alias", parts[0]);
                wrapper.set("dest", partNAChild);
                NDoc layout = new NDoc();
                layout.set("x", 30);
                layout.set("y", 200);
                layout.set("w", 100);
                layout.set("h", 40);
                wrapper.set("layout", layout);

                List<NDoc> children = part.getAndSetValid("children",
                    new ArrayList<NDoc>(), List.class);
                children.add(wrapper);
                part.set("children", children);

                pnlInner.updateGraphWithModel(part);
            }
        });
        context.addPartChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                pnlPath.clear();
                for(int i = 0; i < context.getSteps().size(); i++) {
                    DiveCenterStep step = context.getSteps().get(i);
                    if(step.type == DiveCenterStepType.DIVE) {
                        pnlPath.push(step.part, (String) step.part.get("name", "Unknown"), null);
                    }
                }
                btnUp.setEnabled(context.getSteps().size() != 1);
            }
        });
        btnUp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fireUpNotifier();
            }
        });

    }

    protected ChangeNotifier upNotifier = new ChangeNotifier(this);
    public void addUpListener(ChangeListener listener) {
        upNotifier.addListener(listener);
    }
    protected void fireUpNotifier() {
        upNotifier.fireStateChanged();
    }

    private JLayeredPane createLayeredPane(final JPanel pnlTop, final CardLayout layout) {
        final JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.add(pnlTop ,JLayeredPane.DEFAULT_LAYER);
        final ViewSwitchPanel pnlStack = new ViewSwitchPanel();
        pnlStack.addSwitchListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                view = (View) e.getSource();
                try {
                    layout.show(pnlTop, view.name().toLowerCase());
                } catch(Exception ex) {

                }
            }
        });
        layeredPane.add(pnlStack, JLayeredPane.PALETTE_LAYER);

        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // This allows the graph component to pretend like it's
                // in a BorderLayout in the CENTER position.
                pnlTop.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
                // Move the outline panel accordingly.
                updateStackBounds(layeredPane, pnlTop, pnlStack);
                AdvancedGraphDetailPanel.this.updateUI();
            }
        });

        return layeredPane;
    }

    private void updateStackBounds(JLayeredPane layeredPane, JPanel pnlTop, JPanel pnlStack) {
        int margin = 20;
        int wh = 180;
        int ww = 60;
        pnlStack.setBounds(layeredPane.getWidth() - ww - margin, margin, ww, wh);
    }

//    private void constructNetwork() {
//        try {
//            URL inputUrl = JGraphPartEditDetailPanel.class.getResource("input.txt");
//            String s = FileUtil.getTextContent(inputUrl.openStream());
//            model = new JSONObject(s);
//        } catch(IOException e) {
//            e.printStackTrace();
//        }
//    }

    // Static, called once per VM
    private void addStencilReg() throws IOException {
        InputStream stream = AdvancedGraphDetailPanel.class.getResource ("shapes.xml").openStream();
        Document doc = mxXmlUtils.parseXml(mxUtils.readInputStream(stream));
        Element shapes = doc.getDocumentElement();
        NodeList list = shapes.getElementsByTagName("shape");
        for(int i = 0; i < list.getLength(); i++) {
            Element shape = (Element) list.item(i);
            mxStencilRegistry.addStencil(shape.getAttribute("name"),
                new mxStencil(shape) {
                    @Override
                    protected mxGraphicsCanvas2D createCanvas(final mxGraphics2DCanvas gc) {
                        return new mxGraphicsCanvas2D(gc.getGraphics()) {
                            @Override
                            protected Image loadImage(String src) {
                                // Adds image base path to relative image URLs
                                if(!src.startsWith("/") &&
                                   !src.startsWith("http://") &&
                                   !src.startsWith("https://") &&
                                   !src.startsWith("file:")) {
                                    src = gc.getImageBasePath() + src;
                                }
                                // Call is cached
                                return gc.loadImage(src);
                            }
                        };
                    }
                });
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
        diveNotifier.setSource(child);
        diveNotifier.fireStateChanged();
    }
    protected void fireChangeNotifier() {
        changeNotifier.fireStateChanged();
    }


    ////////////////////
    // HELPER / DEBUG //
    ////////////////////

    public static void listenTo(final mxEventSource src) {
        src.addListener(null, new mxEventSource.mxIEventListener() {
            public void invoke(Object gr, mxEventObject event) {
                System.out.println(src.getClass().getSimpleName() + " ==> " + event.getName());
            }
        });
    }
    public static void listenTo(final mxIGraphModel src) {
        src.addListener(null, new mxEventSource.mxIEventListener() {
            public void invoke(Object gr, mxEventObject event) {
                System.out.println(src.getClass().getSimpleName() + " ==> " + event.getName());
            }
        });
    }


    //////////
    // TEST //
    //////////

    public static void main(String[] args){
        NDoc doc = new NDoc();
        EscapeFrame frame = new EscapeFrame();
        frame.setTitle("N2A Advanced Hierarchical Network Editing Panel");
        frame.setIconImage(ImageUtil.getImage("n2a.gif").getImage());
        final AdvancedGraphDetailPanel pnlDetail;
        PartGraphContext context = new PartGraphContext() {
            public Stack<DiveCenterStep> getSteps() {
                return null;
            }
            public NDoc getCenteredPart() {
                return null;
            }
            public void addPartChangeListener(ChangeListener listener) {
            }
            @Override
            public NDoc getDivedPart() {
                return null;
            }
        };
        Lay.BLtg(frame,
            "C", pnlDetail = new AdvancedGraphDetailPanel(doc, context),
            "size=[800,600],dco=exit,center=2,visible=true"
        );
        frame.addClosingListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                System.out.println("closing - write save code here");
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                pnlDetail.pnlInner.getGraphComponent().requestFocusInWindow();
            }
            @Override
            public void windowOpened(WindowEvent e) {
                pnlDetail.pnlInner.getGraph().getView().setScale(2);
            }
        });
    }
}