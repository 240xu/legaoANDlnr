package io.legado.engine.entity.rule

/**
 * loginUi 行控件定义 - 移植自 lyc486 版 Legado RowUi
 */
data class RowUi(
    val type: String = "text",
    val name: String = "",
    val label: String = "",
    val default: String? = null,
    val values: Any? = null,
    val options: Any? = null,
    val action: String? = null
) {
    object Type {
        const val TEXT = "text"
        const val PASSWORD = "password"
        const val EDIT = "edit"
        const val BUTTON = "button"
        const val SPINNER = "spinner"
        const val SELECT = "select"
        const val INFO = "info"
        const val TEXT_VIEW = "text_view"
    }
}
