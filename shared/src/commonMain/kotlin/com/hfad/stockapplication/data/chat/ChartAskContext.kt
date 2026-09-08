package com.hfad.stockapplication.data.chat

/**
 * 日 K / 分时长按选中的一根，带到对话提问。快照只进模型，不进用户气泡。
 */
data class ChartAskContext(
    val kind: Kind,
    val name: String,
    val code: String,
    val bar: KLineBar,
    val baseline: String = "",
    val market: String = "",
) {
    enum class Kind { Daily, Minute }

    fun toModelSnapshot(): String {
        val prefix = when (kind) {
            Kind.Daily -> "【日K选点】"
            Kind.Minute -> "【分时选点】"
        }
        return buildString {
            appendLine("${prefix}回答该时间点价格、涨跌、成交时必须以本快照为准，不要编造。")
            appendLine("${name.ifBlank { "目标股" }}（$code）")
            appendLine("时间 ${bar.day}")
            append("开 ${bar.open}  高 ${bar.high}  低 ${bar.low}  收 ${bar.close}")
            appendLine()
            if (bar.volume.isNotBlank()) {
                appendLine("量 ${bar.volume}")
            }
            if (baseline.isNotBlank()) {
                append("基准 $baseline")
            }
        }.trimEnd()
    }
}
