/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.AccessElement;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.BuildMatrix;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.function.Event;
import gov.sandia.n2a.language.function.Exp;
import gov.sandia.n2a.language.function.Gaussian;
import gov.sandia.n2a.language.function.Grid;
import gov.sandia.n2a.language.function.Input;
import gov.sandia.n2a.language.function.Log;
import gov.sandia.n2a.language.function.Norm;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.function.ReadMatrix;
import gov.sandia.n2a.language.function.SquareRoot;
import gov.sandia.n2a.language.function.Tangent;
import gov.sandia.n2a.language.function.Uniform;
import gov.sandia.n2a.language.operator.Add;
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

    public RendererC (JobC job, StringBuilder result, EquationSet part)
    {
        super (result);
        this.job  = job;
        this.part = part;
        bed       = (BackendDataC) part.backendData;
    }

    public boolean render (Operator op)
    {
        // TODO: for "3 letter" functions (sin, cos, pow, etc) on matrices, render as visitor which produces a matrix result

        if (op instanceof Add)
        {
            // Check if this is a string expression
            String stringName = job.stringNames.get (op);
            if (stringName != null)
            {
                result.append (stringName);
                return true;
            }
            return false;
        }
        if (op instanceof AccessVariable)
        {
            AccessVariable av = (AccessVariable) op;
            result.append (job.resolve (av.reference, this, false));
            return true;
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
        if (op instanceof BuildMatrix)
        {
            result.append (job.matrixNames.get (op));
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
        if (op instanceof Log)
        {
            Log l = (Log) op;
            Operator a = l.operands[0];
            result.append ("log (");
            a.render (this);
            if (useExponent) result.append (", " + a.exponentNext + ", " + l.exponentNext + ")");
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
        if (op instanceof SquareRoot)
        {
            SquareRoot s = (SquareRoot) op;
            Operator a = s.operands[0];
            result.append ("sqrt (");
            a.render (this);
            if (useExponent) result.append (", " + a.exponentNext + ", " + s.exponentNext + ")");
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
        if (op instanceof Gaussian)
        {
            Gaussian g = (Gaussian) op;
            result.append ("gaussian<" + job.T + "> (");
            if (g.operands.length > 0)
            {
                g.operands[0].render (this);
            }
            result.append (")");
            return true;
        }
        if (op instanceof Uniform)
        {
            Uniform u = (Uniform) op;
            result.append ("uniform<" + job.T + "> (");
            if (u.operands.length > 0)
            {
                u.operands[0].render (this);
            }
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
        if (op instanceof Grid)
        {
            Grid g = (Grid) op;
            boolean raw = g.operands.length >= 5  &&  g.operands[4].getString ().contains ("raw");

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

            String matrixName;
            if (r.operands[0] instanceof Constant) matrixName = job.matrixNames.get (r.operands[0].toString ());
            else                                   matrixName = job.matrixNames.get (r);

            int shift = r.exponent - r.exponentNext;
            if (useExponent  &&  shift != 0) result.append ("(");
            if (mode.equals ("rows"))
            {
                result.append (matrixName + "->rows ()");
            }
            else if (mode.equals ("columns"))
            {
                result.append (matrixName + "->columns ()");
            }
            else
            {
                result.append (matrixName + "->get");
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
        if (op instanceof Output)
        {
            Output o = (Output) op;
            String outputName;
            if (o.operands[0] instanceof Constant) outputName = job.outputNames.get (o.operands[0].toString ());
            else                                   outputName = job.outputNames.get (o);
            result.append (outputName + "->trace (Simulator<" + job.T + ">::instance.currentEvent->t, ");

            if (o.operands.length > 2)  // column name is explicit
            {
                o.operands[2].render (this);
            }
            else  // column name is generated, so use prepared string value
            {
                String stringName = job.stringNames.get (op);  // generated column name is associated with Output function itself, rather than one of its operands
                result.append (stringName);
            }
            result.append (", ");

            o.operands[1].render (this);
            if (useExponent) result.append (", " + o.operands[1].exponentNext);
            result.append (")");

            return true;
        }
        if (op instanceof Input)
        {
            Input i = (Input) op;
            String inputName;
            if (i.operands[0] instanceof Constant) inputName = job.inputNames.get (i.operands[0].toString ());
            else                                   inputName = job.inputNames.get (i);

            String mode = "";
            if (i.operands.length > 3)
            {
                mode = i.operands[3].toString ();  // just assuming it's a constant string
            }
            else if (i.operands[1] instanceof Constant)
            {
                Constant c = (Constant) i.operands[1];
                if (c.value instanceof Text) mode = c.toString ();
            }

            int shift = i.exponent - i.exponentNext;
            if (useExponent  &&  shift != 0) result.append ("(");
            if (mode.contains ("columns"))
            {
                result.append (inputName + "->getColumns ()");
            }
            else
            {
                Operator op1 = i.operands[1];
                Operator op2 = i.operands[2];
                result.append (inputName + "->get");
                if (   mode.contains ("raw")   // select raw mode, but only if column is not identified by a string
                    && ! job.stringNames.containsKey (op2)
                    && ! (op2 instanceof Constant  &&  ((Constant) op2).value instanceof Text))
                {
                    result.append ("Raw");
                }
                result.append (" (");
                op1.render (this);
                result.append (", ");
                op2.render (this);
                result.append (")");
            }
            if (useExponent  &&  shift != 0) result.append (printShift (shift) + ")");

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
                result.append (job.matrixNames.get (op));
                return true;
            }
            return false;
        }
        return false;
    }

    public String printShift (int shift)
    {
        if (shift == 0) return "";
        if (shift > 0) return " << " +  shift;
        else           return " >> " + -shift;
    }

    public String print (double d, int exponent)
    {
        String result = Scalar.print (d);
        if ((int) d != d  &&  job.T.equals ("float")) result += "f";  // Tell C compiler that our type is float, not double.
        return result;
    }
}
