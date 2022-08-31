/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.nio.file.Path;
import gov.sandia.n2a.host.Host;

public class CompilerClang extends CompilerGCC
{
    public static class Factory extends CompilerGCC.Factory
    {
        public Factory (Host host, Path gcc)
        {
            super (host, gcc);
        }

        public Compiler make (Path localJobDir)
        {
            return new CompilerClang (host, localJobDir, gcc);
        }

        public boolean supportsUnicodeIdentifiers ()
        {
            return true;
        }
    }

    public CompilerClang (Host host, Path localJobDir, Path gcc)
    {
        super (host, localJobDir, gcc);
    }
}
