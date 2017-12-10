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
    public Node    root;
    public float[] lo;
    public float[] hi;

    public int   bucketSize;
    public int   k;
    public float radius;   // Maximum distance between query point and any result point. Initially set to INFINITY by constructor.
    public float epsilon;  // Nodes must have at least this much overlap with the current radius (which is always the lesser of the initial radius and the kth nearest neighbor).
    public int   maxNodes; // Expand no more than this number of nodes. Forces a search to be approximate rather than exhaustive.

    public KDTree ()
    {
        bucketSize = 5;
        k          = 5;  // it doesn't make sense for k to be less than bucketSize
        radius     = Float.POSITIVE_INFINITY;
        epsilon    = 1e-4f;
        maxNodes   = Integer.MAX_VALUE;
    }

    /**
        @param data Must not be changed by the caller during the lifetime of this KDTree.
    **/
    public void set (List<float[]> data)
    {
        int dimensions = data.get (0).length;
        lo = new float[dimensions];
        hi = new float[dimensions];
        for (int i = 0; i < dimensions; i++)
        {
            lo[i] = Float.POSITIVE_INFINITY;
            hi[i] = Float.NEGATIVE_INFINITY;
        }

        for (float[] t : data)
        {
            for (int i = 0; i < dimensions; i++)
            {
                lo[i] = Math.min (lo[i], t[i]);
                hi[i] = Math.max (hi[i], t[i]);
            }
        }

        root = construct (data);
    }

    public void find (float[] query, List<float[]> result)
    {
        // Determine distance of query from bounding rectangle for entire tree
        int dimensions = query.length;
        float distance = 0;
        for (int i = 0; i < dimensions; i++)
        {
            float d = Math.max (0.0f, lo[i] - query[i]) + Math.max (0.0f, query[i] - hi[i]);
            distance += d * d;
        }

        // Recursively collect closest points
        Query q = new Query ();
        q.k      = k;
        q.radius = radius * radius;  // this may shrink monotonically once we find enough neighbors
        q.point  = query;

        float oneEpsilon = (1 + epsilon) * (1 + epsilon);
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

        // Transfer results to vector. No need to limit number of results, becaus this has
        // already been done by Leaf::search().
        result = new ArrayList<float[]> (q.sorted.size ());
        for (Sortable<float[]> sit : q.sorted)
        {
            result.add (sit.value);
        }
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

    public Node construct (List<float[]> points)
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
            float longest = 0;
            for (int i = 0; i < dimensions; i++)
            {
                float length = hi[i] - lo[i];
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
            result.mid = points.get (cut)[d];

            hi[d] = result.mid;
            result.lowNode = construct (points.subList (0, cut));
            hi[d] = result.hi;

            lo[d] = result.mid;
            result.highNode = construct (points.subList (cut, count));
            lo[d] = result.lo;  // it is important to restore lo[d] so that when recursion unwinds the vector is still correct

            return result;
        }
    }

    public void sort (List<float[]> points, int dimension)
    {
        TreeSet<Sortable<float[]>> sorted = new TreeSet<Sortable<float[]>> ();
        for (float[] it : points)
        {
            sorted.add (new Sortable<float[]> (it[dimension], it));
        }

        int i = 0;
        for (Sortable<float[]> sit : sorted)
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
        public int     k;
        public float   radius;
        public float[] point;
        public TreeSet<Sortable<float[]>> sorted = new TreeSet<Sortable<float[]>> ();  // points
        public PriorityQueue<Sortable<Node>> queue = new PriorityQueue<Sortable<Node>> ();
    }

    public static class Sortable<T> implements Comparable<Sortable<T>>
    {
        public float  key;
        public T      value;

        public Sortable (float key, T value)
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
        public void search (float distance, Query q);
        public void dump (String pad);
    }

    public static class Branch implements Node
    {
        public int   dimension;
        public float lo;       // Lowest value along the dimension
        public float hi;       // Highest value along the dimension
        public float mid;      // The cut point along the dimension
        public Node  lowNode;  // below mid
        public Node  highNode; // above mid

        public void search (float distance, Query q)
        {
            float qmid = q.point[dimension];
            float newOffset = qmid - mid;
            if (newOffset < 0)  // lowNode is closer
            {
                // We don't do any special testing on nearer node, because it has already been
                // tested as part of the containing node.
                if (lowNode != null) lowNode.search (distance, q);
                if (highNode != null)
                {
                    float oldOffset = Math.max (lo - qmid, 0.0f);
                    distance += newOffset * newOffset - oldOffset * oldOffset;
                    q.queue.add (new Sortable<Node> (distance, highNode));
                }
            }
            else  // newOffset >= 0, so highNode is closer
            {
                if (highNode != null) highNode.search (distance, q);
                if (lowNode != null)
                {
                    float oldOffset = Math.max (qmid - hi, 0.0f);
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
        public List<float[]> points;

        public void search (float distance, Query q)
        {
            int dimensions = points.get (0).length;
            for (float[] p : points)
            {
                // Measure distance using early-out method. Might save operations in
                // high-dimensional spaces.
                float total = 0;
                for (int i = 0; i < dimensions  &&  total < q.radius; i++)
                {
                    float t = p[i] - q.point[i];
                    total += t * t;
                }

                if (total >= q.radius) continue;
                q.sorted.add (new Sortable<float[]> (total, p));
                if (q.sorted.size () > q.k)
                {
                    // Remove the last (most distant) entry.
                    Iterator<Sortable<float[]>> it = q.sorted.descendingIterator ();
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
