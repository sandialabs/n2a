/*
Copyright 2017-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationGraph;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.PanelEquations.FocusCacheEntry;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class AddPart extends UndoableView implements AddEditable
{
    protected List<String>            path;           // to containing part
    protected int                     index;          // Position in the unfiltered tree where the node should be inserted. -1 means add to end.
    protected String                  name;
    protected MNode                   createSubtree;
    protected boolean                 nameIsGenerated;
    protected WeakReference<NodeBase> createdNode;    // Used by caller to initiate editing. Only valid immediately after call to redo().
    protected boolean                 multi;
    protected boolean                 multiLast;
    public    boolean                 multiShared;    // Do not adjust selection or focus at all. Our tree node is not visible because our parent is also the graph parent. Our graph node is visible, but focus should remain in tree.
    protected boolean                 touchesPin;

    public AddPart (NodePart parent, int index, MNode data, Point2D.Double location)
    {
        path       = parent.getKeyPath ();
        this.index = index;

        createSubtree = new MVolatile ();
        if (data == null)
        {
            name = uniqueName (parent, "p", 0, false);
            nameIsGenerated = true;
        }
        else
        {
            createSubtree.merge (data);
            name = data.key ();
            if (name.isBlank ())
            {
                String inherit = data.get ("$inherit");
                if (! inherit.isBlank ()) name = NodePart.validIdentifierFrom (inherit.split (",")[0].trim ());
            }
            if (name.isEmpty ()) name = uniqueName (parent, "p", 0, false);  // Even though this is actually generated, we don't plan to go directly into edit mode, so treat as if not generated.
            else                 name = uniqueName (parent, name, 2, true);
            nameIsGenerated = false;  // Because we don't go into edit mode on a drop or paste. If that changes, then always set nameIsGenerated to true.
        }

        if (location == null) location = centerOf (parent);
        MNode bounds = createSubtree.childOrCreate ("$meta", "gui", "bounds");
        bounds.setTruncated (location.x, 2, "x");
        bounds.setTruncated (location.y, 2, "y");

        touchesPin =  createSubtree.child ("$meta", "gui", "pin") != null;
    }

    public AddPart (NodePart parent, MNode data)
    {
        path            = parent.getKeyPath ();
        index           = -1;
        nameIsGenerated = false;
        name            = data.key ();

        createSubtree = new MVolatile ();
        createSubtree.merge (data);

        touchesPin =  createSubtree.child ("$meta", "gui", "pin") != null;
    }

    public static String uniqueName (NodeBase parent, String prefix, int suffix, boolean allowEmptySuffix)
    {
        if (allowEmptySuffix  &&  parent.source.child (prefix) == null) return prefix;
        prefix = stripSuffix (prefix);
        if (prefix.contains (" ")  &&  ! prefix.endsWith (" ")) prefix += " ";
        while (true)
        {
            String result = prefix + suffix;
            if (parent.source.child (result) == null) return result;
            suffix++;
        }
    }

    /**
        Remove any digits from the end of the name, leaving only the alpha prefix.
        Since name is an identifier, it will not begin with a digit.
        This function is useful for creating unique names without lengthening the counter at the end.
    **/
    public static String stripSuffix (String name)
    {
        int last = name.length () - 1;
        int i = last;
        while (Character.isDigit (name.charAt (i))) i--;
        if (i == last) return name;
        return name.substring (0, i+1);
    }

    /**
        Determine the anchor position for a set of new parts.
        The main approach is to approximate the center of the viewport when the parent part is displayed.
        If that is not possible, the fallback is the average position of existing parts.
        If there are no existing parts, the position is set arbitrarily to (8,8)em. This gives a small
        margin from the edge for nice appearance, while not straying far from (0,0).
    **/
    public static Point2D.Double centerOf (NodePart parent)
    {
        PanelEquations     pe  = PanelModel.instance.panelEquations;
        PanelEquationGraph peg = pe.panelEquationGraph;
        Dimension d = peg.getExtentSize ();
        double em = peg.getEm ();
        Point2D.Double location = new Point2D.Double (d.width / 2 / em, d.height / 2 / em);

        // Base on active viewport.
        if (parent == pe.part)
        {
            Point vp     = peg.getViewPosition ();
            Point offset = peg.getOffset ();
            location.x += (vp.x - offset.x) / em;
            location.y += (vp.y - offset.y) / em;
            return location;
        }

        // Base on stored position of viewport.
        FocusCacheEntry fce = pe.createFocus (parent);
        if (fce.position != null)
        {
            location.x += fce.position.x;
            location.y += fce.position.y;
            return location;
        }

        // Base on average position of existing parts.
        Point2D.Double center = new Point2D.Double ();
        int count = 0;
        Enumeration<?> children = parent.children ();
        while (children.hasMoreElements ())
        {
            Object o = children.nextElement ();
            if (! (o instanceof NodePart)) continue;
            NodePart p = (NodePart) o;
            MNode bounds = p.source.child ("$meta", "gui", "bounds");
            if (bounds == null) continue;
            count++;
            center.x += bounds.getDouble ("x");
            center.y += bounds.getDouble ("y");
        }
        if (count == 1)
        {
            location.x = center.x + 8;
            location.y = center.y + 8;
        }
        else if (count > 1)
        {
            location.x = center.x / count;
            location.y = center.y / count;
        }
        else  // count == 0
        {
            location = new Point2D.Double (8, 8);
        }
        return location;
    }

    /**
        Construct a set of AddPart edits to create a self-consistent set of new parts.
    **/
    public static List<AddPart> makeMulti (NodePart parent, MNode data, Point2D.Double location)
    {
        // Pre-process part set held in data.
        // Handle name collisions and set location of each part.

        Point2D.Double center = new Point2D.Double ();
        int count = 0;
        for (MNode p : data)
        {
            MNode bounds = p.child ("$meta", "gui", "bounds");
            if (bounds == null) continue;
            count++;
            center.x += bounds.getDouble ("x");
            center.y += bounds.getDouble ("y");
        }
        if (count > 0)
        {
            center.x /= count;
            center.y /= count;
        }

        if (location == null) location = centerOf (parent);
        location.x -= center.x;
        location.y -= center.y;
        int needLayout = data.size () - count;
        int columns = (int) Math.sqrt (needLayout);
        int l = 0;

        Map<String,String> nameChanges = new HashMap<String,String> ();
        for (MNode p : data)
        {
            MNode bounds = p.child ("$meta", "gui", "bounds");
            if (bounds == null)
            {
                bounds = p.childOrCreate ("$meta", "gui", "bounds");
                bounds.set ((l % columns) * 8, "x");
                bounds.set ((l / columns) * 8, "y");
                l++;
            }
            bounds.setTruncated (location.x + bounds.getDouble ("x"), 2, "x");
            bounds.setTruncated (location.y + bounds.getDouble ("y"), 2, "y");

            String oldName = p.key ();
            String newName = oldName;
            String prefix  = stripSuffix (oldName);
            if (prefix.contains (" ")  &&  ! prefix.endsWith (" ")) prefix += " ";
            int suffix = 2;
            while (parent.child (newName) != null) newName = prefix + suffix++;
            if (! newName.equals (oldName))  // If the name changed, ensure no self-collision as well.
            {
                while (data.child (newName) != null) newName = prefix + suffix++;
            }
            if (! newName.equals (oldName))
            {
                nameChanges.put (oldName, newName);
                data.move (oldName, newName);
            }
        }

        for (String oldName : nameChanges.keySet ())
        {
            String newName = nameChanges.get (oldName);

            // For all the new parts, scan for apparent connection bindings to the changed name.
            // This is somewhat heuristic. To do more correctly would require us to compile a mini-model.
            for (MNode p : data)
            {
                for (MNode v : p)
                {
                    // Detect if v is a connection binding.
                    // Compare these criteria with NodeVariable.findConnections() and EquationSet.resolveConnectionBindings().
                    String value = v.get ();
                    String[] parts = value.split ("\\.");
                    if (! parts[0].equals (oldName)) continue;
                    String vname = v.key ();
                    if (vname.contains ("$")  ||  vname.contains ("\\.")  ||  vname.endsWith ("'")) continue;  // LHS must be a simple identifier.
                    boolean hasEquations = false;
                    for (MNode e : v)
                    {
                        if (e.key ().startsWith ("@"))
                        {
                            hasEquations = true;
                            break;
                        }
                    }
                    if (hasEquations) continue;  // Must be single line.

                    // Apply name change
                    String newValue = newName;
                    for (int i = 1; i < parts.length; i++) newValue += "." + parts[i];
                    v.set (newValue);
                }
            }
        }

        // Generate AddParts from data
        List<AddPart> result = new ArrayList<AddPart> ();
        for (MNode p : data) result.add (new AddPart (parent, p));
        return result;
    }

    public void setMulti (boolean value)
    {
        multi = value;
    }

    public void setMultiLast (boolean value)
    {
        multiLast = value;
    }

    public void undo ()
    {
        super.undo ();
        destroy (path, false, name, ! multi  ||  multiLast, multiShared, touchesPin);
    }

    public static void destroy (List<String> path, boolean canceled, String name, boolean setSelected, boolean multiShared, boolean touchesPin)
    {
        // Retrieve created node
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodePart createdNode = (NodePart) parent.child (name);

        PanelEquations pe = PanelModel.instance.panelEquations;
        boolean graphParent =  parent == pe.part;
        PanelEquationTree pet = graphParent ? null : parent.getTree ();  // Only use tree if it is not the graph parent, since graph parent hides its sub-parts.
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;  // only used if graphParent is true

        TreeNode[] createdPath = createdNode.getPath ();
        int index = parent.getIndexFiltered (createdNode);  // returns -1 if createdNode is filtered out of parent
        if (canceled) index--;

        // Update database
        MPart mparent = parent.source;
        AddVariable.deleteOrKill (mparent, name);

        // Update GUI
        if (mparent.child (name) == null)  // Node is fully deleted
        {
            pe.deleteFocus (createdNode);
            if (model == null) FilteredTreeModel.removeNodeFromParentStatic (createdNode);
            else               model.removeNodeFromParent (createdNode);
            if (graphParent) peg.removePart (createdNode, setSelected  &&  ! multiShared);
            parent.findConnections ();
            parent.updatePins ();
        }
        else  // Just exposed an overridden node
        {
            createdNode.build ();  // Does not change the fake-root status of this node.
            parent.findConnections ();
            createdNode.rebuildPins ();
            createdNode.filter ();
            if (graphParent)  // Need to update entire model under fake root.
            {
                PanelEquationTree subpet = createdNode.getTree ();
                if (subpet != null)
                {
                    FilteredTreeModel submodel = (FilteredTreeModel) subpet.tree.getModel ();
                    submodel.nodeStructureChanged (createdNode);
                    subpet.animate ();
                }
                // Implicitly, the title of the node was focused when the part was deleted, so ensure it gets the focus back.
                if (setSelected  &&  ! multiShared) createdNode.graph.takeFocusOnTitle ();
            }
        }

        pe.resetBreadcrumbs ();
        if (pet == null)
        {
            PanelEquationTree.updateOrder (null, createdPath);
            PanelEquationTree.updateVisibility (null, createdPath, index, false);
        }
        else
        {
            pet.updateOrder (createdPath);
            pet.updateVisibility (createdPath, index, setSelected);  // includes nodeStructureChanged(), if necessary
            pet.animate ();
        }
        if (graphParent  ||  touchesPin)
        {
            peg.updatePins ();
            peg.reconnect ();
            peg.repaint ();
        }
        if (graphParent)
        {
            if (pe.view == PanelEquations.NODE  &&  peg.isEmpty ()) pe.panelParent.setOpen (true);
            if (setSelected  &&  multiShared) pe.switchFocus (false, false);
        }
    }

    public void redo ()
    {
        super.redo ();
        NodeBase temp = create (path, index, name, createSubtree, nameIsGenerated, multi, multiLast, multiShared, touchesPin);
        createdNode = new WeakReference<NodeBase> (temp);
    }

    public NodeBase getCreatedNode ()
    {
        if (createdNode == null) return null;
        return createdNode.get ();
    }

    public static NodeBase create (List<String> path, int index, String name, MNode newPart, boolean nameIsGenerated, boolean multi, boolean multiLast, boolean multiShared, boolean touchesPin)
    {
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase n = parent.child (name);
        if (n != null  &&  ! (n instanceof NodePart)) throw new CannotUndoException ();  // Should be blocked by GUI constraints, but this defends against ill-formed model on clipboard.
        NodePart createdNode = (NodePart) n;

        // Update database
        MPart createdPart = (MPart) parent.source.childOrCreate (name);
        createdPart.merge (newPart);
        AddVariable.unkill (createdPart);

        // Update GUI

        PanelEquations pe = PanelModel.instance.panelEquations;
        boolean graphParent =  parent == pe.part;
        PanelEquationTree pet = graphParent ? null : parent.getTree ();
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;

        boolean addGraphNode = false;
        if (createdNode == null)
        {
            addGraphNode = true;
            createdNode = new NodePart (createdPart);
            createdNode.hide = graphParent;
            if (index < 0) index = parent.getChildCount ();
            if (model == null) FilteredTreeModel.insertNodeIntoUnfilteredStatic (createdNode, parent, index);
            else               model.insertNodeIntoUnfiltered (createdNode, parent, index);
        }
        createdNode.build ();
        parent.findConnections ();  // Other nodes besides immediate siblings can also refer to us, so to be strictly correct, should run findConnectins() on root of tree.
        createdNode.rebuildPins ();
        createdNode.filter ();

        if (nameIsGenerated) createdNode.setUserObject ("");  // pure create, so about to go into edit mode. This should only happen on first application of the create action, and should only be possible if visibility is already correct.
        TreeNode[] createdPath = createdNode.getPath ();
        if (pet == null)
        {
            if (! nameIsGenerated) PanelEquationTree.updateOrder (null, createdPath);
            PanelEquationTree.updateVisibility (null, createdPath, -2, false);
        }
        else
        {
            if (! nameIsGenerated) pet.updateOrder (createdPath);
            pet.updateVisibility (createdPath, -2, ! multi);
            if (multi) pet.tree.addSelectionPath (new TreePath (createdPath));
            pet.animate ();
        }

        if (graphParent)
        {
            if (addGraphNode)
            {
                peg.addPart (createdNode);
            }
            else  // Existing graph node; content needs to be restructured.
            {
                PanelEquationTree subpet = createdNode.getTree ();
                if (subpet != null)
                {
                    FilteredTreeModel submodel = (FilteredTreeModel) subpet.tree.getModel ();
                    submodel.nodeStructureChanged (createdNode);
                    subpet.animate ();
                }
            }
            createdNode.hide = false;
            if (multi)
            {
                if (! multiShared) createdNode.graph.setSelected (true);
            }
            else
            {
                peg.clearSelection ();
                createdNode.graph.takeFocusOnTitle ();
            }
        }
        if (graphParent  ||  touchesPin)
        {
            if (! multi  ||  multiLast)
            {
                peg.updatePins ();
                peg.reconnect ();
                peg.repaint ();
            }
        }

        return createdNode;
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (nameIsGenerated  &&  edit instanceof ChangePart)
        {
            ChangePart change = (ChangePart) edit;
            if (path.equals (change.path)  &&  name.equals (change.nameBefore))
            {
                name = change.nameAfter;
                nameIsGenerated = false;
                return true;
            }
        }
        else if (edit instanceof ChangeInherit)
        {
            ChangeInherit ci = (ChangeInherit) edit;
            if (ci.connection)  // When this flag is true, PanelSearch promises that the transactions are related.
            {
                createSubtree.set (ci.valueAfter, "$inherit");
                return true;
            }
        }
        return false;
    }
}
