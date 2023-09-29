/*
This collection of utility classes reads and writes N2A model files, which
are stored in a lightweight document database.

The source file (MNode.cc) can function as a header-only implementation, or it
can be compiled separately and added to a library. In the latter case, you can
use this header file like any other, to bring in symbols so you can use MNode
in an application.

Copyright 2022-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

#ifndef n2a_mnode_h
#define n2a_mnode_h

#include "mystring.h"  // Use our own string class, to be compatible with rest of runtime. Unfortunately, this means you need to copy one extra header file to use MNode standalone.
#include <vector>
#include <map>
#include <set>
#include <mutex>
#include <memory>

#ifdef _MSC_VER
#  define strcasecmp _stricmp
#else
#  include <strings.h>
#endif
#include "shared.h"


namespace n2a
{
    // Utility function
    /// Create all parent directories of given file.
    void mkdirs (const String & file);

    // MNode class ID constants
    // This is a hack to avoid the cost of RTTI.
    // (Everything is an MNode, so we don't have a specific bit for that.)
    #define MVolatileID    0x01
    #define MPersistentID  0x02
    #define MDocID         0x04
    #define MDocGroupID    0x08
    #define MDirID         0x10

    class SHARED MNode;
    class SHARED MVolatile;
    class SHARED MPersistent;
    class SHARED MDoc;
    class SHARED MDocGroup;
    class SHARED MDir;
    class SHARED Schema;
    class SHARED Schema2;
    class LineReader;

    /**
        A hierarchical key-value storage system, with subclasses that provide persistence.
        The "M" in MNode refers to the MUMPS language, in which variables have this hierarchical structure.
        MUMPS is one of the earliest hierarchical key-value system, designed in 1966.

        <p>This is a quasi-abstract class. The bulk of the utility methods are implemented at this top level,
        and an instance of this class is minimally useful, mostly as a placeholder. Generally, your code only
        needs to know about MNode. Avoid using knowledge about the details of concrete implementations unless
        it is absolutely necessary. For example, there is value in calling MDoc.save() to write to disk.
    **/
    class SHARED MNode
    {
    public:
        static MNode none;  // For return values, indicating node does not exist. Iterating over none will produce no children.

        virtual ~MNode ();
        virtual uint32_t classID () const;

        virtual String key () const;

        /**
            Returns an array of keys suitable for locating this node relative to its root node.
            Does not include the key of the root node itself.
        **/
        std::vector<String> keyPath () const;

        /**
            Returns an array of keys suitable for locating this node relative to the given root node.
            Does not include the key of the root node itself.
            If the given root node is not on the path to the actual root, then it is ignored and
            the full path to actual root is returned.
        **/
        std::vector<String> keyPath (const MNode & root) const;

        /**
            Utility function for printing node's key path.
        **/
        String keyPathString () const;

        /**
            Utility function for printing node's key path relative to given root node.
        **/
        String keyPathString (const MNode & root) const;

        int depth () const;

        int depth (const MNode & root) const;

        virtual MNode & parent () const;

        MNode & root ();

        /**
            Find the last common ancestor between this node and the given node.
            If the nodes do not share a common ancestor, the result is none.
        **/
        MNode & lca (MNode & that);

        /**
            Returns a child node from arbitrary depth, or none if any part of the path doesn't exist.
        **/
        MNode & child (const std::vector<String> & keys);
        template<typename... Args> MNode & child (Args... keys) {return child ({keys...});}

        /**
            Retrieves a child node from arbitrary depth, or creates it if nonexistent.
            Like a combination of child() and set().
        **/
        MNode & childOrCreate (const std::vector<String> & keys);
        template<typename... Args> MNode & childOrCreate (Args... keys) {return childOrCreate ({keys...});}

        /**
            Convenience method for retrieving node at an ordinal position without knowing the specific key.
            Children are traversed in the usual key order.
            If index is out of range, then result is null.
        **/
        MNode & childAt (int index);

        /**
            Remove all children.
            Releases memory held by children. Any reference to a child is no longer
            valid and should not be used.
        **/
        virtual void clear ();

        /**
            Removes child with arbitrary depth.
            If no key is specified, then removes all children of this node.
        **/
        void clear (const std::vector<String> & keys);
        template<typename... Args> void clear (Args... keys) {clear ({keys...});}

        /**
            @return The number of children we have.
        **/
        virtual int size ();

        bool empty ();

        /**
            Indicates whether this node is defined.
            Works in conjunction with size() to provide information similar to the MUMPS function "DATA".
            Since get() returns "" for undefined nodes, this is the only way to determine whether a node
            is actually defined to "" or is undefined. "Undefined" is not the same as non-existent,
            because and undefined node can have children. Only a child() call on a parent can confirm
            the complete non-existence of a node.
        **/
        virtual bool data ();

        bool data (const std::vector<String> & keys);
        template<typename... Args> bool data (Args... keys) {return data ({keys...});}

        /**
            Determines if the given key exists anywhere in the hierarchy.
            This is a deep query. For a shallow (one-level) query, use child() instead.
        **/
        bool containsKey (const String & key);

        /**
            @return This node's value, with "" as default
        **/
        String get ();

        /**
            Digs down tree as far as possible to retrieve value; returns "" if node does not exist.
        **/
        String get (const std::vector<String> & keys);
        template<typename... Args> String get (Args... keys) {return get ({keys...});}

        /**
            Returns this node's value, or the given default if node is undefined or set to "".
            This is the only get function that needs to be overridden by subclasses.
        **/
        virtual String getOrDefault (const String & defaultValue);

        /**
            Digs down tree as far as possible to retrieve value; returns given defaultValue if node does not exist or is set to "".
        **/
        String getOrDefault (const char * defaultValue, const std::vector<String> & keys);
        template<typename... Args> String getOrDefault (const char * defaultValue, Args... keys) {return getOrDefault (defaultValue, {keys...});}

        String getOrDefault (const String & defaultValue, const std::vector<String> & keys)
        {
            return getOrDefault (defaultValue.c_str (), keys);
        }
        template<typename... Args> String getOrDefault (const String & defaultValue, Args... keys) {return getOrDefault (defaultValue, {keys...});}

        bool getOrDefault (bool defaultValue, const std::vector<String> & keys);
        template<typename... Args> bool getOrDefault (bool defaultValue, Args... keys) {return getOrDefault (defaultValue, {keys...});}

        int getOrDefault (int defaultValue, const std::vector<String> & keys);
        template<typename... Args> int getOrDefault (int defaultValue, Args... keys) {return getOrDefault (defaultValue, {keys...});}

        long getOrDefault (long defaultValue, const std::vector<String> & keys);
        template<typename... Args> long getOrDefault (long defaultValue, Args... keys) {return getOrDefault (defaultValue, {keys...});}

        double getOrDefault (double defaultValue, const std::vector<String> & keys);
        template<typename... Args> double getOrDefault (double defaultValue, Args... keys) {return getOrDefault (defaultValue, {keys...});}

        /**
            Interprets value as boolean:
            true = "1" or "true";
            false = everything else, including empty.
            See getFlag() for a different way to interpret booleans. The key difference is
            that a boolean defaults to false.
        **/
        bool getBool (const std::vector<String> & keys)
        {
            return getOrDefault (false, keys);
        }
        template<typename... Args> bool getBool (Args... keys) {return getBool ({keys...});}

        /**
            Interprets value as flag, which may contain extended information when set:
            false <-- "0", non-existent, or no data
            true <-- everything else, including empty
            See getBoolean() for a different way to interpret booleans. The key difference is
            that a flag defaults to true, so it can indicate something by merely existing, without a value.
            It also tolerates arbitrary content, so a flag can carry extra data and still be interpreted as true.
        **/
        bool getFlag (const std::vector<String> & keys);
        template<typename... Args> bool getFlag (Args... keys) {return getFlag ({keys...});}

        int getInt (const std::vector<String> & keys)
        {
            return getOrDefault (0, keys);
        }
        template<typename... Args> int getInt (Args... keys) {return getInt ({keys...});}

        long getLong (const std::vector<String> & keys)
        {
            return getOrDefault (0l, keys);
        }
        template<typename... Args> long getLong (Args... keys) {return getLong ({keys...});}

        double getDouble (const std::vector<String> & keys)
        {
            return getOrDefault (0.0, keys);
        }
        template<typename... Args> double getDouble (Args... keys) {return getDouble ({keys...});}

        /**
            Sets this node's own value.
            Passing nullptr makes future calls to data() returns false, that is, makes the value of this node undefined.
            Should be overridden by a subclass.
        **/
        virtual void set (const char * value);

        void set (const String & value)
        {
            set (value.c_str ());
        }

        void set (bool value)
        {
            set (value ? "1" : "0");
        }

        void set (int value)
        {
            char buffer[16];
            sprintf (buffer, "%i", value);
            set (buffer);
        }

        void set (long value)
        {
            char buffer[32];
            sprintf (buffer, "%li", value);
            set (buffer);
        }

        void set (double value)
        {
            char buffer[32];
            sprintf (buffer, "%g", value);
            set (buffer);
        }

        void set (MNode & value);

        /**
            Creates all children necessary to set value
        **/
        MNode & set (const char * value, const std::vector<String> & keys);
        template<typename... Args> MNode & set (const char * value, Args... keys) {return set (value, {keys...});}

        MNode & set (const String & value, const std::vector<String> & keys);
        template<typename... Args> MNode & set (const String & value, Args... keys) {return set (value, {keys...});}

        MNode & set (bool value, const std::vector<String> & keys);
        template<typename... Args> MNode & set (bool value, Args... keys) {return set (value, {keys...});}

        MNode & set (int value, const std::vector<String> & keys);
        template<typename... Args> MNode & set (int value, Args... keys) {return set (value, {keys...});}

        MNode & set (long value, const std::vector<String> & keys);
        template<typename... Args> MNode & set (long value, Args... keys) {return set (value, {keys...});}

        MNode & set (double value, const std::vector<String> & keys);
        template<typename... Args> MNode & set (double value, Args... keys) {return set (value, {keys...});}

        /**
            Merges the given node at the given point in the tree.
            See merge()
        **/
        MNode & set (MNode & value, const std::vector<String> & keys);
        template<typename... Args> MNode & set (const MNode & value, Args... keys) {return set (value, {keys...});}

        /**
            Deep copies the source node into this node, while leaving any non-overlapping values in
            this node unchanged. The value of this node is only replaced if the source value is defined.
            Children of the source node are then merged with this node's children.
        **/
        void merge (MNode & that);

        /**
            Deep copies the source node into this node, while leaving all values in this node unchanged.
            This method could be called "underride", but that already has a special meaning in MPart.
        **/
        void mergeUnder (MNode & that);

        /**
            Modifies this tree so it contains only nodes which are not defined in the given tree ("that").
            Implicitly, it also contains parents of such nodes, but a parent will be undefined if it was
            defined in the given tree. This function is used for tree differencing,
            where it is necessary to represent nodes that will be subtracted as a separate tree from nodes
            that will be added. This function computes the subtraction tree. To apply the subtraction,
            this function can be called again, passing the result of the previous call. Specifically, let:
            <pre>
            A = one tree
            B = another tree
            C = clone of A, then run C.uniqueNodes(B)
            D = clone of B, then run D.uniqueValues(A)
            To transform A into B, run
            A.uniqueNodes(C) to remove/undefine nodes that are only in A
            A.merge(D) to add/change values which are different in B
            </pre>
        **/
        void uniqueNodes (MNode & that);

        /**
            Modifies this tree so it contains only nodes which differ from the given tree ("that")
            in either key or value. Any parent nodes which are not also differences will be undefined.
            See uniqueNodes(MNode) for an explanation of tree differencing.
        **/
        void uniqueValues (MNode & that);

        /**
            Assuming "that" will be the target of a merge, saves any values this node would change.
            The resulting tree can be used to revert a merge. Specifically, let:
            <pre>
            A = this tree (merge source) before any operations
            B = that tree (merge target) before any operations
            C = clone of A, then run C.uniqueNodes(B)
            D = clone of A, then run D.changes(B)
            Suppose you run B.merge(A). To revert B to its original state, run
            B.uniqueNodes(C) to remove/undefine nodes not originally in B
            B.merge(D) to restore original values
            </pre>
            See uniqueNodes(MNode) for more explanation of tree differencing.
        **/
        void changes (MNode & that);

        /**
            Changes the key of a child.
            A move only happens if the given keys are different (same key is no-op).
            Any previously existing node at the destination key will be completely erased and replaced.
            An entry will no longer exist at the source key.
            If the source does not exist before the move, then neither node will exist afterward.
            Many subclasses guarantee object identity, but that is not a requirement.
            The safest approach is to call child(toKey) to get a reference to the renamed node.
        **/
        virtual void move (const String & fromKey, const String & toKey);

        struct Iterator
        {
            using iterator_category = std::input_iterator_tag;
            using difference_type   = int;
            using value_type        = MNode;
            using pointer           = MNode *;
            using reference         = MNode &;

            MNode &                              container;
            // We always pull a copy of the keys, rather than iterating directly on child nodes.
            // This allows the caller to delete nodes in the middle of iteration.
            // Dereferencing the iterator could return none.
            std::shared_ptr<std::vector<String>> keys;
            int                                  i;  // position in keys

            Iterator (MNode & container)
            :   container (container),
                keys (std::make_shared<std::vector<String>> ())  // keys is initialized to empty
            {
                i = 0;
            }

            reference operator* ()
            {
                return container.childGet ((*keys)[i]);  // If iterator is past end of keys, this will crash.
            }

            pointer operator-> ()
            {
                return & container.childGet ((*keys)[i]);
            }

            Iterator & operator++ ()  // prefix increment
            {
                i++;
                return *this;
            }

            Iterator operator++ (int)  // postfix increment
            {
                Iterator result = *this;  // Preserve a copy of the iterator at current position.
                i++;
                return result;
            }

            friend bool operator== (const Iterator & a, const Iterator & b)
            {
                if (&a.container != &b.container) return false;
                bool aDone =  a.i >= a.keys->size ();
                bool bDone =  b.i >= b.keys->size ();
                if (aDone != bDone) return false;
                if (aDone) return true;
                return (*a.keys)[a.i] == (*b.keys)[b.i];
            }

            friend bool operator!= (const Iterator & a, const Iterator & b)
            {
                return ! (a == b);
            }
        };

        virtual Iterator begin ();

        virtual Iterator end ();

        struct Visitor
        {
            /**
                @param node Any of key, value or children may be modified during this visit. However, moving a node
                over one of its siblings may result in it being visited again.
                @return true to recurse below current node. false if further recursion below this node is not needed.
            **/
            virtual bool visit (MNode & node) = 0;
        };

        /**
            Execute some operation on each node in the tree. Traversal is depth-first.
        **/
        void visit (Visitor & v);

        /**
            Implements M collation order.
            In short, this places all properly formed numbers ahead of non-numbers.
            Numbers are sorted by actual value rather than lexical representation.
            Then strings are sorted in character-code order. The strings could be
            in UTF-8 form (multi-byte). There are claims that by design, UTF-8
            still sorts correctly if treated as single-byte.
            @return Greater than zero if A>B. Zero if A==B. Less than zero if A<B.
            Only the sign of nonzero return values is guaranteed, not the magnitude.
        **/
        static int compare (const char * A, const char * B);
        static int compare (const String & A, const String & B)
        {
            return compare (A.c_str (), B.c_str ());
        }

        struct Order
        {
            bool operator() (const char * a, const char * b) const
            {
                return compare (a, b) < 0;
            }
            bool operator() (const String & a, const String & b) const
            {
                return compare (a, b) < 0;
            }
        };

        /**
            Deep comparison of two nodes. All structure, keys and values must match exactly.
        **/
        bool operator== (MNode & that);

        bool equalsRecursive (MNode & that);

        /**
            Compares only key structure, not values.
        **/
        bool structureEquals (MNode & that);

        friend MDoc;

    protected:
        /**
            Certain functions/data should only be access by one thread at a time.
            These are node-specific, so every node has a mutex.
            If memory or other resource utilization becomes an issue, this object could be made static.
            That would force serial access even on independent data.
        **/
        std::recursive_mutex mutex;

        // Internal functions for manipulating children ...

        /**
            Returns the child indicated by the given key.
            @param create Changes behavior when key is not found. By default, this
            method returns "none". If true, a new node will be created and returned.
        **/
        virtual MNode & childGet (const String & key, bool create = false);

        /**
            Removes child with the given key, if it exists.
        **/
        virtual void childClear (const String & key);
    };

    class SHARED MVolatile : public MNode
    {
    public:
        MVolatile (const char * value = 0, const char * name = 0, MNode * container = 0);
        virtual ~MVolatile ();  ///< frees memory used by children
        virtual uint32_t classID () const;

        virtual String      key          () const;
        virtual MNode &     parent       () const;
        virtual void        clear        ();
        virtual int         size         ();
        virtual bool        data         ();
        virtual String      getOrDefault (const String & defaultValue);
        virtual void        set          (const char * value);  ///< copies the memory, on assumption that the caller could delete it
        virtual void        move         (const String & fromKey, const String & toKey);  ///< If you already hold a reference to the node named by fromKey, then that reference remains valid and its key is updated.
        virtual Iterator    begin        ();

        // C++ name resolution
        using MNode::clear;
        using MNode::data;
        using MNode::getOrDefault;
        using MNode::set;

        friend MPersistent;

    protected:
        String                                 name;
        char *                                 value;     ///< Our own local copy of string. We are responsible to manage this memory. We use char * rather than String so this value can be null, not merely empty.
        MNode *                                container;
        std::map<const char *, MNode *, Order> children;  // Children are always a subclass of MVolatile. As long as a child exists, we can simply hold a pointer to its name.

        virtual MNode & childGet   (const String & key, bool create = false);
        virtual void    childClear (const String & key);
    };

    class SHARED MPersistent : public MVolatile
    {
    public:
        MPersistent (MNode * container, const char * value = 0, const char * key = 0);
        virtual uint32_t classID () const;

        virtual void markChanged  ();
        virtual void clearChanged ();
        virtual void clear        ();
        virtual void set          (const char * value);
        virtual void move         (const String & fromKey, const String & toKey);

        using MNode::clear;
        using MNode::set;

    protected:
        bool needsWrite; ///< indicates that this node is new or has changed since it was last read from disk (and therefore should be written out)

        virtual MNode & childGet   (const String & key, bool create = false);
        virtual void    childClear (const String & key);
    };

    /**
        Stores a document in memory and coordinates with its persistent form on disk.
        We assume that only one instance of this class exists for a given disk document
        at any given moment, and that no other process in the system modifies the file on disk.

        We inherit the value field from MVolatile. Since we don't really have a direct value,
        we store a copy of the file name there. This is used to implement several functions, such
        as renaming. It also allows an instance to stand alone, without being a child of an MDir.

        We inherit the children collection from MVolatile. This field is left null until we first
        read in the associated file on disk. If the file is empty or non-existent, children becomes
        non-null but empty. Thus, whether children is null or not safely indicates the need to load.

        MDoc.save() must be called explicitly to write data out to disk. If you destroy a standalone
        MDoc with unsaved changes, they will be lost. However, if this MDoc is managed by an MDocGroup,
        then the group object will automatically save all its documents when it is destroyed.
    **/
    class SHARED MDoc : public MPersistent
    {
    public:
        /**
            Sets how load() should handle an exception.
            An exception during load() is the normal consequence of a newly-created doc.
            However, another use-case is loading existing data, and the caller may
            want some feedback in case missing data crashes the program later.
            The modes are:
                0=do nothing (default),
                1=print warning to stderr,
                2=throw an exception
        **/
        static void setMissingFileException (int method);

        /**
            Constructs a stand-alone document with blank key.
            In this case, the value contains the full path to the file on disk.
            @param root Path separator must be forward slash (/), regardless of platform.
        **/
        MDoc (const String & path);
        /**
            Constructs a stand-alone document with specified key.
            In this case, the value contains the full path to the file on disk.
            @param root Path separator must be forward slash (/), regardless of platform.
        **/
        MDoc (const String & path, const String & key);
        virtual uint32_t classID () const;

        virtual void markChanged ();
        virtual int  size        ();
        virtual bool data        ();
        /**
            The value of an MDoc is defined to be its full path on disk.
            Note that the key for an MDoc depends on what kind of collection contains it.
            In an MDir, the key is the primary file name (without path prefix and suffix).
            For a stand-alone document the key is arbitrary, and the document may be stored
            in another MNode with arbitrary other objects.
        **/
        virtual String getOrDefault (const String & defaultValue);
        /**
            If this is a stand-alone document, then move the file on disk.
            Otherwise, do nothing. DeleteDoc.undo() relies on this class to do nothing for a regular doc,
            because it uses merge() to restore the data, and merge() will touch the root value.
        **/
        virtual void     set   (const char * value);
        virtual void     move  (const String & fromKey, const String & toKey);
        virtual Iterator begin ();

        String path ();  ///< subroutine of load() and save()
        /**
            We only load once. We assume no other process is modifying the files, so once loaded, we know its exact state.
        **/
        void load ();
        void save ();
        /**
            Removes this document from persistent storage, but retains its contents in memory.
        **/
        void deleteFile () const;

        using MNode::data;
        using MNode::getOrDefault;
        using MNode::set;

        friend MDocGroup;
        friend MDir;

    protected:
        bool needsRead;  ///< Indicates whether initial load has been done.
        static int missingFileException;

        /**
            Constructs a document as a child of an MDocGroup.
            In this case, "path" is the full path to the file on disk.
            The default implementation of MDocGroup also sets key to the full path.
        **/
        MDoc (MDocGroup & container, const String & path, const String & key);

        /**
            Constructs a document as a child of an MDir.
            In this case, the key contains the file name in the dir, and the full path is constructed
            when needed using information from the parent.
        **/
        MDoc (MDir & container, const String & key);

        virtual MNode & childGet   (const String & key, bool create = false);
        virtual void    childClear (const String & key);
    };

    /**
        Holds a collection of MDocs and ensures that any changes get written out to disk.
        Assumes that the MDocs are at random places in the file system, and that the key contains the full path.
        MDir makes the stronger assumption that all files share the same directory, so the key only contains the file name.

        This node takes responsibility for the memory used by all MDocs it holds.
        The original Java code uses soft references to allow MDocs to be garbage collected when
        they're not in use, while assuring object identity while they are. To do something
        equivalent in C++, you would have to use a weak_ptr for all node handling, which makes
        for ugly code. Instead, there is a function for explicitly releasing an MDoc.
        If you retrieve it again later, it will be recreated from file.
    **/
    class SHARED MDocGroup : public MNode
    {
    public:
        MDocGroup (const char * key = 0);
        /**
            Writes pending changes to disk, then frees memory in use by all managed documents.
            This method will not wait on the mutex before processing writeQueue.
            All other threads should be done with this object before it is destroyed.
        **/
        virtual ~MDocGroup ();
        virtual uint32_t classID () const;

        virtual String   key          () const;
        virtual String   getOrDefault (const String & defaultValue);
        /**
            Empty this group of all documents.
            In the base implementation, the files themselves will not be deleted.
            OTOH, MDir does delete the entire directory from disk.
            All pending changes will be lost.
        **/
        virtual void     clear ();
        virtual int      size  ();
        /**
            Renames an MDoc on disk.
            If you already hold a reference to the MDoc named by fromKey, then that reference remains valid
            after the move.
        **/
        virtual void     move  (const String & fromKey, const String & toKey);
        virtual Iterator begin ();

        /**
            Generates a path for the MDoc, based only on the key.
            This requires making restrictive assumptions about the mapping between key and path.
            This base class assumes the key is literally the path, as a string.
            MDir assumes that the key is a file or subdir name within a given directory.
        **/
        virtual String pathForDoc (const String & key) const;
        /**
            Similar to pathForDoc(), but gives path to the file for the purposes of moving or deleting.
            This is different from pathForDoc() in the specific case of an MDir that has non-null suffix.
            In that case, the file is actually a subdirectory that contains both the MDoc and possibly
            other files.
        **/
        virtual String pathForFile (const String & key) const
        {
            return pathForDoc (key);
        }
        /**
            Writes pending changes to disk.
        **/
        void save ();
        /**
            Release a document from memory, while retaining the file on disk and our knowledge of it.
            If the document has unsaved changes, they will be written before the memory is freed.
        **/
        void unload (MDoc * doc);

        using MNode::clear;
        using MNode::getOrDefault;

        friend MDoc;

    protected:
        String                          name;  // We could be held in an even higher-level node.
        std::map<String, MDoc *, Order> children;
        std::set<MDoc *>                writeQueue;

        virtual MNode & childGet   (const String & key, bool create = false);
        virtual void    childClear (const String & key);
    };

    /**
        A top-level node which maps to a directory on the file system.
        Each child node maps to a file under this directory. However, the document file need not
        be a direct child of this directory. Instead, some additional pathing may be added.
        This allows the direct children of this directory to be subdirectories, and each document
        file may be a specifically-named entry in a subdirectory.
    **/
    class SHARED MDir : public MDocGroup
    {
    public:
        /**
            @param root Path separator must be forward slash (/), regardless of platform.
        **/
        MDir (const String & root, const char * suffix = 0, const char * key = 0);
        virtual uint32_t classID () const;

        virtual String key          () const;
        virtual String getOrDefault (const String & defaultValue);

        /**
            Empty this directory of all files.
            This is an extremely dangerous function! It destroys all data in the directory on disk and all data pending in memory.
        **/
        virtual void     clear ();

        virtual int      size  ();
        virtual bool     data  ();
        virtual Iterator begin ();
        virtual String   pathForDoc  (const String & key) const;
        virtual String   pathForFile (const String & key) const;
        void load ();

        using MNode::clear;
        using MNode::data;
        using MNode::getOrDefault;

    protected:
        String root;   ///< The directory containing the files or subdirs that constitute the children of this node
        String suffix; ///< Relative path to document file, or null if documents are directly under root
        bool   loaded; ///< Indicates that an initial read of the dir has been done. After that, it is not necessary to monitor the dir, only keep track of documents internally.

        virtual MNode & childGet (const String & key, bool create = false);
    };

    class SHARED Schema
    {
    public:
        int    version;  ///< Version 0 means unknown. Version -1 means no-care. Otherwise, version is always positive and increments by 1 with each significant change.
        String type;

        Schema (int version, const String & type);

        /**
            Returns an object suitable for writing out MNodes in the current format.
            The caller must delete this object when done, or memory will leak.
        **/
        static Schema * latest ();

        /**
            Convenience method which reads the header and loads all the objects as children of the given node.
            @param node The contents of the stream get added to this node. The node does not have to be empty,
            but any key that matches a node in the stream will get overwritten.
            @param schema Returns a pointer to the interpreter object. This can be used to obtain version
            information. The caller is responsible to delete this object, or memory will leak. If nullptr is
            passed, this function will delete the object internally.
        **/
        static void readAll (MNode & node, std::istream & reader, Schema ** schema = nullptr);

        /**
            Determines format of the stream and returns an object capable of reading it.
            The caller must delete this object when done, or memory will leak.
        **/
        static Schema * read (std::istream & reader);

        /**
            Low-level routine to interpret contents of stream.
        **/
        virtual void read (MNode & node, std::istream & reader) = 0;

        /**
            Convenience method which writes the header and all the children of the given node.
            The node itself (that is, its key and value) are not written out. The node simply acts
            as a container for the nodes that get written.
        **/
        void writeAll (MNode & node, std::ostream & writer);

        /**
            Writes the header.
        **/
        void write (std::ostream & writer);

        /**
            Convenience function for calling write(MNode,ostream,string) with no initial indent.
        **/
        void write (MNode & node, std::ostream & writer);

        /**
            Low-level routine to write out node in the appropriate format.
        **/
        virtual void write (MNode & node, std::ostream & writer, const String & indent) = 0;
    };

    class SHARED Schema2 : public Schema
    {
    public:
        Schema2 (int version, const String & type);

        virtual void read (MNode & node, std::istream & reader);
        void read (MNode & node, LineReader & reader, int whitespaces);  ///< Subroutine of read(MNode,istream)
        virtual void write (MNode & node, std::ostream & writer, const String & indent);
    };

    class LineReader
    {
    public:
        std::istream & reader;
        String         line;
        int            whitespaces;

        LineReader (std::istream & reader);
        void getNextLine ();
    };

    // Utility functions
    SHARED std::ostream & operator<< (std::ostream & out, MNode & value);
    SHARED char * strdup (const char * from);  ///< This function is not always available (not standard C), so we just provide our own.
    SHARED void create_directories (const String & path);  ///< Ensure all parents of path exist
    SHARED void remove_all (const String & path);  ///< Recursively deletes directory or file.
    SHARED bool exists (const String & path);
    SHARED bool is_directory (const String & path);
}

namespace std
{
    template <>
    struct hash<n2a::MNode>
    {
        size_t operator() (const n2a::MNode & value) const noexcept
        {
            return hash<String> {} (value.key ());  // TODO: are curly braces in middle necessary?
        }
    };

    template<>
    struct less<n2a::MNode>
    {
        bool operator() (const n2a::MNode & a, const n2a::MNode & b) const
        {
            return n2a::MNode::compare (a.key (), b.key ()) < 0;
        }
    };
}


#endif
