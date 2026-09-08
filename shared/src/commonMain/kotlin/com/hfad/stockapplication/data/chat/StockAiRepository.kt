package com.hfad.stockapplication.data.chat

/**
 * 向大模型询问股票上下文（Markdown 正文 + 从一级标题取出的会话标题）。
 */
interface StockAiRepository {
    fun askStockContext(
        question: String,
        history: List<ChatMessage>,
        onDelta: (String) -> Unit,
        onResult: (Result<StockAskResult>) -> Unit,
        marketContext: String = "",
        think: Boolean = false,
    )

    fun cancel()

    fun updateApiKey(apiKey: String) {
    }
}
