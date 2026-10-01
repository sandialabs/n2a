/*
Copyright 2018-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#include "holder.tcc"
#include "holderMatrix.tcc"
#include "holderImage.tcc"
#include "Matrix.tcc"
#include "MatrixFixed.tcc"
#include "MatrixSparse.tcc"

#include "miniz.c"
#include "pugixml.cpp"

#ifdef HAVE_JNI
#  include "image.h"
#  include <jni.h>
#endif

using namespace n2a;
using namespace std;


// Matrix library ------------------------------------------------------------

template class MatrixAbstract<n2a_T>;
template class MatrixStrided<n2a_T>;
template class Matrix<n2a_T>;
template class MatrixFixed<n2a_T,3,1>;
template class MatrixSparse<n2a_T>;

// Most functions and operators are defined outside the matrix classes.
// These must be individually instantiated.

template SHARED void          clear      (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED void          identity   (const MatrixAbstract<n2a_T> & A);
template SHARED void          copy       (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED n2a_T         sumSquares (const MatrixAbstract<n2a_T> & A);
template SHARED Matrix<n2a_T> cross      (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> visit      (const MatrixAbstract<n2a_T> & A, n2a_T (*function) (const n2a_T &));
template SHARED Matrix<n2a_T> visit      (const MatrixAbstract<n2a_T> & A, n2a_T (*function) (const n2a_T));
template SHARED bool          equal      (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);

template SHARED Matrix<n2a_T> operator == (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator == (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator != (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator != (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator <  (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator <  (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator <= (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator <= (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator >  (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator >  (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator >= (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator >= (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator && (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator && (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator || (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator || (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);

template SHARED Matrix<n2a_T> operator & (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator * (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator / (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator / (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator / (const n2a_T scalar,              const MatrixAbstract<n2a_T> & A);
template SHARED Matrix<n2a_T> operator + (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator + (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator - (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator - (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator - (const n2a_T scalar,              const MatrixAbstract<n2a_T> & A);

template SHARED void operator *= (MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED void operator *= (MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED void operator /= (MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED void operator /= (MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED void operator += (MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED void operator += (MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED void operator -= (MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED void operator -= (MatrixAbstract<n2a_T> & A, const n2a_T scalar);

template SHARED Matrix<n2a_T> min (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> min (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> max (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> max (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);

template SHARED ostream & operator << (ostream & stream, const MatrixAbstract<n2a_T> & A);

template SHARED void          clear (      MatrixStrided<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> visit (const MatrixStrided<n2a_T> & A, n2a_T (*function) (const n2a_T &));
template SHARED Matrix<n2a_T> visit (const MatrixStrided<n2a_T> & A, n2a_T (*function) (const n2a_T));

template SHARED Matrix<n2a_T> operator & (const MatrixStrided<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator * (const MatrixStrided<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator * (const MatrixStrided<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator / (const MatrixStrided<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator / (const MatrixStrided<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator / (const n2a_T scalar,             const MatrixStrided<n2a_T> & A);
template SHARED Matrix<n2a_T> operator + (const MatrixStrided<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator + (const MatrixStrided<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator - (const MatrixStrided<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator - (const MatrixStrided<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator - (const n2a_T scalar,             const MatrixStrided<n2a_T> & A);

template SHARED Matrix<n2a_T> operator ~ (const Matrix<n2a_T> & A);
template SHARED Matrix<n2a_T> row        (const Matrix<n2a_T> & A, int row);
template SHARED Matrix<n2a_T> column     (const Matrix<n2a_T> & A, int column);
template SHARED Matrix<n2a_T> region     (const Matrix<n2a_T> & A, int firstRow, int firstColumn, int lastRow, int lastColumn);

#ifndef n2a_FP
// For fixed-point norm(), use the extended form that passes exponents.
template SHARED n2a_T norm (const MatrixAbstract<n2a_T> & A, n2a_T n);
template SHARED n2a_T norm (const MatrixStrided<n2a_T>  & A, n2a_T n);
#endif

// We don't need a full set of MatrixFixed functions, because user code includes MatrixFixed.tcc
// We only instantiate functions used within the runtime itself.
template SHARED MatrixFixed<n2a_T,3,1> operator / (const MatrixFixed<n2a_T,3,1> & A, const n2a_T scalar);
template SHARED MatrixFixed<n2a_T,3,1> operator - (const MatrixFixed<n2a_T,3,1> & A, const MatrixFixed<n2a_T,3,1> & B);

// Even in a fixed-point build, we still need to instantiate some floating-point matrix support.
// This is mostly for OpenGL rendering, but there are a few other dependencies outside that.
#if defined(n2a_FP)

template class MatrixAbstract<float>;
template class MatrixStrided<float>;
template class Matrix<float>;
template class MatrixFixed<float,3,1>;

template SHARED void          clear      (      MatrixStrided<float>  & A, const float scalar);
template SHARED float         norm       (const MatrixStrided<float>  & A, float n);
template SHARED Matrix<float> normalize  (const MatrixAbstract<float> & A);
template SHARED Matrix<float> operator * (const MatrixStrided<float>  & A, const MatrixAbstract<float> & B);

#endif


// I/O library ===============================================================

// ParseXSV ------------------------------------------------------------------

ParseXSV::ParseXSV ()
{
    delimiter     = ' ';
    delimiterSet  = false;
    tokenCapacity = 80;  // Enough for a number or a reasonable amount of text. Any longer text will cause inefficient resizing the first time.
}

void
ParseXSV::parseLine (std::istream & in, std::vector<String> & parts)
{
    parts.resize (0);

    String line;
    getline (in, line);
    int last = line.size () - 1;
    if (last >= 0  &&  line[last] == '\r') line.resize (last--);  // Hack to handle CRLF line ending when c runtime fails to recognize it.
    if (line.empty ()) return;

    bool inQuote = false;
    if (! delimiterSet)
    {
        // Skip BOM from UTF-8 file.
        // Done inside the delimiter section just to limit this check to the beginning of the file.
        // There's no special relationship between BOM and delimiter.
        if (! delimiterSet  &&  last >= 2  &&  (uint8_t) line[0] == 0xEF  &&  (uint8_t) line[1] == 0xBB  &&  (uint8_t) line[2] == 0xBF) line = line.substr (3);

        // Scan for first delimiter character that is not inside a quote.
        for (char c : line)
        {
            if (c == '\"')
            {
                inQuote = ! inQuote;
                continue;
            }
            if (inQuote) continue;
            if (c == '\t')
            {
                delimiter = c;
                break;
            }
            if (c == ',') delimiter = c;  // Don't break, allowing the possibility of finding a higher-precedence delimiter.
            // space character is lowest precedence
        }
        delimiterSet =  delimiter != ' '  ||  line.find_first_not_of (' ') != String::npos;
    }

    // Break line into delimited strings, possibly quoted.
    inQuote = false;
    String token;
    token.reserve (tokenCapacity);
    for (int i = 0; i <= last; i++)
    {
        char c = line[i];
        if (c == '\"')
        {
            if (inQuote  &&  i < last  &&  line[i+1] == '\"')
            {
                token += c;
                i++;
                continue;
            }
            inQuote = ! inQuote;
            continue;
        }
        if (c == delimiter  &&  ! inQuote)
        {
            tokenCapacity = max (tokenCapacity, (int) token.size ());
            parts.push_back (std::move (token));
            token.reserve (tokenCapacity);
            continue;
        }
        token += c;
    }
    if (! token.empty ()) parts.push_back (std::move (token));
}

void
ParseXSV::parse (std::istream & in)
{
    std::vector<String> parts;
    while (in.good ())
    {
        parseLine (in, parts);
        if (! processLine (parts)) break;
    }
}

bool
ParseXSV::processLine (std::vector<String> & parts)
{
    return true;
}


// ZipFile -------------------------------------------------------------------

ZipFile::ZipFile (const String & fileName)
:   fileName (fileName)
{
    mz_zip_zero_struct (&archive);
    open (fileName);
}

ZipFile::~ZipFile ()
{
    close ();
}

bool
ZipFile::probe (const String & fileName)
{
    char magic[4];
    magic[3] = 0;  // Prevent random value in memory from tricking us into seeing the ZIP magic string.
    std::ifstream ifs (fileName.c_str (), std::ios::binary);
    ifs.read (magic, 4);
    ifs.close ();
    return magic[0] == 'P'  &&  magic[1] == 'K'  &&  magic[2] == 3  &&  magic[3] == 4;
}

void
ZipFile::open (const String & fileName)
{
    close ();
    if (fileName.empty ()) return;  // Nothing to open.
    if (! mz_zip_reader_init_file (&archive, fileName.c_str (), 0))
    {
        throw mz_zip_get_error_string (archive.m_last_error);
    }
}

void
ZipFile::close ()
{
    if (archive.m_zip_type != MZ_ZIP_TYPE_INVALID) mz_zip_end (&archive);
    mz_zip_zero_struct (&archive);
}

bool
ZipFile::isOpen ()
{
    return archive.m_zip_type != MZ_ZIP_TYPE_INVALID;
}

String
ZipFile::extract (const String & entryName)
{
    if (! isOpen ()) return "";
    int index = mz_zip_reader_locate_file (&archive, entryName.c_str (), 0, 0);
    if (index < 0) return "";
    mz_zip_archive_file_stat stat;
    if (! mz_zip_reader_file_stat (&archive, index, &stat)) return "";
    int size = stat.m_uncomp_size;

    String result;
    result.reserve (size);  // It would be cleaner to use resize(), but this avoids the useless fill operation.
    if (mz_zip_reader_extract_to_mem (&archive, index, (void *) result.c_str (), size, 0))
    {
        // This is necessary in lieu of using resize() above.
        result.top = result.memory + size;
        *result.top = 0;  // null termination
    }
    return result;
}

bool
ZipFile::exists (const String & entryName)
{
    if (! isOpen ()) return false;
    int entryIndex = mz_zip_reader_locate_file (&archive, entryName.c_str (), 0, 0);
    return entryIndex >= 0;
}

int
ZipFile::entryCount ()
{
    return mz_zip_reader_get_num_files (&archive);
}

String
ZipFile::entryName (int index)
{
    int size = mz_zip_reader_get_filename (&archive, index, 0, 0);  // Includes space for null termination.
    if (size == 0) throw "Zip entry with given index was not found";
    int n = size - 1;  // Actual number of characters in name.

    String result;
    result.reserve (n);
    if (mz_zip_reader_get_filename (&archive, index, (char *) result.c_str (), size))
    {
        result.top = result.memory + n;
        *result.top = 0;
    }
    return result;
}


// Holder --------------------------------------------------------------------

Holder::Holder (const String & fileName)
:   fileName (fileName)
{
}


// SubHolderHDF --------------------------------------------------------------

#ifdef HAVE_HDF

SubHolderHDF::SubHolderHDF (const String & fileName)
:   fileName (fileName),
    file (fileName.c_str (), H5F_ACC_RDONLY)  // Could throw an exception
{
    users = 0;
}

SubHolderHDF *
SubHolderHDF::allocate (const String & fileName)
{
    SubHolderHDF * result;
    std::lock_guard<std::mutex> lock (mutexFiles);
    auto it = SubHolderHDF::files.find (fileName);
    if (it == SubHolderHDF::files.end ())
    {
        try
        {
            result = new SubHolderHDF (fileName);
            files[fileName] = result;
        }
        catch (const H5::Exception & error)
        {
            fprintf (stderr, "Failed to open HDF file: %s\n", fileName.c_str ());
            return 0;
        }
    }
    else
    {
        result = it->second;
    }
    result->users++;
    return result;
}

void
SubHolderHDF::allocate ()
{
    std::lock_guard<std::mutex> lock (mutexFiles);
    users++;
}

void
SubHolderHDF::release ()
{
    std::lock_guard<std::mutex> lock (mutexFiles);
    users--;
    if (users > 0) return;

    // Strictly speaking, the file will be closed by dtor.
    // However, we preemtively close in order to trap any exceptions.
    try
    {
        file.close ();
    }
    catch (const H5::Exception & error)
    {
        fprintf (stderr, "Failed to close HDF file: %s\n", fileName.c_str ());
    }
    files.erase (fileName);
    delete this;  // Self destruct. Object must not be used after this.
}

#endif // HAVE_HDF


// Everything Else -----------------------------------------------------------

template class Parameters<n2a_T>;
template class HolderMatrix<n2a_T>;
template class IteratorNonzero<n2a_T>;
template class IteratorSkip<n2a_T>;
template class IteratorSparse<n2a_T>;
template class MatrixInput<n2a_T>;
template class ReadSonataSpikes<n2a_T>;
template class Table<n2a_T>;
template class Sheet<n2a_T>;
template class TableSheet<n2a_T>;
template class LoadTableXSV<n2a_T>;
template class IteratorSparseRegion<n2a_T>;
template class MatrixSonataEdgesXSV<n2a_T>;
template class IteratorSonataEdgesXSV<n2a_T>;
template class ImageInput<n2a_T>;
template class ImageOutput<n2a_T>;
template class Mfile<n2a_T>;
template class InputHolder<n2a_T>;
template class InputXSV<n2a_T>;
template class OutputHolder<n2a_T>;
#ifdef HAVE_HDF
map<String,SubHolderHDF*> SubHolderHDF::files;
mutex                     SubHolderHDF::mutexFiles;
template class TableHDF<n2a_T>;
template class IteratorNonzeroHDF<n2a_T>;
template class MatrixSonataEdgesHDF<n2a_T>;
template class IteratorSonataEdgesHDF<n2a_T>;
template class MatrixSonataSpikesHDF<n2a_T>;
template class InputHDF<n2a_T>;
#endif

template SHARED IteratorNonzero<n2a_T> * getIterator (MatrixAbstract<n2a_T> * A);
template SHARED n2a_T convertDate (const String & field, n2a_T defaultValue);

#ifdef n2a_FP

template SHARED Matrix<n2a_T> * loadNPY (std::istream & in,                       int exponent);
template SHARED Matrix<n2a_T> * loadNPY (ZipFile & zip, const String & entryName, int exponent);

template SHARED HolderMatrix<n2a_T> * matrixHelper     (const String & fileName, const String & key, const String & value, n2a_T emptyValue, int exponent,                  HolderMatrix<n2a_T> * oldHandle);
template SHARED TableSheet  <n2a_T> * tableHelperSheet (const String & fileName,                                                             int exponent,                  TableSheet  <n2a_T> * oldHandle);
template SHARED InputXSV    <n2a_T> * inputHelperXSV   (const String & fileName,                                                             int exponent, int exponentRow, InputXSV    <n2a_T> * oldHandle);
#  ifdef HAVE_HDF
template SHARED TableHDF    <n2a_T> * tableHelperHDF   (const String & fileName, const String & resource,                                    int exponent,                  TableHDF    <n2a_T> * oldHandle);
template SHARED InputHDF    <n2a_T> * inputHelperHDF   (const String & fileName, const String & resource,                                    int exponent, int exponentRow, InputHDF    <n2a_T> * oldHandle);
#  endif

#else

template SHARED Matrix<n2a_T> * loadNPY (istream & in);
template SHARED Matrix<n2a_T> * loadNPY (ZipFile & zip, const String & entryName);

template SHARED HolderMatrix<n2a_T> * matrixHelper     (const String & fileName, const String & key, const String & value, n2a_T emptyValue, HolderMatrix<n2a_T> * oldHandle);
template SHARED TableSheet  <n2a_T> * tableHelperSheet (const String & fileName,                                                             TableSheet  <n2a_T> * oldHandle);
template SHARED InputXSV    <n2a_T> * inputHelperXSV   (const String & fileName,                                                             InputXSV    <n2a_T> * oldHandle);
#  ifdef HAVE_HDF
template SHARED TableHDF    <n2a_T> * tableHelperHDF   (const String & fileName, const String & resource,                                    TableHDF    <n2a_T> * oldHandle);
template SHARED InputHDF    <n2a_T> * inputHelperHDF   (const String & fileName, const String & resource,                                    InputHDF    <n2a_T> * oldHandle);
#  endif

#endif

template SHARED Mfile       <n2a_T> * MfileHelper      (const String & fileName, Mfile       <n2a_T> * oldHandle);
template SHARED OutputHolder<n2a_T> * outputHelper     (const String & fileName, OutputHolder<n2a_T> * oldHandle);
template SHARED ImageInput  <n2a_T> * imageInputHelper (const String & fileName, ImageInput  <n2a_T> * oldHandle);
template SHARED ImageOutput <n2a_T> * imageOutputHelper(const String & fileName, ImageOutput <n2a_T> * oldHandle);

#ifdef HAVE_GL

LightLocation::LightLocation (GLuint program, int index)
{
    char buffer[32];
    sprintf (buffer, "light[%i].infinite",     index);
    infinite     = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].position",     index);
    position     = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].direction",    index);
    direction    = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].ambient",      index);
    ambient      = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].diffuse",      index);
    diffuse      = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].specular",     index);
    specular     = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].spotExponent", index);
    spotExponent = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].Cutoff",       index);
    spotCutoff   = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].attenuation0", index);
    attenuation0 = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].attenuation1", index);
    attenuation1 = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].attenuation2", index);
    attenuation2 = glGetUniformLocation (program, buffer);
}

void
Light::clear ()
{
    infinite = false;
    position [0] = 0;
    position [1] = 0;
    position [2] = 1;
    direction[0] = 0;
    direction[1] = 0;
    direction[2] = -1;
    ambient  [0] = 0;
    ambient  [1] = 0;
    ambient  [2] = 0;
    diffuse  [0] = 1;
    diffuse  [1] = 1;
    diffuse  [2] = 1;
    specular [0] = 1;
    specular [1] = 1;
    specular [2] = 1;
    spotExponent = 0;
    spotCutoff   = -1;
    attenuation0 = 1;
    attenuation1 = 0;
    attenuation2 = 0;
}

void
Light::setUniform (const LightLocation & l, const Matrix<float> & view)
{
    // Transform the position and direction vectors.
    Matrix<float> P (position,  0, 3, 1, 1, 3);
    Matrix<float> D (direction, 0, 3, 1, 1, 3);
    P = view * P + column (view, 3);  // Ignore fourth row, since view should not have perspective scaling.
    Matrix<float> normal = view;  // TODO: create inverse transpose of view
    D = normal * D;

    glUniform1i  (l.infinite,     infinite);
    glUniform3fv (l.position,  1, P.base ());
    glUniform3fv (l.direction, 1, D.base ());
    glUniform3fv (l.ambient,   1, ambient);
    glUniform3fv (l.diffuse,   1, diffuse);
    glUniform3fv (l.specular,  1, specular);
    glUniform1f  (l.spotExponent, spotExponent);
    glUniform1f  (l.spotCutoff,   spotCutoff);
    glUniform1f  (l.attenuation0, attenuation0);
    glUniform1f  (l.attenuation1, attenuation1);
    glUniform1f  (l.attenuation2, attenuation2);
}

GLint Material::locAmbient;
GLint Material::locDiffuse;
GLint Material::locEmission;
GLint Material::locSpecular;
GLint Material::locShininess;

Material::Material ()
:   ambient{0.2, 0.2, 0.2},
    diffuse{0.8, 0.8, 0.8, 1},
    emission{0, 0, 0},
    specular{0, 0, 0}
{
    shininess = 16;
}

void
Material::setUniform () const
{
    glUniform3fv (locAmbient,  1, ambient);
    glUniform4fv (locDiffuse,  1, diffuse);
    glUniform3fv (locEmission, 1, emission);
    glUniform3fv (locSpecular, 1, specular);
    glUniform1f  (locShininess,   shininess);
}

void
put (std::vector<GLfloat> & vertices, float x, float y, float z, float n[3])
{
    vertices.push_back (x);
    vertices.push_back (y);
    vertices.push_back (z);
    vertices.push_back (n[0]);
    vertices.push_back (n[1]);
    vertices.push_back (n[2]);
}

void
put (std::vector<GLfloat> & vertices, Matrix<float> f, float x, float y, float z, float nx, float ny, float nz)
{
    MatrixFixed<float,4,1> t;
    t[0] = x;
    t[1] = y;
    t[2] = z;
    t[3] = 1;
    Matrix<float> P = f * t;
    vertices.push_back (P[0]);
    vertices.push_back (P[1]);
    vertices.push_back (P[2]);

    t[0] = nx;
    t[1] = ny;
    t[2] = nz;
    t[3] = 0;
    P = f * t;
    vertices.push_back (P[0]);
    vertices.push_back (P[1]);
    vertices.push_back (P[2]);
}

int
putUnique (std::vector<GLfloat> & vertices, float x, float y, float z)
{
    int count = vertices.size ();
    for (int i = 0; i < count; i += 6)
    {
        if (vertices[i] == x  &&  vertices[i+1] == y  &&  vertices[i+2] == z) return i / 6;
    }

    vertices.push_back (x);
    vertices.push_back (y);
    vertices.push_back (z);
    vertices.push_back (x);
    vertices.push_back (y);
    vertices.push_back (z);

    return count / 6;
}

void
icosphere (std::vector<GLfloat> & vertices, std::vector<GLuint> & indices)
{
    // This function is always defined as float.
    // When compiling the fixed-point runtime, it is necessary to restore the
    // floating-point definition of pi.
#   ifdef n2a_FP
#   undef M_PI
#   define M_PI 3.14159265359f
#   endif

    float angleH = 2 * M_PI / 5; // 72 degrees
    float angleV = atan (0.5);   // elevation = 26.565 degree

    float angleH1 = -M_PI / 2 - angleH / 2;  // start from -126 deg at 2nd row
    float angleH2 = -M_PI / 2;               // start from -90  deg at 3rd row
    float z       = sin (angleV);

    // top
    putUnique (vertices, 0, 0, 1);

    // 2nd row
    for (int i = 0; i < 5; i++)
    {
        float xy = cos (angleV);
        float a = angleH1 + i * angleH;
        putUnique (vertices, xy * cos (a), xy * sin (a), z);
    }

    // 3rd row
    for (int i = 0; i < 5; i++)
    {
        float xy = cos (angleV);
        float a = angleH2 + i * angleH;
        putUnique (vertices, xy * cos (a), xy * sin (a), -z);
    }

    // bottom vertex
    putUnique (vertices, 0, 0, -1);

    // Indices
    for (int i = 0; i < 5; i++)
    {
        int i2 = i + 1;
        int i3 = i2 + 5;
        int j2 = (i + 1) % 5 + 1;
        int j3 = j2 + 5;

        // top triangle
        indices.push_back (0);
        indices.push_back (i2);
        indices.push_back (j2);

        // 2nd row
        indices.push_back (i2);
        indices.push_back (i3);
        indices.push_back (j2);

        // 3rd row
        indices.push_back (i3);
        indices.push_back (j3);
        indices.push_back (j2);

        // bottom triangle
        indices.push_back (11);
        indices.push_back (j3);
        indices.push_back (i3);
    }
}

void
icosphereSubdivide (std::vector<GLfloat> & vertices, std::vector<GLuint> & indices)
{
    int count = indices.size ();
    std::vector<GLuint> next (count * 4);

    for (int j = 0; j < count; j += 3)
    {
        // Get current triangle.
        int j0 = indices[j];
        int j1 = indices[j+1];
        int j2 = indices[j+2];

        // Create 3 new vertices by splitting each edge.
        int c01 = split (vertices, j0, j1);
        int c12 = split (vertices, j1, j2);
        int c20 = split (vertices, j2, j0);

        // Add 4 new triangles
        next.push_back (j0);
        next.push_back (c01);
        next.push_back (c20);

        next.push_back (j1);
        next.push_back (c12);
        next.push_back (c01);

        next.push_back (j2);
        next.push_back (c20);
        next.push_back (c12);

        next.push_back (c01);
        next.push_back (c12);
        next.push_back (c20);
    }

    indices = next;
}

int
split (std::vector<GLfloat> & vertices, int v0, int v1)
{
    v0 *= 6;
    v1 *= 6;
    float x = vertices[v0  ] + vertices[v1  ];
    float y = vertices[v0+1] + vertices[v1+1];
    float z = vertices[v0+2] + vertices[v1+2];
    float l = sqrt (x * x + y * y + z * z);
    x /= l;
    y /= l;
    z /= l;
    return putUnique (vertices, x, y, z);
}

#ifdef n2a_FP

void
setVector (float target[], const Matrix<int> & value, int exponent)
{
    float conversion = powf (2, FP_MSB - exponent);
    for (int i = 0; i < 3; i++) target[i] = value[i] / conversion;
}

#else

template SHARED void setVector (float target[], const Matrix<n2a_T> & value);
template SHARED void setColor  (float target[], const Matrix<n2a_T> & color, bool withAlpha);

#endif

#endif
