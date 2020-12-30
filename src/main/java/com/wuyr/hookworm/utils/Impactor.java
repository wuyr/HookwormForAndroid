package com.wuyr.hookworm.utils;

import java.lang.reflect.Method;

/**
 * @author wuyr
 * @github https://github.com/wuyr/HookwormForAndroid
 * @since 2020-12-23 上午12:25
 */
public class Impactor {

    /**
     * Copy from me.weishu.reflection.BootstrapClass（me.weishu:free_reflection）
     */
    public static void hiddenApiExemptions() throws Throwable {
        Method forName = Class.class.getDeclaredMethod("forName", String.class);
        Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);

        Class<?> VMRuntime = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
        Method getRuntime = (Method) getDeclaredMethod.invoke(VMRuntime, "getRuntime", null);
        Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(VMRuntime, "setHiddenApiExemptions", new Class[]{String[].class});
        setHiddenApiExemptions.invoke(getRuntime.invoke(null), new Object[]{new String[]{"L"}});
    }
}
