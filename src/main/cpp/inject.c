#include <android/log.h>
#include "inject.h"

void inject_dex(JNIEnv *env, const char *dex, const char *clazz, const char *method,
                const char *argument) {
    // get system class loader
    jclass classClassLoader = (*env)->FindClass(env, "java/lang/ClassLoader");
    jmethodID methodGetSystemClassLoader = (*env)->GetStaticMethodID(env, classClassLoader,
                                                                     "getSystemClassLoader",
                                                                     "()Ljava/lang/ClassLoader;");
    jobject objectSystemClassLoader = (*env)->CallStaticObjectMethod(env, classClassLoader,
                                                                     methodGetSystemClassLoader);

    // load dex
    jstring stringDexPath = (*env)->NewStringUTF(env, dex);
    jstring stringOdexPath = (*env)->NewStringUTF(env, DEFAULT_ODEX_PATH);
    jclass classDexClassLoader = (*env)->FindClass(env, "dalvik/system/DexClassLoader");
    jmethodID methodDexClassLoaderInit = (*env)->GetMethodID(env, classDexClassLoader, "<init>",
                                                             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
    jobject objectDexClassLoader = (*env)->NewGlobalRef(env,
                                                        (*env)->NewObject(env, classDexClassLoader,
                                                                          methodDexClassLoaderInit,
                                                                          stringDexPath,
                                                                          stringOdexPath, NULL,
                                                                          objectSystemClassLoader));

    // get loaded dex inject method
    jmethodID methodFindClass = (*env)->GetMethodID(env, classDexClassLoader, "loadClass",
                                                    "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring stringInjectClassName = (*env)->NewStringUTF(env, clazz);
    jclass loadedClass = (jclass) (*env)->CallObjectMethod(env, objectDexClassLoader,
                                                           methodFindClass, stringInjectClassName);

    // find method
    jmethodID loadedMethod = (*env)->GetStaticMethodID(env, loadedClass, method,
                                                       DEFAULT_INJECT_SIGNATURE);

    jstring stringArgument = (*env)->NewStringUTF(env, argument);

    (*env)->CallStaticVoidMethod(env, loadedClass, loadedMethod, stringArgument);

    // check status
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Inject dex failure");
    }
}
