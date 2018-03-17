#ifndef backend_c_runtime_h
#define backend_c_runtime_h


#ifdef N2A_SPINNAKER
# include "nosys.h"
#endif

#include "fl/matrix.h"

#include <functional>
#include <queue>
#include <set>
#include <vector>
#include <map>
#include <unordered_map>

typedef fl::MatrixFixed<float,3,1> Vector3;


// General functions. See the N2A language reference for details.

extern float                   uniform ();
extern float                   uniform (float sigma);
extern fl::MatrixResult<float> uniform (const fl::MatrixAbstract<float> & sigma);

extern float                   gaussian ();
extern float                   gaussian (float sigma);
extern fl::MatrixResult<float> gaussian (const fl::MatrixAbstract<float> & sigma);

extern fl::MatrixResult<float> grid (int i, int nx, int ny = 1, int nz = 1);

// Matrix input
#ifndef N2A_SPINNAKER
class MatrixInput : public fl::Matrix<float>
{
public:
    std::string fileName;

    float get    (float row, float column);
    float getRaw (float row, float column);
};
extern MatrixInput * matrixHelper (const std::string & fileName, MatrixInput * oldHandle = 0);
#endif

// Input
#ifndef N2A_SPINNAKER
class InputHolder
{
public:
    std::string                         fileName;
    std::istream *                      in;

    float                               currentLine;
    float *                             currentValues;
    int                                 currentCount;
    float                               nextLine;
    float *                             nextValues;
    int                                 nextCount;
    int                                 columnCount;
    std::unordered_map<std::string,int> columnMap;
    int                                 timeColumn;
    bool                                timeColumnSet;
    float                               epsilon;  ///< for time values

    InputHolder (const std::string & fileName);
    ~InputHolder ();

    void  getRow     (float row, bool time);  ///< subroutine of get() and getRaw()
    int   getColumns (           bool time);  ///< Returns number of columns seen so far.
    float get        (float row, bool time, const std::string & column);
    float get        (float row, bool time, float column);
    float getRaw     (float row, bool time, float column);
};
extern InputHolder * inputHelper (const std::string & fileName, InputHolder * oldHandle = 0);
#endif

// Output
class OutputHolder
{
public:

#   ifdef N2A_SPINNAKER

    std::string fileName;
    bool        raw;

    OutputHolder (const std::string & fileName);

    void trace (double now, const std::string & column, float value);
    void trace (double now, float               column, float value);

#   else

    std::string                         fileName;
    std::ostream *                      out;

    std::unordered_map<std::string,int> columnMap;
    std::vector<float>                  columnValues;
    int                                 columnsPrevious; ///< Number of columns written in previous cycle.
    bool                                traceReceived;   ///< Indicates that at least one column was touched during the current cycle.
    double                              t;
    bool                                raw;             ///< Indicates that column is an exact index.

    OutputHolder (const std::string & fileName);
    ~OutputHolder ();

    void trace (double now);  ///< Subroutine for other trace() functions.
    void trace (double now, const std::string & column, float value);
    void trace (double now, float               column, float value);
    void writeTrace ();

#   endif
};
extern OutputHolder * outputHelper (const std::string & fileName, OutputHolder * oldHandle = 0);
#ifndef N2A_SPINNAKER
extern void           outputClose ();  ///< Close all OutputHolders
#endif


class Simulatable;
class Part;
class PartTime;
class Wrapper;
class Population;
class Simulator;
class Integrator;
class Euler;
class RungeKutta;
class Visitor;
class VisitorStep;
class VisitorSpikeSingle;
class VisitorSpikeMulti;
class Event;
class EventStep;
class EventSpike;
class EventSpikeMulti;
class EventSpikeMultiLatch;
class EventSpikeSingle;
class EventSpikeSingleLatch;
class EventTarget;


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
    virtual void snapshot           ();             ///< save all members trashed by integration
    virtual void restore            ();             ///< restore all members trashed by integration
    virtual void pushDerivative     ();             ///< push D0; D0 = members
    virtual void multiplyAddToStack (float scalar); ///< D0 += members * scalar
    virtual void multiply           (float scalar); ///< members *= scalar
    virtual void addToMembers       ();             ///< members += D0; pop D0

    // Generic metadata
    virtual void path (std::string & result);
    virtual void getNamedValue (const std::string & name, std::string & value);
};

class Part : public Simulatable
{
public:
    Part * next; ///< All parts exist on one primary linked list, either in the simulator or the population's dead list.

    Part * before;   ///< For doubly-linked list of population members. Move to generated code.
    Part * after;

    // Simulation queue
    virtual void        setPrevious (Part * previous);
    virtual void        setVisitor  (VisitorStep * visitor);  ///< Informs this part of both its associated event and the specific thread.
    virtual EventStep * getEvent    ();  ///< @return the event this part is associated with. Provides t and dt.

    // Lifespan management
    virtual void die             (); ///< Set $live=0 (in some form) and decrement $n of our population. If accountable connection, decrement connection counts in target compartments.
    virtual void enterSimulation (); ///< Tells us we are going onto the simulator queue. Increment refcount on parts we directly access.
    virtual void leaveSimulation (); ///< Tells us we are leaving the simulator queue. Ask our population to put us on its dead list. Reduce refcount on parts we directly access, to indicate that they may be re-used.
    virtual bool isFree          (); ///< @return true if the part is ready to use, false if the we are still waiting on other parts that reference us.

    // Connection-specific accessors
    virtual void   setPart  (int i, Part * part);          ///< Assign the instance of population i referenced by this connection.
    virtual Part * getPart  (int i);                       ///< @return Pointer to the instance of population i.
    virtual int    getCount (int i);                       ///< @return Number of connections of this type attached to the part indexed by i.
    virtual void   project  (int i, int j, Vector3 & xyz); ///< Determine position of connection in space of population j based on position in space of population i. Population i must already be bound, but population j is generally not bound.

    // Accessors for $variables
    virtual float getLive ();              ///< @return 1 if we are in normal simulation. 0 if we have died. Default is 1.
    virtual float getP    ();              ///< Default is 1 (always create)
    virtual void  getXYZ  (Vector3 & xyz); ///< Default is [0;0;0].

    // Events
    virtual bool  eventTest  (int i);
    virtual float eventDelay (int i);
    virtual void  setLatch   (int i);
    virtual void  finalizeEvent ();  ///< Does finalize on any external references touched by some event in this part.
};

extern void removeMonitor (std::vector<Part *> & partList, Part * part);

/**
    Supports ability to dequeue and move to a different simulation period.
    Implements other half of doubly-linked list. It is possible to mix singly- and doubly-linked
    parts in the same queue, but only a doubly-linked part can dequeue outside of sim loop.
**/
class PartTime : public Part
{
public:
    Part * previous;
    VisitorStep * visitor;

    virtual void        setPrevious (Part * previous);
    virtual void        setVisitor  (VisitorStep * visitor);
    virtual EventStep * getEvent    ();
    virtual void        dequeue     ();  ///< Handles all direct requests (oustide of sim loop).
    virtual void        setPeriod   (float dt);
};

class WrapperBase : public PartTime
{
public:
    Population * population;  // The top-level population can never be a connection, only a compartment.

    virtual void init               ();
    virtual void integrate          ();
    virtual void update             ();
    virtual bool finalize           ();
    virtual void updateDerivative   ();
    virtual void finalizeDerivative ();

    virtual void snapshot           ();
    virtual void restore            ();
    virtual void pushDerivative     ();
    virtual void multiplyAddToStack (float scalar);
    virtual void multiply           (float scalar);
    virtual void addToMembers       ();
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
class Population : public Simulatable
{
public:
    Part * dead;      ///< Head of linked list of available parts, using Part::next.
    Part   live;      ///< Parts currently on simulator queue (regardless of $live), linked via before and after. Used for iterating populations to make connections.
    Part * old;       ///< Position in parts linked list of first old part. All parts before this were added in the current simulation cycle. If old==&live, then all parts are new.

    Population ();
    virtual ~Population ();  ///< Deletes all parts on our dead list.

    virtual Part * create   () = 0;        ///< Construct an instance of the kind of part this population holds. Caller is fully responsible for lifespan of result, unless it gives the part to us via add().
    virtual void   add      (Part * part); ///< The given part is going onto a simulator queue, but we may also account for it in other ways.
    virtual void   remove   (Part * part); ///< Move part to dead list, and update any other accounting for the part.
    virtual Part * allocate ();            ///< If a dead part is available, re-use it. Otherwise, create and add a new part.
    virtual void   resize   (int n);       ///< Add or kill instances until $n matches given n.

    virtual Population * getTarget (int i); ///< @return The end-point of a connection. Index starts at 0. An index out of range returns a null pointer.
    virtual void         connect   ();      ///< For a connection population, evaluate each possible connection (or some well-defined subset thereof).
    virtual void         clearNew  ();      ///< Reset newborn index

    virtual int   getK      (int i);
    virtual int   getMax    (int i);
    virtual int   getMin    (int i);
    virtual float getRadius (int i);
};

class More
{
public:
    bool operator() (const Event * a, const Event * b) const;
};
typedef std::priority_queue<Event *,std::vector<Event *>,More> priorityQueue;

/**
    Lifetime management: When the simulator shuts down, it must dequeue all
    parts. In general, a simulator will run until its queue is empty.
**/
class Simulator
{
public:
    priorityQueue                             queueEvent;    ///< Pending events in time order.
    std::vector<std::pair<Population *, int>> queueResize;   ///< Populations that need to change $n after the current cycle completes.
    std::queue<Population *>                  queueConnect;  ///< Connection populations that want to construct or recheck their instances after all populations are resized in current cycle.
    std::set<Population *>                    queueClearNew; ///< Populations whose newborn index needs to be reset.
    std::map<float,EventStep *>               periods;
    Integrator *                              integrator;
    bool                                      stop;
    Event *                                   currentEvent;
    enum {BEFORE, DURING, AFTER};
    int                                       eventMode = DURING;

    Simulator ();
    ~Simulator ();

    void run (); ///< Main entry point for simulation. Do all work.
    void updatePopulations ();
    void enqueue (Part * part, float dt); ///< Places part on event with period dt. If the event already exists, then the actual time till the part next executes may be less than dt, but thereafter will be exactly dt. Caller is responsible to call dequeue() or enterSimulation().

    // callbacks
    void resize   (Population * population, int n); ///< Schedule population to be resized at end of current cycle.
    void connect  (Population * population);        ///< Schedule connections to be evaluated at end of current cycle, after all resizing is done.
    void clearNew (Population * population);        ///< Schedule population to have its newborn index reset after all new connections are evaluated.
};
extern Simulator simulator;  // global singleton

class Integrator
{
public:
    // For now, we don't need a virtual dtor, because this class contains no data.
    virtual void run (Event & event) = 0;
};

class Euler : public Integrator
{
public:
    virtual void run (Event & event);
};

class RungeKutta : public Integrator
{
public:
    virtual void run (Event & event);
};

/**
    An instance of this function will normally call some operation on
    Visitor::part, passing the visitor itself as the parameter.
    Information specific to the operation is put into a capture by the
    caller, while information specific to the Simulator/Event is held in
    visitor.
**/
typedef std::function<void (Visitor * visitor)> visitorFunction;

class Event
{
public:
    double t;

    virtual ~Event ();

    virtual void run () = 0;  ///< Does all the work of a simulation cycle. This may be adapted to the specifics of the event type.
    virtual void visit (visitorFunction f) = 0;  ///< Applies function to each part associated with this event. May visit multiple parts in parallel using separate threads.
};

/**
    Holds parts that are formally queued for simulation.
    Executes on a periodic basis ("step" refers to dt). No other event type can hold parts for the simulator.
    Equivalently, all queued parts must belong to exactly one EventStep.
    This class may be heavier (more storage, more computation) than other events, as it is expected
    to live for the entire length of the simulation and repeatedly insert itself into the event queue.
**/
class EventStep : public Event
{
public:
    float dt;
    std::vector<VisitorStep *> visitors;

    EventStep (double t, float dt);
    virtual ~EventStep ();

    virtual void  run     ();
    virtual void  visit   (visitorFunction f);
    void          requeue ();  ///< Subroutine of run(). If our load of instances is non-empty, then get back in the simulation queue.
    void          enqueue (Part * part);
};

/**
    Lightweight class representing one-time events, of which there may be a large quantity.
**/
class EventSpike : public Event
{
public:
    int latch;
};

class EventSpikeSingle : public EventSpike
{
public:
    Part * target;

    virtual void run   ();
    virtual void visit (visitorFunction f);
};

class EventSpikeSingleLatch : public EventSpikeSingle
{
public:
    virtual void run ();
};

class EventSpikeMulti : public EventSpike
{
public:
    std::vector<Part *> * targets;

    virtual void run   ();
    virtual void visit (visitorFunction f);
    void setLatch ();
};

class EventSpikeMultiLatch : public EventSpikeMulti
{
public:
    virtual void run ();
};

/**
    The general interface through which a Part communicates with the Event that is currently processing it.
    This helper class enables Event to be lighter weight by not carrying extra fields that are
    only needed when actually iterating over parts.
**/
class Visitor
{
public:
    Event * event;
    Part *  part;  ///< Current instance being processed.

    Visitor (Event * event, Part * part = 0);

    virtual void visit (visitorFunction f); ///< Applies function to each instance on our local queue. Steps "part" through the list and calls f(this) each time.
};

/**
    Manages the subset of EventStep's load of instances assigned to a specific thread.
**/
class VisitorStep : public Visitor
{
public:
    Part   queue;    ///< The head of a singly-linked list. queue itself never executes, rather, its "next" field points to the first active part.
    Part * previous; ///< Points to the part immediately ahead of the current part.

    VisitorStep (EventStep * event);

    virtual void visit   (visitorFunction f);
    virtual void enqueue (Part * newPart);  ///< Puts newPart on our local queue. Called by EventStep::enqueue(), which balances load across all threads.
};

class VisitorSpikeMulti : public Visitor
{
public:
    VisitorSpikeMulti (EventSpikeMulti * event);

    virtual void visit (visitorFunction f);
};


#endif
