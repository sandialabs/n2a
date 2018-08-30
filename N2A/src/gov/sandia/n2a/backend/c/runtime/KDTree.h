/*
Nearest-neighbor lookup.
This code was adapted from the FL library and specialized for the 3D
spaces common in neural systems.
The implementation is based loosely on the paper "Algorithms for Fast Vector
Quantization" by Sunil Arya and David Mount.

Copyright 2010-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef kdtree_h
#define kdtree_h


#include "fl/matrix.h"

#include <vector>
#include <queue>


template<class T>
class Vector3 : public fl::MatrixFixed<T,3,1>
{
};

template<class T> class Part;

template<class T>
class KDTreeEntry : public Vector3<T>
{
public:
    Part<T> * part;
};

template<class T>
class KDTree
{
public:
    // Note: All the inner classes are defined first, then the main class members come at the bottom of this file.

    class Node;

    typedef std::pair<T, Node *>           PairNode;
    typedef std::pair<T, KDTreeEntry<T> *> PairEntry;

    class Reverse
    {
    public:
        bool operator() (const PairNode & a, const PairNode & b) const
        {
            return a.first > b.first;
        }
    };

    class Forward
    {
    public:
        bool operator() (const PairEntry & a, const PairEntry & b) const
        {
            return a.first < b.first;
        }
    };

    /// Helper class for passing search-related info down the tree.
    class Query
    {
    public:
        int                k;
        T                  radius;
        const Vector3<T> * point;
        std::priority_queue<PairEntry, std::vector<PairEntry>, Forward> sorted;
        std::priority_queue<PairNode,  std::vector<PairNode>,  Reverse> queue;
    };

    class Node
    {
    public:
        virtual ~Node () {}
        virtual void search (T distance, Query & q) const = 0;
#       ifndef N2A_SPINNAKER
        virtual void dump (std::ostream & out, const String & pad = "") const = 0;
#       endif
    };

    class Branch : public Node
    {
    public:
        int    dimension;
        T      lo;        ///< Lowest value along the dimension
        T      hi;        ///< Highest value along the dimension
        T      mid;       ///< The cut point along the dimension
        Node * lowNode;   ///< below mid
        Node * highNode;  ///< above mid

        virtual ~Branch ()
        {
            delete lowNode;
            delete highNode;
        }

        virtual void search (T distance, Query & q) const
        {
            T qmid = (*q.point)[dimension];
            T newOffset = qmid - mid;
            if (newOffset < 0)  // lowNode is closer
            {
                // We don't do any special testing on nearer node, because it has already been
                // tested as part of the containing node.
                if (lowNode) lowNode->search (distance, q);
                if (highNode)
                {
                    T oldOffset = std::max (lo - qmid, (T) 0);
                    distance += newOffset * newOffset - oldOffset * oldOffset;
                    q.queue.push (std::make_pair (distance, highNode));
                }
            }
            else  // newOffset >= 0, so highNode is closer
            {
                if (highNode) highNode->search (distance, q);
                if (lowNode)
                {
                    T oldOffset = std::max (qmid - hi, (T) 0);
                    distance += newOffset * newOffset - oldOffset * oldOffset;
                    q.queue.push (std::make_pair (distance, lowNode));
                }
            }
        }

#       ifndef N2A_SPINNAKER
        virtual void dump (std::ostream & out, const String & pad = "") const
        {
            out << pad << "Branch: " << dimension << " " << lo << " " << mid << " " << hi << std::endl;
            if (lowNode)
            {
                out << pad << "lowNode:" << std::endl;
                lowNode->dump (out, pad + "  ");
            }
            if (highNode)
            {
                out << pad << "highNode:" << std::endl;
                highNode->dump (out, pad + "  ");
            }
        }
#       endif
    };

    class Leaf : public Node
    {
    public:
        std::vector<KDTreeEntry<T> *> points;

        virtual void search (T distance, Query & q) const
        {
            int count = points.size ();
            int dimensions = points[0]->rows ();
            for (int i = 0; i < count; i++)
            {
                KDTreeEntry<T> * p = points[i];

                // Measure distance using early-out method. Might save operations in
                // high-dimensional spaces.
                // Here we make the assumption that the values are stored contiguously in
                // memory.  This is a good place to check for bugs if using more exotic
                // matrix types (not recommended).
                T * x = &(*p)[0];
                T * y = &(*q.point)[0];
                T * end = x + dimensions;
                T total = 0;
                while (x < end  &&  total < q.radius)
                {
                    T t = *x++ - *y++;
                    total += t * t;
                }

                if (total >= q.radius) continue;
                q.sorted.push (std::make_pair (total, p));
                if (q.sorted.size () > q.k) q.sorted.pop ();
                if (q.sorted.size () == q.k) q.radius = std::min (q.radius, q.sorted.top ().first);
            }
        }

#       ifndef N2A_SPINNAKER
        virtual void dump (std::ostream & out, const String & pad = "") const
        {
            for (int i = 0; i < points.size (); i++) out << pad << *points[i] << std::endl;
        }
#       endif
    };

    Node * root;
    Vector3<T> lo;
    Vector3<T> hi;

    int bucketSize;
    int k;
    T   radius;   ///< Maximum distance between query point and any result point. Initially set to INFINITY by constructor.
    T   epsilon;  ///< Nodes must have at least this much overlap with the current radius (which is always the lesser of the initial radius and the kth nearest neighbor).
    int maxNodes; ///< Expand no more than this number of nodes. Forces a search to be approximate rather than exhaustive.

    KDTree ()
    {
        root       = 0;
        bucketSize = 5;
        k          = 5;  // it doesn't make sense for k to be less than bucketSize
        radius     = INFINITY;
        epsilon    = 1e-4;
        maxNodes   = INT_MAX;
    }

    ~KDTree ()
    {
        clear ();
    }

    void clear ()
    {
        delete root;
        root = 0;
    }

    void set (std::vector<KDTreeEntry<T> *> & data)
    {
        fl::clear (lo,  INFINITY);
        fl::clear (hi, -INFINITY);

        typename std::vector<KDTreeEntry<T> *>::iterator t = data.begin ();
        for (; t != data.end (); t++)
        {
            T * a = (*t)->base ();
            T * l = lo.base ();
            T * h = hi.base ();
            T * end = l + 3;  // 3 dimensions
            while (l < end)
            {
                *l = std::min (*l, *a);
                *h = std::max (*h, *a);
                a++;
                l++;
                h++;
            }
        }

        root = construct (data);
    }

    void find (const Vector3<T> & query, std::vector<KDTreeEntry<T> *> & result) const
    {
        // Determine distance of query from bounding rectangle for entire tree
        int dimensions = query.rows ();
        T distance = 0;
        for (int i = 0; i < dimensions; i++)
        {
            T d = std::max ((T) 0, lo[i] - query[i]) + std::max ((T) 0, query[i] - hi[i]);
            distance += d * d;
        }

        // Recursively collect closest points
        Query q;
        q.k      = k;
        q.radius = radius * radius;  // this may shrink monotonically once we find enough neighbors
        q.point  = &query;

        T oneEpsilon = (1 + epsilon) * (1 + epsilon);
        q.queue.push (std::make_pair (distance, root));
        int visited = 0;
        while (q.queue.size ())
        {
            const PairNode & it = q.queue.top ();
            distance = it.first;
            Node * n = it.second;
            q.queue.pop ();
            if (distance * oneEpsilon > q.radius) break;
            n->search (distance, q);
            if (++visited >= maxNodes) break;
        }

        // Transfer results to vector. No need to limit number of results, becaus this has
        // already been done by Leaf::search().
        int count = q.sorted.size ();
        result.resize (count);
        for (int i = count - 1; i >= 0; i--)
        {
            result[i] = q.sorted.top ().second;
            q.sorted.pop ();
        }
    }

#   ifndef N2A_SPINNAKER
    void dump (std::ostream & out, const String & pad = "") const
    {
        out << pad << "KDTree: " << bucketSize << " " << k << " " << radius << " " << epsilon << std::endl;
        out << pad << "lo = " << lo << std::endl;
        out << pad << "hi = " << hi << std::endl;
        if (root)
        {
            out << pad << "root:" << std::endl;
            root->dump (out, pad + "  ");
        }
    }
#   endif

    /// Recursively construct a tree that handles the given volume of points.
    Node * construct (std::vector<KDTreeEntry<T> *> & points)
    {
        int count = points.size ();
        if (count == 0)
        {
            return 0;
        }
        else if (count <= bucketSize)
        {
            Leaf * result = new Leaf;
            result->points = points;
            return result;
        }
        else  // count > bucketSize
        {
            // todo: pass the split method as a function pointer
            int dimensions = lo.rows ();
            int d = 0;
            T longest = 0;
            for (int i = 0; i < dimensions; i++)
            {
                T length = hi[i] - lo[i];
                if (length > longest)
                {
                    d = i;
                    longest = length;
                }
            }
            sort (points, d);
            int cut = count / 2;
            typename std::vector<KDTreeEntry<T> *>::iterator b = points.begin ();
            typename std::vector<KDTreeEntry<T> *>::iterator c = b + cut;
            typename std::vector<KDTreeEntry<T> *>::iterator e = points.end ();

            Branch * result = new Branch;
            result->dimension = d;
            result->lo = lo[d];
            result->hi = hi[d];
            result->mid = (**c)[d];

            hi[d] = result->mid;
            std::vector<KDTreeEntry<T> *> tempPoints (b, c);
            result->lowNode = construct (tempPoints);
            hi[d] = result->hi;

            lo[d] = result->mid;
            tempPoints.clear ();
            tempPoints.insert (tempPoints.begin (), c, e);
            result->highNode = construct (tempPoints);
            lo[d] = result->lo;  // it is important to restore lo[d] so that when recursion unwinds the vector is still correct

            return result;
        }
    }

    /// Rearrange points so they are in ascending order along the given dimension.
    void sort (std::vector<KDTreeEntry<T> *> & points, int dimension)
    {
        // Effectively we do a heap sort, since priority_queue is typically implemented with a heap structure.
        std::priority_queue<PairEntry, std::vector<PairEntry>, Forward> sorted;
        typename std::vector<KDTreeEntry<T> *>::iterator it = points.begin ();
        for (; it != points.end (); it++)
        {
            sorted.push (std::make_pair ((**it)[dimension], *it));
        }

        int count = sorted.size ();
        for (int i = count - 1; i >= 0; i--)
        {
            points[i] = sorted.top ().second;
            sorted.pop ();
        }
    }
};


#endif
