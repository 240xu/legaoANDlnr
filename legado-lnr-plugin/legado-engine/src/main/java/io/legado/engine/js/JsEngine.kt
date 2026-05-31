package io.legado.engine.js

import io.legado.engine.entity.BaseSource
import io.legado.engine.http.HttpClient
import io.legado.engine.provider.CacheProvider
import io.legado.engine.provider.ConfigProvider
import io.legado.engine.provider.Logger
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.util.concurrent.ConcurrentHashMap

/**
 * Rhino JS 引擎封装 - 移植自 lyc486 版 Legado
 * 使用 initSafeStandardObjects + RhinoClassShutter 提供安全沙箱
 * 支持 jsLib 预执行、全局对象注入
 */
class JsEngine(
    private val logger: Logger? = null,
    private val cacheProvider: CacheProvider? = null,
    private val configProvider: ConfigProvider? = null,
    private val httpClient: HttpClient? = null
) {
    companion object {
        private const val TAG = "JsEngine"
        private val scopeMap = ConcurrentHashMap<String, ScriptableObject>()
        private val jsLibExecuted = ConcurrentHashMap<String, Boolean>()
    }

    /**
     * 执行 JS 代码
     * @param jsCode 要执行的 JS 代码
     * @param source 书源（可选）
     * @param bindings 额外绑定的变量
     * @param loginCallback 登录回调
     * @param jsLib jsLib 代码（首次执行时会预执行）
     */
    fun eval(
        jsCode: String,
        source: BaseSource? = null,
        bindings: Map<String, Any?>? = null,
        loginCallback: EngineJsExtensions.LoginCallback? = null,
        jsLib: String? = null
    ): Any? {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = getScope(cx, source)

            // 首次执行 jsLib
            val sourceKey = source?.sourceUrl ?: "_default"
            if (jsLib != null && jsLib.isNotBlank() && jsLibExecuted[sourceKey] != true) {
                executeJsLib(cx, scope, jsLib, source, loginCallback)
                jsLibExecuted[sourceKey] = true
            }

            // 注入额外变量
            bindings?.forEach { (key, value) ->
                ScriptableObject.putProperty(scope, key, toJsValue(value, scope))
            }

            // 注入 java/jsExtensions
            val jsExtensions = EngineJsExtensions(
                cacheProvider, configProvider, logger, loginCallback, httpClient, source
            )
            ScriptableObject.putProperty(scope, "java", Context.javaToJS(jsExtensions, scope))
            ScriptableObject.putProperty(scope, "cache", Context.javaToJS(jsExtensions, scope))

            // 注入 source 对象
            if (source != null) {
                val sourceProxy = SourceJsProxy(source, cacheProvider)
                ScriptableObject.putProperty(scope, "source", Context.javaToJS(sourceProxy, scope))

                // 注入 book 对象
                val bookProxy = BookJsProxy(source, cacheProvider)
                ScriptableObject.putProperty(scope, "book", Context.javaToJS(bookProxy, scope))
            }

            // 注入 cookie 对象
            val cookieProxy = CookieJsProxy(httpClient)
            ScriptableObject.putProperty(scope, "cookie", Context.javaToJS(cookieProxy, scope))

            // 注入基础变量
            ScriptableObject.putProperty(scope, "baseUrl", source?.sourceUrl ?: "")
            ScriptableObject.putProperty(scope, "result", bindings?.get("result") ?: "")

            return cx.evaluateString(scope, jsCode, "bookSource", 1, null)?.let {
                if (it is Undefined) null else Context.jsToJava(it, Any::class.java)
            }
        } catch (e: Exception) {
            logger?.e(TAG, "JS 执行错误: ${e.message}", e)
            throw e
        } finally {
            Context.exit()
        }
    }

    /**
     * 执行 jsLib - 定义全局函数如 getVariable, setVariable, BaseUrl, request 等
     */
    private fun executeJsLib(
        cx: Context,
        scope: ScriptableObject,
        jsLib: String,
        source: BaseSource?,
        loginCallback: EngineJsExtensions.LoginCallback?
    ) {
        try {
            // 注入 java/jsExtensions/source/cookie/book 到 scope
            val jsExtensions = EngineJsExtensions(
                cacheProvider, configProvider, logger, loginCallback, httpClient, source
            )
            ScriptableObject.putProperty(scope, "java", Context.javaToJS(jsExtensions, scope))
            ScriptableObject.putProperty(scope, "cache", Context.javaToJS(jsExtensions, scope))

            if (source != null) {
                val sourceProxy = SourceJsProxy(source, cacheProvider)
                ScriptableObject.putProperty(scope, "source", Context.javaToJS(sourceProxy, scope))
                val bookProxy = BookJsProxy(source, cacheProvider)
                ScriptableObject.putProperty(scope, "book", Context.javaToJS(bookProxy, scope))
            }

            val cookieProxy = CookieJsProxy(httpClient)
            ScriptableObject.putProperty(scope, "cookie", Context.javaToJS(cookieProxy, scope))

            // 执行 jsLib - 函数定义会成为 scope 的全局属性
            cx.evaluateString(scope, jsLib, "jsLib", 1, null)
            logger?.d(TAG, "jsLib 执行成功: ${source?.sourceUrl}")
        } catch (e: Exception) {
            logger?.e(TAG, "jsLib 执行失败: ${e.message}", e)
        }
    }

    private fun getScope(cx: Context, source: BaseSource?): ScriptableObject {
        val sourceKey = source?.sourceUrl ?: "_default"
        return scopeMap.getOrPut(sourceKey) {
            cx.optimizationLevel = -1 // 需要在设置 ClassShutter 之前
            try {
                val field = Context::class.java.getDeclaredField("classShutter")
                field.isAccessible = true
                field.set(cx, RhinoClassShutter())
            } catch (_: Exception) {
                // 如果反射失败，继续执行（安全性降低但仍可工作）
            }
            val scope = cx.initSafeStandardObjects(null, true)
            // 移除危险的全局函数
            val dangerousProps = listOf("exit", "quit", "importPackage", "importClass")
            for (prop in dangerousProps) {
                try { ScriptableObject.deleteProperty(scope, prop) } catch (_: Exception) {}
            }
            scope
        }
    }

    /**
     * 清除指定源的 scope 缓存（当 jsLib 变化时调用）
     */
    fun clearScope(sourceUrl: String) {
        scopeMap.remove(sourceUrl)
        jsLibExecuted.remove(sourceUrl)
    }

    fun clearAllScopes() {
        scopeMap.clear()
        jsLibExecuted.clear()
    }

    private fun toJsValue(value: Any?, scope: ScriptableObject): Any {
        return Context.javaToJS(value, scope)
    }
}

/**
 * Source JS 代理 - 暴露 source 方法给 JS
 * JS 中可调用 source.put()/source.get()/source.setVariable() 等
 */
class SourceJsProxy(
    private val source: BaseSource,
    private val cacheProvider: CacheProvider?
) {
    val key: String get() = source.sourceUrl
    val bookSourceUrl: String get() = source.sourceUrl
    val bookSourceName: String get() = source.sourceName

    fun put(key: String, value: String): String = source.put(cacheProvider, key, value)
    fun get(key: String): String = source.get(cacheProvider, key)
    fun setVariable(variable: String?) { source.putVariable(cacheProvider, variable) }
    fun getVariable(): String = source.getVariable(cacheProvider)
    fun getLoginInfo(): String? = source.getLoginInfo(cacheProvider)
    fun putLoginInfo(info: String) { source.putLoginInfo(cacheProvider, info) }
    fun removeLoginInfo() { source.removeLoginInfo(cacheProvider) }
    fun getLoginInfoMap(): MutableMap<String, String> = source.getLoginInfoMap(cacheProvider)
    fun getLoginJs(): String? = source.getLoginJs()
    fun getHeaderMap(): Map<String, String> = source.getHeaderMap()
}