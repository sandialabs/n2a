/*
Copyright 2019-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.backend.neuroml.XMLutility;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.function.Input.SubHolderHDF;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.linear.MatrixSparse;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import gov.sandia.n2a.util.ParseXSV;
import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
import tech.units.indriya.AbstractUnit;

public class Table extends Function
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

        if (getKeyword ("info") == null)  // normal mode. This includes string mode. In that case we don't care about exponent.
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
        if (getKeyword ("string") != null) return new Text ();
        return new Scalar ();
    }

    public static interface Holder
    {
        public default void parse (String anchor)
        {
            throw new AbortRun ("anchor keyword is not supported for given file type");
        }

        public default int getColumnsInRow ()
        {
            throw new AbortRun ("columnsInRow keyword is not supported for given file type");
        }

        public default int getRowsInColumn ()
        {
            throw new AbortRun ("rowsInColumn keyword is not supported for given file type");
        }

        public int    rows ();
        public int    columns ();
        public int    getColumnIndex (String columnName);
        public int    getRowIndex (int columnIndex, Object columnValue);
        public double getDouble (double row, double column);
        public String getString (int    row, int    column);
    }

    /**
        Table-style access for HDF files.
        Handles several general cases as well as specialty formats (SONATA, possibly NWB).
        See also Input.HolderHDF. The access style is different enough that different classes are justified.
    **/
    public static class HolderHDF extends Matrix implements Holder, AutoCloseable
    {
        protected Path                filePath;
        protected io.jhdf.api.Node    root;             // Can be either a Dataset or a Group.
        protected Group               sonataPopulation; // Population node, for finding related resources. null if not a SONATA file.
        protected boolean             sonataEdges;      // root is an attribute associated with a SONATA style sparse edge list.
        protected boolean             sonataSpikes;     // root is a group that contains SONATA style input spikes.
        protected int[]               dims;             // Size of data. Gets modified to always be 2D.
        protected int                 dimCount;         // Original length of "dims"
        protected Map<String,Integer> rowMap;
        protected Map<String,Integer> columnMap;
        protected List<String>        headers;          // The inverse of columnMap
        protected Matrix              sonataRaster;     // Spike array, pre-loaded. Assumes that input spike pattern is relatively small, so can easily fit in memory.

        public static final int chunkSize = 1000000;

        /**
            @param fileName To the HDF file. Not the same as the key for looking Holder. Specifically, the
            holder key includes both HDF file path and path to resource inside HDF file. Here, we are only
            interested in the actual path to file, so we can keep track of how many holders are using the file.
            @param resource To the resource inside the HDF file.
        **/
        public HolderHDF (Path filePath, String resource)
        {
            this.filePath = filePath;
            SubHolderHDF sub = SubHolderHDF.allocate (filePath);
            root = sub.file.getByPath (resource);

            // Detect SONATA data that requires special interpretation.
            // It should be possible to use the magic string (attribute "magic", a uint32 with value 2682).
            // However, SONATA files don't consistently set this.
            // Instead, we assume that "spikes" and "edges" indicate the presence of special SONATA data.
            // "nodes" does not require special handling.
            List<io.jhdf.api.Node> parents = new ArrayList<io.jhdf.api.Node> ();
            io.jhdf.api.Node p = root;
            parents.add (p);
            while (p != sub.file)
            {
                p = p.getParent ();
                parents.add (0, p);
            }
            int parentCount = parents.size ();
            if (parentCount > 2)
            {
                sonataPopulation = (Group) parents.get (2);
                switch (parents.get (1).getName ()) // Name of group that contains sonataPopulation.
                {
                    // Do some extra verification.
                    case "spikes":
                        sonataSpikes = sonataPopulation.getChild ("node_ids") != null  &&  sonataPopulation.getChild ("timestamps") != null;
                        break;
                    case "edges":
                        sonataEdges = sonataPopulation.getChild ("source_node_id") != null  &&  sonataPopulation.getChild ("target_node_id") != null;
                        break;
                }
                if (! sonataSpikes  &&  ! sonataEdges) sonataPopulation = null;
            }

            if (root.isGroup ())
            {
                // TODO: handle NWB TimeSeries
                dims      = new int [2];
                dimCount  = 1;  // Data columns should be single dimensional.
                columnMap = new TreeMap<String,Integer> ();
                headers   = new ArrayList<String> ();
                for (io.jhdf.api.Node node : (Group) root)
                {
                    if (node.isGroup ()) continue;
                    int temp[] = ((Dataset) node).getDimensions ();
                    if (temp.length != 1) throw new AbortRun ("table() expects HDF dataset to be 1-dimensional: " + node.getName ());
                    if (dims[0] < temp[0]) dims[0] = temp[0];

                    String columnName = node.getName ();
                    columnMap.put (columnName, headers.size ());
                    headers.add (columnName);
                }
                dims[1] = headers.size ();
            }
            else  // root is Dataset
            {
                dims = ((Dataset) root).getDimensions ();
                dimCount = dims.length;
                if (dimCount == 1)
                {
                    int temp = dims[0];
                    dims = new int[2];
                    dims[0] = temp;
                    dims[1] = 1;
                }
            }
        }

        public void close () throws Exception
        {
            SubHolderHDF.release (filePath);
        }

        public int rows ()
        {
            return dims[0];
        }

        public int columns ()
        {
            return dims[1];
        }

        public int getColumnIndex (String columnName)
        {
            Integer result = -1;
            if (root.isGroup ())
            {
                result = columnMap.get (columnName);
                if (result == null) return -1;
            }
            else
            {
                try {result = Integer.valueOf (columnName);}
                catch (NumberFormatException e) {}
            }
            return result;
        }

        /**
            Searches for value in given column.
            Assumes that the number of rows is small enough that indexing in memory is practical.
            For larger cases, really shouldn't be using the approach at all.
        **/
        public int getRowIndex (int columnIndex, Object columnValue)
        {
            if (columnIndex < 0  || columnIndex >= dims[1]) return -1;

            Dataset columnData;
            if (root.isGroup ())
            {
                String columnName = headers.get (columnIndex);
                columnData = ((Group) root).getDatasetByPath (columnName);
            }
            else
            {
                columnData = (Dataset) root;
            }
            Class<?> type = columnData.getJavaType ();

            if (rowMap == null)
            {
                rowMap = new HashMap<String,Integer> ();

                long offset[] = new long[dimCount];
                int  count [] = new int [dimCount];
                offset[0] = 0;
                count [0] = dims[0];
                if (dimCount > 1)
                {
                    count[1] = 1;
                    if (! root.isGroup ()) offset[1] = columnIndex;
                }

                Object result = columnData.getData (offset, count);
                int i = 0;
                if (type == double.class)
                {
                    for (double d : (double[]) result) rowMap.put (String.valueOf (d), i++);
                }
                else if (type == float.class)
                {
                    for (float f : (float[]) result) rowMap.put (String.valueOf (f), i++);
                }
                else if (type == int.class)
                {
                    for (int n : (int[]) result) rowMap.put (String.valueOf (n), i++);
                }
                else if (type == BigInteger.class)
                {
                    for (BigInteger n : (BigInteger[]) result) rowMap.put (n.toString (), i++);
                }
                else if (type == String.class)
                {
                    for (String s : (String[]) result) rowMap.put (s, i++);
                }
                else throw new AbortRun ("Need code to handle data type.");
            }

            Integer result = rowMap.get (columnValue.toString ());
            if (result == null) return -1;
            return result;
        }

        public double getDouble (double row, double column)
        {
            if (sonataEdges  ||  sonataSpikes) throw new AbortRun ("Should access SONATA edges or spikes through matrix()");

            // Bracket the source rows for an interpolated or extrapolated value.
            int r = (int) row;
            int r1 = r + 1;
            if (r < 0) r = 0;
            if (r1 >= dims[0]) r1 = dims[0] - 1;
            if      (r1 < r) r1 = r;
            else if (r > r1) r = r1;

            // Bracket source columns.
            // Some of this may be meaningless for named columns. We don't worry about that.
            int c = (int) column;
            int c1 = c + 1;
            if (c < 0) c = 0;
            if (c1 >= dims[1]) c1 = dims[1] - 1;
            if      (c1 < c) c1 = c;
            else if (c > c1) c = c1;

            Dataset columnData;
            long offset[] = new long[dimCount];
            int  count [] = new int [dimCount];
            offset[0] = r;
            count [0] =  r == r1 ? 1 : 2;
            if (root.isGroup ())
            {
                String columnName = headers.get (c);
                columnData = ((Group) root).getDatasetByPath (columnName);
                c1 = c;  // Don't do interpolation for named columns. The result will only be surprising if the user requests a column that is out of range.
            }
            else  // root is a Dataset
            {
                columnData = (Dataset) root;
                if (dimCount > 1)
                {
                    offset[1] = c;
                    count [1] =  c == c1 ? 1 : 2;
                }
            }

            double d[][] = new double[2][2];
            Object block = columnData.getData (offset, count);
            Class<?> type = columnData.getJavaType ();
            if (type == double.class)
            {
                if (dimCount == 1  ||  count[1] == 1)  // 1D
                {
                    double[] temp = (double[]) block;
                    for (int i = 0; i < count[0]; i++) d[i][0] = temp[i];
                }
                else  // 2D
                {
                    double[][] temp = (double[][]) block;
                    for (int i = 0; i < count[0]; i++)
                    {
                        for (int j = 0; j < count[1]; j++)
                        {
                            d[i][j] = temp[i][j];
                        }
                    }
                }
            }
            else if (type == float.class)
            {
                if (dimCount == 1  ||  count[1] == 1)
                {
                    float[] temp = (float[]) block;
                    for (int i = 0; i < count[0]; i++) d[i][0] = temp[i];
                }
                else
                {
                    float[][] temp = (float[][]) block;
                    for (int i = 0; i < count[0]; i++)
                    {
                        for (int j = 0; j < count[1]; j++)
                        {
                            d[i][j] = temp[i][j];
                        }
                    }
                }
            }
            else if (type == int.class)
            {
                if (dimCount == 1  ||  count[1] == 1)
                {
                    int[] temp = (int[]) block;
                    for (int i = 0; i < count[0]; i++) d[i][0] = temp[i];
                }
                else
                {
                    int[][] temp = (int[][]) block;
                    for (int i = 0; i < count[0]; i++)
                    {
                        for (int j = 0; j < count[1]; j++)
                        {
                            d[i][j] = temp[i][j];
                        }
                    }
                }
            }
            else if (type == BigInteger.class)
            {
                if (dimCount == 1  ||  count[1] == 1)
                {
                    BigInteger[] temp = (BigInteger[]) block;
                    for (int i = 0; i < count[0]; i++) d[i][0] = temp[i].doubleValue ();
                }
                else
                {
                    BigInteger[][] temp = (BigInteger[][]) block;
                    for (int i = 0; i < count[0]; i++)
                    {
                        for (int j = 0; j < count[1]; j++)
                        {
                            d[i][j] = temp[i][j].doubleValue ();
                        }
                    }
                }
            }
            else
            {
                throw new AbortRun ("Need code to handle numeric type: " + type.getSimpleName ());
            }

            if (c == c1)
            {
                if (r == r1) return d[0][0];

                double a = row - r;
                double a1 = 1 - a;
                return a1 * d[0][0] + a * d[1][0];
            }
            else
            {
                double b = column - c;
                double b1 = 1 - b;
                if (r == r1) return b1 * d[0][0] + b * d[0][1];

                double a = row - r;
                double a1 = 1 - a;
                return a1 * (b1 * d[0][0] + b * d[0][1]) + a * (b1 * d[1][0] + b * d[1][1]);  // full bilinear interpolation
            }
        }

        public String getString (int row, int column)
        {
            if (sonataEdges  ||  sonataSpikes)  throw new AbortRun ("Should access SONATA edges or spikes through matrix()");

            if (row < 0  ||  row >= dims[0]  ||  column < 0  ||  column >= dims[1]) return "";
            long offset[] = new long[dimCount];
            int  count [] = new int [dimCount];
            offset[0] = row;
            count [0] = 1;
            if (dimCount > 1) count[1] = 1;

            Dataset columnData;
            if (root.isGroup ())
            {
                String columnName = headers.get (column);
                columnData = ((Group) root).getDatasetByPath (columnName);
            }
            else  // root is a Dataset
            {
                columnData = (Dataset) root;
                if (dimCount > 1) count[1] = column;
            }
            Object result = columnData.getData (offset, count);
            Class<?> type = columnData.getJavaType ();
            if (type == String.class) return ((String[]) result)[0];
            if (type == double.class) return String.valueOf (((double[]) result)[0]);
            if (type == float .class) return String.valueOf (((float []) result)[0]);
            throw new AbortRun ("getString() needs code for numeric type: " + type.getSimpleName ());
        }

        public double get (int row, int column)
        {
            return getDouble (row, column);
        }

        public void set (int row, int column, double a)
        {
            throw new AbortRun ("HolderHDF does not support set()");
        }

        /**
            Utility for ReadMatrix().
            It is the returned Matrix object that is stored in the simulator's holder list.
            ReadMatrix() closes this HolderHDF upon return. If we return ourselves as the matrix,
            we need to take out an extra SubHolderHDF allocation, to prevent the underlying file from being closed.
            If we construct a specialty matrix, that matrix is responsible to call allocate() and release().

            Cases:
            * SONATA "spikes" list. "hdf" keyword points to the Group that holds the spike list (usually named after the population).
            * SONATA "edges" list. "hdf" keyword points to the primary attribute being iterated.
            * Any 1D or 2D dataset.
            * Several parallel datasets under a group. Can either be SONATA attributes or any other data structured the same way.

            For sparse iteration, several Matrix objects coordinate with each other to take advantage of knowledge about current row in the SONATA data.
        **/
        public Matrix getMatrix ()
        {
            if (sonataSpikes) return new MatrixSonataSpikesHDF (filePath, sonataPopulation);
            if (sonataEdges)  return new MatrixSonataEdgesHDF  (filePath, sonataPopulation, root == sonataPopulation ? null : (Dataset) root);

            // * Any 1D or 2D dataset.
            // * Several parallel datasets under a group. Can either be SONATA attributes or any other data structured the same way.
            SubHolderHDF.allocate (filePath);  // This Table.HolderHDF object will be closed upon return. Since we are returning ourselves as the matrix, we need to take out an additional allocation.
            return this;
        }

        public IteratorNonzero getIteratorNonzero ()
        {
            if (root.isGroup ()  ||  dimCount < 2) throw new AbortRun ("IteratorNonzero requires a 2D dataset");
            // Also, we don't bother iterating over attribute columns, since it isn't a meaningful use case.
            return new IteratorNonzeroHDF ();
        }

        public class IteratorNonzeroHDF implements IteratorNonzero
        {
            protected Class<?>   type   = ((Dataset) root).getJavaType ();
            protected double[][] data;
            protected long[]     offset = new long[2];
            protected int[]      count  = new int [2];

            protected double value;
            protected long   row; // of "value"
            protected long   column;

            protected double nextValue;
            protected long   nextRow;
            protected long   nextColumn = -1;

            public IteratorNonzeroHDF ()
            {
                if (type != double.class  &&  type != float.class  &&  type != int.class  &&  type != BigInteger.class) throw new AbortRun ("IteratorNonzeroHDF needs additional code to support data type.");
                count[0] = Math.max (1, chunkSize / dims[1]);
                count[1] = dims[1];
                offset[0] = -count[0];  // Trigger load of first block.
                offset[1] = 0;
                getNext ();
            }

            protected void getNext ()
            {
                for (; nextRow < dims[0]; nextRow++)
                {
                    int nr = (int) (nextRow - offset[0]);  // next row relative to current block of data
                    if (nr >= count[0])  // Out of data, so load another block.
                    {
                        offset[0] = nextRow;
                        count[0] = Math.min (count[0], (int) (dims[0] - nextRow));  // Don't read past end of dataset.
                        Object temp = ((Dataset) root).getData (offset, count);
                        if (type == double.class)
                        {
                            data = (double[][]) temp;
                        }
                        else if (type == float.class)
                        {
                            data = new double[count[0]][count[1]];
                            float[][] f = (float[][]) temp;
                            for (int r = 0; r < count[0]; r++)
                            {
                                for (int c = 0; c < count[1]; c++) data[r][c] = f[r][c];
                            }
                        }
                        else if (type == int.class)
                        {
                            data = new double[count[0]][count[1]];
                            int[][] n = (int[][]) temp;
                            for (int r = 0; r < count[0]; r++)
                            {
                                for (int c = 0; c < count[1]; c++) data[r][c] = n[r][c];
                            }
                        }
                        else if (type == BigInteger.class)
                        {
                            data = new double[count[0]][count[1]];
                            BigInteger[][] n = (BigInteger[][]) temp;
                            for (int r = 0; r < count[0]; r++)
                            {
                                for (int c = 0; c < count[1]; c++) data[r][c] = n[r][c].doubleValue ();
                            }
                        }
                    }

                    while (true)
                    {
                        if (++nextColumn >= dims[1]) break;
                        int nc = (int) (nextColumn - offset[1]);
                        nextValue = data[nr][nc];
                        if (nextValue != 0) return;
                    }
                    nextColumn = -1;
                }
            }

            public boolean hasNext ()
            {
                return nextColumn >= 0;
            }

            public Double next ()
            {
                if (nextColumn < 0) return null;
                value  = nextValue;
                row    = nextRow;
                column = nextColumn;
                getNext ();
                return value;
            }

            public int getRow ()
            {
                return (int) row;
            }

            public int getColumn ()
            {
                return (int) column;
            }
        }
    }

    public static class SharedRow
    {
        long row = -1;
    }

    /**
        Special sparse matrix for SONATA edge lists, backed by HDF data.
        This returns a sparse iterator that simply reads through source and target node IDs serially.
        To support fast lookup of multiple attributes, we keep a static cache of recently iterated edges.
        This hints the row needed to retrieve the attribute value.

        This class only works when the edge group structure is simple. That is, only one value in edge_group_id,
        and edge_group_index is zero-based contiguous. Anything else requires either more complex lookup
        or separated tables. Such tables will probably be in XSV rather than HDF.
    **/
    public static class MatrixSonataEdgesHDF extends Matrix implements AutoCloseable
    {
        protected Path      filePath;  // of HDF file, so it can be disposed when done.
        protected Dataset   datasetSource;
        protected Dataset   datasetTarget;
        protected Dataset   datasetAttribute;
        protected double[]  chunkAttribute;
        protected Class<?>  type;
        protected long      rowCount;
        protected long[]    offset     = {-HolderHDF.chunkSize};  // For chunkAttribute. The iterator below has its own copy for the source and target node IDs.
        protected int[]     count      = { HolderHDF.chunkSize};
        protected double    emptyValue = 0;
        protected SharedRow cached;

        public MatrixSonataEdgesHDF (Path filePath, Group population, Dataset attribute)
        {
            this.filePath = filePath;
            SubHolderHDF.allocate (filePath);

            // Set up to share current row between iterator and other attribute matrices.
            // This is somewhat of an abuse of the Simulator.holder system, but it is a
            // simple way to guarantee that these resources are simulator-specific and
            // will be disposed at the end.
            String key = "$HDFrow";  // Unlikely to ever be a file name.
            Simulator simulator = Simulator.instance.get ();
            @SuppressWarnings("unchecked")
            Map<String,SharedRow> cache1 = (Map<String,SharedRow>) simulator.holders.get (key);
            if (cache1 == null)
            {
                cache1 = new HashMap<String,SharedRow> ();
                simulator.holders.put (key, cache1);
            }
            String populationName = population.getName ();  // Name of the edge collection (distinct from source or target population.
            cached = cache1.get (populationName);
            if (cached == null)
            {
                cached = new SharedRow ();
                cache1.put (populationName, cached);
            }

            datasetSource    = population.getDatasetByPath ("source_node_id");
            datasetTarget    = population.getDatasetByPath ("target_node_id");
            rowCount         = datasetTarget.getSize ();  // Should be same as datasetSource.size().
            datasetAttribute = attribute;
            if (attribute != null) type = attribute.getJavaType ();
        }

        public void close () throws Exception
        {
            SubHolderHDF.release (filePath);
        }

        public int rows ()
        {
            throw new AbortRun ("MatrixSonataEdgesHDF does not support rows()");
        }

        public int columns ()
        {
            throw new AbortRun ("MatrixSonataEdgesHDF does not support columns()");
        }

        /**
            Return attribute associated with the current iterator position.
        **/
        public double get (int row, int column)
        {
            // Blindly assume that the shared row is correct.
            // The alternative is to read back source and target IDs to verify they match row and column.
            // This version assumes no retrograde movement through edges. If there are multiple threads
            // moving the iterator, then it may be necessary to backtrack as many rows as there are threads (T).
            // In this case, we need to more carefully manage chunkAttribute. Could hold on to the final T
            // rows from the previous chunk in a separate buffer.

            // There are two case for this get() function:
            // * There is an associated iterator. -- chunkAttribute will be kept up to date by the iterator.
            // * Otherwise -- We load chunkAttribute here. This should stay in sync with the iterator,
            //                but that is not strictly necessary.
            if (datasetAttribute == null) return 1;  // If attribute is absent, we assume boolean matrix. In that case, always return 1, because this function should only be called for existent elements.
            long r = cached.row;
            if (r >= rowCount) return emptyValue;
            int rr = (int) (r - offset[0]);  // row relative to current block of data
            if (rr >= count[0])
            {
                // Out of data, so load another block.
                rr = 0;
                offset[0] = r;
                count[0] = Math.min (count[0], (int) (rowCount - r));  // Don't read past end of dataset.
                loadChunkAttribute ();
            }
            return chunkAttribute[rr];
        }

        /**
            Because rows() and columns() are not supported, it's not possible to use the default
            implementation of this function from Matrix. This version redirects calls to get(r,c).
        **/
        public double get (double row, double column, int mode)
        {
            return get ((int) row, (int) column);  // Truncate coordinates. Our get(r,c) is mostly consistent with ZEROS mode.
        }

        public void set (int row, int column, double a)
        {
            throw new AbortRun ("MatrixSonataEdgesHDF does not support set()");
        }

        protected void loadChunkAttribute ()
        {
            Object temp = datasetAttribute.getData (offset, count);
            if (type == double.class)
            {
                chunkAttribute = (double[]) temp;
            }
            else if (type == float.class)
            {
                chunkAttribute = new double[count[0]];
                float[] f = (float[]) temp;
                for (int r = 0; r < count[0]; r++) chunkAttribute[r] = f[r];
            }
            else if (type == int.class)
            {
                chunkAttribute = new double[count[0]];
                int[] n = (int[]) temp;
                for (int r = 0; r < count[0]; r++) chunkAttribute[r] = n[r];
            }
            else if (type == BigInteger.class)
            {
                chunkAttribute = new double[count[0]];
                BigInteger[] n = (BigInteger[]) temp;
                for (int r = 0; r < count[0]; r++) chunkAttribute[r] = n[r].doubleValue ();
            }
        }

        public class IteratorEdge implements IteratorNonzero
        {
            protected BigInteger[] chunkSource;
            protected BigInteger[] chunkTarget;
            protected long         row = -1;
            protected int          rr;  // row relative to start of chunk

            public IteratorEdge ()
            {
                cached.row = -1;  // Reset the shared row when an iterator goes into service. Hopefully there is only one iterator!
            }

            protected void getNext ()
            {
                cached.row = ++row;
                if (row >= rowCount) return;
                rr = (int) (row - offset[0]);  // row relative to current block of data
                if (rr < count[0]) return;

                // Out of data, so load another block.
                rr = 0;
                offset[0] = row;
                count[0] = Math.min (count[0], (int) (rowCount - row));  // Don't read past end of dataset.

                chunkSource = (BigInteger[]) datasetSource.getData (offset, count);
                chunkTarget = (BigInteger[]) datasetTarget.getData (offset, count);
                if (datasetAttribute != null) loadChunkAttribute ();
            }

            public boolean hasNext ()
            {
                return row + 1 < rowCount;
            }

            public Double next ()
            {
                getNext ();
                if (row >= rowCount) return null;
                if (datasetAttribute == null) return 1.0;  // Since we iterate only existing elements, always return true.
                return chunkAttribute[rr];
            }

            public int getRow ()
            {
                return chunkSource[rr].intValue ();
            }

            public int getColumn ()
            {
                return chunkTarget[rr].intValue ();
            }
        }

        public IteratorNonzero getIteratorNonzero ()
        {
            return new IteratorEdge ();
        }
    }

    /**
        Special sparse matrix for SONATA spike rasters, backed by HDF data.
        Does not bring in all data. Instead, this merely indexes the node_ids.
        We require the datasets (node_ids, timestamps) to be sorted by node_id then by spike time.
        If that is not satisfied, this class will fail.
    **/
    public static class MatrixSonataSpikesHDF extends Matrix implements AutoCloseable
    {
        protected Path    filePath;  // For releasing the HDF file when we are done.
        protected Dataset datasetTime;
        protected long[]  columnIDs;
        protected long[]  columnPointers;
        protected int     rows;  // Tallest column seen.
        protected double  emptyValue = Double.POSITIVE_INFINITY;

        public MatrixSonataSpikesHDF (Path filePath, Group population)
        {
            SubHolderHDF.allocate (filePath);
            datasetTime = population.getDatasetByPath ("timestamps");

            // Scan node_ids and assemble index.
            List<Long>   listIDs      = new ArrayList<Long> ();
            List<Long>   listPointers = new ArrayList<Long> ();
            Dataset      datasetID    = population.getDatasetByPath ("node_ids");
            long[]       chunkID      = null;
            long[]       offset       = {0};
            int[]        size         = {0};
            long         lastID       = -1;
            long         lastPointer  = 0;
            long         count        = datasetID.getSize ();
            for (long i = 0; i < count; i++)
            {
                if (i % HolderHDF.chunkSize == 0)
                {
                    offset[0] = i;
                    size[0] = (int) Math.min (HolderHDF.chunkSize, count - i);
                    chunkID = (long[]) datasetID.getData (offset, size);
                }
                int  index = (int) (i - offset[0]);
                long ID    = chunkID[index];
                if (ID != lastID)
                {
                    listIDs     .add (ID);
                    listPointers.add (i);
                    lastID      = ID;
                    rows        = Math.max (rows, (int) (i - lastPointer));
                    lastPointer = i;
                }
            }
            lastID++;
            listIDs     .add (lastID);
            listPointers.add (count);
            rows = Math.max (rows, (int) (count - lastPointer));
            int listSize = listIDs.size ();

            columnPointers = new long[listSize];
            for (int i = 0; i < listSize; i++) columnPointers[i] = listPointers.get (i);
            if (lastID >= listSize)  // listIDs has skips. (lastID + 1 - listSize) is the number of skips.
            {
                columnIDs = new long[listSize];
                for (int i = 0; i < listSize; i++) columnIDs[i] = listIDs.get (i);
            }
            // else listIDs is zero-based contiguous. In that case, we can use direct lookup rather than a search.
        }

        public void close () throws Exception
        {
            SubHolderHDF.release (filePath);
        }

        public int rows ()
        {
            return rows;
        }

        public int columns ()
        {
            return columnPointers.length - 1;
        }

        public double get (int row, int column)
        {
            int c;
            if (columnIDs == null)
            {
                c = column;
            }
            else
            {
                c = Arrays.binarySearch (columnIDs, column);
                if (c < 0) return emptyValue;
            }
            if (c >= columnPointers.length) return emptyValue;
            if (row >= (int) (columnPointers[c+1] - columnPointers[c])) return emptyValue;
            long[] offset = {columnPointers[c] + row};
            int[]  count  = {1};
            return ((double[]) datasetTime.getData (offset, count))[0];  // This is rather slow. One possibility is to load the entire array of time values into memory.
        }

        public void set (int row, int column, double a)
        {
            throw new AbortRun ("MatrixSonataSpikesHDF does not support set()");
        }

        public void setEmptyValue (double a)
        {
            emptyValue = a;
        }
    }

    public static class Sheet
    {
        public Matrix              numbers;   // Dense matrix stores empty cells and strings as 0. Sparse matrix does not store them at all.
        public Matrix              strings;   // 1-based indices into string collection. Empty cells and number cells are 0.
        public int                 rows;
        public int                 columns;
        public Integer             index[];   // Array of row numbers, sorted according to key (specified elsewhere).
        public Map<String,Integer> columnMap; // from header text to index
    }

    public static class HolderSheet implements Holder
    {
        protected List<String>      strings = new ArrayList<String> ();     // collection of all strings that appear in the workbook
        protected Map<String,Sheet> wb      = new HashMap<String,Sheet> (); // workbook, a collection of worksheets
        protected Sheet             first;                                  // The first sheet defined in the file. This is the default when no sheet is specified in cell address.
        protected String            anchor;                                 // The most recently parsed anchor cell address. Includes sheet name and coordinates.
        protected Sheet             ws;                                     // anchor sheet
        protected int               ar;                                     // anchor row
        protected int               ac;                                     // anchor column

        public HolderSheet (Path path)
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
                throw new AbortRun ();
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

                class ProcessXSV extends ParseXSV
                {
                    int fillN = 0;
                    int fillS = 0;

                    public boolean processLine (List<String> parts)
                    {
                        int count = parts.size ();
                        for (int c = 0; c < count; c++)
                        {
                            String temp = parts.get (c);
                            if (temp.isBlank ()) continue;

                            // First try to interpret as number. On failure, store as string.
                            double value = Scalar.parseDouble (temp, 0);
                            if (value == 0)  // Because temp is non-blank, zero indicates not parseable as number.
                            {
                                strings.add (temp);
                                int stringIndex = strings.size ();
                                ws.strings.set (ws.rows, c, stringIndex);
                                fillS++;
                            }
                            else
                            {
                                ws.numbers.set (ws.rows, c, value);
                                fillN++;
                            }
                        }
                        ws.rows++;
                        ws.columns = Math.max (ws.columns, columns);
                        return true;
                    }
                }
                ProcessXSV process = new ProcessXSV ();
                try (BufferedReader reader = Files.newBufferedReader (path))
                {
                    process.parse (reader);
                }
                catch (Exception e)
                {
                    PrintStream err = Backend.err.get ();
                    err.println ("ERROR: Failed to parse CSV file: " + path);
                    e.printStackTrace (err);
                    throw new AbortRun ();
                }

                // Check fill-in and possibly convert to dense
                int Nrows = ws.numbers.rows ();
                int Ncols = ws.numbers.columns ();
                int Srows = ws.strings.rows ();
                int Scols = ws.strings.columns ();
                if ((double) process.fillN / (Nrows * Ncols) > fillThreshold) ws.numbers = new MatrixDense (ws.numbers);
                if ((double) process.fillS / (Srows * Scols) > fillThreshold) ws.strings = new MatrixDense (ws.strings);

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
                org.w3c.dom.Node docElement = doc.getDocumentElement ();
                for (org.w3c.dom.Node rel = docElement.getFirstChild (); rel != null; rel = rel.getNextSibling ())
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
                        for (org.w3c.dom.Node si = docElement.getFirstChild (); si != null; si = si.getNextSibling ())
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
                        org.w3c.dom.Node cellXfs = XMLutility.getChild (docElement, "cellXfs");
                        int styleNumber = 0;
                        for (org.w3c.dom.Node xf = cellXfs.getFirstChild (); xf != null; xf = xf.getNextSibling ())
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
                org.w3c.dom.Node sheets = XMLutility.getChild (docElement, "sheets");
                for (org.w3c.dom.Node sheet = sheets.getFirstChild (); sheet != null; sheet = sheet.getNextSibling ())
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
                    org.w3c.dom.Node sheetData = XMLutility.getChild (docElement, "sheetData");
                    for (org.w3c.dom.Node row = sheetData.getFirstChild (); row != null; row = row.getNextSibling ())
                    {
                        for (org.w3c.dom.Node c = row.getFirstChild (); c != null; c = c.getNextSibling ())
                        {
                            parseA1 (XMLutility.getAttribute (c, "r"));
                            switch (XMLutility.getAttribute (c, "t"))
                            {
                                case "s":  // indexed string
                                    org.w3c.dom.Node v = XMLutility.getChild (c, "v");
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
                                    org.w3c.dom.Node si = XMLutility.getChild (c, "si");
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
                throw new AbortRun ();
            }
        }

        public static String extractSI (org.w3c.dom.Node si)
        {
            String result = "";
            for (org.w3c.dom.Node n = si.getFirstChild (); n != null; n = n.getNextSibling ())
            {
                switch (n.getNodeName ())
                {
                    case "t":  // simple text element
                        result += n.getTextContent ();
                        break;
                    case "r":  // rich text element
                        for (org.w3c.dom.Node m = n.getFirstChild (); m != null; m = m.getNextSibling ())
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
            ws.columnMap = null;  // A change of anchor also indicates a change of column headers.
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

        public int rows ()
        {
            return Math.max (0, ws.rows - ar);
        }

        public int columns ()
        {
            return Math.max (0, ws.columns - ac);
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
                if (ws.columnMap == null)  // No column headers, so use all rows. TODO: Need a better way to detect presence of column headers. This is unreliable in multiple ways.
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
            if (index > 0) return strings.get (index - 1);  // offset index back to zero-based

            // No string, so try returning number.
            double value = ws.numbers.get (row, column);
            if (value == 0) return "";
            return Scalar.print (value);
        }

        public Matrix getMatrix ()
        {
            if (ar == 0  &&  ac == 0) return ws.numbers;
            return ws.numbers.getRegion (ar, ac);
        }
    }

    public Holder open (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return null;  // absence of simulator indicates analysis phase, so opening files is unnecessary

        String fileName = ((Text) operands[0].eval (context)).value;
        String hdf      = evalKeyword (context, "hdf", "");

        String key = fileName;
        if (! hdf.isBlank ()) key += "|" + hdf;  // Because multiple holders can share same HDF file.

        Object H = simulator.holders.get (key);
        if (H == null)
        {
            Path filePath = simulator.jobDir.resolve (fileName);
            if (hdf.isBlank ()) H = new HolderSheet (filePath);
            else                H = new HolderHDF   (filePath, hdf);
            simulator.holders.put (key, H);
            return (Holder) H;
        }
        else if (H instanceof Holder)
        {
            return (Holder) H;
        }
        throw new AbortRun ("ERROR: Reopening file as a different resource type: " + key);
    }

    public Type eval (Instance context)
    {
        Holder H = open (context);
        if (H == null) return getType ();

        // H.parse() determines the results of other H functions below, so this must be a critical section.
        synchronized (H)
        {
            Operator anchor = getKeyword ("anchor");
            if (anchor != null) H.parse (anchor.eval (context).toString ());

            Operator info = getKeyword ("info");
            if (info != null)
            {
                switch (info.getString ())  // info must be a constant.
                {
                    case "columns":      return new Scalar (H.columns         ());
                    case "rows":         return new Scalar (H.rows            ());
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

            boolean isString = getKeywordFlag ("string");
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
                if (isString) return new Text ();
                return new Scalar (0);
            }
            else
            {
                if (isString) return new Text (H.getString ((int) row, (int) col));
                return new Scalar (H.getDouble (row, col));
            }
        }
    }

    public String toString ()
    {
        return "table";
    }
}
