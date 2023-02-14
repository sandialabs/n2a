/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

#include "spreadsheet.tcc"

template class Spreadsheet<n2a_T>;
#ifdef n2a_FP
template SHARED Spreadsheet<n2a_T> * spreadsheetHelper (const String & fileName, int exponent, Spreadsheet<n2a_T> *  oldHandle);
#else
template SHARED Spreadsheet<n2a_T> * spreadsheetHelper (const String & fileName,               Spreadsheet<n2a_T> *  oldHandle);
#endif
