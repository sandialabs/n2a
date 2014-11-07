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


// class Part ----------------------------------------------------------------

Part::~Part ()
{
}

void
Part::getPopulations (vector<Population *> & resutl)
{
}

void
Part::init (Simulator & simulator)
{
}

void
Part::prepare ()
{
}

void
Part::integrate (Simulator & simulator)
{
}

void
Part::update (Simulator & simulator)
{
}

bool
Part::finalize (Simulator & simulator)
{
    return true;
}

void
Part::prepareDerivative ()
{
}

void
Part::updateDerivative (Simulator & simulator)
{
}

void
Part::finalizeDerivative ()
{
}

void
Part::pushIntegrated ()
{
}

void
Part::popIntegrated ()
{
}

void
Part::pushDerivative ()
{
}

void
Part::multiplyAddToStack (float scalar)
{
}

void
Part::multiply (float scalar)
{
}

void
Part::addToMembers ()
{
}

void
Part::die ()
{
}

void
Part::release ()
{
}

bool
Part::live ()
{
    return true;
}

float
Part::getLive ()
{
    return 1;
}

float
Part::getP (float __24init)
{
    return 1;
}

MatrixResult<float>
Part::getXYZ (float __24init)
{
    Vector<float> * result = new Vector<float> (3);
    result->clear ();
    return result;
}

void
Part::getNamedValue (const string & name, string & value)
{
}


// class Population ----------------------------------------------------------

Population::Population ()
{
    liveCount = 0;
    nextEntry = 0;
}

Population::~Population ()
{
}

Part *
Population::allocate ()
{
    Part * result = 0;
    if (nextEntry < parts.size ())
    {
        result = parts[nextEntry];
    }
    if (! result)
    {
        result = create ();
        parts.push_back (result);
        if (Compartment * compartment = dynamic_cast<Compartment *> (result))
        {
        	compartment->__24index = nextEntry;
        }
    }
    nextEntry++;
    return result;
}

void
Population::kill (Part * part)
{
    for (int i = 0; i < nextEntry; i++)
    {
        if (parts[i] == part)
        {
            if (i < nextEntry - 1) swap (parts[i], parts[nextEntry-1]);
            assert (nextEntry > 0);
            nextEntry--;
            break;
        }
    }
}

void
Population::die ()
{
    for (int i = 0; i < nextEntry; i++) parts[i]->die ();
}

int
Population::getK ()
{
    return 0;
}

int
Population::getMax (int i)
{
    return 0;
}

int
Population::getMin (int i)
{
    return 0;
}

float
Population::getN ()
{
    return 1;
}

float
Population::getRadius ()
{
    return 0;
}

int
Population::getRef ()
{
    return -1;  // indicates this population is not a connection type
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


// class Simulator -----------------------------------------------------------

Simulator::Simulator ()
{
    dt = 1e-4;
}

Simulator::~Simulator ()
{
}

class KDTreeEntry : public Vector3
{
public:
    Part * part;
    KDTreeEntry & operator = (const MatrixAbstract<float> & that)
    {
        (*this)[0] = that[0];
        (*this)[1] = that[1];
        (*this)[2] = that[2];
        return *this;
    }
};

void
Simulator::run ()
{
    string value = "1";
    populations[0]->getNamedValue ("duration", value);
    float duration = atof (value.c_str ());

    float t = 0;  // updated in middle of loop below, just after integration
    while (t <= duration)
    {
        // Create new instances
        for (int i = 0; i < populations.size (); i++)  // since populations.size() is evaluated each cycle, this allows the string to grow
        {
            Population * p = populations[i];

            // Determine if population size has changed since last cycle
            if (p->getRef () >= 0) continue;  // this is a connection population, so nothing more to do
            int n1 = floor (p->getN ());
            // Note: we do not shrink populations using $n, only grow them.
            while (p->nextEntry < n1)
            {
                Part * q = p->allocate ();
                q->init (*this);

                // Initialize and add any newly created populations
                int oldCount = populations.size ();
                q->getPopulations (populations);  // append any populations contained in the new part
                int newCount = populations.size ();
                for (int j = oldCount; j < newCount; j++)
                {
                    populations[j]->init (*this);
                }
            }
        }

        // Create new connections
        vector<Population *>::iterator it;
        for (it = populations.begin (); it != populations.end (); it++)
        {
            Population * p = *it;

            int Aref = p->getRef ();
            if (Aref < 0) continue;  // this is a compartment population, so no connections to make
            int Bref = 1 - Aref;  // presuming only two endpoints

            vector<Population *> references;
            p->getPopulations (references);
            assert (references.size () == 2);
            Population * A = references[Aref];
            Population * B = references[Bref];

            int An1 = floor (A->getN ());
            int Bn1 = floor (B->getN ());
            int An0 = A->liveCount;
            int Bn0 = B->liveCount;
            if (An1 == An0  &&  Bn1 == Bn0) continue;
            if (An1 == 0  ||  Bn1 == 0) continue;

            // Prepare nearest neighbor search structures on B
            float radius = p->getRadius ();
            int   k      = p->getK ();
            KDTreeEntry * entries = 0;
            vector<MatrixAbstract<float> *> entryPointers;
            KDTree NN;
            bool doNN = k  ||  radius;
            if (doNN)
            {
                entries = new KDTreeEntry[Bn1];
                entryPointers.resize (Bn1);
                for (int i = 0; i < Bn1; i++)
                {
                    Part * b = B->parts[i];
                    KDTreeEntry & e = entries[i];
                    e = b->getXYZ (0);  // b has already been initialized, so pass $init=0
                    e.part = b;
                    entryPointers[i] = &e;
                }
                NN.set (entryPointers);
                NN.k = k ? k : INT_MAX;
            }

            int Amax = p->getMax (Aref);
            int Amin = p->getMin (Aref);
            int Bmax = p->getMax (Bref);
            int Bmin = p->getMin (Bref);
            bool doAccounting = Amin  ||  Amax  ||  Bmin  ||  Bmax;

            // Count existing connections.
            // This is a memory vs time trade-off. It takes more time to count
            // connections whenever needed, but it saves memory, and memory is the
            // more limited resource.
            int Alast = A->parts.size ();
            int Blast = B->parts.size ();
            int * Acount = 0;
            int * Bcount = 0;
            if (doAccounting)
            {
                Acount = new int[Alast]();  // this is subtle, but the () at the end causes each element to be initialized to 0
                Bcount = new int[Blast]();
                for (int i = 0; i < An0; i++)
                {
                    Connection * c = (Connection *) p->parts[i];
                    Acount[((Compartment *) c->getPart (Aref))->__24index]++;  // TODO: Assuming the part is a Compartment prevents us from connecting Connections. Could make $index transitory.
                    Bcount[((Compartment *) c->getPart (Bref))->__24index]++;
                }
            }

            Connection * c = (Connection *) p->create ();

            // Scan AxB
            bool minSatisfied = false;
            while (! minSatisfied)
            {
                minSatisfied = true;
                vector<Part *> subset;  // of B
                if (! doNN) subset = B->parts;
                int count = subset.size ();

                // New A with all of B
                for (int i = An0; i < An1; i++)
                {
                    Compartment * a = (Compartment *) A->parts[i];
                    if (Amax  &&  Acount[a->__24index] >= Amax) continue;  // early out: this part is already full, so skip. (No need to check doAccounting, because Amax != 0 is a subcase.)

                    c->setPart (Aref, a);
                    c->setPart (Bref, B->parts[0]);  // give a dummy B object, in case xyz call breaks rules about only accessing A

                    // Project A into B
                    Vector3 xyz = c->getXYZ (1);  // as a probe part, c is still in init, so pass $init=1

                    // Select the subset of B
                    if (doNN)
                    {
                        vector<MatrixAbstract<float> *> result;
                        NN.find (xyz, result);
                        count = result.size ();
                        subset.resize (count);
                        for (i = 0; i < count; i++) subset[i] = ((KDTreeEntry *) result[i])->part;
                    }

                    // Iterate over the subset in a random order
                    for (int j = 0; j < count; j++)
                    {
                        int k = rand () % (count - j) + j;
                        Part * & bj = subset[j];
                        Part * & bk = subset[k];
                        swap (bj, bk);
                        Compartment * b = (Compartment *) bj;
                        if (Bmax  &&  Bcount[b->__24index] >= Bmax) continue;  // no room in this B.  (No need to check doAccounting, because Bmax != 0 is a subcase.)

                        c->setPart (Bref, b);
                        if (c->getP (1) <= rand () / (RAND_MAX + 1.0f)) continue;
                        c->init (*this);
                        p->parts.push_back (c);
                        p->nextEntry = p->parts.size ();
                        c = (Connection *) p->create ();
                        c->setPart (Aref, a);

                        if (doAccounting)
                        {
                            Acount[a->__24index]++;
                            Bcount[b->__24index]++;
                            if (Amax  &&  Acount[a->__24index] >= Amax) break;  // stop scanning Bs once this A is full
                        }
                    }
                    if (doAccounting  &&  Acount[a->__24index] < Amin) minSatisfied = false;
                }

                // Old A with new B
                for (int i = 0; i < An0; i++)
                {
                    Compartment * a = (Compartment *) A->parts[i];
                    if (Amax  &&  Acount[a->__24index] >= Amax) continue;

                    c->setPart (Aref, a);
                    c->setPart (Bref, B->parts[0]);

                    // Project A into B
                    Vector3 xyz = c->getXYZ (1);

                    // Select the subset of B
                    if (k  ||  radius)
                    {
                        vector<MatrixAbstract<float> *> result;
                        NN.find (xyz, result);
                        count = result.size ();
                        subset.resize (count);
                        for (i = 0; i < count; i++) subset[i] = ((KDTreeEntry *) result[i])->part;
                    }

                    // Iterate over the subset in a random order
                    for (int j = 0; j < count; j++)
                    {
                        int k = rand () % (count - j) + j;
                        Part * & bj = subset[j];
                        Part * & bk = subset[k];
                        swap (bj, bk);
                        Compartment * b = (Compartment *) bj;
                        if (Bmax  &&  Bcount[b->__24index] >= Bmax) continue;  // no room in this B.  (No need to check doAccounting, because Bmax != 0 is a subcase.)

                        c->setPart (Bref, b);
                        if (c->getP (1) <= rand () / (RAND_MAX + 1.0f)) continue;
                        c->init (*this);
                        p->parts.push_back (c);
                        p->nextEntry = p->parts.size ();
                        c = (Connection *) p->create ();
                        c->setPart (Aref, a);

                        if (doAccounting)
                        {
                            Acount[a->__24index]++;
                            Bcount[b->__24index]++;
                            if (Amax  &&  Acount[a->__24index] >= Amax) break;  // stop scanning Bs once this A is full
                        }
                    }
                    if (doAccounting  &&  Acount[a->__24index] < Amin) minSatisfied = false;
                }

                if (doAccounting)
                {
                    if (! minSatisfied) continue;
                    for (int i = 0; i < Bn1; i++)
                    {
                        if (Bcount[((Compartment *) B->parts[i])->__24index] < Bmin)
                        {
                            minSatisfied = false;
                            break;
                        }
                    }
                }
            }
            delete c;
            delete [] entries;
            delete [] Acount;
            delete [] Bcount;
        }

        for (int i = 0; i < populations.size (); i++)
        {
            Population * p = populations[i];
            p->liveCount = p->nextEntry;
        }

        // Update parts
        t += dt;
        integrate ();
        for (it = populations.begin (); it != populations.end (); it++)
        {
            Population * p = *it;
            p->prepare ();
            Part ** j   = & p->parts[0];
            Part ** end = j + p->nextEntry;
            while (j < end) (*j++)->prepare ();
        }
        for (it = populations.begin (); it != populations.end (); it++)
        {
            Population * p = *it;
            p->update (*this);
            Part ** j   = & p->parts[0];
            Part ** end = j + p->nextEntry;
            while (j < end) (*j++)->update (*this);
        }
        for (it = populations.begin (); it != populations.end (); it++)
        {
            Population * p = *it;
            p->finalize (*this);
            Part ** j   = & p->parts[0];
            Part ** end = j + p->nextEntry;
            while (j < end)
            {
                if ((*j)->finalize (*this)) j++;
                else p->kill (*j);
            }
        }
    }
}

void
Simulator::integrate ()
{
}


// class Euler ---------------------------------------------------------------

Euler::~Euler ()
{
}

void
Euler::integrate ()
{
    vector<Population *>::iterator it;
    for (it = populations.begin (); it != populations.end (); it++)
    {
        Population * p = *it;
        p->integrate (*this);
        Part ** j   = & p->parts[0];
        Part ** end = j + p->nextEntry;
        while (j < end) (*j++)->integrate (*this);
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

    vector<Population *>::iterator it;

    // k1
    for (it = populations.begin (); it != populations.end (); it++)
    {
        Population * p = *it;
        p->pushIntegrated ();
        p->pushDerivative ();
        Part ** j   = & p->parts[0];
        Part ** end = j + p->nextEntry;
        while (j < end)
        {
            (*j)->pushIntegrated ();
            (*j)->pushDerivative ();
            j++;
        }
    }

    // k2 and k3
    this->dt /= 2.0f;
    this->t  -= this->dt;  // t is the current point in time, so we must look backward half a timestep
    for (int i = 0; i < 2; i++)
    {
        for (it = populations.begin (); it != populations.end (); it++)
        {
            Population * p = *it;
            p->integrate (*this);
            p->prepareDerivative ();
            Part ** j   = & p->parts[0];
            Part ** end = j + p->nextEntry;
            while (j < end)
            {
                (*j)->integrate (*this);
                (*j)->prepareDerivative ();
                j++;
            }
        }

        for (it = populations.begin (); it != populations.end (); it++)
        {
            Population * p = *it;
            p->updateDerivative (*this);
            Part ** j   = & p->parts[0];
            Part ** end = j + p->nextEntry;
            while (j < end) (*j++)->updateDerivative (*this);
        }

        for (it = populations.begin (); it != populations.end (); it++)
        {
            Population * p = *it;
            p->finalizeDerivative ();
            p->multiplyAddToStack (2.0f);
            Part ** j   = & p->parts[0];
            Part ** end = j + p->nextEntry;
            while (j < end)
            {
                (*j)->finalizeDerivative ();
                (*j)->multiplyAddToStack (2.0f);
                j++;
            }
        }
    }

    // k4
    this->dt = dt;  // restore original values
    this->t  = t;
    {  // curly brace is here just to make organization clear
        for (it = populations.begin (); it != populations.end (); it++)
        {
            Population * p = *it;
            p->integrate (*this);
            p->prepareDerivative ();
            Part ** j   = & p->parts[0];
            Part ** end = j + p->nextEntry;
            while (j < end)
            {
                (*j)->integrate (*this);
                (*j)->prepareDerivative ();
                j++;
            }
        }

        for (it = populations.begin (); it != populations.end (); it++)
        {
            Population * p = *it;
            p->updateDerivative (*this);
            Part ** j   = & p->parts[0];
            Part ** end = j + p->nextEntry;
            while (j < end) (*j++)->updateDerivative (*this);
        }

        for (it = populations.begin (); it != populations.end (); it++)
        {
            Population * p = *it;
            p->finalizeDerivative ();
            p->addToMembers ();  // clears stackDerivative
            Part ** j   = & p->parts[0];
            Part ** end = j + p->nextEntry;
            while (j < end)
            {
                (*j)->finalizeDerivative ();
                (*j)->addToMembers ();  // ditto
                j++;
            }
        }
    }

    for (it = populations.begin (); it != populations.end (); it++)
    {
        Population * p = *it;
        p->multiply (1.0 / 6.0);
        Part ** j   = & p->parts[0];
        Part ** end = j + p->nextEntry;
        while (j < end) (*j++)->multiply (1.0 / 6.0);
    }

    for (it = populations.begin (); it != populations.end (); it++)
    {
        Population * p = *it;
        p->integrate (*this);
        Part ** j   = & p->parts[0];
        Part ** end = j + p->nextEntry;
        while (j < end) (*j++)->integrate (*this);
    }

    for (it = populations.begin (); it != populations.end (); it++)
    {
        Population * p = *it;
        p->popIntegrated ();  // clears stackIntgrated
        Part ** j   = & p->parts[0];
        Part ** end = j + p->nextEntry;
        while (j < end) (*j++)->popIntegrated ();  // ditto
    }
}
