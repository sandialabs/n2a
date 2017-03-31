/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;

public class PanelMRU extends JPanel
{
    public JList<PanelSearch.Holder>            list;
    public DefaultListModel<PanelSearch.Holder> model;

    public PanelMRU ()
    {
        list = new JList<PanelSearch.Holder> (model = new DefaultListModel<PanelSearch.Holder> ());
        list.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer (new PanelSearch.MNodeRenderer ());
        list.setFocusable (false);
        list.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                int index = list.getSelectedIndex ();
                if (index >= 0) PanelSearch.recordSelected (model.get (index).doc);
                list.clearSelection ();
            }
        });

        setMinimumSize (new Dimension (0, 0));
        setFocusable (false);
        setLayout (new BorderLayout ());
        add (list, BorderLayout.CENTER);

        // Load MRU from app data
        int index = 0;
        for (MNode n : AppData.state.childOrCreate ("PanelModel", "MRU"))
        {
            String name = n.get ();
            MNode part = AppData.models.child (name);
            if (part != null) model.add (index++, new PanelSearch.Holder (part));
        }
    }

    public void saveMRU ()
    {
        int limit = (list.getLastVisibleIndex () + 1) * 2;  // roughly twice the length of the visible list, which could be zero
        MNode parts = AppData.state.childOrCreate ("PanelModel", "MRU");
        parts.clear ();
        for (int i = 0; i < model.size ()  &&  i < limit; i++)
        {
            parts.set (i, model.get (i).doc.key ());
        }
    }

    public void removeDoc (MNode doc)
    {
        int index = model.indexOf (new PanelSearch.Holder (doc));
        if (index >= 0) model.remove (index);
        saveMRU ();
    }

    public void insertDoc (MNode doc)
    {
        PanelSearch.Holder h = new PanelSearch.Holder (doc);
        int index = model.indexOf (h);
        if (index < 0) model.add (0, h);
        saveMRU ();
    }

    public void useDoc (MNode doc)
    {
        PanelSearch.Holder h = new PanelSearch.Holder (doc);
        int index = model.indexOf (h);
        if (index >= 0) model.remove (index);
        model.add (0, h);
        saveMRU ();
    }
}
