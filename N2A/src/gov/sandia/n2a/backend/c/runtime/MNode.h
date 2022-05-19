/*
This collection of utility classes reads and writes N2A model files, which
are stored in a lightweight document database.

The source file (MNode.cc) can function as a header-only implementation, or it
can be compiled separately and added to a library. In the latter case, you can
use this header file like any other, to bring in symbols so you can use MNode
in an application.

Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

#ifndef n2a_mnode_h
#define n2a_mnode_h

#include <string>  // We use the provided STL string class rather than our own minimalist String implementation.
#include <cstring>
#include <strings.h>
#include <vector>
#include <map>
#include <unordered_set>
#include <mutex>
#include <cmath>
#include <sstream>
#include <iostream>


namespace n2a
{
    class MNode;
    class MVolatile;
    class Schema;
    class Schema2;
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
    class MNode
    {
    public:
        // Certain functions/data should only be access by one thread at a time.
        // These are node-specific, so every node has a mutex.
        // If memory or other resource utilization becomes an issue, this object could be made static.
        // That would force serial access even on independent data.
        std::recursive_mutex mutex;
        static MNode none;  // For return values, indicating node does not exist.

        virtual ~MNode ();

        virtual std::string key () const;

        /**
            Returns an array of keys suitable for locating this node relative to its root node.
            Does not include the key of the root node itself.
        **/
        std::vector<std::string> keyPath () const;

        /**
            Returns an array of keys suitable for locating this node relative to the given root node.
            Does not include the key of the root node itself.
            If the given root node is not on the path to the actual root, then it is ignored and
            the full path to actual root is returned.
        **/
        std::vector<std::string> keyPath (const MNode & root) const;

        /**
            Utility function for printing node's key path.
        **/
        std::string keyPathString () const;

        /**
            Utility function for printing node's key path relative to given root node.
        **/
        std::string keyPathString (const MNode & root) const;

        int depth () const;

        int depth (const MNode & root) const;

        virtual MNode & parent () const;

        MNode & root () const;

        /**
            Find the last common ancestor between this node and the given node.
            If the nodes do not share a common ancestor, the result is none.
        **/
        MNode & lca (const MNode & that) const;

        /**
            Returns the child indicated by the given key, or none if it doesn't exist.
            This function is separate from child(string...) for ease of implementing subclasses.
        **/
        virtual MNode & child (const std::string & key) const;

        /**
            Returns a child node from arbitrary depth, or none if any part of the path doesn't exist.
            Notice that the object "none" is a functional empty node, so it can be used for iteration.
        **/
        MNode & child (const std::vector<std::string> & keys) const;

        /**
            Retrieves a child node from arbitrary depth, or creates it if nonexistent.
            Like a combination of child() and set().
            The benefit of getting back a node rather than a value is ease of access
            to a list stored as children of the node.
        **/
        MNode & childOrCreate (const std::vector<std::string> & keys);

        /**
            Convenience method for retrieving node at an ordinal position without knowing the specific key.
            Children are traversed in the usual key order.
            If index is out of range, then result is null.
        **/
        MNode & childAt (int index) const;

        /**
            Remove all children.
            Releases memory held by children. Any reference to a child is no longer
            valid and should not be used.
        **/
        virtual void clear ();

        /**
            Removes child with the given key, if it exists.
            This function is separate from clear(string...) for ease of implementing subclasses.
        **/
        virtual void clear (const std::string & key);

        /**
            Removes child with arbitrary depth.
            If no key is specified, then removes all children of this node.
        **/
        void clear (const std::vector<std::string> & keys);

        /**
            @return The number of children we have.
        **/
        virtual int size () const;

        bool isEmpty () const;

        /**
            Indicates whether this node is defined.
            Works in conjunction with size() to provide information similar to the MUMPS function "DATA".
            Since get() returns "" for undefined nodes, this is the only way to determine whether a node
            is actually defined to "" or is undefined. "Undefined" is not the same as non-existent,
            because and undefined node can have children. Only a child() call on a parent can confirm
            the complete non-existence of a node.
        **/
        virtual bool data () const;

        bool data (const std::vector<std::string> & keys) const;

        /**
            Determines if the given key exists anywhere in the hierarchy.
            This is a deep query. For a shallow (one-level) query, use child() instead.
        **/
        bool containsKey (const std::string & key) const;

        /**
            @return This node's value, with "" as default
        **/
        std::string get () const;

        /**
            Digs down tree as far as possible to retrieve value; returns "" if node does not exist.
        **/
        std::string get (const std::vector<std::string> & keys) const;

        /**
            Returns this node's value, or the given default if node is undefined or set to "".
            This is the only get function that needs to be overridden by subclasses.
        **/
        virtual std::string getOrDefault (const std::string & defaultValue) const;

        /**
            Digs down tree as far as possible to retrieve value; returns given defaultValue if node does not exist or is set to "".
        **/
        std::string getOrDefault (const std::string & defaultValue, const std::vector<std::string> & keys) const;

        bool getOrDefault (bool defaultValue, const std::vector<std::string> & keys) const;

        int getOrDefault (int defaultValue, const std::vector<std::string> & keys) const;

        long getOrDefault (long defaultValue, const std::vector<std::string> & keys) const;

        double getOrDefault (double defaultValue, const std::vector<std::string> & keys) const;

        /**
            Interprets value as boolean:
            true = "1" or "true";
            false = everything else, including empty.
            See getFlag() for a different way to interpret booleans. The key difference is
            that a boolean defaults to false.
        **/
        bool getBool (const std::vector<std::string> & keys) const
        {
            return getOrDefault (false, keys);
        }

        /**
            Interprets value as flag, which may contain extended information when set:
            false <-- "0", non-existent, or no data
            true <-- everything else, including empty
            See getBoolean() for a different way to interpret booleans. The key difference is
            that a flag defaults to true, so it can indicate something by merely existing, without a value.
            It also tolerates arbitrary content, so a flag can carry extra data and still be interpreted as true.
        **/
        bool getFlag (const std::vector<std::string> & keys) const;

        int getInt (const std::vector<std::string> & keys) const
        {
            return getOrDefault (0, keys);
        }

        long getLong (const std::vector<std::string> & keys) const
        {
            return getOrDefault (0l, keys);
        }

        double getDouble (const std::vector<std::string> & keys) const
        {
            return getOrDefault (0.0, keys);
        }

        /**
            Sets this node's own value.
            Passing nullptr makes future calls to data() returns false, that is, makes the value of this node undefined.
            Should be overridden by a subclass.
        **/
        virtual void set (const char * value);

        void set (const std::string & value)
        {
            set (value.c_str ());
        }

        void set (bool value)
        {
            set (value ? "1" : "0");
        }

        void set (int value)
        {
            set (std::to_string (value));
        }

        void set (long value)
        {
            set (std::to_string (value));
        }

        void set (double value)
        {
            set (std::to_string (value));
        }

        void set (const MNode & value);

        /**
            Sets value of child node specified by key, effectively with a call to child.set(char *).
            Creates child node if it doesn't already exist.
            Should be overridden by a subclass.
            @return The child node on which the value was set.
        **/
        virtual MNode & set (const char * value, const std::string & key);

        /**
            Creates all children necessary to set value
        **/
        MNode & set (const char * value, const std::vector<std::string> & keys);

        MNode & set (const std::string & value, const std::vector<std::string> & keys);

        MNode & set (bool value, const std::vector<std::string> & keys);

        MNode & set (int value, const std::vector<std::string> & keys);

        MNode & set (long value, const std::vector<std::string> & keys);

        MNode & set (double value, const std::vector<std::string> & keys);

        /**
            Merges the given node at the given point in the tree.
            See merge()
        **/
        MNode & set (const MNode & value, const std::vector<std::string> & keys);

        /**
            Deep copies the source node into this node, while leaving any non-overlapping values in
            this node unchanged. The value of this node is only replaced if the source value is defined.
            Children of the source node are then merged with this node's children.
        **/
        void merge (const MNode & that);

        /**
            Deep copies the source node into this node, while leaving all values in this node unchanged.
            This method could be called "underride", but that already has a special meaning in MPart.
        **/
        void mergeUnder (const MNode & that);

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
        void uniqueNodes (const MNode & that);

        /**
            Modifies this tree so it contains only nodes which differ from the given tree ("that")
            in either key or value. Any parent nodes which are not also differences will be undefined.
            See uniqueNodes(MNode) for an explanation of tree differencing.
        **/
        void uniqueValues (const MNode & that);

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
        void changes (const MNode & that);

        /**
            Changes the key of a child.
            A move only happens if the given keys are different (same key is no-op).
            Any previously existing node at the destination key will be completely erased and replaced.
            An entry will no longer exist at the source key.
            If the source does not exist before the move, then neither node will exist afterward.
            Many subclasses guarantee object identity, but that is not a requirement.
            The safest approach is to call child(toKey) to get a reference to the renamed node.
        **/
        virtual void move (const std::string & fromKey, const std::string & toKey);

        struct Iterator
        {
            using iterator_category = std::input_iterator_tag;
            using difference_type   = int;
            using value_type        = MNode;
            using pointer           = MNode *;
            using reference         = MNode &;

            const MNode & container;
            // We always pull a copy of the keys, rather than iterating directly on child nodes.
            // This allows the caller to delete nodes in the middle of iteration.
            // Dereferencing the iterator could return none.
            std::vector<std::string> keys;
            int                      count;  // number of keys; This a trade of space for time.
            int                      i;  // position in keys
            std::string              key;

            Iterator (const MNode & container, const std::vector<std::string> & keys)
            :   container (container),
                keys (keys)
            {
                count = keys.size ();
                i = 0;
                if (i < count) key = keys[i];
            }

            /// Special constructor just for end()
            Iterator (const MNode & container, int count)
            :   container (container)
                // keys is initialized to empty
            {
                this->count = 0;  // strictly speaking, we're faking the keys collection
                i = count;
                // There is no key to be had, so leave it blank.
            }

            reference operator* () const
            {
                return container.child (key);
            }

            pointer operator-> ()
            {
                return & container.child (key);
            }

            Iterator & operator++ ()  // prefix increment
            {
                i++;
                if (i < count) key = keys[i];
                return *this;
            }

            Iterator operator++ (int)  // postfix increment
            {
                if (i < count) key = keys[i];
                i++;
                return *this;
            }

            friend bool operator== (const Iterator & a, const Iterator & b)
            {
                if (&a.container != &b.container) return false;
                return a.i == b.i;  // key could be different depending on combination of prefix and postfix increment. For simplicity, we only pay attention to current position.
            }  // TODO: is this semicolon necessary?

            friend bool operator!= (const Iterator & a, const Iterator & b)
            {
                return ! (a == b);
            };
        };

        virtual Iterator begin () const;

        virtual Iterator end () const;

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
        static int compare (const std::string & A, const std::string & B)
        {
            return compare (A.c_str (), B.c_str ());
        }

        struct Order
        {
            bool operator() (const char * a, const char * b) const
            {
                return compare (a, b) < 0;
            }
            bool operator() (const std::string & a, const std::string & b) const
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

        std::string toString ();
    };

    class MVolatile : public MNode
    {
    public:
        std::string                            name;
        char *                                 value;  ///< Our own local copy of string. We are responsible to free this memory. We use char * rather than std::string so this value can be null, not merely empty.
        MNode *                                container;
        std::map<const char *, MNode *, Order> children;  // Children are always a subclass of MVolatile. As long as a child exists, we can simply hold a pointer to its name.

        MVolatile (const char * value = 0, const char * name = 0, MNode * container = 0);
        virtual ~MVolatile ();  ///< frees memory used by children

        virtual std::string key          () const;
        virtual MNode &     parent       () const;
        virtual MNode &     child        (const std::string & key) const;
        virtual void        clear        ();
        virtual void        clear        (const std::string & key);
        virtual int         size         () const;
        virtual bool        data         () const;
        virtual std::string getOrDefault (const std::string & defaultValue) const;
        virtual void        set          (const char * value);  ///< copies the memory, on assumption that the caller could delete it
        virtual MNode &     set          (const char * value, const std::string & key);
        virtual void        move         (const std::string & fromKey, const std::string & toKey);  ///< If you already hold a reference to the node named by fromKey, then that reference remains valid and its key is updated.
        virtual Iterator    begin        () const;
        virtual Iterator    end          () const;

        // C++ name resolution
        using MNode::set;
    };

    class Schema
    {
    public:
        int         version;  ///< Version 0 means unknown. Version -1 means no-care. Otherwise, version is always positive and increments by 1 with each significant change.
        std::string type;

        Schema (int version, const std::string & type);

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
        void writeAll (const MNode & node, std::ostream & writer);

        /**
            Writes the header.
        **/
        void write (std::ostream & writer);

        /**
            Convenience function for calling write(MNode,ostream,string) with no initial indent.
        **/
        void write (const MNode & node, std::ostream & writer);

        /**
            Low-level routine to write out node in the appropriate format.
        **/
        virtual void write (const MNode & node, std::ostream & writer, const std::string & indent) = 0;
    };

    class Schema2 : public Schema
    {
    public:
        Schema2 (int version, const std::string & type);

        virtual void read (MNode & node, std::istream & reader);
        void read (MNode & node, LineReader & reader, int whitespaces);  ///< Subroutine of read(MNode,istream)
        virtual void write (const MNode & node, std::ostream & writer, const std::string & indent);
    };

    class LineReader
    {
    public:
        std::istream & reader;
        std::string    line;
        int            whitespaces;

        LineReader (std::istream & reader);
        void getNextLine ();
    };

    inline std::ostream & operator<< (std::ostream & out, const MNode & value)
    {
        Schema * schema = Schema::latest ();
        schema->write (value, out);
        delete schema;
        return out;
    }

    std::string trim (const std::string & value)
    {
        size_t start = value.find_first_not_of (" ");
        if (start == std::string::npos) return "";  // all spaces
        size_t finish = value.find_last_not_of (" ");
        if (finish == std::string::npos) return value.substr (start);
        return value.substr (start, finish - start + 1);
    }

    // This function is not always available (not standard C), so we just provide our own.
    char * strdup (const char * from)
    {
        int length = strlen (from);
        char * result = (char *) malloc (length + 1);
        // Don't bother checking for null result. In that case, we're already in trouble, so doesn't matter where the final explosion happens.
        strcpy (result, from);
        return result;
    }
}

namespace std
{
    template <>
    struct hash<n2a::MNode>
    {
        size_t operator() (const n2a::MNode & value) const noexcept
        {
            return hash<string> {} (value.key ());  // TODO: are curly braces in middle necessary?
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
