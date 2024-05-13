/*
Copyright 2022-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPartRepo;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Text;
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

    public void determineExponent (ExponentContext context)
    {
        // Let any numeric keys determine their exponent.
        for (int i = 1; i < operands.length; i++)
        {
            operands[i].determineExponent (context);
        }

        // Set exponent based on hint. Suitable for numerics outputs. Does no harm for string outputs.
        int centerNew   = MSB / 2;
        int exponentNew = getExponentHint (0) - centerNew;
        updateExponent (context, exponentNew, centerNew);
    }

    public void determineExponentNext ()
    {
        exponent = exponentNext;  // If we output number, we will convert strings using the desired exponent, so need for shift.

        // All numeric keys must be delivered as integers.
        // Skip operands[0] because it is always a string.
        for (int i = 1; i < operands.length; i++)
        {
            Operator op = operands[i];
            op.exponentNext = 0;
            op.determineExponentNext ();
        }
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        unit = AbstractUnit.ONE;
    }

    public String getDelimiter ()
    {
        Operator op = getKeyword ("delimiter");
        if (op == null) return ".";
        return op.getString ();
    }

    public static class Holder
    {
        protected MNode                    doc;
        protected Map<String,Matrix>       matrices  = new HashMap<String,Matrix> ();
        protected Map<String,List<String>> childKeys = new HashMap<String,List<String>> ();

        public static Holder get (Simulator simulator, String path, Instance context, Mfile mf)
        {
            Holder result;
            Object o = simulator.holders.get (path);
            if (o == null)
            {
                result = new Holder ();
                result.doc = new MDoc (simulator.jobDir.resolve (path));
                Type inherit = mf.evalKeyword (context, "inherit");
                if (inherit instanceof Text)
                {
                    result.doc = new MPartRepo (result.doc, ((Text) inherit).value);
                }
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

        public Matrix getMatrix (Instance context, Mfile mf)
        {
            String[] keyPath = keyPath (context, mf, 1);
            String key = String.join (mf.getDelimiter (), keyPath);
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

        public List<String> getChildKeys (Instance context, Mfile mf)
        {
            String[] keyPath = keyPath (context, mf, 2);
            String key = String.join (mf.getDelimiter (), keyPath);
            List<String> result = childKeys.get (key);
            if (result != null) return result;

            MNode c = doc.childOrEmpty (keyPath);
            result = c.childKeys ();
            childKeys.put (key, result);
            return result;
        }

        public static String[] keyPath (Instance context, Mfile m, int start)
        {
            String delimiter = Pattern.quote (m.getDelimiter ());
            List<String> keys = new ArrayList<String> ();
            for (int i = start; i < m.operands.length; i++)
            {
                String key = m.operands[i].eval (context).toString ();
                String[] pieces = key.split (delimiter);
                for (String p : pieces) if (! p.isEmpty ()) keys.add (p);
            }
            return keys.toArray (new String[keys.size ()]);
        }
    }
}
