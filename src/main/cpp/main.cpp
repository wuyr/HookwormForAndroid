#include <jni.h>
#include <sys/types.h>
#include <malloc.h>
#include <cstring>

#include "riru.h"

static char *jstring2char(JNIEnv *env, jstring target) {
    char *result = nullptr;
    if (target) {
        const char *targetChar = env->GetStringUTFChars(target, nullptr);
        if (targetChar != nullptr) {
            int len = strlen(targetChar);
            result = (char *) malloc((len + 1) * sizeof(char));
            if (result != nullptr) {
                memset(result, 0, len + 1);
                memcpy(result, targetChar, len);
            }
            env->ReleaseStringUTFChars(target, targetChar);
        }
    }
    return result;
}

static bool equals(const char *target1, const char *target2) {
    if (target1 == nullptr && target2 == nullptr) {
        return true;
    } else {
        if (target1 != nullptr && target2 != nullptr) {
            return strcmp(target1, target2) == 0;
        } else {
            return false;
        }
    }
}

static bool shouldInject(const char *current_process_name) {
    const char *target_process_name[] = PROCESS_NAME_ARRAY;
    int target_process_size = PROCESS_NAME_ARRAY_SIZE;
    if (target_process_size == 0) {
        return true;
    }
    for (auto &i : target_process_name) {
        if (equals(i, current_process_name)) {
            return true;
        }
    }
    return false;
}

static void inject_dex(JNIEnv *env, const char *dexPath, const char *optimizedDirectory,
                       const char *mainClassName, const char *processName) {

    //get class: ClassLoader
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");

    //get method: ClassLoader.getSystemClassLoader()
    jmethodID getSystemClassLoaderMethodID = env->GetStaticMethodID(
            classLoaderClass, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");

    //invoke method: ClassLoader.getSystemClassLoader(), got ClassLoader object
    jobject systemClassLoader = env->CallStaticObjectMethod(classLoaderClass,
                                                            getSystemClassLoaderMethodID);

    //get class: DexClassLoader
    jclass dexClassLoaderClass = env->FindClass("dalvik/system/DexClassLoader");

    //get constructor: DexClassLoader(String dexPath, String optimizedDirectory, String librarySearchPath, ClassLoader parent)
    jmethodID dexClassLoaderConstructorID = env->GetMethodID(dexClassLoaderClass, "<init>",
                                                             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V");

    //new instance: DexClassLoader(dexPath, optimizedDirectory, null, systemClassLoader), got DexClassLoader object
    jobject dexClassLoader = env->NewObject(dexClassLoaderClass, dexClassLoaderConstructorID,
                                            env->NewStringUTF(dexPath),
                                            env->NewStringUTF(optimizedDirectory), NULL,
                                            systemClassLoader);

    //get method: DexClassLoader.loadClass(String name)
    jmethodID loadClassMethodID = env->GetMethodID(
            dexClassLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");

    //invoke method: DexClassLoader.loadClass(mainClassName), got Main class
    auto mainClass = (jclass) env->CallObjectMethod(
            dexClassLoader, loadClassMethodID, env->NewStringUTF(mainClassName));

    //get method: Main.main(String processName)
    jmethodID mainMethodID = env->GetStaticMethodID(
            mainClass, "main", "(Ljava/lang/String;)V");

    //invoke method: Main.main(processName)
    env->CallStaticVoidMethod(mainClass, mainMethodID, env->NewStringUTF(processName));
}

static char *process_name = nullptr;
static char *app_data_dir = nullptr;

static void forkAndSpecializePre(
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
        app_data_dir = jstring2char(env, *appDataDir);
    } else {
        if (process_name) {
            free(process_name);
            process_name = nullptr;
        }
    }
}

static void forkAndSpecializePost(JNIEnv *env, jclass clazz, jint res) {
    if (res == 0) {
        //normal process
        if (process_name && app_data_dir) {
            inject_dex(env, DEX_PATH, app_data_dir, MAIN_CLASS, process_name);
            free(app_data_dir);
            app_data_dir = nullptr;
            free(process_name);
            process_name = nullptr;
        }
    }
}

extern "C" {

int riru_api_version;
RiruApiV9 *riru_api_v9;

void *init(void *arg) {
    static int step = 0;
    step += 1;

    static void *_module;

    switch (step) {
        case 1: {
            auto core_max_api_version = *(int *) arg;
            riru_api_version =
                    core_max_api_version <= RIRU_MODULE_API_VERSION ? core_max_api_version
                                                                    : RIRU_MODULE_API_VERSION;
            return &riru_api_version;
        }
        case 2: {
            switch (riru_api_version) {
                // RiruApiV10 and RiruModuleInfoV10 are equal to V9
                case 10:
                case 9: {
                    riru_api_v9 = (RiruApiV9 *) arg;

                    auto module = (RiruModuleInfoV9 *) malloc(sizeof(RiruModuleInfoV9));
                    memset(module, 0, sizeof(RiruModuleInfoV9));
                    _module = module;

                    module->supportHide = true;

                    module->version = RIRU_MODULE_VERSION;
                    module->versionName = RIRU_MODULE_VERSION_NAME;
                    module->forkAndSpecializePre = forkAndSpecializePre;
                    module->forkAndSpecializePost = forkAndSpecializePost;
                    return module;
                }
                default: {
                    return nullptr;
                }
            }
        }
        case 3: {
            free(_module);
            return nullptr;
        }
        default: {
            return nullptr;
        }
    }
}
}