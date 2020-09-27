package com.wuyr.hookworm.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import com.wuyr.hookworm.extensions.PhoneLayoutInflater
import com.wuyr.hookworm.utils.invoke
import com.wuyr.hookworm.utils.set
import kotlin.concurrent.thread

/**
 * @author wuyr
 * @github https://github.com/wuyr/HookwormForAndroid
 * @since 2020-09-20 下午3:07
 */
object Hookworm {

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
     * Application实例初始化完毕的回调
     */
    @JvmStatic
    var onApplicationInitializedListener: ((Application) -> Unit)? = null

    private val activityLifecycleCallbackList =
        HashMap<String, Application.ActivityLifecycleCallbacks>()

    private val preInflateListenerList =
        HashMap<String, ((layoutId: Int, root: ViewGroup?, attachToRoot: Boolean) -> Triple<Int, ViewGroup?, Boolean>)?>()

    private var postInflateListenerList = HashMap<String, ((rootView: View?) -> View?)?>()

    /**
     * 在LayoutInflater加载布局前做手脚
     * 注意：此方法并不能拦截 LayoutInflater.inflate(XmlPullParser, ViewGroup, Boolean)
     *
     *  @param className 对应的Activity类名（完整类名），空字符串则表示拦截所有Activity的布局加载
     *  @param preInflateListener 用来接收回调的lambda，需返回：layoutId、parent、attachToRoot（可替换成自己想要的参数）
     */
    @JvmStatic
    fun registerPreInflateListener(
        className: String,
        preInflateListener: (layoutId: Int, root: ViewGroup?, attachToRoot: Boolean) -> Triple<Int, ViewGroup?, Boolean>
    ) {
        preInflateListenerList[className] = preInflateListener
        activities[className]?.also { activity ->
            val oldInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
            if (oldInflater is PhoneLayoutInflater) {
                if (oldInflater.preInflateListener != preInflateListener) {
                    oldInflater.preInflateListener = preInflateListener
                }
            } else {
                val inflater = PhoneLayoutInflater(
                    activity
                ).apply {
                    this.preInflateListener = preInflateListener
                }
                ContextThemeWrapper::class.set(activity, "mInflater", inflater)
            }
        }
    }

    /**
     *  取消（加载布局前）拦截LayoutInflater
     *
     *  @param className 对应的Activity类名（完整类名）
     */
    @JvmStatic
    fun unregisterPreInflateListener(className: String) {
        preInflateListenerList.remove(className)
        activities[className]?.let { activity ->
            val oldInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
            if (oldInflater is PhoneLayoutInflater) {
                oldInflater.preInflateListener = null
            }
        }
    }

    /**
     * 在LayoutInflater加载布局后拦截
     *
     *  @param className 对应的Activity类名（完整类名），空字符串则表示拦截所有Activity的布局加载
     *  @param postInflateListener 用来接收回调的lambda，需返回加载后的View（可在返回前对这个View做手脚）
     */
    @JvmStatic
    fun registerPostInflateListener(
        className: String, postInflateListener: (rootView: View?) -> View?
    ) {
        postInflateListenerList[className] = postInflateListener
        activities[className]?.also { activity ->
            val oldInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
            if (oldInflater is PhoneLayoutInflater) {
                if (oldInflater.postInflateListener != postInflateListener) {
                    oldInflater.postInflateListener = postInflateListener
                }
            } else {
                val inflater = PhoneLayoutInflater(
                    activity
                ).apply {
                    this.postInflateListener = postInflateListener
                }
                ContextThemeWrapper::class.set(activity, "mInflater", inflater)
            }
        }
    }

    /**
     *  取消（加载布局后）拦截LayoutInflater
     *
     *  @param className 对应的Activity类名（完整类名）
     */
    @JvmStatic
    fun unregisterPostInflateListener(className: String) {
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

    @JvmStatic
    fun init() {
        if (initialized) return
        initialized = true
        thread(isDaemon = true) {
            try {
                while (Looper.getMainLooper() == null) Thread.sleep(10)
                while ("android.app.ActivityThread".invoke<Any>(null, "currentApplication") == null
                ) Thread.sleep(10)
                "android.app.ActivityThread".invoke<Application>(null, "currentApplication")!!.run {
                    application = this
                    onApplicationInitializedListener?.invoke(this)
                    registerActivityLifecycleCallbacks(object :
                        Application.ActivityLifecycleCallbacks {

                        override fun onActivityCreated(
                            activity: Activity, savedInstanceState: Bundle?
                        ) {
                            val className = activity::class.java.name
                            if (preInflateListenerList.isNotEmpty() || postInflateListenerList.isNotEmpty()) {
                                val preInflateListener =
                                    preInflateListenerList[className] ?: preInflateListenerList[""]
                                val postInflateListener = postInflateListenerList[className]
                                    ?: postInflateListenerList[""]
                                if (preInflateListener != null || postInflateListener != null) {
                                    val oldInflater =
                                        activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
                                    if (oldInflater is PhoneLayoutInflater) {
                                        oldInflater.preInflateListener = preInflateListener
                                        oldInflater.postInflateListener = postInflateListener
                                    } else {
                                        val inflater = PhoneLayoutInflater(activity)
                                        inflater.preInflateListener = preInflateListener
                                        inflater.postInflateListener = postInflateListener
                                        ContextThemeWrapper::class.set(
                                            activity, "mInflater", inflater
                                        )
                                    }
                                }
                            }
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
                    })
                }
            } catch (e: Exception) {
                Log.e(Main.TAG, Log.getStackTraceString(e))
            }
        }
    }
}
