/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.util.HashSet;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.BuildMatrix;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorArithmetic;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.OperatorLogical;
import gov.sandia.n2a.language.OperatorUnary;
import gov.sandia.n2a.language.function.Cosine;
import gov.sandia.n2a.language.function.Event;
import gov.sandia.n2a.language.function.Exp;
import gov.sandia.n2a.language.function.Floor;
import gov.sandia.n2a.language.function.Gaussian;
import gov.sandia.n2a.language.function.Input;
import gov.sandia.n2a.language.function.Log;
import gov.sandia.n2a.language.function.Norm;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.function.ReadMatrix;
import gov.sandia.n2a.language.function.Round;
import gov.sandia.n2a.language.function.Signum;
import gov.sandia.n2a.language.function.Sine;
import gov.sandia.n2a.language.function.SquareRoot;
import gov.sandia.n2a.language.function.Tangent;
import gov.sandia.n2a.language.function.Uniform;
import gov.sandia.n2a.language.operator.AND;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.operator.Divide;
import gov.sandia.n2a.language.operator.LT;
import gov.sandia.n2a.language.operator.Modulo;
import gov.sandia.n2a.language.operator.Multiply;
import gov.sandia.n2a.language.operator.MultiplyElementwise;
import gov.sandia.n2a.language.operator.NOT;
import gov.sandia.n2a.language.operator.Power;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class RendererCfp extends RendererC
{
    protected Operator latch;

    protected static HashSet<Class<? extends Operator>> operatorsWithExponent = new HashSet<Class<? extends Operator>> ();
    static
    {
        operatorsWithExponent.add (Exp       .class);
        operatorsWithExponent.add (Gaussian  .class);
        operatorsWithExponent.add (Input     .class);
        operatorsWithExponent.add (Log       .class);
        operatorsWithExponent.add (Norm      .class);
        operatorsWithExponent.add (Output    .class);
        operatorsWithExponent.add (Power     .class);
        operatorsWithExponent.add (ReadMatrix.class);
        operatorsWithExponent.add (SquareRoot.class);
        operatorsWithExponent.add (Tangent   .class);
        operatorsWithExponent.add (Uniform   .class);
    }

    public RendererCfp (JobC job, StringBuilder result, EquationSet part)
    {
        super (job, result, part);
        useExponent = true;  // Turn on emission of fixed-point parameters in superclass.
    }

    /**
        Sometimes it is necessary to to let the superclass (RendererC) decide whether to
        render the operator or defer to the operator's own render method, but then continue
        with our work to render more code around the result. The two available methods are:
            op.render (this);          // Produces infinite loop.
            return super.render (op);  // We can't continue.
        This function sets up a latch to suppress recursion for one cycle.
    **/
    public void escalate (Operator op)
    {
        latch = op;
        op.render (this);
    }

    public boolean render (Operator op)
    {
        if (op == latch)
        {
            latch = null;
            return super.render (op);
        }

        // Operators that are explicitly forwarded to RendererC, because they simply add
        // one or more exponent parameters to the end of the usual function call.
        if (operatorsWithExponent.contains (op.getClass ())) return super.render (op);

        // Arithmetic operators
        // These are ordered to trap specific cases before more general ones.
        if (op instanceof OperatorLogical)
        {
            // Don't convert boolean to fixed-point except at the outermost expression. 
            if (op.parent != null  &&  op.parent instanceof OperatorLogical) return super.render (op);

            result.append ("(");
            escalate (op);
            result.append (" ? ");
            if (op instanceof NOT)
            {
                result.append ("0 : " + print (1, op.exponentNext) + ")");
            }
            else
            {
                result.append (print (1, op.exponentNext) + " : 0)");
            }
            return true;
        }
        if (op instanceof Multiply  ||  op instanceof MultiplyElementwise)
        {
            OperatorBinary b = (OperatorBinary) op;

            // Explanation of shift -- The exponent of the result will be the sum of the exponents
            // of the two operands. That new exponent will be associated with bit position 2*MSB.
            // We want the exponent at bit position MSB.
            int exponentRaw = b.operand0.exponentNext + b.operand1.exponentNext - Operator.MSB;  // Exponent at MSB position after a direct integer multiply.
            int shift = exponentRaw - b.exponentNext;

            if (shift == 0)
            {
                escalate (b);
            }
            else
            {
                if (b.getType () instanceof Matrix)
                {
                    if (shift > 0)
                    {
                        result.append ("shiftUp (");
                        escalate (b);
                        result.append (", " + shift + ")");
                    }
                    else
                    {
                        if (b instanceof Multiply  ||  b.operand0.isScalar ()  ||  b.operand1.isScalar ())
                        {
                            result.append ("multiply (");
                        }
                        else  // MultiplyElementwise and both operands are matrices
                        {
                            result.append ("multiplyElementwise (");
                        }
                        if (b.operand0.isScalar ())
                        {
                            // Always put the scalar in second position, so we need only one form of multiply().
                            b.operand1.render (this);
                            result.append (", ");
                            b.operand0.render (this);
                        }
                        else
                        {
                            b.operand0.render (this);
                            result.append (", ");
                            b.operand1.render (this);
                        }
                        result.append (", " + -shift + ")");
                    }
                }
                else
                {
                    if (shift > 0)
                    {
                        result.append ("(");
                        escalate (b);
                        result.append (" << " + shift);
                        result.append (")");
                    }
                    else
                    {
                        if (! parentAccepts64bit (b)) result.append ("(int32_t) ");
                        result.append ("(");
                        if (! provides64bit (b.operand0)) result.append ("(int64_t) ");
                        escalate (b);
                        result.append (" >> " + -shift);
                        result.append (")");
                    }
                }
            }
            return true;
        }
        if (op instanceof Divide)
        {
            Divide d = (Divide) op;

            // Explanation of shift -- In a division, the quotient is effectively down-shifted by
            // the number of bits in the denominator, and its exponent is the difference between
            // the exponents of the numerator and denominator.
            int exponentRaw = d.operand0.exponentNext - d.operand1.exponentNext + Operator.MSB;  // Exponent in MSB from a direct integer division.
            int shift = exponentRaw - d.exponentNext;

            if (shift == 0)
            {
                escalate (d);
            }
            else
            {
                if (d.getType () instanceof Matrix)
                {
                    if (shift > 0)
                    {
                        result.append ("divide (");
                        d.operand0.render (this);
                        result.append (", ");
                        d.operand1.render (this);
                        result.append (", " + shift + ")");
                    }
                    else
                    {
                        result.append ("shiftDown (");
                        escalate (d);
                        result.append (", " + -shift + ")");
                    }
                }
                else
                {
                    if (shift > 0)
                    {
                        if (! parentAccepts64bit (d)) result.append ("(int32_t) ");
                        result.append ("((");
                        if (! provides64bit (d.operand0)) result.append ("(int64_t) ");
                        // OperatorBinary.render() will add parentheses around operand0 if it has lower
                        // precedence than division. This includes the case where it has lower precedence
                        // than shift, so we are safe.
                        d.render (this, " << " + shift + ") / ");
                        result.append (")");
                    }
                    else
                    {
                        result.append ("(");
                        escalate (d);
                        result.append (" >> " + -shift + ")");
                    }
                }
            }
            return true;
        }
        if (op instanceof Modulo)
        {
            Modulo m = (Modulo) op;

            int shift = m.exponent - m.exponentNext;
            if (m.operand0.exponentNext == m.operand1.exponentNext)
            {
                if (shift == 0) return super.render (m);
                result.append ("(");
                escalate (m);
                result.append (printShift (shift) + ")");
            }
            else
            {
                if (shift != 0) result.append ("(");
                result.append ("mod (");
                m.operand0.render (this);
                result.append (", ");
                m.operand1.render (this);
                result.append (", " + m.operand0.exponentNext + ", " + m.operand1.exponentNext + ")");
                if (shift != 0) result.append (printShift (shift) + ")");
            }
            return true;
        }
        if (op instanceof OperatorArithmetic)  // Add, Subtract, Negate, Transpose
        {
            int exponentOperand;
            if   (op instanceof OperatorBinary) exponentOperand = ((OperatorBinary) op).operand0.exponentNext;
            else                                exponentOperand = ((OperatorUnary)  op).operand .exponentNext;
            int shift = exponentOperand - op.exponentNext;
            if (shift == 0) return super.render (op);
            if (op.getType () instanceof Matrix)
            {
                if (shift > 0) result.append ("shiftUp (");
                else           result.append ("shiftDown (");
                escalate (op);
                result.append (", " + Math.abs (shift) + ")");
            }
            else
            {
                result.append ("(");

                boolean needParens = op.precedence () > new Add ().precedence ();  // In C++, shift is just below addition in precedence.
                if (needParens) result.append ("(");
                escalate (op);
                if (needParens) result.append (")");

                result.append (printShift (shift));
                result.append (")");
            }
            return true;
        }

        // Functions
        // These are listed in alphabetical order, with a catch-all at the end.
        if (op instanceof Cosine)
        {
            Cosine c = (Cosine) op;
            Operator a = c.operands[0];
            int shift = c.exponent - c.exponentNext;
            if (shift != 0) result.append ("(");
            result.append ("cos (");
            a.render (this);
            result.append (", " + a.exponentNext + ")");
            if (shift != 0) result.append (printShift (shift) + ")");
            return true;
        }
        if (op instanceof Event)
        {
            if (op.parent instanceof OperatorLogical) return super.render (op);

            Event e = (Event) op;
            int exponentRaw = Operator.MSB - e.eventType.valueIndex;
            int shift = exponentRaw - e.exponentNext;

            if (shift != 0) result.append ("(");
            result.append ("(flags & (" + bed.localFlagType + ") 0x1 << " + e.eventType.valueIndex + ")");
            if (shift != 0)
            {
                result.append (printShift (shift));
                result.append (")");
            }
            return true;
        }
        if (op instanceof Floor)
        {
            Floor f = (Floor) op;
            Operator a = f.operands[0];
            // Floor always sets operands[0].exponentNext to be same as f.exponentNext, so no shift is necessary.
            if (f.exponentNext >= Operator.MSB)  // LSB is above decimal, so floor() operation is impossible.
            {
                a.render (this);
            }
            else if (f.exponentNext < 0)  // All bits are below decimal
            {
                result.append ("0");
            }
            else
            {
                // Mask off bits below the decimal point. This works for both positive and negative numbers.
                int zeroes = Operator.MSB - f.exponentNext;
                int decimalMask = 0xFFFFFFFF << zeroes;
                boolean needParens = a.precedence () >= new AND ().precedence ();
                result.append ("(");
                if (needParens) result.append ("(");
                a.render (this);
                if (needParens) result.append (")");
                result.append (" & " + decimalMask + ")");
            }
            return true;
        }
        if (op instanceof Round)
        {
            Round r = (Round) op;
            Operator a = r.operands[0];
            int shift = a.exponentNext - r.exponentNext;
            int decimalPlaces = Math.max (0, Operator.MSB - a.exponentNext);
            int mask = 0xFFFFFFFF << decimalPlaces;
            int half = 0;
            if (decimalPlaces > 0) half = 0x1 << decimalPlaces - 1;

            if (shift != 0) result.append ("(");
            result.append ("("); // Bitwise operators are low precedence, so we parenthesize them regardless.
            boolean needParens = a.precedence () > new Add ().precedence ();
            if (needParens) result.append ("(");
            a.render (this);
            if (needParens) result.append (")");
            result.append (" + " + half + " & " + mask);
            result.append (")"); // Close parens around bitwise operator
            if (shift != 0) result.append (printShift (shift) + ")");
            return true;
        }
        if (op instanceof Signum)
        {
            Signum s = (Signum) op;
            Operator a = s.operands[0];
            int one = 0x1 << Operator.MSB - s.exponentNext;
            boolean needParens = a.precedence () >= new LT ().precedence ();
            if (needParens) result.append ("(");
            a.render (this);
            if (needParens) result.append (")");
            result.append (" < 0 ? " + -one + ":" + one + ")");
            return true;
        }
        if (op instanceof Sine)
        {
            Sine s = (Sine) op;
            Operator a = s.operands[0];
            int shift = s.exponent - s.exponentNext;
            if (shift != 0) result.append ("(");
            result.append ("sin (");
            a.render (this);
            result.append (", " + a.exponentNext + ")");
            if (shift != 0) result.append (printShift (shift) + ")");
            return true;
        }
        if (op instanceof Function)  // AbsoluteValue, Grid, Max, Min
        {
            int shift = op.exponent - op.exponentNext;
            if (shift == 0) return super.render (op);

            if (op.getType () instanceof Matrix)
            {
                if (shift > 0) result.append ("shiftUp (");
                else           result.append ("shiftDown (");
                escalate (op);
                result.append (", " + Math.abs (shift) + ")");
            }
            else
            {
                result.append ("(");
                escalate (op);
                result.append (printShift (shift));
                result.append (")");
            }
            return true;
        }

        return super.render (op);
    }

    /**
        The given operator uses 64-bit in order to preserve precision.
        The result can remain in 64-bit if that is useful to the parent operator.
    **/
    public boolean provides64bit (Operator op)
    {
        if (! (op.getType () instanceof Scalar)) return false;
        // At this point, both operands should be scalars.
        if (op instanceof Multiply)
        {
            OperatorBinary b = (OperatorBinary) op;
            int exponentRaw = b.operand0.exponentNext + b.operand1.exponentNext - Operator.MSB;
            int shift = exponentRaw - b.exponentNext;
            return shift < 0;  // only use 64-bit when we downshift the raw result
        }
        if (op instanceof Divide)
        {
            OperatorBinary b = (OperatorBinary) op;
            int exponentRaw = b.operand0.exponentNext - b.operand1.exponentNext + Operator.MSB;
            int shift = exponentRaw - b.exponentNext;
            return shift > 0;  // only use 64-bit when we upshift the raw result
        }
        return false;
    }

    /**
        The parent of the given operator either requires 64-bit, or it is the final assignment
        to a 32-bit variable and the downcast is implied.
    **/
    public boolean parentAccepts64bit (Operator op)
    {
        if (op.parent == null) return true;  // Assignment to variable
        if (op.parent instanceof BuildMatrix) return true;  // Assignment to element of matrix
        if (op.parent instanceof Multiply  ||  op.parent instanceof Divide)
        {
            OperatorBinary p = (OperatorBinary) op.parent;
            if (op == p.operand0) return provides64bit (p);
        }
        return false;
    }

    public String print (double d, int exponent)
    {
        if (d == 0) return "0";
        if (Double.isNaN (d)) return String.valueOf (Integer.MIN_VALUE);
        boolean negate = d < 0;
        if (Double.isInfinite (d))
        {
            if (negate) return String.valueOf (Integer.MIN_VALUE + 1);
            return             String.valueOf (Integer.MAX_VALUE);
        }

        long bits = Double.doubleToLongBits (d);
        int  e    = Math.getExponent (d);
        bits |= 0x10000000000000l;  // set implied msb of mantissa (bit 52) to 1
        bits &= 0x1FFFFFFFFFFFFFl;  // clear sign and exponent bits
        if (negate) bits = -bits;
        bits >>= 52 - Operator.MSB + exponent - e;
        return Integer.toString ((int) bits);
    }
}
