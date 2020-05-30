/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import gov.sandia.n2a.backend.internal.InstanceTemporaries;
import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.plugins.extpoints.Backend;

public class Output extends Function
{
    public String  variableName;  // Trace needs to know its target variable in order to auto-generate a column name. This value is set by an analysis process.
    public String  variableName0; // As found in operand[0]
    public String  variableName1; // As found in operand[1]
    public boolean hasColumnName; // Indicates that column name is explicitly provided, rather than generated.
    public int     index;         // For Internal backend, the index of generated column name in valuesObject array.
    public String  name;          // For C backend, the name of the OutputHolder object.
    public String  fileName;      // For C backend, the name of the string variable holding the file name, if any.
    public String  columnName;    // For C backend, the name of the string variable holding the generated column name, if any.

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

    /**
        Depends on determineVariableName() to ensure that file name is in operands[0].
    **/
    public void determineExponent (Variable from)
    {
        Operator op = operands[1];
        op.determineExponent (from);
        if (operands.length > 2) operands[2].determineExponent (from);  // In case column index is computed.
        updateExponent (from, op.exponent, op.center);
    }

    public void determineExponentNext (Variable from)
    {
        // Value
        Operator op = operands[1];
        op.exponentNext = exponentNext;
        op.determineExponentNext (from);

        // Column identifier (name or index)
        if (operands.length >= 3)
        {
            op = operands[2];
            if (operands.length > 3  &&  operands[3].getString ().contains ("raw"))
            {
                op.exponentNext = MSB;  // index, so pure integer
            }
            else
            {
                op.exponentNext = op.exponent;  // name; can be a string or a float
            }
            op.determineExponentNext (from);
        }
    }

    public static class Holder implements gov.sandia.n2a.backend.internal.Holder
    {
        public Map<String,Integer> columnMap    = new HashMap<String,Integer> ();  // Maps from column name to column position.
        public MDoc                columnMode;                                     // Maps from column name to a set of mode flags.
        public List<Float>         columnValues = new ArrayList<Float> ();         // Holds current value for each column.
        public int                 columnsPrevious;                                // Number of columns written in previous cycle.
        public boolean             traceReceived;                                  // Indicates that at least one column was touched during the current cycle.
        public double              t;
        public PrintStream         out;
        public boolean             raw;                                            // Indicates that column is an exact index.

        public Holder (Simulator simulator, String path)
        {
            if (path.isEmpty ())
            {
                out = simulator.out;
                columnMode = new MDoc (simulator.jobDir.resolve ("out.columns"));
            }
            else
            {
                try
                {
                    out = new PrintStream (simulator.jobDir.resolve (path).toFile (), "UTF-8");
                    columnMode = new MDoc (simulator.jobDir.resolve (path + ".columns"));
                }
                catch (Exception e)
                {
                    out = simulator.out;
                    columnMode = new MDoc (simulator.jobDir.resolve ("out.columns"));
                }
            }
        }

        public void close ()
        {
            writeTrace ();
            out.close ();
            columnMode.save ();
        }

        public void trace (double now, String column, float value, String mode)
        {
            // Detect when time changes and dump any previously traced values.
            if (now > t)
            {
                writeTrace ();
                t = now;
            }

            if (! traceReceived)  // First trace for this cycle
            {
                traceReceived = true;
                if (columnValues.isEmpty ())  // slip $t into first column 
                {
                    columnMap.put ("$t", 0);
                    columnValues.add ((float) t);
                    columnMode.set ("$t", 0);
                }
                else
                {
                    columnValues.set (0, (float) t);
                }
            }

            Integer index = columnMap.get (column);
            if (index == null)  // Add new column
            {
                if (raw)
                {
                    int i = Integer.valueOf (column) + 1;  // offset for time in first column
                    while (columnValues.size () < i) columnValues.add (Float.NaN);
                    index = i;
                }
                else
                {
                    index = columnValues.size ();
                }
                columnMap.put (column, index);
                columnValues.add (value);

                columnMode.set (column, index);  // Report all column names, regardless of whether they have any mode flags.
                if (mode != null)
                {
                    String[] hints = mode.split (",");
                    for (String h : hints)
                    {
                        h = h.trim ();
                        if (h.isEmpty ()  ||  h.equals ("raw")) continue;
                        String[] pieces = h.split ("=", 2);
                        String key = pieces[0].trim ();
                        String val = "";
                        if (pieces.length > 1) val = pieces[1].trim ();
                        if (key.equals ("timeScale")) columnMode.set (val, 0,     "scale");  // Set on time column. 
                        else                          columnMode.set (val, index, key);
                    }
                }
            }
            else  // Existing column
            {
                columnValues.set (index, value);
            }
        }

        public void writeTrace ()
        {
            if (! traceReceived) return;  // Don't output anything unless at least one value was set.

            int count = columnValues.size ();
            int last  = count - 1;

            // Write headers if new columns have been added.
            if (count > columnsPrevious)
            {
                if (! raw)
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
                }
                columnsPrevious = count;
                columnMode.save ();
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

    public Type getType ()
    {
        return new Scalar ();
    }

    public Type eval (Instance context)
    {
        Type result = operands[1].eval (context);
        Simulator simulator = Simulator.getSimulator (context);
        if (simulator == null) return result;

        String mode = null;
        if (operands.length > 3) mode = operands[3].eval (context).toString ();

        String path = ((Text) operands[0].eval (context)).value;
        Holder H;
        Object o = simulator.holders.get (path);
        if (o == null)
        {
            H = new Holder (simulator, path);
            if (mode != null) H.raw = mode.contains ("raw");
            simulator.holders.put (path, H);
        }
        else if (! (o instanceof Holder))
        {
            Backend.err.get ().println ("ERROR: Reopening file as a different resource type.");
            throw new Backend.AbortRun ();
        }
        else H = (Holder) o;

        // Determine column name
        String column;
        if (hasColumnName)  // column name is specified
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

        double now;
        if (simulator.currentEvent == null) now = 0;
        else                                now = (float) simulator.currentEvent.t;

        if (result instanceof Matrix)
        {
            Matrix A = (Matrix) result;
            int rows = A.rows ();
            int cols = A.columns ();
            if (rows == 1)
            {
                for (int c = 0; c < cols; c++) H.trace (now, column + "(" + c + ")", (float) A.get (0, c), mode);
            }
            else if (cols == 1)
            {
                for (int r = 0; r < rows; r++) H.trace (now, column + "(" + r + ")", (float) A.get (r, 0), mode);
            }
            else
            {
                for (int r = 0; r < rows; r++)
                {
                    for (int c = 0; c < cols; c++)
                    {
                        H.trace (now, column + "(" + r + "," + c + ")", (float) A.get (r, c), mode);
                    }
                }
            }
        }
        else
        {
            H.trace (now, column, (float) ((Scalar) result).value, mode);
        }

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

        hasColumnName = operands.length > 2  &&  ! (operands[2] instanceof Constant  &&  ((Constant) operands[2]).value.toString ().isEmpty ());
        if (! hasColumnName)  // Column name not specified
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
                for (ConnectionBinding c : container.connectionBindings)
                {
                    dependOnIndex (v, c.endpoint);
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
        class StringVisitor implements Visitor
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
            if (index != null  &&  ! container.isSingleton (false))
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
