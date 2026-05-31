package io.legado.engine.analyze

import io.legado.engine.entity.BaseSource
import io.legado.engine.js.JsEngine
import io.legado.engine.provider.Logger
import io.legado.engine.util.NetworkUtils
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * URL 规则解析器 - 移植自 lyc486 版 Legado AnalyzeUrl
 * 处理 @js:、<js></js>、{{js}}、page 参数、URL options
 */
class AnalyzeUrl(
    private val mUrl: String,
    private val key: String? = null,
    private val page: Int? = null,
    private val baseUrl: String = "",
    private val source: BaseSource? = null,
    private val jsEngine: JsEngine? = null,
    private val logger: Logger? = null,
    headerMapF: Map<String, String>? = null
) {
    companion object {
        private val JS_PATTERN = Pattern.compile("@js:([^@]|@(?!js:))*|<js>([\\s\\S]*?)</js>")
        private val PAGE_PATTERN = Pattern.compile("\\{\\{page(,[^}]*)?\\}\\}")
        private val PARAM_PATTERN = Pattern.compile("\\?(.*)")
    }

    var ruleUrl = ""
        private set
    var url: String = ""
        private set
    val headerMap = LinkedHashMap<String, String>()
    private var body: String? = null
    var urlNoQuery: String = ""
        private set
    private var method = "GET"
    private var useWebView = false

    init {
        headerMapF?.let { headerMap.putAll(it) }
        initUrl()
    }

    fun initUrl() {
        ruleUrl = mUrl
        analyzeJs()
        replaceKeyPageJs()
        analyzeUrl()
    }

    /**
     * 执行 @js: 和 <js></js>
     */
    private fun analyzeJs() {
        if (jsEngine == null) return
        val matcher = JS_PATTERN.matcher(ruleUrl)
        val sb = StringBuffer()
        while (matcher.find()) {
            val jsCode = matcher.group(2) ?: matcher.group(1) ?: continue
            val result = try {
                jsEngine.eval(jsCode.trim(), source)?.toString() ?: ""
            } catch (e: Exception) {
                logger?.e("AnalyzeUrl", "JS 执行失败: $jsCode", e)
                ""
            }
            matcher.appendReplacement(sb, Pattern.quote(result))
        }
        matcher.appendTail(sb)
        ruleUrl = sb.toString()
    }

    /**
     * 替换 {{js}}、{{page}}、{{key}}
     */
    private fun replaceKeyPageJs() {
        // 替换 {{js}}
        if (ruleUrl.contains("{{") && ruleUrl.contains("}}") && jsEngine != null) {
            val start = ruleUrl.indexOf("{{")
            val end = ruleUrl.indexOf("}}", start + 2)
            if (start != -1 && end != -1) {
                val jsCode = ruleUrl.substring(start + 2, end)
                val result = try {
                    jsEngine.eval(jsCode, source)?.toString() ?: ""
                } catch (_: Exception) { "" }
                ruleUrl = ruleUrl.replace("{{$jsCode}}", result)
            }
        }
        // 替换 {{page}}
        page?.let { p ->
            val matcher = PAGE_PATTERN.matcher(ruleUrl)
            val sb = StringBuffer()
            while (matcher.find()) {
                val group = matcher.group(1)
                if (group != null) {
                    val pages = group.substring(1).split(",")
                    val pageVal = if (p <= pages.size) pages[p - 1].trim() else pages.last().trim()
                    matcher.appendReplacement(sb, Pattern.quote(pageVal))
                } else {
                    matcher.appendReplacement(sb, p.toString())
                }
            }
            matcher.appendTail(sb)
            ruleUrl = sb.toString()
        }
        // 替换 {{key}}
        key?.let {
            ruleUrl = ruleUrl.replace("{{key}}", URLEncoder.encode(it, "UTF-8"))
                .replace("\$\$key", URLEncoder.encode(it, "UTF-8"))
        }
    }

    /**
     * 解析 URL 和 options
     */
    private fun analyzeUrl() {
        val paramMatcher = PARAM_PATTERN.matcher(ruleUrl)
        val urlNoOption = if (paramMatcher.find()) ruleUrl.substring(0, paramMatcher.start()) else ruleUrl
        url = NetworkUtils.getAbsoluteURL(baseUrl, urlNoOption)
        urlNoQuery = url

        // 解析 URL options（JSON 格式的参数部分）
        if (paramMatcher.find()) {
            val optionStr = ruleUrl.substring(paramMatcher.end())
            try {
                val json = com.google.gson.Gson().fromJson(optionStr, Map::class.java) as? Map<*, *>
                json?.forEach { (k, v) ->
                    when (k.toString().lowercase()) {
                        "method" -> method = v.toString().uppercase()
                        "body" -> body = v.toString()
                        "webview" -> useWebView = v.toString().toBoolean()
                        "headers" -> {
                            if (v is Map<*, *>) {
                                v.forEach { (hk, hv) ->
                                    if (hk != null && hv != null) headerMap[hk.toString()] = hv.toString()
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun getMethod(): String = method
    fun getBody(): String? = body
    fun isUseWebView(): Boolean = useWebView
    fun getHeaderMap(): Map<String, String> = headerMap
}
