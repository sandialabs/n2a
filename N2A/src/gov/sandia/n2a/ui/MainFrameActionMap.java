/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.plugins.extpoints.MenuItems;
import gov.sandia.n2a.plugins.extpoints.UMFMenuBarActionDescriptor;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;

import replete.gui.lafbasic.LafManager;
import replete.gui.uiaction.MenuBarActionDescriptor;
import replete.gui.uiaction.ToolBarActionDescriptor;
import replete.gui.uiaction.UIAction;
import replete.gui.uiaction.UIActionMap;
import replete.logging.LogViewer;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;
import replete.plugins.ui.PluginDialog;
import replete.util.ReflectionUtil;


public class MainFrameActionMap extends UIActionMap
{
    public MainFrameActionMap ()
    {
        Map<String, Boolean> allEnabledStateMap = new HashMap<String, Boolean>();
        allEnabledStateMap.put("ALL", true);

        // FILE //

        UIAction action = new UIAction ("fileMenu");
        action.addDescriptor (new MenuBarActionDescriptor ("", "File", 'F', null, allEnabledStateMap, false, 0, false, null));
        addAction(action);

        action = new UIAction ("save");
        ActionListener listener = new ActionListener ()
        {
            public void actionPerformed(ActionEvent e)
            {
                AppData.save ();
            }
        };
        action.addDescriptor (new MenuBarActionDescriptor ("fileMenu", "Save", 'S', i("save.gif"), allEnabledStateMap, false, 'S', true, listener));
        action.addDescriptor (new ToolBarActionDescriptor ("fileMenu", "Save",      i("save.gif"), allEnabledStateMap, false,            listener));
        addAction (action);

        action = new UIAction ("backup");
        listener = new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                new BackupDialog (MainFrame.getInstance ()).setVisible (true);
            }
        };
        action.addDescriptor (new MenuBarActionDescriptor ("fileMenu", "Backup...", 0, i ("saveall.gif"), allEnabledStateMap, listener));
        addAction (action);

        ActionListener exitListener = new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                MainFrame.getInstance ().closeWindow ();
            }
        };
        action = new UIAction("exitProgram");
        action.addDescriptor(new MenuBarActionDescriptor("fileMenu", "Exit", 'x', i("exit.gif"),
            allEnabledStateMap, false, 0, true, exitListener));
        addAction(action);

        action = new UIAction(sepId());
        action.addDescriptor(new MenuBarActionDescriptor());
        addAction(action);

        // L&F //

        // TODO: Move to window menu.
        action = new UIAction("lafMenu");
        action.addDescriptor(new MenuBarActionDescriptor("", LafManager.createLafMenu(), allEnabledStateMap));
        addAction(action);

        // HELP //

        action = new UIAction("helpMenu");
        action.addDescriptor(new MenuBarActionDescriptor("", "Help", 'H', null,
            allEnabledStateMap, false, 0, false, null));
        addAction(action);

        ActionListener logListener = new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                new LogViewer (MainFrame.getInstance ()).setVisible (true);
            }
        };
        action = new UIAction("logViewer");
        action.addDescriptor(new MenuBarActionDescriptor("helpMenu", "Log Viewer", 'L', i("log.gif"),
            allEnabledStateMap, false, 0, false, logListener));
        addAction(action);

        ActionListener pluginsListener = new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                new PluginDialog (MainFrame.getInstance ()).setVisible (true);
            }
        };
        action = new UIAction("plugins");
        action.addDescriptor(new MenuBarActionDescriptor("helpMenu", "Plug-ins", 'P', i("connect.gif"),
            allEnabledStateMap, false, 0, false, pluginsListener));
        addAction(action);

        ActionListener aboutListener = new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                new AboutDialog (MainFrame.getInstance ()).setVisible (true);
            }
        };

        action = new UIAction("about");
        action.addDescriptor(new MenuBarActionDescriptor("helpMenu", "About", 'A', i("about.gif"),
            allEnabledStateMap, false, 0, false, aboutListener));
        addAction(action);

        List<ExtensionPoint> exts = PluginManager.getExtensionsForPoint(MenuItems.class);
        for(ExtensionPoint ext : exts) {
            MenuItems menuItems = (MenuItems) ext;
            Map<String, UMFMenuBarActionDescriptor> menuDescs = menuItems.getMenuItems();
            for(String actionName : menuDescs.keySet()) {
                final UMFMenuBarActionDescriptor d = menuDescs.get(actionName);
                ReflectionUtil.set("listener", d, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        d.getUmfListener().actionPerformed (e);
                    }
                });
                action = new UIAction(actionName);
                action.addDescriptor(d);
                addAction(action);
            }
        }
    }

    protected ImageIcon i(String iconFileName) {
        return ImageUtil.getImage(iconFileName);
    }

    private int sepId = 0;
    private String sepId() {
        return "separator" + (sepId++);
    }
}
