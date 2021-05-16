/*
Copyright 2017-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;

import java.awt.Component;
import java.awt.Dimension;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

@SuppressWarnings("serial")
public class PanelSettings extends JTabbedPane
{
    protected int                            lastSelectedIndex = 0;
    protected Hashtable<Component,Component> lastFocus         = new Hashtable<Component,Component> ();
    protected int                            width;

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
        String[] titles = {"About", "General", "Look & Feel", "Repositories", "Hosts"};  // Force the order of these panels.
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

        // updateUI() on all child panels. Needed because Settings often construct their panel during plugin load time, which happens before L&F is set.
        // A better approach would be to require that each Settings plugin separate the construction of its panel from the construction of itself.
        // However, this has the advantage of simplicity. A Settings plugin can be a single class that does everything.
        SwingUtilities.updateComponentTreeUI (this);
    }

    public void addSettings (Settings s)
    {
        String name         = s.getName ();
        Icon icon           = s.getIcon ();
        Component component = s.getPanel ();
        lastFocus.put (component, s.getInitialFocus (component));
        addTab (name, icon, component, null);

        // Hack to avoid messing with vertical labels on Mac platform.
        // Other L&Fs might also have vertical labels, so this is an inadequate test, but it is not obvious how to check.
        if (Host.isMac ()  &&  SettingsLookAndFeel.instance.currentLaf.instance.isNativeLookAndFeel ()) return;

        JLabel label = new JLabel (name, icon, LEADING);
        width = Math.max (width, label.getPreferredSize ().width);
        JPanel title = new JPanel ()
        {
            public Dimension getPreferredSize ()
            {
                Dimension result = super.getPreferredSize ();
                result.width = width;
                return result;
            }
        };
        title.setOpaque (false);
        Lay.BLtg (title, "W", label);
        setTabComponentAt (getTabCount () - 1, title);
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
