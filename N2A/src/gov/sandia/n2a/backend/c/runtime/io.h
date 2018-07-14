#ifndef n2a_io_h
#define n2a_io_h


#include "nosys.h"
#include "String.h"
#include "fl/matrix.h"

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
    fl::Matrix<T> * A;
    int nextRow;
    int nextColumn;
    T   nextValue;

    IteratorSkip (fl::Matrix<T> * A);

    virtual bool next ();
    void         getNext ();
};

template<class T>
class IteratorSparse : public IteratorNonzero<T>
{
public:
    fl::MatrixSparse<T> *              A;
    int                                columns;
    typename std::map<int,T>::iterator it;

    IteratorSparse (fl::MatrixSparse<T> * A);
    virtual bool next ();
};

template<class T>
class MatrixInput : public Holder
{
public:
    fl::MatrixAbstract<T> * A;  // Will be either Matrix or MatrixSparse, determined by matrixHelper when reading the file.

    MatrixInput (const String & fileName);
    virtual ~MatrixInput ();

    T   get     (T row, T column);
    T   getRaw  (T row, T column);
    int rows    ();
    int columns ();

    IteratorNonzero<T> * getIterator ();  // Returns an object that iterates over nonzero elements of A.
};
template<class T> extern MatrixInput<T> * matrixHelper (const String & fileName, MatrixInput<T> * oldHandle = 0);

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
    T                              epsilon;  ///< for time values

    InputHolder (const String & fileName);
    virtual ~InputHolder ();

    void getRow     (T row); ///< subroutine of get() and getRaw()
    int  getColumns ();      ///< Returns number of columns seen so far.
    T    get        (T row, const String & column);
    T    get        (T row, T column);
    T    getRaw     (T row, T column);
};
template<class T> extern InputHolder<T> * inputHelper (const String & fileName, InputHolder<T> * oldHandle = 0);

template<class T>
class OutputHolder : public Holder
{
public:
    bool                           raw; ///< Indicates that column is an exact index.
    std::ostream *                 out;
    std::unordered_map<String,int> columnMap;
    std::vector<T>                 columnValues;
    int                            columnsPrevious; ///< Number of columns written in previous cycle.
    bool                           traceReceived;   ///< Indicates that at least one column was touched during the current cycle.
    T                              t;

    OutputHolder (const String & fileName);
    virtual ~OutputHolder ();

    void trace (T now);  ///< Subroutine for other trace() functions.
    void trace (T now, const String & column, T value);
    void trace (T now, T              column, T value);
    void writeTrace ();
};
template<class T> extern OutputHolder<T> * outputHelper (const String & fileName, OutputHolder<T> * oldHandle = 0);
extern void outputClose ();  ///< Close all OutputHolders


#endif
