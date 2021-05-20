/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#include <string>
#include <iostream>


using namespace std;


void (*init_cb)        (int loadseq, uint64_t version, uint32_t ndevinfos, void * devinfos);
void (*push_region_cb) (const char*);
void (*pop_region_cb)  ();
void (*finalize_cb)    ();

#ifndef _WIN32

#include <dlfcn.h>
void get_callbacks ()
{
    char *x = getenv ("KOKKOS_PROFILE_LIBRARY");
    if (! x) throw "KOKKOS_PROFILE_LIBRARY environment variable must be specified";

    void *firstProfileLibrary = dlopen (x, RTLD_NOW | RTLD_GLOBAL);
    if (! firstProfileLibrary) throw "dlopen() failed";

    void *p9  = dlsym (firstProfileLibrary, "kokkosp_push_profile_region");
    void *p10 = dlsym (firstProfileLibrary, "kokkosp_pop_profile_region");
    void *p11 = dlsym (firstProfileLibrary, "kokkosp_init_library");
    void *p12 = dlsym (firstProfileLibrary, "kokkosp_finalize_library");

    push_region_cb = *reinterpret_cast<decltype(push_region_cb)*> (&p9);
    pop_region_cb  = *reinterpret_cast<decltype(pop_region_cb)*>  (&p10);
    init_cb        = *reinterpret_cast<decltype(init_cb)*>        (&p11);
    finalize_cb    = *reinterpret_cast<decltype(finalize_cb)*>    (&p12);

    (*init_cb) (0, 0, 0, nullptr);
}

#else

void get_callbacks ()
{
    throw "Profiling not implemented on WIN32";
}

#endif

void push_region (const std::string& kName)
{
    if (push_region_cb) (*push_region_cb) (kName.c_str ());
}

void pop_region ()
{
    if (pop_region_cb) (*pop_region_cb) ();
}

void finalize_profiling ()
{
    (*finalize_cb) ();
}
