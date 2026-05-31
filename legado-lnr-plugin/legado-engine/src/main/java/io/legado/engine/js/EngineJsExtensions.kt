package io.legado.engine.js

import io.legado.engine.entity.BaseSource
import io.legado.engine.http.HttpClient
import io.legado.engine.provider.CacheProvider
import io.legado.engine.provider.ConfigProvider
import io.legado.engine.provider.Logger
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.Base64

/**
 * JS 扩展方法 - 暴露给 Rhino JS 引擎
 * 移植自 lyc486 版 Legado JsExtensions + SourceLoginJsExtensions
 * JS 中通过 java.xxx() 调用
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class EngineJsExtensions(
    private val cacheProvider: CacheProvider? = null,
    private val configProvider: ConfigProvider? = null,
    private val logger: Logger? = null,
    private val loginCallback: LoginCallback? = null,
    private val httpClient: HttpClient? = null,
    private val source: BaseSource? = null
) {
    companion object {
        private const val TAG = "JsEngine"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    interface LoginCallback {
        fun upLoginData(data: Map<String, Any?>?)
        fun reLoginView(deltaUp: Boolean = false)
        fun refreshExplore()
        fun refreshBookInfo()
        fun refreshBookToc()
        fun refreshContent()
        fun copyText(text: String)
        fun openUrl(url: String)
        fun openBrowser(url: String, title: String = "")
        fun toast(msg: String)
        fun longToast(msg: String)
    }

    // ===== Base64 =====
    fun base64Encode(str: String): String =
        Base64.getEncoder().encodeToString(str.toByteArray(Charsets.UTF_8))

    fun base64Decode(str: String): String =
        String(Base64.getDecoder().decode(str), Charsets.UTF_8)

    fun base64EncodeToByteArray(str: String): ByteArray =
        Base64.getEncoder().encode(str.toByteArray(Charsets.UTF_8))

    fun base64DecodeToByteArray(str: String): ByteArray =
        Base64.getDecoder().decode(str)

    // ===== Hex 编解码 =====
    fun hexEncode(str: String): String =
        str.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

    fun hexDecodeToString(hex: String): String {
        val cleaned = hex.replace("\\s".toRegex(), "")
        val bytes = ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }

    // ===== URL 编码 =====
    fun encodeURI(str: String): String =
        URLEncoder.encode(str, "UTF-8").replace("+", "%20")
            .replace("%21", "!").replace("%27", "'")
            .replace("%28", "(").replace("%29", ")").replace("%7E", "~")

    fun encodeURIComponent(str: String): String =
        URLEncoder.encode(str, "UTF-8").replace("+", "%20")

    // ===== HTML =====
    fun parseHtml(html: String): org.jsoup.nodes.Document = Jsoup.parse(html)

    // ===== 缓存 =====
    fun getCache(key: String): String? = cacheProvider?.get(key)
    fun setCache(key: String, value: String) { cacheProvider?.put(key, value) }
    fun setCacheWithExpiry(key: String, value: String, expiryMillis: Long) {
        cacheProvider?.putWithExpiry(key, value, expiryMillis)
    }
    fun deleteCache(key: String) { cacheProvider?.delete(key) }

    // ===== 配置 =====
    fun getConfig(key: String, default: String = ""): String =
        configProvider?.getString(key, default) ?: default

    fun setConfig(key: String, value: String) { configProvider?.setString(key, value) }
    fun getConfigBoolean(key: String, default: Boolean = false): Boolean =
        configProvider?.getBoolean(key, default) ?: default

    fun setConfigBoolean(key: String, value: Boolean) { configProvider?.setBoolean(key, value) }

    // ===== 字符串工具 =====
    fun md5(str: String): String =
        java.security.MessageDigest.getInstance("MD5").digest(str.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun sha1(str: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(str.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun sha256(str: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(str.toByteArray())
            .joinToString("") { "%02x".format(it) }

    // ===== JSON =====
    fun toJson(obj: Any): String = com.google.gson.Gson().toJson(obj)
    fun <T> fromJson(json: String, clazz: Class<T>): T? =
        try { com.google.gson.Gson().fromJson(json, clazz) } catch (_: Exception) { null }

    // ===== HTTP 方法 =====

    /**
     * java.ajax(url) - 同步 HTTP 请求
     * URL 格式: "url" 或 "url,{options}"
     * options: {"method":"GET/POST","headers":{...},"body":"..."}
     */
    fun ajax(urlWithOptions: String): String? {
        return try {
            val parts = urlWithOptions.split(",", limit = 2)
            val url = parts[0].trim()
            var method = "GET"
            var body: String? = null
            val headers = mutableMapOf<String, String>()

            if (parts.size > 1) {
                try {
                    val opts = com.google.gson.Gson().fromJson(
                        parts[1].trim(), Map::class.java
                    ) as? Map<*, *>
                    opts?.forEach { (k, v) ->
                        when (k.toString().lowercase()) {
                            "method" -> method = v.toString().uppercase()
                            "body" -> body = if (v is Map<*, *>) com.google.gson.Gson().toJson(v) else v.toString()
                            "headers" -> {
                                if (v is Map<*, *>) {
                                    v.forEach { (hk, hv) ->
                                        if (hk != null && hv != null) {
                                            headers[hk.toString()] = hv.toString()
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 合并 source header
            source?.getHeaderMap()?.forEach { (k, v) ->
                if (k !in headers) headers[k] = v
            }

            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            if (method == "POST") {
                val requestBody = (body ?: "{}").toRequestBody(JSON_MEDIA_TYPE)
                requestBuilder.post(requestBody)
            }

            val response = httpClient?.client?.newCall(requestBuilder.build())?.execute()
            response?.body?.string()
        } catch (e: Exception) {
            logger?.e(TAG, "ajax 请求失败: $urlWithOptions", e)
            null
        }
    }

    fun get(urlStr: String, headers: Map<String, String>? = null): String? {
        return try {
            val response = httpClient?.get(urlStr, headers)
            response?.body?.string()
        } catch (e: Exception) { logger?.e(TAG, "GET 失败: $urlStr", e); null }
    }

    fun post(urlStr: String, body: String, headers: Map<String, String>? = null): String? {
        return try {
            val response = httpClient?.postJson(urlStr, body, headers)
            response?.body?.string()
        } catch (e: Exception) { logger?.e(TAG, "POST 失败: $urlStr", e); null }
    }

    // ===== Cookie 管理 =====
    fun getCookie(urlOrHost: String): String {
        val domain = extractDomain(urlOrHost)
        return httpClient?.getCookies(domain) ?: ""
    }

    fun getCookie(urlOrHost: String, name: String): String {
        val cookies = getCookie(urlOrHost)
        cookies.split(";").forEach { part ->
            val kv = part.trim().split("=", limit = 2)
            if (kv.size == 2 && kv[0].trim() == name) {
                return kv[1].trim()
            }
        }
        return ""
    }

    fun setCookie(urlOrHost: String, cookie: String) {
        val domain = extractDomain(urlOrHost)
        httpClient?.setCookie(domain, cookie)
    }

    fun removeCookie(urlOrHost: String) {
        val domain = extractDomain(urlOrHost)
        httpClient?.setCookie(domain, "")
    }

    // ===== 设备信息 =====
    fun androidId(): String = "lnr_plugin_device"

    /**
     * deviceID() - 在 Legado 中用于检测苹果环境
     * 苹萝端会返回设备 ID，安卓端会抛异常
     * 我们模拟安卓行为（抛异常）
     */
    fun deviceID(): String {
        throw UnsupportedOperationException("Not iOS environment")
    }

    /**
     * qread() - 轻阅读环境检测
     */
    fun qread(): String {
        throw UnsupportedOperationException("Not qread environment")
    }

    /**
     * getWebViewUA() - 获取 WebView User-Agent
     */
    fun getWebViewUA(): String =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

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
        loginCallback?.openUrl(urlStr ?: url)
    }

    fun startBrowser(url: String, title: String = "") {
        loginCallback?.openBrowser(url, title)
    }

    fun startBrowserAwait(url: String, title: String = ""): BrowserResult {
        loginCallback?.openBrowser(url, title)
        return BrowserResult("")
    }

    fun toast(msg: String) { loginCallback?.toast(msg) }
    fun longToast(msg: String) { loginCallback?.longToast(msg) }

    // ===== Header Map 操作 =====
    val headerMap: MutableMap<String, String> = mutableMapOf()

    // ===== URL 属性（供 JS 修改） =====
    var url: String = ""

    // ===== 日志 =====
    fun log(msg: String) { logger?.d(TAG, msg) }
    fun logError(msg: String) { logger?.e(TAG, msg) }

    // ===== 辅助 =====
    private fun extractDomain(urlOrHost: String): String {
        return try {
            if (urlOrHost.startsWith("http")) {
                java.net.URL(urlOrHost).host
            } else {
                urlOrHost
            }
        } catch (_: Exception) { urlOrHost }
    }

    /**
     * 浏览器结果包装（用于 startBrowserAwait）
     */
    class BrowserResult(private val body: String = "") {
        fun body(): String = body
    }
}