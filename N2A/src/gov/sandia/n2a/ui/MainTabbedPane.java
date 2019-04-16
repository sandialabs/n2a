/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Activity;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MouseInputAdapter;

@SuppressWarnings("serial")
public class MainTabbedPane extends JTabbedPane
{
    protected boolean   dragged;  // Becomes true if a mouse dragged event is received.
    protected int       draggedTab;
    protected Icon      draggedIcon;
    protected String    draggedTitle;
    protected Component draggedComponent;
    protected String    draggedToolTip;
    protected int       draggedMnemonic;
    protected Cursor    savedCursor;  // replaced during drag
    protected int       lastSelectedIndex = 0;

    protected Hashtable<Component,Component> lastFocus = new Hashtable<Component,Component> ();

    public MainTabbedPane ()
    {
        // Load all panels provided by plugins.
        // Create tabs in the order the user has specified.
        String order = AppData.state.getOrDefault ("Model", "MainTabbedPane", "order");
        Set<String> sorted = new HashSet<String> ();
        String[] titles = order.split (",");  // comma-separated list
        Map<String,Activity> activities = new TreeMap<String,Activity> ();
        for (ExtensionPoint ep : PluginManager.getExtensionsForPoint (Activity.class))
        {
            Activity a = (Activity) ep;
            activities.put (a.getName (), a);
        }
        for (String title : titles)
        {
            Activity a = activities.get (title);
            if (a != null)
            {
                String name         = a.getName ();
                Icon icon           = a.getIcon ();
                Component component = a.getPanel ();
                lastFocus.put (component, a.getInitialFocus (component));
                addTab (name, icon, component, null);
                setMnemonicAt (getTabCount () - 1, KeyEvent.getExtendedKeyCodeForChar (name.charAt (0)));
                sorted.add (title);
            }
        }
        for (Entry<String,Activity> e : activities.entrySet ())
        {
            if (sorted.contains (e.getKey ())) continue;
            Activity a = e.getValue ();
            String name         = a.getName ();
            Icon icon           = a.getIcon ();
            Component component = a.getPanel ();
            lastFocus.put (component, a.getInitialFocus (component));
            addTab (name, icon, component, null);
            setMnemonicAt (getTabCount () - 1, KeyEvent.getExtendedKeyCodeForChar (name.charAt (0)));
        }

        // Drag and drop to rearrange tab order.
        MouseInputAdapter mouseAdapter = new MouseInputAdapter ()
        {
            @Override
            public void mousePressed (MouseEvent e)
            {
                dragged = false;
                draggedTab = indexAtLocation (e.getX (), e.getY ());
                if (draggedTab >= 0)
                {
                    draggedTitle     = getTitleAt       (draggedTab);
                    draggedIcon      = getIconAt        (draggedTab);
                    draggedComponent = getComponentAt   (draggedTab);
                    draggedToolTip   = getToolTipTextAt (draggedTab);
                    draggedMnemonic  = getMnemonicAt    (draggedTab);
                }
            }

            @Override
            public void mouseReleased (MouseEvent e)
            {
                if (dragged  &&  draggedTab >= 0)
                {
                    int currentX = Math.min (getWidth (), Math.max (0, e.getX ()));
                    int currentTab = indexAtLocation (currentX, 10);
                    if (currentTab >= 0  &&  currentTab != draggedTab)
                    {
                        removeTabAt (draggedTab);
                        insertTab (draggedTitle, draggedIcon, draggedComponent, draggedToolTip, currentTab);
                        setMnemonicAt (currentTab, draggedMnemonic);
                        setSelectedIndex (currentTab);

                        String order = getTitleAt (0);
                        for (int i = 1; i < getTabCount (); i++) order += "," + getTitleAt (i);
                        AppData.state.set (order, "MainTabbedPane", "order");
                    }
                    setCursor (savedCursor);
                }
                dragged = false;
            }

            @Override
            public void mouseDragged (MouseEvent e)
            {
                if (! dragged  &&  draggedTab >= 0)  // drag is initiating, so change cursor
                {
                    savedCursor = getCursor ();
                    java.awt.Toolkit toolkit = java.awt.Toolkit.getDefaultToolkit ();
                    setCursor (toolkit.createCustomCursor (((ImageIcon) draggedIcon).getImage (), new java.awt.Point (0, 0), ""));
                }
                dragged = true;
            }
        };
        addMouseListener       (mouseAdapter);
        addMouseMotionListener (mouseAdapter);

        KeyboardFocusManager.getCurrentKeyboardFocusManager ().addPropertyChangeListener (new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                if (e.getPropertyName ().equals ("permanentFocusOwner"))
                {
                    int index = getSelectedIndex ();
                    if (index < 0) return;
                    Object value = e.getNewValue ();
                    if (value instanceof Component  &&  MainTabbedPane.this.isAncestorOf ((Component) value))
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
        setFocusCycleRoot (true);
        resetFocus ();
    }

    public void resetFocus ()
    {
        if (lastSelectedIndex < 0) return;
        Component panel = getComponentAt (lastSelectedIndex);
        Component c = lastFocus.get (panel);
        if (c == null) c = panel;
        c.requestFocusInWindow ();
    }

    public void setPreferredFocus (Component panel, Component c)
    {
        lastFocus.put (panel, c);
    }

    public Component selectTab (String tabName)
    {
        for (int i = 0; i < getTabCount (); i++)
        {
            if (getTitleAt (i).equals (tabName))
            {
                setSelectedIndex (i);
                return getComponentAt (i);
            }
        }
        return null;
    }
}
