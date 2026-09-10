package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.KLineBar
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.Offset

private const val SPARK_BARS = 30

/**
 * 气泡行情卡里的迷你收盘折线，用消息里已有的日 K，点卡片进详情。
 * 折线用主题强调色，和课件里的卡片示意一致。
 *
 * 不要 [fillMaxSize]：对比卡在 `weight` 列里约束可能无界，Kuikly Canvas 会白屏。
 * 调用方必须给出有限宽高。
 */
@Composable
fun QuoteSparkline(
    bars: List<KLineBar>,
    rising: Boolean,
    modifier: Modifier = Modifier,
) {
    val closes = bars.mapNotNull { it.close.toDoubleOrNull() }.takeLast(SPARK_BARS)
    if (closes.size < 2) {
        return
    }
    val min = closes.minOrNull() ?: return
    val max = closes.maxOrNull() ?: return
    val range = (max - min).let { if (it <= 0.0) 1.0 else it }
    val color = ChatComposeTheme.accent
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width < 1f || height < 1f || !width.isFinite() || !height.isFinite()) {
            return@Canvas
        }
        val step = if (closes.size == 1) 0f else width / (closes.size - 1)
        if (!step.isFinite()) {
            return@Canvas
        }
        var prev: Offset? = null
        closes.forEachIndexed { index, price ->
            val x = step * index
            val y = height - ((price - min) / range).toFloat() * height
            if (!x.isFinite() || !y.isFinite()) {
                return@forEachIndexed
            }
            val next = Offset(x, y)
            val last = prev
            if (last != null) {
                drawLine(
                    color = color,
                    start = last,
                    end = next,
                    strokeWidth = if (rising) 2f else 1.6f,
                )
            }
            prev = next
        }
    }
}
