/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

import gov.sandia.n2a.data.Bridge;
import gov.sandia.n2a.data.Layer;
import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.n2a.data.ParamUtil;
import gov.sandia.n2a.eqset.PartEquationMap;
import gov.sandia.n2a.language.ParsedEquation;
import gov.sandia.n2a.language.gen.ASTNodeBase;
import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.util.List;

public class XyceSimulator implements Simulator {

    @Override
    public String getName() {
        return "Xyce2";
    }

    @Override
    public String[] getCompatibleModelTypes() {
        return new String[] {"n2a"};
    }

    public ParameterDomain getSimulatorParameters () {
        return new XyceSimulation().getAllParameters();
    }

    @Override
    public ParameterDomain getOutputVariables (Object model) {
        ModelOrient modelO = (ModelOrient) model;

        ParameterDomain layersDomain  = new ParameterDomain("Layers");
        for(Layer layer : modelO.getLayers()) {
            ParameterDomain layerDomain = new ParameterDomain(layer.getName(), ImageUtil.getImage("layer.gif"));
            layersDomain.addSubdomain(layerDomain);
            PartEquationMap map = layer.getDerivedPart().getAssembledEquations();
            translateParamMapToDomain(map, layerDomain);
        }

        ParameterDomain bridgesDomain  = new ParameterDomain("Bridges");
        for(Bridge bridge : modelO.getBridges()) {
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
    public boolean canHandleRunEnsembleParameter(Object model, Object key,
            ParameterSpecification spec) {
    // disabling this functionality until we resolve issues with xyce .step
        // key should be strings like "model.layers.hh.V"
//        ParameterKeyPath keyPath = (ParameterKeyPath) key;
//        String top = (String) keyPath.get(0);
//        if (!top.equals("Model")) {
//            return false;
//        }
//        String paramName = (String) keyPath.get(3);
//        if (paramName.startsWith("$") || paramName.endsWith("'")) {
//            return false;
//        }
//        // at this point, parameter OK; check the spec
//        if (spec instanceof StepParameterSpecification) {
//            return true;
//        }
        return false;
    }

    @Override
    public Simulation createSimulation() {
        return new XyceSimulation();
    }
}
