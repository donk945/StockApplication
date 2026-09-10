package com.hfad.stockapplication.state.quotes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hfad.stockapplication.data.chat.StockQuote
import com.hfad.stockapplication.data.chat.TencentMarketRepository
import com.hfad.stockapplication.data.quotes.WatchItem
import com.hfad.stockapplication.data.quotes.WatchlistCatalog

data class WatchQuoteRow(
    val item: WatchItem,
    val quote: StockQuote? = null,
)

/**
 * 任务 1 行情页状态：默认自选列表 + 按代码追加，定时刷新腾讯报价。
 */
class QuotesStore(
    private val marketRepository: TencentMarketRepository,
) {
    val rows = mutableStateListOf<WatchQuoteRow>()
    var query: String by mutableStateOf("")
    var darkTheme: Boolean by mutableStateOf(false)
    var loading: Boolean by mutableStateOf(false)
    var hint: String by mutableStateOf("")

    fun loadInitial(dark: Boolean) {
        darkTheme = dark
        if (rows.isEmpty()) {
            rows.addAll(WatchlistCatalog.DEFAULT.map { WatchQuoteRow(it) })
        }
        refresh()
    }

    fun refresh() {
        if (rows.isEmpty()) {
            return
        }
        loading = true
        val items = rows.map { it.item.symbol to it.item.name }
        marketRepository.fetchQuotes(items) { quotes ->
            loading = false
            val bySymbol = quotes.associateBy { "${it.market}${it.code}" }
            for (index in rows.indices) {
                val row = rows[index]
                val live = bySymbol[row.item.symbol]
                if (live != null) {
                    val named = if (row.item.name.any { it.isDigit() } && live.name.isNotBlank()) {
                        row.item.copy(name = live.name)
                    } else {
                        row.item
                    }
                    rows[index] = row.copy(item = named, quote = live)
                }
            }
        }
    }

    fun submitQuery(): Boolean {
        val item = WatchlistCatalog.resolveCode(query.trim())
        if (item == null) {
            hint = "请输入 6 位 A 股或指数代码"
            return false
        }
        hint = ""
        query = ""
        val existing = rows.indexOfFirst { it.item.symbol == item.symbol }
        if (existing >= 0) {
            val row = rows.removeAt(existing)
            rows.add(0, row)
        } else {
            rows.add(0, WatchQuoteRow(item))
        }
        refresh()
        return true
    }
}
