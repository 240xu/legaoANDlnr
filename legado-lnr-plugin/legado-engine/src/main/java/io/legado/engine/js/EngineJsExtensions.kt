package io.legado.engine.js

import io.legado.engine.provider.CacheProvider
import io.legado.engine.provider.ConfigProvider
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.Base64

/**
 * JS 扩展函数 - 供书源 JS 调用的工具方法
 * 移植自 lyc486 版 Legado 的 JsExtensions
 * 不依赖 Android 平台
 */
@Suppress("unused")
class EngineJsExtensions(
    private val cacheProvider: CacheProvider? = null,
    private val configProvider: ConfigProvider? = null,
    private val logger: io.legado.engine.provider.Logger? = null
) {

    // ===== Base64 编解码 =====

    fun base64Encode(str: String): String {
        return Base64.getEncoder().encodeToString(str.toByteArray(Charsets.UTF_8))
    }

    fun base64Decode(str: String): String {
        return String(Base64.getDecoder().decode(str), Charsets.UTF_8)
    }

    fun base64EncodeToByteArray(str: String): ByteArray {
        return Base64.getEncoder().encode(str.toByteArray(Charsets.UTF_8))
    }

    fun base64DecodeToByteArray(str: String): ByteArray {
        return Base64.getDecoder().decode(str)
    }

    // ===== URL 编码 =====

    fun encodeURI(str: String): String {
        return URLEncoder.encode(str, "UTF-8")
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")
    }

    fun encodeURIComponent(str: String): String {
        return URLEncoder.encode(str, "UTF-8").replace("+", "%20")
    }

    // ===== HTML 解析 =====

    fun parseHtml(html: String): org.jsoup.nodes.Document {
        return Jsoup.parse(html)
    }

    // ===== 缓存操作 =====

    fun getCache(key: String): String? {
        return cacheProvider?.get(key)
    }

    fun setCache(key: String, value: String) {
        cacheProvider?.put(key, value)
    }

    fun setCacheWithExpiry(key: String, value: String, expiryMillis: Long) {
        cacheProvider?.putWithExpiry(key, value, expiryMillis)
    }

    fun deleteCache(key: String) {
        cacheProvider?.delete(key)
    }

    // ===== 配置操作 =====

    fun getConfig(key: String, default: String = ""): String {
        return configProvider?.getString(key, default) ?: default
    }

    fun setConfig(key: String, value: String) {
        configProvider?.setString(key, value)
    }

    fun getConfigBoolean(key: String, default: Boolean = false): Boolean {
        return configProvider?.getBoolean(key, default) ?: default
    }

    fun setConfigBoolean(key: String, value: Boolean) {
        configProvider?.setBoolean(key, value)
    }

    // ===== 字符串工具 =====

    fun md5(str: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(str.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha1(str: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val digest = md.digest(str.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256(str: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(str.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ===== JSON 工具 =====

    fun toJson(obj: Any): String {
        return com.google.gson.Gson().toJson(obj)
    }

    fun <T> fromJson(json: String, clazz: Class<T>): T? {
        return try {
            com.google.gson.Gson().fromJson(json, clazz)
        } catch (_: Exception) {
            null
        }
    }

    // ===== 日志（通过 Logger 接口） =====

    fun log(msg: String) {
        logger?.d("JsEngine", msg)
    }

    fun logError(msg: String) {
        logger?.e("JsEngine", msg)
    }
}
