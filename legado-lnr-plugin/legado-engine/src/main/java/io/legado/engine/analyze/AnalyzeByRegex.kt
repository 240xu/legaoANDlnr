package io.legado.engine.analyze

/**
 * 正则解析器
 * 移植自 io.legado.app.model.analyzeRule.AnalyzeByRegex
 */
class AnalyzeByRegex {

    companion object {
        fun getStringList(content: String, rule: String): List<String> {
            return try {
                val regex = Regex(rule)
                regex.findAll(content).map { it.value }.toList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun getString(content: String, rule: String): String {
            return getStringList(content, rule).firstOrNull() ?: ""
        }

        fun replace(content: String, rule: String, replacement: String): String {
            return try {
                content.replace(Regex(rule), replacement)
            } catch (e: Exception) {
                content
            }
        }
    }
}