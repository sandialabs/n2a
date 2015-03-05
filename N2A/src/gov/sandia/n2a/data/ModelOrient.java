/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.data;

import gov.sandia.n2a.eqset.EquationAssembler;
import gov.sandia.n2a.eqset.PartEquationMap;
import gov.sandia.n2a.language.ParsedEquation;
import gov.sandia.n2a.language.parse.ASTNodeBase;
import gov.sandia.n2a.language.parse.ParseException;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ensemble.params.ParameterSet;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.plugins.PlatformRecord;
import gov.sandia.umf.platform.plugins.Run;
import gov.sandia.umf.platform.plugins.RunEnsemble;
import gov.sandia.umf.platform.plugins.RunEnsembleOrient;
import gov.sandia.umf.platform.plugins.RunOrient;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.orientdb.general.TermValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import replete.util.NumUtil;

public class ModelOrient implements Model {
    private NDoc source;
    private List<NDoc> layerDocs;
    private List<NDoc> bridgeDocs;
    private List<NDoc> runEnsembleDocs;
    List<Layer> layers;
    List<Bridge> bridges;
    List<RunEnsemble> runEnsembles;
    private static Logger logger = Logger.getLogger(ModelOrient.class);

    public ModelOrient(NDoc doc) {
        source = doc;

        layerDocs = source.getAndSetValid("layers", new ArrayList<Layer>(), List.class);
        layers = new ArrayList<Layer>();
        for(NDoc layerDoc : layerDocs) {
            layers.add(new LayerOrient(layerDoc));
        }

        bridgeDocs = source.getAndSetValid("bridges", new ArrayList<Bridge>(), List.class);
        bridges = new ArrayList<Bridge>();
        for(NDoc bridgeDoc : bridgeDocs) {
            bridges.add(new BridgeOrient(bridgeDoc));
        }

        runEnsembleDocs = source.getAndSetValid("runEnsembles", new ArrayList<NDoc>(), List.class);
        runEnsembles = new ArrayList<RunEnsemble>();
        for(NDoc runEnsembleDoc : runEnsembleDocs) {
            runEnsembles.add(new RunEnsembleOrient(runEnsembleDoc));
        }
    }

    public void addRunEnsemble(RunEnsemble re) {
        runEnsembles.add(re);
        runEnsembleDocs.add(re.getSource());
    }

    public void clearRunEnsembles() {
        runEnsembles.clear();
        runEnsembleDocs.clear();
        source.set("runEnsembles", new ArrayList<RunEnsemble>());
        source.set("runs", null);
    }

    // AMap recursive copy / overlay operation.
    public Model copy() {
        ModelOrient modelCopy = new ModelOrient(source.copy());
        Map<String, Layer> layerCopyMap = new HashMap<String, Layer>();
        List<Layer> layerCopies = new ArrayList<Layer>();
        for(Layer layer : layers) {
            Layer layerCopy = ((LayerOrient) layer).copy();
            layerCopies.add(layerCopy);
            layerCopyMap.put(layer.getSource().getId(), layerCopy);
        }
        modelCopy.setLayers(layerCopies);

        List<Bridge> bridgeCopies = new ArrayList<Bridge>();
        for(Bridge bridge : bridges) {
            Bridge bridgeCopy = ((BridgeOrient) bridge).copy();
            bridgeCopies.add(bridgeCopy);
            List<Layer> layersConnected = bridge.getLayers();
            List<Layer> layersConnectedCopy = new ArrayList<Layer>();
            for(Layer layerConnected : layersConnected) {
                layersConnectedCopy.add(layerCopyMap.get(layerConnected.getSource().getId()));
            }
            bridgeCopy.setLayers(layersConnectedCopy);
        }
        modelCopy.setBridges(bridgeCopies);

        // Runs intentionally left off.
        modelCopy.clearRunEnsembles();

        // TODO - manage this through EquationEntry??
        List<NDoc> outputs = new ArrayList<NDoc>();
        for(NDoc op : getOutputEqs()) {
            NDoc opCopy = op.copy();
            opCopy.save();       // TODO: Necessary?  Maybe orient should handle this.
            outputs.add(opCopy);
        }
        modelCopy.setOutputEqs(outputs);

        return modelCopy;
    }

    public boolean isPersisted() {
        return source.isPersisted();
    }

    public NDoc getSource() {
        return source;
    }

    public void setName(String name) {
        source.set("name", name);
    }

    public String getName() {
        return source.get("name").toString();
    }

    public String getOwner() {
        return source.get("$owner").toString();
    }

    public void setNotes(String notes) {
        source.set("notes", notes);
    }

    public String getNotes() {
        return source.get("notes");
    }

    public void setTerms(List<TermValue> tags) {
        for (TermValue tag : tags) {
            source.setMetadata(tag.getTerm(), tag.getValue());
        }
    }

    public List<TermValue> getTerms() {
        List<TermValue> result = new ArrayList<TermValue>();
        Map<String,String> tags = source.getValid("$metadata", new HashMap<String, String>(), Map.class);
        for (String key : tags.keySet()) {
            result.add(new TermValue(key, tags.get(key)));
        }
        return result;
    }

    @Override
    public void setLayers(List<Layer> layers) {
        this.layers = layers;
        layerDocs = new ArrayList<NDoc>();
        for (Layer layer : layers) {
            layerDocs.add(layer.getSource());
        }
        source.set("layers", layerDocs);
    }

    @Override
    public List<Layer> getLayers() {
        return layers;
    }

    public Layer getLayer(String name) {
        for (Layer layer : layers) {
            if (layer.getName().equalsIgnoreCase(name)) {
                return layer;
            }
        }
        return null;
    }

    @Override
    public boolean existsLayerName(String name) {
        for (Layer layer : layers) {
            if (layer.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setBridges(List<Bridge> bridges) {
        this.bridges = bridges;
        bridgeDocs = new ArrayList<NDoc>();
        for (Bridge bridge : bridges) {
            bridgeDocs.add(bridge.getSource());
        }
        source.set("bridges", bridgeDocs);
    }

    @Override
    public List<Bridge> getBridges() {
        return bridges;
    }

    public Bridge getBridge(String name) {
        for (Bridge bridge : bridges) {
            if (bridge.getName().equalsIgnoreCase(name)) {
                return bridge;
            }
        }
        return null;
    }

    @Override
    public boolean existsBridgeName(String name) {
        for (Bridge bridge : bridges) {
            if (bridge.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public List<Run> getRuns() {
        List<Run> result = new ArrayList<Run>();
        List<NDoc> runDocs = source.getAndSetValid("runs", new ArrayList<String>(), List.class);
        for (NDoc doc : runDocs) {
            result.add(new RunOrient(doc));
        }
        return result;
    }

    public String getNextLayerName(NDoc chosenComp) {
        List<String> lNames = new ArrayList<String>();
        for(Layer layer : getLayers()) {
            lNames.add(layer.getName());
        }
        return getNextName(lNames, chosenComp.get("name").toString());
    }

    public String getNextBridgeName(NDoc chosenConn) {
        List<String> bNames = new ArrayList<String>();
        for(Bridge bridge : getBridges()) {
            bNames.add(bridge.getName());
        }
        return getNextName(bNames, chosenConn.get("name").toString());
    }

    private String getNextName(List<String> existingNames, String newName) {
        Set<Integer> digits = new TreeSet<Integer>();
        String incl = newName.replaceAll("[^A-Za-z0-9_]", "");
        boolean hasNonDigit = false;
        for(String name : existingNames) {
            String pan = name;
            if(pan.equals(incl)) {
                hasNonDigit = true;
            }
            if(pan.startsWith(incl)) {
                String rest = pan.substring(incl.length());
                if(NumUtil.isInt(rest)) {
                    digits.add(Integer.parseInt(rest));
                }
            }
        }
        if(!hasNonDigit && digits.size() == 0) {
            return incl;
        }
        int x = 0;
        for(Integer digit : digits) {
            if(digit == x) {
                x++;
            } else {
                break;
            }
        }
        return incl + x;
    }

    public void setOutputEqs(List<NDoc> equations) {
        source.set("outputEqs", equations);
    }

    @Override
    public List<NDoc> getOutputEqs() {
        return source.getValid("outputEqs", new ArrayList<NDoc>(), List.class);
    }

    @Override
    public List<ParsedEquation> getParsedOutputEqs() throws ParseException {
        List<ParsedEquation> result = new ArrayList<ParsedEquation>();
        for (NDoc doc :  getOutputEqs()) {
            result.add(EquationAssembler.getParsedEquation(doc));
        }
        return result;
    }

    @Override
    public void setInputEqs(List<NDoc> equations) {
        source.set("inputEqs", equations);
    }

    @Override
    public List<NDoc> getInputEqs() {
        return source.getAndSetValid("inputEqs", new ArrayList<NDoc>(), List.class);
    }

    public void addRun(Run run) {
        List<NDoc> runDocs = source.getValid("runs", new ArrayList<NDoc>(), List.class);
        runDocs.add(run.getSource());
        source.set("runs", runDocs);
    }

    public List<NDoc> getRunDocs() {
        return source.getValid("runs", new ArrayList<NDoc>(), List.class);
    }

    @Override
    public ParameterDomain getAllParameters() {
        ParameterDomain layersDomain  = new ParameterDomain("Layers");
        for(Layer layer : getLayers()) {
            ParameterDomain layerDomain = new ParameterDomain(layer.getName(), ImageUtil.getImage("layer.gif"));
            layersDomain.addSubdomain(layerDomain);
            PartEquationMap map = layer.getDerivedPart().getAssembledEquations();
            translateParamMapToDomain(map, layerDomain);
        }

        ParameterDomain bridgesDomain  = new ParameterDomain("Bridges");
        for(Bridge bridge : getBridges()) {
            ParameterDomain bridgeDomain = new ParameterDomain(bridge.getName(), ImageUtil.getImage("bridge.gif"));
            bridgesDomain.addSubdomain(bridgeDomain);
            PartEquationMap map = bridge.getDerivedPart().getAssembledEquations();
            translateParamMapToDomain(map, bridgeDomain);
        }

        ParameterDomain domains = new ParameterDomain();
        domains.addSubdomain(layersDomain);
        domains.addSubdomain(bridgesDomain);
        return domains;
    }

    private void translateParamMapToDomain(PartEquationMap map, ParameterDomain bridgeDomain) {
        for(String var : map.keySet()) {
            List<ParsedEquation> eqs = map.get(var);
            for(ParsedEquation eq : eqs) {
                ASTNodeBase tree = eq.getTree();
                if(tree.getCount() == 2) {
                    ASTNodeBase right = tree.getChild(1);
                    String key = eq.getVarNameWithOrder();
                    if(eqs.size() != 1) {
                        key += ParamUtil.getExtra(eq);
                    }
                    Parameter p = new Parameter(key, right.toReadableShort());
                    bridgeDomain.addParameter(p);
                }
            }
        }
    }

    @Override
    public void setSelectedParameters(ParameterDomain domains) {
        domains.list();
        ParameterDomain layersDomain = domains.getSubdomain("Layers");
        if(layersDomain != null) {
            for(ParameterDomain layerDomain : layersDomain.getSubdomains()) {
                System.out.println("layer: " + layerDomain.getName());

                Part part = getLayer(layerDomain.getName()).getDerivedPart();
                part.setSelectedParameters(layerDomain);
            }
        }
        ParameterDomain bridgesDomain = domains.getSubdomain("Bridges");
        if(bridgesDomain != null) {
            for(ParameterDomain bridgeDomain : bridgesDomain.getSubdomains()) {
                System.out.println("bridge: " + bridgeDomain.getName());

                Part part = getBridge(bridgeDomain.getName()).getDerivedPart();
                part.setSelectedParameters(bridgeDomain);
            }
        }
    }
    
    public void save() {
        logger.debug(System.currentTimeMillis() + " start of save(): source model id " + 
                source.getId() + "; version " + source.getVersion());
        for (Layer layer : layers) {
            logger.debug("\t layer id " +
                    layer.getSource().getId() + "; version " + 
                    layer.getSource().getVersion());
            logger.debug("\t layer dP id " +
                    layer.getDerivedPart().getSource().getId() + "; version " + 
                    layer.getDerivedPart().getSource().getVersion());
        }
        for (Bridge bridge : bridges) {
            logger.debug("\t bridge id " +
                    bridge.getSource().getId() + "; version " +
                    bridge.getSource().getVersion());
            logger.debug("\t bridge dP id " +
                    bridge.getDerivedPart().getSource().getId() + "; version " + 
                    bridge.getDerivedPart().getSource().getVersion());
        }
        source.save();
        logger.debug(System.currentTimeMillis() + " after source.save(): model id " + 
                source.getId() + "; version " + source.getVersion());
        for (Layer layer : layers) {
            logger.debug(System.currentTimeMillis() + " before layer save: layer id " + 
                    layer.getSource().getId() + "; version " + 
                    layer.getSource().getVersion());
            layer.getSource().save();
            logger.debug(System.currentTimeMillis() + " after layer save: layer id " + 
                    layer.getSource().getId() + "; version " + 
                    layer.getSource().getVersion());
            logger.debug(System.currentTimeMillis() + " before layer dP save: dP id " + 
                    layer.getDerivedPart().getSource().getId() + "; version " + 
                    layer.getDerivedPart().getSource().getVersion());
            layer.getDerivedPart().getSource().save();
            logger.debug(System.currentTimeMillis() + " after layer dP save: dP id " + 
                    layer.getDerivedPart().getSource().getId() + "; version " + 
                    layer.getDerivedPart().getSource().getVersion());
        }
        for (Bridge bridge : bridges) {
            logger.debug(System.currentTimeMillis() + " before bridge save: bridge id " + 
                    bridge.getSource().getId() + "; version " + 
                    bridge.getSource().getVersion());
            bridge.getSource().save();
            logger.debug(System.currentTimeMillis() + " after bridge save: bridge id " + 
                    bridge.getSource().getId() + "; version " + 
                    bridge.getSource().getVersion());
            logger.debug(System.currentTimeMillis() + " before bridge dP save: dP id " + 
                    bridge.getDerivedPart().getSource().getId() + "; version " + 
                    bridge.getDerivedPart().getSource().getVersion());
            bridge.getDerivedPart().getSource().save();
            logger.debug(System.currentTimeMillis() + " after bridge dP save: dP id " + 
                    bridge.getDerivedPart().getSource().getId() + "; version " + 
                    bridge.getDerivedPart().getSource().getVersion());
        }
    }

    @Override
    public void saveRecursive() {
        source.saveRecursive();
        for (Layer layer : layers) {
            layer.getSource().saveRecursive();
        }
        for (Bridge bridge : bridges) {
            bridge.getSource().saveRecursive();
        }
    }

    @Override
    public void setRunEnsembles(List<RunEnsemble> RunEnsembles) {
        runEnsembles = RunEnsembles;
        runEnsembleDocs = new ArrayList<NDoc>();
        for (RunEnsemble RunEnsemble : RunEnsembles) {
            runEnsembleDocs.add(RunEnsemble.getSource());
        }
        source.set("runEnsembles", runEnsembleDocs);
    }

    @Override
    public List<RunEnsemble> getRunEnsembles() {
        return runEnsembles;
    }

    /* 
     * 'groups' is for model and simulation parameters that the framework will manage
     * 'simGroups' is for model parameters that the Simulator will manage 
     */
    @Override
    public RunEnsembleOrient addRunEnsemble(String label, String environment,
            String simulator, ParameterSpecGroupSet groups, ParameterSpecGroupSet simGroups, 
            List<String> outputExpressions) 
    {
        // Copy model record for frozen run ensemble template model.
        logger.debug(System.currentTimeMillis() + " source model id " + 
                source.getId() + "; version " + source.getVersion());
        PlatformRecord modelCopy = copy();
        NDoc modelCopySource = modelCopy.getSource();
        logger.debug(System.currentTimeMillis() + " before save copy model id " + 
                modelCopySource.getId() + "; version " + modelCopySource.getVersion());
        modelCopySource.set("copied-from", getSource());
        modelCopySource.set("model-template-for-run-ensemble", true);

//        ((ModelOrient) modelCopy).dumpDebug("ModelOrient.addRunEnsemble - model copy before save");
        modelCopy.saveRecursive();

        logger.debug(System.currentTimeMillis() + " after save copy model id " + 
                modelCopySource.getId() + "; version " + modelCopySource.getVersion());
//        ((ModelOrient) modelCopy).dumpDebug("ModelOrient.addRunEnsemble - model copy after save");

        RunEnsembleOrient re = new RunEnsembleOrient(modelCopy, label,
                environment, simulator, groups, simGroups, outputExpressions);
        // Add the RE to the model's RE list.
        // Save the run ensembles to the source model.
        List<RunEnsemble> res = getRunEnsembles();
        res.add(re);
        setRunEnsembles(res);
        return re;
    }

    @Override
    public Run addRun(ParameterSet params, RunEnsemble re) {
        // Create the model for the run.
        ModelOrient model = new ModelOrient(re.getTemplateModelDoc());
//        ((ModelOrient) model).dumpDebug("ModelOrient.addRun - model before copy; from run ensemble");
        ModelOrient modelCopy = (ModelOrient) model.copy();
        NDoc modelCopySource = modelCopy.getSource();
//        ((ModelOrient) modelCopy).dumpDebug("ModelOrient.addRun - model copy after copy");
        modelCopySource.set("copied-from", model.getSource());
        modelCopySource.set("model-template-for-run", true);
        ParameterDomain domain = new ParameterDomain(params);
        modelCopy.setSelectedParameters(domain);
//        ((ModelOrient) modelCopy).dumpDebug("ModelOrient.addRun - model copy after set params");
        modelCopy.save();
//        ((ModelOrient) modelCopy).dumpDebug("ModelOrient.addRun - model copy after save");
        
        // Now create Run
        Run result = new RunOrient(modelCopy);
        return result;
    }
    
    public void dumpDebug(String str) {
        source.dumpDebug(str + "\nModelOrient - model source ");
        for (Layer layer : layers) {
            layer.getSource().dumpDebug("ModelOrient - layer " + layer.getName());
            layer.getDerivedPart().getSource().dumpDebug("ModelOrient - layer derived part ");
        }
        for (NDoc layerDoc : layerDocs) {
            layerDoc.dumpDebug("ModelOrient - layerDoc " + layerDoc.get("name"));
        }
        for (Bridge bridge : bridges) {
            bridge.getSource().dumpDebug("ModelOrient - bridge " + bridge.getName());
            bridge.getDerivedPart().getSource().dumpDebug("ModelOrient - bridge derived part ");
        }
        for (NDoc bridgeDoc : bridgeDocs) {
            bridgeDoc.dumpDebug("ModelOrient - bridgeDoc " + bridgeDoc.get("name"));
        }
        for (RunEnsemble re : runEnsembles) {
            re.getSource().dumpDebug("ModelOrient - run ensemble " + re.getLabel());
        }
        for (NDoc reDoc : runEnsembleDocs) {
            reDoc.dumpDebug("Model Orient - run ensemble doc " + reDoc.get("label"));
        }
        System.out.println("======================");
    }
}
