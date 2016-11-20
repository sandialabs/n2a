/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble;

import gov.sandia.n2a.parms.Parameter;
import gov.sandia.n2a.parms.ParameterBundle;
import gov.sandia.n2a.parms.ParameterDomain;
import gov.sandia.n2a.parms.ParameterKeyPath;
import gov.sandia.umf.platform.ensemble.params.ParameterSetList;
import gov.sandia.umf.platform.ensemble.params.groups.ConstantParameterSpecGroup;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;
import gov.sandia.umf.platform.ui.ensemble.run.DragCursors;
import gov.sandia.umf.platform.ui.ensemble.tree.FilterableParameterTreePanel;
import gov.sandia.umf.platform.ui.ensemble.tree.NodeParameter;
import gov.sandia.umf.platform.ui.ensemble.tree.NodeSubdomain;
import gov.sandia.umf.platform.ui.ensemble.tree.ParameterTree;

import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import replete.event.ChangeNotifier;
import replete.gui.controls.simpletree.NodeBase;
import replete.gui.controls.simpletree.NodeSimpleLabel;
import replete.gui.controls.simpletree.SimpleTree;
import replete.gui.controls.simpletree.TNode;
import replete.gui.tabbed.AdvancedTabbedPane;
import replete.gui.uidebug.DebugPanel;
import replete.gui.windows.Dialogs;
import replete.util.Lay;


public class FixedParameterSpacePanel extends DebugPanel implements DragGestureListener {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private AdvancedTabbedPane tabDefSet;
    private AdvancedTabbedPane tabParamDomains;
    private JScrollPane scrGroups;
    private ParameterDetailsPanel pnlParamDetails;
    private JCheckBox chkDV;
    private ParameterSetTableModel mdlParamSets;
    private long estDuration;

    private ParameterDomain allDomains;
    private ParameterDomain modelDomain;
    private ParameterDomain simDomain;
    private ParameterSpecGroupSet groups;
    private ConstantParameterSpecGroup defaultValueGroup;
    private ParameterTree activeParamTree;

    private ParameterSpecGroupsPanel pnlGroups;

    private ParameterSpecPanel updatePanel;

    private static Color dropEmphasis = Lay.clr("FFF993");
    private Exception error;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public FixedParameterSpacePanel(ParameterDomain domains, long estDur) {
        modelDomain = domains;
        modelDomain.setName("Model");
        modelDomain.setIcon(ImageUtil.getImage("model.gif"));

        estDuration = estDur;
        constructDVGroup();

        String lblStyle = "fg=white";
        String pnlStyle = "bg=100, eb=3, augb=mb(1b,black), gradient, gradclr1=140, gradclr2=100";

        mdlParamSets = new ParameterSetTableModel();
        final JTable tblParamSets = new JTable(mdlParamSets);
        //"Define your fixed parameter space below by dragging parameters to the right and modifying their specifications.",
        //"The following table enumerates the exact parameter sets that will be selected given the definition you provided.",

        final JCheckBox chkShowDVInTree;

        Lay.BLtg(this,
            "C", tabDefSet = Lay.TBL(
                "Definition", Lay.BL(
                    "C", Lay.SPL(
                        Lay.BL(
                            "N", Lay.p(
                                Lay.lb("Available Parameters", lblStyle),
                                pnlStyle
                            ),
                            "C", tabParamDomains = new AdvancedTabbedPane(),
                            "S", chkShowDVInTree = new JCheckBox("Show Default Values")
                        ),
                        Lay.BL(
                            "N", Lay.p(
                                Lay.lb("Defined Parameter Groups", lblStyle),
                                pnlStyle
                            ),
                            "C", scrGroups = Lay.sp(pnlGroups = new ParameterSpecGroupsPanel())
                        ),
                        "divpixel=200"
                    )
                ),
                "Parameter Sets", Lay.BL(
                    "N", Lay.FL(
                        "R",
                        chkDV = Lay.chk("Show Default Values", "fg=white,opaque=false"),
                        "hgap=0,vgap=0", pnlStyle
                    ),
                    "C", Lay.sp(tblParamSets)
                )
            ),
            "S", pnlParamDetails = new ParameterDetailsPanel(pnlStyle)
        );

        new ParamSpecGroupsPanelTargetListener(pnlGroups, dropEmphasis);

        tabParamDomains.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                FilterableParameterTreePanel pnl =
                    (FilterableParameterTreePanel) tabParamDomains.getComponentAt(
                        tabParamDomains.getSelectedIndex());
                activeParamTree = pnl.getTree();
                updateDetailsPanelFromTree(activeParamTree);
            }
        });

        tblParamSets.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                updateDetailsPanelFromTable(tblParamSets);
            }
        });
        tblParamSets.getColumnModel().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                updateDetailsPanelFromTable(tblParamSets);
            }
        });

        tblParamSets.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        pnlGroups.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateFromGroups();
            }
        });
        pnlGroups.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if(updatePanel != null) {
                    int y = 0;
                    Container parent = updatePanel;
                    do {
                        if(parent.getBounds() != null) {
                            y += parent.getBounds().y;
                        }
                        parent = parent.getParent();
                    } while(!(parent instanceof ParameterSpecGroupsPanel));
                    pnlGroups.scrollRectToVisible(new Rectangle(0, y, pnlGroups.getWidth(), updatePanel.getHeight()));
                    updatePanel = null;
                }
            }
        });
        chkDV.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateFromGroups();
            }
        });

        tabDefSet.setIconAt(0, ImageUtil.getImage("def.gif"));
        tabDefSet.setIconAt(1, ImageUtil.getImage("table.gif"));
        tabDefSet.setUseBorderAt(0, true);
        tabDefSet.setUseBorderAt(1, true);

        tabDefSet.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if(tabDefSet.getSelectedIndex() == 0) {
                    updateDetailsPanelFromTree(activeParamTree);
                } else {
                    updateDetailsPanelFromTable(tblParamSets);
                }
            }
        });

        chkShowDVInTree.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                for(int t = 0; t < tabParamDomains.getTabCount(); t++) {
                    FilterableParameterTreePanel pnl =
                        (FilterableParameterTreePanel) tabParamDomains.getComponentAt(t);
                    ParameterTree treParams = pnl.getTree();
                    treParams.setShowDefaultValues(chkShowDVInTree.isSelected());
                }
            }
        });

        for(ParameterDomain domain : allDomains.getSubdomains()) {
            buildDomainTab(domain);
        }
        pnlParamDetails.clearLeft("No Parameter Selected");
        updateFromGroups();
    }

    protected ChangeNotifier errorNotifier = new ChangeNotifier(this);
    public void addErrorListener(ChangeListener listener) {
        errorNotifier.addListener(listener);
    }
    protected void fireErrorNotifier() {
        errorNotifier.fireStateChanged();
    }

    private void constructDVGroup() {
        allDomains = new ParameterDomain();
        if(modelDomain != null) {
            allDomains.addSubdomain(modelDomain);
        }
        if(simDomain != null) {
            allDomains.addSubdomain(simDomain);
        }
        defaultValueGroup = new ConstantParameterSpecGroup();
        Map<Object, Object> flat = ParameterDomain.flattenDomains(allDomains);
        for(Object paramKey : flat.keySet()) {
            defaultValueGroup.addConstParameter(paramKey, flat.get(paramKey));
        }
    }

    private void buildDomainTab(ParameterDomain domain) {
        TNode nRoot = new TNode(new NodeSimpleLabel(domain.getName()));
        populate(nRoot, domain);

        FilterableParameterTreePanel pnlFilterableTree = new FilterableParameterTreePanel(nRoot);
        final ParameterTree treParams = pnlFilterableTree.getTree();

        treParams.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                TreePath path = treParams.getPathForLocation(e.getX(), e.getY());
                if(path == null || ((TNode) path.getLastPathComponent()).getUserObject() instanceof NodeSubdomain) {
                    treParams.setCursor(Cursor.getDefaultCursor());
                } else {
                    treParams.setCursor(DragCursors.getOpenhandcursor());
                }
            }
        });

        treParams.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                updateDetailsPanelFromTree(treParams);
            }
        });

        treParams.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() > 1 && treParams.getSelectionCount() != 0) {
                    List<ParameterBundle> bundles = getSelectedParamsAsBundles(treParams.getSelectionPaths());
                    if(bundles != null) {
                        dropIntoGroups(bundles, dropEmphasis);
                    }
                }
            }
        });

        DragSource ds = new DragSource();
        ds.createDefaultDragGestureRecognizer(treParams, DnDConstants.ACTION_LINK, this);

        int existingIndex = tabParamDomains.indexOfTabByKey(domain.getName());
        if(existingIndex != -1) {
            tabParamDomains.remove(existingIndex);
        }
        tabParamDomains.add(domain.getName(), pnlFilterableTree);
        int index = tabParamDomains.getTabCount();
        tabParamDomains.setIconAt(index - 1, domain.getIcon());
        tabParamDomains.setUseBorderAt(index - 1, true);
    }

    private void updateDetailsPanelFromTree(ParameterTree treParams) {
        TreePath[] paths = treParams.getSelectionPaths();

        if(paths == null || paths.length == 0) {
            pnlParamDetails.clearLeft("No Parameter Selected");

        } else if(paths.length > 1) {
            pnlParamDetails.clearLeft("Multiple Parameters Selected");

        } else {
            NodeBase uObj = ((TNode) paths[0].getLastPathComponent()).getObject();
            if(uObj instanceof NodeParameter) {
                ParameterKeyPath P = new ParameterKeyPath();
                for(Object o : paths[0].getPath()) {
                    P.add(o.toString());
                }
                NodeParameter uParam = (NodeParameter) uObj;
                Parameter param = uParam.getParameter();
                pnlParamDetails.clearLeft();
                pnlParamDetails.addLeft("Parameter", P.toHtml(), false, true, true);
                pnlParamDetails.addLeft("Default Value", param.getDefaultValue(), true, true, false);
                pnlParamDetails.addLeft("Description", param.getDescription(), false, true, true);
            } else {
                pnlParamDetails.clearLeft("No Parameter Selected");
            }
        }
    }

    protected void updateDetailsPanelFromTable(JTable tblParamSets) {
        int col = tblParamSets.getSelectedColumn();
        int row = tblParamSets.getSelectedRow();
        int run = (Integer) mdlParamSets.getValueAt(row, 0);
        if(col == -1 || row == -1) {
            pnlParamDetails.clearLeft("No Cell Selected");
        } else {
            pnlParamDetails.clearLeft();
            pnlParamDetails.addLeft("Run #", run, false, false, true);
            if(col > 0) {
                ParameterKeyPath P = (ParameterKeyPath) mdlParamSets.getParam(col);
                pnlParamDetails.addLeft("Parameter", P.toHtml(), false, true, true);
                pnlParamDetails.addLeft("Value", mdlParamSets.getValueAt(row, col), true, false, false);
            }
        }
    }

    protected void updateFromGroups() {
        ParameterSpecGroupSet newGroups = pnlGroups.getParameterSpecGroupSet();
        newGroups.setDefaultValueGroup(defaultValueGroup);
        try {
            newGroups.validate();
            setError(null);
        } catch(Exception e) {
            setError(new RuntimeException("There is a problem with your parameterization.", e));
            return;
        }
        groups = newGroups;
        ParameterSetList sets = groups.generateAllSetsFromSpecs(chkDV.isSelected());
        mdlParamSets.setParameterSetList(sets);
        pnlParamDetails.updateRun(sets.getNumSets(), estDuration);
    }
    private void setError(Exception err) {
        error = err;
        fireErrorNotifier();
    }

    // AMap wrap operation
    private void populate(TNode nParent, ParameterDomain domain) {
        for(ParameterDomain subdomain : domain.getSubdomains()) {
            TNode nSubdomain = new TNode(new NodeSubdomain(subdomain));
            populate(nSubdomain, subdomain);
            nParent.add(nSubdomain);
        }
        for(Parameter param : domain.getParameters()) {
            TNode nParam = new TNode(new NodeParameter(param));
            nParent.add(nParam);
        }
    }

    public ParameterSpecGroupSet getParameterSpecGroupSet() {
        return groups;
    }

    public void setSimulationInputParameters(ParameterDomain sDomain) {
        if(sDomain == null) {
            sDomain = new ParameterDomain();
        }
        simDomain = sDomain;
        simDomain.setName("Simulator");
        simDomain.setIcon(ImageUtil.getImage("job.gif"));
        constructDVGroup();
        buildDomainTab(simDomain);
        updateFromGroups();
        // TODO remove from groups any old sim params
    }


    /////////////////
    // DRAG & DROP //
    /////////////////

    private class RoundedSpecPanelTargetListener extends DropTargetAdapter {


        ////////////
        // FIELDS //
        ////////////

        //private DropTarget dropTarget;  Unneeded as of now
        private RoundedSpecPanel pnlRounded;
        private Color prevColor;
        private Color backgroundHighlight;


        /////////////////
        // CONSTRUCTOR //
        /////////////////

        public RoundedSpecPanelTargetListener(RoundedSpecPanel panel, Color backgroundHighlight) {
            this.backgroundHighlight = backgroundHighlight;
            pnlRounded = panel;
            prevColor = pnlRounded.getBackground();
            new DropTarget(
                pnlRounded, DnDConstants.ACTION_LINK, this, true, null);
        }

        public void drop(DropTargetDropEvent event) {
            try {
                Transferable tr = event.getTransferable();
                List<ParameterBundle> bundles =
                    (List<ParameterBundle>)
                        tr.getTransferData(TransferableParameterBundles.BUNDLE_FLAVOR);
                if(event.isDataFlavorSupported(TransferableParameterBundles.BUNDLE_FLAVOR)) {
                    dropIntoGroup(bundles, pnlRounded);
                    event.acceptDrop(DnDConstants.ACTION_LINK);
                    event.dropComplete(true);
                    return;
                }
            } catch(Exception e) {
                e.printStackTrace();
            } finally {
                event.rejectDrop();
                dragOff();
            }
        }


        ////////////////
        // OVERRIDDEN //
        ////////////////

        @Override
        public void dragEnter(DropTargetDragEvent dtde) {
            dragOn();
        }
        @Override
        public void dragExit(DropTargetEvent dte) {
            dragOff();
        }


        //////////
        // MISC //
        //////////

        private void dragOn() {
            pnlRounded.highlight2(backgroundHighlight);
        }
        private void dragOff() {
            pnlRounded.highlight0();
        }
    }

    private class ParamSpecGroupsPanelTargetListener extends DropTargetAdapter {


        ////////////
        // FIELDS //
        ////////////

        private ParameterSpecGroupsPanel pnlGroups;
        private Color prevColor;
        private Color backgroundHighlight;


        /////////////////
        // CONSTRUCTOR //
        /////////////////

        public ParamSpecGroupsPanelTargetListener(ParameterSpecGroupsPanel panel, Color backgroundHighlight) {
            this.backgroundHighlight = backgroundHighlight;
            pnlGroups = panel;
            pnlGroups.setOpaque(true);
            prevColor = pnlGroups.getBackground();
            new DropTarget(
                pnlGroups, DnDConstants.ACTION_LINK, this, true, null);
        }

        public void drop(DropTargetDropEvent event) {
            try {
                Transferable tr = event.getTransferable();
                List<ParameterBundle> bundles =
                    (List<ParameterBundle>)
                        tr.getTransferData(TransferableParameterBundles.BUNDLE_FLAVOR);
                if(event.isDataFlavorSupported(TransferableParameterBundles.BUNDLE_FLAVOR)) {
                    dropIntoGroups(bundles, backgroundHighlight);
                    event.acceptDrop(DnDConstants.ACTION_LINK);
                    event.dropComplete(true);
                    return;
                }

            } catch(Exception e) {
                e.printStackTrace();
            } finally {
                event.rejectDrop();
                dragOff();
            }
        }


        ////////////////
        // OVERRIDDEN //
        ////////////////

        @Override
        public void dragEnter(DropTargetDragEvent dtde) {
            dragOn();
        }
        @Override
        public void dragExit(DropTargetEvent dte) {
            dragOff();
        }


        //////////
        // MISC //
        //////////

        private void dragOn() {
            pnlGroups.setBackground(backgroundHighlight);
            for(RoundedSpecPanel pnlRounded : pnlGroups.groupPanels.keySet()) {
                pnlRounded.highlight1(backgroundHighlight);
            }
        }
        private void dragOff() {
            pnlGroups.setBackground(prevColor);
            for(RoundedSpecPanel pnlRounded : pnlGroups.groupPanels.keySet()) {
                pnlRounded.highlight0();
            }
        }
    }

    private void dropIntoGroup(List<ParameterBundle> bundles, RoundedSpecPanel pnlRounded) {
        ParameterSpecGroupPanel pnlDropGroup = (ParameterSpecGroupPanel) pnlRounded;
        ParameterSpecPanel pnlSpec = null;
        for(ParameterBundle bundle : bundles) {
            ParameterSpecGroupPanel pnlExistingGroup = pnlGroups.getGroupPanelForParam(bundle);
            if(pnlExistingGroup != null) {
                if(pnlExistingGroup == pnlDropGroup) {
                    Dialogs.showWarning(FixedParameterSpacePanel.this,
                        "The parameter already exists in the target group (" +
                        pnlDropGroup.getGroupLabel() + ").");
                } else {
                    Dialogs.showWarning(FixedParameterSpacePanel.this,
                        "The parameter already exists in another group (" +
                        pnlExistingGroup.getGroupLabel() + ").");
                }
                continue;
            }
            pnlSpec = pnlDropGroup.addParam(bundle);
        }
        popScrollPane(pnlSpec);
    }

    private void dropIntoGroups(List<ParameterBundle> bundles, Color backgroundHighlight) {
        ParameterSpecGroupPanel pnlGroup = null;
        ParameterSpecPanel pnlSpec = null;
        for(ParameterBundle bundle : bundles) {
            ParameterSpecGroupPanel pnlExistingGroup = pnlGroups.getGroupPanelForParam(bundle);
            if(pnlExistingGroup != null) {
                Dialogs.showWarning(FixedParameterSpacePanel.this,
                    "This parameter already exists in an existing group (" +
                    pnlExistingGroup.getGroupLabel() + ").");
                continue;
            }
            if(pnlGroup == null) {
                pnlGroup = new ParameterSpecGroupPanel();
                new RoundedSpecPanelTargetListener(pnlGroup, backgroundHighlight);
            }
            pnlSpec = pnlGroup.addParam(bundle);
        }
        if(pnlGroup != null) {
            popScrollPane(pnlSpec);
            pnlGroups.addGroupPanel(pnlGroup);
        }
    }

    public void dragGestureRecognized(DragGestureEvent event) {
        Cursor cursor = null;
        SimpleTree list = (SimpleTree) event.getComponent();
        TreePath[] paths = list.getSelectionPaths();
        if(paths == null) {
            return;
        }
        List<ParameterBundle> bundles = getSelectedParamsAsBundles(paths);
        if(bundles == null) {
            return;
        }
        if(event.getDragAction() == DnDConstants.ACTION_LINK) {
            cursor = DragCursors.getGrabhandcursor();
        }
        event.startDrag(cursor, new TransferableParameterBundles(bundles));
    }

    private List<ParameterBundle> getSelectedParamsAsBundles(TreePath[] paths) {
        List<ParameterBundle> bundles = new ArrayList<ParameterBundle>();
        for(TreePath path : paths) {
            List<ParameterDomain> domains = new ArrayList<ParameterDomain>();
            if(tabParamDomains.getSelectedIndex() == 0) {
                domains.add(new ParameterDomain("Model"));
            } else {
                domains.add(new ParameterDomain("Simulator"));
            }
            for(int p = 0; p < path.getPathCount(); p++) {
                Object u = ((TNode)path.getPathComponent(p)).getObject();
                if(u instanceof NodeSubdomain) {
                    domains.add(((NodeSubdomain) u).getSubdomain());
                }
            }
            NodeBase uLeaf = ((TNode) path.getLastPathComponent()).getObject();
            if(uLeaf instanceof NodeParameter) {
                NodeParameter p = (NodeParameter) uLeaf;
                bundles.add(new ParameterBundle(domains, p.getParameter()));
            } else {
                // TODO: add all children?
                return null;
            }
        }
        return bundles;
    }

    public void popScrollPane(ParameterSpecPanel pnlSpec) {
        if(pnlSpec != null) {
            updatePanel = pnlSpec;
        }
    }
    public Exception getError() {
        return error;
    }
}
