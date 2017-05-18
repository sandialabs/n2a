/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
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
