/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform;

import gov.sandia.umf.platform.connect.orientdb.ui.ConnectionManager;
import gov.sandia.umf.platform.connect.orientdb.ui.ConnectionModel;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientConnectDetails;
import gov.sandia.umf.platform.plugins.UMFPluginManager;
import gov.sandia.umf.platform.plugins.base.PlatformPlugin;
import gov.sandia.umf.platform.plugins.base.PlatformProductCustomization;
import gov.sandia.umf.platform.plugins.extpoints.ProductCustomization;
import gov.sandia.umf.platform.runs.RunQueue;
import gov.sandia.umf.platform.ui.AboutDialog;
import gov.sandia.umf.platform.ui.MainFrame;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.wp.WorkpaneModel;
import gov.sandia.umf.platform.ui.wp.WorkpaneRecord;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

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
import replete.util.ReflectionUtil;
import replete.util.User;
import replete.xstream.SerializationResult;
import replete.xstream.XStreamWrapper;

public class UMF {

    public static File getAppResourceDir() {
        return new File(User.getHome(), ".umf");
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
    private static WorkpaneModel workpaneModel;
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
        Application.setVersion(VersionConstants.MAJOR + "." +
                               VersionConstants.MINOR + "." +
                               VersionConstants.SERVICE);
        Application.setTitle("Unified Modeling Framework");

        XStreamWrapper.addAlias(SerializationResult.class.getSimpleName(), SerializationResult.class);
        XStreamWrapper.addAlias(AppState.class.getSimpleName(), AppState.class);
        XStreamWrapper.addAlias(WorkpaneRecord.class.getSimpleName(), WorkpaneRecord.class);

        AppState.load();

        ConnectionModel mdl2 = AppState.getState().getConnectModel();
        if(mdl2.getList().size() == 0) {
            File repos = new File(getAppResourceDir(), "repos");
            File dflt = new File(repos, "default-local");
            OrientConnectDetails details = new OrientConnectDetails(
                "Default Local",
                "local:" + dflt.getAbsolutePath(), "admin", "admin");
            mdl2.getList().add(details);
            mdl2.setSelected(details);
        }

        String[] pluginMem = ArrayUtil.translate(String.class, pluginValuesCL);

        File[] pluginDirs = ArrayUtil.translate(File.class, pluginDirsCL, new ArrayTranslator() {
            public Object translate(Object o) {
                return new File((String) o);
            }
        });
        pluginDirs = ArrayUtil.cat(pluginDirs, new File (getAppResourceDir (), "plugins"));

        if(!PluginManager.initialize(new PlatformPlugin(), pluginMem, pluginDirs)) {
            System.err.println(PluginManager.getInitializationErrors());
        }

        UMFPluginManager.init();

        setUncaughtExceptionHandler(null);

        ProductCustomization pc = chooseProductCustomization(prodCust);
        AppState.getState().setProductCustomization(pc);

        workpaneModel = AppState.getState().getWorkpaneModel();
        //workpaneModel.removeTempRecords();

        // Read L&F from properties.
        String lafClassName = AppState.getState().getLookAndFeel();
        String lafTheme = AppState.getState().getTheme();
        LafManager.initialize(lafClassName, lafTheme);

        LogManager.setLogFile(new File(User.getHome(), "n2a.log"));

        // Read popup help.
        popupHelp = readPopupHelp();

        dataModelMgr2 = ConnectionManager.getInstance();
        dataModelMgr2.setConnectDetails(AppState.getState().getConnectModel().getSelected());

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
        
        // capture memory info regularly
        Timer t = new Timer(true);
        int period = 5000;
        t.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                writeDebugInfo();
            }
        }, period, period);
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

    private static Map<String, Object> getWindowLayoutProps() {
        Map<String, Object> winProps = new TreeMap<String, Object>() {
            @Override
            public Object put(String key, Object o) {
                super.put(key, o.toString());
                return o;
            }
        };
        winProps.put("mainFrame.x", mainFrame.getX());
        winProps.put("mainFrame.y", mainFrame.getY());
        winProps.put("mainFrame.width", mainFrame.getWidth());
        winProps.put("mainFrame.height", mainFrame.getHeight());
        winProps.put("mainFrame.state", mainFrame.getExtendedState());
        winProps.put("mainFrame.workpane.divloc", mainFrame.getWorkpaneDivLoc());

        List<CommonWindow> childWins = mainFrame.getVisibleChildWindows();
        for(CommonWindow win : childWins) {
            String typeId = mainFrame.getTypeIdOfWindow(win);
            winProps.put("childFrame." + typeId + ".x", win.getX());
            winProps.put("childFrame." + typeId + ".y", win.getY());
            winProps.put("childFrame." + typeId + ".width", win.getWidth());
            winProps.put("childFrame." + typeId + ".height", win.getHeight());
        }

        return winProps;
    }

    private static void reloadAppFrame() {
        loadingFrame = new LoadingWindow();
        loadingFrame.setVisible(true);

        AppState.getState().setWinLayout(getWindowLayoutProps());

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

    private static void createAndShowMainFrame() {
        uiController = new UIController(workpaneModel, dataModelMgr2);
        mainFrame = new MainFrame(uiController, workpaneModel);
        MainFrame.setInstance(mainFrame);
        uiController.setParentReference(mainFrame);
        uiController.setTabbedPane(mainFrame.getTabbedPane());
        uiController.setPopupHelp(popupHelp);
        mainFrame.addAttemptToCloseListener(new CommonWindowClosingListener() {
            public void stateChanged(CommonWindowClosingEvent e) {
                AppState.getState().setLookAndFeel(LafManager.getCurrentLaf().getCls());
                AppState.getState().setTheme(LafManager.getCurrentLaf().getCurTheme());
                AppState.getState().setWinLayout(getWindowLayoutProps());
                AppState.getState().getTabStates().clear();
                AppState.getState().getTabStates().addAll(uiController.getTabs().getTabStates());
                AppState.save();
            }
        });
        mainFrame.addClosingListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                RunQueue.getInstance().stop();
                dataModelMgr2.disconnect();
            }
        });
        RunQueue.getInstance().setUiController(uiController);   // Also, starts the queue.


        Map<String, Object> winProps = AppState.getState().getWinLayout();

        int extState = 0;
        // TODO: Why fail on all properties because of one key? -- simplicity yes... laziness.... yes?
        if(winProps != null && winProps.containsKey("mainFrame.x")) {
            Integer es = readPropsInt(winProps, "mainFrame.state");
            if(es != null && es == 1) {
                extState = 0;
            }

            ensureSizeLoc(mainFrame, winProps, "mainFrame");

            Integer divLoc = readPropsInt(winProps, "mainFrame.workpane.divloc");
            if(divLoc != null) {
                mainFrame.setWorkpaneDivLoc(divLoc);
            }

            mainFrame.setVisible(true);

            String[] childTypeIds = mainFrame.getRegisteredTypeIds();
            for(String id : childTypeIds) {
                if(winProps.containsKey("childFrame." + id + ".x")) {
                    CommonWindow childWin = mainFrame.createChildWindow(id, null);
                    ensureSizeLoc(childWin, winProps, "childFrame." + id);
                    mainFrame.openChildWindow(id, null);
                }
            }
        } else {
            mainFrame.setVisible(true);
        }
        mainFrame.setExtendedState(extState);

        setUncaughtExceptionHandler(mainFrame);
        Dialogs.registerApplicationWindow(mainFrame, Application.getName());
        if(loadingFrame != null) {
            loadingFrame.dispose();
        }
        uiController.testConnect(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                uiController.initTabsFromAppState();
            }
        });

    }

    private static void ensureSizeLoc(CommonWindow win, Map<String, Object> winProps, String propKeyPrefix) {
        Integer w = readPropsInt(winProps, propKeyPrefix + ".width");
        Integer h = readPropsInt(winProps, propKeyPrefix + ".height");
        Integer x = readPropsInt(winProps, propKeyPrefix + ".x");
        Integer y = readPropsInt(winProps, propKeyPrefix + ".y");
        if(w != null && h != null) {
            win.setSize(w, h);
        }
        if(x == null || y == null) {
            win.setLocationRelativeTo(win.getParent());
        } else {
            win.setLocation(x, y);
        }
        win.ensureOnScreen(true);
    }

    private static Integer readPropsInt(Map<String, Object> winProps, String key) {
        try {
            return Integer.parseInt((String) winProps.get(key));
        } catch(Exception e) {
            return null;
        }
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
        new Thread() {
            @Override
            public void run() {
                MailUtil.sendErrorMail(t);
            }
        }.start();
        LogManager.log(mainFrame, LogEntryType.ERROR, msg, t);
    }
    
    private static void writeDebugInfo() {
        OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        long totalPhysicalMemory = -1;
        try {
            totalPhysicalMemory = (Long)
                    ReflectionUtil.invoke("getTotalPhysicalMemorySize", bean);
        }
        catch(Exception e) {
        }
        long freePhysicalMemory = -1;
        try {
            freePhysicalMemory = (Long)
                    ReflectionUtil.invoke("getFreePhysicalMemorySize", bean);
        }
        catch(Exception e) {
        }
        
        double systemLoadAvg = bean.getSystemLoadAverage();
        
        // Runtime Memory
        Runtime runtime = Runtime.getRuntime();
        long totalRuntimeMemory = runtime.totalMemory();
        long maxRuntimeMemory = runtime.maxMemory();
        long freeRuntimeMemory = runtime.freeMemory();

        long usedMemory = totalRuntimeMemory - freeRuntimeMemory;
        long realFreeMemory = freeRuntimeMemory + maxRuntimeMemory - totalRuntimeMemory;

        // Disk
         File file = new File(".");
         long totalDiskSpace = file.getTotalSpace();
         long freeDiskSpace = file.getFreeSpace();

         BufferedWriter writer = null;

         File debugFile = new File(User.getHome(), "umf-debug.txt");
         boolean exists = debugFile.exists();
         try {

             writer = new BufferedWriter(new FileWriter(debugFile, true));
             if(!exists) {
                 writer.write("time totphys freephys load totrun maxrun freerun used realfree totdisk freedisk\n");
             }
             writer.write(
                     System.currentTimeMillis() + " " +
                              totalPhysicalMemory + " " +
                              freePhysicalMemory + " " +
                              systemLoadAvg + " " +
                              totalRuntimeMemory + " " +
                              maxRuntimeMemory + " " +
                              freeRuntimeMemory + " " +
                              usedMemory + " " +
                              realFreeMemory + " " +
                              totalDiskSpace + " " +
                              freeDiskSpace + "\n");
         } catch(Exception e) {
                   e.printStackTrace();
                   
         } finally {
             if(writer != null) {
                 try {
                     writer.close();
                 } catch(Exception e) {
                     e.printStackTrace();
                 }
             }
         }
    }
}
