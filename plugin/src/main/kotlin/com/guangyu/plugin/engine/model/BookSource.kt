package com.guangyu.plugin.engine.model

import com.guangyu.plugin.engine.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BookSource(
    var bookSourceUrl: String = "",
    var bookSourceName: String = "",
    var bookSourceGroup: String? = null,
    var bookSourceType: Int = 0,
    var bookUrlPattern: String? = null,
    var customOrder: Int = 0,
    var enabled: Boolean = true,
    var enabledExplore: Boolean = true,
    var jsLib: String? = null,
    var enabledCookieJar: Boolean? = true,
    var concurrentRate: String? = null,
    var header: String? = null,
    var loginUrl: String? = null,
    var loginUi: String? = null,
    var loginCheckJs: String? = null,
    var coverDecodeJs: String? = null,
    var bookSourceComment: String? = null,
    var variableComment: String? = null,
    var lastUpdateTime: Long = 0,
    var respondTime: Long = 180000L,
    var weight: Int = 0,
    var exploreUrl: String? = null,
    var exploreScreen: String? = null,
    @Serializable(with = ExploreRuleSerializer::class)
    var ruleExplore: ExploreRule? = null,
    var searchUrl: String? = null,
    @Serializable(with = SearchRuleSerializer::class)
    var ruleSearch: SearchRule? = null,
    @Serializable(with = BookInfoRuleSerializer::class)
    var ruleBookInfo: BookInfoRule? = null,
    @Serializable(with = TocRuleSerializer::class)
    var ruleToc: TocRule? = null,
    @Serializable(with = ContentRuleSerializer::class)
    var ruleContent: ContentRule? = null,
    var eventListener: Boolean = false,
    var customButton: Boolean = false
) {
    fun getKey(): String = bookSourceUrl
    fun getTag(): String = bookSourceName
    fun getSearchRule(): SearchRule = ruleSearch ?: SearchRule().also { ruleSearch = it }
    fun getExploreRule(): ExploreRule = ruleExplore ?: ExploreRule().also { ruleExplore = it }
    fun getBookInfoRule(): BookInfoRule = ruleBookInfo ?: BookInfoRule().also { ruleBookInfo = it }
    fun getTocRule(): TocRule = ruleToc ?: TocRule().also { ruleToc = it }
    fun getContentRule(): ContentRule = ruleContent ?: ContentRule().also { ruleContent = it }

    fun getHeaderMap(): Map<String, String> {
        val map = HashMap<String, String>()
        header?.let {
            try {
                val h = if (it.startsWith("<js>")) it.substring(4, it.lastIndexOf("<"))
                else if (it.startsWith("@js:")) it.substring(4) else it
                if (h.trimStart().startsWith("{")) {
                    map.putAll(LegadoJson.instance.decodeFromString<Map<String, String>>(h))
                }
            } catch (_: Exception) {}
        }
        if (!map.containsKey("User-Agent")) map["User-Agent"] = DEFAULT_UA
        return map
    }

    companion object {
        const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun fromJson(jsonStr: String): List<BookSource> {
            return try {
                LegadoJson.instance.decodeFromString<List<BookSource>>(jsonStr)
            } catch (_: Exception) {
                try {
                    listOf(LegadoJson.instance.decodeFromString<BookSource>(jsonStr))
                } catch (e: Exception) {
                    android.util.Log.e("BookSource", "Failed to parse book source JSON", e)
                    emptyList()
                }
            }
        }
    }
}
