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


// class _Part ---------------------------------------------------------------

_Part::~_Part ()
{
}

void
_Part::getPopulations (vector<_Population *> & resutl)
{
}

void
_Part::init (float _24t, float & _24dt)
{
}

void
_Part::prepare ()
{
}

void
_Part::integrate (float _24t, float _24dt)
{
}

void
_Part::update (float _24t, float & _24dt)
{
}

void
_Part::finalize (float _24t, float & _24dt)
{
}

void
_Part::prepareDerivative ()
{
}

void
_Part::updateDerivative (float _24t, float _24dt)
{
}

void
_Part::finalizeDerivative ()
{
}

void
_Part::pushIntegrated ()
{
}

void
_Part::popIntegrated ()
{
}

void
_Part::pushDerivative ()
{
}

void
_Part::multiplyAddToStack (float scalar)
{
}

void
_Part::multiply (float scalar)
{
}

void
_Part::addToMembers ()
{
}

float
_Part::getP (const Vector3 & _24xyz)
{
    return 1;
}

void
_Part::getXYZ (Vector3 & _24xyz)
{
    _24xyz[0] = 0;
    _24xyz[1] = 0;
    _24xyz[2] = 0;
}

void
_Part::getNamedValue (const string & name, string & value)
{
}


// class _Population ---------------------------------------------------------

_Population::_Population ()
{
    liveCount = 0;
    nextEntry = 0;
}

_Population::~_Population ()
{
}

_Part *
_Population::allocate (float t, float & dt)
{
    _Part * result;
    if (nextEntry < _parts.size ())
    {
        result = _parts[nextEntry];
    }
    else
    {
        result = create ();
        _parts.push_back (result);
        if (_Compartment * compartment = dynamic_cast<_Compartment *> (result))
        {
        	compartment->_24index = nextEntry;
        }
    }
    nextEntry++;
    result->init (t, dt);
    return result;
}

void
_Population::kill (_Part * p)
{
    vector<_Part *>::iterator it;
    for (it = _parts.begin (); it != _parts.end (); it++)
    {
        if (*it == p)
        {
            _parts.erase (it);
            break;
        }
    }
    delete p;
}

int
_Population::getK ()
{
    return 0;
}

int
_Population::getMax (int i)
{
    return 0;
}

int
_Population::getMin (int i)
{
    return 0;
}

float
_Population::getN ()
{
    return 1;
}

float
_Population::getRadius ()
{
    return 0;
}

int
_Population::getRef ()
{
    return -1;  // indicates this population is not a connection type
}


// class _Compartment --------------------------------------------------------


// class _Connection ---------------------------------------------------------

void
_Connection::setPart (int i, _Part * p)
{
}

_Part *
_Connection::getPart (int i)
{
    return 0;
}


// class _Simulator ----------------------------------------------------------

_Simulator::_Simulator ()
{
    dt = 1e-4;
}

_Simulator::~_Simulator ()
{
}

class KDTreeEntry : public Vector3
{
public:
    _Part * part;
};

void
_Simulator::run ()
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
            _Population * p = populations[i];

            // Determine if population size has changed since last cycle
            if (p->getRef () >= 0) continue;  // this is a connection population, so nothing more to do
            int n1 = floor (p->getN ());
            // Note: we do not shrink populations using $n, only grow them.
            while (p->nextEntry < n1)
            {
                float tempDt = 0;
                _Part * q = p->allocate (t, tempDt);
                if (tempDt) dt = tempDt;  // TODO: this changes $dt for all parts; need to create even-queue and $dt-based groups of parts

                // Initialize and add any newly created populations
                int oldCount = populations.size ();
                q->getPopulations (populations);  // append any populations contained in the new part
                int newCount = populations.size ();
                for (int j = oldCount; j < newCount; j++)
                {
                    tempDt = 0;
                    populations[j]->init (t, tempDt);
                    if (tempDt) dt = tempDt;
                }
            }
        }

        // Create new connections
        vector<_Population *>::iterator it;
        for (it = populations.begin (); it != populations.end (); it++)
        {
            _Population * p = *it;

            int Aref = p->getRef ();
            if (Aref < 0) continue;  // this is a compartment population, so no connections to make
            int Bref = 1 - Aref;  // presuming only two endpoints

            vector<_Population *> references;
            p->getPopulations (references);
            assert (references.size () == 2);
            _Population * A = references[Aref];
            _Population * B = references[Bref];

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
                    _Part * b = B->_parts[i];
                    KDTreeEntry & e = entries[i];
                    b->getXYZ (e);
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
            int Alast = A->_parts.size ();
            int Blast = B->_parts.size ();
            int * Acount = 0;
            int * Bcount = 0;
            if (doAccounting)
            {
                Acount = new int[Alast]();  // this is subtle, but the () at the end causes each element to be initialized to 0
                Bcount = new int[Blast]();
                for (int i = 0; i < An0; i++)
                {
                    _Connection * c = (_Connection *) p->_parts[i];
                    Acount[((_Compartment *) c->getPart (Aref))->_24index]++;  // TODO: Assuming the part is a Compartment prevents us from connecting Connections. Could make $index transitory.
                    Bcount[((_Compartment *) c->getPart (Bref))->_24index]++;
                }
            }

            _Connection * c = (_Connection *) p->create ();

            // Scan AxB
            bool minSatisfied = false;
            while (! minSatisfied)
            {
                minSatisfied = true;
                vector<_Part *> subset;  // of B
                if (! doNN) subset = B->_parts;
                int count = subset.size ();

                // New A with all of B
                for (int i = An0; i < An1; i++)
                {
                    _Compartment * a = (_Compartment *) A->_parts[i];
                    if (Amax  &&  Acount[a->_24index] >= Amax) continue;  // early out: this part is already full, so skip. (No need to check doAccounting, because Amax != 0 is a subcase.)

                    c->setPart (Aref, a);
                    c->setPart (Bref, B->_parts[0]);  // give a dummy B object, in case xyz call breaks rules about only accessing A

                    // Project A into B
                    Vector3 xyz;
                    c->getXYZ (xyz);

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
                        _Part * & bj = subset[j];
                        _Part * & bk = subset[k];
                        swap (bj, bk);
                        _Compartment * b = (_Compartment *) bj;
                        if (Bmax  &&  Bcount[b->_24index] >= Bmax) continue;  // no room in this B.  (No need to check doAccounting, because Bmax != 0 is a subcase.)

                        c->setPart (Bref, b);
                        if (c->getP (xyz) <= rand () / (RAND_MAX + 1.0f)) continue;
                        float tempDt = 0;
                        c->init (t, tempDt);
                        if (tempDt) dt = tempDt;
                        p->_parts.push_back (c);
                        p->nextEntry = p->_parts.size ();
                        c = (_Connection *) p->create ();
                        c->setPart (Aref, a);

                        if (doAccounting)
                        {
                            Acount[a->_24index]++;
                            Bcount[b->_24index]++;
                            if (Amax  &&  Acount[a->_24index] >= Amax) break;  // stop scanning Bs once this A is full
                        }
                    }
                    if (doAccounting  &&  Acount[a->_24index] < Amin) minSatisfied = false;
                }

                // Old A with new B
                for (int i = 0; i < An0; i++)
                {
                    _Compartment * a = (_Compartment *) A->_parts[i];
                    if (Amax  &&  Acount[a->_24index] >= Amax) continue;

                    c->setPart (Aref, a);
                    c->setPart (Bref, B->_parts[0]);

                    // Project A into B
                    Vector3 xyz;
                    c->getXYZ (xyz);

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
                        _Part * & bj = subset[j];
                        _Part * & bk = subset[k];
                        swap (bj, bk);
                        _Compartment * b = (_Compartment *) bj;
                        if (Bmax  &&  Bcount[b->_24index] >= Bmax) continue;  // no room in this B.  (No need to check doAccounting, because Bmax != 0 is a subcase.)

                        c->setPart (Bref, b);
                        if (c->getP (xyz) <= rand () / (RAND_MAX + 1.0f)) continue;
                        float tempDt = 0;
                        c->init (t, tempDt);
                        if (tempDt) dt = tempDt;
                        p->_parts.push_back (c);
                        p->nextEntry = p->_parts.size ();
                        c = (_Connection *) p->create ();
                        c->setPart (Aref, a);

                        if (doAccounting)
                        {
                            Acount[a->_24index]++;
                            Bcount[b->_24index]++;
                            if (Amax  &&  Acount[a->_24index] >= Amax) break;  // stop scanning Bs once this A is full
                        }
                    }
                    if (doAccounting  &&  Acount[a->_24index] < Amin) minSatisfied = false;
                }

                if (doAccounting)
                {
                    if (! minSatisfied) continue;
                    for (int i = 0; i < Bn1; i++)
                    {
                        if (Bcount[((_Compartment *) B->_parts[i])->_24index] < Bmin)
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
            _Population * p = populations[i];
            p->liveCount = p->nextEntry;
        }

        // Update parts
        t += dt;
        integrate (populations, t, dt);
        for (it = populations.begin (); it != populations.end (); it++)
        {
            _Population * p = *it;
            p->prepare ();
            _Part ** j   = & p->_parts[0];
            _Part ** end = j + p->nextEntry;
            while (j < end) (*j++)->prepare ();
        }
        for (it = populations.begin (); it != populations.end (); it++)
        {
            _Population * p = *it;
            p->update (t, dt);
            _Part ** j   = & p->_parts[0];
            _Part ** end = j + p->nextEntry;
            while (j < end) (*j++)->update (t, dt);
        }
        for (it = populations.begin (); it != populations.end (); it++)
        {
            _Population * p = *it;
            p->finalize (t, dt);
            _Part ** j   = & p->_parts[0];
            _Part ** end = j + p->nextEntry;
            while (j < end) (*j++)->finalize (t, dt);
            // TODO: check if a part has died. If so, exchange with lastEntry and stall j for one iteration.
        }
    }
}

void
_Simulator::integrate (std::vector<_Population *> & populations, float t, float dt)
{
}


// class _Euler --------------------------------------------------------------

_Euler::~_Euler ()
{
}

void
_Euler::integrate (std::vector<_Population *> & populations, float t, float dt)
{
    vector<_Population *>::iterator it;
    for (it = populations.begin (); it != populations.end (); it++)
    {
        _Population * p = *it;
        p->integrate (t, dt);
        _Part ** j   = & p->_parts[0];
        _Part ** end = j + p->nextEntry;
        while (j < end) (*j++)->integrate (t, dt);
    }
}


// class _RungeKutta ---------------------------------------------------------

_RungeKutta::~_RungeKutta ()
{
}

void
_RungeKutta::integrate (std::vector<_Population *> & populations, float t, float dt)
{
    float dt2 = dt / 2.0f;
    float t2 = t - dt2;  // t is the current point in time, so we must look backward half a timestep

    vector<_Population *>::iterator it;

    // k1
    for (it = populations.begin (); it != populations.end (); it++)
    {
        _Population * p = *it;
        p->pushIntegrated ();
        p->pushDerivative ();
        _Part ** j   = & p->_parts[0];
        _Part ** end = j + p->nextEntry;
        while (j < end)
        {
            (*j)->pushIntegrated ();
            (*j)->pushDerivative ();
            j++;
        }
    }

    // k2 and k3
    for (int i = 0; i < 2; i++)
    {
        for (it = populations.begin (); it != populations.end (); it++)
        {
            _Population * p = *it;
            p->integrate (t2, dt2);
            p->prepareDerivative ();
            _Part ** j   = & p->_parts[0];
            _Part ** end = j + p->nextEntry;
            while (j < end)
            {
                (*j)->integrate (t2, dt2);
                (*j)->prepareDerivative ();
                j++;
            }
        }

        for (it = populations.begin (); it != populations.end (); it++)
        {
            _Population * p = *it;
            p->updateDerivative (t2, dt2);
            _Part ** j   = & p->_parts[0];
            _Part ** end = j + p->nextEntry;
            while (j < end) (*j++)->updateDerivative (t2, dt2);
        }

        for (it = populations.begin (); it != populations.end (); it++)
        {
            _Population * p = *it;
            p->finalizeDerivative ();
            p->multiplyAddToStack (2.0f);
            _Part ** j   = & p->_parts[0];
            _Part ** end = j + p->nextEntry;
            while (j < end)
            {
                (*j)->finalizeDerivative ();
                (*j)->multiplyAddToStack (2.0f);
                j++;
            }
        }
    }

    // k4
    {  // curly brace is here just to make organization clear
        for (it = populations.begin (); it != populations.end (); it++)
        {
            _Population * p = *it;
            p->integrate (t, dt);
            p->prepareDerivative ();
            _Part ** j   = & p->_parts[0];
            _Part ** end = j + p->nextEntry;
            while (j < end)
            {
                (*j)->integrate (t, dt);
                (*j)->prepareDerivative ();
                j++;
            }
        }

        for (it = populations.begin (); it != populations.end (); it++)
        {
            _Population * p = *it;
            p->updateDerivative (t, dt);
            _Part ** j   = & p->_parts[0];
            _Part ** end = j + p->nextEntry;
            while (j < end) (*j++)->updateDerivative (t, dt);
        }

        for (it = populations.begin (); it != populations.end (); it++)
        {
            _Population * p = *it;
            p->finalizeDerivative ();
            p->addToMembers ();  // clears _stackDerivative
            _Part ** j   = & p->_parts[0];
            _Part ** end = j + p->nextEntry;
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
        _Population * p = *it;
        p->multiply (1.0 / 6.0);
        _Part ** j   = & p->_parts[0];
        _Part ** end = j + p->nextEntry;
        while (j < end) (*j++)->multiply (1.0 / 6.0);
    }

    for (it = populations.begin (); it != populations.end (); it++)
    {
        _Population * p = *it;
        p->integrate (t, dt);
        _Part ** j   = & p->_parts[0];
        _Part ** end = j + p->nextEntry;
        while (j < end) (*j++)->integrate (t, dt);
    }

    for (it = populations.begin (); it != populations.end (); it++)
    {
        _Population * p = *it;
        p->popIntegrated ();  // clears _stackIntgrated
        _Part ** j   = & p->_parts[0];
        _Part ** end = j + p->nextEntry;
        while (j < end) (*j++)->popIntegrated ();  // ditto
    }
}
