/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.test;

import gov.sandia.umf.platform.connect.orientdb.expl.OrientDbExplorer;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientConnectDetails;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientDatasource;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import replete.util.FileUtil;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.OMultiValueChangeEvent;
import com.orientechnologies.orient.core.db.record.OMultiValueChangeTimeLine;
import com.orientechnologies.orient.core.db.record.OTrackedList;
import com.orientechnologies.orient.core.db.record.OTrackedSet;
import com.orientechnologies.orient.core.iterator.ORecordIteratorClass;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.ORecordListener;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;

public class OldOrientDbTest {
    /*
    {
        @id: "103481239048120429042048",
        @class: "Person",
        name: "Derek",
        state: "New Mexico",
        allPets: [{
            @id: "103481239048120429042048",
            @class: Animal,
            name: "Dodger",
        }, {
            @id: "103481239048120429042048",
            @class: Animal,
            name: "Milton"
        }],
        pet1: {
            @id: "103481239048120429042048",
            @class: "Animal",
            name: "Fido"
        },

        // Different styles for ref
        pet2: @reference("../pet1"),
        pet3: @reference("../allPets[0]")
        pet4: @reference(ID("103481239048120429042048")),
        pet5: @reference("Anima", ID("103481239048120429042048")),
        pet6_id: "103481239048120429042048"
    }
    */
    public static final String LOC = "local:C:/Users/dtrumbo/Desktop/orient/testabcg";
    public static void addClass(ODatabaseDocumentTx db, String name) {
        if(!db.getMetadata().getSchema().existsClass(name)) {
            db.getMetadata().getSchema().createClass(name);
        }
    }
    public static void removeClass(ODatabaseDocumentTx db, String name) {
        db.getMetadata().getSchema().dropClass(name);
    }
    public static void addDocument(ODatabaseDocumentTx db, String name, String json) {
        addClass(db, name);
        ODocument doc = db.newInstance(name).fromJSON(json);
        doc.save();
    }
    public static void addDocument(ODatabaseDocumentTx db, String name, File file) {
        addClass(db, name);
        ODocument doc = db.newInstance(name).fromJSON(FileUtil.getTextContent(file));
        doc.save();
    }
    public static long countDocuments(ODatabaseDocumentTx db, String name) {
        OClass cls = db.getMetadata().getSchema().getClass(name);
        return cls.count();
    }

    public static void main(String[] args) {
//        obj();if(true) {
//            return;
//        }
        // OPEN THE DATABASE
        OrientConnectDetails cxn = new OrientConnectDetails(LOC);
        OrientDatasource source = new OrientDatasource(cxn);
        ODatabaseDocumentTx db = source.getDb();

        // Works

        ODocument planet0 = db.newInstance("Planet");        planet0.field("Name", "Jupiter");
        ODocument planet1 = db.newInstance("Planet");        planet1.field("Name", "Saturn");
        ODocument planet2 = db.newInstance("Planet");        planet2.field("Name", "Mars");
        planet0.save();
        planet1.save();
        planet2.save();

        ODocument system0 = db.newInstance("System");
        system0.field("Name", "Sol");
        system0.field("LargestPlanet", planet0);
        List<ODocument> pls = new ArrayList<ODocument>();
        pls.add(planet0); pls.add(planet1); pls.add(planet2);
        List x = new ArrayList();
        x.add(pls);
        Map y = new HashMap();
        y.put("Q", x);
        List z = new ArrayList();
        z.add(y);
        Map w = new HashMap();
        w.put("Q", z);
        system0.field("AllPlanets", w);

        system0.save();

        // Doesn't Work

        ODocument mouse = db.newInstance("Mouse");
        mouse.field("Name", "Mickey3");
        mouse.save();

        ODocument dog = db.newInstance("Dog");
        dog.field("Name", "Fido");
        dog.field("Age", 7);
        List list = new ArrayList();
        Map m1 = new LinkedHashMap();
        m1.put("value", "e = m * c ^ 2");
        List reflist = new ArrayList();
        Map r1 = new LinkedHashMap();
        r1.put("author", "Tony");
        r1.put("ISBN", "1231312");
//        r1.put("mouse", mouse);
        reflist.add(r1);
        reflist.add(mouse);
        m1.put("refs", reflist);
        list.add(m1);
        Map m2 = new LinkedHashMap();
        m2.put("value", "e = m * c ^ 2");
        List reflist2 = new ArrayList();
        Map r2 = new LinkedHashMap();
        r2.put("author", "Marcus");
        r2.put("ISBN", "ABAKLSD");
        r2.put("mouse", mouse);
        reflist2.add(r2);
        m2.put("refs", reflist2);
        list.add(m2);
        dog.field("eqs", list);

        addClass(db, "gov.sandia.umf.platform$Admin");
        removeClass(db, "System");
        addDocument(db, "Platform2", "{'x': 'what', 'y': 123.3}");
        //addDocument(db, "Hello", new File("C:\\Users\\dtrumbo\\work\\eclipse-main\\UMFPlatform\\src\\gov\\sandia\\umf\\platform\\connect\\orientdb\\input.txt"));
        System.out.println(countDocuments(db, "Platform2"));

//        ODocument cat = db.newInstance("Cat");
//        cat.field("Name", "Fluffy");
//        cat.field("Weight", 123);
//        cat.field("Friend", dog);

//        mouse.save();
        dog.save();
//        cat.save();

        source.disconnect();

        OrientDbExplorer.main(new String[] {LOC});

        if(true) {
            return;
        }

        // CREATE A NEW DOCUMENT AND FILL IT
        ODocument doc = new ODocument("gov.sandia.n2a#Person");
        doc.field( "name", "Derek" );
        doc.field( "surname", "Skywalker" );
        Map<String, Integer> map = new HashMap<String, Integer>();
        map.put("Colorado", 33);
        map.put("Ohio", 17);
        map.put("California", 55);
        map.put("Hawaii", 4);
        doc.field("javaMap", map);
        List<Boolean> lst = new ArrayList<Boolean>();
        lst.add(true);
        lst.add(true);
        lst.add(true);
        lst.add(false);
        lst.add(false);
        lst.add(true);
        lst.add(false);
        doc.field("javaList", lst);
        String[] arr = new String[] {"Mountain", "River", "Lake", "Gorge"};
        doc.field("javaArray", arr);
        Set<Float> set = new LinkedHashSet<Float>();
        set.add(12.3F);
        set.add(-45.3F);
        set.add(0.03F);
        doc.field("javaSet", set);
        OTrackedSet<Double> ddd = new OTrackedSet<Double>(doc);
        ddd.add(Math.PI);
        ddd.add(Math.PI);
        doc.field("oset", ddd);
        OTrackedList<Double> xxx = new OTrackedList<Double>(doc);
        xxx.add(Math.PI);
        xxx.add(Math.PI);
        doc.field("olist", xxx);
//        doc.field( "city", new ODocument("City").field("name","Rome").field("country", "Italy") );
        // SAVE THE DOCUMENT
        doc.save();
        db.close();
//        DatasourceExperimental d = new PluginSpecificDatasource("gov.sandia.n2a");
//        System.out.println(d.getEntities());
//        Application.setName("A");
//        Application.setVersion("A");
//        System.out.println(XStreamWrapper.writeToString(new Person("Derek", "New Mexico")));
    }
    public class Animal {
        public String name;
        public Animal() {

        }
        public Animal(String n) {
            name = n;
        }
    }
    public class Person {
        public String name;
        public String state;
        public Animal pet1 = new Animal("Fido");
        public Animal pet2 = pet1;
        public Person() {

        }
        public Person(String n, String s) {
            name = n;
            state = s;
        }
    }
    public static void obj() {
        // OPEN THE DATABASE
        /*OObjectDatabaseTx db = new OObjectDatabaseTx(LOC);
        if(!db.exists()) {
            db.create();
        } else {
            db.open("admin", "admin");
        }

//        db.getEntityManager().registerEntityClasses("gov.sandia.umf.platform.connect.orientdb.ent");
        db.getEntityManager().registerEntityClass(Person.class);
        db.getEntityManager().registerEntityClass(Animal.class);
        Person person = db.newInstance(Person.class);
        person.name = "asdf";
        person.state= "asdfasf";
        db.save(person);

*/
/*
        // CREATE A NEW DOCUMENT AND FILL IT
        ODocument doc = new ODocument("Person");
        doc.field( "name", "Luke" );
        doc.field( "surname", "Skywalker" );
        doc.field( "city", new ODocument("City").field("name","Rome").field("country", "Italy") );
        // SAVE THE DOCUMENT
        doc.save();
        db.close();*/
//        db
        OrientDbExplorer.main(null);
    }

    public static void mainx(String[] args) throws IllegalArgumentException, IllegalAccessException {

        String dbpath = "local:/Users/jhgjk/Desktop/testdbs/one";
        OrientDatasource source = new OrientDatasource(new OrientConnectDetails(dbpath));
        ODatabaseDocumentTx db = source.getDb();

        System.out.println(db.getClusterNames());

        ORecordIteratorClass<ODocument> iter = db.browseClass("Appliance");
        for(ODocument d : iter) {
//          d.field("name", d.field("name") + "XYZ");
            System.out.println(d);
            System.out.println("    " + d.fields());
            System.out.println("    " + d.getClassName());
            System.out.println("    " + d.getDataSegmentName());
//          System.out.println("    " + d.getSerializationId());   // Orient DB v1.2.0 only
            System.out.println("    " + d.getSize());
            System.out.println("    " + d.getVersion());
            System.out.println("    " + Arrays.toString(d.getDirtyFields()));
            System.out.println("    " + d.getIdentity());
            System.out.println("    " + d.getInternalStatus());
            System.out.println("    " + d.getOwners());
            System.out.println("    " + d.getDatabase().getClusters());
            System.out.println("    " + d.getRecordType());
            System.out.println("    " + d.getSchemaClass());
            System.out.println("    " + d.toJSON());    //Type is 'd' for documents, 'b' for binaries, 'f' for flat. what you are looking for is the @class that is 'OGraphVertex' for vertices and 'OGraphEdge' for edges.
            System.out.println("    " + d.toString());
            System.out.println("    " + d.isDirty());
            System.out.println("    " + d.isEmbedded());
            System.out.println("    " + d.isEmpty());
            System.out.println("    " + d.isLazyLoad());
            System.out.println("    " + d.isOrdered());
            System.out.println("    " + d.isTrackingChanges());
            System.out.println("    " + d.isPinned());
            System.out.println("    " + Arrays.toString(d.fieldNames()));
            for(String fname : d.fieldNames()) {
                Object v = d.field(fname);
                System.out.println("       " + fname + " = " + v + " (" + v.getClass().getName() + ")");
            }
            d.addListener(new ORecordListener() {

                @Override
                public void onEvent(ORecord<?> arg0, EVENT arg1) {
                    System.out.println("EVENT="+arg1);
                }
            });
            OMultiValueChangeTimeLine<String, Object> tl = d.getCollectionTimeLine("name");
            if(tl != null) {
            List<OMultiValueChangeEvent<String, Object>> tlevs = tl.getMultiValueChangeEvents();
            if(tlevs != null) {
            for(OMultiValueChangeEvent<String, Object> tlev : tlevs) {
                System.out.println("* " + tlev.getKey());
                System.out.println("* " + tlev.getChangeType());
                System.out.println("* " + tlev.getOldValue());
                System.out.println("* " + tlev.getValue());
            }
            }
            }
//          d.field("width", 999);
//          d.save();
        }

        source.disconnect();

        /*
        {
            "@type": "d", "@rid": "#19:0", "@version": 0, "@class": "Appliance",
            "name": "TV",
            "width": 12
        }
        */


//      ODocument doc = new ODocument("Book");
//      doc.field("author", "Me");
//      doc.save();

//      ODatabaseFlat dbf = new ODatabaseFlat(dbpath);
//        if(dbf.exists()) {
//            dbf.open("admin", "admin");
//        } else {
//            dbf.create();
//        }
//      ORecordFlat flat = new ORecordFlat();
//      flat.value("hello");
//      flat.save();
//
//      System.out.println(dbf.getClusterNames());
//
//      dbf.close();

        if(true) {
            return;
        }


        Product product = new Product("Banana", "123", 23.00);
        XStream xstream = new XStream(new JettisonMappedXmlDriver());
        //xstream.setMode(XStream.NO_REFERENCES);
        xstream.alias("product", Product.class);

        System.out.println(xstream.toXML(product));

//        ODatabaseObjectTx db = new ODatabaseObjectTx(dbpath);
//        if(db.exists()) {
//            db.open("admin", "admin");
//        } else {
//            db.create();
//        }
//        System.out.println(db.getClusterNames());

     // OPEN THE DATABASE
  /*      OObjectDatabaseTx db2 = new OObjectDatabaseTx (dbpath).open("admin", "admin");

        // REGISTER THE CLASS ONLY ONCE AFTER THE DB IS OPEN/CREATED
        db2.getEntityManager().registerEntityClasses("cc.xstream");
        */
/*
        // CREATE A NEW PROXIED OBJECT AND FILL IT
        Account account = db2.newInstance(Account.class);
        account.setName( "Luke" );
        account.setSurname( "Skywalker" );
        db2.save( account );
        DebugUtil.printObjectDetails(account);
        Class<?> clazz = account.getClass();
        Field[] fields = clazz.getDeclaredFields();
        for(Field f : fields) {
            System.out.println(f.getName() + " " + f.getType());
            if(f.getName().equals("_methods_")) {
                f.setAccessible(true);
                Method[] methods = (Method[]) f.get(account);
                System.out.println(methods.length);
                for(Method m : methods) {
                    if(m != null) {
                        System.out.println("--" + m.getName());
                    }
                }
            }
        }*/
/*
        City rome =  db.newInstance(City.class,"Rome",  db.newInstance(Country.class,"Italy"));
        account.getAddresses().add(new Address("Residence", rome, "Piazza Navona, 1"));


        // CREATE A NEW OBJECT AND FILL IT
        Account account = new Account();
        account.setName( "Luke" );
        account.setSurname( "Skywalker" );

        City rome = new City("Rome", new Country("Italy"));
        account.getAddresses().add(new Address("Residence", rome, "Piazza Navona, 1"));

        // SAVE THE ACCOUNT: THE DATABASE WILL SERIALIZE THE OBJECT AND GIVE THE PROXIED INSTANCE
        account = db.save( account );*/
    }

    public static class Product {
        public String name;
        public String iii;
        public double price;
        public Client client = new Client("Derek", "1343-342asf");
        public Client client2 = client;
        public Product(String n, String i, double d) {
            name = n;
            iii = i;
            price = d;
        }
    }
    public static class Client {
        public String name;
        public String acct;
        public Client(String n, String a) {
            name = n;
            acct = a;
        }
    }
}
