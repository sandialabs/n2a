/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
    public JList<String>            list;
    public DefaultListModel<String> model;
    public boolean                  dontInsert;  // Ignore next call to insertDoc()

    public PanelMRU ()
    {
        list = new JList<String> (model = new DefaultListModel<String> ());
        list.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer (new PanelSearch.MNodeRenderer ());
        list.setFocusable (false);
        list.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                int index = list.getSelectedIndex ();
                if (index >= 0) PanelSearch.recordSelected (AppData.references.child (model.get (index)));
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
            if (AppData.references.child (name) != null) model.add (index++, name);
        }
    }

    public void saveMRU ()
    {
        int limit = (list.getLastVisibleIndex () + 1) * 2;  // roughly twice the length of the visible list, which could be zero
        MNode parts = AppData.state.childOrCreate ("PanelReference", "MRU");
        parts.clear ();
        for (int i = 0; i < model.size ()  &&  i < limit; i++)
        {
            parts.set (model.get (i), i);
        }
    }

    public void removeDoc (String key)
    {
        int count = model.size ();
        for (int i = 0; i < count; i++)
        {
            if (model.get (i).equals (key))
            {
                model.remove (i);
                saveMRU ();
                return;
            }
        }
    }

    public void updateDoc (String oldKey, String newKey)
    {
        int count = model.size ();
        for (int i = 0; i < count; i++)
        {
            if (model.get (i).equals (oldKey))
            {
                model.setElementAt (newKey, i);
                return;
            }
        }
    }

    public void insertDoc (String key)
    {
        if (dontInsert)
        {
            dontInsert = false;
            return;
        }
        int index = model.indexOf (key);
        if (index >= 0) return;  // nothing to do
        model.add (0, key);
        saveMRU ();
    }

    public void useDoc (MNode doc)
    {
        String key = doc.key ();
        int index = model.indexOf (key);
        if (index >= 0) model.remove (index);
        model.add (0, key);
        saveMRU ();
    }

    public boolean hasDoc (MNode doc)
    {
        String key = doc.key ();
        int index = model.indexOf (key);
        return index >= 0;
    }
}
