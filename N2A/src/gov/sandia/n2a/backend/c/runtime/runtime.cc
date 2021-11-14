/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#include "runtime.tcc"
#include "MatrixFixed.tcc"
#include "Matrix.tcc"

#include <csignal>


using namespace std;


// General functions ---------------------------------------------------------

template class MatrixFixed<n2a_T,3,1>;

template n2a_T uniform ();
template n2a_T uniform (n2a_T sigma);
template n2a_T gaussian ();
template n2a_T gaussian (n2a_T sigma);

template MatrixFixed<n2a_T,3,1> grid    (int i, int nx, int ny, int nz);
template MatrixFixed<n2a_T,3,1> gridRaw (int i, int nx, int ny, int nz);

template n2a_T pulse (n2a_T t, n2a_T width, n2a_T period, n2a_T rise, n2a_T fall);

template n2a_T unitmap (const MatrixAbstract<n2a_T> & A, n2a_T row, n2a_T column);

#ifndef N2A_SPINNAKER
void signalHandler (int number)
{
    cerr << "Got signal " << number << endl;
    switch (number)
    {
        case SIGINT:
        case SIGTERM:
            Simulator<n2a_T>::instance.stop = true;
            break;
        default:
            exit (number);
    }
}
#endif


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


// classes -------------------------------------------------------------------

template class Parameters<n2a_T>;
template class Simulatable<n2a_T>;
template class Part<n2a_T>;
template void removeMonitor (std::vector<Part<n2a_T> *> & partList, Part<n2a_T> * part);
template class PartTime<n2a_T>;
template class WrapperBase<n2a_T>;
template class ConnectIterator<n2a_T>;
template class ConnectPopulation<n2a_T>;
template class ConnectPopulationNN<n2a_T>;
template class ConnectMatrix<n2a_T>;
template class Population<n2a_T>;
template class Simulator<n2a_T>;
template class Integrator<n2a_T>;
template class Euler<n2a_T>;
template class RungeKutta<n2a_T>;
template class Event<n2a_T>;
template class EventStep<n2a_T>;
template class EventSpike<n2a_T>;
template class EventSpikeSingle<n2a_T>;
template class EventSpikeSingleLatch<n2a_T>;
template class EventSpikeMulti<n2a_T>;
template class EventSpikeMultiLatch<n2a_T>;
template class Visitor<n2a_T>;
template class VisitorStep<n2a_T>;
template class VisitorSpikeMulti<n2a_T>;
template class DelayBuffer<n2a_T>;
