/**
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
**/

#ifndef native_resource_h
#define native_resource_h

#include "shared.h"

class SHARED NativeResource
{
public:
    NativeResource ();
    virtual ~NativeResource ();

    void * proxy;  ///< Java-side object that represents the C++ object. In JNI code this will be a jobject.

    static void * jvm;     ///< Java virtual machine that is hosting the JNI connection. Discovered on first setProxy() call.
    static int    version; ///< Used to retrieve a JNIEnv from the JVM.
};

#endif
