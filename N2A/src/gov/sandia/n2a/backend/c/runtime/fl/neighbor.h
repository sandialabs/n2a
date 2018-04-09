/*
Author: Fred Rothganger

Copyright 2010 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the GNU Lesser General Public License.  See the file LICENSE
for details.
*/


#ifndef neighbor_h
#define neighbor_h


#include "fl/matrix.h"

#include <vector>
#include <map>


namespace fl
{
  /**
	 An implementation based loosely on the paper "Algorithms for Fast Vector
	 Quantization" by Sunil Arya and David Mount.
   **/
  class KDTree
  {
  public:
	KDTree ();
	virtual ~KDTree ();
	void clear ();

	virtual void set  (const std::vector<MatrixAbstract<float> *> & data);
	virtual void find (const MatrixAbstract<float> & query, std::vector<MatrixAbstract<float> *> & result) const;
#	ifndef N2A_SPINNAKER
	virtual void dump (std::ostream & out, const String & pad = "") const;
#	endif

	class Node;

	/// Internal helper class for passing search-related info down the tree.
	class Query
	{
	public:
	  int k;
	  float radius;
	  const MatrixAbstract<float> * point;
	  std::multimap<float, MatrixAbstract<float> *> sorted;
	  std::multimap<float, Node *> queue;
	};

	class Node
	{
	public:
	  virtual ~Node ();
	  virtual void search (float distance, Query & q) const = 0;
#	  ifndef N2A_SPINNAKER
	  virtual void dump (std::ostream & out, const String & pad = "") const = 0;
#	  endif
	};

	class Branch : public Node
	{
	public:
	  virtual ~Branch ();
	  virtual void search (float distance, Query & q) const;
#	  ifndef N2A_SPINNAKER
	  virtual void dump (std::ostream & out, const String & pad = "") const;
#	  endif

	  int dimension;
	  float lo;  ///< Lowest value along the dimension
	  float hi;  ///< Highest value along the dimension
	  float mid;  ///< The cut point along the dimension
	  Node * lowNode;  ///< below mid
	  Node * highNode;  ///< above mid
	};

	class Leaf : public Node
	{
	public:
	  virtual void search (float distance, Query & q) const;
#	  ifndef N2A_SPINNAKER
	  virtual void dump (std::ostream & out, const String & pad = "") const;
#	  endif

	  std::vector<MatrixAbstract<float> *> points;
	};

	Node * construct (std::vector<MatrixAbstract<float> *> & points);  ///< Recursively construct a tree that handles the given volume of points.
	void   sort      (std::vector<MatrixAbstract<float> *> & points, int dimension);  ///< rearrange points so they are in ascending order along the given dimension

	Node * root;
	Vector<float> lo;
	Vector<float> hi;

	int bucketSize;
	int k;
	float radius;  ///< Maximum distance between query point and any result point. Initially set to INFINITY by constructor.
	float epsilon;  ///< Nodes must have at least this much overlap with the current radius (which is always the lesser of the initial radius and the kth nearest neighbor).
	int maxNodes;  ///< Expand no more than this number of nodes. Forces a search to be approximate rather than exhaustive.
  };
}


#endif
