@file:Suppress("UNCHECKED_CAST")

package com.wuyr.hookworm.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.forEach
import com.wuyr.hookworm.utils.get
import com.wuyr.hookworm.utils.invoke

/**
 * @author wuyr
 * @github https://github.com/wuyr/HookwormForAndroid
 * @since 2020-09-22 上午10:29
 */

/**
 * 根据资源id名来查找View实例
 *
 *  @param idName id名数组（即：可同时匹配多个id名）
 *  @return 对应的View，找不到即为null
 */
fun <V : View> Activity.findViewByIDName(vararg idName: String): V? {
    idName.forEach { name ->
        findViewById<V>(resources.getIdentifier(name, "id", packageName))?.let { return it }
    }
    return null
}

/**
 * 根据资源id名来查找所有对应的View实例
 *
 *  @param idName id名数组（即：可同时匹配多个id名）
 *  @return 对应的View集合
 */
fun Activity.findAllViewsByIDName(vararg idName: String): List<View> {
    val result = ArrayList<View>()
    fun fill(view: View) {
        idName.forEach { name ->
            if (view.id == resources.getIdentifier(name, "id", packageName)) result += view
        }
        if (view is ViewGroup) {
            view.forEach { fill(it) }
        }
    }
    fill(window.decorView)
    return result
}

/**
 * 根据资源id名来查找View实例
 *
 *  @param idName id名数组（即：可同时匹配多个id名）
 *  @return 对应的View，找不到即为null
 */
fun <V : View> View.findViewByIDName(vararg idName: String): V? {
    idName.forEach { name ->
        findViewById<V>(resources.getIdentifier(name, "id", context.packageName))?.let { return it }
    }
    return null
}

/**
 * 根据资源id名来查找所有对应的View实例
 *
 *  @param idName id名数组（即：可同时匹配多个id名）
 *  @return 对应的View集合
 */
fun View.findAllViewsByIDName(vararg idName: String): List<View> {
    val result = ArrayList<View>()
    fun fill(view: View) {
        idName.forEach { name ->
            if (view.id == resources.getIdentifier(name, "id", context.packageName)) result += view
        }
        if (view is ViewGroup) {
            view.forEach { fill(it) }
        }
    }
    fill(this)
    return result
}

/**
 * 根据显示的文本来查找View实例
 *
 *  @param textList 文本数组（即：可同时匹配多个文本）
 *  @return 对应的View，找不到即为null
 */
fun <V : View> Activity.findViewByText(vararg textList: String): V? {
    fun find(view: View): View? {
        if (view is TextView) {
            return if (textList.any { it == view.text.toString() }) view else null
        } else {
            val nodeText = view.createAccessibilityNodeInfo().text?.toString()
            if (textList.any { it == nodeText }) {
                return view
            }
            if (view is ViewGroup) {
                view.forEach { child -> find(child)?.let { return it } }
            }
        }
        return null
    }
    return find(window.decorView) as V
}

/**
 * 根据显示的文本来查找所有对应的View实例
 *
 *  @param textList 文本数组（即：可同时匹配多个文本）
 *  @return 对应的View集合
 */
fun Activity.findAllViewsByText(vararg textList: String): List<View> {
    val result = ArrayList<View>()
    fun fill(view: View) {
        if (view is TextView) {
            if (textList.any { it == view.text.toString() }) result += view
        } else {
            val nodeText = view.createAccessibilityNodeInfo().text?.toString()
            if (textList.any { it == nodeText }) result += view
            if (view is ViewGroup) {
                view.forEach { fill(it) }
            }
        }
    }
    fill(window.decorView)
    return result
}

/**
 * 根据显示的文本来查找View实例
 *
 *  @param textList 文本数组（即：可同时匹配多个文本）
 *  @return 对应的View，找不到即为null
 */
fun <V : View> View.findViewByText(vararg textList: String): V? {
    fun find(view: View): View? {
        if (view is TextView) {
            return if (textList.any { it == view.text.toString() }) view else null
        } else {
            val nodeText = view.createAccessibilityNodeInfo().text?.toString()
            if (textList.any { it == nodeText }) {
                return view
            }
            if (view is ViewGroup) {
                view.forEach { child -> find(child)?.let { return it } }
            }
        }
        return null
    }
    return find(this) as V
}

/**
 * 根据显示的文本来查找所有对应的View实例
 *
 *  @param textList 文本数组（即：可同时匹配多个文本）
 *  @return 对应的View集合
 */
fun View.findAllViewsByText(vararg textList: String): List<View> {
    val result = ArrayList<View>()
    fun fill(view: View) {
        if (view is TextView) {
            if (textList.any { it == view.text.toString() }) result += view
        } else {
            val nodeText = view.createAccessibilityNodeInfo().text?.toString()
            if (textList.any { it == nodeText }) result += view
            if (view is ViewGroup) {
                view.forEach { fill(it) }
            }
        }
    }
    fill(this)
    return result
}

/**
 * 检测目标View是否包含某些文本
 *
 * @param targetText 要检测的文本集合（可同时检测多个）
 * @param recursive 是否递归查找
 * @return 有找到则返回true，反之false
 */
fun View.containsText(vararg targetText: String, recursive: Boolean = false): Boolean {
    val identifier = if (this is TextView) {
        if (text.isNullOrEmpty()) {
            if (hint.isNullOrEmpty()) "" else hint.toString()
        } else text.toString()
    } else createAccessibilityNodeInfo().text?.toString() ?: ""

    targetText.forEach { t -> if (identifier.contains(t)) return true }
    if (recursive && this is ViewGroup) {
        forEach { if (it.containsText(*targetText, recursive = true)) return true }
    }
    return false
}

/**
 * 设置目标View的点击代理
 *
 * @param proxyListener 点击回调lambda，参数oldListener即为原来的OnClickListener实例
 */
fun View.setOnClickProxy(proxyListener: (view: View, oldListener: View.OnClickListener?) -> Unit) {
    if (!isClickable) isClickable = true
    val oldListener = View::class.invoke<Any>(this, "getListenerInfo")?.let {
        it::class.get<View.OnClickListener>(it, "mOnClickListener")
    }
    setOnClickListener { proxyListener(it, oldListener) }
}

/**
 * 设置目标View的长按代理
 *
 * @param proxyListener 长按回调lambda，参数oldListener即为原来的OnLongClickListener实例
 */
fun View.setOnLongClickProxy(proxyListener: (view: View, oldListener: View.OnLongClickListener?) -> Boolean) {
    if (!isLongClickable) isLongClickable = true
    val oldListener = View::class.invoke<Any>(this, "getListenerInfo")?.let {
        it::class.get<View.OnLongClickListener>(it, "mOnLongClickListener")
    }
    setOnLongClickListener { proxyListener(it, oldListener) }
}

/**
 * 设置目标View的触摸代理
 *
 * @param proxyListener 触摸回调lambda，参数oldListener即为原来的OnTouchListener实例
 */
@SuppressLint("ClickableViewAccessibility")
fun View.setOnTouchProxy(proxyListener: (view: View, event: MotionEvent, oldListener: View.OnTouchListener?) -> Boolean) {
    val oldListener = View::class.invoke<Any>(this, "getListenerInfo")?.let {
        it::class.get<View.OnTouchListener>(it, "mOnTouchListener")
    }
    setOnTouchListener { v, event ->
        proxyListener(v, event, oldListener)
    }
}
