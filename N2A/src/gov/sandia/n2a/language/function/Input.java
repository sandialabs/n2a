/*
Copyright 2016-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import java.util.HashMap;
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
import io.jhdf.HdfFile;
import io.jhdf.api.Attribute;
import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
import io.jhdf.exceptions.HdfException;
import tech.units.indriya.AbstractUnit;

public class Input extends Function
{
    public boolean warningTime;
    public String  warningIO;              // Only one message per file name.
    public int     exponentRow = UNKNOWN;  // For C backend with integer math. The exponent used to convert row or time values into a floating-point number.
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
        int exponentNew = getExponentHint (0) - centerNew;
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
            if (usesTime ()) op.exponentNext = exponentRow;
            else             op.exponentNext = 0;  // We expect an integer.
            op.determineExponentNext ();
        }
        if (operands.length > 2)
        {
            Operator op = operands[2];
            op.exponentNext = 0;  // We expect an integer.
            op.determineExponentNext ();
        }
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        for (Operator op : operands) op.determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public static abstract class Holder implements AutoCloseable
    {
        public static final double[] empty = {0};

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
        public double              epsilon;

        public Holder (Simulator simulator, boolean time)
        {
            this.time = time;
            epsilon = Math.sqrt (Math.ulp (1.0));  // sqrt (epsilon for time representation (currently double)), about 1e-8
            if (time  &&  simulator.currentEvent instanceof EventStep) epsilon = Math.min (epsilon, ((EventStep) simulator.currentEvent).dt / 1000);
        }

        public abstract void getRow (double requested) throws IOException;
    }

    public static class HolderXSV extends Holder
    {
        public BufferedReader      stream;
        public char                delimiter = ' ';            // Separator character. Allows switch between comma and space/tab.
        public boolean             delimiterSet;               // Indicates that check for CSV has been performed. Avoids constant re-checking.

        public HolderXSV (Simulator simulator, String path, boolean time) throws IOException
        {
            super (simulator, time);

            if (path.isEmpty ()) stream = new BufferedReader (new InputStreamReader (System.in));  // not ideal; reading stdin should be reserved for headless operation
            else                 stream = Files.newBufferedReader (simulator.jobDir.resolve (path));
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
                            nextValues[i] = Scalar.parseDouble (c, 0);

                            // Special case for formatted dates
                            // Convert date to Unix time. Dates before epoch will be negative.
                            if (i == timeColumn) nextValues[i] = convertDate (c, nextValues[i]);
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

    public static class HolderHDF5 extends Holder
    {
        protected String   fileName;
        protected Dataset  data;
        protected Object   flat;        // If slicing is not allowed, this holds the full raw data.
        protected int      rowCount;
        protected double   startingTime;
        protected double   period;
        protected double[] timestamps;  // If null, use startingTime+N*period. If non-null, treat this as time column.
        protected int      lastRow;     // When using timestamps, where to start search.

        public static class SubHolder
        {
            public HdfFile file;
            public int     users;
        }
        protected static HashMap<String,SubHolder> files = new HashMap<String,SubHolder> ();  // Keep track of all open HDF5 files in the app (regardless of which simulation they belong to). These can be shared by multiple HolderHDF5 objects.

        public HolderHDF5 (Simulator simulator, String fileName, String path, boolean nwb, boolean time) throws HdfException
        {
            super (simulator, time);

            this.fileName = fileName;
            SubHolder sub;
            synchronized (files)
            {
                sub = files.get (fileName);
                if (sub == null)
                {
                    sub = new SubHolder ();
                    sub.file = new HdfFile (simulator.jobDir.resolve (fileName));
                    files.put (fileName, sub);
                }
                sub.users++;
            }
            HdfFile file = sub.file;

            if (nwb)
            {
                Group timeSeries = (Group) file.getByPath (path);
                if (timeSeries == null) throw new HdfException ("Can't find TimeSeries: " + path);

                data = (Dataset) timeSeries.getChild ("data");

                Dataset ts = (Dataset) timeSeries.getChild ("timestamps");
                if (ts == null)  // Use startingTime+N*period.
                {
                    Dataset starting_time = (Dataset) timeSeries.getChild ("starting_time");
                    if (starting_time == null) throw new HdfException ("At least one of 'starting_time' or 'timestamps' must be defined: " + path);
                    startingTime = ((double[]) starting_time.getData ())[0];
                    Attribute rate = starting_time.getAttribute ("rate");
                    if (rate == null) throw new HdfException ("'starting_time/rate' must be defined: " + path);
                    period = 1 / (Float) rate.getData ();
                }
                else  // Use explicit time stamps.
                {
                    timestamps = (double[]) ts.getData ();
                }
            }
            else
            {
                data = (Dataset) file.getByPath (path);
            }

            int[] dimensions = data.getDimensions ();
            rowCount = dimensions[0];
            if (dimensions.length > 2) throw new HdfException ("TimeSeries data must be 1D or 2D: " + path);
            if (dimensions.length == 2) columnCount = dimensions[1];
            else                        columnCount = 1;

            if (time) currentLine = Double.NEGATIVE_INFINITY;
            currentValues = new double[columnCount];

            timeColumn = Integer.MAX_VALUE;
        }

        public void close ()
        {
            synchronized (files)
            {
                SubHolder sub = files.get (fileName);
                sub.users--;
                if (sub.users <= 0)
                {
                    try {sub.file.close ();}
                    catch (HdfException e) {}
                    files.remove (fileName);
                }
            }
        }

        public void getRow (double requested) throws IOException
        {
            // Since HDF5 allows random access, we just need to determine the requested row,
            // or rows that bracket the requested time.
            int row = -2;
            if (time)
            {
                if (period > 0)
                {
                    row = (int) Math.floor ((requested - startingTime) / period);
                }
                else if (timestamps != null)
                {
                    for (row = lastRow; row < rowCount; row++)
                    {
                        if (requested >= timestamps[row] - epsilon) continue;
                        // Now row points just past our desired current line.
                        lastRow = row--;
                        break;
                    }
                }
                // else TODO: need to specify time column
            }
            if (row == -2)
            {
                row = (int) Math.floor (requested);
            }

            boolean fetchCurrent = true;
            if (smooth)
            {
                if (nextLine - epsilon <= requested  &&  nextLine + period > requested)  // nextLine is re-usable.
                {
                    currentLine   = nextLine;
                    currentValues = nextValues;
                    fetchCurrent = false;
                }

                int nextRow = row + 1;
                if (nextRow < rowCount)
                {
                    if (period > 0)              nextLine = startingTime + nextRow * period;
                    else if (timestamps != null) nextLine = timestamps[nextRow];
                    else                         nextLine = nextRow;
                    // TODO: extract nextLine from time column, if available.
                    nextValues = getSlice (row);
                }
                else
                {
                    nextLine   = Double.NaN;
                    nextValues = empty;
                }
            }

            if (fetchCurrent  &&  row >= 0  &&  row < rowCount)
            {
                if (period > 0)              currentLine = startingTime + row * period;
                else if (timestamps != null) currentLine = timestamps[row];
                else                         currentLine = row;
                // TODO: extract currentLine from time column, if available.
                currentValues = getSlice (row);
            }
        }

        public double[] getSlice (int row) throws IOException
        {
            Class<?> clz = data.getJavaType ();
            if (flat == null)
            {
                try
                {
                    float[] floats = null;
                    if (columnCount > 1)
                    {
                        long[] anchor = new long[2];
                        int[]  size   = new int [2];
                        anchor[0] = row;
                        anchor[1] = 0;
                        size  [0] = 1;
                        size  [1] = columnCount;
                        if (clz == double.class) return ((double[][]) data.getData (anchor, size))[0];
                        if (clz == float.class) floats = ((float[][]) data.getData (anchor, size))[0];
                    }
                    else
                    {
                        long[] anchor = new long[1];
                        int[]  size   = new int [1];
                        anchor[0] = row;
                        size  [0] = 1;
                        if (clz == double.class) return (double[]) data.getData (anchor, size);  // Will have only a single element.
                        if (clz == float.class) floats = (float[]) data.getData (anchor, size);
                    }
                    if (floats == null)
                    {
                        throw new IOException ("Need code to handle numeric types other than double or float.");
                    }
                    else
                    {
                        double[] result = new double[columnCount];
                        for (int c = 0; c < columnCount; c++) result[c] = floats[c];
                        return result;
                    }
                }
                catch (HdfException e)
                {
                    flat = data.getDataFlat ();
                    // and fall through to "flat" handling below ...
                }
            }

            double[] result = new double[columnCount];
            int base = row * columnCount;
            if (clz == double.class)
            {
                for (int c = 0; c < columnCount; c++) result[c] = ((double[]) flat)[base + c];
            }
            else if (clz == float.class)
            {
                for (int c = 0; c < columnCount; c++) result[c] = ((float[]) flat)[base + c];
            }
            else
            {
                throw new IOException ("Need code to handle numeric types other than double or float.");
            }
            return result;
        }
    }

    public static double convertDate (String dateString, double asNumber)
    {
        // ISO 8601 and its prefixes
        SimpleDateFormat format = null;
        if (asNumber < 3000  &&  asNumber > 1000)  // Just the year. Two-digit years are not accepted.
        {
            format = new SimpleDateFormat ("yyyy");
        }
        else if (dateString.contains ("-"))  // Other parts of date/time are present
        {
            switch (dateString.length ())
            {
                case 7:  format = new SimpleDateFormat ("yyyy-MM");                   break;
                case 10: format = new SimpleDateFormat ("yyyy-MM-dd");                break;
                case 13: format = new SimpleDateFormat ("yyyy-MM-dd'T'HH");           break;
                case 16: format = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm");        break;
                case 19: format = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss");     break;
                case 23: format = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSS"); break;
            }
        }
        else if (dateString.contains ("/"))  // Conventional date
        {
            // TODO: add keyword parameter for correct date format, such as "mdy" or "ymd".
            format = new SimpleDateFormat ("MM/dd/yyyy");
        }
        if (format != null)
        {
            format.setTimeZone (TimeZone.getTimeZone ("GMT"));
            try {return format.parse (dateString).toInstant ().toEpochMilli () / 1000.0;}
            catch (ParseException e) {}
        }
        return asNumber;
    }

    public Holder getRow (Instance context, double line)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return null;  // If we can't cache a line from the requested stream, then semantics of this function are lost, so give up.

        Holder H = null;
        String path = ((Text) operands[0].eval (context)).value;
        try
        {
            boolean smooth =             evalKeywordFlag (context, "smooth");
            boolean time   = smooth  ||  evalKeywordFlag (context, "time");
            String  hdf    = evalKeyword (context, "hdf5", "");

            String key = path;
            if (! hdf.isBlank ()) key += "|" + hdf;  // Because multiple holders can share same HDF5 file.
            Object o = simulator.holders.get (key);
            if (o == null)  // Need to open new file.
            {
                if (hdf.isBlank ())
                {
                    H = new HolderXSV (simulator, path, time);  // can throw IOException
                }
                else
                {
                    boolean nwb = evalKeywordFlag (context, "nwb");
                    H = new HolderHDF5 (simulator, path, hdf, nwb, time);
                }

                simulator.holders.put (key, H);
            }
            else if (! (o instanceof Holder))  // Already exists, but is wrong type.
            {
                throw new Backend.AbortRun ("Reopening file as a different resource type: " + path);
            }
            else H = (Holder) o;  // Already exists, and is correct type. This is the most common case.

            if (H.time != time  &&  ! warningTime)
            {
                Backend.err.get ().println ("WARNING: Changed time mode for input(" + path + ")");
                warningTime = true;
            }
            H.smooth = smooth;  // remember for use by caller

            if (! H.time  &&  Double.isInfinite (line)) line = 0;
            H.getRow (line);
        }
        catch (IOException e)
        {
            if (! path.equals (warningIO))
            {
                Backend.err.get ().println ("WARNING: IO error on input(" + path + ")");
                warningIO = path;
            }
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
