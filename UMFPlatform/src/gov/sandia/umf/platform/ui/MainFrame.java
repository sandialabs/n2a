/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.AppState;
import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.plugins.extpoints.ProductCustomization;
import gov.sandia.umf.platform.ui.general.HelpNotesPanel;
import gov.sandia.umf.platform.ui.jobs.RunManagerFrame;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.gui.CommonStatusBar;
import replete.gui.controls.mnemonics.MMenuItem;
import replete.gui.uiaction.MenuBarActionDescriptor;
import replete.gui.uiaction.ToolBarActionDescriptor;
import replete.gui.uiaction.UIActionMap;
import replete.gui.uiaction.UIActionMenuBar;
import replete.gui.uiaction.UIActionToolBar;
import replete.gui.windows.EscapeFrame;
import replete.gui.windows.common.ChildWindowCreationHandler;
import replete.gui.windows.common.CommonWindow;
import replete.gui.windows.common.CommonWindowClosingEvent;
import replete.gui.windows.common.CommonWindowClosingListener;
import replete.util.GUIUtil;
import replete.util.Lay;

// TODO: Restore to maximized state not working.

public class MainFrame extends EscapeFrame implements HelpCapableWindow {


    ////////////
    // FIELDS //
    ////////////

    // Static

    private static MainFrame instance;

    // Core

    private UIController uiController;

    // UI

    private MainTabbedPane tabN2A;
    private CommonStatusBar sbar;
    private MainGlassPane glassPane;
    private JMenuItem mnuWindow;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public MainFrame(UIController uic)
    {
        ProductCustomization pc = AppState.getState().prodCustomization;
        setTitle(pc.getProductLongName() + " v" + pc.getProductVersion());
        setIconImage(pc.getWindowIcon().getImage());

        uiController = uic;  // Not fully populated yet, as it needs tab & parent reference.

        tabN2A = new MainTabbedPane(uic);
        sbar = new CommonStatusBar();
        Lay.hn(sbar, "dim=[20, 30]");

        UIActionMap actions = new MainFrameActionMap(uiController);
        actions.setState("ALL");
        UIActionToolBar tb = new UIActionToolBar(actions);
        setJMenuBar(new UIActionMenuBar(actions));
        mnuWindow = ((MenuBarActionDescriptor) actions.getAction("windowMenu").getDescriptor(MenuBarActionDescriptor.class)).getComponent();

        Lay.BLtg(this,
            "N", tb,
            "C", tabN2A,
            "S", sbar
        );

        setSize(600, 600);
        setLocationRelativeTo(null);

        tabN2A.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int border = 8;
                HelpNotesPanel pnlHelpNotes = glassPane.getHelpNotesPanel();
                pnlHelpNotes.setLocation(
                    border,
                    tabN2A.getLocation().y +
                        tabN2A.getSize().height +
                        getContentPane().getY() -
                        pnlHelpNotes.getHeight() +
                        getJMenuBar().getHeight() -
                        border +
                        7         // TODO: Fudge number... not sure why I need this.
                    );
            }
        });

        glassPane = new MainGlassPane(uiController);
        glassPane.setVisible(false);
        setGlassPane(glassPane);

        addClosingListener(new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                for (CommonWindow win : getAllChildWindows ())
                {
                    destroyChildWindow(win);
                }
                AppData.getInstance ().quit ();  // Save data before exiting.
            }
        });

        addChildWindowListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateWindowMenu();
            }
        });
        updateWindowMenu();
        addChildWindowHandlers();
    }

    private void updateWindowMenu() {
        mnuWindow.removeAll();
        JLabel lbl = new JLabel("  Open Windows:");
        GUIUtil.setSize(lbl, new Dimension(150, lbl.getPreferredSize().height));
        mnuWindow.add(lbl);
        List<CommonWindow> visWindows = getVisibleChildWindows();
        int i = 1;
        for(CommonWindow win : visWindows) {
            String text = ": " + win.getTitle();
            if(i < 10) {
                text = "&" + i + text;
            } else if(i == 10) {
                text = "1&0" + text;
            } else {
                text = i + text;
            }
            JMenuItem mnuWindowItem = new MMenuItem(text);
            final CommonWindow finalWin = win;
            mnuWindowItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    showChildWindow(finalWin);
                }
            });
            mnuWindow.add(mnuWindowItem);
            i++;
        }
        if(i == 1) {
            mnuWindow.add(new JLabel("      <none>"));
        }
    }

    private void addChildWindowHandlers() {
        addChildWindowCreationHandler("jobs", new ChildWindowCreationHandler() {
            public CommonWindow create(Object... args) {
                return new RunManagerFrame(MainFrame.this);
            }
        });
        addChildWindowCreationHandler("izy", new ChildWindowCreationHandler() {
            @Override
            public CommonWindow create(Object... args) {
//                return new IzyEngineWindow(dataModel);
                return null;
            }
        });
    }

    public CommonStatusBar getStatusBar() {
        return sbar;
    }
    public MainTabbedPane getTabbedPane() {
        return tabN2A;
    }

    public void showHelp(String topic, String content) {
        glassPane.showHelp(topic, content);
    }

    @Override
    protected void escapePressed() {
        if(glassPane.isHelpShowing()) {
            glassPane.hideHelp();
        } else {
            super.escapePressed();
        }
    }

    public static MainFrame getInstance() {
        return instance;
    }
    public static void setInstance(MainFrame f) {
        instance = f;
    }

    public void init ()
    {
        tabN2A.panelSearch.search ();
    }
}
