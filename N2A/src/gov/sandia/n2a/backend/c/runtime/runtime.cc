#include "runtime.h"

#include "fl/Matrix.tcc"
#include "fl/MatrixFixed.tcc"
#include "fl/Vector.tcc"

using namespace fl;
using namespace std;

template class MatrixAbstract<float>;
template class Matrix<float>;
template class MatrixFixed<float,3,1>;


// General functions ---------------------------------------------------------

float
uniform ()
{
    return (float) rand () / RAND_MAX;
}

float
uniform (float sigma)
{
    return sigma * rand () / RAND_MAX;
}

MatrixResult<float>
uniform (const MatrixAbstract<float> & sigma)
{
    int rows = sigma.rows ();
    int cols = sigma.columns ();
    if (cols == 1)
    {
        Vector<float> * result = new Vector<float> (rows);
        for (int i = 0; i < rows; i++) (*result)[i] = uniform (sigma(i,0));
        return result;
    }
    else if (rows == 1)
    {
        Vector<float> * result = new Vector<float> (cols);
        for (int i = 0; i < cols; i++) (*result)[i] = uniform (sigma(0,i));
        return result;
    }
    else
    {
        Vector<float> temp (cols);
        for (int i = 0; i < cols; i++) temp[i] = uniform ();
        return sigma * temp;
    }
}

// Box-Muller method (polar variant) for Gaussian random numbers.
static bool haveNextGaussian = false;
static float nextGaussian;
float
gaussian ()
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
            v1 = uniform () * 2 - 1;   // between -1.0 and 1.0
            v2 = uniform () * 2 - 1;
            s = v1 * v1 + v2 * v2;
        }
        while (s >= 1 || s == 0);
        float multiplier = sqrt (- 2 * log (s) / s);
        nextGaussian = v2 * multiplier;
        haveNextGaussian = true;
        return v1 * multiplier;
    }
}

float
gaussian (float sigma)
{
    return sigma * gaussian ();
}

MatrixResult<float>
gaussian (const MatrixAbstract<float> & sigma)
{
    int rows = sigma.rows ();
    int cols = sigma.columns ();
    if (cols == 1)
    {
        Vector<float> * result = new Vector<float> (rows);
        for (int i = 0; i < rows; i++) (*result)[i] = gaussian (sigma(i,0));
        return result;
    }
    else if (rows == 1)
    {
        Vector<float> * result = new Vector<float> (cols);
        for (int i = 0; i < cols; i++) (*result)[i] = gaussian (sigma(0,i));
        return result;
    }
    else
    {
        Vector<float> temp (cols);
        for (int i = 0; i < cols; i++) temp[i] = gaussian ();
        return sigma * temp;
    }
}

MatrixResult<float>
grid (int i, int nx, int ny, int nz)
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

MatrixResult<float>
gridRaw (int i, int nx, int ny, int nz)
{
    int sx = ny * nz;  // stride x

    // compute xyz in stride order
    Vector3 * result = new Vector3;
    (*result)[0] = i / sx;
    i %= sx;
    (*result)[1] = i / nz;
    (*result)[2] = i % nz;
    return result;
}


// I/O -----------------------------------------------------------------------

Holder *
holderHelper (vector<Holder *> & holders, const String & fileName, Holder * oldHandle)
{
    vector<Holder *>::iterator it;
    if (oldHandle)
    {
        if (oldHandle->fileName == fileName) return oldHandle;
        for (it = holders.begin (); it != holders.end (); it++)
        {
            if (*it == oldHandle)
            {
                holders.erase (it);
                delete *it;
                break;
            }
        }
    }
    for (it = holders.begin (); it != holders.end (); it++)
    {
        if ((*it)->fileName == fileName) return *it;
    }
    return 0;
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
Simulatable::init ()
{
}

void
Simulatable::integrate ()
{
}

void
Simulatable::update ()
{
}

bool
Simulatable::finalize ()
{
    return true;
}

void
Simulatable::updateDerivative ()
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
Simulatable::path (String & result)
{
    result = "";
}

void
Simulatable::getNamedValue (const String & name, String & value)
{
}


// class Part ----------------------------------------------------------------

void
Part::setPrevious (Part * previous)
{
}

void
Part::setVisitor (VisitorStep * visitor)
{
}

EventStep *
Part::getEvent ()
{
    return (EventStep *) simulator.currentEvent;
}

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

void
Part::setPart (int i, Part * part)
{
}

Part *
Part::getPart (int i)
{
    return 0;
}

int
Part::getCount (int i)
{
    return 0;
}

void
Part::getProject (int i, Vector3 & xyz)
{
    getPart (i)->getXYZ (xyz);
}

bool
Part::getNewborn ()
{
    return false;
}

float
Part::getLive ()
{
    return 1;
}

float
Part::getP ()
{
    return 1;
}

void
Part::getXYZ (Vector3 & xyz)
{
    xyz[0] = 0;
    xyz[1] = 0;
    xyz[2] = 0;
}

bool
Part::eventTest (int i)
{
    return false;
}

float
Part::eventDelay (int i)
{
    return -1;  // no care
}

void
Part::setLatch (int i)
{
}

void
Part::finalizeEvent ()
{
}

void
removeMonitor (vector<Part *> & partList, Part * part)
{
    vector<Part *>::iterator it;
    for (it = partList.begin (); it != partList.end (); it++)
    {
        if (*it == part)
        {
            *it = 0;
            break;
        }
    }
}


// class PartTime ------------------------------------------------------------

void
PartTime::setPrevious (Part * previous)
{
    this->previous = previous;
}

void
PartTime::setVisitor (VisitorStep * visitor)
{
    this->visitor = visitor;
}

EventStep *
PartTime::getEvent ()
{
    return (EventStep *) visitor->event;
}

void
PartTime::dequeue ()
{
    // TODO: Need mutex on visitor when modifying its queue, even if it is not part of currentEvent.
    if (simulator.currentEvent == visitor->event)
    {
        // Avoid damaging iterator in visitor
        if (visitor->previous == this) visitor->previous = next;
    }
    if (next) next->setPrevious (previous);
    previous->next = next;
}

void
PartTime::setPeriod (float dt)
{
    dequeue ();
    simulator.enqueue (this, dt);
}


// Wrapper -------------------------------------------------------------------

void
WrapperBase::init ()
{
    population->init ();
}

void
WrapperBase::integrate ()
{
    population->integrate ();
}

void
WrapperBase::update ()
{
    population->update ();
}

bool
WrapperBase::finalize ()
{
    return population->finalize ();  // We depend on explicit code in the top-level finalize() to signal when $n goes to zero.
}

void
WrapperBase::updateDerivative ()
{
    population->updateDerivative ();
}

void
WrapperBase::finalizeDerivative ()
{
    population->finalizeDerivative ();
}

void
WrapperBase::snapshot ()
{
    population->snapshot ();
}

void
WrapperBase::restore ()
{
    population->restore ();
}

void
WrapperBase::pushDerivative ()
{
    population->pushDerivative ();
}

void
WrapperBase::multiplyAddToStack (float scalar)
{
    population->multiplyAddToStack (scalar);
}

void
WrapperBase::multiply (float scalar)
{
    population->multiply (scalar);
}

void
WrapperBase::addToMembers ()
{
    population->addToMembers ();
}


// class ConnectIterator -----------------------------------------------------

ConnectIterator::ConnectIterator (int index)
:   index (index)
{
    permute         = 0;
    contained       = false;
    instances       = 0;
    deleteInstances = false;
    firstborn       = INT_MAX;
    c               = 0;
    p               = 0;

    i    = 0;  // These two values force a reset
    stop = 0;

    Max    = 0;
    Min    = 0;
    k      = 0;
    radius = 0;

    rank        = 0;
    explicitXYZ = false;
    NN          = 0;
    entries     = 0;
}

ConnectIterator::~ConnectIterator ()
{
    if (permute) delete permute;
    else if (xyz) delete xyz;  // The innermost iterator is responsible for destructing xyz.
    if (NN) delete NN;
    if (entries) delete[] entries;
    if (instances  &&  deleteInstances) delete instances;
}

void
ConnectIterator::prepareNN ()
{
    NN = new KDTree ();
    if (k > 0) NN->k = k;
    else       NN->k = INT_MAX;
    if (radius > 0) NN->radius = radius;
    //else NN->radius is INFINITY

    entries = new KDTreeEntry[size];
    vector<KDTreeEntry *> pointers;
    pointers.reserve (size);
    for (int i = 0; i < size; i++)
    {
        p = (*instances)[i];
        if (! p) continue;

        KDTreeEntry & e = entries[i];
        e.part = p;
        // c should already be assigned by caller, but not via setProbe()
        c->setPart (index, p);
        c->getProject (index, e);
        pointers.push_back (&e);
    }
    NN->set (pointers);
}

bool
ConnectIterator::setProbe (Part * probe)
{
    c = probe;
    bool result = false;
    if (p)
    {
        c->setPart (index, p);
        // A new connection was just made, so counts (if they are used) have been updated.
        // Step to next endpoint instance if current instance is full.
        if (Max > 0  &&  c->getCount (index) >= Max) result = true;
    }
    if (permute  &&  permute->setProbe (c))
    {
        i = stop;  // next() will trigger a reset
        result = true;
    }
    return result;
}

void
ConnectIterator::reset (bool newOnly)
{
    this->newOnly = newOnly;
    if (NN)
    {
        vector<KDTreeEntry *> result;
        NN->find (*xyz, result);
        count = result.size ();
        filtered.resize (count);
        int j = 0;
        for (KDTreeEntry * e : result) filtered[j++] = e->part;
        i = 0;
    }
    else
    {
        if (newOnly) count = size - firstborn;
        else         count = size;
        if (count > 1) i = (int) round (uniform () * (count - 1));
        else           i = 0;
    }
    stop = i + count;
}

bool
ConnectIterator::old ()
{
    if (p->getNewborn ()) return false;
    if (permute) return permute->old ();
    return true;
}

bool
ConnectIterator::next ()
{
    while (true)
    {
        if (i >= stop)  // Need to either reset or terminate, depending on whether we have something left to permute with.
        {
            if (! permute)
            {
                if (stop > 0) return false;  // We already reset once, so done.
                // A unary connection should only iterate over new instances.
                // The innermost (slowest) iterator of a multi-way connection should iterate over all instances.
                reset (! contained);
            }
            else
            {
                if (! permute->next ()) return false;
                if (contained) reset (false);
                else           reset (permute->old ());
            }
        }

        if (NN)
        {
            for (; i < stop; i++)
            {
                p = filtered[i];
                if (p->getNewborn ())
                {
                    if (Max == 0) break;
                    c->setPart (index, p);
                    if (c->getCount (index) < Max) break;
                }
            }
        }
        else if (newOnly)
        {
            for (; i < stop; i++)
            {
                p = (*instances)[i % count + firstborn];
                if (p  &&  p->getNewborn ())
                {
                    if (Max == 0) break;
                    c->setPart (index, p);
                    if (c->getCount (index) < Max) break;
                }
            }
        }
        else
        {
            for (; i < stop; i++)
            {
                p = (*instances)[i % count];
                if (p)
                {
                    if (Max == 0) break;
                    c->setPart (index, p);
                    if (c->getCount (index) < Max) break;
                }
            }
        }

        i++;
        if (p  &&  i <= stop)
        {
            if (Max == 0) c->setPart (index, p);
            if (xyz  &&  ! permute)  // Spatial filtering is on, and we are the endpoint that determines the query $xyz
            {
                // Obtain C.$xyz, the best way we can
                if (explicitXYZ) c->getXYZ (*xyz);
                else             c->getProject (index, *xyz);
            }
            return true;
        }
    }
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
            *p = (*p)->next;  // remove from dead
            break;
        }
        p = & (*p)->next;
    }

    if (! result) result = create ();
    add (result);

    return result;
}

void
Population::resize (int n)
{
    EventStep * event = container->getEvent ();
    for (int currentN = getN (); currentN < n; currentN++)
    {
        Part * p = allocate ();
        p->enterSimulation ();
        event->enqueue (p);
        p->init ();
    }
}

int
Population::getN ()
{
    N2A_THROW ("getN not implemented");
}

void
Population::connect ()
{
    ConnectIterator * outer = getIterators ();
    if (! outer) return;

    EventStep * event = container->getEvent ();
    Part * c = create ();
    outer->setProbe (c);
    while (outer->next ())
    {
        float create = c->getP ();
        // Yes, we need all 3 conditions. If create is 0 or 1, we do not do a random draw, since it would have no effect.
        if (create <= 0) continue;
        if (create < 1  &&  create < uniform ()) continue;

        c->enterSimulation ();
        event->enqueue (c);
        c->init ();

        c = this->create ();
        outer->setProbe (c);
    }
    delete c;  // The last created connection instance doesn't get used.
    delete outer;  // Automatically deletes inner iterators as well.
}

void
Population::clearNew ()
{
}

ConnectIterator *
Population::getIterators ()
{
    vector<ConnectIterator *> iterators;
    iterators.reserve (3);  // This is the largest number of endpoints we will usually have in practice.
    bool nothingNew = true;
    bool spatialFiltering = false;
    int i = 0;
    while (true)
    {
        ConnectIterator * it = getIterator (i++);  // Returns null if i is out of range for endpoints.
        if (! it) break;
        iterators.push_back (it);
        it->size = it->instances->size ();
        if (it->firstborn < it->size) nothingNew = false;
        if (it->k > 0  ||  it->radius > 0) spatialFiltering = true;
    }
    if (nothingNew) return 0;

    // Sort so that population with the most old entries is the outermost iterator.
    // That allows the most number of old entries to be skipped.
    // This is a simple in-place insertion sort ...
    int count = iterators.size ();
    for (int i = 1; i < count; i++)
    {
        for (int j = i; j > 0; j--)
        {
            ConnectIterator * A = iterators[j-1];
            ConnectIterator * B = iterators[j  ];
            if (A->firstborn >= B->firstborn) break;
            iterators[j-1] = B;
            iterators[j  ] = A;
        }
    }

    if (spatialFiltering)
    {
        // Create shared $xyz value
        Vector3 * xyz = new Vector3;
        for (int i = 0; i < count; i++) iterators[i]->xyz = xyz;

        // Ensure the innermost iterator be the one that best defines C.$xyz
        // If connection defines its own $xyz, then this sorting operation has no effect.
        int last = count - 1;
        ConnectIterator * A = iterators[last];
        int    bestIndex = last;
        double bestRank  = A->rank;
        for (int i = 0; i < last; i++)
        {
            A = iterators[i];
            if (A->rank > bestRank)
            {
                bestIndex = i;
                bestRank  = A->rank;
            }
        }
        if (bestIndex != last)
        {
            A = iterators[bestIndex];
            iterators.erase (iterators.begin () + bestIndex);
            iterators.push_back (A);
        }
    }

    for (int i = 1; i < count; i++)
    {
        ConnectIterator * A = iterators[i-1];
        ConnectIterator * B = iterators[i  ];
        A->permute   = B;
        B->contained = true;
        if (A->k > 0  ||  A->radius > 0)  // Note that NN structure won't be created on deepest iterator. TODO: Is this correct?
        {
            A->c = create ();
            A->prepareNN ();
            delete A->c;
        }
    }

    return iterators[0];
}

ConnectIterator *
Population::getIterator (int i)
{
    return 0;
}


// class Simulator -----------------------------------------------------------

Simulator simulator;

Simulator::Simulator ()
{
    integrator = 0;
    stop = false;

    EventStep * event = new EventStep (0, 1e-4);
    currentEvent = event;
    periods.push_back (event);
}

Simulator::~Simulator ()
{
    for (auto event : periods) delete event;
    if (integrator) delete integrator;
}

void
Simulator::run (WrapperBase & wrapper)
{
    // Init cycle
    EventStep * event = (EventStep *) currentEvent;
    event->enqueue (&wrapper);  // no need for wrapper->enterSimulation()
    wrapper.init ();
    updatePopulations ();
    event->requeue ();  // Only reinserts self if not empty.

    // Regular simulation
    while (! queueEvent.empty ()  &&  ! stop)
    {
        currentEvent = queueEvent.top ();
        queueEvent.pop ();
        currentEvent->run ();
    }
}

void
Simulator::updatePopulations ()
{
    // Resize populations that have requested it
    for (auto it : queueResize) it.first->resize (it.second);
    queueResize.clear ();

    // Evaluate connection populations that have requested it
    while (! queueConnect.empty ())
    {
        queueConnect.front ()->connect ();
        queueConnect.pop ();
    }

    // Clear new flag from populations that have requested it
    for (auto it : queueClearNew) it->clearNew ();
    queueClearNew.clear ();
}

void
Simulator::enqueue (Part * part, float dt)
{
    // find a matching event
    int index = 0;
    int count = periods.size ();
    for (; index < count; index++)
    {
        if (periods[index]->dt >= dt) break;
    }

    EventStep * event;
    if (index < count  &&  periods[index]->dt == dt)
    {
        event = periods[index];
    }
    else
    {
        event = new EventStep (currentEvent->t + dt, dt);
        periods.insert (periods.begin () + index, event);
        queueEvent.push (event);
    }
    event->enqueue (part);
}

void
Simulator::removePeriod (EventStep * event)
{
    vector<EventStep *>::iterator it;
    for (it = periods.begin (); it != periods.end (); it++)
    {
        if (*it == event)
        {
            periods.erase (it);
            break;
        }
    }
    delete event;  // Events still in periods at end will get deleted by dtor.
}

void
Simulator::resize (Population * population, int n)
{
    queueResize.push_back (make_pair (population, n));
}

void
Simulator::connect (Population * population)
{
    queueConnect.push (population);
}

void
Simulator::clearNew (Population * population)
{
    queueClearNew.insert (population);
}


// class Euler ---------------------------------------------------------------

void
Euler::run (Event & event)
{
    event.visit ([](Visitor * visitor)
    {
        visitor->part->integrate ();
    });
}


// class RungeKutta ----------------------------------------------------------

void
RungeKutta::run (Event & event)
{
    // k1
    event.visit ([](Visitor * visitor)
    {
        visitor->part->snapshot ();
        visitor->part->pushDerivative ();
    });

    // k2 and k3
    EventStep & es = (EventStep &) event;
    float t  = es.t;  // Save current values of t and dt
    float dt = es.dt;
    es.dt /= 2.0f;
    es.t  -= es.dt;  // t is the current point in time, so we must look backward half a timestep
    for (int i = 0; i < 2; i++)
    {
        event.visit ([](Visitor * visitor)
        {
            visitor->part->integrate ();
        });
        event.visit ([](Visitor * visitor)
        {
            visitor->part->updateDerivative ();
        });
        event.visit ([](Visitor * visitor)
        {
            visitor->part->finalizeDerivative ();
            visitor->part->multiplyAddToStack (2.0f);
        });
    }
    es.dt = dt;  // restore original values
    es.t  = t;

    // k4
    event.visit ([](Visitor * visitor)
    {
        visitor->part->integrate ();
    });
    event.visit ([](Visitor * visitor)
    {
        visitor->part->updateDerivative ();
    });
    event.visit ([](Visitor * visitor)
    {
        visitor->part->finalizeDerivative ();
        visitor->part->addToMembers ();  // clears stackDerivative
    });

    // finish
    event.visit ([](Visitor * visitor)
    {
        visitor->part->multiply (1.0 / 6.0);
    });
    event.visit ([](Visitor * visitor)
    {
        visitor->part->integrate ();
    });
    event.visit ([](Visitor * visitor)
    {
        visitor->part->restore ();
    });
}


// class EventStep -----------------------------------------------------------

Event::~Event ()
{
}


// class EventStep -----------------------------------------------------------

EventStep::EventStep (float t, float dt)
:   dt (dt)
{
    this->t = t;
    visitors.push_back (new VisitorStep (this));
}

EventStep::~EventStep ()
{
    for (auto it : visitors) delete it;
}

void
EventStep::run ()
{
    // Update parts
    simulator.integrator->run (*this);
    visit ([](Visitor * visitor)
    {
        visitor->part->update ();
    });
    visit ([](Visitor * visitor)
    {
        if (! visitor->part->finalize ())
        {
            VisitorStep * v = (VisitorStep *) visitor;
            Part * p = visitor->part;  // for convenience
            if (p->next) p->next->setPrevious (v->previous);
            v->previous->next = p->next;
            p->leaveSimulation ();
        }
    });

    simulator.updatePopulations ();
    requeue ();
}

void
EventStep::visit (visitorFunction f)
{
    visitors[0]->visit (f);
}

void
EventStep::requeue ()
{
    if (visitors[0]->queue.next)  // still have instances, so re-queue event
    {
        t += dt;
        simulator.queueEvent.push (this);
    }
    else  // our list of instances is empty, so die
    {
        simulator.removePeriod (this);
    }
}

void
EventStep::enqueue (Part * part)
{
    visitors[0]->enqueue (part);
}


// class EventSpikeSingle ----------------------------------------------------

void
EventSpikeSingle::run ()
{
    target->setLatch (latch);

    simulator.integrator->run (*this);
    visit ([](Visitor * visitor)
    {
        visitor->part->update ();
        visitor->part->finalize ();
        visitor->part->finalizeEvent ();
    });

    delete this;
}

void
EventSpikeSingle::visit (visitorFunction f)
{
    Visitor v (this, target);
    f (&v);
}


// class EventSpikeSingleLatch -----------------------------------------------

void
EventSpikeSingleLatch::run ()
{
    target->setLatch (latch);
    delete this;
}


// class EventSpikeMulti -----------------------------------------------------

void
EventSpikeMulti::run ()
{
    setLatch ();

    simulator.integrator->run (*this);
    visit ([](Visitor * visitor)
    {
        visitor->part->update ();
    });
    visit ([](Visitor * visitor)
    {
        visitor->part->finalize ();
        visitor->part->finalizeEvent ();
        // A part could die during event processing, but it can wait till next EventStep to leave queue.
    });

    delete this;
}

void
EventSpikeMulti::visit (visitorFunction f)
{
    VisitorSpikeMulti v (this);
    v.visit (f);
}

void
EventSpikeMulti::setLatch ()
{
    int i = 0;
    int last = targets->size () - 1;
    while (i <= last)
    {
        Part * target = (*targets)[i];
        if (target)
        {
            target->setLatch (latch);
        }
        else
        {
            (*targets)[i] = (*targets)[last--];
        }
        i++;  // can go past last, but this will cause no harm.
    }
    if ((*targets)[last]) targets->resize (last + 1);
    else                  targets->resize (last);
}


// class EventSpikeMultiLatch ------------------------------------------------

void
EventSpikeMultiLatch::run ()
{
    setLatch ();
    delete this;
}


// class Visitor -------------------------------------------------------------

Visitor::Visitor (Event * event, Part * part)
:   event (event),
    part (part)
{
}

void
Visitor::visit (visitorFunction f)
{
    f (this);
}


// class VisitorStep ---------------------------------------------------------

VisitorStep::VisitorStep (EventStep * event)
:   Visitor (event)
{
    queue.next = 0;
    previous = 0;
}

void
VisitorStep::visit (visitorFunction f)
{
    previous = &queue;
    while (previous->next)
    {
        part = previous->next;
        f (this);
        if (previous->next == part) previous = part;  // Normal advance through list. Check is necessary in case part dequeued while f() was running.
    }
}

void
VisitorStep::enqueue (Part * newPart)
{
    newPart->setVisitor (this);
    if (queue.next) queue.next->setPrevious (newPart);
    newPart->setPrevious (&queue);
    newPart->next = queue.next;
    queue.next = newPart;
}


// class VisitorSpikeMulti ---------------------------------------------------

VisitorSpikeMulti::VisitorSpikeMulti (EventSpikeMulti * event)
:   Visitor (event)
{
}

void
VisitorSpikeMulti::visit (visitorFunction f)
{
    EventSpikeMulti * e = (EventSpikeMulti *) event;
    for (auto target : *e->targets)
    {
        part = target;
        f (this);
    }
}
