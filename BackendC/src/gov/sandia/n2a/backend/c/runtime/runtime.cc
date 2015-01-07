#include "runtime.h"

#include <cmath>
#include <iostream>

#include "fl/Matrix.tcc"
#include "fl/MatrixFixed.tcc"
#include "fl/Vector.tcc"
// As an FL source file, Neighbor.cc has "using namespace" statements in it. Therefore it must come last.
#include "Neighbor.cc"


// Functions -----------------------------------------------------------------

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

map<string, int> columnMap;  // TODO: should use unordered_map and a C11 compiler
vector<float>    columnValues;

float trace (float value, const string & column)
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
    cout << endl;
}


// class Simulatable ---------------------------------------------------------

Simulatable::~Simulatable ()
{
}

void
Simulatable::init (Simulator & simulator)
{
}

void
Simulatable::prepare ()
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
Simulatable::prepareDerivative ()
{
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
Simulatable::pushIntegrated ()
{
}

void
Simulatable::popIntegrated ()
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
Part::enqueue ()
{
}

void
Part::dequeue ()
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

float
Part::getP (float __24live)
{
    return 1;
}

void
Part::getXYZ (float __24live, Vector3 & xyz)
{
    xyz[0] = 0;
    xyz[1] = 0;
    xyz[2] = 0;
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
    __24n       = 0;
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
PopulationCompartment::prepare ()
{
    old = live.after;  // if old==live, then everything is new
}

void
PopulationCompartment::resize (Simulator & simulator, int n)
{
    while (__24n < n)
    {
        Part * p = allocate ();  // creates a part that knows how to find its population (that is, me)
        simulator.enqueue (p);
        p->init (simulator);  // increment $n
    }

    Compartment * p = live.before;
    while (__24n > n)
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
    int An = A->__24n;
    int Bn = B->__24n;
    if (An == 0  ||  Bn == 0) return;
    if (A->old == A->live.after  &&  B->old == B->live.after) return;  // Only proceed if there are some new parts. Later, we might consider periodic scanning among old parts.

    // Prepare nearest neighbor search structures on B
    float radius = getRadius ();
    int   k      = getK ();
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
            b->getXYZ (1, e);
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
                c->getXYZ (0, xyz);
                vector<MatrixAbstract<float> *> result;
                NN.find (xyz, result);
                int count = result.size ();
                vector<MatrixAbstract<float> *>::iterator it;
                for (it = result.begin (); it != result.end (); it++)
                {
                    Compartment * b = ((KDTreeEntry *) (*it))->part;

                    c->setPart (1, b);
                    if (Bmax  &&  c->getCount (1) >= Bmax) continue;  // no room in this B
                    float create = c->getP (0);
                    if (create <= 0  ||  create < 1  &&  create < randf ()) continue;  // Yes, we need all 3 conditions. If create is 0 or 1, we do not do a random draw, since it should have no effect.
                    c->init (simulator);
                    simulator.enqueue (c);
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
                    float create = c->getP (0);
                    if (create <= 0  ||  create < 1  &&  create < randf ()) continue;  // Yes, we need all 3 conditions. If create is 0 or 1, we do not do a random draw, since it should have no effect.
                    c->init (simulator);
                    simulator.enqueue (c);
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
                    float create = c->getP (0);
                    if (create <= 0  ||  create < 1  &&  create < randf ()) continue;
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
PopulationConnection::getK ()
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
PopulationConnection::getRadius ()
{
    return 0;
}


// class Compartment ---------------------------------------------------------


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


// class Simulator -----------------------------------------------------------

Simulator::Simulator ()
{
    dt = 1e-4;
    queue = 0;
}

Simulator::~Simulator ()
{
    while (queue)
    {
        Part * old = queue;
        queue = queue->next;
        old->dequeue ();
    }
}

void
Simulator::run ()
{
    if (queue == 0) return;
    // TODO: eliminate concept of duration; instead use equation: Model.$p=$t<duration
    string value = "100";
    queue->getNamedValue ("duration", value);  // The first part in the queue at the start of the run is presumably the top-level model.
    float duration = atof (value.c_str ());

    t = 0;  // updated in middle of loop below, just before integration
    while (queue != 0  &&  t <= duration)
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
            (*p)->prepare ();
            p = & (*p)->next;
        }
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
                old->dequeue ();
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
    part->enqueue ();
}

void
Simulator::move (float dt)
{
    // TODO: select correct event and move current part to it. If no such event exists, create a new one.
    this->dt = dt;  // TODO: when above is implemented, we will never change our own dt.
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
        (*p)->next->integrate (*this);
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
    while ((*p)->next)
    {
        (*p)->next->pushIntegrated ();
        (*p)->next->pushDerivative ();
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
            (*p)->prepareDerivative ();
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
            (*p)->prepareDerivative ();
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
        (*p)->popIntegrated ();  // clears stackIntgrated
        p = & (*p)->next;
    }
}
