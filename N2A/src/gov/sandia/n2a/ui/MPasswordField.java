/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.JPasswordField;

import gov.sandia.n2a.db.MNode;

/**
    Password field that is bound to a particular MNode for editing.
    See MTextField for related comments.
**/
@SuppressWarnings("serial")
public class MPasswordField extends JPasswordField
{
    protected MNode  parent;
    protected String key;
    protected String original = "";

    public MPasswordField ()
    {
        this (null, "", 20);
    }

    public MPasswordField (int columns)
    {
        this (null, "", columns);
    }

    public MPasswordField (MNode parent, String key)
    {
        this (parent, key, 20);
    }

    public MPasswordField (MNode parent, String key, int columns)
    {
        super (columns);
        bind (parent, key);

        ActionMap actionMap = getActionMap ();
        actionMap.put ("Cancel", new AbstractAction ("Cancel")
        {
            public void actionPerformed (ActionEvent evt)
            {
                setText (original);
            }
        });

        addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                save ();
            }
        });

        addFocusListener (new FocusAdapter ()
        {
            public void focusLost (FocusEvent e)
            {
                save ();
            }
        });
    }

    public void save ()
    {
        String current = new String (getPassword ());
        if (current.equals (original)) return;
        if (parent != null)
        {
            if (current.isEmpty ()) parent.clear (key);
            else                    parent.set (current, key);
        }
        changed (original, current);
        original = current;
    }

    public void bind (MNode parent, String key)
    {
        this.parent = parent;
        this.key    = key;
        if (parent != null)
        {
            original = parent.get (key);
            setText (original);
        }
    }

    /**
        Override this function to do additional work when field value is changed.
        The default implementation is empty. This is simply a way for the programmer
        to act on the change event.
    **/
    public void changed (String before, String after)
    {
    }
}
