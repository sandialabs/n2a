/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class Input extends Function
{
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

    public static class Holder
    {
        public int            lineCount = -1;
        public double[]       values    = {0};
        public BufferedReader stream;
    }

    public Holder getRow (Instance context)
    {
        Simulator simulator = Simulator.getSimulator (context);
        if (simulator == null) return null;  // If we can't cache a line from the requested stream, then semantics of this function are lost, so give up.

        Holder H = null;
        try
        {
            // get an input holder
            String path = ((Text) operands[0].eval (context)).value;
            H = simulator.inputs.get (path);
            if (H == null)
            {
                H = new Holder ();
                if (path.isEmpty ()) H.stream = new BufferedReader (new InputStreamReader (System.in));  // not ideal; reading stdin should be reserved for headless operation
                else                 H.stream = new BufferedReader (new FileReader (new File (path).getAbsoluteFile ()));
                simulator.inputs.put (path, H);
            }

            // if needed, read next line
            int lineCount = Math.max (0, (int) Math.floor (((Scalar) operands[1].eval (context)).value));
            while (H.lineCount < lineCount)
            {
                String line = H.stream.readLine ();
                if (line == null) break;  // use previously set H.values
                H.lineCount++;
                if (H.lineCount == lineCount)
                {
                    String[] columns = line.split ("[ \\t]");
                    H.values = new double[columns.length];
                    for (int i = 0; i < columns.length; i++) H.values[i] = Double.parseDouble (columns[i]);
                }
            }
        }
        catch (IOException e)
        {
            return null;
        }

        return H;
    }

    public Type eval (Instance context)
    {
        Holder H = getRow (context);
        if (H == null) return new Scalar (0);

        int    columns    = H.values.length;
        int    lastColumn = columns - 1;
        double column     = ((Scalar) operands[2].eval (context)).value * lastColumn;
        int    c          = (int) Math.floor (column);
        if (c <  0         ) return new Scalar (H.values[0         ]);
        if (c >= lastColumn) return new Scalar (H.values[lastColumn]);
        double b = column - c;
        return new Scalar ((1 - b) * H.values[c] + b * H.values[c+1]);
    }

    public String toString ()
    {
        return "input";
    }
}
