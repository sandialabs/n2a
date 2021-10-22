/*
Copyright 2018-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_io_tcc
#define n2a_io_tcc


#include "io.h"

#include <fstream>
#include <cmath>
#include <stdlib.h>
#include <time.h>
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
    currentLine      = (T) -1;
    currentValues    = new T[1];
    currentValues[0] = (T) 0;
    currentCount     = 1;
    nextLine         = (T) NAN;
    nextValues       = 0;
    nextCount        = 0;
    A                = 0;
    Alast            = (T) -2;
    columnCount      = 0;
    timeColumn       = 0;
    timeColumnSet    = false;
    time             = false;
    delimiter        = ' ';
    delimiterSet     = false;
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
    if (A)             delete A;
}

template<class T>
void
InputHolder<T>::getRow (T row)
{
    while (true)
    {
        // Read and process next line
#       ifdef n2a_FP
        if (nextLine == NAN  &&  in->good ())
#       else
        if (std::isnan (nextLine)  &&  in->good ())
#       endif
        {
            String line;
            getline (*in, line);
            if (! line.empty ())
            {
                if (! delimiterSet)
                {
                    if      (line.find_first_of ('\t') != String::npos) delimiter = '\t'; // highest precedence
                    else if (line.find_first_of (',' ) != String::npos) delimiter = ',';
                    // space character is lowest precedence
                    delimiterSet =  delimiter != ' '  ||  line.find_first_not_of (' ') != String::npos;
                }

                int tempCount = 1;
                for (auto it : line) if (it == delimiter) tempCount++;
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
                        int j;
                        j = line.find_first_of (delimiter, i);
                        if (j == String::npos) j = end;
                        if (j > i) columnMap.emplace (line.substr (i, j - i), index);
                        i = j + 1;
                        index++;
                    }

                    // Make column count accessible to other code before first row of data is read.
                    if (! A  &&  currentCount < columnCount)
                    {
                        delete[] currentValues;
                        currentValues = new T[columnCount];
                        currentCount = columnCount;
                        memset (&currentValues[0], 0, columnCount * sizeof (T));
                    }

                    // Select time column
                    if (time  &&  ! timeColumnSet)
                    {
                        int timeMatch = 0;
                        for (auto it : columnMap)
                        {
                            int potentialMatch = 0;
                            String header = it.first.tolower ();
                            if      (header == "t"   ) potentialMatch = 1;
                            else if (header == "date") potentialMatch = 1;
                            else if (header == "time") potentialMatch = 2;
                            else if (header == "$t"  ) potentialMatch = 3;
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
                    int j;
                    j = line.find_first_of (delimiter, i);
                    if (j == String::npos) j = line.size ();
                    if (j == i)
                    {
                        nextValues[index] = 0;
                    }
                    else  // j > i
                    {
                        String field = line.substr (i, j - i);

                        // General case
#                       ifdef n2a_FP
                        if (time  &&  timeColumnSet  &&  index == timeColumn)
                        {
                            nextValues[index] = convert (field, Event<T>::exponent);
                        }
                        else
                        {
                            nextValues[index] = convert (field, exponent);
                        }
#                       else
                        nextValues[index] = (T) atof (field.c_str ());
#                       endif

                        // Special case for ISO 8601 formatted date
                        // Convert date to Unix time. Dates before epoch will be negative.
                        if (index == timeColumn)
                        {
                            bool valid = false;
                            int year   = 1970;  // will be adjusted below for mktime()
                            int month  = 1;     // ditto
                            int day    = 1;
                            int hour   = 0;
                            int minute = 0;
                            int second = 0;

                            int length = field.size ();
                            if (length <= 4)
                            {
                                if (nextValues[index] < 3000  &&  nextValues[index] > 0)
                                {
                                    valid = true;
                                    year = nextValues[index];
                                }
                            }
                            else if (length >= 7  &&  field[4] == '-')
                            {
                                valid = true;
                                year  = atoi (field.substr (0, 4).c_str ());
                                month = atoi (field.substr (5, 2).c_str ());
                                if (length >= 10  &&  field[7] == '-')
                                {
                                    day = atoi (field.substr (8, 2).c_str ());
                                    if (length >= 13  &&  field[10] == 'T')
                                    {
                                        hour = atoi (field.substr (11, 2).c_str ());
                                        if (length >= 16  &&  field[13] == ':')
                                        {
                                            minute = atoi (field.substr (14, 2).c_str ());
                                            if (length >= 19  &&  field[16] == ':')
                                            {
                                                second = atoi (field.substr (17, 2).c_str ());
                                            }
                                        }
                                    }
                                }
                            }

                            if (valid)
                            {
                                month -= 1;
                                year  -= 1900;

                                struct tm date;
                                date.tm_isdst = 0;  // time is strictly UTC, with no DST
                                // ignoring tm_wday and tm_yday, as mktime() doesn't do anything with them

                                // Hack to adjust for mktime() that can't handle dates before posix epoch (1970/1/1).
                                // This simple hack only works for years after ~1900.
                                // Solution comes from https://bugs.php.net/bug.php?id=17123
                                // Alternate solution would be to implement a simple mktime() right here.
                                // Since we don't care about DST or timezones, all it has to do is handle Gregorion leap-years.
                                time_t offset = 0;
                                if (year <= 70)  // Yes, that includes 1970 itself.
                                {
                                    // The referenced post suggested 56 years, which apparently makes week days align correctly.
                                    year += 56;
                                    date.tm_year = 70 + 56;
                                    date.tm_mon  = 0;
                                    date.tm_mday = 1;
                                    date.tm_hour = 0;
                                    date.tm_min  = 0;
                                    date.tm_sec  = 0;
                                    offset = mktime (&date);
                                }

                                date.tm_year = year;
                                date.tm_mon  = month;
                                date.tm_mday = day;
                                date.tm_hour = hour;
                                date.tm_min  = minute;
                                date.tm_sec  = second;

                                nextValues[index] = mktime (&date) - offset;
                            }
                        }
                    }
                    i = j + 1;
                }
                for (; index < columnCount; index++) nextValues[index] = 0;

                if (time) nextLine = nextValues[timeColumn];
                else      nextLine = currentLine + 1;
            }
        }

        // Determine if we have the requested data
        if (row <= currentLine) break;
#       ifdef n2a_FP
        if (nextLine == NAN) break;  // Return the current line, because another is not (yet) available. In general, we don't stall the simulator to wait for data.
#       else
        if (std::isnan (nextLine)) break;
#       endif
        if (row < nextLine - epsilon) break;

        T * tempValues = currentValues;
        int tempCount  = currentCount;
        currentLine   = nextLine;
        currentValues = nextValues;
        currentCount  = nextCount;
        nextLine   = (T) NAN;
        nextValues = tempValues;
        nextCount  = tempCount;
    }
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
    int c = (int) round (column);
    if (time  &&  c >= timeColumn) c++;  // time column is not included in raw index
    if      (c < 0            ) c = 0;
    else if (c >= currentCount) c = currentCount - 1;
    return currentValues[c];
}

template<class T>
Matrix<T>
InputHolder<T>::get (T row)
{
    getRow (row);
    if (Alast == currentLine) return *A;

    // Create a new matrix
    if (A) delete A;
    if (time  &&  currentCount > 1)
    {
        int columns = currentCount - 1;
        A = new Matrix<T> (1, columns);
        int from = 0;
        for (int to = 0; to < columns; to++)
        {
            if (from == timeColumn) from++;
            (*A)(0,to) = currentValues[from++];
        }
    }
    else
    {
        A = new Matrix<T> (currentValues, 0, 1, currentCount, currentCount, 1);
    }
    Alast = currentLine;
    return *A;
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

#ifdef n2a_FP

template<class T>
void
OutputHolder<T>::trace (T now, const String & column, const Matrix<T> & A, int exponent, const char * mode)
{
    int rows = A.rows ();
    int cols = A.columns ();
    if (rows == 1)
    {
        for (int c = 0; c < cols; c++) trace (now, column + "(" + c + ")", A(0,c), exponent, mode);
    }
    else if (cols == 1)
    {
        for (int r = 0; r < rows; r++) trace (now, column + "(" + r + ")", A(r,0), exponent, mode);
    }
    else
    {
        for (int r = 0; r < rows; r++)
        {
            for (int c = 0; c < cols; c++)
            {
                trace (now, column + "(" + r + "," + c + ")", A(r,c), exponent, mode);
            }
        }
    }
}

#else

template<class T>
void
OutputHolder<T>::trace (T now, const String & column, const Matrix<T> & A, const char * mode)
{
    int rows = A.rows ();
    int cols = A.columns ();
    if (rows == 1)
    {
        for (int c = 0; c < cols; c++) trace (now, column + "(" + c + ")", A(0,c), mode);
    }
    else if (cols == 1)
    {
        for (int r = 0; r < rows; r++) trace (now, column + "(" + r + ")", A(r,0), mode);
    }
    else
    {
        for (int r = 0; r < rows; r++)
        {
            for (int c = 0; c < cols; c++)
            {
                trace (now, column + "(" + r + "," + c + ")", A(r,c), mode);
            }
        }
    }
}

#endif

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
    int index;  // Only used for "raw" mode.
    if (raw)
    {
        index = (int) round (column);
        sprintf (buffer, "%i", index);
    }
    else
    {
        sprintf (buffer, "%g", column);
    }
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
