package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.KLineBar
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.geometry.Size

@Composable
fun StockVolumeBars(
    bars: List<KLineBar>,
    modifier: Modifier = Modifier,
) {
    val volumes = bars.mapNotNull { it.volume.toDoubleOrNull() }
    MiniChartFrame(title = "成交量", empty = volumes.isEmpty(), modifier = modifier) {
        val max = volumes.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        Canvas(modifier = Modifier.fillMaxSize()) {
            val slot = size.width / volumes.size
            val barWidth = (slot * 0.62f).coerceAtLeast(1f)
            volumes.forEachIndexed { index, volume ->
                val h = (volume / max).toFloat() * size.height
                drawRect(
                    color = ChatComposeTheme.accent.copy(alpha = 0.7f),
                    topLeft = Offset(slot * index + (slot - barWidth) / 2f, size.height - h),
                    size = Size(barWidth, h.coerceAtLeast(1f)),
                )
            }
        }
    }
}
