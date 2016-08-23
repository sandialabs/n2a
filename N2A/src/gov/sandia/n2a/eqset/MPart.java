/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eqset;

import java.util.Iterator;
import java.util.NavigableMap;
import java.util.TreeMap;

import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.db.MPersistent;
import gov.sandia.umf.platform.db.MVolatile;

/**
    Collates models following all the N2A language rules, and provides an interface
    for active editing. Also suitable for construction of EquationSets for execution.
    Implements the full MNode interface on the collated tree, as if it were nothing
    more than a regular MNode tree. Changes made through this interface cause changes
    in the original top-level document that would otherwise produce the resulting tree.
    Additional functions (outside the main MNode interface) support efficient
    construction.

    A key assumption of this implementation is that only MPersistent nodes are fed into it.
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
        expand (source);
    }

    protected MPart (MPart container, MPart inheritedFrom, MPersistent source)
    {
        this.container       = container;
        this.source          = source;
        original             = source;
        this.inheritedFrom   = inheritedFrom;
    }

    /**
        Loads all inherited structure into the sub-tree rooted at this node,
        using existing structure placed here by higher nodes as a starting point.
        The remainder of the tree is filled in by "underride". Assumes this sub-tree
        is a clean structure with only entries placed by higher levels, and no
        lingering structure that we might otherwise build now.
    **/
    public synchronized void expand ()
    {
        expand (getRoot ().source);
    }

    /**
        Loads all our inherited equations, then recurses down to each contained part.
        @param topDocument Used to guard against a document loading itself.
    **/
    public synchronized void expand (MPersistent topDocument)
    {
        inherit (topDocument);
        for (MNode n : this)
        {
            MPart p = (MPart) n;
            if (p.isPart ()) p.expand (topDocument);
        }
    }

    /**
        Initiates an underride load of all equations inherited by this node,
        using the current value of $inherit in our collated children.
        @param topDocument Used to guard against a document loading itself.
    **/
    public synchronized void inherit (MPersistent topDocument)
    {
        if (children == null) return;
        MPart from = (MPart) children.get ("$inherit");
        if (from != null) inherit (topDocument, from, from.get ());
    }

    /**
        Injects inherited equations as children of this node.
        Handles recursion up the hierarchy of parents.
        @param topDocument Used to guard against a document loading itself.
        @param from The node in the collated tree (named "$inherit") which triggered the current
        round of inheritance. May be a child of a higher node, or a child of this node, but never a child
        of a lower node.
        @param value The RHS of the $inherit statement. We parse this into a set of part names
        which we retrieve from the database.
    **/
    public synchronized void inherit (MPersistent topDocument, MPart from, String value)
    {
        String[] parenttNames = value.split (",");
        for (String parentName : parenttNames)
        {
            parentName = parentName.trim ().replace ("\"", "");
            MPersistent parentSource = (MPersistent) AppData.getInstance ().models.child (parentName);
            if (parentSource != null  &&  parentSource != topDocument)
            {
                underrideChildren (from, parentSource);
                MPersistent parentFrom = (MPersistent) parentSource.child ("$inherit");
                if (parentFrom != null) inherit (topDocument, from, parentFrom.get ());  // yes, we continue to treat the root "from" as the initiator for all the inherited equations
            }
        }
    }

    /**
        Injects inherited equations at this node.
        Handles recursion down our containment hierarchy.
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
        if (inherit != null  &&  inherit.inheritedFrom == from) purge (inherit, null);  // Note that in all cases, inherit.inheritedFrom != inherit

        Iterator<MNode> childIterator = iterator ();
        while (childIterator.hasNext ()) ((MPart) childIterator.next ()).purge (from, childIterator);
    }

    /**
        Incorporate a newly-created node of the document into the existing collated tree.
        Assumes this node represents the parent of the given source, and that none of our
        children represent the source itself.
    **/
    public synchronized MPart update (MPersistent source)
    {
        MPart c = new MPart (this, null, source);
        children.put (source.key (), c);
        c.underrideChildren (null, source);
        c.expand ();
        return c;
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
        Since node of the tests depend on actually being an MPart, this test is static so it can be applied to any MNode without casting.
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
        releaseOverrideChildren (true);
        clearPath ();
    }

    /**
        Removes the named child of this node from the top-level document, and restores it to its non-overridden state.
        The child may disappear, if it existed only due to override.
        In general, acts as if the clear is applied in the top-level document, followed by a full collation of the tree.
    **/
    public synchronized void clear (String index)
    {
        clear (index, true);
    }

    /**
        Version of clear(String) which allows user to suppress the actual deletion of top document nodes.
    **/
    public synchronized void clear (String index, boolean removeFromTopDocument)
    {
        if (children == null) return;
        if (! isFromTopDocument ()) return;  // This node is not overridden, so none of the children will be.
        if (source.child (index) == null) return;  // The child is not overridden, so nothing to do.
        ((MPart) children.get (index)).releaseOverride (removeFromTopDocument);
        if (removeFromTopDocument) source.clear (index);
        clearPath ();
    }

    /**
        Remove any top document values from this node and its children.
        @param removeFromTopDocument Indicates that the source node should be deleted from the top-level
        document, not simply removed from the collated tree. This gets passed to our children, but
        note that the caller is responsible for deleting this node's source.
    **/
    public synchronized void releaseOverride (boolean removeFromTopDocument)
    {
        if (! isFromTopDocument ()) return;  // This node is not overridden, so nothing to do.

        String key = source.key ();
        if (source == original)  // This node only exists in top doc, so it should be deleted entirely.
        {
            container.children.remove (key);
        }
        else  // This node is overridden, so release it.
        {
            releaseOverrideChildren (removeFromTopDocument);
            source = original;
        }
        if (key.equals ("$inherit")) container.purge (this, null);
    }

    /**
        Assuming that source in the current node belongs to the top-level document, reset all overridden children back to their original state.
    **/
    public synchronized void releaseOverrideChildren (boolean removeFromTopDocument)
    {
        Iterator<MNode> i = source.iterator ();  // Implicitly, everything we iterate over will be from the top document.
        while (i.hasNext ())
        {
            String key = i.next ().key ();
            ((MPart) children.get (key)).releaseOverride (removeFromTopDocument);  // The key is guaranteed to be in our children collection.
            if (removeFromTopDocument) i.remove ();
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
            // Any parts and equations that might already exist in this subtree take precedence over the
            // newly added $inherit and the structure/equations it brings in. Thus, we can only add
            // structure/equations, not remove or change them.
            expand ();
        }
        return result;
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