/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

@SuppressWarnings("serial")
public class SettingsData extends JPanel implements Settings
{
    protected JTable          table;
    protected MNodeTableModel model;
    protected JScrollPane     scrollPane;

    public SettingsData ()
    {
        setName ("Data");  // Necessary to fulfill Settings interface.

        model      = new MNodeTableModel ();
        table      = new JTable (model);
        scrollPane = new JScrollPane (table);

        table.setAutoResizeMode (JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        table.setCellSelectionEnabled (true);
        table.setSurrendersFocusOnKeystroke (true);

        table.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                Point p = e.getPoint ();
                int row    = table.rowAtPoint (p);
                int column = table.columnAtPoint (p);
                if (row >= 0  &&  column >= 0) model.toggle (row, column);
            }
        });

        InputMap inputMap = table.getInputMap (WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"), "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"), "delete");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),  "startEditing");

        ActionMap actionMap = table.getActionMap ();
        actionMap.put ("add", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                // TODO: new repo with auto-generated name "local#"
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                // TODO: dialog box to confirm erasure of repo, but only if dir is not empty
            }
        });
        actionMap.put ("startEditing", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                int row    = table.getSelectedRow ();
                int column = table.getSelectedColumn ();
                if (! model.toggle (row, column)) table.editCellAt (row, column, e);
            }
        });

        FontMetrics fm = table.getFontMetrics (table.getFont ());
        int em = fm.charWidth ('M');
        TableColumnModel cols = table.getColumnModel ();

        TableColumn col = cols.getColumn (0);
        int width = fm.stringWidth ("Write") + em;
        col.setPreferredWidth (width);
        col.setMaxWidth (width);

        col = cols.getColumn (1);
        width = fm.stringWidth ("Visible") + em;
        col.setPreferredWidth (width);
        col.setMaxWidth (width);

        col = cols.getColumn (2);
        width = fm.stringWidth ("Color") + em;
        col.setPreferredWidth (width);
        col.setMaxWidth (width);
        col.setCellRenderer (new ColorRenderer ());

        col = cols.getColumn (3);
        col.setPreferredWidth (40 * em);
        col.setCellRenderer (new ColorTextRenderer ());

        ((DefaultTableCellRenderer) table.getTableHeader ().getDefaultRenderer ()).setHorizontalAlignment (JLabel.LEFT);

        Lay.BLtg (this,
            "C", scrollPane
        );
    }

    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("repo.gif");
    }

    @Override
    public Component getPanel ()
    {
        return this;
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return panel;
    }

    public class MNodeTableModel extends AbstractTableModel
    {
        List<MNode> repos = new ArrayList<MNode> ();

        public MNodeTableModel ()
        {
            for (String key : AppData.state.get ("Repos", "order").split (","))
            {
                MNode child = AppData.repos.child (key);
                if (child != null) repos.add (child);
            }
        }

        public int getRowCount ()
        {
            return repos.size ();
        }

        public int getColumnCount ()
        {
            return 4;
        }

        public String getColumnName (int column)
        {
            switch (column)
            {
                case 0: return "Write";
                case 1: return "Visible";
                case 2: return "Color";
                case 3: return "Name";
            }
            return "";
        }

        public Class<?> getColumnClass (int column)
        {
            if (column <= 1) return Boolean.class;  // Coerce table into using default Boolean renderer, which looks reasonably good.
            return super.getColumnClass (column);
        }

        public boolean isCellEditable (int row, int column)
        {
            if (column <= 1) return false;
            return true;
        }

        public Object getValueAt (int row, int column)
        {
            MNode repo = repos.get (row);
            String name = repo.key ();
            String primary = AppData.state.get ("Repos", "primary");
            switch (column)
            {
                case 0:
                    if (primary.isEmpty ()) return row == 0;
                    else                    return name.equals (primary);
                case 1: return repo.get ("visible").equals ("1");
                case 2: return getColor (row);
                case 3: return name;
            }
            return null;
        }

        public Color getColor (int row)
        {
            Color result = Color.black;
            String colorName = repos.get (row).get ("color");
            if (! colorName.isEmpty ())
            {
                try {result = Color.decode (colorName);}
                catch (NumberFormatException e) {e.printStackTrace ();}
            }
            return result;
        }

        public boolean toggle (int row, int column)
        {
            MNode repo = repos.get (row);
            switch (column)
            {
                case 0:
                    AppData.state.set ("Repos", "primary", repo.key ());
                    fireTableDataChanged ();
                    return true;
                case 1:
                    int visible = repo.getInt ("visible");
                    if (visible == 0) visible = 1;
                    else              visible = 0;
                    repo.set ("visible", visible);
                    fireTableCellUpdated (row, 1);
                    return true;
                case 2:
                    // Show a modal color chooser
                    Color initialColor = getColor (row);
                    Color chosenColor = JColorChooser.showDialog (MainFrame.instance, "", initialColor);
                    if (chosenColor != null)
                    {
                        repo.set ("color", "#" + Integer.toHexString (chosenColor.getRGB () & 0xFFFFFF));
                        fireTableRowsUpdated (row, row);
                    }
                    return true;
            }
            return false;
        }
    }

    public class ColorRenderer extends JPanel implements TableCellRenderer
    {
        public Component getTableCellRendererComponent (JTable table, Object text, boolean isSelected, boolean hasFocus, int row, int column)
        {
            setOpaque (true);
            setBackground (model.getColor (row));

            Color marginColor;
            if (isSelected) marginColor = table.getSelectionBackground ();
            else            marginColor = table.getBackground ();
            Border border = BorderFactory.createMatteBorder (2, 2, 2, 2, marginColor);
            setBorder (border);

            return this;
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

            String primary = AppData.state.get ("Repos", "primary");
            if (text.equals (primary))
            {
                setForeground (Color.black);
            }
            else
            {
                Color color = model.getColor (row);
                if (color.equals (Color.black)) color = Color.blue;
                setForeground (color);
            }

            setFont (table.getFont ());

            return this;
        }
    }
}