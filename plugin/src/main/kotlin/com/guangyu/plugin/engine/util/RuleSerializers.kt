package com.guangyu.plugin.engine.util

import com.guangyu.plugin.engine.model.*

object SearchRuleSerializer : FlexibleRuleSerializer<SearchRule>(SearchRule.serializer())
object ExploreRuleSerializer : FlexibleRuleSerializer<ExploreRule>(ExploreRule.serializer())
object BookInfoRuleSerializer : FlexibleRuleSerializer<BookInfoRule>(BookInfoRule.serializer())
object TocRuleSerializer : FlexibleRuleSerializer<TocRule>(TocRule.serializer())
object ContentRuleSerializer : FlexibleRuleSerializer<ContentRule>(ContentRule.serializer())
