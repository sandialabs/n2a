/*
Copyright 2013-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.linear.MatrixSparse;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import gov.sandia.n2a.util.ParseXSV;
import tech.units.indriya.AbstractUnit;

public class ReadMatrix extends Function
{
    public    String name;     // For C backend, the name of the MatrixInput object.
    public    String fileName; // For C backend, the name of the string variable holding the file name, if any.
    protected String warningIO;

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "matrix";
            }

            public Operator createInstance ()
            {
                return new ReadMatrix ();
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

    public boolean isMatrixInput ()
    {
        return true;
    }

    public void determineExponent (ExponentContext context)
    {
        int centerNew   = MSB / 2;
        int exponentNew = getExponentHint (0) - centerNew;
        updateExponent (context, exponentNew, centerNew);
    }

    public void determineExponentNext ()
    {
        exponent = exponentNext;  // Conversion done while reading.
        // All our operands are strings, so no point in passing the exponent down.
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        unit = AbstractUnit.ONE;
    }

    public Matrix open (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return null;  // absence of simulator indicates analysis phase, so opening files is unnecessary

        String fileName = ((Text) operands[0].eval (context)).value;
        String hdf      = evalKeyword (context, "hdf",          "");
        String npy      = evalKeyword (context, "npy",          "");
        String csr      = evalKeyword (context, "csr",          "");
        String spikes   = evalKeyword (context, "sonataSpikes", "");

        boolean isHDF    = ! hdf   .isBlank ();  // User asserts that this is an HDF file, so we take their word for it.
        boolean isNPY    = ! npy   .isBlank ();
        boolean isCSR    = ! csr   .isBlank ();
        boolean isSpikes = ! spikes.isBlank ();

        // Evaluate SONATA flag differently than the keys above.
        // It is allowed to be blank and still activate SONATA behavior.
        Operator sonataOp = getKeyword ("sonataEdges");
        boolean  isEdges  = sonataOp != null;
        String   edges    = isEdges ? sonataOp.eval (context).toString () : "";

        String key = fileName;
        if      (isHDF)    key += "|" + hdf;  // Because multiple holders can share same HDF file.
        else if (isNPY)    key += "|" + npy;
        else if (isCSR)    key += "|" + csr;
        else if (isSpikes) key += "|" + spikes;
        else if (isEdges)  key += "|" + edges;  // sonataEdges can be the empty string. This still works.

        Object A = simulator.holders.get (key);
        if (A == null)
        {
            Path path = simulator.jobDir.resolve (fileName);
            String lowerFN = fileName.toLowerCase ();
            Exception trapped = null;

            // For keyword tests (hdf, anchor, npy, csr) we assume that the keyword is only present if the file is really that type.
            if (isHDF)
            {
                try (Table.HolderHDF H = new Table.HolderHDF (path, hdf))
                {
                    A = H.getMatrix ();
                    // H gets closed at the end of this block, but A is also a holder and AutoCloseable.
                    // When holders are closed, the HDF resources will finally be released.
                }
                catch (Exception e) {trapped = e;}
            }
            else if (isNPY  ||  isCSR  ||  lowerFN.endsWith (".npz"))
            {
                try (FileSystem fs = FileSystems.newFileSystem (path, Collections.emptyMap ()))
                {
                    if (isCSR)  // Complex format, requiring input of multiple tables.
                    {
                        A = new MatrixSparse (fs, csr);
                    }
                    else if (isNPY)
                    {
                        A = new MatrixDense (fs, npy);
                    }
                    else
                    {
                        A = new MatrixDense (fs);
                    }
                }
                catch (IOException e) {trapped = e;}
            }
            else if (isEdges)  // N2A-generated SONATA edge instance file, in CSV
            {
                try
                {
                    A = new MatrixSonataEdgesXSV (fileName, path, edges);
                }
                catch (IOException e) {trapped = e;}
            }
            else if (isSpikes)  // SONATA spike file in CSV. (HDF case is handled above, using special case in Table.HolderHDF.)
            {
                // Convert CSV data into sparse spike matrix.
                class ReadSpikes extends ParseXSV
                {
                    MatrixSparse S = new MatrixSparse ();

                    int     colTime       = -1;
                    int     colPopulation = -1;
                    int     colID         = -1;
                    boolean gotColumns    = false;
                    int     lastID        = -1;
                    int     eventCount    = 0;

                    public boolean processLine (List<String> parts)
                    {
                        if (! gotColumns)
                        {
                            colTime       = parts.indexOf ("timestamps");
                            colPopulation = parts.indexOf ("population");
                            colID         = parts.indexOf ("node_ids");
                            gotColumns =  colTime >= 0  &&  colPopulation >= 0  &&  colID >= 0;
                            if (! gotColumns) return false;
                        }
                        //   Extract column data and store in matrix.
                        if (! parts.get (colPopulation).equals (spikes)) return true;
                        double time = Double .valueOf (parts.get (colTime));
                        int    ID   = Integer.valueOf (parts.get (colID));  // Should be long, but MatrixSparse doesn't currently support that.
                        if (ID != lastID)
                        {
                            // We assume that IDs are contiguous in the file, and that timestamps increase monotonically.
                            lastID = ID;
                            eventCount = 0;
                        }
                        S.set (eventCount++, ID, time);
                        return true;
                    }
                }
                try (BufferedReader reader = Files.newBufferedReader (path))
                {
                    ReadSpikes rs = new ReadSpikes ();
                    rs.parse (reader);
                    if (rs.gotColumns) A = rs.S;
                }
                catch (Exception e) {trapped = e;}
            }
            else  // Everything else: spreadsheet, CSV, or plain-text
            {
                boolean isCSV   = lowerFN.endsWith (".csv");  // Unfortunately, there's no better way to check for CSV. The files don't have any magic string in them.
                Operator anchor = getKeyword ("anchor");
                boolean isSheet = isCSV  ||  anchor != null;

                if (! isSheet)  // Probe file for Excel format.
                {
                    try (BufferedReader reader = Files.newBufferedReader (path))
                    {
                        char magic[] = new char[4];
                        reader.read (magic);
                        isSheet =  magic[0] == 'P'  &&  magic[1] == 'K'  &&  magic[2] == 3  &&  magic[3] == 4;
                    }
                    catch (Exception e) {}
                }

                if (isSheet)
                {
                    Table.HolderSheet H = new Table.HolderSheet (path);
                    synchronized (H)
                    {
                        if (anchor != null) H.parse (anchor.eval (context).toString ());
                        A = H.getMatrix ();
                    }
                }

                if (A == null) A = Matrix.factory (path);  // Simple matrix file.
            }

            // Done reading. Now what did we get?
            if (A == null)
            {
                if (! key.equals (warningIO))  // This filter allows a new message each time a dynamic file name changes.
                {
                    PrintStream ps = Backend.err.get ();
                    ps.println ("WARNING: IO error on matrix(" + key + ")");
                    if (trapped != null) ps.println ("  " + trapped.getMessage ());
                    warningIO = key;
                }
            }
            else
            {
                ((Matrix) A).setEmptyValue (evalKeyword (context, "empty", 0.0));
                simulator.holders.put (key, A);
            }
        }
        else if (! (A instanceof Matrix))
        {
            throw new AbortRun ("ERROR: Reopening file as a different resource type: " + fileName);
        }
        return (Matrix) A;
    }

    public Type getType ()
    {
        return new MatrixDense ();
    }

    public Type eval (Instance context)
    {
        Matrix A = open (context);
        if (A == null) return new MatrixDense ();  // C backend depends on this being a zero-dimensional matrix.
        return A;
    }

    public String toString ()
    {
        return "matrix";
    }

    /**
        Special sparse matrix for SONATA edge lists, backed by XSV data.
        See comments on class Τable.MatrixSonataEdgesHDF.
    **/
    public static class MatrixSonataEdgesXSV extends Matrix implements AutoCloseable
    {
        protected String          key;
        protected Input.HolderXSV holder;
        protected String          attribute;
        protected boolean         haveColumns;
        protected int             colAttribute;
        protected int             colSource;
        protected int             colTarget;
        protected double          emptyValue = 0;

        public MatrixSonataEdgesXSV (String key, Path path, String attribute) throws IOException
        {
            this.key       = key;
            this.attribute = attribute;

            Simulator simulator = Simulator.instance.get ();
            Object Η = simulator.holders.get (key);
            if (Η == null)
            {
                holder = new Input.HolderXSV (simulator, path.toString (), false);
                simulator.holders.put (key, holder);
            }
            else if (Η instanceof Input.HolderXSV)
            {
                holder = (Input.HolderXSV) Η;
            }
            else
            {
                throw new AbortRun ("matrix ERROR: Reopening file as a different resource type: " + key);
            }
       }

        public void close () throws Exception
        {
            holder = null;  // The base holder will get closed separately during simulator shutdown.
        }

        public int rows ()
        {
            throw new AbortRun ("MatrixSonataEdgesXSV does not support rows()");
        }

        public int columns ()
        {
            throw new AbortRun ("MatrixSonataEdgesXSV does not support columns()");
        }

        /**
            Return attribute associated with the current iterator position.
        **/
        public double get (int row, int column)
        {
            if (attribute == "") return 1;  // If attribute is absent, we assume boolean matrix. In that case, always return 1, because this function should only be called for existent elements.
            if (row > holder.currentLine  &&  Double.isNaN (holder.nextLine)) return emptyValue;
            if (colAttribute < 0) return emptyValue;
            return holder.currentValues[colAttribute];
        }

        public void set (int row, int column, double a)
        {
            throw new AbortRun ("MatrixSonataEdgesXSV does not support set()");
        }

        public class IteratorEdge implements IteratorNonzero
        {
            int row;

            public boolean hasNext ()
            {
                // The first clause below checks whether we have read any rows yet.
                // The second clause checks if there is any future data.
                return  holder.currentLine < 0  ||  ! Double.isNaN (holder.nextLine);
            }

            public Double next ()
            {
                try
                {
                    holder.getRow (row);
                }
                catch (IOException e)
                {
                    return null;
                }
                if (row > holder.currentLine) return null;
                row++;

                if (! haveColumns)
                {
                    colAttribute = holder.columnMap.get (attribute);
                    colSource    = holder.columnMap.get ("source_node_id");
                    colTarget    = holder.columnMap.get ("target_node_id");
                    if (! attribute.isBlank ()  &&  colAttribute < 0  ||  colSource < 0  ||  colTarget < 0)
                    {
                        PrintStream ps = Backend.err.get ();
                        ps.println ("ERROR: required columns are missing from edges file");  // TODO: save the file name, just for this error message?
                    }
                    haveColumns = true;
                }

                if (colAttribute < 0) return 1.0; // Since we iterate only existing elements, always return true.
                return holder.currentValues[colAttribute];
            }

            public int getRow ()
            {
                return (int) holder.currentValues[colSource];
            }

            public int getColumn ()
            {
                return (int) holder.currentValues[colTarget];
            }
        }
    }
}
