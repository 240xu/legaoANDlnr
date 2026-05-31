package io.legado.engine.js

import io.legado.engine.http.HttpClient

/**
 * Cookie JS 代理 - 暴露给 JS 的 cookie 对象
 * JS 中通过 cookie.getCookie(url) / cookie.setCookie(url, ck) / cookie.removeCookie(url) 调用
 */
@Suppress("unused")
class CookieJsProxy(private val httpClient: HttpClient?) {

    fun getCookie(urlOrHost: String): String {
        val domain = extractDomain(urlOrHost)
        return httpClient?.getCookies(domain) ?: ""
    }

    fun setCookie(urlOrHost: String, cookie: String) {
        val domain = extractDomain(urlOrHost)
        httpClient?.setCookie(domain, cookie)
    }

    fun removeCookie(urlOrHost: String) {
        val domain = extractDomain(urlOrHost)
        httpClient?.setCookie(domain, "")
    }

    private fun extractDomain(urlOrHost: String): String {
        return try {
            if (urlOrHost.startsWith("http")) java.net.URL(urlOrHost).host
            else urlOrHost
        } catch (_: Exception) { urlOrHost }
    }
}