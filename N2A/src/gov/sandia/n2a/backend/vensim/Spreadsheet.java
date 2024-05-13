/*
Copyright 2019-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.vensim;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
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

public class Spreadsheet extends Function implements NonzeroIterable
{
    public String name;     // For C backend, the name of the holder object.
    public String fileName; // For C backend, the name of the string variable holding the file name, if any.

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "spreadsheet";
            }

            public Operator createInstance ()
            {
                return new Spreadsheet ();
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
        public Matrix numbers; // Dense matrix stores empty cells and strings as 0. Sparse matrix does not store them at all. 
        public Matrix strings; // 1-based indices into string collection. Empty cells and numbers are 0.
        public int    rows;
        public int    columns;
    }

    public static class Holder
    {
        protected List<String>      strings = new ArrayList<String> ();     // collection of all strings that appear in the workbook
        protected Map<String,Sheet> wb      = new HashMap<String,Sheet> (); // workbook, a collection of worksheets
        protected Sheet             first;                                  // The first sheet defined in the file. This is the default when no sheet is specified in cell address.
        protected String            cell;                                   // The most recently parsed anchor cell address. Includes sheet name and coordinates.
        protected Sheet             ws;                                     // anchor sheet
        protected int               ar;                                     // anchor row
        protected int               ac;                                     // anchor column

        // May need to support merging spreadsheets, such that one overrides the other.
        // Could take a list of files to load, in reverse precedence order.
        public Holder (Path path)
        {
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
                    // to decide whether to covnert to dense matrix after the load finishes.
                    Sheet ws = new Sheet ();
                    wb.put (name, ws);
                    if (first == null) first = ws;
                    MatrixSparse N = new MatrixSparse ();
                    MatrixSparse S = new MatrixSparse ();
                    ws.numbers = N;
                    ws.strings = S;
                    int fillN = 0;
                    int fillS = 0;
                    final double fillThreshold = 0.5;

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
                                case "e": continue;
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

        public void parse (String cell)
        {
            if (cell.equals (this.cell)) return;
            this.cell = cell;

            String pieces[] = cell.split ("!");
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

        public int getRows (String cell)
        {
            parse (cell);
            return Math.max (0, ws.rows - ar);
        }

        public int getColumns (String cell)
        {
            parse (cell);
            return Math.max (0, ws.columns - ac);
        }

        public int getRowsInColumn (String cell)
        {
            parse (cell);
            int result = 0;
            for (int r = ar; r < ws.rows; r++)
            {
                if (ws.numbers.get (r, ac) == 0  &&  ws.strings.get (r, ac) == 0) break;
                result++;
            }
            return result;
        }

        public int getColumnsInRow (String cell)
        {
            parse (cell);
            int result = 0;
            for (int c = ac; c < ws.columns; c++)
            {
                if (ws.numbers.get (ar, c) == 0  &&  ws.strings.get (ar, c) == 0) break;
                result++;
            }
            return result;
        }

        public double getDouble (String cell, double row, double column)
        {
            parse (cell);
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

        public String getString (String cell, int row, int column)
        {
            parse (cell);
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

        Type op1 = null;
        if (operands.length > 1) op1 = operands[1].eval (context);
        String anchor = "";
        if (op1 instanceof Text) anchor = ((Text) op1).value;

        String info = evalKeyword (context, "info", "");
        if (info.equals ("columns"     )) return new Scalar (H.getColumns      (anchor));
        if (info.equals ("rows"        )) return new Scalar (H.getRows         (anchor));
        if (info.equals ("columnsInRow")) return new Scalar (H.getColumnsInRow (anchor));
        if (info.equals ("rowsInColumn")) return new Scalar (H.getRowsInColumn (anchor));

        Type prefix = evalKeyword (context, "prefix");
        double row = 0;
        double col = 0;

        Type op2 = null;
        if (operands.length > 2) op2 = operands[2].eval (context);
        Type op3 = null;
        if (operands.length > 3) op3 = operands[3].eval (context);

        if (op1 instanceof Scalar)
        {
            row                            = ((Scalar) op1).value;
            if (op2 instanceof Scalar) col = ((Scalar) op2).value;
        }
        else if (op2 instanceof Scalar)
        {
            row                            = ((Scalar) op2).value;
            if (op3 instanceof Scalar) col = ((Scalar) op3).value;
        }

        if (prefix instanceof Text) return new Text (prefix + H.getString (anchor, (int) row, (int) col));
        return new Scalar (H.getDouble (anchor, row, col));
    }

    public String toString ()
    {
        return "spreadsheet";
    }

    public Operator operandA ()
    {
        if (operands.length > 2) return operands[2];
        return null;
    }

    public Operator operandB ()
    {
        if (operands.length > 3) return operands[3];
        return null;
    }

    public boolean hasCorrectForm ()
    {
        if (operands.length < 4) return false;
        if (! (operands[0] instanceof Constant)) return false;
        if (! (operands[1] instanceof Constant)) return false;
        // Could also check if op2 and op3 are numeric expressions, but not worth the effort.
        return true;
    }

    public IteratorNonzero getIteratorNonzero (Instance context)
    {
        Holder H = open (context);
        if (H == null) return null;

        String cell = operands[1].getString ();  // This is required to be constant, so we can simply retrieve the string.
        H.parse (cell);

        Matrix A = H.ws.numbers;
        if (A instanceof MatrixSparse) return new IteratorSparse ((MatrixSparse) A, H.ar, H.ac);
        return ((MatrixDense) A).getRegion (H.ar, H.ac).getIteratorNonzero ();
    }
}
