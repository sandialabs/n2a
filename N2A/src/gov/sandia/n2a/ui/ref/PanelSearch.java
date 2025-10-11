/*
Copyright 2017-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MCombo;
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Import;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.SafeTextTransferHandler;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.ref.undo.AddEntry;
import gov.sandia.n2a.ui.ref.undo.DeleteEntry;
import gov.sandia.n2a.ui.settings.SettingsRepo;

import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

@SuppressWarnings("serial")
public class PanelSearch extends JPanel
{
    public JTextField               textQuery;
    public DefaultListModel<String> model = new DefaultListModel<String> ();
    public JList<String>            list  = new JList<String> (model);
    public int                      lastSelection = -1;
    public int                      insertAt;
    public MNodeRenderer            renderer = new MNodeRenderer ();
    public TransferHandler          transferHandler;

    public static ExportBibTeX exportBibTeX = new ExportBibTeX ();

    public PanelSearch ()
    {
        list.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        list.setDragEnabled (true);
        list.setCellRenderer (renderer);
        list.setRequestFocusEnabled (false);  // Let the mouse listener handle focus on click.

        InputMap inputMap = list.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"),            "add");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl EQUALS"),       "add");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl shift EQUALS"), "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),            "delete");
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"),        "delete");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),             "select");
        inputMap.put (KeyStroke.getKeyStroke ("RIGHT"),             "select");
        inputMap.put (KeyStroke.getKeyStroke ("KP_RIGHT"),          "select");

        ActionMap actionMap = list.getActionMap ();
        actionMap.put ("add", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                MainFrame.undoManager.apply (new AddEntry ());
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                String key = list.getSelectedValue ();
                if (key == null) return;
                if (! ((MCombo) AppData.docs.child ("references")).isWritable (key)) return;
                lastSelection = list.getSelectedIndex ();
                MainFrame.undoManager.apply (new DeleteEntry ((MDoc) AppData.docs.child ("references", key)));
            }
        });
        actionMap.put ("select", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                selectCurrent ();
            }
        });

        list.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent me)
            {
                int clicks = me.getClickCount ();
                if (clicks == 1)
                {
                    int index = list.locationToIndex (me.getPoint ());
                    if (index >= 0)
                    {
                        lastSelection = index;
                        list.setSelectedIndex (index);
                    }
                    list.requestFocusInWindow ();

                    if (me.isControlDown ())  // Bring up context menu for moving between repos.
                    {
                        String key = list.getSelectedValue ();
                        if (key != null)
                        {
                            JPopupMenu menuRepo = SettingsRepo.instance.createTransferMenu ("references", key);
                            menuRepo.show (list, me.getX (), me.getY ());
                        }
                    }
                }
                else
                {
                    selectCurrent ();
                }
            }
        });

        list.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                showSelection ();
            }

            public void focusLost (FocusEvent e)
            {
                if (! e.isTemporary ()) hideSelection ();
            }
        });

        UndoManager um = MainFrame.undoManager;
        transferHandler = new TransferHandler ()
        {
            public boolean canImport (TransferSupport xfer)
            {
                if (xfer.isDataFlavorSupported (TransferableReference.referenceFlavor)) return false;  // only used to export or to re-order MRU
                if (xfer.isDataFlavorSupported (DataFlavor.stringFlavor))               return true;
                if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor))         return true;
                return false;
            }

            public boolean importData (TransferSupport xfer)
            {
                if (! list.isFocusOwner ()) hideSelection ();

                Transferable xferable = xfer.getTransferable ();
                try
                {
                    if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor))
                    {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) xferable.getTransferData (DataFlavor.javaFileListFlavor);
                        Exception error = null;
                        um.addEdit (new CompoundEdit ());  // in case there is more than one file
                        // Ideally this would be on a separate thread, but since we are modifying a compound edit,
                        // we need to stay on EDT.
                        for (File file : files)
                        {
                            try
                            {
                                PanelModel.importFile (file.toPath ());
                            }
                            catch (Exception e)
                            {
                                error = e;
                            }
                        }
                        um.endCompoundEdit ();
                        if (error != null) PanelModel.fileImportExportException ("Import", error);
                        return true;
                    }
                    else if (xfer.isDataFlavorSupported (DataFlavor.stringFlavor))
                    {
                        String dataString = (String) xferable.getTransferData (DataFlavor.stringFlavor);
                        BufferedReader reader = new BufferedReader (new StringReader (dataString));
                        reader.mark (dataString.length () + 1);

                        // Scan data to determine type, then parse the specific type.
                        ImportBibliography bestImporter = null;
                        float              bestP        = 0;
                        for (ExtensionPoint exp : PluginManager.getExtensionsForPoint (Import.class))
                        {
                            if (! (exp instanceof ImportBibliography)) continue;
                            ImportBibliography ib = (ImportBibliography) exp;

                            float P = ib.matches (reader);
                            reader.reset ();
                            if (P > bestP)
                            {
                                bestP        = P;
                                bestImporter = ib;
                            }
                        }

                        // Parse data
                        MNode data = new MVolatile ();
                        if (bestImporter != null) bestImporter.parse (reader, data);
                        um.addEdit (new CompoundEdit ());  // data can contain several entries
                        for (MNode n : data)
                        {
                            String key = MDir.validFilenameFrom (n.key ());
                            um.apply (new AddEntry (key, n));
                        }
                        um.endCompoundEdit ();
                        return true;
                    }
                }
                catch (IOException | UnsupportedFlavorException e) {}

                return false;
            }

            public int getSourceActions (JComponent comp)
            {
                return COPY;  // In particular, no support for cut.
            }

            protected Transferable createTransferable (JComponent comp)
            {
                String key = list.getSelectedValue ();
                if (key == null) return null;
                MNode ref = AppData.docs.child ("references", key);
                MNode references = new MVolatile ();
                references.set (ref, ref.key ());

                // Note that the output is a BibTeX entry, not the usual N2A schema.
                try (StringWriter writer = new StringWriter ())
                {
                    exportBibTeX.process (references, writer);
                    writer.close ();
                    return new TransferableReference (writer.toString (), key);
                }
                catch (IOException e) {}

                return null;
            }

            protected void exportDone (JComponent source, Transferable data, int action)
            {
                if (! list.isFocusOwner ()) hideSelection ();
                // No support for cut or DnD move, so no need to call endCompoundEdit() here.
            }
        };
        list.setTransferHandler (transferHandler);


        textQuery = new JTextField ();

        textQuery.addKeyListener (new KeyAdapter ()
        {
            public void keyReleased (KeyEvent e)
            {
                if (e.getKeyCode () == KeyEvent.VK_ESCAPE) textQuery.setText ("");
            }
        });

        textQuery.getDocument ().addDocumentListener (new DocumentListener ()
        {
            public void insertUpdate (DocumentEvent e)
            {
                search ();
            }

            public void removeUpdate (DocumentEvent e)
            {
                search ();
            }

            public void changedUpdate (DocumentEvent e)
            {
                search ();
            }
        });

        textQuery.setTransferHandler (new SafeTextTransferHandler ()
        {
            public boolean canImport (TransferSupport xfer)
            {
                if (xfer.isDataFlavorSupported (TransferableReference.referenceFlavor)) return false;
                if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor))         return true;
                return super.canImport (xfer);
            }

            public boolean importData (TransferSupport xfer)
            {
                if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor)) return transferHandler.importData (xfer);
                try
                {
                    String data = (String) xfer.getTransferable ().getTransferData (DataFlavor.stringFlavor);
                    // Defend against complex formats, such as full bibliographic entry.
                    if (data.contains ("\n")  ||  data.contains ("\r")) return transferHandler.importData (xfer);
                    return super.importData (xfer);
                }
                catch (IOException | UnsupportedFlavorException e)
                {
                    return false;
                }
            }
        });


        Lay.BLtg (this,
            "N", Lay.BL ("C", textQuery, "eb=2"),
            "C", Lay.sp (list)
        );

        search ();  // This will safely block until the models dir is loaded. If that takes too long for comfort, other arrangements are possible.
    }

    public void search ()
    {
        if (thread != null) thread.stop = true;

        String query = textQuery.getText ();
        thread = new SearchThread (query.trim ());
        thread.start ();
    }

    public void selectCurrent ()
    {
        int index = list.getSelectedIndex ();
        if (index >= 0)
        {
            MNode doc = AppData.docs.child ("references", model.get (index));
            PanelReference.instance.panelMRU.useDoc (doc);
            recordSelected (doc);
        }
    }

    public static void recordSelected (final MNode doc)
    {
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                PanelReference mep = PanelReference.instance;
                mep.panelEntry.model.setRecord (doc);
                mep.panelEntry.table.requestFocusInWindow ();
            }
        });
    }

    public void hideSelection ()
    {
        int index = list.getSelectedIndex ();
        if (index >= 0) lastSelection = index;
        list.clearSelection ();
    }

    public void showSelection ()
    {
        if (list.getSelectedIndex () < 0)
        {
            int last = model.getSize () - 1;
            if      (lastSelection < 0   ) list.setSelectedIndex (0);
            else if (lastSelection > last) list.setSelectedIndex (last);
            else                           list.setSelectedIndex (lastSelection);
        }
    }

    public String currentKey ()
    {
        int index = list.getSelectedIndex ();
        if (index < 0) return "";
        return model.get (index);
    }

    public String keyAfter (String key)
    {
        int index = model.indexOf (key);
        if (index < 0  ||  index == model.getSize () - 1) return "";  // indexOf(String) will return end-of-list in response to this value.
        return model.get (index + 1);
    }

    public int indexOf (String key)
    {
        int count = model.size ();
        if (key.isEmpty ()) return count;
        for (int i = 0; i < count; i++) if (model.get (i).equals (key)) return i;
        return -1;
    }

    public void removeDoc (String key)
    {
        int index = indexOf (key);
        if (index < 0) return;
        model.remove (index);
        if (lastSelection > index) lastSelection--;
        lastSelection = Math.min (model.size () - 1, lastSelection);
    }

    public void updateDoc (String oldKey, String newKey)
    {
        int index = indexOf (oldKey);
        if (index < 0) return;
        model.setElementAt (newKey, index);
    }

    public void insertNextAt (int at)
    {
        insertAt = at;
    }

    public void insertDoc (String key)
    {
        int index = model.indexOf (key);
        if (index < 0)
        {
            if (insertAt > model.size ()) insertAt = 0;  // The list has changed, perhaps due to filtering, and our position is no longer valid, so simply insert at top.
            model.add (insertAt, key);
            lastSelection = insertAt;
        }
        else
        {
            lastSelection = index;
        }
        insertAt = 0;
    }

    public void updateUI ()
    {
        super.updateUI ();
        if (renderer != null) renderer.updateUI ();
    }

    // Retrieve records matching the filter text, and deliver them to the model.
    public class SearchThread extends Thread
    {
        public String query;
        public boolean stop;

        public SearchThread (String query)
        {
            this.query = query.toLowerCase ();
        }

        @Override
        public void run ()
        {
            List<String> results = new LinkedList<String> ();
            for (MNode i : AppData.docs.childOrEmpty ("references"))
            {
                if (stop) return;
                String key = i.key ();
                if (key.toLowerCase ().contains (query)) results.add (key);
            }

            // Update of list should be atomic with respect to other ui events.
            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    synchronized (model)
                    {
                        if (stop) return;
                        model.clear ();
                        for (String key : results) model.addElement (key);
                    }
                }
            });
        }
    }
    protected SearchThread thread;

    public static class MNodeRenderer extends JTextField implements ListCellRenderer<String>
    {
        protected static DefaultHighlighter.DefaultHighlightPainter painter;

        public MNodeRenderer ()
        {
            painter = new DefaultHighlighter.DefaultHighlightPainter (UIManager.getColor ("List.selectionBackground"));
            setBorder (new EmptyBorder (0, 0, 0, 0));
        }

        public Component getListCellRendererComponent (JList<? extends String> list, String key, int index, boolean isSelected, boolean cellHasFocus)
        {
            MCombo references = (MCombo) AppData.docs.child ("references");
            MNode doc = references.child (key);
            String name = doc.get ("title");
            if (name.isEmpty ()) name = key;
            setText (name);

            Color color = Color.black;
            if (! references.isWritable (doc))
            {
                String colorName = "";
                MNode repo = AppData.repos.child (doc.parent ().key ());  // This can return null if multirepo structure changes and this panel is repainted before the change notification arrives.
                if (repo != null) colorName = repo.get ("color");
                if (! colorName.isEmpty ())
                {
                    try {color = Color.decode (colorName);}
                    catch (NumberFormatException e) {}
                }
                if (color.equals (Color.black)) color = Color.blue;
            }
            setForeground (color);

            if (isSelected)
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
        }
    }
}
