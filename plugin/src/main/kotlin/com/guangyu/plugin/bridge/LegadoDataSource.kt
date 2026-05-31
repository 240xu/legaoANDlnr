package com.guangyu.plugin.bridge

import android.net.Uri
import android.util.Log
import com.guangyu.plugin.engine.analyze.AnalyzeRule
import com.guangyu.plugin.engine.http.HttpClient
import com.guangyu.plugin.engine.http.StrResponse
import com.guangyu.plugin.engine.js.JsBridge
import com.guangyu.plugin.engine.model.BookSource
import io.nightfish.lightnovelreader.api.book.*
import io.nightfish.lightnovelreader.api.content.builder.ContentBuilder
import io.nightfish.lightnovelreader.api.content.component.SimpleTextComponentData
import io.nightfish.lightnovelreader.api.explore.ExploreBooksRow
import io.nightfish.lightnovelreader.api.explore.ExploreDisplayBook
import io.nightfish.lightnovelreader.api.web.WebBookDataSource
import io.nightfish.lightnovelreader.api.web.WebDataSource
import io.nightfish.lightnovelreader.api.web.explore.ExplorePageProvider
import io.nightfish.lightnovelreader.api.web.explore.AbstractDefaultExplorePageProvider
import io.nightfish.lightnovelreader.api.web.explore.ExploreTapPageDataSource
import io.nightfish.lightnovelreader.api.web.search.SearchProvider
import io.nightfish.lightnovelreader.api.web.search.SearchResult
import io.nightfish.lightnovelreader.api.web.search.SearchType
import io.nightfish.lightnovelreader.api.util.local
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.jsoup.Jsoup
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("unused")
@WebDataSource(
    name = "光遇聚合",
    provider = "guangyu"
)
object LegadoDataSource : WebBookDataSource {
    private const val TAG = "LegadoDataSource"
    override val id: Int = "legado_bridge".hashCode()
    private val initialized = AtomicBoolean(false)
    private val _isOffLine = MutableStateFlow(false)
    private var currentSource: BookSource? = null

    override fun onLoad() {
        Log.i(TAG, "LegadoDataSource onLoad")
        if (initialized.compareAndSet(false, true)) {
            Thread {
                try {
                    val sourceUrl = "https://shuyuan.nyasama.net/shuyuan/18832c7d4853f72d2816600a95ef2648.json"
                    val sources = BookSourceManager.loadFromUrl(sourceUrl)
                    if (!sources.isNullOrEmpty()) {
                        currentSource = sources.first()
                        Log.i(TAG, "Loaded source: ${currentSource?.bookSourceName}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Init error: ${e.message}")
                }
            }.start()
        }
    }

    override suspend fun isOffLine(): Boolean = _isOffLine.value
    override val offLine: Boolean get() = _isOffLine.value
    override val isOffLineFlow: StateFlow<Boolean> = _isOffLine

    override val searchProvider = object : SearchProvider {
        override val searchTypes: List<SearchType> = listOf(
            SearchType("novel", "小说".local(), "搜索小说名称".local())
        )

        override fun search(searchType: SearchType, keyword: String): Flow<SearchResult> = flow {
            val source = currentSource
            if (source == null) { emit(SearchResult.Empty()); return@flow }
            try {
                val results = withContext(Dispatchers.IO) { searchLegado(source, keyword) }
                _isOffLine.value = false
                if (results.isEmpty()) emit(SearchResult.Empty())
                else { for (item in results) emit(SearchResult.MultipleBook(item)); emit(SearchResult.End()) }
            } catch (e: Exception) {
                Log.e(TAG, "Search error: ${e.message}")
                _isOffLine.value = true
                emit(SearchResult.Empty())
            }
        }
    }

    override val explorePageProvider: ExplorePageProvider = object : AbstractDefaultExplorePageProvider() {
        init {
            registerTapPage("default", object : ExploreTapPageDataSource {
                override val title: String = "发现"
                override fun getRowsFlow(): Flow<List<ExploreBooksRow>> = flow {
                    val source = currentSource ?: return@flow
                    val rows = withContext(Dispatchers.IO) { getExploreRows(source, 1) }
                    emit(rows)
                }
            })
        }
    }

    override suspend fun getBookInformation(id: String): BookInformation {
        return withContext(Dispatchers.IO) {
            val source = currentSource ?: return@withContext BookInformation.empty(id)
            try {
                val info = getBookInfoFromLegado(source, id)
                _isOffLine.value = false
                info
            } catch (e: Exception) {
                Log.e(TAG, "getBookInfo error: ${e.message}")
                _isOffLine.value = true
                BookInformation.empty(id)
            }
        }
    }

    override suspend fun getBookVolumes(id: String): BookVolumes {
        return withContext(Dispatchers.IO) {
            val source = currentSource ?: return@withContext BookVolumes.empty(id)
            try {
                val volumes = getTocFromLegado(source, id)
                _isOffLine.value = false
                volumes
            } catch (e: Exception) {
                Log.e(TAG, "getBookVolumes error: ${e.message}")
                _isOffLine.value = true
                BookVolumes.empty(id)
            }
        }
    }

    override suspend fun getChapterContent(chapterId: String, bookId: String): ChapterContent {
        return withContext(Dispatchers.IO) {
            val source = currentSource ?: return@withContext ChapterContent.empty(chapterId)
            try {
                val content = getContentFromLegado(source, bookId, chapterId)
                _isOffLine.value = false
                content
            } catch (e: Exception) {
                Log.e(TAG, "getChapterContent error: ${e.message}")
                _isOffLine.value = true
                ChapterContent.empty(chapterId)
            }
        }
    }

    private fun searchLegado(source: BookSource, keyword: String): List<BookInformation> {
        val results = mutableListOf<BookInformation>()
        try {
            val searchUrl = source.searchUrl ?: return results
            val rule = source.getSearchRule()
            val finalUrl = buildSearchUrl(searchUrl, keyword, 1)
            Log.d(TAG, "Search URL: $finalUrl")
            val response = executeRequest(finalUrl, source)
            val body = response.body ?: return results
            val analyzer = AnalyzeRule(source, finalUrl)
            if (isJsonResponse(body)) analyzer.setContent(body)
            else analyzer.setContent(Jsoup.parse(body), finalUrl)
            val elements = analyzer.getElementList(rule.bookList)
            for (element in elements) {
                analyzer.setContent(element, finalUrl)
                val name = analyzer.getString(rule.name) ?: continue
                results.add(MutableBookInformation(
                    id = analyzer.getString(rule.bookUrl) ?: name,
                    title = name, subtitle = source.bookSourceName,
                    coverUrl = analyzer.getString(rule.coverUrl)?.let { Uri.parse(it) } ?: Uri.EMPTY,
                    author = analyzer.getString(rule.author) ?: "",
                    description = analyzer.getString(rule.intro) ?: "",
                    tags = emptyList(), publishingHouse = "", wordCount = WordCount(0),
                    lastUpdated = java.time.LocalDateTime.MIN, isComplete = false
                ))
            }
        } catch (e: Exception) { Log.e(TAG, "searchLegado error: ${e.message}") }
        return results
    }

    private fun getExploreRows(source: BookSource, page: Int): List<ExploreBooksRow> {
        val rows = mutableListOf<ExploreBooksRow>()
        try {
            val exploreUrl = source.exploreUrl ?: return rows
            val rule = source.getExploreRule()
            val url = buildSearchUrl(exploreUrl, "", page)
            val response = executeRequest(url, source)
            val body = response.body ?: return rows
            val analyzer = AnalyzeRule(source, url)
            if (isJsonResponse(body)) analyzer.setContent(body)
            else analyzer.setContent(Jsoup.parse(body), url)
            val elements = analyzer.getElementList(rule.bookList)
            if (elements.isNotEmpty()) {
                val books = mutableListOf<ExploreDisplayBook>()
                for (element in elements) {
                    analyzer.setContent(element, url)
                    val name = analyzer.getString(rule.name) ?: continue
                    books.add(ExploreDisplayBook(
                        id = analyzer.getString(rule.bookUrl) ?: name,
                        title = name,
                        author = analyzer.getString(rule.author) ?: "",
                        coverUri = analyzer.getString(rule.coverUrl)?.let { Uri.parse(it) } ?: Uri.EMPTY
                    ))
                }
                if (books.isNotEmpty()) rows.add(ExploreBooksRow("发现", books, false, ""))
            }
        } catch (e: Exception) { Log.e(TAG, "getExploreRows error: ${e.message}") }
        return rows
    }

    private fun getBookInfoFromLegado(source: BookSource, bookUrl: String): BookInformation {
        try {
            val rule = source.getBookInfoRule()
            val url = if (bookUrl.startsWith("http")) bookUrl else "${source.bookSourceUrl}$bookUrl"
            val response = executeRequest(url, source)
            val body = response.body ?: return BookInformation.empty(bookUrl)
            val analyzer = AnalyzeRule(source, url)
            analyzer.setContent(Jsoup.parse(body), url)
            return MutableBookInformation(
                id = bookUrl,
                title = analyzer.getString(rule.name) ?: bookUrl,
                subtitle = source.bookSourceName,
                coverUrl = analyzer.getString(rule.coverUrl)?.let { Uri.parse(it) } ?: Uri.EMPTY,
                author = analyzer.getString(rule.author) ?: "",
                description = analyzer.getString(rule.intro) ?: "",
                tags = analyzer.getString(rule.kind)?.split(",")?.map { it.trim() } ?: emptyList(),
                publishingHouse = "", wordCount = WordCount(0),
                lastUpdated = java.time.LocalDateTime.MIN, isComplete = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "getBookInfo error: ${e.message}")
            return BookInformation.empty(bookUrl)
        }
    }

    private fun getTocFromLegado(source: BookSource, bookUrl: String): BookVolumes {
        try {
            val rule = source.getTocRule()
            val url = if (bookUrl.startsWith("http")) bookUrl else "${source.bookSourceUrl}$bookUrl"
            val response = executeRequest(url, source)
            val body = response.body ?: return BookVolumes.empty(bookUrl)
            val analyzer = AnalyzeRule(source, url)
            analyzer.setContent(Jsoup.parse(body), url)
            val chapters = mutableListOf<ChapterInformation>()
            val elements = analyzer.getElementList(rule.chapterList)
            for (element in elements) {
                analyzer.setContent(element, url)
                val name = analyzer.getString(rule.chapterName) ?: continue
                val chapterUrl = analyzer.getString(rule.chapterUrl) ?: continue
                chapters.add(ChapterInformation(id = chapterUrl, title = name))
            }
            if (chapters.isEmpty()) return BookVolumes.empty(bookUrl)
            return BookVolumes(bookUrl, listOf(Volume("${bookUrl}_vol", "", chapters)))
        } catch (e: Exception) {
            Log.e(TAG, "getToc error: ${e.message}")
            return BookVolumes.empty(bookUrl)
        }
    }

    private fun getContentFromLegado(source: BookSource, bookUrl: String, chapterUrl: String): ChapterContent {
        try {
            val rule = source.getContentRule()
            val url = if (chapterUrl.startsWith("http")) chapterUrl else "${source.bookSourceUrl}$chapterUrl"
            val response = executeRequest(url, source)
            val body = response.body ?: return ChapterContent.empty(chapterUrl)
            val analyzer = AnalyzeRule(source, url)
            analyzer.setContent(Jsoup.parse(body), url)
            val title = analyzer.getString(rule.title) ?: ""
            val contentStr = analyzer.getString(rule.content) ?: ""
            if (contentStr.isBlank()) return ChapterContent.empty(chapterUrl)
            val builder = ContentBuilder()
            val paragraphs = contentStr.replace("\r\n", "\n").replace("\r", "\n").split("\n").filter { it.isNotBlank() }
            for (p in paragraphs) builder.component(SimpleTextComponentData(p.trim()))
            return MutableChapterContent(id = chapterUrl, title = title, content = builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "getContent error: ${e.message}")
            return ChapterContent.empty(chapterUrl)
        }
    }

    private fun buildSearchUrl(template: String, keyword: String, page: Int): String {
        var url = template
        if (url.startsWith("<js>") || url.startsWith("@js:")) {
            val jsStr = if (url.startsWith("<js>")) url.substring(4, url.indexOf("</js>"))
            else url.substring(4)
            val result = JsBridge.evalJS(jsStr, currentSource)
            url = result?.toString() ?: template
        }
        url = url.replace("{{key}}", keyword).replace("{{page}}", page.toString())
        if (url.startsWith("data:")) return url
        return url
    }

    private fun executeRequest(url: String, source: BookSource): StrResponse {
        if (url.startsWith("data:")) {
            val content = url.substringAfter(",")
            return StrResponse(url, emptyMap(), content, 200)
        }
        val builder = Request.Builder().url(url).get()
        source.getHeaderMap().forEach { builder.header(it.key, it.value) }
        val response = HttpClient.execute(builder.build())
        val responseBody = response.body?.string() ?: ""
        return StrResponse(url, response.headers.toMultimap(), responseBody, response.code)
    }

    private fun isJsonResponse(body: String): Boolean {
        val t = body.trimStart()
        return t.startsWith("{") || t.startsWith("[")
    }
}