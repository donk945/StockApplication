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
            appendLine("${prefix}本轮只依据该选点的价格、涨跌、成交与时间作答，不要使用最新现价或其他交易日，不要编造。")
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

/**
 * 详情页与问答页不共享 [ChatStore]。长按 K 线后把选点挂在这里，
 * 关掉详情栈，问答页 [pageDidAppear] 再取走挂到输入框上方。
 */
object ChartAskHandoff {
    private var pending: ChartAskContext? = null

    fun offer(context: ChartAskContext) {
        pending = context
    }

    fun peek(): ChartAskContext? = pending

    fun take(): ChartAskContext? {
        val next = pending
        pending = null
        return next
    }
}
