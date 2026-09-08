package com.hfad.stockapplication.data.chat

enum class ListedKind {
    Stock,
    Index,
}

/**
 * 本轮点名的正股或指数。行情接口用 [symbol]（sh/sz + 6 位代码）。
 */
data class TargetStock(
    val name: String,
    val code: String,
    val market: String,
    val kind: ListedKind = ListedKind.Stock,
) {
    val symbol: String
        get() = "$market$code"

    val isIndex: Boolean
        get() = kind == ListedKind.Index
}
