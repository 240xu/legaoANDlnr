@file:Suppress("OPT_IN_USAGE")

package com.guangyu.plugin

import android.net.Uri
import com.guangyu.plugin.api.GuangYuApi
import com.guangyu.plugin.utils.ContentUtils
import com.guangyu.plugin.model.IdCodec
import io.nightfish.lightnovelreader.api.book.BookInformation
import io.nightfish.lightnovelreader.api.book.BookVolumes
import io.nightfish.lightnovelreader.api.book.ChapterContent
import io.nightfish.lightnovelreader.api.explore.ExploreBooksRow
import io.nightfish.lightnovelreader.api.explore.ExploreDisplayBook
import io.nightfish.lightnovelreader.api.web.WebBookDataSource
import io.nightfish.lightnovelreader.api.web.WebDataSource
import io.nightfish.lightnovelreader.api.web.explore.ExploreExpandedPageDataSource
import io.nightfish.lightnovelreader.api.web.explore.ExplorePageProvider
import io.nightfish.lightnovelreader.api.web.explore.AbstractDefaultExplorePageProvider
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
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("unused")
@WebDataSource(
    name = "\u5149\u9047\u805a\u5408",
    provider = "guangyu"
)
object GuangYuWebDataSource : WebBookDataSource {
    init {
        android.util.Log.i("GuangYuWebDataSource", "GuangYuWebDataSource class initialized")
    }

    private val api = GuangYuApi()
    private val _isOffLine = MutableStateFlow(false)
    private val initialized = AtomicBoolean(false)
    override val id: Int = "guangyu_aggregation".hashCode()

    override fun onLoad() {
        android.util.Log.i("GuangYuWebDataSource", "onLoad")
        if (initialized.compareAndSet(false, true)) {
            Thread {
                try {
                    api.findWorkingHost()
                    android.util.Log.i("GuangYuWebDataSource", "Using base URL: ${api.getBaseUrl()}")
                } catch (e: Exception) {
                    android.util.Log.e("GuangYuWebDataSource", "Init error: ${e.message}")
                }
            }.start()
        }
    }

    override suspend fun isOffLine(): Boolean {
        return withContext(Dispatchers.IO) {
            _isOffLine.value = !api.testConnection()
            _isOffLine.value
        }
    }

    override val offLine: Boolean get() = _isOffLine.value
    override val isOffLineFlow: StateFlow<Boolean> = _isOffLine

    // ========== Search ==========
    override val searchProvider: SearchProvider = object : SearchProvider {
        override val searchTypes: List<SearchType> = listOf(
            SearchType("novel", "\u5c0f\u8bf4".local(), "\u641c\u7d22\u5c0f\u8bf4\u540d\u79f0".local()),
            SearchType("audiobook", "\u542c\u4e66".local(), "\u641c\u7d22\u542c\u4e66\u540d\u79f0".local()),
            SearchType("manga", "\u6f2b\u753b".local(), "\u641c\u7d22\u6f2b\u753b\u540d\u79f0".local()),
            SearchType("short_drama", "\u77ed\u5267".local(), "\u641c\u7d22\u77ed\u5267\u540d\u79f0".local())
        )

        override fun search(searchType: SearchType, keyword: String): Flow<SearchResult> = flow {
            val tab = when (searchType.type) {
                "audiobook" -> "\u542c\u4e66"
                "manga" -> "\u6f2b\u753b"
                "short_drama" -> "\u77ed\u5267"
                else -> "\u5c0f\u8bf4"
            }
            try {
                android.util.Log.i("GuangYuWebDataSource", "Searching: keyword=$keyword, tab=$tab")
                val results = withContext(Dispatchers.IO) {
                    api.search(keyword, tab, "\u5168\u90e8", 1, 0)
                }
                android.util.Log.i("GuangYuWebDataSource", "Search results: ${results?.size ?: 0}")
                _isOffLine.value = false
                if (results == null || results.isEmpty()) {
                    emit(SearchResult.Empty())
                } else {
                    for (item in results) {
                        emit(SearchResult.MultipleBook(ContentUtils.searchItemToBookInfo(item)))
                    }
                    emit(SearchResult.End())
                }
            } catch (e: Exception) {
                android.util.Log.e("GuangYuWebDataSource", "Search error: ${e.message}")
                _isOffLine.value = true
                emit(SearchResult.Empty())
            }
        }
    }

    // ========== Explore / Discovery ==========
    override val explorePageProvider: ExplorePageProvider = object : AbstractDefaultExplorePageProvider() {
        init {
            android.util.Log.i("GuangYuWebDataSource", "ExplorePageProvider created")
        }
        init {
            registerTapPage(GuangYuExploreTapPageDataSource())
        }
    }

    private class GuangYuExploreTapPageDataSource : ExploreTapPageDataSource {
        override val title: String = "\u5149\u9047\u805a\u5408"
        private val rowsFlow = MutableStateFlow<List<ExploreBooksRow>>(emptyList())
        private val loaded = AtomicBoolean(false)

        override fun getRowsFlow(): Flow<List<ExploreBooksRow>> = rowsFlow

        fun refresh() {
            if (loaded.compareAndSet(false, true) || true) {
                Thread {
                    try {
                        val rows = mutableListOf<ExploreBooksRow>()
                        val sources = listOf(
                            "\u756a\u8304" to "\u5c0f\u8bf4",
                            "\u4e03\u732b" to "\u5c0f\u8bf4",
                            "\u5854\u8bfb" to "\u5c0f\u8bf4",
                            "QQ\u9605\u8bfb" to "\u5c0f\u8bf4"
                        )
                        for ((source, tab) in sources) {
                            try {
                                val books = api.search("\u63a8\u8350", tab, source, 1, 0)
                                if (books != null && books.isNotEmpty()) {
                                    val displayBooks = books.map { item ->
                                        val bookId = IdCodec.encodeBookId(item.bookId, item.source, item.tab)
                                        ExploreDisplayBook(
                                            id = bookId,
                                            title = item.bookName,
                                            author = item.author,
                                            coverUri = if (item.thumbUrl.isNotBlank()) Uri.parse(item.thumbUrl) else Uri.EMPTY
                                        )
                                    }
                                    rows.add(ExploreBooksRow(source, displayBooks, false, ""))
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("GuangYuWebDataSource", "Discover row error ($source): ${e.message}")
                            }
                        }
                        if (rows.isEmpty()) {
                            try {
                                val books = api.search("\u63a8\u8350", "\u5c0f\u8bf4", "\u5168\u90e8", 1, 0)
                                if (books != null && books.isNotEmpty()) {
                                    val displayBooks = books.map { item ->
                                        val bookId = IdCodec.encodeBookId(item.bookId, item.source, item.tab)
                                        ExploreDisplayBook(
                                            id = bookId,
                                            title = item.bookName,
                                            author = item.author,
                                            coverUri = if (item.thumbUrl.isNotBlank()) Uri.parse(item.thumbUrl) else Uri.EMPTY
                                        )
                                    }
                                    rows.add(ExploreBooksRow("\u63a8\u8350\u4e66\u7c4d", displayBooks, false, ""))
                                }
                            } catch (_: Exception) {}
                        }
                        rowsFlow.value = rows
                        _isOffLine.value = false
                    } catch (e: Exception) {
                        android.util.Log.e("GuangYuWebDataSource", "Discover error: ${e.message}")
                        _isOffLine.value = true
                    }
                }.start()
            }
        }

        init {
            refresh()
        }
    }

    // ========== Book Information ==========
    override suspend fun getBookInformation(id: String): BookInformation {
        return withContext(Dispatchers.IO) {
            try {
                val (bookId, source, tab) = IdCodec.decodeBookId(id)
                if (bookId.isEmpty()) return@withContext BookInformation.empty()

                val detail = api.getBookDetail(bookId, source, tab)
                _isOffLine.value = false
                if (detail != null) {
                    ContentUtils.bookDetailToBookInfo(detail)
                } else {
                    BookInformation.empty(id)
                }
            } catch (e: Exception) {
                android.util.Log.e("GuangYuWebDataSource", "getBookInfo error: ${e.message}")
                _isOffLine.value = true
                BookInformation.empty(id)
            }
        }
    }

    // ========== Book Volumes (TOC) ==========
    override suspend fun getBookVolumes(id: String): BookVolumes {
        return withContext(Dispatchers.IO) {
            try {
                val (bookId, source, tab) = IdCodec.decodeBookId(id)
                if (bookId.isEmpty()) return@withContext BookVolumes.empty(id)

                val items = api.getCatalog(bookId, source, tab)
                _isOffLine.value = false
                if (items != null) {
                    ContentUtils.catalogItemsToBookVolumes(id, items, source, tab)
                } else {
                    BookVolumes.empty(id)
                }
            } catch (e: Exception) {
                android.util.Log.e("GuangYuWebDataSource", "getBookVolumes error: ${e.message}")
                _isOffLine.value = true
                BookVolumes.empty(id)
            }
        }
    }

    // ========== Chapter Content ==========
    override suspend fun getChapterContent(chapterId: String, bookId: String): ChapterContent {
        return withContext(Dispatchers.IO) {
            try {
                val parts = IdCodec.decodeChapterId(chapterId)
                val (bId, source, tab) = IdCodec.decodeBookId(bookId)
                val effectiveSource = source.ifEmpty { parts.source }
                val effectiveTab = tab.ifEmpty { parts.tab }

                if (parts.itemId.isEmpty()) return@withContext ChapterContent.empty(chapterId)

                val contentData = api.getContent(bId, parts.itemId, effectiveSource, effectiveTab)
                _isOffLine.value = false
                if (contentData != null && contentData.content.isNotBlank()) {
                    ContentUtils.contentToChapterContent(chapterId, parts.title, contentData.content)
                } else {
                    val errorMsg = contentData?.msg ?: "\u5185\u5bb9\u83b7\u53d6\u5931\u8d25"
                    android.util.Log.w("GuangYuWebDataSource", "Content empty: $errorMsg")
                    ChapterContent.empty(chapterId)
                }
            } catch (e: Exception) {
                android.util.Log.e("GuangYuWebDataSource", "getChapterContent error: ${e.message}")
                _isOffLine.value = true
                ChapterContent.empty(chapterId)
            }
        }
    }
}