@file:Suppress("OPT_IN_USAGE")

package io.legado.lnr

import android.net.Uri
import android.util.Log
import io.legado.engine.analyze.AnalyzeRule
import io.legado.engine.entity.BookSource
import io.legado.lnr.util.BookSourceManager
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
import io.nightfish.lightnovelreader.api.explore.ExploreBooksRow
import io.nightfish.lightnovelreader.api.explore.ExploreDisplayBook
import io.nightfish.lightnovelreader.api.web.WebBookDataSource
import io.nightfish.lightnovelreader.api.web.WebDataSource
import io.nightfish.lightnovelreader.api.web.explore.AbstractDefaultExplorePageProvider
import io.nightfish.lightnovelreader.api.web.explore.ExplorePageProvider
import io.nightfish.lightnovelreader.api.web.explore.ExploreTapPageDataSource
import io.nightfish.lightnovelreader.api.web.search.SearchProvider
import io.nightfish.lightnovelreader.api.web.search.SearchResult
import io.nightfish.lightnovelreader.api.web.search.SearchType
import io.nightfish.lightnovelreader.api.util.local
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.time.LocalDateTime

@Suppress("unused")
@WebDataSource(name = "Legado 书源", provider = "legado")
object LegadoWebDataSource : WebBookDataSource {

    private const val TAG = "LegadoWebDataSource"
    private val _isOffLine = MutableStateFlow(false)
    override val id: Int = "legado_source".hashCode()

    override fun onLoad() {
        Log.i(TAG, "onLoad, 已加载 ${BookSourceManager.getSourceCount()} 个书源")
    }

    override suspend fun isOffLine(): Boolean = _isOffLine.value
    override val offLine: Boolean get() = _isOffLine.value
    override val isOffLineFlow: StateFlow<Boolean> = _isOffLine

    // ========== Search ==========
    override val searchProvider: SearchProvider = object : SearchProvider {
        override val searchTypes: List<SearchType> = listOf(
            SearchType("all", "全部".local(), "搜索书源".local())
        )

        override fun search(searchType: SearchType, keyword: String): Flow<SearchResult> = flow {
            val sources = BookSourceManager.getEnabledSources()
            if (sources.isEmpty()) { emit(SearchResult.Empty()); return@flow }
            var foundAny = false
            for (source in sources) {
                try {
                    val searchUrl = source.searchUrl ?: continue
                    val url = buildSearchUrl(searchUrl, keyword)
                    val html = fetchUrl(url, source) ?: continue
                    val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                    rule.setContent(html, url)
                    val sr = source.getSearchRule()
                    val names = rule.getStringList(sr.name ?: "")
                    val authors = rule.getStringList(sr.author ?: "")
                    val bookUrls = rule.getStringList(sr.bookUrl ?: "")
                    val covers = rule.getStringList(sr.coverUrl ?: "")
                    val intros = rule.getStringList(sr.intro ?: "")
                    val count = minOf(names.size, bookUrls.size)
                    for (i in 0 until count) {
                        val bookId = "${source.bookSourceUrl}|${bookUrls.getOrNull(i) ?: ""}"
                        emit(SearchResult.MultipleBook(makeBookInfo(
                            bookId, names.getOrElse(i) { "" }, source.bookSourceName,
                            authors.getOrElse(i) { "" }, intros.getOrElse(i) { "" },
                            covers.getOrNull(i)
                        )))
                        foundAny = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "搜索失败 [${source.bookSourceName}]: ${e.message}")
                }
            }
            if (!foundAny) emit(SearchResult.Empty()) else emit(SearchResult.End())
        }
    }

    // ========== Explore ==========
    override val explorePageProvider: ExplorePageProvider =
        object : AbstractDefaultExplorePageProvider() {
            init {
                val sources = BookSourceManager.getEnabledSources()
                    .filter { it.enabledExplore && !it.exploreUrl.isNullOrBlank() }
                for (source in sources) {
                    registerTapPage(LegadoExploreTapPage(source))
                }
            }
        }

    private class LegadoExploreTapPage(private val source: BookSource) : ExploreTapPageDataSource {
        override val title: String = source.bookSourceName
        private val _rowsFlow = MutableStateFlow<List<ExploreBooksRow>>(emptyList())

        init { refresh() }

        override fun getRowsFlow(): Flow<List<ExploreBooksRow>> = _rowsFlow

        private fun refresh() {
            Thread {
                try {
                    val rows = mutableListOf<ExploreBooksRow>()
                    val exploreUrl = source.exploreUrl ?: return@Thread
                    val url = buildExploreUrl(exploreUrl)
                    val html = fetchUrl(url, source) ?: return@Thread
                    val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                    rule.setContent(html, url)
                    val er = source.getExploreRule()
                    val names = rule.getStringList(er.name ?: "")
                    val bookUrls = rule.getStringList(er.bookUrl ?: "")
                    val covers = rule.getStringList(er.coverUrl ?: "")
                    val authors = rule.getStringList(er.author ?: "")
                    val count = minOf(names.size, bookUrls.size)
                    if (count > 0) {
                        val books = (0 until count).map { i ->
                            val bookId = "${source.bookSourceUrl}|${bookUrls.getOrNull(i) ?: ""}"
                            ExploreDisplayBook(bookId, names[i], authors.getOrElse(i) { "" },
                                covers.getOrNull(i)?.let { Uri.parse(it) } ?: Uri.EMPTY)
                        }
                        rows.add(ExploreBooksRow(source.bookSourceName, books, false, ""))
                    }
                    _rowsFlow.value = rows
                } catch (e: Exception) {
                    Log.e(TAG, "发现页失败 [${source.bookSourceName}]: ${e.message}")
                }
            }.start()
        }
    }

    // ========== Book Information ==========
    override suspend fun getBookInformation(id: String): BookInformation {
        return withContext(Dispatchers.IO) {
            try {
                val (sourceUrl, bookUrl) = parseBookId(id)
                val source = BookSourceManager.getSource(sourceUrl)
                    ?: return@withContext MutableBookInformation.empty(id)
                val html = fetchUrl(bookUrl, source)
                    ?: return@withContext MutableBookInformation.empty(id)
                val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                rule.setContent(html, bookUrl)
                val info = source.getBookInfoRule()
                makeBookInfo(id, rule.getString(info.name ?: ""), source.bookSourceName,
                    rule.getString(info.author ?: ""), rule.getString(info.intro ?: ""),
                    rule.getString(info.coverUrl ?: "").ifBlank { null })
            } catch (e: Exception) {
                Log.e(TAG, "获取书籍信息失败: ${e.message}", e)
                MutableBookInformation.empty(id)
            }
        }
    }

    // ========== Book Volumes ==========
    override suspend fun getBookVolumes(id: String): BookVolumes {
        return withContext(Dispatchers.IO) {
            try {
                val (sourceUrl, bookUrl) = parseBookId(id)
                val source = BookSourceManager.getSource(sourceUrl)
                    ?: return@withContext BookVolumes.empty(id)
                val html = fetchUrl(bookUrl, source)
                    ?: return@withContext BookVolumes.empty(id)
                val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                rule.setContent(html, bookUrl)
                val toc = source.getTocRule()
                val chapterListSelector = toc.chapterList ?: ""
                if (chapterListSelector.isBlank()) return@withContext BookVolumes.empty(id)
                val elements = rule.getElements(chapterListSelector)
                val chapters = elements.mapIndexed { index, element ->
                    val childRule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                    childRule.setContent(element, bookUrl)
                    val name = childRule.getString(toc.chapterName ?: "")
                    val url = childRule.getString(toc.chapterUrl ?: "")
                    ChapterInformation(
                        id = if (url.isNotBlank()) "${source.bookSourceUrl}|$url" else "${id}_ch_$index",
                        title = name.ifBlank { "第${index + 1}章" }
                    )
                }
                BookVolumes(id, listOf(Volume("${id}_vol_0", "", chapters)))
            } catch (e: Exception) {
                Log.e(TAG, "获取目录失败: ${e.message}", e)
                BookVolumes.empty(id)
            }
        }
    }

    // ========== Chapter Content ==========
    override suspend fun getChapterContent(chapterId: String, bookId: String): ChapterContent {
        return withContext(Dispatchers.IO) {
            try {
                val (sourceUrl, _) = parseBookId(bookId)
                val source = BookSourceManager.getSource(sourceUrl)
                    ?: return@withContext MutableChapterContent.empty()
                val html = fetchUrl(chapterId, source)
                    ?: return@withContext MutableChapterContent.empty()
                val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                rule.setContent(html, chapterId)
                val rawContent = rule.getString(source.getContentRule().content ?: "")
                if (rawContent.isBlank()) return@withContext MutableChapterContent.empty()
                val builder = ContentBuilder()
                rawContent.replace("\r\n", "\n").replace("\r", "\n")
                    .split("\n").filter { it.isNotBlank() }
                    .forEach { builder.component(SimpleTextComponentData(it.trim())) }
                MutableChapterContent(chapterId, "", builder.build(), "", "")
            } catch (e: Exception) {
                Log.e(TAG, "获取正文失败: ${e.message}", e)
                MutableChapterContent.empty()
            }
        }
    }

    // ========== Helpers ==========
    private fun makeBookInfo(id: String, title: String, subtitle: String,
                             author: String, description: String, coverUrl: String?): BookInformation {
        return MutableBookInformation(
            id = id, title = title, subtitle = subtitle,
            coverUrl = coverUrl?.let { Uri.parse(it) } ?: Uri.EMPTY,
            author = author, description = description,
            tags = emptyList(), publishingHouse = "",
            wordCount = WordCount(0), lastUpdated = LocalDateTime.MIN, isComplete = false
        )
    }

    private fun buildSearchUrl(template: String, keyword: String): String =
        template.replace("{{key}}", keyword).replace("{{page}}", "1")
            .replace("\$\$key", keyword).replace("\$\$page", "1")

    private fun buildExploreUrl(template: String): String =
        template.replace("{{page}}", "1").replace("\$\$page", "1")

    private fun fetchUrl(url: String, source: BookSource): String? = try {
        val client = BookSourceManager.getHttpClient()
        val headerMap = source.getHeaderMap()
        val request = Request.Builder().url(url).apply {
            headerMap.forEach { (k, v) -> header(k, v) }
            header("User-Agent", headerMap["User-Agent"] ?: "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
        }.build()
        val response = client.client.newCall(request).execute()
        if (response.isSuccessful) response.body?.string() else null
    } catch (e: Exception) { Log.e(TAG, "请求失败: $url, ${e.message}"); null }

    private fun parseBookId(id: String): Pair<String, String> {
        val parts = id.split("|", limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else "" to id
    }
}
