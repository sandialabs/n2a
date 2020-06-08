/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.python;

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
import gov.sandia.n2a.language.operator.AND;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.operator.Modulo;
import gov.sandia.n2a.language.operator.OR;
import gov.sandia.n2a.language.operator.Power;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;

public class RendererPython extends Renderer
{
    public JobPython         job;
    public EquationSet       part;
    public BackendDataPython bed;
    public boolean           global;   ///< Whether this is in the population object (true) or a part object (false)
    public boolean           hasEvent; ///< Indicates that event has been retrieved within current scope.

    public RendererPython (JobPython job, StringBuilder result)
    {
        super (result);
        this.job = job;
    }

    public void setPart (EquationSet part)
    {
        this.part = part;
        bed       = (BackendDataPython) part.backendData;
    }

    public boolean render (Operator op)
    {
        // TODO: for "3 letter" functions (sin, cos, pow, etc) on matrices, render as visitor which produces a matrix result

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
            if (ae.operands.length >= 2)
            {
                result.append ("[");
                ae.operands[1].render (this);
                if (ae.operands.length >= 3)
                {
                    result.append (",");
                    ae.operands[2].render (this);
                }
                result.append ("]");
            }
            return true;
        }
        if (op instanceof AccessVariable)
        {
            AccessVariable av = (AccessVariable) op;
            result.append (job.resolve (av.reference, this, false));
            return true;
        }
        if (op instanceof AND)
        {
            AND and = (AND) op;
            and.render (this, " and ");
            return true;
        }
        if (op instanceof BuildMatrix)
        {
            BuildMatrix b = (BuildMatrix) op;
            result.append (b.name);
            return true;
        }
        if (op instanceof Constant)
        {
            Constant c = (Constant) op;
            Type o = c.value;
            if (o instanceof Scalar)
            {
                result.append (print (((Scalar) o).value));
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
        if (op instanceof Event)
        {
            Event e = (Event) op;
            // The cast to bool gets rid of the specific numeric value from flags.
            // If used in a numeric expression, it will convert to either 1 or 0.
            result.append ("bool (flags & 0x1 << " + e.eventType.valueIndex + ")");
            return true;
        }
        if (op instanceof Exp)
        {
            Exp e = (Exp) op;
            Operator a = e.operands[0];
            result.append ("exp(");
            a.render (this);
            result.append (")");
            return true;
        }
        if (op instanceof Gaussian)
        {
            Gaussian g = (Gaussian) op;
            result.append ("gaussian(");
            if (g.operands.length > 0)
            {
                g.operands[0].render (this);
            }
            result.append (")");
            return true;
        }
        if (op instanceof Grid)
        {
            // TODO: needs library implementation
            Grid g = (Grid) op;
            boolean raw = g.operands.length >= 5  &&  g.operands[4].getString ().contains ("raw");

            result.append ("grid");
            if (raw) result.append ("Raw");
            result.append ("(");

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
        if (op instanceof Input)
        {
            // TODO: needs library implementation
            Input i = (Input) op;

            String mode = "";
            if      (i.operands.length == 2) mode = i.operands[1].getString ();
            else if (i.operands.length >= 4) mode = i.operands[3].getString ();

            if (mode.contains ("columns"))
            {
                result.append (i.name + "->getColumns ()");
            }
            else
            {
                Operator op1 = i.operands[1];
                Operator op2 = i.operands[2];
                result.append (i.name + ".get");
                if (mode.contains ("raw")) result.append ("Raw");
                result.append ("(");
                op1.render (this);
                result.append (", ");
                op2.render (this);
                result.append (")");
            }

            return true;
        }
        if (op instanceof Log)
        {
            Log l = (Log) op;
            Operator a = l.operands[0];
            result.append ("log(");
            a.render (this);
            return true;
        }
        if (op instanceof Modulo)
        {
            Modulo m = (Modulo) op;
            Operator a = m.operand0;
            Operator b = m.operand1;
            a.render (this);
            result.append (" % ");
            b.render (this);
            return true;
        }
        if (op instanceof Norm)
        {
            Norm n = (Norm) op;
            Operator A = n.operands[0];
            result.append ("numpy.linalg.norm(");
            A.render (this);
            result.append (", ");
            n.operands[1].render (this);
            result.append (")");
            return true;
        }
        if (op instanceof OR)
        {
            OR or = (OR) op;
            or.render (this, " or ");
            return true;
        }
        if (op instanceof Output)
        {
            Output o = (Output) op;
            result.append (o.name + "->trace (Simulator::instance.currentEvent->t, ");

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
            result.append (")");

            return true;
        }
        if (op instanceof Power)
        {
            Power p = (Power) op;
            Operator a = p.operand0;
            Operator b = p.operand1;
            result.append ("pow(");
            a.render (this);
            result.append (", ");
            b.render (this);
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
                result.append ("(");
                r.operands[1].render (this);
                result.append (", ");
                r.operands[2].render (this);
                result.append (")");
            }

            return true;
        }
        if (op instanceof SquareRoot)
        {
            SquareRoot s = (SquareRoot) op;
            Operator a = s.operands[0];
            result.append ("sqrt(");
            a.render (this);
            return true;
        }
        if (op instanceof Tangent)
        {
            Tangent t = (Tangent) op;
            Operator a = t.operands[0];
            result.append ("tan(");
            a.render (this);
            result.append (")");
            return true;
        }
        if (op instanceof Uniform)
        {
            Uniform u = (Uniform) op;
            result.append ("uniform(");
            if (u.operands.length > 0)
            {
                u.operands[0].render (this);
            }
            result.append (")");
            return true;
        }

        return super.render (op);
    }

    /**
        Prints any target type (float, double, int) in as simple a form as possible.
    **/
    public String print (double d)
    {
        return Scalar.print (d);
    }
}
