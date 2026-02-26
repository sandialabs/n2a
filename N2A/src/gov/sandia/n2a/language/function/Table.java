/*
Copyright 2019-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.backend.neuroml.XMLutility;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.eqset.EquationSet.NonzeroIterable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Matrix.IteratorNonzero;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.linear.MatrixSparse;
import gov.sandia.n2a.linear.MatrixSparse.IteratorSparse;
import gov.sandia.n2a.plugins.extpoints.Backend;
import tech.units.indriya.AbstractUnit;

public class Table extends Function implements NonzeroIterable
{
    public String name;     // For C backend, the name of the holder object.
    public String fileName; // For C backend, the name of the string variable holding the file name, if any.

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "table";
            }

            public Operator createInstance ()
            {
                return new Table ();
            }
        };
    }

    public boolean canBeConstant ()
    {
        return false;
    }

    public boolean canBeInitOnly ()
    {
        return true;
    }

    public void determineExponent (ExponentContext context)
    {
        for (int i = 0; i < operands.length; i++) operands[i].determineExponent (context);

        if (getKeyword ("info") == null)  // normal mode. This includes "prefix" mode, but in that case we return a string, so don't care about exponent.
        {
            int centerNew   = MSB / 2;
            int exponentNew = getExponentHint (0) - centerNew;
            updateExponent (context, exponentNew, centerNew);
        }
        else  // info mode
        {
            if (getType () instanceof Text) return;  // If we return a string, leave exponent as unknown.
            updateExponent (context, 0, 0);  // Return an integer
        }
    }

    public void determineExponentNext (ExponentContext context)
    {
        for (int i = 0; i < operands.length; i++)
        {
            Operator op = operands[i];
            op.exponentNext = MSB;  // We expect an integer for index parameters. String parameters don't care.
            op.determineExponentNext ();
        }
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        for (int i = 0; i < operands.length; i++) operands[i].determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public Type getType ()
    {
        if (getKeyword ("info"  ) != null) return new Scalar ();
        if (getKeyword ("prefix") != null) return new Text ();
        return new Scalar ();
    }

    public static class Sheet
    {
        public Matrix              numbers;   // Dense matrix stores empty cells and strings as 0. Sparse matrix does not store them at all.
        public Matrix              strings;   // 1-based indices into string collection. Empty cells and number cells are 0.
        public int                 rows;
        public int                 columns;
        public Integer             index[];   // Array of row numbers, sorted according to key.
        public Map<String,Integer> columnMap; // from header text to index
    }

    public static class Holder
    {
        protected List<String>      strings = new ArrayList<String> ();     // collection of all strings that appear in the workbook
        protected Map<String,Sheet> wb      = new HashMap<String,Sheet> (); // workbook, a collection of worksheets
        protected Sheet             first;                                  // The first sheet defined in the file. This is the default when no sheet is specified in cell address.
        protected String            anchor;                                 // The most recently parsed anchor cell address. Includes sheet name and coordinates.
        protected Sheet             ws;                                     // anchor sheet
        protected int               ar;                                     // anchor row
        protected int               ac;                                     // anchor column

        public Holder (Path path)
        {
            final double fillThreshold = 0.5;

            // File-type triage -- If it's zip, then process as Excel spreadsheet. All others are treated as XSV.
            boolean isZip = false;
            try (BufferedReader reader = Files.newBufferedReader (path))
            {
                char magic[] = new char[4];
                reader.read (magic);
                isZip =  magic[0] == 'P'  &&  magic[1] == 'K'  &&  magic[2] == 3  &&  magic[3] == 4;
            }
            catch (Exception e)
            {
                PrintStream err = Backend.err.get ();
                err.println ("ERROR: Can't open table file: " + path);
                e.printStackTrace (err);
                throw new Backend.AbortRun ();
            }

            // Try to process as XSV
            if (! isZip)
            {
                ws = new Sheet ();
                wb.put ("", ws);
                first = ws;
                ws.numbers = new MatrixSparse ();
                ws.strings = new MatrixSparse ();
                ar = 0;
                ac = 0;

                try (BufferedReader reader = Files.newBufferedReader (path))
                {
                    char    delimiter    = ' ';  // space char, initially
                    boolean delimiterSet = false;
                    while (true)
                    {
                        String line = reader.readLine ();
                        if (line == null) break;  // indicates end of stream
                        if (line.length () == 0) continue;

                        char chars[] = line.toCharArray ();
                        if (! delimiterSet)
                        {
                            // Scan for first delimiter character that is not inside a quote.
                            boolean inQuote = false;
                            for (char c : chars)
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
                            delimiterSet =  delimiter != ' '  ||  ! line.isBlank ();
                        }

                        // Break line into delimited strings, possibly quoted.
                        int column = 0;
                        boolean inQuote = false;
                        StringBuilder token = new StringBuilder ();
                        for (int i = 0; i < chars.length; i++)
                        {
                            char c = chars[i];
                            if (c == '\"')
                            {
                                if (inQuote  &&  i < chars.length - 1  &&  chars[i+1] == '\"')
                                {
                                    token.append (c);
                                    i++;
                                    continue;
                                }
                                inQuote = ! inQuote;
                                continue;
                            }
                            if (c == delimiter  &&  ! inQuote)
                            {
                                String temp = token.toString ();
                                if (temp.isBlank ())
                                {
                                    ws.strings.set (first.rows, column++, 0);
                                }
                                else
                                {
                                    strings.add (temp);
                                    int stringIndex = strings.size ();
                                    ws.strings.set (ws.rows, column++, stringIndex);
                                }
                                token.setLength (0);
                                continue;
                            }
                            token.append (c);
                        }
                        if (! token.isEmpty ())
                        {
                            strings.add (token.toString ());
                            int s = strings.size ();
                            ws.strings.set (ws.rows, column++, s);
                        }
                        ws.rows++;
                        ws.columns = Math.max (ws.columns, column);
                    }
                }
                catch (Exception e)
                {
                    PrintStream err = Backend.err.get ();
                    err.println ("ERROR: Failed to parse CSV file: " + path);
                    e.printStackTrace (err);
                    throw new Backend.AbortRun ();
                }
                ws.strings = new MatrixDense (ws.strings);

                // Convert strings to numbers
                int fillN = 0;
                for (int c = 0; c < first.columns; c++)
                {
                    for (int r = 0; r < first.rows; r++)
                    {
                        int index = (int) ws.strings.get (r, c);
                        if (index == 0) continue;
                        String s = strings.get (index - 1);
                        double value = Scalar.parseDouble (s, 0);
                        if (value == 0) continue;
                        ws.numbers.set (r, c, value);
                        fillN++;
                    }
                }

                // Check fill-in and possibly convert to dense
                int Nrows = ws.numbers.rows ();
                int Ncols = ws.numbers.columns ();
                if ((double) fillN / (Nrows * Ncols) > fillThreshold) ws.numbers = new MatrixDense (ws.numbers);

                return;
            }

            // Try to process as Excel workbook
            try (ZipFile archive = new ZipFile (path.toFile ()))
            {
                // Set up XML parser
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance ();
                factory.setCoalescing (true);
                factory.setIgnoringComments (true);
                DocumentBuilder builder = factory.newDocumentBuilder ();

                // Read workbook relationship file to determine paths to sheets, shared strings and styles.
                Map<String,String> IDtarget = new HashMap<String,String> ();
                String sharedStringsPath = "";
                String stylesPath        = "";
                ZipEntry entry = archive.getEntry ("xl/_rels/workbook.xml.rels");
                Document doc = builder.parse (archive.getInputStream (entry));
                Node docElement = doc.getDocumentElement ();
                for (Node rel = docElement.getFirstChild (); rel != null; rel = rel.getNextSibling ())
                {
                    NamedNodeMap attr = rel.getAttributes ();
                    String Type = attr.getNamedItem ("Type").getTextContent ();
                    if (Type.endsWith ("/worksheet"))
                    {
                        String Id = attr.getNamedItem ("Id").getTextContent ();
                        String Target = "xl/" + attr.getNamedItem ("Target").getTextContent ();
                        IDtarget.put (Id, Target);
                    }
                    else if (Type.endsWith ("/sharedStrings"))
                    {
                        sharedStringsPath = "xl/" + attr.getNamedItem ("Target").getTextContent ();
                    }
                    else if (Type.endsWith ("/styles"))
                    {
                        stylesPath = "xl/" + attr.getNamedItem ("Target").getTextContent ();
                    }
                }

                // Load shared strings
                if (! sharedStringsPath.isEmpty ())
                {
                    entry = archive.getEntry (sharedStringsPath);
                    if (entry != null)
                    {
                        doc = builder.parse (archive.getInputStream (entry));
                        docElement = doc.getDocumentElement ();
                        int uniqueCount = XMLutility.getAttribute (docElement, "uniqueCount", 0);
                        if (uniqueCount > 0) strings = new ArrayList<String> (uniqueCount);  // re-allocate array, now that we know the size
                        for (Node si = docElement.getFirstChild (); si != null; si = si.getNextSibling ())
                        {
                            strings.add (extractSI (si));
                        }
                    }
                }

                // Determine date styles
                Set<Integer> dateStyles = new HashSet<Integer> ();  // collection of all style numbers that should be treated as date
                if (! stylesPath.isEmpty ())
                {
                    entry = archive.getEntry (stylesPath);
                    if (entry != null)
                    {
                        doc = builder.parse (archive.getInputStream (entry));
                        docElement = doc.getDocumentElement ();
                        Node cellXfs = XMLutility.getChild (docElement, "cellXfs");
                        int styleNumber = 0;
                        for (Node xf = cellXfs.getFirstChild (); xf != null; xf = xf.getNextSibling ())
                        {
                            int id = XMLutility.getAttribute (xf, "numFmtId", 0);
                            if (id >= 14  &&  id <= 22  ||  id >= 45  &&  id <= 47) dateStyles.add (styleNumber);
                            styleNumber++;
                        }
                    }
                }

                // Scan workbook for sheets
                entry = archive.getEntry ("xl/workbook.xml");
                doc = builder.parse (archive.getInputStream (entry));
                docElement = doc.getDocumentElement ();
                Node sheets = XMLutility.getChild (docElement, "sheets");
                for (Node sheet = sheets.getFirstChild (); sheet != null; sheet = sheet.getNextSibling ())
                {
                    String rid = XMLutility.getAttribute (sheet, "r:id");
                    String target = IDtarget.get (rid);
                    if (target == null) continue;
                    String name = XMLutility.getAttribute (sheet, "name");

                    // Process worksheet
                    // We could try to read the dimension element, but it is not reliable
                    // (not required to be present, and not always formatted correctly).
                    // Thus, the only safe way to load a spreadsheet is with sparse matrices.
                    // There are several delicate tradeoffs between time and space here.
                    // We don't want to lock down more memory than necessary. OTOH, it is a
                    // waste of time to convert to dense matrix if each element is accessed
                    // only once during a simulation. Here it is impossible to know how
                    // all that will play out, so we use a simple heuristic based on fill-in
                    // to decide whether to convert to dense matrix after the load finishes.
                    Sheet ws = new Sheet ();
                    wb.put (name, ws);
                    if (first == null) first = ws;
                    MatrixSparse N = new MatrixSparse ();
                    MatrixSparse S = new MatrixSparse ();
                    ws.numbers = N;
                    ws.strings = S;
                    int fillN = 0;
                    int fillS = 0;

                    entry = archive.getEntry (target);
                    Document worksheet = builder.parse (archive.getInputStream (entry));
                    docElement = worksheet.getDocumentElement ();
                    Node sheetData = XMLutility.getChild (docElement, "sheetData");
                    for (Node row = sheetData.getFirstChild (); row != null; row = row.getNextSibling ())
                    {
                        for (Node c = row.getFirstChild (); c != null; c = c.getNextSibling ())
                        {
                            parseA1 (XMLutility.getAttribute (c, "r"));
                            switch (XMLutility.getAttribute (c, "t"))
                            {
                                case "s":  // indexed string
                                    Node v = XMLutility.getChild (c, "v");
                                    int index = Integer.valueOf (v.getTextContent ());
                                    String value = strings.get (index);
                                    if (value == null  ||  value.isEmpty ()) continue;
                                    S.set (ar, ac, index+1);  // Offset index by 1, so the 0 can represent empty string.
                                    fillS++;
                                    break;
                                case "str":  // "formula string". Not sure how this is different from inlineStr.
                                    v = XMLutility.getChild (c, "v");
                                    String str = v.getTextContent ().trim ();
                                    if (str.isEmpty ()) continue;
                                    strings.add (str);
                                    S.set (ar, ac, strings.size ());  // by putting this call after the add(), we get 1-based index
                                    fillS++;
                                    break;
                                case "inlineStr":
                                    Node si = XMLutility.getChild (c, "si");
                                    str = extractSI (si).trim ();
                                    if (str.isEmpty ()) continue;
                                    strings.add (str);
                                    S.set (ar, ac, strings.size ());
                                    fillS++;
                                    break;
                                case "e":
                                    continue;
                                default:  // All other types should be numeric. Includes "n", "b" and empty string (with default value "n").
                                    // Dates are stored by Excel internally as number of days since December 31, 1899.
                                    // Day 25569 is start of Unix epoch, January 1, 1970.
                                    // I believe that day number includes leap days, so all we need to do is multiply by 86400.
                                    // There are more subtle elements of horology to consider, but this should be good enough.

                                    // The difficulty is identifying a date cell. The only way is to check style (attribute "s").
                                    // See https://www.brendanlong.com/the-minimum-viable-xlsx-reader.html
                                    // At a minimum, we could check all pre-defined data styles: 14-22, 45-47
                                    // It appears that MS Excel won't store negative date numbers. Instead, the value is stored as a string.

                                    v = XMLutility.getChild (c, "v");
                                    if (v == null) continue;  // Sometimes a cell exists in the XML file but has not value.
                                    double d = Double.valueOf (v.getTextContent ());
                                    int s = XMLutility.getAttribute (c, "s", -1);
                                    if (dateStyles.contains (s)) d = (d - 25569) * 86400;  // Convert from Excel time to Unix time.
                                    if (d == 0) continue;  // should we also check for NAN?
                                    N.set (ar, ac, d);
                                    fillN++;
                            }
                        }
                    }

                    // Check fill-in and possibly convert to dense
                    int Nrows = N.rows ();
                    int Ncols = N.columns ();
                    int Srows = S.rows ();
                    int Scols = S.columns ();
                    ws.rows    = Math.max (Nrows, Srows);
                    ws.columns = Math.max (Ncols, Scols);
                    if ((double) fillN / (Nrows * Ncols) > fillThreshold) ws.numbers = new MatrixDense (N);
                    if ((double) fillS / (Srows * Scols) > fillThreshold) ws.strings = new MatrixDense (S);
                }

                ws = first;
                ar = 0;
                ac = 0;
            }
            catch (Exception e)
            {
                PrintStream err = Backend.err.get ();
                err.println ("ERROR: Failed to parse spreadsheet file: " + path);
                e.printStackTrace (err);
                throw new Backend.AbortRun ();
            }
        }

        public static String extractSI (Node si)
        {
            String result = "";
            for (Node n = si.getFirstChild (); n != null; n = n.getNextSibling ())
            {
                switch (n.getNodeName ())
                {
                    case "t":  // simple text element
                        result += n.getTextContent ();
                        break;
                    case "r":  // rich text element
                        for (Node m = n.getFirstChild (); m != null; m = m.getNextSibling ())
                        {
                            if (m.getNodeName ().equals ("t")) result += m.getTextContent ();
                        }
                }
            }
            return result;
        }

        public void parse (String anchor)
        {
            if (anchor.equals (this.anchor)) return;
            this.anchor = anchor;

            String pieces[] = anchor.split ("!");
            String sheetName;
            String coordinates;
            if (pieces.length == 1)
            {
                sheetName   = "";
                coordinates = pieces[0];
                int last = coordinates.length () - 1;
                if (last >= 0)
                {
                    char c = coordinates.charAt (last);
                    if (c < '0'  ||  c > '9')  // not a digit
                    {
                        sheetName = coordinates;
                        coordinates = "A1";
                    }
                }
            }
            else
            {
                sheetName   = pieces[0];
                coordinates = pieces[1];
            }

            Sheet sheet = wb.get (sheetName);
            if (sheet == null) ws = first;
            else               ws = sheet;
            ws.columnMap = null;  // A change of anchor also indicates a change column headers.
            parseA1 (coordinates);
        }

        public void parseA1 (String coordinates)
        {
            ac = 0;
            if (coordinates.isEmpty ())
            {
                ar = 0;
                return;
            }

            coordinates = coordinates.toUpperCase ();
            int pos = 0;
            int length = coordinates.length ();
            for (; pos < length; pos++)
            {
                char c = coordinates.charAt (pos);
                if (c < 'A') break;
                ac = ac * 26 + c - 'A' + 1;
            }
            ac--;
            ar = Integer.valueOf (coordinates.substring (pos));
            if (ar > 0) ar--;  // Cell addresses are usually 1-based, so need to convert to 0-based.
        }

        public int getRows ()
        {
            return Math.max (0, ws.rows - ar);
        }

        public int getColumns ()
        {
            return Math.max (0, ws.columns - ac);
        }

        public int getRowsInColumn ()
        {
            int result = 0;
            for (int r = ar; r < ws.rows; r++)
            {
                if (ws.numbers.get (r, ac) == 0  &&  ws.strings.get (r, ac) == 0) break;
                result++;
            }
            return result;
        }

        public int getColumnsInRow ()
        {
            int result = 0;
            for (int c = ac; c < ws.columns; c++)
            {
                if (ws.numbers.get (ar, c) == 0  &&  ws.strings.get (ar, c) == 0) break;
                result++;
            }
            return result;
        }

        /**
            @return Zero-based index if found. -1 if not found.
        **/
        public int getColumnIndex (String columnName)
        {
            if (ws.columnMap == null)
            {
                ws.columnMap = new HashMap<String,Integer> ();
                for (int c = 0; c < ws.columns; c++)
                {
                    int stringIndex = (int) ws.strings.get (0, c);
                    String s;
                    if (stringIndex == 0) s = "";
                    else                  s = strings.get (stringIndex - 1);
                    ws.columnMap.put (s, c);
                }
            }
            Integer result = ws.columnMap.get (columnName);
            if (result == null) return -1;
            return result;
        }

        /**
            @return Zero-based index if found. -1 if not found.
        **/
        public int getRowIndex (int columnIndex, Object columnValue)
        {
            if (ws.index == null)
            {
                if (ws.columnMap == null)  // No column headers, so use all rows.
                {
                    ws.index = new Integer[ws.rows];
                    for (int i = 0; i < ws.rows; i++) ws.index[i] = i;
                }
                else  // Column headers, so skip row 0.
                {
                    ws.index = new Integer[ws.rows - 1];
                    for (int i = 1; i < ws.rows; i++) ws.index[i - 1] = i;
                }
                Arrays.sort (ws.index, (t1, t2) -> 
                {
                    // This implements M sort order, just because it's the most rational way to handle mixed types.
                    int i1 = (int) ws.strings.get (t1, columnIndex);
                    int i2 = (int) ws.strings.get (t2, columnIndex);
                    if (i1 == 0)  // t1 is a number
                    {
                        if (i2 == 0) return (int) Math.signum (ws.numbers.get (t1, columnIndex) - ws.numbers.get (t2, columnIndex));  // t2 is a number
                        else         return -1;  // t2 is a string; number < string
                    }
                    else  // t1 is a string
                    {
                        if (i2 == 0) return 1;  // t2 is a number; string > number
                        else         return strings.get (i1 -1).compareTo (strings.get (i2 - 1));
                    }
                });
            }

            // Do binary search on indirect values.
            int rowIndex = Arrays.binarySearch (ws.index, -1, (t1, t2) ->
            {
                Object o1;
                if (t1 < 0)
                {
                    o1 = columnValue;
                }
                else
                {
                    int i = (int) ws.strings.get (t1, columnIndex);
                    o1 =  i == 0 ? ws.numbers.get (t1, columnIndex) : strings.get (i - 1);
                }

                Object o2;
                if (t2 < 0)
                {
                    o2 = columnValue;
                }
                else
                {
                    int i = (int) ws.strings.get (t2, columnIndex);
                    o2 =  i == 0 ? ws.numbers.get (t2, columnIndex) : strings.get (i - 1);
                }

                if (o1 instanceof String)
                {
                    if (o2 instanceof String) return ((String) o1).compareTo ((String) o2);
                    else                      return 1;  //o2 is number; string > number
                }
                else  // o1 is a number
                {
                    if (o2 instanceof String) return -1;  // number < string
                    else                      return ((Double) o1).compareTo ((Double) o2);
                }
            });
            if (rowIndex < 0) return -1;
            return ws.index[rowIndex];
        }

        public double getDouble (double row, double column)
        {
            row    += ar;
            column += ac;
            int r = (int) row;
            int c = (int) column;
            Matrix A = ws.numbers;
            double d00 = 0;  // Simpler to set zero here rather than in non-sparse case below, but this is minutely less efficient.
            int rows = 0;  // Don't actually need to initialize, but this silences compiler.
            int cols = 0;
            if (A instanceof MatrixSparse)
            {
                d00 = A.get (r, c);
            }
            else
            {
                rows = A.rows ();
                cols = A.columns ();
                if (r >= 0  &&  r < rows  &&  c >= 0  &&  c < cols) d00 = A.get (r, c);
            }
            if (r == row  &&  c == column) return d00;  // integer coordinates, so no need for interpolation

            // Interpolate data
            double d01 = 0;
            double d10 = 0;
            double d11 = 0;
            if (A instanceof MatrixSparse)
            {
                d01 = A.get (r,   c+1);
                d10 = A.get (r+1, c  );
                d11 = A.get (r+1, c+1);
            }
            else
            {
                if (r >=  0  &&  r < rows    &&  c >= -1  &&  c < cols-1) d01 = A.get (r,   c+1);
                if (r >= -1  &&  r < rows-1  &&  c >=  0  &&  c < cols  ) d10 = A.get (r+1, c  );
                if (r >= -1  &&  r < rows-1  &&  c >= -1  &&  c < cols-1) d11 = A.get (r+1, c+1);
            }
            if (c >= ws.columns)
            {
                d01 = d00;
                d11 = d10;
            }
            if (r >= ws.rows)
            {
                d10 = d00;
                d11 = d01;
            }
            double dr = row    - r;
            double dc = column - c;
            double dr1 = 1 - dr;
            double dc1 = 1 - dc;
            return dc * (dr * d11 + dr1 * d01) + dc1 * (dr * d10 + dr1 * d00);
        }

        public String getString (int row, int column)
        {
            row    += ar;
            column += ac;
            int index = (int) ws.strings.get (row, column);
            if (index == 0) return "";
            return strings.get (index - 1);  // offset index back to zero-based
        }

        public Set<String> worksheetNames ()
        {
            return wb.keySet ();
        }
    }

    public Holder open (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return null;  // absence of simulator indicates analysis phase, so opening files is unnecessary

        String path = ((Text) operands[0].eval (context)).value;
        Object H = simulator.holders.get (path);
        if (H == null)
        {
            H = new Holder (simulator.jobDir.resolve (path));
            simulator.holders.put (path, H);
        }
        else if (! (H instanceof Holder))
        {
            Backend.err.get ().println ("ERROR: Reopening file as a different resource type.");
            throw new Backend.AbortRun ();
        }
        return (Holder) H;
    }

    public Type eval (Instance context)
    {
        Holder H = open (context);
        if (H == null) return getType ();

        Operator anchor = getKeyword ("anchor");
        if (anchor != null) H.parse (anchor.eval (context).toString ());

        Operator info = getKeyword ("info");
        if (info != null)
        {
            switch (info.getString ())  // info must be a constant.
            {
                case "columns":      return new Scalar (H.getColumns      ());
                case "rows":         return new Scalar (H.getRows         ());
                case "columnsInRow": return new Scalar (H.getColumnsInRow ());
                case "rowsInColumn": return new Scalar (H.getRowsInColumn ());
            }
            return new Scalar (0);  // An invalid info keyword indicates that we can't trust the other function parameters, so don't fall through.
        }

        double row = 0;
        double col = 0;

        Type op1 = null;
        if (operands.length > 1) op1 = operands[1].eval (context);
        Type op2 = null;
        if (operands.length > 2) op2 = operands[2].eval (context);

        Type prefix = evalKeyword (context, "prefix");
        Operator key = getKeyword ("key");  // Assumed to be constant string, if it exists.
        if (key == null)
        {
            if (op1 instanceof Scalar) row = ((Scalar) op1).value;
        }
        else  // Look up value in index column specified by key.
        {
            int keyIndex = H.getColumnIndex (key.getString ());
            if (keyIndex >= 0)
            {
                if      (op1 instanceof Text)   row = H.getRowIndex (keyIndex, op1.toString ());
                else if (op1 instanceof Scalar) row = H.getRowIndex (keyIndex, ((Scalar) op1).value);
            }
        }

        if      (op2 instanceof Text)   col = H.getColumnIndex (op2.toString ());
        else if (op2 instanceof Scalar) col = ((Scalar) op2).value;

        if (row < 0  ||  col < 0)
        {
            if (prefix instanceof Text) return prefix;
            return new Scalar (0);
        }
        else
        {
            if (prefix instanceof Text) return new Text (prefix + H.getString ((int) row, (int) col));
            return new Scalar (H.getDouble (row, col));
        }
    }

    public String toString ()
    {
        return "spreadsheet";
    }

    public Operator operandA ()
    {
        if (operands.length > 1) return operands[1];
        return null;
    }

    public Operator operandB ()
    {
        if (operands.length > 2) return operands[2];
        return null;
    }

    public boolean hasCorrectForm ()
    {
        if (operands.length < 3) return false;
        if (! (operands[0] instanceof Constant)) return false;
        Operator anchor = getKeyword ("anchor");
        if (anchor != null  &&  ! (anchor instanceof Constant)) return false;
        // Could also check if op1 and op2 are numeric expressions, but not worth the effort.
        return true;
    }

    public IteratorNonzero getIteratorNonzero (Instance context)
    {
        Holder H = open (context);
        if (H == null) return null;

        Operator anchor = getKeyword ("anchor");
        if (anchor != null) H.parse (anchor.getString ());  // This is required to be constant, so we can simply retrieve the string.

        Matrix A = H.ws.numbers;
        if (A instanceof MatrixSparse) return new IteratorSparse ((MatrixSparse) A, H.ar, H.ac);
        return ((MatrixDense) A).getRegion (H.ar, H.ac).getIteratorNonzero ();
    }
}
