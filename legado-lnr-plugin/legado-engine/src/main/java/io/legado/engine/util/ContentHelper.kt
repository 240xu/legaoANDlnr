package io.legado.engine.util

import org.apache.commons.text.StringEscapeUtils
import java.net.URL
import java.util.regex.Pattern

/**
 * 内容辅助工具 - 移植自 lyc486 版 Legado ContentHelp
 * 处理正文清理、格式化、图片路径修正等
 */
object ContentHelper {

    /**
     * 清理正文内容
     */
    fun cleanContent(content: String, baseUrl: String? = null): String {
        var result = content
        // 移除多余空白
        result = result.replace(Regex("\\s*\\n\\s*\\n\\s*"), "\n\n")
        // 修正图片路径
        if (baseUrl != null) {
            result = fixImageUrls(result, baseUrl)
        }
        // 移除广告标记
        result = result.replace(Regex("(?s)<!--.*?-->"), "")
        // 移除 script 标签
        result = result.replace(Regex("(?si)<script[^>]*>.*?</script>"), "")
        // 移除 style 标签
        result = result.replace(Regex("(?si)<style[^>]*>.*?</style>"), "")
        return result.trim()
    }

    /**
     * 修正正文中的图片 URL
     */
    fun fixImageUrls(content: String, baseUrl: String): String {
        val urlPattern = Pattern.compile("""<img[^>]+src\s*=\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
        val matcher = urlPattern.matcher(content)
        val sb = StringBuffer()
        while (matcher.find()) {
            val src = matcher.group(1) ?: continue
            val absoluteUrl = NetworkUtils.getAbsoluteURL(baseUrl, src)
            matcher.appendReplacement(sb, """<img src="$absoluteUrl"""")
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    /**
     * 格式化章节内容（HTML → 纯文本）
     */
    fun formatContent(html: String): String {
        var text = html
        // 替换 <br> 和 <p> 为换行
        text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?i)</p>"), "\n\n")
        text = text.replace(Regex("(?i)<p[^>]*>"), "")
        // 保留图片标签
        text = text.replace(Regex("(?i)<img([^>]+)>"), "<img$1>")
        // 移除其他 HTML 标签
        text = text.replace(Regex("<[^>]+>"), "")
        // 解码 HTML 实体
        text = StringEscapeUtils.unescapeHtml4(text)
        // 清理多余空白
        text = text.replace(Regex("[ \\t]+"), " ")
        text = text.replace(Regex("\\n[ \\t]+"), "\n")
        text = text.replace(Regex("[ \\t]+\\n"), "\n")
        text = text.replace(Regex("\\n{3,}"), "\n\n")
        return text.trim()
    }

    /**
     * 替换文本规则
     */
    fun replaceContent(content: String, replaceRules: List<Pair<String, String>>): String {
        var result = content
        for ((pattern, replacement) in replaceRules) {
            try {
                result = result.replace(Regex(pattern), replacement)
            } catch (_: Exception) {
                result = result.replace(pattern, replacement)
            }
        }
        return result
    }
}

/**
 * 网络工具
 */
object NetworkUtils {
    /**
     * 获取绝对 URL
     */
    fun getAbsoluteURL(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.isBlank()) return baseUrl
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl
        }
        if (relativeUrl.startsWith("//")) {
            val base = URL(baseUrl)
            return "${base.protocol}:$relativeUrl"
        }
        return try {
            URL(URL(baseUrl), relativeUrl).toString()
        } catch (_: Exception) {
            relativeUrl
        }
    }
}
