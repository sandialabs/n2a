/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.expl;

import gov.sandia.umf.platform.connect.orientdb.ui.OrientConnectDetails;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientDatasource;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.simpletree.NodeBase;
import replete.gui.controls.simpletree.NodeSimpleLabel;
import replete.gui.controls.simpletree.TNode;
import replete.util.ReflectionUtil;

import com.orientechnologies.orient.core.command.OCommandRequest;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.OTrackedList;
import com.orientechnologies.orient.core.db.record.OTrackedMap;
import com.orientechnologies.orient.core.db.record.OTrackedSet;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.iterator.ORecordIteratorClass;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.orientechnologies.orient.core.type.ODocumentWrapper;
import com.orientechnologies.orient.core.type.tree.OMVRBTreeRIDSet;

public class OrientDbExplorerPanelTreeModel {

    private NodeBase uRoot;
    private TNode nRoot;

    public OrientDbExplorerPanelTreeModel() {
        uRoot = new NodeRoot();
        nRoot = new TNode(uRoot);
    }

    public TNode getRoot() {
        return nRoot;
    }


    //////////////
    // NOTIFIER //
    //////////////

    private ChangeNotifier changeNotifier = new ChangeNotifier(this);
    public void addChangeListener(ChangeListener listener) {
        changeNotifier.addListener(listener);
    }
    public void addChangeListener(ChangeListener listener, boolean useEDT) {
        changeNotifier.addListener(listener, useEDT);
    }
    private void fireChangeNotifier() {
        changeNotifier.fireStateChanged();
    }

    public TNode addSource(OrientConnectDetails details) {
        TNode nDb =  new TNode(new NodeDb(details));
        nRoot.add(nDb);
        addNewDb(nDb, details);
        fireChangeNotifier();
        return nDb;
    }

    private static final String[] systemClasses = new String[] {
        "ORestricted", "ORIDs", "OIdentity", "OUser",
        "ORole", "OFunction", "ODocumentWrapper"
    };
//
//    public void populateDbNodes(TNode nDb, OrientConnectDetails cxn) {
////        OObjectDatabaseTx db = new OObjectDatabaseTx(cxn.getLocation());
//        ODatabaseDocumentTx db = new ODatabaseDocumentTx(cxn.getLocation());
//        if(!db.exists()) {
//            db.create();
//        } else {
//            db.open(cxn.getUser(), cxn.getPassword());
//        }
//        TNode nClusters = new TNode(new NodeClusters());
//        nDb.add(nClusters);
//        for(String cName : db.getClusterNames()) {
//            TNode nCluster = new TNode(new NodeCluster(cName, db.getClusterIdByName(cName)));
//            nClusters.add(nCluster);
//            try {
//                ORecordIteratorCluster<ODocument> iterator = db.browseCluster(cName);
//                //iterator.setFetchPlan("*:0"); TODO: FAIL!
//                for(Object doc : iterator) {
//                    addDataToTree(nCluster, doc, null);
//                }
//            } catch(Exception e) {
//                TNode nError = new TNode(new NodeError(e.getMessage()));
//                nCluster.add(nError);
//            }
//        }
//
//        List<String> SYS = Arrays.asList(systemClasses);
//
//        Set<OClass> sysClasses = new TreeSet<OClass>(new OClassComparator());
//        Set<OClass> userClasses = new TreeSet<OClass>(new OClassComparator());
//
//        for(OClass clazz : db.getMetadata().getSchema().getClasses()) {
//            if(SYS.contains(clazz.getName())) {
//                sysClasses.add(clazz);
//            } else {
//                userClasses.add(clazz);
//            }
//        }
//
//        if(!sysClasses.isEmpty()) {
//            TNode nSystemClasses = nDb.add(new NodeClassGroup("System Classes"));
//            addGroup(db, sysClasses, nSystemClasses);
//        }
//
//        if(!userClasses.isEmpty()) {
//            TNode nUserClasses = nDb.add(new NodeClassGroup("User Classes"));
//            addGroup(db, userClasses, nUserClasses);
//        }
//        db.close();
//    }

    private void addNewDb(TNode nDb, OrientConnectDetails cxn) {

        OrientDatasource source = new OrientDatasource(cxn);

        Set<OClass> userClasses = new TreeSet<OClass>(new OClassComparator());
        userClasses.addAll(source.getUserClasses());

        if(!userClasses.isEmpty()) {
            TNode nUserClasses = nDb.add(new NodeClassGroup("User Classes"));
            addGroupClasses(source.getDb(), userClasses, nUserClasses);
        }

        source.disconnect();
    }
//
//    private void addGroup(ODatabaseDocumentTx db, Set<OClass> classes, TNode nGroup) {
//        for(OClass clazz : classes) {
//            String cName = clazz.getName();
//            TNode nClass = new TNode(new NodeClass(cName, db.getClusterIdByName(cName), clazz));
//            nGroup.add(nClass);
//            try {
//                ORecordIteratorClass<ODocument> iterator = db.browseClass(cName);
//                for(Object doc : iterator) {
//                    addDataToTree(nClass, doc, null);
//                }
//            } catch(Exception e) {
//                TNode nError = new TNode(new NodeError(e.getMessage()));
//                nClass.add(nError);
//            }
//        }
//    }

    private void addGroupClasses(ODatabaseDocumentTx db, Set<OClass> classes, TNode nGroup) {
        for(OClass clazz : classes) {
            try {
                String cName = clazz.getName();
                TNode nClass = new TNode(new NodeClass(cName, db.getClusterIdByName(cName), clazz));
                nGroup.add(nClass);
            } catch(Exception e) {
                TNode nError = new TNode(new NodeError(e.getMessage()));
                nGroup.add(nError);
            }
        }
    }

    private void addClassRecords(TNode nClass) {
        TNode nDb = nClass.getTParent().getTParent();
        NodeDb uDb = nDb.getObject();
        OrientConnectDetails cxn = uDb.getConnection();

        OrientDatasource source = new OrientDatasource(cxn);

        NodeClass uClass = nClass.getObject();

        try {
            ORecordIteratorClass<ODocument> iterator = source.getDb().browseClass(uClass.getName());
            for(Object doc : iterator) {
                addDataToTree(nClass, doc, null);
            }
        } catch(Exception e) {
            TNode nError = new TNode(new NodeError(e.getMessage()));
            nClass.add(nError);
        }

        source.disconnect();
    }


    private class OClassComparator implements Comparator<OClass> {
        public int compare(OClass o1, OClass o2) {
            return o1.getName().compareToIgnoreCase(o2.getName());
        }
    }

    int level;
    private void addDataToTree(TNode parent, Object data, String label) {
        addDataToTree(parent, data, label, -1);
    }
    private void addDataToTree(TNode parent, Object data, String label, int pos) {
        Set<Object> stack = new HashSet<Object>();
        level = 0;
        addDataToTree(parent, data, label, null, stack, pos);
    }
    private void addDataToTree(TNode parent, Object data, String label, OType fieldType, Set<Object> stack) {
        addDataToTree(parent, data, label, fieldType, stack, -1);
    }
    private void addDataToTree(TNode parent, Object data, String label, OType fieldType, Set<Object> stack, int pos) {
        level++;
        try {
        NodeBase uData;
        TNode nData = null;

        if(level >= 10) {
            if(data instanceof ODocument) {
                uData = new NodeRecord((ODocument) data, label, fieldType);
                nData = new TNode(uData);
                Object uLabel = new NodeSimpleLabel("(LIMIT REACHED: " + level + ")", ImageUtil.getImage("closehover.gif")) {
                    @Override
                    public boolean isItalic() {
                        return true;
                    }
                    @Override
                    public boolean isBold() {
                        return true;
                    }
                };
                nData.add(new TNode(uLabel));
            } else {
                uData = new NodeSimpleLabel((label != null ? label + " = " : "") +"(LIMIT REACHED: " + level + ")", ImageUtil.getImage("closehover.gif")) {
                    @Override
                    public boolean isItalic() {
                        return true;
                    }
                    @Override
                    public boolean isBold() {
                        return true;
                    }
                };
                nData = new TNode(uData);
            }
            if(pos != -1) {
                parent.insert(nData, pos);
            } else {
                parent.add(nData);
            }
            return;
        }

        if(stack.contains(data)) {
            String wr = "<html><i>(WOULD RECURSE)</i></html>";
            if(data instanceof ODocument) {
                uData = new NodeRecord((ODocument) data, label, fieldType);
                nData = new TNode(uData);
                Object uLabel = new NodeSimpleLabel(wr, ImageUtil.getImage("recurse.gif"));
                nData.add(new TNode(uLabel));
            } else {
                uData = new NodeSimpleLabel((label != null ? label + " = " : "") + wr, ImageUtil.getImage("recurse.gif"));
                nData = new TNode(uData);
            }
            if(pos != -1) {
                parent.insert(nData, pos);
            } else {
                parent.add(nData);
            }
            return;
        }
        stack.add(data);

        try {
            if(data == null) {
                uData = new NodeField(label, data, fieldType);

            } else if(data instanceof ODocumentWrapper) {
                ODocumentWrapper w = (ODocumentWrapper) data;
                addDataToTree(parent, w.getDocument(), label, null, stack);
                return;

            } else if(data instanceof ODocument) {
                uData = new NodeRecord((ODocument) data, label, fieldType);
                nData = new TNode(uData);
                ODocument dd = (ODocument) data;
                for(String fName : dd.fieldNames()) {
                    Object value = dd.field(fName);
                    OType myFieldType = dd.fieldType(fName);
                    addDataToTree(nData, value, fName, myFieldType, stack);
                }

            } else if(data instanceof String || data instanceof Number || data instanceof Boolean) {
                uData = new NodeField(label, data, fieldType);

            } else if(data instanceof OTrackedList || data instanceof OTrackedSet || data instanceof OMVRBTreeRIDSet) {
                int num = (Integer) ReflectionUtil.invoke("size", data);
                uData = new NodeList(label, data, num, fieldType);
                Iterable list = (Iterable) data;
                nData = new TNode(uData);
                int i = 0;
                for(Object o : list) {
                    addDataToTree(nData, o, "" + i, null, stack);
                    i++;
                }
            } else if(data instanceof OTrackedMap) {
                int num = ((OTrackedMap) data).size();
                uData = new NodeMap(label, data, num, fieldType);
                nData = new TNode(uData);
                OTrackedMap map = (OTrackedMap) data;
                for(Object o : map.keySet()) {
                    addDataToTree(nData, map.get(o), "" + o, null, stack);
                }
            } else if(data instanceof ORecordBytes) {
                ORecordBytes bytes = (ORecordBytes) data;
                byte[] bs = bytes.toStream();
                int length = bs.length;
                uData = new NodeBytes("Byte Record (length = " + length + ")", bs, bytes, fieldType);
            } else if(data instanceof ORecordId) {
                ORecordId id = (ORecordId) data;
                uData = new NodeRef(id, fieldType);
                nData = new TNode(uData);
                ODocument dd = (ODocument) id.getRecord();
                for(String fName : dd.fieldNames()) {
                    Object value = dd.field(fName);
                    OType myFieldType = dd.fieldType(fName);
                    addDataToTree(nData, value, fName, myFieldType, stack);
                }
            } else {
                uData = new NodeSimpleLabel("UNKNOWN: [" + label + "] " + data.getClass());
            }
            if(nData == null) {
                nData = new TNode(uData);
            }
            if(pos != -1) {
                parent.insert(nData, pos);
            } else {
                parent.add(nData);
            }
        } finally {
            stack.remove(data);
        }
        } finally {
            level--;
        }
    }


    List<ODocument> searchResults;
    String lastQuery;
    int searchPos;
    public TNode search(String query) {
        OrientConnectDetails cxn = ((NodeDb)((TNode) nRoot.getChildAt(0)).getUserObject()).cxnDetails;

        OrientDatasource source = new OrientDatasource(cxn);

        if(!query.equals(lastQuery)) {
            searchResults= source.getDb().query(
                new OSQLSynchQuery<ODocument>(query));
            if(searchResults.size() == 0) {
                searchPos = -1;
            } else {
                searchPos = 0;
            }
            lastQuery = query;
        } else {
            if(searchResults.size() != 0) {
                searchPos++;
                if(searchPos == searchResults.size()) {
                    searchPos = 0;
                }
            }
        }

        source.disconnect();

        if(searchPos != -1) {
            // Search hard-coded to first connection's user classes section.
            TNode nUserClasses = nRoot.getTChildAt(0).getTChild("User Classes");
            TNode node = searchForTreeNode(nUserClasses, searchResults.get(searchPos));
            return node;
        }

        return null;
    }

    public Integer delete(String query) {
        OrientConnectDetails cxn = ((NodeDb)((TNode) nRoot.getChildAt(0)).getUserObject()).cxnDetails;

        OrientDatasource source = new OrientDatasource(cxn);

        OCommandRequest cmd = source.getDb().command(new OCommandSQL(query));

        Object ret = cmd.execute();

        source.disconnect();

        classesRefresh(null);

        return (Integer) ret;
    }

    private TNode searchForTreeNode(TNode nUserClasses, ODocument doc) {
        for(TNode nClass : nUserClasses.getTChildren()) {
            for(TNode nRecord : nClass.getTChildren(NodeRecord.class)) {
                NodeRecord uRecord = nRecord.getObject();
                if(uRecord.doc.getIdentity().toString().equals(doc.getIdentity().toString())) {
                    return nRecord;
                }
            }
        }
        return null;
    }


    public void dbRefresh(TNode nDb) {
        nDb.removeAllChildren();
        addNewDb(nDb, ((NodeDb) nDb.getUserObject()).getConnection());
        fireChangeNotifier();
    }
    public void dbDelete(TNode nDb) {
        nDb.removeFromParent();
        fireChangeNotifier();
    }

    public void classRefresh(TNode nClass) {
        nClass.removeAllChildren();
        addClassRecords(nClass);
        fireChangeNotifier();
    }

    public void classesRefresh(TNode nDbxxxxx) {
        TNode nDb = (TNode) nRoot.getChildAt(0);
        TNode nUC = nDb.getTChild("User Classes");
        for(TNode nClass : nUC) {
            if(((NodeClass) nClass.getObject()).isLoaded()) {
                classRefresh(nClass);
            }
        }
    }

    private TNode getDocumentNode(TNode n) {
        TNode nDoc = n.getTParent();
        while(!(nDoc.getObject() instanceof NodeRecord)) {
            nDoc = nDoc.getTParent();
        }
        return nDoc;
    }

    private ODocument getDocument(TNode n) {
        TNode nDb = n.getTParent();
        while(!(nDb.getObject() instanceof NodeRecord)) {
            nDb = nDb.getTParent();
        }
        NodeRecord uDb = nDb.getObject();
        return uDb.getDocument();
    }

    private OrientDatasource openConnection(TNode n) {
        TNode nDb = n.getTParent();
        while(!nDb.type(NodeDb.class)) {
            nDb = nDb.getTParent();
        }
        NodeDb uDb = nDb.getObject();
        OrientConnectDetails cxn = uDb.getConnection();
        OrientDatasource source = new OrientDatasource(cxn);
        return source;
    }

    public void recordDelete(TNode nRecord) {
        if(!nRecord.ptype(NodeClass.class)) {
            return;
        }

        OrientDatasource source = openConnection(nRecord);


        NodeRecord uRecord = nRecord.getObject();
        try {
            source.getDb().delete(uRecord.getDocument());
            nRecord.removeFromParent();
            fireChangeNotifier();
        } catch(Exception e) {
            e.printStackTrace();
        }

        source.disconnect();
    }

    public void recordRefresh(TNode nDoc) {
        if(nDoc.ptype(NodeClass.class)) {
            int index = nDoc.getTParent().getIndex(nDoc);
            int count = nDoc.getTParent().getChildCount();
            try {
                TNode nParent = nDoc.getTParent();
                nDoc.removeFromParent();
                addDataToTree(nParent, ((NodeRecord)nDoc.getObject()).getDocument(), null, index);
            } catch (Exception e) {
                e.printStackTrace();
            }
            fireChangeNotifier();
        }
    }

    public void fieldDelete(TNode nField) {
        if(!nField.getTParent().ptype(NodeClass.class)) {
            return;
        }
        OrientDatasource source = openConnection(nField);
        try {
            ODocument doc = getDocument(nField);
            String key;
            if(nField.type(NodeField.class)) {
                key = ((NodeField) nField.getObject()).getKey();
            } else if(nField.type(NodeMap.class)) {
                key = ((NodeMap) nField.getObject()).getKey();
            } else {
                key = ((NodeList) nField.getObject()).getKey();
            }
            doc.removeField(key);
            doc.save();
            recordRefresh(getDocumentNode(nField));
            fireChangeNotifier();
        } catch(Exception e) {
            e.printStackTrace();
        }
        source.disconnect();
    }
}
