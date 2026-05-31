package com.guangyu.plugin.api

import android.util.Log
import com.guangyu.plugin.model.ApiResponse
import com.guangyu.plugin.model.BookDetailData
import com.guangyu.plugin.model.CatalogItem
import com.guangyu.plugin.model.ContentData
import com.guangyu.plugin.model.DiscoverStyleItem
import com.guangyu.plugin.model.SearchBookItem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 光遇聚合 API 客户端
 * 负责与后端服务器通信
 */
class GuangYuApi(
    private var baseUrl: String = DEFAULT_BASE_URL
) {
    companion object {
        const val DEFAULT_BASE_URL = "http://101.35.133.34:8888"
        const val CLOUD_CONFIG_URL_SUFFIX = "/static/source_config/config.json"
        private const val TAG = "GuangYuApi"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = "{}".toRequestBody(JSON_MEDIA_TYPE)
        private val FALLBACK_HOSTS = listOf(
            "http://101.35.133.34:8888",
            "http://103.236.91.147:8888",
            "https://v1.gyks.cf",
            "https://v2.gyks.cf",
            "https://v3.gyks.cf",
            "https://v4.gyks.cf",
            "https://v5.gyks.cf",
            "https://v6.gyks.cf",
            "https://v7.gyks.cf"
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getBaseUrl(): String = baseUrl

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    /**
     * 尝试切换到下一个可用主机
     */
    fun tryNextHost(): Boolean {
        val currentIndex = FALLBACK_HOSTS.indexOf(baseUrl)
        val nextIndex = currentIndex + 1
        if (nextIndex < FALLBACK_HOSTS.size) {
            baseUrl = FALLBACK_HOSTS[nextIndex]
            Log.i(TAG, "Switched to host: $baseUrl")
            return true
        }
        return false
    }

    /**
     * 测试当前主机是否可用
     */
    fun testConnection(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl$CLOUD_CONFIG_URL_SUFFIX")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(TAG, "Connection test failed for $baseUrl: ${e.message}")
            false
        }
    }

    /**
     * 从云端加载配置，获取可用主机列表
     */
    fun loadCloudSettings() {
        try {
            val request = Request.Builder()
                .url("$baseUrl$CLOUD_CONFIG_URL_SUFFIX")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return
                    val settings = json.parseToJsonElement(body)
                    val obj = settings as? JsonObject ?: return
                    val hostsArray = obj["hosts"] as? kotlinx.serialization.json.JsonArray
                    if (hostsArray != null && hostsArray.isNotEmpty()) {
                        val firstHost = hostsArray[0].toString().trim('"')
                        if (firstHost.startsWith("http")) {
                            baseUrl = firstHost.trimEnd('/')
                            Log.i(TAG, "Cloud settings loaded, base URL: $baseUrl")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cloud settings: ${e.message}")
        }
    }

    /**
     * 自动寻找可用主机
     */
    fun findWorkingHost() {
        // Try each fallback host
        for (host in FALLBACK_HOSTS) {
            baseUrl = host
            if (testConnection()) {
                Log.i(TAG, "Found working host: $baseUrl")
                // Try to load cloud settings for better host
                loadCloudSettings()
                if (testConnection()) {
                    Log.i(TAG, "Cloud host also works: $baseUrl")
                } else {
                    // Revert to the working host
                    baseUrl = host
                }
                return
            }
        }
        Log.w(TAG, "No working host found, using default: $baseUrl")
    }

    /**
     * 搜索书籍
     * GET /search?title={key}&tab={tab}&source={sourcesKey}&page={page}&disabled_sources={disabledSources}
     */
    fun search(title: String, tab: String, source: String, page: Int, disabledSources: Int): List<SearchBookItem>? {
        val url = "$baseUrl/search?title=${encode(title)}&tab=${encode(tab)}&source=${encode(source)}&page=$page&disabled_sources=$disabledSources"
        Log.i(TAG, "Search URL: $url")
        val httpRequest = Request.Builder()
            .url(url)
            .get()
            .build()
        return executeWithFallback(httpRequest, "search") { body ->
            Log.i(TAG, "Search response (first 500): ${body.take(500)}")
            val apiResp = json.decodeFromString<ApiResponse<List<SearchBookItem>>>(body)
            Log.i(TAG, "Search parsed: code=${apiResp.code}, msg=${apiResp.msg}, dataCount=${apiResp.data?.size}")
            apiResp.data ?: emptyList()
        }
    }

    /**
     * 获取书籍详情
     * POST /detail?book_id={}&source={}&tab={}&variable={}
     */
    fun getBookDetail(bookId: String, source: String, tab: String): BookDetailData? {
        val url = "$baseUrl/detail?book_id=${encode(bookId)}&source=${encode(source)}&tab=${encode(tab)}&variable=%7B%7D"
        val httpRequest = Request.Builder()
            .url(url)
            .post(EMPTY_BODY)
            .build()
        return executeWithFallback(httpRequest, "detail") { body ->
            val apiResp = json.decodeFromString<ApiResponse<BookDetailData>>(body)
            apiResp.data
        }
    }

    /**
     * 获取目录
     * POST /catalog?book_id={}&source={}&tab={}&variable={}
     */
    fun getCatalog(bookId: String, source: String, tab: String): List<CatalogItem>? {
        val url = "$baseUrl/catalog?book_id=${encode(bookId)}&source=${encode(source)}&tab=${encode(tab)}&variable=%7B%7D"
        val httpRequest = Request.Builder()
            .url(url)
            .post(EMPTY_BODY)
            .build()
        return executeWithFallback(httpRequest, "catalog") { body ->
            val apiResp = json.decodeFromString<ApiResponse<List<CatalogItem>>>(body)
            apiResp.data ?: emptyList()
        }
    }

    /**
     * 获取章节内容
     * GET /content?version=5&source={}&tab={}&item_id={}
     */
    fun getContent(bookId: String, itemId: String, source: String, tab: String): ContentData? {
        val url = "$baseUrl/content?version=5&source=${encode(source)}&tab=${encode(tab)}&item_id=${encode(itemId)}"
        val httpRequest = Request.Builder()
            .url(url)
            .get()
            .build()
        return executeWithFallback(httpRequest, "content") { body ->
            val apiResp = json.decodeFromString<ContentData>(body)
            apiResp
        }
    }

    /**
     * 获取发现页样式数据
     * GET /discovestyle?source={}&source_type={}&tab={}
     */
    fun getDiscoverStyle(source: String, sourceType: String, tab: String): List<DiscoverStyleItem>? {
        val url = "$baseUrl/discovestyle?source=${encode(source)}&source_type=${encode(sourceType)}&tab=${encode(tab)}"
        val httpRequest = Request.Builder()
            .url(url)
            .get()
            .build()
        return executeWithFallback(httpRequest, "discovestyle") { body ->
            val apiResp = json.decodeFromString<ApiResponse<List<DiscoverStyleItem>>>(body)
            apiResp.data ?: emptyList()
        }
    }

    private fun encode(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> executeWithFallback(request: Request, apiName: String, parser: (String) -> T): T? {
        return try {
            executeRequest(request, apiName, parser)
        } catch (e: Exception) {
            Log.e(TAG, "$apiName error: ${e.message}")
            // Try next host on connection failure
            if (tryNextHost()) {
                try {
                    val oldUrl = request.url.toString()
                    val newUrl = replaceHost(oldUrl)
                    val newRequest = request.newBuilder().url(newUrl).build()
                    executeRequest(newRequest, "$apiName(retry)", parser)
                } catch (e2: Exception) {
                    Log.e(TAG, "$apiName retry error: ${e2.message}")
                    null
                }
            } else {
                null
            }
        }
    }

    private fun <T> executeRequest(request: Request, apiName: String, parser: (String) -> T): T? {
        Log.i(TAG, "$apiName request: ${request.method} ${request.url}")
        client.newCall(request).execute().use { response ->
            Log.i(TAG, "$apiName response: ${response.code}")
            if (!response.isSuccessful) {
                Log.e(TAG, "$apiName failed: ${response.code}")
                return null
            }
            val body = response.body?.string() ?: return null
            Log.i(TAG, "$apiName body length: ${body.length}")
            return parser(body)
        }
    }

    private fun replaceHost(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val newUri = java.net.URI(baseUrl)
            uri.scheme?.let { } // keep original scheme
            val port = if (newUri.port != -1) ":${newUri.port}" else ""
            "${newUri.scheme}://${newUri.host}$port${uri.rawPath}${if (uri.rawQuery != null) "?${uri.rawQuery}" else ""}"
        } catch (e: Exception) {
            url
        }
    }
}