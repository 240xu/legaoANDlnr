package io.legado.engine.js

import io.legado.engine.entity.BaseSource
import io.legado.engine.provider.CacheProvider
import io.legado.engine.provider.ConfigProvider
import io.legado.engine.provider.Logger
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Rhino JS 引擎封装 - 移植自 lyc486 版 Legado
 * 使用 initSafeStandardObjects 提供安全沙箱
 */
class JsEngine(
    private val logger: Logger? = null,
    private val cacheProvider: CacheProvider? = null,
    private val configProvider: ConfigProvider? = null,
    private val httpClient: io.legado.engine.http.HttpClient? = null
) {

    companion object {
        private const val TAG = "JsEngine"
        private val scopeMap = ConcurrentHashMap<String, ScriptableObject>()
    }

    fun eval(
        jsCode: String,
        source: BaseSource? = null,
        bindings: Map<String, Any?>? = null,
        loginCallback: EngineJsExtensions.LoginCallback? = null
    ): Any? {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6
            val scope = getScope(cx, source)
            // 注入额外变量
            bindings?.forEach { (key, value) ->
                ScriptableObject.putProperty(scope, key, Context.javaToJS(value, scope))
            }
            // 注入 java/jsExtensions（包含所有 JS 可调用方法）
            val jsExtensions = EngineJsExtensions(cacheProvider, configProvider, logger, loginCallback, httpClient, source)
            ScriptableObject.putProperty(scope, "java", Context.javaToJS(jsExtensions, scope))
            ScriptableObject.putProperty(scope, "cache", Context.javaToJS(jsExtensions, scope))
            // 注入 source 对象（包含 put/get/setVariable/getVariable/getLoginInfo/putLoginInfo）
            if (source != null) {
                val sourceProxy = SourceJsProxy(source, cacheProvider)
                ScriptableObject.putProperty(scope, "source", Context.javaToJS(sourceProxy, scope))
            }
            // 注入基础变量
            ScriptableObject.putProperty(scope, "baseUrl", source?.sourceUrl ?: "")
            ScriptableObject.putProperty(scope, "cookie", "")
            return cx.evaluateString(scope, jsCode, "bookSource", 1, null)?.let {
                Context.jsToJava(it, Any::class.java)
            }
        } catch (e: Exception) {
            logger?.e(TAG, "JS 执行错误: ${e.message}", e)
            throw e
        } finally {
            Context.exit()
        }
    }

    private fun getScope(cx: Context, source: BaseSource?): ScriptableObject {
        val sourceKey = source?.sourceUrl ?: "_default"
        return scopeMap.getOrPut(sourceKey) {
            val scope = cx.initSafeStandardObjects(null, true)
            val dangerousProps = listOf("exit", "quit")
            for (prop in dangerousProps) {
                try { ScriptableObject.deleteProperty(scope, prop) } catch (_: Exception) {}
            }
            scope
        }
    }

    fun clearScope(sourceUrl: String) { scopeMap.remove(sourceUrl) }
    fun clearAllScopes() { scopeMap.clear() }
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
