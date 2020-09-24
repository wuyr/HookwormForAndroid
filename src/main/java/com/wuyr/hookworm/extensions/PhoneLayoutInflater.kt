package com.wuyr.hookworm.extensions

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import org.xmlpull.v1.XmlPullParser

/**
 * @author wuyr
 * @github https://github.com/wuyr/HookwormForAndroid
 * @since 2020-09-20 下午5:29
 */
@Suppress("SENSELESS_COMPARISON")
class PhoneLayoutInflater(original: LayoutInflater, newContext: Context?) :
    LayoutInflater(original, newContext) {

    private val originalLayoutInflater: LayoutInflater = original

    private var tempFactory: Factory? = null
    private var tempFactory2: Factory2? = null
    private var tempFilter: Filter? = null

    init {
        tempFactory?.let { originalLayoutInflater.factory = it }
        tempFactory2?.let { originalLayoutInflater.factory2 = it }
        tempFilter?.let { originalLayoutInflater.filter = it }
    }

    var preInflateListener: ((layoutId: Int, root: ViewGroup?, attachToRoot: Boolean)
    -> Triple<Int, ViewGroup, Boolean>)? = null
    var postInflateListener: ((rootView: View?) -> View?)? = null

    override fun cloneInContext(newContext: Context?) =
        PhoneLayoutInflater(originalLayoutInflater, newContext)

    override fun inflate(resource: Int, root: ViewGroup?, attachToRoot: Boolean): View =
        preInflateListener?.invoke(resource, root, attachToRoot)?.let {
            originalLayoutInflater.inflate(it.first, it.second, it.third)
        } ?: originalLayoutInflater.inflate(resource, root, attachToRoot)

    override fun inflate(parser: XmlPullParser?, root: ViewGroup?, attachToRoot: Boolean): View? {
        val rootView = originalLayoutInflater.inflate(parser, root, attachToRoot)
        return postInflateListener?.invoke(rootView) ?: rootView
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreateView(
        viewContext: Context, parent: View?, name: String, attrs: AttributeSet?
    ): View? = originalLayoutInflater.onCreateView(viewContext, parent, name, attrs)

    override fun setFactory(factory: Factory?) {
        if (originalLayoutInflater == null) {
            tempFactory = factory
        } else {
            originalLayoutInflater.factory = factory
        }
    }

    override fun setFactory2(factory2: Factory2?) {
        if (originalLayoutInflater == null) {
            tempFactory2 = factory2
        } else {
            originalLayoutInflater.factory2 = factory2
        }
    }

    override fun setFilter(filter: Filter?) {
        if (originalLayoutInflater == null) {
            tempFilter = filter
        } else {
            originalLayoutInflater.filter = filter
        }
    }

    override fun getContext(): Context? = originalLayoutInflater.context

    override fun getFilter(): Filter? = originalLayoutInflater.filter
}
