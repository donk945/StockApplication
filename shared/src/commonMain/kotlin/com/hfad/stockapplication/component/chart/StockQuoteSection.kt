package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.KLineBar
import com.hfad.stockapplication.data.chat.StockQuote
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.heightIn
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
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

private enum class QuoteChartTab {
    Minute,
    Daily,
}

/**
 * 个股区：行情头、今开高低、分时/日 K 切换。不含关联股。
 */
@Composable
fun StockQuoteSection(
    quote: StockQuote?,
    bars: List<KLineBar>,
    selectedIndex: Int?,
    selectedBar: KLineBar?,
    onSelect: (Int?) -> Unit,
    minuteBars: List<KLineBar> = emptyList(),
    minutePrevClose: String = "",
    selectedMinuteIndex: Int? = null,
    onMinuteSelect: (Int?) -> Unit = {},
    selectedBaseline: String? = null,
    onDailyAsk: (KLineBar) -> Unit = {},
    onMinuteAsk: (KLineBar) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val onMinuteAskLatest = rememberUpdatedState(onMinuteAsk)
    val onDailyAskLatest = rememberUpdatedState(onDailyAsk)
    var chartTab by remember { mutableStateOf(QuoteChartTab.Minute) }
    LaunchedEffect(quote?.code, quote?.market) {
        chartTab = QuoteChartTab.Minute
    }
    StockQuoteHeader(
        quote = quote,
        selectedBar = selectedBar,
        selectedBaseline = selectedBaseline,
        modifier = modifier,
    )
    Spacer(modifier = Modifier.height(12.dp))
    StockQuoteFacts(quote = quote)
    if (quote?.advanceCount != null || quote?.declineCount != null) {
        Spacer(modifier = Modifier.height(12.dp))
        IndexBreadthRow(quote = quote)
    }
    Spacer(modifier = Modifier.height(16.dp))
    ChartTabRow(
        selected = chartTab,
        onSelect = { next ->
            if (next != chartTab) {
                chartTab = next
                onSelect(null)
                onMinuteSelect(null)
            }
        },
    )
    Spacer(modifier = Modifier.height(8.dp))
    when (chartTab) {
        QuoteChartTab.Minute -> {
            val minuteDate = minuteSessionDateLabel(minuteBars)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "分时",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChatComposeTheme.title,
                )
                if (minuteDate.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = minuteDate,
                        fontSize = 12.sp,
                        color = ChatComposeTheme.placeholder,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            StockIntradayChart(
                bars = minuteBars,
                prevClose = minutePrevClose.ifBlank { quote?.prevClose.orEmpty() },
                selectedIndex = selectedMinuteIndex,
                onSelect = onMinuteSelect,
                onLongPress = { index ->
                    minuteBars.getOrNull(index)?.let { onMinuteAskLatest.value(it) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ChatComposeTheme.contentBg),
            )
        }
        QuoteChartTab.Daily -> {
            var klineWindow by remember { mutableStateOf(defaultKlineWindow(bars.size)) }
            LaunchedEffect(bars.firstOrNull()?.day) {
                klineWindow = defaultKlineWindow(bars.size)
            }
            val visibleWindow = klineWindow.followTotal(bars.size)
            val visibleDaily = visibleWindow.slice(bars)
            val firstVisibleDay = visibleDaily.firstOrNull()?.day.orEmpty()
            val lastVisibleDay = visibleDaily.lastOrNull()?.day.orEmpty()
            val spanYears = klineYear(firstVisibleDay) != klineYear(lastVisibleDay)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "日 K",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChatComposeTheme.title,
                )
                if (firstVisibleDay.isNotBlank() && lastVisibleDay.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${formatKlineAxisDay(firstVisibleDay, spanYears)} – ${formatKlineAxisDay(lastVisibleDay, spanYears)}",
                        fontSize = 12.sp,
                        color = ChatComposeTheme.placeholder,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                MaLegendChip("MA5", MaColors.ma5)
                Spacer(modifier = Modifier.width(8.dp))
                MaLegendChip("MA10", MaColors.ma10)
                Spacer(modifier = Modifier.width(8.dp))
                MaLegendChip("MA20", MaColors.ma20)
            }
            Spacer(modifier = Modifier.height(8.dp))
            StockKlineChart(
                bars = bars,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                window = visibleWindow,
                onWindowChange = { klineWindow = it },
                onLongPress = { index ->
                    bars.getOrNull(index)?.let { onDailyAskLatest.value(it) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ChatComposeTheme.contentBg),
            )
            Spacer(modifier = Modifier.height(16.dp))
            StockVolumeBars(bars = visibleDaily)
        }
    }
}

@Composable
private fun ChartTabRow(
    selected: QuoteChartTab,
    onSelect: (QuoteChartTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ChatComposeTheme.contentBg)
            .padding(3.dp),
    ) {
        ChartTab(
            label = "分时",
            selected = selected == QuoteChartTab.Minute,
            onClick = { onSelect(QuoteChartTab.Minute) },
            modifier = Modifier.weight(1f),
        )
        ChartTab(
            label = "日 K",
            selected = selected == QuoteChartTab.Daily,
            onClick = { onSelect(QuoteChartTab.Daily) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MaLegendChip(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 10.sp, color = ChatComposeTheme.placeholder)
    }
}

@Composable
private fun ChartTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) ChatComposeTheme.surface else ChatComposeTheme.contentBg
    val color = if (selected) ChatComposeTheme.accent else ChatComposeTheme.placeholder
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
        )
    }
}
