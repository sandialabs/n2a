/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eqset;


/**
 * This tests...
 */

public class EquationAssemblerTest {
/*
    @Before
    public void setup() {
        DataModelFactory.getInstance().setDataModel(DM);
    }

    private PartX createBlankPart() {
        PartX part = new PartX(null, null, null, PartXType.COMPARTMENT, false, (Integer) null);
        List<EquationX> eqs = new ArrayList<EquationX>();
        part.setEqs(eqs);
        return part;
    }

    @Test
    public void testTrivial() {
        PartX part = createBlankPart();
        PartEquationMap map = EquationAssembler.getAssembledPartEquations(part, true);
        assertNull(map);
    }

    @Test
    public void testSinglePart() {
        Object[] expected = {
             "a", new String[] {"a = 10"},
             "b", new String[] {"b' = 10  @Test"},
        };

        PartX part = createBlankPart();
        part.getEqs().add(new EquationX("a = 10", null));
        part.getEqs().add(new EquationX("b'=10 @Test", null));

        PartEquationMap map = EquationAssembler.getAssembledPartEquations(part);
        testPartEquationMap(expected, map);
    }

    @Test
    public void testSimpleInheritance1() {
        Object[] expected = {
             "a", new String[] {"a = 20"},
             "b", new String[] {"b' = 20"},
        };

        PartX parent = createBlankPart();
        parent.getEqs().add(new EquationX("a = 10", null));
        parent.getEqs().add(new EquationX("b'=10", null));

        PartX child = createBlankPart();
        child.getEqs().add(new EquationX("a = 20", null));
        child.getEqs().add(new EquationX("b'=20", null));

        child.setParent(parent);

        PartEquationMap map = EquationAssembler.getAssembledPartEquations(child);
        testPartEquationMap(expected, map);
    }

    @Test
    public void testSimpleAnnotationInheritance1() {
        Object[] expected = {
             "a", new String[] {"a = 20"},
             "b", new String[] {"b' = 20  @Test"},
        };

        PartX parent = createBlankPart();
        parent.getEqs().add(new EquationX("a = 10", null));
        parent.getEqs().add(new EquationX("b'=10 @Test", null));

        PartX child = createBlankPart();
        child.getEqs().add(new EquationX("a = 20", null));
        child.getEqs().add(new EquationX("b'=20", null));

        child.setParent(parent);

        PartEquationMap map = EquationAssembler.getAssembledPartEquations(child);
        testPartEquationMap(expected, map);
    }

    @Test
    public void testSimpleAnnotationInheritance2() {
        Object[] expected = {
             "a", new String[] {"a = 20"},
             "b", new String[] {"b' = 20  @Test = 2"},
        };

        PartX parent = createBlankPart();
        parent.getEqs().add(new EquationX("a = 10", null));
        parent.getEqs().add(new EquationX("b'=10 @Test", null));

        PartX child = createBlankPart();
        child.getEqs().add(new EquationX("a = 20", null));
        child.getEqs().add(new EquationX("b'=20 @Test = 2", null));

        child.setParent(parent);

        PartEquationMap map = EquationAssembler.getAssembledPartEquations(child);
        testPartEquationMap(expected, map);
    }

    @Test
    public void testSimpleAnnotationInheritance3() {
        Object[] expected = {
             "a", new String[] {"a = 20"},
             "b", new String[] {"b' = 20  @Test = 2  @TestA"},
        };

        PartX parent = createBlankPart();
        parent.getEqs().add(new EquationX("a = 10", null));
        parent.getEqs().add(new EquationX("b'=10 @TestA", null));

        PartX child = createBlankPart();
        child.getEqs().add(new EquationX("a = 20", null));
        child.getEqs().add(new EquationX("b'=20 @Test = 2", null));

        child.setParent(parent);

        PartEquationMap map = EquationAssembler.getAssembledPartEquations(child);
        testPartEquationMap(expected, map);
    }

    @Test
    public void testSimpleInfiniteRecursion() {
        PartX parent = createBlankPart();
        parent.getEqs().add(new EquationX("a = 10", null));
        parent.getEqs().add(new EquationX("b'=10 @Test", null));

        PartX child = createBlankPart();
        child.getEqs().add(new EquationX("a = 20", null));
        child.getEqs().add(new EquationX("b'=20", null));

        child.setParent(parent);
        parent.setParent(child);

        try {
            EquationAssembler.getAssembledPartEquations(child);
            fail();
        } catch(DataModelLoopException dmle) {
            assertEquals("Could not assemble compartment equations.  A loop exists in the parent and/or include hierarchy:\n(target) <NEW> --> (parent) <NEW> --> (parent) <NEW>", dmle.getMessage());
            assertEquals("[(target), (parent), (parent)]", dmle.getAssembleReasons().toString());
            assertEquals(3, dmle.getParts().size());
            assertEquals(child, dmle.getParts().get(0).bean);
            assertEquals(parent, dmle.getParts().get(1).bean);
            assertEquals(child, dmle.getParts().get(2).bean);
            assertEquals(child, dmle.getTarget());
        }
    }

    // TODO: Test more complex infinite recursion?

    @Test
    public void testSimpleInclude() {
        Object[] expected = {
             "a", new String[] {"a = 10"},
             "b", new String[] {"b' = 10  @Test"},
             "A.x", new String[] {"A.x = 20"},
             "A.y", new String[] {"A.y' = 20"}
        };

        PartX part = createBlankPart();
        part.getEqs().add(new EquationX("a = 10", null));
        part.getEqs().add(new EquationX("b'=10 @Test", null));

        PartX incl = createBlankPart();
        incl.getEqs().add(new EquationX("x = 20", null));
        incl.getEqs().add(new EquationX("y'=20", null));

        part.getPartAssociations().add(new PartAssociation(PartAssociationType.INCLUDE, "A", part, incl));

        PartEquationMap map = EquationAssembler.getAssembledPartEquations(part);
        testPartEquationMap(expected, map);
    }

    @Test
    public void testComplex() {
        Object[] expected = {
            "C", new String[] {"C = 10"},
            "V", new String[] {"V' += I / C + x", "V' += I0.I / C + x", "V' += I1.I / C + x"},
            "parent", new String[] {"parent = 10"},
            "q", new String[] {"q = 10 + y"},
            "target", new String[] {"target = 10"},
            "I0.I", new String[] {"I0.I = 10"},
            "I1.E", new String[] {"I1.E = 10"},
            "I1.I", new String[] {"I1.I = I1.g * (V - I1.E)"},
            "I1.g", new String[] {"I1.g = 3 * V"}
        };

        PartX T = createBlankPart();
        PartX P = createBlankPart();
        PartX I0 = createBlankPart();
        PartX I1 = createBlankPart();
        PartX PI0 = createBlankPart();
        PartX PI1 = createBlankPart();

        T.setParent(P);
        T.getPartAssociations().add(new PartAssociation(PartAssociationType.INCLUDE, "I0", T, I0));
        T.getPartAssociations().add(new PartAssociation(PartAssociationType.INCLUDE, "I1", T, I1));
        I0.setParent(PI0);
        I1.setParent(PI1);

        EquationX E = new EquationX("V' += I / C + x", null);
        P.getEqs().add(new EquationX("parent = 10", null));
        P.getEqs().add(new EquationX("q = 10 + y", null));
        P.getEqs().add(E);
        T.getEqs().add(new EquationX("target = 10", null));
        T.getEqs().add(new EquationX("C = 10", null));
        I0.getEqs().add(new EquationX("I = 10", null));
        I1.getEqs().add(new EquationX("I = g*(V-E)", null));
        I1.getEqs().add(new EquationX("E = 10", null));
        I1.getEqs().add(new EquationX("g = 3*V", null));
        PI0.getEqs().add(E);
        PI1.getEqs().add(E);

        PartEquationMap map = EquationAssembler.getAssembledPartEquations(T);
        testPartEquationMap(expected, map);
    }


    //////////////////
    // SUPPLEMENTAL //
    //////////////////

    private void testPartEquationMap(Object[] expected, PartEquationMap map) {
        assertEquals(expected.length / 2, map.size());
        int i = 0;
        for(String key : map.keySet()) {
            assertEquals(expected[i], key);
            String[] epeqs = (String[]) expected[i + 1];
            List<ParsedEquation> peqs = map.get(key);
            assertEquals(epeqs.length, peqs.size());
            int j = 0;
            for(ParsedEquation peq : peqs) {
                assertEquals(epeqs[j], peq.toString());
                j++;
            }
            i += 2;
        }
    }


    ////////////////
    // DATA MODEL //
    ////////////////

    // An "empty" data model.

    private DataModel DM = new DataModel() {
        public void revert(BeanBase bean) throws DataModelException {}
        public <T extends BeanBase> void reconcileBeansMiddle(List<T> beans, String entity, String field, Integer fkId, String field2) throws DataModelException {}
        public <T extends BeanBase> void reconcileBeans(List<T> beans, String entity, String field, Integer fkId) throws DataModelException {}
        public void persist(BeanBase bean) throws DataModelException {}
        public <T extends BeanBase> List<T> get(Class<T> clazz, String middle, String entKeyField, String critKeyField, Integer id) throws DataModelException {
            return new ArrayList<T>();
        }
        public <T extends BeanBase> List<T> get(Class<T> clazz, Integer id, String field) throws DataModelException {
            return new ArrayList<T>();
        }
        public <T extends BeanBase> List<T> get(Class<T> clazz, Query query) throws DataModelException {
            return new ArrayList<T>();
        }
        public <T extends BeanBase> T get(Class<T> clazz, Integer id) throws DataModelException {
            return null;
        }
        public <T extends BeanBase> List<T> get(Class<T> clazz) throws DataModelException {
            return new ArrayList<T>();
        }
        public boolean exists(Class<? extends BeanBase> clazz, Integer id) throws DataModelException {
            return false;
        }
        public Integer[] deleteBeans(String entity, String field, Integer id, boolean retIds) throws DataModelException {return null;}
        public Integer[] deleteBeans(String entity, String field, Integer id) throws DataModelException {return null;}
        public void delete(List<? extends BeanBase> beans) throws DataModelException {}
        public void delete(BeanBase bean) throws DataModelException {}
    };*/
}
