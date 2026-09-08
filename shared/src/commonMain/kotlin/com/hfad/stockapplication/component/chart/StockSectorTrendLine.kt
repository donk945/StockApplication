package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.KLineBar
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.Offset

@Composable
fun StockSectorTrendLine(
    klines: Collection<List<KLineBar>>,
    modifier: Modifier = Modifier,
) {
    val series = averageCloses(klines)
    MiniChartFrame(title = "板块走势", empty = series.size < 2, modifier = modifier) {
        val min = series.minOrNull() ?: 0.0
        val max = series.maxOrNull() ?: 1.0
        val range = (max - min).let { if (it <= 0.0) 1.0 else it }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = if (series.size <= 1) 0f else size.width / (series.size - 1)
            var prev: Offset? = null
            series.forEachIndexed { index, value ->
                val x = step * index
                val y = size.height - ((value - min) / range).toFloat() * size.height
                val point = Offset(x, y)
                val last = prev
                if (last != null) {
                    drawLine(
                        color = ChatComposeTheme.accent,
                        start = last,
                        end = point,
                        strokeWidth = 2f,
                    )
                }
                prev = point
            }
        }
    }
}

internal fun averageCloses(
    klines: Collection<List<KLineBar>>,
    count: Int = VISIBLE_KLINE_BARS,
): List<Double> {
    val trimmed = klines.map { it.takeLast(count) }.filter { it.isNotEmpty() }
    if (trimmed.isEmpty()) {
        return emptyList()
    }
    val len = trimmed.minOf { it.size }
    if (len < 2) {
        return emptyList()
    }
    return (0 until len).map { offset ->
        val values = trimmed.mapNotNull { bars ->
            bars[bars.size - len + offset].close.toDoubleOrNull()
        }
        if (values.isEmpty()) 0.0 else values.average()
    }
}
