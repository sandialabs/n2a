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

public class NDoc
{
    public ODocument     source;
    public RecordHandler handler;

    public NDoc ()
    {
    	source = new ODocument ();
        init ();
    }

    public NDoc (ODocument source)
    {
    	this.source = source;
        init ();
    }

    public NDoc (String className)
    {
        source = new ODocument (className);
        init ();
    }

    private void init ()
    {
        if (source.getClassName () != null)
        {
            handler = UMFPluginManager.getHandler (source.getClassName ());
        }
        if (source.getIdentity ().isNew ())
        {
            source.setIdentity (-1, new OClusterPositionLong (getNewBeanId ()));
            //System.out.println("NEW ID==>"+source.getIdentity()+","+source.toJSON());
        }
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public RecordHandler getHandler ()
    {
        return handler;
    }

    // Mutators

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
    public <T> T get (String field)
    {
        T result = (T) wrapGetValue (source.field (field));
        OrientDatasource.releaseDB ();
        return result;
    }

    // Returns default value if doesn't exist, value otherwise.
    public <T> T get (String field, Object deflt)
    {
        T result = (T) wrapGetValue (source.field (field));
        OrientDatasource.releaseDB ();
        if (result == null) result = (T) deflt;
        return result;
    }

    // Returns null if doesn't exist, or value is not cast-able to given type,
    // value otherwise.
    public <T> T getValid (String field, Class clazz)
    {
        T result = (T) wrapGetValue (source.field (field));
        OrientDatasource.releaseDB ();
        if (result != null  &&  ! clazz.isAssignableFrom (result.getClass ())) result = null;
        return result;
    }

    // Returns default value if doesn't exist or value is not cast-able
    // to given type.
    public <T> T getValid (String field, Object deflt, Class clazz)
    {
        T result = (T) wrapGetValue (source.field (field));
        OrientDatasource.releaseDB ();
        if (result != null  &&  ! clazz.isAssignableFrom (result.getClass ())) result = null;
        if (result == null) result = (T) deflt;
        return result;
    }

    // Returns default value if doesn't exist, value otherwise.  Sets
    // the return value back into the record to make sure key does
    // then exist after the call.
    public <T> T getAndSet (String field, Object deflt)
    {
        T result;
        List<String> fieldList = Arrays.asList (source.fieldNames ());
        if (fieldList.contains (field))
        {
            result = (T) wrapGetValue (source.field (field));
        }
        else
        {
            result = (T) deflt;
            set (field, result);
        }
        OrientDatasource.releaseDB ();
        return result;
    }

    // Return default value if doesn't exist or value is not cast-able to
    // given type, value otherwise.
    public <T> T getAndSetValid (String field, Object deflt, Class clazz)
    {
        List<String> fieldList = Arrays.asList (source.fieldNames ());
        T result = null;
        if (fieldList.contains (field))
        {
            result = (T) wrapGetValue (source.field (field));
            if (result != null  &&  ! clazz.isAssignableFrom (result.getClass ())) result = null;
        }
        if (result == null)
        {
            result = (T) deflt;
            set (field, result);
        }
        OrientDatasource.releaseDB ();
        return result;
    }

    /**
     * TODO: recursively check for NDoc's
     * Note: No need to release DB, because only called internally by methods that do close DB.
     */
    private <T> T wrapGetValue (Object ret)
    {
        if (ret instanceof ODocument)
        {
            return (T) new NDoc ((ODocument) ret);
        }
        else if (ret instanceof List)
        {
            List docs = new ArrayList ();
            List retList = (List) ret;
            for (Object retElem : retList) docs.add (wrapGetValue (retElem));
            return (T) docs;
        }
        return (T) ret;
    }

    public boolean has (String field)
    {
        List<String> fieldList = Arrays.asList (source.fieldNames ());
        boolean result = fieldList.contains (field);
        OrientDatasource.releaseDB ();  // TODO: Not sure if call to source.fieldNames() actually produces a DB access, so this may be overkill.
        return result;
    }

    public void set (String field, Object o)
    {
        o = unwrapSetValue (o);
        source.field (field, o);
        OrientDatasource.releaseDB ();
    }

    // TODO: recursively check for NDoc's
    // TODO - do we need to worry about caching docs here?
    private Object unwrapSetValue (Object o)
    {
        if (o instanceof NDoc)
        {
            o = ((NDoc) o).source;
        }
        else if (o instanceof List)
        {
            List docs = new ArrayList ();
            for (Object obj : (List) o)
            {
                docs.add (unwrapSetValue (obj));
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

    public void save ()
    {
        set ("$modified", System.currentTimeMillis ());
        source.save ();
        OrientDatasource.releaseDB ();
    }

    public void saveRecursive ()
    {
        Set<Object> stack = new HashSet<Object> ();
        saveRecursiveInternal (this, stack);
        OrientDatasource.releaseDB ();
    }

    private void saveRecursiveInternal (Object obj, Set<Object> stack)
    {
        // Recursion detection.
        if (stack.contains (obj)) return;
        stack.add (obj);

        try
        {

            // Recurse to children.  Would be null only if obj is a scalar.
            Map<String, Object> gMap = toGenericMap (obj);
            if (gMap != null) for (String key : gMap.keySet ()) saveRecursiveInternal (gMap.get (key), stack);
			
            // Reload from database.
            ODocument toSave = null;
            if      (obj instanceof ODocument) toSave = (ODocument) obj;
            else if (obj instanceof NDoc)      toSave = ((NDoc) obj).source;
            if (toSave != null) toSave.save ();
        }
        finally
        {
            // Recursion detection.
            stack.remove (obj);
        }
    }

    public void delete ()
    {
        source.delete ();
        OrientDatasource.releaseDB ();
    }

    // Questionable semantics... only reverts if changes are made, but this means we
    // cannot use this for version discrepancy resolution.
    public void revert ()
    {
        Set<Object> stack = new HashSet<Object> ();
        revertInternal (this, stack);
        OrientDatasource.releaseDB ();
    }

    private void revertInternal (Object obj, Set<Object> stack)
    {
        // Recursion detection.
        if (stack.contains(obj)) return;
        stack.add (obj);

        try
        {
            // Reload from database.
            ODocument toReload = null;
            if      (obj instanceof ODocument) toReload = (ODocument) obj;
            else if (obj instanceof NDoc)      toReload = ((NDoc) obj).source;
            if (toReload != null  &&  toReload.isDirty ()) toReload.reload ();

            // Recurse to children.  Would be null only if obj is a scalar.
            Map<String, Object> gMap = toGenericMap (obj);
            if (gMap != null) for (String key : gMap.keySet ()) revertInternal(gMap.get (key), stack);
        }
        finally
        {
            // Recursion detection.
            stack.remove(obj);
        }
    }

    public NDoc copy ()
    {
        ODocument newSource = source.copy ();
        newSource.setIdentity (new ORecordId ());
        NDoc result = new NDoc (newSource);
        OrientDatasource.releaseDB ();
        return result;
    }


    //////////
    // MISC //
    //////////

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
}
