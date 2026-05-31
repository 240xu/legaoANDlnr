package com.guangyu.plugin.engine.util

fun String.isJson(): Boolean {
    val t = trim()
    return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))
}

fun String.isJsonObject(): Boolean = trimStart().startsWith("{")
fun String.isJsonArray(): Boolean = trimStart().startsWith("[")
