/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.expl;

import gov.sandia.umf.platform.connect.orientdb.ui.OrientConnectDetails;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientDatasource;

import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import replete.gui.controls.GradientPanel;
import replete.gui.controls.XTextPane;
import replete.gui.controls.simpletree.NodeBase;
import replete.gui.controls.simpletree.TNode;
import replete.gui.table.SimpleTable;
import replete.util.DateUtil;
import replete.util.Lay;

import com.orientechnologies.orient.core.config.OStorageEntryConfiguration;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.ORecordElement;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.metadata.OMetadata;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ORecordBytes;

public class PropertiesPanel extends JPanel {
    private SimpleTable tblDetails;
    private JLabel lblTitle;
    private XTextPane txtDetail;
    private final Object[] headers = new Object[]{"Property", "Value"};

    private void clearProperties() {
        lblTitle.setText("<html>(no selection)</html>");
        tblDetails.setModel(new DefaultTableModel(new Object[][]{}, headers){
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }
        });
        tblDetails.setColumnWidths(new int[][] {{-1, 150, 200}});
    }

    public PropertiesPanel() {
        GradientPanel pnlHeader = new GradientPanel(new FlowLayout(FlowLayout.LEFT), Color.red, Color.orange);
        pnlHeader.setAngle(180);
        pnlHeader.add(lblTitle = Lay.lb("", "fg=white,bold"));
        Lay.BLtg(this,
            "N", pnlHeader,
            "C", Lay.sp(tblDetails = new SimpleTable()),
            "S", Lay.sp(txtDetail = new XTextPane(), "dim=[100, 200]")
        );
        txtDetail.setAllowHorizScroll(false);
        tblDetails.setRowHeight(tblDetails.getRowHeight() + 2);
        tblDetails.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                int idx = tblDetails.getSelectedRow();
                if(idx != -1) {
                    Object o = tblDetails.getModel().getValueAt(idx, 1);
                    if(o != null) {
                        txtDetail.setText("");
                        txtDetail.append("[" + o.getClass().getSimpleName() + "] ", Color.blue);
                        txtDetail.append(o.toString(), Color.black);
                    } else {
                        txtDetail.setText("");
                    }
                } else {
                    txtDetail.setText("");
                }
            }
        });
        txtDetail.setEditable(false);
        clearProperties();
    }

    private ODatabaseDocumentTx curDb;
    public void switchDb(OrientConnectDetails cxn) {
        if(curDb != null && curDb.getURL().equals(cxn.getLocation())) {
            return;
        } else if(curDb != null) {
            curDb.close();
        }

        OrientDatasource source = new OrientDatasource(cxn);
        curDb = source.getDb();
    }

    protected void rebuildTable(TNode nSel) {
        PairWiseDataList list = null;

        NodeDb uDb = nSel.getParentObject(NodeDb.class);
        if(uDb != null) {
            switchDb(uDb.getConnection());
            NodeBase uSel = (NodeBase) nSel.getUserObject();
            ODatabaseRecordThreadLocal.INSTANCE.set(curDb);

            if(uSel instanceof NodeDb) {
                list = updateDb(curDb, (NodeDb) uSel);
            } else if(uSel instanceof NodeCluster) {
                list = updateCluster(curDb, (NodeCluster) uSel);
            } else if(uSel instanceof NodeClass) {
                list = updateClass(curDb, (NodeClass) uSel);
            } else if(uSel instanceof NodeRecord) {
                list = updateRecord(curDb, (NodeRecord) uSel);
            } else if(uSel instanceof NodeField) {
                list = updateField(curDb, (NodeField) uSel);
            } else if(uSel instanceof NodeMap) {
                list = updateMap(curDb, (NodeMap) uSel);
            } else if(uSel instanceof NodeList) {
                list = updateList(curDb, (NodeList) uSel);
            } else if(uSel instanceof NodeBytes) {
                list = updateBytes(curDb, (NodeBytes) uSel);
            } else if(uSel instanceof NodeRef) {
                list = updateRef(curDb, (NodeRef) uSel);
            }
        }

        if(list != null) {
            tblDetails.setModel(new DefaultTableModel(list.toArray(), headers) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            });
            tblDetails.setColumnWidths(new int[][] {{-1, 150, 200}});
        } else {
            clearProperties();
        }
    }

    private PairWiseDataList updateDb(ODatabaseDocumentTx db, NodeDb node) {
        lblTitle.setText("Database Properties");
        OrientConnectDetails cxn = node.getConnection();
        PairWiseDataList list = new PairWiseDataList();
        list.addData("Path", cxn.getLocation());
        list.addData("User", cxn.getUser());
        list.addData("Password", cxn.getPassword());
        try {
            list.addData("Exists?", db.exists());
        } catch(Exception e) {
            list.addData("Exists?", "<unsupported>");
        }
        list.addData("# Clusters", db.getClusters());
        list.addData("Cluster Names", db.getClusterNames());
        list.addData("Name", db.getName());
        list.addData("Size", db.getSize());
        list.addData("Status", db.getStatus());
        list.addData("Type", db.getType());
        list.addData("Database Owner", db.getDatabaseOwner());
        list.addData("Default Cluster ID", db.getDefaultClusterId());
        list.addData("Underlying", db.getUnderlying());
        list.addData("Data Segment Strategy", db.getDataSegmentStrategy());
        list.addData("URL", db.getURL());
        list.addData("User", db.getUser());
        list.addData("Closed?", db.isClosed());
        list.addData("MVCC?", db.isMVCC());
        list.addData("Retain Records?", db.isRetainRecords());
        list.addData("Validation?", db.isValidationEnabled());
        list.addData("Properties", "");
        Iterator<Entry<String, Object>> i = db.getProperties();
        while(i.hasNext()) {
            Entry<String, Object> ent = i.next();
            list.addData("   " + ent.getKey(), ent.getValue());
        }
        list.addData("Storage Name", db.getStorage().getName());
        list.addData("Storage # Records", db.getStorage().countRecords());
        list.addData("Storage # Users", db.getStorage().getUsers());
        try {
            list.addData("Storage Version", db.getStorage().getVersion());
        } catch(Exception e) {
            list.addData("Storage Version", "<unsupported>");
        }
        list.addData("Storage Cfg Props", "");
        List<OStorageEntryConfiguration> stocfgprops = db.getStorage().getConfiguration().properties;
        for(OStorageEntryConfiguration x : stocfgprops) {
            list.addData("   " + x.name, x.value);
        }
        OMetadata meta = db.getMetadata();
        list.addData("Schema Cluster ID", meta.getSchemaClusterId());
        OSchema schema = meta.getSchema();
        list.addData("Schema ID", schema.getIdentity());
        list.addData("Schema Version", schema.getVersion());
        list.addData("# Classes", schema.countClasses());
        Collection<OClass> classes = schema.getClasses();
        list.addData("Classes", "");
        for(OClass clazz : classes) {
            list.addData("   " + clazz.getName(), "");
        }
        return list;
    }

    private PairWiseDataList updateRef(ODatabaseDocumentTx db, NodeRef node) {
        lblTitle.setText("Document Reference Properties");
        PairWiseDataList list = new PairWiseDataList();
        ORecordId id = node.getRef();
        list.addData("toString", id.toString());
        list.addData("Cluster ID", id.clusterId);
        list.addData("Cluster Position", id.clusterPosition);
        list.addData("New?", id.isNew());
        list.addData("Persistent?", id.isPersistent());
        list.addData("Temporary?", id.isTemporary());
        list.addData("Valid?", id.isValid());
        list.addData("Forced Type", ot(node.getFieldType()));
        return list;
    }
    private String ot(OType type) {
        if(type == null) {
            return "<none>";
        }
        return type.toString();
    }

    private PairWiseDataList updateBytes(ODatabaseDocumentTx db, NodeBytes node) {
        lblTitle.setText("Byte Record Properties");
        PairWiseDataList list = new PairWiseDataList();
        list.addData("Length", node.getBytes().length);
        ORecordBytes d = node.getRecord();
        list.addData("toString", d.toString());
        list.addData("Data Segment Name", d.getDataSegmentName());
//        list.addData("Serialization ID", d.getSerializationId());
        list.addData("Size", d.getSize());
        list.addData("Version", d.getVersion());
        list.addData("Identity", d.getIdentity());
        list.addData("Internal Status", d.getInternalStatus());
        list.addData("Record Type", d.getRecordType());
        list.addData("JSON", d.toJSON());    //Type is 'd' for documents, 'b' for binaries, 'f' for flat. what you are looking for is the @class that is 'OGraphVertex' for vertices and 'OGraphEdge' for edges.
        list.addData("Dirty?", d.isDirty());
        list.addData("Pinned?", d.isPinned());
        list.addData("Forced Type", ot(node.getFieldType()));
        return list;
    }

    private PairWiseDataList updateCluster(ODatabaseDocumentTx db, NodeCluster node) {
        lblTitle.setText("Cluster Properties");
        PairWiseDataList list = new PairWiseDataList();
        list.addData("ID", node.getId());
        list.addData("Name", node.getName());
        list.addData("Record Size", db.getClusterRecordSizeById(node.getId()));
        list.addData("Type", db.getClusterType(node.getName()));
        list.addData("# Elements", db.countClusterElements(node.getName()));
        return list;
    }

    private PairWiseDataList updateClass(ODatabaseDocumentTx db, NodeClass node) {
        OClass clazz = node.getOClass();
        lblTitle.setText("Class Properties");
        PairWiseDataList list = new PairWiseDataList();
        list.addData("ID", node.getId());
        list.addData("Name", node.getName());
        list.addData("toString", clazz.toString());
        list.addData("Cluster IDs", Arrays.toString(clazz.getClusterIds()));
        list.addData("Count", clazz.count());
        list.addData("Default Cluster ID", clazz.getDefaultClusterId());
        list.addData("Java Class Name", (clazz.getJavaClass() == null) ? "" : clazz.getJavaClass().getName());
        list.addData("Name", clazz.getName());
        list.addData("Over Size", clazz.getOverSize());
        list.addData("Polymorphic Clst IDs", Arrays.toString(clazz.getPolymorphicClusterIds()));
        list.addData("Short Name", clazz.getShortName());
        try {
            list.addData("Size", clazz.getSize());
        } catch(Exception e) {
            list.addData("Size", "<error>");
        }
        list.addData("Streamable Name", clazz.getStreamableName());
        list.addData("Super Class", clazz.getSuperClass());
        list.addData("Base Classes:", clazz.getBaseClasses());
        Iterator<OClass> bcs = clazz.getBaseClasses();
        if(bcs != null) {
            while(bcs.hasNext()) {
                OClass bc = bcs.next();
                list.addData("   " + bc.getName(), "");
            }
        }
        list.addData("Strict?", clazz.isStrictMode());
        list.addData("Declared Properties:", "");
        for(OProperty p : clazz.declaredProperties()) {
            list.addData("   " + p.getName(), p.getType());
        }
        list.addData("Class Indexes:", "");
        for(OIndex i : clazz.getClassIndexes()) {
            list.addData("   " + i.getName(), i.getType());
        }
        list.addData("Indexed Properties:", "");
        for(OProperty p : clazz.getIndexedProperties()) {
            list.addData("   " + p.getName(), p.getType());
        }
        list.addData("Indexes:", "");
        for(OIndex i : clazz.getIndexes()) {
            list.addData("   " + i.getName(), i.getType());
        }
        list.addData("Properties:", "");
        for(OProperty p : clazz.properties()) {
            list.addData("   " + p.getName(), p.getType() + ", mandatory = " + p.isMandatory());
        }

        return list;
    }

    private PairWiseDataList updateRecord(ODatabaseDocumentTx db, NodeRecord node) {
        ODocument d = node.getDocument();
        lblTitle.setText("Record Properties");
        PairWiseDataList list = new PairWiseDataList();
        list.addData("toString", d.toString());
        list.addData("# Fields", d.fields());
        list.addData("Class Name", d.getClassName());
        list.addData("Forced Type", ot(node.getFieldType()));
        list.addData("Data Segment Name", d.getDataSegmentName());
//        list.addData("Serialization ID", d.getSerializationId());
        list.addData("Size", d.getSize());
        list.addData("Version", d.getVersion());
        list.addData("Dirty Fields", Arrays.toString(d.getDirtyFields()));
        list.addData("Identity", d.getIdentity());
        list.addData("Internal Status", d.getInternalStatus());
        list.addData("Owners:", "");
        if(d.getOwners() != null) {
            for(ORecordElement elem : d.getOwners()) {
                list.addData("   " + elem.toString(), elem.getInternalStatus());
            }
        }
        list.addData("Record Type", d.getRecordType());
        list.addData("Schema Class", d.getSchemaClass());
        list.addData("JSON", d.toJSON());    //Type is 'd' for documents, 'b' for binaries, 'f' for flat. what you are looking for is the @class that is 'OGraphVertex' for vertices and 'OGraphEdge' for edges.
        list.addData("Dirty?", d.isDirty());
        list.addData("Embedded?", d.isEmbedded());
        list.addData("Empty?", d.isEmpty());
        list.addData("Lazy Load?", d.isLazyLoad());
        list.addData("Order?", d.isOrdered());
        list.addData("Tracking Changes?", d.isTrackingChanges());
        list.addData("Pinned?", d.isPinned());
        list.addData("Field Names", Arrays.toString(d.fieldNames()));
        return list;
    }
    private PairWiseDataList updateMap(ODatabaseDocumentTx db, NodeMap node) {
        Object value = node.getValue();
        lblTitle.setText("Map Properties");
        PairWiseDataList list = new PairWiseDataList();
        list.addData("toString", value.toString());
        list.addData("Java Class", value.getClass().getName());
        list.addData("# Values", node.getNumValues());
        list.addData("Forced Type", ot(node.getFieldType()));
        return list;
    }
    private PairWiseDataList updateList(ODatabaseDocumentTx db, NodeList node) {
        Object value = node.getValue();
        lblTitle.setText("List Properties");
        PairWiseDataList list = new PairWiseDataList();
        list.addData("toString", value.toString());
        list.addData("Java Class", value.getClass().getName());
        list.addData("# Elements", node.getNumValues());
        list.addData("Forced Type", ot(node.getFieldType()));
        return list;
    }
    private PairWiseDataList updateField(ODatabaseDocumentTx db, NodeField node) {
        lblTitle.setText("Value Properties");
        PairWiseDataList list = new PairWiseDataList();
        list.addData("Name", node.getKey());
        String extra = "";
        if(node.getValue() instanceof Long && node.getKey().equals("$modified")) {
            extra = " (" + DateUtil.toLongString((Long) node.getValue()) + ")";
        }
        list.addData("Value", (node.getValue() == null ? "<null>" : node.getValue()) + extra);
        list.addData("Value Class", (node.getValue() == null) ? "" : node.getValue().getClass().getName());
        list.addData("Forced Type", ot(node.getFieldType()));
        return list;
    }

    private class PairWiseDataList {
        public List<String> propNames = new ArrayList<String>();
        public List<Object> propVals = new ArrayList<Object>();
        public void addData(String name, Object val) {
            propNames.add(name);
            propVals.add(val);
        }
        public Object[][] toArray() {
            Object[][] ret = new Object[propNames.size()][2];
            for(int p = 0; p < propNames.size(); p++) {
                ret[p][0] = propNames.get(p);
                ret[p][1] = propVals.get(p);
            }
            return ret;
        }
    }
}
