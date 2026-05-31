package io.legado.engine.entity.rule

data class ContentRule(
    var content: String? = null,
    var title: String? = null,
    var src: String? = null,
    var imageDecode: String? = null,
    var replaceRegex: String? = null,
    var webJs: String? = null,
    var payAction: String? = null
)