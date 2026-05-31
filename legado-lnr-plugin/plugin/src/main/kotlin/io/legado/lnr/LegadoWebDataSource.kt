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
import io.nightfish.lightnovelreader.api.explore.ExploreBooksRow
import io.nightfish.lightnovelreader.api.explore.ExploreDisplayBook
import io.nightfish.lightnovelreader.api.web.WebBookDataSource
import io.nightfish.lightnovelreader.api.web.WebDataSource
import io.nightfish.lightnovelreader.api.web.explore.AbstractDefaultExplorePageProvider
import io.nightfish.lightnovelreader.api.web.explore.ExplorePageProvider
import io.nightfish.lightnovelreader.api.web.explore.ExploreTapPageDataSource
import io.nightfish.lightnovelreader.api.web.explore.filter.Filter
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

/**
 * Legado 书源 WebDataSource - 将 Legado 书源规则桥接到 LNR API
 */
@Suppress("unused")
@WebDataSource(
    name = "Legado 书源",
    provider = "legado"
)
object LegadoWebDataSource : WebBookDataSource {

    private const val TAG = "LegadoWebDataSource"
    private val _isOffLine = MutableStateFlow(false)
    override val id: Int = "legado_source".hashCode()

    override fun onLoad() {
        Log.i(TAG, "LegadoWebDataSource onLoad, 已加载 ${BookSourceManager.getSourceCount()} 个书源")
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
            if (sources.isEmpty()) {
                emit(SearchResult.Empty())
                return@flow
            }
            var foundAny = false
            for (source in sources) {
                try {
                    val searchUrl = source.searchUrl ?: continue
                    val url = buildSearchUrl(searchUrl, keyword)
                    val html = fetchUrl(url, source) ?: continue
                    val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                    rule.setContent(html, url)
                    val names = rule.getStringList(source.searchRule.name ?: "")
                    val authors = rule.getStringList(source.searchRule.author ?: "")
                    val bookUrls = rule.getStringList(source.searchRule.bookUrl ?: "")
                    val covers = rule.getStringList(source.searchRule.coverUrl ?: "")
                    val intros = rule.getStringList(source.searchRule.intro ?: "")
                    val count = minOf(names.size, bookUrls.size)
                    for (i in 0 until count) {
                        val bookId = "${source.bookSourceUrl}|${bookUrls.getOrNull(i) ?: ""}"
                        emit(SearchResult.MultipleBook(
                            BookInformation(
                                id = bookId.hashCode().toLong(),
                                title = names.getOrElse(i) { "" },
                                author = authors.getOrElse(i) { "" },
                                description = intros.getOrElse(i) { "" },
                                coverUri = covers.getOrNull(i)?.let { Uri.parse(it) } ?: Uri.EMPTY,
                                isAvailable = true
                            )
                        ))
                        foundAny = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "搜索失败 [${source.bookSourceName}]: ${e.message}")
                }
            }
            if (!foundAny) {
                emit(SearchResult.Empty())
            } else {
                emit(SearchResult.End())
            }
        }
    }

    // ========== Explore ==========
    override val explorePageProvider: ExplorePageProvider =
        object : AbstractDefaultExplorePageProvider() {

            override fun getFilters(): List<Filter> = emptyList()

            override fun refresh() {
                Thread {
                    try {
                        val rows = mutableListOf<ExploreBooksRow>()
                        val sources = BookSourceManager.getEnabledSources()
                            .filter { it.enabledExplore && !it.exploreUrl.isNullOrBlank() }
                        for (source in sources) {
                            try {
                                val exploreUrl = source.exploreUrl ?: continue
                                val url = buildExploreUrl(exploreUrl)
                                val html = fetchUrl(url, source) ?: continue
                                val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                                rule.setContent(html, url)
                                val names = rule.getStringList(source.exploreRule.name ?: "")
                                val bookUrls = rule.getStringList(source.exploreRule.bookUrl ?: "")
                                val covers = rule.getStringList(source.exploreRule.coverUrl ?: "")
                                val authors = rule.getStringList(source.exploreRule.author ?: "")
                                val count = minOf(names.size, bookUrls.size)
                                if (count > 0) {
                                    val books = (0 until count).map { i ->
                                        val bookId = "${source.bookSourceUrl}|${bookUrls.getOrNull(i) ?: ""}"
                                        ExploreDisplayBook(
                                            id = bookId.hashCode().toLong(),
                                            title = names.getOrElse(i) { "" },
                                            author = authors.getOrElse(i) { "" },
                                            coverUri = covers.getOrNull(i)?.let { Uri.parse(it) } ?: Uri.EMPTY
                                        )
                                    }
                                    rows.add(ExploreBooksRow(
                                        source.bookSourceName,
                                        books,
                                        false,
                                        ""
                                    ))
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "发现页加载失败 [${source.bookSourceName}]: ${e.message}")
                            }
                        }
                        rowsFlow.value = rows
                    } catch (e: Exception) {
                        Log.e(TAG, "发现页刷新失败: ${e.message}")
                    }
                }.start()
            }

            init { refresh() }
        }

    // ========== Book Information ==========
    override suspend fun getBookInformation(id: String): BookInformation {
        return withContext(Dispatchers.IO) {
            try {
                val (sourceUrl, bookUrl) = parseBookId(id)
                val source = BookSourceManager.getSource(sourceUrl)
                    ?: return@withContext BookInformation.empty(id)
                val html = fetchUrl(bookUrl, source)
                    ?: return@withContext BookInformation.empty(id)
                val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                rule.setContent(html, bookUrl)
                val info = source.bookInfoRule
                BookInformation(
                    id = id.hashCode().toLong(),
                    title = rule.getString(info.name ?: ""),
                    author = rule.getString(info.author ?: ""),
                    description = rule.getString(info.introduce ?: ""),
                    coverUri = rule.getString(info.coverUrl ?: "").let {
                        if (it.isNotBlank()) Uri.parse(it) else Uri.EMPTY
                    },
                    isAvailable = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "获取书籍信息失败: ${e.message}", e)
                BookInformation.empty(id)
            }
        }
    }

    // ========== Book Volumes (TOC) ==========
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
                val toc = source.tocRule
                val chapterNames = rule.getStringList(toc.chapterList ?: "")
                val chapterUrls = rule.getStringList(toc.chapterName ?: "")
                // 实际上 chapterList 是列表选择器，chapterName 和 chapterUrl 从列表项中提取
                // 这里简化处理，实际需要两层解析
                BookVolumes.empty(id)
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
                    ?: return@withContext ChapterContent.empty(chapterId)
                val html = fetchUrl(chapterId, source)
                    ?: return@withContext ChapterContent.empty(chapterId)
                val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                rule.setContent(html, chapterId)
                val content = rule.getString(source.contentRule.content ?: "")
                if (content.isNotBlank()) {
                    ChapterContent(chapterId.hashCode().toLong(), content)
                } else {
                    ChapterContent.empty(chapterId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取正文失败: ${e.message}", e)
                ChapterContent.empty(chapterId)
            }
        }
    }

    // ========== Helper Methods ==========

    private fun buildSearchUrl(template: String, keyword: String): String {
        return template
            .replace("{{key}}", keyword)
            .replace("{{page}}", "1")
            .replace("\$\$key", keyword)
            .replace("\$\$page", "1")
    }

    private fun buildExploreUrl(template: String): String {
        return template
            .replace("{{page}}", "1")
            .replace("\$\$page", "1")
    }

    private fun fetchUrl(url: String, source: BookSource): String? {
        return try {
            val client = BookSourceManager.getHttpClient()
            val headerMap = source.getHeaderMap()
            val request = Request.Builder().url(url).apply {
                headerMap.forEach { (k, v) -> header(k, v) }
                header("User-Agent", headerMap["User-Agent"]
                    ?: "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            }.build()
            val response = client.client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                Log.w(TAG, "HTTP ${response.code}: $url")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求失败: $url, ${e.message}")
            null
        }
    }

    private fun parseBookId(id: String): Pair<String, String> {
        val parts = id.split("|", limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else "" to id
    }
}
