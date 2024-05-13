/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import javax.measure.Unit;

import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import tech.units.indriya.AbstractUnit;

public class Constant extends Operator
{
    public Type      value;
    public UnitValue unitValue; // Used for pretty printing. If value is a scalar, then this is the original object returned by the parser. Note that value has already been scaled to SI, so this is merely a memo.
    public String    name;      // For C backend, name of Matrix variable if this is a matrix constant.

    public Constant ()
    {
    }

    public Constant (Type value)
    {
        this.value = value;
    }

    public Constant (String value)
    {
        this.value = new Text (value);
    }

    public Constant (double value)
    {
        this.value = new Scalar (value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void getOperandsFrom (SimpleNode node)
    {
        Object o = node.jjtGetValue ();
        if (o instanceof UnitValue)
        {
            unitValue = (UnitValue) node.jjtGetValue ();
            if (unitValue.unit == null)  // naked number, so assume already in SI
            {
                unit = AbstractUnit.ONE;
                value = new Scalar (unitValue.value);
            }
            else  // there was a unit given, so convert
            {
                unit = unitValue.unit.getSystemUnit ();
                value = new Scalar (unitValue.unit.getConverterTo ((Unit) unit).convert (unitValue.value));
            }
        }
        else
        {
            unit = AbstractUnit.ONE;
            value = (Type) o;
        }
    }

    /**
        Constants are constructed so that "center" points to the most-significant bit of the value.

        TODO: Determine significant digits for scalar constants that lack unitValue.
        This can happen, for example, if a constant is calculated from other values during
        EquationSet.findConstants(). May also need to address this in BuildMatrix.
    **/
    public void determineExponent (ExponentContext context)
    {
        if (exponent != UNKNOWN) return;  // already done
        if (value instanceof Scalar)
        {
            double v = ((Scalar) value).value;
            int centerNew = MSB / 2;
            if (unitValue != null)
            {
                int bits = (int) Math.ceil (unitValue.digits * Math.log (10) / Math.log (2));
                centerNew = Math.max (centerNew, bits - 1);
                centerNew = Math.min (centerNew, MSB);
            }

            int e = 0;
            if (v != 0  &&  Double.isFinite (v)) e = Math.getExponent (v);
            int exponentNew = e - centerNew;
            updateExponent (context, exponentNew, centerNew);
        }
        // Matrix constants are built by BuildMatrix with their exponent and center values set correctly.
        // Text and reference types are simply ignored (and should have exponent=UNKNOWN).
    }

    /**
        Adjusts this constant so it better aligns with another operand.
    **/
    public void determineExponent (ExponentContext context, int exponentOther)
    {
        int shift = exponent - exponentOther;
        if (shift == 0) return;
        if (shift < 0)  // down-shift
        {
            // To avoid throwing away precision, we only shift until we run out of trailing zeros.
            // How many trailing zeros do we have?
            // Assuming 24 bits (1 implicit + 23 explicit) of a float, we have (center+1)-24 zeros
            // that are just padding, plus any trailing zeros in the value itself.
            int z = trailingZeros () + center - 23;
            z = Math.max (z, 0);  // Don't allow negative z. This could happen if we are truncating some bits (center is less than 23, but there are no trailing zeros).
            shift = Math.max (shift, -z);
        }
        else   // up-shift
        {
            int z = MSB - center;  // number of available (zero) bits to the left
            shift = Math.min (shift, z);
        }
        int exponentNew = exponent - shift;
        int centerNew   = center   + shift;
        updateExponent (context, exponentNew, centerNew);
    }

    public int trailingZeros ()
    {
        if (! (value instanceof Scalar)) return 0;
        float v = (float) ((Scalar) value).value;
        if (v == 0) return 32;  // entire mantissa is zeros, so shift as much as desired

        int bits = Float.floatToRawIntBits (v) | 0x800000;  // Force the implied first bit of mantissa to be 1, to stop iteration.
        return Integer.numberOfTrailingZeros (bits);
    }

    public void dumpExponents (String pad)
    {
        super.dumpExponents (pad);

        /*
        if (exponent == UNKNOWN) return;
        if (value instanceof Scalar)
        {
            double v = ((Scalar) value).value;
            System.out.print (pad + " ");
            if (unitValue != null)
            {
                int bits = (int) Math.ceil (unitValue.digits * Math.log (10) / Math.log (2));
                System.out.print (" bits=" + bits + " digits=" + unitValue.digits);
            }
            int e = 0;
            if (v != 0) e = Math.getExponent (v);
            System.out.print (" e=" + e);
            System.out.println ();
        }
        */
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        if (unit == null) unit = AbstractUnit.ONE;  // Just in case getOperandsFrom() was not called.
    }

    public void render (Renderer renderer)
    {
        if (renderer.render (this)) return;
        if (value instanceof Text) renderer.result.append ("\"" + value.toString () + "\"");
        else                       renderer.result.append (value.toString ());
    }

    public Type getType ()
    {
        return value;
    }

    public Type eval (Instance context)
    {
        return value;
    }

    public String toString ()
    {
        return value.toString ();
    }

    public boolean equals (Object that)
    {
        if (! (that instanceof Constant)) return false;
        Constant c = (Constant) that;
        return value.equals (c.value);
    }
}
