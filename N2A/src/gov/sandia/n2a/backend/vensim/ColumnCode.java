/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.vensim;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class ColumnCode extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "columnCode";
            }

            public Operator createInstance ()
            {
                return new ColumnCode ();
            }
        };
    }

    public Type getType ()
    {
        if (operands[0].getType () instanceof Text) return new Scalar ();
        return new Text ();
    }

    public Type eval (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return getType ();  // absence of simulator indicates analysis phase, so opening files is unnecessary

        Type input = operands[0].eval (context);
        if (input instanceof Text) return new Scalar (valueOf (input.toString ()));
        return new Text (valueOf ((int) ((Scalar) input).value));
    }

    /**
        Convert column letter code to zero-based index.
    **/
    public static int valueOf (String columnName)
    {
        columnName = columnName.toUpperCase ();
        int result = 0;
        int pos = 0;
        int length = columnName.length ();
        for (; pos < length; pos++)
        {
            char c = columnName.charAt (pos);
            if (c < 'A') break;
            result = result * 26 + c - 'A' + 1;
        }
        result--;
        return result;
    }

    /**
        Convert column index (zero-based) to letter code.
    **/
    public static String valueOf (int columnIndex)
    {
        String result = "";
        while (true)
        {
            char c = (char) (columnIndex % 26);
            c += 'A';
            result = Character.toString (c) + result;
            if (columnIndex < 26) break;
            columnIndex = columnIndex / 26 - 1;
        }
        return result;
    }

    public String toString ()
    {
        return "columnCode";
    }
}
