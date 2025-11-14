/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
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
    public void determineExponent (ExponentContext context)
    {
        Operator op1 = operands[1];
        op1.determineExponent (context);
        if (operands.length > 2) operands[2].determineExponent (context);  // In case column index is computed.
        updateExponent (context, op1.exponent, op1.center);

        if (keywords == null) return;
        for (Operator op : keywords.values ())
        {
            op.determineExponent (context);
        }
    }

    public void determineExponentNext ()
    {
        // Value
        Operator op1 = operands[1];
        op1.exponentNext = exponentNext;
        op1.determineExponentNext ();

        // Column identifier (name or index)
        if (operands.length > 2)
        {
            Operator op2 = operands[2];
            op2.exponentNext = 0;  // If index, it will be truncated to an integer.
            op2.determineExponentNext ();
        }

        // Keywords
        if (keywords == null) return;
        for (Operator op : keywords.values ())
        {
            op.exponentNext = op.exponent;
            op.determineExponentNext ();
        }
    }

    public static class Holder implements AutoCloseable
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
                path = "out";
            }
            else
            {
                try
                {
                    // Trap paths to non-existent directories, typically sub-directories of jobDir.
                    // The assumption is that in most cases the user wants output to go somewhere in
                    // jobDir. A sub-directory of jobDir is (currently) not visible in the jobs tab,
                    // so flatten the path. OTOH, a path to an external dir that does exist indicates
                    // that the user really wants the output to go to a special location.
                    Path target = simulator.jobDir.resolve (path);
                    if (! Files.exists (target.getParent ()))
                    {
                        path = path.replace ("/", "_");
                        path = path.replace ("\\", "_");
                        target = simulator.jobDir.resolve (path);
                    }
                    out = new PrintStream (target.toFile (), "UTF-8");
                }
                catch (Exception e)
                {
                    out = simulator.out;
                    path = "out";
                }
            }
            columnMode = new MDoc (simulator.jobDir.resolve (path + ".columns"));
        }

        public static Holder get (Simulator simulator, String path, boolean raw)
        {
            Holder result;
            Object o = simulator.holders.get (path);
            if (o == null)
            {
                result = new Holder (simulator, path);
                result.raw = raw;
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

        public void close ()
        {
            writeTrace ();
            out.close ();
            columnMode.save ();
        }

        public void trace (double now, String column, float value)
        {
            trace (now, column, value, null, null);
        }

        public void trace (double now, String column, float value, Map<String,Operator> mode, Instance context)
        {
            // Detect when time changes and dump any previously traced values.
            if (now != t)  // Compare not equal allows keyword "x" (if defined) to move backward as well as forward.
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
                    int i = Integer.valueOf (column) + 1;  // 1 is offset for time in first column
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
                    for (Entry<String,Operator> h : mode.entrySet ())
                    {
                        String key = h.getKey ();
                        String val;
                        Operator op = h.getValue ();
                        if (op instanceof Constant)
                        {
                            Constant c = (Constant) op;
                            if (c.unitValue == null) val = c.value.toString ();
                            else                     val = c.unitValue.toString ();  // So we render units.
                        }
                        else
                        {
                            val = op.eval (context).toString ();
                        }

                        switch (key)
                        {
                            case "raw":
                            case "x":
                                break;
                            case "timeScale":
                            case "xscale":
                                columnMode.set (val, 0, "scale");  // Set on time column.
                                break;
                            case "scatter":
                            case "xmax":
                            case "xmin":
                            case "ymax":
                            case "ymin":
                                columnMode.set (val, 0, key);  // All chart-wide parameters go on time column.
                                break;
                            default:
                                columnMode.set (val, index, key);
                        }
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
                        String h = headers[i];
                        if (h.contains ("\t")  ||  h.contains (" ")  ||  h.contains (",")  ||  h.contains ("\""))  // Delimiters are forbidden inside naked headers, so quote.
                        {
                            h = h.replaceAll ("\"", "\"\"");  // Replace " with "". At least MS Excel is known to convert "" back to " on reading a CSV.
                            out.print ("\"" + h + "\"");
                        }
                        else
                        {
                            out.print (h);
                        }
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
        return operands[1].getType ();
    }

    public Type eval (Instance context)
    {
        Type result = operands[1].eval (context);
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return result;

        String  path = ((Text) operands[0].eval (context)).value;
        boolean raw  = getKeywordFlag ("raw");
        Holder  H    = Holder.get (simulator, path, raw);

        String column = getColumnName (context);

        double now;
        if (simulator.currentEvent == null) now = 0;
        else                                now = evalKeyword (context, "x", simulator.currentEvent.t);

        if (result instanceof Matrix)
        {
            Matrix A = (Matrix) result;
            int rows = A.rows ();
            int cols = A.columns ();
            if (rows == 1)
            {
                for (int c = 0; c < cols; c++) H.trace (now, column + "(" + c + ")", (float) A.get (0, c), keywords, context);
            }
            else if (cols == 1)
            {
                for (int r = 0; r < rows; r++) H.trace (now, column + "(" + r + ")", (float) A.get (r, 0), keywords, context);
            }
            else
            {
                for (int r = 0; r < rows; r++)
                {
                    for (int c = 0; c < cols; c++)
                    {
                        H.trace (now, column + "(" + r + "," + c + ")", (float) A.get (r, c), keywords, context);
                    }
                }
            }
        }
        else
        {
            H.trace (now, column, (float) ((Scalar) result).value, keywords, context);
        }

        return result;
    }

    public String getColumnName (Instance context)
    {
        // Explicit column name
        if (hasColumnName)
        {
            Type name = operands[2].eval (context);
            if (name instanceof Scalar) return String.valueOf ((long) ((Scalar) name).value);
            return name.toString ();
        }

        // Auto-generate column name
        if (context instanceof InstanceTemporaries) context = ((InstanceTemporaries) context).wrapped;
        String result = (String) context.valuesObject[index];
        if (result == null)
        {
            String prefix = context.path ();
            if (prefix.isEmpty ()) result =                variableName;
            else                   result = prefix + "." + variableName;
            context.valuesObject[index] = result;
        }
        return result;
    }

    public Operator simplify (Variable from, boolean evalOnly)
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

        return super.simplify (from, evalOnly);
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
                // Check if entire path back to root is singletons.
                boolean allSingleton = true;
                EquationSet p = container;
                while (allSingleton  &&  p != null)
                {
                    allSingleton = p.isSingleton ();
                    p = p.container;
                }
                if (allSingleton)  // Can assemble a constant name now.
                {
                    hasColumnName = true;
                    if (operands.length < 3)
                    {
                        Operator[] newOperands = new Operator[3];
                        for (int i = 0; i < operands.length; i++) newOperands[i] = operands[i];
                        operands = newOperands;
                    }
                    String name = container.prefix ();
                    if (! name.isEmpty ()) name += ".";
                    name += variableName;
                    operands[2] = new Constant (name);
                }
                else  // Must generate name at runtime.
                {
                    dependOnIndex (v, container);
                }
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
