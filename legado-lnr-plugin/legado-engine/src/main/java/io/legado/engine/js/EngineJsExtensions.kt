package io.legado.engine.js

import io.legado.engine.provider.CacheProvider
import io.legado.engine.provider.ConfigProvider
import io.legado.engine.http.HttpClient
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.Base64

/**
 * JS 扩展函数 - 供书源 JS 调用的工具方法
 * 完整移植自 lyc486 版 Legado JsExtensions + SourceLoginJsExtensions
 */
@Suppress("unused")
class EngineJsExtensions(
    private val cacheProvider: CacheProvider? = null,
    private val configProvider: ConfigProvider? = null,
    private val logger: io.legado.engine.provider.Logger? = null,
    private val loginCallback: LoginCallback? = null,
    private val httpClient: HttpClient? = null,
    private val source: io.legado.engine.entity.BaseSource? = null
) {

    interface LoginCallback {
        fun upLoginData(data: Map<String, Any?>?)
        fun reLoginView(deltaUp: Boolean = false)
        fun refreshExplore()
        fun refreshBookInfo()
        fun refreshBookToc()
        fun refreshContent()
        fun copyText(text: String)
        fun openUrl(url: String)
        fun toast(msg: String)
    }

    // ===== Base64 =====
    fun base64Encode(str: String): String = Base64.getEncoder().encodeToString(str.toByteArray(Charsets.UTF_8))
    fun base64Decode(str: String): String = String(Base64.getDecoder().decode(str), Charsets.UTF_8)
    fun base64EncodeToByteArray(str: String): ByteArray = Base64.getEncoder().encode(str.toByteArray(Charsets.UTF_8))
    fun base64DecodeToByteArray(str: String): ByteArray = Base64.getDecoder().decode(str)

    // ===== URL 编码 =====
    fun encodeURI(str: String): String = URLEncoder.encode(str, "UTF-8").replace("+", "%20")
        .replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~")
    fun encodeURIComponent(str: String): String = URLEncoder.encode(str, "UTF-8").replace("+", "%20")

    // ===== HTML =====
    fun parseHtml(html: String): org.jsoup.nodes.Document = Jsoup.parse(html)

    // ===== 缓存 =====
    fun getCache(key: String): String? = cacheProvider?.get(key)
    fun setCache(key: String, value: String) { cacheProvider?.put(key, value) }
    fun setCacheWithExpiry(key: String, value: String, expiryMillis: Long) { cacheProvider?.putWithExpiry(key, value, expiryMillis) }
    fun deleteCache(key: String) { cacheProvider?.delete(key) }

    // ===== 配置 =====
    fun getConfig(key: String, default: String = ""): String = configProvider?.getString(key, default) ?: default
    fun setConfig(key: String, value: String) { configProvider?.setString(key, value) }
    fun getConfigBoolean(key: String, default: Boolean = false): Boolean = configProvider?.getBoolean(key, default) ?: default
    fun setConfigBoolean(key: String, value: Boolean) { configProvider?.setBoolean(key, value) }

    // ===== 字符串工具 =====
    fun md5(str: String): String = java.security.MessageDigest.getInstance("MD5").digest(str.toByteArray()).joinToString("") { "%02x".format(it) }
    fun sha1(str: String): String = java.security.MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).joinToString("") { "%02x".format(it) }
    fun sha256(str: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(str.toByteArray()).joinToString("") { "%02x".format(it) }

    // ===== JSON =====
    fun toJson(obj: Any): String = com.google.gson.Gson().toJson(obj)
    fun <T> fromJson(json: String, clazz: Class<T>): T? = try { com.google.gson.Gson().fromJson(json, clazz) } catch (_: Exception) { null }

    // ===== HTTP 方法（供 JS 调用） =====
    fun get(urlStr: String, headers: Map<String, String>? = null): String? {
        return try {
            val response = httpClient?.get(urlStr, headers)
            response?.body?.string()
        } catch (e: Exception) { logger?.e("JsEngine", "GET 失败: $urlStr", e); null }
    }

    fun post(urlStr: String, body: String, headers: Map<String, String>? = null): String? {
        return try {
            val response = httpClient?.postJson(urlStr, body, headers)
            response?.body?.string()
        } catch (e: Exception) { logger?.e("JsEngine", "POST 失败: $urlStr", e); null }
    }

    // ===== 登录 UI JS 扩展 =====
    fun upLoginData(data: Map<String, Any?>?) { loginCallback?.upLoginData(data) }
    fun reLoginView(deltaUp: Boolean = false) { loginCallback?.reLoginView(deltaUp) }
    fun refreshExplore() { loginCallback?.refreshExplore() }
    fun refreshBookInfo() { loginCallback?.refreshBookInfo() }
    fun refreshBookToc() { loginCallback?.refreshBookToc() }
    fun refreshContent() { loginCallback?.refreshContent() }
    fun copyText(text: String) { loginCallback?.copyText(text) }

    // ===== 导航/交互 =====
    fun open(url: String, urlStr: String? = null, title: String? = null) {
        // java.open("explore", url, title) - 打开发现页
        loginCallback?.openUrl(urlStr ?: url)
    }

    fun toast(msg: String) { loginCallback?.toast(msg) }

    // ===== Header Map 操作 =====
    val headerMap: MutableMap<String, String> = mutableMapOf()

    // ===== URL 属性（供 JS 修改） =====
    var url: String = ""

    // ===== 日志 =====
    fun log(msg: String) { logger?.d("JsEngine", msg) }
    fun logError(msg: String) { logger?.e("JsEngine", msg) }
}
