/*
Copyright 2021-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

/*
This software depends on two external software packages: miniz and pugixml. To update them,
download the following items:

https://pugixml.org -- Get the release zip, unpack, and place the following files in runtime:
    pugixml.cpp
    pugixml.hpp
    pugiconfig.hpp
    LICENSE.md --> update gov/sandia/n2a/ui/settings/licenses/pugixml

Uncomment the following defines in pugiconfig.hpp:
    #define PUGIXML_COMPACT
    #define PUGIXML_NO_XPATH
    #define PUGIXML_NO_STL
    #define PUGIXML_NO_EXCEPTIONS
    #define PUGIXML_HEADER_ONLY

https://github.com/richgel999/miniz -- Get the release zip, unpack, and place these files in runtime:
    miniz.c
    miniz.h
    LICENSE --> update gov/sandia/n2a/ui/settings/licenses/miniz

Uncomment the following define in miniz.h:
    #define MINIZ_NO_ARCHIVE_WRITING_APIS
*/

#include "spreadsheet.h"

#include "miniz.h"
#include "miniz.c"
#pragma GCC diagnostic ignored "-Wsubobject-linkage"
#include "pugixml.hpp"
#include "pugixml.cpp"

#include <math.h>
#include <Matrix.tcc>
#include <MatrixSparse.tcc>
#include <set>

template<class T>
Sheet<T>::~Sheet ()
{
    if (numbers) delete numbers;
    if (strings) delete strings;
}

String
unzipFile (mz_zip_archive * archive, const String & fileName)
{
    int index = mz_zip_reader_locate_file (archive, fileName.c_str (), 0, 0);
    if (index < 0) return "";
    mz_zip_archive_file_stat stat;
    if (! mz_zip_reader_file_stat (archive, index, &stat)) return "";
    int size = stat.m_uncomp_size;

    String result;
    result.reserve (size);
    if (mz_zip_reader_extract_to_mem (archive, index, (void *) result.c_str (), size, 0))
    {
        result.top = result.memory + size;
        *result.top = 0;  // null termination
    }
    return result;
}

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
Spreadsheet<T>::Spreadsheet (const String & fileName)
:   Holder (fileName)
{
    mz_zip_archive archive;
    mz_zip_zero_struct (&archive);
    if (! mz_zip_reader_init_file (&archive, fileName.c_str (), 0))
    {
        throw mz_zip_get_error_string (archive.m_last_error);
    }

    // Read workbook relationship file to determine paths to sheets, shared strings and styles.
    std::map<String,String> IDtarget;
    String sharedStringsPath;
    String stylesPath;
    pugi::xml_document workbookRels;
    String fileContents = unzipFile (&archive, "xl/_rels/workbook.xml.rels");
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
        fileContents = unzipFile (&archive, sharedStringsPath);
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
        fileContents = unzipFile (&archive, stylesPath);
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
    fileContents = unzipFile (&archive, "xl/workbook.xml");
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
        ws->numbers = N;
        ws->strings = S;
        int fillN = 0;
        int fillS = 0;
        const double fillThreshold = 0.5;

        pugi::xml_document worksheet;
        fileContents = unzipFile (&archive, target);
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

                    T v = atof (c.child_value ("v"));
                    int s = c.attribute ("s").as_int (-1);
                    if (dateStyles.find (s) != dateStylesEnd) v = (v - 25569) * 86400;  // Convert from Excel time to Unix time.
                    if (v == 0) continue;  // should we also check for NAN?
                    N->set (ar, ac, v);
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

    mz_zip_reader_end (&archive);
}

template<class T>
Spreadsheet<T>::~Spreadsheet ()
{
    for (auto s : wb) delete s.second;
}

template<class T>
void
Spreadsheet<T>::parse (const String & cell)
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
Spreadsheet<T>::parseA1 (const String & coordinates)
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
Spreadsheet<T>::rows (const String & cell)
{
    parse (cell);
    return std::max (0, ws->rows - ar);
}

template<class T>
int
Spreadsheet<T>::columns (const String & cell)
{
    parse (cell);
    return std::max (0, ws->columns - ac);
}

template<class T>
int
Spreadsheet<T>::rowsInColumn (const String & cell)
{
    parse (cell);
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
Spreadsheet<T>::columnsInRow (const String & cell)
{
    parse (cell);
    int result = 0;
    for (int c = ac; c < ws->columns; c++)
    {
        if ((*ws->strings)(ar,c) == 0  &&  (*ws->numbers)(ar,c) == 0) break;
        result++;
    }
    return result;
}

/// TODO: write int specialization of this function
template<class T>
T
Spreadsheet<T>::get (const String & cell, T row, T column)
{
    parse (cell);
    row    += ar;
    column += ac;
    int r = (int) row;
    int c = (int) column;
    // TODO: handle out of bounds access on dense matrix. See Spreadsheet.java
    T d00 = (*ws->numbers)(r,c);
    if (r == row  &&  c == column) return d00;  // integer coordinates, so no need for interpolation

    // Interpolate data
    T d01 = (*ws->numbers)(r,  c+1);
    T d10 = (*ws->numbers)(r+1,c  );
    T d11 = (*ws->numbers)(r+1,c+1);
    if (c >= ws->columns)
    {
        d01 = d00;
        d11 = d10;
    }
    if (r >= ws->rows)
    {
        d10 = d00;
        d11 = d01;
    }
    T dr = row    - r;
    T dc = column - c;
    T dr1 = 1 - dr;
    T dc1 = 1 - dc;
    return dc * (dr * d11 + dr1 * d01) + dc1 * (dr * d10 + dr1 * d00);
}

template<class T>
String
Spreadsheet<T>::get (const String & cell, const String & prefix, T row, T column)
{
    parse (cell);
    int r = (int) row    + ar;
    int c = (int) column + ac;
    int index = (*ws->strings)(r,c);
    if (index > 0) return prefix + strings[index-1];  // back to 0-based index
    return prefix;
}

/**
    Similar to IteratorSkip in io.tcc, except that we handle an offset due to anchor cell.
**/
template<class T>
class IteratorSkipCell : public IteratorNonzero<T>
{
public:
    Matrix<T> * A;
    int ar;
    int ac;
    int nextRow;
    int nextColumn;
    T   nextValue;

    IteratorSkipCell (Matrix<T> * A, int ar, int ac)
    :   A (A)
    {
        this->ar     = ar;
        this->ac     = ac;
        this->row    = -1;
        this->column = 0;
        this->value  = 0;

        nextRow    = ar - 1;  // always start one row above the anchor row
        nextColumn = ac;
        nextValue  = 0;
        getNext ();
    }

    bool next ()
    {
        if (nextRow < ar) return false;
        this->value  = nextValue;
        this->row    = nextRow    - ar;
        this->column = nextColumn - ac;
        getNext ();
        return true;
    }

    void getNext ()
    {
        for (; nextColumn < A->columns_; nextColumn++)
        {
            while (true)
            {
                if (++nextRow >= A->rows_) break;
                nextValue = (*A)(nextRow,nextColumn);
                if (nextValue != 0) return;
            }
            nextRow = ar - 1;
        }
    }
};

/**
    Similar to IteratorSparse in io.tcc, except that we handle an offset due to anchor cell.
**/
template<class T>
class IteratorSparseCell : public IteratorNonzero<T>
{
public:
    MatrixSparse<T> *                  A;
    int                                ar;
    int                                ac;
    int                                columns; // width of A, for fast lookup
    typename std::map<int,T>::iterator it;      // current column iterator
    typename std::map<int,T>::iterator end;     // of current column

    IteratorSparseCell (MatrixSparse<T> * A, int ar, int ac)
    :   A (A)
    {
        this->ar     = ar;
        this->ac     = ac;
        this->row    = 0;
        this->column = 0;
        this->value  = 0;

        columns = (*A->data).size () - ac;
        if (this->column < columns)
        {
            int actualColumn = this->column + ac;
            it  = (*A->data)[actualColumn].begin ();
            end = (*A->data)[actualColumn].end ();
        }
    }

    bool next ()
    {
        if (this->column >= columns) return false;
        while (true)
        {
            if (it != end)
            {
                this->row = it->first - ar;
                if (this->row >= 0) break;  // cell is in range; return result below
                it++;
                continue;
            }
            if (++this->column >= columns) return false;
            int actualColumn = this->column + ac;
            it  = (*A->data)[actualColumn].begin ();
            end = (*A->data)[actualColumn].end ();
        }

        this->value = it->second;
        it++;
        return true;
    }
};

template<class T>
IteratorNonzero<T> *
Spreadsheet<T>::getIterator (const String & cell)
{
    parse (cell);
    // see MatrixInput::getIterator () in io.tcc
    if (ws->numbers->classID () & MatrixSparseID) return new IteratorSparseCell<T> ((MatrixSparse<T> *) ws->numbers, ar, ac);
    return new IteratorSkipCell<T> ((Matrix<T> *) ws->numbers, ar, ac);
}

template<class T>
Spreadsheet<T> *
#ifdef n2a_FP
spreadsheetHelper (const String & fileName, int exponent, Spreadsheet<T> * oldHandle)
#else
spreadsheetHelper (const String & fileName,               Spreadsheet<T> * oldHandle)
#endif
{
    Spreadsheet<T> * handle = (Spreadsheet<T> *) SIMULATOR getHolder (fileName, oldHandle);
    if (! handle)
    {
        handle = new Spreadsheet<T> (fileName);
        SIMULATOR holders.push_back (handle);
#       ifdef n2a_FP
        handle->exponent = exponent;
#       endif
    }
    return handle;
}
