package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.KLineBar
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.Offset

private const val SPARK_BARS = 30

/**
 * 气泡行情卡里的迷你收盘折线，用消息里已有的日 K，点卡片仍进详情。
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
    val color = if (rising) ChatComposeTheme.rise else ChatComposeTheme.fall
    Canvas(modifier = modifier.fillMaxSize()) {
        val step = if (closes.size == 1) 0f else size.width / (closes.size - 1)
        var prev: Offset? = null
        closes.forEachIndexed { index, price ->
            val x = step * index
            val y = size.height - ((price - min) / range).toFloat() * size.height
            val next = Offset(x, y)
            val last = prev
            if (last != null) {
                drawLine(
                    color = color,
                    start = last,
                    end = next,
                    strokeWidth = 1.8f,
                )
            }
            prev = next
        }
    }
}
