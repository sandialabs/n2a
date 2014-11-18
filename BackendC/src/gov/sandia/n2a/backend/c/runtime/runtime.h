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


class Part;
class Population;
class Compartment;
class Connection;
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

    // Interface for computing simulation steps
    virtual void getPopulations     (std::vector<Population *> & result); ///< Append to result any populations contained as members of this part.
    virtual void init               (Simulator & simulator);
    virtual void integrate          (Simulator & simulator);
    virtual void prepare            ();
    virtual void update             (Simulator & simulator);
    virtual bool finalize           (Simulator & simulator);              ///< @return true if this part is still live, false if it should die
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

    // Lifespan management
    virtual void die     (); ///< Make getLive() return false, until the next call to live(). If possible, send death message to all parts that should die because of our death.
    virtual void release (); ///< Reduce count on parts we reference. Indicates we are leaving the simulator queue, so referenced parts may safely be re-used.
    virtual bool live    (); ///< Put a dead part back into service. @return true if the part is ready to use, false if the we are still waiting on other parts that reference us.

    // Accessors for $variables
    virtual float                   getLive ();               ///< @return 1 if we are in normal simulation. 0 if we have died. Default is 1.
    virtual float                   getP    (float __24init, float __24live); ///< Default is 1 (always create)
    virtual fl::MatrixResult<float> getXYZ  (float __24init, float __24live); ///< Default is [0;0;0]. TODO: consider passing vector by reference, for efficiency

    // Generic metadata
    virtual void getNamedValue (const std::string & name, std::string & value);
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
    Population ();  ///< Zero out lastPartSize
    virtual ~Population ();

    virtual Part * create   () = 0;        ///< Construct an instance of the kind of part this population holds. Caller is fully responsible for lifespan of result, unless it pushes the part onto our _parts collection.
    Part *         allocate ();            ///< Convenience function for re-using or creating a part, depending on state of nextEntry.
    virtual void   kill     (Part * part); ///< Receive part back from simulator and either destruct or make available for reuse.
    virtual void   die      ();

    virtual int    getK      ();
    virtual int    getMax    (int i);
    virtual int    getMin    (int i);
    virtual float  getN      ();
    virtual float  getRadius ();
    virtual int    getRef    ();  ///< @return the index (in the collection returned by getPopulations) of the primary population. If this is not a connection, then retun -1.

    std::vector<Part *> parts;
    int liveCount;  ///< Number of live entries in parts before the current cycle. These are stored compactly at the beginning of the vector.
    int nextEntry;  ///< Next available position, which is occupied by a "dead" part if nextEntry < parts.size (). (nextEntry - liveCount) gives the number of newly created parts in a given cycle.
};

class Compartment : public Part
{
public:
    int __24index;  ///< Unique ID of this part. No relationship with _parts[index], as parts may be swapped for various reasons.
};

class Connection : public Part
{
public:
    virtual void   setPart (int i, Part * part); ///< Assign the part referenced by this connection. The given index i follows same order as getRef()
    virtual Part * getPart (int i);              ///< @return pointer to specific part referenced by this connection. The given index i follows same order as getRef()
};

class Simulator
{
public:
    Simulator ();
    virtual ~Simulator ();

    virtual void run       (); ///< Main entry point for simulation. Do all work.
    virtual void integrate (); ///< Perform one time step across all the parts contained in all the populations

    float t;
    float dt;
    std::vector<Population *> populations;  ///< ALL populations currently being simulated, regardless of type.
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
