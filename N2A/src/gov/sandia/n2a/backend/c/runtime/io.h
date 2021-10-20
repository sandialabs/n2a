/*
Copyright 2018-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_io_h
#define n2a_io_h


#include "nosys.h"
#include "StringLite.h"
#include "matrix.h"

#include <vector>
#include <unordered_map>


// Holder and its utility functions are declared in each platform's io.h, but only defined once in runtime.cc
// The alternative would be a "io_common.h", but this seems like overkill for so few items.
class Holder
{
public:
    String fileName;
    Holder (const String & fileName);
    virtual ~Holder ();
};
extern Holder * holderHelper (std::vector<Holder *> & holders, const String & fileName, Holder * oldHandle);

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

template<class T>
class MatrixInput : public Holder
{
public:
    MatrixAbstract<T> * A;  // Will be either Matrix or MatrixSparse, determined by matrixHelper when reading the file.

    MatrixInput (const String & fileName);
    virtual ~MatrixInput ();

    IteratorNonzero<T> * getIterator ();  // Returns an object that iterates over nonzero elements of A.
};
#ifdef n2a_FP
template<class T> extern MatrixInput<T> * matrixHelper (const String & fileName, int exponent, MatrixInput<T> * oldHandle = 0);
#else
template<class T> extern MatrixInput<T> * matrixHelper (const String & fileName,               MatrixInput<T> * oldHandle = 0);
#endif

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
    int                            columnCount;
    std::unordered_map<String,int> columnMap;
    int                            timeColumn;
    bool                           timeColumnSet;
    bool                           time;     ///< mode
    char                           delimiter;
    bool                           delimiterSet;
    T                              epsilon;  ///< for time values
#   ifdef n2a_FP
    int                            exponent;  ///< of value returned by get()
#   endif

    InputHolder (const String & fileName);
    virtual ~InputHolder ();

    void getRow     (T row); ///< subroutine of get() and getRaw()
    int  getColumns ();      ///< Returns number of columns seen so far.
    T    get        (T row, const String & column);
    T    get        (T row, T column);
    T    getRaw     (T row, T column);
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
    void trace (T now, const String & column, T                 value, int exponent, const char * mode = 0);
    void trace (T now, const String & column, const Matrix<T> & A,     int exponent, const char * mode = 0);
    void trace (T now, T              column, T                 value, int exponent, const char * mode = 0);
#   else
    void trace (T now, const String & column, T                 value,               const char * mode = 0);
    void trace (T now, const String & column, const Matrix<T> & A,                   const char * mode = 0);
    void trace (T now, T              column, T                 value,               const char * mode = 0);
#   endif
    void writeTrace ();
    void writeModes ();
};
template<class T> extern OutputHolder<T> * outputHelper (const String & fileName, OutputHolder<T> * oldHandle = 0);
extern void outputClose ();  ///< Close all OutputHolders


#endif
