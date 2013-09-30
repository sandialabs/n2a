/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;

import gov.sandia.umf.platform.plugins.UMFPluginManager;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.ImageIcon;

import replete.util.StringUtil;

import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.OClusterPositionLong;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class NDoc {


    ////////////
    // FIELDS //
    ////////////

    private ODocument source;
    private RecordHandler handler;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NDoc() {
        initSource(null, null);
    }
    public NDoc(ODocument source) {
        initSource(null, source);
    }
    public NDoc(String className) {
        this(className, (String) null);
    }
    public NDoc(String className, String seed) {  // e.g. className = "gov.sandia.n2a$Part", seed = "{name='HH', type='COMPARTMENT'}"
        initSource(className, null);
        if(seed != null) {
            source.fromJSON(seed);
        }
    }
    public NDoc(String className, Map<String, Object> seedMap) {
        initSource(className, null);
        for(String s : seedMap.keySet()) {
            source.field(s, seedMap.get(s));
        }
    }

    private void initSource(String className, ODocument thisSrc) {
        checkDbThread();
        if(thisSrc != null) {
            source = thisSrc;
        } else {
            if(className == null) {
                source = new ODocument();
            } else {
                source = new ODocument(className);
            }
        }
        if(source.getClassName() == null) {
            handler = null;
        } else {
            handler = UMFPluginManager.getHandler(source.getClassName());
        }
        if(source.getIdentity().isNew()) {
            source.setIdentity(-1, new OClusterPositionLong(getNewBeanId()));
            //System.out.println("NEW ID==>"+source.getIdentity()+","+source.toJSON());
        }
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    //// Primary ////

    // Accessors

    public ODocument getSource() {
        return source;
    }
    public RecordHandler getHandler() {
        return handler;
    }

    // Mutators

    public void setSource(ODocument doc) {
        initSource(null, doc);   // So this same NDoc can be used for various
    }
    public void setHandler(RecordHandler hndlr) {
        handler = hndlr;
    }

    //// Fields ////

    // There are 6 different methods for getting fields:
    // * These will return whatever value is in the record,
    //   or default value if no field exists.
    //   - get(String)
    //   - get(String, Object)
    //   - getAndSet(String, Object)
    // * These only return non-null/non-default value if field
    //   is of the correct type.  Examples:
    //      String.class, Boolean.class, Integer.class, Number.class,
    //      List.class, Set.class, ODocument.class, NDoc.class
    //   - getValid(String, Class)
    //   - getValid(String, Object, Class)
    //   - getAndSetValid(String, Object, Class)

    // Returns null if doesn't exist, value corresponding to key if does.
    public <T> T get(String field) {
        checkDbThread();
        return (T) wrapGetValue(source.field(field));
    }

    // Returns null if doesn't exist, or value is not cast-able to given type,
    // value otherwise.
    public <T> T getValid(String field, Class clazz) {
        checkDbThread();
        T ret = (T) wrapGetValue(source.field(field));
        if(ret == null || clazz.isAssignableFrom(ret.getClass())) {
            return ret;
        }
        return null;
    }

    // Returns default value if doesn't exist, value otherwise.
    public <T> T get(String field, Object deflt) {
        checkDbThread();
        List<String> fieldList = Arrays.asList(source.fieldNames());
        if(!fieldList.contains(field)) {
            return (T) deflt;
        }
        return (T) wrapGetValue(source.field(field));
    }

    // Returns default value if doesn't exist or value is not cast-able
    // to given type.
    public <T> T getValid(String field, Object deflt, Class clazz) {
        checkDbThread();
        List<String> fieldList = Arrays.asList(source.fieldNames());
        if(!fieldList.contains(field)) {
            return (T) deflt;
        }
        T ret = (T) wrapGetValue(source.field(field));
        if(ret == null || clazz.isAssignableFrom(ret.getClass())) {
            return ret;
        }
        return (T) deflt;
    }

    // Returns default value if doesn't exist, value otherwise.  Sets
    // the return value back into the record to make sure key does
    // then exist after the call.
    public <T> T getAndSet(String field, Object deflt) {
        checkDbThread();
        List<String> fieldList = Arrays.asList(source.fieldNames());
        T setToThis = null;
        try {
            if(!fieldList.contains(field)) {
                return setToThis = (T) deflt;
            }
            return setToThis = (T) wrapGetValue(source.field(field));
        } finally {
            set(field, setToThis);
        }
    }

    // Return default value if doesn't exist or value is not cast-able to
    // given type, value otherwise.
    public <T> T getAndSetValid(String field, Object deflt, Class clazz) {
        checkDbThread();
        List<String> fieldList = Arrays.asList(source.fieldNames());
        T setToThis = null;
        try {
            if(!fieldList.contains(field)) {
                return setToThis = (T) deflt;
            }
            T ret = (T) wrapGetValue(source.field(field));
            if(ret == null || clazz.isAssignableFrom(ret.getClass())) {
                return setToThis = ret;
            }
            return setToThis = (T) deflt;
        } finally {
            set(field, setToThis);
        }
    }

    // TODO: recursively check for NDoc's
    private <T> T wrapGetValue(Object ret) {
        if(ret instanceof ODocument) {
            return (T) new NDoc((ODocument) ret);
        } else if(ret instanceof List) {
            List docs = new ArrayList();
            List retList = (List) ret;
            for(Object retElem : retList) {
                docs.add(wrapGetValue(retElem));
            }
            return (T) docs;
        }
        return (T) ret;
    }

    public boolean has(String field) {
        checkDbThread();
        List<String> fieldList = Arrays.asList(source.fieldNames());
        return fieldList.contains(field);
    }

    public void set(String field, Object o) {
        checkDbThread();
        o = unwrapSetValue(o);
        source.field(field, o);
    }

    // TODO: recursively check for NDoc's
    // TODO - do we need to worry about caching docs here?
    private Object unwrapSetValue(Object o) {
        if(o instanceof NDoc) {
            o = ((NDoc) o).source;
        } else if(o instanceof List) {
            List docs = new ArrayList();
            for(Object obj : (List) o) {
                docs.add(unwrapSetValue(obj));
            }
            o = docs;
        }
        return o;
    }

    // Source-Backed Convenience Methods

    public String getId() {
        return source.getIdentity().toString();
    }
    public int getVersion() {
        return source.getVersion();
    }

    public String getClassName() {
        return source.getClassName();
    }

    public boolean isDirty() {
        return source.isDirty();
    }
    public boolean isNew() {
        return source.getIdentity().isNew();
    }
    public boolean isPersisted() {
        return !isNew();
    }

    public String getOwner() {
        return get("$owner");
    }
    public void setOwner(String owner) {
        set("$owner", owner);
    }

    public Long getModified() {
        return get("$modified", null);
    }

    public Map<String, Object> getMetadata() {
        return getAndSetValid("$metadata", new HashMap<String, Object>(), Map.class);
    }
    public Object getMetadata(String key) {
        Map<String, Object> o = getAndSetValid("$metadata", new HashMap<String, Object>(), Map.class);
        return o.get(key);
    }
    public void setMetadata(String key, Object value) {
        Map<String, Object> o = getAndSet("$metadata", new HashMap<String, Object>());
        o.put(key, value);
    }

    // Handler-delegated (a couple of convenience methods so you
    // don't have to look up the record handler yourself to get
    // this particular information).

    public String getTitle() {
        if(handler == null) {
            return toString();
        }
        return handler.getTitle(this);
    }

    public ImageIcon getIcon() {
        if(handler == null) {
            return null;
        }
        return handler.getIcon(this);
    }


    //////////
    // BEAN //
    //////////

    public void save() {
        checkDbThread();
        set("$modified", System.currentTimeMillis());
        source.save();
    }

    public void saveRecursive() {
        checkDbThread();
        saveRecursiveInternal(this);
    }

    // Recursively reload this document and child documents from the database.
    private void saveRecursiveInternal(Object obj) {
        Set<Object> stack = new HashSet<Object>();
        saveRecursiveInternal(obj, stack);
    }

    private void saveRecursiveInternal(Object obj, Set<Object> stack) {

        // Recursion detection.
        if(stack.contains(obj)) {
            return;
        }
        stack.add(obj);

        try {

            // Reload from database.
            if(obj instanceof ODocument || obj instanceof NDoc) {
                ODocument doc = obj instanceof ODocument ?
                    (ODocument) obj : ((NDoc) obj).source;
                doc.save();
            }

            // Recurse to children.  Would be null only if obj is a scalar.
            Map<String, Object> gMap = toGenericMap(obj);
            if(gMap != null) {
                for(String key : gMap.keySet()) {
                    Object value = gMap.get(key);
                    saveRecursiveInternal(value, stack);
                }
            }

        } finally {

            // Recursion detection.
            stack.remove(obj);
        }
    }

    public void delete() {
        checkDbThread();
        source.delete();
    }

    // Questionable semantics... only reverts if changes are made, but this means we
    // cannot use this for version discrepancy resolution.
    public void revert() {
        checkDbThread();
        revertInternal(this);
    }

    // Recursively reload this document and child documents from the database.
    private void revertInternal(Object obj) {
        Set<Object> stack = new HashSet<Object>();
        revertInternal(obj, stack);
    }

    private void revertInternal(Object obj, Set<Object> stack) {

        // Recursion detection.
        if(stack.contains(obj)) {
            return;
        }
        stack.add(obj);

        try {

            // Reload from database.
            if(obj instanceof ODocument || obj instanceof NDoc) {
                ODocument doc = obj instanceof ODocument ?
                    (ODocument) obj : ((NDoc) obj).source;
                if(doc.isDirty()) {
                    doc.reload();
                }
            }

            // Recurse to children.  Would be null only if obj is a scalar.
            Map<String, Object> gMap = toGenericMap(obj);
            if(gMap != null) {
                for(String key : gMap.keySet()) {
                    Object value = gMap.get(key);
                    revertInternal(value, stack);
                }
            }

        } finally {

            // Recursion detection.
            stack.remove(obj);
        }
    }

    public NDoc copy() {
        checkDbThread();
        ODocument newSource = source.copy();
        newSource.setIdentity(new ORecordId());
        return new NDoc(newSource);
    }


    //////////
    // MISC //
    //////////

    private void checkDbThread() {
        ConnectionManager dmm = ConnectionManager.getInstance();
        OrientDatasource ds = dmm.getDataModel();
        ODatabaseDocumentTx db = ds.getDb();
        ODatabaseRecordThreadLocal.INSTANCE.set(db);
    }

    public void dump() {
        dump(null);
    }
    public void dump(String label) {
        Set<Object> stack = new HashSet<Object>();
        dump(source, label, 0, 7, stack);
    }
    private void dump(Object obj, String label, int level, int maxLevel, Set<Object> stack) {
        System.out.print(StringUtil.spaces(level * 3) + " - ");

        if(label != null) {
            System.out.print(label + " = ");
        }

        // Recursion detection.
        if(stack.contains(obj)) {
            if(obj instanceof ODocument) {
                System.out.println(obj.toString());
                System.out.print(StringUtil.spaces((level + 1) * 3) + " - ");
            }
            System.out.println("<WOULD RECURSE>");
            return;
        }

        // Maximum level.
        if(level == maxLevel) {
            System.out.println("<MAX LEVEL REACHED>");
            return;
        }

        // Recursion detection.
        if(obj instanceof ODocument || obj instanceof List || obj instanceof Map) {
            stack.add(obj);
        }

        try {

            // Print out strings.
            if(obj instanceof NDoc) {
                System.out.println(((NDoc) obj).source);
            } else if(obj instanceof ODocument) {
                System.out.println(obj);
            } else if(obj instanceof List) {
                System.out.println("[LIST #" + ((List) obj).size() + "]");
            } else if(obj instanceof Map) {
                System.out.println("{MAP #" + ((Map) obj).size() + "}");
            } else {
                System.out.println(obj);
            }

            // Recurse to children.  Would be null only if obj is a scalar.
            Map<String, Object> gMap = toGenericMap(obj);
            if(gMap != null) {
                for(String key : gMap.keySet()) {
                    Object value = gMap.get(key);
                    dump(value, key, level + 1, maxLevel, stack);
                }
            }

        } finally {

            // Recursion detection.
            if(obj instanceof ODocument || obj instanceof List || obj instanceof Map) {
                stack.remove(obj);
            }
        }
    }

    private Map<String, Object> toGenericMap(Object obj) {
        Map<String, Object> gMap = new LinkedHashMap<String, Object>();

        if(obj instanceof NDoc) {
            ODocument record = ((NDoc) obj).source;
            for(String fieldName : record.fieldNames()) {
                Object value = record.field(fieldName);
                gMap.put(fieldName, value);
            }

        } else if(obj instanceof ODocument) {
            ODocument record = (ODocument) obj;
            for(String fieldName : record.fieldNames()) {
                Object value = record.field(fieldName);
                gMap.put(fieldName, value);
            }

        } else if(obj instanceof List) {
            List list = (List) obj;
            int i = 0;
            for(Object value : list) {
                gMap.put("" + i++, value);
            }

        } else if(obj instanceof Map) {
            Map map = (Map) obj;
            for(Object key : map.keySet()) {
                Object value = map.get(key);
                gMap.put("" + key, value);
            }

        } else {
            return null;  // Non-map-type object (scalar).
        }

        return gMap;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public String toString() {
        if(handler != null && handler.providesToString(this)) {
            return handler.getToString(this);
        }
        return source.toString();
    }

    // Temporary
    public void ts() {
        System.out.println(this);
    }


    ///////////
    // DEBUG //
    ///////////

    public void dumpDebug(String label) {
        System.out.println("Debugging '" + label + "': ");
        System.out.println(" -- NDOC HC: " + hashCode());
        System.out.println(" -- NDOC TS: " + StringUtil.max(toString(), 60));
        System.out.println(" -- ODOC HC: " + source.hashCode());
        System.out.println(" -- ODOC TS: " + StringUtil.max(source.toString(), 60));
        System.out.println(" -- ODOC/NDOC ID: " + source.getIdentity());
        System.out.println(" -- ODOC VERS: " + source.getVersion());
        System.out.println(" -- ODOC isDirty: " + source.isDirty());
        System.out.println(" -- ODOC isEmbedded: " + source.isEmbedded());
        System.out.println(" -- ODOC isEmpty: " + source.isEmpty());
        System.out.println(" -- ODOC isLazyLoad: " + source.isLazyLoad());
        System.out.println(" -- ODOC isOrdered: " + source.isOrdered());
        System.out.println(" -- ODOC isPinned: " + source.isPinned());
        System.out.println(" -- ODOC isTrackingChanges: " + source.isTrackingChanges());
        System.out.println(" -- ODOC status: " + source.getInternalStatus());
    }


    ////////////
    // NEW ID //
    ////////////

    // A single, global, threadsafe, negative counter for all new beans of any type.

    private static Integer nextNewId = 0;
    public static synchronized Integer getNewBeanId() {
        nextNewId--;
        return nextNewId;
    }

    // Recording which temp IDs have been replaced with real IDs.
    private static Map<Integer, Integer> tempIdMap = new HashMap<Integer, Integer>();
    public static synchronized Integer getSavedIdForTempId(Integer tempId) {
        return tempIdMap.get(tempId);
    }
    public static synchronized void setSavedIdForTempId(Integer tempId, Integer savedId) {
        tempIdMap.put(tempId, savedId);
    }


    //////////////////////////
    // STATIC RECORD LOOKUP //
    //////////////////////////

    // Field

    private static NDocDataModel model;

    // Accessor/Mutator

    public static NDocDataModel getDataModel() {
        return model;
    }
    public static void setDataModel(NDocDataModel mdl) {
        model = mdl;
    }

    // Look up

    public static NDoc getById(String className, String id) {
        return getDataModel().getById(className, id);
    }
    public static List<NDoc> getAll(String className) {
        return getDataModel().getAll(className);
    }
    public static List<NDoc> getByQuery(String className, String query) {
        return getDataModel().getByQuery(className, query);
    }
}
