/*
Author: Fred Rothganger
Copyright (c) 2001-2004 Dept. of Computer Science and Beckman Institute,
                        Univ. of Illinois.  All rights reserved.
Distributed under the UIUC/NCSA Open Source License.  See the file LICENSE
for details.


Copyright 2005, 2009, 2010 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the GNU Lesser General Public License.  See the file LICENSE
for details.
*/


#ifndef pointer_h
#define pointer_h


#include "fl/archive.h"

#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stddef.h>

#include <ostream>
#include <atomic>


namespace fl
{
  /**
	 Keeps track of a block of memory, which can be shared by multiple objects
	 and multiple threads.  The block can either be managed by Pointer, or
	 it can belong to any other part of the system.  Only managed blocks get
	 reference counting, automatic deletion, and reallocation.

	 <p>This code assumes that <code>sizeof(int32_t) <= sizeof(ptrdiff_t)</code>.
  **/
  class Pointer
  {
  public:
	Pointer ()
	{
	  memory = 0;
	  metaData = 0;
	}

	Pointer (const Pointer & that)
	{
	  attach (that);
	}

	Pointer (void * that, ptrdiff_t size = 0)
	{
	  memory = (Counts *) that;
	  metaData = size;
	}

	Pointer (ptrdiff_t size)
	{
	  if (size > 0)
	  {
		allocate (size);
	  }
	  else
	  {
		memory = 0;
		metaData = 0;
	  }
	}

	~Pointer ()
	{
	  detach ();
	}

	Pointer & operator = (const Pointer & that)
	{
	  if (that.memory != memory)
	  {
		detach ();
		attach (that);
	  }
	  return *this;
	}

	Pointer & operator = (void * that)
	{
	  attach (that);
	  return *this;
	}

	void attach (void * that, ptrdiff_t size = 0)
	{
	  detach ();
	  memory = (Counts *) that;
	  metaData = size;
	}

	void copyFrom (const Pointer & that)  ///< decouple from memory held by that.  "that" could also be this.
	{
	  if (that.memory)
	  {
		Pointer temp (that);  // force refcount up on memory block
		if (that.memory == memory) detach ();  // the enemy is us...
		ptrdiff_t size = temp.size ();
		if (size < 0) throw "Don't know size of block to copy";
		grow (size);
		memcpy (memory, temp.memory, size);
	  }
	  else
	  {
		detach ();
	  }
	}

	void copyFrom (const void * that, ptrdiff_t size)
	{
	  if (size > 0)
	  {
		if (that == memory) detach ();
		grow (size);
		memcpy (memory, that, size);
	  }
	  else
	  {
		detach ();
	  }
	}

	void grow (ptrdiff_t size)
	{
	  if (metaData < 0)
	  {
		if (memory[-1].size >= size) return;
		detach ();
	  }
	  else if (metaData >= size)  // note:  metaData >= 0 at this point
	  {
		return;
	  }
	  if (size > 0) allocate (size);
	}

	void clear ()  // Erase block of memory
	{
	  if      (metaData < 0) memset (memory, 0, memory[-1].size);
	  else if (metaData > 0) memset (memory, 0, metaData);
	  else throw "Don't know size of block to clear";
	}

	int32_t refcount () const
	{
	  if (metaData < 0) return memory[-1].refcount;
	  return -1;
	}

	ptrdiff_t size () const
	{
	  if      (metaData < 0) return memory[-1].size;
	  else if (metaData > 0) return metaData;
	  return -1;
	}

	template <class T>
	operator T * () const
	{
	  return (T *) memory;
	}

	bool operator == (const Pointer & that) const
	{
	  return memory == that.memory;
	}

	bool operator != (const Pointer & that) const
	{
	  return memory != that.memory;
	}

	/**
	   Release memory and reset this pointer back to unititialized state.
	   This is primarily an internal function, but it is OK for client
	   code to call.
	**/
	void detach ()
	{
	  if (metaData < 0)
	  {
		if (--memory[-1].refcount == 0)
		{
		  // Technically, we should not use delete (memory - 1), because it was
		  // allocated with malloc(), not new.
		  (memory - 1)->~Counts ();  // placement dtor, in case atomic_uint needs destruction
		  free (memory - 1);
		}
	  }
	  memory = 0;
	  metaData = 0;
	}

  protected:
	/**
	   This method is protected because it assumes that we are not responsible
	   for our memory.  We must guarantee that we don't own memory when this
	   method is called.  This is true just after a call to detach, as well
	   as a few other situations.
	**/
	void attach (const Pointer & that)
	{
	  memory = that.memory;
	  metaData = that.metaData;
	  if (metaData < 0) memory[-1].refcount++;
	}

	void allocate (ptrdiff_t size)
	{
	  memory = (Counts *) malloc (sizeof (Counts) + size);
	  if (memory)
	  {
		memory = & memory[1];
		new (memory - 1) Counts ();  // in case atomic_uint needs construction
		memory[-1].refcount = 1;
		memory[-1].size     = size;
		metaData = -1;
	  }
	  else
	  {
		metaData = 0;
		throw "Unable to allocate memory";
	  }
	}

	struct Counts
	{
	  std::atomic_uint refcount;
	  ptrdiff_t        size;  ///< Actual size of block, not including this structure. Partially redundant with metadata field.
	};
	Counts * memory;  ///< Pointer to block in heap. Must be offset and typecast to access managed data.

	/**
	   This field is partially redundant with Counts::size. It allows us
	   to wrap blocks of memory owned by other code.
	   metaData < 0 indicates memory is a special pointer we constructed.
	   There is meta data associated with the pointer, and all "smart" pointer
	   functions are available.  This is the only time that we can (and must)
	   delete memory.
	   metaData == 0 indicates either memory == 0 or we don't know how big the
	   block is.
	   metaData > 0 indicates the actual size of the block, and that we don't
	   own it.
	**/
	ptrdiff_t metaData;

	friend std::ostream & operator << (std::ostream & stream, const Pointer & pointer);
  };

  inline std::ostream &
  operator << (std::ostream & stream, const Pointer & pointer)
  {
	return stream << "[" << &pointer << " " << pointer.memory << " " << pointer.size () << " " << pointer.refcount () << "]";
  }


  /**
	 Like Pointer, except that it works with a known structure,
	 and therefore a fixed amount of memory.  In order to use this template,
	 the wrapped class must have a default constructor (ie: one that takes no
	 arguments).
  **/
  template<class T>
  class PointerStruct
  {
  public:
	PointerStruct ()
	{
	  memory = 0;
	}

	PointerStruct (const PointerStruct & that)
	{
	  attach (that.memory);
	}

	~PointerStruct ()
	{
	  detach ();
	}

	/// @todo Should this function be made thread safe?
	void initialize ()
	{
	  if (! memory)
	  {
		memory = new RefcountBlock;
		memory->refcount = 1;
	  }
	}

	PointerStruct & operator = (const PointerStruct & that)
	{
	  if (that.memory != memory)
	  {
		detach ();
		attach (that.memory);
	  }
	  return *this;
	}

	void copyFrom (const PointerStruct & that)
	{
	  if (that.memory)
	  {
		PointerStruct temp (that);
		detach ();
		initialize ();
		memory->object = temp.memory->object;  // May or may not be a deep copy
	  }
	  else
	  {
		detach ();
	  }
	}

	int32_t refcount () const
	{
	  if (memory) return memory->refcount;
	  return -1;
	}

	T * operator -> () const
	{
	  return & memory->object;  // Will cause a segment violation if memory == 0
	}

	T & operator * () const
	{
	  return memory->object;  // Ditto
	}

	void detach ()
	{
	  if (memory)
	  {
		if (--memory->refcount == 0) delete memory;
		memory = 0;
	  }
	}

	struct RefcountBlock
	{
	  T                object;
	  std::atomic_uint refcount;
	};
	RefcountBlock * memory;

  protected:
	/**
	   attach assumes that memory == 0, so we must protect it.
	**/
	void attach (RefcountBlock * that)
	{
	  memory = that;
	  if (memory) memory->refcount++;
	}
  };


  /**
	 Interface the objects held by PointerPoly must implement.  It is not
	 mandatory that such objects inherit from this class, but if they do
	 then they are guaranteed to satisfy the requirements of PointerPoly.
	 When a ReferenceCounted is first constructed, its count is zero.
	 When PointerPolys attach to or detach from it, they update the count.
	 When the last PointerPoly detaches, it destructs this object.
   **/
  class ReferenceCounted
  {
  public:
	ReferenceCounted () {PointerPolyReferenceCount = 0;}
	mutable std::atomic_uint PointerPolyReferenceCount;  ///< The number of PointerPolys that are attached to this instance.
  };
  

  /**
	 Keeps track of an instance of a polymorphic class.  Similar to Pointer
	 and PointerStruct.  Expects that the wrapped object be an instance of
	 ReferenceCounted or at least implement the same interface.

	 <p>Note that this class does not have a copyFrom() function, because it
	 would require adding a clone() function to the ReferenceCounted
	 interface.  (We don't know the exact class of "memory", so we don't know
	 how to contruct another instance of it a priori.  A clone() function
	 encapsulates this knowledge in the wrapped class itself.)  That may be a
	 reasonable thing to do, but it would also add to the burden of using
	 this tool.  On the other hand, it is simple enough to clone the object
	 in client code.
  **/
  template<class T>
  class PointerPoly
  {
  public:
	PointerPoly ()
	{
	  memory = 0;
	}

	PointerPoly (const PointerPoly & that)
	{
	  memory = 0;
	  attach (that.memory);
	}

	PointerPoly (T * that)
	{
	  memory = 0;
	  attach (that);
	}

	~PointerPoly ()
	{
	  detach ();
	}

	static uint32_t serializeVersion;

	void serialize (Archive & archive, uint32_t version)
	{
	  archive & memory;
	  if (archive.in) memory->PointerPolyReferenceCount = 1;
	}

	PointerPoly & operator = (const PointerPoly & that)
	{
	  if (that.memory != memory)
	  {
		detach ();
		attach (that.memory);
	  }
	  return *this;
	}

	PointerPoly & operator = (T * that)
	{
	  if (that != memory)
	  {
		detach ();
		attach (that);
	  }
	  return *this;
	}

	int32_t refcount () const
	{
	  if (memory) return memory->PointerPolyReferenceCount;
	  return -1;
	}

	T * operator -> () const
	{
	  assert (memory);
	  return memory;
	}

	T & operator * () const
	{
	  assert (memory);
	  return *memory;
	}

	template <class T2>
	operator T2 * () const
	{
	  return dynamic_cast<T2 *> (memory);
	}

	bool operator == (const PointerPoly & that) const
	{
	  return memory == that.memory;
	}

	bool operator == (const T * that) const
	{
	  return memory == that;
	}

	bool operator != (const PointerPoly & that) const
	{
	  return memory != that.memory;
	}

	bool operator != (const T * that) const
	{
	  return memory != that;
	}

	/**
	   Binds to the given pointer.  This method should only be called when
	   we are not currently bound to anything.  This class is coded so that
	   this condition is always true when this function is called internally.
	**/
	void attach (T * that)
	{
	  assert (memory == 0);
	  memory = that;
	  if (memory) memory->PointerPolyReferenceCount++;
	}

	void detach ()
	{
	  if (memory)
	  {
		assert (memory->PointerPolyReferenceCount > 0);
		if (--memory->PointerPolyReferenceCount == 0) delete memory;
		memory = 0;
	  }
	}

	T * memory;
  };

  template<class T> uint32_t PointerPoly<T>::serializeVersion = 1;
}


#endif
