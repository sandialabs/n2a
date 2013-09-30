/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.params;

public class PulseInputSpecification extends IOSpecification {
    // parameters taken from Xyce PULSE
    public double initial_value;
    public double pulse_value;
    public double delay_time;
    public double rise_time;
    public double fall_time;
    public double pulse_width;
    public double period;
    
    public PulseInputSpecification() {}
    
    public PulseInputSpecification(double init, double pulse, double delay, double rise, double fall, double width, double period)
    {
        this.initial_value = init;
        this.pulse_value = pulse;
        this.delay_time = delay;
        this.rise_time = rise;
        this.fall_time = fall;
        this.pulse_width = width;
        this.period = period;
    }

    @Override
    public String toString() {
        return // "comp=" + compartmentName + ",var=" + variableName +
               "initial_value=" + initial_value + ", pulse_value=" +
               pulse_value + ", delay_time=" + delay_time + ", rise_time=" + rise_time +
               ", fall_time=" + fall_time + ", pulse_width=" + pulse_width + ", period=" + period;
    }
}
