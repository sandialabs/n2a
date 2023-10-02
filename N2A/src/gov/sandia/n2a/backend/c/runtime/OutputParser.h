/*
A utility class for reading simulation output files.
Primarily for those who wish to write C++ code to analyze these data.
This is a pure header implementation. No need to build/link extra libraries.

Copyright 2020-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

#ifndef output_parser_h
#define output_parser_h

#include <string>  // Because OutputParser is not used by the C runtime, we use the STL-provided string class rather than our own minimalist implementation.
#include <vector>
#include <limits>
#include <fstream>
#include <cmath>

#include <iostream>  // for testing

namespace n2a
{
    /**
        Utility class used by OutputParser. Keeps track of header and all rows
        held by one column. This is the kind of object returned by OutputParser.getColumn().
    **/
    class Column
    {
    public:
        std::string        header;
        int                index;  // Utility field with arbitrary semantics. See OutputParser.assignSpikeIndices() for one possible use.
        std::vector<float> values;
        float              value;  // For most recent row
        int                startRow;
        int                textWidth;
        double             minimum;
        double             maximum;
        double             range;
        std::string        scale;
        std::string        color;

        Column (const std::string & header)
        :   header (header)
        {
            index     = 0;
            startRow  = 0;
            textWidth = 0;
            minimum   =  std::numeric_limits<double>::infinity ();
            maximum   = -std::numeric_limits<double>::infinity ();
            range     = 0;
        }

        void computeStats ()
        {
            for (auto f : values)
            {
                if (std::isinf (f)  ||  std::isnan (f)) continue;
                minimum = std::min (minimum, (double) f);
                maximum = std::max (maximum, (double) f);
            }
            if (std::isinf (maximum))  // There was no good data. If max is infinite, then so is min.
            {
                // Set defensive values.
                range   = 0;
                minimum = 0;
                maximum = 0;
            }
            else
            {
                range = maximum - minimum;
            }
        }

        float get (int row = -1, float defaultValue = 0)
        {
            if (row < 0) return value;
            row -= startRow;
            if (row < 0  ||  row >= values.size ()) return defaultValue;
            return values[row];
        }

        void set (int row, float value)
        {
            fill (row, 0);
            values[row - startRow] = value;
        }

        bool fill (int row, float defaultValue = 0)
        {
            if (row < startRow)
            {
                values.insert (values.begin (), startRow - row, defaultValue);
                startRow = row;
                return true;
            }
            int last = startRow + values.size () - 1;
            if (row > last)
            {
                values.insert (values.end (), row - last, defaultValue);
                return true;
            }
            return false;
        }

        /**
            Creates a new row at the given index with the given value.
            If the new row comes before or after the current block of rows,
            then the block is simply extended.
        **/
        void insert (int row, float defaultValue)
        {
            if (fill (row, defaultValue)) return;
            values.insert (values.begin () + row, defaultValue);
        }
    };

    /**
        Primary class for reading and accessing data in an augmented output file.
        There are two main ways to use this class. One is to read the entire file into
        memory. The other is to read through the file row-by-row, without remembering
        anything but the current row. The second method is designed specifically for
        output files which exceed your system's memory capacity.
        Both interfaces use the get() functions to retrieve buffered values.
        The all-at-once interface uses the parse() function.
        The row-by-row interface uses the open(), nextRow() functions.
        It's a good idea not to mix these usages. Note that the parse() function is
        actually built on top of the row-by-row functions.
    **/
    class OutputParser
    {
    public:
        std::vector<Column *> columns;
        std::ifstream *       in;
        bool                  raw;        // Indicates that all column names are empty, likely the result of output() in raw mode.
        char                  delimiter;
        bool                  delimiterSet;
        bool                  isXycePRN;
        Column *              time;
        bool                  timeFound;  // Indicates that time is a properly-labeled column, rather than a fallback.
        int                   rows;       // Total number of rows successfully read by nextRow()
        float                 defaultValue;

        OutputParser ()
        {
            in           = 0;
            defaultValue = 0;
        }

        ~OutputParser ()
        {
            close ();
        }

        /**
            Use this function in conjunction with nextRow() to read file line-by-line
            without filling memory with more than one row.
        **/
        void open (const std::string & fileName)
        {
            close ();
            in           = new std::ifstream (fileName.c_str ());
            raw          = true;  // Will be negated if any non-empty column name is found.
            isXycePRN    = false;
            time         = 0;
            timeFound    = false;
            rows         = 0;
            delimiter    = ' ';
            delimiterSet = false;
        }

        void close ()
        {
            if (in) delete in;
            in = 0;
            for (Column * c : columns) delete c;
            columns.clear ();
        }

        /**
            Reads in the next row of data values. Also processes header rows, but continues
            reading until a data row comes in.
            @return Number of columns found in current row. If zero, then end-of-file
            has been reached or there is an error.
        **/
        int nextRow ()
        {
            if (! in) return 0;
            std::string line;
            while (true)
            {
                getline (*in, line);
                if (! in->good ()) return 0;
                if (line.empty ()) continue;
                if (line[line.size () - 1] == '\r')  // Hack to handle CRLF line ending when c runtime fails to recognize it.
                {
                    line.resize (line.size () - 1);
                    if (line.empty ()) continue;
                }
                if (line.substr (0, 6) == "End of") return 0;  // Don't mistake Xyce final output line as a column header.

                if (! delimiterSet)
                {
                    // Scan for first delimiter character that is not inside a quote.
                    bool inQuote = false;
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
                        if (c == ',') delimiter = c;
                        // space character is lowest precedence
                    }
                    delimiterSet =  delimiter != ' '  ||  line.find_first_not_of (' ') != std::string::npos;
                }

                char l = line[0];
                bool isHeader = (l < '0'  ||  l > '9')  &&  l != '+'  &&  l != '-';
                if (isHeader) raw = false;

                int index = 0;  // Column index
                std::string::size_type lineSize = line.size ();
                std::string token;
                for (std::string::size_type i = 0; i < lineSize; i++)
                {
                    // Scan for next delimiter, handling quotes as needed.
                    token.clear ();
                    bool inQuote = false;
                    for (; i < lineSize; i++)
                    {
                        char c = line[i];
                        if (c == '\"')
                        {
                            if (inQuote  &&  i < lineSize - 1  &&  line[i+1] == '\"')
                            {
                                token += c;
                                i++;
                                continue;
                            }
                            inQuote = ! inQuote;
                            continue;
                        }
                        if (c == delimiter  &&  ! inQuote) break;
                        token += c;
                    }

                    // Notice that c can never be greater than column count,
                    // because we always fill in columns as we go.
                    if (isHeader)
                    {
                        if (index == columns.size ()) columns.push_back (new Column (token));
                    }
                    else
                    {
                        if (index == columns.size ()) columns.push_back (new Column (""));
                        Column * column = columns[index];
                        if (token.empty ())
                        {
                            column->value = defaultValue;
                        }
                        else
                        {
                            column->textWidth = std::max ((std::string::size_type) column->textWidth, token.size ());
                            column->value = atof (token.c_str ());
                        }
                    }

                    index++;
                }

                if (isHeader)
                {
                    isXycePRN =  columns[0]->header == "Index";
                }
                else
                {
                    rows++;
                    return index;
                }
            }
        }

        /**
            Use this function to read the entire file into memory.
        **/
        void parse (const std::string & fileName, float defaultValue = 0)
        {
            this->defaultValue = defaultValue;
            open (fileName);
            while (int count = nextRow ())
            {
                int c;
                for (c = 0; c < count; c++)
                {
                    Column * column = columns[c];
                    if (column->values.empty ()) column->startRow = rows - 1;
                    column->values.push_back (column->value);
                }
                for (; c < columns.size (); c++)
                {
                    Column * column = columns[c];
                    column->values.push_back (defaultValue);  // Because the structure is not sparse, we must fill out every row.
                }
            }
            if (columns.empty ()) return;

            // If there is a separate columns file, open and parse it.
            std::string columnFileName = fileName + ".columns";
            std::ifstream columnFile (columnFileName.c_str ());
            std::string line;
            getline (columnFile, line);
            if (line.substr (0, 10) == "N2A.schema")
            {
                Column * c = 0;
                while (true)
                {
                    getline (columnFile, line);
                    if (! columnFile.good ()) break;
                    int pos = line.find_first_of (":");
                    std::string key   = line.substr (0, pos);
                    std::string value = line.substr (pos + 1);
                    if (line[0] == ' ')
                    {
                        if (c == 0) continue;
                        if      (key == " color") c->color = value;
                        else if (key == " scale") c->scale = value;
                    }
                    else
                    {
                        int i = atoi (key.c_str ());
                        if (i < 0  ||  i >= columns.size ())
                        {
                            c = 0;
                            continue;
                        }
                        c = columns[i];
                        if (c->header.empty ()) c->header = value;
                    }
                }
            }

            // Determine time column
            time = columns[0];  // fallback, in case we don't find it by name
            int timeMatch = 0;
            for (Column * c : columns)
            {
                int potentialMatch = 0;
                if      (c->header == "t"   ) potentialMatch = 1;
                else if (c->header == "TIME") potentialMatch = 2;
                else if (c->header == "$t"  ) potentialMatch = 3;
                if (potentialMatch > timeMatch)
                {
                    timeMatch = potentialMatch;
                    time = c;
                    timeFound = true;
                }
            }

            // Get rid of Xyce "Index" column, as it is redundant with row number.
            if (isXycePRN) columns.erase (columns.begin ());
        }

        /**
            Optional post-processing step to give columns their position in a spike raster.
        **/
        void assignSpikeIndices ()
        {
            if (raw)
            {
                int i = 0;
                for (Column * c : columns)
                {
                    if (! timeFound  ||  c != time) c->index = i++;
                }
            }
            else
            {
                int nextColumn = -1;
                for (Column * c : columns)
                {
                    try
                    {
                        c->index = std::stoi (c->header);
                    }
                    catch (std::exception & error)
                    {
                        c->index = nextColumn--;  // Yes, that's actually subtraction. Mis-named or unnamed columns get negative indices, reserving the positive indices for explicit and proper names.
                    }
                }
            }
        }

        Column * getColumn (const std::string & columnName)
        {
            for (Column * c : columns) if (c->header == columnName) return c;
            return 0;
        }

        float get (const std::string & columnName, int row = -1)
        {
            Column * c = getColumn (columnName);
            if (c == 0) return defaultValue;
            return c->get (row);
        }

        float get (int columnNumber, int row = -1)
        {
            if (columnNumber >= columns.size ()) return defaultValue;
            return columns[columnNumber]->get (row);
        }

        void set (const std::string & columnName, int row, float value)
        {
            Column * c = getColumn (columnName);
            if (c == 0)
            {
                c = new Column (columnName);
                columns.push_back (c);
            }
            c->set (row, value);
            int newRows = c->startRow + c->values.size ();
            if (newRows > rows) rows = newRows;
        }

        void set (int columnNumber, int row, float value)
        {
            while (columns.size () <= columnNumber) columns.push_back (new Column (""));
            Column * c = columns[columnNumber];
            c->set (row, value);
            int newRows = c->startRow + c->values.size ();
            if (newRows > rows) rows = newRows;
        }

        /**
            Open a new row across all columns at the given row index.
            All values will be filled with the default, including the time column if one exists.
        **/
        void insertRow (int row)
        {
            for (Column * c : columns)
            {
                c->insert (row, defaultValue);
                int newRows = c->startRow + c->values.size ();
                if (newRows > rows) rows = newRows;
            }
        }

        bool hasData ()
        {
            for (Column * c : columns) if (! c->values.empty ()) return true;
            return false;
        }

        bool hasHeaders ()
        {
            for (Column * c : columns) if (! c->header.empty ()) return true;
            return false;
        }

		/**
            Dumps parsed data in tabular form. This can be used directly by most software.
            Pass separator="," to create CSV file.
        **/
        void dump (std::ostream & out, const std::string & separator = "\t")
        {
            if (columns.empty ()) return;
            Column * last = columns.back ();

            if (hasHeaders ())
            {
                for (Column * c : columns)
                {
                    out << c->header;
                    if (c == last) out << std::endl;
                    else           out << separator;
                }
            }

            if (hasData ())
            {
                for (int r = 0; r < rows; r++)
                {
                    for (Column * c : columns)
                    {
                        out << c->get (r);
                        if (c == last) out << std::endl;
                        else           out << separator;
                    }
                }
            }
		}

		/// Dumps column metadata (from output mode field).
		void dumpMode (std::ostream & out)
		{
            if (hasHeaders ())
            {
                for (Column * c : columns)
                {
                    out << c->header << std::endl;
					out << " color=" << c->color << std::endl;
					out << " scale=" << c->scale << std::endl;
                }
            }
        }
    };
}

#endif
