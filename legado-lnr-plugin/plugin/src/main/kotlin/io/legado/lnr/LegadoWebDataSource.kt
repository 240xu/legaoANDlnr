@file:Suppress("OPT_IN_USAGE")

package io.legado.lnr

import android.net.Uri
import android.util.Log
import io.legado.engine.analyze.AnalyzeRule
import io.legado.engine.analyze.AnalyzeUrl
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
                    // 使用 AnalyzeUrl 处理完整 URL 模板
                    val analyzeUrl = AnalyzeUrl(
                        mUrl = searchUrl, key = keyword, page = 1,
                        baseUrl = source.bookSourceUrl, source = source,
                        jsEngine = BookSourceManager.getJsEngine()
                    )
                    val html = fetchUrl(analyzeUrl.url, source, analyzeUrl) ?: continue
                    val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                    // 支持 bookList 列表级提取
                    val sr = source.getSearchRule()
                    if (!sr.bookList.isNullOrBlank()) {
                        rule.setContent(html, analyzeUrl.url)
                        val elements = rule.getElements(sr.bookList!!)
                        for (element in elements) {
                            val childRule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                            childRule.setContent(element, analyzeUrl.url)
                            val name = childRule.getString(sr.name ?: "")
                            val bookUrl = childRule.getString(sr.bookUrl ?: "")
                            if (name.isBlank() || bookUrl.isBlank()) continue
                            val bookId = "${source.bookSourceUrl}|$bookUrl"
                            emit(SearchResult.MultipleBook(makeBookInfo(
                                bookId, name, source.bookSourceName,
                                childRule.getString(sr.author ?: ""),
                                childRule.getString(sr.intro ?: ""),
                                childRule.getString(sr.coverUrl ?: "").ifBlank { null },
                                childRule.getString(sr.kind ?: "")
                            )))
                            foundAny = true
                        }
                    } else {
                        rule.setContent(html, analyzeUrl.url)
                        val names = rule.getStringList(sr.name ?: "")
                        val authors = rule.getStringList(sr.author ?: "")
                        val bookUrls = rule.getStringList(sr.bookUrl ?: "")
                        val covers = rule.getStringList(sr.coverUrl ?: "")
                        val intros = rule.getStringList(sr.intro ?: "")
                        val count = minOf(names.size, bookUrls.size)
                        for (i in 0 until count) {
                            val bookId = "${source.bookSourceUrl}|${bookUrls.getOrNull(i) ?: ""}"
                            emit(SearchResult.MultipleBook(makeBookInfo(
                                bookId, names[i], source.bookSourceName,
                                authors.getOrElse(i) { "" }, intros.getOrElse(i) { "" },
                                covers.getOrNull(i)?.ifBlank { null }
                            )))
                            foundAny = true
                        }
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
                    // 支持 JSON 数组格式的 exploreUrl（多分类）
                    val urls = parseExploreUrls(exploreUrl)
                    for ((categoryName, categoryUrl) in urls) {
                        try {
                            val analyzeUrl = AnalyzeUrl(
                                mUrl = categoryUrl, page = 1,
                                baseUrl = source.bookSourceUrl, source = source,
                                jsEngine = BookSourceManager.getJsEngine()
                            )
                            val html = fetchUrl(analyzeUrl.url, source, analyzeUrl) ?: continue
                            val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                            rule.setContent(html, analyzeUrl.url)
                            val er = source.getExploreRule()
                            val bookListSelector = er.bookList
                            if (!bookListSelector.isNullOrBlank()) {
                                val elements = rule.getElements(bookListSelector)
                                val books = elements.mapNotNull { element ->
                                    val childRule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                                    childRule.setContent(element, analyzeUrl.url)
                                    val name = childRule.getString(er.name ?: "")
                                    val bookUrl = childRule.getString(er.bookUrl ?: "")
                                    if (name.isBlank() || bookUrl.isBlank()) return@mapNotNull null
                                    ExploreDisplayBook(
                                        "${source.bookSourceUrl}|$bookUrl", name,
                                        childRule.getString(er.author ?: ""),
                                        childRule.getString(er.coverUrl ?: "").let {
                                            if (it.isNotBlank()) Uri.parse(it) else Uri.EMPTY
                                        }
                                    )
                                }
                                if (books.isNotEmpty()) {
                                    rows.add(ExploreBooksRow(
                                        categoryName.ifBlank { source.bookSourceName },
                                        books, false, ""
                                    ))
                                }
                            } else {
                                val names = rule.getStringList(er.name ?: "")
                                val bookUrls = rule.getStringList(er.bookUrl ?: "")
                                val covers = rule.getStringList(er.coverUrl ?: "")
                                val authors = rule.getStringList(er.author ?: "")
                                val count = minOf(names.size, bookUrls.size)
                                if (count > 0) {
                                    val books = (0 until count).map { i ->
                                        ExploreDisplayBook(
                                            "${source.bookSourceUrl}|${bookUrls[i]}", names[i],
                                            authors.getOrElse(i) { "" },
                                            covers.getOrNull(i)?.let { Uri.parse(it) } ?: Uri.EMPTY
                                        )
                                    }
                                    rows.add(ExploreBooksRow(
                                        categoryName.ifBlank { source.bookSourceName },
                                        books, false, ""
                                    ))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "发现页分类失败 [$categoryName]: ${e.message}")
                        }
                    }
                    _rowsFlow.value = rows
                } catch (e: Exception) {
                    Log.e(TAG, "发现页刷新失败: ${e.message}")
                }
            }.start()
        }

        private fun parseExploreUrls(exploreUrl: String): List<Pair<String, String>> {
            return try {
                if (exploreUrl.trimStart().startsWith("[")) {
                    val arr = com.google.gson.Gson().fromJson(exploreUrl, Array::class.java)
                    arr?.mapNotNull { item ->
                        when (item) {
                            is Map<*, *> -> {
                                val title = item["title"]?.toString() ?: ""
                                val url = item["url"]?.toString() ?: item["link"]?.toString() ?: ""
                                if (url.isNotBlank()) Pair(title, url) else null
                            }
                            is String -> if (item.isNotBlank()) Pair("", item) else null
                            else -> null
                        }
                    } ?: listOf("" to exploreUrl)
                } else {
                    listOf("" to exploreUrl)
                }
            } catch (_: Exception) { listOf("" to exploreUrl) }
        }
    }

    // ========== Book Information ==========
    override suspend fun getBookInformation(id: String): BookInformation {
        return withContext(Dispatchers.IO) {
            try {
                val (sourceUrl, bookUrl) = parseBookId(id)
                val source = BookSourceManager.getSource(sourceUrl)
                    ?: return@withContext MutableBookInformation.empty(id)
                val analyzeUrl = AnalyzeUrl(
                    mUrl = bookUrl, baseUrl = source.bookSourceUrl,
                    source = source, jsEngine = BookSourceManager.getJsEngine()
                )
                val html = fetchUrl(analyzeUrl.url, source, analyzeUrl)
                    ?: return@withContext MutableBookInformation.empty(id)
                val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                rule.setContent(html, analyzeUrl.url)
                val info = source.getBookInfoRule()
                // 如果有 init JS，先执行
                if (!info.init.isNullOrBlank()) {
                    try { BookSourceManager.getJsEngine().eval(info.init!!, source) } catch (_: Exception) {}
                }
                makeBookInfo(id, rule.getString(info.name ?: ""), source.bookSourceName,
                    rule.getString(info.author ?: ""), rule.getString(info.intro ?: ""),
                    rule.getString(info.coverUrl ?: "").ifBlank { null },
                    rule.getString(info.kind ?: ""))
            } catch (e: Exception) {
                Log.e(TAG, "获取书籍信息失败: ${e.message}", e)
                MutableBookInformation.empty(id)
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
                // 执行 preUpdateJs
                val toc = source.getTocRule()
                if (!toc.preUpdateJs.isNullOrBlank()) {
                    try { BookSourceManager.getJsEngine().eval(toc.preUpdateJs!!, source) } catch (_: Exception) {}
                }
                val allChapters = mutableListOf<ChapterInformation>()
                var currentUrl: String? = bookUrl
                var maxPages = 10 // 防止无限翻页
                while (currentUrl != null && maxPages-- > 0) {
                    val analyzeUrl = AnalyzeUrl(
                        mUrl = currentUrl, baseUrl = source.bookSourceUrl,
                        source = source, jsEngine = BookSourceManager.getJsEngine()
                    )
                    val html = fetchUrl(analyzeUrl.url, source, analyzeUrl) ?: break
                    val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                    rule.setContent(html, analyzeUrl.url)
                    val chapterListSelector = toc.chapterList
                    if (chapterListSelector.isNullOrBlank()) break
                    val elements = rule.getElements(chapterListSelector)
                    for (element in elements) {
                        val childRule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                        childRule.setContent(element, analyzeUrl.url)
                        val name = childRule.getString(toc.chapterName ?: "")
                        val url = childRule.getString(toc.chapterUrl ?: "")
                        // 支持 isVolume 标记
                        val isVolume = if (!toc.isVolume.isNullOrBlank()) {
                            childRule.getString(toc.isVolume!!).toBoolean()
                        } else false
                        if (isVolume) continue // 跳过卷标题行
                        allChapters.add(ChapterInformation(
                            id = if (url.isNotBlank()) "${source.bookSourceUrl}|$url" else "${id}_ch_${allChapters.size}",
                            title = name.ifBlank { "第${allChapters.size + 1}章" }
                        ))
                    }
                    // 支持 nextTocUrl 翻页
                    currentUrl = if (!toc.nextTocUrl.isNullOrBlank()) {
                        val nextUrl = rule.getString(toc.nextTocUrl!!)
                        if (nextUrl.isNotBlank()) nextUrl else null
                    } else null
                }
                BookVolumes(id, listOf(Volume("${id}_vol_0", "", allChapters)))
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
                val analyzeUrl = AnalyzeUrl(
                    mUrl = chapterId, baseUrl = source.bookSourceUrl,
                    source = source, jsEngine = BookSourceManager.getJsEngine()
                )
                val html = fetchUrl(analyzeUrl.url, source, analyzeUrl)
                    ?: return@withContext MutableChapterContent.empty()
                val rule = AnalyzeRule(source, BookSourceManager.getJsEngine())
                rule.setContent(html, analyzeUrl.url)
                val cr = source.getContentRule()
                val contentRule = cr.content ?: ""
                val rawContent = rule.getString(contentRule)
                // 支持 subContent（副文本拼接）
                val subContent = if (!cr.subContent.isNullOrBlank()) {
                    rule.getString(cr.subContent!!)
                } else ""
                val fullContent = if (subContent.isNotBlank()) "$rawContent\n$subContent" else rawContent
                if (fullContent.isBlank()) return@withContext MutableChapterContent.empty()
                // 支持 replaceRegex（文本替换规则）
                val processedContent = applyReplaceRegex(fullContent, cr.replaceRegex)
                // 支持 title（从正文获取标题）
                val contentTitle = if (!cr.title.isNullOrBlank()) rule.getString(cr.title!!) else ""
                val builder = ContentBuilder()
                processedContent.replace("\r\n", "\n").replace("\r", "\n")
                    .split("\n").filter { it.isNotBlank() }
                    .forEach { builder.component(SimpleTextComponentData(it.trim())) }
                MutableChapterContent(chapterId, contentTitle, builder.build(), "", "")
            } catch (e: Exception) {
                Log.e(TAG, "获取正文失败: ${e.message}", e)
                MutableChapterContent.empty()
            }
        }
    }

    // ========== Helpers ==========
    private fun makeBookInfo(id: String, title: String, subtitle: String,
                             author: String, description: String, coverUrl: String?,
                             kind: String = ""): BookInformation {
        return MutableBookInformation(
            id = id, title = title, subtitle = subtitle,
            coverUrl = coverUrl?.let { Uri.parse(it) } ?: Uri.EMPTY,
            author = author, description = description,
            tags = if (kind.isNotBlank()) kind.split(",").map { it.trim() } else emptyList(),
            publishingHouse = "",
            wordCount = WordCount(0), lastUpdated = LocalDateTime.MIN, isComplete = false
        )
    }

    private fun applyReplaceRegex(content: String, replaceRegex: String?): String {
        if (replaceRegex.isNullOrBlank()) return content
        var result = content
        // replaceRegex 格式: "regex1@@replacement1||regex2@@replacement2"
        replaceRegex.split("||").forEach { rule ->
            val parts = rule.split("@@", limit = 2)
            if (parts.size == 2) {
                try {
                    result = result.replace(Regex(parts[0]), parts[1])
                } catch (_: Exception) {
                    result = result.replace(parts[0], parts[1])
                }
            }
        }
        return result
    }

    private fun fetchUrl(url: String, source: BookSource, analyzeUrl: AnalyzeUrl? = null): String? = try {
        val client = BookSourceManager.getHttpClient()
        val headerMap = source.getHeaderMap().toMutableMap()
        analyzeUrl?.getHeaderMap()?.let { headerMap.putAll(it) }
        val requestBuilder = Request.Builder().url(url)
        headerMap.forEach { (k, v) -> requestBuilder.header(k, v) }
        requestBuilder.header("User-Agent", headerMap["User-Agent"] ?: "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
        val method = analyzeUrl?.getMethod() ?: "GET"
        val body = analyzeUrl?.getBody()
        if (method == "POST" && body != null) {
            requestBuilder.post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
        }
        val response = client.client.newCall(requestBuilder.build()).execute()
        if (response.isSuccessful) response.body?.string() else null
    } catch (e: Exception) { Log.e(TAG, "请求失败: $url, ${e.message}"); null }

    private fun parseBookId(id: String): Pair<String, String> {
        val parts = id.split("|", limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else "" to id
    }
}
