/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.SafeTextTransferHandler;
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
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteSetUrlCommand;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.awtui.AwtCredentialsProvider;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;

@SuppressWarnings("serial")
public class SettingsRepo extends JScrollPane implements Settings
{
    protected JTable         repoTable;
    protected RepoTableModel repoModel;
    protected GitTableModel  gitModel;
    protected JTable         gitTable;
    protected JButton        buttonRevert;
    protected JLabel         labelStatus;
    protected JButton        buttonPull;
    protected JButton        buttonPush;
    protected JTextField     fieldAuthor;
    protected JTextArea      fieldMessage;
    protected JScrollPane    paneMessage;
    protected UndoManager    undoMessage;

    protected boolean           needRebuild;     // need to re-collate AppData.models and AppData.references
    protected boolean           needSave = true; // need to flush repositories to disk for git status
    protected Map<String,MNode> existingModels     = AppData.models    .getContainerMap ();
    protected Map<String,MNode> existingReferences = AppData.references.getContainerMap ();
    protected Path              reposDir           = Paths.get (AppData.properties.get ("resourceDir")).resolve ("repos");

    protected int timeout = 30;  // seconds; for git operations

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
                repoModel.create (row, "");
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
                if (! isEmpty (name))
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

                repoModel.delete (row, column);  // column is just a hint for updating focus
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
                String value;
                try
                {
                    Transferable xferable = xfer.getTransferable ();
                    value = (String) xferable.getTransferData (DataFlavor.stringFlavor);
                }
                catch (Exception e)
                {
                    return false;
                }

                // Paste, or drop type other than MOVE --> Could be a Git remote URI
                if (! xfer.isDrop ()  ||  xfer.getDropAction () != MOVE)
                {
                    // Must have the form of a Git URI
                    try
                    {
                        new URIish (value);
                    }
                    catch (URISyntaxException e)
                    {
                        return false;
                    }

                    // Must not already exist in list
                    for (GitWrapper w : repoModel.gitRepos) if (value.equals (w.getURL ())) return false;

                    // Apply the URI
                    int row;
                    if (xfer.isDrop ()) row = ((JTable.DropLocation) xfer.getDropLocation ()).getRow ();
                    else                row = repoTable.getSelectedRow ();
                    if (row >= 0  &&  repoModel.getValueAt (row, 4) == null)  // URI cell is empty, so simply assign value.
                    {
                        repoModel.setValueAt (value, row, 4);
                        repoModel.fireTableCellUpdated (row, 4);
                        return true;
                    }
                    // Clone new repo
                    if (row < 0) row = repoModel.getRowCount ();
                    repoModel.create (row, value);
                    return true;
                }

                // Drop of type MOVE --> Reordering of repository precedence
                int sourceRow = -1;
                try
                {
                    sourceRow = Integer.valueOf (value);
                }
                catch (Exception e)
                {
                    return false;
                }
                if (sourceRow < 0  ||  sourceRow >= repoModel.repos.size ()) return false;
                int destinationRow = ((JTable.DropLocation) xfer.getDropLocation ()).getRow ();
                repoModel.move (sourceRow, destinationRow);
                return true;
            }

            public int getSourceActions (JComponent comp)
            {
                return MOVE;
            }

            protected Transferable createTransferable (JComponent comp)
            {
                return new StringSelection (String.valueOf (repoTable.getSelectedRow ()));
            }
        });

        FocusListener rebuildListener = new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                if (needSave)  // Indicates that focus previously left this settings tab.
                {
                    gitModel.current = null;
                    int row = repoTable.getSelectedRow ();
                    if (row < 0) return;
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
        TextCellEditor cellEditor = new TextCellEditor ();
        col.setCellEditor (cellEditor);

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
        col.setCellEditor (cellEditor);

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

        buttonPull = new JButton (ImageUtil.getImage ("pull.png"));
        buttonPull.setMargin (new Insets (2, 2, 2, 2));
        buttonPull.setFocusable (false);
        buttonPull.setToolTipText ("Pull");
        buttonPull.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                pull (gitModel.current, gitModel.repo.key ());
            }
        });

        buttonPush = new JButton (ImageUtil.getImage ("push.png"));
        buttonPush.setMargin (new Insets (2, 2, 2, 2));
        buttonPush.setFocusable (false);
        buttonPush.setToolTipText ("Push");
        buttonPush.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                gitModel.commit ();
            }
        });

        labelStatus = new JLabel ();
        buttonPush.setMargin (new Insets (2, 2, 2, 2));

        fieldAuthor = new JTextField ();
        fieldAuthor.setColumns (30);

        fieldMessage = new JTextArea ();
        paneMessage = new JScrollPane (fieldMessage);
        fieldMessage.setLineWrap (true);
        fieldMessage.setWrapStyleWord (true);
        fieldMessage.setRows (6);
        fieldMessage.setTabSize (4);

        undoMessage = new UndoManager ();
        fieldMessage.getDocument ().addUndoableEditListener (undoMessage);
        inputMap = fieldMessage.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo");
        inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");
        actionMap = fieldMessage.getActionMap ();
        actionMap.put ("Undo", new AbstractAction ("Undo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoMessage.undo ();}
                catch (CannotUndoException e) {}
            }
        });
        actionMap.put ("Redo", new AbstractAction ("Redo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoMessage.redo();}
                catch (CannotRedoException e) {}
            }
        });

        // Use JGit utility class to preset username/password prompt for remote access.
        AwtCredentialsProvider.install ();


        Lay.BLtg (panel,
            "N", Lay.BxL (
                Lay.BL ("W", repoPanel),
                Lay.WL ("L", buttonRevert, Box.createHorizontalStrut (15), labelStatus, buttonPull, buttonPush, "hgap=10,vgap=10"),
                Lay.BL ("W",
                    Lay.BxL ("H",
                        Lay.BL ("N", gitPanel),
                        Box.createHorizontalStrut (5),
                        Lay.BL (
                            "N", Lay.BxL (
                                Lay.FL (Lay.lb ("Author"), fieldAuthor),
                                Box.createVerticalStrut (5),
                                Lay.BL ("W", Lay.lb ("Commit message:")),
                                paneMessage
                            )
                        )
                    )
                )
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

    public boolean isEmpty (String key)
    {
        MDir models     = (MDir) existingModels    .get (key);
        MDir references = (MDir) existingReferences.get (key);
        return  models.size () == 0  &&  references.size () == 0;
    }

    public void pull (GitWrapper gitRepo, String key)
    {
        MDir models     = (MDir) existingModels    .get (key);
        MDir references = (MDir) existingReferences.get (key);
        boolean empty = models.size () == 0  &&  references.size () == 0;
        needRebuild = true;
        Thread thread = new Thread ()
        {
            public void run ()
            {
                // TODO: Prevent repo rename while pull is in progress. Simple approach is to add check to TextCellEditor.isCellEditable()
                if (empty) gitRepo.pullNew ();
                else       gitRepo.pull ();
                models    .reload ();
                references.reload ();
                if (needRebuild)  // UI focus is still on settings panel.
                {
                    if (gitRepo == gitModel.current)
                    {
                        gitModel.refreshDiff ();
                        gitModel.refreshTrack ();
                    }
                }
                else  // UI focus has shifted away from settings panel, and rebuild() may have been done before pull finished.
                {
                    // Force another rebuild, to ensure that new files are visible to user.
                    needRebuild = true;
                    rebuild ();
                }
            }
        };
        thread.setDaemon (true);
        thread.start ();
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
            MNode repo = repos.get (row);
            String key = repo.key ();
            switch (column)
            {
                case 3:
                    String newName = value.toString ();
                    String oldName = key;
                    if (newName.isEmpty ()  ||  newName.equals (oldName)) return;
                    if (AppData.repos.child (newName) != null) return;

                    // Now we have a legitimate name change.
                    MDir models     = (MDir) existingModels    .get (oldName);
                    MDir references = (MDir) existingReferences.get (oldName);
                    existingModels    .remove (oldName);
                    existingReferences.remove (oldName);
                    existingModels    .put (newName, models);
                    existingReferences.put (newName, references);

                    gitRepos.get (row).close ();
                    Path repoDir = reposDir.resolve (newName);
                    models    .set (repoDir.resolve ("models"));  // Flushes write queue, so save thread won't interfere with the move.
                    references.set (repoDir.resolve ("references"));
                    AppData.repos.move (oldName, newName);
                    gitRepos.set (row, new GitWrapper (repoDir.resolve (".git")));

                    String primary = AppData.state.get ("Repos", "primary");
                    if (oldName.equals (primary)) AppData.state.set ("Repos", "primary", newName);
                    updateOrder ();

                    // No need to rebuild, because all object identities are maintained.
                    return;
                case 4:
                    GitWrapper gitRepo = gitRepos.get (row);
                    gitRepo.setURL (value.toString ());
                    if (isEmpty (key)) pull (gitRepo, key);  // New empty repository, so do initial pull
                    return;
            }
        }

        public void create (int row, String URL)
        {
            String prefix;
            if (URL.isEmpty ())
            {
                prefix = "local";
            }
            else
            {
                // Extract stem from URL
                int position = URL.lastIndexOf ('/');
                if (position < 0) prefix = URL;
                else              prefix = URL.substring (position + 1);
                position = prefix.lastIndexOf ('.');
                if (position > 0) prefix = prefix.substring (0, position);
            }

            String name = prefix;
            int suffix = 2;
            while (AppData.repos.child (name) != null) name = prefix + suffix++;

            // Sequencing here is important, to avoid creating the dir before call to Git.
            // Git requires an empty or non-existent dir in order to clone.
            Path baseDir = reposDir.resolve (name);
            GitWrapper gitRepo = new GitWrapper (baseDir.resolve (".git"));
            gitRepo.setURL (URL);
            AppData.repos.set (name, "visible", 1);  // Implicitly creates the repo node.
            existingModels    .put (name, new MDir (name, baseDir.resolve ("models")));
            existingReferences.put (name, new MDir (name, baseDir.resolve ("references")));
            needRebuild = true;

            repoModel.repos   .add (row, AppData.repos.child (name));
            repoModel.gitRepos.add (row, gitRepo);
            repoModel.updateOrder ();
            repoModel.fireTableRowsInserted (row, row);
            repoTable.changeSelection (row, 3, false, false);

            if (URL.isEmpty ()) repoTable.editCellAt (row, 3, null);
            else                pull (gitRepo, name);
        }

        public void delete (int row, int column)
        {
            // Order is important. Close the git repo before deleting the directory structure.
            gitRepos.get (row).close ();

            String name = repos.get (row).key ();
            AppData.repos.clear (name);
            existingModels    .remove (name);
            existingReferences.remove (name);

            needRebuild = true;
            gitRepos.remove (row);
            repos   .remove (row);
            updateOrder ();
            fireTableRowsDeleted (row, row);
            if (row >= repos.size ()) row = repos.size () - 1;
            if (row >= 0) repoTable.changeSelection (row, column, false, false);
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

            MNode      repo    = repos   .remove (rowBefore);
            GitWrapper gitRepo = gitRepos.remove (rowBefore);
            repos   .add (rowAfter, repo);
            gitRepos.add (rowAfter, gitRepo);
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
            primaryRow = 0;
            String order = "";
            for (int i = 0; i < repos.size (); i++)
            {
                String repoName = repos.get (i).key ();
                if (repoName.equals (primary)) primaryRow = i;
                order += "," + repoName;
            }
            if (! order.isEmpty ()) order = order.substring (1);
            AppData.state.set ("Repos", "order", order);
            if (repos.size () > 0) AppData.state.set ("Repos", "primary", repos.get (primaryRow).key ());
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

    public class TextCellEditor extends AbstractCellEditor implements TableCellEditor
    {
        protected UndoManager undoCell;
        protected JTextField  editor;

        public TextCellEditor ()
        {
            undoCell = new UndoManager ();

            editor = new JTextField ();
            editor.setBorder (new EmptyBorder (0, 0, 0, 0));

            editor.getDocument ().addUndoableEditListener (undoCell);
            InputMap inputMap = editor.getInputMap ();
            inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo");
            inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
            inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");
            ActionMap actionMap = editor.getActionMap ();
            actionMap.put ("Undo", new AbstractAction ("Undo")
            {
                public void actionPerformed (ActionEvent evt)
                {
                    try {undoCell.undo ();}
                    catch (CannotUndoException e) {}
                }
            });
            actionMap.put ("Redo", new AbstractAction ("Redo")
            {
                public void actionPerformed (ActionEvent evt)
                {
                    try {undoCell.redo();}
                    catch (CannotRedoException e) {}
                }
            });

            editor.addActionListener (new ActionListener ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    stopCellEditing ();
                }
            });

            editor.addFocusListener (new FocusListener ()
            {
                public void focusGained (FocusEvent e)
                {
                }

                public void focusLost (FocusEvent e)
                {
                    stopCellEditing ();
                }
            });

            editor.setTransferHandler (new SafeTextTransferHandler ());
        }

        public boolean isCellEditable (EventObject e)
        {
            if (e instanceof MouseEvent) return ((MouseEvent) e).getClickCount () >= 2;
            return true;
        }

        public Object getCellEditorValue ()
        {
            return editor.getText ();
        }

        public Component getTableCellEditorComponent (JTable table, Object value, boolean isSelected, int row, int column)
        {
            undoCell.discardAllEdits ();

            String valueString = "";
            if (value != null) valueString = value.toString ();
            editor.setText (valueString);

            editor.setFont (table.getFont ());
            editor.setForeground (table.getForeground ());
            editor.setBackground (table.getBackground ());

            return editor;
        }
    }

    public static class Delta
    {
        public String  name;
        public boolean deleted;
        public boolean untracked;
        public boolean ignore;

        public MNode add;    // Changed and added nodes, relative to parent commit.
        public MNode remove; // Removed nodes relative to parent commit.
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
        public String       message;

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
                if (remoteName == null) remoteName = Constants.DEFAULT_REMOTE_NAME;
                remote = new RemoteConfig (config, remoteName);
            }
            catch (Exception e)
            {
                e.printStackTrace ();
            }
        }

        public synchronized void close ()
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
            catch (URISyntaxException e)
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

        public String getAuthor ()
        {
            String name  = config.getString ("user", null, "name");
            String email = config.getString ("user", null, "email");
            String result = "";
            if (name != null) result = name;
            if (email != null)
            {
                if (! result.isEmpty ()) result += " <" + email + ">";
                else                     result  =  "<" + email + ">";
            }
            return result;
        }

        public void setAuthor (String author)
        {
            if (author.equals (getAuthor())) return;  // Avoid adding to local config unless there is a real change.

            String[] pieces = author.split ("<", 2);
            String name = pieces[0].trim ();
            String email = "";
            if (pieces.length > 1) email = pieces[1].split (">")[0];

            config.setString ("user", null, "name",  name);
            config.setString ("user", null, "email", email);
            try {config.save ();}
            catch (IOException e) {}
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
                    AppData.save ();  // Flush all changes made outside this settings panel.
                    needSave = false;
                }
                try
                {
                    diff = new IndexDiff (gitRepo, head, new FileTreeIterator (gitRepo));
                    diff.diff ();
                }
                catch (IOException e) {}
            }
            return getDeltas (diff);
        }

        public List<Delta> getDeltas (IndexDiff diff)
        {
            if (diff == null) return new ArrayList<Delta> ();

            TreeMap<String,Delta> sorted = new TreeMap<String,Delta> ();
            for (String s : diff.getUntracked ()) addDelta (s, sorted).untracked = true;
            for (String s : diff.getModified ())  addDelta (s, sorted);
            for (String s : diff.getMissing ())   addDelta (s, sorted).deleted = true;
            return new ArrayList<Delta> (sorted.values ());
        }

        public List<Delta> createStashInternal ()
        {
            List<Delta> stash = getDeltas ();
            try
            {
                ObjectId commitId = gitRepo.resolve (head);
                RevCommit commit = gitRepo.parseCommit (commitId);
                RevTree tree = commit.getTree ();
                try (TreeWalk treeWalk = new TreeWalk (gitRepo))
                {
                    treeWalk.addTree (tree);
                    treeWalk.setRecursive (true);

                    Map<String,Delta> map = new TreeMap<String,Delta> ();
                    Path baseDir = gitDir.getParent ();
                    for (Delta d : stash)
                    {
                        if (d.untracked)
                        {
                            // Preserve state of file.
                            MDoc doc = new MDoc (baseDir.resolve (d.name));
                            d.add = new MVolatile ();
                            d.add.merge (doc);
                            continue;  // No point in adding this delta to map, since it won't be accessed.
                        }
                        map.put (d.name, d);
                    }
                    while (treeWalk.next ())
                    {
                        String path = treeWalk.getPathString ();
                        Delta d = map.get (path);
                        if (d == null) continue;

                        ObjectId objectId = treeWalk.getObjectId (0);  // The int index here selects which of the parallel trees to return id for. (But we are walking only one, so index is always 0.)
                        ObjectLoader loader = gitRepo.open (objectId);
                        ObjectStream stream = loader.openStream ();
                        MNode original = new MVolatile ();
                        Schema.readAll (original, new InputStreamReader (stream));

                        // Create difference trees
                        MDoc doc = new MDoc (baseDir.resolve (d.name));
                        d.add = new MVolatile ();
                        d.add.merge (doc);
                        d.remove = new MVolatile ();
                        d.remove.merge (original);
                        d.remove.uniqueNodes (d.add);
                        d.add.uniqueValues (original);
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            return stash;
        }

        public void applyStashInternal (List<Delta> stash)
        {
            Path baseDir = gitDir.getParent ();
            for (Delta d : stash)
            {
                Path path = baseDir.resolve (d.name);
                if (d.deleted)
                {
                    try {Files.delete (path);}
                    catch (IOException e) {}
                }
                else
                {
                    MDoc doc = new MDoc (path);  // If the file already exists, then it will be loaded as needed.
                    if (d.remove != null) doc.uniqueNodes (d.remove);
                    if (d.add    != null) doc.merge       (d.add);
                    doc.save ();
                }
            }
        }

        public void getRemoteTracking ()
        {
            ahead  = 0;
            behind = 0;

            try {git.fetch ().setTimeout (timeout).call ();}
            catch (Exception e) {}

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

        /**
            Populates a new empty repo from remote.
            Makes no attempt to rebase models, because there are not supposed to be any.
            Configures branch to track remote.
        **/
        public void pullNew ()
        {
            try
            {
                git.pull ().setTimeout (timeout).call ();

                // Update branch to track remote.
                CreateBranchCommand create = git.branchCreate ();
                create.setUpstreamMode (SetupUpstreamMode.SET_UPSTREAM);
                create.setName (head);
                create.setForce (true);  // Because head already exists.
                create.setStartPoint (remote.getName () + "/" + head);
                create.call ();
            }
            catch (Exception e) {}
        }

        public synchronized void pull ()
        {
            // TODO: not clear if git-diff is safe for N2A files. May need to implement our own rebase.
            // At a minimum, we must never subject the user to a merge conflict of any kind.
            try
            {
                // Clear away changes and new (untracked) files so they won't get in the way of pull.
                List<Delta> stash = createStashInternal ();
                Path baseDir = gitDir.getParent ();
                for (Delta d : stash)
                {
                    try {Files.delete (baseDir.resolve (d.name));}
                    catch (IOException e) {}
                }

                PullCommand pull = git.pull ();
                pull.setRebase (true);
                pull.setTimeout (timeout);
                pull.call ();

                applyStashInternal (stash);
                clearDiff ();
            }
            catch (Exception e)
            {
                e.printStackTrace ();
            }
        }

        public synchronized void checkout (String name)
        {
            clearDiff ();  // force update
            try {git.checkout ().addPath (name).call ();}
            catch (Exception e) {}
        }

        public synchronized void commit (List<Delta> deltas, String author, String message)
        {
            // Commit current changes
            AddCommand add = null;
            RmCommand  rm  = null;
            for (Delta d : deltas)
            {
                if (d.ignore) continue;
                if (d.deleted)
                {
                    if (rm == null) rm = git.rm ();
                    rm.addFilepattern (d.name);
                }
                else
                {
                    if (add == null) add = git.add ();
                    add.addFilepattern (d.name);
                }
            }
            try
            {
                if (add != null) add.call ();
                if (rm  != null) rm .call ();
            }
            catch (Exception e) {}

            if (add != null  ||  rm != null)
            {
                String[] pieces = author.split ("<", 2);
                String name = pieces[0].trim ();
                String email = "";
                if (pieces.length > 1) email = pieces[1].split (">")[0];

                CommitCommand commit = git.commit ();
                commit.setAuthor (name, email);
                commit.setMessage (message);
                try {commit.call ();}
                catch (Exception e) {}
            }

            // Push changes upstream
            PushCommand push = git.push ();
            push.setTimeout (timeout);
            try {push.call ();}
            catch (Exception e)
            {
                return;
            }

            // If this is a new repo, set head to track remote origin.
            BranchConfig branch = new BranchConfig (config, head);
            if (branch.getRemote () == null)  // head is not tracking remote
            {
                CreateBranchCommand create = git.branchCreate ();
                create.setUpstreamMode (SetupUpstreamMode.SET_UPSTREAM);
                create.setName (head);
                create.setForce (true);  // Because head already exists.
                create.setStartPoint (remote.getName () + "/" + head);
                try {create.call ();}
                catch (Exception e) {}
            }
        }
    }

    public class GitTableModel extends AbstractTableModel
    {
        GitWrapper  current;
        MNode       repo;
        List<Delta> deltas = new ArrayList<Delta> ();

        public synchronized void setCurrent (int repoIndex)
        {
            if (current != null)
            {
                current.setAuthor (fieldAuthor .getText ());
                current.message  = fieldMessage.getText ();
            }

            undoMessage.discardAllEdits ();
            if (repoIndex < 0  ||  repoIndex >= repoModel.gitRepos.size ())
            {
                current = null;
                repo = null;
                deltas.clear ();
                fieldAuthor .setText ("");
                fieldMessage.setText ("");
                return;
            }

            GitWrapper gitRepo = repoModel.gitRepos.get (repoIndex);
            if (gitRepo == current) return;
            current = gitRepo;
            repo = repoModel.repos.get (repoIndex);

            fieldAuthor .setText (current.getAuthor ());
            fieldMessage.setText (current.message);
            refreshDiff ();
            refreshTrackThread ();
        }

        public synchronized void refreshTable ()
        {
            int column = gitTable.getSelectedColumn ();
            int row = gitTable.getSelectedRow ();

            fireTableDataChanged ();

            if (column < 0) column = 1;
            if (row >= deltas.size ()) row = deltas.size () - 1;
            if (row >= 0) gitTable.changeSelection (row, column, false, false);
        }

        public void refreshDiff ()
        {
            Thread thread = new Thread ()
            {
                public void run ()
                {
                    MNode      mrepo;
                    GitWrapper working;
                    synchronized (GitTableModel.this)
                    {
                        mrepo   = repo;
                        working = current;
                    }
                    List<Delta> newDeltas = working.getDeltas ();

                    // Remove any "ignore" entries that are not in the current diff list.
                    // This prevents any surprises when an item is re-added in the future.
                    MNode ignore = mrepo.child ("ignore");
                    if (ignore != null)
                    {
                        Set<String> names = new TreeSet<String> ();
                        for (Delta d : newDeltas)
                        {
                            names.add (d.name);
                            d.ignore = ignore.child (d.name) != null;
                        }
                        for (MNode i : ignore)
                        {
                            String key = i.key ();
                            if (! names.contains (key)) ignore.clear (key);  // MNode iterators are always safe for concurrent modification.
                        }
                    }

                    synchronized (GitTableModel.this)
                    {
                        if (working != current) return;
                        deltas = newDeltas;
                    }
                    EventQueue.invokeLater (new Runnable ()
                    {
                        public void run ()
                        {
                            gitModel.refreshTable ();
                        }
                    });
                }
            };
            thread.setDaemon (true);
            thread.start ();
        }

        public void refreshTrackThread ()
        {
            Thread thread = new Thread ()
            {
                public void run ()
                {
                    refreshTrack ();
                }
            };
            thread.setDaemon (true);
            thread.start ();
        }

        public void refreshTrack ()
        {
            GitWrapper working = current;
            working.getRemoteTracking ();
            refreshTrackUI (working);
        }

        public synchronized void refreshTrackUI (GitWrapper working)
        {
            if (working != current) return;

            buttonPush.setEnabled (working.behind == 0);
            String status = "";
            if (working.ahead != 0) status = "Ahead " + working.ahead;
            if (working.behind != 0)
            {
                if (! status.isEmpty ()) status += ", ";
                status += "Behind " + working.behind;
            }
            labelStatus.setText (status);
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
            if (row >= deltas.size ()) return null;  // It is possible for "deltas" to get changed (by refreshDiff thread) after the call to getRowCount(), so need guard here.
            Delta delta = deltas.get (row);
            switch (column)
            {
                case 0: return ! delta.ignore;
                case 1: return delta;
            }
            return null;
        }

        public synchronized void toggle (int row)
        {
            Delta delta = deltas.get (row);
            delta.ignore = ! delta.ignore;
            if (delta.ignore) repo.set   ("ignore", delta.name, "");
            else              repo.clear ("ignore", delta.name);
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

        public synchronized void commit ()
        {
            GitWrapper working = current;

            // Assemble data based on what is currently visible to user.

            String author  = fieldAuthor .getText ();
            String message = fieldMessage.getText ();
            working.setAuthor (author);
            working.message = message;

            List<Delta> files     = new ArrayList<Delta> ();
            List<Delta> newDeltas = new ArrayList<Delta> ();
            for (Delta d : deltas)
            {
                if (d.ignore) newDeltas.add (d);
                else          files    .add (d);
            }
            if (files.isEmpty ()) return;

            // Give immediate visual feedback that commit has started.
            fieldMessage.setText ("");
            List<Delta> oldDeltas = deltas;
            deltas = newDeltas;
            refreshTable ();

            Thread thread = new Thread ()
            {
                public void run ()
                {
                    // Ensure that repo is up to date before committing.
                    // The "Push" button should be disabled if we already know that we are behind the upstream repo.
                    // Here we make a last-second check, just in case our information is out of date.
                    refreshTrack ();
                    if (working.behind > 0)
                    {
                        // Restore state of commit screen to show that commit was aborted.
                        fieldMessage.setText (message);
                        deltas = oldDeltas;
                        refreshTable ();
                        return;
                    }
                    undoMessage.discardAllEdits ();
                    working.message = "";

                    working.commit (files, author, message);
                    if (working != current) return;
                    refreshDiff ();
                    refreshTrackUI (working);
                }
            };
            thread.setDaemon (true);
            thread.start ();
        }
    }

    public class GitColorTextRenderer extends JLabel implements TableCellRenderer
    {
        public Color green = Color.green.darker ();

        public Component getTableCellRendererComponent (JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column)
        {
            setOpaque (true);
            if (isSelected) setBackground (table.getSelectionBackground ());
            else            setBackground (table.getBackground ());

            setFont (table.getFont ());

            Delta delta = (Delta) object;
            if (delta == null)
            {
                setText ("");
            }
            else
            {
                setText (delta.name);
                if      (delta.deleted  ) setForeground (Color.red);
                else if (delta.untracked) setForeground (green);
                else                      setForeground (Color.black);
            }

            return this;
        }
    }
}