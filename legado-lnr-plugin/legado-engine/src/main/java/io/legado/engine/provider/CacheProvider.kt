package io.legado.engine.provider

/**
 * 缓存提供者接口 - 由宿主实现
 */
interface CacheProvider {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun putWithExpiry(key: String, value: String, expiryMillis: Long)
    fun delete(key: String)
    fun clear()
}
