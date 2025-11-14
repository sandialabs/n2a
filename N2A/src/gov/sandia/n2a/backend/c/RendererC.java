/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.AccessElement;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.BuildMatrix;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.MatrixVisitable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.function.Atan;
import gov.sandia.n2a.language.function.Columns;
import gov.sandia.n2a.language.function.Delay;
import gov.sandia.n2a.language.function.Draw;
import gov.sandia.n2a.language.function.Draw2D;
import gov.sandia.n2a.language.function.Draw3D;
import gov.sandia.n2a.language.function.DrawCylinder;
import gov.sandia.n2a.language.function.DrawSphere;
import gov.sandia.n2a.language.function.Event;
import gov.sandia.n2a.language.function.Exp;
import gov.sandia.n2a.language.function.Gaussian;
import gov.sandia.n2a.language.function.Grid;
import gov.sandia.n2a.language.function.HyperbolicTangent;
import gov.sandia.n2a.language.function.Input;
import gov.sandia.n2a.language.function.Log;
import gov.sandia.n2a.language.function.Max;
import gov.sandia.n2a.language.function.Mcount;
import gov.sandia.n2a.language.function.Mfile;
import gov.sandia.n2a.language.function.Mmatrix;
import gov.sandia.n2a.language.function.Mnumber;
import gov.sandia.n2a.language.function.Mstring;
import gov.sandia.n2a.language.function.Min;
import gov.sandia.n2a.language.function.Mkey;
import gov.sandia.n2a.language.function.Norm;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.function.Pulse;
import gov.sandia.n2a.language.function.ReadImage;
import gov.sandia.n2a.language.function.ReadMatrix;
import gov.sandia.n2a.language.function.Rows;
import gov.sandia.n2a.language.function.Sat;
import gov.sandia.n2a.language.function.SquareRoot;
import gov.sandia.n2a.language.function.SumSquares;
import gov.sandia.n2a.language.function.Tangent;
import gov.sandia.n2a.language.function.Uniform;
import gov.sandia.n2a.language.function.glRotate;
import gov.sandia.n2a.language.function.glScale;
import gov.sandia.n2a.language.function.glTranslate;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.operator.Modulo;
import gov.sandia.n2a.language.operator.Power;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.plugins.extpoints.Backend;

public class RendererC extends Renderer
{
    public JobC           job;
    public EquationSet    part;
    public BackendDataC   bed;
    public boolean        global;                           // Whether this is in the population object (true) or a part object (false)
    public boolean        useExponent;                      // Some functions have extra parameters in fixed-point mode. Rather than duplicate rendering code, we tack on the extra parameters here.
    public Set<Object>    defined = new HashSet<Object> (); // List of holder objects which have been initialized. Used to prevent redundant initialization in a single function, so gets cleared between emission of different functions.

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
        for (ProvideOperator po : job.extensions)
        {
            Boolean result = po.render (this, op);
            if (result != null) return result;
        }

        if (op instanceof MatrixVisitable)  // For "3 letter" functions (sin, cos, etc) on matrices
        {
            Function f = (Function) op;  // MatrixVisitable should only be applied to Functions
            Operator op0 = f.operands[0];
            if (op0.getType () instanceof Matrix)
            {
                result.append ("visit (");
                op0.render (this);
                result.append (", " + f + ")");
                return true;
            }
            // Fall through to regular cases below.
        }
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
            result.append (".get (");  // safe element access
            ae.operands[1].render (this);
            if (ae.operands.length > 2)
            {
                result.append (",");
                ae.operands[2].render (this);
            }
            result.append (")");
            return true;
        }
        if (op instanceof AccessVariable)
        {
            AccessVariable av = (AccessVariable) op;
            result.append (job.resolve (av.reference, this, false));
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
                    int x =  y.exponent >= 0 ? 0x1 << y.exponent : 0;
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
                // Trap binary operators where one side is a matrix and the other is a scalar.
                // In this case, it is necessary to force the numeric type of the scalar, since
                // C++ won't promote an integer to match a template function.
                // We trap the individual constant rather than the operator, because OperatorBinary.render()
                // is rather complex, and we'd rather not recreate it here.
                if (op.parent instanceof OperatorBinary)
                {
                    OperatorBinary b = (OperatorBinary) op.parent;
                    if (c == b.operand0  &&  b.operand1.getType () instanceof Matrix  ||  c == b.operand1  &&  b.operand0.getType () instanceof Matrix)
                    {
                        renderType (op);
                        return true;
                    }
                }

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
            result.append ("delay" + d.index + ".step (" + job.SIMULATOR + "currentEvent->t, ");  // $t
            if (d.depth > 0)  // Use RingBuffer
            {
                result.append (job.resolve (bed.dt.reference, this, false));  // $t'
                result.append (", ");
                d.operands[0].render (this);  // value
            }
            else  // Use DelayBuffer
            {
                d.operands[1].render (this);  // delay
                result.append (", ");
                d.operands[0].render (this);  // value
                result.append (", ");
                if (d.operands.length > 2) d.operands[2].render (this);  // default value
                else                       result.append ("0");
            }
            result.append (")");
            return true;
        }
        if (op instanceof Draw)
        {
            Draw d = (Draw) op;
            if (op instanceof Draw3D)
            {
                boolean needModel = ((Draw3D) d).needModelMatrix ();
                int exponentP = 0;
                if (useExponent)
                {
                    // position and model matrix share same exponent
                    Operator model = d.getKeyword ("model");
                    if (model != null) exponentP = model.exponentNext;
                    else if (d.operands.length > 1) exponentP = d.operands[1].exponentNext;
                    // else model has been initialized to identity, presumably integer 1. Zero set above is suitable default.
                }

                result.append (d.name + "->" + op + " (" + job.SIMULATOR + "currentEvent->t, material");
                if (needModel)  // Currently, everything but DrawCylinder
                {
                    result.append (", model");
                    if (useExponent) result.append (", " + exponentP);
                }

                if (d instanceof DrawCylinder)
                {
                    result.append (", ");
                    d.operands[1].render (this);  // p1
                    if (useExponent) result.append (", " + exponentP);

                    result.append (", ");
                    d.operands[2].render (this);  // r1
                    if (useExponent) result.append (", " + d.operands[2].exponentNext);  // exponentR

                    result.append (", ");
                    d.operands[3].render (this);  // p2

                    result.append (", ");
                    if (d.operands.length > 4) d.operands[4].render (this);  // r2
                    else                       result.append ("-1");

                    result.append (", ");
                    Operator cap1 = d.getKeyword ("cap1");
                    if (cap1 == null) result.append ("0");
                    else              cap1.render (this);

                    result.append (", ");
                    Operator cap2 = d.getKeyword ("cap2");
                    if (cap2 == null) result.append ("0");
                    else              cap2.render (this);

                    result.append (", ");
                    Operator steps = d.getKeyword ("steps");
                    if (steps == null) result.append ("6");
                    else               steps.render (this);

                    result.append (", ");
                    Operator stepsCap = d.getKeyword ("stepsCap");
                    if (stepsCap == null) result.append ("-1");
                    else                  stepsCap.render (this);
                }
                else if (d instanceof DrawSphere)
                {
                    result.append (", ");
                    Operator steps = d.getKeyword ("steps");
                    if (steps == null) result.append ("1");
                    else               steps.render (this);
                }

                result.append (")");
            }
            else if (op instanceof Draw2D)
            {
                int last = d.operands.length - 1;
                result.append (d.name + "->" + op + " (" + job.SIMULATOR + "currentEvent->t, " + d.getKeywordFlag ("raw"));
                for (int i = 1; i < last; i++)
                {
                    result.append (", ");
                    d.operands[i].render (this);
                }
                if (useExponent) result.append (", " + d.operands[1].exponentNext);
                result.append (", ");
                d.operands[last].render (this);  // color
                result.append (")");
            }
            else  // generic Draw
            {
                // All this does is set keyword args, so it is already handled by JobC.prepareDynamicObjects().

                // Only return a value if we are not in a dummy equation, that is, if we are part of a proper expression.
                if (op.parent instanceof Variable  &&  ((Variable) op.parent).hasAttribute ("dummy"))
                {
                    result.append ("// generic draw()");  // Just to excuse the extra semicolon.
                }
                else
                {
                    result.append ("0");
                }
            }
            return true;
        }
        if (op instanceof Event)
        {
            Event e = (Event) op;
            // The cast to bool gets rid of the specific numeric value from flags.
            result.append ("((" + bed.getFlag ("flags", false, e.eventType.valueIndex) + ") ? 1 : 0)");
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
            result.append ("gaussian<" + job.T + "> (");
            if (g.operands.length > 0)
            {
                g.operands[0].render (this);
            }
            result.append (")");
            return true;
        }
        if (op instanceof glRotate)
        {
            glRotate r = (glRotate) op;
            result.append ("glRotate<" + job.T + "> (");
            r.operands[0].render (this);
            for (int i = 1; i < r.operands.length; i++)
            {
                result.append (", ");
                r.operands[i].render (this);
            }
            result.append (")");
            return true;
        }
        if (op instanceof glScale)
        {
            glScale s = (glScale) op;
            if (! (s.operands[0].getType () instanceof Matrix)) return super.render (op);
            result.append ("glScale<" + job.T + "> (");
            s.operands[0].render (this);
            for (int i = 1; i < s.operands.length; i++)
            {
                result.append (", ");
                s.operands[i].render (this);
            }
            result.append (")");
            return true;
        }
        if (op instanceof glTranslate)
        {
            glTranslate t = (glTranslate) op;
            if (! (t.operands[0].getType () instanceof Matrix)) return super.render (op);
            result.append ("glTranslate<" + job.T + "> (");
            t.operands[0].render (this);
            for (int i = 1; i < t.operands.length; i++)
            {
                result.append (", ");
                t.operands[i].render (this);
            }
            result.append (")");
            return true;
        }
        if (op instanceof Grid)
        {
            Grid g = (Grid) op;
            boolean raw = g.getKeywordFlag ("raw");
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
            result.append (i.name + "->get (");
            if (i.operands.length < 3  ||  i.operands[2].getDouble () < 0)  // return matrix
            {
                String defaultLine = i.usesTime () ? "-INFINITY" : "0";
                if (i.operands.length > 1)
                {
                    Operator op1 = i.operands[1];
                    if (op1.getType () instanceof Scalar) op1.render (this);           // expression for line
                    else                                  result.append (defaultLine); // op1 is probably the mode flag
                }
                else  // line is not specified. We're probably just retrieving a dummy matrix to get column count.
                {
                    result.append (defaultLine);
                }
            }
            else  // return scalar
            {
                i.operands[1].render (this);
                result.append (", ");
                i.operands[2].render (this);
            }
            result.append (")");
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
                renderType (a);
                result.append (", ");
            }
            Operator b = m.operands[m.operands.length - 1];
            renderType (b);
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
                renderType (a);
                result.append (", ");
            }
            Operator b = m.operands[m.operands.length - 1];
            renderType (b);
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
        if (op instanceof Mcount)
        {
            Mcount m = (Mcount) op;
            result.append (m.name + "->doc->child (");
            keyPath (m, 1);
            result.append (").size ()");
            return true;
        }
        if (op instanceof Mkey)
        {
            Mkey m = (Mkey) op;
            if (m.number)
            {
                if (useExponent) result.append ("convert (");
                else             result.append ("atof (");
            }
            result.append (m.name + "->getChildKey (");
            result.append ("\"" + m.getDelimiter () + "\", ");
            keyPath (m, 2);
            result.append (", ");
            m.operands[1].render (this);
            result.append (")");
            if (m.number)
            {
                if (useExponent) result.append (", " + m.exponentNext);
                else             result.append (".c_str ()");
                result.append (")");
            }
            return true;
        }
        if (op instanceof Mmatrix)
        {
            Mmatrix m = (Mmatrix) op;
            if (! (m.parent instanceof Variable)  ||  ! ((Variable) m.parent).hasAttribute ("MatrixPointer")) result.append ("*");  // Dereference the returned value, since variable is not a pointer.
            result.append (m.name + "->getMatrix (");
            result.append ("\"" + m.getDelimiter () + "\", ");
            keyPath (m, 1);
            if (useExponent)
            {
                if (m.operands.length > 1) result.append (", ");
                result.append (m.exponentNext);
            }
            result.append (")");
            return true;
        }
        if (op instanceof Mnumber)
        {
            Mnumber m = (Mnumber) op;
            if (useExponent)
            {
                result.append ("convert (");
                result.append (m.name + "->doc->get (");
            }
            else
            {
                result.append ("(" + job.T + ") " + m.name + "->doc->getDouble (");
            }
            keyPath (m, 1);
            result.append (")");
            if (useExponent) result.append (", " + m.exponentNext + ")");
            return true;
        }
        if (op instanceof Mstring)
        {
            Mstring m = (Mstring) op;
            result.append (m.name + "->doc->get (");
            keyPath (m, 1);
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
            renderType (n.operands[1]);
            if (useExponent) result.append (", " + A.exponentNext + ", " + n.exponentNext);
            result.append (")");
            return true;
        }
        if (op instanceof Output)
        {
            Output o = (Output) op;
            result.append (o.name + "->trace (");
            Operator x = o.getKeyword ("x");
            if (x == null) result.append (job.SIMULATOR + "currentEvent->t");
            else           x.render (this);
            result.append (", ");

            if (o.hasColumnName)  // column name is explicit
            {
                // If this column name is a number, then ensure it is an integer. Truncate everything after the decimal.
                boolean needCast =  ! useExponent  &&  o.operands[2].getType () instanceof Scalar;
                if (needCast) result.append ("(int) (");
                o.operands[2].render (this);
                if (needCast) result.append (")");
            }
            else  // column name is generated, so use prepared string value
            {
                result.append (o.columnName);
            }
            result.append (", ");

            o.operands[1].render (this);
            if (useExponent) result.append (", " + o.operands[1].exponentNext);

            result.append (", ");
            if (o.keywords == null)  // No mode string
            {
                result.append ("0");  // null
            }
            else  // Assemble mode string from keywords
            {
                String mode = "";
                boolean allConstant = true;
                for (Entry<String,Operator> k : o.keywords.entrySet ())
                {
                    String key = k.getKey ();
                    if (key.equals ("raw")  ||  key.equals ("x")) continue;

                    Operator kv = k.getValue ();
                    if (kv instanceof Constant)
                    {
                        Constant c = (Constant) kv;
                        mode += "," + key + "=";
                        if (c.unitValue == null) mode += c.value;
                        else                     mode += c.unitValue;
                    }
                    else
                    {
                        allConstant = false;
                    }
                }
                if (mode.startsWith (",")) mode = mode.substring (1);
                if (allConstant)
                {
                    result.append ("\"" + mode + "\"");
                }
                else  // Some keywords are calculated
                {
                    result.append ("(String (\"" + mode + "\")");
                    boolean commaNeeded = ! mode.isEmpty ();
                    for (Entry<String,Operator> k : o.keywords.entrySet ())
                    {
                        String key = k.getKey ();
                        if (key.equals ("raw")  ||  key.equals ("x")) continue;

                        if (commaNeeded) result.append ("+\",\"");
                        result.append ("+\"" + key + "=\"+");
                        Operator kv = k.getValue ();
                        if (kv instanceof Add)
                        {
                            Add a = (Add) kv;
                            // TODO: "a" could be a numerical add rather than string add. Need to test and handle that case.
                            result.append (a.name);
                        }
                        else if (kv instanceof AccessVariable)
                        {
                            kv.render (this);  // Should be a reference to the value. This could be a string or number. Can't be anything else, in particular a matrix.
                        }
                        else
                        {
                            Backend.err.get ().println ("Can't render keyword argument for output()");
                            throw new Backend.AbortRun ();
                        }
                        commaNeeded = true;
                    }
                    result.append (").c_str ()");
                }
            }
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
        if (op instanceof Pulse)
        {
            Pulse p = (Pulse) op;
            Operator t = p.operands[0];
            result.append ("pulse (");
            renderType (t);
            for (int i = 1; i < p.operands.length; i++)
            {
                result.append (", ");
                renderType (p.operands[i]);
            }
            result.append (")");
            return true;
        }
        if (op instanceof ReadImage)
        {
            ReadImage r = (ReadImage) op;
            result.append (r.name + "->get (");
            if (r.operands.length < 1) result.append ("\"Y\"");
            else                       r.operands[1].render (this);
            result.append (", ");
            if (r.operands.length < 2)
            {
                result.append (job.SIMULATOR + "currentEvent->t");
                result.append (", true");
            }
            else
            {
                r.operands[2].render (this);
                result.append (", false");
            }
            if (useExponent) result.append (", " + r.exponentNext);
            result.append (")");
            return true;
        }
        if (op instanceof ReadMatrix)
        {
            ReadMatrix r = (ReadMatrix) op;
            // Currently, ReadMatrix sets its exponent = exponentNext, so we will never do a shift here.
            // Any shifting should be handled by matrixHelper while loading and converting the matrix to integer.
            if (! (r.parent instanceof Variable)  ||  ! ((Variable) r.parent).hasAttribute ("MatrixPointer")) result.append ("*");  // matrices are held in pointers, so need to dereference
            result.append (r.name + "->A");
            return true;
        }
        if (op instanceof Rows)
        {
            Rows r = (Rows) op;
            r.operands[0].render (this);
            result.append (".rows ()");
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
            renderType (lower);
            if (s.operands.length == 2) result.append (")");

            result.append (", min (");
            renderType (upper);

            result.append (", ");
            renderType (a);

            result.append ("))");
            return true;
        }
        if (op instanceof SquareRoot)
        {
            SquareRoot s = (SquareRoot) op;
            Operator a = s.operands[0];
            result.append ("sqrt (");
            a.render (this);
            if (useExponent) result.append (", " + a.exponentNext + ", " + s.exponentNext);
            result.append (")");
            return true;
        }
        if (op instanceof SumSquares)
        {
            SumSquares ss = (SumSquares) op;
            Operator A = ss.operands[0];
            result.append ("sumSquares (");
            A.render (this);
            if (useExponent) result.append (", " + A.exponentNext + ", " + ss.exponentNext);
            result.append (")");
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
            result.append ("uniform<" + job.T + "> (");
            for (int i = 0; i < u.operands.length; i++)
            {
                if (i > 0) result.append (", ");
                u.operands[i].render (this);
            }
            result.append (")");
            return true;
        }

        return super.render (op);
    }

    public static String printShift (int shift)
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
        if (Double.isInfinite (d)) return (d < 0 ? "-" : "") + "INFINITY";
        String result = Scalar.print (d);
        if ((int) d != d  &&  job.T.equals ("float")) result += "f";  // Tell C compiler that our type is float, not double.
        return result;
    }

    /**
        Change integer values to float in order to overcome type matching problems
        for certain functions, such as min() and max(). This extends print() to add
        some gratuitous endings, forcing the type.
    **/
    public void renderType (Operator a)
    {
        if (a.isScalar ())
        {
            double d = a.getDouble ();
            String s = print (d, a.exponentNext);  // Don't use a.render(), because it produces infinite recursion with the OperatorBinary case above.
            if ((int) d == d)  // This is mutually-exclusive with the integer test in print() above.
            {
                if      (job.T.equals ("float" )) s += ".0f";
                else if (job.T.equals ("double")) s += ".0";
            }
            result.append (s);
        }
        else
        {
            // It's ugly to put casts in front of everything. The way to do better is to determine
            // if the result of the expression is an integer. That's more analysis than it's worth.
            if (! job.fixedPoint) result.append ("(" + job.T + ") ");
            a.render (this);
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

    public void keyPath (Mfile m, int start)
    {
        result.append ("keyPath (");
        result.append ("\"" + m.getDelimiter () + "\"");
        if (m.operands.length > start)  // operands[0] is path to M file
        {
            result.append (", ");
            m.operands[start].render (this);
            for (int i = start + 1; i < m.operands.length; i++)
            {
                result.append (", ");
                m.operands[i].render (this);
            }
        }
        result.append (")");
    }
}
