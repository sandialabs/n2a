/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.ref.undo.AddTag;
import gov.sandia.n2a.ui.ref.undo.AddEntry;
import gov.sandia.n2a.ui.ref.undo.ChangeTag;
import gov.sandia.n2a.ui.ref.undo.ChangeEntry;
import gov.sandia.n2a.ui.ref.undo.DeleteTag;
import gov.sandia.n2a.ui.ref.undo.RenameTag;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

@SuppressWarnings("serial")
public class PanelEntry extends JPanel
{
    // Table
    public    JTable             table;
    public    MNodeTableModel    model;
    protected JScrollPane        scrollPane;
    protected Map<MNode,Integer> focusCache = new HashMap<MNode,Integer> ();

    protected JButton buttonAddEntry;
    protected JButton buttonAddTag;
    protected JButton buttonDeleteTag;

    public static final DataFlavor tagFlavor = new DataFlavor (TransferableTag.class, null);

    @SuppressWarnings("deprecation")
    public class TransferableTag implements Transferable, ClipboardOwner
    {
        public String  data;
        public boolean drag;

        public TransferableTag (String data, boolean drag)
        {
            this.data = data;
            this.drag = drag;
        }

        @Override
        public void lostOwnership (Clipboard clipboard, Transferable contents)
        {
        }

        @Override
        public DataFlavor[] getTransferDataFlavors ()
        {
            DataFlavor[] result = new DataFlavor[3];
            result[0] = DataFlavor.stringFlavor;
            result[1] = DataFlavor.plainTextFlavor;
            result[2] = tagFlavor;
            return result;
        }

        @Override
        public boolean isDataFlavorSupported (DataFlavor flavor)
        {
            if (flavor.equals (DataFlavor.stringFlavor   )) return true;
            if (flavor.equals (DataFlavor.plainTextFlavor)) return true;
            if (flavor.equals (tagFlavor                 )) return true;
            return false;
        }

        @Override
        public Object getTransferData (DataFlavor flavor) throws UnsupportedFlavorException, IOException
        {
            if (flavor.equals (DataFlavor.stringFlavor   )) return data;
            if (flavor.equals (DataFlavor.plainTextFlavor)) return new StringReader (data);
            if (flavor.equals (tagFlavor                 )) return this;
            throw new UnsupportedFlavorException (flavor);
        }
    }

    public PanelEntry ()
    {
        model = new MNodeTableModel ();
        table = new JTable (model)
        {
            public void updateUI ()
            {
                super.updateUI ();

                if (table == null) return;  // This happens once during initial construction, before table is set by assignment above.
                model.updateColumnWidth ();
                model.updateRowHeights ();
            }
        };
        scrollPane = new JScrollPane (table);

        table.setTableHeader (null);
        table.setAutoResizeMode (JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setFillsViewportHeight (true);
        table.setShowHorizontalLines (false);
        table.setShowVerticalLines (false);
        table.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        table.setCellSelectionEnabled (true);
        table.setSurrendersFocusOnKeystroke (true);

        InputMap inputMap = table.getInputMap (WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"),            "add");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl shift EQUALS"), "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),            "delete");
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"),        "delete");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),             "startEditing");
        inputMap.put (KeyStroke.getKeyStroke ("TAB"),               "cycleFocus");
        inputMap.put (KeyStroke.getKeyStroke ("shift TAB"),         "cycleFocus");
        inputMap.put (KeyStroke.getKeyStroke ("control Z"),         "Undo");  // For Windows and Linux
        inputMap.put (KeyStroke.getKeyStroke ("meta Z"),            "Undo");  // For Mac
        inputMap.put (KeyStroke.getKeyStroke ("control Y"),         "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("meta Y"),            "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift control Z"),   "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift meta Z"),      "Redo");

        ActionMap actionMap = table.getActionMap ();
        actionMap.put ("add", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                addTag ();
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                deleteTag ();
            }
        });
        actionMap.put ("cycleFocus", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if ((e.getModifiers () & ActionEvent.SHIFT_MASK) == 0)
                {
                    PanelReference.instance.panelSearch.textQuery.requestFocusInWindow ();
                }
                else
                {
                    PanelReference.instance.panelSearch.list.requestFocusInWindow ();
                }
            }
        });
        actionMap.put ("Undo", new AbstractAction ("Undo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {MainFrame.instance.undoManager.undo ();}
                catch (CannotUndoException e) {}
                catch (CannotRedoException e) {}
            }
        });
        actionMap.put ("Redo", new AbstractAction ("Redo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {MainFrame.instance.undoManager.redo();}
                catch (CannotUndoException e) {}
                catch (CannotRedoException e) {}
            }
        });

        table.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                refocus ();
            }

            public void focusLost (FocusEvent e)
            {
                if (! e.isTemporary ()  &&  ! table.isEditing ())  // The shift to the editing component appears as a loss of focus.
                {
                    if (model.record != null) focusCache.put (model.record, table.getSelectedRow ());
                    table.clearSelection ();
                }
            }
        });

        table.setTransferHandler (new TransferHandler ()
        {
            public boolean canImport (TransferSupport xfer)
            {
                return xfer.isDataFlavorSupported (DataFlavor.stringFlavor);
            }

            public boolean importData (TransferSupport xfer)
            {
                String data;
                try
                {
                    data = (String) xfer.getTransferable ().getTransferData (DataFlavor.stringFlavor);
                }
                catch (IOException | UnsupportedFlavorException e)
                {
                    return false;
                }

                if (data.isEmpty ()) return false;
                if (data.contains ("\n")  ||  data.contains ("\r"))
                {
                    // Defend against complex formats, such as a full BibTeX entry.
                    // If it is very likely to be a BibTeX entry, then forward it to the entry list for import.
                    if (data.startsWith ("@")) return PanelReference.instance.panelSearch.list.getTransferHandler ().importData (xfer);
                    return false;
                }
                if (model.locked) return false;  // Nothing to import into, and we don't really want to mess with instant entry creation. (If we do, then need compound edit.)

                int row;
                int col;
                if (xfer.isDrop ())
                {
                    JTable.DropLocation dl = (JTable.DropLocation) xfer.getDropLocation ();
                    row = dl.getRow ();
                    col = dl.getColumn ();
                }
                else
                {
                    row = table.getSelectedRow ();
                    col = table.getSelectedColumn ();
                }

                String[] parts = data.split ("=", 2);
                if (parts.length == 1)  // simple value
                {
                    model.setValueAt (parts[0], row, col);
                    // TODO: may need a repaint here.
                }
                else  // tag=value
                {
                    String key   = parts[0];
                    String value = parts[1];
                    if (key.isEmpty ()) return false;
                    int index = model.keys.indexOf (key);
                    if (index >= 0)
                    {
                        if (key.equals ("id")) return false;
                        model.setValueAt (value, index, 1);
                    }
                    else
                    {
                        MainFrame.instance.undoManager.apply (new AddTag (model.record, row, key, value));
                    }
                }
                return true;
            }

            public int getSourceActions (JComponent comp)
            {
                return COPY_OR_MOVE;
            }

            boolean dragInitiated;  // This is a horrible hack, but the simplest way to override the default MOVE action chosen internally by Swing.
            public void exportAsDrag (JComponent comp, InputEvent e, int action)
            {
                dragInitiated = true;
                super.exportAsDrag (comp, e, action);
            }

            protected Transferable createTransferable (JComponent comp)
            {
                boolean drag = dragInitiated;
                dragInitiated = false;

                int row = table.getSelectedRow ();
                if (row < 0) return null;
                String key = model.keys.get (row);
                String value = model.record.get (key);
                return new TransferableTag (key + "=" + value, drag);
            }

            protected void exportDone (JComponent source, Transferable data, int action)
            {
                if (action == MOVE  &&  ! ((TransferableTag) data).drag  &&  ! model.locked)
                {
                    try
                    {
                        String key = ((String) data.getTransferData (DataFlavor.stringFlavor)).split ("=", 2)[0];
                        if (! key.equals ("id")  &&  ! key.equals ("form")  &&  ! key.equals ("title"))
                        {
                            MainFrame.instance.undoManager.apply (new DeleteTag (model.record, key));
                        }
                    }
                    catch (UnsupportedFlavorException | IOException e)
                    {
                    }
                }
            }
        });

        TableColumnModel columns = table.getColumnModel ();
        columns.getColumn (0).setCellRenderer (new ColorTextRenderer ());
        columns.getColumn (1).setCellRenderer (new MultilineTextRenderer ());
        columns.getColumn (0).setCellEditor (new MultilineEditor ());  // Won't actually edit tags in multi-line mode, but this brings in focus control for free.
        columns.getColumn (1).setCellEditor (new MultilineEditor ());

        model.addTableModelListener (new TableModelListener ()
        {
            public void tableChanged (TableModelEvent e)
            {
                // This is ugly but necessary. JTable drops its row model and reverts to default row spacing
                // any time there is a change in the row structure (add, delete, full reload). That occurs
                // in the listener methods. Thus, we need to ensure that we run after that method.
                // To do this in the cleanest way, it would be necessary to override the JTable methods that
                // drop the row height structure, and have them instead call back to a recalculate-row-height method.
                SwingUtilities.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        model.updateRowHeights ();
                    }
                });
            }
        });

        buttonAddEntry = new JButton (ImageUtil.getImage ("document.png"));
        buttonAddEntry.setMargin (new Insets (2, 2, 2, 2));
        buttonAddEntry.setFocusable (false);
        buttonAddEntry.setToolTipText ("New Reference");
        buttonAddEntry.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                MainFrame.instance.undoManager.apply (new AddEntry ());
            }
        });

        buttonAddTag = new JButton (ImageUtil.getImage ("add.gif"));
        buttonAddTag.setMargin (new Insets (2, 2, 2, 2));
        buttonAddTag.setFocusable (false);
        buttonAddTag.setToolTipText ("New Tag");
        buttonAddTag.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                addTag ();
            }
        });

        buttonDeleteTag = new JButton (ImageUtil.getImage ("remove.gif"));
        buttonDeleteTag.setMargin (new Insets (2, 2, 2, 2));
        buttonDeleteTag.setFocusable (false);
        buttonDeleteTag.setToolTipText ("Delete Tag");
        buttonDeleteTag.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                deleteTag ();
            }
        });

        Lay.BLtg (this,
            "N", Lay.WL ("L",
                buttonAddEntry,
                Box.createHorizontalStrut (15),
                buttonAddTag,
                buttonDeleteTag,
                "hgap=5,vgap=1"
            ),
            "C", scrollPane
        );
    }

    public void refocus ()
    {
        if (table.getSelectedRow () < 0)
        {
            Integer cachedRow = focusCache.get (model.record);
            if (cachedRow == null) table.changeSelection (0,         1, false, false);
            else                   table.changeSelection (cachedRow, 1, false, false);
        }
    }

    public void addTag ()
    {
        if (model.record == null)
        {
            MainFrame.instance.undoManager.apply (new AddEntry ());
        }
        else if (! model.locked)
        {
            int row = table.getSelectedRow ();
            if (row < 0) row = model.keys.size ();
            if (row < 3) row = 3;  // keep id, form and title at the top
            MainFrame.instance.undoManager.apply (new AddTag (model.record, row));
        }
    }

    public void deleteTag ()
    {
        if (model.locked) return;
        int row = table.getSelectedRow ();
        if (row < 3) return;  // Protect id, form and title
        String tag = model.keys.get (row);
        MainFrame.instance.undoManager.apply (new DeleteTag (model.record, tag));
    }

    /**
        Informs us that some other code deleted a document from the DB.
        We only respond if it happens to be on display.
    **/
    public void recordDeleted (MNode doc)
    {
        focusCache.remove (doc);
        if (doc == model.record) model.setRecord (null);
    }

    public void checkVisible ()
    {
        if (AppData.references.isVisible (model.record)) model.updateLock ();
        else                                             recordDeleted (model.record);
    }

    public static class Form
    {
        public List<String> required = new ArrayList<String> ();
        public List<String> optional = new ArrayList<String> ();

        public Form (String parms)
        {
            String[] parts = parms.split ("=", 2);
            forms.put (parts[0].trim (), this);

            parts = parts[1].split (",");
            for (int i = 0; i < parts.length; i++)
            {
                String s = parts[i].trim ();
                if (s.isEmpty ()) continue;
                if (s.startsWith ("?")) optional.add (s.substring (1));
                else                    required.add (s);
            }
        }
    }
    static Map<String,Form> forms = new TreeMap<String,Form> ();
    static
    {
        String[] formData =
        {
            "article      =title, author, year,?month,?note,?key,                              volume,?number,?pages,journal",
            "book         =title, author, year,?month,?note,?key,              ?address,      ?volume,?number,        publisher, editor,?series,?edition",
            "booklet      =title,?author,?year,?month,?note,?key,              ?address,                                                                 ?howpublished",
            "inbook       =title, author, year,?month,?note,?key,              ?address,?type,?volume,?number, pages, publisher, editor,?series,?edition,               chapter",
            "incollection =title, author, year,?month,?note,?key,              ?address,?type,?volume,?number,?pages, publisher,?editor,?series,?edition,              ?chapter,booktitle",
            "inproceedings=title, author, year,?month,?note,?key,?organization,?address,      ?volume,?number,?pages,?publisher,?editor,?series,                                booktitle",
            "manual       =title,?author,?year,?month,?note,?key,?organization,?address,                                                        ?edition",
            "mastersthesis=title, author, year,?month,?note,?key, school,      ?address,?type",
            "misc         =title,?author,?year,?month,?note,?key,                                                                                        ?howpublished",
            "phdthesis    =title, author, year,?month,?note,?key, school,      ?address,?type",
            "proceedings  =title,         year,?month,?note,?key,?organization,?address,      ?volume,?number,       ?publisher,?editor,?series",
            "techreport   =title, author, year,?month,?note,?key, institution, ?address,?type,        ?number",
            "unpublished  =title, author,?year,?month, note,?key"
        };
        for (String s : formData) new Form (s);
    }

    public class MNodeTableModel extends AbstractTableModel
    {
        public MNode        record;
        public boolean      locked;
        public List<String> keys = new ArrayList<String> ();
        public boolean      editNewRow;
        public Form         form;

        public void setRecord (MNode record)
        {
            if (this.record == record) return;
            this.record = record;
            updateLock ();
            build ();
        }

        public void updateLock ()
        {
            if (record == null) locked = true;
            else                locked = ! AppData.references.isWriteable (record);
        }

        public void build ()
        {
            keys.clear ();
            if (record != null)
            {
                keys.add ("id");
                keys.add ("form");
                keys.add ("title");
                for (MNode n : record)
                {
                    String key = n.key ();
                    if (key.equals ("form")  ||  key.equals ("title")) continue;  // "id" never gets saved as a child
                    keys.add (n.key ());
                }

                // Inject form tags
                String formName = record.get ("form");
                form = forms.get (formName);
                if (form != null)
                {
                    for (String s : form.required) if (! keys.contains (s)) keys.add (s);
                    for (String s : form.optional) if (! keys.contains (s)) keys.add (s);
                }

                updateColumnWidth ();
            }

            fireTableDataChanged ();
            refocus ();
        }

        public void updateColumnWidth ()
        {
            if (keys.isEmpty ()) return;

            int width = 0;
            FontMetrics fm = table.getFontMetrics (table.getFont ().deriveFont (Font.BOLD));
            int em = fm.charWidth ('M');
            for (String key : keys)
            {
                width = Math.max (width, fm.stringWidth (key));
            }
            width += em;  // Add one blank space after the keys
            int width1 = table.getWidth () - width;
            TableColumnModel cols = table.getColumnModel ();
            cols.getColumn (0).setPreferredWidth (width);
            cols.getColumn (1).setPreferredWidth (width1);

            table.doLayout ();
        }

        public void updateRowHeights ()
        {
            TableColumnModel cols = table.getColumnModel ();
            TableColumn col1 = cols.getColumn (1);
            TableCellRenderer renderer = col1.getCellRenderer ();
            int width = col1.getWidth ();
            int margin = table.getRowMargin ();
            int defaultHeight = table.getFontMetrics (table.getFont ()).getHeight () + margin;
            for (int row = 0; row < keys.size (); row++)
            {
                int height = defaultHeight;
                String key = keys.get (row);
                String value = record.get (key);
                if (! value.isEmpty ())
                {
                    JTextArea area = (JTextArea) renderer.getTableCellRendererComponent (table, value, false, false, row, 1);
                    area.setSize (width, 1);
                    height = area.getPreferredSize ().height + margin;
                }
                if (height != table.getRowHeight (row)) table.setRowHeight (row, height);
            }
        }

        public int getRowCount ()
        {
            return keys.size ();
        }

        public int getColumnCount ()
        {
            return 2;
        }

        public String getColumnName (int column)
        {
            if (column == 0) return "Tag";
            if (column == 1) return "Value";
            return "";
        }

        public boolean isCellEditable (int row, int column)
        {
            if (locked) return false;
            if (column == 0  &&  row < 3) return false;  // protect id, form and title
            return true;
        }

        public Object getValueAt (int row, int column)
        {
            if (editNewRow)
            {
                editNewRow = false;
                return "";
            }
            if (row >= keys.size ()) return "";
            String key = keys.get (row);
            if (column == 0) return key;
            if (column == 1)
            {
                if (row == 0) return record.key ();
                return record.get (key);
            }
            return "";
        }

        public void setValueAt (Object value, int row, int column)
        {
            if (row >= keys.size ()  ||  column >= 2) return;  // out of bounds
            if (column == 0  &&  row < 3) return;  // protect id, form and title

            String tag = keys.get (row);
            String newValue = value.toString ();
            if (column == 0)  // name change
            {
                if (newValue.equals (tag)) return;  // nothing to do
                if (newValue.isEmpty ())  // tag is deleted. Most likely it was a new tag the user changed their mind about, but could also be an old tag.
                {
                    MainFrame.instance.undoManager.apply (new DeleteTag (record, tag));
                    return;
                }
                if (record.child (newValue) != null) return;  // not allowed, because another tag with that name already exists
                if (newValue.equals ("id")) return;  // also not allowed; note that "form" and "title" are protected by previous line

                MainFrame.instance.undoManager.apply (new RenameTag (record, keys.indexOf (newValue), tag, newValue));
            }
            else if (column == 1)  // value change
            {
                String oldValue = record.get (tag);
                if (oldValue.equals (newValue)) return;  // nothing to do
                if (row == 0)  // change id
                {
                    if (newValue.isEmpty ()) return;  // not allowed
                    newValue = MDir.validFilenameFrom (newValue);
                    if (AppData.references.child (newValue) != null) return;  // not allowed, because another entry with that id already exists
                    MainFrame.instance.undoManager.apply (new ChangeEntry (record.key (), newValue));
                }
                else
                {
                    MainFrame.instance.undoManager.apply (new ChangeTag (record, tag, newValue));
                }
            }
        }

        public void create (String docKey, int row, String name, String value, boolean nameIsGenerated)
        {
            MNode doc = AppData.references.child (docKey);
            setRecord (doc);

            keys.add (row, name);
            record.set (value, name);
            fireTableRowsInserted (row, row);
            if (nameIsGenerated)
            {
                table.changeSelection (row, 0, false, false);
                editNewRow = true;
                table.editCellAt (row, 0);
            }
            else
            {
                table.changeSelection (row, 1, false, false);
            }
        }

        public void destroy (String docKey, String name)
        {
            MNode doc = AppData.references.child (docKey);
            setRecord (doc);

            int row = keys.indexOf (name);
            keys.remove (row);
            record.clear (name);
            updateColumnWidth ();
            fireTableRowsDeleted (row, row);
            row = Math.min (row, table.getRowCount () - 1);
            table.changeSelection (row, 1, false, false);
        }

        public void rename (String docKey, int exposedRow, String before, String after)
        {
            MNode doc = AppData.references.child (docKey);
            setRecord (doc);
            int rowBefore = keys.indexOf (before);
            int rowAfter  = keys.indexOf (after);

            record.move (before, after);
            if (rowAfter >= 0)  // This only happens when we're about to overwrite a standard tag that has no assigned value.
            {
                keys.remove (rowAfter);
                fireTableRowsDeleted (rowAfter, rowAfter);
            }
            else  // We might be about to expose a standard tag that was previously overwritten.
            {
                if (form != null  &&  (form.required.contains (before)  ||  form.optional.contains (before)))  // It is a standard tag
                {
                    // Assume that exposedRow was saved when the tag was overwritten.
                    keys.add (exposedRow, before);
                    fireTableRowsInserted (exposedRow, exposedRow);
                }
            }
            keys.set (rowBefore, after);
            updateColumnWidth ();
            fireTableRowsUpdated (rowBefore, rowBefore);
        }

        public void changeValue (String docKey, String name, String value)
        {
            MNode doc = AppData.references.child (docKey);
            setRecord (doc);

            // Update data
            if (value.isEmpty ()  &&  form != null  &&  (form.required.contains (name)  ||  form.optional.contains (name)))
            {
                record.clear (name);
            }
            else
            {
                record.set (value, name);
            }

            // Update display
            int row = keys.indexOf (name);
            if (row == 1)  // changed form, so need to rebuild
            {
                focusCache.put (record, row);
                build ();
            }
            else
            {
                fireTableCellUpdated (row, 1);
                if (row == 2)  // title
                {
                    PanelReference.instance.panelMRU.repaint ();
                    PanelReference.instance.panelSearch.repaint ();
                }
            }
        }
    }

    public class ColorTextRenderer extends JLabel implements TableCellRenderer
    {
        public Component getTableCellRendererComponent (JTable table, Object text, boolean isSelected, boolean hasFocus, int row, int column)
        {
            String value = text.toString ();
            setText (value);

            setOpaque (true);
            if (isSelected) setBackground (table.getSelectionBackground ());
            else            setBackground (table.getBackground ());

            Color foreground = table.getForeground ();
            if (! value.equals ("id")  &&  model.record.get (value).isEmpty ()  &&  model.form != null)
            {
                if      (model.form.required.contains (text)) setForeground (Color.red);
                else if (model.form.optional.contains (text)) setForeground (Color.blue);
                else                                          setForeground (foreground);
            }
            else
            {
                setForeground (foreground);
            }

            int style = Font.BOLD;
            if (value.equals ("id")) style |= Font.ITALIC;
            setFont (table.getFont ().deriveFont (style));

            return this;
        }
    }

    public class MultilineTextRenderer extends JTextArea implements TableCellRenderer
    {
        public Component getTableCellRendererComponent (JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
        {
            setText (value.toString ());
            setLineWrap (true);
            setWrapStyleWord (true);
            setTabSize (4);

            if (isSelected) setBackground (table.getSelectionBackground ());
            else            setBackground (table.getBackground ());

            setFont (table.getFont ());

            return this;
        }
    }

    public class MultilineEditor extends AbstractCellEditor implements TableCellEditor
    {
        public JTextArea         textArea    = new JTextArea ();
        public JTextField        textField   = new JTextField ();
        public UndoManager       undoManager = new UndoManager ();
        public JScrollPane       scrollPane  = new JScrollPane ();
        public JComboBox<String> comboBox    = new JComboBox<String> ();
        public boolean           comboBoxSized;
        public JComponent        component;  // The result of last call to getTableCellEditorComponent()

        public MultilineEditor ()
        {
            // Prepare the combo box
            comboBox.setUI (new BasicComboBoxUI ());  // Avoid borders on edit box, because it's too crowded in table. This works, but is ugly. Setting empty border on combo box does not work.
            comboBox.addKeyListener (new KeyAdapter ()
            {
                public void keyPressed (KeyEvent e)
                {
                    if (e.getKeyCode () == KeyEvent.VK_ENTER  &&  ! e.isControlDown ()) stopCellEditing ();
                }
            });
            comboBox.addFocusListener (new FocusListener ()
            {
                public void focusGained (FocusEvent e)
                {
                }

                public void focusLost (FocusEvent e)
                {
                    stopCellEditing ();
                }
            });
            for (String s : forms.keySet ()) comboBox.addItem (s);


            // Prepare the text area

            textArea.setLineWrap (true);
            textArea.setWrapStyleWord (true);
            textArea.setTabSize (4);
            textArea.getDocument ().addUndoableEditListener (undoManager);

            InputMap inputMap = textArea.getInputMap ();
            inputMap.put (KeyStroke.getKeyStroke ("ENTER"),           "none");
            inputMap.put (KeyStroke.getKeyStroke ("control ENTER"),   "insert-break");
            inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo"); 
            inputMap.put (KeyStroke.getKeyStroke ("meta Z"),          "Undo");
            inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
            inputMap.put (KeyStroke.getKeyStroke ("meta Y"),          "Redo");
            inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");
            inputMap.put (KeyStroke.getKeyStroke ("shift meta Z"),    "Redo");

            ActionMap actionMap = textArea.getActionMap ();
            actionMap.put ("Undo", new AbstractAction ("Undo")
            {
                public void actionPerformed (ActionEvent evt)
                {
                    try {undoManager.undo ();}
                    catch (CannotUndoException e) {}
                }
            });
            actionMap.put ("Redo", new AbstractAction ("Redo")
            {
                public void actionPerformed (ActionEvent evt)
                {
                    try {undoManager.redo();}
                    catch (CannotRedoException e) {}
                }
            });

            textArea.addKeyListener (new KeyAdapter ()
            {
                public void keyPressed (KeyEvent e)
                {
                    if (e.getKeyCode () == KeyEvent.VK_ENTER  &&  ! e.isControlDown ()) stopCellEditing ();
                }
            });

            textArea.addFocusListener (new FocusListener ()
            {
                public void focusGained (FocusEvent e)
                {
                }

                public void focusLost (FocusEvent e)
                {
                    stopCellEditing ();
                }
            });

            scrollPane.addFocusListener (new FocusListener ()
            {
                public void focusGained (FocusEvent e)
                {
                    textArea.requestFocusInWindow ();
                }

                public void focusLost (FocusEvent e)
                {
                }
            });


            // Prepare the text field

            textField.getDocument ().addUndoableEditListener (undoManager);

            inputMap = textField.getInputMap ();
            inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo");
            inputMap.put (KeyStroke.getKeyStroke ("meta Z"),          "Undo");
            inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
            inputMap.put (KeyStroke.getKeyStroke ("meta Y"),          "Redo");
            inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");
            inputMap.put (KeyStroke.getKeyStroke ("shift meta Z"),    "Redo");

            actionMap = textField.getActionMap ();
            actionMap.put ("Undo", new AbstractAction ("Undo")
            {
                public void actionPerformed (ActionEvent evt)
                {
                    try {undoManager.undo ();}
                    catch (CannotUndoException e) {}
                }
            });
            actionMap.put ("Redo", new AbstractAction ("Redo")
            {
                public void actionPerformed (ActionEvent evt)
                {
                    try {undoManager.redo();}
                    catch (CannotRedoException e) {}
                }
            });

            textField.addFocusListener (new FocusListener ()
            {
                public void focusGained (FocusEvent e)
                {
                }

                public void focusLost (FocusEvent e)
                {
                    stopCellEditing ();
                }
            });
        }

        public Object getCellEditorValue ()
        {
            if (component == comboBox) return comboBox.getSelectedItem ();
            if (component == textField) return textField.getText ();
            return textArea.getText ();
        }

        public Component getTableCellEditorComponent (JTable table, Object value, boolean isSelected, int row, int column)
        {
            undoManager.discardAllEdits ();

            Font font = table.getFont ();
            Color foreground = table.getForeground ();
            Color background = table.getBackground ();

            if (column == 0)  // tag names
            {
                textField.setText       (value.toString ());
                textField.setForeground (foreground);
                textField.setBackground (background);
                textField.setFont       (font);
                component = textField;
            }
            else if (row == 1)  // form
            {
                comboBox.setSelectedItem (value);
                comboBox.setForeground   (foreground);
                comboBox.setBackground   (background);
                comboBox.setFont         (font);
                component = comboBox;
            }
            else
            {
                textArea.setText       (value.toString ());
                textArea.setForeground (foreground);
                textArea.setBackground (background);
                textArea.setFont       (font);

                FontMetrics fm = table.getFontMetrics (font);
                if (table.getRowHeight (row) > fm.getHeight () + 1)  // use scroll pane
                {
                    component = scrollPane;
                    scrollPane.setViewportView (textArea);
                }
                else  // use text area directly
                {
                    component = textArea;
                }
            }

            return component;
        }
    }
}
