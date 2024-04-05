/*
Copyright 2017-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MCombo;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.ui.MainFrame;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.StringWriter;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

@SuppressWarnings("serial")
public class PanelMRU extends JPanel
{
    public JList<String>            list;
    public DefaultListModel<String> model;
    public boolean                  dontInsert;  // Ignore next call to insertDoc()

    public PanelMRU ()
    {
        model = new DefaultListModel<String> ();
        list = new JList<String> (model);
        list.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer (new MNodeRenderer ());
        list.setFocusable (false);
        list.setDragEnabled (true);
        list.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                int index = list.getSelectedIndex ();
                if (index >= 0)
                {
                    MNode doc = AppData.docs.child ("models", model.get (index));
                    PanelSearch.recordSelected (doc);
                }
                list.clearSelection ();
            }
        });
        list.setTransferHandler (new TransferHandler ()
        {
            public boolean canImport (TransferSupport xfer)
            {
                if (xfer.isDataFlavorSupported (DataFlavor.stringFlavor))       return true;
                if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor)) return true;
                return false;
            }

            public boolean importData (TransferSupport xfer)
            {
                list.clearSelection ();

                TransferHandler th = PanelModel.instance.panelSearch.transferHandler;
                if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor)) return th.importData (xfer);
                if (! xfer.isDrop ()) return th.importData (xfer);
                Transferable xferable = xfer.getTransferable ();
                if (! xferable.isDataFlavorSupported (TransferableNode.nodeFlavor)) return th.importData (xfer);
                // Now we have a DnD within our own app, which means the user is re-ordering entries in the MRU ...

                try
                {
                    TransferableNode xferNode = (TransferableNode) xferable.getTransferData (TransferableNode.nodeFlavor);
                    if (xferNode.newPartName == null) return th.importData (xfer);

                    MNode doc = AppData.docs.child ("models", xferNode.newPartName);
                    if (doc == null) return th.importData (xfer);

                    JList.DropLocation dl = (JList.DropLocation) xfer.getDropLocation ();
                    int newIndex = dl.getIndex ();
                    if (newIndex < 0) newIndex = 0;

                    String key = doc.key ();
                    int oldIndex = model.indexOf (key);
                    if (newIndex != oldIndex)
                    {
                        if (oldIndex >= 0) model.remove (oldIndex);
                        model.add (newIndex, key);
                        saveMRU ();
                    }
                    return true;
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
                String key = list.getSelectedValue ();
                MNode doc = AppData.docs.child ("models", key);

                Schema schema = Schema.latest ();
                schema.type = "Part";
                StringWriter writer = new StringWriter ();
                try
                {
                    schema.write (writer);
                    writer.write (key + String.format ("%n"));
                    for (MNode c : doc) schema.write (c, writer, " ");
                    writer.close ();
                    TransferableNode result = new TransferableNode (writer.toString (), null, false, key);
                    result.panel = PanelMRU.this;
                    return result;
                }
                catch (IOException e) {}

                return null;
            }

            protected void exportDone (JComponent source, Transferable data, int action)
            {
                list.clearSelection ();
                MainFrame.undoManager.endCompoundEdit ();  // This is safe, even if there is no compound edit in progress.
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
            MNode part = AppData.docs.child ("models", name);
            if (part != null) model.addElement (name);
        }

        // Check for first run.
        if (model.size () == 0)
        {
            String name = "Example Hodgkin-Huxley Cable";  // Hard-coded name must appear in "local" db.
            MNode part = AppData.docs.child ("models", name);
            if (part != null)
            {
                model.addElement (name);
                AppData.state.set (name, "PanelModel", "lastUsed");
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

    /**
        Four things can happen when a childChanged() message arrives:
        1) The underlying content of the record has changed;
        2) The key for the document has changed;
        3) An existing document has been exposed under the old key;
        4) An existing document has been hidden under the new key.
        What is the right response to all this?
        #1 -- Repaint, in case the record is coming from a different repo, but otherwise don't worry about identity.
        #2 -- Replace the current key with the new one.
        #3 -- No need to add to list, since the exposed doc isn't necessarily being used.
        #4 -- Get rid of the existing item in the list, since it will share the same key as the renamed document.
    **/
    public void updateDoc (String oldKey, String newKey)
    {
        if (oldKey.equals (newKey))
        {
            list.repaint ();  // A bit lazy, but gets the job done.
            return;
        }

        int count = model.size ();
        for (int i = 0; i < count; i++)
        {
            String key = model.get (i);
            if (key.equals (newKey))
            {
                model.remove (i--);
                count--;
            }
            else if (key.equals (oldKey))
            {
                model.setElementAt (newKey, i);  // Force repaint of the associated row.
            }
        }
        saveMRU ();
    }

    public void insertDoc (MNode doc)
    {
        if (dontInsert)
        {
            dontInsert = false;
            return;
        }
        String key = doc.key ();
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

    public void renamed ()  // No need to specify doc, because it should already be at top of list
    {
        list.repaint ();
        saveMRU ();
    }

    public static class MNodeRenderer extends JTextField implements ListCellRenderer<String>
    {
        protected static DefaultHighlighter.DefaultHighlightPainter painter;

        // These colors may get changed when look & feel is changed.
        public static Color colorInherit          = Color.blue;
        public static Color colorOverride         = Color.black;
        public static Color colorSelectedInherit  = Color.blue;
        public static Color colorSelectedOverride = Color.black;

        public MNodeRenderer ()
        {
            painter = new DefaultHighlighter.DefaultHighlightPainter (UIManager.getColor ("List.selectionBackground"));
            setBorder (new EmptyBorder (0, 0, 0, 0));
        }

        public Component getListCellRendererComponent (JList<? extends String> list, String name, int index, boolean selected, boolean focused)
        {
            setText (name);

            Color color;
            if (((MCombo) AppData.docs.child ("models")).isWritable (name))
            {
                color = selected ? colorSelectedOverride : colorOverride;
            }
            else
            {
                color = null;
                String colorName = "";
                MNode mdir = ((MCombo) AppData.docs.child ("models")).containerFor (name);
                MNode repo = AppData.repos.child (mdir.key ());  // This can return null if multirepo structure changes and this panel is repainted before the change notification arrives.
                if (repo != null) colorName = repo.get ("color");
                if (! colorName.isEmpty ())
                {
                    try
                    {
                        color = Color.decode (colorName);
                        if (color.equals (Color.black)) color = null;  // Treat black as always default. Thus, the user can't explicitly set black, but they can set extremely dark (R=G=B=1).
                    }
                    catch (NumberFormatException e) {}
                }
                if (color == null) color = selected ? colorSelectedInherit : colorInherit;
            }
            setForeground (color);

            if (selected)
            {
                Highlighter h = getHighlighter ();
                h.removeAllHighlights ();
                try
                {
                    h.addHighlight (0, name.length (), painter);
                }
                catch (BadLocationException e)
                {
                }
            }

            return this;
        }

        public void updateUI ()
        {
            super.updateUI ();
            painter = new DefaultHighlighter.DefaultHighlightPainter (UIManager.getColor ("List.selectionBackground"));

            // Check colors to see if text is dark or light.
            Color fg = UIManager.getColor ("List.foreground");
            float[] hsb = Color.RGBtoHSB (fg.getRed (), fg.getGreen (), fg.getBlue (), null);
            if (hsb[2] > 0.5)  // Light text
            {
                colorInherit  = new Color (0xC0C0FF);  // light blue
                colorOverride = Color.white;
            }
            else  // Dark text
            {
                colorInherit  = Color.blue;
                colorOverride = Color.black;
            }

            fg = UIManager.getColor ("List.selectionForeground");  // for Metal, and many other L&Fs
            if (fg == null) fg = UIManager.getColor ("List[Selected].textForeground");  // for Nimbus
            Color.RGBtoHSB (fg.getRed (), fg.getGreen (), fg.getBlue (), hsb);
            if (hsb[2] > 0.5)  // Light text
            {
                colorSelectedInherit  = new Color (0xC0C0FF);
                colorSelectedOverride = Color.white;
            }
            else  // Dark text
            {
                colorSelectedInherit  = Color.blue;
                colorSelectedOverride = Color.black;
            }
        }
    }
}
