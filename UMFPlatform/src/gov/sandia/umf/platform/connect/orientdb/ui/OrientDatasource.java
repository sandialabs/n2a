/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;

import gov.sandia.umf.platform.plugins.UMFPluginManager;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import replete.util.FileUtil;
import replete.util.StringUtil;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.iterator.ORecordIteratorClass;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

public class OrientDatasource implements NDocDataModel {

    private static final String[] systemClasses = new String[] {
        "ORestricted",
        "ORIDs",
        "OIdentity",
        "OUser",
        "ORole",
        "OFunction",
        "ODocumentWrapper",
        "OGraphEdge",       // System classes in relation to
        "OGraphVertex"      // a document database at least.
    };
    private static final String ADMIN_CLASS = "gov.sandia.umf.platform$Admin";

    private ODatabaseDocumentTx db;
    private OrientConnectDetails details;
    private Map<String, ODocument> oDocCache = new HashMap<String, ODocument>();
    private Map<String, NDoc> nDocCache = new HashMap<String, NDoc>();

    public OrientDatasource(OrientConnectDetails deets) {
        details = deets;
        connect();
    }

    public void deleteAllRecords(String className) {
        if(existsClass(className)) {
            for(Object doc : db.browseClass(className)) {
                db.delete((ODocument) doc);
            }
        }
    }

    public void deleteClass(String className) {
        if(existsClass(className)) {
            db.getMetadata().getSchema().dropClass(className);
        }
    }

    public void deleteAllNonSystemClasses() {
        List<String> ents = new ArrayList<String>();
        List<String> sc = Arrays.asList(systemClasses);
        for(OClass clazz : db.getMetadata().getSchema().getClasses()) {
            String name = clazz.getName();
            if(!sc.contains(name)) {
                ents.add(clazz.getName());
            }
        }
        for(String ent : ents) {
            // TODO sometimes this call hangs indefinitely??????
            db.getMetadata().getSchema().dropClass(ent);
        }
    }

    private void connect() {
        String location = details.location;
        if(!location.startsWith("local:") && !location.startsWith("remote:")) {
            location = "local:" + location;  // Could be smarter
        }

        db = new ODatabaseDocumentTx(location);
        if(location.startsWith("local:")) {
            if(!db.exists()) {
                db.create();
            } else {
                db.open(details.user, details.password);
            }
        } else if(location.startsWith("remote:")) {
            db.open(details.user, details.password);
        }
        initialize();
    }

    public ODatabaseDocumentTx getDb() {
        return db;
    }

    private void initialize() {
        addClass(ADMIN_CLASS);
        long count = countDocuments(ADMIN_CLASS);
        if(count == 0) {
            String json = "{'createdDate': " + System.currentTimeMillis() + "}";
            addDocument(ADMIN_CLASS, json);
        } else {
            ORecordIteratorClass<?> it = db.browseClass(ADMIN_CLASS);
            ODocument admin = (ODocument) it.next();
            if(!admin.containsField("createdDate")) {
                admin.field("createdDate", System.currentTimeMillis());
            }
            admin.save();
        }
    }

    public void disconnect() {
        if(db != null) {
            db.close();
        }
    }

    public boolean isConnected() {
        return db != null && !db.isClosed();
    }

    public List<String> getClassNames() {
        List<String> ents = new ArrayList<String>();
        List<String> sc = Arrays.asList(systemClasses);
        for(OClass clazz : db.getMetadata().getSchema().getClasses()) {
            String name = clazz.getName();
            if(!sc.contains(name)) {
                ents.add(clazz.getName());
            }
        }
        return ents;
    }

    public List<OClass> getUserClasses() {
        List<OClass> ents = new ArrayList<OClass>();
        List<String> sc = Arrays.asList(systemClasses);
        for(OClass clazz : db.getMetadata().getSchema().getClasses()) {
            String name = clazz.getName();
            if(!sc.contains(name)) {
                ents.add(clazz);
            }
        }
        return ents;
    }

    public List<OClass> getSystemClasses() {
        List<OClass> ents = new ArrayList<OClass>();
        List<String> sc = Arrays.asList(systemClasses);
        for(OClass clazz : db.getMetadata().getSchema().getClasses()) {
            String name = clazz.getName();
            if(sc.contains(name)) {
                ents.add(clazz);
            }
        }
        return ents;
    }

    public Map<String, OClass> getClassMap() {
        Map<String, OClass> map = new HashMap<String, OClass>();
        List<String> sc = Arrays.asList(systemClasses);
        for(OClass clazz : db.getMetadata().getSchema().getClasses()) {
            String name = clazz.getName();
            if(!sc.contains(name)) {
                map.put(clazz.getName(), clazz);
            }
        }
        return map;
    }

    public long countDocuments(String name) {
        OClass cls = db.getMetadata().getSchema().getClass(name);
        return cls.count();
    }

    public void addClass(String name) {
        if(!existsClass(name)) {
            db.getMetadata().getSchema().createClass(name);
        }
    }

    public boolean existsClass(String className) {
        return db.getMetadata().getSchema().existsClass(className);
    }

    public void removeClass(String name) {
        db.getMetadata().getSchema().dropClass(name);
    }

    public void addDocument(String name, String json) {
        addClass(name);
        ODocument doc = db.newInstance(name).fromJSON(json);
        doc.save();
    }

    public void addDocument(String name, File file) {
        addClass(name);
        ODocument doc = db.newInstance(name).fromJSON(FileUtil.getTextContent(file));
        doc.save();
    }

    public ODocument getAdminDocument() {
        ORecordIteratorClass<?> it = db.browseClass(ADMIN_CLASS);
        ODocument admin = (ODocument) it.next();
        return admin;
    }

    public List<NDoc> search(String searchText) {
        List<String> ents = getClassNames();
        List<NDoc> results = new ArrayList<NDoc>();
        searchText = searchText.trim();
        for(String ent : ents) {
            RecordHandler handler = UMFPluginManager.getHandler(ent);
            if(handler != null) {
                if(handler.includeTypeInSearchResults(ent)) {
                    String query = "select * from " + ent + " where ";
                    String[] searchFields = handler.getRecordTypeSearchFields(ent);
                    for(int f = 0; f < searchFields.length; f++) {
                        String field = searchFields[f];
                        String critExpr;
                        if(searchText.equals("")) {
                            // To fix orient bug where blank values don't match against "like '%%'"
                            critExpr = " like '%" + searchText.toUpperCase() + "%' OR " + field + " == ''";
                        } else {
                            critExpr = " like '%" + searchText.toUpperCase() + "%'";
                        }
                        query += field + ".toUpperCase()" + critExpr;
                        if(f != searchFields.length - 1) {
                            query += " or ";
                        }
                    }
                    if(query.endsWith(" or ")) {
                        query = StringUtil.cut(query, " or ");
                    }
                    List<ODocument> result = db.query(new OSQLSynchQuery<ODocument>(query));
                    oDocCacheLookup(result);
                    for(ODocument result0 : result) {
                        NDoc record = nDocCacheLookup(result0);
                        if(record.getHandler().includeRecordInSearchResults(record)) {
                            results.add(record);
                        }
                    }
                }
            }
        }
        return results;
    }

    public NDoc getRecord(String className, String id) {
        String query = "select * from " + className + " where @rid = ?";
        // Query does not do caching of ODocument instances, but rather
        // will return new instances even for ODocuments with the same
        // record ID returned previously.
        List<ODocument> result = db.query(new OSQLSynchQuery<ODocument>(query), new ORecordId(id));
        if(result.size() == 0) {
            return null;
        }
        return nDocCacheLookup(oDocCacheLookup(result.get(0)));
    }

    @Override
    public NDoc getById(String className, String id) {
        return getRecord(className, id);
    }

    @Override
    public List<NDoc> getAll(String className) {
        return getByQuery(className, null);
    }

    @Override
    public List<NDoc> getByQuery(String className, String queryCrit) {
        String query = "select * from " + className;
        if(queryCrit != null  && !queryCrit.equals("")) {
            query += " " + queryCrit;
        }
        List<ODocument> result = db.query(new OSQLSynchQuery<ODocument>(query));
        oDocCacheLookup(result);
        List<NDoc> results = new ArrayList<NDoc>();
        for(ODocument result0 : result) {
//            Object obj = result0.field("runs");
//            System.out.println(obj + " = " + (obj==null?obj:obj.getClass()));
            NDoc record = nDocCacheLookup(result0);
            if(record.getHandler() != null) {
                if(record.getHandler().includeRecordInSearchResults(record)) {
                    results.add(record);
                }
            } else {
                results.add(record);
            }
        }
        return results;
    }


    /////////////
    // CACHING //
    /////////////

    private void oDocCacheLookup(List<ODocument> docs) {
        for(int i = 0; i < docs.size(); i++) {
            docs.set(i, oDocCacheLookup(docs.get(i)));
        }
    }
//    private void nDocCacheLookup(List<NDoc> docs) {
//        for(int i = 0; i < docs.size(); i++) {
//            docs.set(i, nDocCacheLookup(docs.get(i)));
//        }
//    }

    private ODocument oDocCacheLookup(ODocument doc) {
        String key = doc.getIdentity().toString();   // "#8:23"
        ODocument cachedDoc = oDocCache.get(key);
        if(cachedDoc == null || cachedDoc.getVersion() < doc.getVersion()) {
            cachedDoc = doc;
            oDocCache.put(key, cachedDoc);
            // ALERT
        }
        return cachedDoc;
    }

    private NDoc nDocCacheLookup(ODocument doc) {       // Best input argument?  ODocument? String ID? NDoc?s
        String key = doc.getIdentity().toString();   // "#8:23"
        NDoc cachedDoc = nDocCache.get(key);
        if(cachedDoc == null) {
            cachedDoc = new NDoc(doc);
            nDocCache.put(key, cachedDoc);
            // ALERT
        } else if(cachedDoc.getVersion() < doc.getVersion()) {
            cachedDoc.setSource(doc);  // Replace document in case of new version.
            // ALERT
        }
        return cachedDoc;
    }
}
