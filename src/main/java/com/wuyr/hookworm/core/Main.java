package com.wuyr.hookworm.core;

import android.util.Log;

import java.lang.reflect.Method;

/**
 * Hookworm core file, called by JNI. DO NOT MODIFY
 *
 * @author wuyr
 * @github https://github.com/wuyr/HookwormForAndroid
 * @since 2020-09-15 下午6:45
 */
public class Main {

    public static final String TAG = "Hookworm";

    public static void main(String processName) {
        try {
            Hookworm.init();
            Class<?> mainClass = Class.forName(ModuleInfo.getMainClass());
            Method mainMethod = mainClass.getMethod("main", String.class);
            mainMethod.invoke(null, processName);
        } catch (Throwable t) {
            Log.e(TAG, t.toString(), t);
        }
    }
}