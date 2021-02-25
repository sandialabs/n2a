/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref;

import java.io.BufferedReader;
import java.io.IOException;
import gov.sandia.n2a.db.MNode;

public interface Parser
{
    public void parse (BufferedReader input, MNode output) throws IOException;
}
