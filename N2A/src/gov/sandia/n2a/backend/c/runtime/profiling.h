/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_profiling_h
#define n2a_profiling_h

void get_callbacks ();
void push_region (const std::string&);
void pop_region ();
void finalize_profiling ();

#endif
