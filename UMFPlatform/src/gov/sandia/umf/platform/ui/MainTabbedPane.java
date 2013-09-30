/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.AppState;
import gov.sandia.umf.platform.connect.orientdb.expl.OrientDbExplorerPanel;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditPanel;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;
import gov.sandia.umf.platform.ui.home.HomeTabPanel;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.search.DefaultButtonEnabledPanel;
import gov.sandia.umf.platform.ui.search.SearchPanel;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.tabbed.AdvancedTabbedPane;
import replete.gui.tabbed.TabAboutToCloseEvent;
import replete.gui.tabbed.TabAboutToCloseListener;
import replete.gui.tabbed.TabCloseEvent;
import replete.gui.tabbed.TabCloseListener;

public class MainTabbedPane extends AdvancedTabbedPane {


    ////////////
    // FIELDS //
    ////////////

    // Const

    private static final String HOME_TAB_KEY = "Home";
    private static final String SEARCH_ORIENT_TAB_KEY = "Search";
    private static final String EXPLORE_ORIENT_TAB_KEY = "Explore";

    // Core

    private UIController uiController;

    // Misc

    private boolean noHistoryFire = false;
    private int historyLocation = -1;
    private List<String> history = new ArrayList<String>();


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier historyNotifier = new ChangeNotifier(this);
    public void addHistoryListener(ChangeListener listener) {
        historyNotifier.addListener(listener);
    }
    protected void fireHistoryNotifier() {
        historyNotifier.fireStateChanged();
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public MainTabbedPane(UIController uic) {
        super(true);
        uiController = uic;

        // Home panel
        HomeTabPanel pnlHome = new HomeTabPanel(uiController);
        TabState homeTabState = new TabState(TabStateType.HOME);
        addTab(HOME_TAB_KEY, ImageUtil.getImage("home.gif"), pnlHome, null);
        setAdditionalInfo(0, homeTabState);
        historyNewTab(HOME_TAB_KEY);
        setCloseableAt(0, false);

        // Listeners
        addTabAboutToCloseListener(new TabAboutToCloseListener() {
            public void stateChanged(TabAboutToCloseEvent e) {
                if(!uiController.okToCloseCurrentTab()) {
                    e.cancel();
                }
            }
        });
        addTabCloseListener(new TabCloseListener() {
            public void stateChanged(TabCloseEvent e) {
                String removedKey = getKeyAt(e.getIndex());
                historyRemoveEntry(removedKey);
            }
        });
        addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = getSelectedIndex();
                if(index != -1) {
                    if(!noHistoryFire) {
                        historyNewTab(getKeyAt(index));
                    }
                    Component cmp = getComponentAt(index);
                    if(cmp instanceof DefaultButtonEnabledPanel) {
                        DefaultButtonEnabledPanel pnlDbe = (DefaultButtonEnabledPanel) cmp;
                        getRootPane().setDefaultButton(pnlDbe.getDefaultButton());
                        return;
                    }
                }
                getRootPane().setDefaultButton(null);
            }
        });
    }


    //////////
    // OPEN //
    //////////

    // --- New OrientDB --- //

    public void openSearchOrient() {
        int searchIdx = indexOfTabByKey(SEARCH_ORIENT_TAB_KEY);
        if(searchIdx == -1) {
            final SearchPanel pnlSearch = new SearchPanel(uiController);
            pnlSearch.addSelectRecordListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                  List<NDoc> doc = pnlSearch.getSelectedRecords();
                  uiController.openRecord(doc.get(0));
                }
            });
            insertTab(SEARCH_ORIENT_TAB_KEY, ImageUtil.getImage("mag.gif"), pnlSearch, null, 1);
            TabState searchOrientTabState = new TabState(TabStateType.SEARCH_ORIENT);
            setAdditionalInfo(1, searchOrientTabState);
            searchIdx = 1;
        }
        setSelectedIndex(searchIdx);
        SearchPanel pnlSearch = (SearchPanel) getComponentAt(searchIdx);
        pnlSearch.doFocus();
    }
    public void openExplore() {
        int exploreIdx = indexOfTabByKey(EXPLORE_ORIENT_TAB_KEY);
        if(exploreIdx == -1) {
            final OrientDbExplorerPanel pnlExplore = new OrientDbExplorerPanel(uiController);
            pnlExplore.addDefaultSource();
            addTab(EXPLORE_ORIENT_TAB_KEY, ImageUtil.getImage("explore.gif"),
                pnlExplore, null, EXPLORE_ORIENT_TAB_KEY);
            int newIndex = indexOfTabByKey(EXPLORE_ORIENT_TAB_KEY);
            TabState exploreOrientTabState = new TabState(TabStateType.EXPLORE_ORIENT);
            setAdditionalInfo(newIndex, exploreOrientTabState);
            exploreIdx = newIndex;
        }
        setSelectedIndex(exploreIdx);
        OrientDbExplorerPanel pnlExplore = (OrientDbExplorerPanel) getComponentAt(exploreIdx);
        pnlExplore.doFocus();
    }

    public void openRecordTabOrient(NDoc doc) {
        String tabKey;
        String typeName = doc.getClassName();
        if(doc.isNew()) {
            tabKey = typeName + "-NEW-" + doc.getId();// -NEW- used later to determine if temp
        } else {
            tabKey = typeName + "-" + doc.getId();
        }
        int index = indexOfTabByKey(tabKey);
        if(index == -1) {
            String title;
            if(doc.isNew()) {
                title = "NEW";
            } else {
                title = getOrientTabTitle(doc);
            }
            String tip = doc.isNew() ? "New " + typeName : title;
            RecordEditPanel pnlEdit = makeRecordTabPanelOrient(doc);
            TabState orientTabState = new TabState(TabStateType.ORIENT_RECORD);
            orientTabState.setDbType(TabStateDbType.ORIENT);
            orientTabState.setBeanClass(doc.getClassName());
            orientTabState.setOrientId(doc.getId());
            addTab(title, doc.getIcon(), pnlEdit, tip, tabKey);
            index = indexOfTabByKey(tabKey);
            setAdditionalInfo(index, orientTabState);
            if(doc.isNew()) {
                setDirtyAt(index, true);
            }
            setSelectedIndex(index);
            pnlEdit.doInitialFocus();
            // TODO: post layout not always called when expected (not actually layed out yet).
            pnlEdit.postLayout();
//            uiController.addRecent(bean); TODO
        } else {
            setSelectedIndex(index);
        }
    }

    private String getOrientTabTitle(NDoc doc) {
        String beanTitle = doc.getTitle();
        if(beanTitle == null || beanTitle.trim().equals("")) {
            beanTitle = "(no title)";
        }
        if(beanTitle.length() > 20) {
            beanTitle = beanTitle.substring(0, 20) + "...";
        }
        return beanTitle;
    }

    private RecordEditPanel makeRecordTabPanelOrient(final NDoc doc) {
        final RecordEditPanel pnlRecord = recordTabFactoryOrient(doc);
        pnlRecord.addDirtyChangedNotifier(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = indexOfComponent(pnlRecord);
                // This is needed because of the way the attemptToSave* methods work.  They
                // validate/verify, kick off a thread to do the saving and then immediately
                // return, allowing a close operation to also immediately remove the tab.
                // Then the save thread saves and calls makeClean, which changes the dirty
                // state of a panel that no longer has a tab holding it.  Thus the index != -1.
                // Maybe could redesign this some day.  Purpose of course is to allow the
                // UI thread not to lock up during the save, and get mouse/progress bar activated
                // during save.
                if(index != -1) {
                    if(!pnlRecord.isDirty()) {
                        String beanTitle = getOrientTabTitle(doc);
                        setTitleAt(index, beanTitle);
                        String curKey = getKeyAt(index);
                        if(isTempKey(curKey)) {
                            String typeName = doc.getClassName();
                            String newKey = typeName + "-" + doc.getId();
                            changeKey(curKey, newKey);
                        }
                    }
                    setDirtyAt(index, pnlRecord.isDirty());
                    updateUI();
                }
            }
        });
        return pnlRecord;
    }

    private RecordEditPanel recordTabFactoryOrient(NDoc doc) {
        RecordHandler handler = doc.getHandler();
        return handler.getRecordEditPanel(uiController, doc);
    }

    // Uses object identity for comparison because of the way
    // that the record panels maintain their state (just have
    // single instance of a bean throughout the life of the
    // panel).  Also enables this to remove new records that
    // may have random tab keys.
    public void closeTab(final NDoc record) {
        for(int i = getTabCount() - 1; i >= 1; i--) {
            Component c = getComponentAt(i);
            if(c instanceof RecordEditPanel) {
                if(((RecordEditPanel) c).getRecord() == record) {
                    closeTab(i);
                }
            }
        }
    }

    public void closeCurrentTabNoSave() {
        closeTab(getSelectedIndex());
    }

    // Closes all the tabs without attempting to save.
    public void closeAllTabsNoSave() {
        for(int i = getTabCount() - 1; i >= 1; i--) {
            closeTab(i);
        }
    }

    private void closeTab(int i) {
        // Need to manually update the history for now since
        // removeTabAt does not fire closing events yet.
        String removedKey = getKeyAt(i);
        if(!removedKey.equals(HOME_TAB_KEY)) {
            historyRemoveEntry(removedKey);
            removeTabAt(i);
        }
    }


    //////////
    // SAVE //
    //////////

    public String saveCurrentTab() {
        return saveTab(getSelectedIndex());
    }

    public boolean saveAllTabs() {
        boolean hadFailure = false;
        for(int i = 0; i < getTabCount(); i++) {
            if(saveTab(i) != null) {
                hadFailure = true;
            }
        }
        return !hadFailure;
    }

    private String saveTab(int i) {
        if(isDirtyAt(i)) {
            Component c = getComponentAt(i);
            if(c instanceof RecordEditPanel) {
                String msg = ((RecordEditPanel) c).attemptToSaveSynchronous();
                if(msg != null) {
                    return msg;
                }
            }
        }
        return null;
    }


    /////////////
    // HISTORY //
    /////////////

    public void historyBackTab() {
        if(historyLocation > 0) {
            historyLocation--;
            int index = indexOfTabByKey(history.get(historyLocation));
            noHistoryFire = true;
            if(index != -1) {
                setSelectedIndex(index);
            } else {
                // Safety precaution
                history.remove(historyLocation);
                if(historyLocation == history.size()) {
                    historyLocation--;
                }
            }
            noHistoryFire = false;
        }
//        System.out.println("BACK=" + historyLocation + "/" + history);
        fireHistoryNotifier();
    }

    public void historyForwardTab() {
        if(historyLocation < history.size() - 1) {
            historyLocation++;
            int index = indexOfTabByKey(history.get(historyLocation));
            noHistoryFire = true;
            if(index != -1) {
                setSelectedIndex(index);
            } else {
                // Safety precaution
                history.remove(historyLocation);
                if(historyLocation == history.size()) {
                    historyLocation--;
                }
            }
            noHistoryFire = false;
        }
//        System.out.println("FORWARD=" + historyLocation + "/" + history);
        fireHistoryNotifier();
    }

    public void historyNewTab(String key) {
        for(int h = history.size() - 1; h >= historyLocation + 1; h--) {
            history.remove(h);
        }
        history.remove(key);
        history.add(key);
        historyLocation = history.size() - 1;
//        System.out.println("NEW=" + historyLocation + "/" + history);
        fireHistoryNotifier();
    }

    private void historyRemoveEntry(String removedKey) {
        history.remove(removedKey);
        if(historyLocation == history.size()) {
            historyLocation--;
        }
        fireHistoryNotifier();
    }


    //////////
    // MISC //
    //////////

    private boolean isTempKey(String key) {
        return key.contains("-NEW-");
    }

    @Override
    public void changeKey(String from, String to) {
        super.changeKey(from, to);
        for(int h = 0; h < history.size(); h++) {
            if(history.get(h).equals(from)) {
                history.set(h, to);
            }
        }
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public boolean isDirty() {
        for(int i = 0; i < getTabCount(); i++) {
            if(isDirtyAt(i)) {
                return true;
            }
        }
        return false;
    }

    public int getHistoryLocation() {
        return historyLocation;
    }

    public int getHistorySize() {
        return history.size();
    }

    public List<TabState> getTabStates() {
        List<TabState> states = new ArrayList<TabState>();
        for(int i = 0; i < getTabCount(); i++) {
            states.add((TabState) getAdditionalInfo(i));
        }
        return states;
    }
    public void initFromAppState() {
        if(true) {
            return;
        }
        List<TabState> states = AppState.getState().getTabStates();
        for(TabState state : states) {
            switch(state.getType()) {
                case HOME: break;  // default
                case SEARCH_ORIENT: openSearchOrient(); break;
                case ORIENT_RECORD:
                    NDoc record = uiController.getDMM().getDataModel().getRecord(state.getBeanClass(), state.getOrientId());
                    openRecordTabOrient(record);
//                    uiController.openRecord(state.getBeanClass(), state.getOrientId());
                    break;
            }
        }
    }
}
