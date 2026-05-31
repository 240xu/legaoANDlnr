package io.legado.engine.analyze

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option

/**
 * JSONPath 解析器
 * 移植自 io.legado.app.model.analyzeRule.AnalyzeByJSonPath
 */
class AnalyzeByJSonPath(private var json: Any?) {

    companion object {
        private val conf = Configuration.defaultConfiguration()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
            .addOptions(Option.SUPPRESS_EXCEPTIONS)
    }

    fun getStringList(rule: String): List<String> {
        if (json == null) return emptyList()
        return try {
            val jsonStr = when (val j = json) {
                is String -> j
                else -> j.toString()
            }
            val result = JsonPath.using(conf).parse(jsonStr).read<List<Any>>(rule)
            result.map { it.toString() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getString(rule: String): String {
        return getStringList(rule).firstOrNull() ?: ""
    }

    fun getObject(rule: String): Any? {
        if (json == null) return null
        return try {
            val jsonStr = when (val j = json) {
                is String -> j
                else -> j.toString()
            }
            JsonPath.using(conf).parse(jsonStr).read(rule)
        } catch (e: Exception) {
            null
        }
    }
}