/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.plugins.extpoints.MenuItems;
import gov.sandia.umf.platform.plugins.extpoints.UMFMenuBarActionDescriptor;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;

import replete.gui.lafbasic.LafManager;
import replete.gui.uiaction.MenuBarActionDescriptor;
import replete.gui.uiaction.ToolBarActionDescriptor;
import replete.gui.uiaction.UIAction;
import replete.gui.uiaction.UIActionMap;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;
import replete.util.ReflectionUtil;
import replete.util.User;


public class MainFrameActionMap extends UIActionMap {


    ////////////
    // FIELDS //
    ////////////

    // Core

    private UIController uiController;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public MainFrameActionMap(UIController uic) {
        uiController = uic;
        init();
    }

    public void init() {

        Map<String, Boolean> allEnabledStateMap = new HashMap<String, Boolean>();
        allEnabledStateMap.put("ALL", true);

        // FILE //

        UIAction action = new UIAction("fileMenu");
        action.addDescriptor(new MenuBarActionDescriptor("", "File", 'F', null,
            allEnabledStateMap, false, 0, false, null));
        addAction(action);

        // New (menu items populated later)

        action = new UIAction("newMenu");
        action.addDescriptor(new MenuBarActionDescriptor("fileMenu", "New", 'N', allEnabledStateMap));
        addAction(action);

        // Connect

        ActionListener listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.openOrientDbConnect();
            }
        };
        action = new UIAction("connectDbOrient");
        action.addDescriptor(new MenuBarActionDescriptor("fileMenu", "Connect...", 'o',
            ImageUtil.getImage("connect.gif"), allEnabledStateMap, false, 'D', true, listener));
        action.addDescriptor(new ToolBarActionDescriptor("fileMenu", "Connect...", ImageUtil.getImage("connect.gif"),
            allEnabledStateMap, false, listener));
        addAction(action);

        // Explore

        listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.openExplore();
            }
        };
        action = new UIAction("explore");
        action.addDescriptor(new MenuBarActionDescriptor("fileMenu", "Explore", 0,
            i("explore.gif"), allEnabledStateMap, false, 'E', true, listener));
        action.addDescriptor(new ToolBarActionDescriptor("fileMenu", "Explore", i("explore.gif"),
            allEnabledStateMap, false, listener));
        addAction(action);

        action = new UIAction(sepId());
        action.addDescriptor(new MenuBarActionDescriptor());
        addAction(action);

        listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.saveCurrentTab();
            }
        };
        action = new UIAction("save");
        action.addDescriptor(new MenuBarActionDescriptor("fileMenu", "Save", 'S',
            i("save.gif"), allEnabledStateMap, false, 'S', true, listener));
        action.addDescriptor(new ToolBarActionDescriptor("fileMenu1", "Save", i("save.gif"),
            allEnabledStateMap, false, listener));
        addAction(action);

        listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.saveAllTabs();
            }
        };
        action = new UIAction("save-all");
        action.addDescriptor(new MenuBarActionDescriptor("fileMenu", "Save All", 'e',
            i("saveall.gif"), allEnabledStateMap, false, 'S', true, true, listener));
        action.addDescriptor(new ToolBarActionDescriptor("fileMenu1", "Save All", i("saveall.gif"),
            allEnabledStateMap, false, listener));
        addAction(action);

        action = new UIAction(sepId());
        action.addDescriptor(new MenuBarActionDescriptor());
        addAction(action);

        listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.closeCurrentTab();
            }
        };
        action = new UIAction("closeTab");
        action.addDescriptor(new MenuBarActionDescriptor("fileMenu", "Close", 'C',
            i("close.gif"), allEnabledStateMap, false, 'W', true, listener));
        action.addDescriptor(new ToolBarActionDescriptor("fileMenu1", "Close", i("close.gif"),
            allEnabledStateMap, false, listener));
        addAction(action);

        listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.closeAllTabs();
            }
        };
        action = new UIAction("closeAllTabs");
        action.addDescriptor(new MenuBarActionDescriptor("fileMenu", "Close All", 'l',
            i("closeall.gif"), allEnabledStateMap, false, 'W', true, true, listener));
        action.addDescriptor(new ToolBarActionDescriptor("fileMenu1", "Close All", i("closeall.gif"),
            allEnabledStateMap, false, listener));
        addAction(action);

        action = new UIAction(sepId());
        action.addDescriptor(new MenuBarActionDescriptor());
        addAction(action);

        listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.openImportDialog();
            }
        };
        action = new UIAction("import");
        action.addDescriptor(new MenuBarActionDescriptor("fileMenu", "Import...", 0,
            i("import.gif"), allEnabledStateMap, listener));
        //action.addDescriptor(new ToolBarActionDescriptor("fileMenu", "Close All", i("closeall.gif"),
       //     allEnabledStateMap, false, listener));
        addAction(action);

        listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.openExportDialog();
            }
        };
        action = new UIAction("export");
        action.addDescriptor(new MenuBarActionDescriptor("fileMenu", "Export...", 0,
            i("export.gif"), allEnabledStateMap, listener));
        //action.addDescriptor(new ToolBarActionDescriptor("fileMenu", "Close All", i("closeall.gif"),
       //     allEnabledStateMap, false, listener));
        addAction(action);

        action = new UIAction(sepId());
        action.addDescriptor(new MenuBarActionDescriptor());
        addAction(action);

        listener = new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                uiController.backup ();
            }
        };
        action = new UIAction ("backup");
        action.addDescriptor (new MenuBarActionDescriptor ("fileMenu", "Backup...", 0, i ("saveall.gif"), allEnabledStateMap, listener));
        addAction (action);

        action = new UIAction(sepId());
        action.addDescriptor(new MenuBarActionDescriptor());
        addAction(action);

        ActionListener exitListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.closeMainFrame();
            }
        };
        action = new UIAction("exitProgram");
        action.addDescriptor(new MenuBarActionDescriptor("fileMenu", "Exit", 'x', i("exit.gif"),
            allEnabledStateMap, false, 0, true, exitListener));
        addAction(action);

        // ACTIONS //

        action = new UIAction("viewMenu");
        action.addDescriptor(new MenuBarActionDescriptor("", "View", 'V', null,
            allEnabledStateMap, false, 0, false, null));
        addAction(action);

        // TODO: Make this Alt+Left and Alt+Right...  Need to modify replete.
        listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.historyBackTab();
            }
        };
        action = new UIAction("backTab");
        action.addDescriptor(new MenuBarActionDescriptor("viewMenu", "Back", 'B',
            i("back.gif"), allEnabledStateMap, false, '1', true, listener));
        action.addDescriptor(new ToolBarActionDescriptor("viewMenu", "Back", i("back.gif"),
            allEnabledStateMap, false, listener));
        addAction(action);

        listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.historyForwardTab();
            }
        };
        action = new UIAction("forwardTab");
        action.addDescriptor(new MenuBarActionDescriptor("viewMenu", "Forward", 'F',
            i("forward.gif"), allEnabledStateMap, false, '2', true, listener));
        action.addDescriptor(new ToolBarActionDescriptor("viewMenu", "Forward", i("forward.gif"),
            allEnabledStateMap, false, listener));
        addAction(action);

        action = new UIAction(sepId());
        action.addDescriptor(new MenuBarActionDescriptor());
        addAction(action);

        listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.toggleWorkpane();
            }
        };
        action = new UIAction("toggleWS");
        action.addDescriptor(new MenuBarActionDescriptor("viewMenu", "Toggle Workpane", 'W',
            i("ws.gif"), allEnabledStateMap, false, 0, false, listener));
        action.addDescriptor(new ToolBarActionDescriptor("viewMenu", "Toggle Workpane", i("ws.gif"),
            allEnabledStateMap, false, listener));
        addAction(action);

        // TOOLS //

        action = new UIAction("toolsMenu");
        action.addDescriptor(new MenuBarActionDescriptor("", "Tools", 'T', null,
            allEnabledStateMap, false, 0, false, null));
        addAction(action);

        ActionListener redSkyListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.openChildWindow("jobs", null);
            }
        };
        action = new UIAction("runmgr");
        action.addDescriptor(new MenuBarActionDescriptor("toolsMenu", "Run Manager", 'R',
            i("redsky2.gif"), allEnabledStateMap, false, 'J', true, redSkyListener));
        action.addDescriptor(new ToolBarActionDescriptor("toolsMenu", "Run Manager", i("redsky2.gif"),
            allEnabledStateMap, false, redSkyListener));
        addAction(action);

        action = new UIAction(sepId());
        action.addDescriptor(new MenuBarActionDescriptor());
        addAction(action);

        ActionListener preferencesListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.showPreferences();
            }
        };
        action = new UIAction("prefs");
        action.addDescriptor(new MenuBarActionDescriptor("toolsMenu", "Preferences", 'P',
            i("properties.gif"), allEnabledStateMap, false, 0, false, preferencesListener));
        addAction(action);

        // PEOPLE //

        action = new UIAction("peopleMenu");
        action.addDescriptor(new MenuBarActionDescriptor("", "People", 'P', null,
            allEnabledStateMap, false, 0, false, null));
        addAction(action);

        ActionListener genListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.openProfileFromUserId(User.getName());
            }
        };
        action = new UIAction("openMyProfile");
        action.addDescriptor(new MenuBarActionDescriptor("peopleMenu", "Edit My Profile...", 'P',
            i("user.png"), allEnabledStateMap, false, 0, false, genListener));
        addAction(action);

        // L&F //

        // TODO: Move to window menu.
        action = new UIAction("lafMenu");
        action.addDescriptor(new MenuBarActionDescriptor("", LafManager.createLafMenu(), allEnabledStateMap));
        addAction(action);
        // WINDOW //

        action = new UIAction("windowMenu");
        action.addDescriptor(new MenuBarActionDescriptor("", "Window", 'W', null,
            allEnabledStateMap, false, 0, false, null));
        addAction(action);

        // HELP //

        action = new UIAction("helpMenu");
        action.addDescriptor(new MenuBarActionDescriptor("", "Help", 'H', null,
            allEnabledStateMap, false, 0, false, null));
        addAction(action);

        ActionListener helpListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.notImpl();
            }
        };
        action = new UIAction("helpContents");
        action.addDescriptor(new MenuBarActionDescriptor("helpMenu", "Help Contents", 'H', i("help.gif"),
            allEnabledStateMap, false, KeyEvent.VK_F1, false, helpListener));
        addAction(action);

        ActionListener logListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.showLogViewer();
            }
        };
        action = new UIAction("logViewer");
        action.addDescriptor(new MenuBarActionDescriptor("helpMenu", "Log Viewer", 'L', i("log.gif"),
            allEnabledStateMap, false, 0, false, logListener));
        addAction(action);

        ActionListener pluginsListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.showPluginDialog();
            }
        };
        action = new UIAction("plugins");
        action.addDescriptor(new MenuBarActionDescriptor("helpMenu", "Plug-ins", 'P', i("connect.gif"),
            allEnabledStateMap, false, 0, false, pluginsListener));
        addAction(action);

        ActionListener aboutListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.showAbout();
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
                        d.getUmfListener().actionPerformed(uiController, e);
                    }
                });
                action = new UIAction(actionName);
                action.addDescriptor(d);
                addAction(action);
            }
        }

        action = new UIAction(sepId());
        action.addDescriptor(new MenuBarActionDescriptor());
        addAction(action);

        ActionListener aboutListener2 = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.openNewRun(null);
            }
        };

        action = new UIAction("newrun");
        action.addDescriptor(new MenuBarActionDescriptor("fileMenu/newMenu", "New Run...", 'R', i("run.gif"),
            allEnabledStateMap, false, 0, false, aboutListener2));
        addAction(action);

        aboutListener2 = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.openNewAnalysis(null);
            }
        };

        action = new UIAction("newanalysis");
        action.addDescriptor(new MenuBarActionDescriptor("fileMenu/newMenu", "New Analysis...", 'A', i("analysis.gif"),
            allEnabledStateMap, false, 0, false, aboutListener2));
        addAction(action);

    }

    protected ImageIcon i(String iconFileName) {
        return ImageUtil.getImage(iconFileName);
    }

    private int sepId = 0;
    private String sepId() {
        return "separator" + (sepId++);
    }
}
