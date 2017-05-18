/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce.netlist;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Xyceisms {

    ////////////////////
    // These methods create the strings needed for referencing variables,
    // functions, etc. used in the right hand side of an expression
    ////////////////////

    public static String referenceVariable (String varname, int serialNumber)
    {
            return varname + "_" + serialNumber;
    }

    public static String referenceStateVar (String varname, int serialNumber)
    {
        return "V("+ varname + "_" + serialNumber + "_node)";
    }

    public static String referenceFunction(
            String funcname,
            Collection<String> args,
            int firstSN)
    {
        return funcname + "_" + firstSN + getArgList(args);
    }


    ////////////////////
    // These methods create the strings needed for defining variables,
    // functions, etc.
    // They assume that RHS expressions passed in have already been
    // converted to Xyce form
    ////////////////////

    public static String param(String variableName, int SN, double value)
    {
        return ".param " + variableName + "_" + SN + " = " + value + "\n";
    }

    public static String param(String variableName, int SN, String expression)
    {
        return ".param " + variableName + "_" + SN + " = {" + expression + "}\n";
    }

    public static String stepParam(String variableName, int SN, double start, double step, double stop) 
    {
        String name = referenceVariable(variableName, SN);
        return ".global_param " + name + "=" + start + "\n.step " + name + " " + 
                start + " " + stop + " " + step + "\n";
    }

    public static String defineFunction(String varname, int SN, Collection<String> args, String RHS)
    {
        return ".func " + varname + "_" + SN + getArgList(args) + " {" + RHS + "}\n";
    }
    
    public static String defineStateVar(String varname, int SN, String expression)
    {
        String refVarName = referenceVariable(varname, SN);
        return getVBehavioralDevice(refVarName, expression);
    }

    public static String defineDiffEq(String varname, int SN, String expression)
    {
        String refVarName = referenceVariable(varname, SN);
        return getCapacitor(refVarName) + getIBehavioralDevice(refVarName, expression);
    }

    public static String updateDiffEq(String varname, int SN, String expression)
    {
        String refVarName = referenceVariable(varname, SN);
        return getIBehavioralDevice(refVarName, expression);
    }

    public static String updateDiffEq(String eqName, String varname, String expression)
    {
        return getBehavioralDevice(eqName, varname, expression);
    }

    // Defines a y device entirely from instance parameters; no model
    // specification.  Currently, xyce neuron and synapse devices all
    // require a model, so we can't really use this yet.
    // varnames need to already have SNs appended, as they may belong to 
    // different parts.    A varname of "0" indicates ground.
    public static String defineYDevice(
        String deviceType,
        int deviceLevel,
        String name,
        List<String> varnames,
        int SN,
        Map<String,String> params)
    {
        // format examples:
        // yneuron nrn-1 Vnode 0 neuron level=1 param={value}
        // ysynapse syn-1 preNode postNode synapse level=4 pram={value}
        String result = "y" + deviceType + " " + name + "-" + SN + " " + 
                getNodes(varnames) +
                deviceType + " level=" + deviceLevel + " " +
                getParamList(params) + "\n";
        return result;
    }
    
    // References an already-defined model to define y device, but allows
    // specification of instance parameters as well.  Note that only some
    // device parameters are allowed as instance parameters; it's up to the
    // calling method to ensure that parameter names are valid instance
    // parameter names.
    // varnames need to already have SNs appended, as they may belong to 
    // different parts.  A varname of "0" indicates ground.
    public static String defineYDeviceWithModel(
        String deviceType,
        String name,
        List<String> varnames,
        int SN,
        String modelName,
        Map<String,String> params)
    {
        // format examples:
        // yneuron nrn-1 Vnode 0 modelA param={value}
        // ysynapse syn-1 preNode postNode modelB param={value}
        String result = "y" + deviceType + " " + name + "-" + SN + " " + 
                getNodes(varnames) + modelName + " " +
                getParamList(params) + "\n";
        return result;
    }

    // Create a .model line
    public static String defineModel(
            String deviceType,
            int deviceLevel,
            String name,
            Map<String,String> params)
    {
        // format examples:
        // .model nrnmodel neuron level=1 param={value}
        // .model synmodel synapse level=4 pram={value}
        String result = "\n.model " + name + " " + 
                deviceType + " level=" + deviceLevel + " " +
                getParamList(params) + "\n";
        return result;
    }
    
    private static String getNodes(List<String> varnames)
    {
        StringBuilder sb = new StringBuilder();
        for (String var : varnames)
        {
            if (var.equals("0")) {
                sb.append(var + " ");
            }
            else {
                sb.append(var + "_node ");
            }
        }
        return sb.toString();
    }
    
    public static String voltagePulse(
            String varName,
            int SN,
            List<String> params)
    {
        String result = "V" + varName + SN + " " +
                varName + "_" + SN + "_node" +
                " 0 " + "PULSE(";
        for (int i=0; i<7; i++) {
            result += "{" + params.get(i) + "}";
            if (i!=6) {
                result += " ";
            }
            else {
                result += ")\n";
            }
        }
        return result;
    }

    public static String voltageSinWave(
            String sinName,
            int SN,
            List<String> params)
    {
        String result =  
                "V" + sinName + SN + " " +
                sinName + "_" + SN + "_node" +
                " 0 " + "SIN(";
        for (int i=0; i<5; i++) {
            result += "{" + params.get(i) + "}";
            if (i!=4) {
                result += " ";
            }
            else {
                result += ")\n";
            }
        }
        return result;
    }

    public static String setInitialCondition(String varname, int serialNumber, Number value)
    {
        return ".ic " + referenceStateVar(varname, serialNumber) + "=" + value + "\n";
    }

    ////////////////////
    // Supporting methods
    ////////////////////

    public static String getArgList(Collection<String> args)
    {
        StringBuilder argString = new StringBuilder();
        argString.append("(");
        boolean first = true;
        for (String arg : args)
        {
            if (first) {
                argString.append(arg);
            } else {
                argString.append(","+ arg);
            }
            first = false;
        }
        argString.append(")");
        return argString.toString();
    }

    // This is for writing device or .model parameters.  The params map passed in 
    // should have recognized device parameter names as keys, and expressions as values
    public static String getParamList(Map<String,String> params)
    {
        StringBuilder paramString = new StringBuilder ();
        for (String name : params.keySet ())
        {
            paramString.append (name + "={" + params.get (name) + "} ");
        }
        return paramString.toString();
    }

    public static String getCapacitor(String varname)
    {
        // create capacitor for this variable
        // Cname node 0 1
        return "C" + varname + " " + varname + "_node" + " 0 1\n";
    }

    // This is the behavioral device corresponding to creation of
    // the node for a state variable defined by a differential equation,
    // corresponding to a current in Xyce; the device is named for
    // the node
    public static String getIBehavioralDevice(String varname, String RHS)
    {
        // create the behavioral device, using the RHS passed in
        // Bname 0 node I={eqn}
        return "B" + varname + "_equ" +
               " 0 " + varname + "_node" +
               " I={ " + RHS + "}\n";
    }

    // This is the behavioral device corresponding to creation of
    // the node for a state variable defined by a function of time
    // that does not use a time derivative, corresponding to a voltage
    // in Xyce; the device is named for the node
    public static String getVBehavioralDevice(String varname, String RHS)
    {
        // create the behavioral device, using the RHS passed in
        // Bname node 0 V={eqn}
        return "B" + varname + "_equ " +
               varname + "_node" + " 0 " + 
               " V={ " + RHS + "}\n";
    }

    // this is for any additional behavioral devices after the
    // initial state variable node has been created - it uses the same
    // node name, but a different device name
    public static String getBehavioralDevice(String eqName, String varname,
           String RHS)
    {
        // create the behavioral device, using the RHS passed in
        // Bname 0 node I={eqn}
        return "B" + eqName + "_equ" +
               " 0 " + varname + "_node" +
               " I={ " + RHS + "}\n";
    }

}
