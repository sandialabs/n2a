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
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
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
        // TODO: prevent drag-n-drop re-ordering of columns

        InputMap inputMap = table.getInputMap (WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"), "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"), "delete");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),  "toggle");

        ActionMap actionMap = table.getActionMap ();
        actionMap.put ("add", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                // TODO: pop up a dialog to ask name of new repo
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                // TODO: dialog box to confirm erasure of repo, but only if dir is not empty
            }
        });
        actionMap.put ("toggle", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                int row    = table.getSelectedRow ();
                int column = table.getSelectedColumn ();
                MNode repo = AppData.state.child ("Repos", String.valueOf (row));
                if (repo == null) return;
                switch (column)
                {
                    case 0:
                        AppData.state.set ("Repos", repo.get ());
                        for (int i = model.getRowCount () - 1; i >= 0; i--) model.fireTableCellUpdated (i, 0);
                       break;
                    case 1:
                        int visible = repo.getInt ("visible");
                        if (visible == 0) visible = 1;
                        else              visible = 0;
                        repo.set ("visible", visible);
                        model.fireTableCellUpdated (row, 1);
                        break;
                    case 2:
                        // TODO: bring up a color chooser dialog
                        break;
                }
            }
        });

        FontMetrics fm = table.getFontMetrics (table.getFont ());
        int em = fm.charWidth ('M');
        TableColumnModel cols = table.getColumnModel ();
        cols.getColumn (0).setPreferredWidth (fm.stringWidth ("Write") + em);
        cols.getColumn (1).setPreferredWidth (fm.stringWidth ("Visible") + em);
        cols.getColumn (2).setPreferredWidth (40 * em);
        cols.getColumn (0).setCellRenderer (new CheckboxRenderer ());
        cols.getColumn (1).setCellRenderer (new CheckboxRenderer ());
        cols.getColumn (2).setCellRenderer (new ColorTextRenderer ());

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
        MNode repos;

        public MNodeTableModel ()
        {
            repos = AppData.state.childOrCreate ("Repos");
        }

        public int getRowCount ()
        {
            return repos.size ();
        }

        public int getColumnCount ()
        {
            return 3;
        }

        public String getColumnName (int column)
        {
            switch (column)
            {
                case 0: return "Write";
                case 1: return "Visible";
                case 2: return "Name & Color";
            }
            return "";
        }

        public boolean isCellEditable (int row, int column)
        {
            return true;
        }

        public Object getValueAt (int row, int column)
        {
            MNode repo = repos.child (String.valueOf (row));
            if (repo == null) return null;
            String name = repo.get ();
            String primary = repos.get ();
            switch (column)
            {
                case 0:
                    if (primary.isEmpty ()) return row == 0;
                    else                    return name.equals (primary);
                case 1: return repo.get ("visible").equals ("1");
                case 2: return name;
            }
            return null;
        }
    }

    public class CheckboxRenderer extends JCheckBox implements TableCellRenderer
    {
        public Component getTableCellRendererComponent (JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
        {
            setSelected ((Boolean) value);
            setOpaque (true);
            setForeground (table.getForeground ());
            if (isSelected) setBackground (table.getSelectionBackground ());
            else            setBackground (table.getBackground ());
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

            Color foreground = null;
            String colorName = AppData.state.get ("Repos", String.valueOf (row), "color");
            if (! colorName.isEmpty ())
            {
                try {foreground = Color.decode (colorName);}
                catch (NumberFormatException e) {}
            }
            if (foreground == null) foreground = table.getForeground ();
            setForeground (foreground);

            setFont (table.getFont ());

            return this;
        }
    }
}