/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eqset;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.NavigableMap;
import java.util.TreeMap;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPersistent;
import gov.sandia.n2a.db.MVolatile;

/**
    Collates models following all the N2A language rules, and provides an interface
    for active editing. Also suitable for construction of EquationSets for execution.
    Implements the full MNode interface on the collated tree, as if it were nothing
    more than a regular MNode tree. Changes made through this interface cause changes
    in the original top-level document that would otherwise produce the resulting tree.
    Additional functions (outside the main MNode interface) support efficient
    construction.

    A key assumption of this implementation is that only MPersistent nodes are fed into it.

    All MNode functions are fully supported. In particular, merge() is safe to use, even
    if it involves $inherit lines.
**/
public class MPart extends MNode  // Could derive this from MVolatile, but the extra work of implementing from scratch is worth saving an unused member variable.
{
    protected MPersistent source;
    protected MPersistent original;        // The original source of this node, before it was overwritten by another document. Refers to same object as source if this node has not been overridden.
    protected MPart       inheritedFrom;   // Node in the tree that contains the $include statement that generated this node. Retained even if the node is overridden.

    protected MPart container;
    protected NavigableMap<String,MNode> children;

    /**
        Collates a full model from the given source document.
    **/
    public MPart (MPersistent source)
    {
        container       = null;
        this.source     = source;
        original        = source;
        inheritedFrom   = null;

        underrideChildren (null, source);
        expand ();
    }

    protected MPart (MPart container, MPart inheritedFrom, MPersistent source)
    {
        this.container       = container;
        this.source          = source;
        original             = source;
        this.inheritedFrom   = inheritedFrom;
    }

    /**
        Convenience method for expand(LinkedList<MPersistent>).
    **/
    public synchronized void expand ()
    {
        LinkedList<MPersistent> visited = new LinkedList<MPersistent> ();
        visited.push (getRoot ().source);
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
    public synchronized void expand (LinkedList<MPersistent> visited)
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
    public synchronized void inherit (LinkedList<MPersistent> visited)
    {
        if (children == null) return;
        MPart from = (MPart) children.get ("$inherit");
        if (from != null) inherit (visited, from, from.get ());
    }

    /**
        Injects inherited equations as children of this node.
        Handles recursion up the hierarchy of parents.
        @param visited Used to guard against a document loading itself.
        @param from The node in the collated tree (named "$inherit") which triggered the current
        round of inheritance. May be a child of a higher node, or a child of this node, but never a child
        of a lower node.
        @param value The RHS of the $inherit statement. We parse this into a set of part names
        which we retrieve from the database.
    **/
    public synchronized void inherit (LinkedList<MPersistent> visited, MPart from, String value)
    {
        String[] parentNames = value.split (",");
        for (String parentName : parentNames)
        {
            parentName = parentName.trim ().replace ("\"", "");
            MPersistent parentSource = (MPersistent) AppData.models.child (parentName);
            if (parentSource != null  &&  ! visited.contains (parentSource))
            {
                underrideChildren (from, parentSource);
                MPersistent parentFrom = (MPersistent) parentSource.child ("$inherit");
                if (parentFrom != null)
                {
                    visited.push (parentSource);
                    inherit (visited, from, parentFrom.get ());  // yes, we continue to treat the root "from" as the initiator for all the inherited equations
                    visited.pop ();
                }
            }
        }
    }

    /**
        Injects inherited equations at this node.
        Handles recursion down our containment hierarchy.
        This method only changes the node if it has no existing inheritedFrom value,
        so it is safe to run more than once for a given $inherit statement.
        @param newSource The current node in the source document which corresponds to this node in the MPart tree.
    **/
    public synchronized void underride (MPart from, MPersistent newSource)
    {
        if (inheritedFrom == null  &&  from != this)  // The second clause is for very peculiar case. We don't allow incoming $inherit lines to underride the $inherit that brought them in, since their existence is completely contingent on it.
        {
            inheritedFrom = from;
            original = newSource;
        }
        underrideChildren (from, newSource);
    }

    /**
        Injects inherited equations as children of this node.
        Handles recursion down our containment hierarchy.
        See note on underride(MPart,MPersistent). This is safe to run more than once for a given $inherit statement.
        @param newSource The current node in the source document which matches this node in the MPart tree.
    **/
    public synchronized void underrideChildren (MPart from, MPersistent newSource)
    {
        if (newSource.length () == 0) return;
        if (children == null) children = new TreeMap<String,MNode> (comparator);
        for (MNode n : newSource)
        {
            String key = n.key ();
            MPersistent p = (MPersistent) n;

            MPart c = (MPart) children.get (key);
            if (c == null)
            {
                c = new MPart (this, from, p);
                children.put (key, c);
                c.underrideChildren (from, p);
            }
            else
            {
                c.underride (from, p);
            }
        }
    }

    /**
        Remove any effects the $inherit line "from" had on this node and our children.
        @param parentIterator Enables us to delete ourselves from the containing collection.
        If null, this is the top node of the subtree to be purged, so we should not be
        deleted.
    **/
    public synchronized void purge (MPart from, Iterator<MNode> parentIterator)
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
        MPart inherit = (MPart) children.get ("$inherit");
        if (inherit != null  &&  inherit.inheritedFrom == from) purge (inherit, null);  // If our local $inherit is contingent on "from", then remove all its effects as well. Note that a $inherit line never comes from itself (inherit.inheritedFrom != inherit).

        Iterator<MNode> childIterator = iterator ();
        while (childIterator.hasNext ()) ((MPart) childIterator.next ()).purge (from, childIterator);
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
        String key = node.key ();
        if (key.equals ("$inherit"  )) return false;
        if (key.equals ("$metadata" )) return false;
        if (key.equals ("$reference")) return false;
        for (MNode c : node) if (c.key ().startsWith ("@")) return false;  // has the form of a multi-line equation
        return true;
    }

    public boolean isFromTopDocument ()
    {
        // There are only 3 cases allowed:
        // * original == source and inheritedFrom != null -- node holds an inherited value
        // * original == source and inheritedFrom == null -- node holds a top-level value
        // * original != source and inheritedFrom != null -- node holds a top-level value and an underride (inherited value)
        // The fourth case is excluded by the logic of this class (if it does its job right).
        return original != source  ||  inheritedFrom == null;
    }

    public MPart getParent ()
    {
        return container;
    }

    public MPart getRoot ()
    {
        MPart result = this;
        while (result.container != null) result = result.container;
        return result;
    }

    public MPersistent getSource ()
    {
        return source;
    }

    public synchronized MNode child (String index)
    {
        if (children == null) return null;
        return children.get (index);
    }

    public String key ()
    {
        return source.key ();  // could also use original.key(), as they should always match
    }

    public synchronized int length ()
    {
        if (children == null) return 0;
        return children.size ();
    }

    public synchronized String getOrDefault (String defaultValue)
    {
        return source.getOrDefault (defaultValue);
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
    public synchronized void clear (String index)
    {
        if (children == null) return;
        if (! isFromTopDocument ()) return;  // This node is not overridden, so none of the children will be.
        if (source.child (index) == null) return;  // The child is not overridden, so nothing to do.
        ((MPart) children.get (index)).releaseOverride ();
        source.clear (index);
        clearPath ();

        MPart c = (MPart) children.get (index);  // If child still exists, then it was overridden but exposed by the delete.
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
            ((MPart) children.get (key)).releaseOverride ();  // The key is guaranteed to be in our children collection.
            i.remove ();
        }
    }

    /**
        Extends the trail of overrides from the root to this node.
        Used to prepare this node for modification, where all edits must reside in top-level document.
    **/
    public synchronized void override ()
    {
        if (isFromTopDocument ()) return;
        // The only way to get past the above line is if original==source
        container.override ();
        source = (MPersistent) container.source.set (get (), key ());  // Most intermediate nodes will have a value of "", unless they are a variable.
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
        if (source != original  &&  source.get ().equals (original.get ())  &&  ! overrideNecessary ())
        {
            source.getParent ().clear (source.key ());  // delete ourselves from the top-level document
            source = original;
            container.clearPath ();
        }
    }

    public synchronized void set (String value)
    {
        if (source.get ().equals (value)) return;  // No change, so nothing to do.
        boolean couldReset = original.get ().equals (value);
        if (! couldReset) override ();
        source.set (value);
        if (couldReset) clearPath ();
        if (source.key ().equals ("$inherit"))  // We changed a $inherit node, so rebuild our subtree.
        {
            container.purge (this, null);  // Undo the effect we had on the subtree.
            container.expand ();
        }
    }

    public synchronized MNode set (String value, String index)
    {
        MPart result = null;
        if (children != null) result = (MPart) children.get (index);
        if (result != null)
        {
            result.set (value);
            return result;
        }

        // We don't have the child, so by construction it is not in any source document.
        override ();  // ensures that source is a member of the top-level document tree
        MPersistent s = (MPersistent) source.set (value, index);
        result = new MPart (this, null, s);
        if (children == null) children = new TreeMap<String,MNode> (comparator);
        children.put (index, result);
        if (index.equals ("$inherit"))  // We've created an $inherit line, so load the inherited equations.
        {
            // Purge is unnecessary because "result" is a new entry. There is no previous $inherit line.
            expand ();
        }
        return result;
    }

    public synchronized void move (String fromIndex, String toIndex)
    {
        clear (toIndex);  // By definition, no top-level document nodes are allowed to remain at the destination. However, underrides may exist.
        MPart fromPart = (MPart) child (fromIndex);
        if (fromPart == null) return;
        if (! fromPart.isFromTopDocument ()) return;  // We only move top-document nodes.

        MNode fromDoc = source.child (fromIndex);
        MNode toPart = child (toIndex);
        if (toPart == null)  // No node at the destination, so merge at level of top-document.
        {
            MPersistent toDoc = (MPersistent) source.set ("", toIndex);
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
        clear (fromIndex);
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
        if (children == null) return new MNode.IteratorEmpty ();
        return new MVolatile.IteratorWrapper (children.entrySet ().iterator ());
    }
}
