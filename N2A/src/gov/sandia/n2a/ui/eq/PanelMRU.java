/*
Copyright 2017-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.eq.PanelSearch.Holder;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

@SuppressWarnings("serial")
public class PanelMRU extends JPanel
{
    public JList<PanelSearch.Holder>            list;
    public DefaultListModel<PanelSearch.Holder> model;
    public boolean                              dontInsert;  // Ignore next call to insertDoc()

    public PanelMRU ()
    {
        list = new JList<PanelSearch.Holder> (model = new DefaultListModel<PanelSearch.Holder> ());
        list.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer (new PanelSearch.MNodeRenderer ());
        list.setFocusable (false);
        list.setDragEnabled (true);
        list.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                int index = list.getSelectedIndex ();
                if (index >= 0) PanelSearch.recordSelected (model.get (index).doc);
                list.clearSelection ();
            }
        });
        list.setTransferHandler (new TransferHandler ()
        {
            public boolean canImport (TransferSupport xfer)
            {
                return xfer.isDataFlavorSupported (DataFlavor.stringFlavor);
            }

            public boolean importData (TransferSupport xfer)
            {
                list.clearSelection ();

                TransferHandler th = PanelModel.instance.panelSearch.transferHandler;
                if (! xfer.isDrop ()) return th.importData (xfer);
                Transferable xferable = xfer.getTransferable ();
                if (! xferable.isDataFlavorSupported (TransferableNode.nodeFlavor)) return th.importData (xfer);

                try
                {
                    TransferableNode xferNode = (TransferableNode) xferable.getTransferData (TransferableNode.nodeFlavor);
                    if (xferNode.newPartName == null) return th.importData (xfer);

                    MNode doc = AppData.models.child (xferNode.newPartName);
                    if (doc == null) return th.importData (xfer);

                    JList.DropLocation dl = (JList.DropLocation) xfer.getDropLocation ();
                    int newIndex = dl.getIndex ();
                    if (newIndex < 0) newIndex = 0;

                    PanelSearch.Holder h = new PanelSearch.Holder (doc);
                    int oldIndex = model.indexOf (h);
                    if (newIndex != oldIndex)
                    {
                        if (oldIndex >= 0) model.remove (oldIndex);
                        model.add (newIndex, h);
                        saveMRU ();
                    }
                }
                catch (IOException | UnsupportedFlavorException e) {}

                return th.importData (xfer);
            }

            public int getSourceActions (JComponent comp)
            {
                return COPY;
            }

            protected Transferable createTransferable (JComponent comp)
            {
                TransferableNode result = list.getSelectedValue ().createTransferable ();
                result.panel = PanelMRU.this;
                return result;
            }

            protected void exportDone (JComponent source, Transferable data, int action)
            {
                list.clearSelection ();
                PanelModel.instance.undoManager.endCompoundEdit ();  // This is safe, even if there is no compound edit in progress.
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
        for (MNode n : AppData.state.childOrCreate ("PanelModel", "MRU"))
        {
            String name = n.get ();
            MNode part = AppData.models.child (name);
            if (part != null) model.addElement (new PanelSearch.Holder (part));
        }

        // Check for first run.
        if (model.size () == 0)
        {
            MNode part = AppData.models.child ("Example Hodgkin-Huxley Cable");
            if (part != null)
            {
                model.addElement (new PanelSearch.Holder (part));
                AppData.state.set ("Example Hodgkin-Huxley Cable", "PanelModel", "lastUsed");
            }
        }
    }

    public void saveMRU ()
    {
        int limit = (list.getLastVisibleIndex () + 1) * 2;  // roughly twice the length of the visible list, which could be zero
        MNode parts = AppData.state.childOrCreate ("PanelModel", "MRU");
        parts.clear ();
        for (int i = 0; i < model.size ()  &&  i < limit; i++)
        {
            parts.set (model.get (i).doc.key (), i);
        }
    }

    public void removeDoc (String key)
    {
        int count = model.size ();
        for (int i = 0; i < count; i++)
        {
            Holder h = model.get (i);
            if (h.doc.key ().equals (key))
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
            Holder h = model.get (i);
            if (h.doc.key ().equals (key))
            {
                if (h.doc != doc)
                {
                    h.doc = doc;
                    model.setElementAt (h, i);  // Force repaint of the associated row.
                }
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
        PanelSearch.Holder h = new PanelSearch.Holder (doc);
        int index = model.indexOf (h);
        if (index >= 0) return;  // nothing to do
        model.add (0, h);
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

    public void renamed ()  // No need to specify doc, because it should already be at top of list
    {
        list.repaint ();
        saveMRU ();
    }
}
