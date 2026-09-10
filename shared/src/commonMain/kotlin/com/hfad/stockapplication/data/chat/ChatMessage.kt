package com.hfad.stockapplication.data.chat

/**
 * 对话框一条消息。loading 时 [body] 可为「正在分析…」。
 * 助手 [body] 含标题与对用户问题的回答。「目标股票 / 目标指数」仅在问到具体标的时才有；
 * 名称由蓝色可点芯片绘制，行情卡也可缺省。对比多只时 [targets] 有多条。
 * 概念问用 [relatedPicks] 做 2～3 只可点芯片，不当作目标卡。
 * [related] / 行情仅数据；关联股与完整走势在详情页 `stock_detail` 展示。
 */
data class ChatMessage(
    val id: String,
    val fromUser: Boolean,
    val body: String,
    val quote: StockQuote? = null,
    val kline: List<KLineBar> = emptyList(),
    val related: RelatedStocks? = null,
    val targetName: String? = null,
    val targetCode: String? = null,
    val targets: List<TargetStock> = emptyList(),
    val targetQuotes: Map<String, StockQuote> = emptyMap(),
    val targetKlines: Map<String, List<KLineBar>> = emptyMap(),
    val relatedPicks: List<TargetStock> = emptyList(),
    val failed: Boolean = false,
) {
    /** 本轮要点名的正股或指数；旧会话没有 [targets] 时回退单只字段。 */
    fun listedTargets(): List<TargetStock> {
        if (targets.isNotEmpty()) {
            return targets
        }
        val code = targetCode?.takeIf { it.length == 6 } ?: return emptyList()
        val name = targetName?.takeIf { it.isNotBlank() }
            ?: quote?.name?.takeIf { it.isNotBlank() }
            ?: code
        val stock = TargetStockParser.resolve(
            code = code,
            name = name,
            marketHint = quote?.market.orEmpty(),
        ) ?: return emptyList()
        return listOf(stock)
    }

    fun pickByCode(code: String): TargetStock? {
        return listedTargets().firstOrNull { it.code == code }
            ?: relatedPicks.firstOrNull { it.code == code }
    }

    fun symbolFor(code: String): String? {
        pickByCode(code)?.symbol?.let { return it }
        quote?.takeIf { it.code == code }?.let { return it.market + it.code }
        return TargetStockParser.symbolOf(code, targetName.orEmpty(), quote?.market.orEmpty())
    }

    fun hasCards(): Boolean {
        return listedTargets().isNotEmpty() || relatedPicks.isNotEmpty()
    }

    fun quoteFor(code: String): StockQuote? {
        return targetQuotes[code] ?: quote?.takeIf { it.code == code }
    }

    fun klineFor(code: String): List<KLineBar> {
        targetKlines[code]?.let { return it }
        if (targetCode == code || quote?.code == code) {
            return kline
        }
        return emptyList()
    }

    /**
     * 把目标股和行情合成一份：名称以行情简称为准，主字段与 [targetQuotes] 对齐。
     */
    fun bindTargets(
        targets: List<TargetStock>,
        quotes: Collection<StockQuote> = emptyList(),
        fallbackQuotes: Map<String, StockQuote> = emptyMap(),
    ): ChatMessage {
        val quoteMap = LinkedHashMap(targetQuotes)
        fallbackQuotes.forEach { (code, cached) ->
            if (!quoteMap.containsKey(code)) {
                quoteMap[code] = cached
            }
        }
        quotes.forEach { quote ->
            quoteMap[quote.code] = quote
        }
        val source = targets.ifEmpty { listedTargets() }
        val renamed = source.map { stock ->
            val liveName = quoteMap[stock.code]?.name?.trim()?.ifBlank { null }
            if (liveName != null) {
                stock.copy(name = liveName)
            } else {
                stock
            }
        }
        val primary = renamed.firstOrNull()
        return copy(
            targets = renamed.ifEmpty { this.targets },
            targetName = primary?.name ?: targetName,
            targetCode = primary?.code ?: targetCode,
            quote = primary?.code?.let { quoteMap[it] } ?: quote,
            targetQuotes = quoteMap,
        )
    }
}
