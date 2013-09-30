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

public class MetadataAssemblerTest {
/*
    @Before
    public void setup() {
        DataModelFactory.getInstance().setDataModel(DM);
    }

    private PartX createBlankPart() {
        PartX part = new PartX(null, null, null, PartXType.COMPARTMENT, false, (Integer) null);
        return part;
    }

    @Test
    public void testTrivial() {
        PartX part = createBlankPart();
        PartMetadataMap metadata = MetadataAssembler.getAssembledPartMetadata(part);
        assertEquals(0, metadata.size());
    }

    @Test
    public void testSinglePart() {
        PartMetadataMap expected = new PartMetadataMap();
        expected.put("saturn", "big");
        expected.put("pluto", "small");

        PartX part = createBlankPart();
        part.getTerms().add(new TermValue("big", new Term("saturn", null, null, new Terminology(null, null))));
        part.getTerms().add(new TermValue("small", new Term("pluto", null, null, new Terminology(null, null))));

        PartMetadataMap metadata = MetadataAssembler.getAssembledPartMetadata(part);
        assertEquals(expected, metadata);
    }

    @Test
    public void testSimpleInheritance1() {
        PartMetadataMap expected = new PartMetadataMap();
        expected.put("jupiter", "big");
        expected.put("saturn", "medium");
        expected.put("pluto", "small");

        PartX parent = createBlankPart();
        parent.getTerms().add(new TermValue("big", new Term("saturn", null, null, new Terminology(null, null))));
        parent.getTerms().add(new TermValue("small", new Term("pluto", null, null, new Terminology(null, null))));

        PartX child = createBlankPart();
        child.getTerms().add(new TermValue("big", new Term("jupiter", null, null, new Terminology(null, null))));
        child.getTerms().add(new TermValue("medium", new Term("saturn", null, null, new Terminology(null, null))));

        child.setParent(parent);

        PartMetadataMap metadata = MetadataAssembler.getAssembledPartMetadata(child);
        assertEquals(expected, metadata);
    }

    @Test
    public void testSimpleInheritance2() {
        PartMetadataMap expected = new PartMetadataMap();
        expected.put("venus", "hot");
        expected.put("mars", "red");
        expected.put("earth", "green");
        expected.put("pluto", "tiny");
        expected.put("saturn", "medium");
        expected.put("jupiter", "big");

        PartX gparent = createBlankPart();
        gparent.getTerms().add(new TermValue("big", new Term("pluto", null, null, new Terminology(null, null))));
        gparent.getTerms().add(new TermValue("hot", new Term("venus", null, null, new Terminology(null, null))));

        PartX parent = createBlankPart();
        parent.getTerms().add(new TermValue("big", new Term("saturn", null, null, new Terminology(null, null))));
        parent.getTerms().add(new TermValue("small", new Term("pluto", null, null, new Terminology(null, null))));
        parent.getTerms().add(new TermValue("red", new Term("mars", null, null, new Terminology(null, null))));

        parent.setParent(gparent);

        PartX child = createBlankPart();
        child.getTerms().add(new TermValue("big", new Term("jupiter", null, null, new Terminology(null, null))));
        child.getTerms().add(new TermValue("medium", new Term("saturn", null, null, new Terminology(null, null))));
        child.getTerms().add(new TermValue("green", new Term("earth", null, null, new Terminology(null, null))));
        child.getTerms().add(new TermValue("tiny", new Term("pluto", null, null, new Terminology(null, null))));

        child.setParent(parent);

        PartMetadataMap metadata = MetadataAssembler.getAssembledPartMetadata(child);
        assertEquals(expected, metadata);
    }

    @Test
    public void testSimpleInfiniteRecursion() {

        PartX parent = createBlankPart();
        parent.getTerms().add(new TermValue("big", new Term("saturn", null, null, new Terminology(null, null))));
        parent.getTerms().add(new TermValue("small", new Term("pluto", null, null, new Terminology(null, null))));

        PartX child = createBlankPart();
        child.getTerms().add(new TermValue("green", new Term("earth", null, null, new Terminology(null, null))));
        child.getTerms().add(new TermValue("tiny", new Term("pluto", null, null, new Terminology(null, null))));

        child.setParent(parent);
        parent.setParent(child);

        try {
            MetadataAssembler.getAssembledPartMetadata(child, true);
            fail();
        } catch(DataModelLoopException dmle) {
            assertEquals("A loop exists in the parent and/or include hierarchy:\n(target) <NEW> --> (parent) <NEW> --> (parent) <NEW>", dmle.getMessage());
            assertEquals("[(target), (parent), (parent)]", dmle.getAssembleReasons().toString());
            assertEquals(3, dmle.getParts().size());
            assertEquals(child, dmle.getParts().get(0).bean);
            assertEquals(parent, dmle.getParts().get(1).bean);
            assertEquals(child, dmle.getParts().get(2).bean);
            assertEquals(child, dmle.getTarget());
        }
    }

    @Test
    public void testLongerInfiniteRecursion() {

        PartX ggparent = createBlankPart();
        ggparent.getTerms().add(new TermValue("sea", new Term("neptune", null, null, new Terminology(null, null))));
        ggparent.getTerms().add(new TermValue("nearsun", new Term("mercury", null, null, new Terminology(null, null))));

        PartX gparent = createBlankPart();
        gparent.getTerms().add(new TermValue("big", new Term("pluto", null, null, new Terminology(null, null))));
        gparent.getTerms().add(new TermValue("hot", new Term("venus", null, null, new Terminology(null, null))));

        PartX parent = createBlankPart();
        parent.getTerms().add(new TermValue("big", new Term("saturn", null, null, new Terminology(null, null))));
        parent.getTerms().add(new TermValue("small", new Term("pluto", null, null, new Terminology(null, null))));

        PartX child = createBlankPart();
        child.getTerms().add(new TermValue("green", new Term("earth", null, null, new Terminology(null, null))));
        child.getTerms().add(new TermValue("tiny", new Term("pluto", null, null, new Terminology(null, null))));

        child.setParent(parent);
        parent.setParent(gparent);
        gparent.setParent(ggparent);
        ggparent.setParent(child);

        try {
            MetadataAssembler.getAssembledPartMetadata(child);
            fail();
        } catch(DataModelLoopException dmle) {
            assertEquals("Could not assemble compartment metadata.  A loop exists in the parent and/or include hierarchy:\n(target) <NEW> --> (parent) <NEW> --> (parent) <NEW> --> (parent) <NEW> --> (parent) <NEW>", dmle.getMessage());
            assertEquals("[(target), (parent), (parent), (parent), (parent)]", dmle.getAssembleReasons().toString());
            assertEquals(5, dmle.getParts().size());
            assertEquals(child, dmle.getParts().get(0).bean);
            assertEquals(parent, dmle.getParts().get(1).bean);
            assertEquals(gparent, dmle.getParts().get(2).bean);
            assertEquals(ggparent, dmle.getParts().get(3).bean);
            assertEquals(child, dmle.getParts().get(4).bean);
            assertEquals(child, dmle.getTarget());
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
    };
    */
}
