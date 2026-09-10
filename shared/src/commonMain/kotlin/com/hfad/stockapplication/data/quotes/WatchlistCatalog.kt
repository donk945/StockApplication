package com.hfad.stockapplication.data.quotes

import com.hfad.stockapplication.data.chat.IndexCatalog
import com.hfad.stockapplication.data.chat.TargetStockParser

/**
 * 任务 1 行情页默认自选：常用指数 + 若干流动性较好的 A 股。
 * 不是全市场股票池，仅用于 Demo 展示实时报价。
 */
data class WatchItem(
    val name: String,
    val code: String,
    val market: String,
    val isIndex: Boolean,
) {
    val symbol: String
        get() = "$market$code"
}

object WatchlistCatalog {

    val INDICES: List<WatchItem> = IndexCatalog.ALL.map { meta ->
        WatchItem(
            name = meta.name,
            code = meta.code,
            market = meta.market,
            isIndex = true,
        )
    }

    val STOCKS: List<WatchItem> = listOf(
        WatchItem("贵州茅台", "600519", "sh", false),
        WatchItem("宁德时代", "300750", "sz", false),
        WatchItem("比亚迪", "002594", "sz", false),
        WatchItem("招商银行", "600036", "sh", false),
        WatchItem("中国平安", "601318", "sh", false),
        WatchItem("隆基绿能", "601012", "sh", false),
        WatchItem("中芯国际", "688981", "sh", false),
        WatchItem("东方财富", "300059", "sz", false),
    )

    val DEFAULT: List<WatchItem> = INDICES + STOCKS

    /**
     * 按 6 位代码解析一只标的：优先匹配常用指数，否则按沪深规则推断市场。
     */
    fun resolveCode(raw: String): WatchItem? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length != 6) {
            return null
        }
        val index = IndexCatalog.ALL.firstOrNull { it.code == digits }
        if (index != null) {
            return WatchItem(index.name, index.code, index.market, true)
        }
        val market = TargetStockParser.marketPrefix(digits) ?: return null
        return WatchItem(digits, digits, market, false)
    }
}
