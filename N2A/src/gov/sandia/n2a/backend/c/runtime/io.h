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
    Holder (const String & fileName) : fileName (fileName) {}
};
extern Holder * holderHelper (std::vector<Holder *> & holders, const String & fileName, Holder * oldHandle);

class MatrixInput : public Holder, public fl::Matrix<float>
{
public:
    MatrixInput (const String & fileName);

    float get    (float row, float column);
    float getRaw (float row, float column);
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
    float                          epsilon;  ///< for time values

    InputHolder (const String & fileName);
    ~InputHolder ();

    void  getRow     (float row, bool time);  ///< subroutine of get() and getRaw()
    int   getColumns (           bool time);  ///< Returns number of columns seen so far.
    float get        (float row, bool time, const String & column);
    float get        (float row, bool time, float column);
    float getRaw     (float row, bool time, float column);
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
    ~OutputHolder ();

    void trace (float now);  ///< Subroutine for other trace() functions.
    void trace (float now, const String & column, float value);
    void trace (float now, float          column, float value);
    void writeTrace ();
};
extern OutputHolder * outputHelper (const String & fileName, OutputHolder * oldHandle = 0);
extern void           outputClose ();  ///< Close all OutputHolders


#endif
