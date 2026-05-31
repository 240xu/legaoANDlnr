package com.guangyu.plugin.engine.js

import android.util.Log
import com.guangyu.plugin.engine.model.BookSource
import okhttp3.Request
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ImporterTopLevel
import java.util.concurrent.ConcurrentHashMap

object JsBridge {
    private const val TAG = "JsBridge"
    private val scopeCache = ConcurrentHashMap<String, ScriptableObject>()
    private val configStore = ConcurrentHashMap<String, String>()

    fun evalJS(
        jsStr: String,
        source: BookSource? = null,
        baseUrl: String? = null,
        content: Any? = null,
        result: Any? = null
    ): Any? {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6
            val scope = getScope(source)
            putStandardBindings(cx, scope, source, baseUrl, content, result)
            return cx.evaluateString(scope, jsStr, "legado_js", 1, null)
        } catch (e: Exception) {
            Log.e(TAG, "evalJS error: ${e.message}")
            return null
        } finally {
            Context.exit()
        }
    }

    private fun getScope(source: BookSource?): ScriptableObject {
        val key = source?.bookSourceUrl ?: "_default"
        return scopeCache.getOrPut(key) {
            val cx = Context.enter()
            try {
                cx.optimizationLevel = -1
                val scope = ImporterTopLevel(cx)
                scope
            } finally {
                Context.exit()
            }
        }
    }

    private fun putStandardBindings(
        cx: Context, scope: ScriptableObject,
        source: BookSource?, baseUrl: String?, content: Any?, result: Any?
    ) {
        val java = JsJavaBridge(source, baseUrl)
        ScriptableObject.putProperty(scope, "java", Context.javaToJS(java, scope))
        ScriptableObject.putProperty(scope, "result", Context.javaToJS(result, scope))
        ScriptableObject.putProperty(scope, "src", Context.javaToJS(content, scope))
        ScriptableObject.putProperty(scope, "baseUrl", Context.javaToJS(baseUrl ?: "", scope))
        ScriptableObject.putProperty(scope, "cookie", Context.javaToJS(CookieStore, scope))
        ScriptableObject.putProperty(scope, "cache", Context.javaToJS(ConfigCache, scope))
        ScriptableObject.putProperty(scope, "source", Context.javaToJS(java, scope))
    }

    fun setConfig(key: String, value: String) { configStore[key] = value }
    fun getConfig(key: String): String? = configStore[key]

    object CookieStore {
        private val cookies = ConcurrentHashMap<String, String>()
        fun getCookie(domain: String): String = cookies[domain] ?: ""
        fun setCookie(domain: String, cookie: String) { cookies[domain] = cookie }
        fun removeCookie(domain: String) { cookies.remove(domain) }
    }

    object ConfigCache {
        fun put(key: String, value: String) { configStore[key] = value }
        fun get(key: String): String? = configStore[key]
        fun delete(key: String) { configStore.remove(key) }
    }
}