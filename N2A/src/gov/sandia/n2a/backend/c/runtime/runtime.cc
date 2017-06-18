#include "runtime.h"

#include <cmath>
#include <iostream>

#include "fl/Matrix.tcc"
#include "fl/MatrixFixed.tcc"
#include "fl/Vector.tcc"
// As an FL source file, Neighbor.cc has "using namespace" statements in it. Therefore it must come last.
#include "Neighbor.cc"

template class MatrixAbstract<float>;
template class Matrix<float>;
template class MatrixFixed<float,3,1>;


// Functions -----------------------------------------------------------------

// Box-Muller method (polar variant) for Gaussian random numbers.
static bool haveNextGaussian = false;
static float nextGaussian;
float gaussian1 ()
{
    if (haveNextGaussian)
    {
        haveNextGaussian = false;
        return nextGaussian;
    }
    else
    {
        float v1, v2, s;
        do
        {
            v1 = uniform1 () * 2 - 1;   // between -1.0 and 1.0
            v2 = uniform1 () * 2 - 1;
            s = v1 * v1 + v2 * v2;
        }
        while (s >= 1 || s == 0);
        float multiplier = sqrt (- 2 * log (s) / s);
        nextGaussian = v2 * multiplier;
        haveNextGaussian = true;
        return v1 * multiplier;
    }
}

MatrixResult<float> gaussian (int dimension)
{
    Vector<float> * result = new Vector<float> (dimension);
    for (int i = 0; i < dimension; i++) (*result)[i] = gaussian1 ();
    return result;
}

MatrixResult<float> grid (int i, int nx, int ny, int nz)
{
    int sx = ny * nz;  // stride x

    // compute xyz in stride order
    Vector3 * result = new Vector3;
    (*result)[0] = ((i / sx) + 0.5f) / nx;  // (i / sx) is an integer operation, so remainder is truncated.
    i %= sx;
    (*result)[1] = ((i / nz) + 0.5f) / ny;
    (*result)[2] = ((i % nz) + 0.5f) / nz;
    return result;
}

float matrix (Matrix<float> * handle, float row, float column)
{
    // Just assume handle is good.
    row    = row    * handle->rows_    - 0.5;
    column = column * handle->columns_ - 0.5;
    int lastRow    = handle->rows_    - 1;
    int lastColumn = handle->columns_ - 1;
    int r = (int) floor (row);
    int c = (int) floor (column);
    if (r < 0)
    {
        if      (c <  0         ) return (*handle)(0,0         );
        else if (c >= lastColumn) return (*handle)(0,lastColumn);
        else
        {
            float b = column - c;
            return (1 - b) * (*handle)(0,c) + b * (*handle)(0,c+1);
        }
    }
    else if (r >= lastRow)
    {
        if      (c <  0         ) return (*handle)(lastRow,0         );
        else if (c >= lastColumn) return (*handle)(lastRow,lastColumn);
        else
        {
            float b = column - c;
            return (1 - b) * (*handle)(lastRow,c) + b * (*handle)(lastRow,c+1);
        }
    }
    else
    {
        float a = row - r;
        float a1 = 1 - a;
        if      (c <  0         ) return a1 * (*handle)(r,0         ) + a * (*handle)(r+1,0         );
        else if (c >= lastColumn) return a1 * (*handle)(r,lastColumn) + a * (*handle)(r+1,lastColumn);
        else
        {
            float b = column - c;
            return   (1 - b) * (a1 * (*handle)(r,c  ) + a * (*handle)(r+1,c  ))
                   +      b  * (a1 * (*handle)(r,c+1) + a * (*handle)(r+1,c+1));
        }
    }
}

float matrixRaw (Matrix<float> * handle, float row, float column)
{
    int r = (int) row;
    int c = (int) column;
    if      (r <  0               ) r = 0;
    else if (r >= handle->rows_   ) r = handle->rows_    - 1;
    if      (c <  0               ) c = 0;
    else if (c >= handle->columns_) c = handle->columns_ - 1;
    return (*handle)(r,c);
}

map<string, Matrix<float> *> matrixMap;
map<Matrix<float> *, string> matrixMapReverse;
Matrix<float> * matrixHelper (const string & fileName, Matrix<float> * oldHandle)
{
    if (oldHandle)
    {
        map<Matrix<float> *, string>::iterator r = matrixMapReverse.find (oldHandle);
        if (r != matrixMapReverse.end ())
        {
            if (r->second == fileName) return oldHandle;
            delete oldHandle;
            matrixMap       .erase (r->second);
            matrixMapReverse.erase (r);
        }
    }

    map<string, Matrix<float> *>::iterator i = matrixMap.find (fileName);
    if (i != matrixMap.end ()) return i->second;

    Matrix<float> * handle = new Matrix<float> ();
    matrixMap.insert (make_pair (fileName, handle));
    ifstream ifs (fileName.c_str ());
    ifs >> (*handle);
    if (! ifs.good ()) cerr << "Failed to open matrix file: " << fileName << endl;
    else if (handle->rows () == 0  ||  handle->columns () == 0)
    {
        cerr << "Ill-formed matrix in file: " << fileName << endl;
        handle->resize (1, 1);  // fallback matrix
        handle->clear ();       // set to 0
    }
    return handle;
}

float pulse (float t, float width, float period, float rise, float fall)
{
    if (period == 0.0)
    {
        if (t < 0) return 0.0;
    }
    else t = fmod (t, period);
    if (t < rise) return t / rise;
    t -= rise;
    if (t < width) return 1.0;
    t -= width;
    if (t < fall) return 1.0 - t / fall;
    return 0.0;
}

MatrixResult<float> uniform (int dimension)
{
    Vector<float> * result = new Vector<float> (dimension);
    for (int i = 0; i < dimension; i++) (*result)[i] = uniform1 ();
    return result;
}


// trace ---------------------------------------------------------------------

map<string, int> columnMap;  // TODO: should use unordered_map and a C11 compiler
vector<float>    columnValues;

float output (float value, const string & column)
{
    map<string, int>::iterator result = columnMap.find (column);
    if (result == columnMap.end ())
    {
        columnMap.insert (make_pair (column, columnValues.size ()));
        columnValues.push_back (value);
    }
    else
    {
        columnValues[result->second] = value;
    }
    return value;
}

void writeTrace ()
{
    const int last = columnValues.size () - 1;
    for (int i = 0; i <= last; i++)
    {
        float & c = columnValues[i];
        if (! isnan (c)) cout << c;
        if (i < last) cout << "\t";
        c = NAN;
    }
    if (last >= 0) cout << endl;
}

void writeHeaders ()
{
    const int count = columnMap.size ();
    const int last = count - 1;
    vector<string> headers (count);
    map<string, int>::iterator it;
    for (it = columnMap.begin (); it != columnMap.end (); it++)
    {
        headers[it->second] = it->first;
    }
    for (int i = 0; i < count; i++)
    {
        cout << headers[i];
        if (i < last) cout << "\t";
    }
    if (last >= 0) cout << endl;
}


// class Simulatable ---------------------------------------------------------

Simulatable::~Simulatable ()
{
}

void
Simulatable::clear ()
{
}

void
Simulatable::init (Simulator & simulator)
{
}

void
Simulatable::integrate (Simulator & simulator)
{
}

void
Simulatable::update (Simulator & simulator)
{
}

bool
Simulatable::finalize (Simulator & simulator)
{
    return true;
}

void
Simulatable::updateDerivative (Simulator & simulator)
{
}

void
Simulatable::finalizeDerivative ()
{
}

void
Simulatable::snapshot ()
{
}

void
Simulatable::restore ()
{
}

void
Simulatable::pushDerivative ()
{
}

void
Simulatable::multiplyAddToStack (float scalar)
{
}

void
Simulatable::multiply (float scalar)
{
}

void
Simulatable::addToMembers ()
{
}

void
Simulatable::getNamedValue (const string & name, string & value)
{
}


// class Part ----------------------------------------------------------------

void
Part::die ()
{
}

void
Part::enterSimulation ()
{
}

void
Part::leaveSimulation ()
{
}

bool
Part::isFree ()
{
    return true;
}

float
Part::getLive ()
{
    return 1;
}


// class Population ----------------------------------------------------------

Population::Population ()
{
    dead = 0;
}

Population::~Population ()
{
    Part * p = dead;
    while (p)
    {
        Part * next = p->next;
        delete p;
        p = next;
    }
}

void
Population::add (Part * part)
{
}

void
Population::remove (Part * part)
{
    part->next = dead;
    dead = part;
}

Part *
Population::allocate ()
{
    Part * result = 0;

    Part ** p = &dead;
    while (*p)
    {
        if ((*p)->isFree ())
        {
            result = *p;
            result->clear ();
            *p = (*p)->next;
            break;
        }
        p = & (*p)->next;
    }

    if (! result)
    {
        result = create ();
        add (result);
    }

    return result;
}


// class PopulationCompartment -----------------------------------------------

PopulationCompartment::PopulationCompartment ()
{
    live.before = &live;
    live.after  = &live;
    old         = &live;  // same as old=live.after
    n           = 0;
    nextIndex   = 0;
}

void
PopulationCompartment::add (Part * part)
{
    Compartment * compartment = (Compartment *) part;  // This cast is guaranteed safe by SimulationC
    compartment->__24index = nextIndex++;

    compartment->before        = &live;
    compartment->after         =  live.after;
    compartment->before->after = compartment;
    compartment->after->before = compartment;
}

void
PopulationCompartment::remove (Part * part)
{
    Compartment * compartment = (Compartment *) part;
    if (compartment == old) old = old->after;
    compartment->before->after = compartment->after;
    compartment->after->before = compartment->before;

    //Population::remove (part);  // just do it right here, for greater efficiency
    part->next = dead;
    dead = part;
}

Part *
PopulationCompartment::allocate ()
{
    Part * result = 0;

    Part ** p = &dead;
    while (*p)
    {
        if ((*p)->isFree ())
        {
            result = *p;
            result->clear ();

            // remove from dead
            *p = (*p)->next;

            // add to live
            Compartment * compartment = (Compartment *) result;
            compartment->before        = &live;
            compartment->after         =  live.after;
            compartment->before->after = compartment;
            compartment->after->before = compartment;

            break;
        }
        p = & (*p)->next;
    }

    if (! result)
    {
        result = create ();
        add (result);
    }

    return result;
}

void
PopulationCompartment::resize (Simulator & simulator, int n)
{
    while (this->n < n)
    {
        Part * p = allocate ();  // creates a part that knows how to find its population (that is, me)
        simulator.enqueue (p);
        p->init (simulator);  // increment $n
    }

    Compartment * p = live.before;
    while (this->n > n)
    {
        if (p == &live) throw "Inconsistent $n";
        if (p->getLive ()) p->die ();  // decrement $n. Can't dequeue part until next simulator cycle, so need to store $live.
        p = p->before;
    }
}


// class PopulationConnection ------------------------------------------------

Population *
PopulationConnection::getTarget (int i)
{
    return 0;
}

void
PopulationConnection::connect (Simulator & simulator)
{
    class KDTreeEntry : public Vector3
    {
    public:
        Compartment * part;
    };

    PopulationCompartment * A = dynamic_cast<PopulationCompartment *> (getTarget (0));
    PopulationCompartment * B = dynamic_cast<PopulationCompartment *> (getTarget (1));
    if (A == 0  ||  B == 0) return;  // Nothing to connect. This should never happen, though we might have a unary connection.
    int An = A->n;
    int Bn = B->n;
    if (An == 0  ||  Bn == 0) return;
    if (A->old == A->live.after  &&  B->old == B->live.after) return;  // Only proceed if there are some new parts. Later, we might consider periodic scanning among old parts.

    // Prepare nearest neighbor search structures on B
    float radius = getRadius (1);
    int   k      = getK (1);
    KDTreeEntry * entries = 0;
    vector<MatrixAbstract<float> *> entryPointers;
    KDTree NN;
    bool doNN = k  ||  radius;
    if (doNN)
    {
        entries = new KDTreeEntry[Bn];
        entryPointers.resize (Bn);
        Compartment * b = B->live.after;
        int i = 0;
        while (b != &B->live)
        {
            assert (i < Bn);
            KDTreeEntry & e = entries[i];
            b->getXYZ (e);
            e.part = b;
            entryPointers[i] = &e;
            b = b->after;
            i++;
        }
        NN.set (entryPointers);
        NN.k = k ? k : INT_MAX;
    }

    int Amin = getMin (0);
    int Amax = getMax (0);
    int Bmin = getMin (1);
    int Bmax = getMax (1);

    Connection * c = (Connection *) this->create ();

    // Scan AxB
    Compartment * Alast = A->old;
    Compartment * Blast = B->live.after;
    bool minSatisfied = false;
    while (! minSatisfied)
    {
        minSatisfied = true;

        // New A with all of B
        Compartment * a = A->live.after;
        while (a != A->old)
        {
            c->setPart (0, a);
            volatile int Acount;
            if (Amax  ||  Amin) Acount = c->getCount (0);
            if (Amax  &&  Acount >= Amax)  // early out: this part is already full, so skip
            {
                a = a->after;
                continue;
            }

            // Select the subset of B
            if (doNN)
            {
                c->setPart (1, B->live.after);  // give a dummy B object, in case xyz call breaks rules about only accessing A
                Vector3 xyz;
                c->project (0, 1, xyz);
                vector<MatrixAbstract<float> *> result;
                NN.find (xyz, result);
                int count = result.size ();
                vector<MatrixAbstract<float> *>::iterator it;
                for (it = result.begin (); it != result.end (); it++)
                {
                    Compartment * b = ((KDTreeEntry *) (*it))->part;

                    c->setPart (1, b);
                    if (Bmax  &&  c->getCount (1) >= Bmax) continue;  // no room in this B
                    float create = c->getP (simulator);
                    if (create <= 0  ||  create < 1  &&  create < uniform1 ()) continue;  // Yes, we need all 3 conditions. If create is 0 or 1, we do not do a random draw, since it should have no effect.
                    simulator.enqueue (c);
                    c->init (simulator);
                    Acount++;
                    c = (Connection *) this->create ();
                    c->setPart (0, a);

                    if (Amax  &&  Acount >= Amax) break;  // stop scanning B once this A is full
                }
            }
            else
            {
                Compartment * Bnext = Blast->before;  // will change if we make some connections
                if (Bnext == &B->live) Bnext = Bnext->before;
                Compartment * b = Blast;
                do
                {
                    b = b->after;
                    if (b == &B->live) b = b->after;

                    c->setPart (1, b);
                    if (Bmax  &&  c->getCount (1) >= Bmax) continue;  // no room in this B
                    float create = c->getP (simulator);
                    if (create <= 0  ||  create < 1  &&  create < uniform1 ()) continue;  // Yes, we need all 3 conditions. If create is 0 or 1, we do not do a random draw, since it should have no effect.
                    simulator.enqueue (c);
                    c->init (simulator);
                    c = (Connection *) this->create ();
                    c->setPart (0, a);
                    Bnext = b;

                    if (Amax)
                    {
                        if (++Acount >= Amax) break;  // stop scanning B once this A is full
                    }
                }
                while (b != Blast);
                Blast = Bnext;
            }

            if (Amin  &&  Acount < Amin) minSatisfied = false;
            a = a->after;
        }

        // New B with old A (new A x new B is already covered in case above)
        if (A->old != &A->live)  // There exist some old A
        {
            Compartment * b = B->live.after;
            while (b != B->old)
            {
                c->setPart (1, b);
                int Bcount;
                if (Bmax  ||  Bmin) Bcount = c->getCount (1);
                if (Bmax  &&  Bcount >= Bmax)
                {
                    b = b->after;
                    continue;
                }

                // TODO: the projection from A to B could be inverted, and another spatial search structure built.
                // For now, we don't use spatial constraints.

                Compartment * Anext;
                if (Alast == A->old) Anext = A->live.before;
                else                 Anext = Alast->before;
                a = Alast;
                do
                {
                    a = a->after;
                    if (a == &A->live) a = A->old;

                    c->setPart (0, a);
                    if (Amax  &&  c->getCount (0) >= Amax) continue;
                    float create = c->getP (simulator);
                    if (create <= 0  ||  create < 1  &&  create < uniform1 ()) continue;
                    c->init (simulator);
                    simulator.enqueue (c);
                    c = (Connection *) this->create ();
                    c->setPart (1, b);
                    Anext = a;

                    if (Bmax)
                    {
                        if (++Bcount >= Bmax) break;
                    }
                }
                while (a != Alast);
                Alast = Anext;

                if (Bmin  &&  Bcount < Bmin) minSatisfied = false;
                b = b->after;
            }
        }

        // Check if minimums have been satisfied for old parts. New parts in both A and B were checked above.
        if (Amin  &&  minSatisfied)
        {
            Compartment * a = A->old;
            while (a != &A->live)
            {
                c->setPart (0, a);
                if (c->getCount (0) < Amin)
                {
                    minSatisfied = false;
                    break;
                }
                a = a->after;
            }
        }
        if (Bmin  &&  minSatisfied)
        {
            Compartment * b = B->old;
            while (b != &B->live)
            {
                c->setPart (1, b);
                if (c->getCount (1) < Bmin)
                {
                    minSatisfied = false;
                    break;
                }
                b = b->after;
            }
        }
    }
    delete c;
    delete [] entries;
}

int
PopulationConnection::getK (int i)
{
    return 0;
}

int
PopulationConnection::getMax (int i)
{
    return 0;
}

int
PopulationConnection::getMin (int i)
{
    return 0;
}

float
PopulationConnection::getRadius (int i)
{
    return 0;
}


// class Compartment ---------------------------------------------------------

void
Compartment::getXYZ (Vector3 & xyz)
{
    xyz[0] = 0;
    xyz[1] = 0;
    xyz[2] = 0;
}


// class Connection ----------------------------------------------------------

void
Connection::setPart (int i, Part * part)
{
}

Part *
Connection::getPart (int i)
{
    return 0;
}

int
Connection::getCount (int i)
{
    return 0;
}

void
Connection::project (int i, int j, Vector3 & xyz)
{
    xyz[0] = 0;
    xyz[1] = 0;
    xyz[2] = 0;
}

float
Connection::getP (Simulator & simulator)
{
    return 1;
}


// class Simulator -----------------------------------------------------------

Simulator::Simulator ()
{
    t     = 0;
    dt    = 1e-4;
    queue = 0;
    p     = &queue;
}

Simulator::~Simulator ()
{
    while (queue)
    {
        Part * old = queue;
        queue = queue->next;
        old->leaveSimulation ();
    }
}

void
Simulator::run ()
{
    t = 0;  // updated in middle of loop below, just before integration
    while (queue)
    {
        // Evaluate connection populations that have requested it
        vector<PopulationConnection *>::iterator connectIterator;
        for (connectIterator = connectQueue.begin (); connectIterator != connectQueue.end (); connectIterator++)
        {
            (*connectIterator)->connect (*this);
        }
        connectQueue.clear ();

        // Update parts
        t += dt;
        integrate ();
        p = &queue;
        while (*p)
        {
            (*p)->update (*this);
            p = & (*p)->next;
        }
        p = &queue;
        while (*p)
        {
            if ((*p)->finalize (*this))  // part remains in queue
            {
                p = & (*p)->next;
            }
            else  // part leaves queue
            {
                Part * old = *p;
                *p = (*p)->next;  // note that value of p itself remains unchanged, but its referent points to another part.
                old->leaveSimulation ();
            }
        }

        // Resize populations that have requested it
        vector<pair<PopulationCompartment *, int> >::iterator resizeIterator;
        for (resizeIterator = resizeQueue.begin (); resizeIterator != resizeQueue.end (); resizeIterator++)
        {
            resizeIterator->first->resize (*this, resizeIterator->second);
        }
        resizeQueue.clear ();
    }
}

void
Simulator::integrate ()
{
}

void
Simulator::enqueue (Part * part)
{
    part->next = queue;
    queue = part;
    part->enterSimulation ();
}

void
Simulator::move (float dt)
{
    // TODO: select correct event and move current part to it. If no such event exists, create a new one.
    // When above is implemented, we will never change our own dt.
    this->dt = dt;
}

void
Simulator::resize (PopulationCompartment * population, int n)
{
    resizeQueue.push_back (make_pair (population, n));
}

void
Simulator::connect (PopulationConnection * population)
{
    connectQueue.push_back (population);
}


// class Euler ---------------------------------------------------------------

Euler::~Euler ()
{
}

void
Euler::integrate ()
{
    p = &queue;
    while (*p)
    {
        (*p)->integrate (*this);
        p = & (*p)->next;
    }
}


// class RungeKutta ----------------------------------------------------------

RungeKutta::~RungeKutta ()
{
}

void
RungeKutta::integrate ()
{
    // Save current values of t and dt
    const float t  = this->t;
    const float dt = this->dt;

    // k1
    p = &queue;
    while (*p)
    {
        (*p)->snapshot ();
        (*p)->pushDerivative ();
        p = & (*p)->next;
    }

    // k2 and k3
    this->dt /= 2.0f;
    this->t  -= this->dt;  // t is the current point in time, so we must look backward half a timestep
    for (int i = 0; i < 2; i++)
    {
        p = &queue;
        while (*p)
        {
            (*p)->integrate (*this);
            p = & (*p)->next;
        }

        p = &queue;
        while (*p)
        {
            (*p)->updateDerivative (*this);
            p = & (*p)->next;
        }

        p = &queue;
        while (*p)
        {
            (*p)->finalizeDerivative ();
            (*p)->multiplyAddToStack (2.0f);
            p = & (*p)->next;
        }
    }

    // k4
    this->dt = dt;  // restore original values
    this->t  = t;
    {  // curly brace is here just to make organization clear
        p = &queue;
        while (*p)
        {
            (*p)->integrate (*this);
            p = & (*p)->next;
        }

        p = &queue;
        while (*p)
        {
            (*p)->updateDerivative (*this);
            p = & (*p)->next;
        }

        p = &queue;
        while (*p)
        {
            (*p)->finalizeDerivative ();
            (*p)->addToMembers ();  // clears stackDerivative
            p = & (*p)->next;
        }
    }

    p = &queue;
    while (*p)
    {
        (*p)->multiply (1.0 / 6.0);
        p = & (*p)->next;
    }

    p = &queue;
    while (*p)
    {
        (*p)->integrate (*this);
        p = & (*p)->next;
    }

    p = &queue;
    while (*p)
    {
        (*p)->restore ();
        p = & (*p)->next;
    }
}
