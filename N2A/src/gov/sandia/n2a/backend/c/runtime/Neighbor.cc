/*
Author: Fred Rothganger

Copyright 2010 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the GNU Lesser General Public License.  See the file LICENSE
for details.
*/


#include "fl/neighbor.h"


using namespace fl;
using namespace std;


// class Neighbor -------------------------------------------------------------

Neighbor::~Neighbor ()
{
}

uint32_t Neighbor::serializeVersion = 0;

void
Neighbor::serialize (Archive & archive, uint32_t version)
{
}


// class Entry ----------------------------------------------------------------

Neighbor::Entry::Entry (MatrixAbstract<float> * point, void * item)
: point (point),
  item (item)
{
}

MatrixAbstract<float> *
Neighbor::Entry::clone (bool deep) const
{
  if (deep) return new Entry (point->clone (true), item);
  return new Entry (point, item);
}

int
Neighbor::Entry::rows () const
{
  return point->rows ();
}

int
Neighbor::Entry::columns () const
{
  return point->columns ();
}

void
Neighbor::Entry::resize (const int rows, const int columns)
{
  point->resize (rows, columns);
}


// class KDTree ---------------------------------------------------------------

KDTree::KDTree ()
{
  root       = 0;
  bucketSize = 5;
  k          = 5;  // it doesn't make sense for k to be less than bucketSize
  radius     = INFINITY;
  epsilon    = 1e-4;
  maxNodes   = INT_MAX;
}

KDTree::~KDTree ()
{
  clear ();
}

void
KDTree::clear ()
{
  delete root;
  root = 0;
}

void
KDTree::serialize (Archive & archive, uint32_t version)
{
}

void
KDTree::set (const vector<MatrixAbstract<float> *> & data)
{
  vector<MatrixAbstract<float> *> temp = data;

  int dimensions = temp[0]->rows ();
  lo.resize (dimensions);
  hi.resize (dimensions);
  lo.clear ( INFINITY);
  hi.clear (-INFINITY);

  vector<MatrixAbstract<float> *>::iterator t = temp.begin ();
  for (; t != temp.end (); t++)
  {
	float * a = &(**t)[0];
	float * l = &lo[0];
	float * h = &hi[0];
	float * end = l + dimensions;
	while (l < end)
	{
	  *l = min (*l, *a);
	  *h = max (*h, *a);
	  a++;
	  l++;
	  h++;
	}
  }

  root = construct (temp);
}

void
KDTree::find (const MatrixAbstract<float> & query, vector<MatrixAbstract<float> *> & result) const
{
  // Determine distance of query from bounding rectangle for entire tree
  int dimensions = query.rows ();
  float distance = 0;
  for (int i = 0; i < dimensions; i++)
  {
	float d = max (0.0f, lo[i] - query[i]) + max (0.0f, query[i] - hi[i]);
	distance += d * d;
  }

  // Recursively collect closest points
  Query q;
  q.k          = k;
  q.radius     = radius * radius;  // this may shrink monotonically once we find enough neighbors
  q.point      = &query;

  float oneEpsilon = (1 + epsilon) * (1 + epsilon);
  q.queue.insert (make_pair (distance, root));
  int visited = 0;
  while (q.queue.size ())
  {
	multimap<float, Node *>::iterator it = q.queue.begin ();
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
  multimap<float, MatrixAbstract<float> *>::iterator sit;
  for (sit = q.sorted.begin (); sit != q.sorted.end (); sit++)
  {
	result.push_back (sit->second);
  }
}

void
KDTree::dump (ostream & out, const string & pad) const
{
  out << pad << "KDTree: " << bucketSize << " " << k << " " << radius << " " << epsilon << endl;
  out << pad << "lo = " << lo << endl;
  out << pad << "hi = " << hi << endl;
  if (root)
  {
	out << pad << "root:" << endl;
	root->dump (out, pad + "  ");
  }
}

KDTree::Node *
KDTree::construct (vector<MatrixAbstract<float> *> & points)
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
	vector<MatrixAbstract<float> *>::iterator b = points.begin ();
	vector<MatrixAbstract<float> *>::iterator c = b + cut;
	vector<MatrixAbstract<float> *>::iterator e = points.end ();

	Branch * result = new Branch;
	result->dimension = d;
	result->lo = lo[d];
	result->hi = hi[d];
	result->mid = (**c)[d];

	hi[d] = result->mid;
	vector<MatrixAbstract<float> *> tempPoints (b, c);
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

void
KDTree::sort (vector<MatrixAbstract<float> *> & points, int dimension)
{
  multimap<float, MatrixAbstract<float> *> sorted;
  vector<MatrixAbstract<float> *>::iterator it = points.begin ();
  for (; it != points.end (); it++)
  {
	sorted.insert (make_pair ((**it)[dimension], *it));
  }

  points.clear ();
  points.reserve (sorted.size ());
  multimap<float, MatrixAbstract<float> *>::iterator sit = sorted.begin ();
  for (; sit != sorted.end (); sit++)
  {
	points.push_back (sit->second);
  }
}


// class KDTree::Node ---------------------------------------------------------

KDTree::Node::~Node ()
{
}


// class KDTree::Branch -------------------------------------------------------

KDTree::Branch::~Branch ()
{
  delete lowNode;
  delete highNode;
}

void
KDTree::Branch::search (float distance, Query & q) const
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
	  float oldOffset = max (lo - qmid, 0.0f);
	  distance += newOffset * newOffset - oldOffset * oldOffset;
	  q.queue.insert (make_pair (distance, highNode));
	}
  }
  else  // newOffset >= 0, so highNode is closer
  {
	if (highNode) highNode->search (distance, q);
	if (lowNode)
	{
	  float oldOffset = max (qmid - hi, 0.0f);
	  distance += newOffset * newOffset - oldOffset * oldOffset;
	  q.queue.insert (make_pair (distance, lowNode));
	}
  }
}

void
KDTree::Branch::dump (ostream & out, const string & pad) const
{
  out << pad << "Branch: " << dimension << " " << lo << " " << mid << " " << hi << endl;
  if (lowNode)
  {
	out << pad << "lowNode:" << endl;
	lowNode->dump (out, pad + "  ");
  }
  if (highNode)
  {
	out << pad << "highNode:" << endl;
	highNode->dump (out, pad + "  ");
  }
}


// class KDTree::Leaf ---------------------------------------------------------

void
KDTree::Leaf::search (float distance, Query & q) const
{
  int count = points.size ();
  int dimensions = points[0]->rows ();
  for (int i = 0; i < count; i++)
  {
	MatrixAbstract<float> * p = points[i];

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
	q.sorted.insert (make_pair (total, p));
	if (q.sorted.size () > q.k)
	{
	  multimap<float, MatrixAbstract<float> *>::iterator it = q.sorted.end ();
	  it--;  // it is one past end of collection, so we must back up one step
	  q.sorted.erase (it);
	}
	if (q.sorted.size () == q.k) q.radius = min (q.radius, q.sorted.rbegin ()->first);
  }
}

void
KDTree::Leaf::dump (ostream & out, const string & pad) const
{
  for (int i = 0; i < points.size (); i++) out << pad << *points[i] << endl;
}
