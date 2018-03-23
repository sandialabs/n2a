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
        assign (that.memory, that.top - that.memory);  // If that is an empty string, then that.memory may be null. assign() handles this case properly.
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
        if (value  &&  n  &&  n <= max_size ())  // We should really throw an error if n > max_size, but this library avoids exceptions to support bare metal.
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
        return *this;
    }

    String & operator= (const String & that)
    {
        if (this != &that) assign (that.memory, that.top - that.memory);
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

    int compare (const String & that) const noexcept
    {
        if (memory == that.memory) return 0;
        if (memory == 0) return -1;
        if (that.memory == 0) return 1;

        int thisLength =      top -      memory;
        int thatLength = that.top - that.memory;
        int length = (thisLength < thatLength) ? thisLength : thatLength;

        char * a   =      memory;
        char * b   = that.memory;
        char * end = a + length;
        while (a < end)
        {
            int diff = *a++ - *b++;
            if (diff) return diff;
        }
        return thisLength - thatLength;
    }

    bool operator== (const String & that) const
    {
        return compare (that) == 0;
    }

    bool operator< (const String & that) const
    {
        return compare (that) < 0;
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

            char * m = memory;
            char * a = temp;
            if (a) while (*a) {*m++ = *a++;}
            const char * b   = that;
            const char * end = b + n;
            if (b) while (b < end) {*m++ = *b++;}

            top = memory + length;
            *top = 0;
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
        int available = (top - memory) - pos;
        if (available <= 0) return result;
        if (length > available) length = available;   // Assumes that npos works out to a large positive number.
        result.assign (memory + pos, length);
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
            c++;
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
    if (value.memory) out << value.memory;
    return out;
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
        c = rdbuf->snextc ();
    }
    if (c == delimiter) rdbuf->snextc ();  // Could cause stream bad, but by not using another streambuf function (sbumpc), we may reduce object file size slightly.
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
