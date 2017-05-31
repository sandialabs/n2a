/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce.netlist;

import gov.sandia.n2a.backend.xyce.device.XyceDevice;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.operator.Divide;
import gov.sandia.n2a.language.type.Instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


// Create a Device from equations and metadata.
// The nodes involved in a device (other than ground) are typically state variables
// Need correspondence between varnames that other Parts might reference
// and nodes that this device uses (device references state vars, doesn't
// define them as capacitors as when using B device).
// Note that some N2A equations correspond to equations defined internally to the Xyce code;
// these equations should have xyce.nodeIndex metadata.  Changing these equations in N2A
// will NOT affect the simulation dynamics!
// There is a method provided to "inject current" into a Xyce device that does affect
// the internal state variables - equations doing this should get the xyce.inputIndex tag.
// For internal dynamics like v' = m*(V-E)/C_m +I and external input I = I_inj,
// the metadata would be xyce.nodeIndex=1 for the first and xyce.inputIndex=1 for the second
// (assuming v corresponds to node 1 for this device).
// Also need correspondence between parameter names and equation names
// XyceDevice object needs:
//        device type and level
//        name to use for device instance - based on partname and SN
//        names to use for nodes - based on varname and SN
//        map of device params to expressions from equations
//    needs to be able to take SN and write out appropriate instance-specific netlist line -

public class Device
{
    public static final String DEVICE_TAG = "backend.xyce.device";
    public static final String PARAM_TAG  = "backend.xyce.param";
    public static final String NODE_TAG   = "backend.xyce.nodeIndex";
    public static final String INPUT_TAG  = "backend.xyce.inputIndex";
    public static final String IVAR_TAG   = "backend.xyce.internalVar";
    public static final String IGNORE_TAG = "backend.xyce.ignore";

    public static Variable ground = new Variable ("0");
    public static Variable empty  = new Variable ("");

    public EquationSet eqSet;
    public XyceDevice device;

    // A device definition depends on ordered list of named nodes; these lists equate node index with 
    // those names, which are created from a combination of the variable name used in the relevant equation
    // and the appropriate serial number for a specific part instance
    // Node index appears in the xyce hint : this code assumes the first hint index is 1
    // so that the variable associated with xyce.nodeIndex=1 maps to varnames[0]
    public List<Variable> varnames = new ArrayList<Variable> ();

    // Allows for current injections on the device nodes; map below uses indices of those nodes as keys
    // and the relevant user equations as the values (typically expect only one such input, but no reason to exclude
    // more; might possibly have inputs to both ends of a synapse for testing perhaps??)
    public Map<Integer,Variable> inputs = new HashMap<Integer,Variable> ();

    // Map device parameter names to part variable names - note that the 'variable' in this case actually
    // must be something that evaluates to a constant
    public Map<String,Variable> paramList = new HashMap<String,Variable> ();

    // Map from part variable to name of xyce device internal variable; used primarily for tracing (printed output)
    public Map<Variable,String> ivars = new HashMap<Variable,String> ();

    public Map<String,String> model;  ///< The parameters and their values that were written out in the single .model line for this part. Used to determine if an instance-specific parm is needed or not.
    public String             modelName;

    public Device (EquationSet s) throws EvaluationException
    {
        eqSet = s;

        String deviceName = s.getNamedValue (DEVICE_TAG);
        if (deviceName != null) device = XyceDevice.createFor (deviceName);
        if (device == null) throw new EvaluationException ("unrecognized device name " + deviceName + " for " + eqSet.name);

        modelName = eqSet.name + "Model";

        // This really just sets up the appropriate size of the list for this device,
        // and sets any of the ground nodes to "0". Most of the entries will be
        // placeholders to be filled in below.
        List<String> defaultNodes = device.getDefaultNodes ();
        for (String node : defaultNodes)
        {
            if (node.equals ("0")) varnames.add (ground);
            else                   varnames.add (empty);
        }

        // This checks for equations with 'xyce' annotations and saves the appropriate information
        for (Variable v : eqSet.variables)
        {
            for (Entry<String,String> m : v.getMetadata ())
            {
                String metaKey = m.getKey ();
                String value = m.getValue ().replace ("\"", "");

                if (metaKey.equals (PARAM_TAG))
                {
                    if (! device.isAllowedModelParameter (value))
                    {
                        throw new EvaluationException ("unrecognized parameter " + value + " for device " + deviceName);
                    }
                    paramList.put (value.toUpperCase (), v);
                }
                else if (metaKey.equals (NODE_TAG ))
                {
                    // is RHS of node index hint a number?
                    // is that number within the range of expected device nodes?
                    // assumes node indices in xyce hints start at 1
                    int index = Integer.parseInt (value) - 1;
                    if (! device.isValidNodeIndex (index))
                    {
                        throw new EvaluationException("unrecognized/illegal node index " + index + " for device " + deviceName + " variable " + v.name);
                    }
                    varnames.set (index, v);
                }
                else if (metaKey.equals (IVAR_TAG ))
                {
                    // IVAR_TAG - designates 'internal variables' in Xyce devices,
                    // like 'u' in level 7 neuron
                    // Main purpose would be in case user wants to output those vars
                    if (! device.isInternalVariable (value))
                    {
                        throw new EvaluationException ("unrecognized internal variable " + value + " for device " + deviceName);
                    }
                    ivars.put (v, value);
                }
                else if (metaKey.equals (INPUT_TAG))
                {
                    inputs.put (Integer.parseInt (value) - 1, v);
                }
            }
        }
    }

    /// Writes an instance-specific use of an internal device.
    public String getDefinition (XyceRenderer renderer)
    {
        StringBuilder result = new StringBuilder ();

        // Nodes that this device connects to
        List<String> nodeNames = new ArrayList<String> ();
        for (int i = 0; i < varnames.size (); i++)
        {
            Variable v = varnames.get (i);
            if (v == ground)
            {
                nodeNames.add ("0");
            }
            else if (v != empty)
            {
                nodeNames.add (getInstanceVarname (v.reference, renderer.pi));
            }
        }

        // Define model (if needed)
        // Its specific parameter values will be based on first written part.
        if (model == null)
        {
            model = new HashMap<String,String> ();
            for (Entry<String, Variable> p : paramList.entrySet ())
            {
                model.put (p.getKey (), renderer.change (p.getValue ().select (renderer.pi).expression));  // TODO: select() can return null. Should we guard against it?
            }
            result.append (Xyceisms.defineModel (device.getDeviceTypeName (), device.getDeviceLevel (), modelName, model));
        }
        // Instance-specific parameters that override model
        Map<String,String> instanceParams = new HashMap<String,String> ();
        for (Entry<String, Variable> p : paramList.entrySet ())
        {
            Variable v = p.getValue ();
            if (v.global) continue;
            String name = p.getKey ();
            String value = renderer.change (v.select (renderer.pi).expression);  // TODO: select() can return null. Should we guard against it?
            if (model.get (name).equals (value)) continue;  // no need to override model parameter if value is exactly the same
            instanceParams.put (name, value);
        }

        result.append (Xyceisms.defineYDeviceWithModel (device.getDeviceTypeName (),
                                                        eqSet.name,
                                                        nodeNames,
                                                        renderer.pi.hashCode (),
                                                        modelName,
                                                        instanceParams));

        // Define inputs
        for (Integer inputNode : inputs.keySet ())
        {
            // Write a B device definition to update the node associated with the input
            // For neurons, this assumes we want to add a current of the form I/C_m
            // where the user specifies I and C_m is known.
            // Not at all clear what it means to add current to synapse, which doesn't
            // have a capacitance of its own, but this will allow adding a user-defined
            // current I to a synapse node.
            Variable v = inputs.get (inputNode);
            AccessVariable av = new AccessVariable (v.reference);
            Operator inputEq;
            if (device.getDeviceTypeName ().equals ("neuron"))
            {
                Divide d = new Divide ();
                d.operand0 = av;
                av = new AccessVariable (paramList.get (device.getCapacitanceVar ()).reference);
                d.operand1 = av;
                inputEq = d;
            }
            else
            {
                inputEq = av;
            }
            String eqName = Xyceisms.referenceVariable (v.name, renderer.pi.hashCode ());
            String instanceVarName = getInstanceVarname (v.reference, renderer.pi);
            result.append (Xyceisms.updateDiffEq (eqName, instanceVarName, renderer.change (inputEq)));
        }

        return result.toString ();
    }

    public String getInstanceVarname (VariableReference r, Instance pi)
    {
        Instance target = pi;
        if (r.index >= 0) target = (Instance) pi.valuesObject[r.index];
        return Xyceisms.referenceVariable (r.variable.name, target.hashCode ());
    }

    public static boolean isXyceDevice (EquationSet s)
    {
        return s.getNamedValue (DEVICE_TAG, null) != null;
    }

    public static boolean ignoreEquation (EquationEntry eq)
    {
        // These equations don't have to be translated like others;
        // device already implements them or doesn't need them.
        // Parameter and input equations DO have to be processed/understood elsewhere
        for (Entry<String,String> m : eq.variable.getMetadata ())
        {
            String metaKey = m.getKey ();
            if (metaKey.equals (IGNORE_TAG)  ||  metaKey.equals (NODE_TAG)  ||  metaKey.equals (IVAR_TAG))
            {
                return true;
            }
        }
        return false;
    }
}
