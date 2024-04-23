/*
This collection of utility classes reads and writes N2A model files, which
are stored in a lightweight document database.

This source file can be used as a header-only implementation, or you can compile
it separately and add to a library. In first case, just include this source file
as if it were a header. In the second case, you can use MNode.h as an ordinary
header to expose symbols.

Copyright 2023-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

#ifndef n2a_mnode_cc
#define n2a_mnode_cc

#include "MNode.h"

#include <unordered_set>
#include <iostream>
#include <fstream>
#include <sys/stat.h>
#ifdef _MSC_VER
#  define WIN32_LEAN_AND_MEAN
#  include <windows.h>
#  include <direct.h>
#  undef min
#  undef max
#else
#  include <dirent.h>
#  include <unistd.h>
#endif


// Utility functions ---------------------------------------------------------

std::ostream &
n2a::operator<< (std::ostream & out, n2a::MNode & value)
{
    Schema * schema = Schema::latest ();
    schema->write (value, out);
    delete schema;
    return out;
}

char *
n2a::strdup (const char * from)
{
    int length = strlen (from);
    char * result = (char *) malloc (length + 1);
    // Don't bother checking for null result. In that case, we're already in trouble, so doesn't matter where the final explosion happens.
    strcpy (result, from);
    return result;
}

void
n2a::remove_all (const String & path)
{
    struct stat buffer;
    if (stat (path.c_str (), &buffer) == -1) return;  // Nothing to do
    if (buffer.st_mode & S_IFDIR)  // directory, so need to delete contents
    {
#   ifdef _MSC_VER

        // enumerate files
        WIN32_FIND_DATA fd;
        HANDLE hFind = FindFirstFile ((path + "/*").c_str (), &fd);
        if (hFind == INVALID_HANDLE_VALUE) return;
        do
        {
            String name = fd.cFileName;
            if (name == "."  ||  name == "..") continue;
            remove_all (path + "/" + name);
        }
        while (FindNextFile (hFind, &fd));
        FindClose (hFind);

#   else

        DIR * dir = opendir (path.c_str ());
        while (struct dirent * e = readdir (dir))
        {
            String name = e->d_name;
            if (name == "."  ||  name == "..") continue;
            remove_all (path + "/" + name);
        }
        closedir (dir);

#   endif

        rmdir (path.c_str ());
    }
    else
    {
        if (remove (path.c_str ())) throw "Failed to delete file";  // non-zero return from remove() indicates failure
    }
}

bool
n2a::exists (const String & path)
{
    struct stat buffer;
    return stat (path.c_str (), &buffer) == 0;
}

bool
n2a::is_directory (const String & path)
{
    struct stat buffer;
    if (stat (path.c_str (), &buffer) == -1) return false;
    return buffer.st_mode & S_IFDIR;
}

void
n2a::mkdirs (const String & file)
{
    String::size_type pos = 1;
    String::size_type count = file.size ();
    while (pos < count)
    {
        pos = file.find_first_of ('/', pos);
        if (pos == String::npos) break;  // This should omit that last path element. We only want to process directories.
        String parent = file.substr (0, pos);
#           if  defined(_MSC_VER)  // MSVC
        int result = _mkdir (parent.c_str ());
#           elif  defined(_WIN32)  ||  defined(__WIN32__)  // mingw64, particularly under MSYS2
        int result = mkdir (parent.c_str ());
#           else  // POSIX standard
        int result = mkdir (parent.c_str (), 0777);  // Do this blindly. If dir already exists, an error is returned but no harm is done. If dir can't be created, file write will fail below.
#           endif
        pos += 1;
    }
}


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

String
n2a::MNode::key () const
{
    return "";
}

std::vector<String>
n2a::MNode::keyPath () const
{
    return keyPath (none);
}

std::vector<String>
n2a::MNode::keyPath (const MNode & root) const
{
    int index = depth (root);
    std::vector<String> result (index);  // creates a vector with blank strings
    const MNode * parent = this;
    while (index > 0)
    {
        result[--index] = parent->key ();  // copies the key for each element in vector
        parent = & parent->parent ();
    }
    return result;
}

String
n2a::MNode::keyPathString () const
{
    return keyPathString (none);
}

String
n2a::MNode::keyPathString (const MNode & root) const
{
    std::vector<String> keyPath = this->keyPath (root);
    int count = keyPath.size ();
    if (count == 0) return "";
    String result = keyPath[0];
    for (int i = 1; i < count; i++) result = result + "." + keyPath[i];
    return result;
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
n2a::MNode::child (const std::vector<String> & keys)
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
n2a::MNode::childOrCreate (const std::vector<String> & keys)
{
    MNode * result = this;
    int count = keys.size ();
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (int i = 0; i < count; i++)
    {
        result = & result->childGet (keys[i], true);
    }
    return *result;
}

std::vector<String>
n2a::MNode::childKeys ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    std::vector<String> result;
    result.reserve (size ());
    for (auto & c : *this) result.push_back (c.key ());
    return result;
}

void
n2a::MNode::clear ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (auto & n : *this) childClear (n.key ());
}

void
n2a::MNode::clear (const std::vector<String> & keys)
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
n2a::MNode::data (const std::vector<String> & keys)
{
    MNode & c = child (keys);
    if (&c == &none) return false;
    return c.data ();
}

bool
n2a::MNode::containsKey (const String & key)
{
    if (& childGet (key) != &none) return true;
    for (auto & c : *this) if (c.containsKey (key)) return true;
    return false;
}

String
n2a::MNode::get ()
{
    return getOrDefault (String (""));
}

String
n2a::MNode::get (const std::vector<String> & keys)
{
    MNode & c = child (keys);
    if (&c == &none) return "";
    return c.get ();
}

String
n2a::MNode::getOrDefault (const String & defaultValue)
{
    return defaultValue;
}

String
n2a::MNode::getOrDefault (const char * defaultValue, const std::vector<String> & keys)
{
    String value = get (keys);
    if (value.empty ()) return defaultValue;
    return value;
}

bool
n2a::MNode::getOrDefault (bool defaultValue, const std::vector<String> & keys)
{
    String value = get (keys);
    if (value.empty ()) return defaultValue;
    value.trim ();
    if (value == "1") return true;
    if (strcasecmp (value.c_str (), "true") == 0) return true;
    return false;
}

int
n2a::MNode::getOrDefault (int defaultValue, const std::vector<String> & keys)
{
    String value = get (keys);
    if (value.empty ()) return defaultValue;
    return strtol (value.c_str (), nullptr, 10);  // Best-effort conversion. If there is a decimal point, we effectively truncate the float value.
}

long
n2a::MNode::getOrDefault (long defaultValue, const std::vector<String> & keys)
{
    String value = get (keys);
    if (value.empty ()) return defaultValue;
    return strtol (value.c_str (), nullptr, 10);
}

double
n2a::MNode::getOrDefault (double defaultValue, const std::vector<String> & keys)
{
    String value = get (keys);
    if (value.empty ()) return defaultValue;
    return strtod (value.c_str (), nullptr);
}

bool
n2a::MNode::getFlag (const std::vector<String> & keys)
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
n2a::MNode::set (const char * value, const std::vector<String> & keys)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MNode & result = childOrCreate (keys);
    result.set (value);
    return result;
}

n2a::MNode &
n2a::MNode::set (const String & value, const std::vector<String> & keys)
{
    return set (value.c_str (), keys);
}


n2a::MNode &
n2a::MNode::set (bool value, const std::vector<String> & keys)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MNode & result = childOrCreate (keys);
    result.set (value);
    return result;
}

n2a::MNode &
n2a::MNode::set (int value, const std::vector<String> & keys)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MNode & result = childOrCreate (keys);
    result.set (value);
    return result;
}

n2a::MNode &
n2a::MNode::set (long value, const std::vector<String> & keys)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MNode & result = childOrCreate (keys);
    result.set (value);
    return result;
}

n2a::MNode &
n2a::MNode::set (double value, const std::vector<String> & keys)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MNode & result = childOrCreate (keys);
    result.set (value);
    return result;
}

n2a::MNode &
n2a::MNode::set (MNode & value, const std::vector<String> & keys)
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
        childGet (thatChild.key (), true).merge (thatChild);
    }
}

void
n2a::MNode::mergeUnder (MNode & that)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! data ()  &&  that.data ()) set (that.get ());
    for (auto & thatChild : that)
    {
        String key = thatChild.key ();
        MNode & c = childGet (key);
        if (&c == &none) set (thatChild, {std::move (key)});
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
        String key = c.key ();
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
        String key = c.key ();
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
            String value = that.get ();
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
        String key = c.key ();
        MNode & d = that.childGet (key);
        if (&d == &none) childClear (key);
        else             c.changes (d);
    }
}

void
n2a::MNode::move (const String & fromKey, const String & toKey)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (toKey == fromKey) return;
    childClear (toKey);
    MNode & source = childGet (fromKey);
    if (&source == &none) return;
    childGet (toKey, true).merge (source);
    childClear (fromKey);
}

n2a::MNode::Iterator
n2a::MNode::begin ()
{
    // Same value as end(), so the iterator is already done before it gets started.
    // Obviously, this needs to be overridden in derived classes.
    return Iterator (*this);
}

n2a::MNode::Iterator
n2a::MNode::end ()
{
    return Iterator (*this);
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

const char *
n2a::MNode::keyPointer () const
{
    throw "No memory representation for key";
}

n2a::MNode &
n2a::MNode::childGet (const String & key, bool create)
{
    if (create) throw "Attempt to create child on abstract MNode. Use MVolatile or another concrete class.";
    return none;
}

void
n2a::MNode::childClear (const String & key)
{
}

void
n2a::MNode::addObserver (Observer * o)
{
    throw "Observable interface is not supported.";
}

void
n2a::MNode::removeObserver (Observer * o)
{
    throw "Observable interface is not supported.";
}


// class MNode::Observable ---------------------------------------------------

void
n2a::MNode::Observable::addObserver (Observer * o)
{
    observers.push_back (o);
}

void
n2a::MNode::Observable::removeObserver (Observer * o)
{
    for (int i = observers.size () - 1; i >= 0; i--)
    {
        if (observers[i] != o) continue;
        observers.erase (observers.begin () + i);
        break;
    }
}

void
n2a::MNode::Observable::fireChanged ()
{
    for (auto it : observers) it->changed ();
}

void
n2a::MNode::Observable::fireChildAdded (const String & key)
{
    for (auto it : observers) it->childAdded (key);
}

void
n2a::MNode::Observable::fireChildDeleted (const String & key)
{
    for (auto it : observers) it->childDeleted (key);
}

void
n2a::MNode::Observable::fireChildChanged (const String & oldKey, const String & newKey)
{
    for (auto it : observers) it->childChanged (oldKey, newKey);
}


// class MVolatile -----------------------------------------------------------

n2a::MVolatile::MVolatile (const char * value, const char * key, MNode * container)
:   container (container),
    name      (key),  // If key is null, name is empty string.
    children  (nullptr)
{
    if (value) this->value = strdup (value);
    else       this->value = nullptr;
}

n2a::MVolatile::~MVolatile ()
{
    if (value) free (value);
    if (! children) return;
    for (auto c : *children) delete c.second;
    delete children;
}

uint32_t
n2a::MVolatile::classID () const
{
    return MVolatileID;
}

String
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
    if (! children) return;
    for (auto c : *children) delete c.second;
    children->clear ();
}

int
n2a::MVolatile::size ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! children) return 0;
    return children->size ();
}

bool
n2a::MVolatile::data ()
{
    return value;
}

String
n2a::MVolatile::getOrDefault (const String & defaultValue)
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
n2a::MVolatile::move (const String & fromKey, const String & toKey)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (toKey == fromKey) return;
    if (! children) return;  // nothing to move

    auto end = children->end ();
    auto it  = children->find (toKey.c_str ());
    if (it != end)
    {
        delete it->second;
        children->erase (it);
    }

    it = children->find (fromKey.c_str ());
    if (it != end)
    {
        MVolatile * keep = static_cast<MVolatile *> (it->second);  // It's not currently necessary to check classID here.
        children->erase (it);
        keep->name = toKey;
        (*children)[keep->name.c_str ()] = keep;
    }
}

n2a::MNode::Iterator
n2a::MVolatile::begin ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    Iterator result (*this);
    if (children)
    {
        result.keys->reserve (children->size ());
        for (auto c : *children) result.keys->push_back (c.first);  // To be safe for delete, these must be full copies of the strings.
    }
    return result;
}

const char *
n2a::MVolatile::keyPointer () const
{
    return name.c_str ();
}

n2a::MNode &
n2a::MVolatile::childGet (const String & key, bool create)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! children)
    {
        if (! create) return none;
        children = new std::map<const char *, MNode *, Order>;
    }
    auto it = children->find (key.c_str ());
    if (it == children->end ())
    {
        if (! create) return none;
        MVolatile * result = new MVolatile (nullptr, key.c_str (), this);
        (*children)[result->name.c_str ()] = result;
        return *result;
    }
    return * it->second;
}

void
n2a::MVolatile::childClear (const String & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! children) return;  // nothing to clear
    auto it = children->find (key.c_str ());
    if (it == children->end ()) return;
    delete it->second;
    children->erase (it);
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
n2a::MPersistent::move (const String & fromKey, const String & toKey)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (toKey == fromKey) return;
    if (! children) return;

    auto end = children->end ();
    auto it  = children->find (toKey.c_str ());
    if (it != end)
    {
        delete it->second;
        children->erase (it);
        markChanged ();
    }

    it = children->find (fromKey.c_str ());
    if (it != end)
    {
        MPersistent * keep = (MPersistent *) it->second;  // just assume all children are MPersistent
        children->erase (it);
        keep->name = toKey;
        (*children)[keep->name.c_str ()] = keep;
        keep->markChanged ();
        markChanged ();
    }
}

n2a::MNode &
n2a::MPersistent::childGet (const String & key, bool create)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! children)
    {
        if (! create) return none;
        children = new std::map<const char *, MNode *, Order>;
    }
    auto it = children->find (key.c_str ());
    if (it == children->end ())
    {
        if (! create) return none;
        markChanged ();
        MPersistent * result = new MPersistent (this, nullptr, key.c_str ());
        (*children)[result->name.c_str ()] = result;
        return *result;
    }
    return * it->second;
}

void
n2a::MPersistent::childClear (const String & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    // Notice that this method will mark us changed, even if key doesn't exist.
    MVolatile::childClear (key);
    markChanged ();
}


// class MDoc ----------------------------------------------------------------

int n2a::MDoc::missingFileException = 0;

void
n2a::MDoc::setMissingFileException (int method)
{
    missingFileException = method;
}

n2a::MDoc::MDoc (const char * path, const char * key, n2a::MDocGroup * container)
:   MPersistent (container, path, key)
{
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
    if (! children) children = new std::map<const char *, MNode *, Order>;
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
    if (! children) load ();  // redundant with the guard in load(), but should save time in the common case that file is already loaded
    return MPersistent::size ();
}

bool
n2a::MDoc::data ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! children) load ();
    return MPersistent::data ();
}

String
n2a::MDoc::getOrDefault (const String & defaultValue)
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
    // TODO: do UTF-8 filenames require special handling on Windows?
    if (rename (this->value, value))  // non-zero return from rename() indicates failure
    {
        std::cerr << "Failed to move file: " << this->value << " --> " << value << std::endl;
    }
}

void
n2a::MDoc::move (const String & fromKey, const String & toKey)
{
    if (fromKey == toKey) return;
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! children) load ();
    MPersistent::move (fromKey, toKey);
}

n2a::MNode::Iterator
n2a::MDoc::begin ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! children) load ();
    return MPersistent::begin ();
}

String
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
    if (children) return;  // already loaded
    children = new std::map<const char *, MNode *, Order>;  // prevent re-entrant call when creating nodes
    needsWrite = true;  // lie to ourselves, to prevent being put onto the MDir write queue
    String file = path ();
    try
    {
        std::ifstream ifs (file.c_str ());
        Schema::readAll (*this, ifs);
        ifs.close ();
    }
    catch (...)  // An exception is common for a newly created doc that has not yet been flushed to disk.
    {
        if (missingFileException >= 1) std::cerr << "Failed to read " << file << std::endl;
        if (missingFileException >= 2) throw "MDod::load() failed to read file";
    }
    clearChanged ();  // After load(), clear the slate so we can detect any changes and save the document.
}

void
n2a::MDoc::save ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! needsWrite) return;
    String file = path ();
    try
    {
        mkdirs (file);
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
    String path;
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
        remove (path.c_str ());
    }
    catch (...)
    {
        std::cerr << "Failed to delete file: " << path << std::endl;
    }
}

n2a::MNode &
n2a::MDoc::childGet (const String & key, bool create)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! children) load ();
    return MPersistent::childGet (key, create);

}

void
n2a::MDoc::childClear (const String & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! children) load ();
    MPersistent::childClear (key);
}


// class MDocGroup -----------------------------------------------------------

n2a::MDocGroup::MDocGroup (const char * key)
:   name (key)
{
}

n2a::MDocGroup::~MDocGroup ()
{
    for (MDoc * doc: writeQueue) doc->save ();
    for (auto & c : children) delete c.second;
}

uint32_t
n2a::MDocGroup::classID () const
{
    return MDocGroupID;
}

String
n2a::MDocGroup::key () const
{
    return name;
}

String
n2a::MDocGroup::getOrDefault (const String & defaultValue)
{
    return defaultValue;
}

void
n2a::MDocGroup::clear ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (auto & c : children) delete c.second;
    children.clear ();
    writeQueue.clear ();
    observable.fireChanged ();
}

int
n2a::MDocGroup::size ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    return children.size ();
}

void
n2a::MDocGroup::move (const String & fromKey, const String & toKey)
{
    if (toKey == fromKey) return;
    std::lock_guard<std::recursive_mutex> lock (mutex);
    save ();

    // Adjust files on disk.
    String fromPath = pathForFile (fromKey);
    String toPath   = pathForFile (toKey);
    try
    {
        remove_all (toPath);
        rename (fromPath.c_str (), toPath.c_str ());
    }
    catch (...) {}  // This can happen if a new doc has not yet been flushed to disk.

    // Bookkeeping in "children" collection
    auto it = children.find (toKey);
    if (it != children.end ())
    {
        delete it->second;
        children.erase (it);
    }

    it = children.find (fromKey);
    if (it == children.end ())  // from does not exist
    {
        observable.fireChildDeleted (toKey);  // Because we overwrote an existing node with a non-existing node, causing the destination to cease to exist.
    }
    else  // from exists
    {
        MDoc * keep = (MDoc *) it->second;
        children.erase (it);
        keep->name = toKey;
        children[toKey] = keep;
        observable.fireChildChanged (fromKey, toKey);
    }
}

n2a::MNode::Iterator
n2a::MDocGroup::begin ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    Iterator result (*this);
    result.keys->reserve (children.size ());
    for (auto & c : children) result.keys->push_back (c.first);  // In order be safe for delete, these must be full copies of the strings.
    return result;
}

void
n2a::MDocGroup::addObserver (Observer * o)
{
    observable.addObserver (o);
}

void
n2a::MDocGroup::removeObserver (Observer * o)
{
    observable.removeObserver (o);
}

String
n2a::MDocGroup::pathForDoc (const String & key) const
{
    // MSVC gave a link error when this was a pure-virtual function, so added this stupid implementation.
    throw "MDocGroup is an abstract class. You must define pathForDoc() in a subclass.";
}

String
n2a::MDocGroup::pathForFile (const String & key) const
{
    return pathForDoc (key);
}

void
n2a::MDocGroup::save ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (MDoc * doc: writeQueue) doc->save ();
    writeQueue.clear ();
}

const char *
n2a::MDocGroup::keyPointer () const
{
    return name.c_str ();
}

n2a::MNode &
n2a::MDocGroup::childGet (const String & key, bool create)
{
    if (key.empty ()) throw "MDoc key must not be empty";
    std::lock_guard<std::recursive_mutex> lock (mutex);

    MDoc * result;
    auto it = children.find (key);
    if (it == children.end ())
    {
        if (! create) return none;
        result = nullptr;
    }
    else
    {
        result = it->second;
    }

    if (! result)
    {
        String path = pathForDoc (key);
        result = new MDoc (path.c_str (), key.c_str (), this);
        children[key] = result;
        if (create  &&  ! exists (path)) result->markChanged ();  // Set the new document to save. Adds to writeQueue.
        observable.fireChildAdded (key);
    }
    return *result;
}

void
n2a::MDocGroup::childClear (const String & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    auto it = children.find (key);
    if (it == children.end ()) return;

    MDoc * doc = it->second;
    delete it->second;
    children.erase (it);
    writeQueue.erase (doc);
    remove_all (pathForFile (key));
    observable.fireChildDeleted (key);
}

void
n2a::MDocGroup::unload (n2a::MDoc * doc)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    String key = doc->key ();
    auto it = children.find (key);
    if (it == children.end ()) return;
    if (doc->needsWrite) doc->save ();
    writeQueue.erase (doc);
    delete doc;
    children[key] = nullptr;
}


// class MDir ----------------------------------------------------------------

n2a::MDir::MDir (const String & root, const char * suffix, const char * key)
:   MDocGroup (key),
    root (root)
{
    if (suffix) this->suffix = suffix;
    loaded = false;
}

uint32_t
n2a::MDir::classID () const
{
    return MDocGroupID | MDirID;
}

String
n2a::MDir::key () const
{
    if (name.empty ()) return root;
    return name;
}

String
n2a::MDir::getOrDefault (const String & defaultValue)
{
    return root;
}

void
n2a::MDir::clear ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MDocGroup::clear ();
    remove_all (root);  // It's OK to delete this directory, since it will be re-created by the next MDoc that gets saved.
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

String
n2a::MDir::pathForDoc (const String & key) const
{
    String result = root + "/" + key;
    if (! suffix.empty ()) result = result + "/" + suffix;
    return result;
}

String
n2a::MDir::pathForFile (const String & key) const
{
    return root + "/" + key;
}

void
n2a::MDir::load ()
{
    if (loaded) return;  // as a boolean, presumably "loaded" is sufficiently atomic to avoid taking a lock just to check
    std::lock_guard<std::recursive_mutex> lock (mutex);

    // Scan directory.
    std::map<String, MDoc *, Order> newChildren;
    std::map<String, MDoc *>::iterator end = children.end ();
#   ifdef _MSC_VER

    WIN32_FIND_DATA fd;
    HANDLE hFind = FindFirstFile ((root + "/*").c_str (), &fd);
    if (hFind == INVALID_HANDLE_VALUE) return;
    do
    {
        String key = fd.cFileName;
        if (key[0] == '.') continue;
        if (! suffix.empty ()  &&  ! is_directory (root + "/" + key)) continue;

        std::map<String, MDoc *>::iterator it = children.find (key);
        if (it == end) newChildren[key] = nullptr;
        else           newChildren[key] = it->second;
    }
    while (FindNextFile (hFind, &fd));
    FindClose (hFind);

#   else

    DIR * dir = opendir (root.c_str ());
    while (struct dirent * e = readdir (dir))
    {
        String key = e->d_name;
        if (key[0] == '.') continue; // Filter out special files. This allows, for example, a git repo to share the models dir.
        if (! suffix.empty ()  &&  ! is_directory (root + "/" + key)) continue;  // Only permit directories when suffix is defined.

        // Add child to collection.
        // Keep object identity of any active MDoc.
        // Some MDocs could get lost if they aren't currently listed in the directory.
        // This would only happen if another process is messing with the directory.
        std::map<String, MDoc *>::iterator it = children.find (key);
        if (it == end) newChildren[key] = nullptr;
        else           newChildren[key] = it->second;
    }
    closedir (dir);

#   endif

    // Include newly-created docs that have never been flushed to disk.
    for (MDoc * doc : writeQueue)
    {
        const String & key = doc->key ();
        newChildren[key] = children[key];
    }
    children = std::move (newChildren);

    loaded = true;
}

const char *
n2a::MDir::keyPointer () const
{
    if (! name.empty ()) return name.c_str ();
    return root.c_str ();
}

n2a::MNode &
n2a::MDir::childGet (const String & key, bool create)
{
    if (key.empty ()) throw "MDoc key must not be empty";
    std::lock_guard<std::recursive_mutex> lock (mutex);

    MDoc * result;
    auto it = children.find (key);
    if (it == children.end ())
    {
        if (! create) return none;
        result = nullptr;
    }
    else
    {
        result = it->second;
    }

    if (! result)  // Doc is not currently loaded
    {
        String path = pathForDoc (key);
        bool exists = n2a::exists (path);
        if (! exists  &&  ! create)
        {
            if (suffix.empty ()) return none;
            // Allow the possibility that the dir exists but lacks its special file.
            if (! n2a::exists (pathForFile (key))) return none;
        }
        result = new MDoc (nullptr, key.c_str (), this);
        children[key] = result;
        if (create  &&  ! exists) result->markChanged ();  // Set the new document to save. Adds to writeQueue.
    }
    return *result;
}


// class MDocGroupKey --------------------------------------------------------

uint32_t
n2a::MDocGroupKey::classID () const
{
    return MDocGroupID | MDocGroupKeyID;
}

String
n2a::MDocGroupKey::pathForDoc (const String & key) const
{
    auto it = paths.find (key);
    if (it == paths.end ()) return MDocGroup::pathForDoc (key);
    return it->second;
}

void
n2a::MDocGroupKey::addDoc (const String & value, const String & key)
{
    paths[key] = value;
}


// class MCombo --------------------------------------------------------------

n2a::MCombo::MCombo (const String & name, const std::vector<MNode *> & containers, bool ownContainers)
:   name (name)
{
    primary = nullptr;
    this->ownContainers = false;
    init (containers, ownContainers);
}

n2a::MCombo::~MCombo ()
{
    releaseContainers ();
}

void
n2a::MCombo::init (const std::vector<MNode *> & containers, bool ownContainers)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);

    releaseContainers ();
    for (auto c : containers) c->addObserver (this);
    this->containers    = containers;  // Copy elements.
    this->ownContainers = ownContainers;

    if (! containers.empty ()) primary = containers[0];
    else                       primary = new MVolatile ();
    children.clear ();
    loaded = false;
    observable.fireChanged ();
}

void
n2a::MCombo::releaseContainers ()
{
    if (primary  &&  containers.empty ()) delete primary;  // Because we made it ourselves. See init().
    if (ownContainers)
    {
        for (auto c : containers) delete c;
    }
    else
    {
        for (auto c : containers) c->removeObserver (this);
    }
}

String
n2a::MCombo::key () const
{
    return name;
}

void
n2a::MCombo::clear ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    // This does not remove the original objects, only our links to them.
    containers.clear ();
    children  .clear ();
    observable.fireChanged ();
}

int
n2a::MCombo::size ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    load ();
    return children.size ();
}

void
n2a::MCombo::move (const String & fromKey, const String & toKey)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    auto it = children.find (fromKey);
    if (it != children.end ()  &&  containerIsWritable (* it->second)) it->second->move (fromKey, toKey);  // Triggers childChanged() call from MDir, which updates our children collection.
}

n2a::MNode::Iterator
n2a::MCombo::begin ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    Iterator result (*this);
    result.keys->reserve (children.size ());
    for (auto & c : children) result.keys->push_back (c.first);
    return result;
}

void
n2a::MCombo::addObserver (MNode::Observer * o)
{
    observable.addObserver (o);
}

void
n2a::MCombo::removeObserver (MNode::Observer * o)
{
    observable.removeObserver (o);
}

bool
n2a::MCombo::containerIsWritable (MNode & container) const
{
    if (&container == primary) return true;
    if (&container == &none) return false;
    for (auto c : containers) if (c == &container) return false;
    return true;  // A sub-class could add application-specific criteria for when a container is writable.
}

bool
n2a::MCombo::isWriteable (MNode & doc) const
{
    return containerIsWritable (doc.parent ());
}

bool
n2a::MCombo::isWriteable (const String & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    load ();
    auto it = children.find (key);
    if (it == children.end ()) return false;
    return containerIsWritable (* it->second);
}

bool
n2a::MCombo::isVisible (const MNode & doc)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (&doc == &none) return false;
    String key = doc.key ();
    for (auto c : containers)
    {
        MNode & child = c->child (key);
        if (&child != &none) return &doc == &child;
    }
    return false;
}

bool
n2a::MCombo::isHiding (const String & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    load ();
    int count = 0;
    for (auto c : containers) if (&c->child (key) != &none) count++;
    return count > 1;
}

n2a::MNode &
n2a::MCombo::containerFor (const String & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    load ();
    auto it = children.find (key);
    if (it == children.end ()) return none;
    return * it->second;
}

void
n2a::MCombo::changed ()
{
    // Force a rebuild of children.
    children.clear ();  // Similar to MVolatile, we don't need to dispose of key strings, because they belong to the individual nodes.
    loaded = false;
    observable.fireChanged ();
}

void
n2a::MCombo::childAdded (const String & key)
{
    MNode & oldChild = childGet (key);
    MNode * newContainer = rescanContainer (key);
    MNode & newChild = newContainer->child (key);
    if (&oldChild == &newChild) return;  // Change is hidden by higher-precedence dataset.
    children[key] = newContainer;
    if (&oldChild == &none) observable.fireChildAdded (key);         // This is a completely new child.
    else                    observable.fireChildChanged (key, key);  // The newly-added child hides the old child, so this appears as a change of content.
}

void
n2a::MCombo::childDeleted (const String & key)
{
    MNode * newContainer = rescanContainer (key);
    if (! newContainer)
    {
        children.erase (key);
        observable.fireChildDeleted (key);
        return;
    }
    // A hidden node was exposed by the delete.
    // It's also possible that the deleted node was hidden so no effective change occurred.
    // It's not worth the extra work to detect the "still hidden" case.
    children[key] = newContainer;
    observable.fireChildChanged (key, key);
}

void
n2a::MCombo::childChanged (const String & oldKey, const String & newKey)
{
    if (oldKey != newKey)  // Not a simple change of content, but rather a move of some sort.
    {
        // Update container mapping at both oldKey and newKey
        MNode * container = rescanContainer (oldKey);
        if (container) children.erase (oldKey);
        else           children[oldKey] = container;

        container = rescanContainer (newKey);
        if (container) children.erase (newKey);
        else           children[newKey] = container;
    }

    // It's possible that both oldKey and newKey are hidden by higher-precedent datasets,
    // producing no effective change. We should avoid forwarding the message in that case,
    // but it's not worth the effort to detect.
    observable.fireChildChanged (oldKey, newKey);
}

void
n2a::MCombo::save ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (auto c : containers)
    {
        uint32_t id = c->classID ();
        if      (id & MDirID)   static_cast<MDir *>   (c)->save ();
        else if (id & MComboID) static_cast<MCombo *> (c)->save ();
    }
}

void
n2a::MCombo::load ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (loaded) return;
    for (int i = containers.size () - 1; i >= 0; i--)
    {
        MNode * container = containers[i];
        uint32_t id = container->classID ();
        if (id & MDirID)  // Avoid forcing the load of every file!
        {
            MDir * dir = static_cast<MDir *> (container);
            dir->load ();
            for (auto it : dir->children) children[it.first] = container;
        }
        else if (id & MComboID)  // Ditto
        {
            MCombo * combo = static_cast<MCombo *> (container);
            combo->load ();
            for (auto it : combo->children) children[it.first] = container;
        }
        else  // General case
        {
            for (auto & c : *container) children[c.key ()] = container;
        }
    }
    loaded = true;
}

const char *
n2a::MCombo::keyPointer () const
{
    return name.c_str ();
}

n2a::MNode &
n2a::MCombo::childGet (const String & key, bool create)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    load ();
    auto it = children.find (key);
    if (it != children.end ()) return it->second->child (key);
    if (create) return primary->childOrCreate (key);  // Triggers childAdded() call from MDir, which updates our children collection.
    return none;
}

void
n2a::MCombo::childClear (const String & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    // This actually removes the original object.
    load ();
    auto it = children.find (key);
    if (it != children.end ()  &&  containerIsWritable (* it->second)) it->second->clear (key);  // Triggers childDeleted() call from MDir, which updates our children collection.
}

n2a::MNode *
n2a::MCombo::rescanContainer (const String & key) const
{
    for (auto c : containers) if (&c->child (key) != &none) return c;
    return nullptr;
}


// class MPart ---------------------------------------------------------------

n2a::MPart::MPart (MPart * container, MPart * inheritedFrom, MNode & source)
:   container     (container),
    inheritedFrom (inheritedFrom),
    children      (nullptr)
{
    this->source = original = &source;
}

n2a::MPart::~MPart ()
{
    if (! children) return;
    for (auto c : *children) delete c.second;
    delete children;
}

uint32_t
n2a::MPart::classID () const
{
    return MPartID;
}

String
n2a::MPart::key () const
{
    return source->key ();  // same as original->key()
}

n2a::MNode &
n2a::MPart::parent () const
{
    if (container) return *container;
    return none;
}

void
n2a::MPart::clear ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! children) return;  // nothing to clear
    if (! isFromTopDocument ()) return; // Nothing to do.
    releaseOverrideChildren ();
    clearPath ();
    expand ();
}

int
n2a::MPart::size ()
{
    if (! children) return 0;
    return children->size ();
}

bool
n2a::MPart::data ()
{
    return source->data ()  ||  original->data ();
}

String
n2a::MPart::getOrDefault (const String & defaultValue)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (source->data ()) return source->getOrDefault (defaultValue);
    return original->getOrDefault (defaultValue);
}

void
n2a::MPart::set (const char * value)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (source->data () ? source->get () == value : value == nullptr) return;  // No change, so nothing to do.
    bool couldReset = original->data () ? original->get () == value : value == nullptr;
    if (! couldReset) Override ();
    source->set (value);
    if (couldReset) clearPath ();
    if (source->key () == "$inherit")  // We changed a $inherit node, so rebuild our subtree.
    {
        setIDs ();
        container->purge (*this, nullptr);  // Undo the effect we had on the subtree.
        container->expand ();
    }
}

void
n2a::MPart::merge (MNode & that)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (that.data ()) set (that.get ().c_str ());

    // Process $inherit first
    MNode & thatInherit = that.child ("$inherit");
    if (&thatInherit != &none)
    {
        MNode * inherit = & childGet ("$inherit");
        bool existing =  inherit != &none;
        if (! existing) inherit = &childOrCreate ("$inherit");

        // Now do the equivalent of inherit.merge(thatInherit), but pay attention to IDs.
        // If "that" comes from an outside source, it could merge in IDs which disagree
        // with the ones we would otherwise look up during setIDs() called by set(). To honor the
        // imported IDs (that is, prioritize them over imported names), we merge the metadata
        // under $inherit first, then set the node itself in a way that avoids calling setIDs().
        for (auto & thatInheritChild : thatInherit)
        {
            String index = thatInheritChild.key ();
            MNode & c = inherit->childOrCreate (index);
            c.merge (thatInheritChild);
        }
        String thatInheritValue = thatInherit.get ();
        if (! thatInheritValue.empty ())
        {
            // This is a copy of set() with appropriate modifications.
            MPart * i = static_cast<MPart *> (inherit);
            String thisInheritValue = i->source->get ();
            if (thisInheritValue != thatInheritValue)
            {
                bool couldReset =  i->original->get () == thatInheritValue;
                if (! couldReset) i->Override ();
                i->source->set (thatInheritValue);
                if (couldReset) i->clearPath ();
                if (existing) purge (*i, nullptr);
                expand ();
            }
        }
    }

    // Then the rest of the children
    for (auto & thatChild : that)
    {
        if (&thatChild == &thatInherit) continue;
        String key = thatChild.key ();
        MNode & c = childOrCreate (key);
        c.merge (thatChild);
    }
}

void
n2a::MPart::move (const String & fromKey, const String & toKey)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (toKey == fromKey) return;
    childClear (toKey);  // By definition, no top-level document nodes are allowed to remain at the destination. However, underrides may exist.
    MNode & fromPart = childGet (fromKey);
    if (&fromPart == &none) return;
    if (! static_cast<MPart &> (fromPart).isFromTopDocument ()) return;  // We only move top-document nodes.

    MNode & fromDoc = source->child (fromKey);
    MNode & toPart  = childGet (toKey);
    if (&toPart == &none)  // No node at the destination, so merge at level of top-document.
    {
        MNode & toDoc = source->childOrCreate (toKey);
        toDoc.merge (fromDoc);
        MPart * c = construct (this, nullptr, toDoc);
        (*children)[c->source->keyPointer ()] = c;
        c->underrideChildren (nullptr, toDoc);  // The sub-tree is empty, so all injected nodes are new. They don't really underride anything.
        c->expand ();
    }
    else  // Some existing underrides, so merge in collated tree. This is more expensive because it involves multiple calls to set().
    {
        toPart.merge (fromDoc);
    }
    childClear (fromKey);
}

n2a::MNode::Iterator
n2a::MPart::begin ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    Iterator result (*this);
    if (children)
    {
        result.keys->reserve (children->size ());
        for (auto & c : *children) result.keys->push_back (c.first);
    }
    return result;
}

n2a::MNode &
n2a::MPart::getSource ()
{
    return *source;
}

n2a::MNode &
n2a::MPart::getOriginal ()
{
    return *original;
}

bool
n2a::MPart::isPart (MNode & node)
{
    if (! node.get ().empty ()) return false;  // A part never has an assignment. A variable might not have an assignment if it is multi-line.
    if (node.key ().starts_with ("$")) return false;
    for (auto & c : node) if (c.key ().starts_with ("@")) return false;  // has the form of a multi-line equation
    return true;
}

bool
n2a::MPart::isFromTopDocument () const
{
    // There are only 3 cases allowed:
    // * original == source and inheritedFrom != null -- node holds an inherited value
    // * original == source and inheritedFrom == null -- node holds a top-level value
    // * original != source and inheritedFrom != null -- node holds a top-level value and an underride (inherited value)
    // The fourth case is excluded by the logic of this class. If original != source, then inheritedFrom cannot be null.
    return original != source  ||  inheritedFrom == nullptr;
}

bool
n2a::MPart::isOverridden () const
{
    return original != source;  // and inheritedFrom != null, but no need to check that.
}

bool
n2a::MPart::isInherited () const
{
    return inheritedFrom != nullptr;
}

bool
n2a::MPart::clearRedundantOverrides ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);

    bool overrideNecessary = false;
    if (children)
    {
        for (auto c : *children)
        {
            if (! c.second->clearRedundantOverrides ()) overrideNecessary = true;
        }
    }

    if (source != original  &&  (! source->data ()  ||  source->get () == original->get ()))
    {
        if (overrideNecessary)
        {
            source->set (nullptr);  // Turn this into a pure placeholder node.
        }
        else
        {
            const char * key = original->keyPointer ();
            updateKey (key);
            source->parent ().clear (key);
            source = original;
        }
    }
    return ! isFromTopDocument ();
}

n2a::MNode &
n2a::MPart::getRepo ()
{
    return container->getRepo ();
}

n2a::MNode &
n2a::MPart::findModel (const String & ID)
{
    return container->findModel (ID);
}

n2a::MPart *
n2a::MPart::construct (MPart * container, MPart * inheritedFrom, MNode & source)
{
    return new MPart (container, inheritedFrom, source);
}

n2a::MNode &
n2a::MPart::childGet (const String & key, bool create)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (children)
    {
        auto it = children->find (key.c_str ());
        if (it != children->end ()) return * it->second;
    }
    if (! create) return none;

    // We don't have the child, so by construction it is not in any source document.
    Override ();  // ensures that source is a member of the top-level document tree
    MNode & s = source->childOrCreate (key);
    MPart * result = construct (this, nullptr, s);
    if (! children) children = new std::map<const char *, MPart *, Order>;
    (*children)[result->source->keyPointer ()] = result;
    if (key == "$inherit")  // We've created an $inherit line, so load the inherited equations.
    {
        result->setIDs ();
        // Purge is unnecessary because "result" is a new entry. There is no previous $inherit line.
        expand ();
    }
    return *result;
}

void
n2a::MPart::childClear (const String & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);

    if (! children) return;
    if (! isFromTopDocument ()) return;  // This node is not overridden, so none of the children will be.
    if (&source->child (key) == &none) return;  // The child is not overridden, so nothing to do.
    (*children)[key.c_str ()]->releaseOverride ();
    source->clear (key);
    clearPath ();

    auto it = children->find (key.c_str ());
    if (it != children->end ())  // If child still exists, then it was overridden but exposed by the delete.
    {
        if (key == "$inherit") expand ();             // We changed our $inherit expression, so rebuild our subtree.
        else                   it->second->expand (); // Otherwise, rebuild the subtree under the child.
    }
}

void
n2a::MPart::expand ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    std::forward_list<MNode *> visited;
    visited.push_front (static_cast<MPart &> (root ()).source);
    expand (visited);
}

void
n2a::MPart::expand (std::forward_list<MNode *> & visited)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    inherit (visited);
    if (! children) return;
    for (auto c : *children)
    {
        if (c.second->isPart ()) c.second->expand (visited);
    }
}

void
n2a::MPart::inherit (std::forward_list<MNode *> & visited)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! children) return;
    auto it = children->find ("$inherit");
    if (it != children->end ()) inherit (visited, *it->second, *it->second);
}

void
n2a::MPart::inherit (std::forward_list<MNode *> & visited, MPart & root, MNode & from)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);

    MNode & models = getRepo ();
    bool maintainable =  &from == &root  &&  root.isFromTopDocument ()  &&  (models.classID () & MComboID == 0  ||  static_cast<MCombo &> (models).isWriteable (* static_cast<MPart &> (root.root ()).source));
    bool changedName = false;  // Indicates that at least one name changed due to ID resolution. This lets us delay updating the field until all names are processed.
    bool changedID   = false;

    std::vector<String> parentNames = split (from.get (),              ",");
    std::vector<String> IDs         = split (from.get ("$meta", "id"), ",");  // Any unassigned ID in middle of list will appear as an empty string in the array. Thus they remain in sync.
    int count = parentNames.size ();
    for (int i = 0; i < count; i++)
    {
        String parentName = parentNames[i].trim ().replace_all ("\"", "");
        parentNames[i] = parentName;
        MNode * parentSource = &models.child (parentName);

        String id = "";
        if (i < IDs.size ()) id = IDs[i].trim ();

        String parentID = "";
        if (parentSource != &none)
        {
            parentID = parentSource->get ("$meta", "id");
            if (! id.empty ()  &&  parentID != id) parentSource = &none;  // Even though the name matches, parentSource is not really the same model that was originally linked.
        }
        if (parentSource == &none)
        {
            if (! id.empty ())
            {
                parentSource = & findModel (id);
                if (parentSource != &none  &&  maintainable)  // relink
                {
                    parentNames[i] = parentSource->key ();
                    changedName = true;
                }
            }
        }
        else
        {
            if (id.empty ()  &&  ! parentID.empty ()  &&  maintainable)
            {
                while (IDs.size () <= i) IDs.push_back ("");
                IDs[i] = parentID;
                changedID = true;
            }
        }

        if (parentSource != &none)
        {
            // Does visited contain parentSource? This guards against infinite loop via $inherit.
            bool found = false;
            for (auto v : visited)
            {
                if (v == parentSource)
                {
                    found = true;
                    break;
                }
            }

            if (! found)
            {
                underrideChildren (&root, *parentSource);
                MNode & parentFrom = parentSource->child ("$inherit");
                if (&parentFrom != &none)
                {
                    visited.push_front (parentSource);
                    inherit (visited, root, parentFrom);  // We continue to treat the root as the initiator for all the inherited equations.
                    visited.pop_front ();
                }
            }
        }
    }

    if (changedName)
    {
        String value = parentNames[0];
        for (int i = 1; i < parentNames.size (); i++)
        {
            value += ", ";
            value += parentNames[i];
        }
        root.source->set (value);
    }
    if (changedID)
    {
        String value = IDs[0];
        for (int i = 1; i < IDs.size (); i++)
        {
            value += ",";
            value += IDs[i];
        }
        root.source->set (value, "$meta", "id");
    }
}

void
n2a::MPart::underride (MPart * from, MNode & newSource)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (from != this)  // We don't allow incoming $inherit lines to underride the $inherit that brought them in, since their existence is completely contingent on it.
    {
        if (inheritedFrom == &none)
        {
            inheritedFrom = from;
            original = &newSource;
        }
        else if (! original->data ())  // an inherited value of undefined
        {
            // Allow deeper inherited value to show through.
            // Does not change which $inherit line is responsible for the existence of this node.
            if (original == source)
            {
                updateKey (newSource.keyPointer ());
                source = original = &newSource;
            }
            else
            {
                original = &newSource;
            }
        }
    }
    underrideChildren (from, newSource);
}

void
n2a::MPart::underrideChildren (MPart * from, MNode & newSource)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);

    if (newSource.size () == 0) return;
    if (! children) children = new std::map<const char *, MPart *, Order>;
    for (auto & n : newSource)
    {
        String key = n.key ();
        auto it = children->find (key.c_str ());
        if (it == children->end ())
        {
            MPart * c = construct (this, from, n);
            (*children)[c->source->keyPointer ()] = c;
            c->underrideChildren (from, n);
        }
        else
        {
            it->second->underride (from, n);
        }
    }
}

void
n2a::MPart::purge (MPart & from, MPart * parent)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);

    if (inheritedFrom == &from)
    {
        if (source == original)  // This node exists only because of "from". Implicitly, we are not from the top document, and all our children are in the same condition.
        {
            if (parent) parent->clear (source->key ());
            return;
        }
        else  // This node contains an underride, so simply remove that underride.
        {
            original = source;
            inheritedFrom = nullptr;
        }
    }

    if (! children) return;
    auto it = children->find ("$inherit");
    if (it != children->end ()  &&  it->second->inheritedFrom == &from) purge (* it->second, nullptr);  // If our local $inherit is contingent on "from", then remove all its effects as well. Note that a $inherit line never comes from itself (inherit.inheritedFrom != inherit).

    for (auto c : *children) c.second->purge (from, this);
}

void
n2a::MPart::releaseOverride ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);

    if (! isFromTopDocument ()) return;  // This node is not overridden, so nothing to do.

    String key = source->key ();
    bool selfDestruct = false;
    if (source == original)  // This node only exists in top doc, so it should be deleted entirely.
    {
        container->children->erase (key.c_str ());
        selfDestruct = true;
    }
    else  // This node is overridden, so release it.
    {
        releaseOverrideChildren ();
        updateKey (original->keyPointer ());
        source = original;
    }
    if (key == "$inherit") container->purge (*this, nullptr);
    if (selfDestruct) delete this;
}

void
n2a::MPart::releaseOverrideChildren ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (auto & c : *source)  // Implicitly, everything we iterate over will be from the top document.
    {
        (*children)[c.keyPointer ()]->releaseOverride ();  // The key is guaranteed to be in our children collection.
    }
    source->clear ();
}

void
n2a::MPart::Override ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (isFromTopDocument ()) return;
    // The only way to get past the above line is if original==source
    container->Override ();
    MNode & temp = container->source->childGet (key (), true);
    updateKey (temp.keyPointer ());
    source = &temp;
}

bool
n2a::MPart::overrideNecessary ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! children) return false;
    for (auto c : *children) if (c.second->isFromTopDocument ()) return true;
    return false;
}

void
n2a::MPart::clearPath ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (source != original  &&  (! source->data ()  ||  source->get () == original->get ())  &&  ! overrideNecessary ())
    {
        updateKey (original->keyPointer ());
        source->parent ().clear (source->key ());  // delete ourselves from the top-level document
        source = original;
        container->clearPath ();
    }
}

void
n2a::MPart::setIDs ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);

    std::vector<String> parentNames = split (source->get (), ",");
    int count = parentNames.size ();
    if (count == 0)
    {
        clear ("$meta", "id");
        return;
    }

    std::vector<String> newIDs (count);
    for (int i = 0; i < count; i++)
    {
        String parentName = parentNames[i].trim ().replace_all ("\"", "");
        MNode & parentSource = getRepo ().child (parentName);
        if (&parentSource == &none) newIDs.push_back ("");
        else                        newIDs.push_back (parentSource.get ("$meta", "id"));
    }

    String id = newIDs[0];
    for (int i = 1; i < newIDs.size (); i++)
    {
        id += ",";
        id += newIDs[i];
    }
    set (id, "$meta", "id");
}

void
n2a::MPart::updateKey (const char * key)
{
    if (! container) return;  // Sometimes we are the root node, so guard access to "container".
    container->children->erase (key);
    (*container->children)[key] = this;
}


// class MPartRepo -----------------------------------------------------------

n2a::MPartRepo::MPartRepo (MNode & source, MNode & repo, bool ownRepo)
:   MPart (nullptr, nullptr, source)
{
    this->ownRepo = ownRepo;
    build (repo);
}

n2a::MPartRepo::MPartRepo (MNode & source, const std::vector<String> & paths)
:   MPart (nullptr, nullptr, source)
{
    build (paths);
}

n2a::MPartRepo::MPartRepo (MNode & source, const String & paths)
:   MPart (nullptr, nullptr, source)
{
    build (paths);
}

n2a::MPartRepo::~MPartRepo ()
{
    if (ownRepo) delete repo;
}

uint32_t
n2a::MPartRepo::classID () const
{
    return MPartRepoID;
}

void
n2a::MPartRepo::build (MNode & repo)
{
    this->repo = &repo;
    underrideChildren (nullptr, *source);
    expand ();
}

void
n2a::MPartRepo::build (const std::vector<String> & paths)
{
    // Assemble search path into a repo.
    ownRepo = true;
    std::vector<MNode *> containers;
    for (auto & path : paths)
    {
        if (is_directory (path))
        {
            containers.push_back (new MDir (path));
        }
        else
        {
            MDocGroupKey * group = new MDocGroupKey;
            containers.push_back (group);
            int pos = path.find_last_of ('/');
            if (pos == String::npos) group->addDoc (path, path);
            else                     group->addDoc (path, path.substr (pos+1));
        }
    }
    build (* new MCombo (nullptr, containers, true));
}

void
n2a::MPartRepo::build (const String & paths)
{
    build (split (paths, ":"));
}

n2a::MNode &
n2a::MPartRepo::getRepo ()
{
    return *repo;
}

n2a::MNode &
n2a::MPartRepo::findModel (const String & ID)
{
    if (indexID == nullptr)
    {
        indexID = new std::map<String,String>;
        for (auto & n : *repo)
        {
            String nid = n.get ("$meta", "id");
            if (! nid.empty ()) indexID->emplace (nid, n.key ());
        }
    }
    auto it = indexID->find (ID);
    if (it == indexID->end ()) return none;
    return repo->child (it->second);
}


// class Schema --------------------------------------------------------------

n2a::Schema::Schema (int version, const String & type)
:   version (version),
    type (type)
{
}

n2a::Schema *
n2a::Schema::latest ()
{
    return new Schema2 (3, "");
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
    String line;
    getline (reader, line);
    if (! reader.good ()) throw "File is empty.";
    line.trim ();
    if (line.size () < 12) throw "Malformed schema line.";
    if (line.substr (0, 11) != "N2A.schema=") throw "Schema line missing or malformed.";
    line = line.substr (11);

    String::size_type pos = line.find_first_of (",");
    int version = strtol (line.substr (0, pos).c_str (), nullptr, 10);
    String type = "";
    if (pos != String::npos) type = line.substr (pos + 1).trim ();  // skip the comma

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
        getline (reader, line);  // default line ending is NL
        if (! reader.good ())
        {
            whitespaces = -1;
            return;
        }

        int count = line.size ();
        if (count == 0) continue;
        if (line[count-1] == '\r') line.resize (count - 1);  // Get rid of CR. Should work for every platform except older MacOS.
        if (! line.empty ()) break;
    }

    // Count leading whitespace
    int length = line.size ();
    whitespaces = 0;
    while (whitespaces < length  &&  line[whitespaces] == ' ') whitespaces++;
}


// class Schema2 -------------------------------------------------------------

n2a::Schema2::Schema2 (int version, const String & type)
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
        reader.line.trim ();
        String key;
        String value;
        bool hasValue = false;
        bool escape =  reader.line[0] == '"';
        int i = escape ? 1 : 0;
        int last = reader.line.size () - 1;
        for (; i <= last; i++)
        {
            char c = reader.line[i];
            if (escape)
            {
                if (c == '"')
                {
                    // Look ahead for second quote
                    if (i < last  &&  reader.line[i+1] == '"')
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
                    value = reader.line.substr (i+1).trim ();
                    hasValue = true;
                    break;
                }
            }
            key += c;
        }
        key.trim ();

        if (! value.empty ()  &&  value[0] == '|')  // go into string reading mode
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
        MNode & child = node.set (hasValue ? value.c_str () : nullptr, {std::move (key)});  // Create a child with the given value
        if (reader.whitespaces > whitespaces) read (child, reader, reader.whitespaces);  // Recursively populate child. When this call returns, reader.whitespaces <= whitespaces in this function, because that is what ends the recursion.
        if (reader.whitespaces < whitespaces) return;  // end recursion
    }
}

void
n2a::Schema2::write (MNode & node, std::ostream & writer, const String & indent)
{
    String key = node.key ();
    if (! key.empty ()  &&  (key[0] == '\"'  ||  key.find_first_of (':') != String::npos))  // key contains forbidden characters
    {
        // Quote the key. Any internal quote marks must be escaped.
        // We use quote as its own escape, avoiding the need to escape a second code (such as both quote and backslash).
        // This follows the example of YAML.
        String original = key;
        int count = original.size ();
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
        const String & value = node.get ();
        if (value.find_first_of ('\n') == String::npos  &&  ! value.starts_with ("|"))
        {
            writer << value << std::endl;
        }
        else  // go into extended text write mode
        {
            writer << "|" << std::endl;
            String::size_type count = value.size ();
            String::size_type current = 0;
            while (true)
            {
                String::size_type next = value.find_first_of ('\n', current);
                writer << indent << " ";
                if (next == String::npos)
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

    String space2 = indent + " ";
    for (auto & c : node) write (c, writer, space2);  // if this node has no children, nothing at all is written
}


#endif
