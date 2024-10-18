/**
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
**/

#include "NativeResource.h"

#include <jni.h>


// class NativeResource ------------------------------------------------------

void * NativeResource::jvm     = 0;
int    NativeResource::version = 0;

NativeResource::NativeResource ()
{
    proxy = 0;
}

NativeResource::~NativeResource ()
{
    if (jvm  &&  proxy)
    {
        JNIEnv * env;
        ((JavaVM *) jvm)->GetEnv ((void **) &env, version);

        jclass nrClass = env->FindClass ("gov/sandia/n2a/backend/c/NativeResource");

        if (env->IsInstanceOf ((jobject) proxy, nrClass))
        {
            jmethodID release = env->GetMethodID (nrClass, "release", "()V");
            env->CallVoidMethod ((jobject) proxy, release);
        }

        env->DeleteWeakGlobalRef ((jobject) proxy);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_NativeResource_destruct (JNIEnv * env, jclass cls, jlong handle)
{
    NativeResource * nr = (NativeResource *) handle;
    if (nr->proxy)
    {
        env->DeleteWeakGlobalRef ((jobject) nr->proxy);
        nr->proxy = 0;
    }
    delete nr;
}

extern "C" JNIEXPORT jobject JNICALL
Java_gov_sandia_n2a_backend_c_NativeResource_getProxy (JNIEnv * env, jclass cls, jlong handle)
{
    NativeResource * nr = (NativeResource *) handle;
    if (nr->proxy == 0) return env->NewLocalRef (0);
    return (jobject) nr->proxy;
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_NativeResource_setProxy (JNIEnv * env, jclass cls, jlong handle, jobject proxy)
{
    if (NativeResource::jvm == 0)
    {
        NativeResource::version = env->GetVersion ();
        jint result = env->GetJavaVM ((JavaVM **) &NativeResource::jvm);
        if (result != 0) NativeResource::jvm = 0;
    }

    NativeResource * nr = (NativeResource *) handle;

    if (nr->proxy) env->DeleteWeakGlobalRef ((jobject) nr->proxy);
    nr->proxy = 0;

    jobject Null = env->NewLocalRef (0);
    if (! env->IsSameObject (proxy, Null)) nr->proxy = env->NewWeakGlobalRef (proxy);
}
