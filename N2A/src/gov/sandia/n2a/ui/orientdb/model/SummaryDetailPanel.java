/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.model;

import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.n2a.eqset.DataModelLoopException;
import gov.sandia.n2a.ui.orientdb.eq.EquationSummaryFlatPanel;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeSummaryRoot;
import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.XTextPane;
import replete.gui.controls.mnemonics.MRadioButton;
import replete.util.GUIUtil;
import replete.util.Lay;
import replete.util.ThreadUtil;

public class SummaryDetailPanel extends ModelEditDetailPanel {


    /////////////////
    // INNER CLASS //
    /////////////////

    private final class ProblemTextPane extends XTextPane {
        public void reload(List<String> warnings, List<String> errors) {
            setText("");
            if(warnings == null || warnings.size() == 0) {
                append("No Warnings!\n", Lay.clr("[0,100,0]"));
            } else {
                append("Warnings\n", Color.black);
                append("========\n", Color.black);
                for(String warn : warnings) {
                    append("  " + warn, Color.yellow);
                }
            }
            if(errors == null || errors.size() == 0) {
                append("No Errors!\n", Lay.clr("[0,100,0]"));
            } else {
                append("Errors\n", Color.black);
                append("========\n", Color.black);
                for(String err : errors) {
                    append("  " + err, Color.red);
                }
            }
        }
    }


    ////////////
    // FIELDS //
    ////////////

    // UI

//    private EquationSummaryTreePanel pnlSummaryTree;
//    private JTextArea txtSummaryText;
    private SummaryGraphPanel pnlSummaryGraph;
    private EquationSummaryFlatPanel pnlSummaryFlat;
    private ProblemTextPane txtSummaryWarn;

    // Misc

    private boolean performUpdate;
    private boolean allowSummaryUpdates;


    ///////////////
    // NOTIFIERS //
    ///////////////

    protected ChangeNotifier summaryUpdateStartNotifier = new ChangeNotifier(this);
    protected ChangeNotifier summaryUpdateStopNotifier = new ChangeNotifier(this);

    public void addSummaryUpdateStartListener(ChangeListener listener) {
        summaryUpdateStartNotifier.addListener(listener);
    }
    public void addSummaryUpdateStopListener(ChangeListener listener) {
        summaryUpdateStopNotifier.addListener(listener);
    }
    protected void fireSummaryUpdateStartNotifier() {
        GUIUtil.safeSync(new Runnable() { //SafeSync?
            public void run() {
                summaryUpdateStartNotifier.fireStateChanged();
            }
        });
    }
    protected void fireSummaryUpdateStopNotifier() {
        GUIUtil.safeSync(new Runnable() { //SafeSync?
            public void run() {
                summaryUpdateStopNotifier.fireStateChanged();
            }
        });
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public SummaryDetailPanel(UIController uic, ModelOrient m) {
        super(uic, m);

//        final JRadioButton optTree = new MRadioButton("Tree");
//        final JRadioButton optText = new MRadioButton("Text");
        final JRadioButton optGraph = new MRadioButton("Graph");
        final JRadioButton optFlat = new MRadioButton("Flat");
        final JRadioButton optWarn = new MRadioButton("Problems");

        final CardLayout cl = new CardLayout();
        final JPanel pnlStack = new JPanel(cl);
//        pnlStack.add(pnlSummaryTree = new EquationSummaryTreePanel(model, false), optTree.getText());
//        pnlStack.add(Lay.sp(txtSummaryText = new JTextArea()), optText.getText());
        pnlStack.add(pnlSummaryGraph = new SummaryGraphPanel(/*dataModel*/), optGraph.getText());
        pnlStack.add(pnlSummaryFlat = new EquationSummaryFlatPanel(uiController, true), optFlat.getText());
        pnlStack.add(Lay.sp(txtSummaryWarn = new ProblemTextPane()), optWarn.getText());

//        txtSummaryText.setFont(new Font("Courier New", Font.PLAIN, 12));
//        txtSummaryText.setEditable(false);

        txtSummaryWarn.setFont(new Font("Courier New", Font.PLAIN, 12));
        txtSummaryWarn.setEditable(false);

        Lay.BLtg(this,
            "N", Lay.FL("L",
                createLabelPanel("Summary", "summary"),
                /*optTree, optText,*/ optGraph, optFlat,
                Lay.BL("C", optWarn, "E",Lay.lb(ImageUtil.getImage("warn.gif"))),
                "pref=[10,25],hgap=0,vgap=0"
            ),
            "C", pnlStack,
            "eb=10"
        );

//        optTree.setSelected(true);
        optGraph.setSelected(true);

        ItemListener itemL = new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                cl.show(pnlStack, ((JRadioButton) e.getSource()).getText());
            }
        };
//        optTree.addItemListener(itemL);
        optGraph.addItemListener(itemL);
        optFlat.addItemListener(itemL);
        optWarn.addItemListener(itemL);

        Lay.grp(/*optTree, optText, */optGraph, optFlat, optWarn);

        summaryUpdaterThread.setDaemon(true);
        summaryUpdaterThread.start();
    }

    public void setAllowSummaryUpdates(boolean allow) {
        allowSummaryUpdates = allow;
    }

    @Override
    public void reload() {
        if(allowSummaryUpdates) {
            synchronized(summaryUpdaterThread) {
                performUpdate = true;
                summaryUpdaterThread.notify();
            }
        }
    }

    private Thread summaryUpdaterThread = new Thread() {
        @Override
        public void run() {
            while(true) {
                fireSummaryUpdateStopNotifier();
                checkWait();
                fireSummaryUpdateStartNotifier();
                performUpdate();
                performUpdate = false;
                // Enforce a minimum time between updates.
                ThreadUtil.sleep(800);
            }
        }

        private synchronized void checkWait() {
            while(!performUpdate) {   // While paused essentially
                try {
                    summaryUpdaterThread.wait();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private void performUpdate() {

        GUIUtil.safeSync(new Runnable() { //SafeSync?
            public void run() {

                // Clear all the summary panels
        //        pnlSummaryTree.setStateClear();
        //        txtSummaryText.setText(NodeRoot.CALC);
                pnlSummaryFlat.setStateClear();
                pnlSummaryGraph.setStateClear();
                txtSummaryWarn.setText(NodeSummaryRoot.CALC);

                // Visual cues for update process.
                uiController.startProgressIndeterminate("Updating summary");
            }
        });
//            final PartEquationMap pMap = EquationAssembler.getAssembledPartEquations(dataModel, part);
        pnlSummaryFlat.setEquations(EquationSummaryFlatPanel.createFlatEquationListFromModel(model));

        GUIUtil.safeSync(new Runnable() { //SafeSync?
            public void run() {
                try {
//                    pnlSummaryTree.rebuild();
//                    txtSummaryText.setText(pMap.toString());
                    pnlSummaryGraph.setData(model.getLayers(), model.getBridges());
                    pnlSummaryGraph.rebuild();
                    pnlSummaryFlat.rebuild();
//                    txtSummaryWarn.reload(pMap.getWarnings(), pMap.getErrors());
                } catch(Exception e) {
        //            pnlSummaryTree.setStateError();
        //            txtSummaryText.setText(NodeRoot.ERROR);
                    pnlSummaryGraph.setStateError();
                    pnlSummaryFlat.setStateError();
                    txtSummaryWarn.setText(NodeSummaryRoot.ERROR);

                    String ERR = "An error has occurred updating the summary information.";
                    if(e instanceof DataModelLoopException) {
                        List<String> errors = new ArrayList<String>();
                        errors.add("A loop exists in the parent and/or include hierarchy.");
                        txtSummaryWarn.reload(null, errors);

                        DataModelLoopException dmle = (DataModelLoopException) e;
//                        LoopDialogUtil.showLoopDialog(dataModel, uiController, dmle, part.getType(), ERR);
                    } else {
                        UMF.handleUnexpectedError(null, e, ERR);
                    }

                } finally {
                    uiController.stopProgressIndeterminate();
                }
            }
        });
    }
}
