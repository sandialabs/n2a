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
n2a::MNode::root () const
{
    const MNode * result = this;
    while (true)
    {
        MNode & parent = result->parent ();
        if (&parent == &none) break;
        result = &parent;
    }
    return const_cast<MNode &> (*result);
}

n2a::MNode &
n2a::MNode::lca (const MNode & that) const
{
    // Strategy: Place the ancestry of one node in a set. Then walk up the ancestry
    // of the other node. The first ancestor found in the set is the LCA.

    std::unordered_set<const MNode *> thisAncestors;
    const MNode * A = this;
    while (A != &none)
    {
        thisAncestors.insert (A);
        A = & A->parent ();
    }

    auto end = thisAncestors.end ();
    const MNode * B = &that;
    while (B != &none)
    {
        if (thisAncestors.find (B) != end) return const_cast<MNode &> (*B);  // C++20 has contains(), but no need to create dependency on newer standard
        B = & B->parent ();
    }
    return none;
}

n2a::MNode &
n2a::MNode::child (const std::string & key) const
{
    return none;
}

n2a::MNode &
n2a::MNode::child (const std::vector<std::string> & keys) const
{
    const MNode * result = this;
    int count = keys.size ();
    std::lock_guard<std::recursive_mutex> lock (const_cast<std::recursive_mutex &> (mutex));
    for (int i = 0; i < count; i++)
    {
        MNode * c = & result->child (keys[i]);
        if (c == &none) return none;
        result = c;
    }
    return const_cast<MNode &> (*result);  // If no keys are specified, we return this node.
}

n2a::MNode &
n2a::MNode::childOrCreate (const std::vector<std::string> & keys)
{
    MNode * result = this;
    int count = keys.size ();
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (int i = 0; i < count; i++)
    {
        MNode * c = & result->child (keys[i]);
        if (c == &none) c = & result->set (nullptr, keys[i]);
        result = c;
    }
    return const_cast<MNode &> (*result);
}

n2a::MNode &
n2a::MNode::childAt (int index) const
{
    std::lock_guard<std::recursive_mutex> lock (const_cast<std::recursive_mutex &> (mutex));
    for (auto & c : *this) if (index-- == 0) return c;
    return none;
}

void
n2a::MNode::clear ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (auto & n : *this) clear (n.key ());
}

void
n2a::MNode::clear (const std::string & key)
{
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
        c = & c->child (keys[i]);
        if (c == &none) return;  // Nothing to clear
    }
    c->clear (keys[last]);
}

int
n2a::MNode::size () const
{
    return 0;
}

bool
n2a::MNode::isEmpty () const
{
    return size () == 0;
}

bool
n2a::MNode::data () const
{
    return false;
}

bool
n2a::MNode::data (const std::vector<std::string> & keys) const
{
    MNode & c = child (keys);
    if (&c == &none) return false;
    return c.data ();
}

bool
n2a::MNode::containsKey (const std::string & key) const
{
    if (& child (key) != &none) return true;
    for (auto & c : *this) if (c.containsKey (key)) return true;
    return false;
}

std::string
n2a::MNode::get () const
{
    return getOrDefault (std::string (""));
}

std::string
n2a::MNode::get (const std::vector<std::string> & keys) const
{
    MNode & c = child (keys);
    if (&c == &none) return "";
    return c.get ();
}

std::string
n2a::MNode::getOrDefault (const std::string & defaultValue) const
{
    return defaultValue;
}

std::string
n2a::MNode::getOrDefault (const std::string & defaultValue, const std::vector<std::string> & keys) const
{
    std::string value = get (keys);
    if (value.empty ()) return defaultValue;
    return value;
}

bool
n2a::MNode::getOrDefault (bool defaultValue, const std::vector<std::string> & keys) const
{
    std::string value = get (keys);
    if (value.empty ()) return defaultValue;
    value = trim (value);
    if (trim (value) == "1") return true;
    if (strcasecmp (value.c_str (), "true")) return true;
    return false;
}

int
n2a::MNode::getOrDefault (int defaultValue, const std::vector<std::string> & keys) const
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
n2a::MNode::getOrDefault (long defaultValue, const std::vector<std::string> & keys) const
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
n2a::MNode::getOrDefault (double defaultValue, const std::vector<std::string> & keys) const
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
n2a::MNode::getFlag (const std::vector<std::string> & keys) const
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
n2a::MNode::set (const MNode & value)
{
    clear ();   // get rid of all children
    set (nullptr); // ensure that if value node is undefined, result node will also be undefined
    merge (value);
}

n2a::MNode &
n2a::MNode::set (const char * value, const std::string & key)
{
    return none;
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
n2a::MNode::set (const MNode & value, const std::vector<std::string> & keys)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    MNode & result = childOrCreate (keys);
    result.set (value);
    return result;
}

void
n2a::MNode::merge (const MNode & that)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (that.data ()) set (that.get ());
    for (auto & thatChild : that)
    {
        std::string key = thatChild.key ();
        MNode * c = & child (key);
        if (c == &none) c = & set (nullptr, key);  // ensure a target child node exists
        c->merge (thatChild);
    }
}

void
n2a::MNode::mergeUnder (const MNode & that)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (! data ()  &&  that.data ()) set (that.get ());
    for (auto & thatChild : that)
    {
        std::string key = thatChild.key ();
        MNode & c = child (key);
        if (&c == &none) set (thatChild, {key});
        else             c.mergeUnder (thatChild);
    }
}

void
n2a::MNode::uniqueNodes (const MNode & that)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (that.data ()) set (nullptr);
    for (auto & c : *this)
    {
        std::string key = c.key ();
        MNode & d = that.child (key);
        if (&d == &none) continue;
        c.uniqueNodes (d);
        if (c.size () == 0  &&  ! c.data ()) clear (key);
    }
}

void
n2a::MNode::uniqueValues (const MNode & that)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (data ()  &&  that.data ()  &&  get () == that.get ()) set (nullptr);
    for (auto & c : *this)
    {
        std::string key = c.key ();
        MNode & d = that.child (key);
        if (&d == &none) continue;
        c.uniqueValues (d);
        if (c.size () == 0  &&  ! c.data ()) clear (key);
    }
}

void
n2a::MNode::changes (const MNode & that)
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
        MNode & d = that.child (key);
        if (&d == &none) clear (key);
        else             c.changes (d);
    }
}

void
n2a::MNode::move (const std::string & fromKey, const std::string & toKey)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    if (toKey == fromKey) return;
    clear (toKey);
    MNode & source = child (fromKey);
    if (&source != &none)
    {
        MNode & destination = set (nullptr, toKey);
        destination.merge (source);
        clear (fromKey);
    }
}

n2a::MNode::Iterator
n2a::MNode::begin () const
{
    // Same value as end(), so the iterator is already done before it gets started.
    // Obviously, this needs to be overridden in derived classes.
    std::vector<std::string> empty;
    return Iterator (*this, empty);
}

n2a::MNode::Iterator
n2a::MNode::end () const
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
        MNode & b = that.child (a.key ());
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
        MNode & b = that.child (a.key ());
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


// class MVolatile -----------------------------------------------------------

n2a::MVolatile::MVolatile (const char * value, const char * name, MNode * container)
:   container (container)
{
    if (value) this->value = strdup (value);
    else       this->value = nullptr;
    if (name) this->name = name;  // Otherwise, name is blank
}

n2a::MVolatile::~MVolatile ()
{
    if (value) free (value);
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

n2a::MNode &
n2a::MVolatile::child (const std::string & key) const
{
    std::lock_guard<std::recursive_mutex> lock (const_cast<std::recursive_mutex &> (mutex));
    try
    {
        return * children.at (key.c_str ());
    }
    catch (...)  // out_of_range
    {
        return none;
    }
}

void
n2a::MVolatile::clear ()
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    for (auto c : children) delete c.second;
    children.clear ();
}

void
n2a::MVolatile::clear (const std::string & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    std::map<const char *, MNode *>::iterator it = children.find (key.c_str ());
    if (it == children.end ()) return;
    delete it->second;
    children.erase (it);
}

int
n2a::MVolatile::size () const
{
    std::lock_guard<std::recursive_mutex> lock (const_cast<std::recursive_mutex &> (mutex));
    return children.size ();
}

bool
n2a::MVolatile::data () const
{
    return value;
}

std::string
n2a::MVolatile::getOrDefault (const std::string & defaultValue) const
{
    std::lock_guard<std::recursive_mutex> lock (const_cast<std::recursive_mutex &> (mutex));
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

n2a::MNode &
n2a::MVolatile::set (const char * value, const std::string & key)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);
    try
    {
        MNode * result = children.at (key.c_str ());
        result->set (value);
        return *result;
    }
    catch (...)
    {
        MVolatile * result = new MVolatile (value, key.c_str (), this);
        children[result->name.c_str ()] = result;
        return *result;
    }
}

void
n2a::MVolatile::move (const std::string & fromKey, const std::string & toKey)
{
    std::lock_guard<std::recursive_mutex> lock (mutex);

}

n2a::MNode::Iterator
n2a::MVolatile::begin () const
{
    std::lock_guard<std::recursive_mutex> lock (const_cast<std::recursive_mutex &> (mutex));
    std::vector<std::string> keys;
    keys.reserve (children.size ());
    for (auto c : children) keys.push_back (c.first);  // In order be safe for delete, these must be full copies of the strings.
    return Iterator (*this, keys);
}

n2a::MNode::Iterator
n2a::MVolatile::end () const
{
    std::lock_guard<std::recursive_mutex> lock (const_cast<std::recursive_mutex &> (mutex));
    return MNode::Iterator (*this, children.size ());
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
n2a::Schema::writeAll (const MNode & node, std::ostream & writer)
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
n2a::Schema::write (const MNode & node, std::ostream & writer)
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
        getline (reader, line);
        if (! reader.good ())
        {
            whitespaces = -1;
            return;
        }
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
        MNode & child = node.set (value.c_str (), key);  // Create a child with the given value
        if (reader.whitespaces > whitespaces) read (child, reader, reader.whitespaces);  // Recursively populate child. When this call returns, reader.whitespaces <= whitespaces in this function, because that is what ends the recursion.
        if (reader.whitespaces < whitespaces) return;  // end recursion
    }
}

void
n2a::Schema2::write (const MNode & node, std::ostream & writer, const std::string & indent)
{
    std::string key = node.key ();
    if (key[0] == '\"'  ||  key.find_first_of (":") != std::string::npos)  // key contains forbidden characters
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
        if (value.find_first_of ("\n") == std::string::npos)
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
                std::string::size_type next = value.find_first_of ("\n", current);
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
