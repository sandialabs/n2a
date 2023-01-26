/*
Copyright 2020-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.PanelSearch;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;

/**
    Implements DnD assignment of model category.
    Direct editing of gui.category is done via ChangeAnnotation.
**/
public class ChangeCategory extends Undoable
{
    protected String       key;
    protected String       categoryBefore;
    protected String       categoryAfter;
    protected List<String> selectionBefore;
    protected List<String> selectionAfter;

    public ChangeCategory (MNode doc, String categoryAfter, List<String> selectionBefore, List<String> selectionAfter)
    {
        key                  = doc.key ();
        categoryBefore       = doc.get ("$meta", "gui", "category");
        this.categoryAfter   = categoryAfter;
        this.selectionBefore = selectionBefore;
        this.selectionAfter  = selectionAfter;
    }

    public void undo ()
    {
        super.undo ();
        apply (key, selectionAfter, categoryBefore, selectionBefore);
    }

    public void redo ()
    {
        super.redo ();
        apply (key, selectionBefore, categoryAfter, selectionAfter);
    }

    public void apply (String key, List<String> selectionBefore, String categoryAfter, List<String> selectionAfter)
    {
        MNode doc = AppData.models.child (key);
        if (doc == null) throw new CannotUndoException ();

        // Graph focus
        PanelSearch ps = PanelModel.instance.panelSearch;
        ps.lastSelection = selectionBefore;
        ps.takeFocus ();

        // Update DB, and possibly equation tree.
        PanelEquations pe = PanelModel.instance.panelEquations;
        if (doc != pe.record)  // direct to db
        {
            doc.set (categoryAfter, "$meta", "gui", "category");
        }
        else  // got through MPart
        {
            MNode source = pe.root.source;
            if (categoryAfter.isEmpty ()) source.clear (          "$meta", "gui", "category");
            else                     source.set   (categoryAfter, "$meta", "gui", "category");

            PanelEquationTree pet = pe.root.getTree ();
            FilteredTreeModel model = null;
            if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();

            // See ChangeAnnotations for more general code.
            // To simplify things, we always rebuild the metadata block, even though that is often overkill.
            NodeAnnotations metadataNode = (NodeAnnotations) pe.root.child ("$meta");  // For simplicity, assume this exists. DB models should always some metadata, such as "id". It is possible for the $meta node to be deleted by user, so this is not guaranteed.
            List<String> expanded = null;
            if (model != null) expanded = AddAnnotation.saveExpandedNodes (pet.tree, metadataNode);
            metadataNode.build ();
            metadataNode.filter ();
            if (model != null  &&  metadataNode.visible ())
            {
                model.nodeStructureChanged (metadataNode);
                AddAnnotation.restoreExpandedNodes (pet.tree, metadataNode, expanded);
            }
            if (pet != null)
            {
                TreeNode[] path = metadataNode.getPath ();
                pet.updateVisibility (path, -2, false);
                pet.animate ();
            }
        }

        // Update search panel.
        ps.lastSelection = selectionAfter;
        ps.search ();  // This will apply lastSelection when done.
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (edit instanceof ChangeCategory)
        {
            ChangeCategory cc = (ChangeCategory) edit;
            if (key.equals (cc.key)  &&  categoryAfter.equals (cc.categoryBefore))
            {
                categoryAfter  = cc.categoryAfter;
                selectionAfter = cc.selectionAfter;
                return true;
            }
        }
        return false;
    }

    public boolean anihilate ()
    {
        return categoryBefore.equals (categoryAfter);
    }
}
