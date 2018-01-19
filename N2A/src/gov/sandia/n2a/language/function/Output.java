/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import gov.sandia.n2a.backend.internal.InstanceTemporaries;
import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class Output extends Function
{
    public String variableName;  // Trace needs to know its target variable in order to auto-generate a column name. This value is set by an analysis process.
    public String variableName0; // As found in operand[0]
    public String variableName1; // As found in operand[1]
    public int    index;  // of generated column name in valuesObject array

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

    public boolean canBeConstant ()
    {
        return false;
    }

    public static class Holder
    {
        public Map<String,Integer> columnMap    = new HashMap<String,Integer> ();  ///< Maps from column name to column position.
        public List<Float>         columnValues = new ArrayList<Float> ();         ///< Holds current value for each column.
        public int                 columnsPrevious;                                ///< Number of columns written in previous cycle.
        public boolean             traceReceived;                                  ///< Indicates that at least one column was touched during the current cycle.
        public double              t;
        public PrintStream         out;
        public Simulator           simulator;  ///< So we can get time associated with each trace() call.
        public boolean             raw;  ///< Indicates that column is an exact index.

        public void trace (String column, float value)
        {
            // Detect when time changes and dump any previously traced values.
            double now;
            if (simulator.currentEvent == null) now = 0;
            else                                now = (float) simulator.currentEvent.t;
            if (now > t)
            {
                writeTrace ();
                t = now;
            }

            if (! traceReceived)  // First trace for this cycle
            {
                if (columnValues.isEmpty ())  // slip $t into first column 
                {
                    columnMap.put ("$t", 0);
                    columnValues.add ((float) t);
                }
                else
                {
                    columnValues.set (0, (float) t);
                }
            }

            Integer index = columnMap.get (column);
            if (index == null)
            {
                if (raw)
                {
                    int i = Integer.valueOf (column) + 1;  // offset for time in first column
                    while (columnValues.size () < i) columnValues.add (Float.NaN);
                    columnMap.put (column, i);
                }
                else
                {
                    columnMap.put (column, columnValues.size ());
                }
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
            if (! raw  &&  count > columnsPrevious)
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
            for (int i = 0; i <= last; i++)
            {
                Float c = columnValues.get (i);
                if (! c.isNaN ()) out.print (c);
                if (i < last) out.print ("\t");
                columnValues.set (i, Float.NaN);
            }
            out.println ();

            traceReceived = false;
        }
    }

    public Type eval (Instance context)
    {
        Type result = operands[1].eval (context);
        Simulator simulator = Simulator.getSimulator (context);
        if (simulator == null) return result;

        String path = ((Text) operands[0].eval (context)).value;
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

            if (operands.length > 3) H.raw = operands[3].eval (context).toString ().contains ("raw");

            simulator.outputs.put (path, H);
        }

        // Determine column name
        String column;
        if (operands.length > 2)  // column name is specified
        {
            column = operands[2].eval (context).toString ();
        }
        else  // auto-generate column name
        {
            if (context instanceof InstanceTemporaries) context = ((InstanceTemporaries) context).wrapped;
            column = (String) context.valuesObject[index];
            if (column == null)
            {
                String prefix = context.path ();
                if (prefix.isEmpty ()) column =                variableName;
                else                   column = prefix + "." + variableName;
                context.valuesObject[index] = column;
            }
        }

        H.trace (column, (float) ((Scalar) result).value);

        return result;
    }

    public Operator simplify (Variable from)
    {
        // Even if our variable is about to be replaced by a constant, we want to present its name in the output column.
        if (variableName0 == null  &&  operands.length > 0  &&  operands[0] instanceof AccessVariable)
        {
            variableName0 = ((AccessVariable) operands[0]).name;  // the raw name, including prime marks for derivatives
        }
        if (variableName1 == null  &&  operands.length > 1  &&  operands[1] instanceof AccessVariable)
        {
            variableName1 = ((AccessVariable) operands[1]).name;
        }

        return super.simplify (from);
    }

    // This method should be called by analysis, with v set to the variable that holds this equation.
    public void determineVariableName (Variable v)
    {
        // Ensure that the first operand is a file name.
        // If no file name is specified, stdout is used. We get the same effect by specifying the empty string, so insert it here.
        // This makes parameter processing much simpler elsewhere.
        int length = operands.length;
        if (length > 0  &&  ! isStringExpression (v, operands[0]))
        {
            variableName = variableName0;

            Operator[] newOperands = new Operator[length+1];
            for (int i = 0; i < length; i++) newOperands[i+1] = operands[i];
            newOperands[0] = new Constant (new Text ());
            operands = newOperands;
        }
        else
        {
            variableName = variableName1;
        }

        if (length < 3)  // Column name not specified
        {
            if (variableName == null) variableName = v.nameString ();

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

    /**
        Determines if the given operator is a string expression.
        This is the case if any operator it depends on is a string.
        If an operand is a variable, then its equations must contain a string.
    **/
    public static boolean isStringExpression (Variable v, Operator op)
    {
        class StringVisitor extends Visitor
        {
            boolean foundString;
            Variable from;
            public StringVisitor (Variable from)
            {
                this.from = from;
                from.visited = null;
            }
            public boolean visit (Operator op)
            {
                if (op instanceof Constant)
                {
                    Constant c = (Constant) op;
                    if (c.value instanceof Text) foundString = true;
                    return false;
                }
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    Variable v = av.reference.variable;

                    // Prevent infinite recursion
                    Variable p = from;
                    while (p != null)
                    {
                        if (p == v) return false;
                        p = p.visited;
                    }

                    v.visited = from;
                    from = v;
                    v.visit (this);
                    from = v.visited;
                    return false;
                }
                if (foundString) return false;  // no reason to dig any further
                return op instanceof Add;  // Add is the only operator that can propagate string values. All other operators and functions return scalars or matrices.
            }
        }
        StringVisitor visitor = new StringVisitor (v);
        op.visit (visitor);
        return visitor.foundString;
    }

    public void dependOnIndex (Variable v, EquationSet container)
    {
        while (container != null)
        {
            Variable index = container.find (new Variable ("$index"));
            if (index != null  &&  ! container.isSingleton ())
            {
                v.addDependencyOn (index);
            }
            container = container.container;
        }
    }

    public String toString ()
    {
        return "output";
    }
}
