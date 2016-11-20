/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform;

import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MDoc;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.plugins.UMFPluginManager;
import gov.sandia.umf.platform.plugins.extpoints.ProductCustomization;
import gov.sandia.umf.platform.ui.AboutDialog;
import gov.sandia.umf.platform.ui.MainFrame;

import java.awt.EventQueue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.cli.CommandLineParser;
import replete.cli.errors.CommandLineParseException;
import replete.cli.options.Option;
import replete.gui.lafbasic.LafManager;
import replete.gui.windows.Dialogs;
import replete.gui.windows.LoadingWindow;
import replete.gui.windows.common.CommonWindow;
import replete.gui.windows.common.CommonWindowClosingEvent;
import replete.gui.windows.common.CommonWindowClosingListener;
import replete.plugins.ExtPointNotLoadedException;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;
import replete.util.Application;
import replete.util.ArrayUtil;
import replete.util.ArrayUtil.ArrayTranslator;

public class UMF
{
    public static File getAppResourceDir ()
    {
        return new File (System.getProperty ("user.home"), "n2a").getAbsoluteFile ();
    }

    private static MainFrame mainFrame;
    private static LoadingWindow loadingFrame;

    public static ProductCustomization prodCustomization;

    public static void main (String[] args) throws Exception
    {
        // TODO: Add help to these options.
        CommandLineParser parser = new CommandLineParser ();
        Option optPluginAdd = parser.addStringOption ("plugin");
        Option optPluginDir = parser.addStringOption ("plugindir");
        Option optProdCust = parser.addStringOption ("product");

        try
        {
            parser.parse (args);
        }
        catch (CommandLineParseException e)
        {
            System.err.println (parser.getUsageMessage (e, "UMF", 80, 20));
            return;
        }

        Object[] pluginValuesCL = parser.getOptionValues (optPluginAdd);
        Object[] pluginDirsCL = parser.getOptionValues (optPluginDir);
        String prodCust = (String) parser.getOptionValue (optProdCust);

        // Set some global application properties.
        Application.setName ("Unified Modeling Framework");
        Application.setTitle ("Unified Modeling Framework");

        String[] pluginMem = ArrayUtil.translate (String.class, pluginValuesCL);

        File[] pluginDirs = ArrayUtil.translate (File.class, pluginDirsCL, new ArrayTranslator ()
        {
            public Object translate (Object o)
            {
                return new File ((String) o);
            }
        });
        pluginDirs = ArrayUtil.cat (pluginDirs, UMFPluginManager.getPluginsDir ());

        if (!PluginManager.initialize (new PlatformPlugin (), pluginMem, pluginDirs))
        {
            System.err.println (PluginManager.getInitializationErrors ());
        }

        setUncaughtExceptionHandler (null);

        prodCustomization = chooseProductCustomization (prodCust);

        // Read L&F from properties.
        String lafClassName = AppData.state.get ("LookAndFeel");
        String lafTheme = AppData.state.get ("Theme");
        LafManager.initialize (lafClassName, lafTheme);

        // TODO: Read popup help in M format

        // Start data handling
        AppData.checkInitialDB ();

        // Create the main frame.
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                createAndShowMainFrame ();
            }
        });

        // Build the About dialog as the last thing this thread does, effectively in the background.
        AboutDialog.initializeLabels ();
    }

    private static ProductCustomization chooseProductCustomization(String prodCust) {
        ProductCustomization pc;
        try {
            List<ExtensionPoint> extPoints = PluginManager.getExtensionsForPoint(ProductCustomization.class);
            List<ProductCustomization> allPc = new ArrayList<ProductCustomization>();
            List<ProductCustomization> nonPlatPc = new ArrayList<ProductCustomization>();
            for(ExtensionPoint extPoint : extPoints) {
                allPc.add((ProductCustomization) extPoint);
                if(!(extPoint instanceof PlatformProductCustomization)) {
                    nonPlatPc.add((ProductCustomization) extPoint);
                }
            }
            if(allPc.size() == 0) {
                pc = new PlatformProductCustomization();
            } else if(allPc.size() == 1) {
                pc = allPc.get(0);
            } else {
                if(nonPlatPc.size() == 0) {
                    pc = new PlatformProductCustomization();
                } else if(nonPlatPc.size() >= 2) {
                    if(prodCust == null) {
                        pc = new PlatformProductCustomization();
                    } else {
                        pc = (ProductCustomization) PluginManager.getExtensionById(prodCust);
                        if(pc == null) {
                            pc = new PlatformProductCustomization();
                        }
                    }
                } else {
                    pc = nonPlatPc.get(0);
                }
            }
        } catch(ExtPointNotLoadedException e) {
            pc = new PlatformProductCustomization();
        }
        return pc;
    }

    public static void createAndShowMainFrame ()
    {
        mainFrame = MainFrame.getInstance ();
        mainFrame.addAttemptToCloseListener (new CommonWindowClosingListener ()
        {
            public void stateChanged (CommonWindowClosingEvent e)
            {
                MDoc appState = AppData.state;
                appState.set (LafManager.getCurrentLaf ().getCls (),      "LookAndFeel");
                appState.set (LafManager.getCurrentLaf ().getCurTheme (), "Theme");

                MNode winProps = AppData.state.childOrCreate ("WinLayout");
                winProps.clear ();
                winProps.set (mainFrame.getX             (), "MainFrame", "x");
                winProps.set (mainFrame.getY             (), "MainFrame", "y");
                winProps.set (mainFrame.getWidth         (), "MainFrame", "width");
                winProps.set (mainFrame.getHeight        (), "MainFrame", "height");
                winProps.set (mainFrame.getExtendedState (), "MainFrame", "state");

                appState.save ();
            }
        });
        mainFrame.addClosingListener (new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                AppData.save ();
            }
        });

        MNode winProps = AppData.state.childOrCreate ("WinLayout");

        MNode m = winProps.child ("MainFrame");
        if (m != null)
        {
            int w = m.getOrDefault (-1, "width");
            int h = m.getOrDefault (-1, "height");
            int x = m.getOrDefault (-1, "x");
            int y = m.getOrDefault (-1, "y");
            if (w >= 0  &&  h >= 0) mainFrame.setSize (w, h);
            if (x >= 0  &&  y >= 0) mainFrame.setLocation (x, y);
            else                    mainFrame.setLocationRelativeTo (mainFrame.getParent ());
            mainFrame.ensureOnScreen (true);
            mainFrame.setVisible (true);
        }
        else
        {
            mainFrame.setVisible (true);
        }
        mainFrame.setExtendedState (winProps.getOrDefault (0, "MainFrame", "state"));

        setUncaughtExceptionHandler (mainFrame);
        Dialogs.registerApplicationWindow(mainFrame, Application.getName());
        if (loadingFrame != null) loadingFrame.dispose ();
    }

    public static void setUncaughtExceptionHandler (final JFrame parent)
    {
        Thread.setDefaultUncaughtExceptionHandler (new UncaughtExceptionHandler ()
        {
            public void uncaughtException (Thread thread, final Throwable throwable)
            {
                File crashdump = new File (getAppResourceDir (), "crashdump");
                try
                {
                    PrintStream err = new PrintStream (crashdump);
                    throwable.printStackTrace (err);
                    err.close ();
                }
                catch (FileNotFoundException e) {}

                JOptionPane.showMessageDialog
                (
                    parent,
                    "<html><body><p style='width:300px'>Our apologies, but an exception was raised. Details have been saved in " + crashdump.getAbsolutePath ()
                    + ". Please consider filing a bug report on github, including the crashdump file and a description of what you were doing at the time.</p></body></html>",
                    "Internal Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
}
