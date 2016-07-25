/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
 */

package gov.sandia.n2a.ui.eq;

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

public class EquationInputBox extends EscapeDialog
{
    public enum Result
    {
        OK, CANCEL
    }

    private Result result = Result.CANCEL;

    public Result getResult ()
    {
        return result;
    }

    private SelectAllTextField txtEqn;
    private JLabel lblError;

    private Timer flashTimer = new Timer (500, new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            Lay.hn (lblError, "fg=red,plain");
        }
    });

    public EquationInputBox (JFrame parent, String initVal)
    {
        super (parent, "", true);
        init (initVal);
    }

    public EquationInputBox (JDialog parent, String initVal)
    {
        super (parent, "", true);
        init (initVal);
    }

    private void init (String initVal)
    {
        txtEqn = new SelectAllTextField ();
        txtEqn.setText (initVal);

        JButton btnOK = new MButton ("OK", ImageUtil.getImage ("save.gif"));
        btnOK.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent arg0)
            {
                result = Result.OK;
                dispose ();
            }
        });
        JButton btnCancel = new MButton ("&Cancel", ImageUtil.getImage ("cancel.gif"));
        btnCancel.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent arg0)
            {
                dispose ();
            }
        });

        lblError = Lay.lb (" ", "alignx=0.0,eb=5lbr,fg=red");
        lblError.setFont (lblError.getFont ().deriveFont (10));
        Lay.hn (txtEqn, "maxH=" + txtEqn.getPreferredSize ().height + 10);
        JPanel pnlText = Lay.BL (
            "W", Lay.lb (ImageUtil.getImage ("expr.gif"), "eb=5r"),
            "C", txtEqn,
            "alignx=0.0,eb=5lr"
        );
        Lay.hn (pnlText, "maxH=" + pnlText.getPreferredSize ().height + 10);

        flashTimer.setRepeats (false);

        getRootPane ().setDefaultButton (btnOK);
        Lay.match (btnOK, btnCancel);

        Lay.BLtg (this,
            "C", pnlText,
            "S", Lay.FL ("R", btnOK, btnCancel),
            "size=[450,175],center"
        );
    }

    public String getValue ()
    {
        return txtEqn.getText ().trim ();
    }
}
