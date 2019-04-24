/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.EventQueue;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSplitPane;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileFilter;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPersistent;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Exporter;
import gov.sandia.n2a.plugins.extpoints.Importer;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.MainTabbedPane;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.PanelRun;

@SuppressWarnings("serial")
public class PanelEquations extends JPanel
{
    public MNode    record;
    public NodePart root;
    public boolean  locked;

    public    PanelEquationTree  panelEquationTree;
    public    PanelEquationGraph panelEquationGraph;
    protected JSplitPane         splitEquation;
    protected boolean            firstResize;

    // Controls
    protected JButton buttonAddModel;
    protected JButton buttonAddPart;
    protected JButton buttonAddVariable;
    protected JButton buttonAddEquation;
    protected JButton buttonAddAnnotation;
    protected JButton buttonAddReference;
    protected JButton buttonFilter;
    protected JButton buttonRun;
    protected JButton buttonExport;
    protected JButton buttonImport;

    protected JPopupMenu menuPopup;
    protected JPopupMenu menuFilter;
    protected long       menuFilterCanceledAt = 0;

    protected static ImageIcon iconFilterRevoked = ImageUtil.getImage ("filterRevoked.png");
    protected static ImageIcon iconFilterAll     = ImageUtil.getImage ("filter.png");
    protected static ImageIcon iconFilterParam   = ImageUtil.getImage ("filterParam.png");
    protected static ImageIcon iconFilterLocal   = ImageUtil.getImage ("filterLocal.png");

    protected int jobCount = 0;  // for launching jobs

    public PanelEquations ()
    {
        panelEquationGraph = new PanelEquationGraph (this);
        panelEquationTree  = new PanelEquationTree (this);

        splitEquation = new JSplitPane (JSplitPane.VERTICAL_SPLIT, panelEquationGraph, panelEquationTree);
        splitEquation.setOneTouchExpandable(true);

        String pos = AppData.state.get ("PanelModel", "dividerEquation");
        if (pos.isEmpty ())
        {
            firstResize = true;
            addComponentListener (new ComponentAdapter ()
            {
                @Override
                public void componentResized (ComponentEvent e)
                {
                    if (firstResize)
                    {
                        splitEquation.setDividerLocation (0.5);
                        firstResize = false;
                    }
                }
            });
        }
        else
        {
            splitEquation.setDividerLocation (Integer.valueOf (pos));
        }
        splitEquation.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (o instanceof Integer) AppData.state.set (o, "PanelModel", "dividerEquation");
            }
        });

        buttonAddModel = new JButton (ImageUtil.getImage ("explore.gif"));
        buttonAddModel.setMargin (new Insets (2, 2, 2, 2));
        buttonAddModel.setFocusable (false);
        buttonAddModel.setToolTipText ("New Model");
        buttonAddModel.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                panelEquationTree.tree.stopEditing ();
                PanelModel.instance.undoManager.add (new AddDoc ());
            }
        });

        buttonAddPart = new JButton (ImageUtil.getImage ("comp.gif"));
        buttonAddPart.setMargin (new Insets (2, 2, 2, 2));
        buttonAddPart.setFocusable (false);
        buttonAddPart.setToolTipText ("Add Part");
        buttonAddPart.setActionCommand ("Part");
        buttonAddPart.addActionListener (listenerAdd);

        buttonAddVariable = new JButton (ImageUtil.getImage ("delta.png"));
        buttonAddVariable.setMargin (new Insets (2, 2, 2, 2));
        buttonAddVariable.setFocusable (false);
        buttonAddVariable.setToolTipText ("Add Variable");
        buttonAddVariable.setActionCommand ("Variable");
        buttonAddVariable.addActionListener (listenerAdd);

        buttonAddEquation = new JButton (ImageUtil.getImage ("assign.png"));
        buttonAddEquation.setMargin (new Insets (2, 2, 2, 2));
        buttonAddEquation.setFocusable (false);
        buttonAddEquation.setToolTipText ("Add Equation");
        buttonAddEquation.setActionCommand ("Equation");
        buttonAddEquation.addActionListener (listenerAdd);

        buttonAddAnnotation = new JButton (ImageUtil.getImage ("edit.gif"));
        buttonAddAnnotation.setMargin (new Insets (2, 2, 2, 2));
        buttonAddAnnotation.setFocusable (false);
        buttonAddAnnotation.setToolTipText ("Add Annotation");
        buttonAddAnnotation.setActionCommand ("Annotation");
        buttonAddAnnotation.addActionListener (listenerAdd);

        buttonAddReference = new JButton (ImageUtil.getImage ("book.gif"));
        buttonAddReference.setMargin (new Insets (2, 2, 2, 2));
        buttonAddReference.setFocusable (false);
        buttonAddReference.setToolTipText ("Add Reference");
        buttonAddReference.setActionCommand ("Reference");
        buttonAddReference.addActionListener (listenerAdd);

        buttonRun = new JButton (ImageUtil.getImage ("run.gif"));
        buttonRun.setMargin (new Insets (2, 2, 2, 2));
        buttonRun.setFocusable (false);
        buttonRun.setToolTipText ("Run");
        buttonRun.addActionListener (listenerRun);

        buttonExport = new JButton (ImageUtil.getImage ("export.gif"));
        buttonExport.setMargin (new Insets (2, 2, 2, 2));
        buttonExport.setFocusable (false);
        buttonExport.setToolTipText ("Export");
        buttonExport.addActionListener (listenerExport);

        buttonImport = new JButton (ImageUtil.getImage ("import.gif"));
        buttonImport.setMargin (new Insets (2, 2, 2, 2));
        buttonImport.setFocusable (false);
        buttonImport.setToolTipText ("Import");
        buttonImport.addActionListener (listenerImport);

        buttonFilter = new JButton (ImageUtil.getImage ("filter.png"));
        buttonFilter.setMargin (new Insets (2, 2, 2, 2));
        buttonFilter.setFocusable (false);
        buttonFilter.setToolTipText ("Filter Equations");
        buttonFilter.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (System.currentTimeMillis () - menuFilterCanceledAt > 500)  // A really ugly way to prevent the button from re-showing the menu if it was canceled by clicking the button.
                {
                    menuFilter.show (buttonFilter, 0, buttonFilter.getHeight ());
                }
            }
        });

        Lay.BLtg (this,
            "N", Lay.WL ("L",
                buttonAddModel,
                Box.createHorizontalStrut (15),
                buttonAddPart,
                buttonAddVariable,
                buttonAddEquation,
                buttonAddAnnotation,
                buttonAddReference,
                Box.createHorizontalStrut (15),
                buttonFilter,
                Box.createHorizontalStrut (15),
                buttonRun,
                Box.createHorizontalStrut (15),
                buttonExport,
                buttonImport,
                "hgap=5,vgap=1"
            ),
            "C", splitEquation
        );

        // Context Menu
        JMenuItem itemAddPart = new JMenuItem ("Add Part", ImageUtil.getImage ("comp.gif"));
        itemAddPart.setActionCommand ("Part");
        itemAddPart.addActionListener (listenerAdd);

        JMenuItem itemAddVariable = new JMenuItem ("Add Variable", ImageUtil.getImage ("delta.png"));
        itemAddVariable.setActionCommand ("Variable");
        itemAddVariable.addActionListener (listenerAdd);

        JMenuItem itemAddEquation = new JMenuItem ("Add Equation", ImageUtil.getImage ("equation.png"));
        itemAddEquation.setActionCommand ("Equation");
        itemAddEquation.addActionListener (listenerAdd);

        JMenuItem itemAddAnnotation = new JMenuItem ("Add Annotation", ImageUtil.getImage ("edit.gif"));
        itemAddAnnotation.setActionCommand ("Annotation");
        itemAddAnnotation.addActionListener (listenerAdd);

        JMenuItem itemAddReference = new JMenuItem ("Add Reference", ImageUtil.getImage ("book.gif"));
        itemAddReference.setActionCommand ("Reference");
        itemAddReference.addActionListener (listenerAdd);

        JMenuItem itemDelete = new JMenuItem ("Delete", ImageUtil.getImage ("remove.gif"));
        itemDelete.addActionListener (listenerDelete);

        menuPopup = new JPopupMenu ();
        menuPopup.add (itemAddPart);
        menuPopup.add (itemAddVariable);
        menuPopup.add (itemAddEquation);
        menuPopup.add (itemAddAnnotation);
        menuPopup.add (itemAddReference);
        menuPopup.addSeparator ();
        menuPopup.add (itemDelete);

        // Filter menu

        JRadioButtonMenuItem itemFilterRevoked = new JRadioButtonMenuItem ("Revoked");
        itemFilterRevoked.addActionListener (listenerFilter);

        JRadioButtonMenuItem itemFilterAll     = new JRadioButtonMenuItem ("All");
        itemFilterAll    .addActionListener (listenerFilter);

        JRadioButtonMenuItem itemFilterPublic  = new JRadioButtonMenuItem ("Parameters");
        itemFilterPublic .addActionListener (listenerFilter);

        JRadioButtonMenuItem itemFilterLocal   = new JRadioButtonMenuItem ("Local");
        itemFilterLocal  .addActionListener (listenerFilter);

        int filterLevel = AppData.state.getOrDefault (FilteredTreeModel.PARAM, "PanelModel", "filter");
        panelEquationTree.model.setFilterLevel (filterLevel, panelEquationTree.tree);  // root is still null, so this has no immediate effect
        switch (filterLevel)
        {
            case FilteredTreeModel.REVOKED:
                buttonFilter.setIcon (iconFilterRevoked);
                itemFilterRevoked.setSelected (true);
                break;
            case FilteredTreeModel.ALL:
                buttonFilter.setIcon (iconFilterAll);
                itemFilterAll.setSelected (true);
                break;
            case FilteredTreeModel.PARAM:
                buttonFilter.setIcon (iconFilterParam);
                itemFilterPublic.setSelected (true);
                break;
            case FilteredTreeModel.LOCAL:
                buttonFilter.setIcon (iconFilterLocal);
                itemFilterLocal.setSelected (true);
                break;
        }

        menuFilter = new JPopupMenu ();
        menuFilter.add (itemFilterRevoked);
        menuFilter.add (itemFilterAll);
        menuFilter.add (itemFilterPublic);
        menuFilter.add (itemFilterLocal);
        menuFilter.addPopupMenuListener (new PopupMenuListener ()
        {
            public void popupMenuWillBecomeVisible (PopupMenuEvent e)
            {
            }

            public void popupMenuWillBecomeInvisible (PopupMenuEvent e)
            {
            }

            public void popupMenuCanceled (PopupMenuEvent e)
            {
                menuFilterCanceledAt = System.currentTimeMillis ();
            }
        });

        ButtonGroup groupFilter = new ButtonGroup ();
        groupFilter.add (itemFilterRevoked);
        groupFilter.add (itemFilterAll);
        groupFilter.add (itemFilterPublic);
        groupFilter.add (itemFilterLocal);

        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                MNode lastUsedNode = null;
                String lastUsedKey = AppData.state.get ("PanelModel", "lastUsed");
                if (! lastUsedKey.isEmpty ()) lastUsedNode = AppData.models.child (lastUsedKey);
                if (lastUsedNode != null)
                {
                    load (lastUsedNode);
                    panelEquationTree.tree.requestFocusInWindow ();
                }
            }
        });
    }

    public void load (MNode doc)
    {
        if (record == doc) return;

        if (record != null)
        {
            panelEquationTree.tree.stopEditing ();
            // Save tree state for current record, but only if it's better than the previously-saved state.
            // TODO: generalize focus cache management to include graph panel
            if (panelEquationTree.focusCache.get (record) == null  ||  panelEquationTree.tree.getSelectionPath () != null) panelEquationTree.focusCache.put (record, new StoredPath (panelEquationTree.tree));
        }

        record = doc;
        updateLock ();
        try
        {
            root = new NodePart (new MPart ((MPersistent) record));
            root.build ();
            root.findConnections ();
            panelEquationTree.load ();
            panelEquationGraph.load ();
        }
        catch (Exception e)
        {
            System.err.println ("Exception while parsing model: " + e);
            e.printStackTrace ();
        }
    }

    /**
        Informs us that some other code deleted a document from the DB.
        We only respond if it happens to be on display.
    **/
    public void recordDeleted (MNode doc)
    {
        if (doc != record) return;
        record = null;
        root   = null;
        panelEquationTree.recordDeleted (doc);
        panelEquationGraph.recordDeleted ();
    }

    public void updateLock ()
    {
        locked = ! AppData.models.isWriteable (record);
        panelEquationTree.tree.setEditable (! locked);
    }

    public void checkVisible ()
    {
        if (AppData.models.isVisible (record)) updateLock ();
        else                                   recordDeleted (record);
    }

    ActionListener listenerAdd = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            panelEquationTree.tree.stopEditing ();
            panelEquationTree.addAtSelected (e.getActionCommand ());
        }
    };

    ActionListener listenerDelete = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            panelEquationTree.tree.stopEditing ();  // It may seem odd to save a cell just before destroying it, but this gives cleaner UI painting.
            panelEquationTree.deleteSelected ();
        }
    };

    ActionListener listenerMove = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            panelEquationTree.tree.stopEditing ();
            panelEquationTree.moveSelected (Integer.valueOf (e.getActionCommand ()));
        }
    };

    ActionListener listenerRun = new ActionListener ()
    {
        /**
            Fire off a simulation.
            The code below is adapted from gove.sandia.n2a.ui.model.RunDetailPanel, specifically the old-style single-run.
            uiController.prepareAndSubmitRunEnsemble() is the way to set up a run ensemble
        **/
        public void actionPerformed (ActionEvent e)
        {
            if (record == null) return;
            MainTabbedPane mtp = (MainTabbedPane) MainFrame.instance.tabs;
            if (panelEquationTree.tree.isEditing ())
            {
                panelEquationTree.tree.stopEditing ();
                mtp.setPreferredFocus (PanelModel.instance, panelEquationTree.tree);  // Because tree does not reclaim the focus before focus shifts to the run tab.
            }

            String simulatorName = root.source.get ("$metadata", "backend");  // Note that "record" is the raw model, while "root.source" is the collated model.
            final Backend simulator = Backend.getBackend (simulatorName);
            MNode runs = AppData.runs;
            String jobKey = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ()) + "-" + jobCount++;
            final MNode job = runs.set ("", jobKey);  // Create the dir and model doc
            job.merge (root.source);
            job.set ("\"" + record.key () + "\"", "$inherit");
            ((MDoc) job).save ();  // Force directory (and job file) to exist, so Backends can work with the dir.

            new Thread ()
            {
                public void run ()
                {
                    try
                    {
                        simulator.start (job);
                    }
                    catch (Exception e)
                    {
                        // TODO: Instead of throwing an exception, simulation should record all errors/warnings in a file in the job dir.
                        e.printStackTrace ();
                    }
                }
            }.start ();

            PanelRun panelRun = (PanelRun) mtp.selectTab ("Runs");
            mtp.setPreferredFocus (panelRun, panelRun.tree);
            panelRun.addNewRun (job);
        }
    };

    ActionListener listenerExport = new ActionListener ()
    {
        // We create and customize a file chooser on the fly, display it modally, then use its result to initiate export.

        class ExporterFilter extends FileFilter
        {
            public Exporter exporter;

            ExporterFilter (Exporter exporter)
            {
                this.exporter = exporter;
            }

            @Override
            public boolean accept (File f)
            {
                return true;
            }

            @Override
            public String getDescription ()
            {
                return exporter.getName ();
            }
        }

        public void actionPerformed (ActionEvent e)
        {
            if (record == null) return;
            panelEquationTree.tree.stopEditing ();

            // Construct and customize a file chooser
            final JFileChooser fc = new JFileChooser (AppData.properties.get ("resourceDir"));
            fc.setDialogTitle ("Export \"" + record.key () + "\"");
            ExporterFilter n2a = null;
            List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint (Exporter.class);
            for (ExtensionPoint exp : exps)
            {
                ExporterFilter ef = new ExporterFilter ((Exporter) exp);
                fc.addChoosableFileFilter (ef);
                if (ef.exporter.getName ().contains ("N2A")) n2a = ef;
            }
            fc.setAcceptAllFileFilterUsed (false);
            if (n2a != null) fc.setFileFilter (n2a);

            // Display chooser and collect result
            int result = fc.showSaveDialog (MainFrame.instance);

            // Do export
            if (result == JFileChooser.APPROVE_OPTION)
            {
                Path path = fc.getSelectedFile ().toPath ();
                ExporterFilter filter = (ExporterFilter) fc.getFileFilter ();
                filter.exporter.export (record, path);
            }
        }
    };

    ActionListener listenerImport = new ActionListener ()
    {
        // We create and customize a file chooser on the fly, display it modally, then use its result to initiate export.

        class ImporterFilter extends FileFilter
        {
            public Importer importer;

            ImporterFilter (Importer importer)
            {
                this.importer = importer;
            }

            @Override
            public boolean accept (File f)
            {
                return importer.accept (f);
            }

            @Override
            public String getDescription ()
            {
                return importer.getName ();
            }
        }

        public void actionPerformed (ActionEvent e)
        {
            panelEquationTree.tree.stopEditing ();

            // Construct and customize a file chooser
            final JFileChooser fc = new JFileChooser (AppData.properties.get ("resourceDir"));
            fc.setDialogTitle ("Import");
            List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint (Importer.class);
            for (ExtensionPoint exp : exps)
            {
                ImporterFilter f = new ImporterFilter ((Importer) exp);
                fc.addChoosableFileFilter (f);
            }

            // Display chooser and collect result
            int result = fc.showOpenDialog (MainFrame.instance);

            // Do import
            if (result == JFileChooser.APPROVE_OPTION)
            {
                Path path = fc.getSelectedFile ().toPath ();
                PanelModel.importFile (path);
            }
        }
    };

    ActionListener listenerFilter = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            panelEquationTree.tree.stopEditing ();

            String action = e.getActionCommand ();
            if (action.equals ("Revoked"))
            {
                panelEquationTree.model.setFilterLevel (FilteredTreeModel.REVOKED, panelEquationTree.tree);
                buttonFilter.setIcon (iconFilterRevoked);
            }
            else if (action.equals ("All"))
            {
                panelEquationTree.model.setFilterLevel (FilteredTreeModel.ALL, panelEquationTree.tree);
                buttonFilter.setIcon (iconFilterAll);
            }
            else if (action.equals ("Parameters"))
            {
                panelEquationTree.model.setFilterLevel (FilteredTreeModel.PARAM, panelEquationTree.tree);
                buttonFilter.setIcon (iconFilterParam);
            }
            else if (action.equals ("Local"))
            {
                panelEquationTree.model.setFilterLevel (FilteredTreeModel.LOCAL, panelEquationTree.tree);
                buttonFilter.setIcon (iconFilterLocal);
            }
            AppData.state.set (panelEquationTree.model.filterLevel, "PanelModel", "filter");
        }
    };
}
