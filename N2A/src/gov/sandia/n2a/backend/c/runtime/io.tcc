#ifndef n2a_io_tcc
#define n2a_io_tcc


#include "io.h"

#include <fstream>
#include <cmath>
#include <stdlib.h>
#ifdef n2a_FP
#include "runtime.h"   // For Event::exponent
#include "fixedpoint.h"
#endif


// class IteratorSkip --------------------------------------------------------

template<class T>
IteratorSkip<T>::IteratorSkip (Matrix<T> * A)
:   A (A)
{
    this->row    = -1;
    this->column = 0;
    this->value  = 0;

    nextRow    = -1;
    nextColumn = 0;
    nextValue  = 0;
    getNext ();
}

template<class T>
bool
IteratorSkip<T>::next ()
{
    if (nextRow < 0) return false;
    this->value  = nextValue;
    this->row    = nextRow;
    this->column = nextColumn;
    getNext ();
    return true;
}

template<class T>
void
IteratorSkip<T>::getNext ()
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

template<class T>
IteratorSparse<T>::IteratorSparse (MatrixSparse<T> * A)
:   A (A)
{
    this->row    = 0;
    this->column = 0;
    this->value  = 0;

    columns = (*A->data).size ();
    if (columns > 0) it = (*A->data)[0].begin ();
}

template<class T>
bool
IteratorSparse<T>::next ()
{
    if (columns == 0) return false;
    while (true)
    {
        if (it != (*A->data)[this->column].end ()) break;
        if (++this->column >= columns) return false;
        it = (*A->data)[this->column].begin ();
    }

    this->row   = it->first;
    this->value = it->second;
    it++;
    return true;
}


// MatrixInput ---------------------------------------------------------------

template<class T>
MatrixInput<T>::MatrixInput (const String & fileName)
:   Holder (fileName)
{
    A = 0;
}

template<class T>
MatrixInput<T>::~MatrixInput ()
{
    if (A) delete A;
}

template<class T>
T
MatrixInput<T>::get (T row, T column)
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
            T b = column - c;
            return (1 - b) * (*A)(0,c) + b * (*A)(0,c+1);
        }
    }
    else if (r >= lastRow)
    {
        if      (c <  0         ) return (*A)(lastRow,0         );
        else if (c >= lastColumn) return (*A)(lastRow,lastColumn);
        else
        {
            T b = column - c;
            return (1 - b) * (*A)(lastRow,c) + b * (*A)(lastRow,c+1);
        }
    }
    else
    {
        T a = row - r;
        T a1 = 1 - a;
        if      (c <  0         ) return a1 * (*A)(r,0         ) + a * (*A)(r+1,0         );
        else if (c >= lastColumn) return a1 * (*A)(r,lastColumn) + a * (*A)(r+1,lastColumn);
        else
        {
            T b = column - c;
            return   (1 - b) * (a1 * (*A)(r,c  ) + a * (*A)(r+1,c  ))
                   +      b  * (a1 * (*A)(r,c+1) + a * (*A)(r+1,c+1));
        }
    }
}

#ifdef n2a_FP

template<>
int
MatrixInput<int>::get (int row, int column)  // row and column have exponent=0
{
    // Just assume handle is good.
    int lastRow    = A->rows ()    - 1;  // exponent=MSB
    int lastColumn = A->columns () - 1;
    int64_t scaledRow    = row    * lastRow;   // raw exponent = 0+MSB-MSB = 0
    int64_t scaledColumn = column * lastColumn;
    int r = scaledRow    >> FP_MSB;  // to turn raw result into integer, shift = 0-MSB = -MSB
    int c = scaledColumn >> FP_MSB;
    if (r < 0)
    {
        if      (c <  0         ) return (*A)(0,0         );
        else if (c >= lastColumn) return (*A)(0,lastColumn);
        else
        {
            int b = scaledColumn & 0x3FFFFFFF;  // fractional part, with exponent = 0 (same as raw exponent)
            int b1 = (1 << FP_MSB) - b;
            return (int64_t) b1 * (*A)(0,c) + (int64_t) b * (*A)(0,c+1) >> FP_MSB;
        }
    }
    else if (r >= lastRow)
    {
        if      (c <  0         ) return (*A)(lastRow,0         );
        else if (c >= lastColumn) return (*A)(lastRow,lastColumn);
        else
        {
            int b = scaledColumn & 0x3FFFFFFF;
            int b1 = (1 << FP_MSB) - b;
            return (int64_t) b1 * (*A)(lastRow,c) + (int64_t) b * (*A)(lastRow,c+1) >> FP_MSB;
        }
    }
    else
    {
        int a = scaledRow & 0x3FFFFFFF;
        int a1 = (1 << FP_MSB) - a;
        if      (c <  0         ) return (int64_t) a1 * (*A)(r,0         ) + (int64_t) a * (*A)(r+1,0         ) >> FP_MSB;
        else if (c >= lastColumn) return (int64_t) a1 * (*A)(r,lastColumn) + (int64_t) a * (*A)(r+1,lastColumn) >> FP_MSB;
        else
        {
            int b = scaledColumn & 0x3FFFFFFF;
            int b1 = (1 << FP_MSB) - b;
            return   b1 * ((int64_t) a1 * (*A)(r,c  ) + (int64_t) a * (*A)(r+1,c  ) >> FP_MSB)
                   + b  * ((int64_t) a1 * (*A)(r,c+1) + (int64_t) a * (*A)(r+1,c+1) >> FP_MSB) >> FP_MSB;
        }
    }
}

#endif

template<class T>
T
MatrixInput<T>::getRaw (T row, T column)
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

template<class T>
int
MatrixInput<T>::rows ()
{
    return A->rows ();
}

template<class T>
int
MatrixInput<T>::columns ()
{
    return A->columns ();
}

template<class T>
IteratorNonzero<T> *
MatrixInput<T>::getIterator ()
{
    if (A->classID () & MatrixSparseID) return new IteratorSparse<T> ((MatrixSparse<T> *) A);
    return new IteratorSkip<T> ((Matrix<T> *) A);
}

#ifdef n2a_FP

inline int
convert (String input, int exponent)
{
    const double d = atof (input.c_str ());
    if (d == 0) return 0;
    if (std::isnan (d)) return NAN;
    bool negate = d < 0;
    if (std::isinf (d))
    {
        if (negate) return -INFINITY;
        return              INFINITY;
    }

    int64_t bits = *(int64_t *) &d;
    int e = (int) ((bits >> 52) & 0x7FF) - 1023;
    bits |= 0x10000000000000l;  // set implied msb of mantissa (bit 52) to 1
    bits &= 0x1FFFFFFFFFFFFFl;  // clear sign and exponent bits
    if (negate) bits = -bits;
    return bits >> 52 - FP_MSB + exponent - e;
}

#endif

std::vector<Holder *> matrixMap;

template<class T>
MatrixInput<T> *
#ifdef n2a_FP
matrixHelper (const String & fileName, int exponent, MatrixInput<T> * oldHandle)
#else
matrixHelper (const String & fileName,               MatrixInput<T> * oldHandle)
#endif
{
    MatrixInput<T> * handle = (MatrixInput<T> *) holderHelper (matrixMap, fileName, oldHandle);
    if (! handle)
    {
        handle = new MatrixInput<T> (fileName);
        matrixMap.push_back (handle);

        std::ifstream ifs (fileName.c_str ());
        if (! ifs.good ()) std::cerr << "Failed to open matrix file: " << fileName << std::endl;
        String line;
        getline (ifs, line);
        if (line == "Sparse")  // Homegrown sparse matrix format
        {
            MatrixSparse<T> * S = new MatrixSparse<T>;
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
#               ifdef n2a_FP
                T element = convert (line, exponent);
#               else
                T element = (T) atof (line.c_str ());
#               endif

                if (element) S->set (row, col, element);
            }
        }
        else  // Dense matrix
        {
            // Re-open file to ensure that we get the first line.
            ifs.close ();
            ifs.open (fileName.c_str ());

            std::vector<std::vector<T>> temp;
            std::vector<T> row;
            int columns = 0;
            bool transpose = false;

            // Scan for opening "["
            char token;
            do
            {
                ifs.get (token);
                if (token == '~') transpose = true;
            }
            while (token != '['  &&  ifs.good ());

            // Read rows until closing "]"
            String buffer;
            bool done = false;
            while (ifs.good ()  &&  ! done)
            {
                ifs.get (token);

                bool processLine = false;
                switch (token)
                {
                    case '\r':
                        break;  // ignore CR characters
                    case ' ':
                    case '\t':
                        if (buffer.size () == 0) break;  // ignore leading whitespace (equivalent to trim)
                    case ',':
                        // Process element
                        if (buffer.size () == 0)
                        {
                            row.push_back (0);
                        }
                        else
                        {
#                           ifdef n2a_FP
                            row.push_back (convert (buffer, exponent));
#                           else
                            row.push_back ((T) atof (buffer.c_str ()));
#                           endif
                            buffer.clear ();
                        }
                        break;
                    case ']':
                        done = true;
                    case ';':
                    case '\n':
                    {
                        // Process any final element
                        if (buffer.size () > 0)
                        {
#                           ifdef n2a_FP
                            row.push_back (convert (buffer, exponent));
#                           else
                            row.push_back ((T) atof (buffer.c_str ()));
#                           endif
                            buffer.clear ();
                        }
                        // Process line
                        int c = row.size ();
                        if (c > 0)
                        {
                            temp.push_back (row);  // Duplicates row, rather than saving a reference to it, so row can be reused.
                            columns = std::max (columns, c);
                            row.clear ();
                        }
                        break;
                    }
                    default:
                        buffer += token;
                }
            }

            // Assign elements to A.
            const int rows = temp.size ();
            if (transpose)
            {
                Matrix<T> * A = new Matrix<T> (columns, rows);
                handle->A = A;
                clear (*A);
                for (int r = 0; r < rows; r++)
                {
                    std::vector<T> & row = temp[r];
                    for (int c = 0; c < row.size (); c++)
                    {
                        (*A)(c,r) = row[c];
                    }
                }
            }
            else
            {
                Matrix<T> * A = new Matrix<T> (rows, columns);
                handle->A = A;
                clear (*A);
                for (int r = 0; r < rows; r++)
                {
                    std::vector<T> & row = temp[r];
                    for (int c = 0; c < row.size (); c++)
                    {
                        (*A)(r,c) = row[c];
                    }
                }
            }
        }
        if (handle->A->rows () == 0  ||  handle->A->columns () == 0)
        {
            std::cerr << "Ill-formed matrix in file: " << fileName << std::endl;
            delete handle->A;
            handle->A = new Matrix<T> (1, 1);
            clear (*handle->A); // set to 0
        }
    }
    return handle;
}


// InputHolder ---------------------------------------------------------------

template<class T>
InputHolder<T>::InputHolder (const String & fileName)
:   Holder (fileName)
{
    currentLine      = -1;
    currentValues    = new T[1];
    currentValues[0] = (T) 0;
    currentCount     = 1;
    nextLine         = -1;
    nextValues       = 0;
    nextCount        = 0;
    columnCount      = 0;
    timeColumn       = 0;
    timeColumnSet    = false;
    time             = false;
#   ifdef n2a_FP
    epsilon          = 1;
#   else
    epsilon          = (T) 1e-6;
#   endif

    if (fileName.empty ()) in = &std::cin;
    else                   in = new std::ifstream (fileName.c_str ());
}

template<class T>
InputHolder<T>::~InputHolder ()
{
    if (in  &&  in != &std::cin) delete in;
    if (currentValues) delete[] currentValues;
    if (nextValues   ) delete[] nextValues;
}

template<class T>
void
InputHolder<T>::getRow (T row)
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
                columnCount = std::max (columnCount, tempCount);

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
                    nextValues = new T[columnCount];
                    nextCount = columnCount;
                }
                int index = 0;
                int i = 0;
                for (; index < tempCount; index++)
                {
                    int j = line.find_first_of (" \t", i);
                    if (j == String::npos) j = line.size ();
#                   ifdef n2a_FP
                    if (j > i)
                    {
                        if (time  &&  timeColumnSet  &&  index == timeColumn)
                        {
                            nextValues[index] = convert (line.substr (i, j - i), Event<T>::exponent);
                        }
                        else
                        {
                            nextValues[index] = convert (line.substr (i, j - i), exponent);
                        }
                    }
#                   else
                    if (j > i) nextValues[index] = (T) atof (line.substr (i, j - i).c_str ());
#                   endif
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

        T * tempValues = currentValues;
        int tempCount  = currentCount;
        currentLine   = nextLine;
        currentValues = nextValues;
        currentCount  = nextCount;
        nextLine   = -1;
        nextValues = tempValues;
        nextCount  = tempCount;
    }
}

template<class T>
int
InputHolder<T>::getColumns ()
{
    getRow (0);
    if (time) return std::max (0, columnCount - 1);
    return columnCount;
}

template<class T>
T
InputHolder<T>::get (T row, const String & column)
{
    getRow (row);
    std::unordered_map<String,int>::const_iterator it = columnMap.find (column);
    if (it == columnMap.end ()) return 0;
    return currentValues[it->second];
}

template<class T>
T
InputHolder<T>::get (T row, T column)
{
    getRow (row);
    int lastColumn = currentCount - 1;
    if (time) column *= (lastColumn - 1);  // time column is not included in interpolation
    else      column *=  lastColumn;
    int c = (int) floor (column);
    T b = column - c;
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

#ifdef n2a_FP

template<>
int
InputHolder<int>::get (int row, int column)
{
    getRow (row);
    int lastColumn = currentCount - 1;
    int64_t scaledColumn;
    if (time) scaledColumn = (int64_t) column * (lastColumn - 1);  // time column is not included in interpolation
    else      scaledColumn = (int64_t) column *  lastColumn;
    int c = scaledColumn >> FP_MSB;
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
    int b = scaledColumn & 0x3FFFFFFF;
    int b1 = (1 << FP_MSB) - b;
    return (int64_t) b1 * currentValues[c] + (int64_t) b * currentValues[d] >> FP_MSB;
}

#endif

template<class T>
T
InputHolder<T>::getRaw (T row, T column)
{
    getRow (row);
    int c = (int) round (column);
    if (time  &&  c >= timeColumn) c++;  // time column is not included in raw index
    if      (c < 0            ) c = 0;
    else if (c >= currentCount) c = currentCount - 1;
    return currentValues[c];
}

std::vector<Holder *> inputMap;

template<class T>
InputHolder<T> *
#ifdef n2a_FP
inputHelper (const String & fileName, int exponent, InputHolder<T> * oldHandle)
#else
inputHelper (const String & fileName,               InputHolder<T> * oldHandle)
#endif
{
    InputHolder<T> * handle = (InputHolder<T> *) holderHelper (inputMap, fileName, oldHandle);
    if (! handle)
    {
        handle = new InputHolder<T> (fileName);
        inputMap.push_back (handle);
#       ifdef n2a_FP
        handle->exponent = exponent;
#       endif
    }
    return handle;
}


// OutputHolder --------------------------------------------------------------

template<class T>
OutputHolder<T>::OutputHolder (const String & fileName)
:   Holder (fileName)
{
    columnsPrevious = 0;
    traceReceived   = false;
    t               = 0;
    raw             = false;

    if (fileName.empty ())
    {
        out = &std::cout;
        columnFileName = "out.columns";
    }
    else
    {
        out = new std::ofstream (fileName.c_str ());
        columnFileName = fileName + ".columns";
    }
}

template<class T>
OutputHolder<T>::~OutputHolder ()
{
    if (out)
    {
        writeTrace ();
        out->flush ();
        if (out != &std::cout) delete out;

        writeModes ();
    }
    for (auto it : columnMode) delete it;
}

template<class T>
void
OutputHolder<T>::trace (T now)
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
#           ifdef n2a_FP
            columnValues.push_back ((float) t / pow (2.0f, FP_MSB - Event<T>::exponent));
#           else
            columnValues.push_back (t);
#           endif
            columnMode.push_back (new std::map<String,String>);
        }
        else
        {
#           ifdef n2a_FP
            columnValues[0] = (float) t / pow (2.0f, FP_MSB - Event<T>::exponent);
#           else
            columnValues[0] = t;
#           endif
        }
        traceReceived = true;
    }
}

template<class T>
void
OutputHolder<T>::addMode (const char * mode)
{
    std::map<String,String> * result = new std::map<String,String>;
    columnMode.push_back (result);
    if (mode)
    {
        String rest = mode;
        String hint;
        while (! rest.empty ())
        {
            split (rest, ",", hint, rest);
            hint.trim ();
            String key;
            String value;
            split (hint, "=", key, value);
            if (key == "timeScale")
            {
                std::map<String,String> * c = columnMode[0];
                (*c)["scale"] = value;
            }
            else if (key == "ymin"  ||  key == "ymax"  ||  key == "xmin"  ||  key == "xmax")
            {
                std::map<String,String> * c = columnMode[0];
                (*c)[key] = value;
            }
            else
            {
                (*result)[key] = value;
            }
        }
    }
}

template<class T>
void
#ifdef n2a_FP
OutputHolder<T>::trace (T now, const String & column, T rawValue, int exponent, const char * mode)
#else
OutputHolder<T>::trace (T now, const String & column, T value,                  const char * mode)
#endif
{
    trace (now);

#   ifdef n2a_FP
    float value = (float) rawValue / pow (2.0f, FP_MSB - exponent);
#   endif

    std::unordered_map<String, int>::iterator result = columnMap.find (column);
    if (result == columnMap.end ())
    {
        columnMap[column] = columnValues.size ();
        columnValues.push_back ((float) value);
        addMode (mode);
    }
    else
    {
        columnValues[result->second] = (float) value;
    }
}

template<class T>
void
#ifdef n2a_FP
OutputHolder<T>::trace (T now, T column, T rawValue, int exponent, const char * mode)
#else
OutputHolder<T>::trace (T now, T column, T value,                  const char * mode)
#endif
{
    trace (now);

#   ifdef n2a_FP
    float value = (float) rawValue / pow (2.0f, FP_MSB - exponent);
#   endif

    char buffer[32];
    int index = (int) round (column);  // "raw" is the most likely case, so preemptively convert to int
    if (raw) sprintf (buffer, "%i", index);
    else     sprintf (buffer, "%g", column);
    String columnName = buffer;

    std::unordered_map<String, int>::iterator result = columnMap.find (columnName);
    if (result == columnMap.end ())
    {
        if (raw)
        {
            index++;  // column index + offset for time column
            columnValues.resize (index, NAN);  // add any missing columns before the one we are about to create
        }
        columnMap[columnName] = columnValues.size ();
        columnValues.push_back ((float) value);
        addMode (mode);
    }
    else
    {
        columnValues[result->second] = (float) value;
    }
}

template<class T>
void
OutputHolder<T>::writeTrace ()
{
    if (! traceReceived  ||  ! out) return;  // Don't output anything unless at least one value was set.

    const int count = columnValues.size ();
    const int last  = count - 1;

    // Write headers if new columns have been added
    if (count > columnsPrevious)
    {
        if (! raw)
        {
            std::vector<String> headers (count);
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
                String header (headers[i]);  // deep copy
                header.replace_all (' ', '_');
                (*out) << header;
            }
            (*out) << std::endl;
        }
        columnsPrevious = count;
        writeModes ();
    }

    // Write values
    for (int i = 0; i <= last; i++)
    {
        float & c = columnValues[i];
        if (! std::isnan (c)) (*out) << c;
        if (i < last) (*out) << "\t";
        c = NAN;
    }
    (*out) << std::endl;

    traceReceived = false;
}

template<class T>
void
OutputHolder<T>::writeModes ()
{
    std::ofstream mo (columnFileName.c_str ());
    mo << "N2A.schema=2\n";
    for (auto it : columnMap)
    {
        int i = it.second;
        mo << i << ":" << it.first << "\n";
        auto mode = columnMode[i];
        for (auto nv : *mode) mo << " " << nv.first << ":" << nv.second << "\n";
    }
    // mo should automatically flush and close here
}

std::vector<Holder *> outputMap;

template<class T>
OutputHolder<T> *
outputHelper (const String & fileName, OutputHolder<T> * oldHandle)
{
    OutputHolder<T> * handle = (OutputHolder<T> *) holderHelper (outputMap, fileName, oldHandle);
    if (! handle)
    {
        handle = new OutputHolder<T> (fileName);
        outputMap.push_back (handle);
    }
    return handle;
}


#endif
