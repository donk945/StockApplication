package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.StockQuote
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.geometry.Size
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

@Composable
fun StockRisePie(
    quotes: Collection<StockQuote>,
    modifier: Modifier = Modifier,
) {
    val rise = quotes.count { it.change.startsWith("+") }
    val fall = quotes.count { it.change.startsWith("-") }
    val flat = quotes.size - rise - fall
    val total = quotes.size
    MiniChartFrame(title = "关联股涨跌", empty = total == 0, modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val side = minOf(size.width, size.height)
                val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
                val arcSize = Size(side, side)
                var start = -90f
                fun sweep(count: Int) = if (total == 0) 0f else 360f * count / total
                if (rise > 0) {
                    drawArc(
                        color = ChatComposeTheme.rise,
                        startAngle = start,
                        sweepAngle = sweep(rise),
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize,
                    )
                    start += sweep(rise)
                }
                if (fall > 0) {
                    drawArc(
                        color = ChatComposeTheme.fall,
                        startAngle = start,
                        sweepAngle = sweep(fall),
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize,
                    )
                    start += sweep(fall)
                }
                if (flat > 0) {
                    drawArc(
                        color = ChatComposeTheme.placeholder,
                        startAngle = start,
                        sweepAngle = sweep(flat),
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text("涨$rise", fontSize = 10.sp, color = ChatComposeTheme.rise)
                Spacer(modifier = Modifier.width(8.dp))
                Text("跌$fall", fontSize = 10.sp, color = ChatComposeTheme.fall)
                Spacer(modifier = Modifier.width(8.dp))
                Text("平$flat", fontSize = 10.sp, color = ChatComposeTheme.placeholder)
            }
        }
    }
}
