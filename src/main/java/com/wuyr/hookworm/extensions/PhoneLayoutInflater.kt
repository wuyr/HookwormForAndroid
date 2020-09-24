package com.wuyr.hookworm.extensions

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.xmlpull.v1.XmlPullParser

/**
 * @author wuyr
 * @github https://github.com/wuyr/HookwormForAndroid
 * @since 2020-09-20 下午5:29
 */
class PhoneLayoutInflater : LayoutInflater {

    constructor(context: Context?) : super(context)
    constructor(original: LayoutInflater?, newContext: Context?) : super(original, newContext)

    /**
     * 对直接传XmlPullParser参数的inflate方法无效
     */
    var preInflateListener: ((layoutId: Int, root: ViewGroup?, attachToRoot: Boolean)
    -> Triple<Int, ViewGroup, Boolean>)? = null

    /**
     * 在inflate完成后回调
     */
    var postInflateListener: ((rootView: View?) -> View?)? = null

    private companion object {
        private val sClassPrefixList = arrayOf("android.widget.", "android.webkit.", "android.app.")
    }

    override fun cloneInContext(newContext: Context?) =
        PhoneLayoutInflater(this, newContext)

    @Throws(ClassNotFoundException::class)
    override fun onCreateView(name: String?, attrs: AttributeSet?): View? =
        sClassPrefixList.forEach { prefix ->
            try {
                createView(name, prefix, attrs)?.let { return it }
            } catch (e: Exception) {
            }
        }.run { super.onCreateView(name, attrs) }

    override fun inflate(resource: Int, root: ViewGroup?, attachToRoot: Boolean): View =
        preInflateListener?.invoke(resource, root, attachToRoot)?.let {
            super.inflate(it.first, it.second, it.third)
        } ?: super.inflate(resource, root, attachToRoot)

    override fun inflate(parser: XmlPullParser?, root: ViewGroup?, attachToRoot: Boolean): View? {
        val rootView = super.inflate(parser, root, attachToRoot)
        return postInflateListener?.invoke(rootView) ?: rootView
    }
}
