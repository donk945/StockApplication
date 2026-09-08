package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.KLineBar
import com.hfad.stockapplication.data.chat.StockQuote
import com.hfad.stockapplication.data.chat.TargetStock
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.geometry.Size
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import kotlin.math.abs

/**
 * 对比答：两只并排涨跌，而不是上下叠两张完整卡。
 */
@Composable
fun ComparisonPairRow(
    left: TargetStock,
    leftQuote: StockQuote?,
    leftBars: List<KLineBar>,
    right: TargetStock,
    rightQuote: StockQuote?,
    rightBars: List<KLineBar>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val percents = listOf(leftQuote, rightQuote).map { quote ->
        quote?.let { quotePercent(it) } ?: 0.0
    }
    val maxAbs = percents.maxOf { abs(it) }.takeIf { it > 0.0 } ?: 1.0
    Row(modifier = modifier.fillMaxWidth()) {
        ComparisonCell(
            stock = left,
            quote = leftQuote,
            bars = leftBars,
            percent = percents[0],
            maxAbs = maxAbs,
            onClick = { onOpen(left.code) },
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        ComparisonCell(
            stock = right,
            quote = rightQuote,
            bars = rightBars,
            percent = percents[1],
            maxAbs = maxAbs,
            onClick = { onOpen(right.code) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ComparisonCell(
    stock: TargetStock,
    quote: StockQuote?,
    bars: List<KLineBar>,
    percent: Double,
    maxAbs: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val changeColor = when {
        quote?.change?.startsWith("+") == true -> ChatComposeTheme.rise
        quote?.change?.startsWith("-") == true -> ChatComposeTheme.fall
        else -> ChatComposeTheme.title
    }
    val rising = quote?.change?.startsWith("-") != true
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ChatComposeTheme.contentBg)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Text(
            text = stock.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChatComposeTheme.title,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = stock.code,
            fontSize = 11.sp,
            color = ChatComposeTheme.placeholder,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = quote?.price ?: "—",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = changeColor,
        )
        Text(
            text = quote?.changePercent ?: quote?.change ?: "—",
            fontSize = 12.sp,
            color = changeColor,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChangeRatioBar(percent = percent, maxAbs = maxAbs)
        if (bars.size >= 2) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
            ) {
                QuoteSparkline(bars = bars, rising = rising)
            }
        }
    }
}

@Composable
private fun ChangeRatioBar(
    percent: Double,
    maxAbs: Double,
) {
    val ratio = (abs(percent) / maxAbs).toFloat().coerceIn(0f, 1f)
    val color = if (percent >= 0) ChatComposeTheme.rise else ChatComposeTheme.fall
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
    ) {
        drawRect(color = ChatComposeTheme.hairline)
        val w = size.width * ratio.coerceAtLeast(0.04f)
        drawRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(w, size.height),
        )
    }
}

@Composable
fun IndexBreadthRow(
    quote: StockQuote?,
    modifier: Modifier = Modifier,
) {
    val up = quote?.advanceCount
    val down = quote?.declineCount
    val flat = quote?.unchangedCount
    if (up == null && down == null && flat == null) {
        return
    }
    Row(modifier = modifier.fillMaxWidth()) {
        BreadthCell("上涨", up?.toString() ?: "—", ChatComposeTheme.rise, Modifier.weight(1f))
        BreadthCell("下跌", down?.toString() ?: "—", ChatComposeTheme.fall, Modifier.weight(1f))
        BreadthCell("平盘", flat?.toString() ?: "—", ChatComposeTheme.placeholder, Modifier.weight(1f))
    }
}

@Composable
private fun BreadthCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(end = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(text = label, fontSize = 12.sp, color = ChatComposeTheme.placeholder)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
