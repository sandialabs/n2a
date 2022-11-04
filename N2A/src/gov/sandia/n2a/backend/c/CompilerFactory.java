/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.nio.file.Path;

public interface CompilerFactory
{
    Compiler make (Path localJobDir);
    String   suffixBinary ();
    String   suffixLibraryStatic ();
    String   suffixLibraryShared ();
    String   suffixLibrarySharedWrapper ();  // If non-null, indicates that a shared library must also have a separate static wrapper library.
    String   prefixLibrary ();
    boolean  supportsUnicodeIdentifiers ();  // UFT-8 encoded characters can be inserted directly into identifiers
}
