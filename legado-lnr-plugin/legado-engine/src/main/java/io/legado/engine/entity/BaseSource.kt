package io.legado.engine.entity

/**
 * 书源/RSS源基础接口
 */
interface BaseSource {
    val sourceUrl: String
    val sourceName: String
    var sourceGroup: String?
    val loginUrl: String?
    val loginCheckJs: String?
    val jsEngine: Int
    var concurrentRate: String?
    var header: String?
    var bookSourceComment: String?

    fun getHeaderMap(): Map<String, String> {
        val map = HashMap<String, String>()
        header?.let {
            it.lines().forEach { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    map[parts[0].trim()] = parts[1].trim()
                }
            }
        }
        return map
    }
}