/*
This collection of utility classes reads and writes N2A model files, which
are stored in a lightweight document database.

This source file can be used as a header-only implementation, or you can compile
it separately and add to a library. In first case, just include this source file
as if it were a header. In the second case, you can use MNode.h as an ordinary
header to expose symbols.

Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

#ifndef n2a_mnode_cc
#define n2a_mnode_cc

#include "MNode.h"


// class MNode ---------------------------------------------------------------

n2a::MNode n2a::MNode::none;

n2a::MNode::~MNode ()
{
}

uint32_t
n2a::MNode::classID () const
{
    return 0;
}

std::string
n2a::MNode::key () const
{
    return "";
}

std::vector<std::string>
n2a::MNode::keyPath () const
{
    return keyPath (none);
}

std::vector<std::string>
n2a::MNode::keyPath (const MNode & root) const
{
    int index = depth (root);
    std::vector<std::string> result (index);  // creates a vector with blank strings
    const MNode * parent = this;
    while (index > 0)
    {
        result[--index] = parent->key ();  // copies the key for each element in vector
        parent = & parent->parent ();
    }
    return result;
}

std::string
n2a::MNode::keyPathString () const
{
    return keyPathString (none);
}

std::string
n2a::MNode::keyPathString (const MNode & root) const
{
    std::vector<std::string> keyPath = this->keyPath (root);
    if (keyPath.size () == 0) return "";
    std::string result = "";
    for (auto s : keyPath) result += s + ".";
    return result.substr (0, result.size () - 1);  // Get rid of final dot.
}

int
n2a::MNode::depth () const
{
    return depth (none);
}

int
n2a::MNode::depth (const MNode & root) const
{
    if (this == &root) return 0;  // address comparison, not content comparison
    MNode & parent = this->parent ();
    if (&parent == &none) return 0;
    return parent.depth (root) + 1;
}

n2a::MNode &
n2a::MNode::parent () const
{
    return none;
}

n2a::MNode &
n2a::MNode::root ()
{
    MNode * result = this;
    while (true)
    {
        MNode & parent = result->parent ();
        if (&parent == &none) break;
        result = &parent;
    }
    return *result;
}

n2a::MNode &
n2a::MNode::lca (MNode & that)
{
    // Strategy: Place the ancestry of one node in a set. Then walk up the ancestry
    // of the other node. The first ancestor found in the set is the LCA.

    std::unordered_set<MNode *> thisAncestors;
    MNode * A = this;
    while (A != &none)
    {
        thisAncestors.insert (A);
        A = & A->parent ();
    }

    auto end = thisAncestors.end ();
    MNode * B = &that;
    while (B != &none)
    {
        if (thisAncestors.find (B) != end) return *B;  // C++20 has contains(), but no need to create dependency on newer standard
        B = & B->parent ();
    }
    return none;
}

n2a::MNode &
n2a::MNode::child (const std::vector<std::string> & keys)
{
    MNode * result = this;
    int count = keys.size ();
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (int i = 0; i < count; i++)
    {
        MNode * c = & result->childGet (keys[i]);
        if (c == &none) return none;
        result = c;
    }
    return *result;  // If no keys are specified, we return this node.
}

n2a::MNode &
n2a::MNode::childOrCreate (const std::vector<std::string> & keys)
{
    MNode * result = this;
    int count = keys.size ();
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (int i = 0; i < count; i++)
    {
        result = & result->childGetOrCreate (keys[i]);
    }
    return *result;
}

n2a::MNode &
n2a::MNode::childAt (int index)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (auto & c : *this) if (index-- == 0) return c;
    return none;
}

void
n2a::MNode::clear ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (auto & n : *this) childClear (n.key ());
}

void
n2a::MNode::clear (const std::vector<std::string> & keys)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (keys.empty ())
    {
        clear ();
        return;
    }

    MNode * c = this;
    int last = keys.size () - 1;
    for (int i = 0; i < last; i++)
    {
        c = & c->childGet (keys[i]);
        if (c == &none) return;  // Nothing to clear
    }
    c->childClear (keys[last]);
}

int
n2a::MNode::size ()
{
    return 0;
}

bool
n2a::MNode::empty ()
{
    return size () == 0;
}

bool
n2a::MNode::data ()
{
    return false;
}

bool
n2a::MNode::data (const std::vector<std::string> & keys)
{
    MNode & c = child (keys);
    if (&c == &none) return false;
    return c.data ();
}

bool
n2a::MNode::containsKey (const std::string & key)
{
    if (& childGet (key) != &none) return true;
    for (auto & c : *this) if (c.containsKey (key)) return true;
    return false;
}

std::string
n2a::MNode::get ()
{
    return getOrDefault (std::string (""));
}

std::string
n2a::MNode::get (const std::vector<std::string> & keys)
{
    MNode & c = child (keys);
    if (&c == &none) return "";
    return c.get ();
}

std::string
n2a::MNode::getOrDefault (const std::string & defaultValue)
{
    return defaultValue;
}

std::string
n2a::MNode::getOrDefault (const char * defaultValue, const std::vector<std::string> & keys)
{
    std::string value = get (keys);
    if (value.empty ()) return defaultValue;
    return value;
}

bool
n2a::MNode::getOrDefault (bool defaultValue, const std::vector<std::string> & keys)
{
    std::string value = get (keys);
    if (value.empty ()) return defaultValue;
    value = trim (value);
    if (value == "1") return true;
    if (strcasecmp (value.c_str (), "true") == 0) return true;
    return false;
}

int
n2a::MNode::getOrDefault (int defaultValue, const std::vector<std::string> & keys)
{
    std::string value = get (keys);
    if (value.empty ()) return defaultValue;
    try
    {
        return std::stoi (value);
    }
    catch (...) {}

    // A number formatted as a float (containing a decimal point) will fail to parse as an integer.
    // Attempt to parse as float and round. If that fails, then it is truly hopeless.
    try
    {
        return (int) std::round (std::stod (value));
    }
    catch (...) {}
    {
        return defaultValue;
    }
}

long
n2a::MNode::getOrDefault (long defaultValue, const std::vector<std::string> & keys)
{
    std::string value = get (keys);
    if (value.empty ()) return defaultValue;
    try
    {
        return std::stol (value);
    }
    catch (...) {}

    // A number formatted as a float (containing a decimal point) will fail to parse as an integer.
    // Attempt to parse as float and round. If that fails, then it is truly hopeless.
    try
    {
        return (long) std::round (std::stod (value));
    }
    catch (...) {}
    {
        return defaultValue;
    }
}

double
n2a::MNode::getOrDefault (double defaultValue, const std::vector<std::string> & keys)
{
    std::string value = get (keys);
    if (value.empty ()) return defaultValue;
    try
    {
        return std::stod (value);
    }
    catch (...)
    {
        return defaultValue;
    }
}

bool
n2a::MNode::getFlag (const std::vector<std::string> & keys)
{
    MNode & c = child (keys);
    if (&c == &none  ||  c.get () == "0") return false;
    return true;
}

void
n2a::MNode::set (const char * value)
{
}

void
n2a::MNode::set (MNode & value)
{
    clear ();   // get rid of all children
    set (nullptr); // ensure that if value node is undefined, result node will also be undefined
    merge (value);
}

n2a::MNode &
n2a::MNode::set (const char * value, const std::vector<std::string> & keys)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MNode & result = childOrCreate (keys);
    result.set (value);
    return result;
}

n2a::MNode &
n2a::MNode::set (const std::string & value, const std::vector<std::string> & keys)
{
    return set (value.c_str (), keys);
}


n2a::MNode &
n2a::MNode::set (bool value, const std::vector<std::string> & keys)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MNode & result = childOrCreate (keys);
    result.set (value);
    return result;
}

n2a::MNode &
n2a::MNode::set (int value, const std::vector<std::string> & keys)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MNode & result = childOrCreate (keys);
    result.set (value);
    return result;
}

n2a::MNode &
n2a::MNode::set (long value, const std::vector<std::string> & keys)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MNode & result = childOrCreate (keys);
    result.set (value);
    return result;
}

n2a::MNode &
n2a::MNode::set (double value, const std::vector<std::string> & keys)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MNode & result = childOrCreate (keys);
    result.set (value);
    return result;
}

n2a::MNode &
n2a::MNode::set (MNode & value, const std::vector<std::string> & keys)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MNode & result = childOrCreate (keys);
    result.set (value);
    return result;
}

void
n2a::MNode::merge (MNode & that)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (that.data ()) set (that.get ());
    for (auto & thatChild : that)
    {
        childGetOrCreate (thatChild.key ()).merge (thatChild);
    }
}

void
n2a::MNode::mergeUnder (MNode & that)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! data ()  &&  that.data ()) set (that.get ());
    for (auto & thatChild : that)
    {
        std::string key = thatChild.key ();
        MNode & c = childGet (key);
        if (&c == &none) set (thatChild, {key});
        else             c.mergeUnder (thatChild);
    }
}

void
n2a::MNode::uniqueNodes (MNode & that)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (that.data ()) set (nullptr);
    for (auto & c : *this)
    {
        std::string key = c.key ();
        MNode & d = that.childGet (key);
        if (&d == &none) continue;
        c.uniqueNodes (d);
        if (c.size () == 0  &&  ! c.data ()) childClear (key);
    }
}

void
n2a::MNode::uniqueValues (MNode & that)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (data ()  &&  that.data ()  &&  get () == that.get ()) set (nullptr);
    for (auto & c : *this)
    {
        std::string key = c.key ();
        MNode & d = that.childGet (key);
        if (&d == &none) continue;
        c.uniqueValues (d);
        if (c.size () == 0  &&  ! c.data ()) childClear (key);
    }
}

void
n2a::MNode::changes (MNode & that)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (data ())
    {
        if (that.data ())
        {
            std::string value = that.get ();
            if (get () == value) set (nullptr);
            else                 set (value);
        }
        else
        {
            set (nullptr);
        }
    }
    for (auto & c : *this)
    {
        std::string key = c.key ();
        MNode & d = that.childGet (key);
        if (&d == &none) childClear (key);
        else             c.changes (d);
    }
}

void
n2a::MNode::move (const std::string & fromKey, const std::string & toKey)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (toKey == fromKey) return;
    childClear (toKey);
    MNode & source = childGet (fromKey);
    if (&source == &none) return;
    childGetOrCreate (toKey).merge (source);
    childClear (fromKey);
}

n2a::MNode::Iterator
n2a::MNode::begin ()
{
    // Same value as end(), so the iterator is already done before it gets started.
    // Obviously, this needs to be overridden in derived classes.
    std::vector<std::string> empty;
    return Iterator (*this, empty);
}

n2a::MNode::Iterator
n2a::MNode::end ()
{
    return Iterator (*this, 0);
}

void
n2a::MNode::visit (Visitor & v)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! v.visit (*this)) return;
    for (auto & c : *this) c.visit (v);
}

int
n2a::MNode::compare (const char * A, const char * B)
{
    int result = strcmp (A, B);
    if (result == 0) return 0;  // If strings follow M collation rules, then compare for equals works for numbers.

    // It might be possible to write a more efficient M collation routine by sifting through
    // each string, character by character. It would do the usual string comparison while
    // simultaneously converting each string into a number. As long as a string still
    // appears to be a number, conversion continues.
    // This current version is easier to write and understand, but less efficient.
    const char * Aend = A + strlen (A);
    const char * Bend = B + strlen (B);
    char * end;
    double Avalue = strtod (A, &end);
    while (*end == ' ') end++;  // consume trailing spaces
    if (end == Aend)  // full conversion, so A is a number
    {
        double Bvalue = strtod (B, &end);
        while (*end == ' ') end++;
        if (end == Bend)  // B is a number
        {
            double result = Avalue - Bvalue;  // number to number
            if (result > 0) return 1;
            if (result < 0) return -1;
            return 0;
        }
        else  // B is a string
        {
            return -1;  // number < string
        }
    }
    else  // A is a string
    {
        strtod (B, &end);
        while (*end == ' ') end++;
        if (end == Bend)  // B is a number
        {
            return 1;  // string > number
        }
        else  // B is a string
        {
            return result; // string to string
        }
    }
}

bool
n2a::MNode::operator== (MNode & that)
{
    if (this == &that) return true;  // Short-circuit if exactly the same object
    if (key () != that.key ()) return false;
    return equalsRecursive (that);
}

bool
n2a::MNode::equalsRecursive (MNode & that)
{
    if (data () != that.data ()) return false;
    if (get ()  != that.get  ()) return false;
    if (size () != that.size ()) return false;
    for (auto & a : *this)
    {
        MNode & b = that.childGet (a.key ());
        if (&b == &none) return false;
        if (! a.equalsRecursive (b)) return false;
    }
    return true;
}

bool
n2a::MNode::structureEquals (MNode & that)
{
    if (size () != that.size ()) return false;
    for (auto & a : *this)
    {
        MNode & b = that.childGet (a.key ());
        if (&b == &none) return false;
        if (! a.structureEquals (b)) return false;
    }
    return true;
}

std::string
n2a::MNode::toString ()
{
    std::stringstream writer;
    Schema * schema = Schema::latest ();
    schema->write (*this, writer);
    delete schema;
    return writer.str ();
}

n2a::MNode &
n2a::MNode::childGet (const std::string & key)
{
    return none;
}

n2a::MNode &
n2a::MNode::childGetOrCreate (const std::string & key)
{
    throw "Attempt to create child on abstract MNode. Use MVolatile or another concrete class.";
}

void
n2a::MNode::childClear (const std::string & key)
{
}


// class MVolatile -----------------------------------------------------------

n2a::MVolatile::MVolatile (const char * value, const char * key, MNode * container)
:   container (container)
{
    if (value) this->value = strdup (value);
    else       this->value = nullptr;
    if (key) name = key;  // Otherwise, name is blank
}

n2a::MVolatile::~MVolatile ()
{
    if (value) free (value);
    for (auto c : children) delete c.second;
}

uint32_t
n2a::MVolatile::classID () const
{
    return MVolatileID;
}

std::string
n2a::MVolatile::key () const
{
    return name;
}

n2a::MNode &
n2a::MVolatile::parent () const
{
    if (container) return *container;
    return none;
}

void
n2a::MVolatile::clear ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (auto c : children) delete c.second;
    children.clear ();
}

int
n2a::MVolatile::size ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    return children.size ();
}

bool
n2a::MVolatile::data ()
{
    return value;
}

std::string
n2a::MVolatile::getOrDefault (const std::string & defaultValue)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (value) return value;
    return defaultValue;
}

void
n2a::MVolatile::set (const char * value)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (this->value) free (this->value);
    if (value) this->value = strdup (value);
    else       this->value = nullptr;
}

void
n2a::MVolatile::move (const std::string & fromKey, const std::string & toKey)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (toKey == fromKey) return;

    std::map<const char *, MNode *>::iterator end = children.end ();
    std::map<const char *, MNode *>::iterator it = children.find (toKey.c_str ());
    if (it != end)
    {
        delete it->second;
        children.erase (it);
    }

    it = children.find (fromKey.c_str ());
    if (it != end)
    {
        MVolatile * keep = (MVolatile *) it->second;  // It's not currently necessary to check classID here.
        children.erase (it);
        keep->name = toKey;
        children[keep->name.c_str ()] = keep;
    }
}

n2a::MNode::Iterator
n2a::MVolatile::begin ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    std::vector<std::string> keys;
    keys.reserve (children.size ());
    for (auto c : children) keys.push_back (c.first);  // In order be safe for delete, these must be full copies of the strings.
    return Iterator (*this, keys);
}

n2a::MNode::Iterator
n2a::MVolatile::end ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    return Iterator (*this, children.size ());
}

n2a::MNode &
n2a::MVolatile::childGet (const std::string & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    try
    {
        return * children.at (key.c_str ());
    }
    catch (...)  // out_of_range
    {
        return none;
    }
}

n2a::MNode &
n2a::MVolatile::childGetOrCreate (const std::string & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    try
    {
        return * children.at (key.c_str ());  // Throws out_of_range if key doesn't exist
    }
    catch (...)  // key was not found
    {
        MVolatile * result = new MVolatile (nullptr, key.c_str (), this);
        children[result->name.c_str ()] = result;
        return *result;
    }
}

void
n2a::MVolatile::childClear (const std::string & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    std::map<const char *, MNode *>::iterator it = children.find (key.c_str ());
    if (it == children.end ()) return;
    delete it->second;
    children.erase (it);
}


// class MPersistent ---------------------------------------------------------

n2a::MPersistent::MPersistent (n2a::MNode * container, const char * value, const char * key)
:   MVolatile (value, key, container)
{
    needsWrite = false;
}

uint32_t
n2a::MPersistent::classID () const
{
    return MVolatileID | MPersistentID;
}

void
n2a::MPersistent::markChanged ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (needsWrite) return;  // nothing to do
    if (container  &&  container->classID () & MPersistentID) ((MPersistent *) container)->markChanged ();
    needsWrite = true;
}

void
n2a::MPersistent::clearChanged ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    needsWrite = false;
    for (auto & i : *this) ((MPersistent &) i).clearChanged ();
}

void
n2a::MPersistent::clear ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MVolatile::clear ();
    markChanged ();
}

void
n2a::MPersistent::set (const char * value)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (value)
    {
        if (! this->value  ||  strcmp (this->value, value) != 0)
        {
            MVolatile::set (value);
            markChanged ();
        }
    }
    else
    {
        if (this->value)
        {
            MVolatile::set (value);
            markChanged ();
        }
    }
}

void
n2a::MPersistent::move (const std::string & fromKey, const std::string & toKey)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (toKey == fromKey) return;

    std::map<const char *, MNode *>::iterator end = children.end ();
    std::map<const char *, MNode *>::iterator it = children.find (toKey.c_str ());
    if (it != end)
    {
        delete it->second;
        children.erase (it);
        markChanged ();
    }

    it = children.find (fromKey.c_str ());
    if (it != end)
    {
        MPersistent * keep = (MPersistent *) it->second;  // just assume all children are MPersistent
        children.erase (it);
        keep->name = toKey;
        children[keep->name.c_str ()] = keep;
        keep->markChanged ();
        markChanged ();
    }
}

n2a::MNode &
n2a::MPersistent::childGetOrCreate (const std::string & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    try
    {
        return * children.at (key.c_str ());  // Throws out_of_range if key doesn't exist
    }
    catch (...)  // key was not found
    {
        markChanged ();
        MPersistent * result = new MPersistent (this, nullptr, key.c_str ());
        children[result->name.c_str ()] = result;
        return *result;
    }
}

void
n2a::MPersistent::childClear (const std::string & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MVolatile::childClear (key);
    markChanged ();
}


// class MDoc ----------------------------------------------------------------

n2a::MDoc::MDoc (const std::filesystem::path & path)
:   MPersistent (nullptr, path.c_str (), nullptr)
{
    needsRead = true;
}

n2a::MDoc::MDoc (const std::filesystem::path & path, const std::string & key)
:   MPersistent (nullptr, path.c_str (), key.c_str ())
{
    needsRead = true;
}

n2a::MDoc::MDoc (n2a::MDocGroup & container, const std::string & path, const std::string & key)
:   MPersistent (&container, path.c_str (), key.c_str ())
{
    needsRead = true;
}

n2a::MDoc::MDoc (n2a::MDir & container, const std::string & key)
:   MPersistent (&container, nullptr, key.c_str ())
{
    needsRead = true;
}

uint32_t
n2a::MDoc::classID () const
{
    return MVolatileID | MPersistentID | MDocID;
}

void
n2a::MDoc::markChanged ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (needsWrite) return;

    // If this is a new document, then treat it as if it were already loaded.
    // If there is content on disk, it will be blown away.
    needsRead  = false;
    needsWrite = true;
    if (container  &&  container->classID () & MDocGroupID)
    {
        std::lock_guard<std::recursive_mutex> lock (container->mutex);
        ((MDocGroup *) container)->writeQueue.insert (this);
    }
}

int
n2a::MDoc::size ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (needsRead) load ();  // redundant with the guard in load(), but should save time in the common case that file is already loaded
    return MPersistent::size ();
}

bool
n2a::MDoc::data ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (needsRead) load ();
    return MPersistent::data ();
}

std::string
n2a::MDoc::getOrDefault (const std::string & defaultValue)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (container  &&  container->classID () & MDocGroupID) return ((MDocGroup *) container)->pathForDoc (name);
    if (value) return value;
    return defaultValue;
}

void
n2a::MDoc::set (const char * value)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (container) return;  // Not stand-alone, so ignore.
    if (strcmp (this->value, value) == 0) return;  // Don't call file move if location on disk has not changed.
    try
    {
        std::filesystem::rename (this->value, value);
    }
    catch (...)
    {
        std::cerr << "Failed to move file: " << this->value << " --> " << value << std::endl;
    }
}

void
n2a::MDoc::move (const std::string & fromKey, const std::string & toKey)
{
    if (fromKey == toKey) return;
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (needsRead) load ();
    MPersistent::move (fromKey, toKey);
}

n2a::MNode::Iterator
n2a::MDoc::begin ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (needsRead) load ();
    return MPersistent::begin ();
}

n2a::MNode::Iterator
n2a::MDoc::end ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (needsRead) load ();
    return MPersistent::end ();
}

std::filesystem::path
n2a::MDoc::path ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (container  &&  container->classID () & MDocGroupID) return ((MDocGroup *) container)->pathForDoc (name);
    return value;  // for stand-alone document
}

void
n2a::MDoc::load ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! needsRead) return;  // already loaded
    needsRead  = false; // prevent re-entrant call when creating nodes
    needsWrite = true;  // lie to ourselves, to prevent being put onto the MDir write queue
    std::filesystem::path file = path ();
    try
    {
        std::ifstream ifs (file.c_str ());
        Schema::readAll (*this, ifs);
        ifs.close ();
    }
    catch (...) {}  // An exception is common for a newly created doc that has not yet been flushed to disk.
    clearChanged ();  // After load(), clear the slate so we can detect any changes and save the document.
}

void
n2a::MDoc::save ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! needsWrite) return;
    std::filesystem::path file = path ();
    try
    {
        std::filesystem::create_directories (file.parent_path ());
        std::ofstream ofs (file.c_str (), std::ofstream::trunc);
        Schema * schema = Schema::latest ();
        schema->writeAll (*this, ofs);
        delete schema;
        ofs.close ();
        clearChanged ();
    }
    catch (...)
    {
        std::cerr << "Failed to write file: " << file << std::endl;
    }
}

void
n2a::MDoc::deleteFile () const
{
    std::filesystem::path path;
    if (container  &&  container->classID () & MDocGroupID)
    {
        path = ((MDocGroup *) container)->pathForFile (name);
    }
    else
    {
        path = value;
    }

    try
    {
        std::filesystem::remove (path);
    }
    catch (...)
    {
        std::cerr << "Failed to delete file: " << path << std::endl;
    }
}

n2a::MNode &
n2a::MDoc::childGet (const std::string & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (needsRead) load ();
    return MPersistent::childGet (key);

}

n2a::MNode &
n2a::MDoc::childGetOrCreate (const std::string & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (needsRead) load ();
    return MPersistent::childGetOrCreate (key);
}

void
n2a::MDoc::childClear (const std::string & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (needsRead) load ();
    MPersistent::childClear (key);
}


// class MDocGroup -----------------------------------------------------------

n2a::MDocGroup::MDocGroup (const char * key)
{
    if (key) name = key;
}

n2a::MDocGroup::~MDocGroup ()
{
    for (MDoc * doc: writeQueue) doc->save ();
    for (auto c : children) delete c.second;
}

std::string
n2a::MDocGroup::key () const
{
    return name;
}

std::string
n2a::MDocGroup::getOrDefault (const std::string & defaultValue)
{
    return defaultValue;
}

void
n2a::MDocGroup::clear ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (auto c : children) delete c.second;
    children.clear ();
    writeQueue.clear ();
}

int
n2a::MDocGroup::size ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    return children.size ();
}

void
n2a::MDocGroup::move (const std::string & fromKey, const std::string & toKey)
{
    if (toKey == fromKey) return;
    std::lock_guard<std::recursive_mutex> lock (mutex);
    save ();

    // Adjust files on disk.
    std::filesystem::path fromPath = pathForFile (fromKey);
    std::filesystem::path toPath   = pathForFile (toKey);
    try
    {
        std::filesystem::remove_all (toPath);
        std::filesystem::rename (fromPath, toPath);
    }
    catch (...) {}  // This can happen if a new doc has not yet been flushed to disk.

    // Bookkeeping in "children" collection
    std::map<std::string, MDoc *>::iterator end = children.end ();
    std::map<std::string, MDoc *>::iterator it  = children.find (toKey.c_str ());
    if (it != end)
    {
        delete it->second;
        children.erase (it);
    }

    it = children.find (fromKey.c_str ());
    if (it != end)
    {
        MDoc * keep = (MDoc *) it->second;
        children.erase (it);
        keep->name = toKey;
        children[toKey] = keep;
    }
}

n2a::MNode::Iterator
n2a::MDocGroup::begin ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    std::vector<std::string> keys;
    keys.reserve (children.size ());
    for (auto c : children) keys.push_back (c.first);  // In order be safe for delete, these must be full copies of the strings.
    return Iterator (*this, keys);
}

n2a::MNode::Iterator
n2a::MDocGroup::end ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    return Iterator (*this, children.size ());
}

std::filesystem::path
n2a::MDocGroup::pathForDoc (const std::string & key) const
{
    return std::filesystem::path (key);
}

void
n2a::MDocGroup::save ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (MDoc * doc: writeQueue) doc->save ();
    writeQueue.clear ();
}

n2a::MNode &
n2a::MDocGroup::childGet (const std::string & key)
{
    if (key.empty ()) throw "MDoc key must not be empty";
    std::lock_guard<std::recursive_mutex> lock (mutex);
    std::map<std::string, MDoc *>::iterator it = children.find (key);
    if (it == children.end ()) return none;

    MDoc * result = it->second;
    if (! result)  // Doc is not currently loaded
    {
        std::filesystem::path path = pathForDoc (key);
        if (! std::filesystem::is_regular_file (path)) return none;
        result = new MDoc (*this, key, key);  // Assumes key==path.
        children[key] = result;
    }
    return *result;
}

n2a::MNode &
n2a::MDocGroup::childGetOrCreate (const std::string & key)
{
    if (key.empty ()) throw "MDoc key must not be empty";
    std::lock_guard<std::recursive_mutex> lock (mutex);

    MDoc * result;
    std::map<std::string, MDoc *>::iterator it = children.find (key);
    if (it == children.end ()) result = nullptr;
    else                       result = it->second;

    if (! result)
    {
        result = new MDoc (*this, key, key);  // Assumes key==path. This is overridden in MDir.
        children[key] = result;
        if (! std::filesystem::exists (pathForDoc (key))) result->markChanged ();  // Set the new document to save. Adds to writeQueue.
    }
    return *result;
}

void
n2a::MDocGroup::childClear (const std::string & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    std::map<std::string, MDoc *>::iterator it = children.find (key);
    if (it == children.end ()) return;

    MDoc * doc = it->second;
    delete it->second;
    children.erase (it);
    writeQueue.erase (doc);
    std::filesystem::remove_all (pathForFile (key));
}

void
n2a::MDocGroup::unload (n2a::MDoc * doc)
{
    std::string key = doc->key ();
    std::map<std::string, MDoc *>::iterator it = children.find (key);
    if (it == children.end ()) return;
    if (doc->needsWrite) doc->save ();
    writeQueue.erase (doc);
    delete doc;
    children[key] = nullptr;
}


// class MDir ----------------------------------------------------------------

n2a::MDir::MDir (const std::filesystem::path & root, const char * suffix, const char * key)
:   MDocGroup (key),
    root (root)
{
    if (suffix) this->suffix = suffix;
    loaded = false;
}

std::string
n2a::MDir::key () const
{
    if (name.empty ()) return root;
    return name;
}

std::string
n2a::MDir::getOrDefault (const std::string & defaultValue)
{
    return root;
}

void
n2a::MDir::clear ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MDocGroup::clear ();
    std::filesystem::remove_all (root);  // It's OK to delete this directory, since it will be re-created by the next MDoc that gets saved.
}

int
n2a::MDir::size ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    load ();
    return children.size ();
}

bool
n2a::MDir::data ()
{
    return true;
}

n2a::MNode::Iterator
n2a::MDir::begin ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    load ();
    return MDocGroup::begin ();
}

n2a::MNode::Iterator
n2a::MDir::end ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    load ();
    return MDocGroup::end ();
}

std::filesystem::path
n2a::MDir::pathForDoc (const std::string & key) const
{
    if (suffix.empty ()) return root / key;
    return                      root / key / suffix;
}

std::filesystem::path
n2a::MDir::pathForFile (const std::string & key) const
{
    return root / key;
}

void
n2a::MDir::load ()
{
    if (loaded) return;  // as a boolean, presumably "loaded" is sufficiently atomic to avoid taking a lock just to check
    std::lock_guard<std::recursive_mutex> lock (mutex);

    // Scan directory.
    std::map<std::string, MDoc *, Order> newChildren;
    std::map<std::string, MDoc *>::iterator end = children.end ();
    for (auto const & entry : std::filesystem::directory_iterator (root))
    {
        const std::filesystem::path & path = entry.path ();
        const std::string & key = path.stem ();
        if (key[0] == '.') continue; // Filter out special files. This allows, for example, a git repo to share the models dir.
        if (! suffix.empty ()  &&  ! std::filesystem::is_directory (path)) continue;  // Only permit directories when suffix is defined.

        // Add child to collection.
        // Keep object identity of any active MDoc.
        // Some MDocs could get lost if they aren't currently listed in the directory.
        // This would only happen if another process is messing with the directory.
        std::map<std::string, MDoc *>::iterator it = children.find (key);
        if (it == end) newChildren[key] = nullptr;
        else           newChildren[key] = it->second;
    }

    // Include newly-created docs that have never been flushed to disk.
    for (MDoc * doc : writeQueue)
    {
        const std::string & key = doc->key ();
        newChildren[key] = children[key];
    }
    children = newChildren;

    loaded = true;
}

n2a::MNode &
n2a::MDir::childGet (const std::string & key)
{
    if (key.empty ()) throw "MDoc key must not be empty";
    std::lock_guard<std::recursive_mutex> lock (mutex);
    std::map<std::string, MDoc *>::iterator it = children.find (key);
    if (it == children.end ()) return none;

    MDoc * result = it->second;
    if (! result)  // Doc is not currently loaded
    {
        std::filesystem::path path = pathForDoc (key);
        if (! std::filesystem::exists (path))
        {
            if (suffix.empty ()) return none;
            // Allow the possibility that the dir exists but lacks its special file.
            if (! std::filesystem::exists (path.parent_path ())) return none;
        }
        result = new MDoc (*this, key, key);  // Assumes key==path.
        children[key] = result;
    }
    return *result;
}


// class Schema --------------------------------------------------------------

n2a::Schema::Schema (int version, const std::string & type)
:   version (version),
    type (type)
{
}

n2a::Schema *
n2a::Schema::latest ()
{
    return new Schema2 (2, "");
}

void
n2a::Schema::readAll (MNode & node, std::istream & reader, Schema ** schema)
{
    Schema * result = read (reader);
    result->read (node, reader);
    if (schema) *schema = result;
    else        delete result;
}

n2a::Schema *
n2a::Schema::read (std::istream & reader)
{
    std::string line;
    getline (reader, line);
    if (! reader.good ()) throw "File is empty.";
    line = trim (line);
    if (line.length () < 12) throw "Malformed schema line.";
    if (line.substr (0, 11) != "N2A.schema=") throw "Schema line missing or malformed.";
    line = line.substr (11);

    std::string::size_type pos = line.find_first_of (",");
    int version = std::stoi (line.substr (0, pos));
    std::string type = "";
    if (pos != std::string::npos) type = trim (line.substr (pos + 1));  // skip the comma

    // Note: A single schema subclass could handle multiple versions.
    //if (version == 1) return Schema1 (version, type);  // Schema1 is obsolete, and not worth implementing here.
    return new Schema2 (version, type);
}

void
n2a::Schema::writeAll (MNode & node, std::ostream & writer)
{
    write (writer);
    for (auto & c : node) write (c, writer, "");
}

void
n2a::Schema::write (std::ostream & writer)
{
    writer << "N2A.schema=" << version;
    if (! type.empty ()) writer << "," << type;
    writer << std::endl;
}

void
n2a::Schema::write (MNode & node, std::ostream & writer)
{
    write (node, writer, "");
}

n2a::LineReader::LineReader (std::istream & reader)
:   reader (reader)
{
    getNextLine ();
}


// class LineReader ----------------------------------------------------------

void
n2a::LineReader::getNextLine ()
{
    // Scan for non-empty line
    while (true)
    {
        std::getline (reader, line);  // default line ending is NL
        if (! reader.good ())
        {
            whitespaces = -1;
            return;
        }

        int count = line.length ();
        if (count == 0) continue;
        if (line[count-1] == '\r') line.resize (count - 1);  // Get rid of CR. Should work for every platform except older MacOS.
        if (! line.empty ()) break;
    }

    // Count leading whitespace
    int length = line.length ();
    whitespaces = 0;
    while (whitespaces < length  &&  line[whitespaces] == ' ') whitespaces++;
}


// class Schema2 -------------------------------------------------------------

n2a::Schema2::Schema2 (int version, const std::string & type)
:   Schema (version, type)
{
}

void
n2a::Schema2::read (MNode & node, std::istream & reader)
{
    node.clear ();
    LineReader lineReader (reader);
    read (node, lineReader, 0);
}

void
n2a::Schema2::read (MNode & node, LineReader & reader, int whitespaces)
{
    while (true)
    {
        if (reader.whitespaces == -1) return;  // stop at end of file
        // At this point, reader.whitespaces == whitespaces
        // LineReader guarantees that line contains at least one character.

        // Parse the line into key=value.
        std::string line = trim (reader.line);
        std::string key;
        std::string value;
        bool hasValue = false;
        bool escape =  line[0] == '"';
        int i = escape ? 1 : 0;
        int last = line.length () - 1;
        for (; i <= last; i++)
        {
            char c = line[i];
            if (escape)
            {
                if (c == '"')
                {
                    // Look ahead for second quote
                    if (i < last  &&  line[i+1] == '"')
                    {
                        i++;
                    }
                    else
                    {
                        escape = false;
                        continue;
                    }
                }
            }
            else
            {
                if (c == ':')
                {
                    value = trim (line.substr (i+1));
                    hasValue = true;
                    break;
                }
            }
            key += c;
        }
        key = trim (key);

        if (value.length () > 0  &&  value[0] == '|')  // go into string reading mode
        {
            value.clear ();
            reader.getNextLine ();
            if (reader.whitespaces > whitespaces)
            {
                int blockIndent = reader.whitespaces;
                while (true)
                {
                    value += reader.line.substr (blockIndent);
                    reader.getNextLine ();
                    if (reader.whitespaces < blockIndent) break;
                    value += '\n';  // Internally, we only use newline character in multi-line strings.
                }
            }
        }
        else
        {
            reader.getNextLine ();
        }
        MNode & child = node.set (hasValue ? value.c_str () : nullptr, {key});  // Create a child with the given value
        if (reader.whitespaces > whitespaces) read (child, reader, reader.whitespaces);  // Recursively populate child. When this call returns, reader.whitespaces <= whitespaces in this function, because that is what ends the recursion.
        if (reader.whitespaces < whitespaces) return;  // end recursion
    }
}

void
n2a::Schema2::write (MNode & node, std::ostream & writer, const std::string & indent)
{
    std::string key = node.key ();
    if (key[0] == '\"'  ||  key.find_first_of (':') != std::string::npos)  // key contains forbidden characters
    {
        // Quote the key. Any internal quote marks must be escaped.
        // We use quote as its own escape, avoiding the need to escape a second code (such as both quote and backslash).
        // This follows the example of YAML.
        std::string original = key;
        int count = original.length ();
        key.reserve (count + 4);  // add 2 for opening and closing quotes, plus another 2 for the expansion of at least one embedded quote mark
        key = "\"";
        for (int i = 0; i < count; i++)
        {
            char c = original[i];
            if (c == '\"') key += "\"\"";
            else           key += c;
        }
        key += '\"';
    }

    if (! node.data ())
    {
        writer << indent << key << std::endl;
    }
    else
    {
        writer << indent << key << ":";
        const std::string & value = node.get ();
        if (value.find_first_of ('\n') == std::string::npos)
        {
            writer << value << std::endl;
        }
        else  // go into extended text write mode
        {
            writer << "|" << std::endl;
            std::string::size_type count = value.length ();
            std::string::size_type current = 0;
            while (true)
            {
                std::string::size_type next = value.find_first_of ('\n', current);
                writer << indent << " ";
                if (next == std::string::npos)
                {
                    writer << value.substr (current) << std::endl;
                    break;
                }
                else
                {
                    writer << value.substr (current, next - current) << std::endl;
                    current = next + 1;
                }
            }
        }
    }

    std::string space2 = indent + " ";
    for (auto & c : node) write (c, writer, space2);  // if this node has no children, nothing at all is written
}


#endif
