/*
Copyright 2019-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.io.BufferedReader;
import java.io.FileInputStream;
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
import gov.sandia.n2a.eqset.EquationSet.NonzeroIterable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.function.Input.SubHolderHDF;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Matrix.IteratorNonzero;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.linear.MatrixSparse;
import gov.sandia.n2a.linear.MatrixSparse.IteratorSparse;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import gov.sandia.n2a.util.ParseXSV;
import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
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
        for (Operator op : operands) op.determineExponent (context);
        if (keywords != null)
        {
            for (Operator k : keywords.values ()) k.determineExponent (context);
        }

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

    public void determineExponentNext ()
    {
        for (Operator op : operands)
        {
            // If it is a string, let it do its own thing. If it is a number, force it to be integer.
            if (op.getType () instanceof Text) op.exponentNext = op.exponent;
            else                               op.exponentNext = 0;
            op.determineExponentNext ();
        }

        if (keywords != null)
        {
            // All keywords are strings, possibly calculated.
            for (Operator k : keywords.values ())
            {
                k.exponentNext = k.exponent;
                k.determineExponentNext ();
            }
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

        public int             rows ();
        public int             columns ();
        public int             getColumnIndex (String columnName);
        public int             getRowIndex (int columnIndex, Object columnValue);
        public double          getDouble (int row, int column);
        public String          getString (int row, int column);
        public IteratorNonzero getIteratorNonzero ();
    }

    /**
        Table-style access for HDF files.
        Handles several general cases as well as specialty formats (SONATA, possibly NWB).
        See also Input.HolderHDF. The access style is different enough that different classes are justified.
    **/
    public static class HolderHDF extends Matrix implements Holder, AutoCloseable
    {
        protected SubHolderHDF        sub;
        protected io.jhdf.api.Node    root;             // Can be either a Dataset or a Group.
        protected Group               sonataPopulation; // Population node, for finding related resources. null if not a SONATA file.
        protected boolean             sonataEdges;      // root is an attribute associated with a SONATA style sparse edge list.
        protected boolean             sonataSpikes;     // root is a group that contains SONATA style input spikes.
        protected int[]               dims;             // Size of data. Gets modified to always be 2D.
        protected int                 dimCount;         // Original length of "dims"
        protected Map<String,Integer> rowMap;
        protected Map<String,Integer> columnMap;
        protected List<String>        headers;          // The inverse of columnMap

        public static final int chunkSize = 1000000;

        /**
            @param fileName To the HDF file. Not the same as the key for looking Holder. Specifically, the
            holder key includes both HDF file path and path to resource inside HDF file. Here, we are only
            interested in the actual path to file, so we can keep track of how many holders are using the file.
            @param resource To the resource inside the HDF file.
        **/
        public HolderHDF (String fileName, String resource)
        {
            sub = SubHolderHDF.allocate (fileName);
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
                    case "edges":
                        sonataEdges = sonataPopulation.getChild ("source_node_id") != null  &&  sonataPopulation.getChild ("target_node_id") != null;
                        break;
                    case "spikes":
                        sonataSpikes = sonataPopulation.getChild ("node_ids") != null  &&  sonataPopulation.getChild ("timestamps") != null;
                        break;
                }
                if (! sonataEdges  &&  ! sonataSpikes) sonataPopulation = null;
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
            sub.release ();
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

        public double getDouble (int row, int column)
        {
            if (sonataEdges  ||  sonataSpikes) throw new AbortRun ("Should access SONATA edges or spikes through matrix()");

            if (row < 0  ||  row >= dims[0]  ||  column < 0  ||  column >= dims[1]) return 0;  // should be emptyValue
            long offset[] = new long[dimCount];
            int  count [] = new int [dimCount];
            offset[0] = row;
            count [0] = 1;
            if (dimCount > 1)
            {
                offset[1] = column;
                count [1] = 1;
            }

            Dataset columnData;
            if (root.isGroup ())
            {
                String columnName = headers.get (column);
                columnData = ((Group) root).getDatasetByPath (columnName);
            }
            else  // root is a Dataset
            {
                columnData = (Dataset) root;
            }

            Object result = columnData.getData (offset, count);
            Class<?> type = columnData.getJavaType ();
            if (type == double    .class) return ((double[])     result)[0];
            if (type == float     .class) return ((float[])      result)[0];
            if (type == int       .class) return ((int[])        result)[0];
            if (type == BigInteger.class) return ((BigInteger[]) result)[0].doubleValue ();
            throw new AbortRun ("Need code to handle numeric type: " + type.getSimpleName ());
        }

        public String getString (int row, int column)
        {
            if (sonataEdges  ||  sonataSpikes)  throw new AbortRun ("Should access SONATA edges or spikes through matrix()");

            if (row < 0  ||  row >= dims[0]  ||  column < 0  ||  column >= dims[1]) return "";
            long offset[] = new long[dimCount];
            int  count [] = new int [dimCount];
            offset[0] = row;
            count [0] = 1;
            if (dimCount > 1)
            {
                offset[1] = column;
                count [1] = 1;
            }

            Dataset columnData;
            if (root.isGroup ())
            {
                String columnName = headers.get (column);
                columnData = ((Group) root).getDatasetByPath (columnName);
            }
            else  // root is a Dataset
            {
                columnData = (Dataset) root;
            }

            Object result = columnData.getData (offset, count);
            Class<?> type = columnData.getJavaType ();
            if (type == String    .class) return ((String[]) result)[0];
            if (type == double    .class) return String.valueOf (((double[])     result)[0]);
            if (type == float     .class) return String.valueOf (((float [])     result)[0]);
            if (type == int       .class) return String.valueOf (((int[])        result)[0]);
            if (type == BigInteger.class) return                 ((BigInteger[]) result)[0].toString ();
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
            * SONATA "edges" list. "hdf" keyword points to the primary attribute being iterated.
            * SONATA "spikes" list. "hdf" keyword points to the Group that holds the spike list (usually named after the population).
            * Any 1D or 2D dataset.
            * Several parallel datasets under a group. Can either be SONATA attributes or any other data structured the same way.

            For sparse iteration, several Matrix objects coordinate with each other to take advantage of knowledge about current row in the SONATA data.
        **/
        public Matrix getMatrix ()
        {
            if (sonataEdges)  return new MatrixSonataEdgesHDF  (sub, sonataPopulation, root == sonataPopulation ? null : (Dataset) root);
            if (sonataSpikes) return new MatrixSonataSpikesHDF (sub, sonataPopulation);

            // * Any 1D or 2D dataset.
            // * Several parallel datasets under a group. Can either be SONATA attributes or any other data structured the same way.
            sub.allocate ();  // This Table.HolderHDF object will be closed upon return. Since we are returning ourselves as the matrix, we need to take out an additional allocation. The above SONATA matrices do an extra allocation in their ctor.
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
        protected SubHolderHDF         sub;  // So it can be disposed when done.
        protected long[]               offset = {-HolderHDF.chunkSize};  // For chunkAttribute. The iterator below has its own copy for the source and target node IDs.
        protected int[]                count  = { HolderHDF.chunkSize};

        // Data for matrix that backs the sparse iterator.
        protected Dataset              datasetSource;
        protected Dataset              datasetTarget;
        protected long                 rowCount;
        protected long                 row = -1;

        // Data for attribute matrix that tracks the iterator.
        protected MatrixSonataEdgesHDF track;       // Reference to main matrix that backs the iterator. Allows access to shared variables listed above, particularly row and rowCount.
        protected Dataset              datasetAttribute;
        protected double[]             chunkAttribute;
        protected Class<?>             type;
        protected double               emptyValue;  // Initially zero

        public MatrixSonataEdgesHDF (SubHolderHDF sub, Group population, Dataset attribute)
        {
            this.sub = sub;
            sub.allocate ();

            if (attribute == null)  // The boolean connectivity itself. It will be the basis for a sparse iterator.
            {
                datasetSource    = population.getDatasetByPath ("source_node_id");
                datasetTarget    = population.getDatasetByPath ("target_node_id");
                rowCount         = datasetTarget.getSize ();  // Should be same as datasetSource.size().
                count[0]         = (int) Math.min (rowCount, (long) HolderHDF.chunkSize);
            }
            else  // An attribute that tracks with the sparse iterator.
            {
                datasetAttribute = attribute;
                type             = attribute.getJavaType ();

                // Locate the main iterator.
                String populationPath = population.getPath ().substring (1);  // Skip leading slash in population resource path.
                if (populationPath.endsWith ("/")) populationPath = populationPath.substring (0, populationPath.length () - 1);
                String key = sub.fileName + "|" + populationPath;
                track = (MatrixSonataEdgesHDF) Simulator.instance.get ().holders.get (key);
                if (track == null) throw new AbortRun ("Attempt to create SONATA edge attribute matrix before sparse iterator is created.");

                count[0] = track.count[0];  // local copy
                chunkAttribute = new double[count[0]];
            }
        }

        public void close () throws Exception
        {
            sub.release ();
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
            if (datasetAttribute == null) return 1;  // If attribute is absent, we assume boolean matrix. In that case, always return 1, because this function should only be called for existent elements.

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
            if (track.row >= track.rowCount) return emptyValue;
            int rr = (int) (track.row - offset[0]);  // row relative to current block of data
            if (rr >= count[0])
            {
                // Out of data, so load another block.
                rr = 0;
                offset[0] = track.row;
                count[0] = Math.min (count[0], (int) (track.rowCount - track.row));  // Don't read past end of dataset.

                // Load chunkAttribute.
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

        public class IteratorEdge implements IteratorNonzero
        {
            protected BigInteger[] chunkSource;
            protected BigInteger[] chunkTarget;
            protected int          rr;  // row relative to start of chunk

            protected void getNext ()
            {
                ++row;
                if (row >= rowCount) return;
                rr = (int) (row - offset[0]);  // row relative to current block of data
                if (rr < count[0]) return;

                // Out of data, so load another block.
                rr = 0;
                offset[0] = row;
                count[0] = Math.min (count[0], (int) (rowCount - row));  // Don't read past end of dataset.

                chunkSource = (BigInteger[]) datasetSource.getData (offset, count);
                chunkTarget = (BigInteger[]) datasetTarget.getData (offset, count);
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
        protected SubHolderHDF sub;  // For releasing the HDF file when we are done.
        protected Dataset      datasetTime;
        protected long[]       columnIDs;
        protected long[]       columnPointers;
        protected int          rows;  // Tallest column seen.
        protected double       emptyValue = Double.POSITIVE_INFINITY;

        public MatrixSonataSpikesHDF (SubHolderHDF sub, Group population)
        {
            this.sub = sub;
            sub.allocate ();
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
            sub.release ();
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
        public    Map<String,Sheet> wb      = new HashMap<String,Sheet> (); // workbook, a collection of worksheets
        protected Sheet             first;                                  // The first sheet defined in the file. This is the default when no sheet is specified in cell address.
        protected String            anchor;                                 // The most recently parsed anchor cell address. Includes sheet name and coordinates.
        protected Sheet             ws;                                     // anchor sheet
        public    int               ar;                                     // anchor row
        public    int               ac;                                     // anchor column

        public HolderSheet (String fileName)
        {
            final double fillThreshold = 0.5;
            Path filePath = Simulator.instance.get ().jobDir.resolve (fileName);

            // File-type triage -- If it's zip, then process as Excel spreadsheet. All others are treated as XSV.
            boolean isZip = false;
            try (FileInputStream fis = new FileInputStream (filePath.toFile ()))
            {
                byte magic[] = new byte[4];
                fis.read (magic);
                isZip =  magic[0] == 'P'  &&  magic[1] == 'K'  &&  magic[2] == 3  &&  magic[3] == 4;
            }
            catch (Exception e)
            {
                PrintStream err = Backend.err.get ();
                err.println ("ERROR: Can't open table file: " + filePath);
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
                            try
                            {
                                ws.numbers.set (ws.rows, c, Scalar.parseDouble (temp));
                                fillN++;
                            }
                            catch (NumberFormatException e)
                            {
                                strings.add (temp);
                                int stringIndex = strings.size ();
                                ws.strings.set (ws.rows, c, stringIndex);
                                fillS++;
                            }
                        }
                        ws.rows++;
                        ws.columns = Math.max (ws.columns, count);
                        return true;
                    }
                }
                ProcessXSV process = new ProcessXSV ();
                try (BufferedReader reader = Files.newBufferedReader (filePath))
                {
                    process.parse (reader);
                }
                catch (Exception e)
                {
                    PrintStream err = Backend.err.get ();
                    err.println ("ERROR: Failed to parse CSV file: " + filePath);
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
            try (ZipFile archive = new ZipFile (filePath.toFile ()))
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
                // See https://www.brendanlong.com/the-minimum-viable-xlsx-reader.html
                // At a minimum, we accept all pre-defined date styles: 14-22, 45-47
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
                                    // It appears that MS Excel won't store negative date numbers. Instead, the value is stored as a string.
                                    // The difficulty is identifying a date cell. The only way is to check style (attribute "s").
                                    v = XMLutility.getChild (c, "v");
                                    if (v == null) continue;  // Sometimes a cell exists in the XML file but has no value.
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
                err.println ("ERROR: Failed to parse spreadsheet file: " + filePath);
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

        /**
            Support legacy code that uses HolderSheet as a utility for accessing spreadsheets.
            New code should set anchor separately.
        **/
        public synchronized int getRowsInColumn (String anchor)
        {
            parse (anchor);
            return getRowsInColumn ();
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
            Support legacy code that uses HolderSheet as a utility for accessing spreadsheets.
            New code should set anchor separately.
        **/
        public synchronized int getColumnsInRow (String anchor)
        {
            parse (anchor);
            return getColumnsInRow ();
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
            Looks up the row associated with the given keyValue.
            Builds an index as needed. The current version assumes that only one key column is ever used,
            such that once the index is built, it never needs to be rebuilt for a different column.
            @todo Cache a separate index for each column that is requested.
            @param keyColumn Must be valid, in [0, columns). The caller can use getColumnIndex()
            to determine this value. If it is -1 (column not found), then don't call this function.
            @return Zero-based index if found. -1 if not found.
        **/
        public int getRowIndex (int keyColumn, Object keyValue)
        {
            if (ws.index == null)
            {
                if (ws.columnMap == null)  // No column headers, so use row 0 as well as the others. TODO: Need a better way to detect presence of column headers. This is unreliable in multiple ways.
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
                    int i1 = (int) ws.strings.get (t1, keyColumn);
                    int i2 = (int) ws.strings.get (t2, keyColumn);
                    if (i1 == 0)  // t1 is a number
                    {
                        if (i2 == 0) return (int) Math.signum (ws.numbers.get (t1, keyColumn) - ws.numbers.get (t2, keyColumn));  // t2 is a number
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
                // t1 and t2 are row numbers for the table, not positions in the index.
                // -1 is a pseudo-row that contains the keyValue.
                Object o1;
                if (t1 < 0)
                {
                    o1 = keyValue;
                }
                else
                {
                    int i = (int) ws.strings.get (t1, keyColumn);
                    o1 =  i == 0 ? ws.numbers.get (t1, keyColumn) : strings.get (i - 1);
                }

                Object o2;
                if (t2 < 0)
                {
                    o2 = keyValue;
                }
                else
                {
                    int i = (int) ws.strings.get (t2, keyColumn);
                    o2 =  i == 0 ? ws.numbers.get (t2, keyColumn) : strings.get (i - 1);
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

        public double getDouble (int row, int column)
        {
            int r = ar + row;
            int c = ac + column;
            Matrix A = ws.numbers;
            if (! (A instanceof MatrixSparse)  &&  (r < 0  ||  r >= A.rows ()  ||  c < 0  ||  c >= A.columns ())) return 0;
            return A.get (r, c);
        }

        /**
            Support legacy code that uses HolderSheet as a utility for accessing spreadsheets.
            New code should set anchor separately.
        **/
        public synchronized double getDouble (String anchor, int row, int column)
        {
            parse (anchor);
            return getDouble (row, column);
        }

        public String getString (int row, int column)
        {
            int r = ar + row;
            int c = ac + column;
            Matrix A = ws.strings;
            if (! (A instanceof MatrixSparse)  &&  (r < 0  ||  r >= A.rows ()  ||  c < 0  ||  c >= A.columns ())) return "";
            int index = (int) A.get (r, c);
            if (index > 0) return strings.get (index - 1);  // offset index back to zero-based

            // No string, so try returning number.
            double value = getDouble (row, column);
            if (value == 0) return "";
            return Scalar.print (value);
        }

        /**
            Support legacy code that uses HolderSheet as a utility for accessing spreadsheets.
            New code should set anchor separately.
        **/
        public synchronized String getString (String anchor, int row, int column)
        {
            parse (anchor);
            return getString (row, column);
        }

        public Matrix getMatrix ()
        {
            if (ar == 0  &&  ac == 0) return ws.numbers;
            return ws.numbers.getRegion (ar, ac);
        }

        public IteratorNonzero getIteratorNonzero ()
        {
            Matrix A = ws.numbers;
            if (A instanceof MatrixSparse) return new IteratorSparse ((MatrixSparse) A, ar, ac);
            return ((MatrixDense) A).getRegion (ar, ac).getIteratorNonzero ();
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
            if (hdf.isBlank ()) H = new HolderSheet (fileName);
            else                H = new HolderHDF   (fileName, hdf);
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
                if (isString) return new Text   (H.getString ((int) row, (int) col));
                return               new Scalar (H.getDouble ((int) row, (int) col));
            }
        }
    }

    public String toString ()
    {
        return "table";
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
        // Could also check if op1 and op2 are numeric expressions, but not worth the effort.
        return true;
    }

    public IteratorNonzero getIteratorNonzero (Instance context)
    {
        Holder H = open (context);
        if (H == null) return null;

        if (H instanceof HolderSheet)
        {
            HolderSheet HS = (HolderSheet) H;

            String anchor = "";
            Operator kwAnchor = getKeyword ("anchor");
            if (kwAnchor != null) anchor = ((Text) kwAnchor.eval (context)).toString ();
            synchronized (HS)
            {
                HS.parse (anchor);
                return HS.getIteratorNonzero ();
            }
        }

        return H.getIteratorNonzero ();
    }
}
