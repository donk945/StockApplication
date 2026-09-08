package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.StockQuote
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import kotlin.math.round

@Composable
fun StockSectorSummary(
    quotes: List<StockQuote>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "板块数据",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChatComposeTheme.title,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ChatComposeTheme.contentBg)
                .padding(12.dp)
        ) {
            if (quotes.isEmpty()) {
                Text("暂无", fontSize = 13.sp, color = ChatComposeTheme.placeholder)
                return@Column
            }
            val rise = quotes.count { it.change.startsWith("+") }
            val fall = quotes.count { it.change.startsWith("-") }
            val avg = quotes.map { quotePercent(it) }.average()
            val avgColor = when {
                avg > 0 -> ChatComposeTheme.rise
                avg < 0 -> ChatComposeTheme.fall
                else -> ChatComposeTheme.title
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "上涨  $rise",
                    fontSize = 13.sp,
                    color = ChatComposeTheme.rise,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "下跌  $fall",
                    fontSize = 13.sp,
                    color = ChatComposeTheme.fall,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "平均涨跌  ${formatAvg(avg)}%",
                fontSize = 13.sp,
                color = avgColor,
            )
        }
    }
}

private fun formatAvg(value: Double): String {
    val rounded = round(value * 100.0) / 100.0
    val text = rounded.toString().let { if (it.endsWith(".0")) it.dropLast(2) else it }
    return if (rounded > 0) "+$text" else text
}
