package io.legado.engine.entity

import io.legado.engine.provider.CacheProvider
import io.legado.engine.provider.ConfigProvider
import io.legado.engine.js.JsEngine

/**
 * 书源基础接口 - 移植自 lyc486 版 Legado BaseSource
 * 提供 JS 可调用的方法（login/getLoginInfo/putLoginInfo/变量/键值存储等）
 */
interface BaseSource {
    val sourceUrl: String
    val sourceName: String
    val loginUrl_: String?
    val loginCheckJs_: String?
    val jsEngine_: Int
    var sourceGroup: String?
    var concurrentRate: String?
    var header: String?
    var bookSourceComment: String?

    /**
     * 解析 header 规则（支持 JS 和 JSON）
     */
    fun getHeaderMap(): Map<String, String> {
        val map = HashMap<String, String>()
        header?.let {
            try {
                val json = when {
                    it.startsWith("@js:", true) -> it.substring(4)
                    it.startsWith("<js>", true) -> it.substring(4, it.lastIndexOf("<"))
                    else -> it
                }
                // 尝试解析为 JSON
                val parsed = com.google.gson.Gson().fromJson(json, Map::class.java)
                if (parsed != null) {
                    parsed.forEach { (k, v) -> map[k.toString()] = v.toString() }
                }
            } catch (_: Exception) {
                // 回退到简单 key:value 格式
                it.lines().forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) map[parts[0].trim()] = parts[1].trim()
                }
            }
        }
        return map
    }

    /**
     * 获取登录 JS（从 loginUrl 提取）
     */
    fun getLoginJs(): String? {
        val loginJs = loginUrl_ ?: return null
        return when {
            loginJs.startsWith("@js:") -> loginJs.substring(4)
            loginJs.startsWith("<js>") -> loginJs.substring(4, loginJs.lastIndexOf("<"))
            else -> loginJs
        }
    }

    /**
     * 执行登录函数
     */
    fun login(jsEngine: JsEngine?, loginInfo: Map<String, String>?, javaExtensions: Any?) {
        val loginJs = getLoginJs() ?: return
        val bindings = mutableMapOf<String, Any?>(
            "result" to (loginInfo ?: emptyMap<String, String>())
        )
        if (javaExtensions != null) bindings["java"] = javaExtensions
        val fullJs = """$loginJs
            if(typeof login=='function'){
                login.apply(this);
            } else {
                throw('Function login not implements!!!')
            }
        """.trimIndent()
        jsEngine?.eval(fullJs, this, bindings)
    }

    /**
     * 登录信息管理（JS 可调用）
     */
    fun getLoginInfo(cacheProvider: CacheProvider?): String? {
        return cacheProvider?.get("userInfo_$sourceUrl")
    }

    fun putLoginInfo(cacheProvider: CacheProvider?, info: String) {
        cacheProvider?.put("userInfo_$sourceUrl", info)
    }

    fun removeLoginInfo(cacheProvider: CacheProvider?) {
        cacheProvider?.delete("userInfo_$sourceUrl")
    }

    fun getLoginInfoMap(cacheProvider: CacheProvider?): MutableMap<String, String> {
        val json = getLoginInfo(cacheProvider) ?: return mutableMapOf()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            com.google.gson.Gson().fromJson<Map<String, String>>(json, type).toMutableMap()
        } catch (_: Exception) { mutableMapOf() }
    }

    /**
     * 自定义变量管理（JS 可调用）
     */
    fun getVariable(cacheProvider: CacheProvider?): String {
        return cacheProvider?.get("sourceVariable_$sourceUrl") ?: ""
    }

    fun putVariable(cacheProvider: CacheProvider?, variable: String?) {
        if (variable != null) {
            cacheProvider?.put("sourceVariable_$sourceUrl", variable)
        } else {
            cacheProvider?.delete("sourceVariable_$sourceUrl")
        }
    }

    /**
     * 键值存储（JS 可调用 put/get）
     */
    fun put(cacheProvider: CacheProvider?, key: String, value: String): String {
        cacheProvider?.put("v_${sourceUrl}_$key", value)
        return value
    }

    fun get(cacheProvider: CacheProvider?, key: String): String {
        return cacheProvider?.get("v_${sourceUrl}_$key") ?: ""
    }
}
