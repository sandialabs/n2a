#ifndef backend_c_runtime_h
#define backend_c_runtime_h


#include "fl/matrix.h"

#include <vector>

typedef fl::MatrixFixed<float,3,1> Vector3;


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

// Rule: Anywhere a runtime name might conflict with a generated name, the
// runtime name gets prefixed with an underscore.

class _Part;
class _Population;
class _Compartment;
class _Connection;

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
class _Part
{
public:
    virtual ~_Part ();

    // Interface for computing simulation steps
    virtual void getPopulations     (std::vector<_Population *> & result);         ///< Append to result any populations contained as members of this part.
    virtual void init               (float _24t, float & _24dt);                   ///< $dt may be modified; it is passed in with a value of 0
    virtual void integrate          (float _24t, float _24dt);
    virtual void prepare            ();
    virtual void update             (float _24t, float & _24dt);                   ///< $dt may be modified
    virtual void finalize           (float _24t, float & _24dt);
    virtual void prepareDerivative  ();                                            ///< Same as prepare(), but restricted to computing derivatives.
    virtual void updateDerivative   (float _24t, float _24dt);                     ///< Same as update(), but restricted to computing derivatives.
    virtual void finalizeDerivative ();                                            ///< Same as finalize(), but restricted to computing derivatives.

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
    virtual float getP   (const Vector3 & _24xyz); ///< Default is 1 (always create)
    virtual void  getXYZ (Vector3 & _24xyz);       ///< Default is [0,0,0].

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
class _Population : public _Part
{
public:
    _Population ();  ///< Zero out lastPartSize
    virtual ~_Population ();

    virtual _Part * create   () = 0;                 ///< Construct an instance of the kind of part this population holds. Caller is fully responsible for lifespan of result, unless it pushes the part onto our _parts collection.
    _Part *         allocate (float t, float & dt);  ///< Convenience function for re-using or creating a part, depending on state of nextEntry.
    virtual void    kill     (_Part *);

    virtual int    getK      ();
    virtual int    getMax    (int i);
    virtual int    getMin    (int i);
    virtual float  getN      ();
    virtual float  getRadius ();
    virtual int    getRef    ();  ///< @return the index (in the collection returned by getPopulations) of the primary population. If this is not a connection, then retun -1.

    std::vector<_Part *> _parts;
    int liveCount;  ///< Number of live entries in _parts before the current cycle. These are stored compactly at the beginning of the vector.
    int nextEntry;  ///< Next available position, which is occupied by a "dead" part if nextEntry < _parts.size (). (nextEntry - liveCount) gives the number of newly created parts in a given cycle.
};

class _Compartment : public _Part
{
public:
    int _24index;  ///< Unique ID of this part. No relationship with _parts[index], as parts may be swapped for various reasons.
};

class _Connection : public _Part
{
public:
    virtual void    setPart (int i, _Part * part);  ///< Assign the part referenced by this connection. The given index i follows same order as getRef()
    virtual _Part * getPart (int i);  ///< @return pointer to specific part referenced by this connection. The given index i follows same order as getRef()
};

class _Simulator
{
public:
    _Simulator ();
    virtual ~_Simulator ();

    virtual void run       ();                                                             ///< Main entry point for simulation. Do all work.
    virtual void integrate (std::vector<_Population *> & populations, float t, float dt);  ///< Perform one time step across all the parts contained in all the populations

    float dt;
    std::vector<_Population *> populations;  ///< ALL populations currently being simulated, regardless of type.
};

class _Euler : public _Simulator
{
public:
    virtual ~_Euler ();

    virtual void integrate (std::vector<_Population *> & populations, float t, float dt);
};

class _RungeKutta : public _Simulator
{
public:
    virtual ~_RungeKutta ();

    virtual void integrate (std::vector<_Population *> & populations, float t, float dt);
};


#endif
