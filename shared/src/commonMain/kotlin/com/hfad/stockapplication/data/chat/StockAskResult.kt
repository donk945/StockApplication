package com.hfad.stockapplication.data.chat

/**
 * 模型回包：会话标题 + 完整 Markdown 正文。
 */
data class StockAskResult(
    val title: String,
    val markdown: String,
) {
    companion object {
        fun fromMarkdown(raw: String): StockAskResult? {
            if (raw.isBlank()) {
                return null
            }
            val heading = SessionTitles.fromHeading(raw)
            return StockAskResult(
                title = SessionTitles.orDefault(heading.orEmpty()),
                markdown = raw,
            )
        }
    }
}
