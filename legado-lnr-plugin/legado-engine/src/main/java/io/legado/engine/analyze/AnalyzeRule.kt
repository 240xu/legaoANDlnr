package io.legado.engine.analyze

import io.legado.engine.entity.BaseSource
import io.legado.engine.js.JsEngine
import io.legado.engine.provider.Logger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

/**
 * 规则解析调度器 - 根据规则类型自动选择对应的解析器
 */
class AnalyzeRule(
    private val source: BaseSource? = null,
    private val jsEngine: JsEngine? = null,
    private val logger: Logger? = null
) {

    companion object {
        private const val TAG = "AnalyzeRule"
    }

    private var content: Any? = null
    private var baseUrl: String? = null
    private var isJSON: Boolean = false

    fun setContent(content: Any?, baseUrl: String? = null): AnalyzeRule {
        this.content = content
        this.baseUrl = baseUrl
        isJSON = when (content) {
            is Node -> false
            else -> content.toString().let { str ->
                str.trimStart().let { it.startsWith("{") || it.startsWith("[") }
            }
        }
        return this
    }

    fun setBaseUrl(url: String?): AnalyzeRule {
        url?.let { this.baseUrl = it }
        return this
    }

    fun getStringList(ruleStr: String): List<String> {
        if (ruleStr.isBlank()) return emptyList()
        return try {
            if (!ruleStr.contains("||") && !ruleStr.contains("&&") &&
                !ruleStr.startsWith("@") && !ruleStr.startsWith("##") &&
                !ruleStr.startsWith("<js>") && !ruleStr.startsWith("xpath:") &&
                !ruleStr.startsWith("json:") && !ruleStr.startsWith("@json:") &&
                !ruleStr.startsWith("css:") && !ruleStr.startsWith("@css:")
            ) {
                val cssResults = parseByCss(ruleStr)
                if (cssResults.isNotEmpty()) return cssResults
            }
            val analyzer = RuleAnalyzer(ruleStr)
            val results = mutableListOf<String>()
            while (analyzer.next()) {
                val steps = analyzer.currentSteps() ?: continue
                when (analyzer.currentType()) {
                    RuleAnalyzer.StepType.JS -> {
                        val jsResult = evalJS(steps)
                        if (jsResult != null) results.add(jsResult.toString())
                    }
                    RuleAnalyzer.StepType.JSoup -> results.addAll(parseByCss(steps))
                    RuleAnalyzer.StepType.XPath -> {
                        val node = getNodeContent()
                        if (node != null) results.addAll(AnalyzeByXPath(node).getStringList(steps))
                    }
                    RuleAnalyzer.StepType.JsonPath -> {
                        val json = getJsonContent()
                        if (json != null) results.addAll(AnalyzeByJSonPath(json).getStringList(steps))
                    }
                    RuleAnalyzer.StepType.Regex -> {
                        results.addAll(AnalyzeByRegex.getStringList(getTextContent(), steps))
                    }
                    else -> {
                        val cssResults = parseByCss(steps)
                        if (cssResults.isNotEmpty()) results.addAll(cssResults)
                        else results.add(steps)
                    }
                }
            }
            results
        } catch (e: Exception) {
            logger?.e(TAG, "解析规则失败: $ruleStr, 错误: ${e.message}", e)
            emptyList()
        }
    }

    fun getString(ruleStr: String, defaultValue: String = ""): String {
        return getStringList(ruleStr).firstOrNull() ?: defaultValue
    }

    fun getElements(ruleStr: String): List<Element> {
        if (ruleStr.isBlank()) return emptyList()
        return try {
            val doc = getDocument()
            doc?.select(ruleStr)?.toList() ?: emptyList()
        } catch (e: Exception) {
            logger?.e(TAG, "CSS 选择器解析失败: $ruleStr", e)
            emptyList()
        }
    }

    private fun evalJS(jsCode: String): Any? {
        if (jsCode.isBlank()) return null
        return try {
            val cleanJs = when {
                jsCode.startsWith("@js:") -> jsCode.substring(4)
                jsCode.startsWith("<js>") -> jsCode.substring(4, jsCode.lastIndexOf("<"))
                else -> jsCode
            }
            jsEngine?.eval(cleanJs, source, mapOf(
                "src" to (content?.toString() ?: ""),
                "baseUrl" to (baseUrl ?: "")
            ))
        } catch (e: Exception) {
            logger?.e(TAG, "JS 执行失败: $jsCode", e)
            null
        }
    }

    private fun parseByCss(selector: String): List<String> {
        val doc = getDocument() ?: return emptyList()
        return try { doc.select(selector).map { it.text() } }
        catch (e: Exception) { logger?.e(TAG, "CSS 解析失败: $selector", e); emptyList() }
    }

    private fun getDocument(): Document? = when (content) {
        is Document -> content as Document
        is Node -> Jsoup.parse((content as Node).outerHtml())
        is String -> if (!isJSON) Jsoup.parse(content as String) else null
        else -> content?.toString()?.let { if (!isJSON) Jsoup.parse(it) else null }
    }

    private fun getNodeContent(): Node? = when (content) {
        is Node -> content as Node
        is String -> if (!isJSON) Jsoup.parse(content as String) else null
        else -> content?.toString()?.let { if (!isJSON) Jsoup.parse(it) else null }
    }

    private fun getJsonContent(): String? = when (content) {
        is String -> content as String
        else -> content?.toString()
    }

    private fun getTextContent(): String = when (content) {
        is String -> content as String
        is Node -> (content as Node).outerHtml()
        else -> content?.toString() ?: ""
    }
}
