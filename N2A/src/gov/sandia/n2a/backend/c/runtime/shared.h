/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


// Unconditional include, so that we always replace the current definition of SHARED.
// This should be the last include in the source file, to ensure nothing else undoes
// the setting of SHARED or is impacted by it.
#undef SHARED
#ifdef _MSC_VER
#  ifdef _USRDLL
#    define SHARED __declspec(dllexport)
#  elif defined n2a_DLL
#    define SHARED __declspec(dllimport)
#  else
#    define SHARED
#  endif
#else
#  define SHARED
#endif
