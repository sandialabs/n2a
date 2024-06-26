/*
Copyright 2018-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#include "runtime.h"
#include "mymath.h"
#include "Matrix.tcc"

#undef near
#undef far


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

int
shift (int64_t a, int shift)
{
    if (shift < 0) return a >> -shift;
    if (shift > 0) return a <<  shift;
    return a;
}

Matrix<int>
shift (const MatrixAbstract<int> & A, int shift)
{
    if (shift > 0) return A * (0x1 << shift);
    if (shift < 0) return A / (0x1 << -shift);
    return A;
}

void
identity (const MatrixStrided<int> & A, int one)
{
    int h = A.rows ();
    int w = A.columns ();
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            A(r,c) = (r == c) ? one : 0;
        }
    }
}

int
norm (const MatrixStrided<int> & A, int n, int exponentA, int exponentResult)
{
    const int exponentN = -FP_MSB2;

    int * a   = A.base ();
    int * end = a + A.rows () * A.columns ();  // Assumes MatrixFixed, so that elements are dense in memory.

    if (n == 0  ||  n == INFINITY)
    {
        int result = 0;
        int shift = -exponentResult;
        if (n)  // INFINITY
        {
            while (a < end) result = std::max (std::abs (*a++), result);
            shift += exponentA;
        }
        else  // 0
        {
            while (a < end) if (*a++) result++;
        }
        if (shift > 0) return result <<  shift;
        if (shift < 0) return result >> -shift;
        return result;
    }

    uint64_t sum = 0;
    if (n == 0x1 << -exponentN)
    {
        while (a < end) sum += std::abs (*a++);
        int shift = exponentA - exponentResult;
        if (shift > 0) sum <<=  shift;
        if (shift < 0) sum >>= -shift;
        if (sum > INFINITY) return INFINITY;
        return sum;
    }

    // Fully general form
    // exponentA will hold exponentSum when done.
    int root;  // exponent=-MSB/2
    if (n == 0x2 << -exponentN)
    {
        root = 0x1 << FP_MSB2 - 1;  // 0.5
        exponentA = exponentA * 2;  // raw result of squaring elements of A
        while (a < end)
        {
            int t = *a++;
            sum += (int64_t) t * t;
        }
    }
    else  // fractional power or power > 2
    {
        // for root:
        // raw division = exponentOne - exponentN = 0 - -MSB/2 = MSB/2
        // want exponentN, so shift = raw - exponentN = MSB/2 - -MSB/2 = MSB
        root = (0x1 << FP_MSB) / n;

        // Estimate center bit position
        // Ideally this would be the median of MSB positions, but that
        // requires sorting. Instead, we compute the average.
        int count = 0;
        int center = 0;
        int * t = a;
        while (t < end)
        {
            int temp = std::abs (*t++);
            if (! temp) continue;
            count++;
            while (temp)
            {
                temp >>= 1;
                center++;
            }
        }
        if (count) center /= count;
        else       center = FP_MSB2;  // Though, in this case it doesn't matter because matrix is all zeroes.

        // for exponentSum:
        // centerA = center power of A = exponentA + center
        // centerTerm = center power of one term = centerA*n
        // want center of term at MSB/2, so exponentSum = centerTerm - MSB/2 = (exponentA+center)*n - MSB/2
        int exponentSum = ((exponentA + center) * n >> -exponentN) - FP_MSB2;

        while (a < end) sum += pow (std::abs (*a++), n, exponentA, exponentSum);
        exponentA = exponentSum;
    }
    while (sum > INFINITY)
    {
        sum >>= 1;
        exponentA++;
    }
    return pow ((int) sum, root, exponentA, exponentResult);
}

Matrix<int>
normalize (const MatrixStrided<int> & A, int exponentA)
{
    // Calculate 2-norm of A
    // Allow for magnitude of "scale" to be larger than the magnitude of individual elements.
    int count = norm (A, 0, exponentA, 0);  // Number of nonzero elements
    int bits = 0;
    while (count >>= 1) bits++;
    int exponentScale = exponentA + bits;
    int scale = norm (A, 0x2 << FP_MSB2, exponentA, exponentScale);  // 2-norm

    // Divide A
    // raw = exponentA - exponentScale
    // goal = -MSB = everything in [0,1]
    // shift = raw - goal = exponentA - exponentScale - -MSB
    int shift = exponentA - exponentScale + FP_MSB;
    return divide (A, scale, shift);
}

Matrix<int>
cross (const MatrixStrided<int> & A, const MatrixStrided<int> & B, int shift)
{
    int ah = A.rows ();
    int bh = B.rows ();
    int h = std::min (ah, bh);
    Matrix<int> result (h, 1);
    for (int i = 0; i < h; i++)
    {
        int j = (i + 1) % h;
        int k = (i + 2) % h;
        result[i] = (int64_t) A(j,0) * B(k,0) - (int64_t) A(k,0) * B(j,0) >> shift;
    }
    return result;
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

Matrix<int>
glFrustum (int left, int right, int bottom, int top, int near, int far, int exponent)
{
    Matrix<int> result (4, 4);
    clear (result);

    // After division, raw = exponent - exponent = 0
    // Goal is to shift back to original exponent. shift = raw - exponent = -exponent
    int shift = -exponent;
    result(0,0) = (  (int64_t) 2 * near       << shift) / (right - left);
    result(1,1) = (  (int64_t) 2 * near       << shift) / (top   - bottom);
    result(0,2) = (  (int64_t) right + left   << shift) / (right - left);
    result(1,2) = (  (int64_t) top   + bottom << shift) / (top   - bottom);
    result(2,2) = (-((int64_t) far   + near)  << shift) / (far   - near);
    // shift = exponentOne - exponent = 0 - exponent
    result(3,2) = -1 << shift;
    // raw = (exponent + exponent) - exponent = exponent
    // shift = raw - exponent = 0
    result(2,3) = (int64_t) -2 * far * near / (far - near);

    return result;
}

Matrix<int>
glOrtho (int left, int right, int bottom, int top, int near, int far, int exponent)
{
    Matrix<int> result (4, 4);
    clear (result);

    // raw = exponentOne - exponent = 0 - exponent = -exponent
    // shift = raw - exponent = -2*exponent
    int shift = -2 * exponent;
    result(0,0) = ((int64_t)  2 << shift) / (right - left);
    result(1,1) = ((int64_t)  2 << shift) / (top   - bottom);
    result(2,2) = ((int64_t) -2 << shift) / (far   - near);
    // raw = exponent - exponent = 0
    // shift = raw - exponent = -exponent
    shift = -exponent;
    result(0,3) = (-((int64_t) right + left  ) << shift) / (right - left);
    result(1,3) = (-((int64_t) top   + bottom) << shift) / (top   - bottom);
    result(2,3) = (-((int64_t) far   + near  ) << shift) / (far   - near);
    // shift = exponentOne - exponent = -exponent
    result(3,3) = 1 << shift;

    return result;
}

Matrix<int>
glLookAt (const MatrixFixed<int,3,1> & eye, const MatrixFixed<int,3,1> & center, const MatrixFixed<int,3,1> & up, int exponent)
{
    // Create an orthonormal frame
    Matrix<int> f = center - eye;
    f = normalize (f, exponent);              // f exponent=-MSB
    Matrix<int> u = normalize (up, exponent); // u exponent=-MSB
    Matrix<int> s = cross (f, u, FP_MSB);     // s exponent=-MSB; but s is not necessarily unit length
    s = normalize (s, -FP_MSB);
    u = cross (s, f, FP_MSB);

    Matrix<int> R (4, 4);  // R exponent=-MSB
    clear (R);
    R(0,0) =  s[0];
    R(0,1) =  s[1];
    R(0,2) =  s[2];
    R(1,0) =  u[0];
    R(1,1) =  u[1];
    R(1,2) =  u[2];
    R(2,0) = -f[0];
    R(2,1) = -f[1];
    R(2,2) = -f[2];
    R(3,3) = 1 << FP_MSB;

    Matrix<int> Tr (4, 4);  // Tr has the exponent passed to this function
    identity (Tr, 1 << -exponent);
    Tr(0,3) = -eye[0];
    Tr(1,3) = -eye[1];
    Tr(2,3) = -eye[2];

    // raw = -MSB + exponent
    // goal = exponent
    // shift = raw - goal = -MSB
    return multiply (R, Tr, FP_MSB);
}

Matrix<int>
glPerspective (int fovy, int aspect, int near, int far, int exponent)
{
    // raw = (exponent + 1-MSB) - 0 = exponent+1-MSB
    // goal = 1-MSB, same as M_PI
    // shift = raw - goal = exponent+1-MSB - (1-MSB) = exponent
    int shift = exponent;
    fovy = ::shift ((int64_t) fovy * M_PI / 180, shift);
    // raw = exponentOne - exponentTan = 0 - 3-MSB = MSB-3
    // goal = exponent
    // shift = raw - goal = MSB-3 - exponent
    shift = FP_MSB - 3 - exponent;
    int f = ((int64_t) 1 << shift) / tan (fovy / 2, 1-FP_MSB, 3-FP_MSB);  // tan() goes to infinity, but 8 (2^3) should be sufficient for almost all cases.

    Matrix<int> result (4, 4);
    clear (result);

    // raw = exponent - exponent = 0
    // goal = exponent
    // shift = raw - goal = -exponent
    shift = -exponent;
    result(0,0) = ((int64_t) f          << shift) / aspect;
    result(1,1) = f;
    result(2,2) = ((int64_t) far + near << shift) / (near - far);
    result(3,2) = -1 << FP_MSB - exponent;
    // raw = (exponent + exponent) - exponent = exponent
    result(2,3) = (int64_t) 2 * far * near / (near - far);

    return result;
}

Matrix<int>
glRotate (int angle, const MatrixFixed<int,3,1> & axis, int exponent)
{
    return glRotate (angle, axis[0], axis[1], axis[2], exponent);
}

Matrix<int>
glRotate (int angle, int x, int y, int z, int exponent)
{
    // raw = (exponent + 1-MSB) - 0 = exponent + 1 - MSB
    // goal = 1-MSB, same as M_PI
    // shift = raw - goal = exponent
    int shift = exponent;
    angle = ::shift ((int64_t) angle * M_PI / 180, shift);
    // c, s and c1 all have exponent 1-MSB
    int c = cos (angle, 1);
    int s = sin (angle, 1);
    int c1 = (1 << FP_MSB - 1) - c;

    // normalize([x y z])
    // raw = exponent + exponent
    // result = exponent + 2 bits of headroom for additions
    int l = sqrt ((int64_t) x * x + (int64_t) y * y + (int64_t) z * z, 2 * exponent, exponent + 2);
    // raw = exponent - (exponent + 2) = -2
    // goal = -MSB
    // shift = raw - goal = -2 - -MSB = MSB-2
    shift = FP_MSB - 2;
    x = ((int64_t) x << shift) / l;
    y = ((int64_t) y << shift) / l;
    z = ((int64_t) z << shift) / l;

    // exponentResult=-MSB
    Matrix<int> result (4, 4);
    clear (result);

    // raw = -MSB + -MSB + 1-MSB = -3*MSB + 1
    // goal = 1-MSB to match c
    // shift = -2*MSB, applied in two stages
    // Then we need one bit upshift to match exponentResult
    result(0,0) = (((int64_t) x * x >> FP_MSB) * c1 >> FP_MSB) + c << 1;
    result(1,1) = (((int64_t) y * y >> FP_MSB) * c1 >> FP_MSB) + c << 1;
    result(2,2) = (((int64_t) z * z >> FP_MSB) * c1 >> FP_MSB) + c << 1;
    result(3,3) = 1 << FP_MSB;
    // For second term:
    // raw = -MSB + 1-MSB
    // goal = 1-MSB
    result(1,0) = (((int64_t) y * x >> FP_MSB) * c1 >> FP_MSB) + ((int64_t) z * s >> FP_MSB) << 1;
    result(2,0) = (((int64_t) x * z >> FP_MSB) * c1 >> FP_MSB) - ((int64_t) y * s >> FP_MSB) << 1;
    result(0,1) = (((int64_t) x * y >> FP_MSB) * c1 >> FP_MSB) - ((int64_t) z * s >> FP_MSB) << 1;
    result(2,1) = (((int64_t) y * z >> FP_MSB) * c1 >> FP_MSB) + ((int64_t) x * s >> FP_MSB) << 1;
    result(0,2) = (((int64_t) x * z >> FP_MSB) * c1 >> FP_MSB) + ((int64_t) y * s >> FP_MSB) << 1;
    result(1,2) = (((int64_t) y * z >> FP_MSB) * c1 >> FP_MSB) - ((int64_t) x * s >> FP_MSB) << 1;

    return result;
}

Matrix<int>
glScale (const MatrixFixed<int,3,1> & scales, int exponent)
{
    return glScale (scales[0], scales[1], scales[2], exponent);
}

Matrix<int>
glScale (int sx, int sy, int sz, int exponent)
{
    Matrix<int> result (4, 4);
    clear (result);
    result(0,0) = sx;
    result(1,1) = sy;
    result(2,2) = sz;
    result(3,3) = 1 << -exponent;
    return result;
}

Matrix<int>
glTranslate (const MatrixFixed<int,3,1> & position, int exponent)
{
    return glTranslate (position[0], position[1], position[2], exponent);
}

Matrix<int>
glTranslate (int x, int y, int z, int exponent)
{
    Matrix<int> result (4, 4);
    identity (result, 1 << -exponent);
    result(0,3) = x;
    result(1,3) = y;
    result(2,3) = z;
    return result;
}

// exponentResult = 1-MSB, to accommodate [-pi, pi]
// exponentY == exponentX, but it doesn't matter what the exponent is; only ratio matters
int
atan2 (int y, int x)
{
    // Using the CORDIC algorithm. See https://www.mathworks.com/help/fixedpoint/ug/calculate-fixed-point-arctangent.html

    // Look-up table for values of atan(2^-i), i=0,1,2,...
    // Converted to fixed-point with exponent=1-MSB (same as result of this function).
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
        // raw = 0
        // goal = 1-MSB
        // shift = 0 - (1-MSB) = MSB-1
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
    if (exponentA >= -FP_MSB  &&  exponentA < 0)
    {
        int wholeMask = 0xFFFFFFFF << -exponentA;
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
    // We want to add PI/2 to a. M_PI exponent=1-MSB. To induce down-shift, claim it is -MSB.
    // current = -MSB  (formerly 1-MSB)
    // goal = exponentA
    // shift = current - goal = exponentA + MSB
    if (exponentA >= -FP_MSB) return sin (a + (M_PI >> exponentA + FP_MSB), exponentA);

    // If a is too small, then result is basically 1.
    if (exponentA < -2 * FP_MSB) return 0x20000000;  // one, with exponent=1-MSB

    // Shift a to match M_PI. Strategy for PI/2 remains the same.
    return sin ((a >> -exponentA - FP_MSB) + M_PI, -FP_MSB);
}

int
exp (int a, int exponentResult)
{
    const int exponentA = 7 - FP_MSB;  // Hard-coded value established in gov.sandia.n2a.language.function.Exp class.

    if (a == 0)
    {
        int shift = -exponentResult;
        if (shift < 0) return 0;
        return 1 << shift;
    }
    const int one = 1 << -exponentA;
    if (a == one)  // special case for returning e, the natural logarithm base.
    {
        int shift = 1 - FP_MSB - exponentResult;  // M_E exponent=1-MSB
        if (shift == 0) return M_E;
        if (shift < 0) return M_E >> -shift;
        return INFINITY;  // Up-shifting M_E is nonsense, since it already uses all the bits.
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
    // i has exponent=0
    // a has exponent=7-MSB per above comment
    // term and result have exponentWork
    // raw = (exponentA + exponentWork) - 0
    // goal = exponentWork
    // shift = raw - goal = exponentA
    const int shift = -exponentA;  // preemtively flip the sign, since this is always used in the positive form
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
        // Let 1 have exponent=-2*MSB
        // raw result of inversion = -2*MSB - exponentWork
        // goal = exponentResult
        // shift = raw - goal = -2*MSB - exponentWork - exponentResult
        uint64_t temp = ((uint64_t) 1 << 2 * FP_MSB) / result;
        int shift = -2 * FP_MSB - exponentWork - exponentResult;
        if (shift < 0)
        {
            if (shift < -2 * FP_MSB) return 0;  // Prevent weird effects from modulo arithmetic on size of shift.
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
            uint64_t temp = (uint64_t) result << shift;
            if (temp > INFINITY) return INFINITY;
            return temp;
        }
        return result;
    }
}

int
floor (int a, int exponentA, int exponentResult)
{
    int result;
    if (exponentA >= -FP_MSB  &&  exponentA < 0)
    {
        int wholeMask = 0xFFFFFFFF << -exponentA;
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
    // exponentRaw = exponentResult - -MSB
    // shift = exponentRaw - exponentResult = MSB
    return ((int64_t) log2 (a, exponentA, exponentResult) << FP_MSB) / M_LOG2E;
}

// This implementation tries to keep everything within a single 32-bit word,
// so it throws away some precision.
int
log2 (int a, int exponentA, int exponentResult)
{
    if (a <  0) return NAN;
    if (a == 0) return -INFINITY;

    // If a<1, then the result is -log2(1/a)
    bool negate = false;
    if (exponentA < -FP_MSB  ||  (exponentA < 0  &&  a < 1 << -exponentA))
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
        // Let the numerator 1 have exponent=-MSB, to maximize significant bits in result.
        // raw division exponent = -MSB - exponentA
        a = (1 << FP_MSB) / a;
        exponentA = -FP_MSB - exponentA;
    }

    // At this point a >= 1
    // Using the identity log(ab)=log(a)+log(b), we put a into normal form:
    //   operand = a*2^exponentA
    //   log2(operand) = log2(a)+log2(2^exponentA) = log2(a)+exponentA
    // Our remaining work will be only on the mantissa of a, not its exponent.
    // Goal is to shift the mantissa be in [1,2). Alternately, we define "1"
    // with an exponent.
    int exponentOne = -FP_MSB2;
    int one = 1 << -exponentOne;
    while (a < one)
    {
        one >>= 1;
        exponentOne++;
    }
    int result = exponentA - exponentOne;  // Represented as a pure integer, with exponent=0. Later this will have to be shifted to exponentResult.
    int two = 2 * one;  // Same as upshift by 1.
    while (a >= two)  // This could also be done with a bit mask that checks for any bits in the twos position or higher.
    {
        result++;
        a = (a >> 1) + (a & 1);  // divide-by-2 with rounding
    }

    // "result" is now the exponent of the bit in a at power 0.
    // That gives us the whole part of log_2(operand). We now need to compute the fractional part.

    // TODO: Guard against large shifts.
    int shift = -exponentResult;
    if (a > one)  // Otherwise a==one, in which case the following algorithm will do nothing.
    {
        while (shift > 0)  // The requested result has some fractional bits, so fill them.
        {
            // exponentOne is the working exponent of mantissa a
            // raw exponent of a^2 = 2*exponentOne
            // goal = exponentOne
            // shift = raw - goal = exponentOne
            a = multiplyRound (a, a, exponentOne);
            result <<= 1;
            shift--;
            if (a >= two)
            {
                result |= 1;
                a = (a >> 1) + (a & 1);
            }
        }
        a = multiplyRound (a, a, exponentOne);
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
pow (int a, int b, int exponentA, int exponentResult)
{
    // exponentB = -MSB/2

    // Use the identity: a^b = e^(b*ln(a))
    // Most of the complexity of this function is in trapping special cases.
    // For details, see man page for floating-point pow().
    // We don't have signed zero, so ignore all distinctions based on that.
    bool negate = false;
    int blna = 1;  // exponent=7-MSB, as required by exp(); Nonzero indicates that blna needs to be calculated.
    int shift = -exponentA;
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
            // raw multiply = exponentB + exponentBLNA
            // goal = exponentBLNA
            // shift = raw - goal = exponentB = -MSB/2
            int64_t temp = (int64_t) b * log (a, exponentA, 7-FP_MSB) >> FP_MSB2;
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
    if (exponentA >= -FP_MSB  &&  exponentA < 0)
    {
        int decimalPlaces = -exponentA;
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
    int result = 0x1 << -exponentResult;  // This breaks for exponentResult outside [-MSB, 0], but the calling code is already meaningless in that case.
    if (a < 0) return -result;
    return result;
}

int
sqrt (int a, int exponentA, int exponentResult)
{
    if (a < 0) return NAN;

    // Simple approach: apply the identity a^0.5=e^(ln(a^0.5))=e^(0.5*ln(a))
    //int l = log (a, exponentA, -MSB/2) >> 1;
    //return exp (l, -MSB/2, exponentResult);

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
    if (exponentA % 2)  // Odd, so leave remainder inside sqrt()
    {
        m <<= 1;  // equivalent to "2m" in the comments above
        exponentA--;
    }
    int exponentRaw = exponentA / 2;  // exponent of raw result

    uint32_t bit;
    if (m & 0xFFFE0000) bit = 1 << 30;
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

// The 64-bit version of sqrt() can also handle 32-bit inputs.
// Not sure if we need both. There are some tradeoffs in efficiency
// between having two implementations or one heavier-weight one.
// IE: if the 64-bit version is needed, then it should be used for
// everything rather than also using the 32-bit version. That would
// save space on an embedded system like SpiNNaker.
int
sqrt (int64_t a, int exponentA, int exponentResult)
{
    if (a < 0) return NAN;

    uint64_t m = a;  // "m" for mantissa
    if (exponentA % 2)  // Odd, so leave remainder inside sqrt()
    {
        m <<= 1;  // equivalent to "2m" in the comments above
        exponentA--;
    }
    int exponentRaw = exponentA / 2;

    uint64_t bit;
    if      (m & 0xFFFFFFFF80000000) bit = (int64_t) 1 << 60;
    else if (m &         0x7FFE0000) bit = 1 << 30;
    else                             bit = 1 << 16;  // For efficiency, don't scan upper bits if they're empty.
    while (bit > m) bit >>= 2;  // Locate starting position, at or below msb of m.

    uint64_t result = 0;
    while (bit)
    {
        uint64_t temp = result + bit;
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
        uint64_t temp = (result << 1) + 1;
        if (m >= temp)
        {
            m -= temp;
            result++;
        }
    }
    if (shift < 0) result >>= -shift;
    return result;  // truncate to 32 bits
}

int
sin (int a, int exponentA)
{
    const int exponentResult = 1 - FP_MSB;

    // Limit a to [0,pi/2)
    // To create 2PI, we lie about the exponent of M_PI, increasing it by 1.
    a = modFloor (a, M_PI, exponentA, 2-FP_MSB);  // exponent = min (exponentA, 2-MSB)
    int shift = exponentA + FP_MSB - 2;  // shift = exponentA - (2-MSB); If negative, then result above has exponentA.
    if (shift < 0) a >>= -shift;  // Shift it to match exponent of 2*pi.
    const int PIat2 = M_PI >> 1;  // M_PI with exponent=2-MSB rather than 1-MSB
    bool negate = false;
    if (a > PIat2)
    {
        a -= PIat2;
        negate = true;
    }
    if (a > PIat2 >> 1) a = PIat2 - a;
    a <<= 1;  // Now exponent=1-MSB, which matches our promised exponentResult.

    // Use power-series to compute sine, similar to exp()
    // sine(a) = sum_0^inf (-1)^n * x^(2n-1) / (2n+1)! = x - x^3/3! + x^5/5! - x^7/7! ...

    int term = a;
    int result = a;  // zeroth term
    int n1 = 0;  // exponent=0
    int n2 = 1;
    for (int n = 1; n < 7; n++)
    {
        n1 = n2 + 1;
        n2 = n1 + 1;
        // raw exponent of operations below, in evaluation order:
        // multiply: 2*exponentResult
        // divide: 2*exponentResult
        // shift = 2*exponenetResult - exponentResult = exponentResult = 1-MSB
        // multiply: 2*exponentResult
        // divide : 2*exponentResult
        // same shift again
        term = ((int64_t) -term * a / n1 >> -exponentResult) * a / n2 >> -exponentResult;
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
    return ((int64_t) sin (a, exponentA) << -exponentResult) / cos (a, exponentA);  // Don't do any saturation checks. We are not really interested in infinity.
}

int
tanh (int a, int exponentA)
{
    // result = (exp(2a) - 1) / (exp(2a) + 1)
    // exponentResult = -MSB

    // tanh() is symmetric around 0, so only deal with one sign
    bool negate = a < 0;
    if (negate) a = -a;
    if (a == 0) return 0;  // This also traps NAN

    // Determine "exponentOne" desired from exp(2a).
    // The result from exp(2a) is never smaller than 1, so exponentOne>=-MSB
    // 1 must be representable, so exponentOne<=0.
    // We want enough bits to contain the msb of the output. This is
    // exponentMSB = log2(exp(2a)) = ln(exp(2a)) / ln(2) = 2a / (log2(2) / log2(e)) = 2a * log2(e)
    // Claiming that exponentA is one higher has the effect of multiplying by 2.
    // exponents of the multiplication itself are:
    // raw = (exponentA+1) + exponentLOG2E = exponentA + 1 + -MSB
    // goal = 0  (integer count of bits)
    // shift = raw - goal = exponentA + 1 - MSB
    int exponentOne = -FP_MSB;
    if (exponentA >= -1 - FP_MSB)  // Otherwise 2a is less than 1, so no need for the following calculation.
    {
        exponentOne = multiplyCeil (a, M_LOG2E, exponentA + 1 - FP_MSB);  // exponentOne now refers to MSB
        // If exponentOne gets too large, the result will always be 1 or -1
        if (exponentOne > FP_MSB  ||  exponentOne == 0) return negate ? -0x40000000 : 0x40000000;
        exponentOne -= FP_MSB;  // exponentOne now refers to LSB
    }

    // Find true magnitude of a
    while ((a & 0x40000000) == 0)
    {
        a <<= 1;
        exponentA--;
    }

    // Require at least 16 bits for exp() after downshifting.
    // Otherwise answer is not as accurate as simple linear.
    // See the downshift after this if-block. This is equivalent to ensuring that
    // total amount of shift (6-MSB-exponentA) < 16.
    // Solving for exponentA: exponentA > -10 - MSB
    // Negating logic ...
    if (exponentA <= -10 - FP_MSB)
    {
        // Return linear answer, if possible.
        // goal = exponentResult = -MSB
        // shift = exponentA - goal = exponentA + MSB
        if (exponentA < -2 * FP_MSB) return 0;  // Can't return result with correct magnitude, so only option is zero.
        int result = a >> -exponentA - FP_MSB;
        if (negate) return -result;
        return result;
    }
    // Set correct magnitude for exp().
    // exp(a) expects exponentA=7-MSB, but we want exp(2a), so shift to exponentA=6 and lie about it
    // goal = 6-MSB
    // shift = exponentA - goal = exponentA - 6 + MSB
    // This implies that exponentA <= 6-MSB.
    if (exponentA > 6 - FP_MSB) return negate ? -0x40000000 : 0x40000000;
    a >>= 6 - FP_MSB - exponentA;

    // call exp() and complete calculation
    int result = exp (a, exponentOne);
    int one = 1 << -exponentOne;
    result = ((int64_t) result - one << FP_MSB) / ((int64_t) result + one);

    if (negate) return -result;
    return result;
}
