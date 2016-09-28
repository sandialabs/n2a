/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.function.Input.Holder;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class Output extends Function
{
    String variableName;  // Trace needs to know its target variable in order to auto-generate a column name. This value is set by an analysis process.

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "output";
            }

            public Operator createInstance ()
            {
                return new Output ();
            }
        };
    }

    public boolean isOutput ()
    {
        return true;
    }

    public static class Holder
    {
        public Map<String,Integer> columnMap       = new HashMap<String,Integer> ();  ///< Maps from column name to column position.
        public List<Float>         columnValues    = new ArrayList<Float> ();         ///< Holds current value for each column.
        public int                 columnsPrevious = 0;                               ///< Number of columns written in previous cycle.
        public boolean             traceReceived   = false;                           ///< Indicates that at least one column was touched during the current cycle.
        public PrintStream         out;
        public Simulator           simulator;  ///< So we can get current time

        public void trace (String column, float value)
        {
            if (columnValues.isEmpty ())  // slip $t into first column 
            {
                columnMap.put ("$t", 0);
                columnValues.add ((float) simulator.currentEvent.t);
            }

            Integer index = columnMap.get (column);
            if (index == null)
            {
                columnMap.put (column, columnValues.size ());
                columnValues.add (value);
            }
            else
            {
                columnValues.set (index, value);
            }

            traceReceived = true;
        }

        public void writeTrace ()
        {
            if (! traceReceived) return;  // Don't output anything unless at least one value was set.

            int count = columnValues.size ();
            int last  = count - 1;

            // Write headers if new columns have been added
            if (count > columnsPrevious)
            {
                String headers[] = new String[count];
                for (Entry<String,Integer> i : columnMap.entrySet ())
                {
                    headers[i.getValue ()] = i.getKey ();
                }
                out.print (headers[0]);  // Should be $t
                int i = 1;
                for (; i < columnsPrevious; i++)
                {
                    out.print ("\t");
                }
                for (; i < count; i++)
                {
                    out.print ("\t");
                    out.print (headers[i]);
                }
                out.println ();
                columnsPrevious = count;
            }

            // Write values
            if (count > 0)
            {
                // $t is guaranteed to be column 0, and furthermore, we are still within the current event that generated these column values
                columnValues.set (0, (float) simulator.currentEvent.t);

                for (int i = 0; i <= last; i++)
                {
                    Float c = columnValues.get (i);
                    if (! c.isNaN ()) out.print (c);
                    if (i < last) out.print ("\t");
                    columnValues.set (i, Float.NaN);
                }
                out.println ();
            }

            traceReceived = false;
        }
    }

    public Type eval (Instance context)
    {
        int columnParameter = 1;
        Type result = operands[0].eval (context);  // This will be either the expression itself, or the destination file, depending on form.
        String path = "";
        if (result instanceof Text)
        {
            path = ((Text) result).value;
            result = operands[1].eval (context);  // If the first operand is a string (pathname) then the second operand must evaluate to a number.
            columnParameter = 2;
        }

        Simulator simulator = Simulator.getSimulator (context);
        if (simulator != null)
        {
            Holder H = simulator.outputs.get (path);
            if (H == null)
            {
                H = new Holder ();
                H.simulator = simulator;
                if (path.isEmpty ())
                {
                    H.out = simulator.out;
                }
                else
                {
                    try
                    {
                        H.out = new PrintStream (new File (path).getAbsoluteFile ());
                    }
                    catch (FileNotFoundException e)
                    {
                        H.out = simulator.out;
                    }
                }

                simulator.outputs.put (path, H);
            }

            String column;
            if (operands.length > columnParameter)  // column name is specified
            {
                column = operands[columnParameter].eval (context).toString ();  // evaluate every time, because it could change
            }
            else  // auto-generate column name
            {
                String prefix = context.path ();
                if (prefix.isEmpty ()) column =                variableName;
                else                   column = prefix + "." + variableName;
            }
            H.trace (column, (float) ((Scalar) result).value);
        }

        return result;
    }

    // This method should be called by analysis, with v set to the variable that holds this equation.
    public void determineVariableName (Variable v)
    {
        if (operands[0] instanceof AccessVariable)
        {
            variableName = ((AccessVariable) operands[0]).name;  // the raw name, including prime marks for derivatives
        }
        else
        {
            variableName = v.name;
        }

        if (operands.length < 2)  // We need $index to auto-generate names.
        {
            EquationSet container = v.container;
            if (container.connectionBindings == null)  // regular part
            {
                dependOnIndex (v, container);
            }
            else  // connection
            {
                // depend on all endpoints
                for (Entry<String,EquationSet> e : container.connectionBindings.entrySet ())
                {
                    dependOnIndex (v, e.getValue ());
                }
            }
        }
    }

    public void dependOnIndex (Variable v, EquationSet container)
    {
        while (container != null)
        {
            Variable index = container.find (new Variable ("$index"));
            if (index != null  &&  ! container.isSingleton ())
            {
                v.addDependency (index);
            }
            container = container.container;
        }
    }

    public String toString ()
    {
        return "output";
    }
}
