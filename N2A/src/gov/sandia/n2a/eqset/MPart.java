/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MCombo;
import gov.sandia.n2a.db.MNode;

/**
    Collates models following all the N2A language rules, and provides an interface
    for active editing. Also suitable for construction of EquationSets for execution.
    Implements the full MNode interface on the collated tree, as if it were nothing
    more than a regular MNode tree. Changes made through this interface cause changes
    in the original top-level document that would otherwise produce the resulting tree.
    Additional functions (outside the main MNode interface) support efficient
    construction.

    All MNode functions are fully supported. In particular, merge() is safe to use, even
    if it involves $inherit lines.

    See notes on MNode regarding "undefined" nodes. A value of undefined in a top-level
    document allows an underlying inherited value to show through. This should be used
    only for interior structural nodes, not for leaf nodes. It is possible to set a
    top-level leaf node to null, but this should be immediately followed by setting
    children.
**/
public class MPart extends MNode
{
    protected MNode source;
    protected MNode original;        // The original source of this node, before it was overwritten by another document. Refers to same object as source if this node has not been overridden.
    protected MPart inheritedFrom;   // Node in the tree that contains the $include statement that generated this node. Retained even if the node is overridden.

    protected MPart container;
    protected NavigableMap<String,MPart> children;

    protected static final ThreadLocal<MCombo> models = new ThreadLocal<MCombo> ()
    {
        protected MCombo initialValue ()
        {
            return AppData.models;
        }
    };

    /**
        Collates a full model from the given source document.
    **/
    public MPart (MNode source)
    {
        container     = null;
        this.source   = source;
        original      = source;
        inheritedFrom = null;

        underrideChildren (null, source);
        expand ();
    }

    /**
        Constructs an MPart tree for the model with the given key,
        using the given snapshot to override the main database.
    **/
    public static MPart fromSnapshot (String key, MNode snapshot)
    {
        List<MNode> containers = new ArrayList<MNode> (2);
        containers.add (snapshot);
        containers.add (AppData.models);
        MCombo temp = new MCombo ("temp", containers);
        models.set (temp);
        MPart result = new MPart (temp.child (key));
        models.set (AppData.models);
        temp.done ();
        return result;
    }

    protected MPart (MPart container, MPart inheritedFrom, MNode source)
    {
        this.container     = container;
        this.source        = source;
        original           = source;
        this.inheritedFrom = inheritedFrom;
    }

    /**
        Convenience method for expand(LinkedList<MNode>).
    **/
    protected synchronized void expand ()
    {
        LinkedList<MNode> visited = new LinkedList<MNode> ();
        visited.push (((MPart) root ()).source);
        expand (visited);
    }

    /**
        Loads all inherited structure into the sub-tree rooted at this node,
        using existing structure placed here by higher nodes as a starting point.
        The remainder of the tree is filled in by "underride". Assumes this sub-tree
        is a clean structure with only entries placed by higher levels, and no
        lingering structure that we might otherwise build now.
        @param visited Used to guard against a document loading itself.
    **/
    protected synchronized void expand (LinkedList<MNode> visited)
    {
        inherit (visited);
        for (MNode n : this)
        {
            MPart p = (MPart) n;
            if (p.isPart ()) p.expand (visited);
        }
    }

    /**
        Initiates an underride load of all equations inherited by this node,
        using the current value of $inherit in our collated children.
        @param visited Used to guard against a document loading itself.
    **/
    protected synchronized void inherit (LinkedList<MNode> visited)
    {
        if (children == null) return;
        MPart root = children.get ("$inherit");
        if (root != null) inherit (visited, root, root);
    }

    /**
        Injects inherited equations as children of this node.
        Handles recursion up the hierarchy of parents.
        Handles relinking to parents whose name has changed. (ID is assumed to be constant and universal.)
        @param visited Used to guard against a document loading itself.
        @param root The node in the collated tree (named "$inherit") which triggered the current
        round of inheritance. May be a child of a higher node, or a child of this node, but never
        a child of a lower node.
        @param from The $inherit node to be processed. We parse this into a set of part names
        which we retrieve from the database.
    **/
    protected synchronized void inherit (LinkedList<MNode> visited, MPart root, MNode from)
    {
        MCombo models = MPart.models.get ();
        boolean maintainable =  from == root  &&  root.isFromTopDocument ()  &&  models.isWriteable (((MPart) root.root ()).source);
        boolean changedName = false;  // Indicates that at least one name changed due to ID resolution. This lets us delay updating the field until all names are processed.
        boolean changedID   = false;

        String[] parentNames = from.get ().split (",");
        List<String> IDs     = Arrays.asList (from.get ("$metadata", "id").split (",", -1));  // -1 allows for unassigned ID in middle of list
        for (int i = 0; i < parentNames.length; i++)
        {
            String parentName = parentNames[i];
            parentName = parentName.trim ().replace ("\"", "");
            MNode parentSource = models.child (parentName);

            String id = "";
            if (i < IDs.size ()) id = IDs.get (i).trim ();

            String parentID = "";
            if (parentSource != null)
            {
                parentID = parentSource.get ("$metadata", "id");
                if (! id.isEmpty ()  &&  ! parentID.equals (id)) parentSource = null;  // Even though the name matches, parentSource is not really the same model that was originally linked.
            }
            if (parentSource == null)
            {
                if (! id.isEmpty ())
                {
                    parentSource = AppData.getModel (id);
                    if (parentSource != null  &&  maintainable)  // relink
                    {
                        parentNames[i] = parentSource.key ();
                        changedName = true;
                    }
                }
            }
            else
            {
                if (id.isEmpty ()  &&  ! parentID.isEmpty ()  &&  maintainable)
                {
                    while (IDs.size () <= i) IDs.add ("");
                    IDs.set (i, parentID);
                    changedID = true;
                }
            }

            if (parentSource != null  &&  ! visited.contains (parentSource))
            {
                underrideChildren (root, parentSource);
                MNode parentFrom = parentSource.child ("$inherit");
                if (parentFrom != null)
                {
                    visited.push (parentSource);
                    inherit (visited, root, parentFrom);  // yes, we continue to treat the root as the initiator for all the inherited equations
                    visited.pop ();
                }
            }
        }

        if (changedName)
        {
            StringBuilder value = new StringBuilder ();
            value.append (parentNames[0]);
            for (int i = 1; i < parentNames.length; i++) value.append (", " + parentNames[i]);
            root.source.set (value.toString ());
        }
        if (changedID)
        {
            StringBuilder value = new StringBuilder ();
            value.append (IDs.get (0));
            for (int i = 1; i < IDs.size (); i++) value.append ("," + IDs.get (i));
            root.source.set (value, "$metadata", "id");
        }
    }

    /**
        Injects inherited equations at this node.
        Handles recursion down our containment hierarchy.
        This method only changes the node if it has no existing inheritedFrom value,
        so it is safe to run more than once for a given $inherit statement.
        @param newSource The current node in the source document which corresponds to this node in the MPart tree.
    **/
    protected synchronized void underride (MPart from, MNode newSource)
    {
        if (inheritedFrom == null  &&  from != this)  // The second clause is for a very peculiar case. We don't allow incoming $inherit lines to underride the $inherit that brought them in, since their existence is completely contingent on it.
        {
            inheritedFrom = from;
            original = newSource;
        }
        underrideChildren (from, newSource);
    }

    /**
        Injects inherited equations as children of this node.
        Handles recursion down our containment hierarchy.
        See note on underride(MPart,MNode). This is safe to run more than once for a given $inherit statement.
        @param newSource The current node in the source document which matches this node in the MPart tree.
    **/
    protected synchronized void underrideChildren (MPart from, MNode newSource)
    {
        if (newSource.size () == 0) return;
        if (children == null) children = new TreeMap<String,MPart> (comparator);
        for (MNode n : newSource)
        {
            String key = n.key ();
            MPart c = children.get (key);
            if (c == null)
            {
                c = new MPart (this, from, n);
                children.put (key, c);
                c.underrideChildren (from, n);
            }
            else
            {
                c.underride (from, n);
            }
        }
    }

    /**
        Remove any effects the $inherit line "from" had on this node and our children.
        @param parentIterator Enables us to delete ourselves from the containing collection.
        If null, this is the top node of the subtree to be purged, so we should not be
        deleted.
    **/
    protected synchronized void purge (MPart from, Iterator<Entry<String,MPart>> parentIterator)
    {
        if (inheritedFrom == from)
        {
            if (source == original)  // This node exists only because of "from". Implicitly, we are not from the top document, and all our children are in the same condition.
            {
                if (parentIterator != null) parentIterator.remove ();
                return;
            }
            else  // This node contains an underride, so simply remove that underride.
            {
                original = source;
                inheritedFrom = null;
            }
        }

        if (children == null) return;
        MPart inherit = children.get ("$inherit");
        if (inherit != null  &&  inherit.inheritedFrom == from) purge (inherit, null);  // If our local $inherit is contingent on "from", then remove all its effects as well. Note that a $inherit line never comes from itself (inherit.inheritedFrom != inherit).

        Iterator<Entry<String,MPart>> childIterator = children.entrySet ().iterator ();
        while (childIterator.hasNext ()) (childIterator.next ().getValue ()).purge (from, childIterator);
    }

    /**
        Indicates if this node has the form of a sub-part.
    **/
    public boolean isPart ()
    {
        return isPart (this);
    }

    /**
        Indicates if the given node has the form of a sub-part.
        Since none of the tests depend on actually being an MPart, this test is static so it can be applied to any MNode without casting.
    **/
    public static boolean isPart (MNode node)
    {
        if (! node.get ().isEmpty ()) return false;  // A part never has an assignment. A variable might not have an assignment if it is multi-line.
        if (node.key ().startsWith ("$")) return false;
        for (MNode c : node) if (c.key ().startsWith ("@")) return false;  // has the form of a multi-line equation
        return true;
    }

    public boolean isFromTopDocument ()
    {
        // There are only 3 cases allowed:
        // * original == source and inheritedFrom != null -- node holds an inherited value
        // * original == source and inheritedFrom == null -- node holds a top-level value
        // * original != source and inheritedFrom != null -- node holds a top-level value and an underride (inherited value)
        // The fourth case is excluded by the logic of this class.
        return original != source  ||  inheritedFrom == null;
    }

    public boolean isOverridden ()
    {
        return original != source;
    }

    public MPart parent ()
    {
        return container;
    }

    public MNode getSource ()
    {
        return source;
    }

    public MNode getOriginal ()
    {
        return original;
    }

    protected synchronized MNode getChild (String index)
    {
        if (children == null) return null;
        return children.get (index);
    }

    public String key ()
    {
        return source.key ();  // could also use original.key(), as they should always match
    }

    public synchronized int size ()
    {
        if (children == null) return 0;
        return children.size ();
    }

    public boolean data ()
    {
        return source.data ()  ||  original.data ();
    }

    public synchronized String getOrDefault (String defaultValue)
    {
        if (source.data ()) return source.getOrDefault (defaultValue); 
        return original.getOrDefault (defaultValue);
    }

    /**
        Removes all children of this node from the top-level document, and restores them to their non-overridden state.
        Some of the nodes may disappear, if they existed only due to override.
        In general, acts as if the clear is applied in the top-level document, followed by a full collation of the tree.
    **/
    public synchronized void clear ()
    {
        if (children == null) return;
        if (! isFromTopDocument ()) return; // Nothing to do.
        releaseOverrideChildren ();
        clearPath ();
        expand ();
    }

    /**
        Removes the named child of this node from the top-level document, and restores it to its non-overridden state.
        The child may disappear, if it existed only due to override.
        In general, acts as if the clear is applied in the top-level document, followed by a full collation of the tree.
    **/
    protected synchronized void clearChild (String index)
    {
        if (children == null) return;
        if (! isFromTopDocument ()) return;  // This node is not overridden, so none of the children will be.
        if (source.child (index) == null) return;  // The child is not overridden, so nothing to do.
        children.get (index).releaseOverride ();
        source.clear (index);
        clearPath ();

        MPart c = children.get (index);  // If child still exists, then it was overridden but exposed by the delete.
        if (c != null)
        {
            if (index.equals ("$inherit")) expand ();  // We changed our $inherit expression, so rebuild our subtree.
            else                         c.expand ();  // Otherwise, rebuild the subtree under the child.
        }
    }

    /**
        Remove any top document values from this node and its children.
    **/
    public synchronized void releaseOverride ()
    {
        if (! isFromTopDocument ()) return;  // This node is not overridden, so nothing to do.

        String key = source.key ();
        if (source == original)  // This node only exists in top doc, so it should be deleted entirely.
        {
            container.children.remove (key);
        }
        else  // This node is overridden, so release it.
        {
            releaseOverrideChildren ();
            source = original;
        }
        if (key.equals ("$inherit")) container.purge (this, null);
    }

    /**
        Assuming that source in the current node belongs to the top-level document, reset all overridden children back to their original state.
    **/
    public synchronized void releaseOverrideChildren ()
    {
        Iterator<MNode> i = source.iterator ();  // Implicitly, everything we iterate over will be from the top document.
        while (i.hasNext ())
        {
            String key = i.next ().key ();
            children.get (key).releaseOverride ();  // The key is guaranteed to be in our children collection.
            i.remove ();
        }
    }

    /**
        Extends the trail of overrides from the root to this node.
        Used to prepare this node for modification, where all edits must reside in top-level document.
        If this is a leaf node, then be sure to set a non-null value afterward. IE: leaf nodes should
        always be defined in documents.
    **/
    public synchronized void override ()
    {
        if (isFromTopDocument ()) return;
        // The only way to get past the above line is if original==source
        container.override ();
        source = container.source.set (null, key ());
    }

    /**
        Checks if any child is overridden. If so, then then current node must remain overridden as well.
    **/
    public synchronized boolean overrideNecessary ()
    {
        for (MNode c : this) if (((MPart) c).isFromTopDocument ()) return true;
        return false;
    }

    /**
        If an override is no longer needed on this node, reset it to its original state, and make
        a recursive call to our parent. This is effectively the anti-operation to override().
    **/
    public synchronized void clearPath ()
    {
        if (source != original  &&  (! source.data ()  ||  source.get ().equals (original.get ()))  &&  ! overrideNecessary ())
        {
            source.parent ().clear (source.key ());  // delete ourselves from the top-level document
            source = original;
            container.clearPath ();
        }
    }

    /**
        Changes value of this node in the top-level document, possibly creating
        of clearing a chain of overrides in parent nodes. Setting a value that
        exactly matches an inherited value will possibly clear overrides. Setting
        a value different from inherited will create an override.

        Setting null has a more subtle effect. It will change the top-level document
        as described above, possibly creating or clearing overrides. However, an
        undefined node in the top-level document allows an inherited value to show
        through in calls to get() and data(). Thus, setting null will not generally
        make this MPart node undefined.
    **/
    public synchronized void set (String value)
    {
        if (source.data () ? source.get ().equals (value) : value == null) return;  // No change, so nothing to do.
        boolean couldReset = original.data () ? original.get ().equals (value) : value == null;
        if (! couldReset) override ();
        source.set (value);
        if (couldReset) clearPath ();
        if (source.key ().equals ("$inherit"))  // We changed a $inherit node, so rebuild our subtree.
        {
            setIDs ();
            container.purge (this, null);  // Undo the effect we had on the subtree.
            container.expand ();
        }
    }

    public synchronized MNode set (String value, String index)
    {
        MPart result = null;
        if (children != null) result = children.get (index);
        if (result != null)
        {
            result.set (value);
            return result;
        }

        // We don't have the child, so by construction it is not in any source document.
        override ();  // ensures that source is a member of the top-level document tree
        MNode s = source.set (value, index);
        result = new MPart (this, null, s);
        if (children == null) children = new TreeMap<String,MPart> (comparator);
        children.put (index, result);
        if (index.equals ("$inherit"))  // We've created an $inherit line, so load the inherited equations.
        {
            result.setIDs ();
            // Purge is unnecessary because "result" is a new entry. There is no previous $inherit line.
            expand ();
        }
        return result;
    }

    /**
        Subroutine of set() which locates each parent and records its ID.
        Must only be called on an $inherit node in the top-level document.
    **/
    protected synchronized void setIDs ()
    {
        String[] parentNames = get ().split (",");
        if (parentNames.length == 0)
        {
            clear ("$metadata", "id");
            return;
        }

        List<String> newIDs = new ArrayList<String> (parentNames.length);
        for (int i = 0; i < parentNames.length; i++)
        {
            String parentName = parentNames[i];
            parentName = parentName.trim ().replace ("\"", "");
            MNode parentSource = models.get ().child (parentName);
            if (parentSource == null) newIDs.add ("");
            else                      newIDs.add (parentSource.get ("$metadata", "id"));
        }

        String id = newIDs.get (0);
        for (int i = 1; i < newIDs.size (); i++) id += "," + newIDs.get (i);
        set (id, "$metadata", "id");
    }

    /**
        Ensures that the minimal number of override nodes are created.
        Processes $inherit first, so that as other children are set, they are recognized as matching
        an inherited value when that is the case.
    **/
    public synchronized void merge (MNode that)
    {
        if (that.data ()) set (that.get ());

        // Process $inherit first
        MNode thatInherit = that.child ("$inherit");
        if (thatInherit != null)
        {
            MPart inherit = (MPart) getChild ("$inherit");
            boolean existing =  inherit != null;
            if (! existing) inherit = (MPart) childOrCreate ("$inherit");
            
            // Now do the equivalent of inherit.merge(thatInherit), but pay attention to IDs.
            // If "that" comes from an outside source, it could merge in IDs which disagree
            // with the ones we would otherwise look up during setIDs() called by set(). To honor the
            // imported IDs (that is, prioritize them over imported names), we merge the metadata
            // under $inherit first, then set the node itself in a way that avoids calling setIDs().
            for (MNode thatInheritChild : thatInherit)
            {
                String index = thatInheritChild.key ();
                MNode c = inherit.childOrCreate (index);
                c.merge (thatInheritChild);
            }
            String thatInheritValue = thatInherit.get ();
            if (! thatInheritValue.isEmpty ())
            {
                // This is a copy of set() with appropriate modifications.
                String thisInheritValue = inherit.source.get ();
                if (! thisInheritValue.equals (thatInheritValue))
                {
                    boolean couldReset = inherit.original.get ().equals (thatInheritValue);
                    if (! couldReset) inherit.override ();
                    inherit.source.set (thatInheritValue);
                    if (couldReset) inherit.clearPath ();
                    if (existing) purge (inherit, null);
                    expand ();
                }
            }
        }

        // Then the rest of the children
        for (MNode thatChild : that)
        {
            if (thatChild == thatInherit) continue;
            String index = thatChild.key ();
            MNode c = childOrCreate (index);
            c.merge (thatChild);
        }
    }

    /**
        Clears all top-level document nodes which exactly match the value they override.
        This is a utility function to support import and copy/paste. In general, internal
        models are kept in a clean state by set().
        @return true if the entire tree from this node down is free of top-level nodes.
    **/
    public synchronized boolean clearRedundantOverrides ()
    {
        boolean overrideNecessary = false;
        for (MNode c : this)
        {
            if (! ((MPart) c).clearRedundantOverrides ()) overrideNecessary = true;
        }

        if (source != original  &&  (! source.data ()  ||  source.get ().equals (original.get ())))
        {
            if (overrideNecessary)
            {
                source.set (null);
            }
            else
            {
                source.parent ().clear (source.key ());
                source = original;
            }
        }
        return ! isFromTopDocument ();
    }

    public synchronized void move (String fromIndex, String toIndex)
    {
        if (toIndex.equals (fromIndex)) return;
        clearChild (toIndex);  // By definition, no top-level document nodes are allowed to remain at the destination. However, underrides may exist.
        MPart fromPart = (MPart) getChild (fromIndex);
        if (fromPart == null) return;
        if (! fromPart.isFromTopDocument ()) return;  // We only move top-document nodes.

        MNode fromDoc = source.child (fromIndex);
        MNode toPart = getChild (toIndex);
        if (toPart == null)  // No node at the destination, so merge at level of top-document.
        {
            MNode toDoc = source.childOrCreate (toIndex);
            toDoc.merge (fromDoc);
            MPart c = new MPart (this, null, toDoc);
            children.put (toIndex, c);
            c.underrideChildren (null, toDoc);  // The sub-tree is empty, so all injected nodes are new. They don't really underride anything.
            c.expand ();
        }
        else  // Some existing underrides, so merge in collated tree. This is more expensive because it involves multiple calls to set().
        {
            toPart.merge (fromDoc);
        }
        clearChild (fromIndex);
    }

    /**
        For debugging the assembled tree.
    **/
    public synchronized void dump (String space)
    {
        String index = key ();
        String value = get ();
        if (value.isEmpty ())
        {
            System.out.print (String.format ("%s%s", space, index));
        }
        else
        {
            String newLine = String.format ("%n");
            value = value.split (newLine, 2)[0].trim ();
            System.out.print (String.format ("%s%s=%s", space, index, value));
        }
        System.out.println ("\t" + dumpHash (container) + "\t" + dumpHash (this) + "\t" + isFromTopDocument () + "\t" + dumpHash (source) + "\t" + dumpHash (original) + "\t" + dumpHash (inheritedFrom));

        String space2 = space + " ";
        for (MNode c : this) ((MPart) c).dump (space2);
    }

    public static String dumpHash (MNode part)
    {
        if (part == null) return null;
        return String.valueOf (part.hashCode ());
    }

    public synchronized Iterator<MNode> iterator ()
    {
        if (children == null) return super.iterator ();
        return new IteratorWrapper (new ArrayList<String> (children.keySet ()));
    }
}
