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


// MatrixInput ---------------------------------------------------------------

float
MatrixInput::get (float row, float column)
{
    // Just assume handle is good.
    int lastRow    = rows_    - 1;
    int lastColumn = columns_ - 1;
    row    *= lastRow;
    column *= lastColumn;
    int r = (int) floor (row);
    int c = (int) floor (column);
    if (r < 0)
    {
        if      (c <  0         ) return (*this)(0,0         );
        else if (c >= lastColumn) return (*this)(0,lastColumn);
        else
        {
            float b = column - c;
            return (1 - b) * (*this)(0,c) + b * (*this)(0,c+1);
        }
    }
    else if (r >= lastRow)
    {
        if      (c <  0         ) return (*this)(lastRow,0         );
        else if (c >= lastColumn) return (*this)(lastRow,lastColumn);
        else
        {
            float b = column - c;
            return (1 - b) * (*this)(lastRow,c) + b * (*this)(lastRow,c+1);
        }
    }
    else
    {
        float a = row - r;
        float a1 = 1 - a;
        if      (c <  0         ) return a1 * (*this)(r,0         ) + a * (*this)(r+1,0         );
        else if (c >= lastColumn) return a1 * (*this)(r,lastColumn) + a * (*this)(r+1,lastColumn);
        else
        {
            float b = column - c;
            return   (1 - b) * (a1 * (*this)(r,c  ) + a * (*this)(r+1,c  ))
                   +      b  * (a1 * (*this)(r,c+1) + a * (*this)(r+1,c+1));
        }
    }
}

float
MatrixInput::getRaw (float row, float column)
{
    int r = (int) row;
    int c = (int) column;
    if      (r <  0       ) r = 0;
    else if (r >= rows_   ) r = rows_    - 1;
    if      (c <  0       ) c = 0;
    else if (c >= columns_) c = columns_ - 1;
    return (*this)(r,c);
}

map<string, MatrixInput *> matrixMap;

MatrixInput *
matrixHelper (const string & fileName, MatrixInput * oldHandle)
{
    if (oldHandle)
    {
        if (oldHandle->fileName == fileName) return oldHandle;
        map<string, MatrixInput *>::iterator i = matrixMap.find (oldHandle->fileName);
        if (i != matrixMap.end ())
        {
            delete i->second;
            matrixMap.erase (i);
        }
    }

    map<string, MatrixInput *>::iterator i = matrixMap.find (fileName);
    if (i != matrixMap.end ()) return i->second;

    MatrixInput * handle = new MatrixInput;
    handle->fileName = fileName;
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


// InputHolder ---------------------------------------------------------------

InputHolder::InputHolder (const string & fileName)
:   fileName (fileName)
{
    currentLine   = -1;
    currentValues = 0;
    currentCount  = 0;
    nextLine      = -1;
    nextValues    = 0;
    nextCount     = 0;
    columnCount   = 0;
    timeColumn    = 0;
    timeColumnSet = false;
    epsilon       = 0;

    if (fileName.empty ())
    {
        in = &cin;
    }
    else
    {
        in = new ifstream (fileName.c_str ());
    }
}

InputHolder::~InputHolder ()
{
    if (in  &&  in != &cin) delete in;
    if (currentValues) delete[] currentValues;
    if (nextValues   ) delete[] nextValues;
}

void
InputHolder::getRow (float row, bool time)
{
    while (true)
    {
        // Read and process next line
        if (nextLine < 0  &&  in->good ())
        {
            string line;
            getline (*in, line);
            if (! line.empty ())
            {
                int tempCount = 1;
                for (auto it : line) if (it == ' '  ||  it == '\t') tempCount++;
                columnCount = max (columnCount, tempCount);

                // Decide whether this is a header row or a value row
                char firstCharacter = line[0];
                if (firstCharacter < '-'  ||  firstCharacter == '/'  ||  firstCharacter > '9')  // not a number, so must be column header
                {
                    // Add any column headers. Generally, these will only be new headers as of this cycle.
                    int index = 0;
                    int i = 0;
                    int end = line.size ();
                    while (i < end)
                    {
                        int j = line.find_first_of (" \t", i);
                        if (j == string::npos) j = end;
                        if (j > i) columnMap.emplace (line.substr (i, j - i), index);
                        i = j + 1;
                        index++;
                    }

                    // Select time column
                    if (time  &&  ! timeColumnSet)
                    {
                        int timeMatch = 0;
                        for (auto it : columnMap)
                        {
                            int potentialMatch = 0;
                            if      (it.first == "t"   ) potentialMatch = 1;
                            else if (it.first == "TIME") potentialMatch = 2;
                            else if (it.first == "$t"  ) potentialMatch = 3;
                            if (potentialMatch > timeMatch)
                            {
                                timeMatch = potentialMatch;
                                timeColumn = it.second;
                            }
                        }
                        timeColumnSet = true;
                    }

                    continue;  // back to top of outer while loop, skipping any other processing below
                }

                if (nextCount < columnCount)
                {
                    if (nextValues) delete[] nextValues;
                    nextValues = new float[columnCount];
                    nextCount = columnCount;
                }
                int index = 0;
                int i = 0;
                for (; index < tempCount; index++)
                {
                    int j = line.find_first_of (" \t", i);
                    if (j == string::npos) j = line.size ();
                    if (j > i) nextValues[index] = atof (line.substr (i, j - i).c_str ());
                    else       nextValues[index] = 0;
                    i = j + 1;
                }
                for (; index < columnCount; index++) nextValues[index] = 0;

                if (time) nextLine = nextValues[timeColumn];
                else      nextLine = currentLine + 1;
            }
        }

        // Determine if we have the requested data
        if (row <= currentLine) break;
        if (nextLine < 0) break;  // Return the current line, because another is not (yet) available. In general, we don't stall the simulator to wait for data.
        if (row < nextLine - epsilon) break;

        float * tempValues = currentValues;
        int     tempCount  = currentCount;
        currentLine   = nextLine;
        currentValues = nextValues;
        currentCount  = nextCount;
        nextLine   = -1;
        nextValues = tempValues;
        nextCount  = tempCount;
    }
}

int
InputHolder::getColumns (bool time)
{
    getRow (0, time);
    if (time) return max (0, columnCount - 1);
    return columnCount;
}

float
InputHolder::get (float row, bool time, const string & column)
{
    getRow (row, time);
    try
    {
        return currentValues[columnMap.at (column)];
    }
    catch (const out_of_range & e)
    {
        return 0;
    }
}

float
InputHolder::get (float row, bool time, float column)
{
    getRow (row, time);
    int lastColumn = currentCount - 1;
    if (time) column *= (lastColumn - 1);  // time column is not included in interpolation
    else      column *=  lastColumn;
    int c = (int) floor (column);
    float b = column - c;
    int d = c + 1;
    if (time)
    {
        if (c >= timeColumn) c++;  // Implicitly, d will also be >= timeColumn.
        if (d >= timeColumn) d++;
    }
    if (c < 0)
    {
        if (time  &&  timeColumn == 0  &&  currentCount > 1) return currentValues[1];
        return currentValues[0];
    }
    if (c >= lastColumn)
    {
        if (time  &&  timeColumn == lastColumn  &&  currentCount > 1) return currentValues[lastColumn-1];
        return currentValues[lastColumn];
    }
    return (1 - b) * currentValues[c] + b * currentValues[d];
}

float
InputHolder::getRaw (float row, bool time, float column)
{
    getRow (row, time);
    int c = (int) round (column);
    if (time  &&  c >= timeColumn) c++;  // time column is not included in raw index
    if      (c < 0            ) c = 0;
    else if (c >= currentCount) c = currentCount - 1;
    return currentValues[c];
}

map<string,InputHolder *> inputMap;

InputHolder *
inputHelper (const string & fileName, InputHolder * oldHandle)
{
    if (oldHandle)
    {
        if (oldHandle->fileName == fileName) return oldHandle;
        map<string,InputHolder *>::iterator i = inputMap.find (oldHandle->fileName);
        if (i != inputMap.end ())
        {
            delete i->second;
            inputMap.erase (i);
        }
    }

    map<string,InputHolder *>::iterator i = inputMap.find (fileName);
    if (i != inputMap.end ()) return i->second;

    InputHolder * handle = new InputHolder (fileName);
    inputMap.emplace (fileName, handle);
    return handle;
}


// OutputHolder --------------------------------------------------------------

OutputHolder::OutputHolder (const string & fileName)
:   fileName (fileName)
{
    columnsPrevious = 0;
    traceReceived   = false;
    t               = 0;
    raw             = false;

    if (fileName.empty ())
    {
        out = &cout;
    }
    else
    {
        out = new ofstream (fileName.c_str ());
    }
}

OutputHolder::~OutputHolder ()
{
    if (out)
    {
        writeTrace ();
        out->flush ();
        if (out != &cout) delete out;
    }
}

void
OutputHolder::trace (float now)
{
    // Detect when time changes and dump any previously traced values.
    if (now > t)
    {
        writeTrace ();
        t = now;
    }

    if (! traceReceived)  // First trace for this cycle
    {
        if (columnValues.empty ())  // slip $t into first column
        {
            columnMap["$t"] = 0;
            columnValues.push_back (t);
        }
        else
        {
            columnValues[0] = t;
        }
        traceReceived = true;
    }
}

void
OutputHolder::trace (float now, const string & column, float value)
{
    trace (now);

    unordered_map<string, int>::iterator result = columnMap.find (column);
    if (result == columnMap.end ())
    {
        columnMap[column] = columnValues.size ();
        columnValues.push_back (value);
    }
    else
    {
        columnValues[result->second] = value;
    }
}

void
OutputHolder::trace (float now, float column, float value)
{
    trace (now);

    char buffer[32];
    int index = (int) round (column);  // "raw" is the most likely case, so preemptively convert to int
    if (raw) sprintf (buffer, "%i", index);
    else     sprintf (buffer, "%g", column);
    string columnName = buffer;

    unordered_map<string, int>::iterator result = columnMap.find (columnName);
    if (result == columnMap.end ())
    {
        if (raw)
        {
            index++;  // column index + offset for time column
            columnValues.resize (index, NAN);  // add any missing columns before the one we are about to create
        }
        columnMap[columnName] = columnValues.size ();
        columnValues.push_back (value);
    }
    else
    {
        columnValues[result->second] = value;
    }
}

void
OutputHolder::writeTrace ()
{
    if (! traceReceived  ||  ! out) return;  // Don't output anything unless at least one value was set.

    const int count = columnValues.size ();
    const int last  = count - 1;

    // Write headers if new columns have been added
    if (! raw  &&  count > columnsPrevious)
    {
        vector<string> headers (count);
        for (auto it : columnMap) headers[it.second] = it.first;

        (*out) << headers[0];  // Should be $t
        int i = 1;
        for (; i < columnsPrevious; i++)
        {
            (*out) << "\t";
        }
        for (; i < count; i++)
        {
            (*out) << "\t";
            (*out) << headers[i];
        }
        (*out) << endl;
        columnsPrevious = count;
    }

    // Write values
    for (int i = 0; i <= last; i++)
    {
        float & c = columnValues[i];
        if (! isnan (c)) (*out) << c;
        if (i < last) (*out) << "\t";
        c = NAN;
    }
    (*out) << endl;

    traceReceived = false;
}

map<string,OutputHolder *> outputMap;

OutputHolder *
outputHelper (const string & fileName, OutputHolder * oldHandle)
{
    if (oldHandle)
    {
        if (oldHandle->fileName == fileName) return oldHandle;
        map<string,OutputHolder *>::iterator i = outputMap.find (oldHandle->fileName);
        if (i != outputMap.end ())
        {
            delete i->second;
            outputMap.erase (i);
        }
    }

    map<string, OutputHolder *>::iterator i = outputMap.find (fileName);
    if (i != outputMap.end ()) return i->second;

    OutputHolder * handle = new OutputHolder (fileName);
    outputMap.emplace (fileName, handle);
    return handle;
}

void
outputClose ()
{
    for (auto it : outputMap) delete it.second;
    // No need to clear collection, because this function is only called during shutdown.
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
Simulatable::path (string & result)
{
    result = "";
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
Part::project (int i, int j, Vector3 & xyz)
{
    xyz[0] = 0;
    xyz[1] = 0;
    xyz[2] = 0;
}

float
Part::getLive ()
{
    return 1;
}

float
Part::getP (Simulator & simulator)
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


// Wrapper -------------------------------------------------------------------

void
WrapperBase::init (Simulator & simulator)
{
    population->init (simulator);
}

void
WrapperBase::integrate (Simulator & simulator)
{
    population->integrate (simulator);
}

void
WrapperBase::update (Simulator & simulator)
{
    population->update (simulator);
}

bool
WrapperBase::finalize (Simulator & simulator)
{
    population->finalize (simulator);
    return population->n;  // The simulation stops when the last model instance dies.
}

void
WrapperBase::updateDerivative (Simulator & simulator)
{
    population->updateDerivative (simulator);
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
    part->before        = &live;
    part->after         =  live.after;
    part->before->after = part;
    part->after->before = part;
}

void
PopulationCompartment::remove (Part * part)
{
    if (part == old) old = old->after;
    part->before->after = part->after;
    part->after->before = part->before;

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
            result->before        = &live;
            result->after         =  live.after;
            result->before->after = result;
            result->after->before = result;

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

    Part * p = live.before;
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
        Part * part;
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
        Part * b = B->live.after;
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

    Part * c = this->create ();

    // Scan AxB
    Part * Alast = A->old;
    Part * Blast = B->live.after;
    bool minSatisfied = false;
    while (! minSatisfied)
    {
        minSatisfied = true;

        // New A with all of B
        Part * a = A->live.after;
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
                    Part * b = ((KDTreeEntry *) (*it))->part;

                    c->setPart (1, b);
                    if (Bmax  &&  c->getCount (1) >= Bmax) continue;  // no room in this B
                    float create = c->getP (simulator);
                    if (create <= 0  ||  create < 1  &&  create < uniform ()) continue;  // Yes, we need all 3 conditions. If create is 0 or 1, we do not do a random draw, since it should have no effect.
                    simulator.enqueue (c);
                    c->init (simulator);
                    Acount++;
                    c = this->create ();
                    c->setPart (0, a);

                    if (Amax  &&  Acount >= Amax) break;  // stop scanning B once this A is full
                }
            }
            else
            {
                Part * Bnext = Blast->before;  // will change if we make some connections
                if (Bnext == &B->live) Bnext = Bnext->before;
                Part * b = Blast;
                do
                {
                    b = b->after;
                    if (b == &B->live) b = b->after;

                    c->setPart (1, b);
                    if (Bmax  &&  c->getCount (1) >= Bmax) continue;  // no room in this B
                    float create = c->getP (simulator);
                    if (create <= 0  ||  create < 1  &&  create < uniform ()) continue;  // Yes, we need all 3 conditions. If create is 0 or 1, we do not do a random draw, since it should have no effect.
                    simulator.enqueue (c);
                    c->init (simulator);
                    c = this->create ();
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
            Part * b = B->live.after;
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

                Part * Anext;
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
                    if (create <= 0  ||  create < 1  &&  create < uniform ()) continue;
                    c->init (simulator);
                    simulator.enqueue (c);
                    c = this->create ();
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
            Part * a = A->old;
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
            Part * b = B->old;
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
