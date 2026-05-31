package com.guangyu.plugin.engine.js

import android.util.Log
import com.guangyu.plugin.engine.http.HttpClient
import com.guangyu.plugin.engine.model.BookSource
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64

@Suppress("unused")
class JsJavaBridge(
    private val source: BookSource? = null,
    private val baseUrl: String? = null
) {
    companion object {
        private const val TAG = "JsJavaBridge"
    }

    fun ajax(url: Any): String? {
        val urlStr = url.toString()
        return try {
            val request = Request.Builder()
                .url(urlStr)
                .headers(okhttp3.Headers.headersOf(*(source?.getHeaderMap()?.flatMap { listOf(it.key, it.value) }?.toTypedArray() ?: emptyArray())))
                .get()
                .build()
            HttpClient.execute(request).body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "ajax($urlStr) error: ${e.message}")
            null
        }
    }

    fun ajax(url: Any, options: Any): String? {
        val urlStr = url.toString()
        return try {
            val builder = Request.Builder().url(urlStr)
            source?.getHeaderMap()?.let { headers -> headers.forEach { builder.header(it.key, it.value) } }
            if (options is Map<*, *>) {
                val method = options["method"]?.toString()?.uppercase() ?: "GET"
                if (method == "POST") {
                    val body = options["body"]
                    val bodyStr = if (body is Map<*, *>) JSONObject(body).toString() else body?.toString() ?: "{}"
                    builder.post(okhttp3.RequestBody.create("application/json; charset=utf-8".toMediaType(), bodyStr))
                }
            }
            HttpClient.execute(builder.build()).body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "ajax($urlStr) error: ${e.message}")
            null
        }
    }

    fun base64Encode(str: String): String {
        return Base64.getEncoder().encodeToString(str.toByteArray(Charsets.UTF_8))
    }

    fun base64Decode(str: String): String {
        return try { String(Base64.getDecoder().decode(str), Charsets.UTF_8) } catch (_: Exception) { str }
    }

    fun md5(str: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(str.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256(str: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(str.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun encodeURI(str: String): String = URLEncoder.encode(str, "UTF-8").replace("+", "%20")
    fun encodeURIComponent(str: String): String = URLEncoder.encode(str, "UTF-8")
    fun decodeURI(str: String): String = java.net.URLDecoder.decode(str, "UTF-8")

    fun log(msg: String) { Log.d(TAG, msg) }
    fun log(tag: String, msg: String) { Log.d(tag, msg) }
    fun toast(msg: String) { Log.i(TAG, "Toast: $msg") }
    fun longToast(msg: String) { Log.i(TAG, "LongToast: $msg") }

    fun get(key: String): String = JsBridge.getConfig("v_${source?.bookSourceUrl ?: ""}_$key") ?: ""
    fun put(key: String, value: String) { JsBridge.setConfig("v_${source?.bookSourceUrl ?: ""}_$key", value) }
    fun getVariable(key: String): String = JsBridge.getConfig("sourceVariable_${source?.bookSourceUrl ?: ""}") ?: ""
    fun putVariable(value: String) { JsBridge.setConfig("sourceVariable_${source?.bookSourceUrl ?: ""}", value) }

    fun getCookie(domain: String, name: String): String {
        val cookies = JsBridge.CookieStore.getCookie(domain)
        cookies.split(";").forEach { c ->
            val parts = c.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0].trim() == name) return parts[1].trim()
        }
        return ""
    }

    fun parseJson(str: String): Any? {
        return try {
            val trimmed = str.trim()
            if (trimmed.startsWith("[")) JSONArray(trimmed)
            else JSONObject(trimmed)
        } catch (_: Exception) { null }
    }

    fun startBrowser(url: String, title: String = ""): String { return url }

    fun checkEnv(): String = "安卓"

    fun getVariable(): String = JsBridge.getConfig("sourceVariable_${source?.bookSourceUrl ?: ""}") ?: ""
}
