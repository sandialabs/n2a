/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.ref;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;

import java.awt.BorderLayout;
import java.awt.FontMetrics;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;

public class PanelEntry extends JPanel
{
    // Table
    public    JTable             table;
    public    MNodeTableModel    model;
    protected JScrollPane        scrollPane;
    protected Map<MNode,Integer> focusCache = new HashMap<MNode,Integer> ();

    public PanelEntry ()
    {
        model      = new MNodeTableModel ();
        table      = new JTable (model);
        scrollPane = new JScrollPane (table);

        setLayout (new BorderLayout ());
        add (scrollPane, BorderLayout.CENTER);

        table.setTableHeader (null);
        table.setAutoResizeMode (JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setFillsViewportHeight (true);
        table.setShowHorizontalLines (false);
        table.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        table.setCellSelectionEnabled (true);

        InputMap inputMap = table.getInputMap (WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"),    "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),    "delete");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),     "startEditing");
        inputMap.put (KeyStroke.getKeyStroke ("TAB"),       "cycleFocus");
        inputMap.put (KeyStroke.getKeyStroke ("shift TAB"), "cycleFocus");

        ActionMap actionMap = table.getActionMap ();
        actionMap.put ("add", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                model.insertRow ();
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                model.deleteRow ();
            }
        });
        actionMap.put ("cycleFocus", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if ((e.getModifiers () & ActionEvent.SHIFT_MASK) == 0)
                {
                    transferFocus ();  // This does not work
                }
                else
                {
                    transferFocusBackward ();  // But this does work.
                }
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

    /**
        Informs us that some other code deleted a document from the DB.
        We only respond if it happens to be on display.
    **/
    public void recordDeleted (MNode doc)
    {
        focusCache.remove (doc);
        if (doc == model.record) model.setRecord (null);
    }

    public class MNodeTableModel extends AbstractTableModel
    {
        public MNode        record;
        public List<String> keys = new ArrayList<String> ();
        public boolean      editNewRow;

        public void setRecord (MNode record)
        {
            if (this.record == record) return;
            this.record = record;
            build ();
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
                updateColumnWidth ();
            }

            fireTableDataChanged ();
            refocus ();
        }

        public void updateColumnWidth ()
        {
            if (keys.isEmpty ()) return;

            int width = 0;
            FontMetrics fm = table.getFontMetrics (table.getFont ());
            for (String key : keys)
            {
                width = Math.max (width, fm.stringWidth (key));
            }
            width += fm.charWidth ('M');  // Add one blank space after the keys
            TableColumnModel cols = table.getColumnModel ();
            cols.getColumn (0).setPreferredWidth (width);
            cols.getColumn (1).setPreferredWidth (table.getWidth () - width);
            table.doLayout ();
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
            if (column == 0) return "Field";
            if (column == 1) return "Value";
            return "";
        }

        public boolean isCellEditable (int row, int column)
        {
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

            String key = keys.get (row);
            String name = value.toString ();
            if (column == 0)  // name change
            {
                if (name.equals (key)) return;  // nothing to do
                if (record.child (name) != null) return;  // not allowed
                if (name.equals ("id")) return;  // also not allowed; note that "form" and "title" are protected by previous line

                int existingRow = keys.indexOf (name);
                record.move (key, name);
                keys.set (row, name);
                updateColumnWidth ();
                fireTableRowsUpdated (row, row);
                if (existingRow >= 0)
                {
                    keys.remove (existingRow);
                    fireTableRowsDeleted (existingRow, existingRow);
                }
            }
            else if (column == 1)  // value change
            {
                // Update data
                if (row == 0)  // change id
                {
                    if (AppData.references.child (name) != null) return;  // not allowed, because another entry with that id already exists
                    AppData.references.move (record.key (), name);
                }
                else
                {
                    record.set (key, value);
                }

                // Update display
                if (row == 1)  // changed form, so need to rebuild
                {
                    focusCache.put (record, row);
                    build ();
                }
                else
                {
                    fireTableCellUpdated (row, column);
                    if (row == 2)  // title
                    {
                        PanelReference.instance.panelMRU.repaint ();
                        PanelReference.instance.panelSearch.repaint ();
                    }
                }
            }
        }

        public void insertRow ()
        {
            // Determine unique key name
            String newKey;
            int suffix = 0;
            while (true)
            {
                newKey = "k" + suffix++;
                if (record.child (newKey) == null) break;
            }

            // Add the row
            int row = table.getSelectedRow ();
            if (row < 0) row = keys.size ();
            if (row < 3) row = 3;  // keep id, form and title at the top
            keys.add (row, newKey);
            record.set (newKey, "");
            fireTableRowsInserted (row, row);
            table.changeSelection (row, 1, false, false);
            editNewRow = true;
            table.editCellAt (row, 0);
        }

        public void deleteRow ()
        {
            int row = table.getSelectedRow ();
            if (row < 3) return;  // Protect id, form and title
            String key = keys.get (row);
            keys.remove (row);
            record.clear (key);
            updateColumnWidth ();
            fireTableRowsDeleted (row, row);
        }
    }
}
