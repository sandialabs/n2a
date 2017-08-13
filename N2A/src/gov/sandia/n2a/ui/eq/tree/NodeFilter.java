/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class NodeFilter extends NodeContainer
{
    protected List<Integer> filtered;

    @Override
    public void filter (int filterLevel)
    {
        if (children == null)
        {
            filtered = null;
            return;
        }

        int count = children.size ();
        filtered = new Vector<Integer> (count);
        int childIndex = 0;
        for (Object o : children)
        {
            NodeBase c = (NodeBase) o;
            c.filter (filterLevel);
            c.invalidateTabs ();  // force columns to be updated for new subset of children
            if (c.visible (filterLevel)) filtered.add (childIndex);
            childIndex++;  // always increment
        }
        if (filtered.size () == count) filtered = null;  // all children are visible, so don't bother
    }

    @Override
    public List<Integer> getFiltered ()
    {
        return filtered;
    }

    @Override
    public void insertFiltered (int filteredIndex, int childrenIndex, boolean shift)
    {
        if (filtered == null)
        {
            if (filteredIndex == childrenIndex) return;  // the new entry does does not require instantiating "filtered", because the list continues to be exactly 1-to-1
            int count = children.size () - 1;
            filtered = new ArrayList<Integer> (count);
            for (int i = 0; i < count; i++) filtered.add (i);
        }

        if (filteredIndex >= 0)
        {
            filtered.add (filteredIndex, childrenIndex);  // effectively duplicates the entry at filteredIndex
            if (shift)
            {
                int count = filtered.size ();
                for (int i = filteredIndex + 1; i < count; i++) filtered.set (i, filtered.get (i).intValue () + 1);  // Shift child indices up by one, to account for the new entry added ahead of them.
            }
        }
        else // filteredIndex == -1
        {
            // Don't add element to filtered, since it is invisible, but still ripple up the child indices.
            if (shift)
            {
                int count = filtered.size ();
                for (int i = 0; i < count; i++)
                {
                    int index = filtered.get (i).intValue ();
                    if (index >= childrenIndex) filtered.set (i, index + 1);
                }
            }
        }
     }

    @Override
    public void removeFiltered (int filteredIndex, boolean shift)
    {
        if (filtered == null)
        {
            int count = children.size ();
            if (shift) count++;  // Because a child was removed before this function was called, our count for sizing "filtered" is one short.
            filtered = new ArrayList<Integer> (count);
            for (int i = 0; i < count; i++) filtered.add (i);
        }
        filtered.remove (filteredIndex);
        if (shift)  // Shift child indices down by 1 to account for entry removed ahead of them.
        {
            int count = filtered.size ();
            for (int i = filteredIndex; i < count; i++)  filtered.set (i, filtered.get (i).intValue () - 1);
        }
    }
}
