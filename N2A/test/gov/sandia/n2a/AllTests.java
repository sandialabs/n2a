/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
    gov.sandia.n2a.functions.AllTests.class,
    gov.sandia.n2a.eqset.AllTests.class,
    gov.sandia.n2a.parsing.AllTests.class,
    gov.sandia.n2a.parsing.functions.AllTests.class,
    gov.sandia.n2a.parsing.gen.AllTests.class
})

public class AllTests {}
