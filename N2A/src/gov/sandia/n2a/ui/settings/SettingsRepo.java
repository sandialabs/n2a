/*
Copyright 2018-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MCombo;
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.SafeTextTransferHandler;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.search.NodeModel;
import gov.sandia.n2a.ui.images.ImageUtil;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
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
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteRemoveCommand;
import org.eclipse.jgit.api.RemoteSetUrlCommand;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
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
import org.eclipse.jgit.transport.ChainingCredentialsProvider;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.NetRCCredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.DefaultProxyDataFactory;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;

@SuppressWarnings("serial")
public class SettingsRepo extends JScrollPane implements Settings
{
    public static SettingsRepo instance;  // singleton

    // List of repositories
    protected RepoTable      repoTable;
    protected RepoTableModel repoModel;

    // Details about the selected repository
    protected GitTable       gitTable;
    protected GitTableModel  gitModel;
    protected JLabel         labelStatus;
    protected JButton        buttonRefresh;
    protected JButton        buttonPull;
    protected JButton        buttonPush;
    protected JTextField     fieldAuthor;
    protected UndoManager    undoAuthor;  // specifically for editing text field
    protected JTextArea      fieldMessage;
    protected JScrollPane    paneMessage;
    protected UndoManager    undoMessage;
    protected JLabel         labelProgress = new JLabel ();
    protected PanelDiff      panelDiff;

    public    boolean needRebuild;     // need to re-collate AppData.models and AppData.references
    protected boolean needSave = true; // need to flush repositories to disk for git status
    protected Path    reposDir = Paths.get (AppData.properties.get ("resourceDir")).resolve ("repos");

    protected int                     timeout = 30;  // seconds; for git operations
    protected SshSessionFactory       sessionFactory;
    protected TransportConfigCallback transportConfig;

    public SettingsRepo ()
    {
        instance = this;

        setName ("Repositories");  // Necessary to fulfill Settings interface.
        JPanel panel = new JPanel ();
        setViewportView (panel);
        getVerticalScrollBar ().setUnitIncrement (15);  // About one line of text. Typically, one "click" of the wheel does 3 steps, so about 45px or 3 lines of text.

        repoModel = new RepoTableModel ();
        repoTable = new RepoTable (repoModel);
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
        inputMap.put (KeyStroke.getKeyStroke ("shift UP"),          "moveUp");
        inputMap.put (KeyStroke.getKeyStroke ("shift DOWN"),        "moveDown");
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"),            "add");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl EQUALS"),       "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),            "delete");
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"),        "delete");
        inputMap.put (KeyStroke.getKeyStroke ("SPACE"),             "startEditing");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),             "startEditing");

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
                        "<html><body><p style='width:300px'>Permanently erase all data in repository \"" + name + "\"?</p></body></html>",
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

                    // Clone new repo
                    int row;
                    if (xfer.isDrop ()) row = ((JTable.DropLocation) xfer.getDropLocation ()).getRow ();
                    else                row = repoTable.getSelectedRow ();
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
                if (needSave)  // Indicates that focus previously left this settings tab. Also true when this tab is first exposed.
                {
                    // On first boot, attach "base" to upstream repo.
                    if (AppData.state.getFlag ("Repos", "needUpstream"))
                    {
                        int count = repoModel.getRowCount ();
                        int row = 0;
                        for (; row < count; row++) if (repoModel.getValueAt (row, 4).equals ("base")) break;
                        if (row < count) repoModel.setValueAt ("git@github.com:frothga/n2a-repo-base.git", row, 5);
                        AppData.state.clear ("Repos", "needUpstream");
                    }

                    // Save name of currently-focused document
                    String file = null;
                    int row = gitTable.getSelectedRow ();
                    if (row >= 0) file = gitModel.getValueAt (row, 1).toString ();

                    // Reload the repo
                    gitModel.current = null;
                    row = repoTable.getSelectedRow ();
                    if (row >= 0)
                    {
                        gitModel.setCurrent (row);

                        // Try to find and highlight the previously focused document.
                        int count = gitModel.deltas.size ();
                        for (row = 0; row < count; row++)
                        {
                            if (gitModel.deltas.get (row).name.equals (file))
                            {
                                gitTable.changeSelection (row, 1, false, false);
                                break;
                            }
                        }
                    }
                    else if (repoModel.getRowCount () > 0)  // No repo is selected, but one could be selected.
                    {
                        repoTable.changeSelection (0, 4, false, false);
                        repoTable.requestFocusInWindow ();
                    }
                }

                Component to = e.getComponent ();
                if (to == fieldAuthor  ||  to == fieldMessage)
                {
                    JViewport vp = getViewport ();
                    Rectangle bounds = SwingUtilities.convertRectangle (to, to.getBounds (), vp);
                    vp.scrollRectToVisible (bounds);
                }
            }

            public void focusLost (FocusEvent e)
            {
                Component to = e.getOppositeComponent ();
                if (to == gitTable  ||  to == repoTable  ||  to == fieldAuthor  ||  to == fieldMessage  ||  to == SettingsRepo.this) return;  // Loss is still within current tab, so ignore.
                if (e.isTemporary ()  ||  repoTable.isEditing ()) return;  // The shift to the editing component appears as a loss of focus.

                if (gitModel.current != null)
                {
                    gitModel.current.setAuthor (fieldAuthor .getText ());
                    gitModel.current.message  = fieldMessage.getText ();
                }

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
                if (e.getValueIsAdjusting ()) return;
                int row = repoTable.getSelectedRow ();
                gitModel.setCurrent (row);
            }
        });


        gitModel = new GitTableModel ();
        gitTable = new GitTable (gitModel);
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
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"), "delete");
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

        gitTable.getSelectionModel ().addListSelectionListener (new ListSelectionListener ()
        {
            public void valueChanged (ListSelectionEvent e)
            {
                if (e.getValueIsAdjusting ()) return;
                int row = gitTable.getSelectedRow ();
                if (row >= 0) panelDiff.load (gitModel.deltas.get (row));
                else          panelDiff.clear ();
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
                // Refresh the list of files for this repo, in case they haven't been
                // properly synchronized with the editing views. This gives the user
                // some option to force update other than flipping between repos.
                gitModel.refreshDiff ();  // Starts a separate thread to scan files.

                // Fetch from remote repository and update displayed tracking info.
                // This is not the same as a pull. It only gets change sets, without
                // updating the working directory.
                Thread thread = new Thread ()
                {
                    public void run ()
                    {
                        gitModel.refreshTrack (true);
                    }
                };
                thread.setDaemon (true);
                thread.start ();
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
        buttonPush.setEnabled (false);
        buttonPush.setToolTipText ("Push");
        buttonPush.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                gitModel.commit ();
            }
        });

        labelStatus = new JLabel ();

        fieldAuthor = new JTextField ();
        fieldAuthor.setColumns (30);
        fieldAuthor.addFocusListener (rebuildListener);

        undoAuthor = new UndoManager ();
        fieldAuthor.getDocument ().addUndoableEditListener (undoAuthor);
        inputMap = fieldAuthor.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo");  // For Windows and Linux
        inputMap.put (KeyStroke.getKeyStroke ("meta Z"),          "Undo");  // For Mac
        inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("meta Y"),          "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift meta Z"),    "Redo");
        actionMap = fieldAuthor.getActionMap ();
        actionMap.put ("Undo", new AbstractAction ("Undo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoAuthor.undo ();}
                catch (CannotUndoException e) {}
            }
        });
        actionMap.put ("Redo", new AbstractAction ("Redo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoAuthor.redo();}
                catch (CannotRedoException e) {}
            }
        });

        fieldMessage = new JTextArea ();
        paneMessage = new JScrollPane (fieldMessage);
        fieldMessage.setLineWrap (true);
        fieldMessage.setWrapStyleWord (true);
        fieldMessage.setRows (6);
        fieldMessage.setTabSize (4);
        fieldMessage.addFocusListener (rebuildListener);

        undoMessage = new UndoManager ();
        fieldMessage.getDocument ().addUndoableEditListener (undoMessage);
        inputMap = fieldMessage.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo");
        inputMap.put (KeyStroke.getKeyStroke ("meta Z"),          "Undo");
        inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("meta Y"),          "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift meta Z"),    "Redo");
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

        // JGit configuration
        CredentialsProvider.setDefault (new ChainingCredentialsProvider (new NetRCCredentialsProvider (), new CredentialCache (), new CredentialDialog ()));
        sessionFactory = new SshdSessionFactory (new JGitKeyCache (), new DefaultProxyDataFactory ());
        transportConfig = new TransportConfigCallback ()
        {
            public void configure (Transport t)
            {
                if (t instanceof SshTransport)
                {
                    SshTransport s = (SshTransport) t;
                    s.setSshSessionFactory (sessionFactory);
                }
            }
        };

        panelDiff = new PanelDiff (this);


        Lay.BLtg (panel,
            "N", Lay.BxL (
                Lay.BL ("W", repoPanel),
                Lay.WL ("L",
                        labelStatus,
                        buttonRefresh,
                        buttonPull,
                        buttonPush,
                        Box.createHorizontalStrut (15),
                        labelProgress,
                        "hgap=10,vgap=10"
                ),
                Lay.BL ("W",
                    Lay.BxL ("H",
                        Lay.BL ("N", gitPanel),
                        Box.createHorizontalStrut (5),
                        Lay.BL (
                            "N", Lay.BxL (
                                Lay.FL (Lay.lb ("Author"), fieldAuthor),
                                Box.createVerticalStrut (5),
                                Lay.FL (Lay.lb ("Commit message:")),
                                paneMessage
                            )
                        )
                    )
                ),
                Box.createVerticalStrut (10),
                Lay.BL ("W", panelDiff)
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

    public JPopupMenu createTransferMenu (String folder, String docKey)
    {
        // Determine which repo holds key
        MDir source = (MDir) ((MCombo) AppData.docs.child (folder)).containerFor (docKey);
        String repoName = source.key ();
        String primary = AppData.state.get ("Repos", "primary");
        boolean sourceEditable =  AppData.repos.getBoolean (repoName, "editable")  ||  repoName.equals (primary);

        JPopupMenu result = new JPopupMenu ();

        JMenuItem item = new JMenuItem ("Move to repo");
        item.setEnabled (false);
        result.add (item);
        result.add (new JSeparator ());

        for (MNode repo : repoModel.repos)
        {
            String key = repo.key ();
            boolean editable =  sourceEditable  &&  (repo.getBoolean ("editable")  ||  key.equals (primary));
            boolean selected = key.equals (repoName);

            item = new JRadioButtonMenuItem (key);
            item.setSelected (selected);
            item.setEnabled (editable);
            result.add (item);

            if (editable  &&  ! selected) item.addActionListener (new ActionListener ()
            {
                public void actionPerformed (ActionEvent event)
                {
                    MDir destination = (MDir) getOrCreateContainer (key, folder);
                    rebuild ();  // In case destination is newly created, it needs to be inserted into combo.

                    destination.take (source, docKey);
                    MCombo combo = (MCombo) AppData.docs.child (folder);
                    // rebuild() triggers multiple calls to PanelSearch.search(). One or more of those threads
                    // will likely still be running at this point. A search thread locks docs then locks combo,
                    // while a call to childDeleted() locks combo then lock docs. This creates a deadlock between
                    // the EDT and the search thread. By first acquiring a lock on docs, we force the same order
                    // as the search thread and thus avoid deadlock.
                    synchronized (AppData.docs) {combo.childDeleted (docKey);}  // Not actually deleted. This just forces an update of the children map.
                }
            });
        }
        return result;
    }

    public void status (String message)
    {
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                labelProgress.setText (message);
            }
        });
    }

    public void warning (String message)
    {
        status ("<html><span style=\"color:#C0C000\">" + message + "</span></html>");
    }

    public void error (String message)
    {
        status ("<html><span style=\"color:red\">" + message + "</span></html>");
    }

    public void success (String message)
    {
        status ("<html><span style=\"color:green\">" + message + "</span></html>");
    }

    public MDir getOrCreateContainer (String repoName, String folderName)
    {
        MVolatile containerMap = (MVolatile) AppData.existing.child (folderName);
        if (containerMap == null)
        {
            containerMap = new MVolatile (folderName);
            AppData.existing.link (containerMap);
            needRebuild = true;
        }
        MDir result = (MDir) containerMap.child (repoName);
        if (result == null)
        {
            result = new MDir (repoName, reposDir.resolve (repoName).resolve (folderName));            
            containerMap.link (result);
            needRebuild = true;
        }
        return result;
    }

    /**
        Rebuild the combo directories.
        This is lightweight enough that it can be done on the main UI thread.
    **/
    public void rebuild ()
    {
        if (! needRebuild) return;
        needRebuild = false;
        AppData.buildDocs ();
    }

    public boolean isEmpty (String repoName)
    {
        // Scan "existing" for any non-empty folders in the given repo.
        // Alternately, could scan repo directory on disk, but this allows for unwritten data.
        for (MNode e : AppData.existing)
        {
            MNode container = e.child (repoName);
            if (container != null  &&  container.size () != 0) return false;
        }
        return true;
    }

    public void pull (GitWrapper gitRepo, String key)
    {
        MainFrame.undoManager.discardAllEdits ();  // TODO: purge only edits related to the current repo.
        Thread thread = new Thread ()
        {
            public void run ()
            {
                // TODO: Prevent repo rename while pull is in progress. Simple approach is to add check to TextCellEditor.isCellEditable()
                gitRepo.pull ();

                // Scan all folders in repo and update associated containers
                // Compare with startup code in AppData
                Path repoDir = gitRepo.gitDir.getParent ();
                String repoName = repoDir.getFileName ().toString ();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream (repoDir))
                {
                    for (Path path : stream)
                    {
                        if (! Files.isDirectory (path)) continue;
                        String folderName = path.getFileName ().toString ();
                        if (folderName.startsWith (".")) continue;
                        getOrCreateContainer (repoName, folderName).reload ();  // reload() is redundant in the case where the MDir is newly created.
                    }
                }
                catch (IOException e) {}
                // TODO: purge folders that no longer exist?

                if (isShowing ())  // UI focus is still on settings panel.
                {
                    if (gitRepo == gitModel.current)
                    {
                        gitModel.refreshDiff ();
                        gitModel.refreshTrack (false);
                    }
                }
                else  // UI focus has shifted away from settings panel.
                {
                    // Now we have a problem. The user may be in the middle of editing something whose underlying value has changed.
                    // The cleanest (but still quite dirty) way to handle this is to trash current edits and replace contents.
                    // rebuild() will send events to the UI, which should in turn handle any ongoing edit.
                    rebuild ();
                }
            }
        };
        thread.setDaemon (true);
        thread.start ();
    }

    public class RepoTable extends JTable
    {
        public RepoTable (RepoTableModel repoModel)
        {
            super (repoModel);

            TableColumnModel cols = getColumnModel ();

            TableColumn col = cols.getColumn (3);
            col.setCellRenderer (new ColorRenderer ());

            col = cols.getColumn (4);
            col.setCellRenderer (new ColorTextRenderer ());
            TextCellEditor cellEditor = new TextCellEditor ();
            col.setCellEditor (cellEditor);

            col = cols.getColumn (5);
            col.setCellEditor (cellEditor);

            ((DefaultTableCellRenderer) getTableHeader ().getDefaultRenderer ()).setHorizontalAlignment (JLabel.LEFT);
        }

        public void updateUI ()
        {
            super.updateUI ();

            FontMetrics fm = getFontMetrics (getFont ());
            int em = fm.charWidth ('M');

            setRowHeight (fm.getHeight () + getRowMargin ());

            TableColumnModel cols = getColumnModel ();
            TableColumn col;
            int width;
            for (int c = 0; c < 4; c++)
            {
                col = cols.getColumn (c);
                width = fm.stringWidth (getColumnName (c)) + em;
                col.setMaxWidth (width);
                col.setPreferredWidth (width);
            }

            col = cols.getColumn (4);
            width = fm.stringWidth (getColumnName (4));
            for (MNode r : AppData.repos) width = Math.max (width, fm.stringWidth (r.key ()));
            width = Math.max (width, 15 * em);
            width += em;
            col.setMinWidth (width);
            col.setPreferredWidth (width);

            col = cols.getColumn (5);
            width = fm.stringWidth (getColumnName (5));
            for (GitWrapper w : repoModel.gitRepos)
            {
                String url = w.getURL ();
                if (url.isEmpty ()) continue;
                width = Math.max (width, fm.stringWidth (url));
            }
            width = Math.max (width, 40 * em);
            width += em;
            col.setMinWidth (width);
            col.setPreferredWidth (width);
        }
    }

    public class RepoTableModel extends AbstractTableModel
    {
        public List<MNode>      repos    = new ArrayList<MNode> ();
        public List<GitWrapper> gitRepos = new ArrayList<GitWrapper> ();
        public int              primaryRow;

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
            return 6;
        }

        public String getColumnName (int column)
        {
            switch (column)
            {
                case 0: return "Add";
                case 1: return "Edit";
                case 2: return "View";
                case 3: return "Color";
                case 4: return "Name";
                case 5: return "Git Remote";
            }
            return "";
        }

        public Class<?> getColumnClass (int column)
        {
            if (column < 3) return Boolean.class;  // Coerce table into using default Boolean renderer, which looks reasonably good.
            return super.getColumnClass (column);
        }

        public boolean isCellEditable (int row, int column)
        {
            if (column < 3) return false;
            return true;
        }

        public Object getValueAt (int row, int column)
        {
            MNode repo = repos.get (row);
            switch (column)
            {
                case 0: return row == primaryRow;
                case 1: return repo.get ("editable").equals ("1");
                case 2: return repo.get ("visible") .equals ("1");
                case 3: return getColor (row);
                case 4: return repo.key ();
                case 5: return gitRepos.get (row).getURL ();
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
                        AppData.state.set (newKey, "Repos", "primary");
                        MainFrame.undoManager.discardAllEdits ();
                        needRebuild = true;
                        fireTableCellUpdated (oldRow, 0);  // Primary
                        fireTableCellUpdated (row,    0);
                    }
                    return true;
                case 1:
                    int editable = repo.getInt ("editable");
                    if (editable == 0) editable = 1;
                    else               editable = 0;
                    repo.set (editable, "editable");
                    MainFrame.undoManager.discardAllEdits ();  // TODO: It would be better to only purge edits related to the specific repo.
                    needRebuild = true;
                    fireTableCellUpdated (row, 1);
                    return true;
                case 2:
                    int visible = repo.getInt ("visible");
                    if (visible == 0) visible = 1;
                    else              visible = 0;
                    repo.set (visible, "visible");
                    MainFrame.undoManager.discardAllEdits ();
                    needRebuild = true;
                    fireTableCellUpdated (row, 2);
                    return true;
                case 3:
                    // Show a modal color chooser
                    Color initialColor = getColor (row);
                    Color chosenColor = JColorChooser.showDialog (MainFrame.instance, "", initialColor);
                    if (chosenColor != null)
                    {
                        repo.set ("#" + Integer.toHexString (chosenColor.getRGB () & 0xFFFFFF), "color");
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
                case 4:
                    String newName = MDir.validFilenameFrom (value.toString ());
                    String oldName = key;
                    if (newName.isEmpty ()  ||  newName.equals (oldName)) return;
                    if (AppData.repos.child (newName) != null) return;
                    // Now we have a legitimate name change.

                    gitRepos.get (row).close ();  // Shut down git under oldName
                    Path repoDir = reposDir.resolve (newName);
                    for (MNode e : AppData.existing)
                    {
                        String folderName = e.key ();
                        MDir container = (MDir) e.child (oldName);
                        if (container == null) continue;
                        e.move (oldName, newName);  // MVolatile.move() also changes key of MDir
                        container.set (repoDir.resolve (folderName)); // Flushes write queue, so save thread won't interfere with the move.
                    }
                    AppData.repos.move (oldName, newName);
                    GitWrapper newWrapper = new GitWrapper (repoDir.resolve (".git"));
                    gitRepos.set (row, newWrapper);
                    gitModel.current = newWrapper;  // Presumably current == oldWrapper, so we just replace it without checking.

                    String primary = AppData.state.get ("Repos", "primary");
                    if (oldName.equals (primary)) AppData.state.set (newName, "Repos", "primary");
                    updateOrder ();

                    // No need to rebuild, because all object identities are maintained.
                    return;
                case 5:
                    GitWrapper gitRepo = gitRepos.get (row);
                    String url = value.toString ();
                    gitRepo.setURL (url);
                    if (gitRepo.isNew ())
                    {
                        pull (gitRepo, key);
                    }
                    else
                    {
                        Thread thread = new Thread ()
                        {
                            public void run ()
                            {
                                gitModel.refreshTrack (! url.isEmpty ());
                            }
                        };
                        thread.setDaemon (true);
                        thread.start ();
                    }
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
            AppData.repos.set (1, name, "editable");  // Implicitly creates the repo node.

            repos   .add (row, AppData.repos.child (name));
            gitRepos.add (row, gitRepo);
            updateOrder ();
            fireTableRowsInserted (row, row);
            repoTable.changeSelection (row, 4, false, false);

            // No need to update "existing", because new repo has no folders.
            // Similarly, there is no need to rebuild.
            // However, these structures must be updated when files are added to repo.

            if (URL.isEmpty ()) repoTable.editCellAt (row, 4, null);
            else                pull (gitRepo, name);
        }

        public void delete (int row, int column)
        {
            GitWrapper wrapper = gitRepos.get (row);
            String     name    = repos   .get (row).key ();

            for (MNode e : AppData.existing) e.clear (name);
            gitRepos.remove (row);
            repos   .remove (row);
            updateOrder ();

            gitModel.current = null;  // Keeps us from saving author name to git repo that's about to be deleted.
            fireTableRowsDeleted (row, row);
            if (row >= repos.size ()) row = repos.size () - 1;
            if (row >= 0) repoTable.changeSelection (row, column, false, false);
            needRebuild = true;

            // Do deletion on a separate thread, because it requires walking a potentially large tree.
            Thread DeleteRepoThread = new Thread ()
            {
                public void run ()
                {
                    wrapper.close ();            // Close git repo first.
                    AppData.repos.clear (name);  // Then delete the directory, including .git
                }
            };
            DeleteRepoThread.setDaemon (true);
            DeleteRepoThread.start ();
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
            AppData.state.set (order, "Repos", "order");
            if (repos.size () > 0)
            {
                String newPrimary = repos.get (primaryRow).key ();
                if (! newPrimary.equals (primary))
                {
                    AppData.state.set (newPrimary, "Repos", "primary");
                    MainFrame.undoManager.discardAllEdits ();
                }
            }
            else
            {
                MainFrame.undoManager.discardAllEdits ();
            }
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
            String name = text.toString ();
            setText (name);
            setOpaque (true);
            setFont (table.getFont ());
            setBackground (isSelected ? table.getSelectionBackground () : table.getBackground ());
            setForeground (NodeModel.getColor (name, isSelected ? table.getSelectionForeground () : table.getForeground ()));

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
            inputMap.put (KeyStroke.getKeyStroke ("meta Z"),          "Undo");
            inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
            inputMap.put (KeyStroke.getKeyStroke ("meta Y"),          "Redo");
            inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");
            inputMap.put (KeyStroke.getKeyStroke ("shift meta Z"),    "Redo");
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
            String valueString = "";
            if (value != null) valueString = value.toString ();
            editor.setText (valueString);
            undoCell.discardAllEdits ();  // Discard edits after setting contents, so that the set itself is not undoable (resulting in blank).

            editor.setFont (table.getFont ());
            editor.setForeground (table.getForeground ());
            editor.setBackground (table.getBackground ());

            return editor;
        }
    }

    public class Delta
    {
        public GitWrapper git;
        public String     name;
        public boolean    deleted;
        public boolean    untracked;
        public boolean    ignore;

        // These two members are used only for stashing.
        public MNode add;    // Changed and added nodes, relative to parent commit.
        public MNode remove; // Removed nodes relative to parent commit.

        public Delta (GitWrapper git)
        {
            this.git = git;
        }

        /**
            @return An MDoc attached to the file on disk, or null if the file is missing.
            The returned object is independent of the documents returned by AppData.
            It is meant for temporary use to work with contents on disk. Extra steps must be taken
            to keep AppData synchronized. One such step is to flush AppData upon entering this
            repo settings tab. Another is to notify repos of any changes, so that AppData can be reloaded.
        **/
        public MDoc getDocument ()
        {
            Path baseDir = git.gitDir.getParent ();
            Path path = baseDir.resolve (name);
            if (Files.exists (path)) return new MDoc (path);
            return null;
        }

        public MNode getOriginal ()
        {
            try
            {
                ObjectId commitId = git.gitRepo.resolve (git.head);
                if (commitId == null) return null;
                RevCommit commit = git.gitRepo.parseCommit (commitId);
                RevTree tree = commit.getTree ();
                TreeWalk treeWalk = TreeWalk.forPath (git.gitRepo, name, tree);
                if (treeWalk == null) return null;

                ObjectId objectId = treeWalk.getObjectId (0);
                ObjectLoader loader = git.gitRepo.open (objectId);
                ObjectStream stream = loader.openStream ();
                MNode result = new MVolatile ();
                Schema.readAll (result, new InputStreamReader (stream));
                return result;
            }
            catch (Exception e) {}
            return null;
        }

        public void stash ()
        {
            if (deleted) return;
            MNode doc = getDocument ();
            if (untracked)
            {
                remove = null;
                add = new MVolatile ();
                add.merge (doc);  // Assumes that in untracked state, doc always exists.
            }
            else
            {
                MNode original = getOriginal ();
                add    = new MVolatile ();
                remove = new MVolatile ();
                if (doc      != null) add   .merge (doc);
                if (original != null) remove.merge (original);
                remove.uniqueNodes (add);
                add.uniqueValues (original);
            }
        }

        /**
            Assuming the working directory is clean at parent commit, makes the changes
            necessary to move one file into the state after this delta.
            Caller is responsible to notify MDir of changes. This is for efficiency, so a large number
            of deltas can be applied together, followed by a single update to MDir.
            Caller is responsible to update gitModel.
        **/
        public void apply ()
        {
            Path baseDir = git.gitDir.getParent ();
            Path path = baseDir.resolve (name);
            if (deleted)
            {
                try
                {
                    Files.delete (path);
                }
                catch (IOException e)
                {
                    warning (e.getMessage ());
                }
            }
            else
            {
                // The doc here breaks the rule of object identity. There may be a completely different
                // MDoc open on the same file. However, the caller is expected to update those
                // with contents from disk after we are done.
                MDoc doc = new MDoc (path);  // If the file already exists, then it will be loaded as needed.
                if (remove != null) doc.uniqueNodes (remove);
                if (add    != null) doc.merge       (add);
                doc.save ();
            }
        }

        /**
            Removes the effect of this delta from the working directory.
            Caller is responsible to notify MDir and update gitModel.
        **/
        public void revert ()
        {
            git.checkout (name);
        }

        /**
            Notifies MDir that our associated file has changed on disk.
        **/
        public void notifyDir ()
        {
            String repoName = git.gitDir.getParent ().getFileName ().toString ();
            String[] pieces = name.split ("/");
            MDir dir = getOrCreateContainer (repoName, pieces[0]);
            dir.nodeChanged (pieces[1]);
        }

        public boolean equals (Object o)
        {
            if (! (o instanceof Delta)) return false;
            Delta that = (Delta) o;
            return git == that.git  &&  name.equals (that.name);
        }
    }

    public class GitWrapper implements AutoCloseable
    {
        public Path               gitDir;
        public Repository         gitRepo;
        public Git                git;
        public StoredConfig       config;
        public String             head;
        public RemoteConfig       remote;
        public int                ahead;
        public int                behind;
        public IndexDiff          diff;
        public String             message;
        public Map<String,String> credentials;
        public boolean            authFailed;  // Indicates that last attempt at authentication failed, so prompt user to re-enter password.

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

                // Ensure that repo is configured for EOL handling across platforms.
                // Our goal is to only ever commit LFs to the git repo, but working-tree
                // line endings can be platform-specific.
                Path infoDir = gitDir.resolve ("info");
                Files.createDirectories (infoDir);
                Host.stringToFile ("* text\n", infoDir.resolve ("attributes"));  // And yes, every last file in the repo is text.
            }
            catch (Exception e)
            {
                error ("Failed to open repo: " + e.getMessage ());
            }
        }

        public synchronized void close ()
        {
            try
            {
                // This is just a paranoia measure. config should be saved any time significant changes are made,
                if (config != null) config.save ();
            }
            catch (IOException e)
            {
                warning ("Failed to save config: " + e.getMessage ());
            }
            if (git     != null) git    .close ();
            if (gitRepo != null) gitRepo.close ();

            // Attempt to flush lingering file handles left by NIO on Windows.
            gitRepo = null;
            git     = null;
            config  = null;
            remote  = null;
            diff    = null;
            if (gov.sandia.n2a.host.Host.isWindows ()) System.gc ();
        }

        public String getURL ()
        {
            if (remote == null) return "";
            List<URIish> URIs = remote.getURIs ();
            if (URIs.size () == 0) return "";
            return URIs.get (0).toString ();
        }

        public synchronized void setURL (String value)
        {
            URIish valueURI = null;
            try
            {
                valueURI = new URIish (value);
            }
            catch (URISyntaxException e)
            {
                if (! value.isEmpty ())
                {
                    error (e.getMessage ());
                    return;
                }
                // Otherwise, the user wants to detach from remote.
            }

            String remoteName = remote.getName ();  // If there are no remotes yet, then "remote" is just a placeholder that happens to hold the desired name.
            if (gitRepo.getRemoteNames ().isEmpty ())
            {
                if (valueURI == null) return;  // Nothing to do.
                try
                {
                    remote = git.remoteAdd ().setName (remoteName).setUri (valueURI).call ();
                    success ("Changed URI");
                }
                catch (Exception e)
                {
                    error (e.getMessage ());
                }
                return;  // Because we've already set the remote URI, which is the goal of this function.
            }
            else if (valueURI == null)  // User wants to detach from remote.
            {
                RemoteRemoveCommand command = git.remoteRemove ();
                command.setRemoteName (remoteName);
                try
                {
                    command.call ();
                    success ("Removed remote");
                    remote = new RemoteConfig (config, remoteName);
                }
                catch (Exception e)
                {
                    error (e.getMessage ());
                }
                return;
            }

            // Change the URI of an existing remote.
            RemoteSetUrlCommand command = git.remoteSetUrl ();
            command.setRemoteName (remoteName);
            command.setRemoteUri (valueURI);
            try
            {
                remote = command.call ();
                success ("Changed URI");
            }
            catch (Exception e)
            {
                error (e.getMessage ());
            }
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

            String name  = "";
            String email = "";
            String[] pieces = author.split ("<", 2);
            if (pieces.length > 0) name  = pieces[0].trim ();
            if (pieces.length > 1) email = pieces[1].split (">", -1)[0].trim ();

            config.setString ("user", null, "name",  name);
            config.setString ("user", null, "email", email);
            try
            {
                config.save ();
                success ("Changed author");
            }
            catch (IOException e)
            {
                error (e.getMessage ());
            }
        }

        public synchronized void clearDiff ()
        {
            diff = null;
        }

        public Delta addDelta (String name, TreeMap<String,Delta> map)
        {
            if (! name.contains ("/")) return new Delta (this);  // A throw away object, which won't get included in map and thus not in final result.

            Delta d = map.get (name);
            if (d == null)
            {
                d = new Delta (this);
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
                catch (IOException e)
                {
                    warning (e.getMessage ());
                }
                catch (Exception e)
                {
                    String message = e.getMessage ();
                    if (message == null  ||  message.isEmpty ()) message = e.toString ();  // Get class name
                    error (message);
                }
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

        public Path createZip (Path baseDir, List<Delta> stash) throws Exception
        {
            Path zipFile = baseDir.resolve ("stash.zip");
            if (Files.exists (zipFile))
            {
                throw new Exception ("Backup file already exists, indicating that a previous pull failed.  " + zipFile);
            }

            // Copy files to stash
            try (OutputStream ostream = Files.newOutputStream (zipFile);
                 ZipOutputStream zip = new ZipOutputStream (ostream))
            {
                for (Delta d : stash)
                {
                    if (d.deleted) continue;  // Can't save a non-existent file.

                    Path inFile = baseDir.resolve (d.name);
                    InputStream istream = Files.newInputStream (inFile);
                    zip.putNextEntry (new ZipEntry (d.name));

                    int length;
                    byte[] bytes = new byte[4096];
                    while ((length = istream.read (bytes)) >= 0) zip.write (bytes, 0, length);

                    istream.close ();
                    zip.closeEntry ();
                }
            }
            catch (IOException e)  // The purpose of this try-catch block is to ensure that streams get closed.
            {
                throw new Exception ("Unable to finish stash file.");  // But we pass the exception on in any case.
            }

            return zipFile;
        }

        public void checkAuthFail (Throwable e)
        {
            // This is only a heurisitic.
            // It would be better if there were a specific exception class for authorization failure.
            String message = e.getMessage ();
            if (message == null) return;
            if (message.toLowerCase ().contains ("auth"))
            {
                authFailed = true;
            }
        }

        public void getRemoteTracking (boolean showStatus)
        {
            ahead  = 0;
            behind = 0;
            String URL = getURL ();
            if (URL.isEmpty ())
            {
                if (showStatus) status ("");
                return;  // Nothing to do
            }

            if (showStatus) status ("Fetching " + URL);
            FetchCommand fetch = git.fetch ();
            fetch.setTimeout (timeout);
            fetch.setTransportConfigCallback (transportConfig);
            try
            {
                fetch.call ();
                if (showStatus) success ("Fetched");
            }
            catch (Exception e)
            {
                warning (e.getMessage ());
                checkAuthFail (e);
            }

            try
            {
                BranchTrackingStatus track = BranchTrackingStatus.of (gitRepo, head);
                if (track != null)
                {
                    ahead  = track.getAheadCount ();
                    behind = track.getBehindCount ();
                }
            }
            catch (IOException e)
            {
                warning (e.getMessage ());
            }
        }

        // Determine if this is a newly-create repo.
        public boolean isNew ()
        {
            try
            {
                return gitRepo.resolve (head) == null;
            }
            catch (Exception e)
            {
                return true;
            }
        }

        // Caller is responsible to notify MDir of changes.
        public synchronized void pull ()
        {
            // Stash changed and untracked files so they won't get in the way of pull.
            status ("Stashing changes");
            List<Delta> stash = getDeltas ();
            for (Delta d : stash) d.stash ();

            Path baseDir = gitDir.getParent ();
            Path zipFile = null;
            try
            {
                if (! stash.isEmpty ()) zipFile = createZip (baseDir, stash);
            }
            catch (Exception e)
            {
                error (e.getMessage ());
                return;  // It's crucial that the backup fully succeed before we start deleting files.
            }

            for (Delta d : stash)
            {
                if (d.deleted) continue;
                if (d.untracked)
                {
                    try {Files.delete (baseDir.resolve (d.name));}
                    catch (IOException e) {}
                }
                else
                {
                    d.revert ();
                }
            }

            // User data could be permanently lost if we don't get past this section and restore the internal stash from memory back to disk.
            // Therefore we use the strongest form of catch().
            status ("Pulling " + getURL ());
            boolean succeeded = true;
            try
            {
                boolean newRepo = isNew ();

                PullCommand pull = git.pull ();
                pull.setRebase (! newRepo);  // TODO: not sure if git-diff is safe for N2A files. May need to implement our own rebase, which would be a version of the stash code which reaches all the way back to the common ancestor.
                pull.setTimeout (timeout);
                pull.setTransportConfigCallback (transportConfig);
                pull.call ();

                // Update branch to track remote.
                if (newRepo)
                {
                    CreateBranchCommand create = git.branchCreate ();
                    create.setUpstreamMode (SetupUpstreamMode.SET_UPSTREAM);
                    create.setName (head);
                    create.setForce (true);  // Because head already exists.
                    create.setStartPoint (remote.getName () + "/" + head);
                    create.call ();
                }
            }
            catch (Throwable e)
            {
                succeeded = false;
                error (e.getMessage ());
                checkAuthFail (e);
            }

            // Restore changes
            if (! stash.isEmpty ())
            {
                if (succeeded) status ("Applying stash");
                for (Delta d : stash) d.apply ();

                // Verify that stash applied correctly. This is only a heuristic.
                boolean shouldDelete = true;
                for (Delta d : stash)
                {
                    if (d.deleted) continue;  // We only care about files that should exist but don't ...
                    if (! Files.exists (baseDir.resolve (d.name)))
                    {
                        shouldDelete = false;
                        break;
                    }
                }

                // If backup is no longer needed, delete it.
                try
                {
                    if (shouldDelete) Files.delete (zipFile);
                }
                catch (IOException e)
                {
                    if (succeeded) warning ("Failed to remove stash.zip: " + e.getMessage ());
                    succeeded = false;
                }
            }
            clearDiff ();
            if (succeeded) success ("Pulled");
        }

        public synchronized void checkout (String name)
        {
            clearDiff ();  // force update
            try
            {
                status ("Reverting " + name);
                git.checkout ().addPath (name).call ();
                success ("Reverted " + name);
            }
            catch (Exception e)
            {
                error (e.getMessage ());
            }
        }

        public synchronized void commit (List<Delta> deltas, String author, String message)
        {
            // Commit current changes
            status ("Updating index");
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
            catch (Exception e)
            {
                warning (e.getMessage ());
            }

            if (add != null  ||  rm != null)
            {
                status ("Commiting");
                clearDiff ();

                String name  = "";
                String email = "";
                String[] pieces = author.split ("<", 2);
                if (pieces.length > 0) name  = pieces[0].trim ();
                if (pieces.length > 1) email = pieces[1].split (">", -1)[0].trim ();

                CommitCommand commit = git.commit ();
                commit.setAuthor (name, email);
                commit.setMessage (message);
                try
                {
                    commit.call ();
                    success ("Committed");
                }
                catch (Exception e)
                {
                    warning (e.getMessage ());
                }
            }

            // Push changes upstream
            String url = getURL ();
            if (url.isEmpty ()) return;  // No upstream to push. OK to stop after local commit.
            status ("Pushing to " + url);
            PushCommand push = git.push ();
            push.setTimeout (timeout);
            push.setTransportConfigCallback (transportConfig);
            try
            {
                push.call ();
                success ("Pushed");
            }
            catch (Exception e)
            {
                error (e.getMessage ());
                checkAuthFail (e);
                return;
            }

            // If this is a new repo, set head to track remote origin.
            BranchConfig branch = new BranchConfig (config, head);
            if (branch.getRemote () == null)  // head is not tracking remote
            {
                status ("Set head to track remote");
                CreateBranchCommand create = git.branchCreate ();
                create.setUpstreamMode (SetupUpstreamMode.SET_UPSTREAM);
                create.setName (head);
                create.setForce (true);  // Because head already exists.
                create.setStartPoint (remote.getName () + "/" + head);
                try
                {
                    create.call ();
                    success ("Pushed. Head is now tracking remote.");
                }
                catch (Exception e)
                {
                    warning (e.getMessage ());
                }
            }
        }
    }

    public class GitTable extends JTable
    {
        public GitTable (GitTableModel gitModel)
        {
            super (gitModel);

            TableColumnModel cols = getColumnModel ();
            TableColumn col = cols.getColumn (1);
            col.setCellRenderer (new GitColorTextRenderer ());

            ((DefaultTableCellRenderer) getTableHeader ().getDefaultRenderer ()).setHorizontalAlignment (JLabel.LEFT);
        }

        public void updateUI ()
        {
            super.updateUI ();

            FontMetrics fm = getFontMetrics (getFont ());
            int em = fm.charWidth ('M');

            setRowHeight (fm.getHeight () + getRowMargin ());

            TableColumnModel cols = getColumnModel ();
            TableColumn col = cols.getColumn (0);
            int width = fm.stringWidth (getColumnName (0)) + em;
            col.setMaxWidth (width);
            col.setPreferredWidth (width);

            col = cols.getColumn (1);
            width = fm.stringWidth (getColumnName (1));
            width = Math.max (width, 40 * em);
            width += em;
            col.setMinWidth (width);
        }

        public void scrollSelectionToVisible ()
        {
            int row = gitTable.getSelectedRow ();
            if (row < 0) return;
            int column = gitTable.getSelectedColumn ();
            if (column < 0) column = 1;
            scrollRectToVisible (getCellRect (row, column, true));
        }
    }

    public class GitTableModel extends AbstractTableModel
    {
        public GitWrapper  current;
        public MNode       repo;
        public List<Delta> deltas = new ArrayList<Delta> ();

        public synchronized void setCurrent (int repoIndex)
        {
            if (current != null)
            {
                current.setAuthor (fieldAuthor .getText ());
                current.message  = fieldMessage.getText ();
            }

            panelDiff.clear ();
            if (repoIndex < 0  ||  repoIndex >= repoModel.gitRepos.size ())
            {
                current = null;
                repo = null;
                deltas.clear ();
                fieldAuthor .setText ("");
                fieldMessage.setText ("");
                undoAuthor .discardAllEdits ();
                undoMessage.discardAllEdits ();
                return;
            }

            GitWrapper gitRepo = repoModel.gitRepos.get (repoIndex);
            if (gitRepo == current) return;
            current = gitRepo;
            repo = repoModel.repos.get (repoIndex);

            fieldAuthor .setText (current.getAuthor ());
            fieldMessage.setText (current.message);
            undoAuthor .discardAllEdits ();
            undoMessage.discardAllEdits ();
            status ("");  // Clear the git status line, since it probably applies to a different repo.
            refreshDiff ();
            refreshTrackUI ();  // Only display current knowledge of tracking status. Does not do fetch.
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

        public void refreshTrack (boolean showStatus)
        {
            GitWrapper working = current;
            working.getRemoteTracking (showStatus);
            if (working != current) return;
            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    refreshTrackUI ();
                }
            });
        }

        public void refreshTrackUI ()
        {
            buttonPush.setEnabled (current.behind == 0);
            String status = "";
            if (current.ahead != 0) status = "Ahead " + current.ahead;
            if (current.behind != 0)
            {
                if (! status.isEmpty ()) status += ", ";
                status += "Behind " + current.behind;
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
            if (delta.ignore) repo.set   ("", "ignore", delta.name);
            else              repo.clear (    "ignore", delta.name);
            fireTableCellUpdated (row, 0);
        }

        public synchronized void revert (int row)
        {
            if (row < 0  ||  row >= deltas.size ()) return;
            Delta delta = deltas.get (row);
            if (delta.untracked) return;
            MainFrame.undoManager.apply (new RevertDelta (delta));
        }

        public int indexOf (String name)
        {
            int count = deltas.size ();
            for (int i = 0; i < count; i++)
            {
                Delta d = deltas.get (i);
                if (d.name.equals (name)) return i;
            }
            return -1;
        }

        public Delta deltaFor (String name)
        {
            for (Delta d : deltas)
            {
                if (d.name.equals (name)) return d;
            }
            return null;
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

            // Give immediate visual feedback that commit has started.
            if (! files.isEmpty ())
            {
                fieldMessage.getDocument ().removeUndoableEditListener (undoMessage);  // Ignore the next setText().
                fieldMessage.setText ("");
                fieldMessage.getDocument ().addUndoableEditListener (undoMessage);  // Then start listening again.
            }
            List<Delta> oldDeltas = deltas;
            deltas = newDeltas;
            refreshTable ();

            MainFrame.undoManager.discardAllEdits ();  // TODO: purge only edits related to the current repo.

            Thread thread = new Thread ()
            {
                public void run ()
                {
                    // Ensure that repo is up to date before committing.
                    // The "Push" button should be disabled if we already know that we are behind the upstream repo.
                    // Here we make a last-second check, just in case our information is out of date.
                    refreshTrack (false);
                    if (working.behind > 0)
                    {
                        // Restore state of commit screen to show that commit was aborted.
                        synchronized (GitTableModel.this)
                        {
                            if (working == current) deltas = oldDeltas;
                        }
                        warning ("Please do a pull to synchronize with upstream. Then try push again.");
                        EventQueue.invokeLater (new Runnable ()
                        {
                            public void run ()
                            {
                                fieldMessage.getDocument ().removeUndoableEditListener (undoMessage);
                                fieldMessage.setText (current.message);
                                fieldMessage.getDocument ().addUndoableEditListener (undoMessage);
                                refreshTable ();
                            }
                        });
                        return;
                    }
                    if (! files.isEmpty ())
                    {
                        undoMessage.discardAllEdits ();
                        working.message = "";
                    }

                    working.commit (files, author, message);  // Whether or not there are files to commit, we want to call this to at least do a push on any commits that are ahead of upstream.
                    if (working != current) return;
                    Thread thread = new Thread ()
                    {
                        public void run ()
                        {
                            refreshTrack (false);
                        }
                    };
                    thread.setDaemon (true);
                    thread.start ();
                    refreshDiff ();
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

    public class RevertDelta extends Undoable
    {
        protected Delta delta;
        protected int   index;  // where to insert among siblings

        public RevertDelta (Delta delta)
        {
            this.delta = delta;
            delta.stash ();
            // Set index when we actually remove the delta from the table.
        }

        public void restore ()
        {
            // Select repository
            int row = repoModel.gitRepos.indexOf (delta.git);
            if (row < 0) throw new CannotUndoException ();
            if (repoTable.getSelectedRow () != row) repoTable.changeSelection (row, 3, false, false);
        }

        public void undo ()
        {
            super.undo ();
            restore ();

            delta.apply ();
            delta.notifyDir ();

            index = Math.min (index, gitModel.deltas.size ());
            gitModel.deltas.add (index, delta);
            gitModel.refreshTable ();
            gitTable.scrollSelectionToVisible ();
        }

        public void redo ()
        {
            super.redo ();
            restore ();

            delta.revert ();
            delta.notifyDir ();

            index = gitModel.indexOf (delta.name);
            gitModel.deltas.remove (index);
            gitModel.refreshTable ();
            gitTable.scrollSelectionToVisible ();
        }
    }

    public class CredentialDialog extends CredentialsProvider
    {
        Map<String,String> legalNotices = new HashMap<String,String> ();

        public boolean isInteractive ()
        {
            return true;
        }

        public boolean supports (CredentialItem... items)
        {
            for (CredentialItem i : items)
            {
                if (i instanceof CredentialItem.StringType)           continue;
                if (i instanceof CredentialItem.CharArrayType)        continue;
                if (i instanceof CredentialItem.YesNoType)            continue;
                if (i instanceof CredentialItem.InformationalMessage) continue;
                return false;
            }
            return true;
        }

        public boolean get (URIish uri, CredentialItem... items) throws UnsupportedCredentialItem
        {
            if (items.length == 0) return true;

            // Retrieve git wrapper associated with the URI, so we can remember user responses
            String title = uri.toString ();
            GitWrapper git = null;
            MNode mrepo = null;
            for (int i = 0; i < repoModel.gitRepos.size (); i++)
            {
                GitWrapper g = repoModel.gitRepos.get (i);
                String u = g.getURL ();
                if (u.equals (title))
                {
                    git = g;
                    mrepo = repoModel.repos.get (i);
                    if (git.credentials == null)
                    {
                        git.credentials = new HashMap<String,String> ();
                        MNode mcred = mrepo.child ("credentials");
                        if (mcred != null) for (MNode c : mcred) git.credentials.put (c.key (), c.get ());
                    }
                    break;
                }
            }

            // Simple cases
            if (items.length == 1)
            {
                CredentialItem item = items[0];
                String prompt = item.getPromptText ();

                if (item instanceof CredentialItem.InformationalMessage)
                {
                    // Only show legal messages once.
                    if (prompt.toLowerCase ().contains ("legal notice"))
                    {
                        String notice = legalNotices.get (title);
                        if (notice != null  &&  prompt.equals (notice)) return true;
                        legalNotices.put (title, prompt);
                    }

                    JOptionPane.showMessageDialog (MainFrame.instance, prompt, title, JOptionPane.INFORMATION_MESSAGE);
                    return true;
                }
                if (item instanceof CredentialItem.YesNoType)
                {
                    CredentialItem.YesNoType yn = (CredentialItem.YesNoType) item;
                    int result = JOptionPane.showConfirmDialog (MainFrame.instance, prompt, title, JOptionPane.YES_NO_OPTION);
                    if (result == JOptionPane.YES_OPTION  ||  result == JOptionPane.NO_OPTION)
                    {
                        boolean value =  result == JOptionPane.YES_OPTION;
                        yn.setValue (value);
                        if (git != null)
                        {
                            git.credentials.put (prompt, value ? "1" : "0");
                            mrepo.set (value, "credentials", prompt);
                        }
                        return true;
                    }
                    return false;
                }
            }

            // Complex case
            //   Construct custom dialog
            JPanel panel = new JPanel (new GridBagLayout ());
            GridBagConstraints gbc = new GridBagConstraints
            (
                0, 0, 1, 1, 1, 1,
                GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                new Insets (0, 0, 0, 0),
                0, 0
            );
            JTextField fields[] = new JTextField[items.length];
            for (int i = 0; i < items.length; i++)
            {
                CredentialItem item = items[i];
                String prompt = item.getPromptText ();

                if (item instanceof CredentialItem.StringType  ||  item instanceof CredentialItem.CharArrayType)
                {
                    gbc.gridx     = 0;
                    gbc.fill      = GridBagConstraints.NONE;
                    gbc.gridwidth = GridBagConstraints.RELATIVE;
                    panel.add (new JLabel (prompt), gbc);

                    gbc.gridx     = 1;
                    gbc.fill      = GridBagConstraints.HORIZONTAL;
                    gbc.gridwidth = GridBagConstraints.RELATIVE;
                    String lastValue = null;
                    if (git != null) lastValue = git.credentials.get (prompt);
                    if (lastValue == null) lastValue = "";
                    if (item.isValueSecure ()) fields[i] = new JPasswordField (lastValue, 20);
                    else                       fields[i] = new JTextField     (lastValue, 20);
                    panel.add (fields[i], gbc);
                }
                else if (item instanceof CredentialItem.InformationalMessage)
                {
                    gbc.gridx     = 0;
                    gbc.fill      = GridBagConstraints.NONE;
                    gbc.gridwidth = GridBagConstraints.REMAINDER;
                    panel.add (new JLabel (item.getPromptText ()), gbc);
                }
                else
                {
                    throw new UnsupportedCredentialItem (uri, prompt);
                }

                gbc.gridy++;
            }

            //   Show dialog
            JOptionPane dialog = new JOptionPane (panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION)
            {
                public void selectInitialValue ()
                {
                    // Select the first blank text field
                    for (JTextField f : fields)
                    {
                        if (f == null) continue;
                        String value = "";
                        if (f instanceof JPasswordField) value = new String (((JPasswordField) f).getPassword ());
                        else                             value = f.getText ();
                        if (value.isEmpty ())
                        {
                            f.requestFocusInWindow ();
                            break;
                        }
                    }
                }
            };
            dialog.createDialog (MainFrame.instance, title).setVisible (true);
            Object result = dialog.getValue ();
            if (result == null  ||  (Integer) result != JOptionPane.OK_OPTION) return false;

            //   Apply results
            for (int i = 0; i < items.length; i++)
            {
                JTextField f = fields[i];
                if (f == null) continue;
                CredentialItem item = items[i];

                String value = "";
                if (f instanceof JPasswordField) value = new String (((JPasswordField) f).getPassword ());
                else                             value = f.getText ();

                if (item instanceof CredentialItem.StringType)
                {
                    CredentialItem.StringType st = (CredentialItem.StringType) item;
                    st.setValue (value);
                }
                else if (item instanceof CredentialItem.CharArrayType)
                {
                    CredentialItem.CharArrayType cat = (CredentialItem.CharArrayType) item;
                    cat.setValueNoCopy (value.toCharArray ());
                }

                if (git == null) continue;
                String prompt = item.getPromptText ();
                git.credentials.put (prompt, value);
                if (! item.isValueSecure ()) mrepo.set (value, "credentials", prompt);
            }
            return true;
        }
    }

    public class CredentialCache extends CredentialsProvider
    {
        public boolean isInteractive ()
        {
            return false;
        }

        public boolean supports (CredentialItem... items)
        {
            for (CredentialItem i : items)
            {
                if (i instanceof CredentialItem.StringType)    continue;
                if (i instanceof CredentialItem.CharArrayType) continue;
                if (i instanceof CredentialItem.YesNoType)     continue;
                return false;
            }
            return true;
        }

        public boolean get (URIish uri, CredentialItem... items) throws UnsupportedCredentialItem
        {
            if (items.length == 0) return true;

            // Retrieve git wrapper associated with the URI, so we can remember user responses
            String title = uri.toString ();
            GitWrapper git = null;
            MNode mrepo = null;
            for (int i = 0; i < repoModel.gitRepos.size (); i++)
            {
                GitWrapper g = repoModel.gitRepos.get (i);
                String u = g.getURL ();
                if (u.equals (title))
                {
                    git = g;
                    mrepo = repoModel.repos.get (i);
                    if (git.credentials == null)
                    {
                        git.credentials = new HashMap<String,String> ();
                        MNode mcred = mrepo.child ("credentials");
                        if (mcred != null) for (MNode c : mcred) git.credentials.put (c.key (), c.get ());
                    }
                    break;
                }
            }
            if (git == null) return false;
            if (git.authFailed)
            {
                git.authFailed = false;
                return false;
            }

            for (int i = 0; i < items.length; i++)
            {
                CredentialItem item = items[i];
                String prompt = item.getPromptText ();
                String value = git.credentials.get (prompt);
                if (value == null) return false;

                if (item instanceof CredentialItem.StringType)
                {
                    CredentialItem.StringType st = (CredentialItem.StringType) item;
                    st.setValue (value);
                }
                else if (item instanceof CredentialItem.CharArrayType)
                {
                    CredentialItem.CharArrayType cat = (CredentialItem.CharArrayType) item;
                    cat.setValueNoCopy (value.toCharArray ());
                }
                else if (item instanceof CredentialItem.YesNoType)
                {
                    CredentialItem.YesNoType yn = (CredentialItem.YesNoType) item;
                    yn.setValue (value.equals ("1"));
                }
                else
                {
                    throw new UnsupportedCredentialItem (uri, prompt);
                }
            }
            return true;
        }
    }
}