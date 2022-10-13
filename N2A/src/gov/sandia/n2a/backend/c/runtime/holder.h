/*
Copyright 2018-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_holder_h
#define n2a_holder_h


#include "nosys.h"
#include "StringLite.h"
#include "matrix.h"
#include "MNode.h"
#include "canvas.h"

#include <vector>
#include <unordered_map>


/**
    Utility class for reading/accessing command-line parameters.
    These are primarily intended to override parameters within the model.
**/
template<class T>
class Parameters
{
public:
    std::unordered_map<String,String> namedValues;

    void   parse (const String & line);
    void   parse (int argc, const char * argv[]);
    void   read  (const String & parmFileName);
    void   read  (std::istream & stream);

    T      get   (const String & name, T defaultValue = 0) const;
    String get   (const String & name, const String & defaultValue = "") const;
};

class Holder
{
public:
    String fileName;
    Holder (const String & fileName);
    virtual ~Holder ();
};

template<class T>
class IteratorNonzero
{
public:
    int row;
    int column;
    T   value;

    virtual bool next () = 0;  // Advances to next nonzero element. Returns false if no more are available.
};

template<class T>
class IteratorSkip : public IteratorNonzero<T>
{
public:
    Matrix<T> * A;
    int nextRow;
    int nextColumn;
    T   nextValue;

    IteratorSkip (Matrix<T> * A);

    virtual bool next ();
    void         getNext ();
};

template<class T>
class IteratorSparse : public IteratorNonzero<T>
{
public:
    MatrixSparse<T> *                  A;
    int                                columns;
    typename std::map<int,T>::iterator it;

    IteratorSparse (MatrixSparse<T> * A);
    virtual bool next ();
};

extern int convert (String input, int exponent);

template<class T>
class MatrixInput : public Holder
{
public:
    MatrixAbstract<T> * A;  // Will be either Matrix or MatrixSparse, determined by matrixHelper when reading the file.

    MatrixInput (const String & fileName);
    virtual ~MatrixInput ();
};
#ifdef n2a_FP
template<class T> extern MatrixInput<T> * matrixHelper (const String & fileName, int exponent, MatrixInput<T> * oldHandle = 0);
#else
template<class T> extern MatrixInput<T> * matrixHelper (const String & fileName,               MatrixInput<T> * oldHandle = 0);
#endif

template<class T> extern IteratorNonzero<T> * getIterator (MatrixAbstract<T> * A);  // Returns an object that iterates over nonzero elements of A.

template<class T>
class ImageInput : public Holder
{
public:
    ImageInput (const String & fileName);
};
template<class T> extern ImageInput<T> * imageInputHelper (const String & fileName, ImageInput<T> * oldHandle = 0);

template<class T>
class ImageOutput : public Holder
{
public:
    String path;    // prefix of fileName, not including suffix (format)
    String format;  // name of format as recognized by supporting libraries
    bool   hold;    // Store a single frame rather than an image sequence.
    bool   dirCreated;

    int      width;
    int      height;
    uint32_t clearColor;

    // TODO: handle video streams using FFMPEG.
    T                t;
    int              frameCount; // Number of frames actually written so far.
    n2a::CanvasImage canvas;     // Current image being built.
    bool             haveData;   // indicates that something has been drawn since last write to disk

    ImageOutput (const String & fileName);
    virtual ~ImageOutput ();

    void next        (T now);
#   ifdef n2a_FP
    // All pixel-valued arguments must agree on exponent. "now" is in time exponent.
    T drawDisc    (T now, bool raw, const MatrixFixed<T,3,1> & center, T radius,                               int exponent, uint32_t color);
    T drawBlock   (T now, bool raw, const MatrixFixed<T,3,1> & center, T w, T h,                               int exponent, uint32_t color);
    T drawSegment (T now, bool raw, const MatrixFixed<T,3,1> & p1, const MatrixFixed<T,3,1> & p2, T thickness, int exponent, uint32_t color);
#   else
    T drawDisc    (T now, bool raw, const MatrixFixed<T,3,1> & center, T radius,                               uint32_t color);
    T drawBlock   (T now, bool raw, const MatrixFixed<T,3,1> & center, T w, T h,                               uint32_t color);
    T drawSegment (T now, bool raw, const MatrixFixed<T,3,1> & p1, const MatrixFixed<T,3,1> & p2, T thickness, uint32_t color);
#   endif
    void writeImage  ();
};
template<class T> extern ImageOutput<T> * imageOutputHelper (const String & fileName, ImageOutput<T> * oldHandle = 0);

template<class T>
class Mfile : public Holder
{
public:
    n2a::MDoc *                          doc;
    std::map<String,MatrixAbstract<T> *> matrices;  // Could use unordered_map. Generally, there will be very few entries (like 1), so not sure which will cost the least.

    Mfile (const String & fileName);
    virtual ~Mfile ();

#   ifdef n2a_FP
    MatrixAbstract<T> * getMatrix (const std::vector<String> & path, int exponent);
#   else
    MatrixAbstract<T> * getMatrix (const std::vector<String> & path);
#   endif
};
template<class T> extern Mfile<T> * MfileHelper (const String & fileName, Mfile<T> * oldHandle = 0);

extern std::vector<String> keyPath (const std::vector<String> & path);  ///< Converts any path elements with delimiters (/) into separate elements.
template<typename... Args> std::vector<String> keyPath (Args... keys) {return keyPath ({keys...});}

template<class T>
class InputHolder : public Holder
{
public:
    std::istream *                 in;
    T                              currentLine;
    T *                            currentValues;
    int                            currentCount;
    T                              nextLine;
    T *                            nextValues;
    int                            nextCount;
    Matrix<T> *                    A;
    T                              Alast;
    int                            columnCount;
    std::unordered_map<String,int> columnMap;
    int                            timeColumn;
    bool                           timeColumnSet;
    bool                           time;     ///< mode
    bool                           smooth;   ///< mode; when true, time must also be true
    char                           delimiter;
    bool                           delimiterSet;
    T                              epsilon;  ///< for time values
#   ifdef n2a_FP
    int                            exponent;  ///< of value returned by get()
#   endif

    InputHolder (const String & fileName);
    virtual ~InputHolder ();

    void      getRow (T row); ///< subroutine of get()
    T         get    (T row, const String & column);
    T         get    (T row, T column);
    Matrix<T> get    (T row);
};
#ifdef n2a_FP
template<class T> extern InputHolder<T> * inputHelper (const String & fileName, int exponent, InputHolder<T> * oldHandle = 0);
#else
template<class T> extern InputHolder<T> * inputHelper (const String & fileName,               InputHolder<T> * oldHandle = 0);
#endif

template<class T>
class OutputHolder : public Holder
{
public:
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

    void trace (T now);               ///< Subroutine for other trace() functions.
    void addMode (const char * mode); ///< Subroutine for other trace() functions.
#   ifdef n2a_FP
    T         trace (T now, const String & column, T                 value, int exponent, const char * mode = 0);
    Matrix<T> trace (T now, const String & column, const Matrix<T> & A,     int exponent, const char * mode = 0);
    T         trace (T now, T              column, T                 value, int exponent, const char * mode = 0);
#   else
    T         trace (T now, const String & column, T                 value,               const char * mode = 0);
    Matrix<T> trace (T now, const String & column, const Matrix<T> & A,                   const char * mode = 0);
    T         trace (T now, T              column, T                 value,               const char * mode = 0);
#   endif
    void writeTrace ();
    void writeModes ();
};
template<class T> extern OutputHolder<T> * outputHelper (const String & fileName, OutputHolder<T> * oldHandle = 0);


#endif
