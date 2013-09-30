/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.params;

public class SineInputSpecification extends IOSpecification {
    // parameters taken from Xyce SIN sinusoidal source waveform
    public double offset;        // vertical offset from 0 volts or amps
    public double amplitude;
    public double frequency;
    public double delay;        // start time of waveform; before this time, input will = offset value
    public double attenuation;
    
    public SineInputSpecification() {}

    public SineInputSpecification(double offset, double amp, double freq, double delay, double attenuation)
    {
        this.offset = offset;
        this.amplitude = amp;
        this.frequency = freq;
        this.delay = delay;
        this.attenuation = attenuation;
    }
    
    @Override
    public String toString() {
        return //"comp=" + compartmentName + ",var=" + variableName +
               "offset=" + offset + ", amplitude=" +
               amplitude + ", frequency=" + frequency + ", delay=" + delay +
               ", attenuation=" + attenuation;
    }
}
