package io.legado.lnr.util

import android.content.Context
import android.util.Log
import io.legado.engine.entity.BookSource
import io.legado.engine.http.HttpClient
import io.legado.engine.js.JsEngine
import io.legado.engine.provider.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * 书源管理器 - 加载、存储、管理 Legado JSON 书源
 */
object BookSourceManager {

    private const val TAG = "BookSourceManager"
    private val sources = ConcurrentHashMap<String, BookSource>()
    private var httpClient: HttpClient? = null
    private var jsEngine: JsEngine? = null
    private var logger: Logger = object : Logger {
        override fun d(tag: String, msg: String) { Log.d(tag, msg) }
        override fun i(tag: String, msg: String) { Log.i(tag, msg) }
        override fun w(tag: String, msg: String) { Log.w(tag, msg) }
        override fun e(tag: String, msg: String, throwable: Throwable?) { Log.e(tag, msg, throwable) }
    }

    fun init() {
        httpClient = HttpClient(logger)
        jsEngine = JsEngine(logger)
        Log.i(TAG, "BookSourceManager 初始化完成")
    }

    fun getHttpClient(): HttpClient = httpClient ?: HttpClient(logger).also { httpClient = it }
    fun getJsEngine(): JsEngine = jsEngine ?: JsEngine(logger).also { jsEngine = it }
    fun getLogger(): Logger = logger

    /**
     * 从 JSON 字符串导入书源
     */
    fun importFromJson(json: String): Int {
        var count = 0
        try {
            val list = BookSource.fromJsonArray(json)
            for (source in list) {
                if (source.bookSourceUrl.isNotBlank()) {
                    sources[source.bookSourceUrl] = source
                    count++
                    Log.i(TAG, "导入书源: ${source.bookSourceName} (${source.bookSourceUrl})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "导入书源失败: ${e.message}", e)
        }
        return count
    }

    /**
     * 获取所有已启用的书源
     */
    fun getEnabledSources(): List<BookSource> {
        return sources.values.filter { it.enabled }
    }

    /**
     * 获取所有书源
     */
    fun getAllSources(): List<BookSource> {
        return sources.values.toList()
    }

    /**
     * 根据 URL 获取书源
     */
    fun getSource(url: String): BookSource? {
        return sources[url]
    }

    /**
     * 获取书源数量
     */
    fun getSourceCount(): Int = sources.size

    /**
     * 启用/禁用书源
     */
    fun setEnabled(sourceUrl: String, enabled: Boolean) {
        sources[sourceUrl]?.enabled = enabled
    }

    /**
     * 删除书源
     */
    fun removeSource(sourceUrl: String) {
        sources.remove(sourceUrl)
    }

    /**
     * 清除所有书源
     */
    fun clearAll() {
        sources.clear()
    }
}
