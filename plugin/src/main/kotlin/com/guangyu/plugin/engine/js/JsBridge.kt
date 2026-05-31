package com.guangyu.plugin.engine.js

import android.util.Log
import com.guangyu.plugin.engine.http.HttpClient
import com.guangyu.plugin.engine.model.BookSource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ImporterTopLevel
import java.util.concurrent.ConcurrentHashMap

object JsBridge {
    private const val TAG = "JsBridge"
    private val scopeCache = ConcurrentHashMap<String, ScriptableObject>()
    private val configStore = ConcurrentHashMap<String, String>()
    private val jsLibExecuted = ConcurrentHashMap<String, Boolean>()

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
            val scope = getScope(source, baseUrl)
            putStandardBindings(cx, scope, source, baseUrl, content, result)
            return cx.evaluateString(scope, jsStr, "legado_js", 1, null)
        } catch (e: Exception) {
            Log.e(TAG, "evalJS error: ${e.message}")
            return null
        } finally {
            Context.exit()
        }
    }

    private fun getScope(source: BookSource?, baseUrl: String?): ScriptableObject {
        val key = source?.bookSourceUrl ?: "_default"
        val scope = scopeCache.getOrPut(key) {
            val cx = Context.enter()
            try {
                cx.optimizationLevel = -1
                ImporterTopLevel(cx)
            } finally {
                Context.exit()
            }
        }
        // 执行 jsLib 预加载（仅一次）
        if (jsLibExecuted.putIfAbsent(key, true) == null) {
            source?.jsLib?.let { jsLib ->
                if (jsLib.isNotBlank()) {
                    val cx = Context.enter()
                    try {
                        cx.optimizationLevel = -1
                        cx.languageVersion = Context.VERSION_ES6
                        val java = JsJavaBridge(source, baseUrl)
                        ScriptableObject.putProperty(scope, "java", Context.javaToJS(java, scope))
                        ScriptableObject.putProperty(scope, "source", Context.javaToJS(java, scope))
                        ScriptableObject.putProperty(scope, "baseUrl", Context.javaToJS(baseUrl ?: "", scope))
                        ScriptableObject.putProperty(scope, "cookie", Context.javaToJS(CookieStore, scope))
                        ScriptableObject.putProperty(scope, "cache", Context.javaToJS(ConfigCache, scope))
                        // 注入 request() 全局函数
                        injectGlobalFunctions(cx, scope, java)
                        // 预执行 jsLib
                        cx.evaluateString(scope, jsLib, "jsLib_${source.bookSourceName}", 1, null)
                        Log.i(TAG, "jsLib executed for: ${source.bookSourceName}")
                    } catch (e: Exception) {
                        Log.e(TAG, "jsLib execution error: ${e.message}")
                    } finally {
                        Context.exit()
                    }
                }
            }
        }
        return scope
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

    /**
     * 注入 Legado 全局函数 request(), BaseUrl(), getVariable() 等
     * 这些函数在 jsLib 中用 function 定义，但有些 Legado 版本也将它们作为全局注入
     */
    private fun injectGlobalFunctions(cx: Context, scope: ScriptableObject, java: JsJavaBridge) {
        // request(url, method, body) - 书源 JS 最常用的函数
        val requestFn = object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any? {
                val url = args.getOrNull(0)?.toString() ?: return null
                val method = args.getOrNull(1)?.toString() ?: "GET"
                val body = args.getOrNull(2)
                return java.request(url, method, body)
            }
        }
        ScriptableObject.putProperty(scope, "request", Context.javaToJS(requestFn, scope))
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