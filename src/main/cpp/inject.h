#pragma once

#include <jni.h>

#define DEFAULT_INJECT_SIGNATURE "(Ljava/lang/String;)V"
#define DEFAULT_ODEX_PATH "/data/dalvik-cache/"
#define TAG "Hookworm"

void inject_dex(JNIEnv *env, const char *dex, const char *clazz, const char *method, const char *argument);