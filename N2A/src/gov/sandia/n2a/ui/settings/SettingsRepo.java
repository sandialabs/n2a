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
import gov.sandia.n2a.ui.images.ImageUtil;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DropMode;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RemoteSetUrlCommand;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.FileTreeIterator;

@SuppressWarnings("serial")
public class SettingsRepo extends JScrollPane implements Settings
{
    protected JTable         repoTable;
    protected RepoTableModel repoModel;
    protected GitTableModel  gitModel;
    protected JTable         gitTable;
    protected JButton        buttonRevert;
    protected JButton        buttonRefresh;
    protected JLabel         labelStatus;
    protected JButton        buttonPull;
    protected JButton        buttonCommit;
    protected JButton        buttonPush;

    protected boolean           needRebuild;  // need to re-collate AppData.models and AppData.references
    protected boolean           needSave;     // need to flush repositories to disk for git status
    protected Map<String,MNode> existingModels     = AppData.models    .getContainerMap ();
    protected Map<String,MNode> existingReferences = AppData.references.getContainerMap ();
    protected Path              reposDir           = Paths.get (AppData.properties.get ("resourceDir")).resolve ("repos");

    public SettingsRepo ()
    {
        setName ("Repositories");  // Necessary to fulfill Settings interface.
        JPanel panel = new JPanel ();
        setViewportView (panel);

        repoModel = new RepoTableModel ();
        repoTable = new JTable (repoModel);
        JPanel repoPanel = new JPanel ();
        repoPanel.setAlignmentX (LEFT_ALIGNMENT);
        Lay.BLtg (repoPanel, "N", repoTable.getTableHeader (), "C", repoTable);

        repoTable.setAutoResizeMode (JTable.AUTO_RESIZE_OFF);
        repoTable.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        repoTable.setCellSelectionEnabled (true);
        repoTable.setSurrendersFocusOnKeystroke (true);
        repoTable.setDragEnabled (true);
        repoTable.setDropMode (DropMode.ON);

        repoTable.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                Point p = e.getPoint ();
                int row    = repoTable.rowAtPoint (p);
                int column = repoTable.columnAtPoint (p);
                if (row >= 0  &&  column >= 0) repoModel.toggle (row, column);
            }
        });

        InputMap inputMap = repoTable.getInputMap (WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put (KeyStroke.getKeyStroke ("shift UP"),   "moveUp");
        inputMap.put (KeyStroke.getKeyStroke ("shift DOWN"), "moveDown");
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"),     "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),     "delete");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),      "startEditing");

        ActionMap actionMap = repoTable.getActionMap ();
        actionMap.put ("moveUp", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                repoModel.moveSelected (-1);
            }
        });
        actionMap.put ("moveDown", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                repoModel.moveSelected (1);
            }
        });
        actionMap.put ("add", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                int row = repoTable.getSelectedRow ();
                if (row < 0) row = 0;

                String name = "local";
                int suffix = 2;
                while (AppData.repos.child (name) != null) name = "local" + suffix++;

                AppData.repos.set (name, "visible", 1);  // Implicitly creates the repo node.
                Path baseDir = reposDir.resolve (name);
                existingModels    .put (name, new MDir (baseDir.resolve ("models")));
                existingReferences.put (name, new MDir (baseDir.resolve ("references")));
                needRebuild = true;

                repoModel.repos   .add (row, AppData.repos.child (name));
                repoModel.gitRepos.add (row, new GitWrapper (baseDir.resolve (".git")));
                repoModel.updateOrder ();
                repoModel.fireTableRowsInserted (row, row);
                repoTable.changeSelection (row, 3, false, false);
                repoTable.editCellAt (row, 3, e);
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                int column = repoTable.getSelectedColumn ();
                int row    = repoTable.getSelectedRow ();
                if (row < 0) return;
                MNode repo = repoModel.repos.get (row);
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

                AppData.repos.clear (name);
                existingModels    .remove (name);
                existingReferences.remove (name);
                needRebuild = true;

                GitWrapper gitRepo = repoModel.gitRepos.get (row);
                gitRepo.close ();
                repoModel.gitRepos.remove (row);
                repoModel.repos   .remove (row);
                repoModel.updateOrder ();
                repoModel.fireTableRowsDeleted (row, row);
                if (row >= repoModel.repos.size ()) row = repoModel.repos.size () - 1;
                if (row >= 0) repoTable.changeSelection (row, column, false, false);
            }
        });
        actionMap.put ("startEditing", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                int row    = repoTable.getSelectedRow ();
                int column = repoTable.getSelectedColumn ();
                if (! repoModel.toggle (row, column)) repoTable.editCellAt (row, column, e);
            }
        });

        repoTable.setTransferHandler (new TransferHandler ()
        {
            public boolean canImport (TransferSupport xfer)
            {
                return xfer.isDataFlavorSupported (DataFlavor.stringFlavor);
            }

            public boolean importData (TransferSupport xfer)
            {
                if (! xfer.isDrop ()  ||  xfer.getDropAction () != MOVE) return false;

                int sourceRow = -1;
                try
                {
                    Transferable xferable = xfer.getTransferable ();
                    sourceRow = Integer.valueOf ((String) xferable.getTransferData (DataFlavor.stringFlavor));
                }
                catch (Exception e)
                {
                    return false;
                }
                if (sourceRow < 0  ||  sourceRow >= repoModel.repos.size ()) return false;

                int destinationRow = ((JTable.DropLocation) xfer.getDropLocation ()).getRow ();  // TODO: does need a range check?
                repoModel.move (sourceRow, destinationRow);
                return true;
            }

            public int getSourceActions (JComponent comp)
            {
                return MOVE;
            }

            protected Transferable createTransferable (JComponent comp)
            {
                // TODO: is row selection updated at start of drag? If not, need to enforce that somewhere.
                return new StringSelection (String.valueOf (repoTable.getSelectedRow ()));
            }
        });

        FocusListener rebuildListener = new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                if (needSave)  // Indicates that focus left this settings tab in previous event.
                {
                    int row = repoTable.getSelectedRow ();
                    if (row < 0)
                    {
                        if (repoModel.getRowCount () == 0) return;
                        row = 0;
                    }
                    gitModel.current = null;
                    gitModel.setCurrent (row);
                }
            }

            public void focusLost (FocusEvent e)
            {
                Component to = e.getOppositeComponent ();
                if (to == gitTable  ||  to == repoTable  ||  to == SettingsRepo.this) return;  // Loss is still within current tab, so ignore.
                if (e.isTemporary ()  ||  repoTable.isEditing ()) return;  // The shift to the editing component appears as a loss of focus.
                repoModel.clearStatus ();
                needSave = true;
                rebuild ();
            }
        };
        repoTable.addFocusListener (rebuildListener);

        repoTable.getSelectionModel ().addListSelectionListener (new ListSelectionListener ()
        {
            public void valueChanged (ListSelectionEvent e)
            {
                int row = repoTable.getSelectedRow ();
                gitModel.setCurrent (row);
            }
        });

        FontMetrics fm = repoTable.getFontMetrics (repoTable.getFont ());
        int em = fm.charWidth ('M');
        TableColumnModel cols = repoTable.getColumnModel ();

        TableColumn col = cols.getColumn (0);
        int width = fm.stringWidth (repoModel.getColumnName (0)) + em;
        col.setMaxWidth (width);

        col = cols.getColumn (1);
        width = fm.stringWidth (repoModel.getColumnName (1)) + em;
        col.setMaxWidth (width);

        col = cols.getColumn (2);
        width = fm.stringWidth (repoModel.getColumnName (2)) + em;
        col.setMaxWidth (width);
        col.setCellRenderer (new ColorRenderer ());

        col = cols.getColumn (3);
        width = fm.stringWidth (repoModel.getColumnName (3));
        for (MNode r : AppData.repos) width = Math.max (width, fm.stringWidth (r.key ()));
        width = Math.max (width, 15 * em);
        width += em;
        col.setMinWidth (width);
        col.setPreferredWidth (width);
        col.setCellRenderer (new ColorTextRenderer ());

        col = cols.getColumn (4);
        width = fm.stringWidth (repoModel.getColumnName (4));
        for (GitWrapper w : repoModel.gitRepos)
        {
            String url = w.getURL ();
            if (url == null) continue;
            width = Math.max (width, fm.stringWidth (url));
        }
        width = Math.max (width, 40 * em);
        width += em;
        col.setMinWidth (width);
        col.setPreferredWidth (width);

        ((DefaultTableCellRenderer) repoTable.getTableHeader ().getDefaultRenderer ()).setHorizontalAlignment (JLabel.LEFT);


        gitModel = new GitTableModel ();
        gitTable = new JTable (gitModel);
        JPanel gitPanel = new JPanel ();
        gitPanel.setAlignmentX (LEFT_ALIGNMENT);
        Lay.BLtg (gitPanel, "N", gitTable.getTableHeader (), "C", gitTable);

        gitTable.setAutoResizeMode (JTable.AUTO_RESIZE_OFF);
        gitTable.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        gitTable.setCellSelectionEnabled (true);
        gitTable.setSurrendersFocusOnKeystroke (true);
        gitTable.addFocusListener (rebuildListener);
        gitTable.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                Point p = e.getPoint ();
                int row    = gitTable.rowAtPoint (p);
                int column = gitTable.columnAtPoint (p);
                if (row >= 0  &&  column == 0) gitModel.toggle (row);
            }
        });

        inputMap = gitTable.getInputMap (WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),     "delete");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),      "startEditing");

        actionMap = gitTable.getActionMap ();
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                gitModel.revert (gitTable.getSelectedRow ());
            }
        });
        actionMap.put ("startEditing", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                int row    = gitTable.getSelectedRow ();
                int column = gitTable.getSelectedColumn ();
                if (column == 0) gitModel.toggle (row);
            }
        });

        fm = gitTable.getFontMetrics (gitTable.getFont ());
        em = fm.charWidth ('M');
        cols = gitTable.getColumnModel ();

        col = cols.getColumn (0);
        width = fm.stringWidth (gitModel.getColumnName (0)) + em;
        col.setMaxWidth (width);

        col = cols.getColumn (1);
        width = fm.stringWidth (gitModel.getColumnName (1));
        width = Math.max (width, 40 * em);
        width += em;
        col.setMinWidth (width);
        col.setCellRenderer (new GitColorTextRenderer ());

        ((DefaultTableCellRenderer) gitTable.getTableHeader ().getDefaultRenderer ()).setHorizontalAlignment (JLabel.LEFT);


        buttonRevert = new JButton (ImageUtil.getImage ("revert.gif"));
        buttonRevert.setMargin (new Insets (2, 2, 2, 2));
        buttonRevert.setFocusable (false);
        buttonRevert.setToolTipText ("Revert");
        buttonRevert.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                gitModel.revert (gitTable.getSelectedRow ());
            }
        });

        buttonRefresh = new JButton (ImageUtil.getImage ("refresh.gif"));
        buttonRefresh.setMargin (new Insets (2, 2, 2, 2));
        buttonRefresh.setFocusable (false);
        buttonRefresh.setToolTipText ("Refresh");
        buttonRefresh.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                gitModel.refreshTrack ();
            }
        });

        buttonPull = new JButton (ImageUtil.getImage ("pull.png"));
        buttonPull.setMargin (new Insets (2, 2, 2, 2));
        buttonPull.setFocusable (false);
        buttonPull.setToolTipText ("Pull");

        buttonCommit = new JButton (ImageUtil.getImage ("commit.png"));
        buttonCommit.setMargin (new Insets (2, 2, 2, 2));
        buttonCommit.setFocusable (false);
        buttonCommit.setToolTipText ("Commit");

        buttonPush = new JButton (ImageUtil.getImage ("push.png"));
        buttonPush.setMargin (new Insets (2, 2, 2, 2));
        buttonPush.setFocusable (false);
        buttonPush.setToolTipText ("Push");

        labelStatus = new JLabel ("Ahead ?, Behind ?");
        buttonPush.setMargin (new Insets (2, 2, 2, 2));


        Lay.BLtg (panel,
            "N", Lay.BxL (
                Lay.BL ("W", repoPanel),
                Lay.WL ("L", buttonRevert, Box.createHorizontalStrut (15), labelStatus, buttonRefresh, buttonPull, buttonCommit, buttonPush, "hgap=10,vgap=10"),
                Lay.BL ("W", gitPanel)
            )
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

    /**
        Rebuild the combo directories.
        This is lightweight enough that it can be done on the main UI thread.
    **/
    public void rebuild ()
    {
        if (! needRebuild) return;

        List<MNode> modelContainers     = new ArrayList<MNode> ();
        List<MNode> referenceContainers = new ArrayList<MNode> ();
        String primary = AppData.state.get ("Repos", "primary");
        for (MNode repo : repoModel.repos)
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
        AppData.models    .init (modelContainers);     // Triggers change() call to PanelModel
        AppData.references.init (referenceContainers); // Triggers change() call to PanelReference
        needRebuild = false;
    }

    public class RepoTableModel extends AbstractTableModel
    {
        List<MNode>      repos    = new ArrayList<MNode> ();
        List<GitWrapper> gitRepos = new ArrayList<GitWrapper> ();
        int              primaryRow;

        public RepoTableModel ()
        {
            String primary = AppData.state.get ("Repos", "primary");
            for (String key : AppData.state.get ("Repos", "order").split (","))
            {
                MNode child = AppData.repos.child (key);
                if (child == null) continue;
                if (key.equals (primary)) primaryRow = repos.size ();
                repos.add (child);
                gitRepos.add (new GitWrapper (reposDir.resolve (key).resolve (".git")));
            }
        }

        public void clearStatus ()
        {
            for (GitWrapper g : gitRepos) g.clearDiff ();
        }

        public int getRowCount ()
        {
            return repos.size ();
        }

        public int getColumnCount ()
        {
            return 5;
        }

        public String getColumnName (int column)
        {
            switch (column)
            {
                case 0: return "Primary";
                case 1: return "Enable";
                case 2: return "Color";
                case 3: return "Name";
                case 4: return "Git Remote";
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
            switch (column)
            {
                case 0: return row == primaryRow;
                case 1: return repo.get ("visible").equals ("1");
                case 2: return getColor (row);
                case 3: return name;
                case 4: return gitRepos.get (row).getURL ();
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
                        int oldRow = primaryRow;
                        primaryRow = row;
                        AppData.state.set ("Repos", "primary", newKey);
                        needRebuild = true;
                        fireTableCellUpdated (oldRow, 0);  // Primary
                        fireTableCellUpdated (oldRow, 3);  // Name, which may be rendered in a new color
                        fireTableCellUpdated (row,    0);
                        fireTableCellUpdated (row,    3);
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
            switch (column)
            {
                case 3:
                    MNode repo = repos.get (row);
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
                case 4:
                    GitWrapper gitRepo = gitRepos.get (row);
                    gitRepo.setURL (value.toString ());
                    return;
            }
        }

        public void moveSelected (int direction)
        {
            int rowBefore = repoTable.getSelectedRow ();
            if (rowBefore < 0) return;

            int rowAfter = rowBefore + direction;
            if (rowAfter >= 0  &&  rowAfter < repos.size ()) move (rowBefore, rowAfter);
        }

        public void move (int rowBefore, int rowAfter)
        {
            if (rowAfter == rowBefore) return;

            MNode repo = repos.remove (rowBefore);
            repos.add (rowAfter, repo);
            updateOrder ();
            int column = repoTable.getSelectedColumn ();
            if (rowAfter < rowBefore) fireTableRowsUpdated (rowAfter,  rowBefore);
            else                      fireTableRowsUpdated (rowBefore, rowAfter);
            repoTable.changeSelection (rowAfter, column, false, false);
            needRebuild = true;
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
            if (! primaryFound  &&  repos.size () > 0) AppData.state.set ("Repos", "primary", repos.get (0).key ());  // and primaryRow should already be 0
        }
    }

    public class ColorRenderer extends JPanel implements TableCellRenderer
    {
        public Component getTableCellRendererComponent (JTable table, Object text, boolean isSelected, boolean hasFocus, int row, int column)
        {
            setOpaque (true);
            setBackground (repoModel.getColor (row));

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
            setText (text.toString ());

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
                Color color = repoModel.getColor (row);
                if (color.equals (Color.black)) color = Color.blue;
                setForeground (color);
            }

            setFont (table.getFont ());

            return this;
        }
    }

    public static class Delta
    {
        public String  name;
        public boolean deleted;
        public boolean untracked;
    }

    public class GitWrapper implements AutoCloseable
    {
        public Path         gitDir;
        public Repository   gitRepo;
        public Git          git;
        public StoredConfig config;
        public String       head;
        public RemoteConfig remote;
        public int          ahead;
        public int          behind;
        public IndexDiff    diff;

        public GitWrapper (Path gitDir)
        {
            this.gitDir = gitDir;
            // Every N2A repo gets an associated git repo with at least local version control.
            try
            {
                gitRepo = new FileRepository (gitDir.toFile ());
                git = new Git (gitRepo);
                config = gitRepo.getConfig ();
                config.load ();

                head = gitRepo.getBranch ();
                if (head == null)
                {
                    gitRepo.create ();
                    head = gitRepo.getBranch ();
                }

                BranchConfig branch = new BranchConfig (config, head);
                String remoteName = branch.getRemote ();
                if (remoteName == null) remoteName = "origin";
                remote = new RemoteConfig (config, remoteName);
            }
            catch (Exception e)
            {
                e.printStackTrace ();
            }
        }

        public void close ()
        {
            try
            {
                // This is just a paranoia measure. config should be saved any time significant changes are made,
                if (config != null) config.save ();
            }
            catch (IOException e) {}
            if (git     != null) git    .close ();
            if (gitRepo != null) gitRepo.close ();
        }

        public String getURL ()
        {
            List<URIish> URIs = remote.getURIs ();
            if (URIs.size () == 0) return null;
            return URIs.get (0).toString ();
        }

        public void setURL (String value)
        {
            URIish valueURI;
            try
            {
                valueURI = new URIish (value);
            }
            catch (URISyntaxException e1)
            {
                return;
            }

            String remoteName = remote.getName ();
            if (gitRepo.getRemoteNames ().isEmpty ())
            {
                try {remote = git.remoteAdd ().setName (remoteName).setUri (valueURI).call ();}
                catch (Exception e) {}
                return;  // Because we've already set the remote URI, which is the goal of this function.
            }

            RemoteSetUrlCommand command = git.remoteSetUrl ();
            command.setName (remoteName);
            command.setUri (valueURI);
            try {remote = command.call ();}
            catch (Exception e) {}
        }

        public synchronized void clearDiff ()
        {
            diff = null;
        }

        public Delta addDelta (String name, TreeMap<String,Delta> map)
        {
            if (! name.contains ("/")) return new Delta ();  // A throw away object, which won't get included in map and thus not in final result.

            Delta d = map.get (name);
            if (d == null)
            {
                d = new Delta ();
                d.name = name;
                map.put (name, d);
            }
            return d;
        }

        public synchronized List<Delta> getDeltas ()
        {
            if (diff == null)
            {
                if (needSave)
                {
                    AppData.save ();
                    needSave = false;
                }
                try
                {
                    diff = new IndexDiff (gitRepo, "HEAD", new FileTreeIterator (gitRepo));
                    diff.diff ();
                }
                catch (IOException e) {e.printStackTrace ();}
            }
            if (diff == null) return new ArrayList<Delta> ();

            TreeMap<String,Delta> sorted = new TreeMap<String,Delta> ();
            for (String s : diff.getUntracked ()) addDelta (s, sorted).untracked = true;
            for (String s : diff.getModified ())  addDelta (s, sorted);
            for (String s : diff.getMissing ())   addDelta (s, sorted).deleted = true;
            return new ArrayList<Delta> (sorted.values ());
        }

        public void getRemoteTracking ()
        {
            ahead  = 0;
            behind = 0;
            if (head == null) return;
            try
            {
                BranchTrackingStatus track = BranchTrackingStatus.of (gitRepo, head);
                if (track != null)
                {
                    ahead  = track.getAheadCount ();
                    behind = track.getBehindCount ();
                }
            }
            catch (IOException e) {}
        }

        public void checkout (String name)
        {
            try
            {
                git.checkout ().addPath (name).call ();
            }
            catch (Exception e) {}
            clearDiff ();  // force update
        }
    }

    public class GitTableModel extends AbstractTableModel
    {
        GitWrapper  current;
        MNode       repo;
        List<Delta> deltas = new ArrayList<Delta> ();

        public synchronized void setCurrent (int repoIndex)
        {
            if (repoIndex < 0  ||  repoIndex >= repoModel.gitRepos.size ())
            {
                current = null;
                repo = null;
                deltas.clear ();
                return;
            }

            GitWrapper gitRepo = repoModel.gitRepos.get (repoIndex);
            if (current == gitRepo) return;
            current = gitRepo;
            repo = repoModel.repos.get (repoIndex);
            refreshDiff ();
            refreshTrack ();
        }

        public void refreshDiff ()
        {
            Thread thread = new Thread ()
            {
                public void run ()
                {
                    MNode      mrepo;
                    GitWrapper gitRepo;
                    synchronized (gitModel)  // Same as GitTableModel.this, just simpler to write.
                    {
                        mrepo   = repo;
                        gitRepo = current;
                    }
                    List<Delta> deltas = gitRepo.getDeltas ();

                    // Remove any "ignore" entries that are not in the current diff list.
                    // This prevents any surprises when an item is re-added in the future.
                    MNode ignore = mrepo.child ("ignore");
                    if (ignore != null)
                    {
                        Set<String> names = new TreeSet<String> ();
                        for (Delta d : deltas) names.add (d.name);
                        for (MNode i : ignore)
                        {
                            String key = i.key ();
                            if (! names.contains (key)) ignore.clear (key);  // MNode iterators are always safe for concurrent modification.
                        }
                    }

                    synchronized (gitModel)
                    {
                        if (gitRepo != gitModel.current) return;
                        gitModel.deltas = deltas;
                        EventQueue.invokeLater (new Runnable ()
                        {
                            public void run ()
                            {
                                int column = gitTable.getSelectedColumn ();
                                int row = gitTable.getSelectedRow ();

                                gitModel.fireTableDataChanged ();

                                if (column < 0) column = 1;
                                if (row >= deltas.size ()) row = deltas.size () - 1;
                                if (row >= 0) gitTable.changeSelection (row, column, false, false);
                            }
                        });
                    }
                }
            };
            thread.setDaemon (true);
            thread.run ();
        }

        public void refreshTrack ()
        {
            Thread thread = new Thread ()
            {
                public void run ()
                {
                    GitWrapper gitRepo = gitModel.current;
                    gitRepo.getRemoteTracking ();  // If this actually calls the remote server, then it could take a long time.
                    synchronized (gitModel)
                    {
                        if (gitRepo != gitModel.current) return;
                        labelStatus.setText ("Ahead " + gitRepo.ahead + ", Behind " + gitRepo.behind);
                        EventQueue.invokeLater (new Runnable ()
                        {
                            public void run ()
                            {
                                labelStatus.repaint ();
                            }
                        });
                    }
                }
            };
            thread.setDaemon (true);
            thread.run ();
        }

        public synchronized int getRowCount ()
        {
           return deltas.size ();
        }

        public int getColumnCount ()
        {
            return 2;
        }

        public String getColumnName (int column)
        {
            switch (column)
            {
                case 0: return "Commit";
                case 1: return "Filename";
            }
            return "";
        }

        public Class<?> getColumnClass (int column)
        {
            if (column == 0) return Boolean.class;
            return super.getColumnClass (column);
        }

        public boolean isCellEditable (int row, int column)
        {
            return false;
        }

        public synchronized Object getValueAt (int row, int column)
        {
            Delta delta = deltas.get (row);
            switch (column)
            {
                case 0: return repo.child ("ignore", delta.name) == null;
                case 1: return delta;
            }
            return null;
        }

        public synchronized void toggle (int row)
        {
            String name = deltas.get (row).name;
            if (repo.child ("ignore", name) == null) repo.set   ("ignore", name, "");
            else                                     repo.clear ("ignore", name);
            fireTableCellUpdated (row, 0);
        }

        public synchronized void revert (int row)
        {
            if (row < 0  ||  row >= deltas.size ()) return;
            Delta delta = deltas.get (row);
            if (delta.untracked) return;

            if (! delta.deleted)
            {
                int response = JOptionPane.showConfirmDialog
                (
                    MainFrame.instance,
                    "<html><body><p style='width:300px'>Permanently abandon all edits to \"" + delta.name + "\" since last commit?</p></body></html>",
                    "Revert",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                if (response != JOptionPane.OK_OPTION) return;
            }

            AppData.save ();
            current.checkout (delta.name);
            refreshDiff ();

            String repoName = repo.key ();
            MDir dir;
            String[] pieces = delta.name.split ("/");
            if (pieces[0].equals ("models")) dir = (MDir) existingModels    .get (repoName);
            else                             dir = (MDir) existingReferences.get (repoName);
            dir.nodeChanged (pieces[1]);
            // TODO: create an undo object for the checkout, so it can integrate with other edit actions in the UI.
        }
    }

    public class GitColorTextRenderer extends JLabel implements TableCellRenderer
    {
        public Color green = Color.green.darker ();

        public Component getTableCellRendererComponent (JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column)
        {
            Delta delta = (Delta) object;
            setText (delta.name);

            setOpaque (true);
            if (isSelected) setBackground (table.getSelectionBackground ());
            else            setBackground (table.getBackground ());

            if      (delta.deleted  ) setForeground (Color.red);
            else if (delta.untracked) setForeground (green);
            else                      setForeground (Color.black);

            setFont (table.getFont ());

            return this;
        }
    }
}