package io.legado.engine.provider

/**
 * 配置提供者接口
 * 由 LNR 宿主实现，读写功能开关状态
 */
interface ConfigProvider {
    /** 获取布尔配置 */
    fun getBoolean(key: String, default: Boolean = false): Boolean
    /** 设置布尔配置 */
    fun setBoolean(key: String, value: Boolean)
    /** 获取字符串配置 */
    fun getString(key: String, default: String = ""): String
    /** 设置字符串配置 */
    fun setString(key: String, value: String)
    /** 获取整数配置 */
    fun getInt(key: String, default: Int = 0): Int
    /** 设置整数配置 */
    fun setInt(key: String, value: Int)

    companion object Keys {
        const val KEY_PARAGRAPH_REVIEW = "paragraph_review"
        const val KEY_TEXT_REPLACE = "text_replace"
        const val KEY_EXPLORE_SOURCE = "explore_source"
        const val KEY_SEARCH_MODE = "search_mode"
        const val KEY_NETWORK_MODE = "network_mode"
        const val KEY_TIMEOUT = "timeout"
        const val KEY_AUTO_SWITCH = "auto_switch_line"
    }
}