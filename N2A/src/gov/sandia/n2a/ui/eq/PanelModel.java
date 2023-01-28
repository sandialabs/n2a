/*
Copyright 2013-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.SwingUtilities;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNodeListener;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Import;
import gov.sandia.n2a.ui.eq.PanelEquations.BreadcrumbRenderer;
import gov.sandia.n2a.ui.settings.SettingsLookAndFeel;

@SuppressWarnings("serial")
public class PanelModel extends JPanel implements MNodeListener
{
    public static PanelModel instance;  ///< Technically, this class is a singleton, because only one would normally be created.

    protected JSplitPane     split;
    protected JSplitPane     splitMRU;
    public    PanelMRU       panelMRU;
    public    PanelSearch    panelSearch;
    public    PanelEquations panelEquations;

    public static GraphNode getGraphNode (Component g)
    {
        while (g != null  &&  ! (g instanceof GraphNode)) g = g.getParent ();
        return (GraphNode) g;
    }

    // TODO: Enforce focus policy on individual components, rather than using a single policy object for everything.
    public class GraphFocusTraversalPolicy extends LayoutFocusTraversalPolicy
    {
    	public Component getComponentAfter (Container aContainer, Component aComponent)
        {
    	    Container ec = panelEquations.editor.editingContainer;
    	    if (aComponent == ec)
            {
                if (panelEquations.editor.editingNode == null  &&  ! panelEquations.editor.editingTitle)
                {
                    return panelEquations.editor.editingTree;  // Return focus to tree when done editing.
                }
                return super.getComponentAfter (aContainer, aComponent);  // Otherwise, do normal focus cycle without considering GraphNode. This is necessary to enter editor to begin with.
            }
    	    else if (SwingUtilities.isDescendingFrom (aComponent, ec)  &&  panelEquations.view != PanelEquations.NODE)
    	    {
    	        // Behave as if user tried to tab out of property tree itself. See below.
    	        aComponent = panelEquations.panelEquationTree.tree;
    	    }

            if (aComponent == panelSearch.nameEditor.editor)
            {
                return panelSearch.tree;
            }

            Component result = super.getComponentAfter (aContainer, aComponent);

            if (result == panelSearch.tree  &&  panelSearch.tree.getRowCount () == 0)
            {
                return super.getComponentAfter (aContainer, result);  // Skip search tree if it is empty.
            }

            if (panelEquations.view == PanelEquations.NODE)
            {
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
                    if (g1 != null) return g1.getTargetComponent ();
                    return result;
                }

                // Handle parent node behavior
                BreadcrumbRenderer title = panelEquations.breadcrumbRenderer;
                JTree              tree  = panelEquations.panelParent.panelEquationTree.tree;
                if (aComponent == title  &&  result == tree)
                {
                    result = super.getComponentAfter (aContainer, result);
                    g1 = getGraphNode (result);
                    if (g1 != null) return g1.getTargetComponent ();
                    return result;
                }
                if (result == title) return panelEquations.getTitleFocus ();
            }
            else
            {
                PanelEquationTree pet = panelEquations.panelEquationTree;
                JTree tree = pet.tree;
                if (result == tree)
                {
                    // Skip property panel in tab sequence
                    result = super.getComponentAfter (aContainer, result);
                }
                else if (aComponent == tree)
                {
                    // Go to next graph node after the one currently associated with property panel
                    if (pet.root == panelEquations.part)  // tree is attached to graph parent
                    {
                        result = super.getComponentAfter (aContainer, panelEquations.breadcrumbRenderer);
                    }
                    else  // tree is attached to a graph node
                    {
                        // Notice the lack of "super" here. We call the present function again, so we also
                        // skip over the property panel at the end of the graph node focus cycle.
                        result = getComponentAfter (aContainer, pet.root.graph.title);
                    }

                    // Tabbing while tree has focus is similar to a click on a graph node.
                    // It removes any current multi-selection, so only the new focus gets selected.
                    panelEquations.panelEquationGraph.clearSelection ();

                    // If appropriate, claw focus back to tree.
                    if (result instanceof GraphNode.TitleRenderer  ||  result instanceof PanelEquations.BreadcrumbRenderer)
                    {
                        EventQueue.invokeLater (new Runnable ()
                        {
                            public void run ()
                            {
                                pet.tree.requestFocusInWindow ();
                            }
                        });
                    }
                }
            }

            return result;
        }

        public Component getComponentBefore (Container aContainer, Component aComponent)
        {
            Container ec = panelEquations.editor.editingContainer;
            if (aComponent != ec  &&  SwingUtilities.isDescendingFrom (aComponent, ec)  &&  panelEquations.view != PanelEquations.NODE)
            {
                aComponent = panelEquations.panelEquationTree.tree;
            }

            Component result = super.getComponentBefore (aContainer, aComponent);

            if (result == panelSearch.tree  &&  panelSearch.tree.getRowCount () == 0)
            {
                return super.getComponentBefore (aContainer, result);  // Skip search tree if it is empty.
            }

            if (panelEquations.view == PanelEquations.NODE)
            {
                GraphNode g1 = getGraphNode (result);
                if (g1 != null)
                {
                    GraphNode g0 = getGraphNode (aComponent);
                    if (g1 == g0)
                    {
                        result = super.getComponentBefore (aContainer, result);
                        g1 = getGraphNode (result);
                    }
                    if (g1 != null) return g1.getTargetComponent ();
                    // Most likely we have cycled into the graph parent, so fall through. The value of aComponent won't matter.
                }

                BreadcrumbRenderer title = panelEquations.breadcrumbRenderer;
                JTree              tree  = panelEquations.panelParent.panelEquationTree.tree;
                if (aComponent == tree  &&  result == title) return super.getComponentBefore (aContainer, result);
                if (result == tree) return panelEquations.getTitleFocus ();
            }
            else
            {
                PanelEquationTree pet = panelEquations.panelEquationTree;
                JTree tree = pet.tree;
                if (result == tree)
                {
                    result = super.getComponentBefore (aContainer, result);
                }
                else if (aComponent == tree)
                {
                    if (pet.root == panelEquations.part)
                    {
                        result = super.getComponentBefore (aContainer, panelEquations.breadcrumbRenderer);
                    }
                    else
                    {
                        result = super.getComponentBefore (aContainer, pet.root.graph.title);
                    }

                    panelEquations.panelEquationGraph.clearSelection ();

                    if (result instanceof GraphNode.TitleRenderer  ||  result instanceof PanelEquations.BreadcrumbRenderer)
                    {
                        EventQueue.invokeLater (new Runnable ()
                        {
                            public void run ()
                            {
                                pet.tree.requestFocusInWindow ();
                            }
                        });
                    }
                }
            }

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
        split.setOneTouchExpandable (true);

        setLayout (new BorderLayout ());
        add (split, BorderLayout.CENTER);

        setFocusCycleRoot (true);
        setFocusTraversalPolicy (new GraphFocusTraversalPolicy ());

        // Determine the split positions.

        FontMetrics fm = panelSearch.tree.getFontMetrics (panelSearch.tree.getFont ());
        float em = SettingsLookAndFeel.em;
        splitMRU.setDividerLocation ((int) Math.round (AppData.state.getOrDefault (fm.getHeight () * 4 / em, "PanelModel", "dividerMRU") * em));
        splitMRU.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (! (o instanceof Integer)) return;
                float value = ((Integer) o).floatValue () / SettingsLookAndFeel.em;
                AppData.state.setTruncated (value, 2, "PanelModel", "dividerMRU");
            }
        });

        split.setDividerLocation ((int) Math.round (AppData.state.getOrDefault (fm.stringWidth ("Example Hodgkin-Huxley Cable") / em, "PanelModel", "divider") * em));
        split.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (! (o instanceof Integer)) return;
                float value = ((Integer) o).floatValue () / SettingsLookAndFeel.em;
                AppData.state.setTruncated (value, 2, "PanelModel", "divider");
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
        panelMRU.updateDoc (oldKey, newKey);
        panelSearch.updateDoc (oldKey, newKey);
        panelEquations.updateDoc (oldKey, newKey);
    }

    public static void importFile (Path path)
    {
        Import bestImporter = null;
        float  bestP        = 0;
        for (ExtensionPoint exp : PluginManager.getExtensionsForPoint (Import.class))
        {
            float P = ((Import) exp).matches (path);
            if (P > bestP)
            {
                bestP        = P;
                bestImporter = (Import) exp;
            }
        }
        if (bestImporter != null) bestImporter.process (path);
    }
}
