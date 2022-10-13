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


#include "matrix.h"
#include "math.h"

#include <vector>
#include <queue>
#include <cmath>
#include <limits.h>


template<class T> class Part;

template<class T>
class KDTree
{
public:
    // Note: All the inner classes are defined first, then the main class members come at the bottom of this file.

    typedef MatrixFixed<T,3,1> Vector3;

    class Entry : public Vector3
    {
    public:
        Part<T> * part;
    };

    class Node;

    typedef std::pair<T, Node *>  PairNode;
    typedef std::pair<T, Entry *> PairEntry;

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
        int             k;
        T               radius;
        const Vector3 * point;
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
        std::vector<Entry *> points;

        virtual void search (T distance, Query & q) const
        {
            int count = points.size ();
            for (int i = 0; i < count; i++)
            {
                Entry * p = points[i];

                // Measure distance using early-out method. Might save operations in
                // high-dimensional spaces.
                // Here we make the assumption that the values are stored contiguously in
                // memory.  This is a good place to check for bugs if using more exotic
                // matrix types (not recommended).
                T * x = &(*p)[0];
                T * y = &(*q.point)[0];
                T * end = x + 3;  // 3 dimensions in Vector3
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
    Vector3 lo;
    Vector3 hi;

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

    void set (std::vector<Entry *> & data)
    {
        ::clear (lo, (T)  INFINITY);
        ::clear (hi, (T) -INFINITY);

        typename std::vector<Entry *>::iterator t = data.begin ();
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

    void find (const Vector3 & query, std::vector<Entry *> & result) const
    {
        // Determine distance of query from bounding rectangle for entire tree
        T distance = 0;
        for (int i = 0; i < 3; i++)
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

        // Transfer results to vector. No need to limit number of results, because this has
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
    Node * construct (std::vector<Entry *> & points)
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
            int d = 0;
            T longest = 0;
            for (int i = 0; i < 3; i++)
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
            typename std::vector<Entry *>::iterator b = points.begin ();
            typename std::vector<Entry *>::iterator c = b + cut;
            typename std::vector<Entry *>::iterator e = points.end ();

            Branch * result = new Branch;
            result->dimension = d;
            result->lo = lo[d];
            result->hi = hi[d];
            result->mid = (**c)[d];

            hi[d] = result->mid;
            std::vector<Entry *> tempPoints (b, c);
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
    void sort (std::vector<Entry *> & points, int dimension)
    {
        // Effectively we do a heap sort, since priority_queue is typically implemented with a heap structure.
        std::priority_queue<PairEntry, std::vector<PairEntry>, Forward> sorted;
        typename std::vector<Entry *>::iterator it = points.begin ();
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

#ifdef n2a_FP

template<>
class KDTree<int>
{
public:
    typedef MatrixFixed<int,3,1> Vector3;

    class Entry : public Vector3
    {
    public:
        Part<int> * part;
    };

    class Node;

    typedef std::pair<int64_t, Node *>  LongNode;
    typedef std::pair<int64_t, Entry *> LongEntry;
    typedef std::pair<int,     Entry *> ShortEntry;

    class LongReverse
    {
    public:
        bool operator() (const LongNode & a, const LongNode & b) const
        {
            return a.first > b.first;
        }
    };

    class LongForward
    {
    public:
        bool operator() (const LongEntry & a, const LongEntry & b) const
        {
            return a.first < b.first;
        }
    };

    class ShortForward
    {
    public:
        bool operator() (const ShortEntry & a, const ShortEntry & b) const
        {
            return a.first < b.first;
        }
    };

    /// Helper class for passing search-related info down the tree.
    class Query
    {
    public:
        int             k;
        int64_t         radius;
        const Vector3 * point;
        std::priority_queue<LongEntry, std::vector<LongEntry>, LongForward> sorted;
        std::priority_queue<LongNode,  std::vector<LongNode>,  LongReverse> queue;
    };

    class Node
    {
    public:
        virtual ~Node () {}
        virtual void search (int64_t distance, Query & q) const = 0;
    };

    class Branch : public Node
    {
    public:
        int    dimension;
        int    lo;        ///< Lowest value along the dimension
        int    hi;        ///< Highest value along the dimension
        int    mid;       ///< The cut point along the dimension
        Node * lowNode;   ///< below mid
        Node * highNode;  ///< above mid

        virtual ~Branch ()
        {
            delete lowNode;
            delete highNode;
        }

        virtual void search (int64_t distance, Query & q) const
        {
            int qmid = (*q.point)[dimension];
            int newOffset = qmid - mid;
            if (newOffset < 0)  // lowNode is closer
            {
                // We don't do any special testing on nearer node, because it has already been
                // tested as part of the containing node.
                if (lowNode) lowNode->search (distance, q);
                if (highNode)
                {
                    int oldOffset = std::max (lo - qmid, 0);
                    distance += (int64_t) newOffset * newOffset - (int64_t) oldOffset * oldOffset;
                    q.queue.push (std::make_pair (distance, highNode));
                }
            }
            else  // newOffset >= 0, so highNode is closer
            {
                if (highNode) highNode->search (distance, q);
                if (lowNode)
                {
                    int oldOffset = std::max (qmid - hi, 0);
                    distance += (int64_t) newOffset * newOffset - (int64_t) oldOffset * oldOffset;
                    q.queue.push (std::make_pair (distance, lowNode));
                }
            }
        }
    };

    class Leaf : public Node
    {
    public:
        std::vector<Entry *> points;

        virtual void search (int64_t distance, Query & q) const
        {
            int count = points.size ();
            for (int i = 0; i < count; i++)
            {
                Entry * p = points[i];

                // Measure distance using early-out method. Might save operations in
                // high-dimensional spaces.
                // Here we make the assumption that the values are stored contiguously in
                // memory.  This is a good place to check for bugs if using more exotic
                // matrix types (not recommended).
                int * x = &(*p)[0];
                int * y = &(*q.point)[0];
                int * end = x + 3;
                int64_t total = 0;
                while (x < end  &&  total < q.radius)
                {
                    int t = *x++ - *y++;
                    total += (int64_t) t * t;
                }

                if (total >= q.radius) continue;
                q.sorted.push (std::make_pair (total, p));
                if (q.sorted.size () > q.k) q.sorted.pop ();
                if (q.sorted.size () == q.k) q.radius = std::min (q.radius, q.sorted.top ().first);
            }
        }
    };

    Node * root;
    Vector3 lo;
    Vector3 hi;

    int bucketSize;
    int k;
    int radius;   ///< Maximum distance between query point and any result point. Initially set to INFINITY by constructor.
    int epsilon;  ///< Nodes must have at least this much overlap with the current radius (which is always the lesser of the initial radius and the kth nearest neighbor).
    int maxNodes; ///< Expand no more than this number of nodes. Forces a search to be approximate rather than exhaustive.

    KDTree ()
    {
        root       = 0;
        bucketSize = 5;
        k          = 5;  // it doesn't make sense for k to be less than bucketSize
        radius     = INFINITY;
        epsilon    = 0x3;  // exponent=MSB/2; (1<<MSB/2)*1e-4 = 32768/10000 ~= 3
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

    void set (std::vector<Entry *> & data)
    {
        ::clear (lo,  INFINITY);
        ::clear (hi, -INFINITY);

        typename std::vector<Entry *>::iterator t = data.begin ();
        for (; t != data.end (); t++)
        {
            int * a = (*t)->base ();
            int * l = lo.base ();
            int * h = hi.base ();
            int * end = l + 3;  // 3 dimensions
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

    void find (const Vector3 & query, std::vector<Entry *> & result) const
    {
        // Determine distance of query from bounding rectangle for entire tree
        int64_t distance = 0;
        for (int i = 0; i < 3; i++)
        {
            int d = std::max (0, lo[i] - query[i]) + std::max (0, query[i] - hi[i]);
            distance += (int64_t) d * d;
        }

        // Recursively collect closest points
        Query q;
        q.k      = k;
        q.radius = (int64_t) radius * radius;  // this may shrink monotonically once we find enough neighbors
        q.point  = &query;

        int oneEpsilon = (1 << FP_MSB2) + epsilon;  // exponent=MSB/2
        oneEpsilon = oneEpsilon * oneEpsilon >> FP_MSB2;  // This multiplication fits in 32-bit word, so no need for upcast.
        q.queue.push (std::make_pair (distance, root));
        int visited = 0;
        while (q.queue.size ())
        {
            const LongNode & it = q.queue.top ();
            distance = it.first;
            Node * n = it.second;
            q.queue.pop ();
            if (distance * oneEpsilon >> FP_MSB2 > q.radius) break;
            n->search (distance, q);
            if (++visited >= maxNodes) break;
        }

        // Transfer results to vector. No need to limit number of results, because this has
        // already been done by Leaf::search().
        int count = q.sorted.size ();
        result.resize (count);
        for (int i = count - 1; i >= 0; i--)
        {
            result[i] = q.sorted.top ().second;
            q.sorted.pop ();
        }
    }

    /// Recursively construct a tree that handles the given volume of points.
    Node * construct (std::vector<Entry *> & points)
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
            int d = 0;
            int longest = 0;
            for (int i = 0; i < 3; i++)
            {
                int length = hi[i] - lo[i];
                if (length > longest)
                {
                    d = i;
                    longest = length;
                }
            }
            sort (points, d);
            int cut = count / 2;
            typename std::vector<Entry *>::iterator b = points.begin ();
            typename std::vector<Entry *>::iterator c = b + cut;
            typename std::vector<Entry *>::iterator e = points.end ();

            Branch * result = new Branch;
            result->dimension = d;
            result->lo = lo[d];
            result->hi = hi[d];
            result->mid = (**c)[d];

            hi[d] = result->mid;
            std::vector<Entry *> tempPoints (b, c);
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
    void sort (std::vector<Entry *> & points, int dimension)
    {
        // Effectively we do a heap sort, since priority_queue is typically implemented with a heap structure.
        std::priority_queue<ShortEntry, std::vector<ShortEntry>, ShortForward> sorted;
        typename std::vector<Entry *>::iterator it = points.begin ();
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

#endif
