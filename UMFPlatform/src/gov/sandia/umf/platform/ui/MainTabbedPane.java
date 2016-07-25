/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditPanel;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.plugins.UMFPluginManager;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;
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
import replete.gui.tabbed.TabCloseEvent;
import replete.gui.tabbed.TabCloseListener;

public class MainTabbedPane extends AdvancedTabbedPane
{
    private static final String SEARCH_TAB_KEY = "Search";

    private UIController uiController;
    public SearchPanel panelSearch;
    public RecordEditPanel panelEdit;

    private boolean noHistoryFire = false;
    private int historyLocation = -1;
    private List<String> history = new ArrayList<String>();


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier historyNotifier = new ChangeNotifier(this);

    public void addHistoryListener (ChangeListener listener)
    {
        historyNotifier.addListener (listener);
    }

    protected void fireHistoryNotifier ()
    {
        historyNotifier.fireStateChanged ();
    }

    // ///////////////
    // CONSTRUCTOR //
    // ///////////////

    public MainTabbedPane (UIController uic)
    {
        super (true);
        uiController = uic;

        // Search panel
        panelSearch = new SearchPanel (uiController);
        panelSearch.addSelectRecordListener (new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                List<MNode> doc = panelSearch.getSelectedRecords ();
                uiController.openRecord (doc.get (0));
            }
        });
        addTab (SEARCH_TAB_KEY, ImageUtil.getImage ("mag.gif"), panelSearch, null);
        historyNewTab (SEARCH_TAB_KEY);
        setCloseableAt (0, false);

        // Listeners
        addTabCloseListener (new TabCloseListener ()
        {
            public void stateChanged (TabCloseEvent e)
            {
                String removedKey = getKeyAt (e.getIndex ());
                historyRemoveEntry (removedKey);
            }
        });
        addChangeListener (new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                int index = getSelectedIndex ();
                if (index != -1)
                {
                    if (!noHistoryFire)
                    {
                        historyNewTab (getKeyAt (index));
                    }
                    Component cmp = getComponentAt (index);
                    if (cmp instanceof DefaultButtonEnabledPanel)
                    {
                        DefaultButtonEnabledPanel pnlDbe = (DefaultButtonEnabledPanel) cmp;
                        getRootPane ().setDefaultButton (pnlDbe.getDefaultButton ());
                        return;
                    }
                }
                getRootPane ().setDefaultButton (null);
            }
        });
    }

    public void openRecordTab (MNode node)
    {
        int index = indexOfTabByKey ("Model");
        if (index >= 0)
        {
            panelEdit.loadFromRecord (node);
            setSelectedIndex (index);
        }
        else  // Model tab not open yet. TODO: create and insert model tab at startup, just like Search tab is now. (Combine Search and Model into one tab.)
        {
            RecordHandler handler = UMFPluginManager.getHandler ("Model");
            panelEdit = handler.getRecordEditPanel (uiController, node);
            addTab ("Model", handler.getIcon (), panelEdit, "Model", "Model");
            index = indexOfTabByKey ("Model");
            setSelectedIndex (index);
            panelEdit.doInitialFocus ();
            // TODO: post layout not always called when expected (not actually layed out yet).
            panelEdit.postLayout ();
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

    private void closeTab (int i)
    {
        // Need to manually update the history for now since
        // removeTabAt does not fire closing events yet.
        String removedKey = getKeyAt (i);
        if (! removedKey.equals (SEARCH_TAB_KEY))
        {
            historyRemoveEntry (removedKey);
            removeTabAt (i);
        }
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
}
