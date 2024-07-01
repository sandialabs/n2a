/*
Copyright 2022-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.nio.file.Path;
import java.util.List;

import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Windows;

public class CompilerClang extends CompilerGCC
{
    public static class Factory extends CompilerGCC.Factory
    {
        public Factory (Host host, Path gcc)
        {
            super (host, gcc);
        }

        public Compiler compiler (Path localJobDir)
        {
            return new CompilerClang (host, localJobDir, gcc, Darwin);
        }

        public String suffixDebug ()
        {
            if (host instanceof Windows) return ".pdb";
            return "";
        }

        public boolean debugRequired ()
        {
            return host instanceof Windows;
        }

        public boolean supportsUnicodeIdentifiers ()
        {
            return true;
        }
    }

    public CompilerClang (Host host, Path localJobDir, Path gcc, boolean Darwin)
    {
        super (host, localJobDir, gcc, Darwin);
    }

    public void addDebugCompile (List<String> command)
    {
        if (debug)
        {
            if (host instanceof Windows) command.add ("-Z7");
            else                         command.add ("-g");
        }
        else
        {
            command.add (optimize);
        }
    }

    public void addDebugLink (List<String> command)
    {
        if (debug  &&  host instanceof Windows) command.add ("-debug");
    }
}
