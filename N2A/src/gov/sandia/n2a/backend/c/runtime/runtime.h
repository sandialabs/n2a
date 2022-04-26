/*
Copyright 2013-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_runtime_h
#define n2a_runtime_h


#include "nosys.h"
#include "io.h"
#include "StringLite.h"
#include "KDTree.h"

#include <functional>
#include <queue>
#include <vector>
#include <map>

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

template<class T> T                         uniform ();
template<class T> T                         uniform (T sigma);
template<class T> T                         uniform (T lo, T hi, T step = (T) 1);
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
    return sigma * temp;
}

template<class T> T                         gaussian ();
template<class T> T                         gaussian (T sigma);
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
    return sigma * temp;
}

template<class T, int R> MatrixFixed<T,R,1> sphere (const MatrixFixed<T,R,1> & sigma)
{
    MatrixFixed<T,R,1> result;
    T * r   = result.base ();
    T * end = r + R;
    while (r < end) *r++ = gaussian<T> ();

    T scale = pow (uniform<T> (), (T) 1 / R) / norm (result, (T) 2);
    r = result.base ();
    const T * s = sigma.base ();
    while (r < end) *r++ *= scale * *s++;

    return result;
}
template<class T, int R, int C> MatrixFixed<T,R,1> sphere (const MatrixFixed<T,R,C> & sigma)
{
    MatrixFixed<T,C,1> temp;
    T * t   = temp.base ();
    T * end = t + C;
    while (t < end) *t++ = gaussian<T> ();

    T scale = pow (uniform<T> (), (T) 1 / C) / norm (temp, (T) 2);
    t = temp.base ();
    while (t < end) *t++ *= scale;

    return sigma * temp;
}

template<class T> MatrixFixed<T,3,1> grid    (int i, int nx = 1, int ny = 1, int nz = 1);
template<class T> MatrixFixed<T,3,1> gridRaw (int i, int nx = 1, int ny = 1, int nz = 1);

template<class T> T pulse (T t, T width = (T) INFINITY, T period = (T) 0, T rise = (T) 0, T fall = (T) 0);

template<class T> T unitmap (const MatrixAbstract<T> & A, T row, T column = (T) 0.5);
#ifdef n2a_FP
template<int> int unitmap (const MatrixAbstract<int> & A, int row, int column = 0x1 << FP_MSB - 1);
#endif

#ifndef N2A_SPINNAKER
extern void signalHandler (int number);
#endif


// Simulation classes --------------------------------------------------------

template<class T> class Simulatable;
template<class T> class Part;
template<class T> class PartTime;
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

    <p>Reference counting has to do with whether a part might be executed,
    regardless of whether it is live or not. Thus, enqueue() and dequeue()
    are responsible for maintaining refcounts on parts that we directly
    reference.
**/
template<class T>
class Simulatable
{
public:
    virtual ~Simulatable ();
    virtual void clear ();  ///< Zeroes the same member variables zeroed by the ctor, in order to recycle a part.

    // Interface for computing simulation steps
    virtual void init               ();  ///< Initialize all variables. A part must increment $n of its population, enqueue each of its contained populations and call their init(). A population must create its instances, enqueue them and call init(). If this is a connection with $min or $max, increment count in respective target compartments.
    virtual void integrate          ();
    virtual void update             ();
    virtual bool finalize           ();  ///< A population may init() and add new parts to simulator. @return true if this part is still live, false if it should be removed from simulator queue.
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
    virtual void getNamedValue (const String & name, String & value);
};

template<class T>
class Part : public Simulatable<T>
{
public:
    Part<T> * next; ///< All parts exist on one primary linked list, either in the simulator or the population's dead list.

    // Simulation queue
    virtual void           setPrevious (Part<T> * previous);
    virtual void           setVisitor  (VisitorStep<T> * visitor);  ///< Informs this part of both its associated event and the specific thread.
    virtual EventStep<T> * getEvent    ();  ///< @return the event this part is associated with. Provides t and dt.

    // Lifespan management
    virtual void die             (); ///< Set $live=0 (in some form) and decrement $n of our population. If accountable connection, decrement connection counts in target compartments.
    virtual void enterSimulation (); ///< Tells us we are going onto the simulator queue. Increment refcount on parts we directly access.
    virtual void leaveSimulation (); ///< Tells us we are leaving the simulator queue. Ask our population to put us on its dead list. Reduce refcount on parts we directly access, to indicate that they may be re-used.
    virtual bool isFree          (); ///< @return true if the part is ready to use, false if the we are still waiting on other parts that reference us.

    // Connection-specific accessors
    virtual void      setPart    (int i, Part<T> * part);           ///< Assign the instance of population i referenced by this connection.
    virtual Part<T> * getPart    (int i);                           ///< @return Pointer to the instance of population i.
    virtual int       getCount   (int i);                           ///< @return Number of connections of this type attached to the part indexed by i.
    virtual void      getProject (int i, MatrixFixed<T,3,1> & xyz); ///< Determine position of endpoint i as projected into this connection's space. Default is the endpoint's own $xyz.
    virtual int       mapIndex   (int i, int rc);                   ///< Applies user-specified transformation formula between matrix index and population $index. Parameter i indicates which endpoint to process. Generally, but not always, rows are endpoint 0 and columns are endpoint 1.
    virtual bool      getNewborn ();                                ///< @return The value of the newborn flag (or false if it doesn't exist in this part). Unlike the above, this is a direct function of the endpoint.

    // Accessors for $variables
    virtual T    getLive ();                         ///< @return 1 if we are in normal simulation. 0 if we have died. Default is 1.
    virtual T    getP    ();                         ///< Default is 1 (always create)
    virtual void getXYZ  (MatrixFixed<T,3,1> & xyz); ///< Default is [0;0;0].

    // Events
    virtual bool eventTest  (int i);
    virtual T    eventDelay (int i);
    virtual void setLatch   (int i);
    virtual void finalizeEvent ();  ///< Does finalize on any external references touched by some event in this part.
};

template<class T> void removeMonitor (std::vector<Part<T> *> & partList, Part<T> * part);

/**
    Supports ability to dequeue and move to a different simulation period.
    Implements other half of doubly-linked list. It is possible to mix singly- and doubly-linked
    parts in the same queue, but only a doubly-linked part can dequeue outside of sim loop.
**/
template<class T>
class PartTime : public Part<T>
{
public:
    Part<T> * previous;
    VisitorStep<T> * visitor;

    virtual void           setPrevious (Part<T> * previous);
    virtual void           setVisitor  (VisitorStep<T> * visitor);
    virtual EventStep<T> * getEvent    ();
    virtual void           dequeue     ();  ///< Handles all direct requests (oustide of sim loop).
    virtual void           setPeriod   (T dt);
};

template<class T>
class WrapperBase : public PartTime<T>
{
public:
    Population<T> * population;  // The top-level population can never be a connection, only a compartment.

    virtual void init               ();
    virtual void integrate          ();
    virtual void update             ();
    virtual bool finalize           ();
    virtual void updateDerivative   ();
    virtual void finalizeDerivative ();

    virtual void snapshot           ();
    virtual void restore            ();
    virtual void pushDerivative     ();
    virtual void multiplyAddToStack (T scalar);
    virtual void multiply           (T scalar);
    virtual void addToMembers       ();
};

template<class T>
class ConnectIterator
{
public:
    virtual ~ConnectIterator ();
    virtual bool setProbe (Part<T> * probe) = 0; ///< Sets up the next connection instance to have its endpoints configured. Return value is used primarily by ConnectPopulation to implement $max.
    virtual bool next     () = 0;                ///< Fills probe with next permutation. Returns false if no more permutations are available.
};

/**
    Enumerates all instances that can act as a particular connection endpoint.
    Handles deep paths to multiple populations, appending them into a single contiguous list.
    When nested, this class is responsible for destructing its inner iterators.
**/
template<class T>
class ConnectPopulation : public ConnectIterator<T>
{
public:
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

    ConnectPopulation (int index);
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
class ConnectPopulationNN : public ConnectPopulation<T>
{
public:
    ConnectPopulationNN (int index);
    virtual ~ConnectPopulationNN ();

    virtual void reset (bool newOnly);
};

template<class T>
class ConnectMatrix : public ConnectIterator<T>
{
public:
    ConnectPopulation<T> * rows;
    ConnectPopulation<T> * cols;
    int                    rowIndex;
    int                    colIndex;
    IteratorNonzero<T>   * it;
    Part<T>              * dummy; ///< Temporary connection used to evaluate index mappings.
    Part<T>              * c;

    ConnectMatrix (ConnectPopulation<T> * rows, ConnectPopulation<T> * cols, int rowIndex, int colIndex, IteratorNonzero<T> * it, Part<T> * dummy);
    virtual ~ConnectMatrix ();

    virtual bool setProbe (Part<T> * probe);
    virtual bool next     ();
};

/**
    The shared variables of a group of part instances. Because a population
    may be contained in another part, which may be replicated several times,
    there may be several separate populations of exactly that same kind of
    part. Therefore, these cannot simply be stored as static members in the
    part class.

    <p>Lifetime management: An object of this class is responsible to destroy
    all part instances it contains.
**/
template<class T>
class Population : public Simulatable<T>
{
public:
    Part<T> * container;
    Part<T> * dead; ///< Head of linked list of available parts, using Part::next.

    Population ();
    virtual ~Population ();  ///< Deletes all parts on our dead list.

    // Instance management
    virtual Part<T> * create   ();               ///< Construct an instance of the kind of part this population holds. Caller is fully responsible for lifespan of result, unless it gives the part to us via add().
    virtual void      add      (Part<T> * part); ///< The given part is going onto a simulator queue, but we may also account for it in other ways.
    virtual void      remove   (Part<T> * part); ///< Move part to dead list, and update any other accounting for the part.
    virtual Part<T> * allocate ();               ///< If a dead part is available, re-use it. Otherwise, create and add a new part.
    virtual void      resize   (int n);          ///< Add or kill instances until $n matches given n.
    virtual int       getN     ();

    // Connections
    virtual void                   connect            (); ///< For a connection population, evaluate each possible connection (or some well-defined subset thereof).
    virtual void                   clearNew           (); ///< Reset newborn index
    virtual ConnectIterator<T> *   getIterators       (); ///< Assembles one or more nested iterators in an optimal manner and returns the outermost one.
    ConnectIterator<T> *           getIteratorsSimple (); ///< Implementation of getIterators() without nearest-neighbor search.
    ConnectIterator<T> *           getIteratorsNN     (); ///< Implementation of getIterators() which uses KDTree for nearest-neighbor search.
    virtual ConnectPopulation<T> * getIterator        (int i);
};

template<class T>
class Event
{
public:
    T t;
#   ifdef n2a_FP
    static int exponent;  ///< for time values
#   endif

    virtual ~Event ();
    virtual bool isStep () const;  ///< Always returns false, except for instances of EventStep. Used as a poor-man's RTTI in the class More.

    virtual void run () = 0;  ///< Does all the work of a simulation cycle. This may be adapted to the specifics of the event type.
    virtual void visit (std::function<void (Visitor<T> * visitor)> f) = 0;  ///< Applies function to each part associated with this event. May visit multiple parts in parallel using separate threads.
};

template<class T>
class More
{
public:
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
class Simulator
{
public:
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

#   ifdef n2a_TLS
    static thread_local Simulator<T> * instance;  ///< Singleton
#   else
    static Simulator<T> instance;  ///< Singleton
#   endif

    Simulator ();
    ~Simulator ();
    void clear ();

    void init (WrapperBase<T> & wrapper); ///< init phase and event queue set up
    void run (T until = (T) INFINITY);    ///< Run until given time. This function can be called multiple times to step through simulation. Default value runs until queue is empty.
    void updatePopulations ();

    void enqueue      (Part<T> * part, T dt); ///< Places part on event with period dt. If the event already exists, then the actual time till the part next executes may be less than dt, but thereafter will be exactly dt. Caller is responsible to call dequeue() or enterSimulation().
    void removePeriod (EventStep<T> * event);

    // callbacks
    void resize   (Population<T> * population, int n); ///< Schedule population to be resized at end of current cycle.
    void connect  (Population<T> * population);        ///< Schedule connections to be evaluated at end of current cycle, after all resizing is done.
    void clearNew (Population<T> * population);        ///< Schedule population to have its newborn index reset after all new connections are evaluated.

    Holder * getHolder (const String & fileName, Holder * oldHandle);
};

template<class T>
class Integrator
{
public:
    // For now, we don't need a virtual dtor, because this class contains no data.
    virtual void run (Event<T> & event) = 0;
};

template<class T>
class Euler : public Integrator<T>
{
public:
    virtual void run (Event<T> & event);
};

template<class T>
class RungeKutta : public Integrator<T>
{
public:
    virtual void run (Event<T> & event);
};

/**
    Holds parts that are formally queued for simulation.
    Executes on a periodic basis ("step" refers to dt). No other event type can hold parts for the simulator.
    Equivalently, all queued parts must belong to exactly one EventStep.
    This class is heavier (more storage, more computation) than other events, as it is expected
    to live for the entire length of the simulation and repeatedly insert itself into the event queue.
**/
template<class T>
class EventStep : public Event<T>
{
public:
    T dt;
    std::vector<VisitorStep<T> *> visitors;

    EventStep (T t, T dt);
    virtual ~EventStep ();
    virtual bool isStep () const;

    virtual void  run     ();
    virtual void  visit   (std::function<void (Visitor<T> * visitor)> f);
    void          requeue ();  ///< Subroutine of run(). If our load of instances is non-empty, then get back in the simulation queue.
    void          enqueue (Part<T> * part);
};

/**
    Lightweight class representing one-time events, of which there may be a large quantity.
**/
template<class T>
class EventSpike : public Event<T>
{
public:
    int latch;
};

template<class T>
class EventSpikeSingle : public EventSpike<T>
{
public:
    Part<T> * target;

    virtual void run   ();
    virtual void visit (std::function<void (Visitor<T> * visitor)> f);
};

template<class T>
class EventSpikeSingleLatch : public EventSpikeSingle<T>
{
public:
    virtual void run ();
};

template<class T>
class EventSpikeMulti : public EventSpike<T>
{
public:
    std::vector<Part<T> *> * targets;

    virtual void run   ();
    virtual void visit (std::function<void (Visitor<T> * visitor)> f);
    void setLatch ();
};

template<class T>
class EventSpikeMultiLatch : public EventSpikeMulti<T>
{
public:
    virtual void run ();
};

/**
    The general interface through which a Part communicates with the Event that is currently processing it.
    This helper class enables Event to be lighter weight by not carrying extra fields that are
    only needed when actually iterating over parts.
**/
template<class T>
class Visitor
{
public:
    Event<T> * event;
    Part<T> *  part;  ///< Current instance being processed.

    Visitor (Event<T> * event, Part<T> * part = 0);

    virtual void visit (std::function<void (Visitor<T> * visitor)> f); ///< Applies function to each instance on our local queue. Steps "part" through the list and calls f(this) each time.
};

/**
    Manages the subset of EventStep's load of instances assigned to a specific thread.
**/
template<class T>
class VisitorStep : public Visitor<T>
{
public:
    Part<T>   queue;    ///< The head of a singly-linked list. queue itself never executes, rather, its "next" field points to the first active part.
    Part<T> * previous; ///< Points to the part immediately ahead of the current part.

    VisitorStep (EventStep<T> * event);

    virtual void visit   (std::function<void (Visitor<T> * visitor)> f);
    virtual void enqueue (Part<T> * newPart);  ///< Puts newPart on our local queue. Called by EventStep::enqueue(), which balances load across all threads.
};

template<class T>
class VisitorSpikeMulti : public Visitor<T>
{
public:
    VisitorSpikeMulti (EventSpikeMulti<T> * event);

    virtual void visit (std::function<void (Visitor<T> * visitor)> f);
};

// TODO: This implementation using std::map is quite inefficient in both memory and time.
// If we can assume constant delay and constant dt, then a fixed-size ring buffer would be best.
// Maybe we can have two implementations: one general and one efficient. The code generator
// could choose between them based on whether the above conditions are met.
template<class T>
class DelayBuffer
{
public:
    T             value;
    std::map<T,T> buffer;

    DelayBuffer ();

    T step (T now, T delay, T value, T initialValue);
};


#endif
