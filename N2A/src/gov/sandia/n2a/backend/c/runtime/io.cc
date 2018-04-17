#include "nosys.h"
#include "io.h"

#include "fl/MatrixSparse.tcc"
#include <fstream>

using namespace fl;
using namespace std;

template class Matrix<float>;


// Holder --------------------------------------------------------------------

Holder::Holder (const String & fileName)
:   fileName (fileName)
{
}

Holder::~Holder ()
{
}


// class IteratorSkip --------------------------------------------------------

IteratorSkip::IteratorSkip (Matrix<float> * A)
:   A (A)
{
    row    = -1;
    column = 0;
    value  = 0;

    nextRow    = -1;
    nextColumn = 0;
    nextValue  = 0;
    getNext ();
}

bool
IteratorSkip::next ()
{
    if (nextRow < 0) return false;
    value  = nextValue;
    row    = nextRow;
    column = nextColumn;
    getNext ();
    return true;
}

void
IteratorSkip::getNext ()
{
    for (; nextColumn < A->columns_; nextColumn++)
    {
        while (true)
        {
            if (++nextRow >= A->rows_) break;
            nextValue = (*A)(nextRow,nextColumn);
            if (nextValue != 0) return;
        }
        nextRow = -1;
    }
}


// class IteratorSparse ------------------------------------------------------

IteratorSparse::IteratorSparse (fl::MatrixSparse<float> * A)
:   A (A)
{
    row    = 0;
    column = 0;
    value  = 0;

    columns = (*A->data).size ();
    if (columns > 0) it = (*A->data)[0].begin ();
}

bool
IteratorSparse::next ()
{
    if (columns == 0) return false;
    while (true)
    {
        if (it != (*A->data)[column].end ()) break;
        if (++column >= columns) return false;
        it = (*A->data)[column].begin ();
    }

    row   = it->first;
    value = it->second;
    it++;
    return true;
}


// MatrixInput ---------------------------------------------------------------

MatrixInput::MatrixInput (const String & fileName)
:   Holder (fileName)
{
    A = 0;
}

MatrixInput::~MatrixInput ()
{
    if (A) delete A;
}

float
MatrixInput::get (float row, float column)
{
    // Just assume handle is good.
    int lastRow    = A->rows ()    - 1;
    int lastColumn = A->columns () - 1;
    row    *= lastRow;
    column *= lastColumn;
    int r = (int) floor (row);
    int c = (int) floor (column);
    if (r < 0)
    {
        if      (c <  0         ) return (*A)(0,0         );
        else if (c >= lastColumn) return (*A)(0,lastColumn);
        else
        {
            float b = column - c;
            return (1 - b) * (*A)(0,c) + b * (*A)(0,c+1);
        }
    }
    else if (r >= lastRow)
    {
        if      (c <  0         ) return (*A)(lastRow,0         );
        else if (c >= lastColumn) return (*A)(lastRow,lastColumn);
        else
        {
            float b = column - c;
            return (1 - b) * (*A)(lastRow,c) + b * (*A)(lastRow,c+1);
        }
    }
    else
    {
        float a = row - r;
        float a1 = 1 - a;
        if      (c <  0         ) return a1 * (*A)(r,0         ) + a * (*A)(r+1,0         );
        else if (c >= lastColumn) return a1 * (*A)(r,lastColumn) + a * (*A)(r+1,lastColumn);
        else
        {
            float b = column - c;
            return   (1 - b) * (a1 * (*A)(r,c  ) + a * (*A)(r+1,c  ))
                   +      b  * (a1 * (*A)(r,c+1) + a * (*A)(r+1,c+1));
        }
    }
}

float
MatrixInput::getRaw (float row, float column)
{
    int rows = A->rows ();
    int cols = A->columns ();
    int r = (int) row;
    int c = (int) column;
    if      (r <  0   ) r = 0;
    else if (r >= rows) r = rows - 1;
    if      (c <  0   ) c = 0;
    else if (c >= cols) c = cols - 1;
    return (*A)(r,c);
}

int
MatrixInput::rows ()
{
    return A->rows ();
}

int
MatrixInput::columns ()
{
    return A->columns ();
}

IteratorNonzero *
MatrixInput::getIterator ()
{
    if (A->classID () & MatrixSparseID) return new IteratorSparse ((MatrixSparse<float> *) A);
    return new IteratorSkip ((Matrix<float> *) A);
}

vector<Holder *> matrixMap;

MatrixInput *
matrixHelper (const String & fileName, MatrixInput * oldHandle)
{
    MatrixInput * handle = (MatrixInput *) holderHelper (matrixMap, fileName, oldHandle);
    if (! handle)
    {
        handle = new MatrixInput (fileName);
        matrixMap.push_back (handle);

        ifstream ifs (fileName.c_str ());
        if (! ifs.good ()) cerr << "Failed to open matrix file: " << fileName << endl;
        String line;
        getline (ifs, line);
        if (line == "Sparse")  // Homegrown sparse matrix format
        {
            MatrixSparse<float> * S = new MatrixSparse<float>;
            handle->A = S;
            while (ifs.good ())
            {
                getline (ifs, line);
                line.trim ();
                if (line.empty ()) continue;

                String value;
                split (line, ",", value, line);
                value.trim ();
                int row = atoi (value.c_str ());

                split (line, ",", value, line);
                value.trim ();
                int col = atoi (value.c_str ());

                line.trim ();
                float element = atof (line.c_str ());

                if (element) S->set (row, col, element);
            }
        }
        else  // Dense matrix
        {
            handle->A = new Matrix<float>;
            // Re-open file to ensure that we get the first line.
            ifs.close ();
            ifs.open (fileName.c_str ());
            ifs >> (*handle->A);
        }
        if (handle->rows () == 0  ||  handle->columns () == 0)
        {
            cerr << "Ill-formed matrix in file: " << fileName << endl;
            handle->A->resize (1, 1);  // fallback matrix
            handle->A->clear ();       // set to 0
        }
    }
    return handle;
}


// InputHolder ---------------------------------------------------------------

InputHolder::InputHolder (const String & fileName)
:   Holder (fileName)
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
    time          = false;
    epsilon       = 1e-6f;

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
InputHolder::getRow (float row)
{
    while (true)
    {
        // Read and process next line
        if (nextLine < 0  &&  in->good ())
        {
            String line;
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
                        if (j == String::npos) j = end;
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
                    if (j == String::npos) j = line.size ();
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
InputHolder::getColumns ()
{
    getRow (0);
    if (time) return max (0, columnCount - 1);
    return columnCount;
}

float
InputHolder::get (float row, const String & column)
{
    getRow (row);
    unordered_map<String,int>::const_iterator it = columnMap.find (column);
    if (it == columnMap.end ()) return 0;
    return currentValues[it->second];
}

float
InputHolder::get (float row, float column)
{
    getRow (row);
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
InputHolder::getRaw (float row, float column)
{
    getRow (row);
    int c = (int) round (column);
    if (time  &&  c >= timeColumn) c++;  // time column is not included in raw index
    if      (c < 0            ) c = 0;
    else if (c >= currentCount) c = currentCount - 1;
    return currentValues[c];
}

vector<Holder *> inputMap;

InputHolder *
inputHelper (const String & fileName, InputHolder * oldHandle)
{
    InputHolder * handle = (InputHolder *) holderHelper (inputMap, fileName, oldHandle);
    if (! handle)
    {
        handle = new InputHolder (fileName);
        inputMap.push_back (handle);
    }
    return handle;
}


// OutputHolder --------------------------------------------------------------

OutputHolder::OutputHolder (const String & fileName)
:   Holder (fileName)
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
OutputHolder::trace (float now, const String & column, float value)
{
    trace (now);

    unordered_map<String, int>::iterator result = columnMap.find (column);
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
    String columnName = buffer;

    unordered_map<String, int>::iterator result = columnMap.find (columnName);
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
        vector<String> headers (count);
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

vector<Holder *> outputMap;

OutputHolder *
outputHelper (const String & fileName, OutputHolder * oldHandle)
{
    OutputHolder * handle = (OutputHolder *) holderHelper (outputMap, fileName, oldHandle);
    if (! handle)
    {
        handle = new OutputHolder (fileName);
        outputMap.push_back (handle);
    }
    return handle;
}

void
outputClose ()
{
    for (auto it : outputMap) delete it;
    // No need to clear collection, because this function is only called during shutdown.
}
