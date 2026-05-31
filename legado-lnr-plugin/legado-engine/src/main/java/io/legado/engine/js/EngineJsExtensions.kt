package io.legado.engine.js

import io.legado.engine.provider.CacheProvider
import io.legado.engine.provider.ConfigProvider
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.Base64

/**
 * JS 扩展函数 - 供书源 JS 调用的工具方法
 * 移植自 lyc486 版 Legado JsExtensions + SourceLoginJsExtensions
 */
@Suppress("unused")
class EngineJsExtensions(
    private val cacheProvider: CacheProvider? = null,
    private val configProvider: ConfigProvider? = null,
    private val logger: io.legado.engine.provider.Logger? = null,
    private val loginCallback: LoginCallback? = null
) {

    /**
     * 登录 UI 回调接口
     */
    interface LoginCallback {
        fun upLoginData(data: Map<String, Any?>?)
        fun reLoginView(deltaUp: Boolean = false)
        fun refreshExplore()
        fun refreshBookInfo()
        fun refreshBookToc()
        fun refreshContent()
        fun copyText(text: String)
    }

    // ===== Base64 编解码 =====
    fun base64Encode(str: String): String = Base64.getEncoder().encodeToString(str.toByteArray(Charsets.UTF_8))
    fun base64Decode(str: String): String = String(Base64.getDecoder().decode(str), Charsets.UTF_8)
    fun base64EncodeToByteArray(str: String): ByteArray = Base64.getEncoder().encode(str.toByteArray(Charsets.UTF_8))
    fun base64DecodeToByteArray(str: String): ByteArray = Base64.getDecoder().decode(str)

    // ===== URL 编码 =====
    fun encodeURI(str: String): String = URLEncoder.encode(str, "UTF-8").replace("+", "%20")
        .replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~")
    fun encodeURIComponent(str: String): String = URLEncoder.encode(str, "UTF-8").replace("+", "%20")

    // ===== HTML 解析 =====
    fun parseHtml(html: String): org.jsoup.nodes.Document = Jsoup.parse(html)

    // ===== 缓存操作 =====
    fun getCache(key: String): String? = cacheProvider?.get(key)
    fun setCache(key: String, value: String) { cacheProvider?.put(key, value) }
    fun setCacheWithExpiry(key: String, value: String, expiryMillis: Long) { cacheProvider?.putWithExpiry(key, value, expiryMillis) }
    fun deleteCache(key: String) { cacheProvider?.delete(key) }

    // ===== 配置操作 =====
    fun getConfig(key: String, default: String = ""): String = configProvider?.getString(key, default) ?: default
    fun setConfig(key: String, value: String) { configProvider?.setString(key, value) }
    fun getConfigBoolean(key: String, default: Boolean = false): Boolean = configProvider?.getBoolean(key, default) ?: default
    fun setConfigBoolean(key: String, value: Boolean) { configProvider?.setBoolean(key, value) }

    // ===== 字符串工具 =====
    fun md5(str: String): String = java.security.MessageDigest.getInstance("MD5").digest(str.toByteArray()).joinToString("") { "%02x".format(it) }
    fun sha1(str: String): String = java.security.MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).joinToString("") { "%02x".format(it) }
    fun sha256(str: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(str.toByteArray()).joinToString("") { "%02x".format(it) }

    // ===== JSON 工具 =====
    fun toJson(obj: Any): String = com.google.gson.Gson().toJson(obj)
    fun <T> fromJson(json: String, clazz: Class<T>): T? = try { com.google.gson.Gson().fromJson(json, clazz) } catch (_: Exception) { null }

    // ===== 登录 UI JS 扩展（移植自 SourceLoginJsExtensions） =====
    fun upLoginData(data: Map<String, Any?>?) { loginCallback?.upLoginData(data) }
    fun reLoginView(deltaUp: Boolean = false) { loginCallback?.reLoginView(deltaUp) }
    fun refreshExplore() { loginCallback?.refreshExplore() }
    fun refreshBookInfo() { loginCallback?.refreshBookInfo() }
    fun refreshBookToc() { loginCallback?.refreshBookToc() }
    fun refreshContent() { loginCallback?.refreshContent() }
    fun copyText(text: String) { loginCallback?.copyText(text) }

    // ===== 日志 =====
    fun log(msg: String) { logger?.d("JsEngine", msg) }
    fun logError(msg: String) { logger?.e("JsEngine", msg) }
}
