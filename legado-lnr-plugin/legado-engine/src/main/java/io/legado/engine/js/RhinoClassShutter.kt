package io.legado.engine.js

import org.mozilla.javascript.ClassShutter

/**
 * Rhino JS 安全沙箱 - 移植自 lyc486 版 Legado
 * 限制 JS 可访问的 Java 类，防止恶意代码执行
 * 允许 Packages 访问以支持 source.put/get 等方法
 */
class RhinoClassShutter : ClassShutter {

    companion object {
        private val BLOCKED_CLASSES = setOf(
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
            "java.net.ServerSocket",
            "javax.script.",
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
        return true
    }
}
