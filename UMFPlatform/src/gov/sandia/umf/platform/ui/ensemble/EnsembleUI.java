/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble;

import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.Parameterizable;
import gov.sandia.umf.platform.plugins.RunState;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;
import gov.sandia.umf.platform.ui.run.CreateRunEnsembleDialog;

import java.util.List;

public class EnsembleUI {
    public static void main(String[] args) {
        final ParameterDomain modelDomain = new ParameterDomain("Model", ImageUtil.getImage("model.gif"));
        modelDomain.addParameter(new Parameter("cost", 123));
        modelDomain.addParameter(new Parameter("time", 10.231));
        modelDomain.addParameter(new Parameter("projection", "x + y", "this is a <font size='+2'>really long</font> piece of text that will hopefully overflow the description label at the bottom of the screeen."));

        ParameterDomain modelSubDomain = new ParameterDomain("Structure", null);
        modelSubDomain.addParameter(new Parameter("length", 123));
        modelSubDomain.addParameter(new Parameter("width", 10.231));
        modelSubDomain.addParameter(new Parameter("area", "length + width"));

        ParameterDomain modelSubSubDomain = new ParameterDomain("Heating", null);
        modelSubSubDomain.addParameter(new Parameter("air_loss", 123));
        modelSubSubDomain.addParameter(new Parameter("avg_temp", 10.231));
        modelSubSubDomain.addParameter(new Parameter("heat_alpha", "x + y"));

        modelSubDomain.addSubdomain(modelSubSubDomain);
        modelDomain.addSubdomain(modelSubDomain);

        ParameterDomain simDomain = new ParameterDomain("Simulator", ImageUtil.getImage("job.gif"));
        simDomain.addParameter(new Parameter("X", 999));
        simDomain.addParameter(new Parameter("Y", 888.231));
        simDomain.addParameter(new Parameter("Z", "x + y"));

        ParameterDomain domains = new ParameterDomain();
        domains.addSubdomain(modelDomain);
        domains.addSubdomain(simDomain);

        Parameterizable model = new Parameterizable() {
            public ParameterDomain getAllParameters() {
                return modelDomain;
            }
            public void setSelectedParameters(ParameterDomain domain) {
            }
        };
        ExecutionEnv[] envs = ExecutionEnv.envs.toArray(new ExecutionEnv[0]);
        Simulator[] simulators = createTestSimulators();

        CreateRunEnsembleDialog dlg = new CreateRunEnsembleDialog(
            null, null, 1123123,
            /*TEMP*/"Unknown Model 3", "dtrumbo", 1203477386476L,/*TEMP*/
            model, simulators, simulators[1], envs, envs[1], false);
        dlg.setVisible(true);

        String label = dlg.getLabel();
        ExecutionEnv env = dlg.getEnvironment();
        Simulator simulator = dlg.getSimulator();
        ParameterSpecGroupSet groups = dlg.getParameterSpecGroupSet();
        List<String> outputExpressions = dlg.getSelectedOutputExpressions();

        System.out.println(label);
        System.out.println(env);
        System.out.println(simulator);
        System.out.println(groups);
        groups.printParameterSets(true);
        groups.list(true);
        System.out.println(outputExpressions);
    }

    private static Simulator[] createTestSimulators() {
        Simulator[] simulators = new Simulator[] {
            new Simulator() {
//                @Override
//                public Map<String, RunEditDetailPanel> getPanels(UIController uiController, Run run) {
//                    return null;
//                }
                @Override
                public ParameterDomain getOutputVariables(Object model) {
                    ParameterDomain d = new ParameterDomain();
                    d.addParameter(new Parameter("integration_method", "trapezoid"));
                    d.addParameter(new Parameter("parse_type", "full"));
                    ParameterDomain e = new ParameterDomain("sim", ImageUtil.getImage("job.gif"));
                    d.addSubdomain(e);
                    e.addParameter(new Parameter("hi", 123.3));
                    return d;
                }
                @Override
                public String getName() {
                    return "TEST SIM 1";
                }
                @Override
                public ParameterDomain getSimulatorParameters() {
                    ParameterDomain d = new ParameterDomain();
                    d.addParameter(new Parameter("zelda", "link"));
                    d.addParameter(new Parameter("peach", "mario"));
                    ParameterDomain e = new ParameterDomain("games");
                    d.addSubdomain(e);
                    e.addParameter(new Parameter("metal", true));
                    return d;
                }
                @Override
                public String[] getCompatibleModelTypes() {
                    return null;
                }
                @Override
                public Simulation createSimulation() {
                    return dummy;
                }
                @Override
                public boolean canHandleRunEnsembleParameter(Object model,
                        Object key, ParameterSpecification spec) {
                    // TODO Auto-generated method stub
                    return false;
                }
            },
            new Simulator() {
//                @Override
//                public Map<String, RunEditDetailPanel> getPanels(UIController uiController, Run run) {
//                    return null;
//                }
                @Override
                public ParameterDomain getOutputVariables(Object model) {
                    ParameterDomain d = new ParameterDomain();
                    d.addParameter(new Parameter("integration_method", "trapezoid"));
                    d.addParameter(new Parameter("parse_type", "full"));
                    ParameterDomain e = new ParameterDomain("sim");
                    d.addSubdomain(e);
                    e.addParameter(new Parameter("hi", 123.3));
                    return d;
                }
                @Override
                public String getName() {
                    return "TEST SIM 2";
                }
                @Override
                public ParameterDomain getSimulatorParameters() {
                    ParameterDomain d = new ParameterDomain();
                    d.addParameter(new Parameter("integration_method", "trapezoid"));
                    d.addParameter(new Parameter("parse_type", "full"));
                    ParameterDomain e = new ParameterDomain("sim");
                    d.addSubdomain(e);
                    e.addParameter(new Parameter("hi", 123.3));
                    return d;
                }
                @Override
                public String[] getCompatibleModelTypes() {
                    return null;
                }
                @Override
                public Simulation createSimulation() {
                    return dummy;
                }
                @Override
                public boolean canHandleRunEnsembleParameter(Object model,
                        Object key, ParameterSpecification spec) {
                    // TODO Auto-generated method stub
                    return false;
                }
            }
        };
        return simulators;
    }

    private static Simulation dummy = new Simulation() {
        @Override
        public ParameterDomain getAllParameters() {
            return null;
        }
        @Override
        public void setSelectedParameters(ParameterDomain domain) {
        }
//        @Override
//        public RunState prepare(Object run, ParameterSpecGroupSet groups) throws Exception {
//            return null;
//        }
//        @Override
//        public void execute(ExecutionEnv env, RunState runState) throws Exception {
//        }
        public RunState execute(Object run, ParameterSpecGroupSet groups, ExecutionEnv env) throws Exception {
            return null;
        }
        @Override
        public RunState prepare(Object run, ParameterSpecGroupSet groups,
                ExecutionEnv env) throws Exception {
            // TODO Auto-generated method stub
            return null;
        }
        @Override
        public void submit(ExecutionEnv env, RunState runState)
                throws Exception {
            // TODO Auto-generated method stub
            
        }
        @Override
        public boolean resourcesAvailable() {
            // TODO Auto-generated method stub
            return false;
        }
		@Override
		public void submit() {
			// TODO Auto-generated method stub
			
		}
    };
}
