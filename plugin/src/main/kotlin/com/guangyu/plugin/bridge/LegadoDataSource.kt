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
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.Base64
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
                _isOffLine.value = true; emit(SearchResult.Empty())
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
                _isOffLine.value = false; info
            } catch (e: Exception) {
                Log.e(TAG, "getBookInfo error: ${e.message}")
                _isOffLine.value = true; BookInformation.empty(id)
            }
        }
    }

    override suspend fun getBookVolumes(id: String): BookVolumes {
        return withContext(Dispatchers.IO) {
            val source = currentSource ?: return@withContext BookVolumes.empty(id)
            try {
                val volumes = getTocFromLegado(source, id)
                _isOffLine.value = false; volumes
            } catch (e: Exception) {
                Log.e(TAG, "getBookVolumes error: ${e.message}")
                _isOffLine.value = true; BookVolumes.empty(id)
            }
        }
    }

    override suspend fun getChapterContent(chapterId: String, bookId: String): ChapterContent {
        return withContext(Dispatchers.IO) {
            val source = currentSource ?: return@withContext ChapterContent.empty(chapterId)
            try {
                val content = getContentFromLegado(source, bookId, chapterId)
                _isOffLine.value = false; content
            } catch (e: Exception) {
                Log.e(TAG, "getChapterContent error: ${e.message}")
                _isOffLine.value = true; ChapterContent.empty(chapterId)
            }
        }
    }

    // ===== 搜索 =====
    private fun searchLegado(source: BookSource, keyword: String): List<BookInformation> {
        val results = mutableListOf<BookInformation>()
        try {
            val searchUrl = source.searchUrl ?: return results
            val rule = source.getSearchRule()
            val finalUrl = buildRuleUrl(searchUrl, source, keyword, 1)
            Log.d(TAG, "Search URL: $finalUrl")
            val body = executeUrl(finalUrl, source) ?: return results
            val analyzer = AnalyzeRule(source, finalUrl)
            // 解析响应
            if (isJsonResponse(body)) {
                analyzer.setContent(body, finalUrl)
            } else {
                analyzer.setContent(Jsoup.parse(body), finalUrl)
            }
            // JS bookList 规则可能返回 JSON 数组字符串
            val elements = analyzer.getElementList(rule.bookList)
            for (element in elements) {
                val elementStr = element.toString()
                analyzer.setContent(
                    if (elementStr.isJsonObject() || elementStr.isJsonArray()) elementStr
                    else if (element is org.jsoup.nodes.Node) element
                    else Jsoup.parse(elementStr),
                    finalUrl
                )
                val name = analyzer.getString(rule.name) ?: continue
                val bookUrl = analyzer.getString(rule.bookUrl) ?: name
                results.add(MutableBookInformation(
                    id = bookUrl, title = name, subtitle = source.bookSourceName,
                    coverUrl = analyzer.getString(rule.coverUrl)?.let { Uri.parse(it) } ?: Uri.EMPTY,
                    author = analyzer.getString(rule.author) ?: "",
                    description = analyzer.getString(rule.intro) ?: "",
                    tags = analyzer.getString(rule.kind)?.split(",")?.map { it.trim() } ?: emptyList(),
                    publishingHouse = "", wordCount = WordCount(0),
                    lastUpdated = java.time.LocalDateTime.MIN, isComplete = false
                ))
            }
        } catch (e: Exception) { Log.e(TAG, "searchLegado error: ${e.message}") }
        return results
    }

    // ===== 发现页 =====
    private fun getExploreRows(source: BookSource, page: Int): List<ExploreBooksRow> {
        val rows = mutableListOf<ExploreBooksRow>()
        try {
            val exploreUrl = source.exploreUrl ?: return rows
            val rule = source.getExploreRule()
            val url = buildRuleUrl(exploreUrl, source, "", page)
            Log.d(TAG, "Explore URL: $url")
            val body = executeUrl(url, source) ?: return rows
            val analyzer = AnalyzeRule(source, url)
            if (isJsonResponse(body)) analyzer.setContent(body, url)
            else analyzer.setContent(Jsoup.parse(body), url)
            val elements = analyzer.getElementList(rule.bookList)
            if (elements.isNotEmpty()) {
                val books = mutableListOf<ExploreDisplayBook>()
                for (element in elements) {
                    val elementStr = element.toString()
                    analyzer.setContent(
                        if (elementStr.isJsonObject() || elementStr.isJsonArray()) elementStr
                        else if (element is org.jsoup.nodes.Node) element
                        else Jsoup.parse(elementStr),
                        url
                    )
                    val name = analyzer.getString(rule.name) ?: continue
                    val bookUrl = analyzer.getString(rule.bookUrl) ?: name
                    books.add(ExploreDisplayBook(
                        id = bookUrl, title = name,
                        author = analyzer.getString(rule.author) ?: "",
                        coverUri = analyzer.getString(rule.coverUrl)?.let { Uri.parse(it) } ?: Uri.EMPTY
                    ))
                }
                if (books.isNotEmpty()) rows.add(ExploreBooksRow("发现", books, false, ""))
            }
        } catch (e: Exception) { Log.e(TAG, "getExploreRows error: ${e.message}") }
        return rows
    }

    // ===== 书籍详情 =====
    private fun getBookInfoFromLegado(source: BookSource, bookId: String): BookInformation {
        try {
            val rule = source.getBookInfoRule()
            // bookId 可能是 data:base64 URL，需要解码
            val (actualUrl, decodedData) = decodeDataUrl(bookId)
            Log.d(TAG, "BookInfo actualUrl: $actualUrl")

            // 如果有 init 规则，先执行 JS 预处理
            if (!rule.init.isNullOrBlank()) {
                val initResult = evalRuleJs(rule.init!!, source, actualUrl, decodedData)
                Log.d(TAG, "Init result: ${(initResult as? String)?.take(200)}")
            }

            // 请求详情页
            val body = if (actualUrl.startsWith("http")) {
                executeUrl(actualUrl, source)
            } else if (decodedData != null) {
                // data: URL，数据已在 decodedData 中
                decodedData
            } else {
                executeUrl(actualUrl, source)
            } ?: return BookInformation.empty(bookId)

            val analyzer = AnalyzeRule(source, actualUrl)
            if (isJsonResponse(body!!)) analyzer.setContent(body!!, actualUrl)
            else analyzer.setContent(Jsoup.parse(body!!), actualUrl)

            return MutableBookInformation(
                id = bookId,
                title = analyzer.getString(rule.name) ?: bookId,
                subtitle = source.bookSourceName,
                coverUrl = analyzer.getString(rule.coverUrl)?.let { Uri.parse(it) } ?: Uri.EMPTY,
                author = analyzer.getString(rule.author) ?: "",
                description = analyzer.getString(rule.intro) ?: "",
                tags = analyzer.getString(rule.kind)?.split(",")?.map { it.trim() } ?: emptyList(),
                publishingHouse = "", wordCount = WordCount(0),
                lastUpdated = java.time.LocalDateTime.MIN, isComplete = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "getBookInfo error: ${e.message}", e)
            return BookInformation.empty(bookId)
        }
    }

    // ===== 目录 =====
    private fun getTocFromLegado(source: BookSource, bookId: String): BookVolumes {
        try {
            val rule = source.getTocRule()
            val (actualUrl, _) = decodeDataUrl(bookId)
            val url = if (actualUrl.startsWith("http")) actualUrl else "${source.bookSourceUrl}$actualUrl"
            val body = executeUrl(url, source) ?: return BookVolumes.empty(bookId)
            val analyzer = AnalyzeRule(source, url)
            if (isJsonResponse(body)) analyzer.setContent(body, url)
            else analyzer.setContent(Jsoup.parse(body), url)

            val chapters = mutableListOf<ChapterInformation>()
            val elements = analyzer.getElementList(rule.chapterList)
            for (element in elements) {
                val elementStr = element.toString()
                analyzer.setContent(
                    if (elementStr.isJsonObject()) elementStr
                    else if (element is org.jsoup.nodes.Node) element
                    else Jsoup.parse(elementStr),
                    url
                )
                val name = analyzer.getString(rule.chapterName) ?: continue
                val chapterUrl = analyzer.getString(rule.chapterUrl) ?: continue
                chapters.add(ChapterInformation(id = chapterUrl, title = name))
            }
            if (chapters.isEmpty()) return BookVolumes.empty(bookId)
            return BookVolumes(bookId, listOf(Volume("${bookId}_vol", "", chapters)))
        } catch (e: Exception) {
            Log.e(TAG, "getToc error: ${e.message}")
            return BookVolumes.empty(bookId)
        }
    }

    // ===== 正文 =====
    private fun getContentFromLegado(source: BookSource, bookId: String, chapterId: String): ChapterContent {
        try {
            val rule = source.getContentRule()
            // chapterId 可能是 data:base64 URL
            val (actualUrl, decodedData) = decodeDataUrl(chapterId)
            Log.d(TAG, "Content actualUrl: ${actualUrl.take(100)}")

            // 内容规则可能是纯 JS
            if (!rule.content.isNullOrBlank() && rule.content!!.trimStart().startsWith("<js>")) {
                val jsCode = rule.content!!.let {
                    val s = it.indexOf("<js>") + 4
                    val e = it.indexOf("</js>")
                    if (e > s) it.substring(s, e) else it
                }
                val jsResult = evalRuleJs(jsCode, source, actualUrl, decodedData)
                if (jsResult != null) {
                    return parseJsContentResult(jsResult.toString(), chapterId, source)
                }
            }

            // 普通 HTML 解析
            val url = if (actualUrl.startsWith("http")) actualUrl else "${source.bookSourceUrl}$actualUrl"
            val body = executeUrl(url, source) ?: return ChapterContent.empty(chapterId)
            val analyzer = AnalyzeRule(source, url)
            if (isJsonResponse(body)) analyzer.setContent(body, url)
            else analyzer.setContent(Jsoup.parse(body), url)

            val title = analyzer.getString(rule.title) ?: ""
            val contentStr = analyzer.getString(rule.content) ?: ""
            if (contentStr.isBlank()) return ChapterContent.empty(chapterId)
            return buildChapterContent(chapterId, title, contentStr)
        } catch (e: Exception) {
            Log.e(TAG, "getContent error: ${e.message}", e)
            return ChapterContent.empty(chapterId)
        }
    }

    // ===== data: URL 解码 =====
    /**
     * 解码 data:;base64,XXX,{...} 格式的 URL
     * 返回 (实际请求URL 或 解码后的数据, JSON 数据)
     */
    private fun decodeDataUrl(encoded: String): Pair<String, String?> {
        if (!encoded.startsWith("data:")) return Pair(encoded, null)
        return try {
            // data:;base64,BASE64_DATA,EXTRA_JSON
            val afterData = encoded.substringAfter("data:;base64,")
            val parts = afterData.split(",", limit = 2)
            val base64Part = parts[0]
            val jsonPart = parts.getOrNull(1)
            val decoded = String(Base64.getDecoder().decode(base64Part), Charsets.UTF_8)
            Log.d(TAG, "data: decoded: ${decoded.take(200)}")
            Pair(decoded, jsonPart)
        } catch (e: Exception) {
            Log.e(TAG, "decodeDataUrl error: ${e.message}")
            Pair(encoded, null)
        }
    }

    // ===== JS 执行辅助 =====
    private fun evalRuleJs(jsCode: String, source: BookSource, url: String, data: String?): Any? {
        return try {
            JsBridge.evalJS(jsCode, source, url, data)
        } catch (e: Exception) {
            Log.e(TAG, "evalRuleJs error: ${e.message}")
            null
        }
    }

    private fun parseJsContentResult(jsResult: String, chapterId: String, source: BookSource): ChapterContent {
        return try {
            // JS 结果可能是 JSON: {"content": "...", "title": "..."}
            if (jsResult.trimStart().startsWith("{")) {
                val obj = JSONObject(jsResult)
                val content = obj.optString("content", "")
                val title = obj.optString("title", "")
                if (content.isNotBlank()) {
                    return buildChapterContent(chapterId, title, content)
                }
            }
            // 直接是文本
            if (jsResult.isNotBlank()) {
                return buildChapterContent(chapterId, "", jsResult)
            }
            ChapterContent.empty(chapterId)
        } catch (e: Exception) {
            Log.e(TAG, "parseJsContentResult error: ${e.message}")
            ChapterContent.empty(chapterId)
        }
    }

    private fun buildChapterContent(chapterId: String, title: String, rawContent: String): ChapterContent {
        val builder = ContentBuilder()
        val paragraphs = rawContent.replace("\r\n", "\n").replace("\r", "\n")
            .split("\n").filter { it.isNotBlank() }
        for (p in paragraphs) builder.component(SimpleTextComponentData(p.trim()))
        return MutableChapterContent(id = chapterId, title = title, content = builder.build())
    }

    // ===== URL 构建 =====
    private fun buildRuleUrl(template: String, source: BookSource, keyword: String, page: Int): String {
        var url = template
        // JS 规则生成 URL
        if (url.trimStart().startsWith("<js>") || url.trimStart().startsWith("@js:")) {
            val jsStr = if (url.trimStart().startsWith("<js>")) {
                val s = url.indexOf("<js>") + 4
                val e = url.indexOf("</js>")
                if (e > s) url.substring(s, e) else url
            } else {
                url.substring(4)
            }
            val result = JsBridge.evalJS(jsStr, source, source.bookSourceUrl, null)
            url = result?.toString() ?: template
        }
        // 替换模板变量
        url = url.replace("{{key}}", keyword)
            .replace("{{page}}", page.toString())
            .replace("searchKey", keyword)
            .replace("searchPage", page.toString())
        return url
    }

    // ===== HTTP 请求 =====
    private fun executeUrl(url: String, source: BookSource): String? {
        if (url.startsWith("data:")) {
            // data: URL 已经包含数据
            val (_, data) = decodeDataUrl(url)
            return data
        }
        return try {
            val builder = Request.Builder().url(url).get()
            source.getHeaderMap().forEach { builder.header(it.key, it.value) }
            val response = HttpClient.execute(builder.build())
            response.body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "executeUrl($url) error: ${e.message}")
            null
        }
    }

    private fun isJsonResponse(body: String): Boolean {
        val t = body.trimStart()
        return t.startsWith("{") || t.startsWith("[")
    }

    private fun String.isJsonObject(): Boolean = trimStart().startsWith("{")
    private fun String.isJsonArray(): Boolean = trimStart().startsWith("[")
}
