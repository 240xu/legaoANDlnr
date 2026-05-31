package io.legado.engine.entity

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.legado.engine.entity.rule.*

/**
 * 书籍源 - 从 Legado JSON 格式解析
 * 已移除 Room 数据库注解和 Android Parcelable
 */
data class BookSource(
    @PrimaryKey
    var bookSourceUrl: String = "",
    var bookSourceName: String = "",
    var sourceGroup: String? = null,
    var bookSourceType: Int = 0,
    var bookUrlPattern: String? = null,
    var customOrder: Int = 0,
    var enabled: Boolean = true,
    var enabledExplore: Boolean = true,
    var enabledCookieJar: Boolean = false,
    var header: String? = null,
    var loginUrl: String? = null,
    var loginUi: String? = null,
    var loginCheckJs: String? = null,
    var bookSourceComment: String? = null,
    var variableComment: String? = null,
    var searchUrl: String? = null,
    var exploreUrl: String? = null,
    var ruleSearch: SearchRule? = null,
    var ruleBookInfo: BookInfoRule? = null,
    var ruleToc: TocRule? = null,
    var ruleContent: ContentRule? = null,
    var ruleExplore: ExploreRule? = null,
    var bookSourceGroup: String? = null,
    var jsEngine: Int = 0,
    var concurrentRate: String? = null,
    override val sourceUrl: String get() = bookSourceUrl,
    override val sourceName: String get() = bookSourceName
) : BaseSource {

    companion object {
        val GSON: Gson = Gson()

        fun fromJson(json: String): BookSource? {
            return try {
                GSON.fromJson(json, BookSource::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun fromJsonArray(json: String): List<BookSource> {
            return try {
                val array = GSON.fromJson(json, Array<BookSource>::class.java)
                array?.toList() ?: emptyList()
            } catch (e: Exception) {
                // Try single object
                fromJson(json)?.let { listOf(it) } ?: emptyList()
            }
        }
    }

    // Computed properties matching Legado
    val searchRule: SearchRule get() = ruleSearch ?: SearchRule()
    val bookInfoRule: BookInfoRule get() = ruleBookInfo ?: BookInfoRule()
    val tocRule: TocRule get() = ruleToc ?: TocRule()
    val contentRule: ContentRule get() = ruleContent ?: ContentRule()
    val exploreRule: ExploreRule get() = ruleExplore ?: ExploreRule()
}

// Placeholder annotation - no-op in engine lib
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class PrimaryKey