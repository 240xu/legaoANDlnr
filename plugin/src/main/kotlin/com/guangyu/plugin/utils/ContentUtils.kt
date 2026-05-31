package com.guangyu.plugin.utils

import io.nightfish.lightnovelreader.api.book.BookInformation
import io.nightfish.lightnovelreader.api.book.BookVolumes
import io.nightfish.lightnovelreader.api.book.ChapterContent
import io.nightfish.lightnovelreader.api.book.ChapterInformation
import io.nightfish.lightnovelreader.api.book.MutableBookInformation
import io.nightfish.lightnovelreader.api.book.MutableChapterContent
import io.nightfish.lightnovelreader.api.book.Volume
import io.nightfish.lightnovelreader.api.book.WordCount
import io.nightfish.lightnovelreader.api.content.builder.ContentBuilder
import io.nightfish.lightnovelreader.api.content.component.SimpleTextComponentData
import com.guangyu.plugin.model.BookDetailData
import com.guangyu.plugin.model.CatalogItem
import com.guangyu.plugin.model.IdCodec
import com.guangyu.plugin.model.SearchBookItem
import android.net.Uri
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ContentUtils {

    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd")
    )

    fun parseDateTime(dateStr: String?): LocalDateTime {
        if (dateStr.isNullOrBlank()) return LocalDateTime.MIN
        for (fmt in dateFormats) {
            try {
                return LocalDateTime.parse(dateStr, fmt)
            } catch (_: Exception) { }
        }
        return LocalDateTime.MIN
    }

    fun parseWordCount(wordCountStr: String?): WordCount {
        if (wordCountStr.isNullOrBlank()) return WordCount(0)
        val numStr = wordCountStr.replace(Regex("[^0-9]"), "")
        return WordCount(numStr.toIntOrNull() ?: 0)
    }

    fun searchItemToBookInfo(item: SearchBookItem): BookInformation {
        val bookId = IdCodec.encodeBookId(item.bookId, item.source, item.tab)
        return MutableBookInformation(
            id = bookId,
            title = item.bookName,
            subtitle = item.source,
            coverUrl = if (item.thumbUrl.isNotBlank()) Uri.parse(item.thumbUrl) else Uri.EMPTY,
            author = item.author,
            description = item.abstract,
            tags = if (item.tags.isNotBlank()) item.tags.split(",").map { it.trim() } else emptyList(),
            publishingHouse = "",
            wordCount = WordCount(0),
            lastUpdated = parseDateTime(item.lastChapterUpdateTime),
            isComplete = item.status.contains("\u5b8c\u7ed3")
        )
    }

    fun bookDetailToBookInfo(detail: BookDetailData): BookInformation {
        val bookId = IdCodec.encodeBookId(detail.bookId, detail.source, detail.tab)
        val tagsList = mutableListOf<String>()
        if (detail.category.isNotBlank()) tagsList.add(detail.category)
        if (detail.tags.isNotBlank() && detail.tags != detail.category) {
            tagsList.addAll(detail.tags.split(",").map { it.trim() })
        }
        return MutableBookInformation(
            id = bookId,
            title = detail.bookName,
            subtitle = detail.source,
            coverUrl = if (detail.thumbUrl.isNotBlank()) Uri.parse(detail.thumbUrl) else Uri.EMPTY,
            author = detail.author,
            description = detail.abstract,
            tags = tagsList,
            publishingHouse = "",
            wordCount = parseWordCount(detail.wordNumber),
            lastUpdated = parseDateTime(detail.lastChapterUpdateTime),
            isComplete = detail.status.contains("\u5b8c\u7ed3")
        )
    }

    fun catalogItemsToBookVolumes(bookId: String, items: List<CatalogItem>, source: String, tab: String): BookVolumes {
        if (items.isEmpty()) return BookVolumes.empty(bookId)
        val chapters = items.filter { !it.isVolume }.map { item ->
            ChapterInformation(
                id = IdCodec.encodeChapterId(item.itemId, item.title, source, tab, item.tocUrl),
                title = item.title
            )
        }
        if (chapters.isEmpty()) return BookVolumes.empty(bookId)
        val volume = Volume(
            volumeId = "${bookId}_vol_0",
            volumeTitle = "",
            chapters = chapters
        )
        return BookVolumes(bookId, listOf(volume))
    }

    fun contentToChapterContent(chapterId: String, title: String, rawContent: String): ChapterContent {
        if (rawContent.isBlank()) return ChapterContent.empty(chapterId)

        val builder = ContentBuilder()
        val paragraphs = rawContent
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .filter { it.isNotBlank() }

        for (paragraph in paragraphs) {
            builder.component(SimpleTextComponentData(paragraph.trim()))
        }

        return MutableChapterContent(
            id = chapterId,
            title = title,
            content = builder.build()
        )
    }
}