package io.legado.engine.provider

/**
 * 缓存提供者接口
 * 由 LNR 宿主实现
 */
interface CacheProvider {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}