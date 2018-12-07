/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

public class Schema2 extends Schema1
{
    public Schema2 (int version, String type)
    {
        super (1, type);  // TODO: change "1" to "version" after initial testing.
    }
}
