package com.guangyu.plugin.engine.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Legado JSON 书源中，规则字段可能是 JSON 对象或 JSON 字符串。
 * 此序列化器兼容两种格式。
 */
open class FlexibleRuleSerializer<T : Any>(
    private val kSerializer: KSerializer<T>
) : KSerializer<T?> {
    override val descriptor: SerialDescriptor = kSerializer.descriptor

    override fun deserialize(decoder: Decoder): T? {
        val jsonDecoder = decoder as? JsonDecoder ?: return try {
            kSerializer.deserialize(decoder)
        } catch (_: Exception) { null }
        val element = jsonDecoder.decodeJsonElement()
        return try {
            when (element) {
                is JsonObject -> jsonDecoder.json.decodeFromJsonElement(kSerializer, element)
                is JsonPrimitive -> {
                    val str = element.content
                    if (str.isBlank() || str == "null") null
                    else try {
                        jsonDecoder.json.decodeFromString(kSerializer, str)
                    } catch (_: Exception) { null }
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    override fun serialize(encoder: Encoder, value: T?) {
        if (value != null) encoder.encodeSerializableValue(kSerializer, value)
        else encoder.encodeNull()
    }
}

object LegadoJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        allowStructuredMapKeys = true
    }
}
