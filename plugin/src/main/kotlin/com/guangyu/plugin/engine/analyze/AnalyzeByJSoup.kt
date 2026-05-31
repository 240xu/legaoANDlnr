package com.guangyu.plugin.engine.analyze

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.select.Collector
import org.jsoup.select.Elements
import org.jsoup.select.Evaluator
import org.seimicrawler.xpath.JXNode

class AnalyzeByJSoup(doc: Any) {
    companion object {
        private val nullSet = setOf(null)
    }

    private var element: Element = parse(doc)

    private fun parse(doc: Any): Element {
        if (doc is Element) return doc
        if (doc is JXNode) return if (doc.isElement) doc.asElement() else Jsoup.parse(doc.toString())
        return try {
            if (doc.toString().startsWith("<?xml", true)) Jsoup.parse(doc.toString(), Parser.xmlParser())
            else Jsoup.parse(doc.toString())
        } catch (_: Exception) { Jsoup.parse(doc.toString()) }
    }

    internal fun getElements(rule: String) = getElements(element, rule)

    internal fun getString(ruleStr: String): String? {
        if (ruleStr.isEmpty()) return null
        val list = getStringList(ruleStr)
        if (list.isEmpty()) return null
        return if (list.size == 1) list.first() else list.joinToString("\n")
    }

    internal fun getString0(ruleStr: String) = getStringList(ruleStr).let { if (it.isEmpty()) "" else it[0] }

    internal fun getStringList(ruleStr: String): List<String> {
        val textS = ArrayList<String>()
        if (ruleStr.isEmpty()) return textS
        val sourceRule = SourceRule(ruleStr)
        if (sourceRule.elementsRule.isEmpty()) {
            textS.add(element.data() ?: "")
        } else {
            val ruleAnalyzes = RuleAnalyzer(sourceRule.elementsRule)
            val ruleStrS = ruleAnalyzes.splitRule("&&", "||", "%%")
            val results = ArrayList<List<String>>()
            for (ruleStrX in ruleStrS) {
                val temp: ArrayList<String>? = if (sourceRule.isCss) {
                    val lastIndex = ruleStrX.lastIndexOf('@')
                    getResultLast(element.select(ruleStrX.take(lastIndex)), ruleStrX.substring(lastIndex + 1))
                } else {
                    getResultList(ruleStrX)
                }
                if (!temp.isNullOrEmpty()) {
                    results.add(temp)
                    if (ruleAnalyzes.elementsType == "||") break
                }
            }
            if (results.isNotEmpty()) {
                if ("%%" == ruleAnalyzes.elementsType) {
                    for (i in results[0].indices) {
                        for (temp in results) { if (i < temp.size) textS.add(temp[i]) }
                    }
                } else {
                    for (temp in results) textS.addAll(temp)
                }
            }
        }
        return textS
    }

    private fun getElements(temp: Element?, rule: String): Elements {
        if (temp == null || rule.isEmpty()) return Elements()
        val elements = Elements()
        val sourceRule = SourceRule(rule)
        val ruleAnalyzes = RuleAnalyzer(sourceRule.elementsRule)
        val ruleStrS = ruleAnalyzes.splitRule("&&", "||", "%%")
        val elementsList = ArrayList<Elements>()
        if (sourceRule.isCss) {
            for (ruleStr in ruleStrS) {
                val tempS = temp.select(ruleStr)
                elementsList.add(tempS)
                if (tempS.isNotEmpty() && ruleAnalyzes.elementsType == "||") break
            }
        } else {
            for (ruleStr in ruleStrS) {
                val rsRule = RuleAnalyzer(ruleStr)
                rsRule.trim()
                val rs = rsRule.splitRule("@")
                val el = if (rs.size > 1) {
                    val el = Elements(); el.add(temp)
                    for (rl in rs) {
                        val es = Elements()
                        for (et in el) es.addAll(getElements(et, rl))
                        el.clear(); el.addAll(es)
                    }
                    el
                } else {
                    getElements(temp, rs[0])
                }
                elementsList.add(el)
                if (el.isNotEmpty() && ruleAnalyzes.elementsType == "||") break
            }
        }
        if (elementsList.isNotEmpty()) {
            if ("%%" == ruleAnalyzes.elementsType) {
                for (i in elementsList[0].indices) {
                    for (temp in elementsList) { if (i < temp.size) elements.add(temp[i]) }
                }
            } else {
                for (temp in elementsList) elements.addAll(temp)
            }
        }
        return elements
    }

    private fun getResultList(ruleStr: String): ArrayList<String>? {
        val result = ArrayList<String>()
        val sourceRule = SourceRule(ruleStr)
        val element: Element = if (sourceRule.elementsRule.isEmpty()) {
            return null
        } else {
            val elements = getElements(element, sourceRule.elementsRule)
            if (elements.isEmpty()) return null
            elements[0]
        }
        for (text in getResultLast(Elements().apply { add(element) }, sourceRule.putRule)) {
            result.add(text)
        }
        return result
    }

    private fun getResultLast(elements: Elements, rule: String): ArrayList<String> {
        val textS = ArrayList<String>()
        if (elements.isEmpty()) return textS
        val ruleAnalyzes = RuleAnalyzer(rule)
        val rules = ruleAnalyzes.splitRule("@")
        var curElements = elements
        for (rl in rules) {
            if (rl.startsWith("!")) {
                val removeEvaluators = rl.substring(1).split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val iterator = curElements.iterator()
                while (iterator.hasNext()) {
                    val el = iterator.next()
                    for (evalStr in removeEvaluators) {
                        if (evalStr.startsWith(".")) {
                            val cls = evalStr.substring(1)
                            if (el.classNames().contains(cls)) { iterator.remove(); break }
                        }
                    }
                }
            } else if (rl.startsWith("[")) {
                val indexStr = rl.removeSurrounding("[", "]")
                val indices = parseIndexSet(indexStr, curElements.size)
                val newElements = Elements()
                for (idx in indices) {
                    if (idx in 0 until curElements.size) newElements.add(curElements[idx])
                }
                curElements = newElements
            } else if (rl == "text") {
                for (el in curElements) textS.add(el.text())
            } else if (rl == "textNodes") {
                for (el in curElements) {
                    val node = el.textNodes()
                    for (n in node) textS.add(n.text())
                }
            } else if (rl == "ownText") {
                for (el in curElements) textS.add(el.ownText())
            } else if (rl.startsWith("attr(")) {
                val attr = rl.substring(5, rl.length - 1)
                for (el in curElements) textS.add(el.attr(attr))
            } else if (rl == "html") {
                for (el in curElements) textS.add(el.html())
            } else if (rl == "outerHtml") {
                for (el in curElements) textS.add(el.outerHtml())
            } else if (rl == "all") {
                for (el in curElements) textS.add(el.outerHtml())
            } else if (rl.startsWith("css(")) {
                val cssAttr = rl.substring(4, rl.length - 1)
                for (el in curElements) textS.add(el.attr("style"))
            }
        }
        return textS
    }

    private fun parseIndexSet(rule: String, size: Int): List<Int> {
        val result = mutableListOf<Int>()
        val parts = rule.split(",")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.contains(":")) {
                val rangeParts = trimmed.split(":")
                val start = if (rangeParts[0].isEmpty()) 0 else rangeParts[0].toIntOrNull() ?: 0
                val end = if (rangeParts.size < 2 || rangeParts[1].isEmpty()) size else rangeParts[1].toIntOrNull() ?: size
                for (i in start until end) result.add(if (i < 0) size + i else i)
            } else {
                val idx = trimmed.toIntOrNull() ?: continue
                result.add(if (idx < 0) size + idx else idx)
            }
        }
        return result
    }

    internal inner class SourceRule(ruleStr: String) {
        var isCss = false
        var elementsRule: String
        var putRule: String = ""
        init {
            if (ruleStr.startsWith("@CSS:", true)) {
                isCss = true
                elementsRule = ruleStr.substring(5).trim()
            } else {
                val putIndex = ruleStr.indexOf("\$put")
                if (putIndex > 0) {
                    elementsRule = ruleStr.substring(0, putIndex).trim()
                    putRule = ruleStr.substring(putIndex)
                } else {
                    elementsRule = ruleStr
                }
            }
        }
    }
}
