/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble.run;

import gov.sandia.n2a.execenvs.HostSystem;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.ui.ensemble.ParameterDetailsPanel;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.Dialogs;
import replete.gui.windows.EscapeDialog;
import replete.util.Lay;
import replete.util.StringUtil;

public class CreateRunEnsembleDialog extends EscapeDialog implements HelpCapableWindow {


    ////////////
    // FIELDS //
    ////////////

    // Const

    public static final int CREATE = 0;
    public static final int CANCEL = 1;

    // Core

    private Object model;

    // UI

    private MainGlassPane glassPane;
    private RunPanel pnlRun;

    // Misc
    public int result = CANCEL;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public CreateRunEnsembleDialog(JFrame parent, final long estDur,
            /*TEMP*/String modName, String modOwner, long modLm,/*TEMP*/
            final Object model, Backend[] simulators, Backend defaultSimulator,
            HostSystem[] envs, HostSystem defaultEnv, final boolean askNoOutputs) {

        super(parent, "Create New Run Ensemble", true);

        this.model = model;
        setIconImage(ImageUtil.getImage("runensadd.gif").getImage());

        final MButton btnCreate = new MButton("C&reate", ImageUtil.getImage("run.gif"));
        btnCreate.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if(askNoOutputs && pnlRun.getSelectedOutputExpressions().size() == 0) {
                    if(!Dialogs.showConfirm(CreateRunEnsembleDialog.this,
                            "You have no selected outputs.  Continue with run ensemble creation?",
                            "Continue?")) {
                        return;
                    }
                }
                ParameterSpecGroupSet groups = pnlRun.getParameterSpecGroupSet();
                long runs = groups.getRunCount();
                String timing = "";
                if(estDur >= 0) {
                    timing = "<br>and will take approximately <font color='blue' size='+1'>" +
                        ParameterDetailsPanel.getElapsedEnsembleEstimate(runs, estDur) +
                        "</font>";
                }
                String s = "<html>This will create <font color='blue' size='+1'>" + runs + "</font> run" +
                    StringUtil.s(runs) + " on <font color='blue' size='+1'>" + pnlRun.getEnvironment().name +
                    "</font> using <font color='blue' size='+1'>" + pnlRun.getSimulator().getName() +
                    "</font>" + timing + ".<br><br>Do you want to continue?</html>";
                int answer = JOptionPane.showConfirmDialog(
                    CreateRunEnsembleDialog.this,
                    Lay.lb(s), "Confirm Run Ensemble Creation",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if(answer == JOptionPane.NO_OPTION) {
                    return;
                }
                result = CREATE;
                closeDialog();
            }
        });

        MButton btnCancel = new MButton("&Cancel", ImageUtil.getImage("cancel.gif"));
        btnCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                closeDialog();
            }
        });

        glassPane = new MainGlassPane ();
        glassPane.setVisible(false);
        setGlassPane(glassPane);

        final JLabel lblError;
        Lay.BLtg(this,
            "C", pnlRun = new RunPanel(this, estDur, modName, modOwner, modLm,
                model, simulators, defaultSimulator, envs, defaultEnv),
            "S", Lay.BL(
                "C", lblError = Lay.lb(" "),
                "E", Lay.FL(btnCreate, btnCancel),
                "eb=2"
            ),
            "size=[800,600],center"
        );

        pnlRun.addErrorListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if(pnlRun.getError() == null) {
                    lblError.setIcon(null);
                    lblError.setText("");
                    lblError.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    btnCreate.setEnabled(true);
                    lblError.removeMouseListener(errorClickListener);
                } else {
                    lblError.setIcon(ImageUtil.getImage("error.gif"));
                    lblError.setText(pnlRun.getError().getMessage());
                    lblError.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    lblError.addMouseListener(errorClickListener);
                    btnCreate.setEnabled(false);
                }
            }
        });

        getContentPane().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int border = 8;
                HelpNotesPanel pnlHelpNotes = glassPane.getHelpNotesPanel();
                pnlHelpNotes.setLocation(
                    border,
                        getContentPane().getSize().height +
                        getContentPane().getY() -
                        pnlHelpNotes.getHeight() -
                        border
                    );
            }
        });

        setDefaultButton(btnCreate);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                pnlRun.focus();
            }
        });
    }

    private MouseListener errorClickListener = new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
            Dialogs.showDetails(pnlRun, pnlRun.getError().getMessage(), "Error", pnlRun.getError().getCause());
        }
    };


    ///////////////
    // ACCESSORS //
    ///////////////

    // Cancel / Create
    public int getResult() {
        return result;
    }

    // Key dialog results
    public String getLabel() {
        return pnlRun.getLabel();
    }
    public HostSystem getEnvironment() {
        return pnlRun.getEnvironment();
    }
    public Backend getSimulator() {
        return pnlRun.getSimulator();
    }
    public ParameterSpecGroupSet getParameterSpecGroupSet() {
        return pnlRun.getParameterSpecGroupSet();
    }
    public List<String> getSelectedOutputExpressions() {
        return pnlRun.getSelectedOutputExpressions();
    }


    //////////
    // MISC //
    //////////

    protected JPanel createLabelPanel(String text, String helpKey) {
        return HelpLabels.createLabelPanel(this, text, helpKey);
    }
    public void showHelp(String topic, String content) {
        glassPane.showHelp(topic, content);
    }
}
