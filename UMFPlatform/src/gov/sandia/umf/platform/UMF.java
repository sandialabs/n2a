/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform;

import gov.sandia.umf.platform.connect.orientdb.ui.ConnectionManager;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientConnectDetails;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.plugins.UMFPluginManager;
import gov.sandia.umf.platform.plugins.base.PlatformPlugin;
import gov.sandia.umf.platform.plugins.base.PlatformProductCustomization;
import gov.sandia.umf.platform.plugins.extpoints.ProductCustomization;
import gov.sandia.umf.platform.runs.RunQueue;
import gov.sandia.umf.platform.ui.AboutDialog;
import gov.sandia.umf.platform.ui.MainFrame;
import gov.sandia.umf.platform.ui.UIController;

import java.io.File;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONObject;

import replete.cli.CommandLineParser;
import replete.cli.errors.CommandLineParseException;
import replete.cli.options.Option;
import replete.gui.lafbasic.LafManager;
import replete.gui.lafbasic.RebootFramesListener;
import replete.gui.windows.Dialogs;
import replete.gui.windows.LoadingWindow;
import replete.gui.windows.common.CommonWindow;
import replete.gui.windows.common.CommonWindowClosingEvent;
import replete.gui.windows.common.CommonWindowClosingListener;
import replete.logging.LogEntryType;
import replete.logging.LogManager;
import replete.plugins.ExtPointNotLoadedException;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;
import replete.util.Application;
import replete.util.ArrayUtil;
import replete.util.ArrayUtil.ArrayTranslator;
import replete.util.FileUtil;

public class UMF
{
    public static File getAppResourceDir ()
    {
        return new File (System.getProperty ("user.home"), "n2a").getAbsoluteFile ();
    }
    public static File getAppLogDir ()
    {
        return new File (getAppResourceDir (), "log");
    }


    ////////////
    // FIELDS //
    ////////////

    // Const

    private static Logger logger = Logger.getLogger(UMF.class);
    private static final String DEFAULT_INTERNAL_LOG4J_CONFIG = "/gov/sandia/umf/platform/log4j.properties";

    // UI

    private static MainFrame mainFrame;
    private static UIController uiController;
    private static ConnectionManager dataModelMgr2;
    private static LoadingWindow loadingFrame;

    // Misc

    private static Map<String, String[]> popupHelp;


    //////////
    // MAIN //
    //////////

    public static void main(String[] args) {
        /* String expectedXml = "" +
                "<software>\n" +
                "  <version>1.0</version>\n" +
                "  <vendor>Joe</vendor>\n" +
                "  <name>XStream</name>\n" +
                "</software>";
        xstream = new XStream() {
            protected MapperWrapper wrapMapper(MapperWrapper next) {
                return new MapperWrapper(next) {
                    public boolean shouldSerializeMember(Class definedIn, String fieldName) {
                        return definedIn != Object.class ? super.shouldSerializeMember(definedIn, fieldName) : false;
                    }
                };
            }
        };
        xstream.alias("software", Software.class);
        Software out = (Software) xstream.fromXML(expectedXml);
        assertEquals("Joe", out.vendor);
        assertEquals("XStream", out.name);
         */

        // TODO: Add help to these options.
        CommandLineParser parser = new CommandLineParser();
        Option optPluginAdd = parser.addStringOption("plugin");
        Option optPluginDir = parser.addStringOption("plugindir");
        Option optProdCust = parser.addStringOption("product");

        try {
            parser.parse(args);
        } catch(CommandLineParseException e) {
            System.err.println(parser.getUsageMessage(e, "UMF", 80, 20));
            return;
        }

        Object[] pluginValuesCL = parser.getOptionValues(optPluginAdd);
        Object[] pluginDirsCL = parser.getOptionValues(optPluginDir);
        String prodCust = (String) parser.getOptionValue(optProdCust);

        // Set up Log4J.
        URL log4jConfig = UMF.class.getResource(DEFAULT_INTERNAL_LOG4J_CONFIG);
        PropertyConfigurator.configure(log4jConfig);

        // Set some global application properties.
        Application.setName("Unified Modeling Framework");
        Application.setTitle("Unified Modeling Framework");

        AppState.getState ().load();

        String[] pluginMem = ArrayUtil.translate(String.class, pluginValuesCL);

        File[] pluginDirs = ArrayUtil.translate(File.class, pluginDirsCL, new ArrayTranslator() {
            public Object translate(Object o) {
                return new File((String) o);
            }
        });
        pluginDirs = ArrayUtil.cat (pluginDirs, UMFPluginManager.getPluginsDir ());

        if(!PluginManager.initialize(new PlatformPlugin(), pluginMem, pluginDirs)) {
            System.err.println(PluginManager.getInitializationErrors());
        }

        UMFPluginManager.init();

        setUncaughtExceptionHandler(null);

        AppState.getState ().prodCustomization = chooseProductCustomization (prodCust);

        // Read L&F from properties.
        String lafClassName = AppState.getState ().get ("LookAndFeel");
        String lafTheme = AppState.getState ().get ("Theme");
        LafManager.initialize (lafClassName, lafTheme);

        LogManager.setLogFile (new File (getAppLogDir (), "n2a.log"));

        // Read popup help.
        popupHelp = readPopupHelp();

        // Create a new DB in the standard location
        File repos = new File (getAppResourceDir (), "repos");
        File dflt  = new File (repos, "local");
        OrientConnectDetails details = new OrientConnectDetails
        (
            "Local",
            "local:" + dflt.getAbsolutePath (),  // TODO: change to "plocal:"
            "admin",
            "admin"
        );
        // DB will be automatically populated by the OrientDatasource class
        dataModelMgr2 = ConnectionManager.getInstance ();
        dataModelMgr2.setConnectDetails (details);

        // Create the main frame.
        createAndShowMainFrame();

        // Hear about when the L&F manager needs the main frame to reboot itself.
        LafManager.setNeedToRebootListener(new RebootFramesListener() {
            public void reboot() {
                reloadAppFrame();
            }
            public boolean allowReboot() {
                if(uiController.isDirty()) {
                    int val = Dialogs.showMulti("Changing to this look and feel requires the window to restart.\n" +
                        "One or more active panels has unsaved information.\n" +
                        "Are you sure you wish to change the look and feel?\nYou will lose all unsaved information.",
                        "Change L&F?",
                        new String[] {"Yes", "No"}, JOptionPane.WARNING_MESSAGE);
                    if(val == 0) {
                        return true;
                    }
                    return false;
                }
                return true;
            }
        });

        // Needed because HTML labels are slow to construct!
        new Thread() {
            @Override
            public void run() {
                AboutDialog.initializeLabels();
            };
        }.start();
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

    private static void getWindowLayoutProps ()
    {
        MNode winProps = AppState.getState ().getNode ("WinLayout");
        winProps.clear ();

        winProps.set (mainFrame.getX             (), "MainFrame", "x");
        winProps.set (mainFrame.getY             (), "MainFrame", "y");
        winProps.set (mainFrame.getWidth         (), "MainFrame", "width");
        winProps.set (mainFrame.getHeight        (), "MainFrame", "height");
        winProps.set (mainFrame.getExtendedState (), "MainFrame", "state");

        List<CommonWindow> childWins = mainFrame.getVisibleChildWindows ();
        for (CommonWindow win : childWins)
        {
            String typeId = mainFrame.getTypeIdOfWindow (win);
            winProps.set (win.getX      (), "ChildFrame", typeId, "x");
            winProps.set (win.getY      (), "ChildFrame", typeId, "y");
            winProps.set (win.getWidth  (), "ChildFrame", typeId, "width");
            winProps.set (win.getHeight (), "ChildFrame", typeId, "height");
        }
    }

    private static void reloadAppFrame ()
    {
        loadingFrame = new LoadingWindow();
        loadingFrame.setVisible(true);

        getWindowLayoutProps ();

        List<CommonWindow> childWins = mainFrame.getAllChildWindows();
        for(CommonWindow win : childWins) {
            mainFrame.destroyChildWindow(win);
        }

        mainFrame.dispose();
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
                createAndShowMainFrame();
            }
        }.start();
    }

    private static void createAndShowMainFrame ()
    {
        uiController = new UIController (dataModelMgr2);
        mainFrame    = new MainFrame (uiController);
        MainFrame.setInstance (mainFrame);
        uiController.setParentReference (mainFrame);
        uiController.setTabbedPane (mainFrame.getTabbedPane ());
        uiController.setPopupHelp (popupHelp);
        mainFrame.addAttemptToCloseListener (new CommonWindowClosingListener ()
        {
            public void stateChanged (CommonWindowClosingEvent e)
            {
                AppState appState = AppState.getState ();
                appState.set (LafManager.getCurrentLaf ().getCls (),      "LookAndFeel");
                appState.set (LafManager.getCurrentLaf ().getCurTheme (), "Theme");
                getWindowLayoutProps ();
                appState.save ();
            }
        });
        mainFrame.addClosingListener (new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                RunQueue.getInstance ().stop ();
                dataModelMgr2.disconnect ();
            }
        });
        RunQueue.getInstance().setUiController (uiController);   // Also, starts the queue.


        MNode winProps = AppState.getState ().getNode ("WinLayout");

        MNode m = winProps.child ("MainFrame");
        if (m != null)
        {
            ensureSizeLoc (mainFrame, m);
            mainFrame.setVisible (true);

            String[] childTypeIds = mainFrame.getRegisteredTypeIds ();
            for (String id : childTypeIds)
            {
                MNode c = winProps.child ("ChildFrame", id);
                if (c != null)
                {
                    CommonWindow childWin = mainFrame.createChildWindow (id, null);
                    ensureSizeLoc (childWin, c);
                    mainFrame.openChildWindow (id, null);
                }
            }
        }
        else
        {
            mainFrame.setVisible (true);
        }
        mainFrame.setExtendedState (winProps.getInt (0, "MainFrame", "state"));

        setUncaughtExceptionHandler(mainFrame);
        Dialogs.registerApplicationWindow(mainFrame, Application.getName());
        if(loadingFrame != null) {
            loadingFrame.dispose();
        }
        uiController.testConnect (new ChangeListener ()
        {
            public void stateChanged (ChangeEvent arg0)
            {
                mainFrame.init ();
            }
        });
    }

    private static void ensureSizeLoc (CommonWindow win, MNode winProps)
    {
        int w = winProps.getInt (-1, "width");
        int h = winProps.getInt (-1, "height");
        int x = winProps.getInt (-1, "x");
        int y = winProps.getInt (-1, "y");
        if (w >= 0  &&  h >= 0) win.setSize (w, h);
        if (x >= 0  &&  y >= 0) win.setLocation (x, y);
        else                    win.setLocationRelativeTo (win.getParent ());
        win.ensureOnScreen (true);
    }

    private static Map<String, String[]> readPopupHelp() {
        String content = FileUtil.getTextContent(UMF.class.getResourceAsStream("ui/help.txt"));
        JSONObject obj = new JSONObject(content);
        Iterator<?> iter = obj.keys();
        Map<String, String[]> popupHelp = new HashMap<String, String[]>();
        while(iter.hasNext()) {
            String key = (String) iter.next();
            JSONObject entry = obj.getJSONObject(key);
            String[] topicContent = new String[] {entry.getString("topic"), entry.getString("content")};
            popupHelp.put(key, topicContent);
        }
        return popupHelp;
    }


    ////////////////////////////////
    // UNCAUGHT EXCEPTION HANDLER //
    ////////////////////////////////

    protected static void setUncaughtExceptionHandler(final JFrame mainFrame) {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            public void uncaughtException(Thread th, final Throwable t) {
                handleUnexpectedError(th, t, null);
            }
        });
    }

    public static void handleUnexpectedError(Thread th, final Throwable t, String msg) {
        // TODO: CommonThread needs a way to ignore these errors too maybe... or not?  Maybe common thread code and print to system err any time it thinks UI errors are being thrown within it...
        // A hint to use invokeLater.
        StackTraceElement[] elems = t.getStackTrace();
        if(elems != null && elems.length > 0) {
            for(int i = 0; i < 3 && i < elems.length; i++) {
                StackTraceElement ste = elems[i];
                String[] ignoreClasses = {"BasicListUI", "BasicTableUI", "BasicTreeUI", "BufferStrategyPaintManager", "BaseTabbedPaneUI", "BasicTabbedPaneUI", "VariableHeightLayoutCache"};
                for(String ignoreClass : ignoreClasses) {
                    if(ste.getClassName().contains(ignoreClass)) {
                        // ignored a non-threading UI issue...... :(  need to fix.
//                        return;
                    }
                }
            }
        }
        logger.error("An unexpected error has occurred.", t);
        LogManager.log(mainFrame, LogEntryType.ERROR, msg, t);
    }
}
