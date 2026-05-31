package io.legado.engine.entity.rule

/**
 * 段评规则 - 移植自 lyc486 版 Legado ReviewRule
 */
data class ReviewRule(
    var reviewUrl: String? = null,
    var avatarRule: String? = null,
    var contentRule: String? = null,
    var postTimeRule: String? = null,
    var reviewQuoteUrl: String? = null,
    var voteUpUrl: String? = null,
    var voteDownUrl: String? = null,
    var postReviewUrl: String? = null,
    var postQuoteUrl: String? = null,
    var deleteUrl: String? = null
)
