/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.plugins.UMFPluginManager;
import gov.sandia.n2a.plugins.extpoints.RecordHandler;

import java.awt.Component;
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
import java.util.Map.Entry;

import javax.swing.Icon;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MouseInputAdapter;

public class MainTabbedPane extends JTabbedPane
{
    protected int       draggedTab;
    protected Icon      draggedIcon;
    protected String    draggedTitle;
    protected Component draggedComponent;
    protected String    draggedToolTip;
    protected int       draggedMnemonic;
    protected int       lastX;
    protected int       lastSelectedIndex = 0;

    protected Hashtable<Component,Component> lastFocus = new Hashtable<Component,Component> ();

    public MainTabbedPane ()
    {
        // Load all panels provided by plugins.
        // Create tabs in the order the user has specified.
        String order = AppData.state.getOrDefault ("MainTabbedPane", "order", "Model");
        Set<String> sorted = new HashSet<String> ();
        String[] titles = order.split (",");  // comma-separated list
        Map<String,RecordHandler> handlers = UMFPluginManager.getRecordHandlers ();
        for (String title : titles)
        {
            RecordHandler handler = handlers.get (title);
            if (handler != null)
            {
                String name         = handler.getName ();
                Icon icon           = handler.getIcon ();
                Component component = handler.getPanel ();
                lastFocus.put (component, handler.getInitialFocus (component));
                addTab (name, icon, component, null);
                setMnemonicAt (getTabCount () - 1, KeyEvent.getExtendedKeyCodeForChar (name.charAt (0)));
                sorted.add (title);
            }
        }
        for (Entry<String,RecordHandler> e : handlers.entrySet ())
        {
            if (sorted.contains (e.getKey ())) continue;
            RecordHandler handler = e.getValue ();
            String name         = handler.getName ();
            Icon icon           = handler.getIcon ();
            Component component = handler.getPanel ();
            lastFocus.put (component, handler.getInitialFocus (component));
            addTab (name, icon, component, null);
            setMnemonicAt (getTabCount () - 1, KeyEvent.getExtendedKeyCodeForChar (name.charAt (0)));
        }

        // Drag and drop to rearrange tab order.
        MouseInputAdapter mouseAdapter = new MouseInputAdapter ()
        {
            @Override
            public void mousePressed (MouseEvent e)
            {
                lastX = e.getX ();
                draggedTab = indexAtLocation (lastX, e.getY ());
                if (draggedTab >= 0)
                {
                    draggedTitle     = getTitleAt       (draggedTab);
                    draggedIcon      = getIconAt        (draggedTab);
                    draggedComponent = getComponentAt   (draggedTab);
                    draggedToolTip   = getToolTipTextAt (draggedTab);
                    draggedMnemonic  = getMnemonicAt    (draggedTab);
                    setSelectedIndex (-1);
                }
            }

            @Override
            public void mouseReleased (MouseEvent e)
            {
                if (draggedTab >= 0)
                {
                    setSelectedIndex (draggedTab);
                    draggedTab = -1;

                    String order = getTitleAt (0);
                    for (int i = 1; i < getTabCount (); i++) order += "," + getTitleAt (i);
                    AppData.state.set ("MainTabbedPane", "order", order);
                }
            }

            @Override
            public void mouseDragged (MouseEvent e)
            {
                if (draggedTab < 0) return;
                int currentX = Math.min (getWidth (), Math.max (0, e.getX ()));
                int currentTab = indexAtLocation (currentX, 10);
                if (currentTab < 0) return;
                if (currentTab == draggedTab) return;
                if ((currentTab - draggedTab) * (currentX - lastX) < 0) return;  // Only move tab in same direction as drag. Prevents thrashing in case where tabs are different widths.

                removeTabAt (draggedTab);
                insertTab (draggedTitle, draggedIcon, draggedComponent, draggedToolTip, currentTab);
                setMnemonicAt (currentTab, draggedMnemonic);
                draggedTab = currentTab;
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
                return getComponent (i);
            }
        }
        return null;
    }
}
