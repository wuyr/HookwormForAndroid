package com.wuyr.hookworm.extensions

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.wuyr.hookworm.core.Main
import org.xmlpull.v1.XmlPullParser
import kotlin.jvm.Throws

/**
 * @author wuyr
 * @github https://github.com/wuyr/HookwormForAndroid
 * @since 2020-09-20 下午5:29
 */
class PhoneLayoutInflater : LayoutInflater {

    constructor(context: Context?) : super(context)
    constructor(original: LayoutInflater?, newContext: Context?) : super(original, newContext) {
        if (original is PhoneLayoutInflater) {
            postInflateListener = original.postInflateListener
        }
    }

    /**
     * 在inflate完成后回调
     */
    var postInflateListener: ((resourceId: Int, resourceName: String, rootView: View?) -> View?)? =
        null

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

    private var currentResourceId = 0
    private var currentResourceName = ""

    override fun inflate(resource: Int, root: ViewGroup?, attachToRoot: Boolean): View {
        currentResourceId = resource
        currentResourceName = context.resources.getResourceEntryName(resource)
        return super.inflate(resource, root, attachToRoot)
    }

    override fun inflate(parser: XmlPullParser?, root: ViewGroup?, attachToRoot: Boolean): View? {
        val rootView = super.inflate(parser, root, attachToRoot)
        return (postInflateListener?.invoke(currentResourceId, currentResourceName, rootView)
            ?: rootView).also {
            currentResourceId = 0
            currentResourceName = ""
        }
    }
}
