/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_runtime_h
#define n2a_runtime_h


#include "nosys.h"
#include "holder.h"
#include "mystring.h"
#include "KDTree.h"

#include <functional>
#include <queue>
#include <vector>
#include <map>
#include <mutex>

#include "shared.h"

#ifdef n2a_TLS
# define SIMULATOR Simulator<T>::instance->
#else
# define SIMULATOR Simulator<T>::instance.
#endif


// General functions ---------------------------------------------------------
// See the N2A language reference for details.

template<class T> inline T modFloor (T a, T b)
{
    return a - std::floor (a / b) * b;
}
template<> inline int modFloor (int a, int b)
{
    int result = a % b;
    if (result < 0)
    {
        if (b > 0) result += b;
    }
    else if (result > 0)
    {
        if (b < 0) result += b;
    }
    return result;
}

template<class T> SHARED T                  uniform ();
template<class T> SHARED T                  uniform (T sigma);
template<class T> SHARED T                  uniform (T lo, T hi, T step = (T) 1);
template<class T, int R> MatrixFixed<T,R,1> uniform (const MatrixFixed<T,R,1> & sigma)
{
    MatrixFixed<T,R,1> result;
    const T * s = sigma.base ();
    T *       r = result.base ();
    T *       end = r + R;
    while (r < end) *r++ = uniform (*s++);
    return result;
}
template<class T, int R, int C> MatrixFixed<T,R,1> uniform (const MatrixFixed<T,R,C> & sigma)
{
    MatrixFixed<T,C,1> temp;
    T * t   = temp.base ();
    T * end = t + C;
    while (t < end) *t++ = uniform<T> ();
#   ifdef n2a_FP
    return multiply (sigma, temp, 1 + FP_MSB);  // See uniform<int>(int) in runtime.tcc
#   else
    return sigma * temp;
#   endif
}

template<class T> SHARED T                  gaussian ();
template<class T> SHARED T                  gaussian (T sigma);
template<class T, int R> MatrixFixed<T,R,1> gaussian (const MatrixFixed<T,R,1> & sigma)
{
    MatrixFixed<T,R,1> result;
    const T * s = sigma.base ();
    T *       r = result.base ();
    T *       end = r + R;
    while (r < end) *r++ = gaussian (*s++);
    return result;
}
template<class T, int R, int C> MatrixFixed<T,R,1> gaussian (const MatrixFixed<T,R,C> & sigma)
{
    MatrixFixed<T,C,1> temp;
    T * t   = temp.base ();
    T * end = t + C;
    while (t < end) *t++ = gaussian<T> ();
#   ifdef n2a_FP
    return multiply (sigma, temp, FP_MSB - 2);  // See gaussian<int>(int) in runtime.tcc
#   else
    return sigma * temp;
#   endif
}

template<class T, int R> MatrixFixed<T,R,1> sphere (const MatrixFixed<T,R,1> & sigma)
{
    MatrixFixed<T,R,1> result;
    T * r   = result.base ();
    T * end = r + R;
    while (r < end) *r++ = gaussian<T> ();

    // The basic idea is to scale a Gaussian vector to unit length, thus getting random
    // directions on a sphere of dimension R, then scale that vector so that it fills
    // the volume evenly between radius 0 and 1. This requires less density near the center.
#   ifdef n2a_FP
    int n     = norm (result, 2 << FP_MSB2, 2-FP_MSB, 4-FP_MSB);  // exponentA is result of gaussian(). exponentResult should be roughly 2+log2(R)-MSB (exponent of gaussian, plus log of the number of additions). Currently hardcoded for up to 4 rows.
    int R1    = (1 << FP_MSB2) / R;  // R1 exponent is -MSB/2, as required by pow()
    int scale = pow (uniform<int> (), R1, -1-FP_MSB, -1-FP_MSB);
    result = divide (result, n, FP_MSB-2);  // result of division is (2-MSB) - (4-MSB) = -2. Result is always in [0,1], so exponentResult should be -MSB.
    result = multiply (result, scale, FP_MSB+1);  // result of multiply is -MSB + -1-MSB = -2*MSB-1. Result is always in [0,1].
    return multiplyElementwise (sigma, result, FP_MSB);  // Result should have same exponent as sigma
#   else
    T scale = pow (uniform<T> (), (T) 1 / R) / norm (result, (T) 2);
    r = result.base ();
    const T * s = sigma.base ();
    while (r < end) *r++ *= scale * *s++;
    return result;
#   endif
}
template<class T, int R, int C> MatrixFixed<T,R,1> sphere (const MatrixFixed<T,R,C> & sigma)
{
    MatrixFixed<T,C,1> temp;
    T * t   = temp.base ();
    T * end = t + C;
    while (t < end) *t++ = gaussian<T> ();

#   ifdef n2a_FP
    int n     = norm (temp, 2 << FP_MSB2, 2-FP_MSB, 4-FP_MSB);
    int C1    = (1 << FP_MSB2) / C;
    int scale = pow (uniform<int> (), C1, -1-FP_MSB, -1-FP_MSB);
    temp = divide (temp, n, FP_MSB-2);
    temp = multiply (temp, scale, FP_MSB+1);
    return multiply (sigma, temp, FP_MSB);
#   else
    T scale = pow (uniform<T> (), (T) 1 / C) / norm (temp, (T) 2);
    temp *= scale;
    return sigma * temp;
#   endif
}

template<class T> SHARED MatrixFixed<T,3,1> grid    (int i, int nx = 1, int ny = 1, int nz = 1);
template<class T> SHARED MatrixFixed<T,3,1> gridRaw (int i, int nx = 1, int ny = 1, int nz = 1);

template<class T> SHARED T pulse (T t, T width = (T) INFINITY, T period = (T) 0, T rise = (T) 0, T fall = (T) 0);

template<class T> SHARED T unitmap (const MatrixAbstract<T> & A, T row, T column = (T) 0.5);
#ifdef n2a_FP
template<int> SHARED int unitmap (const MatrixAbstract<int> & A, int row, int column = 0x1 << FP_MSB - 1);
#endif

#ifdef n2a_FP
SHARED Matrix<int> glFrustum (int left, int right, int bottom, int top, int near, int far, int exponent);
SHARED Matrix<int> glOrtho (int left, int right, int bottom, int top, int near, int far, int exponent);
SHARED Matrix<int> glLookAt (const MatrixFixed<int,3,1> & eye, const MatrixFixed<int,3,1> & center, const MatrixFixed<int,3,1> & up, int exponent);
SHARED Matrix<int> glPerspective (int fovy, int aspect, int near, int far, int exponent);
SHARED Matrix<int> glRotate2 (int angle, const MatrixFixed<int,3,1> & axis, int exponent);
SHARED Matrix<int> glRotate (int angle, int x, int y, int z, int exponent);
SHARED Matrix<int> glScale (const MatrixFixed<int,3,1> & scales, int exponent);
SHARED Matrix<int> glScale (int sx, int sy, int sz, int exponent);
SHARED Matrix<int> glTranslate (const MatrixFixed<int,3,1> & position, int exponent);
SHARED Matrix<int> glTranslate (int x, int y, int z, int exponent);
#else
template<class T> SHARED Matrix<T> glFrustum (T left, T right, T bottom, T top, T near, T far);
template<class T> SHARED Matrix<T> glLookAt (const MatrixFixed<T,3,1> & eye, const MatrixFixed<T,3,1> & center, const MatrixFixed<T,3,1> & up);
template<class T> SHARED Matrix<T> glPerspective (T fovy, T aspect, T near, T far);
template<class T> SHARED Matrix<T> glRotate (T angle, const MatrixFixed<T,3,1> & axis);
template<class T> SHARED Matrix<T> glRotate (T angle, T x, T y, T z);
template<class T> SHARED Matrix<T> glScale (const MatrixFixed<T,3,1> & scales);
template<class T> SHARED Matrix<T> glScale (T sx, T sy, T sz);
template<class T> SHARED Matrix<T> glTranslate (const MatrixFixed<T,3,1> & position);
template<class T> SHARED Matrix<T> glTranslate (T x, T y, T z);
#endif
template<class T> SHARED Matrix<T> glOrtho (T left, T right, T bottom, T top, T near, T far);  // Always declared, regardless of numeric type. Used by ImageOutput.next3D().

#ifndef N2A_SPINNAKER
SHARED void signalHandler (int number);
#endif


// Simulation classes --------------------------------------------------------

template<class T> class Simulatable;
template<class T> class Part;
template<class T> class WrapperBase;
template<class T> class ConnectIterator;
template<class T> class ConnectPopulation;
template<class T> class ConnectPopulationNN;
template<class T> class ConnectMatrix;
template<class T> class Population;
template<class T> class Simulator;
template<class T> class Integrator;
template<class T> class Euler;
template<class T> class RungeKutta;
template<class T> class Event;
template<class T> class EventStep;
template<class T> class EventSpike;
template<class T> class EventSpikeSingle;
template<class T> class EventSpikeSingleLatch;
template<class T> class EventSpikeMulti;
template<class T> class EventSpikeMultiLatch;
template<class T> class Visitor;
template<class T> class VisitorStep;
template<class T> class VisitorSpikeMulti;
template<class T> class DelayBuffer;


/**
    The universal interface through which the runtime accesses model
    components. All parts are collected in populations, and all populations
    are members of parts. A wrapper part contains the top-level population,
    which contains at least one instance of the top-level model.

    <p>Lifetime management: Generally, if a part contains populations, they
    appear as member variables that are destructed automatically. Parts
    may not be deleted until they have a zero referenceCount.

    <p>Functions that change state of $live must also update $n. This means
    that init() increments $n and die() decrements $n. Likewise, functions
    that change $live should also change connection counts on parts with $min
    and $max. If finalize() will return false, it should call die() or do the
    equivalent first.

    <p>Even if dead, a part instance must remain valid as long as any live
    instances reference it. Thus, init() and die() are responsible for maintaining
    refcounts on parts that we directly reference.
**/
template<class T>
struct SHARED Simulatable
{
    virtual ~Simulatable () = default;
    virtual void clear ();  ///< Zeroes the same member variables zeroed by the ctor, in order to recycle a part.

    // Interface for computing simulation steps
    virtual void init               ();  ///< Initialize all variables. A part must increment $n of its population, enqueue each of its contained populations and call their init(). A population must create its instances, enqueue them and call init(). If this is a connection with $min or $max, increment count in respective target compartments.
    virtual void integrate          ();
    virtual void update             ();
    virtual int  finalize           ();  ///< A population may init() and add new parts to simulator. @return 0 if this part is still live; 1 if it should be removed from simulator queue; 2 if it should also be removed from population.
    virtual void updateDerivative   ();  ///< Same as update(), but restricted to computing derivatives.
    virtual void finalizeDerivative ();  ///< Same as finalize(), but restricted to computing derivatives.

    // Interface for numerical manipulation
    // This is deliberately a minimal set of functions to support Runge-Kutta, with folded push and pop operations.
    // This interface will no doubt need to be generalized when another integrator is added.
    virtual void snapshot           ();         ///< save all members trashed by integration
    virtual void restore            ();         ///< restore all members trashed by integration
    virtual void pushDerivative     ();         ///< push D0; D0 = members
    virtual void multiplyAddToStack (T scalar); ///< D0 += members * scalar; for fixed-point, exponentScalar=1
    virtual void multiply           (T scalar); ///< members *= scalar; for fixed-point, exponentScalar=1
    virtual void addToMembers       ();         ///< members += D0; pop D0

    // Generic metadata
    virtual void path (String & result);
};

template<class T>
struct SHARED Part : public Simulatable<T>
{
    // Lifespan management
    virtual void die            (); ///< Set $live=0 (in some form). If accountable connection, decrement connection counts in target compartments. Reduces refcount on parts we directly access, to indicate that they may be re-used.
    virtual void remove         (); ///< Asks our population to put us on its dead list.
    virtual void ref            (); ///< Increment refcount, if there is one. Called by Simulator when part is enqueued. This keeps a part from being reused until it is off all queues.
    virtual void deref          (); ///< Decrement refcount, if there is one. Called by Simulator (visitor) when part is dequeued.
    virtual bool isFree         (); ///< @return true if the part is ready to use, false if the we are still waiting on other parts that reference us.
    virtual void clearDuplicate (); ///< Clears the "duplicate" flag, if it exists.
    virtual int  flush          (); ///< Check if this part is dead, dequeued or duplicate. Return values have same meaning as finalize().

    // Connection-specific accessors
    virtual void      setPart    (int i, Part<T> * part);           ///< Assign the instance of population i referenced by this connection.
    virtual Part<T> * getPart    (int i);                           ///< @return Pointer to the instance of population i.
    virtual int       getCount   (int i);                           ///< @return Number of connections of this type attached to the part indexed by i.
    virtual void      getProject (int i, MatrixFixed<T,3,1> & xyz); ///< Determine position of endpoint i as projected into this connection's space. Default is the endpoint's own $xyz.
    virtual int       mapIndex   (int i, int rc);                   ///< Applies user-specified transformation formula between matrix index and population $index. Parameter i indicates which endpoint to process. Generally, but not always, rows are endpoint 0 and columns are endpoint 1.
    virtual bool      getNewborn ();                                ///< @return The value of the newborn flag (or false if it doesn't exist in this part). Unlike the above, this is a direct function of the endpoint.

    // Accessors for $variables
    virtual T    getLive ();                         ///< @return 1 if we are in normal simulation. 0 if we have died. Default is 1.
    virtual T    getP    ();                         ///< Default is 1 (always create). exponent=-MSB
    virtual T    getDt   ();                         ///< Get $t'. Walks up containment hierarchy, starting with current part, until it finds a stored value.
    virtual void getXYZ  (MatrixFixed<T,3,1> & xyz); ///< Default is [0;0;0].

    // Events
    virtual bool eventTest  (int i);
    virtual T    eventDelay (int i);
    virtual void setLatch   (int i);
    virtual void finalizeEvent ();  ///< Does finalize on any external references touched by some event in this part.
};

template<class T> SHARED void removeMonitor (std::vector<Part<T> *> & partList, Part<T> * part);

template<class T>
struct SHARED WrapperBase : public Part<T>
{
    Population<T> * population;  // The top-level population can never be a connection, only a compartment.
    T               dt;          // $t'. Never accessed directly by generated code, other than one update by first child.
    bool            duplicate;

    WrapperBase ();

    virtual void init               ();
    virtual void clearDuplicate     ();
    virtual int  flush              ();
    virtual void integrate          ();
    virtual void update             ();
    virtual int  finalize           ();
    virtual void updateDerivative   ();
    virtual void finalizeDerivative ();

    virtual void snapshot           ();
    virtual void restore            ();
    virtual void pushDerivative     ();
    virtual void multiplyAddToStack (T scalar);
    virtual void multiply           (T scalar);
    virtual void addToMembers       ();

    virtual T    getDt              ();
};

template<class T>
struct SHARED ConnectIterator
{
    virtual ~ConnectIterator () = default;
    virtual bool setProbe (Part<T> * probe) = 0; ///< Sets up the next connection instance to have its endpoints configured. Return value is used primarily by ConnectPopulation to implement $max.
    virtual bool next     () = 0;                ///< Fills probe with next permutation. Returns false if no more permutations are available.
};

/**
    Enumerates all instances that can act as a particular connection endpoint.
    Handles deep paths to multiple populations, appending them into a single contiguous list.
    When nested, this class is responsible for destructing its inner iterators.
**/
template<class T>
struct SHARED ConnectPopulation : public ConnectIterator<T>
{
    int                      index;      ///< of endpoint, for use with accessors
    ConnectPopulation<T> *   permute;
    bool                     contained;  ///< Another iterator holds us in its permute reference.
    std::vector<Part<T> *> * instances;
    bool                     deleteInstances;
    int                      size;       ///< Cached value of instances.size(). Doesn't change.
    int                      firstborn;  ///< Index in instances of first new entry. In simplest case, it is the same as population firstborn.
    std::vector<Part<T> *>   filtered;   ///< A subset of instances selected by spatial filtering.
    Part<T> *                c;          ///< The connection instance being built.
    Part<T> *                p;          ///< Our current part, contributed as an endpoint of c.

    // Iteration
    bool newOnly;
    bool poll;
    int  count;  ///< Size of current subset of instances we are iterating through.
    int  offset;
    int  i;
    int  stop;

    // Endpoint parameters get stashed here, rather than using accessors.
    int Max;  // $max. Capitalized to avoid name collision with macro or function.
    int Min;
    int k;
    T   radius;

    // Nearest-neighbor filtering
    T                             rank;        ///< heuristic value indicating how good a candidate this endpoint is to determine C.$xyz
    bool                          explicitXYZ; ///< c explicitly defines $xyz, which takes precedence over any $project value
    typename KDTree<T>::Vector3 * xyz;         ///< C.$xyz (that is, probe $xyz), shared by all iterators
    KDTree<T> *                   NN;          ///< "nearest neighbor" search class
    typename KDTree<T>::Entry *   entries;     ///< A dynamically-allocated array

    ConnectPopulation (int index, bool poll);
    virtual ~ConnectPopulation ();

    void         prepareNN ();
    virtual bool setProbe  (Part<T> * probe); ///< @return true If we need to advance to the next instance. This happens when p has reached its max number of connections.
    virtual void reset     (bool newOnly);
    bool         old       ();                ///< Indicates that all iterators from this level down return a part that is old.
    virtual bool next      ();
};

/**
    Isolates KDTree link dependencies.
    Most of the KDTree-related members are in our superclass, but they do not
    trigger linkage. Our dtor and reset() contain code that does trigger linkage.
**/
template<class T>
struct SHARED ConnectPopulationNN : public ConnectPopulation<T>
{
    ConnectPopulationNN (int index, bool poll);
    virtual ~ConnectPopulationNN ();

    virtual void reset (bool newOnly);
};

template<class T>
struct SHARED ConnectMatrix : public ConnectIterator<T>
{
    ConnectPopulation<T> * rows;
    ConnectPopulation<T> * cols;
    int                    rowIndex;
    int                    colIndex;
    IteratorNonzero<T>   * it;
    Part<T>              * dummy;      ///< Temporary connection used to evaluate index mappings.
    Population<T>        * population; ///< Needed to release "dummy" when done.
    Part<T>              * c;

    ConnectMatrix (ConnectPopulation<T> * rows, ConnectPopulation<T> * cols, int rowIndex, int colIndex, IteratorNonzero<T> * it, Part<T> * dummy, Population<T> * population);
    virtual ~ConnectMatrix ();

    virtual bool setProbe (Part<T> * probe);
    virtual bool next     ();
};

/**
    The shared variables of a group of part instances. Because a population
    may be contained in another part, which may be replicated several times,
    there may be several separate populations of exactly that same kind of
    part.

    <p>Lifetime management: A population class allocates a single (static) pool
    of part instances shared by all its population instances. This pool generally does
    not shrink during the simulation, on the assumption that the peak number
    of part instances may be required more than once. When the simulation shuts
    down, the main thread can call ::releaseMemory() to clean up all the pools.
**/
template<class T>
struct SHARED Population : public Simulatable<T>
{
    Part<T> * container;

    // The code generator will create fields like the following in subclasses:
    //static vector<Part<T>*> dead;        ///< List of available parts.
    //static vector<Part<T>*> memory;      ///< Allocated blocks of memory for part instances.
    //static mutex            mutexMemory; ///< Controls access to "dead" and "memory".
    //static void releaseMemory ();  ///< Free all blocks of allocated memory.

    virtual ~Population () = default;

    // Instance management
    virtual Part<T> * allocate ();               ///< Obtains an instance of the part. This comes from the dead list. If no dead instance is free, allocates more memory and adds any leftover instances to the dead list.
    virtual void      release  (Part<T> * part); ///< Returns part to the dead list.
    virtual void      add      (Part<T> * part); ///< Makes the given instance an active member of the population. Increments $n.
    virtual void      remove   (Part<T> * part); ///< Moves part to dead list and decrements $n. Derived classes may update other accounting for the part.
    virtual void      resize   (int n);          ///< Adds or kills instances until $n matches given n.
    virtual int       getN     ();               ///< Subroutine for resize(). Returns current number of live instances (true n). Not exactly the same as an accessor for $n, because it does not give the requested size, only actual size.

    // Connections
    virtual void                   connect            ();          ///< For a connection population, evaluate each possible connection (or some well-defined subset thereof).
    virtual void                   clearNew           ();          ///< Reset newborn index
    virtual ConnectIterator<T> *   getIterators       (bool poll); ///< Assembles one or more nested iterators in an optimal manner and returns the outermost one.
    ConnectIterator<T> *           getIteratorsSimple (bool poll); ///< Implementation of getIterators() without nearest-neighbor search.
    ConnectIterator<T> *           getIteratorsNN     (bool poll); ///< Implementation of getIterators() which uses KDTree for nearest-neighbor search.
    virtual ConnectPopulation<T> * getIterator        (int i, bool poll);
};

template<class T>
struct SHARED Event
{
    T t;
#   ifdef n2a_FP
    static int exponent;  ///< for time values
#   endif

    virtual ~Event () = default;
    virtual bool isStep () const;  ///< Always returns false, except for instances of EventStep. Used as a poor-man's RTTI in the class More.

    virtual void run () = 0;  ///< Does all the work of a simulation cycle. This may be adapted to the specifics of the event type.
    virtual void visit (const std::function<void (Visitor<T> * visitor)> & f) = 0;  ///< Applies function to each part associated with this event. May visit multiple parts in parallel using separate threads.
};

template<class T>
struct More
{
    /**
        Returns true if a sorts before b. However, priority_queue.top() returns the last
        element in sort order. Thus we need to sort so earlier times come last.
        This means that we answer false when a has timestamp strictly before b.
        Regarding ties (equal timestamps): The contract of priority_queue appears to be that entries
        will move toward the bottom until the answer switches from true to false.
        We return true for ties, so they always insert later than existing entries (FIFO).
        When the tie is between a step event and a spike event, then we generally sort the spike event
        earlier than the step event. The flag "after" switches this behavior, so spike events
        sort later than step events.
    **/
    bool operator() (const Event<T> * a, const Event<T> * b) const
    {
        if (a->t > b->t) return true;
        if (a->t < b->t) return false;
        // Events have the same timestamp, so sort by event type ...
        bool stepA = a->isStep ();
        bool stepB = b->isStep ();
        if (stepA  &&  stepB) return true;  // Both are step events. New entries will get sorted after existing entries at the same point in time.
        if (stepA) return ! SIMULATOR after;
        if (stepB) return   SIMULATOR after;
        return true;  // Both are spike events.
    }
};

template<class T>
class priorityQueue : public std::priority_queue<Event<T> *,std::vector<Event<T> *>,More<T>>
{
};

/**
    Lifetime management: When the simulator shuts down, it must dequeue all
    parts. In general, a simulator will run until its queue is empty.
**/
template<class T>
#ifdef n2a_TLS
struct Simulator   // TLS is incompatible with shared runtime library. Can only be used within a fully self-contained model DLL.
#else
struct SHARED Simulator
#endif
{
    priorityQueue<T>                             queueEvent;    ///< Pending events in time order.
    std::vector<std::pair<Population<T> *, int>> queueResize;   ///< Populations that need to change $n after the current cycle completes.
    std::queue<Population<T> *>                  queueConnect;  ///< Connection populations that want to construct or recheck their instances after all populations are resized in current cycle.
    std::vector<Population<T> *>                 queueClearNew; ///< Populations whose newborn index needs to be reset.
    std::vector<EventStep<T> *>                  periods;
    Integrator<T> *                              integrator;
    bool                                         stop;
    Event<T> *                                   currentEvent;
    bool                                         after;         ///< When true, and timesteps match, sort spike events after step events. Otherwise sort them before.
    std::vector<Holder *>                        holders;

    // Singleton
#   ifdef n2a_TLS
    static thread_local Simulator<T> * instance;
#   else
    static Simulator<T> instance;
#   endif

    Simulator ();
    ~Simulator ();
    void clear ();  ///< Restores simulator to same condition as newly-constructed object.

    void init (WrapperBase<T> * wrapper); ///< init phase and event queue set up
    void run (T until = (T) INFINITY);    ///< Run until given time. This function can be called multiple times to step through simulation. Default value runs until queue is empty.
    void updatePopulations ();

    void enqueue      (Part<T> * part, T dt); ///< Places part on event with period dt. If the event already exists, then the actual time till the part next executes may be less than dt, but thereafter will be exactly dt. Caller is responsible to call dequeue() or enterSimulation().
    void linger       (T dt);                 ///< Does bookkeeping for lazy removal from period dt. When enough parts have been dequeued, does a pass to flush them.
    void removePeriod (EventStep<T> * event);

    // callbacks
    void resize   (Population<T> * population, int n); ///< Schedule population to be resized at end of current cycle.
    void connect  (Population<T> * population);        ///< Schedule connections to be evaluated at end of current cycle, after all resizing is done.
    void clearNew (Population<T> * population);        ///< Schedule population to have its newborn index reset after all new connections are evaluated.

    Holder * getHolder (const String & fileName, Holder * oldHandle);
};
#ifdef n2a_TLS
// Normally, this template would defined in runtime.tcc
// However, unless it is defined in every module, TLS fails under GCC.
// Not sure if this is a bug in GCC or just my misunderstanding of how C++ works.
template<class T> thread_local Simulator<T> * Simulator<T>::instance;
#endif

template<class T>
struct SHARED Integrator
{
    // For now, we don't need a virtual dtor, because this class contains no data.
    virtual void run (Event<T> & event) = 0;
};

template<class T>
struct SHARED Euler : public Integrator<T>
{
    virtual void run (Event<T> & event);
};

template<class T>
struct SHARED RungeKutta : public Integrator<T>
{
    virtual void run (Event<T> & event);
};

/**
    Holds parts that are formally queued for simulation.
    Executes on a periodic basis ("step" refers to dt). No other event type can hold parts for the simulator.
    Equivalently, all queued parts must belong to exactly one EventStep.
    This class is heavier (more storage, more computation) than other events, as it is expected
    to live for the entire length of the simulation and repeatedly insert itself into the event queue.

    <p>Lifetime management: When a simulation shuts down normally, all parts die and get
    processed by their population object. If the simulation get terminated by a signal,
    then this class is responsible for disposing any parts that are still alive.
**/
template<class T>
struct SHARED EventStep : public Event<T>
{
    T                             dt;
    std::vector<VisitorStep<T> *> visitors;
    uint32_t                      countLinger;

    static uint32_t threshold;  ///< Maximum value of countDequeue before we do a preemptive flush.

    EventStep (T t, T dt);
    virtual ~EventStep ();  ///< Frees any parts that have not yet died.
    virtual bool isStep () const;

    virtual void  run     ();
    virtual void  visit   (const std::function<void (Visitor<T> * visitor)> & f);
    void          flush   ();  ///< Subroutine of run(). Removes dead, dequeued or duplicate parts from queue.
    void          requeue ();  ///< Subroutine of run(). If our load of instances is non-empty, then get back in the simulation queue.
    void          enqueue (Part<T> * part);
};

/**
    Lightweight class representing one-time events, of which there may be a large quantity.
**/
template<class T>
struct SHARED EventSpike : public Event<T>
{
    int latch;
};

template<class T>
struct SHARED EventSpikeSingle : public EventSpike<T>
{
    Part<T> * target;

    virtual void run   ();
    virtual void visit (const std::function<void (Visitor<T> * visitor)> & f);
};

template<class T>
struct SHARED EventSpikeSingleLatch : public EventSpikeSingle<T>
{
    virtual void run ();
};

template<class T>
struct SHARED EventSpikeMulti : public EventSpike<T>
{
    std::vector<Part<T> *> * targets;

    virtual void run   ();
    virtual void visit (const std::function<void (Visitor<T> * visitor)> & f);
    void setLatch ();
};

template<class T>
struct SHARED EventSpikeMultiLatch : public EventSpikeMulti<T>
{
    virtual void run ();
};

/**
    The general interface through which a Part communicates with the Event that is currently processing it.
    This helper class enables Event to be lighter weight by not carrying extra fields that are
    only needed when actually iterating over parts.
**/
template<class T>
struct SHARED Visitor
{
    Part<T> * part;  ///< Current instance being processed.

    Visitor (Part<T> * part = 0);

    virtual void visit (const std::function<void (Visitor<T> * visitor)> & f); ///< Applies function to each instance on our local queue. Steps "part" through the list and calls f(this) each time.
};

/**
    Manages the subset of EventStep's load of instances assigned to a specific thread.
**/
template<class T>
struct SHARED VisitorStep : public Visitor<T>
{
    std::vector<Part<T> *> queue;  ///< The list of parts to simulate.
    int                    index;  ///< Of element in queue currently being simulated.
    int                    last;   ///< Element of queue at which we should stop iterating. Accommodates new parts added during finalize() pass.

    ~VisitorStep ();  ///< Free any parts still lingering in queue.

    virtual void visit (const std::function<void (Visitor<T> * visitor)> & f);
};

template<class T>
struct SHARED VisitorSpikeMulti : public Visitor<T>
{
    EventSpikeMulti<T> * event;

    VisitorSpikeMulti (EventSpikeMulti<T> * event);

    virtual void visit (const std::function<void (Visitor<T> * visitor)> & f);
};

/**
    For implementing delay().
    Requires a known delay depth D and a known initial value.
**/
template<class T, int D>
struct RingBuffer
{
    T buffer[D];

    void clear (T initialValue = (T) 0)
    {
        for (int i = 0; i < D; i++) buffer[i] = initialValue;
    }

    T step (T now, T dt, T futureValue)
    {
        int t = (int) round (now / dt);
        T result = buffer[t % D];
        buffer[t % D] = futureValue;
        return result;
    }
};

/**
    For implementing delay() in more general situations where requirements of RingBuffer
    can't be satisfied.
    This implementation using std::map is quite inefficient in both memory and time.
**/
template<class T>
struct SHARED DelayBuffer
{
    T             value;
    std::map<T,T> buffer;

    void clear ();
    T step (T now, T delay, T value, T initialValue);
};


#endif
