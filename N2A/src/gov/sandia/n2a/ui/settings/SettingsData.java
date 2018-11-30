/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.ref.PanelEntry;
import gov.sandia.n2a.ui.ref.PanelReference;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
    protected JTable            table;
    protected MNodeTableModel   model;
    protected JScrollPane       scrollPane;
    protected boolean           needRebuild;  // need to re-collate AppData.models and AppData.references
    protected Map<String,MNode> existingModels     = AppData.models    .getContainerMap ();
    protected Map<String,MNode> existingReferences = AppData.references.getContainerMap ();
    protected Path              reposDir           = Paths.get (AppData.properties.get ("resourceDir")).resolve ("repos");

    public SettingsData ()
    {
        setName ("Data");  // Necessary to fulfill Settings interface.

        model      = new MNodeTableModel ();
        table      = new JTable (model);
        scrollPane = new JScrollPane (table);

        table.setAutoResizeMode (JTable.AUTO_RESIZE_OFF);
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
                int row = table.getSelectedRow ();
                if (row < 0) row = 0;

                String name = "local";
                int suffix = 2;
                while (AppData.repos.child (name) != null) name = "local" + suffix++;

                AppData.repos.set (name, "visible", 1);
                Path baseDir = reposDir.resolve (name);
                existingModels    .put (name, new MDir (baseDir.resolve ("models")));
                existingReferences.put (name, new MDir (baseDir.resolve ("references")));
                needRebuild = true;

                model.repos.add (row, AppData.repos.child (name));
                model.updateOrder ();
                model.fireTableRowsInserted (row, row);
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                int row = table.getSelectedRow ();
                if (row < 0) return;
                MNode repo = model.repos.get (row);
                String name = repo.key ();

                // Show a dialog if any data is about to be destroyed
                MDir models     = (MDir) existingModels    .get (name);
                MDir references = (MDir) existingReferences.get (name);
                if (models.size () > 0  ||  references.size () > 0)
                {
                    int response = JOptionPane.showConfirmDialog
                    (
                        MainFrame.instance,
                        "<html><body><p style='width:300px'>Permanently erase all models and references in repository \"" + name + "\"?</p></body></html>",
                        "Delete Repository",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE
                    );
                    if (response != JOptionPane.OK_OPTION) return;
                }

                // If a record from the repo is currently on display, inform UI that it is going away.
                PanelEquationTree mep = PanelModel.instance.panelEquations;
                MNode doc = mep.record;
                if (doc != null  &&  doc.parent () == models) mep.recordDeleted (doc);
                PanelModel.instance.panelMRU   .removeDoc (doc);
                PanelModel.instance.panelSearch.removeDoc (doc);

                PanelEntry pe = PanelReference.instance.panelEntry;
                doc = pe.model.record;
                if (doc != null  &&  doc.parent () == references) pe.recordDeleted (doc);
                PanelReference.instance.panelMRU   .removeDoc (doc);
                PanelReference.instance.panelSearch.removeDoc (doc);

                AppData.repos.clear (name);
                existingModels    .remove (name);
                existingReferences.remove (name);
                needRebuild = true;

                model.repos.remove (row);
                model.updateOrder ();
                int column = table.getSelectedColumn ();
                model.fireTableRowsDeleted (row, row);
                if (row >= model.repos.size ()) row = model.repos.size () - 1;
                if (row >= 0) table.changeSelection (row, column, false, false);
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

        table.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
            }

            public void focusLost (FocusEvent e)
            {
                if (e.isTemporary ()  ||  table.isEditing ()) return;  // The shift to the editing component appears as a loss of focus.
                if (! needRebuild) return;

                // Rebuild the combo directories.
                // This is lightweight enough that it can be done on the main UI thread.
                List<MNode> modelContainers     = new ArrayList<MNode> ();
                List<MNode> referenceContainers = new ArrayList<MNode> ();
                String primary = AppData.state.get ("Repos", "primary");
                for (MNode repo : model.repos)
                {
                    String repoName = repo.key ();
                    boolean isPrimary = repoName.equals (primary);
                    if (repo.getInt ("visible") == 0  &&  ! isPrimary) continue;

                    MNode models     = existingModels    .get (repoName);
                    MNode references = existingReferences.get (repoName);
                    Path repoDir = reposDir.resolve (repoName);
                    if (models == null)
                    {
                        models = new MDir (repoName, repoDir.resolve ("models"));
                        existingModels.put (repoName, models);
                    }
                    if (references == null)
                    {
                        references = new MDir (repoName, repoDir.resolve ("references"));
                        existingReferences.put (repoName, references);
                    }

                    if (isPrimary)
                    {
                        modelContainers    .add (0, models);
                        referenceContainers.add (0, references);
                    }
                    else
                    {
                        modelContainers    .add (models);
                        referenceContainers.add (references);
                    }
                }
                AppData.models    .init (modelContainers);
                AppData.references.init (referenceContainers);
                needRebuild = false;

                // Trigger background threads to update search lists.
                PanelModel    .instance.panelSearch.search ();
                PanelReference.instance.panelSearch.search ();
                PanelModel    .instance.panelMRU.loadMRU ();
                PanelReference.instance.panelMRU.loadMRU ();
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
                    String newKey = repo.key ();
                    String oldKey = AppData.state.get ("Repos", "primary");
                    if (! newKey.equals (oldKey))
                    {
                        AppData.state.set ("Repos", "primary", newKey);
                        needRebuild = true;
                        fireTableDataChanged ();
                    }
                    return true;
                case 1:
                    int visible = repo.getInt ("visible");
                    if (visible == 0) visible = 1;
                    else              visible = 0;
                    repo.set ("visible", visible);
                    needRebuild = true;
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

        public void setValueAt (Object value, int row, int column)
        {
            MNode repo = repos.get (row);
            switch (column)
            {
                case 3:
                    String newName = value.toString ();
                    String oldName = repo.key ();
                    if (newName.isEmpty ()  ||  newName.equals (oldName)) return;
                    if (AppData.repos.child (newName) != null) return;

                    // Now we have a legitimate name change.
                    MDir models     = (MDir) existingModels    .get (oldName);
                    MDir references = (MDir) existingReferences.get (oldName);
                    existingModels    .remove (oldName);
                    existingReferences.remove (oldName);
                    existingModels    .put (newName, models);
                    existingReferences.put (newName, references);
                    Path repoDir = reposDir.resolve (newName);
                    models    .set (repoDir.resolve ("models"));  // Flushes write queue, so save thread won't interfere with the move.
                    references.set (repoDir.resolve ("references"));
                    AppData.repos.move (oldName, newName);

                    String primary = AppData.state.get ("Repos", "primary");
                    if (oldName.equals (primary)) AppData.state.set ("Repos", "primary", newName);
                    updateOrder ();

                    // No need to rebuild, because all object identities are maintained.
                    return;
            }
        }

        public void updateOrder ()
        {
            String primary = AppData.state.get ("Repos", "primary");
            boolean primaryFound = false;
            String order = "";
            for (MNode r : repos)
            {
                String repoName = r.key ();
                if (repoName.equals (primary)) primaryFound = true;
                order += "," + repoName;
            }
            if (! order.isEmpty ()) order = order.substring (1);
            AppData.state.set ("Repos", "order", order);
            if (! primaryFound  &&  repos.size () > 0) AppData.state.set ("Repos", "primary", repos.get (0).key ());
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