/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.device;

import java.util.Arrays;

public class XyceDeviceFactory 
{
    private static final String[] SUPPORTED_DEVICES = 
        {"neuron1", "neuron7", "neuron9", "synapse3", "synapse4"};

    public static boolean isSupported(String name)
    {
        return Arrays.asList(SUPPORTED_DEVICES).contains(name.toLowerCase());
    }
    
    public static XyceDevice getDevice(String name)
    {
        if (name.toLowerCase().equals("neuron1")) {
            return new Neuron1XyceDevice();
        }
        if (name.toLowerCase().equals("neuron7")) {
            return new Neuron7XyceDevice();
        }
        if (name.toLowerCase().equals("neuron9")) {
            return new Neuron9XyceDevice();
        }
        if (name.toLowerCase().equals("synapse3")) {
            return new Synapse3XyceDevice();
        }
        if (name.toLowerCase().equals("synapse4")) {
            return new Synapse4XyceDevice();
        }
        return null;
    }
}
