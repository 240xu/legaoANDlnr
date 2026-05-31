package com.guangyu.plugin.engine.analyze

import com.guangyu.plugin.engine.js.JsBridge
import com.guangyu.plugin.engine.model.BookSource
import org.jsoup.nodes.Node
import java.util.regex.Pattern

@Suppress("MemberVisibilityCanBePrivate", "unused")
class AnalyzeRule(
    private var source: BookSource? = null,
    private var baseUrl: String? = null
) {
    private var content: Any? = null
    private var isJSON: Boolean = false
    private var analyzeByXPath: AnalyzeByXPath? = null
    private var analyzeByJSoup: AnalyzeByJSoup? = null
    private var analyzeByJSonPath: AnalyzeByJSonPath? = null
    private val stringRuleCache = hashMapOf<String, List<SourceRule>>()

    fun setContent(content: Any?, baseUrl: String? = null): AnalyzeRule {
        if (content == null) throw AssertionError("Content cannot be null")
        this.content = content
        isJSON = when (content) {
            is Node -> false
            else -> content.toString().isJson()
        }
        baseUrl?.let { this.baseUrl = baseUrl }
        analyzeByXPath = null
        analyzeByJSoup = null
        analyzeByJSonPath = null
        return this
    }

    fun setBaseUrl(baseUrl: String?): AnalyzeRule {
        baseUrl?.let { this.baseUrl = it }
        return this
    }

    private fun getAnalyzeByJSoup(o: Any): AnalyzeByJSoup {
        return if (o != content) AnalyzeByJSoup(o)
        else { if (analyzeByJSoup == null) analyzeByJSoup = AnalyzeByJSoup(content!!); analyzeByJSoup!! }
    }

    private fun getAnalyzeByXPath(o: Any): AnalyzeByXPath {
        return if (o != content) AnalyzeByXPath(o)
        else { if (analyzeByXPath == null) analyzeByXPath = AnalyzeByXPath(content!!); analyzeByXPath!! }
    }

    private fun getAnalyzeByJSonPath(o: Any): AnalyzeByJSonPath {
        return if (o != content) AnalyzeByJSonPath(o)
        else { if (analyzeByJSonPath == null) analyzeByJSonPath = AnalyzeByJSonPath(content!!); analyzeByJSonPath!! }
    }

    /**
     * 获取单个字符串结果
     * 支持: CSS/XPath/JSONPath/Regex/JS 规则
     * 支持: ## 正则替换、{{}} 内联表达式
     */
    fun getString(ruleStr: String?, isUrl: Boolean = false): String? {
        if (ruleStr.isNullOrEmpty()) return null
        return try {
            val rules = splitSourceRule(ruleStr)
            var result: String? = null
            for (sourceRule in rules) {
                result = when {
                    sourceRule.isJS -> evalJS(sourceRule.rule) as? String
                    sourceRule.isXPath -> getStringByXPath(sourceRule.rule)
                    sourceRule.isRegex -> getStringByRegex(sourceRule.rule)
                    isJSON && !sourceRule.isXPath -> getStringByJsonPath(sourceRule.rule)
                    else -> getStringByJSoup(sourceRule.rule)
                }
                if (!result.isNullOrEmpty()) break
            }
            result
        } catch (e: Exception) {
            android.util.Log.e("AnalyzeRule", "getString error: $ruleStr", e)
            null
        }
    }

    /**
     * 获取字符串列表
     */
    fun getStringList(ruleStr: String?): List<String> {
        if (ruleStr.isNullOrEmpty()) return emptyList()
        return try {
            val rules = splitSourceRule(ruleStr)
            var result: List<String> = emptyList()
            for (sourceRule in rules) {
                result = when {
                    sourceRule.isXPath -> getStringListByXPath(sourceRule.rule)
                    sourceRule.isRegex -> getStringListByRegex(sourceRule.rule)
                    isJSON && !sourceRule.isXPath -> getStringListByJsonPath(sourceRule.rule)
                    else -> getStringListByJSoup(sourceRule.rule)
                }
                if (result.isNotEmpty()) break
            }
            result
        } catch (e: Exception) {
            android.util.Log.e("AnalyzeRule", "getStringList error: $ruleStr", e)
            emptyList()
        }
    }

    /**
     * 获取元素列表（用于 bookList、chapterList 等）
     */
    fun getElementList(ruleStr: String?): List<Any> {
        if (ruleStr.isNullOrEmpty()) return emptyList()
        val rules = splitSourceRule(ruleStr)
        for (sourceRule in rules) {
            if (sourceRule.isJS) {
                // JS 规则返回 JSON 数组字符串 → 解析为列表
                val jsResult = evalJS(sourceRule.rule)
                if (jsResult is String && jsResult.isJson()) {
                    try {
                        val arr = org.json.JSONArray(jsResult)
                        val list = mutableListOf<Any>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: arr.opt(i) ?: continue
                            list.add(obj.toString())
                        }
                        if (list.isNotEmpty()) return list
                    } catch (_: Exception) {}
                }
                continue
            }
            if (isJSON && !sourceRule.isXPath && !sourceRule.isRegex) {
                try {
                    val jp = AnalyzeByJSonPath(content!!)
                    val list = jp.getList(sourceRule.rule)
                    if (list != null && list.isNotEmpty()) return list
                } catch (_: Exception) {}
            } else if (!sourceRule.isRegex) {
                try {
                    val jsoup = getAnalyzeByJSoup(content!!)
                    val elements = jsoup.getElements(sourceRule.rule)
                    if (elements.isNotEmpty()) return elements.toList()
                } catch (_: Exception) {}
            }
        }
        return emptyList()
    }

    // ===== 内部解析方法 =====

    private fun getStringByJSoup(rule: String): String? {
        return try {
            val raw = getAnalyzeByJSoup(content!!).getString(rule)
            applyPostProcessing(raw, rule)
        } catch (_: Exception) { null }
    }

    private fun getStringListByJSoup(rule: String): List<String> {
        return try { getAnalyzeByJSoup(content!!).getStringList(rule) } catch (_: Exception) { emptyList() }
    }

    private fun getStringByXPath(rule: String): String? {
        return try {
            val raw = getAnalyzeByXPath(content!!).getString(rule)
            applyPostProcessing(raw, rule)
        } catch (_: Exception) { null }
    }

    private fun getStringListByXPath(rule: String): List<String> {
        return try { getAnalyzeByXPath(content!!).getStringList(rule) } catch (_: Exception) { emptyList() }
    }

    private fun getStringByJsonPath(rule: String): String? {
        return try {
            // 先处理内联 {{}} 表达式
            val processed = processInlineExpressions(rule)
            if (processed != rule) return processed
            val raw = getAnalyzeByJSonPath(content!!).getString(rule)
            applyPostProcessing(raw, rule)
        } catch (_: Exception) { null }
    }

    private fun getStringListByJsonPath(rule: String): List<String> {
        return try { getAnalyzeByJSonPath(content!!).getStringList(rule) } catch (_: Exception) { emptyList() }
    }

    private fun getStringByRegex(rule: String): String? {
        val result = AnalyzeByRegex.getElement(content.toString(), rule.split("{{regex}}").toTypedArray())
        return result?.joinToString("\n")
    }

    private fun getStringListByRegex(rule: String): List<String> {
        val result = AnalyzeByRegex.getElements(content.toString(), rule.split("{{regex}}").toTypedArray())
        return result.map { it.joinToString("\n") }
    }

    fun evalJS(jsStr: String, result: Any? = null): Any? {
        return JsBridge.evalJS(jsStr, source, baseUrl, content, result)
    }

    // ===== 后处理: ## 正则替换、{{}} 内联表达式 =====

    /**
     * 处理 ## 正则替换
     * 例: "$.book_name##（别名：.*?）" 表示提取 book_name 后删除 "（别名：xxx）"
     */
    private fun applyPostProcessing(value: String?, rule: String): String? {
        if (value.isNullOrEmpty()) return value
        // 检查规则中是否有 ## 替换部分
        val hashIdx = rule.indexOf("##")
        if (hashIdx < 0) return value
        val replacePart = rule.substring(hashIdx + 2)
        return try {
            value.replace(Regex(replacePart), "")
        } catch (_: Exception) { value }
    }

    /**
     * 处理 {{}} 内联表达式
     * 例: "{{$.status}},{{$.score}},{{$.tags}}" 将各部分分别求值后拼接
     */
    private fun processInlineExpressions(rule: String): String {
        if (!rule.contains("{{")) return rule
        val sb = StringBuilder()
        var i = 0
        while (i < rule.length) {
            val start = rule.indexOf("{{", i)
            if (start < 0) {
                sb.append(rule.substring(i))
                break
            }
            sb.append(rule, i, start)
            val end = rule.indexOf("}}", start + 2)
            if (end < 0) {
                sb.append(rule.substring(start))
                break
            }
            val innerRule = rule.substring(start + 2, end)
            val innerResult = try {
                if (innerRule.startsWith("$")) {
                    getAnalyzeByJSonPath(content!!).getString(innerRule)
                } else {
                    innerRule
                }
            } catch (_: Exception) { null }
            sb.append(innerResult ?: "")
            i = end + 2
        }
        return sb.toString()
    }

    // ===== 规则解析 =====

    private fun splitSourceRule(ruleStr: String): List<SourceRule> {
        stringRuleCache[ruleStr]?.let { return it }
        val rules = ArrayList<SourceRule>()
        // 先去掉 ## 后面的替换部分（在 getString 中处理替换）
        val cleanRule = ruleStr.let {
            // 不要去掉 ## ，因为 ## 只在 getString 结果后处理
            it
        }
        val ruleAnalyzer = RuleAnalyzer(cleanRule)
        val splitRules = ruleAnalyzer.splitRule("&&", "||", "%%")
        for (rule in splitRules) {
            val r = rule.trim()
            when {
                r.startsWith("@XPath:", true) -> rules.add(SourceRule(r.substring(7).trim(), isXPath = true))
                r.startsWith("xpath:", true) -> rules.add(SourceRule(r.substring(6).trim(), isXPath = true))
                r.startsWith("@CSS:", true) -> rules.add(SourceRule(r.substring(5).trim(), isCSS = true))
                r.startsWith("<js>", true) -> {
                    val endIdx = r.lastIndexOf("</js>")
                    if (endIdx > 0) rules.add(SourceRule(r.substring(4, endIdx), isJS = true))
                    else rules.add(SourceRule(r))
                }
                r.startsWith("@js:", true) -> rules.add(SourceRule(r.substring(4).trim(), isJS = true))
                r.startsWith("/") || r.startsWith("regex:", true) -> {
                    val regex = if (r.startsWith("regex:")) r.substring(6) else r
                    rules.add(SourceRule(regex, isRegex = true))
                }
                else -> rules.add(SourceRule(r))
            }
        }
        stringRuleCache[ruleStr] = rules
        return rules
    }

    data class SourceRule(
        val rule: String,
        val isXPath: Boolean = false,
        val isCSS: Boolean = false,
        val isRegex: Boolean = false,
        val isJS: Boolean = false
    )

    companion object {
        private fun String.isJson(): Boolean {
            val t = trim()
            return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))
        }
    }
}