package io.legado.engine.entity.rule

data class SearchRule(
    var checkKeyWord: String? = null,
    var bookList: String? = null,
    var name: String? = null,
    var author: String? = null,
    var intro: String? = null,
    var kind: String? = null,
    var lastChapter: String? = null,
    var updateTime: String? = null,
    var bookUrl: String? = null,
    var coverUrl: String? = null,
    var wordCount: String? = null
)
