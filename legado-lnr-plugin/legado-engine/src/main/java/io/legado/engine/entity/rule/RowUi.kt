package io.legado.engine.entity.rule

/**
 * loginUi 行控件定义 - 完整移植自 lyc486 版 Legado RowUi
 * 支持类型：text, password, button, toggle, select, edit, info, text_view
 */
data class RowUi(
    val type: String = "text",
    val name: String = "",
    val label: String = "",
    val default: String? = null,
    val values: Any? = null,
    val options: Any? = null,
    val action: String? = null,
    val chars: List<String>? = null,
    val viewName: String? = null,
    val style: Map<String, Any>? = null
) {
    object Type {
        const val TEXT = "text"
        const val PASSWORD = "password"
        const val EDIT = "edit"
        const val BUTTON = "button"
        const val TOGGLE = "toggle"
        const val SELECT = "select"
        const val SPINNER = "spinner"
        const val INFO = "info"
        const val TEXT_VIEW = "text_view"
    }
}
