/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeSet;

/**
    Retrieves items in space near a given query.
    This implementation is adapted from the C++ version in FL, which in turn is based loosely on the paper
    "Algorithms for Fast Vector Quantization" by Sunil Arya and David Mount.
**/
public class KDTree
{
    public Node     root;
    public double[] lo;
    public double[] hi;

    public int    bucketSize;
    public int    k;
    public double radius;   // Maximum distance between query point and any result point. Initially set to INFINITY by constructor.
    public double epsilon;  // Nodes must have at least this much overlap with the current radius (which is always the lesser of the initial radius and the kth nearest neighbor).
    public int    maxNodes; // Expand no more than this number of nodes. Forces a search to be approximate rather than exhaustive.

    public KDTree ()
    {
        bucketSize = 5;
        k          = 5;  // it doesn't make sense for k to be less than bucketSize
        radius     = Double.POSITIVE_INFINITY;
        epsilon    = 1e-4;
        maxNodes   = Integer.MAX_VALUE;
    }

    /**
        @param data Must not be changed by the caller during the lifetime of this KDTree.
    **/
    public void set (List<Entry> data)
    {
        int dimensions = data.get (0).point.length;
        lo = new double[dimensions];
        hi = new double[dimensions];
        for (int i = 0; i < dimensions; i++)
        {
            lo[i] = Double.POSITIVE_INFINITY;
            hi[i] = Double.NEGATIVE_INFINITY;
        }

        for (Entry e : data)
        {
            double[] t = e.point;
            for (int i = 0; i < dimensions; i++)
            {
                lo[i] = Math.min (lo[i], t[i]);
                hi[i] = Math.max (hi[i], t[i]);
            }
        }

        root = construct (data);
    }

    public List<Entry> find (double[] query)
    {
        // Determine distance of query from bounding rectangle for entire tree
        int dimensions = query.length;
        double distance = 0;
        for (int i = 0; i < dimensions; i++)
        {
            double d = Math.max (0, lo[i] - query[i]) + Math.max (0, query[i] - hi[i]);
            distance += d * d;
        }

        // Recursively collect closest points
        Query q = new Query ();
        q.k      = k;
        q.radius = radius * radius;  // this may shrink monotonically once we find enough neighbors
        q.point  = query;

        double oneEpsilon = (1 + epsilon) * (1 + epsilon);
        q.queue.add (new Sortable<Node> (distance, root));
        int visited = 0;
        while (true)
        {
            Sortable<Node> it = q.queue.poll ();
            if (it == null) break;
            distance = it.key;
            if (distance * oneEpsilon > q.radius) break;
            it.value.search (distance, q);
            if (++visited >= maxNodes) break;
        }

        // Transfer results to vector. No need to limit number of results, because this has
        // already been done by Leaf::search().
        List<Entry> result = new ArrayList<Entry> (q.sorted.size ());
        for (Sortable<Entry> sit : q.sorted) result.add (sit.value);
        return result;
    }

    public void dump (String pad)
    {
        System.out.println (pad + "KDTree: " + bucketSize + " " + k + " " + radius + " " + epsilon);
        System.out.println (pad + "lo = " + lo);
        System.out.println (pad + "hi = " + hi);
        if (root != null)
        {
            System.out.println (pad + "root:");
            root.dump (pad + "  ");
        }
    }

    public Node construct (List<Entry> points)
    {
        int count = points.size ();
        if (count == 0) return null;
        if (count <= bucketSize)
        {
            Leaf result = new Leaf ();
            result.points = points;
            return result;
        }
        else  // count > bucketSize
        {
            // TODO: accept the split method as a parameter
            int dimensions = lo.length;
            int d = 0;
            double longest = 0;
            for (int i = 0; i < dimensions; i++)
            {
                double length = hi[i] - lo[i];
                if (length > longest)
                {
                    d = i;
                    longest = length;
                }
            }
            sort (points, d);
            int cut = count / 2;

            Branch result = new Branch ();
            result.dimension = d;
            result.lo = lo[d];
            result.hi = hi[d];
            result.mid = points.get (cut).point[d];

            hi[d] = result.mid;
            result.lowNode = construct (points.subList (0, cut));
            hi[d] = result.hi;

            lo[d] = result.mid;
            result.highNode = construct (points.subList (cut, count));
            lo[d] = result.lo;  // it is important to restore lo[d] so that when recursion unwinds the vector is still correct

            return result;
        }
    }

    public void sort (List<Entry> points, int dimension)
    {
        TreeSet<Sortable<Entry>> sorted = new TreeSet<Sortable<Entry>> ();
        for (Entry it : points)
        {
            sorted.add (new Sortable<Entry> (it.point[dimension], it));
        }

        int i = 0;
        for (Sortable<Entry> sit : sorted)
        {
            points.set (i++, sit.value);
        }
    }

    public static class Entry
    {
        public double[] point;
        public Object   item;
    }

    /// Internal helper class for passing search-related info down the tree.
    public static class Query
    {
        public int      k;
        public double   radius;
        public double[] point;
        public TreeSet<Sortable<Entry>>      sorted = new TreeSet<Sortable<Entry>> ();
        public PriorityQueue<Sortable<Node>> queue  = new PriorityQueue<Sortable<Node>> ();
    }

    public static class Sortable<T> implements Comparable<Sortable<T>>
    {
        public double  key;
        public T      value;

        public Sortable (double key, T value)
        {
            this.key   = key;
            this.value = value;
        }

        public int compareTo (Sortable<T> that)
        {
            if (key < that.key) return -1;
            if (key > that.key) return  1;
            return value.hashCode () - that.hashCode ();
        }

        @SuppressWarnings("unchecked")
        public boolean equals (Object that)
        {
            if (this == that) return true;
            if (that instanceof Sortable<?>) return compareTo ((Sortable<T>) that) == 0;
            return false;
        }
    }

    public static interface Node
    {
        public void search (double distance, Query q);
        public void dump (String pad);
    }

    public static class Branch implements Node
    {
        public int    dimension;
        public double lo;       // Lowest value along the dimension
        public double hi;       // Highest value along the dimension
        public double mid;      // The cut point along the dimension
        public Node   lowNode;  // below mid
        public Node   highNode; // above mid

        public void search (double distance, Query q)
        {
            double qmid = q.point[dimension];
            double newOffset = qmid - mid;
            if (newOffset < 0)  // lowNode is closer
            {
                // We don't do any special testing on nearer node, because it has already been
                // tested as part of the containing node.
                if (lowNode != null) lowNode.search (distance, q);
                if (highNode != null)
                {
                    double oldOffset = Math.max (lo - qmid, 0.0f);
                    distance += newOffset * newOffset - oldOffset * oldOffset;
                    q.queue.add (new Sortable<Node> (distance, highNode));
                }
            }
            else  // newOffset >= 0, so highNode is closer
            {
                if (highNode != null) highNode.search (distance, q);
                if (lowNode != null)
                {
                    double oldOffset = Math.max (qmid - hi, 0.0f);
                    distance += newOffset * newOffset - oldOffset * oldOffset;
                    q.queue.add (new Sortable<Node> (distance, lowNode));
                }
            }
        }

        public void dump (String pad)
        {
            System.out.println (pad + "Branch: " + dimension + " " + lo + " " + mid + " " + hi);
            if (lowNode != null)
            {
                System.out.println (pad + "lowNode:");
                lowNode.dump (pad + "  ");
            }
            if (highNode != null)
            {
                System.out.println (pad + "highNode:");
                highNode.dump (pad + "  ");
            }
        }
    }

    public static class Leaf implements Node
    {
        public List<Entry> points;

        public void search (double distance, Query q)
        {
            int dimensions = points.get (0).point.length;
            for (Entry p : points)
            {
                // Measure distance using early-out method. Might save operations in
                // high-dimensional spaces.
                double total = 0;
                for (int i = 0; i < dimensions  &&  total < q.radius; i++)
                {
                    double t = p.point[i] - q.point[i];
                    total += t * t;
                }

                if (total >= q.radius) continue;
                q.sorted.add (new Sortable<Entry> (total, p));
                if (q.sorted.size () > q.k)
                {
                    // Remove the last (most distant) entry.
                    Iterator<Sortable<Entry>> it = q.sorted.descendingIterator ();
                    it.next ();
                    it.remove ();
                }
                if (q.sorted.size () == q.k) q.radius = Math.min (q.radius, q.sorted.descendingIterator ().next ().key);
            }
        }

        public void dump (String pad)
        {
            for (int i = 0; i < points.size (); i++) System.out.println (pad + points.get (i));
        }
    }
}
