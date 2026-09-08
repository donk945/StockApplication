package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.KLineBar
import com.hfad.stockapplication.data.chat.StockQuote
import com.tencent.kuikly.compose.foundation.background
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
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/** 行情卡固定高度，避免十字光标切到某根 K 线时把日 K 图挤短。 */
private val QuoteHeaderHeight = 112.dp

/**
 * 行情卡。未选中时展示现价；选中某根日 K 或某一分钟时改展示该点日期/时刻与 OHLC。
 * [selectedBaseline] 有值时涨跌相对该价（分时用昨收），否则相对该根开盘。
 * 两种状态共用同一行结构，高度不变。
 */
@Composable
fun StockQuoteHeader(
    quote: StockQuote?,
    selectedBar: KLineBar? = null,
    selectedBaseline: String? = null,
    modifier: Modifier = Modifier,
) {
    val caption: String
    val price: String
    val change: String
    val changeColor: Color
    val line3: String
    val line4: String

    if (selectedBar != null) {
        val open = selectedBar.open.toDoubleOrNull()
        val close = selectedBar.close.toDoubleOrNull()
        val baseline = selectedBaseline?.toDoubleOrNull() ?: open
        caption = selectedBar.day.ifBlank { "该根 K 线" }
        price = selectedBar.close
        change = barChangeText(baseline, close)
        changeColor = when {
            baseline == null || close == null -> ChatComposeTheme.title
            close >= baseline -> ChatComposeTheme.rise
            else -> ChatComposeTheme.fall
        }
        line3 = "开  ${selectedBar.open}    高  ${selectedBar.high}"
        line4 = buildString {
            append("低  ${selectedBar.low}    收  ${selectedBar.close}")
            if (selectedBar.volume.isNotBlank()) {
                append("    量  ${selectedBar.volume}")
            }
        }
    } else if (quote != null) {
        caption = quote.time.ifBlank { "最新" }
        price = quote.price
        change = "${quote.change}  ${quote.changePercent}"
        changeColor = when {
            quote.change.startsWith("+") -> ChatComposeTheme.rise
            quote.change.startsWith("-") -> ChatComposeTheme.fall
            else -> ChatComposeTheme.title
        }
        line3 = "昨收  ${quote.prevClose}    成交额  ${quote.amount}"
        line4 = " "
    } else {
        caption = " "
        price = "—"
        change = ""
        changeColor = ChatComposeTheme.placeholder
        line3 = "行情加载中或暂无"
        line4 = " "
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(QuoteHeaderHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(ChatComposeTheme.contentBg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = caption,
            fontSize = 12.sp,
            color = ChatComposeTheme.placeholder,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = price,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = changeColor,
            )
            if (change.isNotBlank()) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = change,
                    fontSize = 13.sp,
                    color = changeColor,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = line3,
            fontSize = 12.sp,
            color = ChatComposeTheme.title,
        )
        Text(
            text = line4,
            fontSize = 12.sp,
            color = ChatComposeTheme.placeholder,
        )
    }
}

internal fun barChangeText(open: Double?, close: Double?): String {
    if (open == null || close == null) return ""
    val diff = close - open
    val percent = if (open == 0.0) 0.0 else diff / open * 100.0
    val sign = if (diff > 0) "+" else ""
    return "$sign${diff.toPlain()}  $sign${percent.toPlain()}%"
}

private fun Double.toPlain(): String {
    val rounded = (this * 100.0).toLong() / 100.0
    val text = rounded.toString()
    return if (text.endsWith(".0")) text.dropLast(2) else text
}
