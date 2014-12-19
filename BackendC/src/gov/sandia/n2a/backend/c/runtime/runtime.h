#ifndef backend_c_runtime_h
#define backend_c_runtime_h


#include "fl/matrix.h"

#include <vector>

typedef fl::MatrixFixed<float,3,1> Vector3;


// Random number in [0, 1]
inline float
randf ()
{
  return (float) rand () / RAND_MAX;
}

/**
    Generate square and triangular waves.
    @return real values in [0,1].  Exact shape of wave depends on input parameters.
    @param t Current time. First rise starts at time 0.
    @param width Time from end of rise to start of fall.  That is, how long the output remains at 1.
    @param period Amount of time between start of rise in one cycle and start of rise in next cycle.
    If zero, function does not repeat.
    @param rise How long to change from 0 to 1. Default is instantaneous.
    @param fall How long to change from 1 to 0. Default is instantaneous.
**/
extern float pulse (float t, float width, float period = 0, float rise = 0, float fall = 0);

/**
    Logs values to be written to stdout at end of cycle.
    @param value The numeric value to save.
    @param column Label for the value. Once allocated, it never goes away for the remainder of the simulation.
    @return The value provide as input. Thus, trace() can be inserted into any expression.
**/
extern float trace        (float value, const std::string & column);
extern void  writeTrace   ();  ///< called only by top-level Population::finalize()
extern void  writeHeaders ();  ///< called only by main() on termination of simulation


class Part;
class Compartment;
class Connection;
class Population;
class PopulationCompartment;
class PopulationConnection;
class Simulator;
class Euler;
class RungeKutta;

/**
    The universal interface through which the runtime accesses model
    components. All parts are collected in populations, and all populations
    are members of parts. The top-level population contains at least one
    instance of the top-level model, but is not itself contained in any
    model.

    <p>Lifetime management: Generally, if a part contains populations, they
    appear as member variables that are destructed automatically. Parts
    may not be deleted until they have a zero referenceCount.
**/
class Part
{
public:
    virtual ~Part ();

    // Lifespan management
    virtual void die     (); ///< Set $live=0 (in some form) and decrement $n of our population. If a connection with $min or $max, decrement connection counts in respective target compartments.
    virtual void enqueue (); ///< Tells us we are going onto the simulator queue. Increment refcount on parts we directly access.
    virtual void dequeue (); ///< Tells us we are leaving the simulator queue. Ask our population to put us on its dead list. Reduce refcount on parts we directly access, to indicate that they may be re-used.
    virtual bool isFree  (); ///< @return true if the part is ready to use, false if the we are still waiting on other parts that reference us.

    // Interface for computing simulation steps
    virtual void init               (Simulator & simulator);              ///< Initialize all variables. A part must increment $n of its population, enqueue each of its contained populations and call their init(). A population must create its instances, enqueue them and call init(). If this is a connection with $min or $max, increment count in respective target compartments.
    virtual void integrate          (Simulator & simulator);
    virtual void prepare            ();
    virtual void update             (Simulator & simulator);
    virtual bool finalize           (Simulator & simulator);              ///< A population may init() and add new parts to simulator. @return true if this part is still live, false if it should die.
    virtual void prepareDerivative  ();                                   ///< Same as prepare(), but restricted to computing derivatives.
    virtual void updateDerivative   (Simulator & simulator);              ///< Same as update(), but restricted to computing derivatives.
    virtual void finalizeDerivative ();                                   ///< Same as finalize(), but restricted to computing derivatives.

    // Interface for numerical manipulation
    // This is deliberately a minimal set of functions to support Runge-Kutta, with folded push and pop operations.
    // This interface will no doubt need to be generalized when another integrator is added.
    virtual void pushIntegrated     ();                  ///< push I0; I0 = members
    virtual void popIntegrated      ();                  ///< pop I0; no change to members
    virtual void pushDerivative     ();                  ///< push D0; D0 = members
    virtual void multiplyAddToStack (float scalar);      ///< D0 += members * scalar
    virtual void multiply           (float scalar);      ///< members *= scalar
    virtual void addToMembers       ();                  ///< members += D0; pop D0

    // Accessors for $variables
    virtual float getLive ();                                  ///< @return 1 if we are in normal simulation. 0 if we have died. Default is 1.
    virtual float getP    (float __24live);                    ///< Default is 1 (always create)
    virtual void  getXYZ  (float __24live, Vector3 & __24xyz); ///< Default is [0;0;0].

    // Generic metadata
    virtual void getNamedValue (const std::string & name, std::string & value);

    // Memory management
    Part * next;  ///< All Parts exist on one primary linked list, either in the simulator or the population's dead list.
};

class Compartment : public Part
{
public:
    int __24index; ///< Unique ID of this part.
    Compartment * before;
    Compartment * after;
};

/**
    Note that a Part can be both a Compartment and a Connection.
**/
class Connection : public Part
{
public:
    virtual void   setPart  (int i, Part * part); ///< Assign the part referenced by this connection. @param i follows same order as PopulationConnection::getTarget()
    virtual Part * getPart  (int i);              ///< @return pointer to specific part referenced by this connection. @param i follows same order as PopulationConnection::getTarget()
    virtual int    getCount (int i);              ///< @return Number of connections of this type attached to the part instance indexed by i. @param i follows same order as PopulationConnection::getTarget()
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
class Population : public Part
{
public:
    Population ();
    virtual ~Population ();  ///< Deletes all parts on our dead list.

    virtual Part * create   () = 0;        ///< Construct an instance of the kind of part this population holds. Caller is fully responsible for lifespan of result, unless it gives the part to us via add().
    virtual void   add      (Part * part); ///< Tells us that part newly made by create() is moving onto simulator queue.
    virtual void   remove   (Part * part); ///< Tells us that part is moving off the simulator queue. Add it to dead.
    virtual Part * allocate ();            ///< If a dead part is available, re-use it. Otherwise, create and add a new part.

    Part * dead;  ///< Head of linked list of available parts, using Part::next.
};

/**
    Version of Population specific to Compartments.
**/
class PopulationCompartment : public Population
{
public:
    PopulationCompartment ();
    ~PopulationCompartment ();

    virtual void   add      (Part * part); ///< Assign an index to part and put it on our live list (ahead of old).
    virtual void   remove   (Part * part); ///< Remove part from live.
    virtual Part * allocate ();            ///< New part is inserted at head of live list (before old).

    virtual void prepare ();                             ///< Reset the old pointer.
    virtual void resize  (Simulator & simulator, int n); ///< Add or kill instances until $n matches given n.

    Compartment   live;      ///< Parts currently on simulator queue (regardless of $live), linked via Compartment::before and after. Used for crossing populations to make connections.
    Compartment * old;       ///< Position in parts linked list of first old part. All parts before this were added in the current simulation cycle. If old==&live, then all parts are new.
    int           __24n;     ///< Actual number of parts with $live==1. Maintained directly by parts as they are born or die. Requested number of parts must have a separate "next" variable.
    int           nextIndex; ///< Next available $index value. Indices are consumed only when add() is called. With garbage collection, there could be gaps in the set of used indices.
};

class PopulationConnection : public Population
{
public:
    virtual Population * getTarget (int i);                 ///< @return The end-point of a connection. These are ordered by $from, with indices starting at 0. An index that goes beyond the set of end-points returns a null pointer.
    virtual void         connect   (Simulator & simulator); ///< For a connection population, evaluate each possible connection (or some well-defined subset thereof).

    virtual int   getK      ();
    virtual int   getMax    (int i);
    virtual int   getMin    (int i);
    virtual float getRadius ();
};

/**
    Lifetime management: When the simulator shuts down, it must dequeue all
    parts. In general, a simulator will run until its queue is empty.
**/
class Simulator
{
public:
    Simulator ();
    virtual ~Simulator ();

    virtual void run       (); ///< Main entry point for simulation. Do all work.
    virtual void integrate (); ///< Perform one time step across all the parts contained in all the populations

    // callbacks
    virtual void enqueue (Part *);                                    ///< Put part onto the queue. The only way to dequeue is to return false from Part::finalize().
    virtual void move    (float dt);                                  ///< Change simulation period for the part that makes the call. We know which part is currently executing, so no need to pass it as a parameter.
    virtual void resize  (PopulationCompartment * population, int n); ///< Schedule compartment to be resized at end of current cycle.
    virtual void connect (PopulationConnection * population);         ///< Schedule connection population to be evaluated at end of current cycle, after all resizing is done.

    float t;
    float dt;
    Part *  queue; ///< All parts currently being simulated, regardless of type.
    Part ** p;     ///< Current position of simulation in queue. Needed to implement move().
    std::vector<std::pair<PopulationCompartment *, int> > resizeQueue;  ///< Populations that need to change $n after the current cycle completes.
    std::vector<PopulationConnection *>                   connectQueue; ///< Connection populations that want to construct or recheck their instances after all populations are resized in current cycle.
};

class Euler : public Simulator
{
public:
    virtual ~Euler ();

    virtual void integrate ();
};

class RungeKutta : public Simulator
{
public:
    virtual ~RungeKutta ();

    virtual void integrate ();
};


#endif
