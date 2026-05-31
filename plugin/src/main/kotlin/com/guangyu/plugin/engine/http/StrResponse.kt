package com.guangyu.plugin.engine.http

data class StrResponse(
    val url: String = "",
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String? = null,
    val statusCode: Int = 0
) {
    fun isSuccessful(): Boolean = statusCode in 200..299
}
