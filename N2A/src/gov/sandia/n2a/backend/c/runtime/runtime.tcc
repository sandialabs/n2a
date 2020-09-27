#ifndef n2a_runtime_tcc
#define n2a_runtime_tcc


#include "runtime.h"
#include "matrix.h"
#include <limits.h>
#ifdef n2a_FP
# include "fixedpoint.h"
#endif


// General functions ---------------------------------------------------------

template<class T>
T
uniform ()
{
    return (T) rand () / RAND_MAX;
}

template<class T>
T
uniform (T sigma)
{
    return sigma * rand () / RAND_MAX;
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

#ifdef n2a_FP

template<>
int
uniform ()
{
#if RAND_MAX == 0x7FFFFFFF
    return rand ();  // exponent=-1; This version can never actually reach 1, only [0,1). However, this shouldn't make any algorithmic difference to callers.
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
    return (int64_t) uniform<int> () * sigma >> 31;  // shift = -1 - MSB
}

// Box-Muller method (polar variant) for Gaussian random numbers.
// Although this method can return very large values, we limit it to strictly
// less than 8 std (3 bits above the decimal point). Result exponent=2.
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
        const int half  = 0x40000000; // 0.5, with exponent=-1
        const int one   = 0x10000;    // exponent=14
        const int small = 0x8;        // Too small for the division that creates multiplier. exponent=14
        int v1, v2, s;
        do
        {
            v1 = half - uniform<int> ();   // 0.5 - u; Then implicitly double by treating exponent as 0 rather than -1.
            v2 = half - uniform<int> ();
            // Squaring v puts exponent=0 at bit 60
            // Down-shift puts exponent=14 at bit 30.
            // We could keep more bits, but this approach is better conditioned.
            s = (int64_t) v1 * v1 + (int64_t) v2 * v2 >> 44;  // MSB + 14
        }
        while (s >= one || s <= small);
        // log (s, 14, 14) / s -- Raw result of division has exponent=MSB
        // Median absolute value of result is near 1 (ln(0.5)/0.5~=-1.4), so we want center power of 0, for exponent=15.
        // Ideal shift is 15(=MSB-15), to put exponent=15 at bit 30.
        // We also multiply by 2, so claim exponent=16.
        int multiplier = sqrt (((int64_t) log (s, 14, 14) << 15) / -s, 16, 14);  // multiplier has exponent=14; v1 and v2 have exponent=0
        nextGaussian = (int64_t) v2 * multiplier >> 18;  // MSB-12; product has exponent=14 at bit 60; shift so exponent=2 at bit 30
        haveNextGaussian = true;
        return         (int64_t) v1 * multiplier >> 18;
    }
}

template<>
int
gaussian (int sigma)
{
    return (int64_t) gaussian<int> () * sigma >> 28;   // ones bit of gaussian draw is at position MSB - 2
}

template<>
MatrixFixed<int,3,1>
grid (int i, int nx, int ny, int nz)
{
    MatrixFixed<int,3,1> result = gridRaw<int> (i, nx, ny, nz);
    result[0] = (((int64_t) result[0] << 1) + 1 << FP_MSB) / nx;  // exponentResult = -1
    result[1] = (((int64_t) result[1] << 1) + 1 << FP_MSB) / ny;
    result[2] = (((int64_t) result[2] << 1) + 1 << FP_MSB) / nz;
    return result;
}

#endif

namespace n2a
{
#   ifdef n2a_FP

    inline bool isnan (int a)
    {
        return a == NAN;
    }

    inline bool isinf (int a)
    {
        return abs (a) == INFINITY;
    }

#   else

    using std::isnan;
    using std::isinf;

#   endif    
}


// class Simulatable ---------------------------------------------------------

template<class T>
Simulatable<T>::~Simulatable ()
{
}

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
bool
Simulatable<T>::finalize ()
{
    return true;
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

template<class T>
void
Simulatable<T>::getNamedValue (const String & name, String & value)
{
}


// class Part ----------------------------------------------------------------

template<class T>
void
Part<T>::setPrevious (Part<T> * previous)
{
}

template<class T>
void
Part<T>::setVisitor (VisitorStep<T> * visitor)
{
}

template<class T>
EventStep<T> *
Part<T>::getEvent ()
{
    return (EventStep<T> *) Simulator<T>::instance.currentEvent;
}

template<class T>
void
Part<T>::die ()
{
}

template<class T>
void
Part<T>::enterSimulation ()
{
}

template<class T>
void
Part<T>::leaveSimulation ()
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
    return 0;
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
    return 1;
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
    typename std::vector<Part<T> *>::iterator it;
    for (it = partList.begin (); it != partList.end (); it++)
    {
        if (*it == part)
        {
            *it = 0;
            break;
        }
    }
}


// class PartTime ------------------------------------------------------------

template<class T>
void
PartTime<T>::setPrevious (Part<T> * previous)
{
    this->previous = previous;
}

template<class T>
void
PartTime<T>::setVisitor (VisitorStep<T> * visitor)
{
    this->visitor = visitor;
}

template<class T>
EventStep<T> *
PartTime<T>::getEvent ()
{
    return (EventStep<T> *) visitor->event;
}

template<class T>
void
PartTime<T>::dequeue ()
{
    // TODO: Need mutex on visitor when modifying its queue, even if it is not part of currentEvent.
    if (Simulator<T>::instance.currentEvent == visitor->event)
    {
        // Avoid damaging iterator in visitor
        if (visitor->previous == this) visitor->previous = this->next;
    }
    if (this->next) this->next->setPrevious (previous);
    previous->next = this->next;
}

template<class T>
void
PartTime<T>::setPeriod (T dt)
{
    dequeue ();
    Simulator<T>::instance.enqueue (this, dt);
}


// Wrapper -------------------------------------------------------------------

template<class T>
void
WrapperBase<T>::init ()
{
    population->init ();
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
bool
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


// class ConnectIterator -----------------------------------------------------

template<class T>
ConnectIterator<T>::~ConnectIterator ()
{
}


// class ConnectPopulation ---------------------------------------------------

template<class T>
ConnectPopulation<T>::ConnectPopulation (int index)
:   index (index)
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
        c->setPart (index, p);
        // A new connection was just made, so counts (if they are used) have been updated.
        // Step to next endpoint instance if current instance is full.
        if (Max > 0  &&  c->getCount (index) >= Max) result = true;
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
    if (newOnly) count = size - firstborn;
    else         count = size;
#   ifdef n2a_FP
    // raw multiply = -1+MSB-MSB = -1
    // shift = -1 - MSB
    if (count > 1) i = (int) round ((int64_t) uniform<T> () * (count - 1) >> FP_MSB + 1);
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
            if (! permute)
            {
                if (stop > 0) return false;  // We already reset once, so done.
                // A unary connection should only iterate over new instances.
                // The innermost (slowest) iterator of a multi-way connection should iterate over all instances.
                reset (! contained);
            }
            else
            {
                if (! permute->next ()) return false;
                if (contained) reset (false);
                else           reset (permute->old ());
            }
        }

        if (NN)
        {
            for (; i < stop; i++)
            {
                p = filtered[i];
                if (p->getNewborn ())
                {
                    if (Max == 0) break;
                    c->setPart (index, p);
                    if (c->getCount (index) < Max) break;
                }
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
ConnectPopulationNN<T>::ConnectPopulationNN (int index)
:   ConnectPopulation<T> (index)
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
    this->newOnly = newOnly;
    if (this->NN)
    {
        std::vector<typename KDTree<T>::Entry *> result;
        this->NN->find (*this->xyz, result);
        this->count = result.size ();
        this->filtered.resize (this->count);
        int j = 0;
        for (auto e : result) this->filtered[j++] = e->part;
        this->i = 0;
    }
    else
    {
        if (newOnly) this->count = this->size - this->firstborn;
        else         this->count = this->size;
#       ifdef n2a_FP
        if (this->count > 1) this->i = (int) round ((int64_t) uniform<T> () * (this->count - 1) >> FP_MSB + 1);
#       else
        if (this->count > 1) this->i = (int) round (uniform<T> () * (this->count - 1));
#       endif
        else                 this->i = 0;
    }
    this->stop = this->i + this->count;
}


// class ConnectMatrix -------------------------------------------------------

template<class T>
ConnectMatrix<T>::ConnectMatrix (ConnectPopulation<T> * rows, ConnectPopulation<T> * cols, IteratorNonzero<T> * it, Part<T> * dummy)
:   rows  (rows),
    cols  (cols),
    it    (it),
    dummy (dummy)
{
    dummy->setPart (0, (*rows->instances)[0]);
    dummy->setPart (1, (*cols->instances)[0]);
}

template<class T>
ConnectMatrix<T>::~ConnectMatrix ()
{
    delete rows;
    delete cols;
    delete it;
    delete dummy;
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
        int a = dummy->mapIndex (0, it->row);
        int b = dummy->mapIndex (1, it->column);
        if (a < 0  ||  a >= rows->size  ||  b < 0  ||  b >= cols->size) continue;
        Part<T> * A = (*rows->instances)[a];
        Part<T> * B = (*cols->instances)[b];
        if (A->getNewborn ()  ||  B->getNewborn ())
        {
            c->setPart (0, A);
            c->setPart (1, B);
            return true;
        }
    }
    return false;
}


// class Population ----------------------------------------------------------

template<class T>
Population<T>::Population ()
{
    dead = 0;
}

template<class T>
Population<T>::~Population ()
{
    Part<T> * p = dead;
    while (p)
    {
        Part<T> * next = p->next;
        delete p;
        p = next;
    }
}

template<class T>
Part<T> *
Population<T>::create ()
{
    return 0;
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
    part->next = dead;
    dead = part;
}

template<class T>
Part<T> *
Population<T>::allocate ()
{
    Part<T> * result = 0;

    Part<T> ** p = &dead;
    while (*p)
    {
        if ((*p)->isFree ())
        {
            result = *p;
            result->clear ();
            *p = (*p)->next;  // remove from dead
            break;
        }
        p = & (*p)->next;
    }

    if (! result) result = create ();
    add (result);

    return result;
}

template<class T>
void
Population<T>::resize (int n)
{
    EventStep<T> * event = container->getEvent ();
    for (int currentN = getN (); currentN < n; currentN++)
    {
        Part<T> * p = allocate ();
        p->enterSimulation ();
        event->enqueue (p);
        p->init ();
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
    ConnectIterator<T> * outer = getIterators ();
    if (! outer) return;

    EventStep<T> * event = container->getEvent ();
    Part<T> * c = create ();
    outer->setProbe (c);
    while (outer->next ())
    {
        T create = c->getP ();
        // Yes, we need all 3 conditions. If create is 0 or 1, we do not do a random draw, since it would have no effect.
        if (create <= 0) continue;
#       ifdef n2a_FP
        if (create < 1  &&  create < uniform<T> () >> 16) continue;
#       else
        if (create < 1  &&  create < uniform<T> ()) continue;
#       endif

        c->enterSimulation ();
        event->enqueue (c);
        c->init ();

        c = this->create ();
        outer->setProbe (c);
    }
    delete c;  // The last created connection instance doesn't get used.
    delete outer;  // Automatically deletes inner iterators as well.
}

template<class T>
void
Population<T>::clearNew ()
{
}

template<class T>
ConnectIterator<T> *
Population<T>::getIterators ()
{
    return 0;
}

template<class T>
ConnectIterator<T> *
Population<T>::getIteratorsSimple ()
{
    std::vector<ConnectPopulation<T> *> iterators;
    iterators.reserve (3);  // This is the largest number of endpoints we will usually have in practice.
    bool nothingNew = true;
    int i = 0;
    while (true)
    {
        ConnectPopulation<T> * it = getIterator (i++);  // Returns null if i is out of range for endpoints.
        if (! it) break;
        iterators.push_back (it);
        if (it->firstborn < it->size) nothingNew = false;
    }
    if (nothingNew) return 0;

    // Sort so that population with the most old entries is the outermost iterator.
    // That allows the most number of old entries to be skipped.
    // This is a simple in-place insertion sort ...
    int count = iterators.size ();
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
Population<T>::getIteratorsNN ()
{
    std::vector<ConnectPopulation<T> *> iterators;
    iterators.reserve (3);  // This is the largest number of endpoints we will usually have in practice.
    bool nothingNew = true;
    bool spatialFiltering = false;
    int i = 0;
    while (true)
    {
        ConnectPopulation<T> * it = getIterator (i++);  // Returns null if i is out of range for endpoints.
        if (! it) break;
        iterators.push_back (it);
        if (it->firstborn < it->size) nothingNew = false;
        if (it->k > 0  ||  it->radius > 0) spatialFiltering = true;
    }
    if (nothingNew) return 0;

    // Sort so that population with the most old entries is the outermost iterator.
    // That allows the most number of old entries to be skipped.
    // This is a simple in-place insertion sort ...
    int count = iterators.size ();
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
            A->c = create ();
            A->prepareNN ();
            delete A->c;
        }
    }

    return iterators[0];
}

template<class T>
ConnectPopulation<T> *
Population<T>::getIterator (int i)
{
    return 0;
}


// class Simulator -----------------------------------------------------------

template<class T> Simulator<T> Simulator<T>::instance;

template<class T>
Simulator<T>::Simulator ()
{
    integrator = 0;
    stop = false;

#   ifdef n2a_FP
    EventStep<T> * event = new EventStep<T> (0, (1 << FP_MSB) / 10000);  // Works for exponentTime=0. For any other case, it is necessary for top-level part to call setPeriod().
#   else
    EventStep<T> * event = new EventStep<T> ((T) 0, (T) 1e-4);
#   endif
    currentEvent = event;
    periods.push_back (event);
}

template<class T>
Simulator<T>::~Simulator ()
{
    for (auto event : periods) delete event;
    if (integrator) delete integrator;
}

template<class T>
void
Simulator<T>::run (WrapperBase<T> & wrapper)
{
    // Init cycle
    EventStep<T> * event = (EventStep<T> *) currentEvent;
    event->enqueue (&wrapper);  // no need for wrapper->enterSimulation()
    wrapper.init ();
    updatePopulations ();
    event->requeue ();  // Only reinserts self if not empty.

    // Regular simulation
    while (! queueEvent.empty ()  &&  ! stop)
    {
        currentEvent = queueEvent.top ();
        queueEvent.pop ();
        currentEvent->run ();
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
    // find a matching event
    int index = 0;
    int count = periods.size ();
    for (; index < count; index++)
    {
        if (periods[index]->dt >= dt) break;
    }

    EventStep<T> * event;
    if (index < count  &&  periods[index]->dt == dt)
    {
        event = periods[index];
    }
    else
    {
        event = new EventStep<T> (currentEvent->t + dt, dt);
        periods.insert (periods.begin () + index, event);
        queueEvent.push (event);
    }
    event->enqueue (part);
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
            visitor->part->multiplyAddToStack (2 << FP_MSB - 1);  // exponent=1, just enough to hold the values used by RK4
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
Event<T>::~Event ()
{
}

template<class T>
bool
Event<T>::isStep () const
{
    return false;
}


// class EventStep -----------------------------------------------------------

template<class T>
EventStep<T>::EventStep (T t, T dt)
:   dt (dt)
{
    this->t = t;
    visitors.push_back (new VisitorStep<T> (this));
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
    Simulator<T>::instance.integrator->run (*this);
    visit ([](Visitor<T> * visitor)
    {
        visitor->part->update ();
    });
    visit ([](Visitor<T> * visitor)
    {
        if (! visitor->part->finalize ())
        {
            VisitorStep<T> * v = (VisitorStep<T> *) visitor;
            Part<T> * p = visitor->part;  // for convenience
            if (p->next) p->next->setPrevious (v->previous);
            v->previous->next = p->next;
            p->leaveSimulation ();
        }
    });

    Simulator<T>::instance.updatePopulations ();
    requeue ();
}

template<class T>
void
EventStep<T>::visit (std::function<void (Visitor<T> * visitor)> f)
{
    visitors[0]->visit (f);
}

template<class T>
void
EventStep<T>::requeue ()
{
    if (visitors[0]->queue.next)  // still have instances, so re-queue event
    {
        this->t += dt;
        Simulator<T>::instance.queueEvent.push (this);
    }
    else  // our list of instances is empty, so die
    {
        Simulator<T>::instance.removePeriod (this);
    }
}

template<class T>
void
EventStep<T>::enqueue (Part<T> * part)
{
    visitors[0]->enqueue (part);
}


// class EventSpikeSingle ----------------------------------------------------

template<class T>
void
EventSpikeSingle<T>::run ()
{
    target->setLatch (this->latch);

    Simulator<T>::instance.integrator->run (*this);
    visit ([](Visitor<T> * visitor)
    {
        visitor->part->update ();
        visitor->part->finalize ();
        visitor->part->finalizeEvent ();
    });

    delete this;
}

template<class T>
void
EventSpikeSingle<T>::visit (std::function<void (Visitor<T> * visitor)> f)
{
    Visitor<T> v (this, target);
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

    Simulator<T>::instance.integrator->run (*this);
    visit ([](Visitor<T> * visitor)
    {
        visitor->part->update ();
    });
    visit ([](Visitor<T> * visitor)
    {
        visitor->part->finalize ();
        visitor->part->finalizeEvent ();
        // A part could die during event processing, but it can wait till next EventStep to leave queue.
    });

    delete this;
}

template<class T>
void
EventSpikeMulti<T>::visit (std::function<void (Visitor<T> * visitor)> f)
{
    VisitorSpikeMulti<T> v (this);
    v.visit (f);
}

template<class T>
void
EventSpikeMulti<T>::setLatch ()
{
    int i = 0;
    int last = targets->size () - 1;
    while (i <= last)
    {
        Part<T> * target = (*targets)[i];
        if (target)
        {
            target->setLatch (EventSpike<T>::latch);
        }
        else
        {
            (*targets)[i] = (*targets)[last--];
        }
        i++;  // can go past last, but this will cause no harm.
    }
    if ((*targets)[last]) targets->resize (last + 1);
    else                  targets->resize (last);
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
Visitor<T>::Visitor (Event<T> * event, Part<T> * part)
:   event (event),
    part (part)
{
}

template<class T>
void
Visitor<T>::visit (std::function<void (Visitor<T> * visitor)> f)
{
    f (this);
}


// class VisitorStep ---------------------------------------------------------

template<class T>
VisitorStep<T>::VisitorStep (EventStep<T> * event)
:   Visitor<T> (event)
{
    queue.next = 0;
    previous = 0;
}

template<class T>
void
VisitorStep<T>::visit (std::function<void (Visitor<T> * visitor)> f)
{
    previous = &queue;
    while (previous->next)
    {
        this->part = previous->next;
        f (this);
        if (previous->next == this->part) previous = this->part;  // Normal advance through list. Check is necessary in case part dequeued while f() was running.
    }
}

template<class T>
void
VisitorStep<T>::enqueue (Part<T> * newPart)
{
    newPart->setVisitor (this);
    if (queue.next) queue.next->setPrevious (newPart);
    newPart->setPrevious (&queue);
    newPart->next = queue.next;
    queue.next = newPart;
}


// class VisitorSpikeMulti ---------------------------------------------------

template<class T>
VisitorSpikeMulti<T>::VisitorSpikeMulti (EventSpikeMulti<T> * event)
:   Visitor<T> (event)
{
}

template<class T>
void
VisitorSpikeMulti<T>::visit (std::function<void (Visitor<T> * visitor)> f)
{
    EventSpikeMulti<T> * e = (EventSpikeMulti<T> *) this->event;
    for (auto target : *e->targets)
    {
        this->part = target;
        f (this);
    }
}


// class DelayBuffer ---------------------------------------------------------

template<class T>
DelayBuffer<T>::DelayBuffer ()
{
    value = NAN;
}

template<class T>
T
DelayBuffer<T>::step (T now, T delay, T futureValue, T initialValue)
{
    if (n2a::isnan (value)) value = initialValue;
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
