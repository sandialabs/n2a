/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;

import gov.sandia.n2a.db.MNode;

/**
    Text field that is bound to a particular MNode for editing.
    This current version assumes it is the only source of changes to the target node.
    It may be possible to add the ability to update the value each time the field becomes visible.
**/
@SuppressWarnings("serial")
public class MTextField extends NTextField
{
    protected MNode   parent;
    protected String  key;
    protected String  original = "";
    protected boolean editKey;  // False to edit contents of node[key]. True to edit key itself.
    protected String  defaultValue;

    public MTextField ()
    {
        this (null, "", "", 20, false);
    }

    public MTextField (int columns)
    {
        this (null, "", "", columns, false);
    }

    public MTextField (MNode parent, String key)
    {
        this (parent, key, "", 20, false);
    }

    public MTextField (MNode parent, String key, String defaultValue)
    {
        this (parent, key, defaultValue, 20, false);
    }

    public MTextField (MNode parent, String key, String defaultValue, int columns, boolean editKey)
    {
        super (columns);
        this.editKey = editKey;
        bind (parent, key, defaultValue);

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
        String current = getText ();
        if (current.equals (original)) return;
        if (current.isEmpty ()) current = defaultValue;  // revert to default if field is cleared
        if (parent != null)
        {
            if (editKey)
            {
                parent.move (original, current);
            }
            else
            {
                if (current.equals (defaultValue))
                {
                    parent.clear (key);
                    setText (defaultValue);
                }
                else
                {
                    parent.set (current, key);
                }
            }
        }
        changed (original, current);
        original = current;
        if (current.equals (defaultValue)) setForeground (Color.blue);
        else                               setForeground (Color.black);
    }

    public void bind (MNode parent, String key)
    {
        bind (parent, key, "");
    }

    public void bind (MNode parent, String key, String defaultValue)
    {
        this.parent       = parent;
        this.key          = key;
        this.defaultValue = defaultValue;
        if (parent != null)
        {
            if (editKey) original = key;
            else         original = parent.getOrDefault (defaultValue, key);
            setText (original);
            if (original.equals (defaultValue)) setForeground (Color.blue);
            else                                setForeground (Color.black);
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
