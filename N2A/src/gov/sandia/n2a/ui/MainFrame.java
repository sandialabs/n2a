/*
Copyright 2013,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.ArrayList;

import javax.swing.JFrame;

public class MainFrame extends JFrame
{
    public static MainFrame instance;

    public MainTabbedPane tabs;

    public MainFrame ()
    {
        if (instance != null) throw new RuntimeException ("Multiple attempts to create main application window.");
        instance = this;

        String appName = AppData.properties.get ("name");
        setTitle (appName);

        try
        {
            Toolkit xToolkit = Toolkit.getDefaultToolkit ();
            java.lang.reflect.Field awtAppClassNameField = xToolkit.getClass ().getDeclaredField ("awtAppClassName");
            awtAppClassNameField.setAccessible (true);
            awtAppClassNameField.set (xToolkit, appName);
        }
        catch (Exception e)
        {
        }

        ArrayList<Image> icons = new ArrayList<Image> ();
        icons.add (ImageUtil.getImage ("n2a-16.png").getImage ());
        icons.add (ImageUtil.getImage ("n2a-32.png").getImage ());
        setIconImages (icons);

        tabs = new MainTabbedPane ();

        Lay.BLtg (this,
            "C", tabs
        );

        MNode winProps = AppData.state.childOrCreate ("WinLayout");
        int w = winProps.getOrDefaultInt ("width",  "800");
        int h = winProps.getOrDefaultInt ("height", "600");
        int x = winProps.getOrDefaultInt ("x",      "-1");
        int y = winProps.getOrDefaultInt ("y",      "-1");
        if (w >= 0  &&  h >= 0) setSize (w, h);
        if (x >= 0  &&  y >= 0) setLocation (x, y);
        else                    setLocationRelativeTo (null);
        setExtendedState (winProps.getOrDefaultInt ("state", "0"));
        setVisible (true);

        addComponentListener (new ComponentAdapter ()
        {
            public void componentResized (ComponentEvent e)
            {
                if (getExtendedState () == NORMAL)
                {
                    AppData.state.set ("WinLayout", "width",  getWidth ());
                    AppData.state.set ("WinLayout", "height", getHeight ());
                }
            }

            public void componentMoved (ComponentEvent e)
            {
                if (getExtendedState () == NORMAL)
                {
                    AppData.state.set ("WinLayout", "x", getX ());
                    AppData.state.set ("WinLayout", "y", getY ());
                }
            }
        });

        addWindowStateListener (new WindowStateListener ()
        {
            public void windowStateChanged (WindowEvent e)
            {
                AppData.state.set ("WinLayout", "state", getExtendedState ());
            }
        });

        setDefaultCloseOperation (EXIT_ON_CLOSE);
        addWindowListener (new WindowAdapter ()
        {
            public void windowClosing (WindowEvent arg0)
            {
                AppData.quit ();  // Save all user settings (including those from other parts of the app).
            }
        });
    }
}
