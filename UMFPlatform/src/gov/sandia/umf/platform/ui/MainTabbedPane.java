/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.AppState;
import gov.sandia.umf.platform.plugins.UMFPluginManager;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.swing.Icon;
import javax.swing.JTabbedPane;
import javax.swing.event.MouseInputAdapter;

public class MainTabbedPane extends JTabbedPane
{
    protected int       draggedTab;
    protected Icon      draggedIcon;
    protected String    draggedTitle;
    protected Component draggedComponent;
    protected String    draggedToolTip;
    protected int       lastX;

    public MainTabbedPane (UIController uic)
    {
        // Load all panels provided by plugins.
        // Create tabs in the order the user has specified.
        String order = AppState.getInstance ().getOrDefault ("Model", "MainTabbedPane", "order");
        Set<String> sorted = new HashSet<String> ();
        String[] titles = order.split (",");  // comma-separated list
        Map<String,RecordHandler> handlers = UMFPluginManager.recordHandlers;
        for (String title : titles)
        {
            RecordHandler handler = handlers.get (title);
            if (handler != null)
            {
                String name         = handler.getName ();
                Icon icon           = handler.getIcon ();
                Component component = handler.getComponent (uic);
                addTab (name, icon, component, null);
                sorted.add (title);
            }
        }
        for (Entry<String,RecordHandler> e : handlers.entrySet ())
        {
            if (sorted.contains (e.getKey ())) continue;
            RecordHandler handler = e.getValue ();
            String name         = handler.getName ();
            Icon icon           = handler.getIcon ();
            Component component = handler.getComponent (uic);
            addTab (name, icon, component, null);
        }

        setTabLayoutPolicy (SCROLL_TAB_LAYOUT);

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
                    AppState.getInstance ().set (order, "MainTabbedPane", "order");
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
                draggedTab = currentTab;
            }
        };
        addMouseListener       (mouseAdapter);
        addMouseMotionListener (mouseAdapter);
    }
}
