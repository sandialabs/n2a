/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble.specs;

import gov.sandia.n2a.parms.ParameterSpecification;

import javax.swing.JPanel;

public abstract class ParamSpecDefEditPanel extends JPanel {
    public abstract void setSpecification(ParameterSpecification spec);
    public abstract ParameterSpecification getSpecification();
    public abstract String getValidationMsg();
}
