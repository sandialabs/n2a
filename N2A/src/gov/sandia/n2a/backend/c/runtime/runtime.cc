#include "runtime.tcc"
#include "MatrixFixed.tcc"
#include "Matrix.tcc"


using namespace std;


// General functions ---------------------------------------------------------

template class MatrixFixed<n2a_T,3,1>;

template n2a_T          uniform ();
template n2a_T          uniform (n2a_T sigma);
template n2a_T          gaussian ();
template n2a_T          gaussian (n2a_T sigma);

template MatrixFixed<n2a_T,3,1> grid    (int i, int nx, int ny, int nz);
template MatrixFixed<n2a_T,3,1> gridRaw (int i, int nx, int ny, int nz);


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

template class Simulatable<n2a_T>;
template class Part<n2a_T>;
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
