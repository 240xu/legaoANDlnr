package io.legado.engine.http

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * HTTP 客户端封装 - 基于 OkHttp
 * 支持 Cookie 管理、自定义 Headers、超时配置
 */
class HttpClient(
    private val logger: io.legado.engine.provider.Logger? = null
) {

    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }
            override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            logger?.d("HttpClient", "${request.method} ${request.url}")
            val response = chain.proceed(request)
            logger?.d("HttpClient", "${response.code} ${request.url}")
            response
        })
        .build()

    /**
     * 设置指定域名的 Cookie
     */
    fun setCookie(domain: String, cookie: String) {
        try {
            val httpUrl = "https://$domain/".toHttpUrl()
            val cookies = cookie.split(";").mapNotNull { part ->
                Cookie.parse(httpUrl, part.trim())
            }
            cookieStore[domain] = cookies
        } catch (e: Exception) {
            logger?.e("HttpClient", "设置 Cookie 失败: $domain", e)
        }
    }

    /**
     * 获取指定域名的 Cookie 字符串
     */
    fun getCookies(domain: String): String {
        return cookieStore[domain]?.joinToString("; ") { "${it.name}=${it.value}" } ?: ""
    }

    /**
     * GET 请求
     */
    fun get(url: String, headers: Map<String, String>? = null): Response {
        val builder = Request.Builder().url(url).get()
        headers?.forEach { (key, value) -> builder.header(key, value) }
        return client.newCall(builder.build()).execute()
    }

    /**
     * POST JSON 请求
     */
    fun postJson(url: String, json: String, headers: Map<String, String>? = null): Response {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val builder = Request.Builder().url(url).post(body)
        headers?.forEach { (key, value) -> builder.header(key, value) }
        return client.newCall(builder.build()).execute()
    }

    /**
     * POST Form 请求
     */
    fun postForm(url: String, formBody: Map<String, String>, headers: Map<String, String>? = null): Response {
        val bodyBuilder = FormBody.Builder()
        formBody.forEach { (key, value) -> bodyBuilder.add(key, value) }
        val builder = Request.Builder().url(url).post(bodyBuilder.build())
        headers?.forEach { (key, value) -> builder.header(key, value) }
        return client.newCall(builder.build()).execute()
    }
}
