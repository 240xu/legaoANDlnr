@file:Suppress("OPT_IN_USAGE")

package io.legado.lnr

import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.legado.engine.analyze.AnalyzeRule
import io.legado.engine.analyze.AnalyzeUrl
import io.legado.engine.entity.BookSource
import io.legado.engine.http.HttpClient
import io.legado.engine.js.EngineJsExtensions
import io.legado.engine.js.JsEngine
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
import java.time.LocalDateTime
import java.util.Base64

/**
 * Legado WebDataSource - 桥接 Legado 书源引擎与 LNR API
 * 实现搜索、探索、书籍详情、目录、正文的完整解析
 * 支持 data:;base64,... URL 路由（聚合源）
 */
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
            if (sources.isEmpty()) {
                emit(SearchResult.Empty())
                return@flow
            }
            var foundAny = false
            for (source in sources) {
                try {
                    val searchUrl = source.searchUrl ?: continue
                    val jsEngine = BookSourceManager.getJsEngine()
                    val httpClient = BookSourceManager.getHttpClient()

                    // 执行 searchUrl（可能是 JS 代码，返回 data: URL 或实际 URL）
                    val evaluatedUrl = evaluateUrl(searchUrl, source, keyword, jsEngine)
                    Log.d(TAG, "搜索 URL: $evaluatedUrl")

                    // 处理 data: URL 路由
                    val actualData = fetchDataFromUrl(evaluatedUrl, source, httpClient, jsEngine)
                    if (actualData.isNullOrBlank()) continue

                    // 使用 ruleSearch 解析搜索结果
                    val searchRule = source.getSearchRule()
                    val ruleAnalyzer = AnalyzeRule(source, jsEngine)

                    // 解析 bookList
                    val bookListRule = searchRule.bookList
                    if (bookListRule.isNullOrBlank()) continue

                    val books = parseBookList(actualData, bookListRule, source, searchRule, ruleAnalyzer)
                    for (book in books) {
                        emit(SearchResult.MultipleBook(book))
                        foundAny = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "搜索失败 [${source.bookSourceName}]: ${e.message}")
                }
            }
            if (!foundAny) {
                emit(SearchResult.Empty())
            }
        }
    }

    // ========== Explore ==========
    override val explorePageProvider: ExplorePageProvider =
        object : AbstractDefaultExplorePageProvider() {
            init {
                try {
                    val sources = BookSourceManager.getEnabledSources().filter { it.enabledExplore }
                    for (source in sources) {
                        val exploreUrl = source.exploreUrl ?: continue
                        registerTapPage(source.bookSourceUrl, LegadoExploreTapPage(source, exploreUrl))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "探索页加载失败: ${e.message}")
                }
            }
        }

    /**
     * 探索页 TapPage - 对应一个书源的发现页
     */
    private class LegadoExploreTapPage(
        private val source: BookSource,
        private val exploreUrl: String
    ) : ExploreTapPageDataSource {
        override val title: String get() = source.bookSourceName

        override fun getRowsFlow(): Flow<List<ExploreBooksRow>> = flow {
            try {
                val jsEngine = BookSourceManager.getJsEngine()
                val httpClient = BookSourceManager.getHttpClient()

                // 执行 exploreUrl（通常是 JS 代码）
                val evaluated = evaluateExploreUrl(exploreUrl, source, jsEngine)
                Log.d(TAG, "探索结果: ${evaluated?.take(200)}")

                if (evaluated.isNullOrBlank()) {
                    emit(emptyList())
                    return@flow
                }

                // 解析探索结果为 ExploreBooksRow 列表
                val rows = parseExploreResult(evaluated, source, jsEngine)
                emit(rows)
            } catch (e: Exception) {
                Log.e(TAG, "探索页解析失败: ${e.message}")
                emit(emptyList())
            }
        }
    }

    // ========== Book Info ==========
    override suspend fun getBookInformation(id: String): BookInformation {
        return withContext(Dispatchers.IO) {
            try {
                val source = BookSourceManager.getSourceForBook(id) ?: return@withContext emptyBookInfo(id)
                val jsEngine = BookSourceManager.getJsEngine()
                val httpClient = BookSourceManager.getHttpClient()
                val bookInfoRule = source.getBookInfoRule()

                // 处理 data: URL（从搜索结果的 bookUrl 来的）
                val baseUrl = decodeDataUrl(id, source)
                val initData = if (baseUrl.startsWith("data:")) {
                    extractBase64Data(baseUrl)
                } else {
                    fetchPageContent(baseUrl, source, httpClient)
                }

                // 执行 init 规则
                var processedData = initData
                val initRule = bookInfoRule.init
                if (!initRule.isNullOrBlank()) {
                    val ruleAnalyzer = AnalyzeRule(source, jsEngine)
                    ruleAnalyzer.setContent(initData, source.sourceUrl)
                    processedData = ruleAnalyzer.getString(initRule)
                    if (processedData.isBlank()) processedData = initData ?: ""
                }

                // 提取书籍信息
                val ruleAnalyzer = AnalyzeRule(source, jsEngine)
                ruleAnalyzer.setContent(processedData, source.sourceUrl)

                val name = ruleAnalyzer.getString(bookInfoRule.name ?: "")
                val author = ruleAnalyzer.getString(bookInfoRule.author ?: "")
                val coverUrl = ruleAnalyzer.getString(bookInfoRule.coverUrl ?: "")
                val intro = ruleAnalyzer.getString(bookInfoRule.intro ?: "")
                val kind = ruleAnalyzer.getString(bookInfoRule.kind ?: "")
                val lastChapter = ruleAnalyzer.getString(bookInfoRule.lastChapter ?: "")
                val tocUrl = ruleAnalyzer.getString(bookInfoRule.tocUrl ?: "")

                MutableBookInformation(
                    id = id,
                    title = name.ifBlank { id },
                    subtitle = source.bookSourceName,
                    coverUrl = if (coverUrl.isNotBlank()) Uri.parse(coverUrl) else Uri.EMPTY,
                    author = author,
                    description = intro,
                    tags = if (kind.isNotBlank()) kind.split(",").map { it.trim() } else emptyList(),
                    publishingHouse = "",
                    wordCount = WordCount(0),
                    lastUpdated = LocalDateTime.now(),
                    isComplete = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "获取书籍信息失败: ${e.message}")
                emptyBookInfo(id)
            }
        }
    }

    // ========== Book Volumes (TOC) ==========
    override suspend fun getBookVolumes(id: String): BookVolumes {
        return withContext(Dispatchers.IO) {
            try {
                val source = BookSourceManager.getSourceForBook(id) ?: return@withContext BookVolumes.empty(id)
                val jsEngine = BookSourceManager.getJsEngine()
                val httpClient = BookSourceManager.getHttpClient()
                val tocRule = source.getTocRule()

                // 获取目录 URL
                val tocUrl = getTocUrl(id, source, jsEngine, httpClient)
                if (tocUrl.isNullOrBlank()) return@withContext BookVolumes.empty(id)

                val allChapters = mutableListOf<ChapterInformation>()
                var currentUrl: String? = tocUrl

                // 支持分页目录 (nextTocUrl)
                while (currentUrl != null) {
                    val pageContent = fetchPageContent(currentUrl, source, httpClient) ?: break
                    val ruleAnalyzer = AnalyzeRule(source, jsEngine)
                    ruleAnalyzer.setContent(pageContent, currentUrl)

                    // 执行 preUpdateJs
                    val preUpdateJs = tocRule.preUpdateJs
                    if (!preUpdateJs.isNullOrBlank()) {
                        ruleAnalyzer.getString(preUpdateJs)
                    }

                    // 解析章节列表
                    val chapterListRule = tocRule.chapterList ?: break
                    val elements = ruleAnalyzer.getElements(chapterListRule)
                    val isVolumeRule = tocRule.isVolume
                    val chapterNameRule = tocRule.chapterName ?: ""
                    val chapterUrlRule = tocRule.chapterUrl ?: ""

                    for (element in elements) {
                        val elementAnalyzer = AnalyzeRule(source, jsEngine)
                        elementAnalyzer.setContent(element, currentUrl)

                        // 检查是否是卷标题
                        if (!isVolumeRule.isNullOrBlank()) {
                            val isVol = elementAnalyzer.getString(isVolumeRule)
                            if (isVol.isNotBlank()) {
                                // 卷标题暂时跳过，后续可扩展
                            }
                        }

                        val chapterName = elementAnalyzer.getString(chapterNameRule)
                        val chapterUrl = elementAnalyzer.getString(chapterUrlRule)
                        if (chapterName.isNotBlank() && chapterUrl.isNotBlank()) {
                            allChapters.add(ChapterInformation(
                                id = chapterUrl,
                                title = chapterName
                            ))
                        }
                    }

                    // 检查下一页目录
                    currentUrl = if (!tocRule.nextTocUrl.isNullOrBlank()) {
                        val nextUrl = ruleAnalyzer.getString(tocRule.nextTocUrl!!)
                        if (nextUrl.isNotBlank() && nextUrl != currentUrl) nextUrl else null
                    } else null
                }

                if (allChapters.isEmpty()) return@withContext BookVolumes.empty(id)
                val volume = Volume(volumeId = "${id}_vol_0", volumeTitle = "", chapters = allChapters)
                BookVolumes(id, listOf(volume))
            } catch (e: Exception) {
                Log.e(TAG, "获取目录失败: ${e.message}")
                BookVolumes.empty(id)
            }
        }
    }

    // ========== Chapter Content ==========
    override suspend fun getChapterContent(chapterId: String, bookId: String): ChapterContent {
        return withContext(Dispatchers.IO) {
            try {
                val source = BookSourceManager.getSourceForBook(bookId)
                    ?: return@withContext ChapterContent.empty(chapterId)
                val jsEngine = BookSourceManager.getJsEngine()
                val httpClient = BookSourceManager.getHttpClient()
                val contentRule = source.getContentRule()

                // 处理 data: URL 路由
                val actualUrl = decodeDataUrl(chapterId, source)
                val rawContent = if (actualUrl.startsWith("data:")) {
                    // 对于 data: URL，提取 base64 数据并传递给规则
                    extractBase64Data(actualUrl)
                } else {
                    fetchPageContent(actualUrl, source, httpClient)
                }

                if (rawContent.isNullOrBlank()) {
                    return@withContext ChapterContent.empty(chapterId)
                }

                val ruleAnalyzer = AnalyzeRule(source, jsEngine)
                ruleAnalyzer.setContent(rawContent, actualUrl)

                // 执行 content 规则
                val contentRuleStr = contentRule.content
                var text = if (!contentRuleStr.isNullOrBlank()) {
                    ruleAnalyzer.getString(contentRuleStr)
                } else {
                    rawContent
                }

                // 执行 subContent 规则（正文后处理）
                val subContentRule = contentRule.subContent
                if (!subContentRule.isNullOrBlank()) {
                    val subAnalyzer = AnalyzeRule(source, jsEngine)
                    subAnalyzer.setContent(text, actualUrl)
                    text = subAnalyzer.getString(subContentRule)
                }

                // 文本替换
                val replaceRegex = contentRule.replaceRegex
                if (!replaceRegex.isNullOrBlank()) {
                    text = applyReplaceRules(text, replaceRegex)
                }

                // 提取标题
                val titleRule = contentRule.title
                val title = if (!titleRule.isNullOrBlank()) {
                    ruleAnalyzer.getString(titleRule)
                } else ""

                // 构建 LNR 内容格式
                val builder = ContentBuilder()
                val paragraphs = text.replace("\r\n", "\n").replace("\r", "\n")
                    .split("\n").filter { it.isNotBlank() }
                for (paragraph in paragraphs) {
                    builder.component(SimpleTextComponentData(paragraph.trim()))
                }

                MutableChapterContent(
                    id = chapterId,
                    title = title.ifBlank { chapterId },
                    content = builder.build()
                )
            } catch (e: Exception) {
                Log.e(TAG, "获取正文失败: ${e.message}")
                ChapterContent.empty(chapterId)
            }
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 执行 URL 规则（支持 JS、模板变量、data: URL）
     */
    private fun evaluateUrl(
        urlRule: String,
        source: BookSource,
        key: String?,
        jsEngine: JsEngine
    ): String {
        // 如果是 JS 代码，执行它
        if (urlRule.startsWith("<js>") || urlRule.startsWith("@js:")) {
            val jsCode = when {
                urlRule.startsWith("<js>") -> {
                    val end = urlRule.lastIndexOf("</js>")
                    if (end > 4) urlRule.substring(4, end) else urlRule.substring(4)
                }
                urlRule.startsWith("@js:") -> urlRule.substring(4)
                else -> urlRule
            }
            val result = jsEngine.eval(jsCode, source, mapOf(
                "key" to (key ?: ""),
                "page" to 1
            ))
            return result?.toString() ?: ""
        }

        // 否则使用 AnalyzeUrl 处理模板
        val analyzeUrl = AnalyzeUrl(
            mUrl = urlRule,
            key = key,
            page = 1,
            baseUrl = source.sourceUrl,
            source = source,
            jsEngine = jsEngine
        )
        return analyzeUrl.url
    }

    /**
     * 执行探索页 URL 规则
     */
    private fun evaluateExploreUrl(
        urlRule: String,
        source: BookSource,
        jsEngine: JsEngine
    ): String? {
        if (urlRule.startsWith("<js>") || urlRule.startsWith("@js:")) {
            val jsCode = when {
                urlRule.startsWith("<js>") -> {
                    val end = urlRule.lastIndexOf("</js>")
                    if (end > 4) urlRule.substring(4, end) else urlRule.substring(4)
                }
                urlRule.startsWith("@js:") -> urlRule.substring(4)
                else -> urlRule
            }
            return try {
                jsEngine.eval(jsCode, source, mapOf("page" to 1))?.toString()
            } catch (e: Exception) {
                Log.e(TAG, "探索页 JS 执行失败: ${e.message}")
                null
            }
        }
        return urlRule
    }

    /**
     * 从 URL 获取数据（支持 data: URL 路由）
     */
    private fun fetchDataFromUrl(
        url: String?,
        source: BookSource,
        httpClient: HttpClient,
        jsEngine: JsEngine
    ): String? {
        if (url.isNullOrBlank()) return null

        // 处理 data: URL 路由
        if (url.startsWith("data:")) {
            return handleDataUrl(url, source, httpClient, jsEngine)
        }

        // 普通 URL
        return fetchPageContent(url, source, httpClient)
    }

    /**
     * 处理 data:;base64,...,{...} URL 路由
     * 这是 Legado 聚合源的路由机制
     */
    private fun handleDataUrl(
        dataUrl: String,
        source: BookSource,
        httpClient: HttpClient,
        jsEngine: JsEngine
    ): String? {
        try {
            // 格式: data:;base64,<base64data>,<json-options>
            val afterData = dataUrl.substringAfter("data:;base64,", "")
            if (afterData.isBlank()) return null

            // 分离 base64 数据和 JSON options
            val lastComma = afterData.lastIndexOf(",")
            if (lastComma == -1) return null

            val base64Part = afterData.substring(0, lastComma)
            val jsonPart = afterData.substring(lastComma + 1)

            // 解析 options
            val options = try {
                Gson().fromJson(jsonPart, Map::class.java) as? Map<*, *>
            } catch (_: Exception) { null }

            val type = options?.get("type")?.toString() ?: ""

            // base64 解码
            val decoded = String(Base64.getDecoder().decode(base64Part), Charsets.UTF_8)

            Log.d(TAG, "data: URL 路由, type=$type, data=${decoded.take(100)}")

            // 根据 type 路由到不同的处理逻辑
            // 对于聚合源，data: URL 中的 base64 数据包含了请求参数
            // 这些参数会被传递给 init 规则的 result 变量
            return decoded
        } catch (e: Exception) {
            Log.e(TAG, "处理 data: URL 失败: ${e.message}")
            return null
        }
    }

    /**
     * 解码 data: URL，返回原始数据或普通 URL
     */
    private fun decodeDataUrl(id: String, source: BookSource): String {
        if (id.startsWith("data:")) {
            return id
        }
        return id
    }

    /**
     * 从 data: URL 提取 base64 编码的数据
     */
    private fun extractBase64Data(dataUrl: String): String? {
        try {
            if (!dataUrl.startsWith("data:")) return null
            val afterData = dataUrl.substringAfter("data:;base64,", "")
            if (afterData.isBlank()) return null
            val lastComma = afterData.lastIndexOf(",")
            val base64Part = if (lastComma > 0) afterData.substring(0, lastComma) else afterData
            return String(Base64.getDecoder().decode(base64Part), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "提取 base64 数据失败: ${e.message}")
            return null
        }
    }

    /**
     * 获取网页内容
     */
    private fun fetchPageContent(url: String, source: BookSource, httpClient: HttpClient): String? {
        return try {
            val headers = source.getHeaderMap()
            val response = httpClient.get(url, headers)
            response.body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "获取页面失败: $url, ${e.message}")
            null
        }
    }

    /**
     * 获取目录 URL
     */
    private fun getTocUrl(
        bookId: String,
        source: BookSource,
        jsEngine: JsEngine,
        httpClient: HttpClient
    ): String? {
        val bookInfoRule = source.getBookInfoRule()
        val tocUrlRule = bookInfoRule.tocUrl
        if (!tocUrlRule.isNullOrBlank()) {
            val ruleAnalyzer = AnalyzeRule(source, jsEngine)
            ruleAnalyzer.setContent(bookId, source.sourceUrl)
            return ruleAnalyzer.getString(tocUrlRule)
        }
        // 如果没有 tocUrl 规则，使用 bookId 作为目录 URL
        return if (bookId.startsWith("data:")) bookId else bookId
    }

    /**
     * 解析搜索结果的 bookList
     */
    private fun parseBookList(
        data: String,
        bookListRule: String,
        source: BookSource,
        searchRule: io.legado.engine.entity.rule.SearchRule,
        ruleAnalyzer: AnalyzeRule
    ): List<BookInformation> {
        val results = mutableListOf<BookInformation>()

        // 如果 bookList 包含 JS，先执行
        val processedData = if (bookListRule.startsWith("<js>") || bookListRule.startsWith("@js:")) {
            val jsCode = when {
                bookListRule.startsWith("<js>") -> {
                    val end = bookListRule.lastIndexOf("</js>")
                    if (end > 4) bookListRule.substring(4, end) else bookListRule.substring(4)
                }
                bookListRule.startsWith("@js:") -> bookListRule.substring(4)
                else -> bookListRule
            }
            val jsEngine = BookSourceManager.getJsEngine()
            try {
                jsEngine.eval(jsCode, source, mapOf("result" to data))?.toString() ?: data
            } catch (e: Exception) {
                Log.e(TAG, "bookList JS 执行失败: ${e.message}")
                data
            }
        } else {
            data
        }

        // 使用 JSONPath 或 CSS 选择器解析列表
        ruleAnalyzer.setContent(processedData, source.sourceUrl)
        val listRule = bookListRule.substringAfter("</js>", bookListRule)
            .let { if (it.startsWith("<js>")) "" else it }
            .trim()

        if (listRule.isNotBlank()) {
            // 有额外的列表选择器
            val elements = ruleAnalyzer.getElements(listRule)
            for (element in elements) {
                val elementAnalyzer = AnalyzeRule(source, BookSourceManager.getJsEngine())
                elementAnalyzer.setContent(element, source.sourceUrl)
                val book = parseBookFromElement(elementAnalyzer, searchRule, source)
                if (book != null) results.add(book)
            }
        } else {
            // 没有额外选择器，尝试直接从 JSON 解析
            val name = ruleAnalyzer.getString(searchRule.name ?: "")
            if (name.isNotBlank()) {
                val book = parseBookFromElement(ruleAnalyzer, searchRule, source)
                if (book != null) results.add(book)
            }
        }

        return results
    }

    /**
     * 从元素中解析书籍信息
     */
    private fun parseBookFromElement(
        ruleAnalyzer: AnalyzeRule,
        searchRule: io.legado.engine.entity.rule.SearchRule,
        source: BookSource
    ): BookInformation? {
        val name = ruleAnalyzer.getString(searchRule.name ?: "")
        if (name.isBlank()) return null

        val author = ruleAnalyzer.getString(searchRule.author ?: "")
        val bookUrl = ruleAnalyzer.getString(searchRule.bookUrl ?: "")
        val coverUrl = ruleAnalyzer.getString(searchRule.coverUrl ?: "")
        val intro = ruleAnalyzer.getString(searchRule.intro ?: "")
        val kind = ruleAnalyzer.getString(searchRule.kind ?: "")
        val lastChapter = ruleAnalyzer.getString(searchRule.lastChapter ?: "")

        return MutableBookInformation(
            id = bookUrl.ifBlank { name },
            title = name,
            subtitle = source.bookSourceName,
            coverUrl = if (coverUrl.isNotBlank()) Uri.parse(coverUrl) else Uri.EMPTY,
            author = author,
            description = intro,
            tags = if (kind.isNotBlank()) kind.split(",").map { it.trim() } else emptyList(),
            publishingHouse = "",
            wordCount = WordCount(0),
            lastUpdated = LocalDateTime.now(),
            isComplete = false
        )
    }

    /**
     * 解析探索页结果
     */
    private fun parseExploreResult(
        data: String,
        source: BookSource,
        jsEngine: JsEngine
    ): List<ExploreBooksRow> {
        val rows = mutableListOf<ExploreBooksRow>()
        val exploreRule = source.getExploreRule()
        val ruleAnalyzer = AnalyzeRule(source, jsEngine)

        // 探索结果可能是 JSON 数组（多分类）或单个列表
        val trimmed = data.trim()
        if (trimmed.startsWith("[")) {
            // JSON 数组 - 每个元素是一个分类
            try {
                val list = Gson().fromJson(trimmed, List::class.java)
                for (item in list) {
                    if (item is Map<*, *>) {
                        val title = item["title"]?.toString() ?: ""
                        val url = item["url"]?.toString() ?: ""
                        val books = mutableListOf<ExploreDisplayBook>()

                        // 如果有子数据
                        val subData = item["data"]
                        if (subData is List<*>) {
                            for (bookItem in subData) {
                                if (bookItem is Map<*, *>) {
                                    val book = mapToExploreBook(bookItem, source)
                                    if (book != null) books.add(book)
                                }
                            }
                        }

                        rows.add(ExploreBooksRow(
                            title = title,
                            bookList = books,
                            expandable = url.isNotBlank(),
                            expandedPageDataSourceId = url
                        ))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析探索 JSON 数组失败: ${e.message}")
            }
        } else if (trimmed.startsWith("{")) {
            // JSON 对象 - 单个数据集
            try {
                val map = Gson().fromJson(trimmed, Map::class.java) as? Map<*, *>
                val dataList = map?.get("data")
                if (dataList is List<*>) {
                    val books = mutableListOf<ExploreDisplayBook>()
                    for (item in dataList) {
                        if (item is Map<*, *>) {
                            val book = mapToExploreBook(item, source)
                            if (book != null) books.add(book)
                        }
                    }
                    rows.add(ExploreBooksRow(
                        title = source.bookSourceName,
                        bookList = books,
                        expandable = false,
                        expandedPageDataSourceId = ""
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析探索 JSON 对象失败: ${e.message}")
            }
        } else {
            // HTML 内容 - 使用 CSS 选择器
            ruleAnalyzer.setContent(data, source.sourceUrl)
            val bookListRule = exploreRule.bookList
            if (!bookListRule.isNullOrBlank()) {
                val elements = ruleAnalyzer.getElements(bookListRule)
                val books = mutableListOf<ExploreDisplayBook>()
                for (element in elements) {
                    val elementAnalyzer = AnalyzeRule(source, jsEngine)
                    elementAnalyzer.setContent(element, source.sourceUrl)
                    val name = elementAnalyzer.getString(exploreRule.name ?: "")
                    if (name.isNotBlank()) {
                        books.add(ExploreDisplayBook(
                            id = elementAnalyzer.getString(exploreRule.bookUrl ?: ""),
                            title = name,
                            author = elementAnalyzer.getString(exploreRule.author ?: ""),
                            coverUri = Uri.parse(elementAnalyzer.getString(exploreRule.coverUrl ?: ""))
                        ))
                    }
                }
                if (books.isNotEmpty()) {
                    rows.add(ExploreBooksRow(
                        title = source.bookSourceName,
                        bookList = books,
                        expandable = false,
                        expandedPageDataSourceId = ""
                    ))
                }
            }
        }

        return rows
    }

    /**
     * 将 Map 转换为 ExploreDisplayBook
     */
    private fun mapToExploreBook(item: Map<*, *>, source: BookSource): ExploreDisplayBook? {
        val name = item["book_name"]?.toString() ?: item["name"]?.toString() ?: return null
        val bookUrl = item["book_url"]?.toString() ?: item["book_id"]?.toString() ?: name
        val author = item["author"]?.toString() ?: ""
        val coverUrl = item["thumb_url"]?.toString() ?: item["cover_url"]?.toString() ?: ""
        return ExploreDisplayBook(
            id = bookUrl,
            title = name,
            author = author,
            coverUri = if (coverUrl.isNotBlank()) Uri.parse(coverUrl) else Uri.EMPTY
        )
    }

    /**
     * 应用文本替换规则
     */
    private fun applyReplaceRules(content: String, replaceRegex: String): String {
        var result = content
        // 替换规则格式: /pattern/replacement/flags 或 @pattern@replacement@
        val rules = replaceRegex.split("\n").filter { it.isNotBlank() }
        for (rule in rules) {
            try {
                val parts = rule.split(rule.first().toString())
                if (parts.size >= 3) {
                    val pattern = parts[1]
                    val replacement = parts[2]
                    val flags = if (parts.size > 3) parts[3] else ""
                    val regex = if (flags.contains("i")) {
                        Regex(pattern, RegexOption.IGNORE_CASE)
                    } else {
                        Regex(pattern)
                    }
                    result = result.replace(regex, replacement)
                }
            } catch (_: Exception) {
                // 非正则替换
                result = result.replace(rule, "")
            }
        }
        return result
    }

    private fun emptyBookInfo(id: String): BookInformation = MutableBookInformation(
        id = id,
        title = id,
        subtitle = "",
        coverUrl = Uri.EMPTY,
        author = "",
        description = "",
        tags = emptyList(),
        publishingHouse = "",
        wordCount = WordCount(0),
        lastUpdated = LocalDateTime.now(),
        isComplete = false
    )
}