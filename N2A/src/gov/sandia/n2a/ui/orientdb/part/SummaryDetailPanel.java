/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.part;

import gov.sandia.n2a.eqset.DataModelLoopException;
import gov.sandia.n2a.eqset.EquationAssembler;
import gov.sandia.n2a.eqset.PartEquationMap;
import gov.sandia.n2a.ui.orientdb.eq.EquationSummaryFlatPanel;
import gov.sandia.n2a.ui.orientdb.eq.EquationSummaryTreePanel;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeSummaryRoot;
import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.XTextPane;
import replete.gui.controls.mnemonics.MRadioButton;
import replete.util.GUIUtil;
import replete.util.Lay;
import replete.util.ThreadUtil;

public class SummaryDetailPanel extends RecordEditDetailPanel {


    /////////////////
    // INNER CLASS //
    /////////////////

    private final class ProblemTextPane extends XTextPane {
        public void reload(List<String> warnings, List<String> errors) {
            setText("");
            append("Warnings\n", Color.black);
            append("========\n", Color.black);
            boolean endNl = false;
            if(warnings == null || warnings.size() == 0) {
                append("  No Warnings!\n", Lay.clr("[0,100,0]"));
                endNl = true;
            } else {
                for(String warn : warnings) {
                    append("  " + warn, Lay.clr("235,100,0"));
                    if(warn.endsWith("\n")) {
                        endNl = true;
                    }
                }
            }
            if(!endNl) {
                append("\n", Color.black);
            }
            append("\nErrors\n", Color.black);
            append("========\n", Color.black);
            if(errors == null || errors.size() == 0) {
                append("  No Errors!\n", Lay.clr("[0,100,0]"));
            } else {
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

    private EquationSummaryTreePanel pnlSummaryTree;
    private EquationSummaryFlatPanel pnlSummaryFlat;
    private JTextArea txtSummaryText;
    private SummaryGraphPanel pnlSummaryGraph;
    private ProblemTextPane txtSummaryWarn;

    // Misc

    private boolean performUpdate;
    private boolean allowSummaryUpdates;


    ///////////////
    // NOTIFIERS //
    ///////////////

    protected ChangeNotifier summaryUpdateStartNotifier = new ChangeNotifier(this);
    protected ChangeNotifier summaryUpdateStopNotifier = new ChangeNotifier(this);
    protected ChangeNotifier editEquationNotifier = new ChangeNotifier(this);

    public void addSummaryUpdateStartListener(ChangeListener listener) {
        summaryUpdateStartNotifier.addListener(listener);
    }
    public void addSummaryUpdateStopListener(ChangeListener listener) {
        summaryUpdateStopNotifier.addListener(listener);
    }
    public void addEditEquationListener(ChangeListener listener) {
        editEquationNotifier.addListener(listener);
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
    protected void fireEditEquationNotifier() {
        editEquationNotifier.fireStateChanged();
    }

    public NDoc getChangeOverrideEquation() {
        return pnlSummaryTree.getChangeOverrideEquation();
    }
    public String getChangeOverridePrefix() {
        return pnlSummaryTree.getChangeOverridePrefix();
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public SummaryDetailPanel(UIController uic, NDoc p) {
        super(uic, p);

        final JRadioButton optTree = new MRadioButton("Tree");
        final JRadioButton optFlat = new MRadioButton("Flat");
        final JRadioButton optText = new MRadioButton("Text");
        final JRadioButton optGraph = new MRadioButton("Graph");
        final JRadioButton optWarn = new MRadioButton("Problems");

        final CardLayout cl = new CardLayout();
        final JPanel pnlStack = new JPanel(cl);
        pnlStack.add(pnlSummaryTree = new EquationSummaryTreePanel(uiController, record, true), optTree.getText()); //TODO
        pnlStack.add(pnlSummaryFlat = new EquationSummaryFlatPanel(uiController, true), optFlat.getText());
        pnlStack.add(Lay.sp(txtSummaryText = new JTextArea()), optText.getText());

        pnlStack.add(pnlSummaryGraph = new SummaryGraphPanel(record), optGraph.getText());
        pnlStack.add(Lay.sp(txtSummaryWarn = new ProblemTextPane()), optWarn.getText());

        txtSummaryText.setFont(new Font("Courier New", Font.PLAIN, 12));
        txtSummaryText.setEditable(false);

        txtSummaryWarn.setFont(new Font("Courier New", Font.PLAIN, 12));
        txtSummaryWarn.setEditable(false);

        Lay.BLtg(this,
            "N", Lay.FL("L",
                createLabelPanel("Summary", "summary"),
                optTree, optFlat, optText, optGraph,
                Lay.BL("C", optWarn, "E",Lay.lb(ImageUtil.getImage("warn.gif"))),
                "pref=[10,25],hgap=0,vgap=0"
            ),
            "C", pnlStack,
            "eb=10"
        );

        optTree.setSelected(true);

        ItemListener itemL = new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                cl.show(pnlStack, ((JRadioButton) e.getSource()).getText());
            }
        };
        optTree.addItemListener(itemL);
        optFlat.addItemListener(itemL);
        optText.addItemListener(itemL);
        optGraph.addItemListener(itemL);
        optWarn.addItemListener(itemL);

        Lay.grp(optTree, optFlat, optText, optGraph, optWarn);

        summaryUpdaterThread.setDaemon(true);
        summaryUpdaterThread.start();

        pnlSummaryTree.addEditEquationListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                fireEditEquationNotifier();
            }
        });
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
                try {
                    performUpdate();
                } catch(Exception e) {

                }
                performUpdate = false;
                // Enforce a minimum time between updates.
                ThreadUtil.sleep(800);
            }
        };

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
                pnlSummaryTree.setStateClear();
                pnlSummaryFlat.setStateClear();
                txtSummaryText.setText(NodeSummaryRoot.CALC);
                txtSummaryWarn.setText(NodeSummaryRoot.CALC);
                pnlSummaryGraph.setStateClear();

                // Visual cues for update process.
                uiController.startProgressIndeterminate("Updating summary");
            }
        });

        // TODO: performUpdate called as app was shutting down - AFTER connection was closed!
        final PartEquationMap pMap = EquationAssembler.getAssembledPartEquations(record);
        pnlSummaryFlat.setEquations(EquationSummaryFlatPanel.createFlatEquationListFromPart(record));

        GUIUtil.safeSync(new Runnable() { //SafeSync?
            public void run() {
                try {
                    pnlSummaryTree.rebuild();
                    pnlSummaryFlat.rebuild();
                    txtSummaryText.setText("\n" + pMap.toString());
                    pnlSummaryGraph.rebuild();
                    List<String> errors = new ArrayList<String>();
                    List<String> warns = new ArrayList<String>();
                    Set<String> unks = pMap.getUnknownSymbols();
                    if(unks.size() != 0) {
                        warns.add("The following symbols are undefined:\n");
                        for(String unk : unks) {
                            warns.add("    " + unk + "\n");
                        }
                    }
                    txtSummaryWarn.reload(warns, errors);
                } catch(Exception e) {
                    pnlSummaryTree.setStateError();
                    pnlSummaryFlat.setStateError();
                    txtSummaryText.setText(NodeSummaryRoot.ERROR);
                    txtSummaryWarn.setText(NodeSummaryRoot.ERROR);
                    pnlSummaryGraph.setStateError();

                    String ERR = "An error has occurred updating the summary information.";
                    if(e instanceof DataModelLoopException) {
                        List<String> errors = new ArrayList<String>();
                        errors.add("A loop exists in the parent and/or include hierarchy.");
                        txtSummaryWarn.reload(null, errors);

                        DataModelLoopException dmle = (DataModelLoopException) e;
//                        LoopDialogUtil.showLoopDialog(uiController, dmle, part.getType(), ERR);
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
