package com.guangyu.plugin.engine.js

import android.util.Log
import com.guangyu.plugin.engine.http.HttpClient
import com.guangyu.plugin.engine.model.BookSource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64

@Suppress("unused")
class JsJavaBridge(
    private val source: BookSource? = null,
    private var baseUrl: String? = null
) {
    companion object {
        private const val TAG = "JsJavaBridge"
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    // ======================== 网络请求 ========================

    /**
     * ajax(url) / ajax(url, options)
     * url 可以是 "url,JSON_OPTIONS" 的格式（逗号分隔）
     */
    fun ajax(url: Any): String? = ajax(url, null)

    fun ajax(url: Any, options: Any?): String? {
        val raw = url.toString()
        // 处理 "url,{...}" 格式
        val (urlStr, extraOpts) = splitUrlAndOptions(raw)
        val mergedOpts = mergeOptions(extraOpts, options)

        return try {
            val actualUrl = resolveUrl(urlStr)
            val builder = Request.Builder().url(actualUrl)
            // 添加 source header
            source?.getHeaderMap()?.forEach { builder.header(it.key, it.value) }
            val method = mergedOpts["method"]?.toString()?.uppercase() ?: "GET"
            val headers = mergedOpts["headers"]
            if (headers is Map<*, *>) {
                headers.forEach { (k, v) -> if (k != null) builder.header(k.toString(), v.toString()) }
            }
            if (method == "POST") {
                val body = mergedOpts["body"]
                val bodyStr = when (body) {
                    is Map<*, *> -> JSONObject(body).toString()
                    is JSONObject -> body.toString()
                    else -> body?.toString() ?: "{}"
                }
                builder.post(bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType()))
            }
            val resp = HttpClient.execute(builder.build())
            resp.body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "ajax($raw) error: ${e.message}")
            null
        }
    }

    /**
     * request(url) / request(url, method) / request(url, method, body)
     * 书源 JS 中常用的简化请求函数
     */
    fun request(url: Any): String? = request(url, "GET", null)
    fun request(url: Any, method: String): String? = request(url, method, null)
    fun request(url: Any, method: String, body: Any?): String? {
        val urlStr = url.toString()
        return try {
            val actualUrl = resolveUrl(urlStr)
            val builder = Request.Builder().url(actualUrl)
            source?.getHeaderMap()?.forEach { builder.header(it.key, it.value) }
            if (method.equals("POST", true)) {
                val bodyStr = when (body) {
                    null -> "{}"
                    is Map<*, *> -> JSONObject(body).toString()
                    is JSONObject -> body.toString()
                    else -> body.toString()
                }
                builder.post(bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType()))
            }
            HttpClient.execute(builder.build()).body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "request($urlStr) error: ${e.message}")
            null
        }
    }

    // ======================== Base64 / Hex ========================

    fun base64Encode(str: String): String =
        Base64.getEncoder().encodeToString(str.toByteArray(Charsets.UTF_8))

    fun base64Decode(str: String): String =
        try { String(Base64.getDecoder().decode(str), Charsets.UTF_8) } catch (_: Exception) { str }

    fun hexEncode(str: String): String =
        str.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

    fun hexDecodeToString(hex: String): String {
        return try {
            val clean = hex.replace("\\s".toRegex(), "")
            val bytes = ByteArray(clean.length / 2)
            for (i in bytes.indices) {
                bytes[i] = Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16).toByte()
            }
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "hexDecode error: ${e.message}")
            hex
        }
    }

    // ======================== 编码 ========================

    fun md5(str: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        return md.digest(str.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun sha256(str: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(str.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun encodeURI(str: String): String = URLEncoder.encode(str, "UTF-8").replace("+", "%20")
    fun encodeURIComponent(str: String): String = URLEncoder.encode(str, "UTF-8")
    fun decodeURI(str: String): String = java.net.URLDecoder.decode(str, "UTF-8")

    // ======================== 日志 / 提示 ========================

    fun log(msg: String) { Log.d(TAG, "$msg") }
    fun log(tag: String, msg: String) { Log.d(tag, msg) }
    fun toast(msg: String) { Log.i(TAG, "Toast: $msg") }
    fun longToast(msg: String) { Log.i(TAG, "LongToast: $msg") }

    // ======================== 变量管理 ========================

    fun get(key: String): String = JsBridge.getConfig("v_${source?.bookSourceUrl ?: ""}_$key") ?: ""
    fun put(key: String, value: String) {
        JsBridge.setConfig("v_${source?.bookSourceUrl ?: ""}_$key", value)
    }

    /**
     * getVariable() -> 返回整个变量对象的JSON字符串
     * getVariable(key) -> 返回指定键的值
     * Legado 中 getVariable 是 BaseSource 上的方法，返回 JSON 字符串
     */
    fun getVariable(): String = JsBridge.getConfig("sourceVariable_${source?.bookSourceUrl ?: ""}") ?: "{}"
    fun getVariable(key: String): String = JsBridge.getConfig("sourceVariable_${source?.bookSourceUrl ?: ""}") ?: "{}"
    fun putVariable(value: String) {
        JsBridge.setConfig("sourceVariable_${source?.bookSourceUrl ?: ""}", value)
    }

    // ======================== Cookie ========================

    fun getCookie(domain: String, name: String): String {
        val cookies = JsBridge.CookieStore.getCookie(domain)
        cookies.split(";").forEach { c ->
            val parts = c.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0].trim() == name) return parts[1].trim()
        }
        return ""
    }

    // ======================== 设备信息 ========================

    fun getWebViewUA(): String = DEFAULT_UA
    fun deviceID(): String = JsBridge.getConfig("deviceId") ?: run {
        val id = java.util.UUID.randomUUID().toString()
        JsBridge.setConfig("deviceId", id)
        id
    }
    fun getPackageName(): String = "com.guangyu.plugin"

    // ======================== JSON ========================

    fun parseJson(str: String): Any? {
        return try {
            val trimmed = str.trim()
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONObject(trimmed)
                else -> null
            }
        } catch (_: Exception) { null }
    }

    // ======================== 浏览器 ========================

    fun startBrowser(url: String, title: String = ""): String = url
    fun startBrowserAwait(url: String, title: String = ""): BrowserResult = BrowserResult("")

    data class BrowserResult(val body: String)

    // ======================== 环境检测 ========================

    fun checkEnv(): String = "安卓"

    // ======================== 辅助方法 ========================

    private fun resolveUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val base = baseUrl ?: source?.bookSourceUrl ?: ""
        return if (url.startsWith("/")) {
            val hostEnd = base.indexOf("/", 8)
            if (hostEnd > 0) base.substring(0, hostEnd) + url else base + url
        } else {
            "$base/$url"
        }
    }

    private fun splitUrlAndOptions(raw: String): Pair<String, Map<String, Any>?> {
        // 处理 "url,{...}" 格式
        val commaIdx = raw.indexOf(",{")
        if (commaIdx > 0 && raw.endsWith("}")) {
            val urlPart = raw.substring(0, commaIdx).trim()
            val optPart = raw.substring(commaIdx + 1).trim()
            try {
                val obj = JSONObject(optPart)
                val map = mutableMapOf<String, Any>()
                obj.keys().forEach { k -> map[k] = obj.get(k) }
                return Pair(urlPart, map)
            } catch (_: Exception) {}
        }
        return Pair(raw, null)
    }

    private fun mergeOptions(a: Map<String, Any>?, b: Any?): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        a?.let { result.putAll(it) }
        if (b is Map<*, *>) {
            b.forEach { (k, v) -> if (k != null) result[k.toString()] = v!! }
        }
        return result
    }
}