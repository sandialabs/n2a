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
#include <map>


typedef fl::MatrixFixed<float,3,1> Vector3;

class Part;

class KDTreeEntry : public Vector3
{
public:
    Part * part;
};

class KDTree
{
public:
    // Note: All the inner classes are defined first, then the main class members come at the bottom of this file.

    class Node;

    /// Helper class for passing search-related info down the tree.
    class Query
    {
    public:
        int                                               k;
        float                                             radius;
        const fl::MatrixAbstract<float> *                 point;
        std::multimap<float, fl::MatrixAbstract<float> *> sorted;
        std::multimap<float, Node *>                      queue;
    };

    class Node
    {
    public:
        virtual ~Node ()
        {
        }

        virtual void search (float distance, Query & q) const = 0;
#       ifndef N2A_SPINNAKER
        virtual void dump (std::ostream & out, const String & pad = "") const = 0;
#       endif
    };

    class Branch : public Node
    {
    public:
        int    dimension;
        float  lo;        ///< Lowest value along the dimension
        float  hi;        ///< Highest value along the dimension
        float  mid;       ///< The cut point along the dimension
        Node * lowNode;   ///< below mid
        Node * highNode;  ///< above mid

        virtual ~Branch ()
        {
            delete lowNode;
            delete highNode;
        }

        virtual void search (float distance, Query & q) const
        {
            float qmid = (*q.point)[dimension];
            float newOffset = qmid - mid;
            if (newOffset < 0)  // lowNode is closer
            {
                // We don't do any special testing on nearer node, because it has already been
                // tested as part of the containing node.
                if (lowNode) lowNode->search (distance, q);
                if (highNode)
                {
                    float oldOffset = std::max (lo - qmid, 0.0f);
                    distance += newOffset * newOffset - oldOffset * oldOffset;
                    q.queue.insert (std::make_pair (distance, highNode));
                }
            }
            else  // newOffset >= 0, so highNode is closer
            {
                if (highNode) highNode->search (distance, q);
                if (lowNode)
                {
                    float oldOffset = std::max (qmid - hi, 0.0f);
                    distance += newOffset * newOffset - oldOffset * oldOffset;
                    q.queue.insert (std::make_pair (distance, lowNode));
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
        std::vector<fl::MatrixAbstract<float> *> points;

        virtual void search (float distance, Query & q) const
        {
            int count = points.size ();
            int dimensions = points[0]->rows ();
            for (int i = 0; i < count; i++)
            {
                fl::MatrixAbstract<float> * p = points[i];

                // Measure distance using early-out method. Might save operations in
                // high-dimensional spaces.
                // Here we make the assumption that the values are stored contiguously in
                // memory.  This is a good place to check for bugs if using more exotic
                // matrix types (not recommended).
                float * x = &(*p)[0];
                float * y = &(*q.point)[0];
                float * end = x + dimensions;
                float total = 0;
                while (x < end  &&  total < q.radius)
                {
                    float t = *x++ - *y++;
                    total += t * t;
                }

                if (total >= q.radius) continue;
                q.sorted.insert (std::make_pair (total, p));
                if (q.sorted.size () > q.k)
                {
                    std::multimap<float, fl::MatrixAbstract<float> *>::iterator it = q.sorted.end ();
                    it--;  // it is one past end of collection, so we must back up one step
                    q.sorted.erase (it);
                }
                if (q.sorted.size () == q.k) q.radius = std::min (q.radius, q.sorted.rbegin ()->first);
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
    fl::Vector<float> lo;
    fl::Vector<float> hi;

    int   bucketSize;
    int   k;
    float radius;   ///< Maximum distance between query point and any result point. Initially set to INFINITY by constructor.
    float epsilon;  ///< Nodes must have at least this much overlap with the current radius (which is always the lesser of the initial radius and the kth nearest neighbor).
    int   maxNodes; ///< Expand no more than this number of nodes. Forces a search to be approximate rather than exhaustive.

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

    void set  (const std::vector<fl::MatrixAbstract<float> *> & data)
    {
        std::vector<fl::MatrixAbstract<float> *> temp = data;

        int dimensions = temp[0]->rows ();
        lo.resize (dimensions);
        hi.resize (dimensions);
        lo.clear ( INFINITY);
        hi.clear (-INFINITY);

        std::vector<fl::MatrixAbstract<float> *>::iterator t = temp.begin ();
        for (; t != temp.end (); t++)
        {
            float * a = &(**t)[0];
            float * l = &lo[0];
            float * h = &hi[0];
            float * end = l + dimensions;
            while (l < end)
            {
                *l = std::min (*l, *a);
                *h = std::max (*h, *a);
                a++;
                l++;
                h++;
            }
        }

        root = construct (temp);
    }

    void find (const fl::MatrixAbstract<float> & query, std::vector<fl::MatrixAbstract<float> *> & result) const
    {
        // Determine distance of query from bounding rectangle for entire tree
        int dimensions = query.rows ();
        float distance = 0;
        for (int i = 0; i < dimensions; i++)
        {
            float d = std::max (0.0f, lo[i] - query[i]) + std::max (0.0f, query[i] - hi[i]);
            distance += d * d;
        }

        // Recursively collect closest points
        Query q;
        q.k          = k;
        q.radius     = radius * radius;  // this may shrink monotonically once we find enough neighbors
        q.point      = &query;

        float oneEpsilon = (1 + epsilon) * (1 + epsilon);
        q.queue.insert (std::make_pair (distance, root));
        int visited = 0;
        while (q.queue.size ())
        {
            std::multimap<float, Node *>::iterator it = q.queue.begin ();
            distance = it->first;
            Node * n = it->second;
            q.queue.erase (it);
            if (distance * oneEpsilon > q.radius) break;
            n->search (distance, q);
            if (++visited >= maxNodes) break;
        }

        // Transfer results to vector. No need to limit number of results, becaus this has
        // already been done by Leaf::search().
        result.reserve (q.sorted.size ());
        std::multimap<float, fl::MatrixAbstract<float> *>::iterator sit;
        for (sit = q.sorted.begin (); sit != q.sorted.end (); sit++)
        {
            result.push_back (sit->second);
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
    Node * construct (std::vector<fl::MatrixAbstract<float> *> & points)
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
            std::vector<fl::MatrixAbstract<float> *>::iterator b = points.begin ();
            std::vector<fl::MatrixAbstract<float> *>::iterator c = b + cut;
            std::vector<fl::MatrixAbstract<float> *>::iterator e = points.end ();

            Branch * result = new Branch;
            result->dimension = d;
            result->lo = lo[d];
            result->hi = hi[d];
            result->mid = (**c)[d];

            hi[d] = result->mid;
            std::vector<fl::MatrixAbstract<float> *> tempPoints (b, c);
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

    /// rearrange points so they are in ascending order along the given dimension
    void sort (std::vector<fl::MatrixAbstract<float> *> & points, int dimension)
    {
        std::multimap<float, fl::MatrixAbstract<float> *> sorted;
        std::vector<fl::MatrixAbstract<float> *>::iterator it = points.begin ();
        for (; it != points.end (); it++)
        {
            sorted.insert (std::make_pair ((**it)[dimension], *it));
        }

        points.clear ();
        points.reserve (sorted.size ());
        std::multimap<float, fl::MatrixAbstract<float> *>::iterator sit = sorted.begin ();
        for (; sit != sorted.end (); sit++)
        {
            points.push_back (sit->second);
        }
    }
};


#endif
