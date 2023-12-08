/*
Copyright 2013-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.ShutdownHook;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.settings.SettingsLookAndFeel;
import gov.sandia.n2a.ui.studies.PanelStudy;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

@SuppressWarnings("serial")
public class MainFrame extends JFrame
{
    public static MainFrame   instance;
    public static UndoManager undoManager = new UndoManager ();

    public MainTabbedPane tabs;

    public MainFrame ()
    {
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
        catch (Exception e) {}

        ArrayList<Image> icons = new ArrayList<Image> ();
        icons.add (ImageUtil.getImage ("n2a-16.png").getImage ());
        icons.add (ImageUtil.getImage ("n2a-32.png").getImage ());
        icons.add (ImageUtil.getImage ("n2a-48.png").getImage ());
        icons.add (ImageUtil.getImage ("n2a-128.png").getImage ());
        setIconImages (icons);
        // TODO: make icon appear on Mac. This requires accessing the "dock", which is an arbitrarily different concept than "tray" on every other desktop system, even though it does the same thing.

        tabs = new MainTabbedPane ();

        Lay.BLtg (this,
            "C", tabs
        );

        setDimensions ();
        MNode winProps = AppData.state.childOrCreate ("WinLayout");
        setExtendedState (winProps.getOrDefault (NORMAL, "state"));
        setVisible (true);

        // The main window could be resized or moved in response to user interaction or
        // to a change in screen resolution. In the latter case, it seems AWT/Swing is
        // trying to keep the window at the same amount of screen area. In theory, the
        // em-based size/position of the window should not change, so we don't need to
        // record a new value. However, we do need to update the size of em itself.
        addComponentListener (new ComponentAdapter ()
        {
            public void componentResized (ComponentEvent e)
            {
                if (SettingsLookAndFeel.instance.checkRescale ()) return;
                if (getExtendedState () != NORMAL) return;
                float em = SettingsLookAndFeel.em;
                AppData.state.setTruncated (getWidth ()  / em, 2, "WinLayout", "width");
                AppData.state.setTruncated (getHeight () / em, 2, "WinLayout", "height");
            }

            public void componentMoved (ComponentEvent e)
            {
                if (SettingsLookAndFeel.instance.checkRescale ()) return;
                if (getExtendedState () != NORMAL) return;
                float em = SettingsLookAndFeel.em;
                AppData.state.setTruncated (getX () / em, 2, "WinLayout", "x");
                AppData.state.setTruncated (getY () / em, 2, "WinLayout", "y");
            }
        });

        addWindowStateListener (new WindowStateListener ()
        {
            public void windowStateChanged (WindowEvent e)
            {
                AppData.state.set (getExtendedState (), "WinLayout", "state");
            }
        });

        setDefaultCloseOperation (EXIT_ON_CLOSE);
        addWindowListener (new WindowAdapter ()
        {
            public void windowClosing (WindowEvent arg0)
            {
                PanelModel.instance.panelEquations.saveFocus ();  // Hack to ensure that final viewport position gets recorded.
                PanelStudy.instance.quit ();  // Give any active study threads a chance to shut down gracefully.
                AppData.quit ();  // Save all user settings (including those from other parts of the app).
                List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint (ShutdownHook.class);
                for (ExtensionPoint exp : exps) ((ShutdownHook) exp).shutdown ();
                Host.quit ();  // Close down any ssh sessions.
            }
        });

        // Undo
        JComponent content = (JComponent) getContentPane ();

        InputMap inputMap = content.getInputMap (JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo");  // For Windows and Linux
        inputMap.put (KeyStroke.getKeyStroke ("meta Z"),          "Undo");  // For Mac
        inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("meta Y"),          "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift meta Z"),    "Redo");

        ActionMap actionMap = content.getActionMap ();
        actionMap.put ("Undo", new AbstractAction ("Undo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.undo ();}
                catch (CannotUndoException e) {}
                catch (CannotRedoException e) {}
            }
        });
        actionMap.put ("Redo", new AbstractAction ("Redo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.redo();}
                catch (CannotUndoException e) {}
                catch (CannotRedoException e) {}
            }
        });

        ToolTipManager.sharedInstance ().setDismissDelay (20000);
    }

    public void setDimensions ()
    {
        MNode winProps = AppData.state.childOrCreate ("WinLayout");
        float em = SettingsLookAndFeel.em;
        int w = (int) Math.round (winProps.getOrDefault (90.0, "width")  * em);
        int h = (int) Math.round (winProps.getOrDefault (60.0, "height") * em);
        int x;
        int y;
        if (SettingsLookAndFeel.rescaling)  // Screen resolution has changed (or we just started up) so shift position.
        {
            x = (int) Math.round (winProps.getOrDefault (-1.0, "x") * em);
            y = (int) Math.round (winProps.getOrDefault (-1.0, "y") * em);
        }
        else  // L&F or font scale changed, so don't move window.
        {
            x = getX ();
            y = getY ();
            // Need to record (x,y), in case em changed. We won't get a componentMoved() message unless (x,y) actually changes.
            AppData.state.setTruncated (x / em, 2, "WinLayout", "x");
            AppData.state.setTruncated (y / em, 2, "WinLayout", "y");
        }

        // Avoid rescaling past edge of screen.
        Dimension screen = Toolkit.getDefaultToolkit ().getScreenSize ();
        if (w >= 0  &&  x >= 0  &&  x + w > screen.width)
        {
            if (w <= screen.width)
            {
                x = screen.width - w;
            }
            else
            {
                w = screen.width;
                x = 0;
            }
        }
        if (h >= 0  &&  y >= 0  &&  y + h > screen.height)
        {
            if (h <= screen.height)
            {
                y = screen.height - h;
            }
            else
            {
                h = screen.height;
                y = 0;
            }
        }

        if (w >= 0  &&  h >= 0) setSize (w, h);
        if (x >= 0  &&  y >= 0) setLocation (x, y);
        else                    setLocationRelativeTo (null);
    }
}
