/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.partadv;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.ui.UIController;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Stack;

import javax.swing.JButton;
import javax.swing.JSplitPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.util.Lay;

public class AdvancedDetailPanel extends RecordEditDetailPanel implements PartGraphContext {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private AdvancedGraphDetailPanel pnlGraph;
    private AdvancedEquationPanel pnlEqs;

    // Model

    private NDoc curDive;
    private NDoc centeredChild;
    private Stack<DiveCenterStep> steps = new Stack<DiveCenterStep>();


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public AdvancedDetailPanel(UIController uic, NDoc part) {
        super(uic, part);

        JButton btnHi = new JButton("Hi");
        btnHi.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                for(int i = 0; i < steps.size(); i++) {
                    System.out.println(steps.get(i));
                }
            }
        });

        final JSplitPane splMain;
        Lay.BLtg(this,
            "C", splMain = Lay.SPL("Y",
                pnlGraph = new AdvancedGraphDetailPanel(part, this),
                pnlEqs = new AdvancedEquationPanel(part, this),
                "divratio=0.2"
            ),"S", btnHi
        );

        addComponentListener(new ComponentAdapter() {
            private boolean firstTime = true;
            @Override
            public void componentResized(ComponentEvent e) {
                if(firstTime) {
                    splMain.setDividerLocation(0.8);
                    firstTime = false;
                }
            }
        });

        pnlGraph.addCenterListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                centerOnChild((NDoc) e.getSource());
            }
        });
        pnlGraph.addDiveListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                diveIntoChild((NDoc) e.getSource());
            }
        });
        pnlGraph.addUpListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                popUp();
            }
        });

        pnlGraph.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                fireContentChangedNotifier();
            }
        });
        pnlEqs.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                fireContentChangedNotifier();
            }
        });

        diveIntoChild(part);

    }


    //////////
    // PUSH //
    //////////

    // Used by child panels to dive deeper into the hierarchy.

//    public void push(NDoc record) {
//        final SuperPanel pnlSuper = new JGraphSuperPanel(uiController, this, record);
//        stack.add(new Pair(pnlSuper, record));
//        // TODO: what?????????? no need for title to uniquely identify a stack of panels.
//        pnlStack.pushPanel(pnlSuper, record.getTitle(), null, null);
//        pnlSuper.addChildSelectListener(new ChangeListener() {
//            public void stateChanged(ChangeEvent e) {
//                NDoc record = pnlSuper.getSelectedChildAssociation();
//                if(record == null) {
//                    record = SuperStackDetailPanel.this.record;
//                } else {
//                    record = record.get("dest");
//                    if(record != null) {
//                        record = record.get("parent");
//                    }
//                }
//                if(record != null) {
//                    List<NDoc> eqs = record.getValid("eqs", new ArrayList<NDoc>(), List.class);
//                    eqMdl.setEquations(eqs);
//                }
//            }
//        });
//        updateUI();
////        lblTitle.setText((String) record.get("title"));
////        panelStack.add(pnlSuper);
//    }
//
//    public int getPanelCount() {
//        return stack.size();
//    }
//
//    public void pop() {
//        stack.remove(stack.size() - 1);
////        pnlStack.popPanel();
//    }


    @Override
    public void reload() {
        steps.clear();
        curDive = null;
        centeredChild = null;
        diveIntoChild(record);
    }

    protected void centerOnChild(NDoc child) {
        child.dumpDebug("centerOnChild");
        steps.add(new DiveCenterStep(DiveCenterStepType.CENTER, child, curDive));
        centeredChild = child;
        firePartChangeNotifier();
    }

    protected void diveIntoChild(NDoc child) {
        steps.add(new DiveCenterStep(DiveCenterStepType.DIVE, child, curDive));
        curDive = child;
        centeredChild = null;
        firePartChangeNotifier();
    }

    protected void popUp() {
        if(steps.size() == 1) {
            return;
        }
        DiveCenterStep step = steps.pop();
        DiveCenterStep stepNow = steps.lastElement();
        if(step.type == DiveCenterStepType.DIVE) {
            curDive = stepNow.parent;
            centeredChild = stepNow.part;
        } else {
            curDive = stepNow.part;
            centeredChild = null;
        }
        firePartChangeNotifier();
    }

    @Override
    public NDoc getCenteredPart() {
        return centeredChild;
    }
    @Override
    public NDoc getDivedPart() {
        return curDive;
    }

    protected ChangeNotifier partChangeNotifier = new ChangeNotifier(this);
    public void addPartChangeListener(ChangeListener listener) {
        partChangeNotifier.addListener(listener);
    }
    protected void firePartChangeNotifier() {
        partChangeNotifier.fireStateChanged();
    }

    @Override
    public Stack<DiveCenterStep> getSteps() {
        return steps;
    }
}
