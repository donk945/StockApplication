package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.StockQuote
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * 最新行情网格。不跟十字光标走。
 */
@Composable
fun StockQuoteFacts(
    quote: StockQuote?,
    modifier: Modifier = Modifier,
) {
    val open = quote?.open.orDash()
    val high = quote?.high.orDash()
    val low = quote?.low.orDash()
    val volume = quote?.volume.orDash()
    val amount = quote?.amount.orDash()
    val prevClose = quote?.prevClose.orDash()
    Column(modifier = modifier.fillMaxWidth()) {
        FactRow(
            leftLabel = "今开",
            leftValue = open,
            midLabel = "最高",
            midValue = high,
            rightLabel = "最低",
            rightValue = low,
        )
        Spacer(modifier = Modifier.height(12.dp))
        FactRow(
            leftLabel = "成交量",
            leftValue = volume,
            midLabel = "成交额",
            midValue = amount,
            rightLabel = "昨收",
            rightValue = prevClose,
        )
    }
}

@Composable
private fun FactRow(
    leftLabel: String,
    leftValue: String,
    midLabel: String,
    midValue: String,
    rightLabel: String,
    rightValue: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        FactCell(label = leftLabel, value = leftValue, modifier = Modifier.weight(1f))
        FactCell(label = midLabel, value = midValue, modifier = Modifier.weight(1f))
        FactCell(label = rightLabel, value = rightValue, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FactCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(end = 8.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = ChatComposeTheme.placeholder,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = ChatComposeTheme.title,
        )
    }
}

private fun String?.orDash(): String {
    return if (this.isNullOrBlank()) "—" else this
}
