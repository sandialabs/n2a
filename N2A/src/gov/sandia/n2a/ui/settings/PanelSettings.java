/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Settings;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import javax.swing.Icon;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

// TODO: force tabs to render left-aligned, as described in this post: http://stackoverflow.com/questions/26308859/jtabbedpane-tab-placement-set-to-left-but-icons-are-not-aligned
public class PanelSettings extends JTabbedPane
{
    protected int                            lastSelectedIndex = 0;
    protected Hashtable<Component,Component> lastFocus         = new Hashtable<Component,Component> ();

    public PanelSettings ()
    {
        // Load all panels provided by plugins.
        Map<String,Settings> settings = new TreeMap<String,Settings> ();
        for (ExtensionPoint ep : PluginManager.getExtensionsForPoint (Settings.class))
        {
            Settings s = (Settings) ep;
            settings.put (s.getName (), s);
        }

        Set<String> sorted = new HashSet<String> ();
        String[] titles = {"About", "Backup"};  // Force the order of these panels.
        for (String title : titles)
        {
            Settings s = settings.get (title);
            if (s != null)
            {
                addSettings (s);
                sorted.add (title);
            }
        }
        for (Entry<String,Settings> e : settings.entrySet ())
        {
            if (sorted.contains (e.getKey ())) continue;
            addSettings (e.getValue ());
        }

        KeyboardFocusManager.getCurrentKeyboardFocusManager ().addPropertyChangeListener (new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                if (e.getPropertyName ().equals ("permanentFocusOwner"))
                {
                    int index = getSelectedIndex ();
                    if (index < 0) return;
                    Object value = e.getNewValue ();
                    if (value instanceof Component  &&  PanelSettings.this.isAncestorOf ((Component) value))
                    {
                        lastFocus.put (getComponentAt (index), (Component) value);
                    }
                }
            }
        });

        addChangeListener (new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                int newIndex = getSelectedIndex ();
                if (newIndex < 0) return;
                if (newIndex != lastSelectedIndex)
                {
                    lastSelectedIndex = newIndex;
                    resetFocus ();
                }
            }
        });

        addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                resetFocus ();
            }

            public void focusLost (FocusEvent e)
            {
            }
        });

        setTabLayoutPolicy (SCROLL_TAB_LAYOUT);
        setTabPlacement (LEFT);
        setOpaque (true);
        setFocusCycleRoot (true);
        resetFocus ();
    }

    public void addSettings (Settings s)
    {
        String name         = s.getName ();
        Icon icon           = s.getIcon ();
        Component component = s.getPanel ();
        lastFocus.put (component, s.getInitialFocus (component));
        addTab (name, icon, component, null);
        //setMnemonicAt (getTabCount () - 1, KeyEvent.getExtendedKeyCodeForChar (name.charAt (0)));
    }

    public void resetFocus ()
    {
        if (lastSelectedIndex < 0) return;
        Component panel = getComponentAt (lastSelectedIndex);
        Component c = lastFocus.get (panel);
        if (c == null) c = panel;
        c.requestFocusInWindow ();
    }
}
