/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.data;

import gov.sandia.umf.platform.connect.orientdb.ui.ConnectionManager;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientConnectDetails;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientDatasource;
import gov.sandia.umf.platform.ensemble.params.ParameterSet;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.execenvs.Linux;
import gov.sandia.umf.platform.plugins.Run;
import gov.sandia.umf.platform.plugins.RunEnsemble;
import gov.sandia.umf.platform.plugins.UMFPluginManager;
import gov.sandia.umf.platform.plugins.base.PlatformPlugin;
import gov.sandia.umf.platform.plugins.extpoints.AbstractRecordHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import replete.plugins.DefaultPlugin;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginInitializationErrors;
import replete.plugins.PluginManager;

public class OrientDbEnsembleExperimenting {
    private static ConnectionManager mgr;
    private static NDoc hawk;

    public static void main(String[] args) {

        // Connect to a specific OrientDB instance.
        OrientConnectDetails details = new OrientConnectDetails(
            "local n2a",
            "local:/Users/cewarr/.umf/n2adb/n2a-gold",
            "admin", "admin");
        mgr = ConnectionManager.getInstance();
        mgr.setConnectDetails(details);
        mgr.connect();

        // Load N2A-related plug-ins.
        String[] plugins = new String[] {
            "gov.sandia.n2a.N2APlugin",
            "gov.sandia.n2a.data.OrientDbEnsembleExperimenting$ExperimentPlugin"
        };
        PluginManager.initialize(new PlatformPlugin(), plugins, null);
        PluginInitializationErrors errors = PluginManager.getInitializationErrors();
        if(errors.isError()) {
            System.err.println(errors);
            return;
        }
        UMFPluginManager.init();

        OrientDatasource src = mgr.getDataModel();
        NDoc.setDataModel(src);

        NDoc modelDoc = NDoc.getById("gov.sandia.umf.n2a$Model", "#10:3");
        ModelOrient model = new ModelOrient(modelDoc);
        model.dumpDebug("initial model");

        // much of this copied from UIController
        
        ExecutionEnv env = new Linux();
//        Simulator simulator = new XyceSimulator();
        // TODO - need to get actual params in there!
        ParameterSpecGroupSet groups = new ParameterSpecGroupSet();
        List<String> outputExpressions = new ArrayList<String>();

        RunEnsemble re = model.addRunEnsemble("label",
                env.toString(), "gov.sandia.umf.plugins.xyce.XyceSimulator",
                groups, null, outputExpressions);
//        model.dumpDebug("model source after adding ensemble before saving");
        
        model.saveRecursive();   // not really appropriate here, I don't think
//        model.dumpDebug("model source after adding ensemble and saving");
        
        re.getTemplateModelDoc().dumpDebug("Run Ensemble's template model doc");
        
        for(ParameterSet set : groups.generateAllSetsFromSpecs(false)) {
            ParameterSet modelSet = set.subset("Model");
            modelSet.sliceKeyPathKeys();
            Run run = model.addRun(modelSet, re);
            model.dumpDebug("model source after creating Run");
            re.addRun(run);
            model.dumpDebug("model source after adding Run to ensemble");
        }
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
