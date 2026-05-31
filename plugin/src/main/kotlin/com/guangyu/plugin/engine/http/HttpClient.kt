package com.guangyu.plugin.engine.http

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

object HttpClient {
    private const val TAG = "HttpClient"

    private val cookieJar = object : CookieJar {
        private val store = ConcurrentHashMap<String, List<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            store[url.host] = cookies
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return store[url.host] ?: emptyList()
        }
        fun getCookiesForHost(host: String): List<Cookie> = store[host] ?: emptyList()
    }

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, SecureRandom())
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    fun execute(request: Request): Response {
        return client.newCall(request).execute()
    }

    fun execute(request: Request, headers: Map<String, String>): Response {
        val builder = request.newBuilder()
        headers.forEach { builder.header(it.key, it.value) }
        return client.newCall(builder.build()).execute()
    }

    fun getCookies(host: String): String {
        return cookieJar.getCookiesForHost(host).joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun getCookiesForUrl(url: String): String {
        return try {
            val httpUrl = url.toHttpUrlOrNull() ?: return ""
            getCookies(httpUrl.host)
        } catch (_: Exception) { "" }
    }
}
