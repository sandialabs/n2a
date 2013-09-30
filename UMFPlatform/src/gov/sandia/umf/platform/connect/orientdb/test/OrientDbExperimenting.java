/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.test;

import gov.sandia.umf.platform.connect.orientdb.ui.ConnectionManager;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientConnectDetails;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientDatasource;
import gov.sandia.umf.platform.plugins.UMFPluginManager;
import gov.sandia.umf.platform.plugins.base.PlatformPlugin;
import gov.sandia.umf.platform.plugins.extpoints.AbstractRecordHandler;

import java.util.Arrays;
import java.util.List;

import replete.plugins.DefaultPlugin;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginInitializationErrors;
import replete.plugins.PluginManager;
import replete.util.ManualTimeProfiler;

import com.orientechnologies.orient.core.record.impl.ODocument;

public class OrientDbExperimenting {
    private static ConnectionManager model;
    private static NDoc hawk;

    public static void main(String[] args) {

        // Connect to a specific OrientDB instance.
        OrientConnectDetails details = new OrientConnectDetails(
            "Local Experimenting",
            "local:C:/Users/dtrumbo/Desktop/orient/A",
            "admin", "admin");
        model = ConnectionManager.getInstance();
        model.setConnectDetails(details);
        model.connect();

        // Load N2A-related plug-ins.
        String[] plugins = new String[] {
            "gov.sandia.umf.plugins.n2a.N2APlugin",
            "gov.sandia.umf.platform.connect.orientdb.test.OrientDbExperimenting$ExperimentPlugin"
        };
        PluginManager.initialize(new PlatformPlugin(), plugins, null);
        PluginInitializationErrors errors = PluginManager.getInitializationErrors();
        if(errors.isError()) {
            System.err.println(errors);
            return;
        }
        UMFPluginManager.init();

        List<NDoc> docs = model.getDataModel().search("Hawk");
        hawk = docs.get(0);

        OrientDatasource src = model.getDataModel();
        NDoc.setDataModel(src);

        NDoc d = NDoc.getById("gov.sandia.umf.n2a$Part", "#8:2");
        NDoc d2 = NDoc.getById("gov.sandia.umf.n2a$Part", "#8:2");

//        NDoc d3 = NDoc.getById("test", "#18:0");
//        d3.set("ref", d);
//        d.set("new-val", 22);
//        d3.save();
//        System.out.println(d3.getId());
        System.out.println(Arrays.toString(d.getSource().fieldNames()));
        System.out.println(d.getSource().getVersion());
//        d.save();
//        System.out.println(d.getSource().getVersion());
//        d2.save();


//        hawk.dump("hawk");

        // Begin experimenting.
//        containmentReverting(); // should revert
//        associationReverting(); // should not revert
//        xyz();
    }

    private static void xyz() {
        System.out.println(model.getDataModel().getClassNames());

//        System.out.println(new ODocument().getSchemaClass().getDefaultClusterId());
        ODocument d1 = new ODocument("gov.sandia.umf.n2a$Part");
        ODocument d2 = new ODocument();
        System.out.println(d1.getIdentity() + " " + d1.isDirty() + " " + d1.getClassName() + " " + d1.getSchemaClass() + " " + d1.getIdentity().isNew());
        System.out.println(d2.getIdentity() + " " + d2.isDirty() + " " + d2.getClassName() + " " + d2.getSchemaClass() + " " + d2.getIdentity().isNew());
        d1.save();
        d2.save();
        System.out.println(d1.getIdentity() + " " + d1.isDirty() + " " + d1.getClassName() + " " + d1.getSchemaClass() + " " + d1.getIdentity().isNew());
        System.out.println(d2.getIdentity() + " " + d2.isDirty() + " " + d2.getClassName() + " " + d2.getSchemaClass() + " " + d2.getIdentity().isNew());

        NDoc d = new NDoc();
        NDoc e = new NDoc("ldfskjlaskf");
        NDoc f = new NDoc("ldfskjlaskf");
        NDoc g = new NDoc("ldfskjlaskf");
        NDoc h = new NDoc("ldfskjlaskf");
        System.out.println(d.getId());
        System.out.println(e.getId());
        System.out.println(f.getId());
        System.out.println(g.getId());
        System.out.println(h.getId());
        d.save();
        e.save();
        f.save();
        g.save();
        h.save();
        System.out.println(d.getId());
        System.out.println(e.getId());
        System.out.println(f.getId());
        System.out.println(g.getId());
        System.out.println(h.getId());
    }

    private static void associationReverting() {
        List<NDoc> docs = model.getDataModel().search("Eagle");
        NDoc eagle = docs.get(0);
        System.out.println(eagle);
        eagle.set("name", "Eagle2");
        System.out.println(eagle);
        ManualTimeProfiler P = new ManualTimeProfiler();
        for(int i = 0; i < 1000; i++) {
            P.step("revert");
            hawk.revert();
        }
        P.print();
        System.out.println(eagle);
    }

    private static void containmentReverting() {

        // Show hawk
        hawk.ts();

        // Show eq0
        List<NDoc> eqs = hawk.get("eqs");
        NDoc eq0 = eqs.get(0);
        eq0.ts();

        // Change eq0
        eq0.set("value", "hi = 6 @why");
        eq0.ts(); hawk.ts();

        // Change hawk
        hawk.set("name", "Hawk3");

        // Show hawk
        hawk.ts();

        // Revert
        System.out.println("--REVERT--");
        hawk.revert();

        hawk.ts();
        eq0.ts();
    }

    public static class ExperimentPlugin extends DefaultPlugin {
        @Override
        public String getName() {
            return "Experiment Plugin";
        }
        @Override
        public ExtensionPoint[] getExtensions() {
            return new ExtensionPoint[] {
                new EquationRecordHandler()
            };
        }
    }

    private static class EquationRecordHandler extends AbstractRecordHandler {
        @Override
        public String[] getHandledRecordTypes() {
            return new String[] {"gov.sandia.umf.n2a$Equation"};
        }
        @Override
        public boolean providesToString(NDoc doc) {
            return true;
        }
        @Override
        public String getToString(NDoc doc) {
            return (String) doc.get("value") + " [DIRTY=" + doc.isDirty() + ", FIELDS=" + Arrays.toString(doc.getSource().getDirtyFields()) + "]";
        }
    }
}
