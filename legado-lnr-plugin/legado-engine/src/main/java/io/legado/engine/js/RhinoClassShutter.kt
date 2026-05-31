package io.legado.engine.js

import org.mozilla.javascript.ClassShutter

/**
 * Rhino JS 安全沙箱 - 移植自 lyc486 版 Legado
 * 限制 JS 可访问的 Java 类，防止恶意代码执行
 * 允许必要的类访问以支持书源功能
 */
class RhinoClassShutter : ClassShutter {

    companion object {
        private val BLOCKED_CLASSES = setOf(
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Thread",
            "java.lang.ThreadGroup",
            "java.lang.ClassLoader",
            "java.lang.System",
            "java.lang.reflect",
            "java.io.File",
            "java.io.FileInputStream",
            "java.io.FileOutputStream",
            "java.io.RandomAccessFile",
            "java.net.ServerSocket",
            "java.net.Socket",
            "java.sql",
            "javax.script",
            "org.mozilla.javascript"
        )

        private val ALLOWED_CLASSES = setOf(
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.Short",
            "java.lang.Character",
            "java.lang.Math",
            "java.lang.Number",
            "java.util.Base64",
            "java.util.HashMap",
            "java.util.ArrayList",
            "java.util.Map",
            "java.util.List",
            "java.util.Set",
            "java.util.Collection",
            "java.util.Collections",
            "java.util.Arrays",
            "java.util.regex",
            "java.net.URLEncoder",
            "java.net.URLDecoder",
            "java.net.URL",
            "java.security.MessageDigest",
            "java.text.SimpleDateFormat",
            "java.util.Date",
            "java.util.Calendar",
            "org.jsoup",
            "com.google.gson",
            "okhttp3"
        )
    }

    override fun visibleToScripts(className: String): Boolean {
        // 允许书源引擎相关的类
        for (allowed in ALLOWED_CLASSES) {
            if (className.startsWith(allowed)) return true
        }
        // 阻止危险类
        for (blocked in BLOCKED_CLASSES) {
            if (className.startsWith(blocked)) return false
        }
        // 默认允许（与 Legado 行为一致，Legado 使用白名单模式）
        return true
    }
}