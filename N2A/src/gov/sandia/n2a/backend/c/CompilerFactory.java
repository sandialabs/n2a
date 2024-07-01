/*
Copyright 2022-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.nio.file.Path;

public interface CompilerFactory
{
    Compiler compiler (Path localJobDir);
    String   suffixBinary ();
    String   suffixLibrary (boolean shared);
    String   suffixLibraryWrapper ();
    String   suffixDebug ();
    String   prefixLibrary (boolean shared);
    boolean  wrapperRequired ();             // Indicates that the shared library wrapper must be used for linking. If false, then compiler can link directly to the shared library, even if it is capable of generating a wrapper.
    boolean  debugRequired ();               // Indicates that debug symbols are stored in a separate file, as opposed to embedded in the executable or shared library file.
    boolean  supportsUnicodeIdentifiers ();  // UFT-8 encoded characters can be inserted directly into identifiers
}
