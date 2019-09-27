/*
Copyright 2013-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNodeListener;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Importer;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.eq.PanelEquations.BreadcrumbRenderer;

@SuppressWarnings("serial")
public class PanelModel extends JPanel implements MNodeListener
{
    public static PanelModel instance;  ///< Technically, this class is a singleton, because only one would normally be created.

    protected JSplitPane     split;
    protected JSplitPane     splitMRU;
    public    PanelMRU       panelMRU;
    public    PanelSearch    panelSearch;
    public    PanelEquations panelEquations;
    public    UndoManager    undoManager = new UndoManager ();

    public class GraphFocusTraversalPolicy extends LayoutFocusTraversalPolicy
    {
    	public GraphNode getGraphNode (Component g)
    	{
            while (g != null  &&  ! (g instanceof GraphNode)) g = g.getParent ();
            return (GraphNode) g;
    	}

    	public Component getComponentAfter (Container aContainer, Component aComponent)
        {
            if (aComponent == panelEquations.editor.editingContainer)
            {
                if (panelEquations.editor.editingNode == null  &&  ! panelEquations.editor.editingTitle)
                {
                    return panelEquations.editor.editingTree;  // Return focus to tree when done editing.
                }
                return super.getComponentAfter (aContainer, aComponent);  // Otherwise, do normal focus cycle without considering GraphNode. This is necessary to enter editor to begin with.
            }

            if (aComponent == panelSearch.nameEditor)  // nameEditor will be null when no active name edit
            {
                return panelSearch.list;
            }

            Component result = super.getComponentAfter (aContainer, aComponent);

            // Handle GraphNode behavior: only one of title or tree should receive keyboard focus at any given time
            GraphNode g1 = getGraphNode (result);
            if (g1 != null)  // result is inside a GraphNode
            {
                GraphNode g0 = getGraphNode (aComponent);
                if (g1 == g0)  // Need to escape from the current GraphNode
                {
                    result = super.getComponentAfter (aContainer, result);
                    g1 = getGraphNode (result);
                }
                if (g1 != null) result = g1.getTitleFocus ();
                return result;
            }

            // Handle parent node behavior
            BreadcrumbRenderer title = instance.panelEquations.breadcrumbRenderer;
            JTree              tree  = instance.panelEquations.panelParent.panelEquations.tree;
        	if (aComponent == title  &&  result == tree) return super.getComponentAfter (aContainer, result);
            if (result == title)                         return panelEquations.getTitleFocus ();

            return result;
        }

        public Component getComponentBefore (Container aContainer, Component aComponent)
        {
            Component result = super.getComponentBefore (aContainer, aComponent);

            GraphNode g1 = getGraphNode (result);
            if (g1 != null)
            {
                GraphNode g0 = getGraphNode (aComponent);
                if (g1 == g0)
                {
                    result = super.getComponentBefore (aContainer, result);
                    g1 = getGraphNode (result);
                }
                if (g1 != null) result = g1.getTitleFocus ();
                return result;
            }

            BreadcrumbRenderer title = instance.panelEquations.breadcrumbRenderer;
            JTree              tree  = instance.panelEquations.panelParent.panelEquations.tree;
        	if (aComponent == tree  &&  result == title) return super.getComponentBefore (aContainer, result);
            if (result == tree)                          return panelEquations.getTitleFocus ();

            return result;
        }
    }

    public PanelModel ()
    {
        instance = this;

        panelMRU       = new PanelMRU ();
        panelSearch    = new PanelSearch ();
        panelEquations = new PanelEquations ();

        splitMRU = new JSplitPane (JSplitPane.VERTICAL_SPLIT, panelMRU, panelSearch);
        split = new JSplitPane (JSplitPane.HORIZONTAL_SPLIT, splitMRU, panelEquations);
        split.setOneTouchExpandable(true);

        setLayout (new BorderLayout ());
        add (split, BorderLayout.CENTER);

        setFocusCycleRoot (true);
        setFocusTraversalPolicy (new GraphFocusTraversalPolicy ());

        // Determine the split positions.

        FontMetrics fm = panelSearch.list.getFontMetrics (panelSearch.list.getFont ());
        splitMRU.setDividerLocation (AppData.state.getOrDefault (fm.getHeight () * 4, "PanelModel", "dividerMRU"));
        splitMRU.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (o instanceof Integer) AppData.state.set (o, "PanelModel", "dividerMRU");
            }
        });

        split.setDividerLocation (AppData.state.getOrDefault (fm.stringWidth ("Example Hodgkin-Huxley Cable"), "PanelModel", "divider"));
        split.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (o instanceof Integer) AppData.state.set (o, "PanelModel", "divider");
            }
        });

        // Undo

        InputMap inputMap = getInputMap (WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo");
        inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");

        ActionMap actionMap = getActionMap ();
        actionMap.put ("Undo", new AbstractAction ("Undo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.undo ();}
                catch (CannotUndoException e) {}
                catch (CannotRedoException e) {}
            }
        });
        actionMap.put ("Redo", new AbstractAction ("Redo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.redo();}
                catch (CannotUndoException e) {}
                catch (CannotRedoException e) {}
            }
        });

        AppData.models.addListener (this);
    }

    public void changed ()
    {
        panelMRU.loadMRU ();
        panelSearch.search ();
        MNode record = panelEquations.record;
        if (record == null) return;
        if (AppData.models.isVisible (record))
        {
            panelEquations.record = null;
            panelEquations.load (record);
        }
        else
        {
            panelEquations.recordDeleted (record);
        }
    }

    public void childAdded (String key)
    {
        MNode doc = AppData.models.child (key);
        panelMRU.insertDoc (doc);
        panelSearch.insertDoc (doc);
    }

    public void childDeleted (String key)
    {
        panelMRU.removeDoc (key);
        panelSearch.removeDoc (key);
        panelEquations.checkVisible ();
    }

    public void childChanged (String oldKey, String newKey)
    {
        // Holders in search and MRU should associate newKey with correct doc.
        MNode newDoc = AppData.models.child (newKey);
        panelMRU.updateDoc (newDoc);
        panelSearch.updateDoc (newDoc);

        String key = "";
        MNode record = panelEquations.record;
        if (record != null) key = record.key ();

        boolean contentOnly = oldKey.equals (newKey);
        if (key.equals (newKey))
        {
            if (contentOnly)
            {
                panelEquations.record = null;  // Force rebuild of display
                panelEquations.load (newDoc);
            }
            else
            {
                panelEquations.checkVisible ();
            }
        }
        if (contentOnly) return;

        MNode oldDoc = AppData.models.child (oldKey);
        if (oldDoc == null)  // deleted
        {
            panelMRU.removeDoc (oldKey);
            panelSearch.removeDoc (oldKey);
            panelEquations.checkVisible ();
        }
        else  // oldDoc has changed identity
        {
            panelMRU.updateDoc (oldDoc);
            panelSearch.updateDoc (oldDoc);
            if (key.equals (oldKey))
            {
                panelEquations.record = null;
                panelEquations.load (oldDoc);
            }
        }
    }

    public static void importFile (Path path)
    {
        Importer bestImporter = null;
        float    bestP        = 0;
        for (ExtensionPoint exp : PluginManager.getExtensionsForPoint (Importer.class))
        {
            float P = ((Importer) exp).isIn (path);
            if (P > bestP)
            {
                bestP        = P;
                bestImporter = (Importer) exp;
            }
        }
        if (bestImporter != null) bestImporter.process (path);
    }
}
