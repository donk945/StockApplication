package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.RelatedStock
import com.hfad.stockapplication.data.chat.RelatedStocks
import com.hfad.stockapplication.data.chat.StockQuote
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * 三组关联股。有 6 位代码才可点，复用 [TargetStockChip]。
 */
@Composable
fun RelatedStocksSection(
    related: RelatedStocks?,
    quotes: Map<String, StockQuote> = emptyMap(),
    onOpen: (RelatedStock) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (related == null || related.isEmpty()) {
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        RelatedGroup(title = "行业板块", stocks = related.industry, quotes = quotes, onOpen = onOpen)
        RelatedGroup(
            title = "产业链与供应链",
            stocks = related.supplyChain,
            quotes = quotes,
            onOpen = onOpen,
        )
        RelatedGroup(
            title = "同行业与同概念",
            stocks = related.peers,
            quotes = quotes,
            onOpen = onOpen,
        )
    }
}

@Composable
private fun RelatedGroup(
    title: String,
    stocks: List<RelatedStock>,
    quotes: Map<String, StockQuote>,
    onOpen: (RelatedStock) -> Unit,
) {
    if (stocks.isEmpty()) {
        return
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = ChatComposeTheme.title,
    )
    Spacer(modifier = Modifier.height(4.dp))
    stocks.forEach { stock ->
        val quote = quotes[stock.code]
        val canOpen = stock.code.length == 6
        val change = listOfNotNull(
            quote?.change?.takeIf { it.isNotBlank() },
            quote?.changePercent?.takeIf { it.isNotBlank() },
        ).joinToString("  ")
        TargetStockChip(
            name = stock.name,
            code = stock.code,
            enabled = canOpen,
            changeText = change,
            onClick = { if (canOpen) onOpen(stock) },
        )
    }
}
