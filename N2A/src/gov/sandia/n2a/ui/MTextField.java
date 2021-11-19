/*
Copyright 2020-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import gov.sandia.n2a.db.MNode;

/**
    Text field that is bound to a particular MNode for editing.
    This current version assumes it is the only source of changes to the target node.
    It may be possible to add the ability to update the value each time the field becomes visible.
**/
@SuppressWarnings("serial")
public class MTextField extends NTextField
{
    protected MNode                parent;
    protected String               key;
    protected String               original = "";
    protected boolean              editKey;  // False to edit contents of node[key]. True to edit key itself.
    protected boolean              overwrite;  // If editKey is true and this field is true, then a name change can overwrite and existing key. The default (false) is to modify the key to avoid collision.
    protected String               defaultValue;
    protected List<ChangeListener> listeners = new ArrayList<ChangeListener> ();

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
                if (! overwrite)  // check for collision
                {
                    current = current.trim ();
                    String prefix = current;
                    int suffix = 2;
                    while (parent.child (current) != null) current = prefix + suffix++;
                    if (! current.equals (prefix)) setText (current);
                }
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
        notifyChange ();
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
        if (parent == null)
        {
            setText ("");
        }
        else
        {
            if (editKey) original = key;
            else         original = parent.getOrDefault (defaultValue, key);
            setText (original);
            if (original.equals (defaultValue)) setForeground (Color.blue);
            else                                setForeground (Color.black);
        }
    }

    /**
        Update the default value.
        If the current value matches the current default, then current value will also
        change, which may result in a call to changed(). Therefore, setDefault()
        must never be called from changed().
    **/
    public void setDefault (String newDefault)
    {
        String current = getText ();
        if (current.equals (defaultValue))
        {
            setText (newDefault);
            notifyChange ();
            original = newDefault;
            // node remains unset
            // field remains blue
        }
        else if (current.equals (newDefault))
        {
            if (parent != null  &&  ! editKey) parent.clear (key);
            setForeground (Color.blue);
            // text and original remain the same
        }
        defaultValue = newDefault;
    }

    public String getOriginal ()
    {
        return original;
    }

    public void setOverwrite (boolean value)
    {
        overwrite = value;
    }

    public void addChangeListener (ChangeListener l)
    {
        if (listeners == null) listeners = new ArrayList<ChangeListener> ();
        if (! listeners.contains (l)) listeners.add (l);
    }

    public void removeChangeListener (ChangeListener l)
    {
        if (listeners == null) return;
        listeners.remove (l);
    }

    public void notifyChange ()
    {
        if (listeners == null) return;
        ChangeEvent e = new ChangeEvent (this);
        for (ChangeListener l : listeners) l.stateChanged (e);
    }
}
