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
    private val configProvider: ConfigProvider? = null
) {

    companion object {
        private const val TAG = "JsEngine"
        private val scopeMap = ConcurrentHashMap<String, ScriptableObject>()
    }

    /**
     * 在沙箱中执行 JS 代码
     */
    fun eval(
        jsCode: String,
        source: BaseSource? = null,
        bindings: Map<String, Any?>? = null
    ): Any? {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1 // Android 不支持 JIT
            cx.languageVersion = Context.VERSION_ES6
            val scope = getScope(cx, source)
            bindings?.forEach { (key, value) ->
                ScriptableObject.putProperty(scope, key, Context.javaToJS(value, scope))
            }
            val jsExtensions = EngineJsExtensions(cacheProvider, configProvider, logger)
            ScriptableObject.putProperty(scope, "java", Context.javaToJS(jsExtensions, scope))
            ScriptableObject.putProperty(scope, "cache", Context.javaToJS(jsExtensions, scope))
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
            // 移除危险的全局对象
            val dangerousProps = listOf(
                "Packages", "javax", "org", "net", "android",
                "dalvik", "getClass", "exit", "quit"
            )
            for (prop in dangerousProps) {
                try {
                    ScriptableObject.deleteProperty(scope, prop)
                } catch (_: Exception) {}
            }
            scope
        }
    }

    fun clearScope(sourceUrl: String) {
        scopeMap.remove(sourceUrl)
    }

    fun clearAllScopes() {
        scopeMap.clear()
    }
}
