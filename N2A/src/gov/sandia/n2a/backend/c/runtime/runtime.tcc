/*
Copyright 2018-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_runtime_tcc
#define n2a_runtime_tcc


#include "mymath.h"
#include "runtime.h"
#include "matrix.h"

#include <climits>

#ifdef _WIN32
#  define WIN32_LEAN_AND_MEAN
#  include <windows.h>
#  include <time.h>
#  undef min
#  undef max
#endif

#undef near
#undef far


// General functions ---------------------------------------------------------

template<class T>
T
uniform ()
{
    return rand () / (RAND_MAX + (T) 1);
}

template<class T>
T
uniform (T sigma)
{
    return sigma * rand () / (RAND_MAX + (T) 1);
}

template<class T>
T
uniform (T lo, T hi, T step)
{
    int steps = floor ((hi - lo) / step + 1);
    return lo + step * (rand () % steps);
}

// Box-Muller method (polar variant) for Gaussian random numbers.
template<class T>
T
gaussian ()
{
    static bool haveNextGaussian = false;
    static T nextGaussian;

    if (haveNextGaussian)
    {
        haveNextGaussian = false;
        return nextGaussian;
    }
    else
    {
        T v1, v2, s;
        do
        {
            v1 = uniform<T> () * 2 - 1;   // between -1.0 and 1.0
            v2 = uniform<T> () * 2 - 1;
            s = v1 * v1 + v2 * v2;
        }
        while (s >= 1 || s == 0);
        T multiplier = sqrt (- 2 * log (s) / s);
        nextGaussian = v2 * multiplier;
        haveNextGaussian = true;
        return v1 * multiplier;
    }
}

template<class T>
T
gaussian (T sigma)
{
    return sigma * gaussian<T> ();
}

template<class T>
MatrixFixed<T,3,1>
grid (int i, int nx, int ny, int nz)
{
    int sx = ny * nz;  // stride x

    // compute xyz in stride order
    MatrixFixed<T,3,1> result;
    result[0] = ((i / sx) + 0.5f) / nx;  // (i / sx) is an integer operation, so remainder is truncated.
    i %= sx;
    result[1] = ((i / nz) + 0.5f) / ny;
    result[2] = ((i % nz) + 0.5f) / nz;
    return result;
}

template<class T>
MatrixFixed<T,3,1>
gridRaw (int i, int nx, int ny, int nz)
{
    int sx = ny * nz;  // stride x

    // compute xyz in stride order
    MatrixFixed<T,3,1> result;
    result[0] = i / sx;
    i %= sx;
    result[1] = i / nz;
    result[2] = i % nz;
    return result;
}

template<class T>
T
pulse (T t, T width, T period, T rise, T fall)
{
    if (t < 0) return 0;
    if (period > 0) t = std::fmod (t, period);
    if (t < rise) return t / rise;
    if (rise > 0) t -= rise;
    if (t < width) return 1;
    if (width > 0) t -= width;
    if (t < fall) return (T) 1 - t / fall;
    return 0;
}

template<class T>
T
unitmap (const MatrixAbstract<T> & A, T row, T column)
{
    // Just assume handle is good.
    int rows    = A.rows ();
    int columns = A.columns ();
    int lastRow    = rows    - 1;
    int lastColumn = columns - 1;
    row    = row    * rows    - (T) 0.5;
    column = column * columns - (T) 0.5;
    int r = (int) floor (row);
    int c = (int) floor (column);
    if (r < 0)
    {
        if      (c <  0         ) return A(0,0         );
        else if (c >= lastColumn) return A(0,lastColumn);
        else
        {
            T b = column - c;
            return (1 - b) * A(0,c) + b * A(0,c+1);
        }
    }
    else if (r >= lastRow)
    {
        if      (c <  0         ) return A(lastRow,0         );
        else if (c >= lastColumn) return A(lastRow,lastColumn);
        else
        {
            T b = column - c;
            return (1 - b) * A(lastRow,c) + b * A(lastRow,c+1);
        }
    }
    else
    {
        T a = row - r;
        T a1 = 1 - a;
        if      (c <  0         ) return a1 * A(r,0         ) + a * A(r+1,0         );
        else if (c >= lastColumn) return a1 * A(r,lastColumn) + a * A(r+1,lastColumn);
        else
        {
            T b = column - c;
            return   (1 - b) * (a1 * A(r,c  ) + a * A(r+1,c  ))
                   +      b  * (a1 * A(r,c+1) + a * A(r+1,c+1));
        }
    }
}

template<class T>
Matrix<T>
glFrustum (T left, T right, T bottom, T top, T near, T far)
{
    Matrix<T> result (4, 4);
    clear (result);
    result(0,0) =   2 * near / (right - left);
    result(1,1) =   2 * near / (top   - bottom);
    result(0,2) =  (right + left)   / (right - left);
    result(1,2) =  (top   + bottom) / (top   - bottom);
    result(2,2) = -(far   + near)   / (far   - near);
    result(3,2) = -1;
    result(2,3) = -2 * far * near / (far - near);
    return result;
}

template<class T>
Matrix<T>
glOrtho (T left, T right, T bottom, T top, T near, T far)
{
    Matrix<T> result (4, 4);
    clear (result);
    result(0,0) =  2 / (right - left);
    result(1,1) =  2 / (top   - bottom);
    result(2,2) = -2 / (far   - near);
    result(0,3) = -(right + left)   / (right - left);
    result(1,3) = -(top   + bottom) / (top   - bottom);
    result(2,3) = -(far   + near)   / (far   - near);
    result(3,3) = 1;

    return result;
}

template<class T>
Matrix<T>
glLookAt (const MatrixFixed<T,3,1> & eye, const MatrixFixed<T,3,1> & center, const MatrixFixed<T,3,1> & up)
{
    // Create an orthonormal frame
    Matrix<T> f = center - eye;
    f /= norm (f, (T) 2);
    Matrix<T> u = up / norm (up, (T) 2);
    Matrix<T> s = cross (f, u);
    s /= norm (s, (T) 2);
    u = cross (s, f);

    Matrix<T> R (4, 4);
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
    R(3,3) = 1;

    Matrix<T> Tr (4, 4);
    identity (Tr);
    Tr(0,3) = -eye[0];
    Tr(1,3) = -eye[1];
    Tr(2,3) = -eye[2];

    return R * Tr;
}

template<class T>
Matrix<T>
glPerspective (T fovy, T aspect, T near, T far)
{
    fovy *= M_PI / 180;  // Convert from degrees to radians.
    T f = 1 / tan (fovy / 2);

    Matrix<T> result (4, 4);
    clear (result);
    result(0,0) = f / aspect;
    result(1,1) = f;
    result(2,2) = (far + near) / (near - far);
    result(3,2) = -1;
    result(2,3) = 2 * far * near / (near - far);
    return result;
}

template<class T>
Matrix<T>
glRotate (T angle, const MatrixFixed<T,3,1> & axis)
{
    return glRotate (angle, axis[0], axis[1], axis[2]);
}

template<class T>
Matrix<T>
glRotate (T angle, T x, T y, T z)
{
    T degrees = M_PI / 180;
    T c = cos (angle * degrees);
    T s = sin (angle * degrees);
    T c1 = 1 - c;

    T l = sqrt (x * x + y * y + z * z);
    x /= l;
    y /= l;
    z /= l;

    Matrix<T> result (4, 4);
    clear (result);
    result(0,0) = x*x*c1+c;
    result(1,0) = y*x*c1+z*s;
    result(2,0) = x*z*c1-y*s;
    result(0,1) = x*y*c1-z*s;
    result(1,1) = y*y*c1+c;
    result(2,1) = y*z*c1+x*s;
    result(0,2) = x*z*c1+y*s;
    result(1,2) = y*z*c1-x*s;
    result(2,2) = z*z*c1+c;
    result(3,3) = 1;

    return result;
}

template<class T>
Matrix<T>
glScale (const MatrixFixed<T,3,1> & scales)
{
    return glScale (scales[0], scales[1], scales[2]);
}

template<class T>
Matrix<T>
glScale (T sx, T sy, T sz)
{
    Matrix<T> result (4, 4);
    clear (result);
    result(0,0) = sx;
    result(1,1) = sy;
    result(2,2) = sz;
    result(3,3) = (T) 1;
    return result;
}

template<class T>
Matrix<T>
glTranslate (const MatrixFixed<T,3,1> & position)
{
    return glTranslate (position[0], position[1], position[2]);
}

template<class T>
Matrix<T>
glTranslate (T x, T y, T z)
{
    Matrix<T> result (4, 4);
    identity (result);
    result(0,3) = x;
    result(1,3) = y;
    result(2,3) = z;
    return result;
}

#ifdef n2a_FP

template<>
int
uniform ()
{
    // exponent=-1-MSB; We promise the semi-open interval [0,1), so must never actaully reach 1.
#if RAND_MAX == 0x7FFFFFFF
    return rand ();
#elif RAND_MAX == 0x7FFF
    return rand () << 16;
#else
# error Need support for unique size of RAND_MAX
#endif
}

template<>
int
uniform (int sigma)
{
    // raw = (-1-MSB) + exponentSigma
    // goal = exponentSigma
    // shift = raw - goal = -1-MSB
    return (int64_t) uniform<int> () * sigma >> 1 + FP_MSB;
}

template<>
int
uniform (int lo, int hi, int step)
{
    // lo, hi and step all have same exponent
    int steps = (hi - lo) / step + 1;
    return lo + step * (rand () % steps);
}

// Box-Muller method (polar variant) for Gaussian random numbers.
// Although this method can return very large values, we limit it to strictly
// less than 8 std (3 bits above the decimal point). Result exponent=2-MSB.
template<>
int
gaussian ()
{
    static bool haveNextGaussian = false;
    static int nextGaussian;

    if (haveNextGaussian)
    {
        haveNextGaussian = false;
        return nextGaussian;
    }
    else
    {
        const int half  = 0x40000000; // 0.5, with exponent=-1-MSB
        const int big   = 0xFFFF;     // Too large for log(). exponent=-16
        const int small = 0x8;        // Too small for the division that creates multiplier. exponent=-16
        int v1, v2, s;
        do
        {
            v1 = uniform<int> () - half;   // u-0.5; Then implicitly double by treating exponent as -MSB rather than -1-MSB.
            v2 = uniform<int> () - half;
            // raw after multiply = -2*MSB
            // goal = -16
            // shift = raw - goal = -2*MSB + 16
            // We could keep more bits, but this approach is better conditioned.
            s = (int64_t) v1 * v1 + (int64_t) v2 * v2 >> 2 * FP_MSB - 16;
        }
        while (s >= big  ||  s <= small);
        // log (s, -16, -16) / s -- Raw result of division has exponent= -16 - -16 = 0
        // Median absolute value of result is near 1 (ln(0.5)/0.5~=-1.4), so we want center power of 0.
        // Want center bit in the middle of the word, position 15. Resulting exponent = -15.
        // We also multiply by 2, so claim exponent=-14.
        int multiplier = sqrt (((int64_t) log (s, -16, -16) << 15) / -s, -14, -16);  // multiplier has exponent=-16; v1 and v2 have exponent=-MSB
        nextGaussian = (int64_t) v2 * multiplier >> 18;  // product has exponent=-16-MSB; shift so exponent=2-MSB
        haveNextGaussian = true;
        return         (int64_t) v1 * multiplier >> 18;
    }
}

template<>
int
gaussian (int sigma)
{
    // raw = 2-MSB + X
    // goal = X
    // shift = raw - goal = 2-MSB
    return (int64_t) gaussian<int> () * sigma >> FP_MSB - 2;
}

template<>
MatrixFixed<int,3,1>
grid (int i, int nx, int ny, int nz)
{
    MatrixFixed<int,3,1> result = gridRaw<int> (i, nx, ny, nz);
    result[0] = (((int64_t) result[0] << 1) + 1 << FP_MSB) / nx;  // exponentResult = -1-MSB
    result[1] = (((int64_t) result[1] << 1) + 1 << FP_MSB) / ny;
    result[2] = (((int64_t) result[2] << 1) + 1 << FP_MSB) / nz;
    return result;
}

template<>
int
pulse (int t, int width, int period, int rise, int fall)
{
    if (t < 0) return 0;
    if (period > 0) t %= period;
    if (t < rise) return ((int64_t) t << FP_MSB) / rise;
    if (rise > 0) t -= rise;
    if (t < width  ||  std::isinf (width)) return 1 << FP_MSB;
    if (width > 0) t -= width;
    if (t < fall) return (1 << FP_MSB) - (int) (((int64_t) t << FP_MSB) / fall);
    return 0;
}

template<>
int
unitmap (const MatrixAbstract<int> & A, int row, int column)  // row and column have exponent=-MSB
{
    // Just assume handle is good.
    int rows    = A.rows ();  // exponent=0
    int columns = A.columns ();
    int lastRow    = rows    - 1;
    int lastColumn = columns - 1;
    int64_t scaledRow    = row    * rows    - (0x1 << FP_MSB - 1);   // raw exponent = 0 + -MSB = -MSB
    int64_t scaledColumn = column * columns - (0x1 << FP_MSB - 1);
    int r = scaledRow    >> FP_MSB;  // to turn raw result into integer, shift = -MSB - 0 = -MSB
    int c = scaledColumn >> FP_MSB;
    if (r < 0)
    {
        if      (c <  0         ) return A(0,0         );
        else if (c >= lastColumn) return A(0,lastColumn);
        else
        {
            int b = scaledColumn & 0x3FFFFFFF;  // fractional part, with exponent = -MSB (same as raw exponent)
            int b1 = (1 << FP_MSB) - b;
            return (int64_t) b1 * A(0,c) + (int64_t) b * A(0,c+1) >> FP_MSB;
        }
    }
    else if (r >= lastRow)
    {
        if      (c <  0         ) return A(lastRow,0         );
        else if (c >= lastColumn) return A(lastRow,lastColumn);
        else
        {
            int b = scaledColumn & 0x3FFFFFFF;
            int b1 = (1 << FP_MSB) - b;
            return (int64_t) b1 * A(lastRow,c) + (int64_t) b * A(lastRow,c+1) >> FP_MSB;
        }
    }
    else
    {
        int a = scaledRow & 0x3FFFFFFF;
        int a1 = (1 << FP_MSB) - a;
        if      (c <  0         ) return (int64_t) a1 * A(r,0         ) + (int64_t) a * A(r+1,0         ) >> FP_MSB;
        else if (c >= lastColumn) return (int64_t) a1 * A(r,lastColumn) + (int64_t) a * A(r+1,lastColumn) >> FP_MSB;
        else
        {
            int b = scaledColumn & 0x3FFFFFFF;
            int b1 = (1 << FP_MSB) - b;
            return   b1 * ((int64_t) a1 * A(r,c  ) + (int64_t) a * A(r+1,c  ) >> FP_MSB)
                   + b  * ((int64_t) a1 * A(r,c+1) + (int64_t) a * A(r+1,c+1) >> FP_MSB) >> FP_MSB;
        }
    }
}

#endif


// class Simulatable ---------------------------------------------------------

template<class T>
void
Simulatable<T>::clear ()
{
}

template<class T>
void
Simulatable<T>::init ()
{
}

template<class T>
void
Simulatable<T>::integrate ()
{
}

template<class T>
void
Simulatable<T>::update ()
{
}

template<class T>
int
Simulatable<T>::finalize ()
{
    return 0;  // Continue living.
}

template<class T>
void
Simulatable<T>::updateDerivative ()
{
}

template<class T>
void
Simulatable<T>::finalizeDerivative ()
{
}

template<class T>
void
Simulatable<T>::snapshot ()
{
}

template<class T>
void
Simulatable<T>::restore ()
{
}

template<class T>
void
Simulatable<T>::pushDerivative ()
{
}

template<class T>
void
Simulatable<T>::multiplyAddToStack (T scalar)
{
}

template<class T>
void
Simulatable<T>::multiply (T scalar)
{
}

template<class T>
void
Simulatable<T>::addToMembers ()
{
}

template<class T>
void
Simulatable<T>::path (String & result)
{
    result = "";
}


// class Part ----------------------------------------------------------------

template<class T>
void
Part<T>::die ()
{
}

template<class T>
void
Part<T>::remove ()
{
}

template<class T>
void
Part<T>::ref ()
{
}

template<class T>
void
Part<T>::deref ()
{
}

template<class T>
bool
Part<T>::isFree ()
{
    return true;
}

template<class T>
void
Part<T>::clearDuplicate ()
{
}

template<class T>
int
Part<T>::flush ()
{
    return 0;  // Default is to stay alive.
}

template<class T>
void
Part<T>::setPart (int i, Part<T> * part)
{
}

template<class T>
Part<T> *
Part<T>::getPart (int i)
{
    return 0;
}

template<class T>
int
Part<T>::getCount (int i)
{
    return 0;
}

template<class T>
void
Part<T>::getProject (int i, MatrixFixed<T,3,1> & xyz)
{
    getPart (i)->getXYZ (xyz);
}

template<class T>
int
Part<T>::mapIndex (int i, int rc)
{
    return rc;
}

template<class T>
bool
Part<T>::getNewborn ()
{
    return false;
}

template<class T>
T
Part<T>::getLive ()
{
    return 1;
}

template<class T>
T
Part<T>::getP ()
{
#   ifdef n2a_FP
    return 1 << FP_MSB2;
#   else
    return 1;
#   endif
}

template<class T>
T
Part<T>::getDt ()
{
#   ifdef n2a_FP
    return ((int64_t) 1 << -Event<int>::exponent) / 1000;  // 1ms step sizes, rather than 0.1ms like below. We are only promised (weakly) 10 bits below the decimal.
#   else
    return (T) 1e-4;
#   endif
}

template<class T>
void
Part<T>::getXYZ (MatrixFixed<T,3,1> & xyz)
{
    xyz[0] = 0;
    xyz[1] = 0;
    xyz[2] = 0;
}

template<class T>
bool
Part<T>::eventTest (int i)
{
    return false;
}

template<class T>
T
Part<T>::eventDelay (int i)
{
    return -1;  // no care
}

template<class T>
void
Part<T>::setLatch (int i)
{
}

template<class T>
void
Part<T>::finalizeEvent ()
{
}

template<class T>
void
removeMonitor (std::vector<Part<T> *> & partList, Part<T> * part)
{
    int last = partList.size () - 1;
    for (int i = last; i >= 0; i--)
    {
        if (partList[i] != part) continue;
        if (last > i) partList[i] = partList[last];
        partList.resize (last);  // removes last entry, shrinking partList by 1
        break;
    }
}


// Wrapper -------------------------------------------------------------------

template<class T>
WrapperBase<T>::WrapperBase ()
{
    // If $t' is never set in the model, then Wrapper will return this value
    // causing all its children to queue with this period.
    dt = Part<T>::getDt ();
}

template<class T>
void
WrapperBase<T>::init ()
{
    SIMULATOR enqueue (this, dt);
    population->init ();
}

template<class T>
void
WrapperBase<T>::clearDuplicate ()
{
    duplicate = false;
}

template<class T>
int
WrapperBase<T>::flush ()
{
    // flush() should only ever get called on an EventStep, so the following cast should be safe.
    if (((EventStep<T> *) SIMULATOR currentEvent)->dt != dt) return 1;  // dequeue
    if (duplicate) return 1;
    duplicate = true;
    return 0;
}

template<class T>
void
WrapperBase<T>::integrate ()
{
    population->integrate ();
}

template<class T>
void
WrapperBase<T>::update ()
{
    population->update ();
}

template<class T>
int
WrapperBase<T>::finalize ()
{
    return population->finalize ();  // We depend on explicit code in the top-level finalize() to signal when $n goes to zero.
}

template<class T>
void
WrapperBase<T>::updateDerivative ()
{
    population->updateDerivative ();
}

template<class T>
void
WrapperBase<T>::finalizeDerivative ()
{
    population->finalizeDerivative ();
}

template<class T>
void
WrapperBase<T>::snapshot ()
{
    population->snapshot ();
}

template<class T>
void
WrapperBase<T>::restore ()
{
    population->restore ();
}

template<class T>
void
WrapperBase<T>::pushDerivative ()
{
    population->pushDerivative ();
}

template<class T>
void
WrapperBase<T>::multiplyAddToStack (T scalar)
{
    population->multiplyAddToStack (scalar);
}

template<class T>
void
WrapperBase<T>::multiply (T scalar)
{
    population->multiply (scalar);
}

template<class T>
void
WrapperBase<T>::addToMembers ()
{
    population->addToMembers ();
}

template<class T>
T
WrapperBase<T>::getDt ()
{
    return dt;
}


// class ConnectPopulation ---------------------------------------------------

template<class T>
ConnectPopulation<T>::ConnectPopulation (int index, bool poll)
:   index (index),
    poll  (poll)
{
    permute         = 0;
    contained       = false;
    instances       = 0;
    deleteInstances = false;
    firstborn       = INT_MAX;
    c               = 0;
    p               = 0;

    i    = 0;  // These two values force a reset
    stop = 0;

    Max    = 0;
    Min    = 0;
    k      = 0;
    radius = 0;

    rank        = 0;
    explicitXYZ = false;
    xyz         = 0;
    NN          = 0;
    entries     = 0;
}

template<class T>
ConnectPopulation<T>::~ConnectPopulation ()
{
    if (permute) delete permute;
    else if (xyz) delete xyz;  // The innermost iterator is responsible for destructing xyz. Otherwise, permute and xyz are not related at all.
    if (entries) delete[] entries;
    if (instances  &&  deleteInstances) delete instances;
}

template<class T>
void
ConnectPopulation<T>::prepareNN ()
{
    NN = new KDTree<T> ();
    if (k > 0) NN->k = k;
    else       NN->k = INT_MAX;
    if (radius > 0) NN->radius = radius;
    //else NN->radius is INFINITY

    entries = new typename KDTree<T>::Entry[size];
    std::vector<typename KDTree<T>::Entry *> pointers;
    pointers.reserve (size);
    for (int i = 0; i < size; i++)
    {
        p = (*instances)[i];
        if (! p) continue;

        typename KDTree<T>::Entry & e = entries[i];
        e.part = p;
        // c should already be assigned by caller, but not via setProbe()
        c->setPart (index, p);
        c->getProject (index, e);
        pointers.push_back (&e);
    }
    NN->set (pointers);
}

template<class T>
bool
ConnectPopulation<T>::setProbe (Part<T> * probe)
{
    c = probe;
    bool result = false;
    if (p)
    {
        // A new connection was just made, so counts (if they are used) have been updated.
        // Step to next endpoint instance if current instance is full.
        if (Max > 0  &&  c->getCount (index) >= Max) result = true;
        else c->setPart (index, p);
    }
    if (permute  &&  permute->setProbe (c))
    {
        i = stop;  // next() will trigger a reset
        result = true;
    }
    return result;
}

template<class T>
void
ConnectPopulation<T>::reset (bool newOnly)
{
    this->newOnly = newOnly;
    if (newOnly) count = std::max (0, size - firstborn);
    else         count = size;
#   ifdef n2a_FP
    // raw multiply = -1-MSB + 0 = -1-MSB
    // goal = 0
    // shift = -1-MSB - 0 = -1-MSB
    // Could instead call multiplyRound()
    if (count > 1) i = (int) (((int64_t) uniform<T> () * (count - 1) >> FP_MSB) + 1 >> 1);  // Add 1 just below the decimal point to cause rounding. Total shift is -(MSB+1)
#   else
    if (count > 1) i = (int) round (uniform<T> () * (count - 1));
#   endif
    else           i = 0;
    stop = i + count;
}

template<class T>
bool
ConnectPopulation<T>::old ()
{
    if (p->getNewborn ()) return false;
    if (permute) return permute->old ();
    return true;
}

template<class T>
bool
ConnectPopulation<T>::next ()
{
    while (true)
    {
        if (i >= stop)  // Need to either reset or terminate, depending on whether we have something left to permute with.
        {
            if (! permute)  // We are the innermost (slowest) iterator. If contained is false, then we are also the only iterator (unary connection).
            {
                if (stop > 0) return false;  // We already reset once, so done.
                // A unary connection should only iterate over new instances, unless we're polling.
                // The innermost iterator of a multi-way connection should iterate over all instances.
                // Polling should iterate over all instances.
                reset (! contained  &&  ! poll);
            }
            else  // There is another iterator below us. If it has more items, then we should start iteration again.
            {
                if (! permute->next ()) return false;
                if (contained  ||  poll) reset (false);
                else                     reset (permute->old ());
            }
            if (count == 0) return false;  // Nothing to iterate over, so done. This test comes here because we need an initial reset (above code) before count is set.
        }

        if (NN)
        {
            for (; i < stop; i++)
            {
                p = filtered[i];  // Contains only non-null instances that satisfy the newOnly constraint (when newOnly is true).
                if (Max == 0) break;
                c->setPart (index, p);
                if (c->getCount (index) < Max) break;
            }
        }
        else if (newOnly)
        {
            for (; i < stop; i++)
            {
                p = (*instances)[i % count + firstborn];
                if (p  &&  p->getNewborn ())
                {
                    if (Max == 0) break;
                    c->setPart (index, p);
                    if (c->getCount (index) < Max) break;
                }
            }
        }
        else
        {
            for (; i < stop; i++)
            {
                p = (*instances)[i % count];
                if (p)
                {
                    if (Max == 0) break;
                    c->setPart (index, p);
                    if (c->getCount (index) < Max) break;
                }
            }
        }

        i++;
        if (p  &&  i <= stop)
        {
            if (Max == 0) c->setPart (index, p);
            if (xyz  &&  ! permute)  // Spatial filtering is on, and we are the endpoint that determines the query $xyz
            {
                // Obtain C.$xyz, the best way we can
                if (explicitXYZ) c->getXYZ (*xyz);
                else             c->getProject (index, *xyz);
            }
            return true;
        }
    }
}


// class ConnectPopulationNN -------------------------------------------------

template<class T>
ConnectPopulationNN<T>::ConnectPopulationNN (int index, bool poll)
:   ConnectPopulation<T> (index, poll)
{
}

template<class T>
ConnectPopulationNN<T>::~ConnectPopulationNN ()
{
    if (this->NN) delete this->NN;
}

template<class T>
void
ConnectPopulationNN<T>::reset (bool newOnly)
{
    assert (this->NN);
    this->newOnly = newOnly;
    std::vector<typename KDTree<T>::Entry *> result;
    this->NN->find (*this->xyz, result);
    this->count = result.size ();
    this->filtered.clear ();
    this->filtered.reserve (this->count);
    if (newOnly)
    {
        for (auto e : result) if (e->part->getNewborn ()) this->filtered.push_back (e->part);
        this->count = this->filtered.size ();
    }
    else
    {
        for (auto e : result)                             this->filtered.push_back (e->part);
    }
    this->i = 0;
    this->stop = this->count;
}


// class ConnectMatrix -------------------------------------------------------

template<class T>
ConnectMatrix<T>::ConnectMatrix (ConnectPopulation<T> * rows, ConnectPopulation<T> * cols, int rowIndex, int colIndex, IteratorNonzero<T> * it, Part<T> * dummy, Population<T> * population)
:   rows       (rows),
    cols       (cols),
    rowIndex   (rowIndex),
    colIndex   (colIndex),
    it         (it),
    dummy      (dummy),
    population (population)
{
}

template<class T>
ConnectMatrix<T>::~ConnectMatrix ()
{
    delete rows;
    delete cols;
    delete it;
    population->Population<T>::remove (dummy);
}

template<class T>
bool
ConnectMatrix<T>::setProbe (Part<T> * probe)
{
    c = probe;
    return false;
}

template<class T>
bool
ConnectMatrix<T>::next ()
{
    while (it->next ())
    {
        int a = dummy->mapIndex (rowIndex, it->row);
        int b = dummy->mapIndex (colIndex, it->column);
        if (a < 0  ||  a >= rows->size  ||  b < 0  ||  b >= cols->size) continue;
        Part<T> * row = (*rows->instances)[a];
        Part<T> * col = (*cols->instances)[b];
        if (row->getNewborn ()  ||  col->getNewborn ())
        {
            c->setPart (rowIndex, row);
            c->setPart (colIndex, col);
            return true;
        }
    }
    return false;
}


// class Population ----------------------------------------------------------

template<class T>
Part<T> *
Population<T>::allocate ()
{
    return 0;
}

template<class T>
void
Population<T>::release (Part<T> * part)
{
}

template<class T>
void
Population<T>::add (Part<T> * part)
{
}

template<class T>
void
Population<T>::remove (Part<T> * part)
{
    release (part);
}

template<class T>
void
Population<T>::resize (int n)
{
    for (int currentN = getN (); currentN < n; currentN++)
    {
        Part<T> * p = allocate ();
        add (p);
        p->init ();  // Includes setPeriod(), that actually puts part onto simulator queue.
    }
}

template<class T>
int
Population<T>::getN ()
{
    return 1;
}

template<class T>
void
Population<T>::connect ()
{
    ConnectIterator<T> * outer = getIterators (false);  // If this version of connect() is called, then poll is known to be false.
    if (! outer) return;

    Part<T> * c = allocate ();
    outer->setProbe (c);
    while (outer->next ())
    {
        T p = c->getP ();
        // Yes, we need all 3 conditions. If create is 0 or 1, we do not do a random draw, since it would have no effect.
        if (p <= 0) continue;
#       ifdef n2a_FP
        if (p < 1  &&  p < uniform<T> () >> 1 + FP_MSB2) continue;  // p exponent is -MSB/2; uniform() exponent is -1-MSB. shift = (-1-MSB) - -MSB/2 = -1 - MSB/2
#       else
        if (p < 1  &&  p < uniform<T> ()) continue;
#       endif

        add (c);
        c->init ();

        c = allocate ();
        outer->setProbe (c);
    }
    release (c);  // The last allocated connection instance doesn't get used.
    delete outer;  // Automatically deletes inner iterators as well.
}

template<class T>
void
Population<T>::clearNew ()
{
}

template<class T>
ConnectIterator<T> *
Population<T>::getIterators (bool poll)
{
    return 0;
}

template<class T>
ConnectIterator<T> *
Population<T>::getIteratorsSimple (bool poll)
{
    std::vector<ConnectPopulation<T> *> iterators;
    iterators.reserve (2);  // By far the most common case.
    bool nothingNew = true;
    int i = 0;
    while (true)
    {
        ConnectPopulation<T> * it = getIterator (i++, poll);  // Returns null if i is out of range for endpoints.
        if (! it) break;
        iterators.push_back (it);
        if (it->firstborn < it->size) nothingNew = false;
    }
    if (nothingNew  &&  ! poll)
    {
        for (auto it : iterators) delete it;
        return 0;
    }

    // Sort so that population with the most old entries is the outermost iterator.
    // That allows the most number of old entries to be skipped.
    // This is a simple in-place insertion sort ...
    int count = iterators.size ();
    if (! poll)
    {
        for (int i = 1; i < count; i++)
        {
            for (int j = i; j > 0; j--)
            {
                ConnectPopulation<T> * A = iterators[j-1];
                ConnectPopulation<T> * B = iterators[j  ];
                if (A->firstborn >= B->firstborn) break;
                iterators[j-1] = B;
                iterators[j  ] = A;
            }
        }
    }

    for (int i = 1; i < count; i++)
    {
        ConnectPopulation<T> * A = iterators[i-1];
        ConnectPopulation<T> * B = iterators[i  ];
        A->permute   = B;
        B->contained = true;
    }

    return iterators[0];
}

template<class T>
ConnectIterator<T> *
Population<T>::getIteratorsNN (bool poll)
{
    std::vector<ConnectPopulation<T> *> iterators;
    iterators.reserve (3);  // This is the largest number of endpoints we will usually have in practice.
    bool nothingNew = true;
    bool spatialFiltering = false;
    int i = 0;
    while (true)
    {
        ConnectPopulation<T> * it = getIterator (i++, poll);  // Returns null if i is out of range for endpoints.
        if (! it) break;
        iterators.push_back (it);
        if (it->firstborn < it->size) nothingNew = false;
        if (it->k > 0  ||  it->radius > 0) spatialFiltering = true;
    }
    if (nothingNew  &&  ! poll)
    {
        for (auto it : iterators) delete it;
        return 0;
    }

    // Sort so that population with the most old entries is the outermost iterator.
    // That allows the most number of old entries to be skipped.
    // This is a simple in-place insertion sort ...
    int count = iterators.size ();
    if (! poll)
    {
        for (int i = 1; i < count; i++)
        {
            for (int j = i; j > 0; j--)
            {
                ConnectPopulation<T> * A = iterators[j-1];
                ConnectPopulation<T> * B = iterators[j  ];
                if (A->firstborn >= B->firstborn) break;
                iterators[j-1] = B;
                iterators[j  ] = A;
            }
        }
    }

    if (spatialFiltering)
    {
        // Create shared $xyz value
        MatrixFixed<T,3,1> * xyz = new MatrixFixed<T,3,1>;
        for (int i = 0; i < count; i++) iterators[i]->xyz = xyz;

        // Ensure the innermost iterator be the one that best defines C.$xyz
        // If connection defines its own $xyz, then this sorting operation has no effect.
        int last = count - 1;
        ConnectPopulation<T> * A = iterators[last];
        int    bestIndex = last;
        double bestRank  = A->rank;
        for (int i = 0; i < last; i++)
        {
            A = iterators[i];
            if (A->rank > bestRank)
            {
                bestIndex = i;
                bestRank  = A->rank;
            }
        }
        if (bestIndex != last)
        {
            A = iterators[bestIndex];
            iterators.erase (iterators.begin () + bestIndex);
            iterators.push_back (A);
        }
    }

    for (int i = 1; i < count; i++)
    {
        ConnectPopulation<T> * A = iterators[i-1];
        ConnectPopulation<T> * B = iterators[i  ];
        A->permute   = B;
        B->contained = true;
        if (A->k > 0  ||  A->radius > 0)  // Note that NN structure won't be created on deepest iterator. TODO: Is this correct?
        {
            A->c = allocate ();
            A->prepareNN ();
            release (A->c);
        }
    }

    return iterators[0];
}

template<class T>
ConnectPopulation<T> *
Population<T>::getIterator (int i, bool poll)
{
    return 0;
}


// class Simulator -----------------------------------------------------------

#ifndef n2a_TLS
template<class T> SHARED Simulator<T> Simulator<T>::instance;
#endif

template<class T>
Simulator<T>::Simulator ()
{
    integrator   = 0;
    stop         = false;
    currentEvent = 0;
    after        = false;
}

template<class T>
Simulator<T>::~Simulator ()
{
    clear ();
}

template<class T>
void
Simulator<T>::clear ()
{
    // Free all non-step events still in queue.
    while (! queueEvent.empty ())
    {
        currentEvent = queueEvent.top ();
        queueEvent.pop ();
        if (! currentEvent->isStep ()) delete currentEvent;
    }
    currentEvent = 0;

    queueResize.clear ();
    std::queue<Population<T> *> ().swap (queueConnect);  // Necessary because queue lacks clear() function. Exchange contents with an empty queue. Then temporary queue object (now holding any content from queueConnect) is automagically disposed.
    queueClearNew.clear ();

    // Free all step events
    for (auto event : periods) delete event;
    periods.clear ();

    if (integrator) delete integrator;
    integrator = 0;

    for (auto it : holders) delete it;
    holders.clear ();

    stop  = false;
    after = false;
}

template<class T>
void
Simulator<T>::init (WrapperBase<T> * wrapper)
{
    EventStep<T> * event = new EventStep<T> ((T) 0, wrapper->dt);
    currentEvent = event;
    periods.push_back (event);

    // Init cycle
    wrapper->init ();
    updatePopulations ();
    event->requeue ();
}

template<class T>
void
Simulator<T>::run (T until)
{
#   ifdef _WIN32
    // Handle graceful shutdown on Windows.
    int64_t lastCheck = 0;  // Wasting extra bits so we will be ready for the end of Unix time in 2038. By then N2A will have taken over the world, but will Windows still be around?
#   endif

    // Regular simulation
    while (! queueEvent.empty ()  &&  ! stop)
    {
        currentEvent = queueEvent.top ();
        if (currentEvent->t >= until) return;  // Event remains in queue, so a subsequent call to run() will resume seamlessly.
        queueEvent.pop ();
        currentEvent->run ();

#       ifdef _WIN32
        // Since time() is in seconds, simply checking for a difference is sufficient
        // to throttle our check rate to once per second.
        int64_t wallTime = (int64_t) time (0);
        if (wallTime != lastCheck)
        {
            if (GetFileAttributes ("finished") != INVALID_FILE_ATTRIBUTES) break;
            lastCheck = wallTime;
        }
#       endif
    }
}

template<class T>
void
Simulator<T>::updatePopulations ()
{
    // Resize populations that have requested it
    for (auto it : queueResize) it.first->resize (it.second);
    queueResize.clear ();

    // Evaluate connection populations that have requested it
    while (! queueConnect.empty ())
    {
        queueConnect.front ()->connect ();
        queueConnect.pop ();
    }

    // Clear new flag from populations that have requested it
    for (auto it : queueClearNew) it->clearNew ();
    queueClearNew.clear ();
}

template<class T>
void
Simulator<T>::enqueue (Part<T> * part, T dt)
{
    // Find a matching event.
    // Could use a binary search here, but it's not worth the complexity.
    // In general, there will only be 1 or 2 periods.
    int index = 0;
    int count = periods.size ();
    while (index < count  &&  periods[index]->dt < dt) index++;

    EventStep<T> * event;
    if (index < count  &&  periods[index]->dt == dt)
    {
        event = periods[index];
    }
    else
    {
        // Maintains "periods" in ascending order.
        event = new EventStep<T> (currentEvent->t + dt, dt);
        periods.insert (periods.begin () + index, event);
        queueEvent.push (event);
    }
    event->enqueue (part);
    part->ref ();  // The queue counts as a user of the part.
}

template<class T>
void
Simulator<T>::linger (T dt)
{
    int size = periods.size ();
    for (int i = 0; i < size; i++)
    {
        EventStep<T> * event = periods[i];
        if (event->dt != dt) continue;

        if (event->countLinger++ >= EventStep<T>::threshold) event->flush ();
        return;
    }
}

template<class T>
void
Simulator<T>::removePeriod (EventStep<T> * event)
{
    typename std::vector<EventStep<T> *>::iterator it;
    for (it = periods.begin (); it != periods.end (); it++)
    {
        if (*it == event)
        {
            periods.erase (it);
            break;
        }
    }
    delete event;  // Events still in periods at end will get deleted by dtor.
}

template<class T>
void
Simulator<T>::resize (Population<T> * population, int n)
{
    queueResize.push_back (std::make_pair (population, n));
}

template<class T>
void
Simulator<T>::connect (Population<T> * population)
{
    queueConnect.push (population);
}

template<class T>
void
Simulator<T>::clearNew (Population<T> * population)
{
    queueClearNew.push_back (population);
}

template<class T>
Holder *
Simulator<T>::getHolder (const String & fileName, Holder * oldHandle)
{
    std::vector<Holder *>::iterator it;
    if (oldHandle)
    {
        if (oldHandle->fileName == fileName) return oldHandle;
        for (it = holders.begin (); it != holders.end (); it++)
        {
            if (*it == oldHandle)
            {
                holders.erase (it);
                delete *it;
                break;
            }
        }
    }
    for (it = holders.begin (); it != holders.end (); it++)
    {
        if ((*it)->fileName == fileName) return *it;
    }
    return 0;
}


// class Euler ---------------------------------------------------------------

template<class T>
void
Euler<T>::run (Event<T> & event)
{
    event.visit ([](Visitor<T> * visitor)
    {
        visitor->part->integrate ();
    });
}


// class RungeKutta ----------------------------------------------------------

template<class T>
void
RungeKutta<T>::run (Event<T> & event)
{
    // k1
    event.visit ([](Visitor<T> * visitor)
    {
        visitor->part->snapshot ();
        visitor->part->pushDerivative ();
    });

    // k2 and k3
    EventStep<T> & es = (EventStep<T> &) event;
    T t  = es.t;  // Save current values of t and dt
    T dt = es.dt;
    es.dt /= 2;
    es.t  -= es.dt;  // t is the current point in time, so we must look backward half a timestep
    for (int i = 0; i < 2; i++)
    {
        event.visit ([](Visitor<T> * visitor)
        {
            visitor->part->integrate ();
        });
        event.visit ([](Visitor<T> * visitor)
        {
            visitor->part->updateDerivative ();
        });
        event.visit ([](Visitor<T> * visitor)
        {
            visitor->part->finalizeDerivative ();
            visitor->part->multiplyAddToStack (2.0f);
        });
    }
    es.dt = dt;  // restore original values
    es.t  = t;

    // k4
    event.visit ([](Visitor<T> * visitor)
    {
        visitor->part->integrate ();
    });
    event.visit ([](Visitor<T> * visitor)
    {
        visitor->part->updateDerivative ();
    });
    event.visit ([](Visitor<T> * visitor)
    {
        visitor->part->finalizeDerivative ();
        visitor->part->addToMembers ();  // clears stackDerivative
    });

    // finish
    event.visit ([](Visitor<T> * visitor)
    {
        visitor->part->multiply ((T) 1 / 6);
    });
    event.visit ([](Visitor<T> * visitor)
    {
        visitor->part->integrate ();
    });
    event.visit ([](Visitor<T> * visitor)
    {
        visitor->part->restore ();
    });
}

#ifdef n2a_FP

template<>
void
RungeKutta<int>::run (Event<int> & event)
{
    // k1
    event.visit ([](Visitor<int> * visitor)
    {
        visitor->part->snapshot ();
        visitor->part->pushDerivative ();
    });

    // k2 and k3
    EventStep<int> & es = (EventStep<int> &) event;
    int t  = es.t;  // Save current values of t and dt
    int dt = es.dt;
    es.dt >>= 1;  // divide by 2
    es.t   -= es.dt;  // t is the current point in time, so we must look backward half a timestep
    for (int i = 0; i < 2; i++)
    {
        event.visit ([](Visitor<int> * visitor)
        {
            visitor->part->integrate ();
        });
        event.visit ([](Visitor<int> * visitor)
        {
            visitor->part->updateDerivative ();
        });
        event.visit ([](Visitor<int> * visitor)
        {
            visitor->part->finalizeDerivative ();
            visitor->part->multiplyAddToStack (2 << FP_MSB - 1);  // exponent=1-MSB, just enough to hold the values used by RK4
        });
    }
    es.dt = dt;  // restore original values
    es.t  = t;

    // k4
    event.visit ([](Visitor<int> * visitor)
    {
        visitor->part->integrate ();
    });
    event.visit ([](Visitor<int> * visitor)
    {
        visitor->part->updateDerivative ();
    });
    event.visit ([](Visitor<int> * visitor)
    {
        visitor->part->finalizeDerivative ();
        visitor->part->addToMembers ();  // clears stackDerivative
    });

    // finish
    event.visit ([](Visitor<int> * visitor)
    {
        visitor->part->multiply ((1 << FP_MSB - 1) / 6);
    });
    event.visit ([](Visitor<int> * visitor)
    {
        visitor->part->integrate ();
    });
    event.visit ([](Visitor<int> * visitor)
    {
        visitor->part->restore ();
    });
}

#endif


// class Event ---------------------------------------------------------------

#ifdef n2a_FP
template<class T> int Event<T>::exponent;
#endif

template<class T>
bool
Event<T>::isStep () const
{
    return false;
}


// class EventStep -----------------------------------------------------------

template<class T> uint32_t EventStep<T>::threshold = 1000000;

template<class T>
EventStep<T>::EventStep (T t, T dt)
:   dt (dt)
{
    this->t = t;
    visitors.push_back (new VisitorStep<T> ());
    countLinger = 0;
}

template<class T>
EventStep<T>::~EventStep ()
{
    for (auto it : visitors) delete it;
}

template<class T>
bool
EventStep<T>::isStep () const
{
    return true;
}

template<class T>
void
EventStep<T>::run ()
{
    // Update parts
    if (countLinger) flush ();
    SIMULATOR integrator->run (*this);
    visit ([](Visitor<T> * visitor)
    {
        visitor->part->update ();
    });
    visit ([](Visitor<T> * visitor)
    {
        Part<T> * p = visitor->part;  // for convenience
        int dispose = p->finalize ();
        if (dispose > 0)  // Remove from queue.
        {
            // We must manage v->index carefully so we don't call finalize() on any parts added
            // during this pass, for example by $type split. (They should only have init() called.)
            VisitorStep<T> * v = (VisitorStep<T> *) visitor;
            int last = v->queue.size () - 1;
            if (v->index < last)
            {
                v->queue[v->index] = v->queue[last];
                if (v->last == last)
                {
                    // The next current element is one that existed before any adds done by finalize().
                    // It still needs to be processed.
                    v->index--;  // Repeat processing at current index. This decrement neutralizes the increment in the for loop of EventStep::run().
                    v->last--;
                }
                // else v->last < last, which means there are some newly-added parts which should not be finalized.
                // In this case, a new part gets moved into v->queue[v->index], and we do not want to process it.
                // v->index remains untouched, so it increments past this element.
                // Notice that we never have v->last > last.
            }
            v->queue.pop_back ();
            p->deref ();  // Balance ref added when part was enqueued.
        }
        if (dispose > 1)  // Also remove from population.
        {
            p->remove ();
        }
    });
    if (SIMULATOR stop) return;

    SIMULATOR updatePopulations ();
    requeue ();
}

template<class T>
void
EventStep<T>::visit (const std::function<void (Visitor<T> * visitor)> & f)
{
    visitors[0]->visit (f);
}

template<class T>
void
EventStep<T>::flush ()
{
    visit ([](Visitor<T> * visitor)
    {
        visitor->part->clearDuplicate ();
    });

    // This is a light-weight version of the finalize pass in run().
    visit ([](Visitor<T> * visitor)
    {
        Part<T> * p = visitor->part;  // for convenience
        if (p->flush ())  // Remove from queue.
        {
            VisitorStep<T> * v = (VisitorStep<T> *) visitor;
            if (v->index < v->last)
            {
                v->queue[v->index] = v->queue[v->last];
                v->index--;  // To repeat at current element.
            }
            v->queue.pop_back ();
            v->last--;    // Because the vector got shorter.
            p->deref ();  // Balance ref added when part was enqueued.
        }
        // If part is dead, no need to call p->remove(). It was called when part died.
    });

    countLinger = 0;
}

template<class T>
void
EventStep<T>::requeue ()
{
    if (visitors[0]->queue.empty ())  // Our list of instances is empty, so die.
    {
        SIMULATOR removePeriod (this);
    }
    else  // Still have instances, so re-queue event.
    {
        this->t += dt;
        SIMULATOR queueEvent.push (this);
    }
}

template<class T>
void
EventStep<T>::enqueue (Part<T> * part)
{
    visitors[0]->queue.push_back (part);
}


// class EventSpikeSingle ----------------------------------------------------

template<class T>
void
EventSpikeSingle<T>::run ()
{
    target->setLatch (this->latch);

    SIMULATOR integrator->run (*this);
    visit ([](Visitor<T> * visitor)
    {
        Part<T> * p = visitor->part;
        p->update ();
        T dt = p->getDt ();
        int dispose = p->finalize ();
        if (dispose > 0) SIMULATOR linger (dt);
        if (dispose > 1) p->remove ();
        p->finalizeEvent ();
    });

    delete this;
}

template<class T>
void
EventSpikeSingle<T>::visit (const std::function<void (Visitor<T> * visitor)> & f)
{
    Visitor<T> v (target);
    f (&v);
}


// class EventSpikeSingleLatch -----------------------------------------------

template<class T>
void
EventSpikeSingleLatch<T>::run ()
{
    this->target->setLatch (this->latch);
    delete this;
}


// class EventSpikeMulti -----------------------------------------------------

template<class T>
void
EventSpikeMulti<T>::run ()
{
    setLatch ();

    SIMULATOR integrator->run (*this);
    visit ([](Visitor<T> * visitor)
    {
        visitor->part->update ();
    });
    visit ([](Visitor<T> * visitor)
    {
        Part<T> * p = visitor->part;
        T dt = p->getDt ();
        int dispose = p->finalize ();
        if (dispose > 0) SIMULATOR linger (dt);  // It can wait till next EventStep to leave queue.
        if (dispose > 1) p->remove ();
        p->finalizeEvent ();  // TODO: Is is possible to get by without this?
    });

    delete this;
}

template<class T>
void
EventSpikeMulti<T>::visit (const std::function<void (Visitor<T> * visitor)> & f)
{
    VisitorSpikeMulti<T> v (this);
    v.visit (f);
}

template<class T>
void
EventSpikeMulti<T>::setLatch ()
{
    for (int i = targets->size () - 1; i >= 0; i--)
    {
        (*targets)[i]->setLatch (EventSpike<T>::latch);
    }
}


// class EventSpikeMultiLatch ------------------------------------------------

template<class T>
void
EventSpikeMultiLatch<T>::run ()
{
    this->setLatch ();
    delete this;
}


// class Visitor -------------------------------------------------------------

template<class T>
Visitor<T>::Visitor (Part<T> * part)
:   part (part)
{
}

template<class T>
void
Visitor<T>::visit (const std::function<void (Visitor<T> * visitor)> & f)
{
    f (this);
}


// class VisitorStep ---------------------------------------------------------

template<class T>
VisitorStep<T>::~VisitorStep ()
{
    // Flush any lingering instances. These get moved to their respective population dead list.
    // Note that singletons don't get moved to a dead list because they are a direct member of their population object.
    for (auto p : queue) p->remove ();
}

template<class T>
void
VisitorStep<T>::visit (const std::function<void (Visitor<T> * visitor)> & f)
{
    last = queue.size () - 1;
    for (index = 0; index <= last; index++)  // Notice that queue can shrink or grow due to finalize().
    {
        this->part = queue[index];
        f (this);
    }
}


// class VisitorSpikeMulti ---------------------------------------------------

template<class T>
VisitorSpikeMulti<T>::VisitorSpikeMulti (EventSpikeMulti<T> * event)
:   event (event)
{
}

template<class T>
void
VisitorSpikeMulti<T>::visit (const std::function<void (Visitor<T> * visitor)> & f)
{
    for (auto target : *event->targets)
    {
        this->part = target;
        f (this);
    }
}


// class DelayBuffer ---------------------------------------------------------

template<class T>
void
DelayBuffer<T>::clear ()
{
    value = NAN;
    buffer.clear ();
}

template<class T>
T
DelayBuffer<T>::step (T now, T delay, T futureValue, T initialValue)
{
    if (std::isnan (value)) value = initialValue;
    buffer.emplace (now + delay, futureValue);
    while (true)
    {
        typename std::map<T,T>::iterator it = buffer.begin ();
        if (it->first > now) break;
        value = it->second;
        buffer.erase (it);
        if (buffer.empty ()) break;
    }
    return value;
}


#endif
