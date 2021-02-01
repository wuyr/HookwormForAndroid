package com.wuyr.hookworm.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import com.wuyr.hookworm.extensions.PhoneLayoutInflater
import com.wuyr.hookworm.extensions.SimpleActivityLifecycleCallbacks
import com.wuyr.hookworm.utils.*
import dalvik.system.DexFile
import java.io.File
import java.lang.reflect.Proxy
import kotlin.concurrent.thread

/**
 * @author wuyr
 * @github https://github.com/wuyr/HookwormForAndroid
 * @since 2020-09-20 下午3:07
 */
@Suppress("unused")
object Hookworm {

    /**
     * 是否转接插件Dex的ClassLoader
     * 如果引用到了目标应用的一些自定义类或接口（或第三方库），则需要转接，否则会报 [ClassNotFoundException]
     */
    @JvmStatic
    var transferClassLoader = false
        set(value) {
            if (field != value) {
                field = value
                if (isApplicationInitialized && value) {
                    application.initClassLoader()
                }
            }
        }

    /**
     * 是否劫持全局的LayoutInflater
     */
    @JvmStatic
    var hookGlobalLayoutInflater = false
        set(value) {
            if (field != value) {
                field = value
                if (isApplicationInitialized && value) {
                    initGlobalLayoutInflater()
                }
            }
        }
    private var globalLayoutInflater: PhoneLayoutInflater? = null

    /**
     * 进程Application实例
     */
    @JvmStatic
    lateinit var application: Application
        private set

    /**
     * 进程存活Activity实例集合
     */
    @JvmStatic
    val activities = HashMap<String, Activity?>()

    /**
     * 监听Application初始化
     */
    @JvmStatic
    var onApplicationInitializedListener: ((Application) -> Unit)? = null
    private var isApplicationInitialized = false

    private val activityLifecycleCallbackList =
        HashMap<String, Application.ActivityLifecycleCallbacks>()

    private var postInflateListenerList =
        HashMap<String, ((resourceId: Int, resourceName: String, rootView: View?) -> View?)?>()

    private val tempActivityLifecycleCallbacksList =
        ArrayList<Application.ActivityLifecycleCallbacks>()

    /**
     * 拦截LayoutInflater布局加载
     *
     *  @param className 对应的Activity类名（完整类名），空字符串则表示拦截所有Activity的布局加载
     *  @param postInflateListener 用来接收回调的lambda，需返回加载后的View（可在返回前对这个View做手脚）
     *
     *  Lambda参数
     *      resourceId：正在加载的xml ID
     *      resourceName：正在加载的xml名称
     *      rootView：加载完成后的View
     */
    @JvmStatic
    fun registerPostInflateListener(
        className: String,
        postInflateListener: (resourceId: Int, resourceName: String, rootView: View?) -> View?
    ) {
        postInflateListenerList[className] = postInflateListener
        if (className.isEmpty() && hookGlobalLayoutInflater) {
            globalLayoutInflater?.postInflateListener = postInflateListener
        } else {
            activities[className]?.hookLayoutInflater(postInflateListener)
        }
    }

    /**
     *  取消拦截LayoutInflater布局加载
     *
     *  @param className 对应的Activity类名（完整类名）
     */
    @JvmStatic
    fun unregisterPostInflateListener(className: String) {
        if (className.isEmpty() && hookGlobalLayoutInflater) {
            globalLayoutInflater?.postInflateListener = null
        }
        postInflateListenerList.remove(className)
        activities[className]?.let { activity ->
            val oldInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
            if (oldInflater is PhoneLayoutInflater) {
                oldInflater.postInflateListener = null
            }
        }
    }

    /**
     * 监听Activity的生命周期
     *
     *  @param className 对应的Activity类名（完整类名），空字符串表示监听所有Activity
     *  @param callback ActivityLifecycleCallbacks实例
     */
    @JvmStatic
    fun registerActivityLifecycleCallbacks(
        className: String, callback: Application.ActivityLifecycleCallbacks
    ) {
        activityLifecycleCallbackList[className] = callback
    }

    /**
     *  取消监听Activity的生命周期
     *
     *  @param className 对应的Activity类名（完整类名）
     */
    @JvmStatic
    fun unregisterActivityLifecycleCallbacks(className: String) =
        activityLifecycleCallbackList.remove(className)

    /**
     *  根据完整类名查找Activity对象
     *
     *  @param className 对应的Activity类名（完整类名）
     *  @return 对应的Activity实例，找不到即为null
     */
    @JvmStatic
    fun findActivityByClassName(className: String) = activities[className]

    private var initialized = false

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    @Suppress("ControlFlowWithEmptyBody")
    @JvmStatic
    fun init() {
        if (initialized) return
        throwReflectException = true
        initialized = true
        thread(isDaemon = true) {
            try {
                while (Looper.getMainLooper() == null) {
                }
                val currentApplicationMethod = Class.forName("android.app.ActivityThread")
                    .getDeclaredMethod("currentApplication").also { it.isAccessible = true }
                while (currentApplicationMethod.invoke(null) == null) {
                }
                "android.app.ActivityThread".invoke<Application>(null, "currentApplication")!!.run {
                    application = this
                    if (ModuleInfo.isDebug() && Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                        hiddenApiExemptions()
                    }
                    initGlobalLayoutInflater()
                    initClassLoader()
                    initLibrary()
                    Handler(Looper.getMainLooper()).post {
                        onApplicationInitializedListener?.invoke(this)
                    }
                    registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks())
                    tempActivityLifecycleCallbacksList.forEach { callback ->
                        registerActivityLifecycleCallbacks(callback)
                    }
                    tempActivityLifecycleCallbacksList.clear()
                    isApplicationInitialized = true
                }
            } catch (e: Exception) {
                Log.e(Main.TAG, Log.getStackTraceString(e))
            }
        }
    }

    private fun hiddenApiExemptions() {
        try {
            Impactor.hiddenApiExemptions()
        } catch (t: Throwable) {
            try {
                DexFile(ModuleInfo.getDexPath()).apply {
                    loadClass(Impactor::class.java.canonicalName, null)
                        .invokeVoid(null, "hiddenApiExemptions")
                    close()
                }
            } catch (t: Throwable) {
                Log.e(Main.TAG, t.toString(), t)
            }
        }
    }

    private fun hookLayoutInflater(className: String, activity: Activity) {
        if (postInflateListenerList.isNotEmpty()) {
            (postInflateListenerList[className]
                ?: if (hookGlobalLayoutInflater) null else postInflateListenerList[""])
                ?.also { activity.hookLayoutInflater(it) }
        }
    }

    @SuppressLint("PrivateApi")
    private fun Activity.hookLayoutInflater(
        postInflateListener: (resourceId: Int, resourceName: String, rootView: View?) -> View?
    ) {
        val oldInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
        if (oldInflater is PhoneLayoutInflater) {
            oldInflater.postInflateListener = postInflateListener
        } else {
            val inflater = PhoneLayoutInflater(this).also {
                it.postInflateListener = postInflateListener
            }
            try {
                ContextThemeWrapper::class.set(this, "mInflater", inflater)
            } catch (e: Exception) {
                Log.e(Main.TAG, "hookLayoutInflater", e)
            }
        }
        val oldWindowInflater = window.layoutInflater
        if (oldWindowInflater is PhoneLayoutInflater) {
            oldWindowInflater.postInflateListener = postInflateListener
        } else {
            try {
                val phoneWindowClass =
                    Class.forName("com.android.internal.policy.PhoneWindow")
                if (phoneWindowClass.isInstance(window)) {
                    val inflater = PhoneLayoutInflater(this).also {
                        it.postInflateListener = postInflateListener
                    }
                    phoneWindowClass.set(window, "mLayoutInflater", inflater)
                }
            } catch (e: Exception) {
                Log.e(Main.TAG, "hookLayoutInflater", e)
            }
        }
    }

    private fun Application.initClassLoader() {
        try {
            if (transferClassLoader) {
                ClassLoader::class.set(
                    Hookworm::class.java.classLoader, "parent", this::class.java.classLoader
                )
            }
        } catch (e: Exception) {
            Log.e(Main.TAG, "initClassLoader", e)
        }
    }

    private fun initLibrary() {
        try {
            @Suppress("ConstantConditionIf")
            if (ModuleInfo.hasSOFile()) {
                "dalvik.system.BaseDexClassLoader".get<Any>(
                    Hookworm::class.java.classLoader, "pathList"
                )?.let { pathList ->
                    pathList::class.run {
                        val newDirectories = get<MutableList<File>>(
                            pathList, "nativeLibraryDirectories"
                        )!! + pathList::class.get<MutableList<File>>(
                            pathList, "systemNativeLibraryDirectories"
                        )!! + File(application.applicationInfo.dataDir, ModuleInfo.getSOPath())
                        set(
                            pathList, "nativeLibraryPathElements",
                            invoke<Any>(
                                pathList,
                                "makePathElements",
                                List::class to newDirectories
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(Main.TAG, "initLibrary", e)
        }
    }

    @SuppressLint("PrivateApi")
    private fun initGlobalLayoutInflater() {
        try {
            if (hookGlobalLayoutInflater && globalLayoutInflater == null) {
                "android.app.SystemServiceRegistry".get<MutableMap<String, Any>>(
                    null, "SYSTEM_SERVICE_FETCHERS"
                )?.let { fetchers ->
                    fetchers[Context.LAYOUT_INFLATER_SERVICE]?.let { layoutInflaterFetcher ->
                        fetchers[Context.LAYOUT_INFLATER_SERVICE] = Proxy.newProxyInstance(
                            ClassLoader.getSystemClassLoader(),
                            arrayOf(Class.forName("android.app.SystemServiceRegistry\$ServiceFetcher"))
                        ) { _, method, args ->
                            if (method.name == "getService") {
                                method.invoke(layoutInflaterFetcher, *args ?: arrayOf())
                                globalLayoutInflater
                                    ?: PhoneLayoutInflater(args[0] as Context?).also {
                                        globalLayoutInflater = it
                                        it.postInflateListener = postInflateListenerList[""]
                                    }
                            } else method.invoke(layoutInflaterFetcher, *args ?: arrayOf())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(Main.TAG, "initGlobalLayoutInflater", e)
        }
    }

    /**
     *  监听[Activity.onCreate]方法回调
     *  @param className 对应的Activity类名（完整类名）
     *  @param callback Callback lambda
     *
     *  @return [Application.ActivityLifecycleCallbacks]实例，可用来取消注册
     */
    fun registerOnActivityCreated(
        className: String, callback: (Activity, Bundle?) -> Unit
    ): Application.ActivityLifecycleCallbacks = object : SimpleActivityLifecycleCallbacks() {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity::class.java.name == className) {
                callback(activity, savedInstanceState)
            }
        }
    }.apply {
        if (::application.isInitialized) {
            application.registerActivityLifecycleCallbacks(this)
        } else {
            tempActivityLifecycleCallbacksList += this
        }
    }

    /**
     *  监听[Activity.onStart]方法回调
     *  @param className 对应的Activity类名（完整类名）
     *  @param callback Callback lambda
     *
     *  @return [Application.ActivityLifecycleCallbacks]实例，可用来取消注册
     */
    fun registerOnActivityStarted(
        className: String, callback: (Activity) -> Unit
    ): Application.ActivityLifecycleCallbacks = object : SimpleActivityLifecycleCallbacks() {
        override fun onActivityStarted(activity: Activity) {
            if (activity::class.java.name == className) {
                callback(activity)
            }
        }
    }.apply {
        if (::application.isInitialized) {
            application.registerActivityLifecycleCallbacks(this)
        } else {
            tempActivityLifecycleCallbacksList += this
        }
    }

    /**
     *  监听[Activity.onResume]方法回调
     *  @param className 对应的Activity类名（完整类名）
     *  @param callback Callback lambda
     *
     *  @return [Application.ActivityLifecycleCallbacks]实例，可用来取消注册
     */
    fun registerOnActivityResumed(
        className: String, callback: (Activity) -> Unit
    ): Application.ActivityLifecycleCallbacks = object : SimpleActivityLifecycleCallbacks() {
        override fun onActivityResumed(activity: Activity) {
            if (activity::class.java.name == className) {
                callback(activity)
            }
        }
    }.apply {
        if (::application.isInitialized) {
            application.registerActivityLifecycleCallbacks(this)
        } else {
            tempActivityLifecycleCallbacksList += this
        }
    }

    /**
     *  监听[Activity.onPause]方法回调
     *  @param className 对应的Activity类名（完整类名）
     *  @param callback Callback lambda
     *
     *  @return [Application.ActivityLifecycleCallbacks]实例，可用来取消注册
     */
    fun registerOnActivityPaused(
        className: String, callback: (Activity) -> Unit
    ): Application.ActivityLifecycleCallbacks = object : SimpleActivityLifecycleCallbacks() {
        override fun onActivityPaused(activity: Activity) {
            if (activity::class.java.name == className) {
                callback(activity)
            }
        }
    }.apply {
        if (::application.isInitialized) {
            application.registerActivityLifecycleCallbacks(this)
        } else {
            tempActivityLifecycleCallbacksList += this
        }
    }

    /**
     *  监听[Activity.onStop]方法回调
     *  @param className 对应的Activity类名（完整类名）
     *  @param callback Callback lambda
     *
     *  @return [Application.ActivityLifecycleCallbacks]实例，可用来取消注册
     */
    fun registerOnActivityStopped(
        className: String, callback: (Activity) -> Unit
    ): Application.ActivityLifecycleCallbacks = object : SimpleActivityLifecycleCallbacks() {
        override fun onActivityStopped(activity: Activity) {
            if (activity::class.java.name == className) {
                callback(activity)
            }
        }
    }.apply {
        if (::application.isInitialized) {
            application.registerActivityLifecycleCallbacks(this)
        } else {
            tempActivityLifecycleCallbacksList += this
        }
    }

    /**
     *  监听[Activity.onDestroy]方法回调
     *  @param className 对应的Activity类名（完整类名）
     *  @param callback Callback lambda
     *
     *  @return [Application.ActivityLifecycleCallbacks]实例，可用来取消注册
     */
    fun registerOnActivityDestroyed(
        className: String, callback: (Activity) -> Unit
    ): Application.ActivityLifecycleCallbacks = object : SimpleActivityLifecycleCallbacks() {
        override fun onActivityDestroyed(activity: Activity) {
            if (activity::class.java.name == className) {
                callback(activity)
            }
        }
    }.apply {
        if (::application.isInitialized) {
            application.registerActivityLifecycleCallbacks(this)
        } else {
            tempActivityLifecycleCallbacksList += this
        }
    }

    /**
     *  监听[Activity.onSaveInstanceState]方法回调
     *  @param className 对应的Activity类名（完整类名）
     *  @param callback Callback lambda
     *
     *  @return [Application.ActivityLifecycleCallbacks]实例，可用来取消注册
     */
    fun registerOnActivitySaveInstanceState(
        className: String, callback: (Activity, Bundle) -> Unit
    ): Application.ActivityLifecycleCallbacks = object : SimpleActivityLifecycleCallbacks() {
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            if (activity::class.java.name == className) {
                callback(activity, outState)
            }
        }
    }.apply {
        if (::application.isInitialized) {
            application.registerActivityLifecycleCallbacks(this)
        } else {
            tempActivityLifecycleCallbacksList += this
        }
    }

    private class ActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {

        override fun onActivityCreated(
            activity: Activity, savedInstanceState: Bundle?
        ) {
            val className = activity::class.java.name
            hookLayoutInflater(className, activity)
            activities[className] = activity
            activityLifecycleCallbackList[className]
                ?.onActivityCreated(activity, savedInstanceState)
            activityLifecycleCallbackList[""]
                ?.onActivityCreated(activity, savedInstanceState)
        }

        override fun onActivityStarted(activity: Activity) {
            activityLifecycleCallbackList[activity::class.java.name]
                ?.onActivityStarted(activity)
            activityLifecycleCallbackList[""]?.onActivityStarted(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            activityLifecycleCallbackList[activity::class.java.name]
                ?.onActivityResumed(activity)
            activityLifecycleCallbackList[""]?.onActivityResumed(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            activityLifecycleCallbackList[activity::class.java.name]
                ?.onActivityPaused(activity)
            activityLifecycleCallbackList[""]?.onActivityPaused(activity)
        }

        override fun onActivityStopped(activity: Activity) {
            activityLifecycleCallbackList[activity::class.java.name]
                ?.onActivityStopped(activity)
            activityLifecycleCallbackList[""]?.onActivityStopped(activity)
        }

        override fun onActivityDestroyed(activity: Activity) {
            val className = activity::class.java.name
            activities.remove(className)
            activityLifecycleCallbackList[className]
                ?.onActivityDestroyed(activity)
            activityLifecycleCallbackList[""]?.onActivityDestroyed(activity)
        }

        override fun onActivitySaveInstanceState(
            activity: Activity, outState: Bundle
        ) {
            activityLifecycleCallbackList[activity::class.java.name]
                ?.onActivitySaveInstanceState(activity, outState)
            activityLifecycleCallbackList[""]
                ?.onActivitySaveInstanceState(activity, outState)
        }
    }
}
