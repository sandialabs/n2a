/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import gov.sandia.n2a.ui.MainFrame;

/**
    Handle user interaction for JSch.
    This class does a similar job to SettingsRepo.CredentialDialog
**/
public class ConnectionInfo implements UserInfo, UIKeyboardInteractive
{
    protected String      password   = "";
    protected String      passphrase = "";
    protected Set<String> messages = new HashSet<String> ();  // Remember messages, so we only display them once per session.
    protected boolean     triedPassword;   // The stored password has already been tried, so need to prompt user.
    protected boolean     triedPassphrase; // ditto for passphrase
    protected boolean     allowDialogs;    // Initially false. Dialogs must be specifically enabled by the user.

    public String getPassword ()
    {
        triedPassword = true;
        return password;
    }

    public String getPassphrase ()
    {
        triedPassphrase = true;
        return passphrase;
    }

    public JPasswordField makePasswordField (String value)
    {
        JPasswordField result = new JPasswordField (value, 20);
        result.addAncestorListener (new AncestorListener ()
        {
            public void ancestorRemoved (AncestorEvent event)
            {
            }

            public void ancestorMoved (AncestorEvent event)
            {
            }

            public void ancestorAdded (AncestorEvent event)
            {
                result.requestFocusInWindow ();
            }
        });
        return result;
    }

    public boolean promptPassword (String message)
    {
        if (! password.isEmpty ()  &&  ! triedPassword) return true;
        if (! allowDialogs) return false;
        JTextField field = makePasswordField (password);
        Object[] contents = {field};
        int result = JOptionPane.showConfirmDialog (MainFrame.instance, contents, message, JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION)
        {
            password = field.getText ();
            return true;
        }
        return false;
    }

    public boolean promptPassphrase (String message)
    {
        if (! passphrase.isEmpty ()  &&  ! triedPassphrase) return true;
        if (! allowDialogs) return false;
        JTextField field = makePasswordField (passphrase);
        Object[] contents = {field};
        int result = JOptionPane.showConfirmDialog (MainFrame.instance, contents, message, JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION)
        {
            passphrase = field.getText ();
            return true;
        }
        return false;
    }

    public boolean promptYesNo (String message)
    {
        if (! allowDialogs) return false;
        int result = JOptionPane.showConfirmDialog (MainFrame.instance, message, "Question", JOptionPane.YES_NO_OPTION);
        return result == JOptionPane.OK_OPTION;
    }

    public void showMessage (String message)
    {
        if (messages.contains (message)) return;
        messages.add (message);
        // TODO: let user view saved messages via sessions menu
    }

    public String[] promptKeyboardInteractive (String destination, String name, String instruction, String[] prompt, boolean[] echo)
    {
        if (! allowDialogs) return null;

        JPanel panel = new JPanel ();
        panel.setLayout (new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints
        (
            0, 0, 1, 1, 1, 1,
            GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
            new Insets (0, 0, 0, 0),
            0, 0
        );

        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.gridx = 0;
        panel.add (new JLabel(instruction), gbc);
        gbc.gridy++;

        gbc.gridwidth = GridBagConstraints.RELATIVE;

        JTextField[] texts = new JTextField[prompt.length];
        for (int i = 0; i < prompt.length; i++)
        {
            gbc.fill = GridBagConstraints.NONE;
            gbc.gridx = 0;
            gbc.weightx = 1;
            panel.add (new JLabel (prompt[i]), gbc);

            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 1;
            if (echo[i]) texts[i] = new JTextField(20);
            else         texts[i] = new JPasswordField(20);
            panel.add (texts[i], gbc);
            gbc.gridy++;
        }

        int result = JOptionPane.showConfirmDialog
        (
            MainFrame.instance, panel,
            destination + ": " + name,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        if (result == JOptionPane.OK_OPTION)
        {
            String[] response = new String[prompt.length];
            for (int i = 0; i < prompt.length; i++) response[i] = texts[i].getText ();
            return response;
        }
        return null; // cancel
    }
}
