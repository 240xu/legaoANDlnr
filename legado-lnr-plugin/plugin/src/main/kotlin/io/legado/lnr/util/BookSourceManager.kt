package io.legado.lnr.util

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import io.legado.engine.entity.BookSource
import io.legado.engine.http.HttpClient
import io.legado.engine.js.JsEngine
import io.legado.engine.provider.CacheProvider
import io.legado.engine.provider.ConfigProvider
import io.legado.engine.provider.Logger

/**
 * 书源管理器 - 负责加载、存储、管理 Legado JSON 书源
 */
object BookSourceManager {
    private const val TAG = "BookSourceManager"
    private const val PREFS_NAME = "legado_sources"
    private const val KEY_SOURCES = "sources_json"

    private val sources = mutableListOf<BookSource>()
    private val httpClient = HttpClient(object : Logger {
        override fun d(tag: String, msg: String) { Log.d(tag, msg) }
        override fun i(tag: String, msg: String) { Log.i(tag, msg) }
        override fun w(tag: String, msg: String) { Log.w(tag, msg) }
        override fun e(tag: String, msg: String, throwable: Throwable?) { Log.e(tag, msg, throwable) }
    })
    private val jsEngine = JsEngine(
        logger = object : Logger {
            override fun d(tag: String, msg: String) { Log.d(tag, msg) }
            override fun i(tag: String, msg: String) { Log.i(tag, msg) }
            override fun w(tag: String, msg: String) { Log.w(tag, msg) }
            override fun e(tag: String, msg: String, throwable: Throwable?) { Log.e(tag, msg, throwable) }
        },
        cacheProvider = MemoryCacheProvider(),
        configProvider = MemoryConfigProvider(),
        httpClient = httpClient
    )

    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
        loadSources()
    }

    fun init() {
        // 无 Context 初始化（仅用于测试）
        loadDefaultSources()
    }

    fun getHttpClient(): HttpClient = httpClient
    fun getJsEngine(): JsEngine = jsEngine

    fun getSourceCount(): Int = sources.size

    fun getAllSources(): List<BookSource> = sources.toList()

    fun getEnabledSources(): List<BookSource> = sources.filter { it.enabled }

    fun getSourceByUrl(url: String): BookSource? = sources.find { it.bookSourceUrl == url }

    /**
     * 根据书籍 ID 找到对应的书源
     * 书籍 ID 可能是 URL 或 data: URL
     */
    fun getSourceForBook(bookId: String): BookSource? {
        // 优先找第一个启用的源
        return sources.firstOrNull { it.enabled }
    }

    /**
     * 导入书源 JSON
     */
    fun importSources(json: String): Int {
        val newSources = BookSource.fromJsonArray(json)
        var count = 0
        for (source in newSources) {
            if (sources.none { it.bookSourceUrl == source.bookSourceUrl }) {
                sources.add(source)
                count++
            }
        }
        if (count > 0) {
            saveSources()
            Log.i(TAG, "导入 $count 个书源")
        }
        return count
    }

    /**
     * 加载默认书源（从 assets 或内嵌 JSON）
     */
    private fun loadDefaultSources() {
        // 尝试加载内嵌的书源
        try {
            val json = DEFAULT_SOURCE_JSON
            if (json.isNotBlank()) {
                importSources(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载默认书源失败: ${e.message}")
        }
    }

    private fun loadSources() {
        try {
            val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs?.getString(KEY_SOURCES, null)
            if (!json.isNullOrBlank()) {
                val loaded = BookSource.fromJsonArray(json)
                sources.clear()
                sources.addAll(loaded)
                Log.i(TAG, "加载 ${sources.size} 个书源")
            } else {
                loadDefaultSources()
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载书源失败: ${e.message}")
        }
    }

    private fun saveSources() {
        try {
            val json = Gson().toJson(sources)
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()?.putString(KEY_SOURCES, json)?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "保存书源失败: ${e.message}")
        }
    }

    /**
     * 内存缓存提供者
     */
    private class MemoryCacheProvider : CacheProvider {
        private val store = mutableMapOf<String, String>()
        override fun get(key: String): String? = store[key]
        override fun put(key: String, value: String) { store[key] = value }
        override fun putWithExpiry(key: String, value: String, expiryMillis: Long) { store[key] = value }
        override fun delete(key: String) { store.remove(key) }
        override fun clear() { store.clear() }
    }

    /**
     * 内存配置提供者
     */
    private class MemoryConfigProvider : ConfigProvider {
        private val store = mutableMapOf<String, Any>()
        override fun getBoolean(key: String, default: Boolean): Boolean = store[key] as? Boolean ?: default
        override fun setBoolean(key: String, value: Boolean) { store[key] = value }
        override fun getString(key: String, default: String): String = store[key]?.toString() ?: default
        override fun setString(key: String, value: String) { store[key] = value }
        override fun getInt(key: String, default: Int): Int = store[key] as? Int ?: default
        override fun setInt(key: String, value: Int) { store[key] = value }
    }

    // 内嵌默认书源 JSON（可选）
    private const val DEFAULT_SOURCE_JSON = ""
}