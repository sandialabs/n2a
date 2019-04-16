/*
Copyright 2017-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref;

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

@SuppressWarnings("serial")
public class PanelMRU extends JPanel
{
    public JList<MNode>            list;
    public DefaultListModel<MNode> model;
    public boolean                 dontInsert;  // Ignore next call to insertDoc()

    public PanelMRU ()
    {
        list = new JList<MNode> (model = new DefaultListModel<MNode> ());
        list.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer (new PanelSearch.MNodeRenderer ());
        list.setFocusable (false);
        list.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                int index = list.getSelectedIndex ();
                if (index >= 0) PanelSearch.recordSelected (model.get (index));
                list.clearSelection ();
            }
        });

        setMinimumSize (new Dimension (0, 0));
        setFocusable (false);
        setLayout (new BorderLayout ());
        add (list, BorderLayout.CENTER);

        loadMRU ();
    }

    public void loadMRU ()
    {
        model.clear ();
        int index = 0;
        for (MNode n : AppData.state.childOrCreate ("PanelReference", "MRU"))
        {
            String name = n.get ();
            MNode part = AppData.references.child (name);
            if (part != null) model.add (index++, part);
        }
    }

    public void saveMRU ()
    {
        int limit = (list.getLastVisibleIndex () + 1) * 2;  // roughly twice the length of the visible list, which could be zero
        MNode parts = AppData.state.childOrCreate ("PanelReference", "MRU");
        parts.clear ();
        for (int i = 0; i < model.size ()  &&  i < limit; i++)
        {
            parts.set (model.get (i).key (), i);
        }
    }

    public void removeDoc (String key)
    {
        int count = model.size ();
        for (int i = 0; i < count; i++)
        {
            MNode n = model.get (i);
            if (n.key ().equals (key))
            {
                model.remove (i);
                saveMRU ();
                return;
            }
        }
    }

    public void updateDoc (MNode doc)
    {
        String key = doc.key ();
        int count = model.size ();
        for (int i = 0; i < count; i++)
        {
            MNode n = model.get (i);
            if (n.key ().equals (key))
            {
                if (n != doc) model.setElementAt (doc, i);
                return;
            }
        }
    }

    public void insertDoc (MNode doc)
    {
        if (dontInsert)
        {
            dontInsert = false;
            return;
        }
        int index = model.indexOf (doc);
        if (index >= 0) return;  // nothing to do
        model.add (0, doc);
        saveMRU ();
    }

    public void useDoc (MNode doc)
    {
        int index = model.indexOf (doc);
        if (index >= 0) model.remove (index);
        model.add (0, doc);
        saveMRU ();
    }
}
