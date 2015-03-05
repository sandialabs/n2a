/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.parse;

import static javax.measure.unit.NonSI.POUND;
import static javax.measure.unit.SI.AMPERE;
import static javax.measure.unit.SI.KILO;
import static javax.measure.unit.SI.METER;
import static javax.measure.unit.SI.MICRO;

import java.awt.Font;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.measure.DecimalMeasure;
import javax.measure.Measure;
import javax.measure.quantity.Acceleration;
import javax.measure.quantity.AmountOfSubstance;
import javax.measure.quantity.Angle;
import javax.measure.quantity.AngularAcceleration;
import javax.measure.quantity.AngularVelocity;
import javax.measure.quantity.Area;
import javax.measure.quantity.CatalyticActivity;
import javax.measure.quantity.DataAmount;
import javax.measure.quantity.DataRate;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Duration;
import javax.measure.quantity.DynamicViscosity;
import javax.measure.quantity.ElectricCapacitance;
import javax.measure.quantity.ElectricCharge;
import javax.measure.quantity.ElectricConductance;
import javax.measure.quantity.ElectricCurrent;
import javax.measure.quantity.ElectricInductance;
import javax.measure.quantity.ElectricPotential;
import javax.measure.quantity.ElectricResistance;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Force;
import javax.measure.quantity.Frequency;
import javax.measure.quantity.Illuminance;
import javax.measure.quantity.KinematicViscosity;
import javax.measure.quantity.Length;
import javax.measure.quantity.LuminousFlux;
import javax.measure.quantity.LuminousIntensity;
import javax.measure.quantity.MagneticFlux;
import javax.measure.quantity.MagneticFluxDensity;
import javax.measure.quantity.Mass;
import javax.measure.quantity.MassFlowRate;
import javax.measure.quantity.Power;
import javax.measure.quantity.Pressure;
import javax.measure.quantity.Quantity;
import javax.measure.quantity.RadiationDoseAbsorbed;
import javax.measure.quantity.RadiationDoseEffective;
import javax.measure.quantity.RadioactiveActivity;
import javax.measure.quantity.SolidAngle;
import javax.measure.quantity.Temperature;
import javax.measure.quantity.Torque;
import javax.measure.quantity.Velocity;
import javax.measure.quantity.Volume;
import javax.measure.quantity.VolumetricDensity;
import javax.measure.quantity.VolumetricFlowRate;
import javax.measure.unit.Dimension;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.JTextComponent;

import org.jscience.economics.money.Money;
import org.jscience.physics.amount.Amount;
import org.jscience.physics.amount.AmountFormat;

import replete.util.DebugUtil;
import replete.util.Lay;
import replete.util.StringUtil;
import replete.util.mm.MembershipMap;

public class UnitHelper {

    public static void main(String[] args) {
//        String s = "<b attr=3 attr='3' attr=\"4>\">what</b>";
//        String s = "<b>what</b>";
//        String s = "<b  d='k'>what</b>";
//        s = s.replaceAll("</?[a-zA-Z0-9_-]+(\\s+[a-zA-Z0-9_-]+(\\s*=\\s*([a-zA-Z0-9_-]+|'[^']*'|\"[^\"]*\"))?)*>", "");

        DecimalMeasure<Length> heightOfHouse = new DecimalMeasure<Length>(new BigDecimal(12.0), METER);
        System.out.println(heightOfHouse);
        Measure<Float, Length> height = Measure.valueOf(12.F, METER);
        p(height);
        DebugUtil.printObjectDetails(height);
        p(height.intValue(METER));
        p(height.longValue(METER));
        p(height.floatValue(METER));
        p(height.doubleValue(METER));

        p(height.intValue(KILO(METER)));
        p(height.longValue(KILO(METER)));
        p(height.floatValue(KILO(METER)));
        p(height.doubleValue(KILO(METER)));

        p(height.doubleValue(KILO(METER)));

        p(SI.CENTIMETER.hashCode() == SI.METER.divide(100).hashCode());

        Unit<Length> m = METER;
        Unit<Length> cm = SI.KILOMETER;
/*        p(m,cm);
        p(m,SI.GRAM);
        p(m,NonSI.FOOT);
        p(m,SI.SQUARE_METRE);
        */
        p(m);
        p(SI.SQUARE_METRE);

        Measure<Double, Length> boatLengthMeter = Measure.valueOf(7.0, METER);
        //p(boatLengthMeter.floatValue(NonSI.DAY));   // Doesn't compile
        p(boatLengthMeter.longValue(NonSI.FOOT));
        p(boatLengthMeter.compareTo(Measure.valueOf(30, NonSI.FOOT)));
        p(boatLengthMeter.getValue());
        Measure<Double, Length> boatLengthFoot = boatLengthMeter.to(NonSI.FOOT);

        Unit<?> z = Unit.valueOf("kg/(s^2*A)");
        p(z.getDimension());
//        p(dimUnits.get(z.getDimension()));
        System.out.println(SI.WATT.getDimension());
        System.out.println(SI.PASCAL.getDimension());

        /*
        EscapeFrame f = new EscapeFrame("JScience Inspection");
        Lay.BLtg(f,
            Lay.TBL(
                "Dim->Units", x(dimToUnitsStr()),
                "Dim:Field->Obj", x(dimFieldToObjStr()),
                "SIUnit->Quan", x(siUnitsToQuans()),
                "Quan->SIUnit", x(quansToUnits()),
                "NonSIUnits", x(nonSIUnits()),
                "SIUnits", x(siUnits()),
                "Factors", x(factors()),
                "BaseUnits", x(typeUnits(BaseUnit.class)),
                "AlternateUnits", x(typeUnits(AlternateUnit.class)),
                "CompoundUnits", x(typeUnits(CompoundUnit.class)),
                "Currency", x(typeUnits(Currency.class)),
                "ProductUnit", x(typeUnits(ProductUnit.class)),
                "TransformedUnit", x(typeUnits(TransformedUnit.class))
            ),
            "size=[900,600],center,visible=true"
        );
        /**/

        Amount<Mass> m0 = Amount.valueOf(100, POUND);
        Amount<Mass> m1 = m0.times(33).divide(2);
        Amount<ElectricCurrent> m2 = Amount.valueOf("234 mA").to(
                MICRO(AMPERE));
        System.out.println("m0 = " + m0);
        System.out.println("m1 = " + m1);
        System.out.println("m2 = " + m2);


        /* Models aren't really implemented in JScience - save for the relativistic one, which equates length to time.
        p(Dimension.getModel().getDimension(SI.METRE));
        p(Dimension.getModel().getDimension(SI.SECOND));
        p(Dimension.getModel().getDimension(SI.AMPERE));
        p(Dimension.getModel().getDimension(SI.CANDELA));
        p(Dimension.getModel().getDimension(SI.KELVIN));
        p(Dimension.getModel().getDimension(SI.KILOGRAM));
        p(Dimension.getModel().getDimension(SI.MOLE));
        RelativisticModel.select();
        p("");
        Dimension.Model mmm;
        Class[] models = new Class[] {
            HighEnergyModel.class, NaturalModel.class, QuantumModel.class,
            RelativisticModel.class, StandardModel.class, Dimension.Model.STANDARD
        };
        p(Dimension.getModel().getDimension(SI.METRE));
        p(Dimension.getModel().getDimension(SI.SECOND));
        p(Dimension.getModel().getDimension(SI.AMPERE));
        p(Dimension.getModel().getDimension(SI.CANDELA));
        p(Dimension.getModel().getDimension(SI.KELVIN));
        p(Dimension.getModel().getDimension(SI.KILOGRAM));
        p(Dimension.getModel().getDimension(SI.MOLE));
        */

        p(Measure.valueOf(50D, NonSI.FOOT).to(SI.METER));
//        Amount<Length> a = Amount.valueOf(20, NonSI.FOOT);
//        Amount<Length> a2 = Amount.valueOf(30, NonSI.FOOT);
//        Amount<Length> sum = a.plus(a2);
//        p(sum);

        AmountFormat.setInstance(AmountFormat.getPlusMinusErrorInstance(3));
//        AmountFormat.setInstance(AmountFormat.getBracketErrorInstance(3));
//        AmountFormat.setInstance(AmountFormat.getExactDigitsInstance());
        // why error >= 4?? Exception in thread "main" java.lang.IllegalArgumentException: digits: 20

//        {
//            Amount<Length> e1 = Amount.valueOf(40, SI.CENTIMETER);
//            Amount<Length> e2 = Amount.valueOf(10, SI.METER);
//            p(e1); p(e2);
//            p(e1.plus(e2));   // exact makes sense
//            p(e2.plus(e1));   // not exact makes sense
//        }
//        {
//            Amount<Length> e1 = Amount.valueOf(50, NonSI.FOOT);
//            Amount<Length> e2 = Amount.valueOf(10, SI.METER);
//            p(e1); p(e2); p(e1.plus(e2));
//        }
//        {
//            Amount<Length> e1 = Amount.valueOf(50, NonSI.FOOT);
//            Amount<ElectricCurrent> e2 = Amount.valueOf(10, SI.AMPERE);
//            p(e1);
//            p(e2);
//            try {
//                p(e1.plus(e2));
//            } catch(ConversionException e) {
//                e.printStackTrace();
//            }
//        }
//        {
//            Amount<Length> e1 = Amount.valueOf(50, NonSI.FOOT);
//            Amount<Dimensionless> e2 = Amount.valueOf(10, Unit.ONE);
//            p(e1);
//            p(e2);
//            try {
//                p(e1.plus(e2));
//            } catch(ConversionException e) {
//                e.printStackTrace();
//            }
//        }
        {
            Amount<Length> x = Amount.valueOf(10, 2, NonSI.FOOT);
            Amount<Length> y = Amount.valueOf(10, 3, SI.METER);
            p(x.plus(y));
            Amount<?> q = Amount.valueOf(12.3, Unit.valueOf("m/s"));
            p(q);
            q.plus(x);

        }

//        p(Amount.valueOf(12, NonSI.FOOT));
//        p(Amount.valueOf(33D, NonSI.FOOT));
//        p(Amount.valueOf(33D, 5D, NonSI.FOOT));
    }
    private static void p(Amount<?> a) {
        p("Amount:");
        p("   units    = " + a.getUnit());
        p("   isexact  = " + a.isExact());
        p("   exactval = " + (a.isExact() ? a.getExactValue() : "<none>"));
        p("   estval   = " + a.getEstimatedValue());
        p("   minval   = " + a.getMinimumValue());
        p("   maxval   = " + a.getMaximumValue());
        p("   abserr   = " + a.getAbsoluteError());
        p("   relerr   = " + a.getRelativeError());
        p("   tostr    = " + a.toString());
        p("   totext   = " + a.toText());
        p("   quan     = " + siUnitQuans.get(a.getUnit().getStandardUnit()).getSimpleName());
//        p("   longval  = " + a.longValue(METER) + " (meters)");
//        p("   dblval   = " + a.doubleValue(METER) + " (meters)");
    }

    private static JScrollPane x(String s) {
        JTextComponent y = new JTextPane();
        y.setFont(new Font("Courier New", Font.PLAIN, 14));
        y.setEditable(false);
        y.setText(s);
        return Lay.sp(y);
    }

    private static String factors() {
        String ret = "";
        ret += "DECI, CENTI, MILLI, MICRO, NANO, PICO, FEMTO, ATTO, ZEPTO, YOCTO\n";
        ret += "\nLong Factors < 0:\n";

        long x = 1;
        do {
            x *= 10;
            ret += "   " + METER.divide(x) + "\n";
        } while(x < 1000000000000000000L);

        ret += "\nDouble Factors < 0:\n";
        double y = 1;
        do {
            y *= 10;
            Unit<Length> unit = METER.divide(y);
            ret += "   " + unit + " (" + unit.getClass().getSimpleName() + ")\n";
        } while(y < 1000000000000000000000000.0);

        ret += "\nDEKA, HECTO, KILO, MEGA, GIGA, TERA, PETA, EXA, ZETTA, YOTTA\n";

        ret += "\nLong Factors > 0:\n";

        x = 1;
        do {
            x *= 10;
            ret += "   " + METER.times(x) + "\n";
        } while(x < 1000000000000000000L);

        ret += "\nDouble Factors > 0:\n";
        y = 1;
        do {
            y *= 10;
            Unit<Length> unit = METER.times(y);
            ret += "   " + unit + " (" + unit.getClass().getSimpleName() + ")\n";
        } while(y < 1000000000000000000000000.0);

        return ret;
    }

    private static String nonSIUnits() {
        Map<String, Unit<?>> sortedUnits = new TreeMap<String, Unit<?>>();
        for(Unit<?> u : NonSI.getInstance().getUnits()) {
            sortedUnits.put(u.toString(), u);
        }
        String ret = "Count: " + sortedUnits.size() + "\n";
        for(Unit<?> u : sortedUnits.values()) {
            ret += u + "\n";
            ret += "   ClassName=" + u.getClass().getSimpleName() + "\n";
            ret += "   IsStdUnit=" + u.isStandardUnit() + "\n";
            ret += "   StdUnit=" + u.getStandardUnit() + "\n";
            ret += "   Dimension=" + u.getDimension() + "\n";
            ret += "   Inverse=" + u.inverse() + "\n";
            ret += "   Quantity=" + (siUnitQuans.get(u)==null?"<none>":siUnitQuans.get(u).getSimpleName()) + "\n";
            ret += "   StdQuantity=" + (siUnitQuans.get(u.getStandardUnit())==null?"<none>":siUnitQuans.get(u.getStandardUnit()).getSimpleName()) + "\n";
            if(siUnitQuans.get(u.getStandardUnit())==null) {
                System.err.println("PROBLEM: " + u);  // Roentgen, g/(cm*s) (Poise)
            }
            ret += "   FIELDS=" + Arrays.toString(unitToSystem.getMembers(u.toString()).keySet().toArray()) + "\n";
        }
        return ret;
    }

    private static String siUnits() {
        Map<String, Unit<?>> sortedUnits = new TreeMap<String, Unit<?>>();
        for(Unit<?> u : SI.getInstance().getUnits()) {
            sortedUnits.put(u.toString(), u);
        }
        String ret = "Count: " + sortedUnits.size() + "\n";
        for(Unit<?> u : sortedUnits.values()) {
            ret += u + "\n";
            ret += "   ClassName=" + u.getClass().getSimpleName() + "\n";
            ret += "   IsStdUnit=" + u.isStandardUnit() + "\n";
            ret += "   StdUnit=" + u.getStandardUnit() + "\n";
            ret += "   Dimension=" + u.getDimension() + "\n";
            ret += "   Inverse=" + u.inverse() + "\n";
            ret += "   Quantity=" + (siUnitQuans.get(u)==null?"<none>":siUnitQuans.get(u).getSimpleName()) + "\n";
            ret += "   StdQuantity=" + (siUnitQuans.get(u.getStandardUnit())==null?"<none>":siUnitQuans.get(u.getStandardUnit()).getSimpleName()) + "\n";
            if(siUnitQuans.get(u.getStandardUnit())==null) {
                System.err.println("PROBLEM: " + u);  // Roentgen, g/(cm*s) (Poise)
            }
            ret += "   FIELDS=" + Arrays.toString(unitToSystem.getMembers(u.toString()).keySet().toArray()) + "\n";
        }
        return ret;
    }

    private static String dimFieldToObjStr() {
        String ret = "Count: " + dimFieldToObj.size() + "\n";
        for(String s : dimFieldToObj.keySet()) {
            ret += s + " = " + dimFieldToObj.get(s) + "\n";
        }
        return ret;
    }

    private static String dimToUnitsStr() {
        String ret = "Count: " + dimToUnits.size() + "\n";
        for(Dimension dim : dimToUnits.keySet()) {
            if(dim == Dimension.NONE) {
                ret += "<none>\n";
            } else {
                ret += dim + "\n";
            }
            List<Unit<?>> units = dimToUnits.get(dim);
            for(Unit<?> u : units) {
                ret += "    " + u +
                    (u.isStandardUnit() ? " (standard)" : "") +
                    (SI.getInstance().getUnits().contains(u)?"(si)":"") + "\n";
            }
        }
        return ret;
    }

    private static String siUnitsToQuans() {
        String ret = "Count: " + siUnitQuans.size() + "\n";
        for(Unit<?> siUnit : siUnitQuans.keySet()) {
            Class<? extends Quantity> quan = siUnitQuans.get(siUnit);
            ret += q(siUnit) + " = " + quan.getSimpleName() + "\n";
        }
        return ret;
    }

    private static String q(Unit<?> siUnit) {
        return siUnit == Unit.ONE ? "<one>" : siUnit.toString();
    }
    private static String quansToUnits() {
        String ret = "Count: " + quanUnits.size() + "\n";
        for(Class<? extends Quantity> quan : quanUnits.keySet()) {
            Unit<?> siUnit = quanUnits.get(quan);
            ret += quan.getSimpleName() + " = " + q(siUnit) + "\n";
        }
        return ret;
    }
    private static String typeUnits(Class<?> clazz) {
        int count = 0;
        for(Unit<?> u : allUnits) {
            if(clazz.isAssignableFrom(u.getClass())) {
                count++;
            }
        }
        String ret = "Count: " + count + "\n";
        for(Unit<?> u : allUnits) {
            if(clazz.isAssignableFrom(u.getClass())) {
                ret += renderUnit(u);
            }
        }
        return ret;
    }

    private static String renderUnit(Unit<?> unit) {
        String ret = "";
        ret += unit + "\n";
        ret += "   ClassName=" + unit.getClass().getSimpleName() + "\n";
        ret += "   IsStdUnit=" + unit.isStandardUnit() + "\n";
        ret += "   StdUnit=" + unit.getStandardUnit() + "\n";
        ret += "   Dimension=" + unit.getDimension() + "\n";
        ret += "   Inverse=" + unit.inverse() + "\n";
        ret += "   Quantity=" + (siUnitQuans.get(unit)==null?"<none>":siUnitQuans.get(unit).getSimpleName()) + "\n";
        ret += "   StdQuantity=" + (siUnitQuans.get(unit.getStandardUnit())==null?"<none>":siUnitQuans.get(unit.getStandardUnit()).getSimpleName()) + "\n";
        if(siUnitQuans.get(unit.getStandardUnit())==null) {
            System.err.println("PROBLEM: " + unit);  // Roentgen, g/(cm*s) (Poise)
        }
        Object[] oo = unitToSystem.getMembers(unit.toString()).keySet().toArray();
        ret += "   FIELDS=" + Arrays.toString(oo) + "\n";
        ret += "   Name=" +
            StringUtil.removeStart(
                StringUtil.removeStart(
                    ((String) oo[0]).toLowerCase()
                        .replaceAll("_", " ")
                            .replaceAll("metre", "meter")
                            .replaceAll("litre", "liter"),
                                "si."), "nonsi.") + "\n";
        return ret;
    }

    private static Comparator<Object> cmp = new Comparator<Object>() {
        public int compare(Object o1, Object o2) {
            return o1.toString().compareTo(o2.toString());
        }
    };

    public static Unit<?> getSIUnitForQuantity(Class<? extends Quantity> quan) {
        return quanUnits.get(quan);
    }
    public static Class<? extends Quantity> getQuantityForUnit(Unit<?> unit) {
        return siUnitQuans.get(unit);
    }

    private static Map<Class<? extends Quantity>, Unit<?>> quanUnits = new TreeMap<Class<? extends Quantity>, Unit<?>>(cmp);
    private static Map<Unit<?>, Class<? extends Quantity>> siUnitQuans = new TreeMap<Unit<?>, Class<? extends Quantity>>(cmp);
    private static MembershipMap<String, String> unitToSystem = new MembershipMap<String, String>();
    private static Map<String, Dimension> dimFieldToObj = new TreeMap<String, Dimension>();
    private static Map<Dimension, List<Unit<?>>> dimToUnits = new HashMap<Dimension, List<Unit<?>>>();
    private static Set<Unit<?>> allUnits = new TreeSet<Unit<?>>(cmp);
    static {
        Class[] quans = new Class[] {
             Acceleration.class, AmountOfSubstance.class, Angle.class, AngularAcceleration.class,
             AngularVelocity.class, Area.class, CatalyticActivity.class, DataAmount.class, DataRate.class,
             Dimensionless.class, Duration.class, DynamicViscosity.class, ElectricCapacitance.class,
             ElectricCharge.class, ElectricConductance.class, ElectricCurrent.class, ElectricInductance.class,
             ElectricPotential.class, ElectricResistance.class, Energy.class, Force.class, Frequency.class,
             Illuminance.class, KinematicViscosity.class, Length.class, LuminousFlux.class, LuminousIntensity.class,
             MagneticFlux.class, MagneticFluxDensity.class, Mass.class, MassFlowRate.class, Money.class, Power.class,
             Pressure.class, RadiationDoseAbsorbed.class, RadiationDoseEffective.class, RadioactiveActivity.class,
             SolidAngle.class, Temperature.class, Torque.class, Velocity.class, Volume.class, VolumetricDensity.class,
             VolumetricFlowRate.class
         };
        for(Class<? extends Quantity> quan : quans) {
            try {
                String fieldN;
                if(quan.equals(Money.class)) {
                    fieldN = "BASE_UNIT";
                } else {
                    fieldN = "UNIT";
                }
                Field f = quan.getField(fieldN);
                Object o = f.get(null);
                if(quanUnits.put(quan, (Unit<?>) o) != null) {
                    System.err.println("PROBLEM");
                    System.exit(-1);
                }
                if(siUnitQuans.put((Unit<?>) o, quan) != null) {
                    System.err.println("PROBLEM 2");
                    System.exit(-1);
                }
            } catch(Exception e) {
                System.err.println(quan);
                e.printStackTrace();
            }
        }

        for(Unit<?> u : SI.getInstance().getUnits()) {
            allUnits.add(u);
        }
        for(Unit<?> u : NonSI.getInstance().getUnits()) {
            allUnits.add(u);
        }

        Class[] systems = new Class[] {SI.class, NonSI.class};
        for(Class system : systems) {
            Field[] fields = system.getFields();
            for(Field f : fields) {
                if(f.isAnnotationPresent(Deprecated.class)) {
                    continue;
                }
                if(Unit.class.isAssignableFrom(f.getType())) {
                    Object o;
                    try {
                        o = f.get(null);
                        if(o != null) {
                            unitToSystem.addMembership(o.toString(), system.getSimpleName()+"."+f.getName());
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        Field[] fields = Dimension.class.getFields();
        for(Field f : fields) {
            if(Modifier.isStatic(f.getModifiers()) && f.getType().equals(Dimension.class)) {
                try {
                    Object o = f.get(null);
                    if(o != null) {
                        dimFieldToObj.put(f.getName(), (Dimension) o);
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }

        for(Unit<?> u : NonSI.getInstance().getUnits()) {
            List<Unit<?>> uList = dimToUnits.get(u.getDimension());
            if(uList == null) {
                uList = new ArrayList<Unit<?>>();
                dimToUnits.put(u.getDimension(), uList);
            }
            uList.add(u);
        }
        for(Unit<?> u : SI.getInstance().getUnits()) {
            List<Unit<?>> uList = dimToUnits.get(u.getDimension());
            if(uList == null) {
                uList = new ArrayList<Unit<?>>();
                dimToUnits.put(u.getDimension(), uList);
            }
            uList.add(u);
        }
    }

    public static void printUnit(Unit<?> u) {
        p(renderUnit(u));
    }

    private static void p(Object o) {
        System.out.println(o);
    }
}
