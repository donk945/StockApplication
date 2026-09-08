package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.data.chat.KLineBar
import com.hfad.stockapplication.data.chat.RelatedStocks
import com.hfad.stockapplication.data.chat.StockQuote
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 关联区：板块卡、走势、涨跌饼/柱、三组关联股。目标股抽屉才组。
 */
@Composable
fun StockRelatedSection(
    related: RelatedStocks?,
    relatedQuotes: Map<String, StockQuote>,
    industryKlines: Map<String, List<KLineBar>>,
    onOpen: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (related == null || related.isEmpty()) {
        return
    }
    val listedCodes = related.allCoded().map { it.code }.toSet()
    val quotes = relatedQuotes.filterKeys { it in listedCodes }
    val industryQuotes = related.industry.mapNotNull { stock ->
        quotes[stock.code]
    }
    Spacer(modifier = Modifier.height(16.dp))
    StockSectorSummary(quotes = industryQuotes)
    Spacer(modifier = Modifier.height(16.dp))
    StockSectorTrendLine(klines = industryKlines.values)
    Spacer(modifier = Modifier.height(16.dp))
    StockRisePie(quotes = quotes.values)
    Spacer(modifier = Modifier.height(16.dp))
    val barQuotes = related.allCoded().mapNotNull { stock ->
        quotes[stock.code]
    }
    StockChangeBars(quotes = barQuotes.ifEmpty { quotes.values.toList() })
    RelatedStocksSection(
        related = related,
        quotes = quotes,
        onOpen = { stock -> onOpen(stock.name, stock.code) },
    )
}
