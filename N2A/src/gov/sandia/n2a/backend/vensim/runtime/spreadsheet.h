/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

#ifndef n2a_spreadsheet_h
#define n2a_spreadsheet_h

#include <runtime.h>
#include <shared.h>

/**
    Matrices which provide cell values for the entire sheet.
    In general, a cell will either be a number, a string, or empty.
    We don't know ahead of time whether the matrix is dense or sparse, so the
    exact type of matrix is decided by the loader.
**/
template<class T>
class SHARED Sheet
{
public:
    MatrixAbstract<T> *   numbers; ///< Dense matrix stores empty cells and strings as 0. Sparse matrix does not store them at all.
    MatrixAbstract<int> * strings; ///< 1-based indices into string collection. Empty cells and numbers are 0.
    int                   rows;
    int                   columns;

    ~Sheet ();
};

template<class T>
class SHARED Spreadsheet : public Holder
{
public:
    std::vector<String>          strings;  ///< collection of all strings that appear in the workbook
    std::map<String, Sheet<T> *> wb;       ///< workbook, a collection of worksheets
    Sheet<T> *                   first;    ///< The first sheet defined in the file. This is the default when no sheet is specified in cell address.
    String                       cell;     ///< The most recently parsed anchor cell address. Includes sheet name and coordinates.
    Sheet<T> *                   ws;       ///< Anchor sheet
    int                          ar;       ///< Anchor row
    int                          ac;       ///< Anchor column
#   ifdef n2a_FP
    int                          exponent; ///< of value returned by get()
#   endif

    Spreadsheet (const String & fileName);
    virtual ~Spreadsheet ();

    void parse   (const String & cell);       ///< Subroutine for all functions that take an anchor cell address.
    void parseA1 (const String & coordinate); ///< Process just the coordinates of a cell address.

    // These counts are always relative to an anchor cell.
    int rows         (const String & cell);
    int columns      (const String & cell);
    int rowsInColumn (const String & cell);
    int columnsInRow (const String & cell);

    T      get (const String & cell,                        T row = (T) 0, T column = (T) 0);
    String get (const String & cell, const String & prefix, T row = (T) 0, T column = (T) 0);

    IteratorNonzero<T> * getIterator (const String & cell);  // Returns an object that iterates over nonzero elements of doc
};
#ifdef n2a_FP
template<class T> extern SHARED Spreadsheet<T> * spreadsheetHelper (const String & fileName, int exponent, Spreadsheet<T> * oldHandle = 0);
#else
template<class T> extern SHARED Spreadsheet<T> * spreadsheetHelper (const String & fileName,               Spreadsheet<T> * oldHandle = 0);
#endif

#endif
