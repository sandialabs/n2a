/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.tree;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import gov.sandia.n2a.ui.eq.FilteredTreeModel;

@SuppressWarnings("serial")
public class NodeContainer extends NodeBase
{
    protected List<Integer>       filtered;
    protected List<List<Integer>> columnGroups;
    protected boolean             columnsValid;

    public void build ()
    {
    }

    // Filtering -------------------------------------------------------------

    @Override
    public void filter (int filterLevel)
    {
        invalidateColumns (null);  // force columns to be updated for new subset of children
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
            int count = children.size ();
            if (shift) count--;  // Because a child was added before this function was called, our count for sizing "filtered" is one too many.
            filtered = new ArrayList<Integer> (count);
            for (int i = 0; i < count; i++) filtered.add (i);
        }

        if (filteredIndex >= 0)
        {
            filtered.add (filteredIndex, childrenIndex);  // effectively duplicates the entry at filteredIndex
            if (shift)
            {
                int count = filtered.size ();
                for (int i = filteredIndex + 1; i < count; i++) filtered.set (i, filtered.get (i) + 1);  // Shift child indices up by one, to account for the new entry added ahead of them.
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
                    int index = filtered.get (i);
                    if (index >= childrenIndex) filtered.set (i, index + 1);
                }
            }
        }
    }

    @Override
    public void removeFiltered (int filteredIndex, int childrenIndex, boolean shift)
    {
        if (filtered == null)  // Currently no filtering
        {
            if (filteredIndex == childrenIndex) return;
            int count = children.size ();
            if (shift) count++;  // Because a child was removed before this function was called, our count for sizing "filtered" is one short.
            filtered = new ArrayList<Integer> (count);
            for (int i = 0; i < count; i++) filtered.add (i);
        }

        if (filteredIndex >= 0)
        {
            filtered.remove (filteredIndex);
            if (shift)  // Shift child indices down by 1 to account for entry removed ahead of them.
            {
                int count = filtered.size ();
                for (int i = filteredIndex; i < count; i++) filtered.set (i, filtered.get (i) - 1);
            }
        }
        else  // filteredIndex == -1, so element was invisible before removal
        {
            if (shift)
            {
                int count = filtered.size ();
                for (int i = 0; i < count; i++)
                {
                    int index = filtered.get (i);  // This will never be equal to childrenIndex, since element was invisible.
                    if (index > childrenIndex) filtered.set (i, index - 1);
                }
            }
        }
    }

    // Column alignment ------------------------------------------------------

    @Override
    public List<Integer> getMaxColumnWidths (int group, FontMetrics fm)
    {
        if (columnsValid)
        {
            if (group < columnGroups.size ()) return columnGroups.get (group);
            return null;
        }
        if (children == null) return null;

        List<Integer> indices = filtered;
        if (indices == null)
        {
            int count = children.size ();
            indices = new ArrayList<Integer> (count);
            for (int i = 0; i < count; i++) indices.add (i);
        }

        columnGroups = new ArrayList<List<Integer>> ();
        for (int index : indices)
        {
            NodeBase n = (NodeBase) children.get (index);
            List<Integer> columnWidths = n.getColumnWidths (fm);
            if (columnWidths == null) continue;

            int g = n.getColumnGroup ();
            while (columnGroups.size () <= g) columnGroups.add (new ArrayList<Integer> ());
            List<Integer> maxes = columnGroups.get (g);

            int i = 0;
            int columns = columnWidths.size ();
            int overlap = Math.min (columns, maxes.size ());
            for (; i < overlap; i++) maxes.set (i, Math.max (columnWidths.get (i), maxes.get (i)));
            for (; i < columns; i++) maxes.add (columnWidths.get (i));
        }

        columnsValid = true;
        if (group < columnGroups.size ()) return columnGroups.get (group);
        return null;
    }

    @Override
    public void invalidateColumns (FilteredTreeModel model)
    {
        columnsValid = false;
        super.invalidateColumns (model);
    }
}
