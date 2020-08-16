/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.ArrayList;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

@SuppressWarnings("serial")
public class MainFrame extends JFrame
{
    public static MainFrame instance;

    public MainTabbedPane tabs;
    public UndoManager    undoManager;

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
        icons.add (ImageUtil.getImage ("n2a-48.png").getImage ());
        icons.add (ImageUtil.getImage ("n2a-128.png").getImage ());
        setIconImages (icons);
        // TODO: make icon appear on Mac. This requires accessing the "dock", which is an arbitrarily different concept than "tray" on every other desktop system, even though it does the same thing.

        undoManager = new UndoManager ();
        tabs = new MainTabbedPane ();

        Lay.BLtg (this,
            "C", tabs
        );

        MNode winProps = AppData.state.childOrCreate ("WinLayout");
        int w = winProps.getOrDefault (900, "width");
        int h = winProps.getOrDefault (600, "height");
        int x = winProps.getOrDefault (-1,  "x");
        int y = winProps.getOrDefault (-1,  "y");
        if (w >= 0  &&  h >= 0) setSize (w, h);
        if (x >= 0  &&  y >= 0) setLocation (x, y);
        else                    setLocationRelativeTo (null);
        setExtendedState (winProps.getOrDefault (0, "state"));
        setVisible (true);

        addComponentListener (new ComponentAdapter ()
        {
            public void componentResized (ComponentEvent e)
            {
                if (getExtendedState () == NORMAL)
                {
                    AppData.state.set (getWidth (),  "WinLayout", "width");
                    AppData.state.set (getHeight (), "WinLayout", "height");
                }
            }

            public void componentMoved (ComponentEvent e)
            {
                if (getExtendedState () == NORMAL)
                {
                    AppData.state.set (getX (), "WinLayout", "x");
                    AppData.state.set (getY (), "WinLayout", "y");
                }
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
                AppData.quit ();  // Save all user settings (including those from other parts of the app).
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
    }
}
