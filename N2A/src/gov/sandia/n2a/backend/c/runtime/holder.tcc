/*
Copyright 2018-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_holder_tcc
#define n2a_holder_tcc


#include "mymath.h"
#include "holder.h"
#include "runtime.h"   // For Event::exponent

#include <fstream>


// Parameters ----------------------------------------------------------------

template<class T>
void
Parameters<T>::parse (const String & line)
{
    int pos = line.find_first_of ('=');
    if (pos == String::npos)
    {
        namedValues[line] = "";
    }
    else
    {
        String name  = line.substr (0, pos);
        String value = line.substr (pos + 1);
        if (name == "-include") read (value);
        else                    namedValues[name] = value;
    }
}

template<class T>
void
Parameters<T>::parse (int argc, const char * argv[])
{
    for (int i = 1; i < argc; i++) parse (argv[i]);
}

template<class T>
void
Parameters<T>::read (const String & parmFileName)
{
    std::ifstream ifs (parmFileName.c_str ());
    if (! ifs.good ()) fprintf (stderr, "Failed to open parameter file: %s\n", parmFileName.c_str ());
    read (ifs);
}

template<class T>
void
Parameters<T>::read (std::istream & stream)
{
    while (stream.good ())
    {
        String line;
        getline (stream, line);
        line.trim ();
        parse (line);
    }
}

template<class T>
T
Parameters<T>::get (const String & name, T defaultValue) const
{
    std::unordered_map<String,String>::const_iterator it = namedValues.find (name);
    if (it == namedValues.end ()) return defaultValue;
    const String & value = it->second;

#   ifdef n2a_FP
    return (T) atoi (value.c_str ());  // TODO: Use atof() instead, then convert to suitable fixed-point format.
#   else
    return (T) atof (value.c_str ());
#   endif
}

template<class T>
String
Parameters<T>::get (const String & name, const String & defaultValue) const
{
    std::unordered_map<String,String>::const_iterator it = namedValues.find (name);
    if (it == namedValues.end ()) return defaultValue;
    return it->second;
}


// Mfile ---------------------------------------------------------------------

template<class T>
Mfile<T>::Mfile (const String & fileName)
:   Holder (fileName)
{
    doc = new n2a::MDoc (fileName.c_str ());
}

template<class T>
Mfile<T>::~Mfile ()
{
    if (doc) delete doc;
    for (auto & m : matrices)  if (m.second) delete m.second;
    for (auto & k : childKeys) if (k.second) delete k.second;
}

std::vector<String>
keyPath (const char * delimiter, const std::vector<String> & path)
{
    std::vector<String> result;
    result.reserve (path.size ());  // assuming there are no delimiters
    for (auto & e : path)
    {
        size_t pos   = 0;
        size_t count = e.size ();
        while (pos < count)
        {
            size_t next = e.find_first_of (delimiter, pos);
            if (next == String::npos)  // This is actually the most common case.
            {
                result.push_back (e.substr (pos).c_str ());
                break;
            }
            if (next != pos) result.push_back (e.substr (pos, next-pos).c_str ());  // The test is necessary to skip multiple delimiters with nothing between them.
            pos = next + 1;
        }
    }
    return result;
}

template<class T>
MatrixAbstract<T> *
#ifdef n2a_FP
Mfile<T>::getMatrix (const char * delimiter, const std::vector<String> & path, int exponent)
#else
Mfile<T>::getMatrix (const char * delimiter, const std::vector<String> & path)
#endif
{
    String key = join (delimiter, path);  // This skips any kind of normalization, so not 100% correct. Not sure if it's worth the extra compute to split and rejoin the keys.
    MatrixAbstract<T> * A = matrices[key];  // If key does not exist, then the c++ standard promises that the inserted value will be zero-initialized.
    if (A) return A;

    MatrixSparse<T> * S = new MatrixSparse<T>;
    n2a::MNode & m = doc->child (keyPath (delimiter, path));
    for (auto & row : m)
    {
        int r = atoi (row.key ().c_str ());
        for (auto & col : row)
        {
            int c = atoi (col.key ().c_str ());
            String value = col.get ();
#           ifdef n2a_FP
            S->set (r, c, convert (value, exponent));
#           else
            S->set (r, c, (T) atof (value.c_str ()));
#           endif
        }
    }
    matrices[key] = S;
    return S;
}

template<class T>
String
Mfile<T>::getChildKey (const char * delimiter, const std::vector<String> & path, const int index)
{
    if (index < 0) return "";
    String key = join (delimiter, path);
    std::vector<String> * list = childKeys[key];
    if (! list)
    {
        n2a::MNode & m = doc->child (keyPath (delimiter, path));
        list = new std::vector<String> (m.childKeys ());
        childKeys[key] = list;
    }
    if (index >= list->size ()) return "";
    return (*list)[index];
}

template<class T>
Mfile<T> *
MfileHelper (const String & fileName, Mfile<T> * oldHandle)
{
    Mfile<T> * handle = (Mfile<T> *) SIMULATOR getHolder (fileName, oldHandle);
    if (! handle)
    {
        handle = new Mfile<T> (fileName);
        SIMULATOR holders.push_back (handle);
    }
    return handle;
}


// InputHolder ---------------------------------------------------------------

template<class T>
InputHolder<T>::InputHolder (const String & fileName)
:   Holder (fileName)
{
    current            = new InputLine<T>;
    current->line      = (T) -1;
    current->values.resize (1);
    current->values[0] = (T) 0;
    next               = new InputLine<T>;
    next->line         = (T) NAN;
    A                  = 0;
    Alast              = (T) NAN;
    columnCount        = 0;
    timeColumn         = 0;
    timeColumnSet      = false;
    time               = false;
    smooth             = false;
#   ifdef n2a_FP
    epsilon            = 1;
#   else
    epsilon            = (T) 1e-6;
#   endif
}

template<class T>
InputHolder<T>::~InputHolder ()
{
    if (current) delete current;
    if (next)    delete next;
    if (A)       delete A;
}

template<class T>
T
convertDate (const String & field, T defaultValue)
{
    // This function uses mktime(), which depends on the value of the environment variable TZ.
    // To ensure interpretation in UTC, this variable must be set in the program startup code.

    bool valid = false;
    int year   = 1970;  // will be adjusted below for mktime()
    int month  = 1;     // ditto
    int day    = 1;
    int hour   = 0;
    int minute = 0;
    int second = 0;

    // ISO 8601 and its prefixes
    int length = field.size ();
    if (length == 4)
    {
        year  = atoi (field.c_str ());
        valid =  year < 3000  &&  year > 1000;
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
    else  // Conventional dates
    {
        int pos1 = field.find_first_of ('/');
        if (pos1 != String::npos)
        {
            month = atoi (field.substr (0, pos1).c_str ());
            pos1++;
            int pos2 = field.find_first_of ('/', pos1);
            if (pos2 != String::npos)
            {
                valid = true;
                day  = atoi (field.substr (pos1, pos2-pos1).c_str ());
                year = atoi (field.substr (pos2+1).c_str ());

                // TODO: add keyword parameter for correct date format, such as "mdy" or "ymd". Implement by shuffling fields.
                if (month > 31)  // probably ymd format
                {
                    int temp = month;
                    month    = day;
                    day      = year;
                    year     = temp;
                }
            }
            else  // Only two numbers, so either ym or my
            {
                year = atoi (field.substr (pos1).c_str ());
                if (month > 12)  // probably ym format
                {
                    int temp = month;
                    month    = year;
                    year     = temp;
                }
                valid = year > 12  &&  month <= 12;
            }

            // Until we have better hints from user, assume 2-digit year should have century offset.
            // TODO: fix Y2K bug ...
            if (year < 100)
            {
                if (year > 50) year += 1900;
                else           year += 2000;
            }
        }
    }

    if (! valid) return defaultValue;

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

    return mktime (&date) - offset;  // Unix time; an integer, so exponent=0
}

template<class T>
T
InputHolder<T>::get (T row, const String & column)
{
    getRow (row);
    std::unordered_map<String,int>::const_iterator it = columnMap.find (column);
    if (it == columnMap.end ()) return 0;

#   ifdef n2a_FP
    if (smooth  &&  row >= current->line  &&  current->line != -INFINITY  &&  next->line != NAN)
    {
        // We don't need to know what exponent the line values have, as long as they match.
        int b = ((int64_t) (row - current->line) << FP_MSB) / (next->line - current->line);
        int b1 = (1 << FP_MSB) - b;
        return (int64_t) b * next->values[it->second] + (int64_t) b1 * current->values[it->second] >> FP_MSB;
    }
#   else
    if (smooth  &&  row >= current->line  &&  std::isfinite (current->line)  &&  std::isfinite (next->line))
    {
        T b = (row - current->line) / (next->line - current->line);
        return b * next->values[it->second] + (1-b) * current->values[it->second];
    }
#   endif

    return current->values[it->second];
}

template<class T>
T
InputHolder<T>::get (T row, T column)
{
    getRow (row);
    int c = (int) round (column);
    if (time  &&  c >= timeColumn) c++;  // time column is not included in raw index
    int currentCount = current->values.size ();
    if      (c < 0            ) c = 0;
    else if (c >= currentCount) c = currentCount - 1;

#   ifdef n2a_FP
    if (smooth  &&  row >= current->line  &&  current->line != -INFINITY  &&  next->line != NAN)
    {
        int b  = ((int64_t) (row - current->line) << FP_MSB) / (next->line - current->line);
        int b1 = (1 << FP_MSB) - b;
        return (int64_t) b * next->values[c] + (int64_t) b1 * current->values[c] >> FP_MSB;
    }
#   else
    if (smooth  &&  row >= current->line  &&  std::isfinite (current->line)  &&  std::isfinite (next->line))
    {
        T b = (row - current->line) / (next->line - current->line);
        return b * next->values[c] + (1-b) * current->values[c];
    }
#   endif

    return current->values[c];
}

template<class T>
Matrix<T>
InputHolder<T>::get (T row)
{
    getRow (row);
    int currentCount = current->values.size ();

#   ifdef n2a_FP
    if (smooth  &&  row >= current->line  &&  current->line != -INFINITY  &&  next->line != NAN)
    {
        if (Alast == row) return *A;

        // Create a new matrix
        if (A) delete A;
        int b  = ((int64_t) (row - current->line) << FP_MSB) / (next->line - current->line);
        int b1 = (1 << FP_MSB) - b;
        if (currentCount > 1)
        {
            int columns = currentCount - 1;
            A = new Matrix<T> (1, columns);
            int from = 0;
            for (int to = 0; to < columns; to++)
            {
                if (from == timeColumn) from++;
                (*A)(0,to) = (int64_t) b * next->values[from] + (int64_t) b1 * current->values[from] >> FP_MSB;
                from++;
            }
        }
        else
        {
            A = new Matrix<T> (1, 1);
            (*A)(0,0) = (int64_t) b * next->values[0] + (int64_t) b1 * current->values[0] >> FP_MSB;
        }

        Alast = row;
        return *A;
    }
#   else
    if (smooth  &&  row >= current->line  &&  std::isfinite (current->line)  &&  std::isfinite (next->line))
    {
        if (Alast == row) return *A;

        // Create a new matrix
        if (A) delete A;
        T b  = (row - current->line) / (next->line - current->line);
        T b1 = 1 - b;
        if (currentCount > 1)
        {
            int columns = currentCount - 1;
            A = new Matrix<T> (1, columns);
            int from = 0;
            for (int to = 0; to < columns; to++)
            {
                if (from == timeColumn) from++;
                (*A)(0,to) = b * next->values[from] + b1 * current->values[from];
                from++;
            }
        }
        else
        {
            A = new Matrix<T> (1, 1);
            (*A)(0,0) = b * next->values[0] + b1 * current->values[0];
        }

        Alast = row;
        return *A;
    }
#   endif

    if (Alast == current->line) return *A;

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
            (*A)(0,to) = current->values[from++];
        }
    }
    else
    {
        A = new Matrix<T> (current->values.data (), 0, 1, currentCount, currentCount, 1);
    }
    Alast = current->line;
    return *A;
}


// InputXSV ------------------------------------------------------------------

template<class T>
InputXSV<T>::InputXSV (const String & fileName)
:   InputHolder<T> (fileName)
{
    if (fileName.empty ())
    {
        in = &std::cin;
    }
    else
    {
        in = new std::ifstream (fileName.c_str ());
        if (! in->good ()) fprintf (stderr, "Failed to open input file: %s\n", fileName.c_str ());
    }
}

template<class T>
InputXSV<T>::~InputXSV ()
{
    if (in  &&  in != &std::cin) delete in;
    for (auto it : buffer) delete it;
}

template<class T>
void
InputXSV<T>::getRow (T row)
{
    std::lock_guard<std::mutex> lock (mutexLine);

    while (true)
    {
        // Read and process next line
        if (std::isnan (next->line)  &&  in->good ())
        {
            parser.parseLine (*in, parts);
            int partCount = parts.size ();
            if (partCount == 0) continue;
            columnCount = std::max (columnCount, partCount);

            // Decide whether this is a header row or a value row
            // * Always check the first row of the file.
            // * Otherwise, only check rows that start with a non-number character.
            String & firstColumn = parts[0];
            char firstCharacter = firstColumn.empty () ? parser.delimiter : firstColumn[0];
            if (firstCharacter != parser.delimiter)
            {
                if (firstCharacter < '-'  ||  firstCharacter == '/'  ||  firstCharacter > '9')
                {
                    // Add any column headers. Generally, these will only be new headers as of this cycle.
                    for (int i = 0; i < partCount; i++)
                    {
                        String & c = parts[i];
                        c.trim ();
                        if (! c.empty ()) columnMap.emplace (c, i);
                    }

                    // Make column count accessible to other code before first row of data is read.
                    if (! A)
                    {
                        if (time) current->line = -INFINITY;
                        if (current->values.size () != columnCount)
                        {
                            current->values.resize (columnCount);
                            memset (current->values.data (), 0, columnCount * sizeof (T));
                        }
                    }

                    // Select time column
                    if (time  &&  ! timeColumnSet)
                    {
                        int timeMatch = 0;
                        for (auto it : columnMap)
                        {
                            int potentialMatch = 0;
                            String header = it.first.toLowerCase ();
                            if      (header == "t"   ) potentialMatch = 2;
                            else if (header == "date") potentialMatch = 2;
                            else if (header == "time") potentialMatch = 3;
                            else if (header == "$t"  ) potentialMatch = 4;
                            else if (header.find ("time") != String::npos) potentialMatch = 1;
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
            }

            next->values.resize (columnCount);
            int index = 0;
            for (; index < partCount; index++)
            {
                String & field = parts[index];
                if (field.empty ())
                {
                    next->values[index] = 0;
                }
                else
                {
                    // Special case for formatted date
                    bool valid = false;
                    if (index == timeColumn)
                    {
                        next->values[index] = convertDate (field, NAN);
                        valid = ! std::isnan (next->values[index]);
#                       ifdef n2a_FP
                        if (valid)
                        {
                            // Need to put value in expected exponent.
                            int shift = -(time ? exponentRow : exponent);
                            if (shift >= 0) next->values[index] <<= shift;
                            else            next->values[index] >>= -shift;
                        }
#                       endif
                    }

                    if (! valid)  // Not a date, so general case ...
                    {
#                       ifdef n2a_FP
                        next->values[index] = convert (field, time  &&  index == timeColumn ? exponentRow : exponent);
#                       else
                        next->values[index] = (T) atof (field.c_str ());
#                       endif
                    }
                }
            }
            for (; index < columnCount; index++) next->values[index] = 0;

            if (time) next->line = next->values[timeColumn];
            else      next->line = current->line + 1;
        }

        // Determine if we have the requested data
        if (row <= current->line) break;
        if (std::isnan (next->line)) break;  // Return the current line, because another is not (yet) available. In general, we don't stall the simulator to wait for data.
        if (row < next->line - epsilon) break;

        InputLine<T> * temp = current;
        current = next;
        next    = temp;
        next->line = (T) NAN;
    }
}

template<class T>
int
InputXSV<T>::readAhead (int rowCount)
{
    // TODO
    return 0;
}

template<class T>
void
InputXSV<T>::release ()
{
    // TODO
}

template<class T>
InputXSV<T> *
#ifdef n2a_FP
inputHelperXSV (const String & fileName, int exponent, int exponentRow, InputXSV<T> * oldHandle)
#else
inputHelperXSV (const String & fileName,                                InputXSV<T> * oldHandle)
#endif
{
    InputXSV<T> * handle = (InputXSV<T> *) SIMULATOR getHolder (fileName, oldHandle);
    if (! handle)
    {
        handle = new InputXSV<T> (fileName);
        SIMULATOR holders.push_back (handle);
#       ifdef n2a_FP
        handle->exponent    = exponent;
        handle->exponentRow = exponentRow;
#       endif
    }
    return handle;
}


#ifdef HAVE_HDF

// InputHDF ------------------------------------------------------------------

template<class T>
InputHDF<T>::InputHDF (const String & fileName, const String & resource)
:   InputHolder<T> (fileName),
    resource       (resource)
{
    sub          = SubHolderHDF::allocate (fileName);
    warning      = false;
    nwb          = false;
    rowCount     = 0;
    startingTime = (T) 0;
    period       = (T) 0;
    timestamps   = 0;
    lastRow      = 0;
    start        = 0;
    count        = 0;

    // Check for NWB
    std::lock_guard<std::mutex> lock (sub->mutexFile);
    try
    {
        // If we can succeed at retrieving the resource as a Group, then it is an NWB.
        // This test is sufficient for the present. We may need to examine the structure more carefully to accomodate other cases, such as SONATA.
        H5::Group timeSeries = sub->file.openGroup (resource.c_str ());
        nwb = true;
    }
    catch (const H5::Exception & error)
    {
        // "nwb" is already set to false.
    }
}

template<class T>
InputHDF<T>::~InputHDF ()
{
    if (timestamps) delete[] timestamps;
    if (start)      delete[] start;
    if (count)      delete[] count;

    if (! sub) return;
    {
        // "data" will be closed automatically after this dtor, but we should close it before sub->file is destroyed.
        std::lock_guard<std::mutex> lock (sub->mutexFile);
        data.close ();
    }
    sub->release ();
}

template<class T>
void
InputHDF<T>::getRow (T requested)
{
    std::lock_guard<std::mutex> lock (mutexLine);

    try
    {
        if (data.getId () == H5I_INVALID_HID)
        {
            if (! sub  ||  warning) return;  // In failed state.

            std::lock_guard<std::mutex> lock (sub->mutexFile);

            H5::H5File & file = sub->file;
            if (nwb)
            {
                H5::Group timeSeries = file.openGroup (resource.c_str ());
                data = timeSeries.openDataSet ("data");
                if (timeSeries.nameExists ("timestamps"))  // Use explicit time stamps.
                {
                    H5::DataSet ts = timeSeries.openDataSet ("timestamps");
                    H5::DataSpace fspace = ts.getSpace ();
                    int dimCount = fspace.getSimpleExtentNdims ();  // NWB standard promises this is 1.
                    std::vector<hsize_t> dims (dimCount);
                    fspace.getSimpleExtentDims (dims.data ());
                    int timeRows = dims[0];
                    for (int i = 1; i < dimCount; i++) timeRows *= dims[i];  // Defensive, just in case this isn't really an NWB group.
                    timestamps = new T[timeRows];
#                   ifdef n2a_FP
                    std::vector<double> temp (timeRows);
                    ts.read (temp.data (), H5::PredType::NATIVE_DOUBLE);
                    for (int i = 0; i < timeRows; i++) timestamps[i] = (T) convert (temp[i], exponentRow);
#                   else
                    ts.read (timestamps, H5::PredType::n2a_HDF_T);
#                   endif
                }
                else  // Use startingTime+N*period.
                {
                    H5::DataSet   starting_time = timeSeries   .openDataSet   ("starting_time");
                    H5::Attribute rateAttribute = starting_time.openAttribute ("rate");
                    double rate;
                    rateAttribute.read (H5::PredType::NATIVE_DOUBLE, &rate);

#                   ifdef n2a_FP
                    double temp;
                    starting_time.read (&temp, H5::PredType::NATIVE_DOUBLE);
                    startingTime = (T) convert (temp,     exponentRow);
                    period       = (T) convert (1 / rate, exponentRow);  // (1/rate) * scale = scale/rate
#                   else
                    starting_time.read (&startingTime, H5::PredType::n2a_HDF_T);
                    period = 1 / rate;
#                   endif
                }
            }
            else  // Regular dataset, not NWB
            {
                data = file.openDataSet (resource.c_str ());
            }

            H5::DataSpace fspace = data.getSpace ();
            dimCount = fspace.getSimpleExtentNdims ();
            if (dimCount > 2)
            {
                fprintf (stderr, "TimeSeries data must be 1D or 2D: %s\n", resource.c_str ());
                warning = true;
                return;
            }
            start = new hsize_t[dimCount];
            count = new hsize_t[dimCount];
            std::vector<hsize_t> dims (dimCount);
            fspace.getSimpleExtentDims (dims.data ());
            rowCount = dims[0];
            if (dimCount == 2)
            {
                columnCount = dims[1];
                start[1] = 0;
                count[1] = columnCount;
            }
            else
            {
                columnCount = 1;
            }

            if (time) current->line = -INFINITY;
            if (current->values.size () != columnCount)
            {
                current->values.resize (columnCount);
                memset (current->values.data (), 0, columnCount * sizeof (T));
            }
            next->values.resize (columnCount);
            // next->line is NAN, so next->values won't be used. No need to clear them.
        }

        // Since HDF allows random access, we just need to determine the requested row,
        // or rows that bracket the requested time.
        int row = rowFromLine (requested);

        bool fetchCurrent = true;
        if (smooth)
        {
            if (next->line - epsilon <= requested  &&  next->line + period > requested)  // next is re-usable.
            {
                InputLine<T> * temp = current;
                current = next;
                next    = temp;
                next->line = (T) NAN;

                fetchCurrent = false;
            }

            int nextRow = row + 1;
            if (nextRow < rowCount)
            {
                T oldLine = next->line;
                next->line = lineFromRow (nextRow);
                if (next->line != oldLine) getSlab (nextRow, 1, next->values.data ());
            }
            else
            {
                next->line = (T) NAN;
            }
        }

        if (fetchCurrent  &&  row >= 0  &&  row < rowCount)
        {
            T oldLine = current->line;
            current->line = lineFromRow (row);
            if (current->line != oldLine) getSlab (row, 1, current->values.data ());
        }
    }
    catch (const H5::Exception & error)
    {
        std::stringstream ss;
        ss << "Error while accessing HDF:" << std::endl;
        ss << "  " << fileName << std::endl;
        ss << "  " << resource << std::endl;
        ss << "  " << error.getDetailMsg () << std::endl;
        fprintf (stderr, "%s", ss.str ().c_str ());
        warning = true;
        return;
    }
}

template<class T>
int
InputHDF<T>::rowFromLine (T line)
{
    if (time)
    {
        if (period > 0)
        {
#           ifdef n2a_FP
            return (line - startingTime) / period;
#           else
            return (int) floor ((line - startingTime) / period);
#           endif
        }
        else if (timestamps)
        {
            for (; lastRow < rowCount; lastRow++)
            {
                if (timestamps[lastRow] - epsilon > line) break;
            }
            // Now row points just past our desired current line, or it points just past end of timestamps.
            lastRow--;
            if (lastRow < 0) lastRow = 0;
            return lastRow;
        }
        else if (timeColumnSet)
        {
            // TODO: determine time column during startup, then handle line similarly to timestamps case above.
            // Currently, we fall through to default case, which is to return line literally as row.
        }
    }
#   ifdef n2a_FP
    return line;
#   else
    return (int) floor (line);
#   endif
}

template<class T>
T
InputHDF<T>::lineFromRow (int row)
{
    if (time)
    {
        if (period > 0)
        {
            return startingTime + row * period;
        }
        else if (timestamps)
        {
            return timestamps[row];
        }
        // else time column. Fall through to row index below.
    }
    return row;
}

template<class T>
void
InputHDF<T>::getSlab (hsize_t row, hsize_t rowCount, T * values)
{
    std::lock_guard<std::mutex> lock (sub->mutexFile);

    start[0] = row;
    count[0] = rowCount;
    H5::DataSpace fspace = data.getSpace ();
    fspace.selectHyperslab (H5S_SELECT_SET, count, start);
    H5::DataSpace mspace (dimCount, count);

#   ifdef n2a_FP
    int dataCount = columnCount * rowCount;
    std::vector<double> temp (dataCount);
    data.read (temp.data (), H5::PredType::NATIVE_DOUBLE, mspace, fspace);
    for (int i = 0; i < dataCount; i++) values[i] = (T) convert (temp[i], exponent);
    if (time  &&  ! nwb)
    {
        for (int r = 0; r < rowCount; r++)
        {
            int base = r * columnCount;
            values[base + timeColumn] = (T) convert (temp[base + timeColumn], exponentRow);
        }
    }
#   else
    data.read (values, H5::PredType::n2a_HDF_T, mspace, fspace);
#   endif
}

template<class T>
InputHDF<T> *
#ifdef n2a_FP
inputHelperHDF (const String & fileName, const String & resource, int exponent, int exponentRow, InputHDF<T> * oldHandle)
#else
inputHelperHDF (const String & fileName, const String & resource,                                InputHDF<T> * oldHandle)
#endif
{
    String key = fileName + "|" + (resource[0] == '/' ? resource.substr (1) : resource);
    InputHDF<T> * handle = (InputHDF<T> *) SIMULATOR getHolder (key, oldHandle);
    if (! handle)
    {
        handle = new InputHDF<T> (fileName, resource);
        SIMULATOR holders.push_back (handle);
#       ifdef n2a_FP
        handle->exponent    = exponent;
        handle->exponentRow = exponentRow;
#       endif
    }
    return handle;
}

#endif  // HAVE_HDF


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
        try
        {
            writeTrace ();
            out->flush ();
        }
        catch (...)
        {
            std::cerr << "WARNING: final trace values might have been lost" << std::endl;
        }
        if (out != &std::cout) delete out;

        try
        {
            writeModes ();
        }
        catch (...)
        {
            std::cerr << "WARNING: column info might have been lost" << std::endl;
        }
    }
    for (auto it : columnMode) if (it) delete it;
}

template<class T>
void
OutputHolder<T>::trace (T now)
{
    std::lock_guard<std::recursive_mutex> lock (mutexLine);

    // Detect when time changes and dump any previously traced values.
    if (now != t)  // Compare not equal allows keyword "x" (if defined) to move backward as well as forward.
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
            columnValues.push_back ((float) t * pow (2.0f, Event<T>::exponent));
#           else
            columnValues.push_back (t);
#           endif
            columnMode.push_back (new std::map<String,String>);
        }
        else
        {
#           ifdef n2a_FP
            columnValues[0] = (float) t * pow (2.0f, Event<T>::exponent);
#           else
            columnValues[0] = t;
#           endif
        }
        traceReceived = true;
    }
}

template<class T>
int
OutputHolder<T>::getColumnIndex (const String & column)
{
    std::lock_guard<std::recursive_mutex> lock (mutexLine);

    std::unordered_map<String, int>::iterator result = columnMap.find (column);
    if (result != columnMap.end ()) return result->second;

    if (raw)
    {
        // Backfill entries up to, but not including, the one that will be created
        // after this conditional section.
        // Notice that "count" is one less than actual position index for the column.
        // That is, column=="1" is in position 2, but count here would be 1, and we
        // ensure columnValues and columnMap of size 2. Then below, those vectors are
        // expanded to size 3. The three elements end up being $t, column 0, and column 1.
        int count = atoi (column.c_str ());
        for (int i = columnValues.size (); i <= count; i++)
        {
            columnValues.push_back (std::numeric_limits<float>::quiet_NaN ());
            columnMap[i-1] = i;
        }
    }
    int index = columnValues.size ();
    columnMap[column] = index;
    columnValues.push_back (0);
    if (! raw) columnMode.push_back (0);
    return index;
}

template<class T>
void
OutputHolder<T>::setMode (int index, const char * mode, const char * lineSeparator, const char * keySeparator)
{
    std::lock_guard<std::recursive_mutex> lock (mutexLine);

    if (! columnMode[index]) columnMode[index] = new std::map<String,String>;
    std::map<String,String> * result = columnMode[index];

    String rest = mode;
    String hint;
    while (! rest.empty ())
    {
        split (rest, lineSeparator, hint, rest);
        hint.trim ();
        String key;
        String value;
        split (hint, keySeparator, key, value);
        if (key == "timeScale"  ||  key == "xscale")
        {
            std::map<String,String> * c = columnMode[0];
            (*c)["scale"] = value;
        }
        else if (key == "scatter"  ||  key == "ymin"  ||  key == "ymax"  ||  key == "xmin"  ||  key == "xmax")
        {
            std::map<String,String> * c = columnMode[0];
            (*c)[key] = value;
        }
        else
        {
            if (key == "yscale") key = "scale";
            (*result)[key] = value;
        }
    }
}

template<class T>
T
#ifdef n2a_FP
OutputHolder<T>::trace (T now, const String & column, T valueFP, int exponent, const char * mode)
#else
OutputHolder<T>::trace (T now, const String & column, T value,                 const char * mode)
#endif
{
    std::lock_guard<std::recursive_mutex> lock (mutexLine);

    trace (now);

#   ifdef n2a_FP
    float value;
    if      (valueFP ==  NAN)      value =  std::numeric_limits<float>::quiet_NaN ();
    else if (valueFP ==  INFINITY) value =  std::numeric_limits<float>::infinity ();
    else if (valueFP == -INFINITY) value = -std::numeric_limits<float>::infinity ();
    else                           value = (float) valueFP * pow (2.0f, exponent);
#   endif

    int index = getColumnIndex (column);
    columnValues[index] = (float) value;
    if (mode  &&  ! columnMode[index]) setMode (index, mode);

#   ifdef n2a_FP
    return valueFP;
#   else
    return value;
#   endif
}

#ifdef n2a_FP

template<class T>
Matrix<T>
OutputHolder<T>::trace (T now, const String & column, const Matrix<T> & A, int exponent, const char * mode)
{
    std::lock_guard<std::recursive_mutex> lock (mutexLine);

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

    return A;
}

#else

template<class T>
Matrix<T>
OutputHolder<T>::trace (T now, const String & column, const Matrix<T> & A, const char * mode)
{
    std::lock_guard<std::recursive_mutex> lock (mutexLine);

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

    return A;
}

#endif

template<class T>
void
OutputHolder<T>::writeTrace ()
{
    std::lock_guard<std::recursive_mutex> lock (mutexLine);

    if (! traceReceived  ||  ! out) return;  // Don't output anything unless at least one value was set.

    const int count = columnValues.size ();
    const int last  = count - 1;

    // Write headers if new columns have been added
    if (count > columnsPrevious)
    {
        if (! raw)
        {
            std::vector<String> headers (count);
            for (auto & it : columnMap) headers[it.second] = it.first;

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
                if (header.find_first_of (" \t\",") != String::npos)
                {
                    (*out) << "\"";
                    (*out) << header.replace_all ("\"", "\"\"");
                    (*out) << "\"";
                }
                else
                {
                    (*out) << header;
                }
            }
            (*out) << std::endl;
        }
        columnsPrevious = count;
        writeModes ();
    }

    // Write values
    float NANf = std::numeric_limits<float>::quiet_NaN ();  // Necessary because "NAN" might be an integer.
    for (int i = 0; i <= last; i++)
    {
        float & c = columnValues[i];
        if (! std::isnan (c)) (*out) << c;
        if (i < last) (*out) << "\t";
        c = NANf;
    }
    (*out) << std::endl;

    traceReceived = false;
}

template<class T>
void
OutputHolder<T>::writeModes ()
{
    if (raw) return;  // "raw" shouldn't change, so no need to put in critical section.

    std::lock_guard<std::recursive_mutex> lock (mutexLine);

    std::ofstream mo (columnFileName.c_str ());
    mo << "N2A.schema=3\n";
    for (auto & it : columnMap)
    {
        int i = it.second;
        mo << i << ":" << it.first << "\n";
        auto mode = columnMode[i];
        if (! mode) continue;
        for (auto & nv : *mode) mo << " " << nv.first << ":" << nv.second << "\n";
    }
    // mo should automatically flush and close here
}

template<class T>
OutputHolder<T> *
outputHelper (const String & fileName, OutputHolder<T> * oldHandle)
{
    OutputHolder<T> * handle = (OutputHolder<T> *) SIMULATOR getHolder (fileName, oldHandle);
    if (! handle)
    {
        handle = new OutputHolder<T> (fileName);
        SIMULATOR holders.push_back (handle);
    }
    return handle;
}


#endif
