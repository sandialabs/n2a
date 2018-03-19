/**
    A lightweight drop-in replacement for stl::string.
    Avoids STL bloat (locales, exceptions ...) and only deals with single-byte characters.
    This class only implements functions that are actually used by the runtime engine.
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

    String ()
    {
        memory   = 0;
        top      = 0;
        capacity = 0;
    }

    String (const char * value)
    {
        memory   = 0;
        top      = 0;
        capacity = 0;
        if (value) assign (value, strlen (value));
    }

    String (const String & that)
    {
        memory   = 0;
        top      = 0;
        capacity = 0;
        assign (that.memory, that.top - that.memory);
    }

    String (String && that) noexcept
    {
        memory   = that.memory;
        top      = that.top;
        capacity = that.capacity;
        that.memory   = 0;
        that.top      = 0;
        that.capacity = 0;
    }

    ~String ()
    {
        if (memory) free (memory);
    }

    String & assign (const char * value, size_t n)
    {
        if (value  &&  n)
        {
            size_t requiredCapacity = n + 1;
            if (requiredCapacity > capacity)
            {
                if (memory) free (memory);
                capacity = requiredCapacity;
                memory = (char *) malloc (capacity);
            }
            memcpy (memory, value, n);
            top = memory + n;
        }
        else
        {
            top = memory;
        }
        if (top) *top = 0;
    }

    String & operator= (const String & that)
    {
        if (this != &that)
        {
            assign (that.memory, that.top - that.memory);
        }
        return *this;
    }

    String & operator= (String && that) noexcept
    {
        if (this != &that)
        {
            if (memory) free (memory);
            memory   = that.memory;
            top      = that.top;
            capacity = that.capacity;
            that.memory   = 0;
            that.top      = 0;
            that.capacity = 0;
        }
        return *this;
    }

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

    bool operator== (const String & that) const
    {
        if (memory == that.memory) return true;
        if (memory == 0  ||  that.memory == 0) return false;
        return strcmp (memory, that.memory) == 0;
    }

    bool operator< (const String & that) const
    {
        if (memory == that.memory) return false;
        if (memory == 0) return true;
        if (that.memory == 0) return false;
        return strcmp (memory, that.memory) < 0;
    }

    const char & operator[] (size_t pos) const
    {
        return memory[pos];
    }

    /// Subroutine for add operators
    static void combine (const char * a, const char * b, char * result)
    {
        if (a) while (*a) {*result++ = *a++;}  // copies everything but the null terminator
        if (b) while (*b) {*result++ = *b++;}
        *result = 0;
    }

    String operator+ (const String & that) const
    {
        String result;
        int length = (top - memory) + (that.top - that.memory);
        result.capacity = length + 1;
        result.memory = (char *) malloc (result.capacity);
        combine (memory, that.memory, result.memory);
        result.top = result.memory + length;
        return result;
    }

    String operator+ (const char * that) const
    {
        String result;
        int length = top - memory;
        if (that) length += strlen (that);
        result.capacity = length + 1;
        result.memory = (char *) malloc (result.capacity);
        combine (memory, that, result.memory);
        result.top = result.memory + length;
        return result;
    }

    String & append (const char * that, size_t n)
    {
        size_t length = (top - memory) + n;
        if (! length) return *this;
        size_t requiredCapacity = length + 1;
        if (requiredCapacity > capacity)
        {
            char * temp = memory;
            capacity = requiredCapacity;
            memory = (char *) malloc (capacity);
            combine (temp, that, memory);
            top = memory + length;
            if (temp) free (temp);
        }
        else
        {
            const char * t = that;
            while (t < that + n) *top++ = *t++;
            *top = 0;
        }
        return *this;
    }

    String & operator+= (const String & that)
    {
        return append (that.memory, that.top - that.memory);
    }

    String & operator+= (const char * that)
    {
        if (that) return append (that, strlen (that));
        return *this;
    }

    String & operator+= (int that)
    {
        char buffer[16];
        sprintf (buffer, "%i", that);
        return append (buffer, strlen (buffer));
    }

    String & operator+= (double that)
    {
        char buffer[32];
        sprintf (buffer, "%g", that);
        return append (buffer, strlen (buffer));
    }

    String substr (size_t pos, size_t length = npos) const noexcept
    {
        String result;
        size_t available = top - memory;
        if (length > available  ||  length == npos) length = available;
        length -= pos;
        if (length >= 0) result.assign (memory + pos, length);
        return result;
    }

    size_t find_first_of (const char * pattern, size_t pos = 0) const
    {
        if (memory == top  ||  ! pattern) return npos;
        char * c = memory + pos;
        while (c < top)
        {
            const char * p = pattern;
            while (*p)
            {
                if (*p == *c) return c - memory;
                p++;
            }
        }
        return npos;
    }

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

inline std::istream & getline (std::istream & in, String & result, char delimiter = '\n')
{
    result.clear ();
    const int limit = result.max_size ();
    char buffer[128];
    char * top = buffer + sizeof (buffer);
    char * b   = buffer;
    std::streambuf * rdbuf = in.rdbuf ();
    int c = rdbuf->sgetc ();
    while (c != delimiter  &&  c >= 0)  // The last test is for EOF
    {
        *b++ = c;
        if (b == top)
        {
            result.append (buffer, sizeof (buffer));
            b = buffer;
            if (result.size () >= limit) break;
        }
        c = rdbuf->sgetc ();
    }
    int remaining = b - buffer;
    if (remaining) result.append (buffer, remaining);
}

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
