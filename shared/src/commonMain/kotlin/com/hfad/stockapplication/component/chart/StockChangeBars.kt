package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.StockQuote
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.geometry.Size
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

private val ChangeBarRowHeight = 28.dp
private val ChangeBarNameSlot = 80.dp

@Composable
fun StockChangeBars(
    quotes: List<StockQuote>,
    modifier: Modifier = Modifier,
) {
    val rows = quotes.map { it to quotePercent(it) }
    val plotHeight = ChangeBarRowHeight * rows.size.coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "涨跌幅对比",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChatComposeTheme.title,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MiniChartHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ChatComposeTheme.contentBg),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无", fontSize = 13.sp, color = ChatComposeTheme.placeholder)
            }
            return@Column
        }
        val maxAbs = rows.maxOf { kotlin.math.abs(it.second) }.takeIf { it > 0.0 } ?: 1.0
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = ChangeBarNameSlot)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ChatComposeTheme.contentBg),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    rows.forEach { (_, percent) ->
                        Canvas(modifier = Modifier.fillMaxWidth().height(ChangeBarRowHeight)) {
                            val mid = size.width / 2f
                            val w = (kotlin.math.abs(percent) / maxAbs).toFloat() * (size.width / 2f)
                            val color = if (percent >= 0) {
                                ChatComposeTheme.rise
                            } else {
                                ChatComposeTheme.fall
                            }
                            if (percent >= 0) {
                                drawRect(
                                    color = color,
                                    topLeft = Offset(mid, 2f),
                                    size = Size(w.coerceAtLeast(1f), size.height - 4f),
                                )
                            } else {
                                drawRect(
                                    color = color,
                                    topLeft = Offset(mid - w, 2f),
                                    size = Size(w.coerceAtLeast(1f), size.height - 4f),
                                )
                            }
                            drawLine(
                                color = ChatComposeTheme.hairline,
                                start = Offset(mid, 0f),
                                end = Offset(mid, size.height),
                                strokeWidth = 1f,
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                rows.forEach { (quote, _) ->
                    Box(
                        modifier = Modifier.height(ChangeBarRowHeight),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = changeBarLabel(quote.name),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChatComposeTheme.title,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

internal fun quotePercent(quote: StockQuote): Double {
    return quote.changePercent.replace("%", "").trim().toDoubleOrNull() ?: 0.0
}

/** 去掉空白后再截 4 字，避免「五 粮液」只露出「五 粮」。 */
internal fun changeBarLabel(name: String): String {
    return name.filter { ch ->
        !ch.isWhitespace() &&
            ch.category != CharCategory.FORMAT &&
            ch.category != CharCategory.CONTROL
    }.take(4)
}
