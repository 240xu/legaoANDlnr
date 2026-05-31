package com.guangyu.plugin.bridge

import android.util.Log
import com.guangyu.plugin.engine.model.BookSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

object BookSourceManager {
    private const val TAG = "BookSourceManager"
    private val sources = ConcurrentHashMap<String, BookSource>()
    private val _sourcesFlow = MutableStateFlow<List<BookSource>>(emptyList())
    val sourcesFlow: StateFlow<List<BookSource>> = _sourcesFlow
    private val client = OkHttpClient()

    fun addSource(source: BookSource) {
        sources[source.bookSourceUrl] = source
        _sourcesFlow.value = sources.values.toList()
        Log.i(TAG, "Added source: ${source.bookSourceName} (${source.bookSourceUrl})")
    }

    fun removeSource(key: String) {
        sources.remove(key)
        _sourcesFlow.value = sources.values.toList()
    }

    fun getSource(key: String): BookSource? = sources[key]

    fun getAllSources(): List<BookSource> = sources.values.toList()

    fun getEnabledSources(): List<BookSource> = sources.values.filter { it.enabled }

    fun loadFromJson(jsonStr: String): List<BookSource> {
        val loaded = BookSource.fromJson(jsonStr)
        for (source in loaded) {
            sources[source.bookSourceUrl] = source
        }
        _sourcesFlow.value = sources.values.toList()
        Log.i(TAG, "Loaded ${loaded.size} sources from JSON")
        return loaded
    }

    fun loadFromUrl(url: String): List<BookSource>? {
        return try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                loadFromJson(body)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "loadFromUrl($url) error: ${e.message}")
            null
        }
    }

    fun findSourceByName(name: String): BookSource? {
        return sources.values.find { it.bookSourceName.contains(name) }
    }
}
