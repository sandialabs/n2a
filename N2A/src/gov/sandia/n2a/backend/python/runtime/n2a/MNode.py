"""
    A collection of classes for operating on N2A model files.
    This includes both file IO and in-memory manipulation.

    Copyright 2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
    Under the terms of Contract DE-NA0003525 with NTESS,
    the U.S. Government retains certain rights in this software.
"""

import io
import sys
import os
import re
import pathlib
import weakref
from weakref import WeakValueDictionary
from abc import abstractstaticmethod

class MNode:
    """
        A hierarchical key-value storage system, with subclasses that provide persistence.
        The "M" in MNode refers to the MUMPS language, in which variables have this hierarchical structure.
        MUMPS is one of the earliest hierarchical key-value system, designed in 1966.

        This is the base class for more specific implementations. It is minimally functional, but generally
        you shouldn't directly instantiate it. Instead, use something like MVolatile.
        This class defines a wide range of operations and utility functions. Usually you operate on MNodes
        without knowing the specific implementation class.
    
        A node can be "undefined". For the most part, this behaves as having a value of ''.
        Undefined nodes have a special role in tree differencing, where they act as structural placeholders.
        The tree-differencing and merge functions in this class assume that leaf nodes are always defined.
    """

    # There is no __init__ function. Direct instances have no storage.

    def key(self):
        return ''

    def keyPath(self, root=None):
        """
            Returns an array of keys suitable for locating this node relative to the given root node.
            Does not include the key of the root node itself.
            If the given root node is not on the path to the actual root, then it is ignored and
            the full path to actual root is returned.
        """
        index = self.depth(root)
        result = [None] * index
        parent = self
        for i in range(1, index+1):
            result[-i] = parent.key()
            parent = parent.parent()
        return result;

    def keyPathString(self, root=None):
        """
            Utility function for printing node's key path relative to given root node.
            The key path is a dot-separated list of keys.
        """
        keyPath = self.keyPath(root)
        if not keyPath: return ''
        result = keyPath[0]
        for i in range(1, len(keyPath)): result += '.' + keyPath[i]
        return result

    def depth(self, root=None):
        if self is root: return 0
        parent = self.parent()
        if parent == None: return 0
        return parent.depth(root) + 1

    def parent(self):
        return None

    def root(self):
        result = self
        while True:
            parent = result.parent()
            if parent == None: return result
            result = parent

    def lca(self, other):
        """
            Find the last common ancestor between this node and the given node.
            If the nodes do not share a common ancestor, the result is null.
        """

        # Strategy: Place the ancestry of one node in a set. Then walk up the ancestry
        # of the other node. The first ancestor found in the set is the LCA.

        thisAncestors = {}
        A = self
        while A:
            thisAncestors.add(A)
            A = A.parent()

        B = other
        while B:
            if B in thisAncestors: return B
            B = B.parent()

        return None

    def getChild(self, key):
        """
            Returns the child indicated by the given key, or null if it doesn't exist.
            This function is separate from child(String...) for ease of implementing subclasses.
        """
        return None

    def child(self, *keys):
        """
            Returns a child node from arbitrary depth, or null if any part of the path doesn't exist.
        """
        result = self  # If no keys are specified, we return this node.
        for key in keys:
            c = result.getChild(str(key))  # keys must always be strings
            if c == None: return None
            result = c
        return result

    def childOrCreate(self, *keys):
        """
            Retrieves a child node from arbitrary depth, or creates it if nonexistent.
            Like a combination of child() and set().
            The benefit of getting back a node rather than a value is ease of access
            to a list stored as children of the node.
        """
        result = self
        for key in keys:
            key = str(key)
            c = result.getChild(key)
            if c == None: c = result.set(None, key)
            result = c
        return result;

    def childOrEmpty(self, *keys):
        """
            Convenience method for iterating over an arbitrary sub-node.
            If the node doesn't exist, returns a temporary value with no children.
            The returned value is not attached to any tree, and should not be used for anything except iteration.
        """
        result = self.child(keys)
        if result == None: return MNode()
        return result

    def childAt(self, index):
        """
            Convenience method for retrieving node at an ordinal position without knowing the specific key.
            Children are traversed in the usual key order.
            If index is out of range, then result is None.
        """
        if index < 0: return None
        for c in self:
            if index == 0: return c
            index -= 1
        return None

    def clear(self):
        """
            Remove all children.
        """
        for n in self: clearChild(n.key())

    def clearChild(self, key):
        """
            Removes child with the given key, if it exists.
            This function is separate from clear(String...) for ease of implementing subclasses.
        """
        pass

    def clear(self, *keys):
        """
            Removes child with arbitrary depth.
            If no key is specified, then removes all children of this node.
        """
        if not keys:
            self.clear()
            return

        c = self
        last = len(keys) - 1
        for i in range(last):
            c = c.getChild(str(keys[i]))
            if c == None: return  # Nothing to clear
        c.clearChild(str(keys[last]))

    def size(self):
        """
            :return: The number of children we have.
        """
        return 0

    def isEmpty(self):
        return self.size() == 0

    def data(self):
        """
            Indicates whether this node is defined.
            Works in conjunction with isEmpty() to provide information similar to the MUMPS function "DATA".
            Since get() returns '' for undefined nodes, this is the only way to determine whether a node
            is actually defined to '' or is undefined. "Undefined" is not the same as non-existent,
            because and undefined node can have children. A child() call on the parent can confirm the
            complete non-existence of a node. Alternately, if the node does not exist, then data()
            returns false and isEmpty() returns true.
        """
        return False

    def data(self, *keys):
        c = self.child(keys)
        if c == None: return False
        return c.data()

    def containsKey(self, key):
        """
            Determines if the given key exists anywhere in the hierarchy.
            This is a deep query. For a shallow (one-level) query, use child() instead.
        """
        if child(key): return True
        for c in self:
            if key in c: return True
        return False

    def get(self):
        """
            :return: This node's value, with '' as default
        """
        return self.getOrDefault('')

    def get(self, *keys):
        """
            Digs down tree as far as possible to retrieve value; returns '' if node does not exist.
        """
        c = self.child(keys)
        if c == None: return ''
        return c.get()

    def getOrDefault(self, defaultValue):
        """
            Returns this node's value, or the given default if node is undefined or set to ''.
            This is the only get*() function that needs to be overridden by subclasses.
        """
        return defaultValue

    def getOrDefault(self, defaultValue, *keys):
        """
            Digs down tree as far as possible to retrieve value; returns given defaultValue if node does not exist or is set to "".
        """
        value = self.get(keys)
        if not value: return defaultValue
        if isinstance(defaultValue, bool ): return value.strip() == '1'
        if isinstance(defaultValue, int  ): return round(float(value))
        if isinstance(defaultValue, float): return float(value)
        return value

    def getBoolean(self, *keys):
        """
            Interprets value as boolean, with a small extension to Java's string parser:
            true <-- "1" or "true";
            false <-- everything else, including empty and undefined.
            See getFlag() for a different way to interpret booleans. The key difference is
            that a boolean defaults to false.
        """
        return self.getOrDefault(False, keys)

    def getFlag(self, *keys):
        """
            Interprets value as flag, which may contain extended information when set:
            False <-- "0" or non-existent;
            True <-- everything else, including empty and undefined.
            See getBoolean() for a different way to interpret booleans. The key difference is
            that a flag defaults to true, so it can indicate something by merely existing, without a value.
            It also tolerates arbitrary content, so a flag can carry extra data (either its value
            or children) and still be interpreted as true.
        """
        c = self.child(keys)
        if c == None  or  c.get() == '0': return False
        return True

    def getInt(self, *keys):
        return self.getOrDefault(0, keys)

    def getFloat(self, *keys):
        return self.getOrDefault(0.0, keys)

    def set(self, value):
        """
            Sets this node's own value.
            Passing None makes future calls to data() return False, that is, makes the value of this node undefined.
            Should be overridden by a subclass.
        """
        pass

    def set(self, value, key):  # TODO: if this form conflicts with the others, call it setChild() instead.
        """
            Sets value of child node specified by key, effectively with a call to child.set(String).
            Creates child node if it doesn't already exist.
            Should be overridden by a subclass.
            :returns: The child node on which the value was set.
        """
        return MNode()  # A lie that allows this base class to be minimally useful.

    def set(self, value, *keys):
        """
            Creates all children necessary to set value
        """
        result = self.childOrCreate(keys)
        if isinstance(value, MNode):
            result.clear()    # get rid of all children
            result.set(None)  # ensure that if value node is undefined, result node will also be undefined
            result.merge(value)
        else:
            if isinstance(value, bool): value = '1' if value else '0'
            elif value:                 value = str(value)
            result.set(value)
        return result

    def setTruncated(self, value, precision, *keys):
        """
            Formats a number by truncating it to the given number of decimal places.
            Drops any trailing zeroes to produce the most compact form.
            The purpose of this function is to efficiently store numbers when the
            desired/reasonable precision is known. Otherwise, might get a bunch of junk
            precision at the end of the string.
            :param value: Must have a relatively small exponent, because this routine
            relies on converting to integer then back again.
            :param precision: Of the digits after (to the right of) the decimal point.
            Cannot be negative.
        """
        shift = 10**precision
        converted = str(round(value * shift) / shift)
        pos = converted.rfind('.')
        if pos >= 0:
            end = min(pos + precision, len(converted) - 1)
            while end >= pos:
                c = converted[end]
                if c != '0'  and  c != '.': break
                end -= 1
            converted = converted[0:end + 1]
        return self.set(converted, keys)

    def merge(self, other):
        """
            Deep copies the source node into this node, while leaving any non-overlapping values in
            this node unchanged. The value of this node is only replaced if the source value is defined.
            Children of the source node are then merged with this node's children.
        """
        if other.data(): self.set(other.get())
        for otherChild in other:
            key = otherChild.key()
            c = self.getChild(key)
            if c == None: c = self.set(None, key)  # ensure a target child node exists
            c.merge(otherChild)

    def mergeUnder(self, other):
        """
            Deep copies the source node into this node, while leaving all values in this node unchanged.
            This method could be called "underride", but that already has a special meaning in MPart.
        """
        if not self.data()  and  other.data(): self.set(other.get())
        for otherChild in other:
            key = otherChild.key()
            c = self.getChild(key)
            if c == None: self.set(otherChild, key)
            else:         c.mergeUnder(otherChild)

    def uniqueNodes(self, other):
        """
            Modifies this tree so it contains only nodes which are not defined in the given tree ("other").
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
        """
        if other.data(): self.set(None)
        for c in self:
            key = c.key()
            d = other.getChild(key)
            if d == None: continue
            c.uniqueNodes(d)
            if c.isEmpty()  and  not c.data(): self.clearChild(key)

    def uniqueValues(self, other):
        """
            Modifies this tree so it contains only nodes which differ from the given tree ("other")
            in either key or value. Any parent nodes which are not also differences will be undefined.
            See uniqueNodes(MNode) for an explanation of tree differencing.
        """
        if self.data()  and  other.data()  and  self.get() == other.get(): self.set(None)
        for c in this:
            key = c.key()
            d = other.getChild(key)
            if d == None: continue
            c.uniqueValues(d)
            if c.isEmpty()  and  not c.data(): self.clearChild(key)

    def changes(self, other):
        """
            Assuming "other" will be the target of a merge, saves any values this node would change.
            The resulting tree can be used to revert a merge. Specifically, let:
            <pre>
            A = self tree (merge source) before any operations
            B = other tree (merge target) before any operations
            C = clone of A, then run C.uniqueNodes(B)
            D = clone of A, then run D.changes(B)
            Suppose you run B.merge(A). To revert B to its original state, run
            B.uniqueNodes(C) to remove/undefine nodes not originally in B
            B.merge(D) to restore original values
            </pre>
            See uniqueNodes(MNode) for more explanation of tree differencing.
        """
        if self.data():
            if other.data():
                value = other.get()
                if self.get() == value: self.set(None)
                else:                   self.set(value)
            else:
                self.set(None)
        for c in this:
            key = c.key()
            d = other.child(key)
            if d == None: self.clearChild(key)
            else:         c.changes(d)

    def move(self, fromKey, toKey):
        """
            Changes the key of a child.
            A move only happens if the given keys are different (same key is no-op).
            Any previously existing node at the destination key will be completely erased and replaced.
            An entry will no longer exist at the source key.
            If the source does not exist before the move, then neither node will exist afterward.
            Many subclasses guarantee object identity, but that is not a requirement.
            The safest approach is to call child(toKey) to get a reference to the renamed node.
        """
        if toKey == fromKey: return
        self.clearChild(toKey)
        source = self.getChild(fromKey)
        if source:
            destination = self.set(None, toKey)
            destination.merge(source)
            self.clearChild(fromKey)

    def visit(self, visitor):
        """
            Execute some operation on each node in the tree. Traversal is depth-first.
            visitor must be of a class with a method visit(node), where node is an MNode instance.
            visit() must return True to continue recursion below the current level, or False if further
            recursion is not needed.
        """
        if not visitor.visit(self): return
        for c in self: c.visit(visitor)

    class IteratorWrapper:
        def __init__(self, node, iterator):
            self.node     = node
            self.iterator = iterator

        def __next__(self):
            return self.node.child(self.iterator.next())  # Could return None if a child has been deleted after this iterator was created.

    def __iter__(self):
        """
            Note: The iterator is supposed to return nodes in M collation order.
            However, Python lacks standard support for sorted containers.
            Rather than fight this, we just iterate in whatever order dict returns.
            The alternative is to either sort keys whenever making a new iterator
            or to store children collections in a proper sorted map. That requires
            additional code or an external package dependency.
        """
        return IteratorWrapper(self, [].__iter__())

    def __len__(self):
        return self.size()

    def __bool__(self):
        return self.getFlag()

    def __str__(self):
        writer = StringIO()
        Schema.latest().write(self, writer)
        return writer.getvalue()

    def __getitem__(self, key):
        c = self.child(key)
        if c == None: raise KeyError
        return c.get()

    def __setitem__(self, key, value):
        self.set(value, key)

    def __delitem__(self, key):
        return self.clear(key)

    def __missing__(self, key):
        self.childOrCreate(key)

    def __contains__(self, key):
        return self.child(key) != None

    def __hash__(self):
        return hash(self.key())

    def __eq__(self, other):
        return self.key() == other.key()

    def __ne__(self, other):
        return self.key() != other.key()

    def __gt__(self, other):
        return compare(self.key(), other.key()) > 0

    def __ge__(self, other):
        return compare(self.key(), other.key()) >= 0

    def __lt__(self, other):
        return compare(self.key(), other.key()) < 0

    def __le__(self, other):
        return compare(self.key(), other.key()) <= 0

    @staticmethod
    def compare(A, B):
        """
            Compares two strings in M collation order.
            In short, this places all properly formed numbers ahead of non-numbers.
            Numbers are sorted by actual value rather than lexical representation.
            :return: Greater than zero if A>B. Zero if A==B. Less than zero if A<B.
        """
        if A == B: return 0  # If strings follow M collation rules, then compare for equals works for numbers.

        try:
            Avalue = float(A)
        except:
            Avalue = None
        try:
            Bvalue = float(B)
        except:
            Bvalue = None

        if Avalue == None:  # A is a string
            if Bvalue == None: return 1 if A > B else -1  # Both A and B are strings
            return 1;  # string > number
        else:  # A is a number
            if Bvalue == None: return -1;  # number < string
            # Both A and B are numbers
            if Avalue > Bvalue: return 1
            if Avalue < Bvalue: return -1  # This test is redundant with the first one above, but only for traditional M number formatting.
            return 0

    def equals(self, other):
        """
            Deep comparison of two nodes. All structure, keys and values must match exactly.
        """
        if self == other: return True
        if not isinstance(other, MNode): return False
        if not self.key() == other.key(): return False
        return self.equalsRecursive(other)

    def equalsRecursive(self, other):
        """
            Subroutine of equals(). Don't call directly.
        """
        if self.data() != other.data(): return False
        if self.get()  != other.get():  return False
        if self.size() != other.size(): return False
        for a in self:
            b = other.getChild(a.key())
            if b == None: return False
            if not a.equalsRecursive(b): return False
        return True

    def structureEquals(self, other):
        """
            Compares only key structure, not values.
        """
        if self.size() != other.size(): return False
        for a in self:
            b = other.getChild(a.key())
            if b == None: return False
            if not a.structureEquals(b): return False
        return True

class MVolatile(MNode):
    """
        A concrete implementation of MNode that works entirely in memory.
        value can be set to any type, not just string. However, standard MNode
        functions all return string. Use the special functions getObject() and
        getOrDefaultObject() to avoid string conversion.
    """

    def __init__(self, value=None, key=None, parent=None):
        self.value    = value
        self.key      = key
        self.parent   = parent
        self.children = None

    def key(self):
        if self.key == None: return ''
        return self.key

    def parent(self):
        return self.parent

    def getChild(self, key):
        if self.children == None: return None
        return self.children.get(key)  # Returns None if key is not present.

    def clear(self):
        if self.children != None: self.children.clear()

    def clearChild(self, key):
        if self.children == None: return
        try:
            del self.children[key]
        except KeyError:
            pass

    def size(self):
        if self.children == None: return 0
        return len(self.children)

    def data(self):
        return self.value != None

    def getOrDefault(self, defaultValue):
        if self.value == None: return defaultValue
        result = str(self.value)
        if not result: return defaultValue
        return result

    def getOrDefaultObject(self, defaultValue, *keys):
        c = self.child(keys)
        if c == None  or  c.value == None: return defaultValue
        return c.value

    def getObject(self, *keys):
        return self.getOrDefaultObject(None, keys)

    def set(self, value):
        self.value = value

    def set(self, value, key):
        if self.children == None: self.children = {}
        result = self.children.get(key)
        if result == None:
            result = MVolatile(value=value, key=key, parent=self)
            self.children[key] = result
            return result
        result.set(value)
        return result

    def setObject(self, value, *keys):
        """
            Store the value as an object, rather than converting to string.
        """
        result = self.childOrCreate(keys)
        result.value = value
        return result

    def link(self, node):
        """
            Adds the given node to our list of children.
            This can be thought of as a symbolic link.
            The node establishes no special relationship with this parent.
            In particular, it can still be the child of another parent, including
            one that it has a special connection with, such as an MDir.
        """
        if self.children == None: self.children = {}
        self.children[node.key()] = node

    def move(self, fromKey, toKey):
        """
            If you already hold a reference to the node named by fromKey, then that reference remains
            valid and its key is updated.
        """
        if toKey == fromKey: return
        if not self.children: return  # Nothing to move
        try:
            del self.children[toKey]
        except KeyError:
            pass
        source = self.children.get(fromKey)
        if source != None:
            try:
                del self.children[fromKey]
            except KeyError:
                pass
            if isinstance(source, (MVolatile, MDocGroup, MCombo)): source.key = toKey
            self.children[toKey] = source

    def __iter__(self):
        if self.children == None: return super().__iter__()
        return IteratorWrapper(list(self.children))  # Duplicate the keys, to avoid concurrent modification

class MPersistent(MVolatile):
    """
        A node that keeps track of whether it needs to be saved to disk.
    """

    def __init__(self, value=None, key=None, parent=None):
        super().__init__(value=value, key=key, parent=parent)
        self.needsWrite = False

    def markChanged(self):
        if self.needsWrite: return
        if isinstance(self.parent, MPersistent): self.parent.markChanged()
        self.needsWrite = True

    def clearChanged(self):
        self.needsWrite = False
        for i in self: i.clearChanged()

    def clear(self):
        super().clear()
        self.markChanged()

    def clearChild(self, key):
        super().clearChild(key)
        self.markChanged()

    def set(self, value):
        if self.value == value: return  # Covers all cases, including None
        self.value = value
        self.markChanged()

    def set(self, value, key):
        if self.children == None: self.children = {}
        result = self.children.get(key)
        if result == None:
            self.markChanged()
            result = MPersistent(value=value, key=key, parent=self)
            self.children[key] = result
            return result
        result.set(value)
        return result

    def move(self, fromKey, toKey):
        if toKey == fromKey: return
        if not self.children: return  # Nothing to move
        try:
            del self.children[toKey]
        except KeyError:
            pass
        source = self.children.get(fromKey)
        if source != None:
            try:
                del self.children[fromKey]
            except KeyError:
                pass
            self.children[toKey] = source
            source.key = toKey
            source.markChanged()  # This will also mark self, since we are the parent of source.
            return
        markChanged()

class MDoc(MPersistent):
    """
        Stores a document in memory and coordinates with its persistent form on disk.
        We assume that only one instance of this class exists for a given disk document
        at any given moment, and that no other process in the system modifies the file on disk.
    
        We inherit the value field from MVolatile. Since we don't really have a direct value,
        we store a copy of the file name there. This is used to implement several functions, such
        as renaming. It also allows an instance to stand alone, without being a child of an MDir.
    
        We inherit the children collection from MVolatile. This field is left as None until we first
        read in the associated file on disk. If the file is empty or non-existent, children becomes
        an empty collection. Thus, whether children is None safely indicates the need to load.
    """

    def __init__(self, path=None, key=None, parent=None):
        """
            :param path: Required for a stand-alone document. Should be None for a document that
            is part of an MDocGroup such as MDir.
            :param key: If key is None or empty, then path must point to file on disk.
            :param parent: If parent is an MDir, then key provides the file name within the dir.
            Otherwise, parent may be an arbitrary MNode class.
        """
        super().__init__(value=path, key=key, parent=parent);

    def getOrDefault(self, defaultValue):
        """
            The value of an MDoc is defined to be its full path on disk.
            Note that the key for an MDoc depends on what kind of collection contains it.
            In an MDir, the key is the primary file name (without path prefix and suffix).
            For a stand-alone document the key is arbitrary, and the document may be stored
            in another MNode with arbitrary other objects.
        """
        if isinstance(self.parent, MDocGroup): return str(self.parent.pathForDoc(self.key).absolute())
        if self.value == None: return defaultValue
        return str(self.value)

    def markChanged(self):
        if self.needsWrite: return

        # If this is a new document, then treat it as if it were already loaded.
        # If there is content on disk, it will be blown away.
        if self.children == None: self.children = {}
        self.needsWrite = True
        if isinstance(self.parent, MDocGroup): self.parent.writeQueue.add(self)

    def delete(self):
        """
            Removes this document from persistent storage, but retains its contents in memory.
        """
        if self.parent == None:  # Standalone file, with full path stored in value (as a path object, not a string).
            try:
                os.remove(self.value)
            except OSError:
                print('Failed to delete file:', self.value, file=sys.stderr)
        else:
            self.parent.clear(self.key)

    def getChild(self, key):
        if self.children == None: self.load()  # This test is redundant with the guard in load(), but should save time in the common case that file is already loaded
        return self.children.get(key)

    def clearChild(self, key):
        if self.children == None: self.load()
        super().clearChild(key)

    def size(self):
        if self.children == None: load()
        return len(self.children)

    def set(self, value):
        """
            If this is a stand-alone document, then move the file on disk. Otherwise, do nothing.
        """
        if self.parent != None: return  # Not stand-alone, so ignore.
        if value == self.value: return  # Don't call file move if location on disk has not changed.
        try:
            os.replace(self.value, value)
            self.value = value
        except OSError:
            print('Failed to move file:', self.value, '-->', value, file=sys.stderr)

    def set(self, value, key):
        if self.children == None: self.load()
        return super().set(value, key)

    def move(self, fromKey, toKey):
        if toKey == fromKey: return
        if self.children == None: self.load()
        super().move(fromKey, toKey)

    def __iter__(self):
        if self.children == None: self.load()
        return super().__iter__()

    def path(self):
        """
            Subroutine of load() and save().
        """
        if isinstance(self.parent, MDocGroup): return self.parent.pathForDoc(self.key)
        return value  # for stand-alone document

    def load(self):
        """
            We only load once. We assume no other process is modifying the files, so once loaded, we know its exact state.
        """
        if self.children != None: return  # already loaded
        self.children = {}
        path = self.path()
        self.needsWrite = True  # lie to ourselves, to prevent being put onto the MDir write queue
        with open(path) as reader:
            Schema.readAll(self, reader)
        self.clearChanged()  # After load(), clear the slate so we can detect any changes and save the document.

    def save(self):
        if not self.needsWrite: return
        path = self.path()
        try:
            os.makedirs(path.parent, exist_ok=True)
            with open(path, 'w') as writer:
                Schema.latest().writeAll(self, writer)
                self.clearChanged()
        except OSError:
            print('Failed to write file:', path, file=sys.stderr)

class MDocGroup(MNode):
    """
        Holds a collection of MDocs and ensures that any changes get written out to disk.
        Assumes that the MDocs are at random places in the file system, and that the key contains the full path.
        MDir makes the stronger assumption that all files share the same directory, so the key only contains the file name.
    """

    def __init__(self, key=None):
        self.key        = key
        self.children   = {}     # Holds weak references
        self.writeQueue = set()  # MDocs waiting to be written to disk. These are strong references, so objects will not be garbage collected before they are written.

    def key(self):
        return self.key  # Don't bother guarding against None. If the caller puts us under a higher-level node, it is also responsible to construct us with a proper key.

    def getOrDefault(self, defaultValue):
        return defaultValue

    @staticmethod
    def validFilenameFrom(name):
        forbiddenChars = '\\/:*"<>|'
        for c in forbiddenChars: name.replace(c, '-')

        # For Windows, certain file names are forbidden due to its archaic roots in DOS.
        upperName = name.upper()
        if upperName in ['CON', 'PRN', 'AUX', 'NUL']  or  re.match('(LPT|COM)\\d', upperName): name += '_'

        return name

    def pathForDoc(self, key):
        """
            Generates a path for the MDoc, based only on the key.
            This requires making restrictive assumptions about the mapping between key and path.
            This base class assumes the key is literally the path, as a string.
            MDir assumes that the key is a file or subdir name within a given directory.
        """
        return Path(key)

    def pathForFile(self, key):
        """
            Similar to pathForDoc(), but gives path to the file for the purposes of moving or deleting.
            This is different from pathForDoc() in the specific case of an MDir that has non-null suffix.
            In that case, the file is actually a subdirectory that contains both the MDoc and possibly
            other files.
        """
        return self.pathForDoc(key)

    def getChild(self, key):
        if not key: return None  # In Java, the file-existence test below can be fooled by an empty string. TODO: verify if this also happens in Python.
        if not key in self.children: return None  # Avoid creating a new entry if it didn't already exist.
        result = None
        reference = self.children.get(key)  # Could be null if we did a lazy load.
        if reference: result = reference()
        if result == None:  # MDoc has been garbage collected, or it may have never been loaded.
            path = self.pathForDoc(key)
            if not path.exists(): return None
            result = MDoc(path=path, key=key, parent=this)  # Assumes key==path.
            self.children[key] = weakref.ref(result)
        return result

    def clear(self):
        """
            Empty this group of all files.
            File themselves will not be deleted.
            However, subclass MDir does delete the entire directory from disk.
        """
        self.children  .clear()
        self.writeQueue.clear()

    def clearChild(self, key):
        ref = self.children.get(key)
        try:
            del self.children[key]
        except KeyError:
            pass
        if ref != None: self.writeQueue.discard(ref())
        deleteTree(pathForFile(key))

    @staticmethod
    def deleteTree(path):
        try:
            if path.is_dir():
                for f in path.iterdir(): deleteTree(f)
                path.rmdir()
            else: 
                path.unlink(missing_ok=True)
        except IOError:
            pass

    def size(self):
        return len(children)

    def set(self, value, key):
        """
            Creates a new MDoc that refers to the path given in key.
            @param key Mapped to location on disk according to pathForDoc().
            @param value ignored in all cases.
        """
        result = self.getChild(key)
        if result == None:  # new document, or at least new to us
            path = self.pathForDoc(key)
            result = MDoc(path=path, key=key, parent=self);  # Assumes key==path. This is overridden in MDir.
            self.children[key] = weakref.ref(result)
            if not path.exists(): result.markChanged();  # Set the new document to save. Adds to writeQueue.
        return result;

    def move(self, fromKey, toKey):
        """
            Renames an MDoc on disk.
            If you already hold a reference to the MDoc named by fromKey, then that reference remains valid
            after the move.
        """
        if toKey == fromKey: return
        self.save()  # If this turns out to be too much work, then scan the write queue for fromKey and save it directly.

        # This operation is independent of bookkeeping in "children" collection.
        fromPath = self.pathForFile(fromKey)
        toPath   = self.pathForFile(toKey)
        try:
            if toPath.exists(): deleteTree(toPath)
            os.replace(fromPath, toPath)
        except IOError:
            pass  # This can happen if a new doc has not yet been flushed to disk.

        try:
            del self.children[toKey]
        except:
            pass
        if fromKey in self.children:
            ref = self.children.get(fromKey)
            del self.children[fromKey]
            f = None
            if ref != None: f = ref()
            if f != None: f.key = toKey
            self.children[toKey] = ref

    def __iter__(self):
        return IteratorWrapper(list(self.children))

    def save(self):
        for doc in self.writeQueue: doc.save()
        self.writeQueue.clear()  # This releases the strong references, so these docs can be garbage collected if needed.

class MDir(MDocGroup):
    """
        A top-level node which maps to a directory on the file system.
        Each child node maps to a file under this directory. However, the document file need not
        be a direct child of this directory. Instead, some additional pathing may be added.
        This allows the direct children of this directory to be subdirectories, and each document
        file may be a specifically-named entry in a subdirectory.
    """

    def __init__(self, root, key=None, suffix=None):
        super().__init__(key=key)
        if suffix != None  and  len(suffix) == 0: suffix = None
        self.root   = root
        self.suffix = suffix
        self.loaded = false
        try:
            os.makedirs(root, exist_ok=True)  # We take the liberty of forcing the dir to exist.
        except IOError:
            pass

    def key(self):
        if name == None: return str(root)
        return name

    def getOrDefault(self, defaultValue):
        if root == None: return defaultValue  # This should never happen.
        return str(root.absolute())

    def pathForDoc(self, key):
        result = root / key
        if suffix != None: result = result / suffix
        return result

    def pathForFile(self, key):
        return root / key

    def getChild(self, key):
        self.load()
        if not key: return None  # In Java, the file-existence code below can be fooled by an empty string. TODO: check if this is also a problem in Python.
        if not key in self.children: return None
        result = None
        reference = self.children.get(key)
        if reference != None: result = reference()
        if result == None:  # We have never loaded this document, or it has been garbage collected.
            childPath = self.pathForDoc(key)
            if not childPath.exists():
                if suffix == None: return None
                # We allow the possibility that the dir exists but lacks its special file.
                parentPath = childPath.parent
                if not parentPath.exists(): return None
            result = MDoc(parent=self, key=key)
            children[key] = weakref.ref(result)
        return result

    def clear(self):
        """
            Empty this directory of all files.
            This is an extremely dangerous function! It destroys all data in the directory on disk and all data pending in memory.
        """
        self.children  .clear()
        self.writeQueue.clear()
        deleteTree(root.absolute())  # It's OK to delete this directory, since it will be re-created by the next MDoc that gets saved.

    def size(self):
        self.load()
        return len(self.children)

    def data(self):
        return root != None  # Should always be true.

    def set(self, value):
        """
            Point to a new location on disk.
            Must be called before actually moving the dir, since we need to flush the write queue.
        """
        self.save()
        self.root = value

    def set(self, value, key):
        """
            Creates a new MDoc in this directory if it does not already exist.
            MDocs that are children of an MDir ignore the value parameter, so it doesn't matter what is passed in that case.
        """
        result = self.getChild(key)
        if result == None:  # new document
            result = MDoc(parent=self, key=key)
            self.children[key] = weakref.ref(result)
            result.markChanged()  # Set the new document to save. Adds to writeQueue.
        return result

    def __iter__(self):
        self.load()
        return super().__iter__()

    def nodeChanged(self, key):
        """
            Notifies us that changes were made directly to a document on disk, for example by git.
        """
        # Check if it exists on disk. If not, then this is a delete.
        if not key: return
        childPath = self.pathForDoc(key)
        if not childPath.exists():
            try:
                self.children[key]
            except KeyError:
                pass
            return

        # Synchronize with updated/restored doc on disk.
        reference = self.children.get(key)
        if reference == None:  # added back into db, or not currently loaded
            child = MDoc(parent=this, key=key)
            self.children[key] = weakref.ref(child)
        else:  # reverted to previous state
            child = reference()
            if child != None:
                # Put doc into same state as newly-read directory entry.
                # All old children are invalid.
                child.needsWrite = False
                child.children = None

    def reload(self):
        """
            Notifies us that entire directory may have changed on disk.
            The caller should follow this sequence of operations:
            <ol>
            <li>MDir.save()
            <li>make changes to directory
            <li>MDir.reload()
            </ol>
        """
        self.loaded = False  # Force a fresh run of load(). children will be preserved as much as possible, to maintain object identity.
        self.load()
        for reference in self.children.items():
            if reference == None: continue
            child = reference()
            if child == None: continue
            child.needsWrite = False
            child.children = None

    def load(self):
        if self.loaded: return

        newChildren = {}
        # Scan directory.
        # This may cost a lot of time in some cases. However, N2A should never have more than about 10,000 models in a dir.
        try:
            for path in self.root.iterdir():
                key = path.name
                if key[0] == '.': continue  # Filter out special files. This allows, for example, a git repo to share the models dir.
                if self.suffix != None  and  not path.is_dir(): continue  # Only permit directories when suffix is defined.
                newChildren[key] = self.children.get(key)  # Some children could get orphaned, if they were deleted from disk by another process.
        except IOError:
            pass
        # Include newly-created docs that have never been flushed to disk.
        for doc in writeQueue:
            key = doc.key()
            newChildren[key] = self.children.get(key)
        self.children = newChildren

        self.loaded = True

class Schema:
    """
        Encapsulates the serialization method used for a particular file.
        This encompasses two related mechanisms:
        <ol>
        <li>The serialization method. Everything past the first line of the file is expressed in this format.
        <li>The general interpretation of the data.
        </ol>
    
        Each subclass provides a particular implementation of the serialization/deserialization process,
        and also informs the data consumer of the expected interpretation. This base class defines the
        interface and provides some utility functions.
    """

    def __init__(self, version, type):
        self.version = version
        self.type    = type

    @staticmethod
    def latest():
        return Schema2(3, '')

    @staticmethod
    def readAll(node, file):
        """
            Convenience method which reads the header and loads all the objects as children of the given node.
        """
        result = read(file)
        result.read(node, file)
        return result

    @staticmethod
    def read(file):
        line = file.readline()
        if len(line) == 0: raise IOError('File is empty.')
        line = line.strip()
        if not line[0:10] == 'N2A.schema': raise IOError('Schema line not found.')
        if len(line) < 12: raise IOError('Malformed schema line.')
        delimiter = line[10]
        if delimiter != '=': raise IOError('Malformed schema line.')
        line = line[11:]
        pieces = line.split(',', 2)
        version = int(pieces[0])
        type = ''
        if len(pieces) > 1: type = pieces[1].strip()

        # Note: A single schema subclass could handle multiple versions.
        #if version == 1: return Schema1(version, type)
        return Schema2(version, type)

    def read(self, node, file):
        """
            Brings in data from a stream. See write(MNode,Writer,String) for format.
            This method only processes children. The direct value of the node
            must be set by the caller.
        """
        pass

    def writeAll(self, node, file):
        """
            Convenience method which writes the header and all the children of the given node.
            The node itself (that is, its key and value) are not written out. The node simply acts
            as a container for the nodes that get written.
        """
        self.write(file)
        for c in node: self.write(c, file, '')

    def write(self, file):
        file.write(f'N2A.schema={self.version}')
        if self.type: file.write(f',{self.type}')
        file.write('\n')

    def write(self, node, file):
        """
            Convenience function for calling write(MNode,Writer,String) with no initial indent.
        """
        self.write(node, file, '')

    def write(self, node, file, indent):
        pass

class LineReader:
    def __init__(self, file):
        self.file        = file
        self.line        = None
        self.whitespaces = 0
        self.getNextLine()

    def getNextLine(self):
        # Scan for non-empty line
        while True:
            line = file.readline()
            if not line:  # end of file
                whitespaces = -1
                return
            if line == '\n': continue
            break

        # Count leading whitespace
        if line[-1] == '\n': line = line[:-1]  # remove trailing \n
        length = len(line)
        whitespaces = 0
        while whitespaces < length  and  line[whitespaces] == ' ': whitespaces += 1

class Schema2(Schema):
    def __init__(self, version, type):
        super().__init__(version, type)

    def read(self, node, file):
        node.clear()
        try:
            lineReader = LineReader(reader)
            self.read(node, lineReader, 0)
        except IOError:
            pass

    def read(self, node, reader, whitespaces):
        while reader.line != None:  # stop at end of file
            # At this point, reader.whitespaces == whitespaces
            # LineReader guarantees that line contains at least one character.

            # Parse the line into key=value.
            line = reader.line.strip()
            key = ''
            value = None
            escape = line[0] == '"'
            i = 1 if escape else 0
            last = len(line) - 1
            while i <= last:
                c = line[i]
                if escape:
                    if c == '"':
                        # Look ahead for second quote
                        i += 1
                        if i > last: break
                        if line[i] != '"':
                            escape = False
                            continue
                        # fall through to add quote character to prefix below
                else:
                    if c == ':':
                        value = line[i+1:].strip()
                        break
                key += c
                i += 1
            key = key.strip()   # Impossible to put leading or trailing spaces in a key, even when quoted.

            if value  and  value[0] == '|':  # go into string reading mode
                value = ''
                reader.getNextLine()
                if reader.whitespaces > whitespaces:
                    blockIndent = reader.whitespaces
                    while True:
                        value += reader.line[blockIndent:]
                        reader.getNextLine()
                        if reader.whitespaces < blockIndent: break
                        value += '\n'
            else:
                reader.getNextLine()
            child = node.set(value, key)  # Create a child with the given value
            if reader.whitespaces > whitespaces: self.read(child, reader, reader.whitespaces)  # Recursively populate child. When this call returns, reader.whitespaces <= whitespaces in this function, because that is what ends the recursion.
            if reader.whitespaces < whitespaces: return  # end recursion

    def write(self, node, file, indent):
        key = node.key()
        if len(key) == 0  or  key[0] == '"'  or  ':' in key:  # Could also trap if key starts with white space, but such cases are usually normalized by the UI.
            key = '"' + key.replace('"', '""') + '"'  # Using quote as its own escape, we avoid the need to escape a second code (such as both quote and backslash). This follows the example of YAML.

        if not node.data():
            file.write(f"{indent}{key}\n")  # No colon. That's how we distinguish undefined node from node defined as empty string.
        else:
            value = node.get()
            if '\n' in value  or  value.startswith('|'):  # go into extended text write mode
                value = value.replace('\n', f'\n{indent} ')  # Notice the addition of one extra white space. This offsets the extended block.
                value = f'|\n{indent} {value}'
            file.write(f'{indent}{key}:{value}\n')

        space2 = indent + ' '
        for c in node: self.write(c, file, space2)  # if this node has no children, nothing at all is written
