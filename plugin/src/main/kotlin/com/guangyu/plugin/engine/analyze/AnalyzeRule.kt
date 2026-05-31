package com.guangyu.plugin.engine.analyze

import com.guangyu.plugin.engine.js.JsBridge
import com.guangyu.plugin.engine.model.BookSource
import com.jayway.jsonpath.JsonPath
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import java.util.regex.Pattern

@Suppress("MemberVisibilityCanBePrivate", "unused")
class AnalyzeRule(
    private var source: BookSource? = null,
    private var baseUrl: String? = null
) {
    private var content: Any? = null
    private var isJSON: Boolean = false
    private var redirectUrl: String? = null
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

    fun setRedirectUrl(url: String) { redirectUrl = url }

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

    fun getString(ruleStr: String?, isUrl: Boolean = false): String? {
        if (ruleStr.isNullOrEmpty()) return null
        return try {
            val rules = splitSourceRule(ruleStr)
            var result: String? = null
            for (sourceRule in rules) {
                result = if (isJSON && !sourceRule.isRegex && !sourceRule.isXPath) {
                    getStringByJsonPath(sourceRule.rule)
                } else {
                    when {
                        sourceRule.isXPath -> getStringByXPath(sourceRule.rule)
                        sourceRule.isRegex -> getStringByRegex(sourceRule.rule)
                        sourceRule.isJS -> evalJS(sourceRule.rule) as? String
                        else -> getStringByJSoup(sourceRule.rule)
                    }
                }
                if (!result.isNullOrEmpty()) break
            }
            result
        } catch (e: Exception) {
            android.util.Log.e("AnalyzeRule", "getString error: $ruleStr", e)
            null
        }
    }

    fun getStringList(ruleStr: String?): List<String> {
        if (ruleStr.isNullOrEmpty()) return emptyList()
        return try {
            val rules = splitSourceRule(ruleStr)
            var result: List<String> = emptyList()
            for (sourceRule in rules) {
                result = if (isJSON && !sourceRule.isRegex && !sourceRule.isXPath) {
                    getStringListByJsonPath(sourceRule.rule)
                } else {
                    when {
                        sourceRule.isXPath -> getStringListByXPath(sourceRule.rule)
                        sourceRule.isRegex -> getStringListByRegex(sourceRule.rule)
                        else -> getStringListByJSoup(sourceRule.rule)
                    }
                }
                if (result.isNotEmpty()) break
            }
            result
        } catch (e: Exception) {
            android.util.Log.e("AnalyzeRule", "getStringList error: $ruleStr", e)
            emptyList()
        }
    }

    fun getElementList(ruleStr: String?): List<Any> {
        if (ruleStr.isNullOrEmpty()) return emptyList()
        val rules = splitSourceRule(ruleStr)
        for (sourceRule in rules) {
            if (isJSON && !sourceRule.isXPath) {
                try {
                    val jp = AnalyzeByJSonPath(content!!)
                    val list = jp.getList(sourceRule.rule)
                    if (list != null && list.isNotEmpty()) return list
                } catch (_: Exception) {}
            } else {
                try {
                    val jsoup = getAnalyzeByJSoup(content!!)
                    val elements = jsoup.getElements(sourceRule.rule)
                    if (elements.isNotEmpty()) return elements.toList()
                } catch (_: Exception) {}
            }
        }
        return emptyList()
    }

    private fun getStringByJSoup(rule: String): String? {
        return try { getAnalyzeByJSoup(content!!).getString(rule) } catch (_: Exception) { null }
    }

    private fun getStringListByJSoup(rule: String): List<String> {
        return try { getAnalyzeByJSoup(content!!).getStringList(rule) } catch (_: Exception) { emptyList() }
    }

    private fun getStringByXPath(rule: String): String? {
        return try { getAnalyzeByXPath(content!!).getString(rule) } catch (_: Exception) { null }
    }

    private fun getStringListByXPath(rule: String): List<String> {
        return try { getAnalyzeByXPath(content!!).getStringList(rule) } catch (_: Exception) { emptyList() }
    }

    private fun getStringByJsonPath(rule: String): String? {
        return try { getAnalyzeByJSonPath(content!!).getString(rule) } catch (_: Exception) { null }
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

    private fun splitSourceRule(ruleStr: String): List<SourceRule> {
        stringRuleCache[ruleStr]?.let { return it }
        val rules = ArrayList<SourceRule>()
        val ruleAnalyzer = RuleAnalyzer(ruleStr)
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
