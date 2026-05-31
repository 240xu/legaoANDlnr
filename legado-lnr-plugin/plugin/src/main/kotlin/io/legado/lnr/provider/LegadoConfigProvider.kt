package io.legado.lnr.provider

import android.content.Context
import android.content.SharedPreferences
import io.legado.engine.provider.ConfigProvider

/**
 * Legado 配置提供者实现
 * 使用 SharedPreferences 持久化存储，支持按书源隔离
 */
class LegadoConfigProvider(context: Context) : ConfigProvider {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "legado_config", Context.MODE_PRIVATE
    )

    override fun getBoolean(key: String, default: Boolean): Boolean {
        return prefs.getBoolean(key, default)
    }

    override fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getString(key: String, default: String): String {
        return prefs.getString(key, default) ?: default
    }

    override fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getInt(key: String, default: Int): Int {
        return prefs.getInt(key, default)
    }

    override fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }
}
