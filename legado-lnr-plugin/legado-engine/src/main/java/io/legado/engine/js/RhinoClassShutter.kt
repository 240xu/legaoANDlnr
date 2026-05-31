package io.legado.engine.js

import org.mozilla.javascript.ClassShutter

/**
 * Rhino JS 安全沙箱 - 移植自 lyc486 版 Legado
 * 限制 JS 可访问的 Java 类，防止恶意代码执行
 */
class RhinoClassShutter : ClassShutter {

    companion object {
        private val ALLOWED_PREFIXES = listOf(
            "java.lang.",
            "java.util.",
            "java.net.",
            "java.io.ByteArray",
            "java.io.InputStream",
            "java.io.InputStreamReader",
            "java.io.BufferedReader",
            "java.math.",
            "java.text.",
            "java.util.regex.",
            "java.util.Base64",
            "kotlin.",
            "kotlin.text.",
            "kotlin.collections.",
            "kotlin.io.",
            "org.jsoup.",
            "org.json.",
            "com.google.gson.",
            "io.legado.engine.",
            "org.apache.commons.text."
        )

        private val BLOCKED_CLASSES = setOf(
            "java.lang.System",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Thread",
            "java.lang.ThreadGroup",
            "java.lang.ClassLoader",
            "java.lang.reflect.",
            "java.io.File",
            "java.io.FileInputStream",
            "java.io.FileOutputStream",
            "java.io.RandomAccessFile",
            "java.net.URLClassLoader",
            "java.net.ServerSocket",
            "java.net.Socket",
            "javax.script.",
            "org.mozilla.javascript.",
            "dalvik.system.",
            "android."
        )
    }

    override fun visibleToScripts(className: String): Boolean {
        for (blocked in BLOCKED_CLASSES) {
            if (className == blocked || className.startsWith(blocked)) {
                return false
            }
        }
        for (prefix in ALLOWED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true
            }
        }
        return false
    }
}
