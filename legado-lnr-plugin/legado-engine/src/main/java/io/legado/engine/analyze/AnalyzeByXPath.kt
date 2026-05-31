package io.legado.engine.analyze

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * XPath 解析器
 * 移植自 io.legado.app.model.analyzeRule.AnalyzeByXPath
 */
class AnalyzeByXPath(private var doc: Any?) {

    fun getStringList(rule: String): List<String> {
        // Simplified XPath using JSoup's select as fallback
        // Full XPath support requires a dedicated library
        return try {
            val elements = getElements(rule)
            elements.map { it.text() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getString(rule: String): String {
        return getStringList(rule).firstOrNull() ?: ""
    }

    private fun getElements(rule: String): List<Element> {
        val node = doc ?: return emptyList()
        val element = when (node) {
            is Element -> node
            is Document -> node
            else -> return emptyList()
        }
        // Convert simple XPath to CSS selector as approximation
        val cssSelector = xpathToCss(rule)
        return try {
            element.select(cssSelector).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun xpathToCss(xpath: String): String {
        // Basic XPath to CSS conversion for common patterns
        var css = xpath
            .replace("//", " ")
            .replace("/", " > ")
            .replace("[contains(@class,", ".")
            .replace(")]", "")
            .replace("[@class=", ".")
            .replace("[@id=", "#")
            .replace("[text()]", "")
        return css.trim()
    }
}