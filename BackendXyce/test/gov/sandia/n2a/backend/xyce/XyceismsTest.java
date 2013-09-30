/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class XyceismsTest {

    private String varname = "V";
    private String constname = "V_rest";
    private String funcname = "alpha_n";
    private List<String> args;

    @Before
    public void setup() {
        args = new ArrayList<String>();
        args.add("x");
        args.add("y");
        args.add("z");
    }

    @Test
    public void testReferenceVariable()
    {
        int SN = 10;
        String expected = "V_rest_10";
        assertEquals(expected, Xyceisms.referenceVariable(constname, SN));
    }

    @Test
    public void testReferenceStatVar()
    {
        int SN = 5;
        String expected = "V(V_5_node)";
        assertEquals(expected, Xyceisms.referenceStateVar(varname, SN));
    }

    @Test
    public void testReferenceFunction()
    {
        int firstSN = 1;
        String expected = "alpha_n_1(x,y,z)";
        assertEquals(expected, Xyceisms.referenceFunction(funcname, args, firstSN));
    }
    
    @Test
    public void testDefineYDevice()
    {   
        ArrayList<String> varnames = new ArrayList<String>();
        varnames.add("V");
        varnames.add("0");
        HashMap<String,String> params = new HashMap<String,String>();
        params.put("param", "value");
        String expected = "yneuron nrn-1 V_node 0 neuron level=1 param={value} \n";
        String actual = Xyceisms.defineYDevice("neuron", 1, "nrn", varnames, 1, params);
        assertEquals(expected, actual);

        varnames.clear();
        varnames.add("pre");
        varnames.add("post");
        expected = "ysynapse syn-1 pre_node post_node synapse level=4 param={value} \n";
        actual = Xyceisms.defineYDevice("synapse", 4, "syn", varnames, 1, params);
    }

    @Test
    public void testDefineYDeviceFromModel()
    {   
        ArrayList<String> varnames = new ArrayList<String>();
        varnames.add("V");
        varnames.add("0");
        HashMap<String,String> params = new HashMap<String,String>();
        String expected = "yneuron nrn-1 V_node 0 modelName \n";
        String actual = Xyceisms.defineYDeviceWithModel("neuron", "nrn", varnames, 1, "modelName", params);
        assertEquals(expected, actual);
        
        varnames.clear();
        varnames.add("pre");
        varnames.add("post");
        params.put("param", "value");
        expected = "ysynapse syn-1 pre_node post_node modelname param={value} \n";
        actual = Xyceisms.defineYDeviceWithModel("synapse", "syn", varnames, 1, "modelName", params);
        
    }

    @Test
    public void testDefineModel()
    {   
        HashMap<String,String> params = new HashMap<String,String>();
        params.put("param", "value");
        String expected = "\n.model nrnModel neuron level=1 param={value} \n";
        String actual = Xyceisms.defineModel("neuron", 1, "nrnModel", params);
        assertEquals(expected, actual);
    }

    @Test
    public void testGetArgList()
    {
        assertEquals("(x,y,z)", Xyceisms.getArgList(args));
    }

    @Test
    public void testgetParamList()
    {
        HashMap<String,String> params = new HashMap<String,String>();
        params.put("param", "value");
        assertEquals("param={value} ", Xyceisms.getParamList(params));
    }

     @Test
    public void testVoltagePulse()
    {
        ArrayList<String> params = new ArrayList<String>(7);
        for (double i=1; i<8; i++) {params.add(String.valueOf(i));}
        String expected = "Vvar10 var_10_node 0 PULSE({1.0} {2.0} {3.0} {4.0} {5.0} {6.0} {7.0})\n";
        String actual = Xyceisms.voltagePulse("var", 10, params);
        assertEquals(expected, actual);
    }
    
    @Test
    public void testVoltageSinWave()
    {
        ArrayList<String> params = new ArrayList<String>(7);
        for (double i=1; i<8; i++) {params.add(String.valueOf(i));}
        String expected = "Vvar10 var_10_node 0 SIN({1.0} {2.0} {3.0} {4.0} {5.0})\n";
        String actual = Xyceisms.voltageSinWave("var", 10, params);
        assertEquals(expected, actual);
    }
    
    @Test
    public void testStepParam()
    {
        String expected = ".global_param a_1=0.0\n.step a_1 0.0 10.0 1.0\n";
        String actual = Xyceisms.stepParam("a", 1, 0, 1, 10);
        assertEquals(expected, actual);
    }
}
