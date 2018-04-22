#ifndef n2a_io_h
#define n2a_io_h


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

class IteratorNonzero
{
public:
    int   row;
    int   column;
    float value;

    virtual bool next () = 0;  // Advances to next nonzero element. Returns false if no more are available.
};

class IteratorSkip : public IteratorNonzero
{
public:
    fl::Matrix<float> * A;
    int   nextRow;
    int   nextColumn;
    float nextValue;

    IteratorSkip (fl::Matrix<float> * A);

    virtual bool next ();
    void         getNext ();
};

class IteratorSparse : public IteratorNonzero
{
public:
    fl::MatrixSparse<float> *     A;
    int                           columns;
    std::map<int,float>::iterator it;

    IteratorSparse (fl::MatrixSparse<float> * A);
    virtual bool next ();
};

class MatrixInput : public Holder
{
public:
    fl::MatrixAbstract<float> * A;  // Will be either Matrix or MatrixSparse, determined by matrixHelper when reading the file.

    MatrixInput (const String & fileName);
    virtual ~MatrixInput ();

    float get     (float row, float column);
    float getRaw  (float row, float column);
    int   rows    ();
    int   columns ();

    IteratorNonzero * getIterator ();  // Returns an object that iterates over nonzero elements of A.
};
extern MatrixInput * matrixHelper (const String & fileName, MatrixInput * oldHandle = 0);

class InputHolder : public Holder
{
public:
    std::istream *                 in;
    float                          currentLine;
    float *                        currentValues;
    int                            currentCount;
    float                          nextLine;
    float *                        nextValues;
    int                            nextCount;
    int                            columnCount;
    std::unordered_map<String,int> columnMap;
    int                            timeColumn;
    bool                           timeColumnSet;
    bool                           time;     ///< mode
    float                          epsilon;  ///< for time values

    InputHolder (const String & fileName);
    virtual ~InputHolder ();

    void  getRow     (float row); ///< subroutine of get() and getRaw()
    int   getColumns ();          ///< Returns number of columns seen so far.
    float get        (float row, const String & column);
    float get        (float row, float column);
    float getRaw     (float row, float column);
};
extern InputHolder * inputHelper (const String & fileName, InputHolder * oldHandle = 0);

class OutputHolder : public Holder
{
public:

    bool                           raw; ///< Indicates that column is an exact index.
    std::ostream *                 out;
    std::unordered_map<String,int> columnMap;
    std::vector<float>             columnValues;
    int                            columnsPrevious; ///< Number of columns written in previous cycle.
    bool                           traceReceived;   ///< Indicates that at least one column was touched during the current cycle.
    double                         t;

    OutputHolder (const String & fileName);
    virtual ~OutputHolder ();

    void trace (float now);  ///< Subroutine for other trace() functions.
    void trace (float now, const String & column, float value);
    void trace (float now, float          column, float value);
    void writeTrace ();
};
extern OutputHolder * outputHelper (const String & fileName, OutputHolder * oldHandle = 0);
extern void           outputClose ();  ///< Close all OutputHolders


#endif