/*
Copyright 2018-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_holder_matrix_tcc
#define n2a_holder_matrix_tcc

#include "mymath.h"
#include "holder.h"
#include "runtime.h"   // For Event::exponent
#include "myendian.h"
#include "pugixml.hpp"

#include <fstream>
#include <algorithm>


// HolderMatrix --------------------------------------------------------------

template<class T>
HolderMatrix<T>::HolderMatrix (const String & fileName)
:   Holder (fileName)
{
}


// IteratorSkip --------------------------------------------------------------

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


// IteratorSparse ------------------------------------------------------------

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
:   HolderMatrix<T> (fileName)
{
    A = 0;
}

template<class T>
MatrixInput<T>::~MatrixInput ()
{
    if (A) delete A;
}

template<class T>
Matrix<T> *
#ifdef n2a_FP
loadNPY (std::istream & in, int exponent)
#else
loadNPY (std::istream & in)
#endif
{
    Matrix<T> * result = new Matrix<T>;

    // Read file identifier.
    in.ignore (6);  // Skip magic string.
    int major = in.get ();  // strictly-speaking, the value is uint8
    int minor = in.get ();

    // Read content description.
    uint32_t headerLength;
    if (major >= 2)
    {
        char buffer [4];
        in.read (buffer, 4);
        headerLength = * (uint32_t *) buffer;
    }
    else
    {
        char buffer [2];
        in.read (buffer, 2);
        headerLength = * (uint16_t *) buffer;
    }

    n2a::MVolatile header;
    String buffer;
    buffer.resize (headerLength);
    in.read ((char *) buffer.data (), headerLength);
    StringStreambuf hsb (buffer);
    std::istream hs (&hsb);
    n2a::JSON json;
    json.read (header, hs);

    n2a::MNode & shape = header.child ("shape");
    int rows    = shape.getOrDefault (1, 0);
    int columns = shape.getOrDefault (1, 1);
    int count   = rows * columns;
    result->resize (rows, columns);
    if (header.getFlag ("fortran_order"))
    {
        result->strideR_ = 1;
        result->strideC_ = rows;
    }
    else
    {
        result->strideR_ = columns;
        result->strideC_ = 1;
    }
    String descr = header.get ("descr");
    if (descr.empty ()) throw "NumPy matrix file is missing the 'descr' field";
    bool   le   = descr[0] == '<';  // little-endian; '>' = big-ending; '|' = not applicable
    String type = descr.substr (1);  // includes type and size
    int    size = 1;
    if (descr.size () > 2) size = descr[2] - '0';

    // Read data
    int bytes = count * size;
    buffer.resize (bytes);
    in.read ((char *) buffer.data (), bytes);
    T * b = &(*result)[0];

    // Convert byte order, if needed.
    if (BYTE_ORDER != (le ? LITTLE_ENDIAN : BIG_ENDIAN))
    {
        if (size == 8)
        {
            // Unfortunately, none of the standard (portable) conversion functions are applicable to both endians.
            // Thus, we do the swap explicitly. This is inefficient compared to a processor instruction,
            // which might have been available via a standard function.
            uint64_t * a = (uint64_t *) buffer.data ();
            for (int i = 0; i < count; i++) a[i] =
                (a[i] & 0x00000000000000FFull) << 56 |
                (a[i] & 0x000000000000FF00ull) << 40 |
                (a[i] & 0x0000000000FF0000ull) << 24 |
                (a[i] & 0x00000000FF000000ull) <<  8 |
                (a[i] & 0x000000FF00000000ull) >>  8 |
                (a[i] & 0x0000FF0000000000ull) >> 24 |
                (a[i] & 0x00FF000000000000ull) >> 40 |
                (a[i] & 0xFF00000000000000ull) >> 56;
        }
        else if (size == 4)
        {
            uint32_t * a = (uint32_t *) buffer.data ();
            for (int i = 0; i < count; i++) a[i] =
                (a[i] & 0x000000FFu) << 24 |
                (a[i] & 0x0000FF00u) <<  8 |
                (a[i] & 0x00FF0000u) >>  8 |
                (a[i] & 0xFF000000u) >> 24;
        }
        else if (size == 2)
        {
            uint16_t * a = (uint16_t *) buffer.data ();
            for (int i = 0; i < count; i++) a[i] =
                (a[i] & 0x00FFu) << 8 |
                (a[i] & 0xFF00u) >> 8;
        }
    }

#   ifdef na2_FP

    if (type == "f8")
    {
        double * a = (double *) buffer.data ();
        for (int i = 0; i < count; i++) b[i] = (T) convert (a[i], exponent);
    }
    else if (type == "f4")
    {
        float * a = (float *) buffer.data ();
        for (int i = 0; i < count; i++) b[i] = (T) convert (a[i], exponent);
    }
    else if (type == "i8")
    {
        int64_t * a = (int64_t *) buffer.data ();
        if (exponent >= 0)
        {
            for (int i = 0; i < count; i++) b[i] = (T) (a[i] >>  exponent);
        }
        else
        {
            for (int i = 0; i < count; i++) b[i] = (T) (a[i] << -exponent);
        }
    }
    else if (type == "i4")
    {
        int32_t * a = (int32_t *) buffer.data ();
        if (exponent >= 0)
        {
            for (int i = 0; i < count; i++) b[i] = (T) (a[i] >>  exponent);
        }
        else
        {
            for (int i = 0; i < count; i++) b[i] = (T) (a[i] << -exponent);
        }
    }
    else if (type == "i2")
    {
        int16_t * a = (int16_t *) buffer.data ();
        if (exponent >= 0)
        {
            for (int i = 0; i < count; i++) b[i] = (T) (a[i] >>  exponent);
        }
        else
        {
            for (int i = 0; i < count; i++) b[i] = (T) ((int32_t) a[i] << -exponent);
        }
    }
    else if (type == "i1")
    {
        int8_t * a = (int8_t *) buffer.data ();
        if (exponent >= 0)
        {
            for (int i = 0; i < count; i++) b[i] = (T) (a[i] >>  exponent);
        }
        else
        {
            for (int i = 0; i < count; i++) b[i] = (T) ((int32_t) a[i] << -exponent);
        }
    }
    else if (type == "u8")
    {
        uint64_t * a = (uint64_t *) buffer.data ();
        if (exponent >= 0)
        {
            for (int i = 0; i < count; i++) b[i] = (T) (a[i] >>  exponent);
        }
        else
        {
            for (int i = 0; i < count; i++) b[i] = (T) (a[i] << -exponent);
        }
    }
    else if (type == "u4")
    {
        uint32_t * a = (uint32_t *) buffer.data ();
        if (exponent >= 0)
        {
            for (int i = 0; i < count; i++) b[i] = (T) (a[i] >>  exponent);
        }
        else
        {
            for (int i = 0; i < count; i++) b[i] = (T) (a[i] << -exponent);
        }
    }
    else if (type == "u2")
    {
        uint16_t * a = (uint16_t *) buffer.data ();
        if (exponent >= 0)
        {
            for (int i = 0; i < count; i++) b[i] = (T) (a[i] >>  exponent);
        }
        else
        {
            for (int i = 0; i < count; i++) b[i] = (T) ((uint32_t) a[i] << -exponent);
        }
    }
    else if (type == "u1")
    {
        uint8_t * a = (uint8_t *) buffer.data ();
        if (exponent >= 0)
        {
            for (int i = 0; i < count; i++) b[i] = (T) (a[i] >>  exponent);
        }
        else
        {
            for (int i = 0; i < count; i++) b[i] = (T) ((uint32_t) a[i] << -exponent);
        }
    }
    else throw "Unhandled type in NumPy matrix";

#   else

    if (type == "f8")
    {
        double * a = (double *) buffer.data ();
        for (int i = 0; i < count; i++) b[i] = (T) a[i];
    }
    else if (type == "f4")
    {
        float * a = (float *) buffer.data ();
        for (int i = 0; i < count; i++) b[i] = (T) a[i];
    }
    else if (type == "i8")
    {
        int64_t * a = (int64_t *) buffer.data ();
        for (int i = 0; i < count; i++) b[i] = (T) a[i];
    }
    else if (type == "i4")
    {
        int32_t * a = (int32_t *) buffer.data ();
        for (int i = 0; i < count; i++) b[i] = (T) a[i];
    }
    else if (type == "i2")
    {
        int16_t * a = (int16_t *) buffer.data ();
        for (int i = 0; i < count; i++) b[i] = (T) a[i];
    }
    else if (type == "i1")
    {
        int8_t * a = (int8_t *) buffer.data ();
        for (int i = 0; i < count; i++) b[i] = (T) a[i];
    }
    else if (type == "u8")
    {
        uint64_t * a = (uint64_t *) buffer.data ();
        for (int i = 0; i < count; i++) b[i] = (T) a[i];
    }
    else if (type == "u4")
    {
        uint32_t * a = (uint32_t *) buffer.data ();
        for (int i = 0; i < count; i++) b[i] = (T) a[i];
    }
    else if (type == "u2")
    {
        uint16_t * a = (uint16_t *) buffer.data ();
        for (int i = 0; i < count; i++) b[i] = (T) a[i];
    }
    else if (type == "u1")
    {
        uint8_t * a = (uint8_t *) buffer.data ();
        for (int i = 0; i < count; i++) b[i] = (T) a[i];
    }
    else throw "Unhandled type in NumPy matrix";

#   endif

    return result;
}

template<class T>
Matrix<T> *
#ifdef n2a_FP
loadNPY (ZipFile & zip, const String & entryName, int exponent)
#else
loadNPY (ZipFile & zip, const String & entryName)
#endif
{
    String fileContents = zip.extract (entryName);
    StringStreambuf fsb (fileContents);
    std::istream fs (&fsb);

#   ifdef n2a_FP
    return loadNPY<T> (fs, exponent);
#   else
    return loadNPY<T> (fs);
#   endif
}

template<class T>
void
#ifdef n2a_FP
MatrixInput<T>::loadNPY (int exponent)
#else
MatrixInput<T>::loadNPY ()
#endif
{
    std::ifstream ifs (this->fileName.c_str (), std::ios::binary);
#   ifdef n2a_FP
    A = ::loadNPY<T> (ifs, exponent);
#   else
    A = ::loadNPY<T> (ifs);
#   endif
}

template<class T>
void
#ifdef n2a_FP
MatrixInput<T>::loadTextDense (int exponent)
#else
MatrixInput<T>::loadTextDense ()
#endif
{
    std::ifstream ifs (this->fileName.c_str ());

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
#                   ifdef n2a_FP
                    row.push_back (convert (buffer, exponent));
#                   else
                    row.push_back ((T) atof (buffer.c_str ()));
#                   endif
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
#                   ifdef n2a_FP
                    row.push_back (convert (buffer, exponent));
#                   else
                    row.push_back ((T) atof (buffer.c_str ()));
#                   endif
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
    Matrix<T> * D = new Matrix<T> (rows, columns);
    A = D;
    clear (*D);
    for (int r = 0; r < rows; r++)
    {
        std::vector<T> & row = temp[r];
        for (int c = 0; c < row.size (); c++)
        {
            (*D)(r,c) = row[c];
        }
    }
    if (transpose)
    {
        std::swap (D->rows_,    D->columns_);
        std::swap (D->strideR_, D->strideC_);
    }
}

template<class T>
void
#ifdef n2a_FP
MatrixInput<T>::loadTextSparse (T emptyValue, int exponent)
#else
MatrixInput<T>::loadTextSparse (T emptyValue)
#endif
{
    std::ifstream ifs (this->fileName.c_str ());
    String line;
    getline (ifs, line);  // Ignore "Sparse" line.

    MatrixSparse<T> * S = new MatrixSparse<T>;
    S->emptyValue = emptyValue;
    A = S;
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
#       ifdef n2a_FP
        T element = convert (line, exponent);
#       else
        T element = (T) atof (line.c_str ());
#       endif

        if (element) S->set (row, col, element);
    }
}

template<class T>
void
#ifdef n2a_FP
MatrixInput<T>::loadSonataSpikes (const String & population, T emptyValue, int exponent)
#else
MatrixInput<T>::loadSonataSpikes (const String & population, T emptyValue)
#endif
{
    ReadSonataSpikes<T> rs (population);
    std::ifstream ifs (this->fileName.c_str ());
    rs.parse (ifs);
    rs.S->emptyValue = emptyValue;
    A = rs.S;
}

template<class T>
MatrixAbstract<T> *
MatrixInput<T>::getMatrix (const char * resource)
{
    return A;
}

template<class T>
HolderMatrix<T> *
#ifdef n2a_FP
matrixHelper (const String & fileName, const String & key, const String & value, T emptyValue, int exponent, HolderMatrix<T> * oldHandle)
#else
matrixHelper (const String & fileName, const String & key, const String & value, T emptyValue,               HolderMatrix<T> * oldHandle)
#endif
{
    bool isHDF    =  key == "hdf";
    bool isSheet  =  key == "anchor";
    bool isNPZ    =  key == "npy"  ||  key == "csr"  ||  key == "csc";
    bool isEdges  =  key == "sonataEdges";
    bool isSpikes =  key == "sonataSpikes";

    // Determine holder key used to store/save this object.
    // Exact form depends on type of file.
    // At this point, we only care about what the user explicitly stated.
    // We don't create an extended key for a file that is later discovered to be of a type that uses one.
    String holderKey;
    if (isHDF  ||  isNPZ  ||  isEdges  ||  isSpikes  ||  isSheet)
    {
        // In the case of sonataEdges, value can be empty string.
        holderKey = fileName + "|" + value;
    }
    else
    {
        holderKey = fileName;
    }

    HolderMatrix<T> * handle = (HolderMatrix<T> *) SIMULATOR getHolder (holderKey, oldHandle);
    if (handle) return handle;

    // Triage based on file suffix and magic.

    bool isXSV   = false;
    bool isExcel = false;
    bool isNPY   = false;
    String lowerFN = fileName.toLowerCase ();
    if      (lowerFN.ends_with (".npz" )) isNPZ   = true;
    else if (lowerFN.ends_with (".npy" )) isNPY   = true;
    else if (lowerFN.ends_with (".csv" )) isXSV   = true;
    else if (lowerFN.ends_with (".xlsx")) isExcel = true;
    if (isXSV  ||  isExcel) isSheet = true;

    ZipFile zip;  // RAII, so the zip will be closed at the end of this function, if it is ever opened.
    if (! (isHDF  ||  isNPY  ||  isNPZ  ||  isEdges  ||  isSpikes  ||  isSheet))  // type is not yet identified
    {
        // Probe if the file is ZIP
        if (ZipFile::probe (fileName))
        {
            // Could be either Excel or NPZ, so probe further.
            zip.open (fileName);
            if (zip.exists ("xl/_rels/workbook.xml.rels")) isExcel = true;
            else                                           isNPZ   = true;  // Don't know this for sure, but it's the only option we have.
        }
    }

    if (isHDF)
    {
#       ifndef HAVE_HDF
        throw "HDF support is not available because path to library was not specified.";
#       else

        TableHDF<T> * table = new TableHDF<T> (fileName, value, emptyValue);  // Generates same holderKey as above.
        SIMULATOR holders.push_back (table);
#       ifdef n2a_FP
        table->exponent = exponent;
#       endif // n2a_FP
        return table;

#       endif // HAVE_HDF
    }
    else if (isSheet)
    {
        TableSheet<T> * sheet = new TableSheet<T> (fileName, emptyValue);
        SIMULATOR holders.push_back (sheet);
#       ifdef n2a_FP
        sheet->exponent = exponent;
#       endif
        sheet->load ();
        return sheet;
    }

    // All other forms will use a MatrixInput object.
    MatrixInput<T> * mi = new MatrixInput<T> (holderKey);
    SIMULATOR holders.push_back (mi);

    if (isNPZ)
    {
        if (! zip.isOpen ()) zip.open (fileName);

        String keyDetected   = key;
        String valueDetected = value;
        if (keyDetected.empty ())  // Type/location of matrix is unspecified.
        {
            int count = zip.entryCount ();
            for (int i = 0; i < count; i++)
            {
                String entryName = zip.entryName (i);
                String lower = entryName.toLowerCase ();
                if (lower.ends_with ("indptr.npy"))  // Higher priority on compressed matrix.
                {
                    String prefix = entryName.substr (0, entryName.size () - 10);  // length("indptr.npy") is 10
                    if (zip.exists (prefix + "shape.npy")  &&  zip.exists (prefix + "data.npy")  &&  zip.exists (prefix + "indices.npy"))
                    {
                        keyDetected   = "csr";  // reasonable default
                        valueDetected = prefix;
                        if (zip.exists (prefix + "format.npy"))
                        {
                            String fileContents = zip.extract (prefix + "format.npy");
                            if (fileContents.find ("csc") != String::npos) keyDetected = "csc";
                        }
                        break;
                    }
                }
                if (lower.ends_with (".npy"))
                {
                    if (! keyDetected.empty ()) continue;  // Retain the first matrix found.
                    keyDetected   = "npy";
                    valueDetected = entryName;
                }
            }
            if (keyDetected.empty ()) throw "Can't find NPY inside NPZ";
        }

        if (keyDetected == "npy")  // Single matrix file inside ZIP file.
        {
            if (! valueDetected.toLowerCase ().ends_with (".npy")) valueDetected += ".npy";
#           ifdef n2a_FP
            mi->A = loadNPY<T> (zip, valueDetected, exponent);
#           else
            mi->A = loadNPY<T> (zip, valueDetected);
#           endif
        }
        else  // keyDetected == "csc"  ||  keyDetected == "csr"
        {
            // "shape" may need to be a 64-bit integer matrix in some cases.
#           ifdef n2a_FP
            Matrix<int> * shape = loadNPY<int> (zip, valueDetected + "shape.npy", exponent);
#           else
            Matrix<int> * shape = loadNPY<int> (zip, valueDetected + "shape.npy");
#           endif
            if (shape->rows () > 2)
            {
                delete shape;
                throw "Only 2D sparse matrices are supported.";
            }

#           ifdef n2a_FP
            Matrix<T> *   data    = loadNPY<T>   (zip, valueDetected + "data.npy",    exponent);
            Matrix<int> * indices = loadNPY<int> (zip, valueDetected + "indices.npy", exponent);
            Matrix<int> * indptr  = loadNPY<int> (zip, valueDetected + "indptr.npy",  exponent);
#           else
            Matrix<T> *   data    = loadNPY<T>   (zip, valueDetected + "data.npy");
            Matrix<int> * indices = loadNPY<int> (zip, valueDetected + "indices.npy");
            Matrix<int> * indptr  = loadNPY<int> (zip, valueDetected + "indptr.npy");
#           endif

            int rows = (*shape)[0];
            int cols = (*shape)[1];
            MatrixSparse<T> * S = new MatrixSparse<T> (rows, cols);
            S->emptyValue = emptyValue;
            mi->A = S;

            if (keyDetected == "csr")
            {
                for (int r = 0; r < rows; r++)
                {
                    int i   = (*indptr)[r];
                    int end = (*indptr)[r + 1];
                    for (; i < end; i++)
                    {
                        int c = (*indices)[i];
                        S->set (r, c, (*data)[i]);
                    }
                }
            }
            else  // csc
            {
                for (int c = 0; c < cols; c++)
                {
                    int i   = (*indptr)[c];
                    int end = (*indptr)[c + 1];
                    for (; i < end; i++)
                    {
                        int r = (*indices)[i];
                        S->set (r, c, (*data)[i]);
                    }
                }
            }
            delete shape;
            delete data;
            delete indices;
            delete indptr;
        }
    }
    else if (isNPY)
    {
#       ifdef n2a_FP
        mi->loadNPY (exponent);
#       else
        mi->loadNPY ();
#       endif
    }
    else if (isEdges)
    {
#       ifdef n2a_FP
        mi->A = new MatrixSonataEdgesXSV<T> (fileName, value, emptyValue, exponent);
#       else
        mi->A = new MatrixSonataEdgesXSV<T> (fileName, value, emptyValue);
#       endif
    }
    else if (isSpikes)
    {
#       ifdef n2a_FP
        mi->loadSonataSpikes (value, emptyValue, exponent);  // exponent should be same as Event::exponent, because these are time values.
#       else
        mi->loadSonataSpikes (value, emptyValue);
#       endif
    }
    else  // Plain text format.
    {
        std::ifstream ifs (fileName.c_str ());
        if (! ifs.good ()) fprintf (stderr, "Failed to open matrix file: %s\n", fileName.c_str ());
        String line;
        getline (ifs, line);
        ifs.close ();
        line = line.trim ().toLowerCase ();  // In particular, get rid of trailing line-ending character in some cases.

#       ifdef n2a_FP
        if (line == "sparse") mi->loadTextSparse (emptyValue, exponent);  // Homegrown sparse matrix format
        else                  mi->loadTextDense  (            exponent);
#       else
        if (line == "sparse") mi->loadTextSparse (emptyValue);
        else                  mi->loadTextDense ();
#       endif
    }

    if (mi->A->rows () == 0  ||  mi->A->columns () == 0)
    {
        fprintf (stderr, "Ill-formed matrix in file: %s\n", fileName.c_str ());
        delete mi->A;
        mi->A = new Matrix<T> (1, 1);
        clear (*mi->A); // set to 0
    }
    return mi;
}

// Notice that this is not part of MatrixInput. It is a global function.
template<class T>
IteratorNonzero<T> *
getIterator (MatrixAbstract<T> * A)
{
    uint32_t ID = A->classID ();
    if (ID & MatrixID)                return new IteratorSkip<T>           ((Matrix<T> *)               A);
    if (ID & MatrixSparseID)          return new IteratorSparse<T>         ((MatrixSparse<T> *)         A);
    if (ID & MatrixSparseRegionID)    return new IteratorSparseRegion<T>   ((MatrixSparseRegion<T> *)   A);
    if (ID & MatrixSonataEdgesXSV_ID) return new IteratorSonataEdgesXSV<T> ((MatrixSonataEdgesXSV<T> *) A);
#   ifdef HAVE_HDF
    if (ID & MatrixSonataEdgesHDF_ID) return new IteratorSonataEdgesHDF<T> ((MatrixSonataEdgesHDF<T> *) A);
    if (ID & TableHDF_ID)             return new IteratorNonzeroHDF<T>     ((TableHDF<T> *)             A);
#   endif
    throw "Unexpected matrix type when setting up sparse iterator.";
}


// ReadSonataSpikes ----------------------------------------------------------

template<class T>
ReadSonataSpikes<T>::ReadSonataSpikes (const String & population)
:   population (population)
{
    S          = new MatrixSparse<T> ();  // No need to dispose of this, because it will be taken over by a MatrixInput object.
    gotColumns = false;
    lastID     = -1;
}

template<class T>
bool
ReadSonataSpikes<T>::processLine (std::vector<String> & parts)
{
    if (! gotColumns)
    {
        auto it = std::find (parts.begin (), parts.end (), "timestamps");
        colTime = it - parts.begin ();
        if (colTime >= parts.size ()) colTime = -1;

        it = std::find (parts.begin (), parts.end (), "population");
        colPopulation = it - parts.begin ();
        if (colPopulation >= parts.size ()) colPopulation = -1;

        it = std::find (parts.begin (), parts.end (), "node_ids");
        colID = it - parts.begin ();
        if (colID >= parts.size ()) colID = -1;

        gotColumns =  colTime >= 0  &&  colPopulation >= 0  &&  colID >= 0;
        if (! gotColumns) return false;
    }

    //   Extract column data and store in matrix.
    if (parts[colPopulation] != population) return true;
    double temp = atof (parts[colTime].c_str ());
#   ifdef n2a_FP
    T time = (T) convert (temp, exponent);
#   else
    T time = (T) temp;
#   endif
    int ID = atoi (parts[colID].c_str ());  // Should be uint64_t, but MatrixSparse doesn't currently support that.
    if (ID != lastID)
    {
        // We assume that IDs are contiguous in the file, and that timestamps increase monotonically.
        lastID = ID;
        eventCount = 0;
    }
    S->set (eventCount++, ID, time);
    return true;
}


// Table ---------------------------------------------------------------------

template<class T>
Table<T>::Table (const String & fileName, T emptyValue)
:   HolderMatrix<T> (fileName),
    emptyValue (emptyValue)
{
}

template<class T>
void
Table<T>::parse (const String & anchor)
{
    throw "anchor keyword is not supported for given file type";
}

template<class T>
int
Table<T>::rowsInColumn () const
{
    throw "rowsInColumn keyword is not supported for given file type";
}

template<class T>
int
Table<T>::columnsInRow () const
{
    throw "columnsInRow keyword is not supported for given file type";
}

template<class T>
int
Table<T>::rows (const String & anchor)
{
    std::lock_guard<std::mutex> lock (mutexAnchor);
    parse (anchor);
    return rows ();
}

template<class T>
int
Table<T>::columns (const String & anchor)
{
    std::lock_guard<std::mutex> lock (mutexAnchor);
    parse (anchor);
    return columns ();
}

template<class T>
int
Table<T>::rowsInColumn (const String & anchor)
{
    std::lock_guard<std::mutex> lock (mutexAnchor);
    parse (anchor);
    return rowsInColumn ();
}

template<class T>
int
Table<T>::columnsInRow (const String & anchor)
{
    std::lock_guard<std::mutex> lock (mutexAnchor);
    parse (anchor);
    return columnsInRow ();
}

template<class T>
T
Table<T>::get (const String & anchor, const int row, const int column)
{
    std::lock_guard<std::mutex> lock (mutexAnchor);
    parse (anchor);
    return get (row, column);
}

template<class T>
String
Table<T>::getString (const String & anchor, const int row, const int column)
{
    std::lock_guard<std::mutex> lock (mutexAnchor);
    parse (anchor);
    return getString (row, column);
}


// Sheet ---------------------------------------------------------------------

template<class T>
Sheet<T>::Sheet ()
{
    numbers = 0;
    strings = 0;
}

template<class T>
Sheet<T>::~Sheet ()
{
    if (numbers) delete numbers;
    if (strings) delete strings;
}


// TableSheet ----------------------------------------------------------------

String
extractSI (const pugi::xml_node & si)
{
    String result;
    for (auto n : si)
    {
        String name = n.name ();
        if (name == "t")  // simple text element
        {
            result += n.child_value ();
        }
        else if (name == "r")  // rich text element
        {
            for (auto m : n) if (String (m.name ()) == "t") result += m.child_value ();
        }
    }
    return result;
}

template<class T>
TableSheet<T>::TableSheet (const String & fileName, T emptyValue)
:   Table<T> (fileName, emptyValue)
{
}

template<class T>
TableSheet<T>::~TableSheet ()
{
    for (auto & a : matrices) delete a.second;
    for (auto & s : wb)       delete s.second;
}

template<class T>
void
TableSheet<T>::load ()
{
    if (ZipFile::probe (this->fileName)) loadExcel ();  // ZIP, so treat as Excel.
    else                                 loadXSV ();    // Not ZIP, so treat as XSV.
}

template<class T>
void
TableSheet<T>::loadXSV ()
{
    ws = new Sheet<T> ();
    wb[""] = ws;
    first = ws;
    MatrixSparse<T> * N = new MatrixSparse<T> ();
    N->emptyValue = this->emptyValue;
    ws->numbers = N;
    ws->strings = new MatrixSparse<int> ();
    ar = 0;
    ac = 0;

    LoadTableXSV<T> parser;
    parser.table = this;
#   ifdef n2a_FP
    parser.exponent = exponent;
#   endif
    std::ifstream ifs (this->fileName.c_str ());
    parser.parse (ifs);

    // Check fill-in and possibly convert to dense
    int Nrows = ws->numbers->rows ();
    int Ncols = ws->numbers->columns ();
    int Srows = ws->strings->rows ();
    int Scols = ws->strings->columns ();
    if ((double) parser.fillN / (Nrows * Ncols) > fillThreshold)
    {
        MatrixAbstract<T> * temp = ws->numbers;
        ws->numbers = new Matrix<T> (*temp);  // densify
        delete temp;
    }
    if ((double) parser.fillS / (Srows * Scols) > fillThreshold)
    {
        MatrixAbstract<int> * temp = ws->strings;
        ws->strings = new Matrix<int> (*temp);
        delete temp;
    }
}

template<class T>
void
TableSheet<T>::loadExcel ()
{
    ZipFile zip (this->fileName);  // RAII

    // Read workbook relationship file to determine paths to sheets, shared strings and styles.
    std::map<String,String> IDtarget;
    String sharedStringsPath;
    String stylesPath;
    pugi::xml_document workbookRels;
    String fileContents = zip.extract ("xl/_rels/workbook.xml.rels");
    workbookRels.load_string (fileContents.c_str (), pugi::parse_default | pugi::parse_ws_pcdata);
    for (auto n : workbookRels.document_element ())
    {
        String Type = n.attribute ("Type").value ();
        if (Type.ends_with ("/worksheet"))
        {
            String Id = n.attribute ("Id").value ();
            String Target = "xl/";
            Target += n.attribute ("Target").value ();
            IDtarget[Id] = Target;
        }
        else if (Type.ends_with ("/sharedStrings"))
        {
            sharedStringsPath = "xl/";
            sharedStringsPath += n.attribute ("Target").value ();
        }
        else if (Type.ends_with ("/styles"))
        {
            stylesPath = "xl/";
            stylesPath += n.attribute ("Target").value ();
        }
    }

    // Load shared strings
    if (! sharedStringsPath.empty ())
    {
        pugi::xml_document sharedStrings;
        fileContents = zip.extract (sharedStringsPath);
        sharedStrings.load_string (fileContents.c_str (), pugi::parse_default | pugi::parse_ws_pcdata);
        auto sst = sharedStrings.document_element ();
        int uniqueCount = sst.attribute ("uniqueCount").as_int ();
        strings.reserve (uniqueCount);
        for (auto si : sst) strings.push_back (extractSI (si));
    }

    // Determine date styles
    std::set<int> dateStyles;  // collection of all style numbers that should be treated as date
    if (! stylesPath.empty ())
    {
        pugi::xml_document styles;
        fileContents = zip.extract (stylesPath);
        styles.load_string (fileContents.c_str (), pugi::parse_default | pugi::parse_ws_pcdata);
        auto styleSheet = styles.document_element ();
        int styleNumber = 0;
        for (auto xf : styleSheet.child ("cellXfs"))
        {
            int id = xf.attribute ("numFmtId").as_int ();
            if (id >= 14  &&  id <= 22  ||  id >= 45  &&  id <= 47) dateStyles.insert (styleNumber);
            styleNumber++;
        }
    }
    std::set<int>::iterator dateStylesEnd = dateStyles.end ();

    // Scan workbook for sheets
    pugi::xml_document workbook;
    fileContents = zip.extract ("xl/workbook.xml");
    workbook.load_string (fileContents.c_str (), pugi::parse_default | pugi::parse_ws_pcdata);
    first = 0;
    for (auto n : workbook.document_element ().child ("sheets"))
    {
        String rid = n.attribute ("r:id").value ();
        if (IDtarget.find (rid) == IDtarget.end ()) continue;
        String name = n.attribute ("name").value ();
        String target = IDtarget[rid];

        // Process worksheet
        // We could try to read the dimension element, but it is not reliable
        // (not required to be present, and not always formatted correctly).
        // Thus, the only safe way to load a spreadsheet is with sparse matrices.
        // There are several delicate tradeoffs between time and space here.
        // We don't want to lock down more memory than necessary. OTOH, it is a
        // waste of time to convert to dense matrix if each element is accessed
        // only once during a simulation. Here it is impossible to know how
        // all that will play out, so we use a simple heuristic based on fill-in
        // to decide whether to covnert to dense matrix after the load finishes.
        ws = new Sheet<T>;
        wb[name] = ws;
        if (first == 0) first = ws;
        MatrixSparse<T>   * N = new MatrixSparse<T>;
        MatrixSparse<int> * S = new MatrixSparse<int>;
        N->emptyValue = this->emptyValue;
        ws->numbers = N;
        ws->strings = S;
        int fillN = 0;
        int fillS = 0;

        pugi::xml_document worksheet;
        fileContents = zip.extract (target);
        worksheet.load_string (fileContents.c_str (), pugi::parse_default | pugi::parse_ws_pcdata);
        for (auto row : worksheet.document_element ().child ("sheetData"))
        {
            for (auto c : row)
            {
                String t = c.attribute ("t").value ();
                if (t == "e") continue;
                parseA1 (c.attribute ("r").value ());  // result stored in ar and ac
                if (t == "s")
                {
                    int index = atoi (c.child_value ("v"));
                    if (strings[index].empty ()) continue;
                    S->set (ar, ac, index+1);  // Offset index by 1, so the 0 can represent empty string.
                    fillS++;
                }
                else if (t == "str")
                {
                    String v = c.child_value ("v");
                    v.trim ();
                    if (v.empty ()) continue;
                    strings.push_back (v);
                    S->set (ar, ac, strings.size ());  // by putting this call after the push_back(), we get 1-based index
                    fillS++;
                }
                else if (t == "inlineStr")
                {
                    String si = extractSI (c.child ("si"));
                    if (si.empty ()) continue;
                    strings.push_back (si);
                    S->set (ar, ac, strings.size ());
                    fillS++;
                }
                else  // all remaining types should be numeric
                {
                    // Dates are stored internally as number of days since December 31, 1899.
                    // Day 25569 is start of Unix epoch, January 1, 1970.
                    // I believe that day number includes leap days, so all we need to do is multiply by 86400.
                    // There are more subtle elements of horology to consider, but this should be good enough.

                    // The difficulty is identifying a date cell. The only way is to check style (attribute "s").
                    // See https://www.brendanlong.com/the-minimum-viable-xlsx-reader.html
                    // At a minimum, we could check all pre-defined date styles: 14-22, 45-47
                    // It appears that MS Excel won't store negative date numbers. Instead, the value is stored as a string.

                    double d = atof (c.child_value ("v"));
                    int s = c.attribute ("s").as_int (-1);
                    if (dateStyles.find (s) != dateStylesEnd) d = (d - 25569) * 86400;  // Convert from Excel time to Unix time.
                    if (d == 0) continue;  // should we also check for NAN?
#                   ifdef n2a_FP
                    N->set (ar, ac, convert (d, exponent));
#                   else
                    N->set (ar, ac, (T) d);
#                   endif
                    fillN++;
                }
            }
        }

        // Check fill-in and possibly convert to dense
        int Nrows = N->rows ();
        int Ncols = N->columns ();
        int Srows = S->rows ();
        int Scols = S->columns ();
        ws->rows    = std::max (Nrows, Srows);
        ws->columns = std::max (Ncols, Scols);
        if ((double) fillN / (Nrows * Ncols) > fillThreshold)
        {
            ws->numbers = new Matrix<T> (*N);
            delete N;
        }
        if ((double) fillS / (Srows * Scols) > fillThreshold)
        {
            ws->strings = new Matrix<int> (*S);
            delete S;
        }
    }

    ws = first;
    ar = 0;
    ac = 0;
}

template<class T>
void
TableSheet<T>::parse (const String & cell)
{
    if (cell == this->cell) return;

    String sheetName;
    String coordinates;
    int pos = cell.find_first_of ('!');
    if (pos == String::npos)
    {
        coordinates = cell;
    }
    else
    {
        sheetName   = cell.substr (0, pos);
        coordinates = cell.substr (pos + 1);
    }
    parseA1 (coordinates);

    ws = first;  // the default if sheetName is empty or not found
    if (! sheetName.empty ())
    {
        typename std::map<String, Sheet<T> *>::iterator it = wb.find (sheetName);
        if (it != wb.end ()) ws = it->second;
    }

    this->cell = cell;
}

template<class T>
void
TableSheet<T>::parseA1 (const String & coordinates)
{
    ac = 0;  // Must start at 0 for column converter to work correctly.
    if (coordinates.empty ())
    {
        ar = 0;
        return;
    }

    int pos = 0;
    int length = coordinates.size ();
    for (; pos < length; pos++)
    {
        char c = coordinates[pos];
        if (c >= 97) c &= 0xDF;  // convert to upper case by clearing bit 5
        if (c < 'A') break;
        ac = ac * 26 + c - 'A' + 1;
    }
    ac--;
    ar = atoi (coordinates.substr (pos).c_str ());
    if (ar > 0) ar--;  // Cell addresses are usually 1-based, so need to convert to 0-based.
}

template<class T>
int
TableSheet<T>::rows () const
{
    return std::max (0, ws->rows - ar);
}

template<class T>
int
TableSheet<T>::columns () const
{
    return std::max (0, ws->columns - ac);
}

template<class T>
int
TableSheet<T>::rowsInColumn () const
{
    int result = 0;
    for (int r = ar; r < ws->rows; r++)
    {
        if ((*ws->strings)(r,ac) == 0  &&  (*ws->numbers)(r,ac) == 0) break;
        result++;
    }
    return result;
}

template<class T>
int
TableSheet<T>::columnsInRow () const
{
    int result = 0;
    for (int c = ac; c < ws->columns; c++)
    {
        if ((*ws->strings)(ar,c) == 0  &&  (*ws->numbers)(ar,c) == 0) break;
        result++;
    }
    return result;
}

template<class T>
int
TableSheet<T>::columnIndex (const String & columnName)
{
    if (ws->columnMap.empty ())
    {
        for (int c = 0; c < ws->columns; c++)
        {
            int stringIndex = (*ws->strings)(0,c);
            if (stringIndex == 0) ws->columnMap[""                    ] = c;
            else                  ws->columnMap[strings[stringIndex-1]] = c;
        }
    }
    auto it = ws->columnMap.find (columnName);
    if (it == ws->columnMap.end ()) return -1;
    return it->second;
}

template<class T>
int
TableSheet<T>::rowIndex (const int keyColumn, const String & keyValue)
{
    if (ws->index.empty ())
    {
        if (ws->columnMap.empty ())  // No column headers, so use row 0 as well as the others. TODO: Need a better way to detect presence of column headers. This is unreliable in multiple ways.
        {
            for (int i = 0; i < ws->rows; i++) ws->index[i] = i;
        }
        else  // Column headers, so skip row 0.
        {
            for (int i = 1; i < ws->rows; i++) ws->index[i - 1] = i;
        }

        std::sort (ws->index.begin (), ws->index.end (), [&](int t1, int t2)
        {
            // This implements M sort order, just because it's the most rational way to handle mixed types.
            int i1 = (*ws->strings)(t1,keyColumn);
            int i2 = (*ws->strings)(t2,keyColumn);
            if (i1 == 0)  // t1 is a number
            {
                if (i2 == 0) return (*ws->numbers)(t1,keyColumn) < (*ws->numbers)(t2,keyColumn);  // t2 is a number
                else         return true;  // t2 is a string; number < string
            }
            else  // t1 is a string
            {
                if (i2 == 0) return false;  // t2 is a number; string > number
                else         return strings[i1 - 1] < strings[i2 - 1];  // t2 is a string
            }
        });
    }

    // Do binary search on indirect values.
    const char * start = keyValue.c_str ();
    char * end;
    double temp  = strtod (start, &end);
    bool   valid =  end - start == keyValue.size ();
#   ifdef n2a_FP
    T keyNumber = convert (temp, exponent);
#   else
    T keyNumber = (T) temp;
#   endif
    auto it = std::lower_bound (ws->index.begin (), ws->index.end (), -1, [&](int t1, int t2)  // -1 is a pseudo-row that contains the keyValue.
    {
        // t1 is a row number for the table, not a position in the index.
        // t2 is always the target value (-1, aka keyValue)
        int i = (*ws->strings)(t1,keyColumn);
        if (i == 0)  // t1 is a number
        {
            if (valid) return (*ws->numbers)(t1,keyColumn) < keyNumber;  // t2 is a number
            else       return true;  // t2 is a string. number < string.
        }
        else  // t1 is a string
        {
            // TODO: Should we treat "valid" as indicating that t2 is truly a number?
            return strings[i-1] < keyValue;
        }
    });
    if (it == ws->index.end ()) return -1;
    return *it;
}

template<class T>
T
TableSheet<T>::get (const int row, const int column) const
{
    int r = ar + row;
    int c = ac + column;
    MatrixAbstract<T> * A = ws->numbers;
    if (r < 0  ||  row >= A->rows ()  ||  column < 0  ||  column >= A->columns ()) return (T) 0;
    return (*A)(r,c);
}

template<class T>
String
TableSheet<T>::getString (const int row, const int column) const
{
    int r = ar + row;
    int c = ac + column;
    MatrixAbstract<int> * A = ws->strings;
    if (r < 0  ||  row >= A->rows ()  ||  column < 0  ||  column >= A->columns ()) return "";
    int index = (*A)(r,c);
    if (index > 0) return strings[index-1];  // back to 0-based index

    // No string, so try returning number.
    MatrixAbstract<T> * B = ws->numbers;
    if (r < 0  ||  row >= B->rows ()  ||  column < 0  ||  column >= B->columns ()) return "";
    T value = (*B)(r,c);
    if (! value) return "";
    return value;  // Converts number to string.
}

template<class T>
MatrixAbstract<T> *
TableSheet<T>::getMatrix (const char * resource)
{
    String key = resource ? resource : "";
    auto it = matrices.find (key);
    if (it != matrices.end ()) return it->second;

    std::lock_guard<std::mutex> lock (this->mutexAnchor);
    if (resource) parse (key);
    MatrixAbstract<T> * A = ws->numbers;
    if (ar == 0  &&  ac == 0) return A;  // Don't save in matrices, because A is already saved in ws.

    // Create a region
    if (A->classID () & MatrixSparseID)
    {
        MatrixSparse<T> * S = (MatrixSparse<T> *) A;
        MatrixSparseRegion<T> * result = new MatrixSparseRegion<T> (*S, ar, ac);
        result->emptyValue = this->emptyValue;
        matrices[key] = result;
        return result;
    }
    else  // dense matrix
    {
        Matrix<T> * D = (Matrix<T> *) A;
        Matrix<T> * result = new Matrix<T>;
        *result = region (*D, ar, ac);
        matrices[key] = result;
        return result;
    }
}

template<class T>
TableSheet<T> *
#ifdef n2a_FP
tableHelperSheet (const String & fileName, int exponent, TableSheet<T> * oldHandle)
#else
tableHelperSheet (const String & fileName,               TableSheet<T> * oldHandle)
#endif
{
    TableSheet<T> * handle = (TableSheet<T> *) SIMULATOR getHolder (fileName, oldHandle);
    if (! handle)
    {
        handle = new TableSheet<T> (fileName, (T) 0);
        SIMULATOR holders.push_back (handle);
#       ifdef n2a_FP
        handle->exponent = exponent;
#       endif
        handle->load ();
    }
    return handle;
}


// LoadTableXSV --------------------------------------------------------------

template<class T>
LoadTableXSV<T>::LoadTableXSV ()
{
    fillN = 0;
    fillS = 0;
}

template<class T>
bool
LoadTableXSV<T>::processLine (std::vector<String> & parts)
{
    int count = parts.size ();
    for (int c = 0; c < count; c++)
    {
        String & temp = parts[c];
        temp.trim ();
        if (temp.empty ()) continue;

        // First try to interpret as number. On failure, store as string.
        const char * begin = temp.c_str ();
        char * end;
        double d = strtod (begin, &end);
        if (end - begin < temp.size ())  // Conversion failed, or more accurately, not every character was converted.
        {
            table->strings.push_back (std::move (temp));
            int stringIndex = table->strings.size ();
            ((MatrixSparse<int> *) table->ws->strings)->set (table->ws->rows, c, stringIndex);
            fillS++;
        }
        else  // Successfully converted the entire string to a number.
        {
#           ifdef n2a_FP
            ((MatrixSparse<T> *) table->ws->numbers)->set (table->ws->rows, c, convert (d, exponent));
#           else
            ((MatrixSparse<T> *) table->ws->numbers)->set (table->ws->rows, c, d);
#           endif
            fillN++;
        }
    }
    if (count)
    {
        table->ws->rows++;
        table->ws->columns = std::max (table->ws->columns, count);
    }
    // else ignore blank lines.
    return true;
}


// IteratorSparseRegion ------------------------------------------------------

template<class T>
IteratorSparseRegion<T>::IteratorSparseRegion (MatrixSparseRegion<T> * S)
:   S (S)
{
    this->row    = 0;
    this->column = 0;
    this->value  = 0;

    if (S->columns_ > 0)
    {
        int actualColumn = S->ac;
        it  = (*S->data)[actualColumn].begin ();
        end = (*S->data)[actualColumn].end ();
    }
}

template<class T>
bool
IteratorSparseRegion<T>::next ()
{
    if (this->column >= S->columns_) return false;
    while (true)
    {
        if (it != end)
        {
            this->row = it->first - S->ar;
            if (this->row < 0)
            {
                it++;
                continue;
            }
            // cell is in range
            this->value = it->second;
            it++;
            return true;
        }
        if (++this->column >= S->columns_) return false;
        int actualColumn = this->column + S->ac;
        it  = (*S->data)[actualColumn].begin ();
        end = (*S->data)[actualColumn].end ();
    }
}


// MatrixSonataEdgesXSV ------------------------------------------------------

template<class T>
#ifdef n2a_FP
MatrixSonataEdgesXSV<T>::MatrixSonataEdgesXSV (const String & fileName, const String & attribute, T emptyValue, int exponent)
#else
MatrixSonataEdgesXSV<T>::MatrixSonataEdgesXSV (const String & fileName, const String & attribute, T emptyValue)
#endif
:   attribute (attribute),
    emptyValue (emptyValue)
{
    input       = 0;
    haveColumns = false;
    row         = 0;

    if (attribute.empty ())  // The main iterator.
    {
        input = new InputXSV<T> (fileName);
#       ifdef n2a_FP
        input->exponent    = exponent;
        input->exponentRow = 0;
#       endif
        track = this;
        // After ctor, this object will stashed as a MatrixInput in Simulator::holders under fileName+"|"
    }
    else  // An attribute that tracks with main iterator.
    {
        // Retrieve main iterator.
        track = 0;
        String key = fileName + "|";  // No attribute appended for main key.
        MatrixInput<T> * mi = (MatrixInput<T> *) SIMULATOR getHolder (key, 0);
        if (mi) track = (MatrixSonataEdgesXSV<T> *) mi->A;
        if (! track) throw "Attempt to create SONATA edge attribute matrix before sparse iterator is created.";

        input = track->input;
    }
}

template<class T>
MatrixSonataEdgesXSV<T>::~MatrixSonataEdgesXSV ()
{
    if (input) delete input;
}

template<class T>
uint32_t
MatrixSonataEdgesXSV<T>::classID () const
{
    return MatrixSonataEdgesXSV_ID;
}

template<class T>
T
MatrixSonataEdgesXSV<T>::get (const int row, const int column) const
{
    if (attribute.empty ()) return (T) 1;  // If attribute is absent, we assume boolean matrix. In that case, always return 1, because this function should only be called for existent elements.

    if (! haveColumns)
    {
        MatrixSonataEdgesXSV<T> * me = const_cast<MatrixSonataEdgesXSV<T>*> (this);  // Remove const so we can modify some variables regarding column detection.

        auto it = input->columnMap.find (attribute);
        if (it == input->columnMap.end ())
        {
            me->colAttribute = -1;
            fprintf (stderr, "ERROR: attribute column missing from edges file: %s\n", attribute.c_str ());
        }
        else
        {
            me->colAttribute = it->second;
        }
        me->haveColumns = true;
    }
    if (colAttribute < 0  ||  colAttribute >= input->current->values.size ()) return emptyValue;
    return input->current->values[colAttribute];
}

template<class T>
T &
MatrixSonataEdgesXSV<T>::operator () (const int row, const int column) const
{
    MatrixSonataEdgesXSV<T> * me = const_cast<MatrixSonataEdgesXSV<T>*> (this);
    me->tempResult = get (row, column);
    return me->tempResult;
}

template<class T>
int
MatrixSonataEdgesXSV<T>::rows () const
{
    throw "MatrixSonataEdgesXSV does not support rows()";
}

template<class T>
int
MatrixSonataEdgesXSV<T>::columns () const
{
    return track->input->columnCount;
}


// IteratorSonataEdgesXSV ----------------------------------------------------

template<class T>
IteratorSonataEdgesXSV<T>::IteratorSonataEdgesXSV (MatrixSonataEdgesXSV<T> * A)
:   A (*A)
{
    this->value = (T) 1; // Since we iterate only existing elements, always return true.
}

template<class T>
bool
IteratorSonataEdgesXSV<T>::next ()
{
    A.input->getRow (A.row);
    if (A.row > A.input->current->line) return false;  // Could not retrieve
    A.row++;  // Effectively, this is really next row.

    if (! A.haveColumns)
    {
        auto it = A.input->columnMap.find ("source_node_id");
        if (it == A.input->columnMap.end ()) A.colSource = -1;
        else                                 A.colSource = it->second;

        it = A.input->columnMap.find ("target_node_id");
        if (it == A.input->columnMap.end ()) A.colTarget = -1;
        else                                 A.colTarget = it->second;

        if (A.colSource < 0  ||  A.colTarget < 0)
        {
            fprintf (stderr, "ERROR: source_node_id or target_node_id is missing from edges file\n");
        }
        A.haveColumns = true;
    }

    this->row    = A.input->current->values[A.colSource];
    this->column = A.input->current->values[A.colTarget];
    return true;
}


#ifdef HAVE_HDF

// TableHDF ------------------------------------------------------------------

template<class T>
TableHDF<T>::TableHDF (const String & filePath, const String & resource, T emptyValue)
:   Table (filePath + "|" + (resource[0] == '/' ? resource.substr (1) : resource), emptyValue),
    resource (resource)
{
    sonataEdges  = false;
    sonataSpikes = false;
    start        = 0;
    count        = 0;
    A            = 0;

    sub = SubHolderHDF::allocate (filePath);
    if (! sub)
    {
        // Treat HDF errors as fatal. Otherwise, code would be too complicated.
        fprintf (stderr, "Error reading HDF file: %s\n", filePath.c_str ());
        throw "table() can't open HDF file";
    }

    std::lock_guard<std::mutex> lock (sub->mutexFile);
    H5::H5File & file = sub->file;

    try
    {
        if (rootIsGroup = file.childObjType (resource.c_str ()) == H5G_GROUP)
        {
            rootGroup = file.openGroup (resource.c_str ());
        }
        else
        {
            rootDataSet = file.openDataSet (resource.c_str ());
        }
    }
    catch (const H5::Exception & error)
    {
        fprintf (stderr, "Error reading HDF resource: %s\n", resource.c_str ());
        throw "table() can't open resource";
    }

    // Detect SONATA data that requires special interpretation.
    // It should be possible to use the magic string (attribute "magic", a uint32 with value 2682).
    // However, SONATA files don't consistently set this.
    // Instead, we assume that "spikes" and "edges" indicate the presence of special SONATA data.
    // "nodes" does not require special handling.
    std::vector<String> parents = split (resource, "/");
    if (parents[0].empty ()) parents.erase (parents.begin ());  // Remove start of absolute path.
    int parentCount = parents.size ();
    if (parentCount > 1)
    {
        try
        {
            sonataPopulation = file.openGroup ((parents[0] + "/" + parents[1]).c_str ());
            if (parents[0] == "edges")
            {
                sonataEdges = sonataPopulation.exists ("source_node_id")  &&  sonataPopulation.exists ("target_node_id");
            }
            else if (parents[0] == "spikes") // Name of group that contains sonataPopulation.
            {
                sonataSpikes = sonataPopulation.exists ("node_ids")  &&  sonataPopulation.exists ("timestamps");
            }
            if (! sonataEdges  &&  ! sonataSpikes) sonataPopulation.close ();
        }
        catch (const H5::Exception & error) {}
    }

    if (rootIsGroup)
    {
        // TODO: handle NWB TimeSeries
        dims.resize (2);  // and set to 0
        dimCount = 1;  // Data columns should be single dimensional.
        hsize_t count = rootGroup.getNumObjs ();
        for (hsize_t i = 0; i < count; i++)
        {
            if (rootGroup.getObjTypeByIdx (i) != H5G_DATASET) continue;
            std::string name = rootGroup.getObjnameByIdx (i);  // Unfortunately, we have to work with conventional std::string here, rather than our own String.

            H5::DataSet dataset = rootGroup.openDataSet (name);
            H5::DataSpace fspace = dataset.getSpace ();
            int tempDim = fspace.getSimpleExtentNdims ();
            if (tempDim != 1)
            {
                fprintf (stderr, "HDF dataset '%s' has %i dimensions\n", name.c_str (), tempDim);
                throw "table() expects HDF dataset to be 1-dimensional";
            }
            hsize_t temp;
            fspace.getSimpleExtentDims (&temp);
            dims[0] = max (dims[0], temp);

            columnMap[name.c_str ()] = headers.size ();
            headers.push_back (name.c_str ());
        }
        dims[1] = headers.size ();
    }
    else  // root is Dataset
    {
        H5::DataSpace fspace = rootDataSet.getSpace ();
        dimCount = fspace.getSimpleExtentNdims ();
        dims.resize (dimCount);
        fspace.getSimpleExtentDims (dims.data ());

        if (dimCount == 1)
        {
            dims.resize (2);  // Leaves dims[0] in place.
            dims[1] = 1;
        }
    }

    start = new hsize_t[dimCount];
    count = new hsize_t[dimCount];
}

template<class T>
TableHDF<T>::~TableHDF ()
{
    if (A  &&  A != this) delete A;
    if (start) delete[] start;
    if (count) delete[] count;

    if (! sub) return;
    {
        std::lock_guard<std::mutex> lock (sub->mutexFile);
        rootGroup       .close ();
        rootDataSet     .close ();
        sonataPopulation.close ();
    }
    sub->release ();
}

template<class T>
uint32_t
TableHDF<T>::classID () const
{
    return TableHDF_ID;
}

template<class T>
int
TableHDF<T>::rows () const
{
    return dims[0];
}

template<class T>
int
TableHDF<T>::columns () const
{
    return dims[1];
}

template<class T>
int
TableHDF<T>::columnIndex (const String & columnName)
{
    if (rootIsGroup)
    {
        auto it = columnMap.find (columnName);
        if (it == columnMap.end ()) return -1;
        return it->second;
    }
    else
    {
        return atoi (columnName.c_str ());
    }
}

template<class T>
int
TableHDF<T>::rowIndex (int keyColumn, const String & keyValue)
{
    if (keyColumn < 0  || keyColumn >= dims[1]) return -1;

    if (rowMap.empty ())
    {
        std::lock_guard<std::mutex> lock (sub->mutexFile);

        H5::DataSet columnData;
        if (rootIsGroup)
        {
            String & columnName = headers[keyColumn];
            columnData = rootGroup.openDataSet (columnName.c_str ());
        }
        else
        {
            columnData = rootDataSet;
        }

        start[0] = 0;
        count[0] = dims[0];
        if (dimCount > 1)
        {
            start[1] = rootIsGroup ? 0 : keyColumn;
            count[1] = 1;
        }

        H5::DataSpace fspace = columnData.getSpace ();  // File space
        fspace.selectHyperslab (H5S_SELECT_SET, count, start);
        H5::DataSpace mspace (dimCount, count);  // Memory space. Always starts at offset zero, so only give count.

        // Convert any data format to string.
        switch (columnData.getTypeClass ())
        {
            case H5T_FLOAT:
            {
                // TODO: This code is naive, because string matching for floating point is not well constrained.
                // The solution may be to use M-order comparator in rowMap.
                std::vector<double> values (dims[0]);
                columnData.read (values.data (), H5::PredType::NATIVE_DOUBLE, mspace, fspace);
                for (int i = 0; i < dims[0]; i++) rowMap[values[i]] = i;
            }
            case H5T_INTEGER:
            {
                H5::IntType intType = columnData.getIntType ();
                if (intType.getSign () == H5T_SGN_NONE)  // unsigned
                {
                    std::vector<uint64_t> values (dims[0]);
                    columnData.read (values.data (), H5::PredType::NATIVE_UINT64, mspace, fspace);
                    for (int i = 0; i < dims[0]; i++) rowMap[values[i]] = i;
                }
                else  // signed
                {
                    std::vector<int64_t> values (dims[0]);
                    columnData.read (values.data (), H5::PredType::NATIVE_INT64, mspace, fspace);
                    for (int i = 0; i < dims[0]; i++) rowMap[values[i]] = i;
                }
            }
            case H5T_STRING:
            {
                std::vector<char*> values (dims[0]);
                columnData.read (values.data (), H5::PredType::C_S1, mspace, fspace);
                for (int i = 0; i < dims[0]; i++)
                {
                    char * c = values[i];
                    if (! c) continue;
                    rowMap[c] = i;
                    free (c);  // HDF5 lib gives us responsibility for the allocated string blocks.
                }
            }
        }
    }

    auto it = rowMap.find (keyValue);
    if (it == rowMap.end ()) return -1;
    return it->second;
}

template<class T>
T
TableHDF<T>::get (const int row, const int column) const
{
    if (sonataEdges  ||  sonataSpikes) throw "Should access SONATA edges or spikes through matrix()";

    if (row < 0  ||  row >= dims[0]  ||  column < 0  ||  column >= dims[1]) return (T) 0;
    start[0] = row;
    count[0] = 1;
    if (dimCount > 1)
    {
        start[1] = column;
        count[1] = 1;
    }

    std::lock_guard<std::mutex> lock (sub->mutexFile);

    H5::DataSet columnData;
    if (rootIsGroup)
    {
        const String & columnName = headers[column];
        columnData = rootGroup.openDataSet (columnName.c_str ());
    }
    else  // root is a Dataset
    {
        columnData = rootDataSet;
    }

    H5::DataSpace fspace = columnData.getSpace ();  // File space
    fspace.selectHyperslab (H5S_SELECT_SET, count, start);
    H5::DataSpace mspace (dimCount, count);  // Memory space. Always starts at offset zero, so only give count.

#   ifdef n2a_FP
    double result;
    columnData.read (&result, H5::PredType::NATIVE_DOUBLE, mspace, fspace);
    return convert (result, exponent);
#   else
    T result;
    columnData.read (&result, H5::PredType::n2a_HDF_T, mspace, fspace);
    return result;
#   endif
}

template<class T>
String
TableHDF<T>::getString (const int row, const int column) const
{
    if (sonataEdges  ||  sonataSpikes)  throw "Should access SONATA edges or spikes through matrix()";

    if (row < 0  ||  row >= dims[0]  ||  column < 0  ||  column >= dims[1]) return "";
    start[0] = row;
    count[0] = 1;
    if (dimCount > 1)
    {
        start[1] = column;
        count[1] = 1;
    }

    std::lock_guard<std::mutex> lock (sub->mutexFile);

    H5::DataSet columnData;
    if (rootIsGroup)
    {
        const String & columnName = headers[column];
        columnData = rootGroup.openDataSet (columnName.c_str ());
    }
    else  // root is a Dataset
    {
        columnData = rootDataSet;
    }

    H5::DataSpace fspace = columnData.getSpace ();  // File space
    fspace.selectHyperslab (H5S_SELECT_SET, count, start);
    H5::DataSpace mspace (dimCount, count);  // Memory space. Always starts at offset zero, so only give count.

    // Convert any data format to string.
    switch (columnData.getTypeClass ())
    {
        case H5T_FLOAT:
        {
            double result;
            columnData.read (&result, H5::PredType::NATIVE_DOUBLE, mspace, fspace);
            return result;
        }
        case H5T_INTEGER:
        {
            H5::IntType intType = columnData.getIntType ();
            if (intType.getSign () == H5T_SGN_NONE)  // unsigned
            {
                uint64_t result;
                columnData.read (&result, H5::PredType::NATIVE_UINT64, mspace, fspace);
                return result;
            }
            else  // signed
            {
                int64_t result;
                columnData.read (&result, H5::PredType::NATIVE_INT64, mspace, fspace);
                return result;
            }
        }
        case H5T_STRING:
        {
            char * c;
            columnData.read (&c, H5::PredType::C_S1, mspace, fspace);
            if (! c) return "";
            String result = c;
            free (c);
            return result;
        }
    }
}

template<class T>
T &
TableHDF<T>::operator () (const int row, const int column) const
{
    TableHDF<T> * me = const_cast<TableHDF<T>*> (this);
    me->tempResult = get (row, column);
    return me->tempResult;
}

template<class T>
MatrixAbstract<T> *
TableHDF<T>::getMatrix (const char * resource)
{
    if (A) return A;
    if (sonataEdges)  return A = new MatrixSonataEdgesHDF<T>  (this);
    if (sonataSpikes) return A = new MatrixSonataSpikesHDF<T> (this);

    // This can be:
    // * Any 1D or 2D dataset.
    // * Several parallel datasets under a group. Can either be SONATA attributes or any other data structured the same way.
    return A = this;  // Caller will not delete us in this case.
}

template<class T>
TableHDF<T> *
#ifdef n2a_FP
tableHelperHDF (const String & fileName, const String & resource, int exponent, TableHDF<T> * oldHandle)
#else
tableHelperHDF (const String & fileName, const String & resource,               TableHDF<T> * oldHandle)
#endif
{
    String key = fileName + "|" + (resource[0] == '/' ? resource.substr (1) : resource);
    TableHDF<T> * handle = (TableHDF<T> *) SIMULATOR getHolder (key, oldHandle);
    if (! handle)
    {
        handle = new TableHDF<T> (fileName, resource, (T) 0);
        SIMULATOR holders.push_back (handle);
#       ifdef n2a_FP
        handle->exponent = exponent;
#       endif
    }
    return handle;
}


// IteratorNonzeroHDF --------------------------------------------------------

template<class T>
IteratorNonzeroHDF<T>::IteratorNonzeroHDF (TableHDF<T> * table)
:   table (table)
{
    if (table->rootIsGroup  ||  table->dimCount < 2) throw "IteratorNonzeroHDF requires a 2D dataset";

    row    = 0;
    column = -1;
    value  = 0;

    count[0] = max (1, (int) (TableHDF<T>::chunkSize / table->dims[1]));
    count[1] = table->dims[1];
    start[0] = -count[0];  // Trigger load of first block.
    start[1] = 0;
    chunk.resize (count[0] * count[1]);
}

template<class T>
bool
IteratorNonzeroHDF<T>::next ()
{
    for (; row < table->dims[0]; row++)
    {
        if (row >= start[0] + count[0])  // row has exceeded loaded data, so load more.
        {
            start[0] = row;

            std::lock_guard<std::mutex> lock (table->sub->mutexFile);

            H5::DataSpace fspace = table->rootDataSet.getSpace ();  // File space
            fspace.selectHyperslab (H5S_SELECT_SET, count, start);
            H5::DataSpace mspace (2, count);  // Memory space. Always starts at offset zero, so only give count.

#           ifdef n2a_FP
            int chunkSize = chunk.size ();
            std::vector<double> temp (chunkSize);
            table->rootDataSet.read (temp.data (), H5::PredType::NATIVE_DOUBLE, mspace, fspace);
            for (int i = 0; i < chunkSize; i++) chunk[i] = convert (temp[i], table->exponent);
#           else
            table->rootDataSet.read (chunk.data (), H5::PredType::n2a_HDF_T, mspace, fspace);
#           endif
        }

        while (true)
        {
            if (++column >= table->dims[1]) break;
            int r = row    - start[0];
            int c = column - start[1];
            value = chunk[r * count[1] + c];
            if (value != 0) return true;
        }
        column = -1;
    }

    return false;
}


// MatrixSonataEdgesHDF ------------------------------------------------------

template<class T>
MatrixSonataEdgesHDF<T>::MatrixSonataEdgesHDF (TableHDF<T> * table)
:   table (table)
{
    std::lock_guard<std::mutex> lock (table->sub->mutexFile);

    datasetAttribute = table->rootDataSet;
    if (datasetAttribute.getId () == H5I_INVALID_HID)  // The boolean connectivity itself. It will be the basis for a sparse iterator.
    {
        datasetSource = table->sonataPopulation.openDataSet ("source_node_id");
        datasetTarget = table->sonataPopulation.openDataSet ("target_node_id");

        H5::DataSpace dataspace = datasetSource.getSpace();
        dataspace.getSimpleExtentDims (&count);
        rowCount = count;  // Should be same as datasetTarget size.
        count = min (count, (hsize_t) TableHDF<T>::chunkSize);

        row = -1;  // Translates to max value, so it rolls over to 0 on first increment.

        // Our table will be added to Simulator::holders under "key", before any specific attribute matrix is created.
    }
    else  // An attribute that tracks with the sparse iterator.
    {
        // Locate the main iterator.
        track = 0;
        String key = table->sub->fileName + "|" + table->sonataPopulation.getObjName ().substr (1).c_str ();  // objName is always absolute. Our canonical form omits leading slash.
        TableHDF<T> * trackTable = (TableHDF<T> *) SIMULATOR getHolder (key, 0);
        if (trackTable) track = (MatrixSonataEdgesHDF<T> *) trackTable->A;
        if (! track) throw "Attempt to create SONATA edge attribute matrix before sparse iterator is created.";

        count = track->count;
        chunkAttribute.resize (count);
    }

    start = -count;  // Pretend that a chunk was already loaded, but before row 0.
}

template<class T>
uint32_t
MatrixSonataEdgesHDF<T>::classID () const
{
    return MatrixSonataEdgesHDF_ID;
}

template<class T>
T
MatrixSonataEdgesHDF<T>::get (const int row, const int column) const
{
    if (datasetAttribute.getId () == H5I_INVALID_HID) return 1;  // If attribute is absent, we are a boolean matrix. Always return 1, because this function should only be called for existent elements.
    if (track->row >= track->rowCount) return table->emptyValue;

    int rr = (int) (track->row - start);  // row relative to current block of data
    if (rr >= count)  // Out of data, so load another block.
    {
        rr = 0;
        MatrixSonataEdgesHDF<T> * me = const_cast<MatrixSonataEdgesHDF<T>*> (this);
        me->start = track->row;
        me->count = min (count, (hsize_t) track->rowCount - start);  // Don't read past end of dataset.

        std::lock_guard<std::mutex> lock (table->sub->mutexFile);

        H5::DataSpace fspace = datasetAttribute.getSpace ();  // File space
        fspace.selectHyperslab (H5S_SELECT_SET, &me->count, &me->start);
        H5::DataSpace mspace (1, &me->count);  // Memory space. Always starts at offset zero, so only give count.

#       ifdef n2a_FP
        std::vector<double> temp (count);
        datasetAttribute.read (temp.data (), H5::PredType::NATIVE_DOUBLE, mspace, fspace);
        for (int i = 0; i < count; i++) me->chunkAttribute[i] = convert (temp[i], exponent);
#       else
        datasetAttribute.read ((T *) chunkAttribute.data (), H5::PredType::n2a_HDF_T, mspace, fspace);
#       endif
    }

    return chunkAttribute[rr];
}

template<class T>
T &
MatrixSonataEdgesHDF<T>::operator () (const int row, const int column = 0) const
{
    MatrixSonataEdgesHDF<T> * me = const_cast<MatrixSonataEdgesHDF<T>*> (this);
    me->tempResult = get (row, column);
    return me->tempResult;
}

template<class T>
int
MatrixSonataEdgesHDF<T>::rows () const
{
    throw "MatrixSonataEdgesHDF does not support rows()";
}

template<class T>
int
MatrixSonataEdgesHDF<T>::columns () const
{
    throw "MatrixSonataEdgesHDF does not support columns()";
}


// IteratorSonataEdgesHDF ----------------------------------------------------

template<class T>
IteratorSonataEdgesHDF<T>::IteratorSonataEdgesHDF (MatrixSonataEdgesHDF<T> * A)
:   A (*A)
{
    value = 1;  // Always gives value of "true". The return value of next() ensures only legit (source,target) combinations are processed.

    chunkSource.resize (TableHDF<T>::chunkSize);
    chunkTarget.resize (TableHDF<T>::chunkSize);
}

template<class T>
bool
IteratorSonataEdgesHDF<T>::next ()
{
    A.row++;
    if (A.row >= A.rowCount) return false;  // Should stop iteration, so we don't keep incrementing row.

    int rr = (int) (A.row - A.start);  // row relative to current block of data
    if (rr >= A.count)  // Out of data, so load another block.
    {
        rr = 0;
        A.start = A.row;
        A.count = min (A.count, A.rowCount - A.row);  // Don't read past end of dataset.

        std::lock_guard<std::mutex> lock (A.table->sub->mutexFile);

        H5::DataSpace fspace = A.datasetSource.getSpace ();  // File space
        fspace.selectHyperslab (H5S_SELECT_SET, &A.count, &A.start);
        H5::DataSpace mspace (1, &A.count);  // Memory space. Always starts at offset zero, so only give count.

        A.datasetSource.read (chunkSource.data (), H5::PredType::NATIVE_UINT64, mspace, fspace);
        A.datasetTarget.read (chunkTarget.data (), H5::PredType::NATIVE_UINT64, mspace, fspace);
    }

    row    = chunkSource[rr];
    column = chunkTarget[rr];
    return true;
}


// MatrixSonataSpikesHDF -----------------------------------------------------

template<class T>
MatrixSonataSpikesHDF<T>::MatrixSonataSpikesHDF (TableHDF<T> * table)
:   table (table)
{
    std::lock_guard<std::mutex> lock (table->sub->mutexFile);

    datasetTime = table->sonataPopulation.openDataSet ("timestamps");
    rows_       = 0;

    // Scan node_ids and assemble index.
    H5::DataSet           datasetID   = table->sonataPopulation.openDataSet ("node_ids");
    std::vector<uint64_t> chunkID;
    hsize_t               start       = 0;
    hsize_t               count;
    uint64_t              lastID      = -1;  // Translates to max value.
    uint64_t              lastPointer = 0;

    H5::DataSpace dataspace = datasetID.getSpace();
    dataspace.getSimpleExtentDims (&count);
    uint64_t IDcount = count;

    for (uint64_t i = 0; i < IDcount; i++)
    {
        if (i % TableHDF<T>::chunkSize == 0)
        {
            start = i;
            count = min ((uint64_t) TableHDF<T>::chunkSize, IDcount - i);

            H5::DataSpace fspace = datasetID.getSpace ();
            fspace.selectHyperslab (H5S_SELECT_SET, &count, &start);
            H5::DataSpace mspace (1, &count);

            datasetID.read (chunkID.data (), H5::PredType::NATIVE_UINT64, mspace, fspace);
        }
        int      index = (int) (i - start);
        uint64_t ID    = chunkID[index];
        if (ID != lastID)
        {
            columnIDs     .push_back (ID);
            columnPointers.push_back (i);
            lastID      = ID;
            rows_       = max (rows_, i - lastPointer);
            lastPointer = i;
        }
    }
    lastID++;
    columnIDs     .push_back (lastID);
    columnPointers.push_back (count);
    rows_ = max (rows_, count - lastPointer);

    columnPointers.shrink_to_fit ();

    int skips = lastID + 1 - columnIDs.size ();
    if (skips == 0) columnIDs.clear ();  // columnIDs is zero-based contiguous. In this case, we can use column number directly.
    // else There are skips, so we need the list to look up a column.
    columnIDs.shrink_to_fit ();
}

template<class T>
uint32_t MatrixSonataSpikesHDF<T>::classID () const
{
    return MatrixSonataSpikesHDF_ID;
}

template<class T>
T
MatrixSonataSpikesHDF<T>::get (const int row, const int column) const
{
    int c;
    if (columnIDs.empty ())  // Column IDs are zero-based contiguous, so no need for search.
    {
        c = column;
    }
    else  // Column IDs are sparse, so search for position of column.
    {
        auto it = std::lower_bound (columnIDs.begin (), columnIDs.end (), column);
        if (it == columnIDs.end ()  ||  *it != column) return table->emptyValue;
        c = it - columnIDs.begin ();
    }
    if (c < 0  ||  c >= columnPointers.size ()) return table->emptyValue;
    if (row < 0  ||  row >= (int) (columnPointers[c+1] - columnPointers[c])) return table->emptyValue;

    hsize_t start = columnPointers[c] + row;
    hsize_t count = 1;

    std::lock_guard<std::mutex> lock (table->sub->mutexFile);

    H5::DataSpace fspace = datasetTime.getSpace ();
    fspace.selectHyperslab (H5S_SELECT_SET, &count, &start);
    H5::DataSpace mspace (1, &count);

    // This is rather slow. One possibility is to load the entire array of time values into memory.
#   ifdef n2a_FP
    double result;
    datasetTime.read (&result, H5::PredType::NATIVE_DOUBLE, mspace, fspace);
    return convert (result, Event<T>::exponent);
#   else
    T result;
    datasetTime.read (&result, H5::PredType::n2a_HDF_T, mspace, fspace);
    return result;
#   endif
}

template<class T>
T &
MatrixSonataSpikesHDF<T>::operator() (const int row, const int column) const
{
    MatrixSonataSpikesHDF<T> * me = const_cast<MatrixSonataSpikesHDF<T>*> (this);
    me->tempResult = get (row, column);
    return me->tempResult;
}

template<class T>
int
MatrixSonataSpikesHDF<T>::rows () const
{
    return rows_;
}

template<class T>
int
MatrixSonataSpikesHDF<T>::columns () const
{
    return columnPointers.size () - 1;
}

#endif  // HAVE_HDF

#endif
