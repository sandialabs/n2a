/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

import gov.sandia.n2a.backend.xyce.network.ModelInstanceOrient;
import gov.sandia.n2a.backend.xyce.network.PartInstanceCounter;
import gov.sandia.n2a.backend.xyce.network.PartSetOrient;
import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.RunOrient;
import gov.sandia.umf.platform.plugins.RunState;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Map;
import java.util.Random;
import java.util.Set;

class XyceSimulation implements Simulation {
    private ParameterSpecGroupSet paramsToHandle;
    // default sim parameter values
    private String simDur = "1.0";
    private long seed = System.currentTimeMillis();
    private String intMethodValue;
    private ExecutionEnv execEnv;
    private RunOrient runRecord;

    public ParameterDomain getAllParameters() {
        ParameterDomain inputs = new ParameterDomain();
        // TODO:  add real integration options, etc. - also need code to make sure they get in netlist!
        // and perhaps that's how run duration should get there too?
        inputs.addParameter(new Parameter("sim_duration", simDur));
        inputs.addParameter(new Parameter("seed", seed));
        inputs.addParameter(new Parameter("integration_method", "trapezoid"));
        return inputs;
    }

    @Override
    public void setSelectedParameters(ParameterDomain domain) {
        if(domain == null) {   // temp check for development, should never happen though
            return;
        }
        Map<Object, Parameter> params = domain.getParameterMap();
        if (params.containsKey("sim_duration")) {
            Double dur = (Double) params.get("sim_duration").getDefaultValue();
            simDur = dur.toString();
        }
        if (params.containsKey("seed")) {
            seed = ((Number) params.get("seed").getDefaultValue()).longValue();
        }
        if(params.containsKey("integration_method")) {
            intMethodValue = (String) params.get("integration_method").getDefaultValue();
        }
    }

    // TODO: Throws Exception?
    public RunState prepare(Object run, ParameterSpecGroupSet groups, ExecutionEnv env) 
            throws Exception {
        // handle any parameters that still need to be set
        runRecord = (RunOrient) run;
        // don't want to overwrite sim duration if it was set in RunOrient by RunDetailPanel
        // but if we created this the 'new' way, it won't exist in RunOrient yet
        if (runRecord.getSource().get("duration")==null) {
            runRecord.setSimDuration(Double.valueOf(simDur));
        }
        setParamsToHandle(groups);
        
        // set up job info
        String xyce    = env.getNamedValue ("xyce.binary");
        String jobDir  = env.createJobDir ();
        String cirFile = env.file (jobDir, "model");
        String prnFile = env.file (jobDir, "result");  // "prn" doesn't work, at least on windows
        
        // prepare netlist
        NetlistOrient nn;
        ModelInstanceOrient mi = new ModelInstanceOrient(new ModelOrient(runRecord.getModel()), seed);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(cirFile));
            nn = new NetlistOrient(mi, runRecord, this, writer);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            if(writer != null) {
                try {
                    writer.close();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        } 
        // save job info 
        XyceRunState runState = new XyceRunState();
        runState.jobDir = jobDir;
        runState.command = xyce + " " + env.quotePath (cirFile) + " -o " + env.quotePath (prnFile);
        execEnv = env;
        return runState;
    }

    public boolean resourcesAvailable()
    {
        // TODO - estimate what memory and CPU resources this sim needs
        // getting good estimates could be very difficult...
        // maybe do this during prepare, through ModelInstance 
        // for now, just hard code
        // check that the required resources are available
        // if there are already xyce sims running, use them to estimate needs
        // if not, assume we have space for one
        try {
            Set<Integer> procs = execEnv.getActiveProcs();
            int numRunning = procs.size();
            if (numRunning == 0) {
                System.out.println("resourcesAvailable:  numRunning=0");
                return true;
            }
            // check CPU usage
            // assume n2a processes are responsible for all current load;
            // evaluate whether there's room for another process of size load/numRunning
            System.out.println("num procs: " + numRunning);
            double cpuLoad = execEnv.getProcessorLoad();
            int cpuTotal = execEnv.getProcessorTotal();
            double cpuPerProc = cpuLoad/numRunning;
            boolean cpuAvailable = (cpuTotal-cpuLoad) > cpuPerProc;
            System.out.println("cpuLoad: " + cpuLoad + 
                    "; cpuPerProc: " + cpuPerProc +
                    "; cpuAvailable: " + cpuAvailable
                    );
            // Memory estimates using approach above thrown off by N2A using a lot of memory
            // Instead, ask system for memory usage per proc
            // TODO - use this approach for CPU usage also?
            long maxMem = -1;
            for (Integer procNum : procs) {
                long procMem = execEnv.getProcMem(procNum);
                if (procMem>maxMem) {
                    maxMem = procMem;
                }
            }
            long memLoad = execEnv.getMemoryPhysicalTotal() - execEnv.getMemoryPhysicalFree();
            double memPerProc;
            if (maxMem>0) {
                System.out.println("using individual process memory estimate");
                memPerProc = maxMem;
            }
            else {
                System.out.println("processor memory usage negative; using memLoad/numRunning");
                memPerProc = memLoad/numRunning;
            }
            boolean memAvailable = execEnv.getMemoryPhysicalFree() > memPerProc;
            System.out.println("memLoad: " + memLoad + 
                    "; memPerProc: " + memPerProc +
                    "; memAvailable: " + memAvailable
                    );
            return memAvailable && cpuAvailable;
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }

    public void submit() throws Exception {
        execEnv.submitJob(runRecord.getState());
    }

    public void submit(ExecutionEnv env, RunState runState) throws Exception {
        env.submitJob (runState);
    }
    
    @Override
    public RunState execute(Object run, ParameterSpecGroupSet groups, ExecutionEnv env) 
            throws Exception {
        RunState runState = prepare(run, groups, env);
        env.submitJob (runState);
        return runState;
    }

    public double getSimDuration() {
        return Double.valueOf(simDur);
    }

    public ParameterSpecGroupSet getParamsToHandle() {
        return paramsToHandle;
    }

    public void setParamsToHandle(ParameterSpecGroupSet paramsToHandle) {
        this.paramsToHandle = paramsToHandle;
    }
    
    public long getSeed()
    {
        return seed;
    }
}