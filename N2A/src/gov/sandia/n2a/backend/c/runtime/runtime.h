#ifndef backend_c_runtime_h
#define backend_c_runtime_h


#include "fl/matrix.h"

#include <vector>
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
class MatrixInput : public fl::Matrix<float>
{
public:
    std::string fileName;

    float get    (float row, float column);
    float getRaw (float row, float column);
};
extern MatrixInput * matrixHelper (const std::string & fileName, MatrixInput * oldHandle = 0);

// Input
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

// Output
class OutputHolder
{
public:
    std::unordered_map<std::string,int> columnMap;
    std::vector<float>                  columnValues;
    int                                 columnsPrevious; ///< Number of columns written in previous cycle.
    bool                                traceReceived;   ///< Indicates that at least one column was touched during the current cycle.
    float                               t;
    std::string                         fileName;
    std::ostream *                      out;
    bool                                raw;             ///< Indicates that column is an exact index.

    OutputHolder (const std::string & fileName);
    ~OutputHolder ();

    void trace (float now);  ///< Subroutine for other trace() functions.
    void trace (float now, const std::string & column, float value);
    void trace (float now, float               column, float value);
    void writeTrace ();
};
extern OutputHolder * outputHelper (const std::string & fileName, OutputHolder * oldHandle = 0);
extern void           outputClose ();  ///< Close all OutputHolders


class Simulatable;
class Part;
class Wrapper;
class Population;
class Simulator;
class Euler;
class RungeKutta;

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
    virtual void init               (Simulator & simulator);              ///< Initialize all variables. A part must increment $n of its population, enqueue each of its contained populations and call their init(). A population must create its instances, enqueue them and call init(). If this is a connection with $min or $max, increment count in respective target compartments.
    virtual void integrate          (Simulator & simulator);
    virtual void update             (Simulator & simulator);
    virtual bool finalize           (Simulator & simulator);              ///< A population may init() and add new parts to simulator. @return true if this part is still live, false if it should be removed from simulator queue.
    virtual void updateDerivative   (Simulator & simulator);              ///< Same as update(), but restricted to computing derivatives.
    virtual void finalizeDerivative ();                                   ///< Same as finalize(), but restricted to computing derivatives.

    // Interface for numerical manipulation
    // This is deliberately a minimal set of functions to support Runge-Kutta, with folded push and pop operations.
    // This interface will no doubt need to be generalized when another integrator is added.
    virtual void snapshot           ();                  ///< save all members trashed by integration
    virtual void restore            ();                  ///< restore all members trashed by integration
    virtual void pushDerivative     ();                  ///< push D0; D0 = members
    virtual void multiplyAddToStack (float scalar);      ///< D0 += members * scalar
    virtual void multiply           (float scalar);      ///< members *= scalar
    virtual void addToMembers       ();                  ///< members += D0; pop D0

    // Generic metadata
    virtual void path (std::string & result);
    virtual void getNamedValue (const std::string & name, std::string & value);
};

class Part : public Simulatable
{
public:
    // Memory management
    Part * next;  ///< All parts exist on one primary linked list, either in the simulator or the population's dead list.
    Part * before;  ///< For doubly-linked list of population members. Move to generated code.
    Part * after;

    // Lifespan management
    virtual void die             (); ///< Set $live=0 (in some form) and decrement $n of our population. If a connection with $min or $max, decrement connection counts in respective target compartments.
    virtual void enterSimulation (); ///< Tells us we are going onto the simulator queue. Increment refcount on parts we directly access.
    virtual void leaveSimulation (); ///< Tells us we are leaving the simulator queue. Ask our population to put us on its dead list. Reduce refcount on parts we directly access, to indicate that they may be re-used.
    virtual bool isFree          (); ///< @return true if the part is ready to use, false if the we are still waiting on other parts that reference us.

    // Connection-specific accessors
    virtual void   setPart  (int i, Part * part);          ///< Assign the instance of population i referenced by this connection.
    virtual Part * getPart  (int i);                       ///< @return Pointer to the instance of population i.
    virtual int    getCount (int i);                       ///< @return Number of connections of this type attached to the part indexed by i.
    virtual void   project  (int i, int j, Vector3 & xyz); ///< Determine position of connection in space of population j based on position in space of population i. Population i must already be bound, but population j is generally not bound.

    // Accessors for $variables
    virtual float getLive ();                      ///< @return 1 if we are in normal simulation. 0 if we have died. Default is 1.
    virtual float getP    (Simulator & simulator); ///< Default is 1 (always create)
    virtual void  getXYZ  (Vector3 & xyz);         ///< Default is [0;0;0].
};

class WrapperBase : public Part
{
public:
    Population * population;  // The top-level population can never be a connection, only a compartment.

    virtual void init               (Simulator & simulator);
    virtual void integrate          (Simulator & simulator);
    virtual void update             (Simulator & simulator);
    virtual bool finalize           (Simulator & simulator);
    virtual void updateDerivative   (Simulator & simulator);
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

    virtual Part * create   () = 0;                         ///< Construct an instance of the kind of part this population holds. Caller is fully responsible for lifespan of result, unless it gives the part to us via add().
    virtual void   add      (Part * part);                  ///< The given part is going onto a simulator queue, but we may also account for it in other ways.
    virtual void   remove   (Part * part);                  ///< Move part to dead list, and update any other accounting for the part.
    virtual Part * allocate ();                             ///< If a dead part is available, re-use it. Otherwise, create and add a new part.
    virtual void   resize   (Simulator & simulator, int n); ///< Add or kill instances until $n matches given n.

    virtual Population * getTarget (int i);                 ///< @return The end-point of a connection. Index starts at 0. An index out of range returns a null pointer.
    virtual void         connect   (Simulator & simulator); ///< For a connection population, evaluate each possible connection (or some well-defined subset thereof).

    virtual int   getK      (int i);
    virtual int   getMax    (int i);
    virtual int   getMin    (int i);
    virtual float getRadius (int i);
};

/**
    Lifetime management: When the simulator shuts down, it must dequeue all
    parts. In general, a simulator will run until its queue is empty.
**/
class Simulator
{
public:
    float t;
    float dt;
    Part *  queue; ///< All parts currently being simulated, regardless of type.
    Part ** p;     ///< Current position of simulation in queue. Needed to implement move().
    std::vector<std::pair<Population *, int> > resizeQueue;  ///< Populations that need to change $n after the current cycle completes.
    std::vector<Population *>                  connectQueue; ///< Connection populations that want to construct or recheck their instances after all populations are resized in current cycle.

    Simulator ();
    virtual ~Simulator ();

    virtual void run       (); ///< Main entry point for simulation. Do all work.
    virtual void integrate (); ///< Perform one time step across all the parts contained in all the populations

    // callbacks
    virtual void enqueue (Part * part);                    ///< Put part onto the queue and calls Part::enqueue(). The only way to dequeue is to return false from Part::finalize().
    virtual void move    (float dt);                       ///< Change simulation period for the part that makes the call. We know which part is currently executing, so no need to pass it as a parameter.
    virtual void resize  (Population * population, int n); ///< Schedule compartment to be resized at end of current cycle.
    virtual void connect (Population * population);        ///< Schedule connection population to be evaluated at end of current cycle, after all resizing is done.
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
