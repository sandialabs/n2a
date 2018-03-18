#include "String.h"


String::String ()
{
    memory   = 0;
    top      = 0;
    capacity = 0;
}

String::String (const char * value)
{
    memory   = 0;
    top      = 0;
    capacity = 0;
    if (value) assign (value, strlen (value));
}

String::String (const String & that)
{
    memory   = 0;
    top      = 0;
    capacity = 0;
    assign (that.memory, that.top - that.memory);
}

String::String (String && that) noexcept
{
    memory   = that.memory;
    top      = that.top;
    capacity = that.capacity;
    that.memory   = 0;
    that.top      = 0;
    that.capacity = 0;
}

String::~String ()
{
    if (memory) free (memory);
}

String &
String::assign (const char * value, size_t n)
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

String &
String::operator= (const String & that)
{
    if (this != &that)
    {
        assign (that.memory, that.top - that.memory);
    }
    return *this;
}

String &
String::operator= (String && that) noexcept
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

bool
String::operator== (const String & that) const
{
    if (memory == that.memory) return true;
    if (memory == 0  ||  that.memory == 0) return false;
    return strcmp (memory, that.memory) == 0;
}

bool
String::operator< (const String & that) const
{
    if (memory == that.memory) return false;
    if (memory == 0) return true;
    if (that.memory == 0) return false;
    return strcmp (memory, that.memory) < 0;
}

/// Subroutine for add operators
static void combine (const char * a, const char * b, char * result)
{
    if (a) while (*a) {*result++ = *a++;}  // copies everything but the null terminator
    if (b) while (*b) {*result++ = *b++;}
    *result = 0;
}

String
String::operator+ (const String & that) const
{
    String result;
    int length = (top - memory) + (that.top - that.memory);
    result.capacity = length + 1;
    result.memory = (char *) malloc (result.capacity);
    combine (memory, that.memory, result.memory);
    result.top = result.memory + length;
    return result;
}

String
String::operator+ (const char * that) const
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

String &
String::append (const char * that, size_t n)
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

String &
String::operator+= (const char * that)
{
    if (that) return append (that, strlen (that));
    return *this;
}

String &
String::operator+= (int that)
{
    char buffer[16];
    sprintf (buffer, "%i", that);
    return append (buffer, strlen (buffer));
}

String &
String::operator+= (double that)
{
    char buffer[32];
    sprintf (buffer, "%g", that);
    return append (buffer, strlen (buffer));
}

String
String::substr (size_t pos, size_t length) const noexcept
{
    String result;
    size_t available = top - memory;
    if (length > available  ||  length == npos) length = available;
    length -= pos;
    if (length >= 0) result.assign (memory + pos, length);
    return result;
}

size_t
String::find_first_of (const char * pattern, size_t pos) const
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

#ifndef N2A_SPINNAKER

std::istream & getline (std::istream & in, String & result, char delimiter)
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
