/*
Copyright 2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.


This source file will be appended to the main model.cc file.
Before this, headers or the equivalent will be included:
  JNI -- included at top of model.cc
  IOvector interface -- Emitted at end of model.cc, before this source file.
*/

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_NativeSimulator_init (JNIEnv * env, jclass cls, jobjectArray args)
{
    int argc = env->GetArrayLength (args);
    const char ** argv = new const char * [argc];
    for (int i = 0; i < argc; i++)
    {
        jstring string = (jstring) env->GetObjectArrayElement (args, i);
        argv[i] = env->GetStringUTFChars (string, 0);
    }

    init (argc, argv);

    for (int i = 0; i < argc; i++)
    {
        jstring string = (jstring) env->GetObjectArrayElement (args, i);
        env->ReleaseStringUTFChars (string, argv[i]);
    }
    delete[] argv;
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_NativeSimulator_run (JNIEnv * env, jclass cls, jdouble until)
{
    run (until);
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_NativeSimulator_finish (JNIEnv * env, jclass cls)
{
    finish ();
}

extern "C" JNIEXPORT jlong JNICALL
Java_gov_sandia_n2a_backend_c_NativeIOvector_construct (JNIEnv * env, jclass cls, jobjectArray path)
{
    int count = env->GetArrayLength (path);
    vector<string> keys (count);
    for (int i = 0; i < count; i++)
    {
        jstring string = (jstring) env->GetObjectArrayElement (path, i);
        const char * c = env->GetStringUTFChars (string, 0);
        keys[i] = c;  // Makes a copy of c.
        env->ReleaseStringUTFChars (string, c);
    }

    jlong result = (jlong) n2a::IOvectorCreate (keys);
    if (result) return result;

    // throw exception
    jclass ex = env->FindClass ("java/lang/RuntimeException");
    return env->ThrowNew (ex, "No IOvector is defined at the given path");
}

extern "C" JNIEXPORT jint JNICALL
Java_gov_sandia_n2a_backend_c_NativeIOvector_size (JNIEnv * env, jclass cls, jlong handle)
{
    return ((IOvector *) handle)->size ();
}

extern "C" JNIEXPORT jdouble JNICALL
Java_gov_sandia_n2a_backend_c_NativeIOvector_get (JNIEnv * env, jclass cls, jlong handle, jint i)
{
    return ((IOvector *) handle)->get (i);
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_NativeIOvector_set (JNIEnv * env, jclass cls, jlong handle, jint i, jdouble value)
{
    ((IOvector *) handle)->set (i, value);
}
