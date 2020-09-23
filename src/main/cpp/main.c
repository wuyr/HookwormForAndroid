#include <stdio.h>
#include <jni.h>
#include <dlfcn.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <android/log.h>
#include <sys/stat.h>
#include <sys/system_properties.h>

#include "inject.h"
#include "main.h"

char *jstring2char(JNIEnv *env, jstring target) {
    char *result = NULL;
    if (target) {
        const char *targetChar = (*env)->GetStringUTFChars(env, target, NULL);
        if (targetChar != NULL) {
            int len = strlen(targetChar);
            result = (char *) malloc((len + 1) * sizeof(char));
            if (result != NULL) {
                memset(result, 0, len + 1);
                memcpy(result, targetChar, len);
            }
            (*env)->ReleaseStringUTFChars(env, target, targetChar);
        }
    }
    return result;
}

bool equals(const char *target1, const char *target2) {
    if (target1 == NULL && target2 == NULL) {
        return true;
    } else {
        if (target1 != NULL && target2 != NULL) {
            return strcmp(target1, target2) == 0;
        } else {
            return false;
        }
    }
}

bool shouldInject(const char *current_process_name) {
    if (target_process_size == 0) {
        return true;
    }
    for (int i = 0; i < target_process_size; i++) {
        if (equals(target_process_name[i], current_process_name)) {
            return true;
        }
    }
    return false;
}

EXPORT void nativeForkAndSpecializePre(
        JNIEnv *env, jclass clazz, jint *_uid, jint *gid, jintArray *gids, jint *runtimeFlags,
        jobjectArray *rlimits, jint *mountExternal, jstring *seInfo, jstring *niceName,
        jintArray *fdsToClose, jintArray *fdsToIgnore, jboolean *is_child_zygote,
        jstring *instructionSet, jstring *appDataDir, jboolean *isTopApp,
        jobjectArray *pkgDataInfoList,
        jobjectArray *whitelistedDataInfoList, jboolean *bindMountAppDataDirs,
        jboolean *bindMountAppStorageDirs) {
    char *current_process_name = jstring2char(env, *niceName);
    if (shouldInject(current_process_name)) {
        process_name = current_process_name;
    } else {
        if (process_name) {
            free(process_name);
            process_name = NULL;
        }
    }
}

EXPORT int nativeForkAndSpecializePost(JNIEnv *env, jclass clazz, jint res) {
    if (res == 0) {
        //normal process
        if (process_name) {
            inject_dex(env, DEX_PATH, INJECT_CLASS_PATH, "main", process_name);
            free(process_name);
            process_name = NULL;
        }
    }
    return 0;
}