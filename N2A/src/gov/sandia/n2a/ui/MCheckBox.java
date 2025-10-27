/*
Copyright 2022-2025 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import gov.sandia.n2a.db.MNode;

/**
    Text field that is bound to a particular MNode for editing.
    This current version assumes it is the only source of changes to the target node.
    It may be possible to add the ability to update the value each time the field becomes visible.
**/
@SuppressWarnings("serial")
public class MCheckBox extends JCheckBox
{
    protected MNode                parent;
    protected String               key;
    protected boolean              original;
    protected boolean              defaultValue;
    protected List<ChangeListener> listeners = new ArrayList<ChangeListener> ();

    public MCheckBox ()
    {
        this (null, "", "", false);
    }

    public MCheckBox (String description)
    {
        this (null, "", description, false);
    }

    public MCheckBox (MNode parent, String key, String description, boolean defaultValue)
    {
        super (description, defaultValue);
        bind (parent, key, defaultValue);

        addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                save ();
            }
        });
    }

    public void save ()
    {
        boolean current = isSelected ();
        if (current == original) return;
        if (parent != null)
        {
            if (current == defaultValue)
            {
                parent.clear (key);
            }
            else
            {
                parent.set (current, key);
            }
        }
        notifyChange ();
        original = current;
        if (current == defaultValue) setForeground (Color.blue);
        else                         setForeground (Color.black);
    }

    public void bind (MNode parent, String key)
    {
        bind (parent, key, false);
    }

    public void bind (MNode parent, String key, boolean defaultValue)
    {
        this.parent       = parent;
        this.key          = key;
        this.defaultValue = defaultValue;
        if (parent == null)
        {
            this.setSelected (false);
        }
        else
        {
            original = parent.getOrDefault (defaultValue, key);
            setSelected (original);
            if (original == defaultValue) setForeground (Color.blue);
            else                          setForeground (Color.black);
        }
    }

    /**
        Update the default value.
        If the current value matches the current default, then current value will also
        change, which may result in a call to changed(). Therefore, setDefault()
        must never be called from changed().
    **/
    public void setDefault (boolean newDefault)
    {
        boolean current = isSelected ();
        if (current == defaultValue)
        {
            setSelected (newDefault);
            notifyChange ();
            original = newDefault;
            // node remains unset
            // field remains blue
        }
        else if (current == newDefault)
        {
            if (parent != null) parent.clear (key);
            setForeground (Color.blue);
            // text and original remain the same
        }
        defaultValue = newDefault;
    }

    public boolean getOriginal ()
    {
        return original;
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
