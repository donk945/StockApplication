package com.hfad.stockapplication.data.chat

/**
 * 目标股实时行情（腾讯 qt.gtimg.cn）。名称优先用接口简称，缺省时回退模型文案。
 */
data class StockQuote(
    val code: String,
    val market: String,
    val name: String,
    val price: String,
    val prevClose: String,
    val change: String,
    val changePercent: String,
    val amount: String,
    val time: String,
    val open: String = "",
    val high: String = "",
    val low: String = "",
    val volume: String = "",
    val advanceCount: Int? = null,
    val declineCount: Int? = null,
    val unchangedCount: Int? = null,
) {
    /** 拼进本轮 user 消息末尾，气泡里不展示。 */
    fun toModelSnapshot(): String {
        return buildString {
            appendLine("【行情快照】回答价格、涨跌、成交时必须以本快照为准，不要编造或使用过时记忆。")
            appendLine("${name.ifBlank { "目标股" }}（$code）")
            append("现价 $price  涨跌 $change  $changePercent")
            appendLine()
            append("昨收 $prevClose")
            if (open.isNotBlank()) {
                append("  开 $open")
            }
            if (high.isNotBlank()) {
                append("  高 $high")
            }
            if (low.isNotBlank()) {
                append("  低 $low")
            }
            appendLine()
            if (amount.isNotBlank()) {
                append("成交额 $amount")
            }
            if (volume.isNotBlank()) {
                if (amount.isNotBlank()) {
                    append("  ")
                }
                append("量 $volume")
            }
            if (amount.isNotBlank() || volume.isNotBlank()) {
                appendLine()
            }
            if (time.isNotBlank()) {
                append("时间 $time")
            }
        }.trimEnd()
    }
}
