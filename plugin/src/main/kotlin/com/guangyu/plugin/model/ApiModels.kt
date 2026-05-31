package com.guangyu.plugin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 书籍ID编码格式: bookId|source|tab
 * 章节ID编码格式: itemId|title|source|tab|tocUrl
 */
object IdCodec {
    fun encodeBookId(bookId: String, source: String, tab: String): String =
        "$bookId|$source|$tab"

    fun decodeBookId(encoded: String): Triple<String, String, String> {
        val parts = encoded.split("|", limit = 3)
        return Triple(
            parts.getOrElse(0) { "" },
            parts.getOrElse(1) { "" },
            parts.getOrElse(2) { "\u5c0f\u8bf4" }
        )
    }

    fun encodeChapterId(itemId: String, title: String, source: String, tab: String, tocUrl: String = ""): String =
        "$itemId|$title|$source|$tab|$tocUrl"

    fun decodeChapterId(encoded: String): ChapterIdParts {
        val parts = encoded.split("|", limit = 5)
        return ChapterIdParts(
            itemId = parts.getOrElse(0) { "" },
            title = parts.getOrElse(1) { "" },
            source = parts.getOrElse(2) { "" },
            tab = parts.getOrElse(3) { "\u5c0f\u8bf4" },
            tocUrl = parts.getOrElse(4) { "" }
        )
    }
}

data class ChapterIdParts(
    val itemId: String,
    val title: String,
    val source: String,
    val tab: String,
    val tocUrl: String
)

/**
 * 通用 API 响应包装
 */
@Serializable
data class ApiResponse<T>(
    val data: T? = null,
    val msg: String? = null,
    val code: Int? = null,
    val cache: Boolean? = null,
    val time: Long? = null,
    val title: String? = null,
    val content: String? = null,
    val contents: String? = null
)

/**
 * 搜索结果书籍项
 */
@Serializable
data class SearchBookItem(
    @SerialName("book_id")
    val bookId: String = "",
    @SerialName("book_name")
    val bookName: String = "",
    val author: String = "",
    @SerialName("thumb_url")
    val thumbUrl: String = "",
    val source: String = "",
    val tab: String = "\u5c0f\u8bf4",
    val abstract: String = "",
    @SerialName("last_chapter_title")
    val lastChapterTitle: String = "",
    val status: String = "",
    val score: String = "",
    val tags: String = "",
    @SerialName("last_chapter_update_time")
    val lastChapterUpdateTime: String = ""
)

/**
 * 书籍详情数据
 */
@Serializable
data class BookDetailData(
    @SerialName("book_name")
    val bookName: String = "",
    @SerialName("book_id")
    val bookId: String = "",
    val author: String = "",
    val score: String = "",
    val status: String = "",
    @SerialName("word_number")
    val wordNumber: String = "",
    @SerialName("last_chapter_update_time")
    val lastChapterUpdateTime: String = "",
    val category: String = "",
    val tags: String = "",
    val abstract: String = "",
    @SerialName("read_count")
    val readCount: String = "",
    @SerialName("thumb_url")
    val thumbUrl: String = "",
    val tab: String = "\u5c0f\u8bf4",
    val source: String = "",
    @SerialName("last_chapter_title")
    val lastChapterTitle: String = "",
    @SerialName("book_url")
    val bookUrl: String = "",
    @SerialName("toc_url")
    val tocUrl: String = "",
    @SerialName("catalog_url")
    val catalogUrl: String = ""
)

/**
 * 目录项
 */
@Serializable
data class CatalogItem(
    @SerialName("item_id")
    val itemId: String = "",
    val title: String = "",
    @SerialName("first_pass_time")
    val firstPassTime: String = "",
    @SerialName("chapter_word_number")
    val chapterWordNumber: String = "",
    val source: String = "",
    val tab: String = "\u5c0f\u8bf4",
    val url: String = "",
    @SerialName("is_pay")
    val isPay: Boolean = false,
    @SerialName("is_volume")
    val isVolume: Boolean = false,
    @SerialName("toc_url")
    val tocUrl: String = "",
    @SerialName("content_url")
    val contentUrl: String = ""
)

/**
 * 章节内容数据
 */
@Serializable
data class ContentData(
    val code: Int? = null,
    val msg: String? = null,
    val title: String? = null,
    val content: String = "",
    val contents: String? = null
)

/**
 * 发现页样式项
 */
@Serializable
data class DiscoverStyleItem(
    val title: String = "",
    val url: String = "",
    val style: DiscoverStyle? = null
)

@Serializable
data class DiscoverStyle(
    @SerialName("layout_flexGrow")
    val layoutFlexGrow: Float = 1f,
    @SerialName("layout_flexBasisPercent")
    val layoutFlexBasisPercent: Float = 0.25f
)