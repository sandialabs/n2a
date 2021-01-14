/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.AccessElement;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.BuildMatrix;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.function.Atan;
import gov.sandia.n2a.language.function.Columns;
import gov.sandia.n2a.language.function.Delay;
import gov.sandia.n2a.language.function.Event;
import gov.sandia.n2a.language.function.Exp;
import gov.sandia.n2a.language.function.Gaussian;
import gov.sandia.n2a.language.function.Grid;
import gov.sandia.n2a.language.function.HyperbolicTangent;
import gov.sandia.n2a.language.function.Input;
import gov.sandia.n2a.language.function.Log;
import gov.sandia.n2a.language.function.Max;
import gov.sandia.n2a.language.function.Min;
import gov.sandia.n2a.language.function.Norm;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.function.ReadMatrix;
import gov.sandia.n2a.language.function.Rows;
import gov.sandia.n2a.language.function.Sat;
import gov.sandia.n2a.language.function.SquareRoot;
import gov.sandia.n2a.language.function.Tangent;
import gov.sandia.n2a.language.function.Uniform;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.operator.Modulo;
import gov.sandia.n2a.language.operator.Power;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class RendererC extends Renderer
{
    public JobC         job;
    public EquationSet  part;
    public BackendDataC bed;
    public boolean      global;   ///< Whether this is in the population object (true) or a part object (false)
    public boolean      hasEvent; ///< Indicates that event has been retrieved within current scope.
    public boolean      useExponent;  ///< Some functions have extra parameters in fixed-point mode. Rather than duplicate rendering code, we tack on the extra parameters here.

    public RendererC (JobC job, StringBuilder result)
    {
        super (result);
        this.job = job;
    }

    public void setPart (EquationSet part)
    {
        this.part = part;
        bed       = (BackendDataC) part.backendData;
    }

    public boolean render (Operator op)
    {
        // TODO: for "3 letter" functions (sin, cos, pow, etc) on matrices, render as visitor which produces a matrix result
        // TODO: implement min and max on matrices

        if (op instanceof Add)
        {
            Add a = (Add) op;
            // Check if this is a string expression
            if (a.name != null)
            {
                result.append (a.name);
                return true;
            }
            return false;
        }
        if (op instanceof AccessElement)
        {
            AccessElement ae = (AccessElement) op;
            ae.operands[0].render (this);
            if (ae.operands.length == 2)
            {
                result.append ("[");
                ae.operands[1].render (this);
                result.append ("]");
            }
            else if (ae.operands.length == 3)
            {
                result.append ("(");
                ae.operands[1].render (this);
                result.append (",");
                ae.operands[2].render (this);
                result.append (")");
            }
            return true;
        }
        if (op instanceof AccessVariable)
        {
            AccessVariable av = (AccessVariable) op;
            int shift = av.exponent - av.exponentNext;
            if (useExponent  &&  shift != 0) result.append ("(");
            result.append (job.resolve (av.reference, this, false));
            if (useExponent  &&  shift != 0) result.append (printShift (shift) + ")");
            return true;
        }
        if (op instanceof Atan)
        {
            Atan atan = (Atan) op;
            int shift = atan.exponent - atan.exponentNext;
            if (useExponent  &&  shift != 0) result.append ("(");
            if (atan.operands.length > 1  ||  useExponent) result.append ("atan2 (");
            else                                           result.append ("atan (");

            Operator y = atan.operands[0];
            if (y.getType () instanceof Matrix)
            {
                y.render (this);
                result.append ("[1], ");
                y.render (this);
                result.append ("[0]");
            }
            else
            {
                y.render (this);
                if (atan.operands.length > 1)
                {
                    result.append (", ");
                    atan.operands[1].render (this);  // x
                }
                else if (useExponent)
                {
                    int shiftX = Operator.MSB - y.exponent;
                    int x =  shiftX >= 0 ? 0x1 << shiftX : 0;
                    result.append (", " + x);
                }
            }

            result.append (")");
            if (useExponent  &&  shift != 0) result.append (printShift (shift) + ")");
            return true;
        }
        if (op instanceof BuildMatrix)
        {
            BuildMatrix b = (BuildMatrix) op;
            result.append (b.name);
            return true;
        }
        if (op instanceof Columns)
        {
            Columns c = (Columns) op;
            c.operands[0].render (this);
            result.append (".columns ()");
            return true;
        }
        if (op instanceof Constant)
        {
            Constant c = (Constant) op;
            Type o = c.value;
            if (o instanceof Scalar)
            {
                result.append (print (((Scalar) o).value, c.exponentNext));
                return true;
            }
            if (o instanceof Text)
            {
                result.append ("\"" + o.toString () + "\"");
                return true;
            }
            if (o instanceof Matrix)
            {
                result.append (c.name);
                return true;
            }
            return false;
        }
        if (op instanceof Delay)
        {
            Delay d = (Delay) op;
            result.append ("delay" + d.index + ".step (Simulator<" + job.T + ">::instance.currentEvent->t, ");
            d.operands[1].render (this);
            result.append (", ");
            d.operands[0].render (this);
            result.append (", ");
            if (d.operands.length > 2) d.operands[2].render (this);
            else                       result.append ("0");
            result.append (")");
            return true;
        }
        if (op instanceof Event)
        {
            Event e = (Event) op;
            // The cast to bool gets rid of the specific numeric value from flags.
            // If used in a numeric expression, it should convert to either 1 or 0.
            result.append ("((bool) (flags & (" + bed.localFlagType + ") 0x1 << " + e.eventType.valueIndex + "))");
            return true;
        }
        if (op instanceof Exp)
        {
            Exp e = (Exp) op;
            Operator a = e.operands[0];
            result.append ("exp (");
            a.render (this);
            if (useExponent) result.append (", " + e.exponentNext);
            result.append (")");
            return true;
        }
        if (op instanceof Gaussian)
        {
            Gaussian g = (Gaussian) op;
            int shift = op.exponent - op.exponentNext;
            if (useExponent  &&  shift != 0) result.append ("(");
            result.append ("gaussian<" + job.T + "> (");
            if (g.operands.length > 0)
            {
                g.operands[0].render (this);
            }
            result.append (")");
            if (useExponent  &&  shift != 0) result.append (printShift (shift) + ")");
            return true;
        }
        if (op instanceof Grid)
        {
            Grid g = (Grid) op;
            boolean raw = g.operands.length >= 5  &&  g.operands[4].getString ().contains ("raw");
            int shift = g.exponent - g.exponentNext;

            if (useExponent  &&  shift != 0) result.append ("(");
            result.append ("grid");
            if (raw) result.append ("Raw");
            result.append ("<" + job.T + "> (");

            int count = Math.min (4, g.operands.length);
            if (count > 0) g.operands[0].render (this);
            for (int i = 1; i < count; i++)
            {
                result.append (", ");
                g.operands[i].render (this);
            }

            result.append (")");
            if (useExponent  &&  shift != 0) result.append (printShift (shift) + ")");
            return true;
        }
        if (op instanceof HyperbolicTangent)
        {
            HyperbolicTangent t = (HyperbolicTangent) op;
            Operator a = t.operands[0];
            result.append ("tanh (");
            a.render (this);
            result.append (")");
            return true;
        }
        if (op instanceof Input)
        {
            Input i = (Input) op;

            String mode = "";
            if      (i.operands.length == 2) mode = i.operands[1].getString ();
            else if (i.operands.length >= 4) mode = i.operands[3].getString ();

            int shift = i.exponent - i.exponentNext;
            if (useExponent  &&  shift != 0) result.append ("(");
            if (mode.contains ("columns"))
            {
                result.append (i.name + "->getColumns ()");
            }
            else
            {
                Operator op1 = i.operands[1];
                Operator op2 = i.operands[2];
                result.append (i.name + "->get");
                if (mode.contains ("raw")) result.append ("Raw");
                result.append (" (");
                op1.render (this);
                result.append (", ");
                op2.render (this);
                result.append (")");
            }
            if (useExponent  &&  shift != 0) result.append (printShift (shift) + ")");

            return true;
        }
        if (op instanceof Log)
        {
            Log l = (Log) op;
            Operator a = l.operands[0];
            result.append ("log (");
            a.render (this);
            if (useExponent) result.append (", " + a.exponentNext + ", " + l.exponentNext);
            result.append (")");
            return true;
        }
        if (op instanceof Max)
        {
            Max m = (Max) op;
            for (int i = 0; i < m.operands.length - 1; i++)
            {
                Operator a = m.operands[i];
                result.append ("max (");
                a.render (this);
                appendType (a);
                result.append (", ");
            }
            Operator b = m.operands[m.operands.length - 1];
            b.render (this);
            appendType (b);
            for (int i = 0; i < m.operands.length - 1; i++) result.append (")");
            return true;
        }
        if (op instanceof Min)
        {
            Min m = (Min) op;
            for (int i = 0; i < m.operands.length - 1; i++)
            {
                Operator a = m.operands[i];
                result.append ("min (");
                a.render (this);
                appendType (a);
                result.append (", ");
            }
            Operator b = m.operands[m.operands.length - 1];
            b.render (this);
            appendType (b);
            for (int i = 0; i < m.operands.length - 1; i++) result.append (")");
            return true;
        }
        if (op instanceof Modulo)
        {
            Modulo m = (Modulo) op;
            Operator a = m.operand0;
            Operator b = m.operand1;
            result.append ("modFloor (");
            moduloParam (a);
            result.append (", ");
            moduloParam (b);
            result.append (")");
            return true;
        }
        if (op instanceof Norm)
        {
            Norm n = (Norm) op;
            Operator A = n.operands[0];
            result.append ("norm (");
            A.render (this);
            result.append (", ");
            n.operands[1].render (this);
            if (useExponent) result.append (", " + A.exponentNext + ", " + n.exponentNext);
            result.append (")");
            return true;
        }
        if (op instanceof Output)
        {
            Output o = (Output) op;
            result.append (o.name + "->trace (Simulator<" + job.T + ">::instance.currentEvent->t, ");

            if (o.hasColumnName)  // column name is explicit
            {
                o.operands[2].render (this);
            }
            else  // column name is generated, so use prepared string value
            {
                result.append (o.columnName);
            }
            result.append (", ");

            o.operands[1].render (this);
            if (useExponent) result.append (", " + o.operands[1].exponentNext);

            result.append (", ");
            if (o.operands.length < 4)  // No mode string
            {
                result.append ("0");  // null
            }
            else if (o.operands[3] instanceof Constant)  // Mode string is constant
            {
                result.append ("\"" + o.operands[3] + "\"");
            }
            else if (o.operands[3] instanceof Add)  // Mode string is calculated
            {
                Add a = (Add) o.operands[3];
                result.append (a.name);  // No need for cast or call c_str()
            }
            // else badness
            result.append (")");

            return true;
        }
        if (op instanceof Power)
        {
            Power p = (Power) op;
            Operator a = p.operand0;
            Operator b = p.operand1;
            result.append ("pow (");
            a.render (this);
            result.append (", ");
            b.render (this);
            if (useExponent) result.append (", " + a.exponentNext + ", " + p.exponentNext);
            result.append (")");
            return true;
        }
        if (op instanceof ReadMatrix)
        {
            ReadMatrix r = (ReadMatrix) op;

            String mode = "";
            int lastParm = r.operands.length - 1;
            if (lastParm > 0)
            {
                if (r.operands[lastParm] instanceof Constant)
                {
                    Constant c = (Constant) r.operands[lastParm];
                    if (c.value instanceof Text)
                    {
                        mode = ((Text) c.value).value;
                    }
                }
            }

            int shift = r.exponent - r.exponentNext;
            if (useExponent  &&  shift != 0) result.append ("(");
            if (mode.equals ("rows"))
            {
                result.append (r.name + "->rows ()");
            }
            else if (mode.equals ("columns"))
            {
                result.append (r.name + "->columns ()");
            }
            else
            {
                result.append (r.name + "->get");
                if (mode.equals ("raw")) result.append ("Raw");
                result.append (" (");
                r.operands[1].render (this);
                result.append (", ");
                r.operands[2].render (this);
                result.append (")");
            }
            if (useExponent  &&  shift != 0) result.append (printShift (shift) + ")");

            return true;
        }
        if (op instanceof Rows)
        {
            Rows r = (Rows) op;
            r.operands[0].render (this);
            result.append (".rows ()");
            return true;
        }
        if (op instanceof SquareRoot)
        {
            SquareRoot s = (SquareRoot) op;
            Operator a = s.operands[0];
            result.append ("sqrt (");
            a.render (this);
            if (useExponent) result.append (", " + a.exponentNext + ", " + s.exponentNext + ")");
            return true;
        }
        if (op instanceof Sat)
        {
            Sat s = (Sat) op;
            Operator a     = s.operands[0];
            Operator lower = s.operands[1];
            Operator upper;
            if (s.operands.length >= 3) upper = s.operands[2];
            else                        upper = lower;

            result.append ("max (");
            if (s.operands.length == 2) result.append ("-1 * (");
            lower.render (this);
            appendType (lower);
            if (s.operands.length == 2) result.append (")");

            result.append (", min (");
            upper.render (this);
            appendType (upper);

            result.append (", ");
            a.render (this);
            appendType (a);

            result.append ("))");
            return true;
        }
        if (op instanceof Tangent)
        {
            Tangent t = (Tangent) op;
            Operator a = t.operands[0];
            result.append ("tan (");
            a.render (this);
            if (useExponent) result.append (", " + a.exponentNext + ", " + t.exponentNext);
            result.append (")");
            return true;
        }
        if (op instanceof Uniform)
        {
            Uniform u = (Uniform) op;
            int shift = op.exponent - op.exponentNext;
            if (useExponent  &&  shift != 0) result.append ("(");
            result.append ("uniform<" + job.T + "> (");
            if (u.operands.length > 0)
            {
                u.operands[0].render (this);
            }
            result.append (")");
            if (useExponent  &&  shift != 0) result.append (printShift (shift) + ")");
            return true;
        }

        return super.render (op);
    }

    public String printShift (int shift)
    {
        if (shift == 0) return "";
        if (shift > 0) return " << " +  shift;
        else           return " >> " + -shift;
    }

    /**
        Prints any target type (float, double, int) in as simple a form as possible.
    **/
    public String print (double d, int exponent)
    {
        String result = Scalar.print (d);
        if ((int) d != d  &&  job.T.equals ("float")) result += "f";  // Tell C compiler that our type is float, not double.
        return result;
    }

    /**
        Add the float type indicator that print() omits, in order to overcome type matching
        problems for certain functions, such as min() and max().
    **/
    public void appendType (Operator a)
    {
        if (a.isScalar ())
        {
            double d = a.getDouble ();
            if ((int) d == d)
            {
                if      (job.T.equals ("float" )) result.append (".0f");
                else if (job.T.equals ("double")) result.append (".0");
            }
        }
    }

    public void moduloParam (Operator a)
    {
        if (a.isScalar ())
        {
            double d = a.getDouble ();
            result.append (String.valueOf (d));
            if (job.T.equals ("float")) result.append ("f");
            return;
        }
        if (a instanceof AccessVariable)
        {
            // Trap variables that are declared int
            Variable v = ((AccessVariable) a).reference.variable;
            if (v.name.equals ("$index")  ||  v.name.equals ("$n")  &&  v.hasAttribute ("initOnly"))
            {
                result.append ("(" + job.T + ") ");
            }
            a.render (this);
            return;
        }
        // The general case
        result.append ("(" + job.T + ") (");
        a.render (this);
        result.append (")");
    }
}
