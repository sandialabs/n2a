#include "fixedpoint.h"


using namespace fl;


static const int MSB   = 30;
static const int MSB2  = MSB / 2;
static const int LOG2E = 1549082004;  // log_2(e) = 1.4426950408889634074; exponent=0
static const int FP_E  = 1459366444;  // the constant e, with exponent=1 (that is, using all 31 bits in word)

inline int
multiplyRound (int a, int b, int shift)
{
    int64_t temp = (int64_t) a * b;
    if (shift < 0) return (temp + (1 << -shift - 1)) >> -shift;
    if (shift > 0) return  temp                      <<  shift;
    return temp;
}

int
cos (int a)
{
}

int
exp (int a, int exponentA, int exponentResult)
{
    if (a == 0) return 1 << MSB - exponentResult;
    int one = 1 << MSB - exponentA;
    if (a == one)
    {
        int shift = 1 - exponentResult;
        if (shift < 0) return FP_E >> -shift;
        return FP_E;  // A positive (nonzero) shift would return nonsense, so we don't bother checking for it.
    }
    if (exponentResult > MSB) return 0x7FFFFFFF;  // positive infinity; Because
    // TODO: check for overflow and underflow

    // Algorithm:
    // exp(a) = sum_0^inf (a^k / k!)
    // term_n = term_(n-1) * (a/n)
    // Stop when term loses significance.
    // exp(-a) = 1/exp(a), but positive terms converge faster

    bool negate = a < 0;
    if (negate) a = -a;

    int result = 1 << MSB - exponentResult;   // zeroth term
    int shift = exponentA - exponentResult;
    if      (shift > 0) a <<=  shift;
    else if (shift < 0) a >>= -shift;
    result += a;  // first term

    // Pre-compute shift for inner loop.
    // Loop variable i has exponent=MSB
    // Everything else has exponentResult
    // raw multiply = 2*exponentResult at bit 60
    // raw divide = 2*exponentResult-MSB at bit 30
    // We want exponentResult at bit 30, so shift = (2*exponentResult-MSB)-exponentResult = exponentResult-MSB
    shift = MSB - exponentResult;  // preemptively negate, since we must always down-shift
    int64_t round = 0;
    if (shift > 0) round = (int64_t) 1 << shift - 1;

    int term = a;
    for (int i = 2; i < 30; i++)
    {
        term = (int64_t) term * a / i + round >> shift;
        if (term == 0) break;
        result += term;
        if (result < 0) return 0x7FFFFFFF;  // overflow; This is a weak check, in that it can miss some cases, but better than nothing.
    }

    // Determine shift for inversion associated with negative a
    // Let 1 be pure integer, so exponent=MSB
    // raw result of inversion = MSB-exponentResult+MSB = 2*MSB-exponentResult at bit 30
    // We want exponentResult, so shift = (2*MSB-exponentResult)-exponentResult = 2*(MSB-exponentResult)
    if (negate) return ((int64_t) 1 << 2 * (MSB - exponentResult)) / result;

    return result;
}

int
log (int a, int exponentA, int exponentResult)
{
    // We use the simple identity log_e(a) = log_2(a) / log_2(e)
    // exponentRaw = exponentResult - 0 + MSB
    // shift = exponentRaw - exponentResult = MSB
    return ((int64_t) log2 (a, exponentA, exponentResult) << MSB) / LOG2E;
}

int
log2 (int a, int exponentA, int exponentResult)
{
    if (a <  0) return 0x80000000;  // nan
    if (a == 0) return 0x80000001;  // -infinity

    // If a<1, then the result is -log2(1/a)
    bool negate = false;
    if (exponentA < 0  ||  (exponentA < MSB  &&  a < 1 << MSB - exponentA))
    {
        negate = true;

        // The intention of this code is that only about half the word is occupied
        // by significant bits. That way, it will still have half a word worth of
        // bits when inverted.
        while (a & 0x7FFF0000)
        {
            a >>= 1;
            exponentA++;
        }

        // compute 1/a
        // Let the numerator 1 have center power of 0, with center at MSB/2, for exponent=MSB/2
        // Center of a is presumably at MSB/2
        // Center of inverse should be at MSB/2
        // Center power of a is exponentA-MSB/2
        // Center power of inverse is 0-(exponentA-MSB/2) = MSB/2-exponentA
        // Exponent of inverse = center power + MSB/2 = MSB-exponentA
        // Exponent of unshifted division = exponentRaw = MSB/2-exponentA+MSB = 3*MSB/2-exponentA
        // Needed shift for exponent of inverse = exponentRaw - (MSB-exponentA) = MSB/2
        // If shifting 1 up to necessary position: (1 << MSB/2) << MSB/2 = 1 << MSB
        a = (1 << MSB) / a;
        exponentA = MSB - exponentA;
    }

    // At this point a >= 1
    // Using the identity log(ab)=log(a)+log(b), we put a into normal form:
    //   operand = a*2^exponentA
    //   log2(operand) = log2(a)+log2(2^exponentA) = log2(a)+exponentA
    int exponentWork = 15;
    int one = 1 << MSB - exponentWork;
    while (a < one)
    {
        one >>= 1;
        exponentWork++;
    }
    int result = exponentA - exponentWork;  // Represented as a pure integer, with exponent=MSB
    int two = 2 * one;
    while (a >= two)  // This could also be done with a bit mask that checks for any bits in the twos position or higher.
    {
        result++;
        a = (a >> 1) + (a & 1);  // divide-by-2 with rounding
    }

    // TODO: Guard against large shifts.
    int shift = MSB - exponentResult;
    if (a > one)  // Otherwise a==one, in which case the following algorithm will do nothing.
    {
        while (shift > 0)
        {
            a = multiplyRound (a, a, exponentWork - MSB);  // exponentRaw - exponentWork = (2*exponentWork-MSB) - exponentWork
            result <<= 1;
            shift--;
            if (a >= two)
            {
                result |= 1;
                a = (a >> 1) + (a & 1);
            }
        }
        a = multiplyRound (a, a, exponentWork - MSB);
        if (a >= two) result++;
    }

    if      (shift > 0) result <<=  shift;
    else if (shift < 0) result >>= -shift;
    if (negate) return -result;
    return result;
}

int
mod (int a, int b, int exponentA, int exponentB, int exponentResult)
{
}

int
pow (int a, int b, int exponentA, int exponentB, int exponentResult)
{
}

int
sqrt (int a, int exponentA, int exponentResult)
{
    if (a < 0) return 0x80000000;  // nan

    // Simple approach: apply the identity a^0.5=e^(ln(a^0.5))=e^(0.5*ln(a))
    //int l = log (a, exponentA, MSB/2) >> 1;
    //return exp (l, MSB/2, exponentResult);

    // More efficient approach: Use digit-by-digit method described in
    // https://en.wikipedia.org/wiki/Methods_of_computing_square_roots
    // To handle exponentA, notice that sqrt(m*2^n) = 2^(n/2)*sqrt(m)
    // If n is even, this is sqrt(m) << n/2
    // If n is not even, we can leave remainder inside: sqrt(2m) << (n-1)/2
    // If n is negative and uneven, this becomes:
    //   sqrt(m/2)  >> -(n+1)/2
    //   sqrt(2m/4) >> -(n+1)/2
    //   sqrt(2m)/2 >> -(n+1)/2
    //   sqrt(2m)   >> -(n+1)/2 + 1
    //   sqrt(2m)   >> -(n-1)/2

    uint32_t m = a;  // "m" for mantissa
    int exponent0 = exponentA - MSB;  // exponent at bit position 0
    if (exponent0 % 2)  // Odd, so leave remainder inside sqrt()
    {
        m <<= 1;  // equivalent to "2m" in the comments above
        exponent0--;
    }
    int exponentRaw = exponent0 / 2 + MSB;  // exponent of raw result at MSB

    uint32_t bit;
    if (m & 0xFFFF0000) bit = 1 << 30;
    else                bit = 1 << 16;  // For efficiency, don't scan upper bits if they're empty.
    while (bit > m) bit >>= 2;  // Locate starting position, at or below msb of m.

    uint32_t result = 0;
    while (bit)
    {
        uint32_t temp = result + bit;
        result >>= 1;
        if (m >= temp)
        {
            m -= temp;
            result += bit;
        }
        bit >>= 2;
    }

    // At this point, the exponent of the raw result is same as exponentA.
    // If the requested exponent of the result requires it, compute more precision.
    int shift = exponentRaw - exponentResult;
    while (shift > 0)
    {
        m <<= 2;
        result <<= 1;
        shift--;
        uint32_t temp = (result << 1) + 1;
        if (m >= temp)
        {
            m -= temp;
            result++;
        }
    }
    if (shift < 0) result >>= -shift;
    return result;
}

int
sin (int a)
{
}

int
tan (int a, int exponentA, int exponentResult)
{
}

MatrixResult<int>
shift (int shift, Matrix<int> A)
{
}

int
norm (int n, Matrix<int> A, int exponentA, int exponentResult)
{
}

MatrixResult<int>
multiply (Matrix<int> A, Matrix<int> B, int shift)
{
}

MatrixResult<int>
multiply (Matrix<int> A, int b, int shift)
{
}

MatrixResult<int>
multiply (int a, Matrix<int> B, int shift)
{
}

MatrixResult<int>
multiplyElementwise (Matrix<int> A, Matrix<int> B, int shift)
{
}

MatrixResult<int>
divide (Matrix<int> A, Matrix<int> B, int shift)
{
}

MatrixResult<int>
divide (Matrix<int> A, int b, int shift)
{
}

MatrixResult<int>
divide (int a, Matrix<int> B, int shift)
{
}


#endif
