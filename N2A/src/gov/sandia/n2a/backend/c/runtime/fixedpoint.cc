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

Matrix<int>
normalize (const MatrixStrided<int> & A, int exponentA)
{
    // Calculate 2-norm of A
    // Allow for magnitude of "scale" to be larger than the magnitude of individual elements.
    int count = norm (A, 0, exponentA, FP_MSB);  // Number of nonzero elements
    int bits = 0;
    while (count >>= 1) bits++;
    int exponentScale = exponentA + bits;
    int scale = norm (A, 0x2 << 15, exponentA, exponentScale);  // 2-norm

    // Divide A
    // Goal is for result to be at exponent=0
    // See comments on Divide in RendererCfp
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

    // After division, raw = exponent - exponent + MSB = MSB
    // Goal is to shift back to original exponent. shift = raw - exponent = MSB - exponent
    int shift = FP_MSB - exponent;
    result(0,0) = (  (int64_t) 2 * near       << shift) / (right - left);
    result(1,1) = (  (int64_t) 2 * near       << shift) / (top   - bottom);
    result(0,2) = (  (int64_t) right + left   << shift) / (right - left);
    result(1,2) = (  (int64_t) top   + bottom << shift) / (top   - bottom);
    result(2,2) = (-((int64_t) far   + near)  << shift) / (far   - near);
    // shift = MSB - exponent
    result(3,2) = -1 << shift;
    // raw = (exponent + exponent - MSB) - exponent + MSB = exponent
    // shift = raw - exponent = 0
    result(2,3) = (int64_t) -2 * far * near / (far - near);

    return result;
}

Matrix<int>
glOrtho (int left, int right, int bottom, int top, int near, int far, int exponent)
{
    Matrix<int> result (4, 4);
    clear (result);

    // raw = MSB - exponent + MSB = 2*MSB - exponent
    // shift = raw - exponent = 2*MSB - 2*exponent
    int shift = 2 * (FP_MSB - exponent);
    result(0,0) = ((int64_t)  2 << shift) / (right - left);
    result(1,1) = ((int64_t)  2 << shift) / (top   - bottom);
    result(2,2) = ((int64_t) -2 << shift) / (far   - near);
    // raw = exponent - exponent + MSB = MSB
    // shift = raw - exponent = MSB - exponent
    shift = FP_MSB - exponent;
    result(0,3) = (-((int64_t) right + left  ) << shift) / (right - left);
    result(1,3) = (-((int64_t) top   + bottom) << shift) / (top   - bottom);
    result(2,3) = (-((int64_t) far   + near  ) << shift) / (far   - near);
    // shift = MSB - exponent
    result(3,3) = 1 << shift;

    return result;
}

Matrix<int>
glLookAt (const MatrixFixed<int,3,1> & eye, const MatrixFixed<int,3,1> & center, const MatrixFixed<int,3,1> & up, int exponent)
{
    // Create an orthonormal frame
    Matrix<int> f = center - eye;
    f = normalize (f, exponent);              // f exponent=0
    Matrix<int> u = normalize (up, exponent); // u exponent=0
    Matrix<int> s = cross (f, u, FP_MSB);     // s exponent=0; but s is not necessarily unit length
    s = normalize (s, 0);
    u = cross (s, f, FP_MSB);

    Matrix<int> R (4, 4);  // R exponent=0
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
    identity (Tr, 1 << FP_MSB - exponent);
    Tr(0,3) = -eye[0];
    Tr(1,3) = -eye[1];
    Tr(2,3) = -eye[2];

    // raw = 0 + exponent - MSB
    // goal = exponent
    // shift = raw - goal = -MSB
    return multiply (R, Tr, FP_MSB);
}

Matrix<int>
glPerspective (int fovy, int aspect, int near, int far, int exponent)
{
    // raw = (exponent + 1 - MSB) - MSB + MSB = exponent + 1 - MSB
    // goal = 1, same as M_PI
    // shift = raw - goal = exponent - MSB
    int shift = exponent - FP_MSB;
    fovy = ::shift ((int64_t) fovy * M_PI / 180, shift);
    // raw = MSB - 3 + MSB = 2*MSB - 3
    // goal = exponent
    // shift = raw - goal = 2*MSB - 3 - exponent
    shift = 2 * FP_MSB - 3 - exponent;
    int f = ((int64_t) 1 << shift) / tan (fovy / 2, 1, 3);  // tan() goes to infinity, but 8 (2^3) should be sufficient for almost all cases.

    Matrix<int> result (4, 4);
    clear (result);

    // raw = exponent - exponent + MSB = MSB
    // goal = exponent
    // shift = raw - goal = MSB - exponent
    shift = FP_MSB - exponent;
    result(0,0) = ((int64_t) f          << shift) / aspect;
    result(1,1) = f;
    result(2,2) = ((int64_t) far + near << shift) / (near - far);
    result(3,2) = -1 << FP_MSB - exponent;
    // raw = (exponent + exponent - MSB) - exponent + MSB = exponent
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
    // raw = (exponent + 1 - MSB) - MSB + MSB = exponent + 1 - MSB
    // goal = 1, same as M_PI
    // shift = raw - goal = exponent - MSB
    int shift = exponent - FP_MSB;
    angle = ::shift ((int64_t) angle * M_PI / 180, shift);
    // c, s and c1 all have exponent 1
    int c = cos (angle, 1);
    int s = sin (angle, 1);
    int c1 = (1 << FP_MSB - 1) - c;

    // normalize([x y z])
    // raw = exponent + exponent - MSB
    // result = exponent + 2 bits of headroom for additions
    int l = sqrt ((int64_t) x * x + (int64_t) y * y + (int64_t) z * z, 2 * exponent - FP_MSB, exponent + 2);
    // raw = exponent - (exponent + 2) + MSB = MSB - 2
    // goal = 0
    shift = FP_MSB - 2;
    x = ((int64_t) x << shift) / l;
    y = ((int64_t) y << shift) / l;
    z = ((int64_t) z << shift) / l;

    // exponentResult=0
    Matrix<int> result (4, 4);
    clear (result);

    // raw = (0 + 0 - MSB) + 1 - MSB = -2*MSB + 1
    // goal = 1 to match c
    // shift = -2 * MSB, applied in two stages
    // Then we need one bit upshift to match exponentResult
    result(0,0) = (((int64_t) x * x >> FP_MSB) * c1 >> FP_MSB) + c << 1;
    result(1,1) = (((int64_t) y * y >> FP_MSB) * c1 >> FP_MSB) + c << 1;
    result(2,2) = (((int64_t) z * z >> FP_MSB) * c1 >> FP_MSB) + c << 1;
    result(3,3) = 1 << FP_MSB;
    // For second term:
    // raw = 0 + 1 - MSB
    // goal = 1
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
    result(3,3) = 1 << FP_MSB - exponent;
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
    identity (result, 1 << FP_MSB - exponent);
    result(0,3) = x;
    result(1,3) = y;
    result(2,3) = z;
    return result;
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
    int exponent0 = exponentA - FP_MSB;  // exponent at bit position 0
    if (exponent0 % 2)  // Odd, so leave remainder inside sqrt()
    {
        m <<= 1;  // equivalent to "2m" in the comments above
        exponent0--;
    }
    int exponentRaw = exponent0 / 2 + FP_MSB;  // exponent of raw result at MSB

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
