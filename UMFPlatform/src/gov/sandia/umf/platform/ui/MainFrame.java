/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.gui.uiaction.UIActionMap;
import replete.gui.uiaction.UIActionMenuBar;
import replete.gui.windows.EscapeFrame;
import replete.gui.windows.common.CommonWindow;
import replete.util.Lay;

// TODO: Restore to maximized state not working.

public class MainFrame extends EscapeFrame implements HelpCapableWindow
{
    protected static MainFrame instance;

    public static MainFrame getInstance ()
    {
        if (instance == null) instance = new MainFrame ();
        return instance;
    }

    public MainTabbedPane tabs;
    public MainGlassPane glassPane;

    public MainFrame ()
    {
        MNode pc = AppData.properties;
        setTitle(pc.get ("name") + " v" + pc.get ("version"));

        ArrayList<Image> icons = new ArrayList<Image> ();
        icons.add (ImageUtil.getImage ("n2a-16.png").getImage ());
        icons.add (ImageUtil.getImage ("n2a-32.png").getImage ());
        setIconImages (icons);

        tabs = new MainTabbedPane ();

        UIActionMap actions = new MainFrameActionMap ();
        actions.setState("ALL");
        // TODO: ensure that the buttons in tb do not receive keyboard focus. They should only be mouse operated.
        //UIActionToolBar tb = new UIActionToolBar(actions);
        setJMenuBar(new UIActionMenuBar(actions));

        Lay.BLtg(this,
            //"N", tb,
            "C", tabs
        );

        setSize(600, 600);
        setLocationRelativeTo(null);

        tabs.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int border = 8;
                HelpNotesPanel pnlHelpNotes = glassPane.getHelpNotesPanel();
                pnlHelpNotes.setLocation(
                    border,
                    tabs.getLocation().y +
                        tabs.getSize().height +
                        getContentPane().getY() -
                        pnlHelpNotes.getHeight() +
                        getJMenuBar().getHeight() -
                        border +
                        7         // TODO: Fudge number... not sure why I need this.
                    );
            }
        });

        glassPane = new MainGlassPane ();
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
                AppData.quit ();  // Save data before exiting.
            }
        });
    }

    public void showHelp (String topic, String content)
    {
        glassPane.showHelp (topic, content);
    }

    @Override
    protected void escapePressed ()
    {
        if (glassPane.isHelpShowing ()) glassPane.hideHelp ();
    }
}
