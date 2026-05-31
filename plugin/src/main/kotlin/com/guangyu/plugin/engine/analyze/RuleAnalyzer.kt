package com.guangyu.plugin.engine.analyze

class RuleAnalyzer(data: String, code: Boolean = false) {
    private var queue: String = data
    private var pos = 0
    private var start = 0
    private var startX = 0
    private var rule = ArrayList<String>()
    private var step: Int = 0
    var elementsType = ""

    fun trim() {
        if (queue[pos] == '@' || queue[pos] < '!') {
            pos++
            while (pos < queue.length && (queue[pos] == '@' || queue[pos] < '!')) pos++
            start = pos
            startX = pos
        }
    }

    fun reSetPos() { pos = 0; startX = 0 }

    private fun consumeTo(seq: String): Boolean {
        start = pos
        val offset = queue.indexOf(seq, pos)
        return if (offset != -1) { pos = offset; true } else false
    }

    private fun consumeToAny(vararg seq: String): Boolean {
        var p = pos
        while (p != queue.length) {
            for (s in seq) {
                if (queue.regionMatches(p, s, 0, s.length)) {
                    step = s.length
                    this.pos = p
                    return true
                }
            }
            p++
        }
        return false
    }

    private fun chompCodeBalanced(open: Char, close: Char): Boolean {
        var p = pos
        var depth = 0
        var otherDepth = 0
        var inSingleQuote = false
        var inDoubleQuote = false
        do {
            if (p == queue.length) break
            val c = queue[p++]
            if (c != '\\') {
                if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote
                else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote
                if (inSingleQuote || inDoubleQuote) continue
                if (c == '[') depth++
                else if (c == ']') depth--
                else if (depth == 0) {
                    if (c == open) otherDepth++
                    else if (c == close) otherDepth--
                }
            } else p++
        } while (depth > 0 || otherDepth > 0)
        return if (depth > 0 || otherDepth > 0) false else { this.pos = p; true }
    }

    private fun chompRuleBalanced(open: Char, close: Char): Boolean {
        var p = pos
        var depth = 0
        do {
            if (p == queue.length) break
            val c = queue[p++]
            if (c == open) depth++
            else if (c == close) depth--
        } while (depth > 0)
        return if (depth > 0) false else { this.pos = p; true }
    }

    val chompBalanced: (Char, Char) -> Boolean = if (code) ::chompCodeBalanced else ::chompRuleBalanced

    fun splitRule(vararg split: String): ArrayList<String> {
        rule = ArrayList()
        if (queue.isEmpty()) return rule
        if (!consumeToAny(*split)) {
            rule.add(queue)
            return rule
        }
        elementsType = queue.substring(pos, pos + step)
        return splitRule()
    }

    private fun splitRule(): ArrayList<String> {
        var end: Int
        do {
            end = pos
            val st = findToAny('[', '(')
            if (st == -1) {
                rule.add(queue.substring(startX, end))
                pos = end + step
                while (consumeTo(elementsType)) {
                    rule.add(queue.substring(start, pos))
                    pos += step
                }
                rule.add(queue.substring(pos))
                return rule
            }
            if (st > end) {
                rule.add(queue.substring(startX, end))
                pos = end + step
                while (consumeTo(elementsType) && pos < st) {
                    rule.add(queue.substring(start, pos))
                    pos += step
                }
                return if (pos > st) {
                    startX = start
                    splitRule()
                } else {
                    rule.add(queue.substring(pos))
                    rule
                }
            }
            pos = st
            val next = if (queue[pos] == '[') ']' else ')'
            if (!chompBalanced(queue[pos], next)) throw Error(queue.substring(0, start) + "后未平衡")
        } while (end > pos)
        start = pos
        return if (!consumeTo(elementsType)) {
            rule.add(queue.substring(startX))
            rule
        } else splitRule()
    }

    fun findToAny(vararg seq: Char): Int {
        var p = pos
        while (p != queue.length) {
            for (s in seq) if (queue[p] == s) return p
            p++
        }
        return -1
    }

    fun innerRule(inner: String, startStep: Int = 1, endStep: Int = 1, fr: (String) -> String?): String {
        val st = StringBuilder()
        while (consumeTo(inner)) {
            val posPre = pos
            if (chompCodeBalanced('{', '}')) {
                val frv = fr(queue.substring(posPre + startStep, pos - endStep))
                if (!frv.isNullOrEmpty()) {
                    st.append(queue.substring(startX, posPre) + frv)
                    startX = pos
                    continue
                }
            }
            pos += inner.length
        }
        return if (startX == 0) "" else st.apply { append(queue.substring(startX)) }.toString()
    }
}
