/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.runs;


public class PendingRun {
    private RunEnsemble ensemble;
    private int run;
    public PendingRun(RunEnsemble ensemble, int run) {
        super();
        this.ensemble = ensemble;
        this.run = run;
    }
    public RunEnsemble getEnsemble() {
        return ensemble;
    }
    public int getRun() {
        return run;
    }
}
