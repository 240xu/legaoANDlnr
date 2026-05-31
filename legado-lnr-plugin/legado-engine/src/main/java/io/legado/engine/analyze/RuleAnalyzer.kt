package io.legado.engine.analyze

/**
 * 规则解析器 - 将书源规则字符串拆分为可执行的操作序列
 * 移植自 io.legado.app.model.analyzeRule.RuleAnalyzer
 */
class RuleAnalyzer(
    private var rule: String,
    private val mode: Mode = Mode.Default
) {
    enum class Mode { Default, Js, Regex }

    private var pos = 0
    private var start = 0
    private var steps: String? = null
    private var stepType = StepType.None

    enum class StepType { None, XPath, JSoup, JsonPath, Regex, JS, Replace }

    val isJS get() = stepType == StepType.JS

    fun trim(): RuleAnalyzer {
        rule = rule.trim()
        pos = 0
        start = 0
        return this
    }

    fun next(): Boolean {
        if (pos >= rule.length) return false
        start = pos
        val ch = rule[pos]
        when {
            ch == '@' -> {
                pos++
                stepType = StepType.Replace
                steps = null
            }
            ch == '|' && pos + 1 < rule.length && rule[pos + 1] == '|' -> {
                pos += 2
                stepType = StepType.None
                steps = "||"
            }
            ch == '&' && pos + 1 < rule.length && rule[pos + 1] == '&' -> {
                pos += 2
                stepType = StepType.None
                steps = "&&"
            }
            rule.startsWith("xpath:", pos, true) -> {
                pos += 6
                stepType = StepType.XPath
                consumeRule()
            }
            rule.startsWith("json:", pos, true) -> {
                pos += 5
                stepType = StepType.JsonPath
                consumeRule()
            }
            rule.startsWith("@json:", pos, true) -> {
                pos += 6
                stepType = StepType.JsonPath
                consumeRule()
            }
            rule.startsWith("css:", pos, true) -> {
                pos += 4
                stepType = StepType.JSoup
                consumeRule()
            }
            rule.startsWith("@css:", pos, true) -> {
                pos += 5
                stepType = StepType.JSoup
                consumeRule()
            }
            rule.startsWith("##", pos) -> {
                pos += 2
                stepType = StepType.Regex
                consumeRule()
            }
            rule.startsWith("<js>", pos, true) -> {
                pos += 4
                val end = rule.indexOf("</js>", pos, true)
                if (end != -1) {
                    steps = rule.substring(pos, end)
                    pos = end + 5
                } else {
                    steps = rule.substring(pos)
                    pos = rule.length
                }
                stepType = StepType.JS
            }
            else -> {
                stepType = StepType.JSoup
                consumeRule()
            }
        }
        return true
    }

    private fun consumeRule() {
        var i = pos
        while (i < rule.length) {
            val c = rule[i]
            if (c == '|' && i + 1 < rule.length && rule[i + 1] == '|') break
            if (c == '&' && i + 1 < rule.length && rule[i + 1] == '&') break
            if (c == '@' && i + 1 < rule.length && rule[i + 1] != '@') break
            if (c == '[' || c == ']') break
            i++
        }
        steps = rule.substring(pos, i).trim()
        pos = i
    }

    fun currentSteps(): String? = steps
    fun currentType(): StepType = stepType
    fun remaining(): String = if (pos < rule.length) rule.substring(pos) else ""
    fun getRule(): String = rule
}