#include "fixedpoint.h"


inline int
multiplyRound (int a, int b, int shift)
{
    int64_t temp = (int64_t) a * b;
    if (shift < 0) return (temp + (1 << -shift - 1)) >> -shift;
    if (shift > 0) return  temp                      <<  shift;
    return temp;
}

int
cos (int a, int exponentA)
{
    // We want to add PI/2 to a. FP_PI exponent=1. To induce down-shift, claim it is 0.
    // Thus, shift is exactly exponentA.
    if (exponentA >= 0) return (a + (FP_PI >> exponentA), exponentA);
    // If exponentA is negative, then a is too small to use as-is.
    if (exponentA < -FP_MSB) return 0x20000000;  // one, with exponent=1
    return sin ((a >> -exponentA) + FP_PI, 0);
}

int
exp (int a, int exponentResult)
{
    const int exponentA = 7;  // Hard-coded value established in gov.sandia.n2a.language.function.Exp class.

    if (a == 0)
    {
        int shift = FP_MSB - exponentResult;
        if (shift < 0) return 0;
        return 1 << shift;
    }
    const int one = 1 << FP_MSB - exponentA;
    if (a == one)
    {
        int shift = 1 - exponentResult;  // FP_E exponent=1
        if (shift < 0) return FP_E >> -shift;
        if (shift > 0) return INFINITY;  // Up-shifting FP_E is nonsense, since it already uses all the bits.
        return FP_E;
    }

    // Algorithm:
    // exp(a) = sum_0^inf (a^k / k!)
    // term_n = term_(n-1) * (a/n)
    // Stop when term loses significance.
    // exp(-a) = 1/exp(a), but positive terms converge faster

    bool negate = a < 0;
    if (negate) a = -a;

    uint32_t result = one + a;  // zeroth and first term
    int exponentWork = exponentA;

    // Shift for inner loop:
    // i has exponent=MSB
    // a has exponent=7 per above comment
    // term and result have exponentWork
    // raw multiply = exponentA+exponentWork at bit 60
    // raw divide = (exponentA+exponentWork)-MSB at bit 30
    // We want exponentWork at bit 30, so shift = raw-exponentWork = (exponentA+exponentWork-MSB)-exponentWork = exponentA-MSB
    const int shift = FP_MSB - exponentA;  // preemtively flip the sign, since this is always used in the positive form
    const int round = 1 << shift - 1;
    const int maximum = 1 << FP_MSB;

    uint32_t term = a;
    for (int i = 2; i < 30; i++)
    {
        uint64_t temp = (uint64_t) term * a / i + round >> shift;
        if (temp == 0) break;
        while (temp >= maximum  ||  result >= maximum)  // Potential overflow, so down-shift
        {
            temp >>= 1;
            result++;  // rounding
            result >>= 1;
            exponentWork++;
        }
        term = temp;
        result += term;
    }

    if (negate)
    {
        // Let 1 have exponent=0 at bit 60 (2*MSB)
        // raw result of inversion = 0-exponentWork at bit 30
        uint64_t temp = ((uint64_t) 1 << 2 * FP_MSB) / result;
        int shift = -exponentWork - exponentResult;
        if (shift < 0)
        {
            if (shift < -60) return 0;  // Prevent weird effects from modulo arithmetic on size of shift.
            return temp >> -shift;
        }
        if (shift > 0)
        {
            if (shift > FP_MSB) return INFINITY;
            temp <<= shift;
            if (temp > INFINITY) return INFINITY;
            return temp;
        }
        return temp;
    }
    else
    {
        int shift = exponentWork - exponentResult;
        if (shift < 0)
        {
            if (shift < -FP_MSB) return 0;
            return result >> -shift;
        }
        if (shift > 0)
        {
            if (shift > FP_MSB) return INFINITY;
            // Don't bother trapping overflow with 32-bit math. Our fixed-point analysis should keep numbers in range in any case.
            return result <<= shift;
        }
        return result;
    }
}

int
log (int a, int exponentA, int exponentResult)
{
    // We use the simple identity log_e(a) = log_2(a) / log_2(e)
    // exponentRaw = exponentResult - 0 + MSB
    // shift = exponentRaw - exponentResult = MSB
    return ((int64_t) log2 (a, exponentA, exponentResult) << FP_MSB) / FP_LOG2E;
}

int
log2 (int a, int exponentA, int exponentResult)
{
    if (a <  0) return NAN;
    if (a == 0) return -INFINITY;

    // If a<1, then the result is -log2(1/a)
    bool negate = false;
    if (exponentA < 0  ||  (exponentA < FP_MSB  &&  a < 1 << FP_MSB - exponentA))
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
        a = (1 << FP_MSB) / a;
        exponentA = FP_MSB - exponentA;
    }

    // At this point a >= 1
    // Using the identity log(ab)=log(a)+log(b), we put a into normal form:
    //   operand = a*2^exponentA
    //   log2(operand) = log2(a)+log2(2^exponentA) = log2(a)+exponentA
    int exponentWork = 15;
    int one = 1 << FP_MSB - exponentWork;
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
    int shift = FP_MSB - exponentResult;
    if (a > one)  // Otherwise a==one, in which case the following algorithm will do nothing.
    {
        while (shift > 0)
        {
            a = multiplyRound (a, a, exponentWork - FP_MSB);  // exponentRaw - exponentWork = (2*exponentWork-MSB) - exponentWork
            result <<= 1;
            shift--;
            if (a >= two)
            {
                result |= 1;
                a = (a >> 1) + (a & 1);
            }
        }
        a = multiplyRound (a, a, exponentWork - FP_MSB);
        if (a >= two) result++;
    }

    if      (shift > 0) result <<=  shift;
    else if (shift < 0) result >>= -shift;
    if (negate) return -result;
    return result;
}

int
modFloor (int a, int b, int exponentA, int exponentB)
{
    if (a == 0) return 0;
    if (b == 0) return NAN;

    // All computations are positive, and remainder is always positive.
    bool negateA = false;
    bool negateB = false;
    if (a < 0)
    {
        a = -a;
        negateA = true;
    }
    if (b < 0)
    {
        b = -b;
        negateB = true;
    }

    // Strategy: Align a and b to have the same exponent, then use integer modulo (%).
    while (exponentB > exponentA  &&  (b & 0x40000000) == 0)
    {
        b <<= 1;
        exponentB--;
    }
    if (exponentB <= exponentA)  // If not, then b is strictly greater than a, and a is the answer.
    {
        if (b == a) return 0;  // Regardless of exponent, b will divide evenly into a, so remainder will be 0.
        while (true)
        {
            while (exponentA > exponentB  &&  (a & 0x40000000) == 0)
            {
                a <<= 1;
                exponentA--;
            }
            if (exponentA == exponentB)
            {
                if (a > b) a %= b;
                break;
            }
            // At this point, both numbers have been up-shifted so they have a 1 in MSB.

            // Partial remainder
            if (b < a)
            {
                a -= b;
            }
            else  // b > a
            {
                a = ((uint32_t) a << 1) - (uint32_t) b;
                exponentA--;  // To adjust for up-shift.
            }
        }
    }
    if (negateA) a = b - a;
    if (negateB) a = a - b;
    return a;
}

int
norm (const MatrixStrided<int> & A, int n, int exponentA, int exponentResult)
{
    const int exponentN = 15;

    int * a   = A.base ();
    int * end = a + A.rows () * A.columns ();  // Assumes MatrixFixed, so that elements are dense in memory.
    int result = 0;

    if (n == INFINITY)
    {
        while (a < end) result = std::max (std::abs (*a++), result);
        int shift = exponentA - exponentResult;
        if (shift > 0) return result <<  shift;
        if (shift < 0) return result >> -shift;
        return result;
    }
    if (n == 0)
    {
        while (a < end) if (*a++) result++;
        int shift = FP_MSB - exponentResult;
        if (shift > 0) return result <<  shift;
        if (shift < 0) return result >> -shift;
        return result;
    }
    if (n == 0x1 << exponentN)
    {
        while (a < end) result += std::abs (*a++);
        int shift = exponentA - exponentResult;
        if (shift > 0) return result <<  shift;
        if (shift < 0) return result >> -shift;
        return result;
    }

    // Fully general form
    // "result" will hold the sum, and exponentA will hold exponentSum when done.
    int root;  // exponent=15
    if (n == 0x2 << exponentN)
    {
        root = 0x4000;  // 0.5
        exponentA = exponentA * 2 - FP_MSB;  // raw result of squaring elements of A
        register uint64_t sum = 0;
        while (a < end)
        {
            int t = *a++;
            sum += (int64_t) t * t;
        }
        while (sum > INFINITY)
        {
            sum >>= 1;
            exponentA++;
        }
        result = sum;  // truncate to 32 bits
    }
    else
    {
        // for root:
        // raw division = exponentOne-exponentN+MSB = MSB-MSB/2+MSB
        // want exponentN, so shift = raw-exponentN = (MSB-MSB/2+MSB)-MSB/2 = MSB
        root = (0x1 << FP_MSB) / n;

        // for exponentSum:
        // assume center of A = MSB/2
        // center power of A = centerA = exponentA - MSB/2
        // center power of one term = centerTerm = centerA*n
        // want center of term at MSB/2, so exponentSum = centerTerm+MSB/2 = (exponentA-MSB/2)*n+MSB/2 = exponentA*n+(1-n)*MSB/2
        int exponentSum = ((exponentA - FP_MSB2) * n >> exponentN) + FP_MSB2;

        while (a < end) result += pow (std::abs (*a++), n, exponentA, exponentSum);
        exponentA = exponentSum;
    }
    return pow (result, root, exponentA, exponentResult);
}

int
pow (int a, int b, int exponentA, int exponentResult)
{
    // exponentB = 15

    // Use the identity: a^b = e^(b*ln(a))
    // Most of the complexity of this function is in trapping special cases.
    // For details, see man page for floating-point pow().
    // We don't have signed zero, so ignore all distinctions based on that.
    bool negate = false;
    int blna = 1;  // exponent=7, as required by exp(); Nonzero indicates that blna needs to be calculated.
    int shift = FP_MSB - exponentA;
    int one;
    if (shift < 0) one = 0;
    else           one = 1 << shift;
    if (a == one  ||  b == 0)
    {
        blna = 0;  // Zero indicates that we want to return 1, scaled according to exponentA.
    }
    else
    {
        if (a == NAN  ||  b == NAN) return NAN;
        if (a == 0)
        {
            if (b > 0) return 0;
            return INFINITY;
        }
        if (a == INFINITY  ||  a == -INFINITY)
        {
            if (b < 0) return 0;
            if (a < 0  &&  ! (b & 0x7FFF)  &&  b & 0x8000) return -INFINITY;  // negative infinity to the power of an odd integer
            return INFINITY;
        }
        if (b == INFINITY  ||  b == -INFINITY)
        {
            int absa = a > 0 ? a : -a;
            if (absa > one)
            {
                if (b > 0) return INFINITY;
                return 0;
            }
            else if (absa < one)
            {
                if (b > 0) return 0;
                return INFINITY;
            }
            else
            {
                blna = 0;
            }
        }
        else if (a < 0)
        {
            // Check for integer
            if ((b & 0x7FFF) == 0)  // integer
            {
                a = -a;
                negate = b & 0x8000;  // odd integer
            }
            else  // non-integer
            {
                return NAN;
            }
        }

        if (blna)
        {
            // raw multiply = exponentB+7-MSB at bit 30
            // shift = (exponentB+7-MSB)-7 = exponentB-MSB = -15
            int64_t temp = (int64_t) b * log (a, exponentA, 7) >> 15;
            if (temp >  INFINITY) return INFINITY;
            if (temp < -INFINITY) return 0;
            blna = temp;
        }
    }
    int result = exp (blna, exponentResult);
    if (negate) return -result;
    return result;
}

int
sqrt (int a, int exponentA, int exponentResult)
{
    if (a < 0) return NAN;

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
    int exponent0 = exponentA - FP_MSB;  // exponent at bit position 0
    if (exponent0 % 2)  // Odd, so leave remainder inside sqrt()
    {
        m <<= 1;  // equivalent to "2m" in the comments above
        exponent0--;
    }
    int exponentRaw = exponent0 / 2 + FP_MSB;  // exponent of raw result at MSB

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
sin (int a, int exponentA)
{
    // Limit a to [0,pi/2)
    // To create 2PI, we lie about the exponent of FP_PI, increasing it by 1.
    a = mod (a, FP_PI, exponentA, 2);  // exponent = min (exponentA, 2)
    int shift = exponentA - 2;
    if (shift < 0) a >>= -shift;
    const int PIat2 = FP_PI >> 1;  // FP_PI with exponent=2 rather than 1
    bool negate = false;
    if (a > PIat2)
    {
        a -= PIat2;
        negate = true;
    }
    if (a > PIat2 >> 1) a = PIat2 - a;
    a <<= 1;  // Now exponent=1, which matches our promised exponentResult.

    // Use power-series to compute sine, similar to exp()
    // sine(a) = sum_0^inf (-1)^n * x^(2n-1) / (2n+1)! = x - x^3/3! + x^5/5! - x^7/7! ...

    int term = a;
    int result = a;  // zeroth term
    int n1 = 0;  // exponent=MSB
    int n2 = 1;
    for (int n = 1; n < 7; n++)
    {
        n1 = n2 + 1;
        n2 = n1 + 1;
        // raw exponent of operations below, in evaluation order:
        // 2*exponentResult at bit 60
        // 2*exponentResult-MSB at bit 30
        // shift = (2*exponenetResult-MSB)-exponentResult = exponentResult-MSB = -29
        // 2*exponentResult at bit 60
        // 2*exponentResult-MSB at bit 30
        // same shift again
        term = ((int64_t) -term * a / n1 >> 29) * a / n2 >> 29;
        if (term == 0) break;
        result += term;
    }
    if (negate) return -result;
    return result;
}

int
tan (int a, int exponentA, int exponentResult)
{
    // There is a power-series expansion for tan() which would be more efficient.
    // See http://mathworld.wolfram.com/MaclaurinSeries.html
    // However, to save space we simply compute sin()/cos().
    // raw division exponent=0 at bit 0
    return ((int64_t) sin (a, exponentA) << exponentResult) / cos (a, exponentA);  // Don't do any saturation checks. We are not really interested in infinity.
}
