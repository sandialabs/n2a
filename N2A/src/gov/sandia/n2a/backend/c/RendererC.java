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
import gov.sandia.n2a.language.function.Input;
import gov.sandia.n2a.language.function.Norm;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.function.ReadMatrix;
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
        if (op instanceof Exp)  // Prevent fp hint from being emitted into c code.
        {
            Exp e = (Exp) op;
            result.append ("exp (");
            e.operands[0].render (this);
            result.append (")");
            return true;
        }
        if (op instanceof Power)
        {
            Power p = (Power) op;
            result.append ("pow (");
            p.operand0.render (this);
            result.append (", ");
            p.operand1.render (this);
            result.append (")");
            return true;
        }
        if (op instanceof Tangent)  // Prevent fp hint from being emitted into c code.
        {
            Tangent t = (Tangent) op;
            result.append ("tan (");
            t.operands[0].render (this);
            result.append (")");
            return true;
        }
        if (op instanceof Norm)
        {
            Norm n = (Norm) op;
            result.append ("(");
            n.operands[1].render (this);
            result.append (").norm (");
            n.operands[0].render (this);
            result.append (")");
            return true;
        }
        if (op instanceof Gaussian)
        {
            Gaussian g = (Gaussian) op;
            result.append ("gaussian (");
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
            result.append ("uniform (");
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
            result.append (matrixName + "->");
            if (mode.equals ("rows"))
            {
                result.append ("rows ()");
            }
            else if (mode.equals ("columns"))
            {
                result.append ("columns ()");
            }
            else
            {
                result.append ("get");
                if (mode.equals ("raw")) result.append ("Raw");
                result.append (" (");
                r.operands[1].render (this);
                result.append (", ");
                r.operands[2].render (this);
                result.append (")");
            }
            return true;
        }
        if (op instanceof Output)
        {
            Output o = (Output) op;
            String outputName;
            if (o.operands[0] instanceof Constant) outputName = job.outputNames.get (o.operands[0].toString ());
            else                                   outputName = job.outputNames.get (o);
            result.append (outputName + "->trace (simulator.currentEvent->t, ");

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
        if (op instanceof BuildMatrix)
        {
            result.append (job.matrixNames.get (op));
            return true;
        }
        return false;
    }

    public String print (double d, int exponent)
    {
        String result = Scalar.print (d);
        if ((int) d != d  &&  job.numericType.equals ("float")) result += "f";  // Tell C compiler that our type is float, not double.
        return result;
    }
}
