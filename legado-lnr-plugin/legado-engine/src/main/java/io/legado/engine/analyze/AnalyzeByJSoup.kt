package io.legado.engine.analyze

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * CSS/JSoup 解析器
 * 移植自 io.legado.app.model.analyzeRule.AnalyzeByJSoup
 */
class AnalyzeByJSoup(private var doc: Any?) {

    companion object {
        @JvmStatic
        fun parse(html: String): Document {
            return Jsoup.parse(html)
        }
    }

    fun getStringList(rule: String): List<String> {
        val elements = getElements(rule)
        return elements.map { it.text() }
    }

    fun getString(rule: String): String {
        val elements = getElements(rule)
        return if (elements.isNotEmpty()) {
            val element = elements.first()!!
            when {
                rule.contains("@text", true) -> element.text()
                rule.contains("@src", true) -> element.attr("src")
                rule.contains("@href", true) -> element.attr("href")
                rule.contains("@content", true) -> element.attr("content")
                rule.contains("@html", true) -> element.html()
                rule.contains("@outerHtml", true) -> element.outerHtml()
                rule.contains("@", true) -> {
                    val attr = rule.substringAfterLast("@").substringBefore("[").trim()
                    element.attr(attr)
                }
                else -> element.text()
            }
        } else ""
    }

    private fun getElements(ruleStr: String): Elements {
        val node = doc ?: return Elements()
        val element = when (node) {
            is Element -> node
            is Document -> node
            else -> return Elements()
        }
        return try {
            val parts = ruleStr.split("@", limit = 2)
            val selector = parts[0].trim()
            if (selector.isBlank()) {
                Elements(element)
            } else {
                element.select(selector)
            }
        } catch (e: Exception) {
            Elements()
        }
    }
}