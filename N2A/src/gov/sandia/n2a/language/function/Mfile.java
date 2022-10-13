/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.linear.MatrixSparse;
import gov.sandia.n2a.plugins.extpoints.Backend;
import tech.units.indriya.AbstractUnit;

public class Mfile extends Function
{
    public String name;     // For C backend, the name of the Mfile holder.
    public String fileName; // For C backend, the name of the string variable holding the file name, if any.

    public boolean canBeConstant ()
    {
        return false;
    }

    public boolean canBeInitOnly ()
    {
        return true;
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        unit = AbstractUnit.ONE;
    }

    public static class Holder
    {
        protected MNode              doc;
        protected Map<String,Matrix> matrices = new HashMap<String,Matrix> ();

        public static Holder get (Simulator simulator, String path)
        {
            Holder result;
            Object o = simulator.holders.get (path);
            if (o == null)
            {
                result = new Holder ();
                result.doc = new MDoc (simulator.jobDir.resolve (path));
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

        public Matrix getMatrix (String... keyPath)
        {
            String key = String.join ("/", keyPath);
            Matrix A = matrices.get (key);
            if (A != null) return A;

            MatrixSparse S = new MatrixSparse ();
            MNode m = doc.childOrEmpty (keyPath);
            for (MNode row : m)
            {
                int r = Integer.valueOf (row.key ());
                for (MNode col : row)
                {
                    int c = Integer.valueOf (col.key ());
                    S.set (r, c, col.getDouble ());
                }
            }
            matrices.put (key, S);
            return S;
        }

        public static String[] keyPath (Instance context, Operator[] operands)
        {
            List<String> keys = new ArrayList<String> ();
            for (int i = 1; i < operands.length; i++)
            {
                String key = operands[i].eval (context).toString ();
                String[] pieces = key.split ("/");
                for (String p : pieces) if (! p.isEmpty ()) keys.add (p);
            }
            return keys.toArray (new String[keys.size ()]);
        }
    }
}
