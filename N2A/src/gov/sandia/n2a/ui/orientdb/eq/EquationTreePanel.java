/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.eq;

import gov.sandia.n2a.parsing.EquationParser;
import gov.sandia.n2a.parsing.ParsedEquation;
import gov.sandia.n2a.parsing.gen.ParseException;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeEqReference;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeEquation;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeRoot;
import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import replete.event.ChangeNotifier;
import replete.gui.controls.IconButton;
import replete.gui.controls.mnemonics.MMenuItem;
import replete.gui.controls.simpletree.NodeBase;
import replete.gui.controls.simpletree.SimpleTree;
import replete.gui.controls.simpletree.TModel;
import replete.gui.controls.simpletree.TNode;
import replete.gui.fc.CommonFileChooser;
import replete.gui.windows.Dialogs;
import replete.util.FileUtil;
import replete.util.GUIUtil;
import replete.util.Lay;

public class EquationTreePanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    // Core

    private UIController uiController;

    // UI

    // Tree & Its Model
    private SimpleTree treEqs;
    private TNode root;

    // Side Buttons
    private JButton btnAddAnnot;
    private JButton btnAddRef;
    private JButton btnEdit;
    private JButton btnRemove;
    private JButton btnMoveUp;
    private JButton btnMoveDown;
    private JButton btnImport;

    // Context Menus
    private JPopupMenu mnuAddNewPopup;
    private JPopupMenu mnuEqPopup;
    private JPopupMenu mnuAnnotPopup;
    private JPopupMenu mnuEqRefPopup;
    private JMenuItem mnuMoveUp;
    private JMenuItem mnuMoveDown;

    // Edit Context
    private EquationTreeEditContext context;


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier eqChangeNotifier = new ChangeNotifier(this);
    public void addEqChangeListener(ChangeListener listener) {
        eqChangeNotifier.addListener(listener);
    }
    protected void fireEqChangeNotifier() {
        eqChangeNotifier.fireStateChanged();
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public EquationTreePanel(UIController uic) {
        uiController = uic;
        uiController.addPropListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                treEqs.updateUI();
            }
        });

        // Tree

        root = new TNode(new NodeRoot());
        treEqs = new SimpleTree(root);
        treEqs.setExpandsSelectedPaths(true);
        treEqs.setRootVisible(false);
        treEqs.addMouseListener(contextMenuListener);
        treEqs.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean empty = (treEqs.getPathForLocation(e.getX(), e.getY()) == null);
                if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    if(empty) {
                        context.addEquation(null);
                    } else {
                        context.editSelectedNode();
                    }
                }
            }
        });
        treEqs.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                checkEnabledButtons();
            }
        });
        treEqs.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_DELETE) {
                    context.removeSelectedNode();
                } else if(e.getKeyCode() == KeyEvent.VK_ENTER) {
                    context.editSelectedNode();
                }
            }
        });

        // Side Buttons

        JButton btnAddEqn = new IconButton(ImageUtil.getImage("addexpr.gif"), 2);
        btnAddEqn.setToolTipText("Add Equation...");
        btnAddEqn.addActionListener(addNewListener);

        btnAddAnnot = new IconButton(ImageUtil.getImage("addannot.gif"), 2);
        btnAddAnnot.setToolTipText("Add Annotation...");
        btnAddAnnot.addActionListener(addAnnotListener);

        btnAddRef = new IconButton(ImageUtil.getImage("booknew.gif"), 2);
        btnAddRef.setToolTipText("Add Reference...");
        btnAddRef.addActionListener(addRefListener);

        btnEdit = new IconButton(ImageUtil.getImage("edit.gif"), 2);
        btnEdit.setToolTipText("Edit...");
        btnEdit.addActionListener(editListener);

        btnRemove = new IconButton(ImageUtil.getImage("remove.gif"), 2);
        btnRemove.setToolTipText("Remove");
        btnRemove.addActionListener(removeListener);

        btnMoveUp = new IconButton(ImageUtil.getImage("up.gif"), 2);
        btnMoveUp.setToolTipText("Move Up");
        btnMoveUp.addActionListener(moveUpListener);

        btnMoveDown = new IconButton(ImageUtil.getImage("down.gif"), 2);
        btnMoveDown.setToolTipText("Move Down");
        btnMoveDown.addActionListener(moveDownListener);

        btnImport = new IconButton(ImageUtil.getImage("import.gif"), 2);
        btnImport.setToolTipText("Import Equations");
        btnImport.addActionListener(importListener);


        // Context Menus
        JMenuItem mnuAddNew = new MMenuItem("Add &Equation...", ImageUtil.getImage("addexpr.gif"));
        mnuAddNew.addActionListener(addNewListener);

        JMenuItem mnuAddAnnot = new MMenuItem("Add &Annotation...", ImageUtil.getImage("addannot.gif"));
        JMenuItem mnuAddRef = new MMenuItem("Add &Reference...", ImageUtil.getImage("booknew.gif"));
        mnuAddAnnot.addActionListener(addAnnotListener);
        mnuAddRef.addActionListener(addRefListener);

        JMenuItem mnuEdit = new MMenuItem("&Edit...", ImageUtil.getImage("edit.gif"));
        JMenuItem mnuEdit2 = new MMenuItem("&Edit...", ImageUtil.getImage("edit.gif"));
        mnuEdit.addActionListener(editListener);
        mnuEdit2.addActionListener(editListener);

        JMenuItem mnuRemove = new MMenuItem("&Remove", ImageUtil.getImage("remove.gif"));
        JMenuItem mnuRemove2 = new MMenuItem("&Remove", ImageUtil.getImage("remove.gif"));
        JMenuItem mnuRemove3 = new MMenuItem("&Remove", ImageUtil.getImage("remove.gif"));
        mnuRemove.addActionListener(removeListener);
        mnuRemove2.addActionListener(removeListener);
        mnuRemove3.addActionListener(removeListener);
        // TODO: Group context menu items? (Remove)

        mnuMoveUp = new MMenuItem("Move &Up", ImageUtil.getImage("up.gif"));
        mnuMoveDown = new MMenuItem("Move &Down", ImageUtil.getImage("down.gif"));
        mnuMoveUp.addActionListener(moveUpListener);
        mnuMoveDown.addActionListener(moveDownListener);

        JMenuItem mnuOpen = new MMenuItem("&Open", ImageUtil.getImage("open.gif"));
        mnuOpen.addActionListener(editListener);

        mnuAddNewPopup = new JPopupMenu();
        mnuAddNewPopup.add(mnuAddNew);

        mnuEqPopup = new JPopupMenu();
        mnuEqPopup.add(mnuAddAnnot);
        mnuEqPopup.add(mnuAddRef);
        mnuEqPopup.add(new JSeparator());
        mnuEqPopup.add(mnuEdit);
        mnuEqPopup.add(mnuRemove);
        mnuEqPopup.add(new JSeparator());
        mnuEqPopup.add(mnuMoveUp);
        mnuEqPopup.add(mnuMoveDown);

        mnuAnnotPopup = new JPopupMenu();
        mnuAnnotPopup.add(mnuEdit2);
        mnuAnnotPopup.add(mnuRemove2);

        mnuEqRefPopup = new JPopupMenu();
        mnuEqRefPopup.add(mnuOpen);
        mnuEqRefPopup.add(mnuRemove3);

        Lay.BLtg(this,
            "C", Lay.p(Lay.sp(treEqs), "eb=5r"),
            "E", Lay.BxL("Y",
                Lay.BL(btnAddEqn, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL(btnAddAnnot, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL(btnAddRef, "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL(btnEdit, "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL(btnRemove, "eb=20b,alignx=0.5,maxH=20"),
                Lay.BL(btnMoveUp, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL(btnMoveDown, "eb=5b,alignx=0.5,maxH=20"),
                Lay.hn(btnImport, "alignx=0.5"),
                Box.createVerticalGlue()
            )
        );

        context = new EquationTreeEditContext(uiController, treEqs, null);
        context.addEqChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                fireEqChangeNotifier();
            }
        });

        checkEnabledButtons();
    }


    ///////////////
    // LISTENERS //
    ///////////////

    ActionListener addNewListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            context.addEquation(null);
        }
    };
    ActionListener addAnnotListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            context.addAnnotationToSelected();
        }
    };
    ActionListener addRefListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            context.addReferenceToSelected();
        }
    };
    ActionListener editListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            context.editSelectedNode();
        }
    };
    ActionListener removeListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            context.removeSelectedNode();
        }
    };
    ActionListener moveUpListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            context.moveSelectedUp();
            checkEnabledButtons();
        }
    };
    ActionListener moveDownListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            context.moveSelectedDown();
            checkEnabledButtons();
        }
    };

    ActionListener importListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            CommonFileChooser fc = CommonFileChooser.getChooser("Import Equations");
            if(fc.showOpen(EquationTreePanel.this)) {
                String content = FileUtil.getTextContent(fc.getSelectedFile());
                String[] lines = content.split("\n");
                List<ParsedEquation> good = new ArrayList<ParsedEquation>();
                List<String> invalid = new ArrayList<String>();
                for(String line : lines) {
                    line = line.trim();
                    if(line.equals("") || line.startsWith("#")) {
                        continue;
                    }
                    try {
                        ParsedEquation peq = EquationParser.parse(line);
                        if(peq.getVarName() == null) {
                            invalid.add(line);  // TODO: Behavior should be discussed
                        } else {
                            good.add(peq);
                        }
                    } catch(ParseException ex) {
                        invalid.add(line);
                    }
                }

                context.addEquations(good);
                checkEnabledButtons();

                if(invalid.size() != 0) {
                    String msg = "Import Complete: The following lines were unrecognized as equations and ignored from the import:\n";
                    for(String inv : invalid) {
                        msg += "  > " + inv + "\n";
                    }
                    Dialogs.showWarning(msg);
                }
            }
        }
    };

    private MouseListener contextMenuListener = new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
            if(SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
                TreePath selPath = treEqs.getPathForLocation(e.getX(), e.getY());
                if(selPath == null) {
                    mnuAddNewPopup.show(treEqs, e.getX(), e.getY());
                    return;
                }
                TNode nAny = (TNode) selPath.getLastPathComponent();
                treEqs.setSelectionPath(selPath);
                if(nAny.getUserObject() instanceof NodeEquation) {
                    mnuMoveUp.setEnabled(btnMoveUp.isEnabled());
                    mnuMoveDown.setEnabled(btnMoveDown.isEnabled());
                    mnuEqPopup.show(treEqs, e.getX(), e.getY());
                } else if(nAny.getUserObject() instanceof NodeAnnotation) {
                    mnuAnnotPopup.show(treEqs, e.getX(), e.getY());
                } else if(nAny.getUserObject() instanceof NodeEqReference) {
                    mnuEqRefPopup.show(treEqs, e.getX(), e.getY());
                }
            }
        }
    };


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public List<NDoc> getEquations() {
        return context.getEquations(root);
    }

    public void setEquations(final List<NDoc> eqs) {
        GUIUtil.safe(new Runnable() {
            public void run() {
                context.setEquations(root, eqs);
                treEqs.update();
            }
        });
    }


    //////////
    // MISC //
    //////////

    protected void checkEnabledButtons() {
        boolean isAny = (treEqs.getSelectionCount() != 0);
        boolean isOne = (treEqs.getSelectionCount() == 1);

        btnAddAnnot.setEnabled(isOne);
        btnAddRef.setEnabled(isOne);
        btnEdit.setEnabled(isOne);
        btnRemove.setEnabled(isAny);

        if(isOne) {
            TreePath selPath = treEqs.getSelectionPath();
            TNode nSel = (TNode) selPath.getLastPathComponent();
            NodeBase uSel = (NodeBase) nSel.getUserObject();
            boolean upOk = uSel instanceof NodeEquation && root.getIndex(nSel) > 0;
            boolean downOk = uSel instanceof NodeEquation && root.getIndex(nSel) < root.getChildCount() - 1;
            btnMoveUp.setEnabled(upOk);
            btnMoveDown.setEnabled(downOk);
        } else {
            btnMoveUp.setEnabled(false);
            btnMoveDown.setEnabled(false);
        }
    }

    // So other components can ask this panel to open a dialog
    // for editing an equation.
    public void openForEditing(NDoc openEq, String prefix) {
        ParsedEquation openPeq;
        try {
            openPeq = EquationParser.parse((String) openEq.getValid("value", String.class));
        } catch(ParseException e) {
            UMF.handleUnexpectedError(null, e, "Could not open equation for editing.");
            return;
        }

        String openVar = openPeq.getVarName();

        for(int i = 0; i < root.getChildCount(); i++) {
            TNode nEq = (TNode) root.getChildAt(i);
            NodeEquation uEq = (NodeEquation) nEq.getUserObject();
            NDoc eq = uEq.getEq();
            if(eq == openEq || uEq.getParsed().getVarName().equals(openVar)) {   // TODO: Review this and document.
                TreePath newPath = new TreePath(new Object[]{root, nEq});
                treEqs.expandPath(newPath);
                treEqs.setSelectionPath(newPath);
                treEqs.scrollPathToVisible(newPath);
                context.editEquationNode(nEq);
                return;
            }
        }

        treEqs.clearSelection();
        context.addEquation((prefix == null ? "" : prefix + ".") + openEq.get("value"));
    }
    public void postLayout() {
        TModel model = (TModel) treEqs.getModel();
        model.reload();
        treEqs.updateUI();
    }
}
