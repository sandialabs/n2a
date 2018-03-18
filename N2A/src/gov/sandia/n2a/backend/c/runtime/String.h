/**
    A lightweight drop-in replacement for stl::string.
    This is lightweight in that it avoid STL bloat (like traits, locales, exceptions ...)
    and only deals with single-byte characters.
    This class only implements functions that are actually used by the runtime engine,
    and it adds a few useful functions not found in STL.
**/

#ifndef n2a_string_h
#define n2a_string_h

#include <string.h>
#include <stdio.h>
#include <cstddef>
#include <functional>
#ifndef N2A_SPINNAKER
# include <istream>
#endif


class String    // Note the initial cap. This name will not conflict with stl::string.
{
public:
    char * memory;
    char * top;       // position of null terminator in memory block
    size_t capacity;  // size of currently-allocated memory block

    static const size_t npos = static_cast<size_t> (-1);

    String ();
    String (const char * value);
    String (const String & that);
    String (String && that) noexcept;
    ~String ();

    String & assign (const char * value, size_t n);
    String & operator= (const String & that);
    String & operator= (String && that) noexcept;
    void clear ()
    {
        if (memory) memory[0] = 0;
        top = memory;
    }
    size_t size () const
    {
        return top - memory;
    }
    bool empty () const
    {
        return top == memory;
    }
    size_t max_size () const
    {
        return 0x1000000;  // 16Mb; This is suitable for most systems.
    }
    const char * c_str () const
    {
        if (memory) return memory;
        return "";
    }
    const char * operator() () const
    {
        if (memory) return memory;
        return "";
    }
    bool operator== (const String & that) const;
    bool operator< (const String & that) const;
    const char & operator[] (size_t pos) const
    {
        return memory[pos];
    }
    String operator+ (const String & that) const;
    String operator+ (const char * that) const;
    String & append (const char * that, size_t n);
    String & operator+= (const String & that)
    {
        return append (that.memory, that.top - that.memory);
    }
    String & operator+= (const char * that);
    String & operator+= (int that);
    String & operator+= (double that);
    String substr (size_t pos, size_t length = npos) const noexcept;
    size_t find_first_of (const char * pattern, size_t pos = 0) const;
    const char * begin () const
    {
        return memory;
    }
    const char * end () const
    {
        return top;
    }
};

#ifndef N2A_SPINNAKER

inline std::ostream & operator<< (std::ostream & out, const String & value)
{
    return out << value.memory;
}

std::istream & getline (std::istream & in, String & result, char delimiter = '\n');

#endif

namespace std
{
    template <>
    struct hash<String>
    {
        size_t operator() (const String & value) const
        {
            size_t result = 5381;
            if (value.memory)
            {
                char * m = value.memory;
                while (int c = *m++) result = ((result << 5) + result) + c;  // effectively: result * 33 + c
            }
            return result;
        }
    };
}

#endif
