/*
Copyright 2018-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

/*
For 3D graphics support, this software depends on header files from the Khronos Group.
Download the following files and place them in same directory as holder.h:
    https://www.khronos.org/registry/OpenGL/api/GL/glcorearb.h
    https://www.khronos.org/registry/OpenGL/api/GL/wglext.h
Store in subdirectory KHR:
    https://www.khronos.org/registry/EGL/api/KHR/khrplatform.h


For Excel spreadsheet support, this software depends on external packages miniz and pugixml.
NumPy matrix support also depends on miniz.

https://github.com/zeux/pugixml -- Get the release zip, unpack, and place the following files in same directory as holder.h:
    pugixml.cpp
    pugixml.hpp
    pugiconfig.hpp
    LICENSE.md --> gov/sandia/n2a/ui/settings/licenses/pugixml

Uncomment the following defines in pugiconfig.hpp:
    #define PUGIXML_COMPACT
    #define PUGIXML_NO_XPATH
    #define PUGIXML_NO_STL
    #define PUGIXML_NO_EXCEPTIONS
    #define PUGIXML_HEADER_ONLY

https://github.com/richgel999/miniz -- Get the release zip, unpack, and place these files in the same directory as holder.h:
    miniz.c
    miniz.h
    LICENSE --> gov/sandia/n2a/ui/settings/licenses/miniz

Uncomment the following define in miniz.h:
    #define MINIZ_NO_ARCHIVE_WRITING_APIS
*/

#ifndef n2a_holder_h
#define n2a_holder_h


#include <nosys.h>
#include "mystring.h"
#include "matrix.h"
#include "MNode.h"
#include "canvas.h"
#include "miniz.h"
#ifdef HAVE_FFMPEG
#  include "video.h"
#endif

// Control how Windows gets included.
#ifdef _MSC_VER
#  define WIN32_LEAN_AND_MEAN
#  include <windows.h>
#  undef min
#  undef max
#endif

#ifdef HAVE_HDF
#  include <H5Cpp.h>
#endif

#ifdef HAVE_GL
#  include "glcorearb.h"
#endif

#include <vector>
#include <list>
#include <unordered_map>
#include <map>
#include <mutex>

#include "shared.h"


/**
    Utility class for reading/accessing command-line parameters.
    These are primarily intended to override parameters within the model.
**/
template<class T>
struct SHARED Parameters
{
    std::unordered_map<String,String> namedValues;

    void   parse (const String & line);
    void   parse (int argc, const char * argv[]);  ///< The arguments have the same semantics as main(argc, argv). In particular, the first argument is ignored because it is the name of the program.
    void   read  (const String & parmFileName);
    void   read  (std::istream & stream);

    T      get   (const String & name, T defaultValue = (T) 0) const;  // TODO: perform unit conversion. Consider using https://github.com/LLNL/units or https://github.com/martinmoene/PhysUnits-RT
    String get   (const String & name, const String & defaultValue = "") const;
};

/**
    Facility to parse XSV files.
    This separates the interpretation of the data from the parsing, allowing the parse code to be reused (DRY).
    This does not handle streaming
**/
struct SHARED ParseXSV
{
    char delimiter;  // space char, initially
    bool delimiterSet;
    int  tokenCapacity;

    ParseXSV ();

    /**
        Reads one line from the file.
        Blocks until a full line has arrived, or EOF or error.
        @param parts Filled with current row. At EOF or error, this holds all data that was successfully
        read before the end was reached.
    **/
    void parseLine (std::istream & in, std::vector<String> & parts);
    void parse (std::istream & in);  ///< Reads entire file, calling processLine() for each valid line.

    /**
        @param parts The columns found on the current row.
        @return true to continue parsing the file. false to stop early.
    **/
    virtual bool processLine (std::vector<String> & parts);
};

struct SHARED ZipFile
{
    mz_zip_archive archive;
    String         fileName;

    ZipFile (const String & fileName = "");  ///< Empty string means don't open a file now.
    ~ZipFile ();

    static bool probe      (const String & fileName);

    void   open       (const String & fileName);
    void   close      ();
    bool   isOpen     ();
    /**
        Decompresses the binary contents of the entry.
        Our custom String class is currently limited to 1GiB.
        @return Any failure results in an empty string. The caller must decide whether this is tolerable or fatal.
    **/
    String extract    (const String & entryName);
    bool   exists     (const String & entryName);
    int    entryCount ();
    String entryName  (int index);
};

struct SHARED Holder
{
    String fileName;
    Holder (const String & fileName);
    virtual ~Holder () = default;
};

template<class T>
struct SHARED HolderMatrix : public Holder
{
    HolderMatrix (const String & fileName);

    /**
        Retrieve a matrix for some portion of this holder's content.
        @param resource A subclass-specific path to more specific content inside this holder.
        The char pointer is only valid during the call.
        @return A pointer to the matrix. This holder remains responsible for the
        associated memory. The caller can simply forget the pointer when it is done.
    **/
    virtual MatrixAbstract<T> * getMatrix (const char * resource = 0) = 0;
};

template<class T>
struct SHARED IteratorNonzero
{
    int row;
    int column;
    T   value;

    virtual bool next () = 0;  // Advances to next nonzero element. Returns false if no more are available.
};

template<class T>
struct SHARED IteratorSkip : public IteratorNonzero<T>
{
    Matrix<T> * A;
    int nextRow;
    int nextColumn;
    T   nextValue;

    IteratorSkip (Matrix<T> * A);

    virtual bool next ();
    void         getNext ();
};

template<class T>
struct SHARED IteratorSparse : public IteratorNonzero<T>
{
    MatrixSparse<T> *                  A;
    int                                columns;
    typename std::map<int,T>::iterator it;

    IteratorSparse (MatrixSparse<T> * A);
    virtual bool next ();
};

template<class T>
struct SHARED MatrixInput : public HolderMatrix<T>
{
    MatrixAbstract<T> * A;  // Will be either Matrix or MatrixSparse, determined by matrixHelper when reading the file.

    MatrixInput (const String & fileName);
    virtual ~MatrixInput ();

#   ifdef n2a_FP
    void loadNPY          (                                         int exponent);
    void loadTextDense    (                                         int exponent);
    void loadTextSparse   (                           T emptyValue, int exponent);
    void loadSonataSpikes (const String & population, T emptyValue, int exponent);
#   else
    void loadNPY          ();
    void loadTextDense    ();
    void loadTextSparse   (                           T emptyValue);
    void loadSonataSpikes (const String & population, T emptyValue);
#   endif

    virtual MatrixAbstract<T> * getMatrix (const char * resource = 0);  ///< Returns A. "resource" is ignored.
};

// There is a wide variety of file types that can be loaded as a matrix.
// The different types are handled by a family of matrix helper functions.
// The code generator does initial triage based on keywords passed to matrix(), and selects the right helper.
#ifdef n2a_FP
template<class T> SHARED HolderMatrix<T> * matrixHelper (const String & fileName, const String & key, const String & value, T emptyValue, int exponent, HolderMatrix<T> * oldHandle = 0);
#else
template<class T> SHARED HolderMatrix<T> * matrixHelper (const String & fileName, const String & key, const String & value, T emptyValue,               HolderMatrix<T> * oldHandle = 0);
#endif

template<class T> SHARED IteratorNonzero<T> * getIterator (MatrixAbstract<T> * A);  // Returns an object that iterates over nonzero elements of A.

#ifdef n2a_FP

inline int
convert (double input, int exponent)
{
    if (input == 0) return 0;
    if (std::isnan (input)) return NAN;
    bool negate = input < 0;
    if (std::isinf (input))
    {
        if (negate) return -INFINITY;
        return              INFINITY;
    }

    int64_t bits = (int64_t &) input;
    int e = (int) ((bits >> 52) & 0x7FF) - 1023;
    bits &= 0x0FFFFFFFFFFFFFl;  // clear sign and exponent bits
    bits |= 0x10000000000000l;  // set implied msb of mantissa (bit 52) to 1
    if (negate) bits = -bits;
    int shift = 52 + exponent - e;
    if (shift >= 0) return bits >> shift;
    return bits << -shift;
}

inline int
convert (float input, int exponent)
{
    if (input == 0) return 0;
    if (std::isnan (input)) return NAN;
    bool negate = input < 0;
    if (std::isinf (input))
    {
        if (negate) return -INFINITY;
        return              INFINITY;
    }

    int32_t bits = (int32_t &) input;
    int e = ((bits >> 23) & 0xFF) - 127;
    bits &= 0x7FFFFF;  // clear sign and exponent bits
    bits |= 0x800000;  // set implied msb of mantissa (bit 23) to 1
    if (negate) bits = -bits;
    int shift = 23 + exponent - e;
    if (shift >= 0) return bits >> shift;
    return bits << -shift;
}

inline int
convert (const String & input, int exponent)
{
    return convert (atof (input.c_str ()), exponent);
}

template<class T> SHARED Matrix<T> * loadNPY (std::istream & in,                       int exponent);
template<class T> SHARED Matrix<T> * loadNPY (ZipFile & zip, const String & entryName, int exponent);

#else

template<class T> SHARED Matrix<T> * loadNPY (std::istream & in);
template<class T> SHARED Matrix<T> * loadNPY (ZipFile & zip, const String & entryName);

#endif  // n2a_FP

/// Convert CSV data into sparse spike matrix.
template<class T>
struct ReadSonataSpikes : public ParseXSV
{
    MatrixSparse<T> * S;
    String            population;  // name of population that is spiking
    int               colTime;
    int               colPopulation;
    int               colID;
    bool              gotColumns;
    int               lastID;
    int               eventCount;
#   ifdef n2a_FP
    int               exponent;
#   endif

    ReadSonataSpikes (const String & population);
    virtual bool processLine (std::vector<String> & parts);
};

template<class T>
struct SHARED Table : public HolderMatrix<T>
{
    std::mutex mutexAnchor;
    T          emptyValue;

    Table (const String & fileName, T emptyValue);

    virtual void   parse         (const String & anchor);
    virtual int    rows          () const = 0;
    virtual int    columns       () const = 0;
    virtual int    rowsInColumn  () const;
    virtual int    columnsInRow  () const;
    virtual int    columnIndex   (const String & columnName) = 0;
    virtual int    rowIndex      (const int keyColumn, const String & keyValue) = 0;
    virtual T      get           (const int row, const int column) const = 0;
    virtual String getString     (const int row, const int column) const = 0;

    // Functions that take anchor.
    // These wrap the anchor setting and info retrieval in a critical section, so anchor is guaranteed to remain consistent.
    int    rows         (const String & anchor);
    int    columns      (const String & anchor);
    int    rowsInColumn (const String & anchor);
    int    columnsInRow (const String & anchor);
    T      get          (const String & anchor, const int row, const int column);
    String getString    (const String & anchor, const int row, const int column);
};

/**
    Matrices which provide cell values for the entire sheet.
    In general, a cell will either be a number, a string, or empty.
    We don't know ahead of time whether the matrix is dense or sparse, so the
    exact type of matrix is decided by the loader.
**/
template<class T>
struct SHARED Sheet
{
    MatrixAbstract<T> *            numbers;   ///< Dense matrix stores empty cells and strings as 0. Sparse matrix does not store them at all.
    MatrixAbstract<int> *          strings;   ///< 1-based indices into string collection. Empty cells and numbers are 0.
    int                            rows;
    int                            columns;
    std::unordered_map<String,int> columnMap; ///< From header text to index. If empty, then there is no header row.
    std::vector<int>               index;     ///< Array of row numbers, sorted according to key (specified elsewhere). If empty, then index needs to be built.

    Sheet ();
    ~Sheet ();
};

template<class T>
struct SHARED TableSheet : public Table<T>
{
    std::vector<String>                 strings;  ///< collection of all strings that appear in the workbook
    std::map<String,Sheet<T>*>          wb;       ///< workbook, a collection of worksheets
    Sheet<T> *                          first;    ///< The first sheet defined in the file. This is the default when no sheet is specified in cell address.
    String                              cell;     ///< The most recently parsed anchor cell address. Includes sheet name and coordinates.
    Sheet<T> *                          ws;       ///< Anchor sheet
    int                                 ar;       ///< Anchor row
    int                                 ac;       ///< Anchor column
#   ifdef n2a_FP
    int                                 exponent;
#   endif
    std::map<String,MatrixAbstract<T>*> matrices; ///< Cache of matrices handed out by getMatrix(). See Mmatrix for comments about this field.

    static constexpr double fillThreshold = 0.5;

    TableSheet (const String & fileName, T emptyValue);
    virtual ~TableSheet ();

    void load      ();  ///< Triage file magic, than call appropriate loader.
    void loadXSV   ();
    void loadExcel ();

    void parse   (const String & cell);       ///< Subroutine for all functions that take an anchor cell address.
    void parseA1 (const String & coordinate); ///< Process just the coordinates of a cell address.

    // These counts are always relative to an anchor cell.
    virtual int    rows         () const;
    virtual int    columns      () const;
    virtual int    rowsInColumn () const;
    virtual int    columnsInRow () const;
    virtual int    columnIndex  (const String & columnName);
    virtual int    rowIndex     (const int keyColumn, const String & keyValue);
    virtual T      get          (const int row, const int column) const;
    virtual String getString    (const int row, const int column) const;

    using Table::rows;
    using Table::columns;
    using Table::rowsInColumn;
    using Table::columnsInRow;
    using Table::get;
    using Table::getString;

    virtual MatrixAbstract<T> * getMatrix (const char * resource = 0);  ///< @param resource A cell anchor, if appropriate.
};

#ifdef n2a_FP
template<class T> extern SHARED TableSheet<T> * tableHelperSheet (const String & fileName, int exponent, TableSheet<T> * oldHandle = 0);
#else
template<class T> extern SHARED TableSheet<T> * tableHelperSheet (const String & fileName,               TableSheet<T> * oldHandle = 0);
#endif

template<class T>
struct LoadTableXSV : public ParseXSV
{
    TableSheet<T> * table;
    int             fillN;
    int             fillS;
#   ifdef n2a_FP
    int             exponent;
#   endif

    LoadTableXSV ();
    virtual bool processLine (std::vector<String> & parts);
};

/**
    Similar to IteratorSparse, except that we handle a coordinate offset.
**/
template<class T>
struct IteratorSparseRegion : public IteratorNonzero<T>
{
    MatrixSparseRegion<T> *            S;
    typename std::map<int,T>::iterator it;      // current column iterator
    typename std::map<int,T>::iterator end;     // of current column

    IteratorSparseRegion (MatrixSparseRegion<T> * S);

    virtual bool next ();
};

template<class T> struct InputXSV;

/**
    Special sparse matrix for SONATA edge lists, backed by XSV data.
    See comments on class MatrixSonataEdgesHDF.
**/
template<class T>
struct SHARED MatrixSonataEdgesXSV : public MatrixAbstract<T>
{
    InputXSV<T> *             input;     ///< Table that backs this object. We own it and manage its lifetime. It is not placed in Simulator::holders.
    bool                      haveColumns;

    // Data for main iterator.
    uint64_t                  row;       ///< Next position of sparse iterator.
    int                       colSource;
    int                       colTarget;

    // Data for attributes that track main iterator.
    MatrixSonataEdgesXSV<T> * track;
    String                    attribute;
    int                       colAttribute;
    T                         emptyValue;
    T                         tempResult;

#   ifdef n2a_FP
    MatrixSonataEdgesXSV (const String & fileName, const String & attribute, T emptyValue, int exponent);
#   else
    MatrixSonataEdgesXSV (const String & fileName, const String & attribute, T emptyValue);
#   endif
    ~MatrixSonataEdgesXSV ();
    virtual uint32_t classID () const;

    virtual T   get         (const int row, const int column = 0) const;
    virtual T & operator () (const int row, const int column = 0) const;
    virtual int rows        () const;
    virtual int columns     () const;
};

template<class T>
struct SHARED IteratorSonataEdgesXSV : public IteratorNonzero<T>
{
    MatrixSonataEdgesXSV<T> & A;

    IteratorSonataEdgesXSV (MatrixSonataEdgesXSV<T> * A);

    virtual bool next ();
};

#ifdef HAVE_HDF

struct SHARED SubHolderHDF
{
    String     fileName;  ///< To retrieve record in "files".
    H5::H5File file;
    int        users;
    std::mutex mutexFile;  ///< Serialize access to a given open file, since HDF is not thread-safe.

    static std::map<String,SubHolderHDF*> files;  ///< Keep track of all open HDF files in the app (regardless of which simulation they belong to). These can be shared by multiple InputHDF objects.
    static std::mutex                     mutexFiles;

    SubHolderHDF (const String & fileName);
    static SubHolderHDF * allocate (const String & fileName);  ///< Returns pointer to sub-holder, or null if file can't be opened.
    void allocate ();  ///< Increment reference count.
    void release ();  ///< Decrement reference count. When last reference is released, destroys the sub-holder and removes it from "files".
};

template<class T>
struct SHARED TableHDF : public Table<T>, public MatrixAbstract<T>
{
    SubHolderHDF *                 sub;
    String                         resource;         ///< Path to resource inside HDF file.
    bool                           rootIsGroup;      ///< Root can be either a Dataset or a Group. This indicates which one.
    H5::Group                      rootGroup;
    H5::DataSet                    rootDataSet;
    H5::Group                      sonataPopulation; ///< Population node, for finding related resources. INVALID_HID if not a SONATA file.
    bool                           sonataEdges;      ///< root is an attribute associated with a SONATA style sparse edge list.
    bool                           sonataSpikes;     ///< root is a group that contains SONATA style input spikes.
    std::vector<hsize_t>           dims;             ///< Size of data. Gets modified to always be 2D.
    int                            dimCount;         ///< Original length of "dims"
    std::unordered_map<String,int> rowMap;
    std::unordered_map<String,int> columnMap;
    std::vector<String>            headers;          ///< The inverse of columnMap
    hsize_t *                      start;            ///< For accessing data. This avoids recreating the object every time.
    hsize_t *                      count;            ///< see "start"
    T                              tempResult;       ///< To fake a reference in operator().
#   ifdef n2a_FP
    int                            exponent;
#   endif
    MatrixAbstract<T> *            A;                ///< Matrix handed out by getMatrix(). That function ignores the resource parameter, so there is only one per TableHDF instance.

    static const int chunkSize = 1000000;

    /**
        @param fileName To the HDF file. Not the same as the key for looking Holder. Specifically, the
        holder key includes both HDF file path and path to resource inside HDF file. Here, we are only
        interested in the actual path to file, so we can keep track of how many holders are using the file.
        @param resource To the resource inside the HDF file.
    **/
    TableHDF (const String & filePath, const String & resource, T emptyValue);
    virtual ~TableHDF ();
    virtual uint32_t classID () const;

    virtual int    rows        () const;
    virtual int    columns     () const;
    virtual int    columnIndex (const String & columnName);
    virtual int    rowIndex    (int keyColumn, const String & keyValue);
    virtual T      get         (const int row, const int column) const;
    virtual String getString   (const int row, const int column) const;
    virtual T &    operator () (const int row, const int column) const;  ///< Thin wrapper around get(), just to satisfy the Matrix interface. Doesn't actually allow writing of elements.

    using Table::rows;
    using Table::columns;
    using Table::rowsInColumn;
    using Table::columnsInRow;
    using Table::get;
    using Table::getString;

    virtual MatrixAbstract<T> * getMatrix (const char * resource = 0);  ///< @param resource Ignored
};

#ifdef n2a_FP
template<class T> extern SHARED TableHDF<T> * tableHelperHDF (const String & fileName, const String & resource, int exponent, TableHDF<T> * oldHandle = 0);
#else
template<class T> extern SHARED TableHDF<T> * tableHelperHDF (const String & fileName, const String & resource,               TableHDF<T> * oldHandle = 0);
#endif

template<class T>
struct SHARED IteratorNonzeroHDF : public IteratorNonzero<T>
{
    TableHDF<T> *  table;
    std::vector<T> chunk;     ///< Buffered 2D chunk, row major, covering whole rows.
    hsize_t        start[2]; ///< Coordinates for current region in "data".
    hsize_t        count[2];

    IteratorNonzeroHDF (TableHDF<T> * table);

    virtual bool next ();
};

template<class T>
struct SHARED MatrixSonataEdgesHDF : public MatrixAbstract<T>
{
    TableHDF<T> *             table;
    hsize_t                   start;     ///< For current block of data.
    hsize_t                   count;

    // Data for matrix that backs the sparse iterator.
    H5::DataSet               datasetSource;
    H5::DataSet               datasetTarget;
    uint64_t                  rowCount;
    uint64_t                  row;       ///< Current position of sparse iterator. Initially at max value, so it rolls over to 0 on first increment.

    // Data for attribute matrix that tracks the iterator.
    MatrixSonataEdgesHDF<T> * track;  // For attribute matrices, this refers to the matrix backing the sparse iterator which we are tracking.
    H5::DataSet               datasetAttribute;
    std::vector<T>            chunkAttribute;
    T                         tempResult;
#   ifdef n2a_FP
    int                       exponent;
#   endif

    /**
        @param key Combined file name and resource name inside the HDF file.
        The name of the main iterator will be derived by popping the last element off the resource name.
    **/
    MatrixSonataEdgesHDF (TableHDF<T> * table);
    virtual uint32_t classID () const;

    virtual T   get         (const int row, const int column = 0) const;
    virtual T & operator () (const int row, const int column = 0) const;
    virtual int rows        () const;
    virtual int columns     () const;
};

template<class T>
struct IteratorSonataEdgesHDF : public IteratorNonzero<T>
{
    MatrixSonataEdgesHDF<T> & A;
    std::vector<uint64_t>     chunkSource;
    std::vector<uint64_t>     chunkTarget;

    IteratorSonataEdgesHDF (MatrixSonataEdgesHDF<T> * A);

    virtual bool next ();
};

template<class T>
struct SHARED MatrixSonataSpikesHDF : public MatrixAbstract<T>
{
    TableHDF<T> *         table;
    H5::DataSet           datasetTime;
    std::vector<uint64_t> columnIDs;
    std::vector<uint64_t> columnPointers;
    uint64_t              rows_;  // Tallest column seen.
    T                     tempResult;

    MatrixSonataSpikesHDF (TableHDF<T> * table);
    virtual uint32_t classID () const;

    virtual T   get         (const int row, const int column = 0) const;
    virtual T & operator () (const int row, const int column = 0) const;
    virtual int rows        () const;
    virtual int columns     () const;
};

#endif

template<class T>
struct SHARED ImageInput : public Holder
{
#   ifdef HAVE_FFMPEG
    n2a::VideoIn * video;
#   endif

    n2a::Image                           image;
    String                               pattern;     ///< printf pattern for generating sequence file names. Includes full path to directory where sequence resides. If empty, then this is not a sequence or we are using FFmpeg to handle it.
    int                                  index;       ///< of current image in sequence
    T                                    t;           ///< next PTS for video, or next frame number for sequence
    double                               framePeriod; ///< for converting PTS to sequence number. Nonzero only when handling a sequence through FFmpeg.
    std::unordered_map<String,Matrix<T>> channels;

    ImageInput (const String & fileName);
    ~ImageInput ();

#   ifdef n2a_FP
    Matrix<T> get (String channelName, T now, bool step, int exponent);
#   else
    Matrix<T> get (String channelName, T now, bool step);  ///< @param step Indicates one frame step per cycle. If true, then "now" is $t. If false, then "now" is desired PTS in seconds.
#   endif
};
template<class T> SHARED ImageInput<T> * imageInputHelper (const String & fileName, ImageInput<T> * oldHandle = 0);

#ifdef HAVE_GL

struct LightLocation
{
    GLint infinite;
    GLint position;
    GLint direction;
    GLint ambient;
    GLint diffuse;
    GLint specular;
    GLint spotExponent;
    GLint spotCutoff;
    GLint attenuation0;
    GLint attenuation1;
    GLint attenuation2;

    LightLocation (GLuint program, int index);
};

struct SHARED Light
{
    bool  infinite;
    float position[3];
    float direction[3];
    float ambient[3];
    float diffuse[3];
    float specular[3];
    float spotExponent;
    float spotCutoff;  // cos(cutoff) rather than raw angle
    float attenuation0;
    float attenuation1;
    float attenuation2;

    void clear ();  // Reset all values to default.
    void setUniform (const LightLocation & l, const Matrix<float> & view);
};

struct SHARED Material
{
    float ambient[3];
    float diffuse[4];
    float emission[3];
    float specular[3];
    float shininess;

    static GLint locAmbient;
    static GLint locDiffuse;
    static GLint locEmission;
    static GLint locSpecular;
    static GLint locShininess;

    Material ();
    void setUniform () const;
};

void put       (std::vector<GLfloat> & vertices,                  float x, float y, float z, float n[3]);
void put       (std::vector<GLfloat> & vertices, Matrix<float> f, float x, float y, float z, float nx, float ny, float nz);
int  putUnique (std::vector<GLfloat> & vertices,                  float x, float y, float z);

void icosphere          (std::vector<GLfloat> & vertices, std::vector<GLuint> & indices);
void icosphereSubdivide (std::vector<GLfloat> & vertices, std::vector<GLuint> & indices);
int  split              (std::vector<GLfloat> & vertices, int v0, int v1);

#ifdef n2a_FP
                  SHARED void setVector (float target[], const Matrix<int> & value, int exponent);
#else
template<class T> SHARED void setVector (float target[], const Matrix<T>   & value);
#endif

#endif

// Utility functions to set material colors. May also be useful in other contexts.
                  SHARED void setColor (float target[], uint32_t            color, bool withAlpha);
template<class T> SHARED void setColor (float target[], const Matrix<T>   & color, bool withAlpha);
#ifdef n2a_FP
template<int>     SHARED void setColor (float target[], const Matrix<int> & color, bool withAlpha);  // exponent = 0, which gives full range [0,1]
#endif

template<class T>
struct SHARED ImageOutput : public Holder
{
    String path;    // prefix of fileName, not including suffix (format)
    String format;  // Name of format as recognized by supporting libraries. For video, can be set by keyword parameter. For image sequence, derived automatically from fileName suffix.
    bool   hold;    // Store a single frame rather than an image sequence.
    bool   dirCreated;

    int      width;
    int      height;
    uint32_t clearColor;  // kept in sync with cv

    T                t;
    int              frameCount; // Number of frames actually written so far.
    bool             haveData;   // indicates that something has been drawn since last write to disk
    n2a::CanvasImage canvas;     // Current image being built.
    bool             opened;     // Indicates that video or image-sequence output has been configured. This is delayed until first write to disk, so user can set video parameters.
#   ifdef HAVE_FFMPEG
    n2a::VideoOut *  video;
    String           codec;      // Optional specification of video encoder. Default is derived from container.
    double           timeScale;  // Multiply simtime (t) by this value to get PTS. Zero (default value) means 24fps, regardless of simtime.
#   endif

#   ifdef HAVE_GL
#     ifdef _WIN32
    HWND   window;
    HDC    dc;
    HGLRC  rc;
#     endif
    bool                         extensionsBound;  // Indicates that extension function addresses have been bound.
    GLuint                       program;
    GLuint                       rboColor;
    GLuint                       rboDepth;
    GLint                        locVertexPosition;
    GLint                        locVertexNormal;
    GLint                        locMatrixModelView;
    GLint                        locMatrixNormal;
    GLint                        locMatrixProjection;
    std::vector<LightLocation *> locLights;
    GLint                        locEnabled;
    bool                         have3D;
    float                        cv[4];  // Color vector; kept in sync with clearColor
    int                          lastWidth;
    int                          lastHeight;
    Matrix<float>                projection;
    Matrix<float>                view;
    Matrix<float>                nextProjection;  // Initialized to 4x4, all zeros. If all zeros at start of 3D drawing, then we generate a default matrix based on current view size.
    Matrix<float>                nextView;        // Initialized to 4x4 identity, which is also the default.
    std::map<int,Light *>        lights;
    std::map<String,GLuint>      buffers;
    int                          sphereStep;
    std::vector<GLfloat>         sphereVertices;
    std::vector<GLuint>          sphereIndices;
#   endif

    ImageOutput (const String & fileName);
    virtual ~ImageOutput ();
    void open ();  // Subroutine of writeImage()

    void setClearColor (uint32_t          color);
    void setClearColor (const Matrix<T> & color);  // Converting to Matrix<T> lets us be agnostic about orientation, unless it is already Matrix<T> and there are gaps between columns (unlikely).

    void next (T now);
#   ifdef n2a_FP
    // All pixel-valued arguments must agree on exponent. "now" is in time exponent.
    T drawDisc    (T now, bool raw, const MatrixFixed<T,3,1> & center, T radius,                               int exponent, uint32_t color);
    T drawSquare  (T now, bool raw, const MatrixFixed<T,3,1> & center, T w, T h,                               int exponent, uint32_t color);
    T drawSegment (T now, bool raw, const MatrixFixed<T,3,1> & p1, const MatrixFixed<T,3,1> & p2, T thickness, int exponent, uint32_t color);
#   else
    T drawDisc    (T now, bool raw, const MatrixFixed<T,3,1> & center, T radius,                                             uint32_t color);
    T drawSquare  (T now, bool raw, const MatrixFixed<T,3,1> & center, T w, T h,                                             uint32_t color);
    T drawSegment (T now, bool raw, const MatrixFixed<T,3,1> & p1, const MatrixFixed<T,3,1> & p2, T thickness,               uint32_t color);
#   endif
    void writeImage ();

    // 3D drawing functions.
#   ifdef HAVE_GL
    Light * addLight    (int index);
    void    removeLight (int index);
    bool next3D (const Matrix<float> * model, const Material & material);  // Additional setup work done by 3D draw functions. Does both one-time initialization and per-frame initialization, as needed.
    GLuint getBuffer (String name, bool vertices);  // vertices==true indicates that this is a vertex array; vertices==false indicates that this is an index array
#     ifdef n2a_FP
      // These functions are written as float to force immediate type conversion at the point of call. Then we scale by exponent.
    T drawCube     (T now, const Material & material, const Matrix<float> & model,       int exponentP);
    T drawCylinder (T now, const Material & material, const MatrixFixed<float,3,1> & p1, int exponentP, float r1, int exponentR, const MatrixFixed<float,3,1> & p2, float r2 = -1, int cap1 = 0, int cap2 = 0, int steps = 6, int stepsCap = -1);
    T drawPlane    (T now, const Material & material, const Matrix<float> & model,       int exponentP);
    T drawSphere   (T now, const Material & material, const Matrix<float> & model,       int exponentP, int steps = 1);
#     else
    T drawCube     (T now, const Material & material, const Matrix<float> & model);
    T drawCylinder (T now, const Material & material, const MatrixFixed<float,3,1> & p1,                float r1,                const MatrixFixed<float,3,1> & p2, float r2 = -1, int cap1 = 0, int cap2 = 0, int steps = 6, int stepsCap = -1);
    T drawPlane    (T now, const Material & material, const Matrix<float> & model);
    T drawSphere   (T now, const Material & material, const Matrix<float> & model, int steps = 1);
#     endif
#   endif
};
template<class T> SHARED ImageOutput<T> * imageOutputHelper (const String & fileName, ImageOutput<T> * oldHandle = 0);

template<class T>
struct SHARED Mfile : public Holder
{
    n2a::MDoc *                           doc;
    std::map<String,MatrixAbstract<T>*>   matrices;  // Could use unordered_map. Generally, there will be very few entries (like 1), so not sure which will cost the least.
    std::map<String,std::vector<String>*> childKeys;

    Mfile (const String & fileName);
    virtual ~Mfile ();

#   ifdef n2a_FP
    MatrixAbstract<T> * getMatrix (const char * delimiter, const std::vector<String> & path, int exponent);
#   else
    MatrixAbstract<T> * getMatrix (const char * delimiter, const std::vector<String> & path);
#   endif
    String getChildKey (const char * delimiter, const std::vector<String> & path, const int index);
};
template<class T> SHARED Mfile<T> * MfileHelper (const String & fileName, Mfile<T> * oldHandle = 0);

SHARED std::vector<String> keyPath (const char * delimiter, const std::vector<String> & path);  ///< Converts any path elements with delimiters into separate elements.
template<typename... Args> std::vector<String> keyPath (const char * delimiter, Args... keys) {return keyPath (delimiter, {keys...});}

/// Convert date to Unix time. Dates before epoch will be negative.
template<class T> SHARED T convertDate (const String & field, T defaultValue);

template<class T>
struct SHARED InputLine
{
    T              line;  ///< Can be either time or integer row number.
    std::vector<T> values;
};

template<class T>
struct SHARED InputHolder : public Holder
{
    std::mutex                     mutexLine;  ///< Threads are expected to agree on current line. However, only one thread should advance current line, so getRow() should include a critical section.
    InputLine<T> *                 current;
    InputLine<T> *                 next;
    Matrix<T> *                    A;
    T                              Alast;
    int                            columnCount;
    std::unordered_map<String,int> columnMap;
    int                            timeColumn;
    bool                           timeColumnSet;
    bool                           time;     ///< mode
    bool                           smooth;   ///< mode; when true, time must also be true
    T                              epsilon;  ///< for time values
#   ifdef n2a_FP
    int                            exponent;    ///< of value returned by get()
    int                            exponentRow; ///< of row value passed into get()
#   endif

    InputHolder (const String & fileName);
    virtual ~InputHolder ();

    virtual void getRow (T row) = 0; ///< subroutine of get()
    T            get    (T row, const String & column);
    T            get    (T row, T column);
    Matrix<T>    get    (T row);
};

template<class T>
struct SHARED InputXSV : public InputHolder<T>
{
    // Need "using" for GCC, but not for MSVC.
    using InputHolder<T>::mutexLine;
    using InputHolder<T>::current;
    using InputHolder<T>::next;
    using InputHolder<T>::A;
    using InputHolder<T>::columnCount;
    using InputHolder<T>::columnMap;
    using InputHolder<T>::timeColumn;
    using InputHolder<T>::timeColumnSet;
    using InputHolder<T>::time;
    using InputHolder<T>::epsilon;
#   ifdef n2a_FP
    using InputHolder<T>::exponent;
    using InputHolder<T>::exponentRow;
#   endif

    std::istream *            in;
    ParseXSV                  parser;
    std::vector<String>       parts;
    std::list<InputLine<T> *> buffer;

    InputXSV (const String & fileName);
    virtual ~InputXSV ();

    virtual void getRow (T row);

    /**
        Buffer extra data rows.
        Called after getRow() has already established the current row.
        Stops rows from leaving buffer, even if "current" moves ahead.
        Rows remain buffered until release() is called.
        @return The effective number of rows buffered.
    **/
    int readAhead (int rowCount);

    /**
        Allow rows that come before "current" to be dropped from buffer.
    **/
    void release ();
};
#ifdef n2a_FP
template<class T> SHARED InputXSV<T> * inputHelperXSV (const String & fileName, int exponent, int exponentRow, InputXSV<T> * oldHandle = 0);
#else
template<class T> SHARED InputXSV<T> * inputHelperXSV (const String & fileName,                                InputXSV<T> * oldHandle = 0);
#endif

#ifdef HAVE_HDF

template<class T>
struct SHARED InputHDF : public InputHolder<T>
{
    using InputHolder<T>::fileName;
    using InputHolder<T>::mutexLine;
    using InputHolder<T>::current;
    using InputHolder<T>::next;
    using InputHolder<T>::columnCount;
    using InputHolder<T>::timeColumn;
    using InputHolder<T>::timeColumnSet;
    using InputHolder<T>::time;
    using InputHolder<T>::smooth;
    using InputHolder<T>::epsilon;
#   ifdef n2a_FP
    using InputHolder<T>::exponent;
    using InputHolder<T>::exponentRow;
#   endif

    String         resource;
    SubHolderHDF * sub;
    H5::DataSet    data;
    bool           warning;
    bool           nwb;
    int            rowCount;
    T              startingTime;
    T              period;
    T *            timestamps;  // If null, use startingTime+N*period. If non-null, treat this as time column.
    int            lastRow;     // When using timestamps, where to start search.
    int            dimCount;
    hsize_t *      start;       // For accessing data. This avoids recreating the object every time.
    hsize_t *      count;       // ditto

    InputHDF (const String & fileName, const String & resource);
    virtual ~InputHDF ();

    virtual void getRow  (T row);
    int          rowFromLine (T line);
    T            lineFromRow (int row);
    void         getSlab (hsize_t row, hsize_t rowCount, T * values);  ///< Fetch a single complete row.
};

#ifdef n2a_FP
template<class T> SHARED InputHDF<T> * inputHelperHDF (const String & fileName, const String & path, int exponent, int exponentRow, InputHDF<T> * oldHandle = 0);
#else
template<class T> SHARED InputHDF<T> * inputHelperHDF (const String & fileName, const String & path,                                InputHDF<T> * oldHandle = 0);
#endif

#endif  // HAVE_HDF

template<class T>
struct SHARED OutputHolder : public Holder
{
    std::recursive_mutex                   mutexLine;       ///< Synchronize anything that changes content or structure.
    bool                                   raw;             ///< Indicates that column is an exact index.
    std::ostream *                         out;
    String                                 columnFileName;
    std::unordered_map<String,int>         columnMap;
    std::vector<std::map<String,String> *> columnMode;
    std::vector<float>                     columnValues;
    int                                    columnsPrevious; ///< Number of columns written in previous cycle.
    bool                                   traceReceived;   ///< Indicates that at least one column was touched during the current cycle.
    T                                      t;

    OutputHolder (const String & fileName);
    virtual ~OutputHolder ();

    void trace (T now);  ///< Subroutine for other trace() functions.
    int  getColumnIndex (const String & column);  ///< Retrieves index of existing column, or creates new column.
    void setMode (int index, const char * mode, const char * lineSeparator = ",", const char * keySeparator = "=");  ///< Subroutine for other trace() functions.
#   ifdef n2a_FP
    T         trace (T now, const String & column, T                 value, int exponent, const char * mode = 0);
    Matrix<T> trace (T now, const String & column, const Matrix<T> & A,     int exponent, const char * mode = 0);
#   else
    T         trace (T now, const String & column, T                 value,               const char * mode = 0);
    Matrix<T> trace (T now, const String & column, const Matrix<T> & A,                   const char * mode = 0);
#   endif
    void writeTrace ();
    void writeModes ();
};
template<class T> SHARED OutputHolder<T> * outputHelper (const String & fileName, OutputHolder<T> * oldHandle = 0);


#endif
