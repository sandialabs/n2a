/*
Copyright 2017-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref;

import java.awt.BorderLayout;
import java.awt.FontMetrics;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MCombo;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNodeListener;
import gov.sandia.n2a.ui.settings.SettingsLookAndFeel;

@SuppressWarnings("serial")
public class PanelReference extends JPanel implements MNodeListener
{
    public static PanelReference instance;  ///< Technically, this class is a singleton, because only one would normally be created.

    public JSplitPane  split;
    public JSplitPane  splitMRU;
    public PanelMRU    panelMRU;
    public PanelSearch panelSearch;
    public PanelEntry  panelEntry;

    public PanelReference ()
    {
        instance = this;

        panelMRU    = new PanelMRU ();
        panelSearch = new PanelSearch ();
        panelEntry  = new PanelEntry ();

        splitMRU = new JSplitPane (JSplitPane.VERTICAL_SPLIT, panelMRU, panelSearch);
        split = new JSplitPane (JSplitPane.HORIZONTAL_SPLIT, splitMRU, panelEntry);

        setLayout (new BorderLayout ());
        add (split, BorderLayout.CENTER);
        setFocusCycleRoot (true);

        // Determine the split positions.

        setSplits ();
        splitMRU.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                if (SettingsLookAndFeel.rescaling) return;  // Don't record change if we are handling a change in screen resolution.
                float value = (Integer) e.getNewValue ();
                AppData.state.setTruncated (value / SettingsLookAndFeel.em, 2, "PanelReference", "dividerMRU");
            }
        });

        split.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                if (SettingsLookAndFeel.rescaling) return;
                float value = (Integer) e.getNewValue ();
                AppData.state.setTruncated (value / SettingsLookAndFeel.em, 2, "PanelReference", "divider");
            }
        });

        AppData.docs.childOrCreate ("references").addListener (this);
    }

    public void updateUI ()
    {
        super.updateUI ();
        if (split != null) setSplits ();
    }

    public void setSplits ()
    {
        FontMetrics fm = panelSearch.list.getFontMetrics (panelSearch.list.getFont ());
        float em = SettingsLookAndFeel.em;
        splitMRU.setDividerLocation ((int) Math.round (AppData.state.getOrDefault (fm.getHeight () * 4 / em, "PanelReference", "dividerMRU") * em));
        split   .setDividerLocation ((int) Math.round (AppData.state.getOrDefault (30.0,                     "PanelReference", "divider"   ) * em));
    }

    public void changed ()
    {
        panelMRU.loadMRU ();
        panelSearch.search ();
        panelEntry.checkVisible ();
        MNode record = panelEntry.model.record;
        if (record == null) return;
        if (((MCombo) AppData.docs.child ("references")).isVisible (record))
        {
            panelEntry.model.record = null;
            panelEntry.model.setRecord (record);
        }
        else
        {
            panelEntry.recordDeleted (record);
        }
    }

    public void childAdded (String key)
    {
        panelMRU.insertDoc (key);
        panelSearch.insertDoc (key);
    }

    public void childDeleted (String key)
    {
        panelMRU.removeDoc (key);
        panelSearch.removeDoc (key);
        panelEntry.checkVisible ();
    }

    public void childChanged (String oldKey, String newKey)
    {
        panelMRU.updateDoc (oldKey, newKey);
        panelSearch.updateDoc (oldKey, newKey);

        String key = "";
        MNode record = panelEntry.model.record;
        if (record != null) key = record.key ();

        boolean contentOnly = oldKey.equals (newKey);
        if (key.equals (newKey))
        {
            if (contentOnly)
            {
                panelEntry.model.record = null;  // Force rebuild of display
                panelEntry.model.setRecord (AppData.docs.child ("references", newKey));
            }
            else
            {
                panelEntry.checkVisible ();
            }
        }
        if (contentOnly) return;  // nothing more to do

        MNode oldDoc = AppData.docs.child ("references", oldKey);
        if (oldDoc == null)  // deleted
        {
            panelEntry.checkVisible ();
        }
        else  // oldDoc has changed identity
        {
            // We renamed oldKey in search and MRU, so need to add it back in.
            panelMRU.insertDoc (oldKey);
            panelSearch.insertDoc (oldKey);

            if (key.equals (oldKey))
            {
                panelEntry.model.record = null;
                panelEntry.model.setRecord (oldDoc);
            }
        }
    }
}
