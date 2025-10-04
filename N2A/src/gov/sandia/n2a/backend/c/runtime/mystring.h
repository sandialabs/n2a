/*
Copyright 2018-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_string_h
#define n2a_string_h

#include <cstring>
#include <stdio.h>
#include <cstddef>
#include <functional>
#include <vector>
#include <cmath>
#include <float.h>  // for FLT_EPSILON
#ifndef N2A_SPINNAKER
# include <istream>
#endif

// In some cases, the compiler knows the exact size of a string during a call
// to memchr(), due to inlining. However, in this source file we
// can't know that information. We use maxSize to specify the scan limit for
// memchr(), which is of course the logical thing to do. However, GCC 11+ barfs
// up warnings when our scan limit exceeds the known (to it) size of the string.
// We don't want to see this scary and useless warning.
#if __GNUC__ >= 11
#  pragma GCC diagnostic ignored "-Wstringop-overread"
#endif


// String formatting functions -----------------------------------------------

// These are general purpose functions for inserting formatted data into a
// char buffer. Their main role is to substitute for sprintf on an embedded
// system. However, they can be used anywhere.

// For float functions, we support only one output format: the equivalent of %g.

// These functions all take a pointer "p" to the start position in the char buffer.
// The buffer is assumed to have enough room for any inserted value.
// The pointer will be moved to just past the inserted string.

inline void append (char * & p, const char * value)
{
    while (*value) *p++ = *value++;
}

template<class T, typename std::enable_if<std::is_integral<T>::value  &&  std::is_unsigned<T>::value  &&  sizeof (T) >= 4, bool>::type = true>
inline void append (char * & p, T value)
{
    char * b = p;
    do
    {
        char digit = value % 10;
        value /= 10;
        *b++ = '0' + digit;
    }
    while (value);

    // Reverse the buffer contents.
    char * a = p;
    char * c = b - 1;
    while (c > a)
    {
        char temp = *a;
        *a++ = *c;
        *c-- = temp;
    }

    p = b;
}

template<class T, typename std::enable_if<std::is_integral<T>::value  &&  std::is_signed<T>::value  &&  sizeof (T) >= 4, bool>::type = true>
inline void append (char * & p, T value)
{
    if (value < 0)
    {
        *p++ = '-';
        value = -value;
    }
    append (p, (typename std::make_unsigned<T>::type) value);
}

/**
    The approach is to implement Dragon2 from Steele & White, "How to Print Floating-Point Numbers Accurately".
    The output is intended only for human viewing, not accurate transfer of data.
    The code also needs to be compact, so the output isn't as nice as a fully-developed C library.

    Since user code is calling a float function, we assume full float support is present, including exp() and log().

    @param value Must be a standard IEEE-754 float.
**/
inline void append (char * & p, float value)
{
    if (value == 0)
    {
        *p++ = '0';
        return;
    }

    // Detect NaN and Inf first.
    // Some float operations below will blow up on those.
    uint32_t raw = * (uint32_t *) &value;  // integer form of value
    if ((raw & 0x7F800000) == 0x7F800000)  // exponent==255
    {
        if (raw & 0x7FFFFF) append (p, ".nan");
        else                append (p, ".inf");
        return;
    }

    if (value < 0)
    {
        *p++ = '-';
        value = -value;
    }

    // Align float to [1,10). This effectively transfers the exponent from binary
    // representation into decimal. This is also what we print out for "e" at the end.
    // Because this is done in floating-point, it destroys some precision. We're being lazy.
    // TODO: adding FLT_EPSILON to log value breaks some output. Fix latter code to be less fragile to inexact alignment.
    int e = (int) log10f (value);  // Truncates the exponent to integer, which is what we want. This will leave as much as one digit above the decimal point.
    uint32_t threshold = 0xFFFFF7;  // Mantissa rounding threshold. 24 bits.
    if      (e > 6) value /= powf (10, e);
    else if (e < 0) value *= powf (10, -e);
    else
    {
        // Mantissa rounding threshold depends on how many significant digits remain after the integer portion is printed.
        // (mantissa * {remaining precision} + (1 << 23)) >> 24 == mantissa
        uint32_t remaining = (uint32_t) powf (10, 6 - e);
        threshold = (((uint64_t) remaining << 24) - (1 << 23)) / remaining;
        e = 0;  // No adjustment
    }
    // At this point, value should not be subnormal, even if it started that way.

    raw = * (uint32_t *) &value;
    uint32_t mantissa = raw & 0x7FFFFF;  // 23 bits
    mantissa |= 0x800000;  // Add implied 1.x (24th bit).
    int exponent = (raw >> 23 & 0xFF) - 126;  // Standard bias is -127, +1 to move our reference point to bit 24. "exponent" is now the power of the bit in position 24.

    // Output integer portion.
    int shift = exponent - 24;
    if (shift >= 0)
    {
        append (p, mantissa << shift);
        // There are no bits below the decimal point.
        return;
    }
    // else negative shift
    uint32_t integer = mantissa >> -shift;
    if (exponent >= 0) mantissa <<=  exponent;  // Effective exponent becomes 0. Equivalently, the lower 24 bits are all fractional.
    else               mantissa >>= -exponent;  // Ditto. "exponent" might be slightly negative due to small errors in decimal alignment above.
    mantissa &= 0xFFFFFF;
    if (mantissa >= threshold)
    {
        integer++;
        mantissa = 0;
    }
    append (p, integer);

    if (mantissa)
    {
        // Output decimal point.
        *p++ = '.';

        // Output fractional portion.
        // This is the core Steel & White algorithm.
        //   B = 10
        //   U is "digit"
        //   R is "mantissa"
        //   k is simply effective position (p) in output buffer
        uint32_t n = -shift;  // number of bits below the decimal point
        if (n > 23) n = 23;
        int M = 1 << 24 - n - 1;  // digit cutoff threshold
        char digit;
        for (int i = 0; i < 6; i++)  // Limit number of digits, regardless of threshold.
        {
            mantissa *= 10;
            digit = mantissa >> 24;
            mantissa &= 0xFFFFFF;
            M *= 10;
            if (mantissa < M  ||  mantissa > (1 << 24) - M)
            {
                if (mantissa >= 0x800000) digit++;  // R >= 0.5
                i = 100;  // Exit loop after this iteration.
            }
            *p++ = '0' + digit;
        }

        // Trim zeroes.
        while (*(p-1) == '0') p--;
    }

    // Output decimal exponent.
    if (e)
    {
        append (p, "e");
        append (p, e);
    }
}

inline void append (char * & p, double value)
{
    append (p, (float) value);
}


// String class --------------------------------------------------------------

/**
    A lightweight drop-in replacement for std::string.
    Avoids STL bloat (locales, exceptions ...) and only deals with single-byte characters.
    This class implements only functions that are actually used by the N2A C runtime engine.
**/
struct String    // Note the initial capital letter. This name will not conflict with std::string.
{
    using size_type = size_t;

    char *    memory;
    char *    top;       // position of null terminator in memory block
    size_type capacity_; // size of currently-allocated memory block

    static const size_type npos    = static_cast<size_type> (-1);
    static const size_type maxSize = 0x1000000;  // 16MiB. This is suitable for most systems.

    String ()
    {
        memory    = 0;
        top       = 0;
        capacity_ = 0;
    }

    String (const char * value)
    {
        memory    = 0;
        top       = 0;
        capacity_ = 0;
        if (! value) return;
        const char * end = (const char *) memchr (value, 0, maxSize);
        assign (value, end ? (size_type) (end - value) : maxSize);
    }

    String (const String & that)
    {
        memory    = 0;
        top       = 0;
        capacity_ = 0;
        assign (that.memory, that.top - that.memory);  // If that is an empty string, then that.memory may be null. assign() handles this case properly.
    }

    String (String && that) noexcept
    {
        memory    = that.memory;
        top       = that.top;
        capacity_ = that.capacity_;
        that.memory    = 0;
        that.top       = 0;
        that.capacity_ = 0;
    }

    /**
        This constructor allows numbers to be passed as string arguments without extra conversion code.
    **/
    template<class T, typename std::enable_if<(std::is_integral<T>::value  ||  std::is_floating_point<T>::value)  &&  sizeof (T) >= 4, bool>::type = true>
    String (T value)
    {
        memory    = 0;
        top       = 0;
        capacity_ = 0;

        char buffer[4 * sizeof (T)];
        char * b = buffer;
        ::append (b, value);
        assign (buffer, b - buffer);
    }

    ~String ()
    {
        delete[] memory;  // Safe, even if "memory" is null.
    }

    String & assign (const char * value, size_type n)
    {
        if (value  &&  n)
        {
            if (n > maxSize) n = maxSize;  // We should really throw an error, but this library supports bare metal so it does not throw exceptions.
            size_type requiredCapacity = n + 1;
            if (requiredCapacity > capacity_)
            {
                delete[] memory;
                capacity_ = requiredCapacity;
                memory = new char[capacity_];
            }
            memcpy (memory, value, n);  // Saves space (size of executable) rather than time.
            top = memory + n;
        }
        else
        {
            top = memory;  // Notice that "memory" could be null here, in which case top is also null.
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
            delete[] memory;
            memory    = that.memory;
            top       = that.top;
            capacity_ = that.capacity_;
            that.memory    = 0;
            that.top       = 0;
            that.capacity_ = 0;
        }
        return *this;
    }

    void clear ()
    {
        if (memory) memory[0] = 0;
        top = memory;
    }

    size_type size () const
    {
        return top - memory;
    }

    bool empty () const
    {
        return top == memory;
    }

    size_type max_size () const
    {
        return maxSize;
    }

    size_type capacity () const
    {
        if (capacity_ <= 0) return 0;
        return capacity_ - 1;
    }

    void reserve (size_type n = 0)
    {
        // We don't defend against n>maxSize here. The caller should exercise care.
        n++;  // Add one byte for null termination.
        if (n <= capacity_) return;
        char * temp = memory;
        char * end  = top;
        memory    = new char[n];
        top       = memory + (end - temp);
        capacity_ = n;
        if (! temp) return;
        char * m = memory;
        char * a = temp;
        while (a <= end) *m++ = *a++;  // Copies both string and null terminator.
        delete[] temp;
    }

    void resize (size_type n, char c = 0)
    {
        // We don't defend against n>maxSize here. The caller should exercise care.
        size_type length = top - memory;
        reserve (n);
        top = memory + n;
        memory[n] = 0;
        char * m = memory + length;
        while (m < top) *m++ = c;
    }

    const char * c_str () const
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

    bool operator!= (const String & that) const
    {
        return compare (that) != 0;
    }

    bool operator< (const String & that) const
    {
        return compare (that) < 0;
    }

    bool operator<= (const String & that) const
    {
        return compare (that) <= 0;
    }

    bool operator> (const String & that) const
    {
        return compare (that) > 0;
    }

    bool operator>= (const String & that) const
    {
        return compare (that) >= 0;
    }

    const char & operator[] (size_type pos) const
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
        result.capacity_ = length + 1;
        result.memory = new char[result.capacity_];
        combine (memory, that.memory, result.memory);
        result.top = result.memory + length;
        return result;
    }

    String operator+ (const char * that) const
    {
        String result;
        int length = top - memory;
        if (that)
        {
            const char * end = (const char *) memchr (that, 0, maxSize);
            length += end ? (size_type) (end - that) : maxSize;
            if (length > maxSize) length = maxSize;
        }
        result.capacity_ = length + 1;
        result.memory = new char[result.capacity_];
        combine (memory, that, result.memory);
        result.top = result.memory + length;
        return result;
    }

    template<class T, typename std::enable_if<(std::is_integral<T>::value  ||  std::is_floating_point<T>::value)  &&  sizeof (T) >= 4, bool>::type = true>
    String operator+ (T that) const
    {
        char buffer[4 * sizeof (T)];
        char * b = buffer;
        ::append (b, that);
        *b = 0;
        return operator+ (buffer);
    }

    String & append (const char * that, size_type n)
    {
        size_type length = (top - memory) + n;
        if (! length) return *this;
        if (length > maxSize) length = maxSize;
        size_type requiredCapacity = length + 1;
        if (requiredCapacity > capacity_)
        {
            char * temp = memory;
            capacity_ = requiredCapacity;
            memory = new char[capacity_];

            char * m = memory;
            char * a = temp;
            if (a) while (*a) {*m++ = *a++;}
            const char * b   = that;
            const char * end = b + n;
            if (b) while (b < end) {*m++ = *b++;}

            top = memory + length;
            *top = 0;
            delete[] temp;
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
        if (! that) return *this;
        const char * end = (const char *) memchr (that, 0, maxSize);
        return append (that, end ? (size_type) (end - that) : maxSize);
    }

    String & operator+= (char that)
    {
        return append (&that, 1);
    }

    template<class T, typename std::enable_if<(std::is_integral<T>::value  ||  std::is_floating_point<T>::value)  &&  sizeof (T) >= 4, bool>::type = true>
    String & operator+= (T that)
    {
        char buffer[4 * sizeof (T)];
        char * b = buffer;
        ::append (b, that);
        return append (buffer, b - buffer);
    }

    String substr (size_type pos, size_type length = npos) const noexcept
    {
        String result;
        int available = (top - memory) - pos;
        if (available <= 0) return result;
        if (length > available) length = available;   // Assumes that npos works out to a large positive number.
        result.assign (memory + pos, length);
        return result;
    }

    size_type find (const char * pattern, size_type pos, size_type n) const
    {
        int available = top - memory;
        if (n == 0) return pos <= available ? pos : npos;  // The empty pattern will always match exactly where it's at, unless it's off the end of the target string.
        char * start = memory + pos;
        char * last  = top - n;  // the last place we could start scanning
        while (start <= last)
        {
            char *       i   = start;
            const char * j   = pattern;
            const char * end = pattern + n;
            while (j < end  &&  *i == *j) {i++; j++;}
            if (j >= end) return start - memory;
            start++;
        }
        return npos;
    }

    size_type find (const String & pattern, size_type pos = 0) const noexcept
    {
        return find (pattern.memory, pos, pattern.top - pattern.memory);
    }

    size_type find_first_of (const char * pattern, size_type pos = 0) const
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

    size_type find_first_of (char pattern, size_type pos = 0) const noexcept
    {
        if (memory == top  ||  ! pattern) return npos;
        char * c = memory + pos;
        while (c < top)
        {
            if (*c == pattern) return c - memory;
            c++;
        }
        return npos;
    }

    size_type find_first_not_of (const char * pattern, size_type pos = 0) const
    {
        if (memory == top  ||  ! pattern) return npos;
        char * c = memory + pos;
        while (c < top)
        {
            bool found = false;
            const char * p = pattern;
            while (*p)
            {
                if (*p == *c)
                {
                    found = true;
                    break;
                }
                p++;
            }
            if (! found) return c - memory;
            c++;
        }
        return npos;
    }

    size_type find_first_not_of (char pattern, size_type pos = 0) const noexcept
    {
        if (memory == top  ||  ! pattern) return npos;
        char * c = memory + pos;
        while (c < top)
        {
            if (*c != pattern) return c - memory;
            c++;
        }
        return npos;
    }

    size_type find_last_of (const char * pattern, size_type pos = npos) const
    {
        if (memory == top  ||  ! pattern) return npos;
        char * c = memory + pos;
        if (pos == npos  ||  c >= top) c = top - 1;  // Explicitly check for npos, because it might not be suitable for adding to a pointer.
        while (c >= memory)
        {
            const char * p = pattern;
            while (*p)
            {
                if (*p == *c) return c - memory;
                p++;
            }
            c--;
        }
        return npos;
    }

    size_type find_last_of (char pattern, size_type pos = npos) const
    {
        if (memory == top  ||  ! pattern) return npos;
        char * c = memory + pos;
        if (pos == npos  ||  c >= top) c = top - 1;
        while (c >= memory)
        {
            if (*c == pattern) return c - memory;
            c--;
        }
        return npos;
    }

    bool starts_with (const String & that) const
    {
        int lengthA =      top -      memory;
        int lengthB = that.top - that.memory;
        if (lengthB > lengthA) return false;
        if (memory == 0) return true;  // both are empty

        char * a   = memory;
        char * b   = that.memory;
        char * end = memory + lengthB;
        while (a < end) if (*a++ != *b++) return false;
        return true;
    }

    bool ends_with (const String & that) const
    {
        int lengthA =      top -      memory;
        int lengthB = that.top - that.memory;
        if (lengthB > lengthA) return false;
        if (memory == 0) return true;  // both are empty

        char * end = top;
        char * a   = end - lengthB;
        char * b   = that.memory;
        while (a < end) if (*a++ != *b++) return false;
        return true;
    }

    bool contains (const String & that) const
    {
        return find (that) != npos;
    }

    bool contains (char that) const
    {
        return find_first_of (that) != npos;
    }

    const char * begin () const
    {
        return memory;
    }

    const char * end () const
    {
        return top;
    }

    // Non-standard functions ------------------------------------------------

    /**
        Remove leading and trailing white space.
        Unlike standard member functions, this mutates the object (modification in place).
        The reason is that in practice the untrimmed string is almost never used afterward.
    **/
    String & trim ()
    {
        if (top == memory) return *this;
        char * first = memory;
        while (first < top  &&  (*first == ' '  ||  *first == '\t'  ||  *first == '\r'  ||  *first == '\n')) first++;
        char * last = top - 1;
        while (last >= memory  &&  (*last == ' '  ||  *last == '\t'  ||  *last == '\r'  ||  *last == '\n')) last--;
        if (first > memory)  // Move trimmed sting down to start of memory block ...
        {
            char * i = first;
            char * j = memory;
            while (i <= last) *j++ = *i++;
            top = j;  // ... and adjust end marker.
        }
        else  // Only adjust end marker.
        {
            top = last + 1;
        }
        *top = 0;
        return *this;
    }

    /**
        Replace all occurrences of a with b.
        Unlike standard member functions, this mutates the object (modification in place).
    **/
    String & replace_all (char a, char b)
    {
        char * i = memory;
        while (i < top)
        {
            if (*i == a) *i = b;
            i++;
        }
        return *this;
    }

    /**
        Replace all occurrences of a with b.
    **/
    String replace_all (const String & a, const String & b)
    {
        String result;
        size_t count = size ();  // Of source string (ourself).
        size_t countA = a.size ();  // To be completely defensive, we should guard against countA==0. But nobody will ever pass that under normal circumstances.
        size_t countB = b.size ();
        if (countA >= countB) result.reserve (count);
        else                  result.reserve (count + countB - countA);  // This formula assumes just one replacement. It's not worth counting replacements ahead of time.
        size_t i = 0;  // Current position in source string (ourself).
        while (i < count)
        {
            size_t next = find (a, i);
            if (next == npos)
            {
                result.append (memory + i, count - i);
                break;
            }
            result.append (memory + i, next - i);
            result.append (b.memory, countB);
            i = next + countA;
        }
        return result;
    }

    /**
        Returns a new string where all characters are lower-case versions of this string.
        Used for case-insensitive comparison.
    **/
    String toLowerCase () const
    {
        String result;
        int length = top - memory;
        if (length <= 0) return result;

        result.capacity_ = length + 1;
        result.memory    = new char[result.capacity_];
        result.top       = result.memory + length;

        char * from = memory;
        char * to   = result.memory;
        while (from < top)
        {
            char temp = *from++;
            if (temp >= 65  &&  temp <= 90) temp |= 0x20;  // set bit 5
            *to++ = temp;
        }
        *result.top = 0;

        return result;
    }

    /**
        Returns a new string where all characters are upper-case versions of this string.
        Used for case-insensitive comparison.
    **/
    String toUpperCase () const
    {
        String result;
        int length = top - memory;
        if (length <= 0) return result;

        result.capacity_ = length + 1;
        result.memory    = new char[result.capacity_];
        result.top       = result.memory + length;

        char * from = memory;
        char * to   = result.memory;
        while (from < top)
        {
            char temp = *from++;
            if (temp >= 97  &&  temp <= 122) temp &= 0xDF;  // clear bit 5
            *to++ = temp;
        }
        *result.top = 0;

        return result;
    }
};


// Global operations on String -----------------------------------------------

#ifndef N2A_SPINNAKER   // This only covers IO functions. There are other functions below that still get defined.

inline std::ostream & operator<< (std::ostream & out, const String & value)
{
    if (value.memory) out << value.memory;
    return out;
}

inline std::istream & getline (std::istream & in, String & result, char delimiter = '\n')
{
    result.clear ();
    const int limit = String::maxSize;
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
    else if (c < 0) in.setstate (std::istream::eofbit);
    int remaining = b - buffer;
    if (remaining) result.append (buffer, remaining);
    return in;
}

#endif

inline void split (const String & source, const String & delimiter, String & first, String & second)
{
    int index = source.find (delimiter);
    if (index == String::npos)
    {
        first = source;
        second.clear ();
    }
    else
    {
        String temp = source;  // Make a copy of source, in case source is also one of the destination strings.
        first = temp.substr (0, index);
        second = temp.substr (index + delimiter.size ());
    }
}

inline std::vector<String> split (const String & source, const String & delimiter)
{
    std::vector<String> result;
    int lengthSource    = source.size ();
    int lengthDelimiter = delimiter.size ();
    int index           = 0;
    while (index < lengthSource)
    {
        int next = source.find (delimiter, index);
        if (next == String::npos) next = lengthSource;
        result.push_back (source.substr (index, next-index));
        index = next + lengthDelimiter;
    }
    return result;
}

inline String join (const String & delimiter, const std::vector<String> & elements)
{
    int count = elements.size ();
    if (count == 0) return "";
    int total = (count - 1) * delimiter.size ();
    for (auto & e : elements) total += e.size ();
    String result;
    result.reserve (total);
    result = elements[0];
    for (int i = 1; i < count; i++)
    {
        result += delimiter;
        result += elements[i];
    }
    return result;
}

// Oddly, we don't get an automatic conversion from char* to String when char* is on the left,
// so we need this explicit operator.
inline String operator+ (const char * A, const String & B)
{
    return String (A) + B;
}

inline bool operator== (const char * A, const String & B)
{
    return B.compare (A) == 0;
}

inline bool operator!= (const char * A, const String & B)
{
    return B.compare (A) != 0;
}

inline bool operator< (const char * A, const String & B)
{
    return B.compare (A) > 0;
}

inline bool operator<= (const char * A, const String & B)
{
    return B.compare (A) >= 0;
}

inline bool operator> (const char * A, const String & B)
{
    return B.compare (A) < 0;
}

inline bool operator>= (const char * A, const String & B)
{
    return B.compare (A) <= 0;
}

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
                while (int c = *m++) result = (result << 5) + result + c;  // effectively: result * 33 + c
            }
            return result;
        }
    };
}

#endif
