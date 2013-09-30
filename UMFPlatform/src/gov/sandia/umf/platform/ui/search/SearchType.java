/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.search;

public enum SearchType {

    // TODO: This must be dynamic someday.
    ANY("-- ANY --"),
    COMPARTMENT("Compartment"),
    CONNECTION("Connection"),
    MODEL("Model"),
    REFERENCE("Reference"),
    PROFILE("Profile"),
    BIA_MODEL("BIA Model"),
    RUN("Run"),
    GIVEN("$Given");

    private String label;
    private SearchType(String lbl) {
        label = lbl;
    }
    @Override
    public String toString() {
        return label;
    }
}
