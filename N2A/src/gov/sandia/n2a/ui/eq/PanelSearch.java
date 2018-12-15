/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.SafeTextTransferHandler;
import gov.sandia.n2a.ui.eq.PanelEquationTree.TransferableNode;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import gov.sandia.n2a.ui.eq.undo.DeleteDoc;

import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    public JList<Holder>            list;
    public DefaultListModel<Holder> model;
    public int                      lastSelection = -1;
    public int                      insertAt;
    public MNodeRenderer            renderer = new MNodeRenderer ();
    public TransferHandler          transferHandler;

    public PanelSearch ()
    {
        list = new JList<Holder> (model = new DefaultListModel<Holder> ());
        list.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        list.setDragEnabled (true);
        list.setCellRenderer (renderer);

        InputMap inputMap = list.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"),     "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),     "delete");
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"), "delete");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),      "select");

        ActionMap actionMap = list.getActionMap ();
        actionMap.put ("add", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                PanelModel.instance.undoManager.add (new AddDoc ());
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                MNode deleteMe = list.getSelectedValue ().doc;
                if (deleteMe == null  ||  ! AppData.models.isWriteable (deleteMe)) return;
                lastSelection = list.getSelectedIndex ();
                PanelModel.instance.undoManager.add (new DeleteDoc ((MDoc) deleteMe));
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
            public void mouseClicked (MouseEvent e)
            {
                if (e.getClickCount () > 1) selectCurrent ();
            }
        });

        list.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                PanelModel.instance.panelEquations.yieldFocus ();
                showSelection ();
            }

            public void focusLost (FocusEvent e)
            {
                hideSelection ();
            }
        });

        transferHandler = new TransferHandler ()
        {
            public boolean canImport (TransferSupport xfer)
            {
                return xfer.isDataFlavorSupported (DataFlavor.stringFlavor)  ||  xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor);
            }

            public boolean importData (TransferSupport xfer)
            {
                if (! list.isFocusOwner ()) hideSelection ();

                Transferable xferable = xfer.getTransferable ();
                if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor))
                {
                    try
                    {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) xferable.getTransferData (DataFlavor.javaFileListFlavor);
                        for (File path : files) PanelEquationTree.importFile (path);
                        return true;
                    }
                    catch (IOException | UnsupportedFlavorException e)
                    {
                    }
                }
                else if (xfer.isDataFlavorSupported (DataFlavor.stringFlavor))
                {
                    Schema schema;
                    MNode data = new MVolatile ();
                    TransferableNode xferNode = null;
                    try
                    {
                        StringReader reader = new StringReader ((String) xferable.getTransferData (DataFlavor.stringFlavor));
                        schema = Schema.readAll (data, reader);
                        if (xferable.isDataFlavorSupported (PanelEquationTree.nodeFlavor)) xferNode = (TransferableNode) xferable.getTransferData (PanelEquationTree.nodeFlavor);
                    }
                    catch (IOException | UnsupportedFlavorException e)
                    {
                        return false;
                    }

                    if (! schema.type.contains ("Part")) return false;
                    PanelModel.instance.undoManager.addEdit (new CompoundEdit ());
                    for (MNode n : data)  // data can contain several parts
                    {
                        AddDoc add = new AddDoc (n.key (), n);
                        if (xferNode != null  &&  xferNode.drag)
                        {
                            add.wasShowing = false;  // on the presumption that the sending side will create an Outsource operation, and thus wants to keep the old model in the equation tree
                            xferNode.newPartName = add.name;
                        }
                        PanelModel.instance.undoManager.add (add);
                        break;  // For now, we only support transferring a single part. To do more, we need to add collections in TransferableNode for both the node paths and the created part names.
                    }
                    if (! xfer.isDrop ()  ||  xfer.getDropAction () != MOVE  ||  xferNode == null) PanelModel.instance.undoManager.endCompoundEdit ();  // By not closing the compound edit on a DnD move, we allow the sending side to include any changes in it when exportDone() is called.

                    return true;
                }

                return false;
            }

            public int getSourceActions (JComponent comp)
            {
                return COPY;
            }

            protected Transferable createTransferable (JComponent comp)
            {
                return list.getSelectedValue ().createTransferable ();
            }

            protected void exportDone (JComponent source, Transferable data, int action)
            {
                if (! list.isFocusOwner ()) hideSelection ();
                PanelModel.instance.undoManager.endCompoundEdit ();  // This is safe, even if there is no compound edit in progress.
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

        textQuery.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                PanelModel.instance.panelEquations.yieldFocus ();
            }

            public void focusLost (FocusEvent e)
            {
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
            public boolean importData (TransferSupport support)
            {
                boolean result = super.importData (support);
                if (! result) result = transferHandler.importData (support);
                return result;
            }
        });


        Lay.BLtg (this,
            "N", Lay.BL ("C", textQuery, "eb=2"),
            "C", Lay.sp (list)
        );

        search ();
    }

    public void search ()
    {
        if (thread != null) thread.stop = true;
        // Don't wait for thread to stop.

        String query = textQuery.getText ();
        thread = new SearchThread (query.trim ());
        thread.start ();
    }

    public void selectCurrent ()
    {
        int index = list.getSelectedIndex ();
        if (index >= 0)
        {
            MNode doc = model.get (index).doc;
            PanelModel.instance.panelMRU.useDoc (doc);
            recordSelected (doc);
        }
    }

    public static void recordSelected (final MNode doc)
    {
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                PanelModel mep = PanelModel.instance;
                mep.panelEquations.loadRootFromDB (doc);
                mep.panelEquations.tree.requestFocusInWindow ();
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
        return model.get (index).doc.key ();
    }

    public String keyAfter (MNode doc)
    {
        int index = model.indexOf (new Holder (doc));
        if (index < 0  ||  index == model.getSize () - 1) return "";  // indexOf(String) will return end-of-list in response to this value.
        return model.get (index + 1).doc.key ();
    }

    public int indexOf (String key)
    {
        int count = model.size ();
        if (key.isEmpty ()) return count;
        for (int i = 0; i < count; i++) if (model.get (i).doc.key ().equals (key)) return i;
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

    /**
        For multirepo, if the key of the doc in a Holder gets claimed by another doc,
        then the Holder should be updated to point to the new doc. This could, for example,
        change what color it gets displayed as.
    **/
    public void updateDoc (MNode doc)
    {
        String key = doc.key ();
        int index = indexOf (key);
        if (index < 0) return;
        Holder h = model.get (index);
        if (h.doc == doc) return;
        h.doc = doc;
        model.setElementAt (h, index);  // Doesn't change the entry in model, just forces repaint of the associated row.
    }

    public void insertNextAt (int at)
    {
        insertAt = at;
    }

    public void insertDoc (MNode doc)
    {
        Holder h = new Holder (doc);
        int index = model.indexOf (h);
        if (index < 0)
        {
            if (insertAt > model.size ()) insertAt = 0;  // The list has changed, perhaps due to filtering, and our position is no longer valid, so simply insert at top.
            model.add (insertAt, h);
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

    /**
        Indirect access to MDoc, because toString() would otherwise load and return the entire document.
    **/
    public static class Holder
    {
        public MNode doc;

        public Holder (MNode doc)
        {
            this.doc = doc;
        }

        public Transferable createTransferable ()
        {
            Schema schema = Schema.latest ();
            schema.type = "Part";
            StringWriter writer = new StringWriter ();
            try
            {
                schema.write (writer);
                writer.write (doc.key () + String.format ("%n"));
                for (MNode c : doc) schema.write (c, writer, " ");
                writer.close ();
                return new StringSelection (writer.toString ());
            }
            catch (IOException e)
            {
            }

            return null;
        }

        public String toString ()
        {
            return doc.key ();
        }

        public boolean equals (Object that)
        {
            // MDocs are unique, so direct object equality is OK here.
            if (that instanceof Holder) return ((Holder) that).doc == doc;
            return false;
        }
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
            List<MNode> results = new LinkedList<MNode> ();
            for (MNode i : AppData.models)
            {
                if (stop) return;
                if (i.key ().toLowerCase ().contains (query)) results.add (i);
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
                        lastSelection = -1;
                        for (MNode record : results)
                        {
                            if (stop) return;
                            model.addElement (new Holder (record));
                        }
                    }
                }
            });
        }
    }
    protected SearchThread thread;

    public static class MNodeRenderer extends JTextField implements ListCellRenderer<Holder>
    {
        protected static DefaultHighlighter.DefaultHighlightPainter painter;

        public MNodeRenderer ()
        {
            painter = new DefaultHighlighter.DefaultHighlightPainter (UIManager.getColor ("List.selectionBackground"));
            setBorder (new EmptyBorder (0, 0, 0, 0));
        }

        public Component getListCellRendererComponent (JList<? extends Holder> list, Holder holder, int index, boolean isSelected, boolean cellHasFocus)
        {
            MNode doc = holder.doc;
            String name = doc.key ();
            if (name.isEmpty ()) name = doc.get ();
            setText (name);

            Color color = Color.black;
            if (! AppData.models.isWriteable (doc))
            {
                MNode repo = AppData.repos.child (doc.parent ().key ());
                String colorName = repo.get ("color");
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
