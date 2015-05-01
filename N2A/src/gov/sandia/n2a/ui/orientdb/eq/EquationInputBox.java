/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.eq;

import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import replete.gui.controls.SelectAllTextField;
import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.Dialogs;
import replete.gui.windows.EscapeDialog;
import replete.util.Lay;

public class EquationInputBox extends EscapeDialog {
    public enum Result {
        OK,
        CANCEL
    }

    private Result result = Result.CANCEL;
    public Result getResult() {
        return result;
    }

    private SelectAllTextField txtEqn;
    private JLabel lblError;
    private boolean isAnnotation;

    private Timer flashTimer = new Timer(500, new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            Lay.hn(lblError, "fg=red,plain");
        }
    });

    public EquationInputBox(JFrame parent, boolean isEdit, boolean annot, String initVal) {
        super(parent, "", true);
        init(isEdit, annot, initVal);
    }
    public EquationInputBox(JDialog parent, boolean isEdit, boolean annot, String initVal) {
        super(parent, "", true);
        init(isEdit, annot, initVal);
    }
    private void init(boolean isEdit, boolean annot, String initVal) {
        isAnnotation = annot;

        setTitle((isEdit ? "Edit" : "Add") + " " + (isAnnotation ? "Annotation" : "Equation"));
        setIconImage(ImageUtil.getImage("expr.gif").getImage());

        txtEqn = new SelectAllTextField();
        if(initVal != null) {
            txtEqn.setText(initVal);
        }

        String icon = isEdit ? "change.gif" : "add.gif";
        JButton btnOK = new MButton(isEdit ? "Ch&ange" : "&Add", ImageUtil.getImage(icon));
        btnOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                result = Result.OK;
                dispose();
            }
        });
        JButton btnCancel = new MButton("&Cancel", ImageUtil.getImage("cancel.gif"));
        btnCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                dispose();
            }
        });

        String message = "Please enter a value for this " + (isAnnotation ? "annotation." : "equation.");
        lblError = Lay.lb(" ", "alignx=0.0,eb=5lbr,fg=red");
        lblError.setFont(lblError.getFont().deriveFont(10));
        Lay.hn(txtEqn, "maxH=" + txtEqn.getPreferredSize().height + 10);
        icon = isAnnotation ? "about.gif" : "expr.gif";
        JPanel pnlText = Lay.BL(
            "W", Lay.lb(ImageUtil.getImage(icon), "eb=5r"),
            "C", txtEqn,
            "alignx=0.0,eb=5lr"
        );
        Lay.hn(pnlText, "maxH=" + pnlText.getPreferredSize().height + 10);

        JLabel lblIcon = new JLabel(getIconForType(JOptionPane.QUESTION_MESSAGE));
        lblIcon.setVerticalAlignment(SwingConstants.TOP);
        flashTimer.setRepeats(false);

        getRootPane().setDefaultButton(btnOK);
        if(isEdit) {
            Lay.match(btnOK, btnCancel);
        } else {
            Lay.match(btnCancel, btnOK);
        }

        Lay.BLtg(this,
            "C", Lay.BL(
                "W", Lay.augb(lblIcon, Lay.eb("8t5lb")),
                "C", Lay.BxL("Y",
                    Lay.lb("<html>" + message + "</html>", "alignx=0.0,eb=5"),
                    pnlText,
                    lblError,
                    Box.createVerticalGlue()
                )
            ),
            "S", Lay.FL("R", btnOK, btnCancel),
            "size=[450,175],center"
        );
    }

    // Stolen from BasicOptionPaneUI (related to JOptionPane).
    protected Icon getIconForType(int messageType) {
        if(messageType < 0 || messageType > 3) {
            return null;
        }
        String propertyName = null;
        switch(messageType) {
            case 0: propertyName = "OptionPane.errorIcon";       break;
            case 1: propertyName = "OptionPane.informationIcon"; break;
            case 2: propertyName = "OptionPane.warningIcon";     break;
            case 3: propertyName = "OptionPane.questionIcon";    break;
        }
        if(propertyName != null) {
            return UIManager.getIcon(propertyName);
        }
        return null;
    }

    public String getValue() {
        return txtEqn.getText().trim();
    }

    public static void main(String[] args) {
        EquationInputBox input = new EquationInputBox((JDialog) null, false, false, "init val!");
        input.setVisible(true);
        if(input.getResult() == EquationInputBox.Result.OK) {
            Dialogs.showMessage(input.getValue());
        }
    }
}
