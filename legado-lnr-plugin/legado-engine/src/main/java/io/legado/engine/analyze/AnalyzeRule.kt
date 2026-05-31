package io.legado.engine.analyze

import io.legado.engine.entity.BaseSource
import io.legado.engine.js.JsEngine
import io.legado.engine.provider.Logger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

/**
 * 规则解析调度器 - 移植自 lyc486 版 Legado AnalyzeRule
 * 根据规则类型自动选择对应的解析器（CSS/XPath/JSONPath/Regex/JS）
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

    /**
     * 设置待解析的内容
     */
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

    /**
     * 设置基准 URL
     */
    fun setBaseUrl(url: String?): AnalyzeRule {
        url?.let { this.baseUrl = it }
        return this
    }

    /**
     * 解析单个规则字符串，返回结果列表
     */
    fun getStringList(ruleStr: String): List<String> {
        if (ruleStr.isBlank()) return emptyList()
        return try {
            val analyzer = RuleAnalyzer(ruleStr)
            val results = mutableListOf<String>()
            while (analyzer.next()) {
                when (analyzer.stepType) {
                    RuleAnalyzer.StepType.JS -> {
                        val jsResult = evalJS(analyzer.steps ?: "")
                        if (jsResult != null) {
                            results.add(jsResult.toString())
                        }
                    }
                    RuleAnalyzer.StepType.JSoup -> {
                        results.addAll(parseByCss(analyzer.steps ?: ""))
                    }
                    RuleAnalyzer.StepType.XPath -> {
                        results.addAll(AnalyzeByXPath.parse(getNodeContent(), analyzer.steps ?: ""))
                    }
                    RuleAnalyzer.StepType.JsonPath -> {
                        results.addAll(AnalyzeByJSonPath.parse(getJsonContent(), analyzer.steps ?: ""))
                    }
                    RuleAnalyzer.StepType.Regex -> {
                        results.addAll(AnalyzeByRegex.parse(getTextContent(), analyzer.steps ?: ""))
                    }
                    else -> {
                        // 普通文本规则，尝试作为 CSS 选择器
                        val cssResults = parseByCss(ruleStr)
                        if (cssResults.isNotEmpty()) {
                            results.addAll(cssResults)
                        } else {
                            results.add(ruleStr)
                        }
                    }
                }
            }
            results
        } catch (e: Exception) {
            logger?.e(TAG, "解析规则失败: $ruleStr, 错误: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 解析单个规则字符串，返回第一个结果
     */
    fun getString(ruleStr: String, defaultValue: String = ""): String {
        return getStringList(ruleStr).firstOrNull() ?: defaultValue
    }

    /**
     * 解析规则并返回 Element 列表
     */
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

    /**
     * 解析 JS 规则
     */
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

    /**
     * CSS 选择器解析
     */
    private fun parseByCss(selector: String): List<String> {
        val doc = getDocument() ?: return emptyList()
        return try {
            doc.select(selector).map { it.text() }
        } catch (e: Exception) {
            logger?.e(TAG, "CSS 解析失败: $selector", e)
            emptyList()
        }
    }

    private fun getDocument(): Document? {
        return when (content) {
            is Document -> content as Document
            is Node -> Jsoup.parse((content as Node).outerHtml())
            is String -> {
                val str = content as String
                if (!isJSON) Jsoup.parse(str) else null
            }
            else -> content?.toString()?.let { if (!isJSON) Jsoup.parse(it) else null }
        }
    }

    private fun getNodeContent(): Node? {
        return when (content) {
            is Node -> content as Node
            is String -> {
                val str = content as String
                if (!isJSON) Jsoup.parse(str) else null
            }
            else -> content?.toString()?.let { if (!isJSON) Jsoup.parse(it) else null }
        }
    }

    private fun getJsonContent(): String? {
        return when (content) {
            is String -> content as String
            else -> content?.toString()
        }
    }

    private fun getTextContent(): String {
        return when (content) {
            is String -> content as String
            is Node -> (content as Node).outerHtml()
            else -> content?.toString() ?: ""
        }
    }
}
