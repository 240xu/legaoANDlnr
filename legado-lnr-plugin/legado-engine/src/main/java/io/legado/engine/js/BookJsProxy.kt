package io.legado.engine.js

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.legado.engine.entity.BaseSource
import io.legado.engine.provider.CacheProvider

/**
 * Book JS 代理 - 暴露给 JS 的 book 对象
 * JS 中通过 book.getVariable(key) / book.setVariable(key, value) 等调用
 */
@Suppress("unused")
class BookJsProxy(
    private val source: BaseSource,
    private val cacheProvider: CacheProvider?
) {
    /** 当前章节索引（由宿主设置） */
    var durChapterIndex: Int = 0
    /** 当前章节 URL */
    var durChapterUrl: String = ""
    /** 阅读配置 */
    var readConfig: MutableMap<String, Any?> = mutableMapOf()

    fun getVariable(key: String): Any? {
        val raw = source.getVariable(cacheProvider)
        if (raw.isBlank()) return null
        return try {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = Gson().fromJson(raw, type)
            map[key]
        } catch (_: Exception) { null }
    }

    fun setVariable(key: String, value: Any?) {
        val raw = source.getVariable(cacheProvider)
        val map = try {
            if (raw.isNotBlank()) {
                val type = object : TypeToken<MutableMap<String, Any?>>() {}.type
                Gson().fromJson<MutableMap<String, Any?>>(raw, type) ?: mutableMapOf()
            } else mutableMapOf()
        } catch (_: Exception) { mutableMapOf() }
        map[key] = value
        source.putVariable(cacheProvider, Gson().toJson(map))
    }

    fun setUseReplaceRule(use: Boolean) {
        readConfig["useReplaceRule"] = use
    }

    fun getUseReplaceRule(): Boolean {
        return readConfig["useReplaceRule"] as? Boolean ?: false
    }
}