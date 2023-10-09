/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.TreeMap;

import gov.sandia.n2a.backend.internal.EventStep;
import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.plugins.extpoints.Backend;
import tech.units.indriya.AbstractUnit;

public class Input extends Function
{
    public boolean timeWarning;
    public int     exponentTime = UNKNOWN; // For C backend with integer math. The exponent used to convert time values to integer, both from the input file and from the caller.
    public String  name;                   // For C backend, the name of the InputHolder object.
    public String  fileName;               // For C backend, the name of the string variable holding the file name, if any.

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "input";
            }

            public Operator createInstance ()
            {
                return new Input ();
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
        for (Operator op : operands) op.determineExponent (context);

        int centerNew   = MSB / 2;
        int exponentNew = getExponentHint (0) + MSB - centerNew;
        updateExponent (context, exponentNew, centerNew);
    }

    public boolean usesTime ()
    {
        if (getKeywordFlag ("time"  )) return true;
        if (getKeywordFlag ("smooth")) return true;
        return false;
    }

    public void determineExponentNext ()
    {
        if (operands.length > 1)
        {
            Operator op = operands[1];
            if (usesTime ()) op.exponentNext = exponentTime;
            else             op.exponentNext = MSB;  // We expect an integer.
            op.determineExponentNext ();
        }
        if (operands.length > 2)
        {
            Operator op = operands[2];
            op.exponentNext = MSB;  // We expect an integer.
            op.determineExponentNext ();
        }
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        for (Operator op : operands) op.determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public static class Holder implements AutoCloseable
    {
        public static final double[] empty = {0};

        public BufferedReader      stream;
        public double              currentLine   = -1;
        public double[]            currentValues = empty;
        public double              nextLine      = Double.NaN; // Initial condition is no line available.
        public double[]            nextValues    = empty;
        public Matrix              A;                          // vector returned if in whole-row mode (column parameter < 0)
        public double              Alast         = Double.NaN; // Line number when A was last generated.
        public Map<String,Integer> columnMap     = new TreeMap<String,Integer> ();
        public List<String>        headers       = new ArrayList<String> ();  // The inverse of columnMap
        public int                 columnCount;
        public boolean             time;                       // mode flag
        public boolean             smooth;                     // mode flag. When true, time must also be true. Does not change the behavior of Holder, just stored here for convenience.
        public int                 timeColumn;                 // We assume column 0, unless a header overrides this.
        public boolean             timeColumnSet;              // Indicates that a header appeared in the file, so timeColumn has been evaluated.
        public char                delimiter = ' ';            // Regular expression for separator character. Allows switch between comma and space/tab.
        public boolean             delimiterSet;               // Indicates that check for CSV has been performed. Avoids constant re-checking.
        public double              epsilon;

        public static Holder get (Simulator simulator, String path, boolean time) throws IOException
        {
            Holder result;
            Object o = simulator.holders.get (path);
            if (o == null)
            {
                result = new Holder ();

                if (path.isEmpty ()) result.stream = new BufferedReader (new InputStreamReader (System.in));  // not ideal; reading stdin should be reserved for headless operation
                else                 result.stream = Files.newBufferedReader (simulator.jobDir.resolve (path));

                result.time = time;
                result.epsilon = Math.sqrt (Math.ulp (1.0));  // sqrt (epsilon for time representation (currently double)), about 1e-8
                if (time  &&  simulator.currentEvent instanceof EventStep) result.epsilon = Math.min (result.epsilon, ((EventStep) simulator.currentEvent).dt / 1000);

                simulator.holders.put (path, result);
            }
            else if (! (o instanceof Holder))
            {
                Backend.err.get ().println ("ERROR: Reopening file as a different resource type.");
                throw new Backend.AbortRun ();
            }
            else result = (Holder) o;
            return result;
        }

        public void close ()
        {
            try {stream.close ();}
            catch (IOException e) {}
        }

        public void getRow (double requested) throws IOException
        {
            while (true)
            {
                // Read and process next line
                if (Double.isNaN (nextLine)  &&  stream.ready ())
                {
                    String line = stream.readLine ();
                    if (line != null  &&  ! line.isEmpty ())
                    {
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
                            delimiterSet =  delimiter != ' '  ||  ! line.trim ().isEmpty ();
                        }

                        // Break line into delimited strings, possibly quoted.
                        List<String> columns = new ArrayList<String> ();
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
                                columns.add (token.toString ());
                                token.setLength (0);
                                continue;
                            }
                            token.append (c);
                        }
                        if (! token.isEmpty ()) columns.add (token.toString ());

                        int currentColumnCount = columns.size ();
                        columnCount = Math.max (columnCount, currentColumnCount);

                        // Decide whether this is a header row or a value row
                        // This approach assumes that columns never start with white-space.
                        if (! columns.get (0).isEmpty ())
                        {
                            char firstCharacter = chars[0];
                            if (firstCharacter < '-'  ||  firstCharacter == '/'  ||  firstCharacter > '9')  // not a number, so must be column header
                            {
                                for (int i = 0; i < currentColumnCount; i++)
                                {
                                    String header = columns.get (i).trim ();
                                    if (! header.isEmpty ())
                                    {
                                        columnMap.put (header, i);
                                        while (headers.size () < i) headers.add ("");
                                        if (headers.size () <= i) headers.add (header);
                                        else                      headers.set (i, header);  // Replace an existing, possibly blank, header.
                                    }
                                }

                                // Make column count accessible to other code before first row of data is read.
                                if (A == null)
                                {
                                    if (time) currentLine = Double.NEGATIVE_INFINITY;
                                    if (currentValues.length != columnCount) currentValues = new double[columnCount];
                                }

                                // Select time column
                                // The time column should be specified in the first row of headers, if at all.
                                if (time  &&  ! timeColumnSet)
                                {
                                    int timeMatch = 0;
                                    for (Entry<String,Integer> e : columnMap.entrySet ())
                                    {
                                        int potentialMatch = 0;
                                        String header = e.getKey ().toLowerCase ();
                                        switch (header)
                                        {
                                            case "t":
                                            case "date":
                                                potentialMatch = 2;
                                                break;
                                            case "time": potentialMatch = 3; break;
                                            case "$t":   potentialMatch = 4; break;
                                            default:
                                                if (header.contains ("time")) potentialMatch = 1;
                                        }
                                        if (potentialMatch > timeMatch)
                                        {
                                            timeMatch = potentialMatch;
                                            timeColumn = e.getValue ();
                                        }
                                    }
                                    timeColumnSet = true;
                                }

                                continue;  // back to top of outer while loop, skipping any other processing below
                            }
                        }

                        nextValues = new double[columnCount];
                        for (int i = 0; i < currentColumnCount; i++)
                        {
                            String c = columns.get (i);
                            if (c.isEmpty ()) continue;  // and use default value of 0 that the array element was initialized with

                            // General case
                            try {nextValues[i] = Double.parseDouble (c);}
                            catch (NumberFormatException e) {}  // should leave nextValues[i] at 0

                            // Special case for ISO 8601 formatted date
                            // Convert date to Unix time. Dates before epoch will be negative.
                            if (i == timeColumn)
                            {
                                try
                                {
                                    SimpleDateFormat format = null;
                                    if (nextValues[i] < 3000  &&  nextValues[i] > 1000)  // Just the year. Two-digit years are not accepted.
                                    {
                                        format = new SimpleDateFormat ("yyyy");
                                    }
                                    else if (c.contains ("-"))  // Other parts of date/time are present
                                    {
                                        switch (c.length ())
                                        {
                                            case 7:  format = new SimpleDateFormat ("yyyy-MM");                   break;
                                            case 10: format = new SimpleDateFormat ("yyyy-MM-dd");                break;
                                            case 13: format = new SimpleDateFormat ("yyyy-MM-dd'T'HH");           break;
                                            case 16: format = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm");        break;
                                            case 19: format = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss");     break;
                                            case 23: format = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSS"); break;
                                        }
                                    }
                                    if (format != null)
                                    {
                                        format.setTimeZone (TimeZone.getTimeZone ("GMT"));
                                        nextValues[i] = format.parse (c).toInstant ().toEpochMilli () / 1000.0;
                                    }
                                }
                                catch (ParseException e) {}
                            }
                        }
                        if (time) nextLine = nextValues[timeColumn];
                        else      nextLine = currentLine + 1;
                    }
                }

                // Determine if we have the requested data
                if (requested <= currentLine) break;
                if (Double.isNaN (nextLine)) break;  // Return the current line, because another is not available. In general, we don't stall the simulator to wait for data.
                if (requested < nextLine - epsilon) break;
                currentLine   = nextLine;
                currentValues = nextValues;
                nextLine   = Double.NaN;
                nextValues = empty;
            }
        }
    }

    public Holder getRow (Instance context, double line)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return null;  // If we can't cache a line from the requested stream, then semantics of this function are lost, so give up.

        Holder H = null;
        try
        {
            boolean smooth =             evalKeywordFlag (context, "smooth");
            boolean time   = smooth  ||  evalKeywordFlag (context, "time");

            // get an input holder
            String path = ((Text) operands[0].eval (context)).value;
            H = Holder.get (simulator, path, time);

            if (H.time != time  &&  ! timeWarning)
            {
                Backend.err.get ().println ("WARNING: Changed time mode for input(" + path + ")");
                timeWarning = true;
            }
            H.smooth = smooth;  // remember for use by caller

            if (! H.time  &&  Double.isInfinite (line)) line = 0;
            H.getRow (line);
        }
        catch (IOException e)
        {
            return null;
        }

        return H;
    }

    public Type getType ()
    {
        // 1  argument  -- Just filename, so always return a matrix.
        // 2  arguments -- Filename, plus either row or mode, so always return a matrix
        // 3+ arguments -- Filename, row and column. Only return matrix if column<=0
        if (operands.length < 3  ||  operands[2].getDouble () < 0) return new MatrixDense ();
        return new Scalar (0);
    }

    public Type eval (Instance context)
    {
        double line = Double.NEGATIVE_INFINITY;
        Type op1 = null;
        if (operands.length > 1) op1 = operands[1].eval (context);
        if (op1 instanceof Scalar) line = ((Scalar) op1).value;

        Holder H = getRow (context, line);
        if (H == null) return getType ();

        int c = -1;
        boolean namedColumn = false;
        if (operands.length > 2)
        {
            Type columnSpec = operands[2].eval (context);
            if (columnSpec instanceof Text)
            {
                Integer columnMapping = H.columnMap.get (((Text) columnSpec).value);
                if (columnMapping == null) return new Scalar (0);
                c = columnMapping;
                namedColumn = true;
            }
            else  // Otherwise, just assume it is a Scalar
            {
                c = (int) Math.round (((Scalar) columnSpec).value);
            }
        }

        int columns    = H.currentValues.length;
        int lastColumn = columns - 1;
        if (H.smooth  &&  line >= H.currentLine  &&  Double.isFinite (H.currentLine)  &&  ! Double.isNaN (H.nextLine))
        {
            double b  = (line - H.currentLine) / (H.nextLine - H.currentLine);  // This should still work, even if line < H.currentLine.
            double b1 = 1 - b;
            if (c >= 0)
            {
                if (H.time  &&  ! namedColumn  &&  c >= H.timeColumn) c++;  // time column is not included in raw index
                if (c >= columns) c = lastColumn;
                return new Scalar (b * H.nextValues[c] + b1 * H.currentValues[c]);
            }
            else
            {
                if (H.Alast == line) return H.A;

                // Create a new matrix
                if (columns > 1)
                {
                    columns--;
                    H.A = new MatrixDense (1, columns);
                    int from = 0;
                    for (int to = 0; to < columns; to++)
                    {
                        if (from == H.timeColumn) from++;
                        H.A.set (0, to, b * H.nextValues[from] + b1 * H.currentValues[from]);
                        from++;
                    }
                }
                else  // There is always at least 1 column, enforced by Holder.
                {
                    H.A = new MatrixDense (1, 1);
                    H.A.set (0, 0, b * H.nextValues[0] + b1 * H.currentValues[0]);
                }

                H.Alast = line;
                return H.A;
            }
        }
        else
        {
            if (c >= 0)
            {
                if (H.time  &&  ! namedColumn  &&  c >= H.timeColumn) c++;  // time column is not included in raw index
                if (c >= columns) c = lastColumn;
                return new Scalar (H.currentValues[c]);
            }
            else
            {
                if (H.Alast == H.currentLine) return H.A;

                // Create a new matrix
                if (H.time  &&  columns > 1)
                {
                    columns--;
                    H.A = new MatrixDense (1, columns);
                    int from = 0;
                    for (int to = 0; to < columns; to++)
                    {
                        if (from == H.timeColumn) from++;
                        H.A.set (0, to, H.currentValues[from++]);
                    }
                }
                else
                {
                    H.A = new MatrixDense (H.currentValues, 0, 1, columns, columns, 1);
                }

                H.Alast = H.currentLine;
                return H.A;
            }
        }
    }

    public String toString ()
    {
        return "input";
    }
}
