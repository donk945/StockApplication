package com.hfad.stockapplication.data.chat

/**
 * 助手 Markdown 里 `## 标题` 分段。目标股、关联三段共用，避免两套扫描。
 */
internal object MarkdownSection {

    fun afterHeading(markdown: String, heading: String): String? {
        val lines = markdown.lines()
        val start = lines.indexOfFirst { line ->
            val trimmed = line.trim()
            trimmed.startsWith("##") && trimmed.contains(heading)
        }
        if (start < 0) {
            return null
        }
        val rest = lines.drop(start + 1)
        val end = rest.indexOfFirst { it.trim().startsWith("##") }
        val body = if (end < 0) rest else rest.take(end)
        return body.joinToString("\n").trim().ifBlank { null }
    }
}
