/*
Copyright 2013-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
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
        String hdf      = evalKeyword (context, "hdf", "");
        String npy      = evalKeyword (context, "npy", "");
        String csr      = evalKeyword (context, "csr", "");

        boolean isHDF = ! hdf.isBlank ();  // User asserts that this is an HDF file, so we take their word for it.
        boolean isNPY = ! npy.isBlank ();
        boolean isCSR = ! csr.isBlank ();

        String key = fileName;
        if      (isHDF) key += "|" + hdf;  // Because multiple holders can share same HDF file.
        else if (isNPY) key += "|" + npy;
        else if (isCSR) key += "|" + csr;

        Object A = simulator.holders.get (key);
        if (A == null)
        {
            Path path = simulator.jobDir.resolve (fileName);
            String lowerFN = fileName.toLowerCase ();

            // For keyword tests (hdf, anchor, npy, csr) we assume that the keyword is only present if the file is really that type.
            if (isHDF)
            {
                try (Table.HolderHDF H = new Table.HolderHDF (path, hdf))
                {
                    A = H.getMatrix ();
                    // H gets closed at the end of this block, but A is also a holder and AutoCloseable.
                    // When holders are closed, the HDF resources will finally be released.
                }
                catch (Exception e) {}
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
                catch (IOException e) {}
            }
            else  // Everything else: spreadsheet, CSV, or plain-text
            {
                // TODO: handle SONATA spike files in CSV format. Simplest approach is to do it in Matrix.factory(), but need a way to avoid repeating the XSV file parser.
                Operator anchor = getKeyword ("anchor");
                boolean isSheet = anchor != null  ||  lowerFN.endsWith (".csv");
                if (! isSheet)  // Probe file
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
                    if (anchor != null) H.parse (anchor.eval (context).toString ());
                    A = H.getMatrix ();
                }

                if (A == null) A = Matrix.factory (path);  // Simple matrix file.
            }

            // Done reading. Now what did we get?
            if (A == null)
            {
                if (! key.equals (warningIO))
                {
                    Backend.err.get ().println ("WARNING: IO error on matrix(" + key + ")");
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
            Backend.err.get ().println ("ERROR: Reopening file as a different resource type.");
            throw new Backend.AbortRun ();
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
}
