/*
Copyright 2018-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#include "math.h"
#include "Matrix.tcc"


Matrix<int>
shift (const MatrixAbstract<int> & A, int shift)
{
    if (shift > 0) return A * (0x1 << shift);
    if (shift < 0) return A / (0x1 << -shift);
    return A;
}

Matrix<int>
visit (const MatrixStrided<int> & A, int (*function) (int, int), int exponent1)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    Matrix<int> result (h, w);
    int step = sc - h * sr;
    int * r    = result.base ();
    int * a    = A.base ();
    int * end  = a + sc * w;
    while (a != end)
    {
        int * columnEnd = a + h * sr;
        while (a != columnEnd)
        {
            *r++ = (*function) (*a, exponent1);
            a += sr;
        }
        a += step;
    }

    return result;
}

Matrix<int>
visit (const MatrixStrided<int> & A, int (*function) (int, int, int), int exponent1, int exponent2)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    Matrix<int> result (h, w);
    int step = sc - h * sr;
    int * r    = result.base ();
    int * a    = A.base ();
    int * end  = a + sc * w;
    while (a != end)
    {
        int * columnEnd = a + h * sr;
        while (a != columnEnd)
        {
            *r++ = (*function) (*a, exponent1, exponent2);
            a += sr;
        }
        a += step;
    }

    return result;
}

Matrix<int>
multiplyElementwise (const MatrixStrided<int> & A, const MatrixStrided<int> & B, int shift)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    int bh  = B.rows ();
    int bw  = B.columns ();
    int bsc = B.strideC ();
    int bsr = B.strideR ();

    Matrix<int> result (h, w);
    int oh = std::min (h, bh);
    int ow = std::min (w, bw);
    int stepA = sc  - h  * sr;
    int stepB = bsc - oh * bsr;
    int * a   = A.base ();
    int * b   = B.base ();
    int * r   = result.base ();
    int * end = r + h * ow;
    while (r < end)
    {
        int * overlapEnd = r + oh;
        int * columnEnd  = r + h;
        while (r < overlapEnd)
        {
            *r++ = (int64_t) *a * *b >> shift;
            a += sr;
            b += bsr;
        }
        while (r < columnEnd)
        {
            *r++ = 0;
            a += sr;
        }
        a += stepA;
        b += stepB;
    }
    end += h * (w - ow);
    while (r < end)
    {
        int * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = 0;
            a += sr;
        }
        a += stepA;
    }

    return result;
}

Matrix<int>
multiply (const MatrixStrided<int> & A, const MatrixStrided<int> & B, int shift)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    int bh  = B.rows ();
    int bw  = B.columns ();
    int bsc = B.strideC ();
    int bsr = B.strideR ();

    Matrix<int> result (h, bw);
    int ow = std::min (w, bh);
    int * aa  = A.base ();
    int * b   = B.base ();
    int * c   = result.base ();
    int * end = c + h * bw;
    while (c < end)
    {
        int * a = aa;
        int * columnEnd = c + h;
        while (c < columnEnd)
        {
            register int64_t element = 0;
            int * i = a;
            int * j = b;
            int * rowEnd = j + ow * bsr;
            while (j != rowEnd)
            {
                element += (int64_t) *i * *j;
                i += sc;
                j += bsr;
            }
            *c++ = element >> shift;
            a += sr;
        }
        b += bsc;
    }
    return result;
}

Matrix<int>
multiply (const MatrixStrided<int> & A, int scalar, int shift)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    Matrix<int> result (h, w);
    int stepC = sc - h * sr;
    int * i   = A.base ();
    int * r   = result.base ();
    int * end = r + h * w;
    while (r < end)
    {
        int * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = (int64_t) scalar * *i >> shift;
            i += sr;
        }
        i += stepC;
    }
    return result;
}

Matrix<int>
divide (const MatrixStrided<int> & A, const MatrixStrided<int> & B, int shift)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    int bh  = B.rows ();
    int bw  = B.columns ();
    int bsc = B.strideC ();
    int bsr = B.strideR ();

    Matrix<int> result (h, w);
    int oh = std::min (h, bh);
    int ow = std::min (w, bw);
    int stepA = sc  - h  * sr;
    int stepB = bsc - oh * bsr;
    int * a   = A.base ();
    int * b   = B.base ();
    int * r   = result.base ();
    int * end = r + h * ow;
    while (r < end)
    {
        int * overlapEnd = r + oh;
        int * columnEnd  = r + h;
        while (r < overlapEnd)
        {
            *r++ = ((int64_t) *a << shift) / *b;
            a += sr;
            b += bsr;
        }
        while (r < columnEnd)
        {
            *r++ = 0;
            a += sr;
        }
        a += stepA;
        b += stepB;
    }
    end += h * (w - ow);
    while (r < end)
    {
        int * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = 0;
            a += sr;
        }
        a += stepA;
    }

    return result;
}

Matrix<int>
divide (const MatrixStrided<int> & A, int scalar, int shift)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    Matrix<int> result (h, w);
    int * i   = A.base ();
    int * r   = result.base ();
    int * end = r + h * w;
    int stepC = sc - h * sr;
    while (r < end)
    {
        int * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = ((int64_t) *i << shift) / scalar;
            i += sr;
        }
        i += stepC;
    }
    return result;
}

Matrix<int>
divide (int scalar, const MatrixStrided<int> & A, int shift)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    Matrix<int> result (h, w);
    int * i   = A.base ();
    int * r   = result.base ();
    int * end = r + h * w;
    int stepC = sc - h * sr;
    while (r < end)
    {
        int * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = ((int64_t) scalar << shift) / *i;
            i += sr;
        }
        i += stepC;
    }
    return result;
}

/**
    Breaks for shifts less than -2*MSB
    Could add an extra guard here, or be careful in calling code.
    Current approach is to minimize cases in favor of compactness and efficiency.
**/
inline int
multiplyRound (int a, int b, int shift)
{
    int64_t temp = (int64_t) a * b;
    if (shift < 0) return (temp + ((int64_t) 1 << -shift - 1)) >> -shift;
    if (shift > 0) return  temp                                <<  shift;
    return temp;
}

// See comments on multiplyRound()
inline int
multiplyCeil (int a, int b, int shift)
{
    int64_t temp = (int64_t) a * b;
    if (shift < 0) return (temp + ~(0xFFFFFFFFFFFFFFFF << -shift)) >> -shift;   // The constant here is a 64-bit unsigned integer, all 1s.
    if (shift > 0) return  temp                                    <<  shift;
    return temp;
}

// exponentResult = 1, to accommodate [-pi, pi]
// exponentY == exponentX, but it doesn't matter what the exponent is; only ratio matters
int
atan2 (int y, int x)
{
    // Using CORDIC algorithm. See https://www.mathworks.com/help/fixedpoint/ug/calculate-fixed-point-arctangent.html

    // Look-up table for values of atan(2^-i), i=0,1,2,...
    // Converted to fixed-point with exponent=1 (same as result of this function).
    // Limited to 12 terms, as a compromise between accuracy and time+space cost.
    // The Mathworks article discusses these tradeoffs.
    static const int lut[] = {421657428, 248918914, 131521918, 66762579, 33510843, 16771757, 8387925, 4194218, 2097141, 1048574, 524287, 262143}; //, 131071, 65535, 32767, 16383, 8191, 4095, 2047, 1023, 511, 255, 127, 63, 31, 15, 7, 4, 2, 1};
    const int count = sizeof (lut) / sizeof (int);  // number of entries in lut

    // Trap corner cases
    if (x == 0)
    {
        if (y == 0) return 0;
        if (y <  0) return -M_PI / 2;
        return              M_PI / 2;
    }
    else if (y == 0)
    {
        if (x < 0) return M_PI;
        return 0;
    }

    // Move problem into first quadrant.
    // While this isn't necessary for CORDIC itself, it ensures that x and y don't overflow due to rotation.
    int result  = 0;
    bool negate = false;
    if (x < 0)
    {
        x *= -1;
        if (y < 0)
        {
            y *= -1;
            result = -M_PI;
        }
        else  // y > 0
        {
            result = -M_PI;
            negate = true;
        }
    }
    else if (y < 0)  // x > 0
    {
        y *= -1;
        negate = true;
    }

    if (x >> 4 >= y)  // Use small-angle formula.
    {
        result += (int) (((int64_t) y << FP_MSB - 1) / x);
    }
    else  // Use CORDIC
    {
        if (x + y < 0)  // Detect potential overflow in first iteration.
        {
            // To prevent overflow, reduce accuracy a bit (literally).
            x >>= 1;
            y >>= 1;
        }
        int shiftX = x;
        int shiftY = y;
        for (int i = 0; i < count;)
        {
            if (y < 0)
            {
                x -= shiftY;
                y += shiftX;
                result -= lut[i];
            }
            else  // y > 0
            {
                x += shiftY;
                y -= shiftX;
                result += lut[i];
            }
            if (y == 0) break;  // This iteration solved the problem exactly, so stop. Rare condition, so might not be worth time+space to test.
            i++;
            shiftX = x >> i;
            shiftY = y >> i;
        }
    }

    if (negate) return -result;
    return result;
}

int
ceil (int a, int exponentA, int exponentResult)
{
    int result;
    if (exponentA >= 0  &&  exponentA < FP_MSB)
    {
        int decimalPlaces = FP_MSB - exponentA;
        int wholeMask = 0xFFFFFFFF << decimalPlaces;
        int decimalMask = ~wholeMask;
        result = a + decimalMask & wholeMask;
    }
    else
    {
        result = a;
    }

    int shift = exponentA - exponentResult;
    if (shift > 0) return result << shift;
    if (shift < 0) return result >> -shift;
    return result;
}

int
cos (int a, int exponentA)
{
    // We want to add PI/2 to a. M_PI exponent=1. To induce down-shift, claim it is 0.
    // Thus, shift is exactly exponentA.
    if (exponentA >= 0) return sin (a + (M_PI >> exponentA), exponentA);
    // If exponentA is negative, then a is too small to use as-is.
    if (exponentA < -FP_MSB) return 0x20000000;  // one, with exponent=1
    return sin ((a >> -exponentA) + M_PI, 0);
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
        int shift = 1 - exponentResult;  // M_E exponent=1
        if (shift < 0) return M_E >> -shift;
        if (shift > 0) return INFINITY;  // Up-shifting M_E is nonsense, since it already uses all the bits.
        return M_E;
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
floor (int a, int exponentA, int exponentResult)
{
    int result;
    if (exponentA >= 0  &&  exponentA < FP_MSB)
    {
        int decimalPlaces = FP_MSB - exponentA;
        int wholeMask = 0xFFFFFFFF << decimalPlaces;
        result = a & wholeMask;
    }
    else
    {
        result = a;
    }

    int shift = exponentA - exponentResult;
    if (shift > 0) return result << shift;
    if (shift < 0) return result >> -shift;
    return result;
}

int
log (int a, int exponentA, int exponentResult)
{
    // We use the simple identity log_e(a) = log_2(a) / log_2(e)
    // exponentRaw = exponentResult - 0 + MSB
    // shift = exponentRaw - exponentResult = MSB
    return ((int64_t) log2 (a, exponentA, exponentResult) << FP_MSB) / M_LOG2E;
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
round (int a, int exponentA, int exponentResult)
{
    int result;
    if (exponentA >= 0  &&  exponentA < FP_MSB)
    {
        int decimalPlaces = FP_MSB - exponentA;
        int mask = 0xFFFFFFFF << decimalPlaces;
        int half = 0x1 << decimalPlaces - 1;
        result = a + half & mask;
    }
    else
    {
        result = a;
    }

    int shift = exponentA - exponentResult;
    if (shift > 0) return result << shift;
    if (shift < 0) return result >> -shift;
    return result;
}

int
sgn (int a, int exponentResult)
{
    if (a == 0) return 0;
    int result = 0x1 << FP_MSB - exponentResult;  // This breaks for exponentResult outside [0, MSB], but the calling code is already meaningless in that case.
    if (a < 0) return -result;
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
    // To create 2PI, we lie about the exponent of M_PI, increasing it by 1.
    a = modFloor (a, M_PI, exponentA, 2);  // exponent = min (exponentA, 2)
    int shift = exponentA - 2;
    if (shift < 0) a >>= -shift;
    const int PIat2 = M_PI >> 1;  // M_PI with exponent=2 rather than 1
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

int
tanh (int a, int exponentA)
{
    // result = (exp(2a) - 1) / (exp(2a) + 1)
    // exponentResult = 0

    // tanh() is symmetric around 0, so only deal with one sign
    bool negate = a < 0;
    if (negate) a = -a;
    if (a == 0) return 0;  // This also traps NAN

    // Determine exponent desired from exp(2a). The result from exp(2a) is never smaller than 1, so exponent>=0
    // We want enough bits to contain the msb of the output. This is
    // exponent = log2(exp(2a)) = ln(exp(2a)) / ln(2) = 2a / (log2(2) / log2(e)) = 2a * log2(e)
    // "exponent" should be expressed as a simple integer
    // raw = exponentA + 1 + exponentLOG2E - MSB = exponentA + 1 - MSB
    // shift = raw - MSB = exponentA + 1 - 2 * MSB
    int exponent = 0;
    if (exponentA >= -1)
    {
        exponent = multiplyCeil (a, M_LOG2E, exponentA + 1 - 2 * FP_MSB);
        // If exponent gets too large, the result will always be 1 or -1
        if (exponent > FP_MSB) return negate ? -0x40000000 : 0x40000000;
    }

    // Find true magnitude of a
    while ((a & 0x40000000) == 0)
    {
        a <<= 1;
        exponentA--;
    }

    // Require at least 16 bits for exp() after downshifting.
    // Otherwise answer is not as accurate as simple linear.
    if (exponentA < 22 - FP_MSB)  // Includes offset of 6 for the downshift.
    {
        // Return linear answer, if possible.
        if (exponentA < -FP_MSB) return 0;  // Can't return result with correct magnitude, so only option is zero.
        int result = a >> -exponentA;
        if (negate) return -result;
        return result;
    }
    // Set correct magnitude for exp().
    // exp(a) expects exponentA=7, but we want exp(2a), so shift to exponentA=6 and lie about it
    a >>= 6 - exponentA;

    // call exp() and complete calculation
    int result = exp (a, exponent);
    int one = 1 << FP_MSB - exponent;
    result = ((int64_t) result - one << FP_MSB) / ((int64_t) result + one);

    if (negate) return -result;
    return result;
}
