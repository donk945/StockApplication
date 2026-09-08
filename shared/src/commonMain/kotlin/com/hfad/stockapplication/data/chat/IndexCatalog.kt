package com.hfad.stockapplication.data.chat

/**
 * 少量常用指数（不是全市场）。别名用于用户问句和模型「目标指数」段。
 * 000001 / 000016 / 000688 会与正股撞码，必须靠名称或 sh 前缀区分。
 */
data class IndexMeta(
    val name: String,
    val code: String,
    val market: String,
    val aliases: List<String>,
    val blurb: String,
    val constituents: List<RelatedStock> = emptyList(),
) {
    val symbol: String
        get() = "$market$code"

    fun toTarget(displayName: String = ""): TargetStock {
        return TargetStock(
            name = displayName.trim().ifBlank { name },
            code = code,
            market = market,
            kind = ListedKind.Index,
        )
    }
}

object IndexCatalog {

    val ALL: List<IndexMeta> = listOf(
        IndexMeta(
            name = "上证指数",
            code = "000001",
            market = "sh",
            aliases = listOf("上证指数", "上证综指", "沪深大盘", "沪指", "上证", "大盘"),
            blurb = "上海证券交易所全部上市股票的综合指数，用来看沪市整体涨跌，不是单只股票。",
            constituents = listOf(
                RelatedStock("贵州茅台", "600519"),
                RelatedStock("招商银行", "600036"),
                RelatedStock("中国平安", "601318"),
            ),
        ),
        IndexMeta(
            name = "深证成指",
            code = "399001",
            market = "sz",
            aliases = listOf("深证成指", "深圳成指", "深成指", "深指"),
            blurb = "深圳证券交易所成份指数，反映深市代表性公司整体表现。",
            constituents = listOf(
                RelatedStock("宁德时代", "300750"),
                RelatedStock("比亚迪", "002594"),
                RelatedStock("迈瑞医疗", "300760"),
            ),
        ),
        IndexMeta(
            name = "创业板指",
            code = "399006",
            market = "sz",
            aliases = listOf("创业板指数", "创业板指", "创指", "创业板"),
            blurb = "创业板成份指数，跟踪深市创业板代表性公司，成长股波动通常大于主板。",
            constituents = listOf(
                RelatedStock("宁德时代", "300750"),
                RelatedStock("迈瑞医疗", "300760"),
                RelatedStock("东方财富", "300059"),
            ),
        ),
        IndexMeta(
            name = "沪深300",
            code = "000300",
            market = "sh",
            aliases = listOf("沪深300指数", "沪深三百", "沪深300"),
            blurb = "沪深两市规模大、流动性好的 300 只股票，常被当作 A 股核心资产基准。",
            constituents = listOf(
                RelatedStock("贵州茅台", "600519"),
                RelatedStock("宁德时代", "300750"),
                RelatedStock("招商银行", "600036"),
            ),
        ),
        IndexMeta(
            name = "上证50",
            code = "000016",
            market = "sh",
            aliases = listOf("上证50指数", "上证五十", "上证50"),
            blurb = "上证规模大、流动性好的 50 只龙头股，偏大盘蓝筹。",
            constituents = listOf(
                RelatedStock("贵州茅台", "600519"),
                RelatedStock("招商银行", "600036"),
                RelatedStock("中国平安", "601318"),
            ),
        ),
        IndexMeta(
            name = "科创50",
            code = "000688",
            market = "sh",
            aliases = listOf("科创50指数", "科创五十", "科创50", "科创板50"),
            blurb = "科创板代表性公司指数，科技成长风格，波动大于主板蓝筹。",
            constituents = listOf(
                RelatedStock("中芯国际", "688981"),
                RelatedStock("海光信息", "688041"),
                RelatedStock("寒武纪", "688256"),
            ),
        ),
    )

    private val uniqueCodes: Set<String> = ALL
        .groupBy { it.code }
        .filter { it.value.size == 1 && it.key.startsWith("399") }
        .keys

    fun bySymbol(symbol: String): IndexMeta? {
        val key = symbol.trim().lowercase()
        return ALL.firstOrNull { it.symbol == key }
    }

    fun uniqueByCode(code: String): IndexMeta? {
        if (code !in uniqueCodes) {
            return null
        }
        return ALL.firstOrNull { it.code == code }
    }

    fun matchName(name: String): IndexMeta? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        ALL.firstOrNull { it.name == trimmed }?.let { return it }
        val sorted = ALL.flatMap { meta ->
            meta.aliases.map { alias -> alias to meta }
        }.sortedByDescending { it.first.length }
        return sorted.firstOrNull { (alias, _) ->
            trimmed == alias || trimmed.contains(alias)
        }?.second
    }

    fun looksLikeIndexName(name: String): Boolean {
        if (name.contains("指数")) {
            return true
        }
        return matchName(name) != null
    }

    /**
     * 在用户问句里找指数。更长别名优先，避免「上证50」再拆成「上证」。
     */
    fun matchInText(text: String): List<IndexMeta> {
        if (text.isBlank()) {
            return emptyList()
        }
        val occupied = BooleanArray(text.length)
        val found = linkedMapOf<String, IndexMeta>()
        val sorted = ALL.flatMap { meta ->
            meta.aliases.map { alias -> alias to meta }
        }.sortedByDescending { it.first.length }
        sorted.forEach { (alias, meta) ->
            var start = 0
            while (start <= text.length - alias.length) {
                val index = text.indexOf(alias, start)
                if (index < 0) {
                    break
                }
                val range = index until (index + alias.length)
                val overlap = range.any { occupied.getOrElse(it) { true } }
                if (!overlap) {
                    range.forEach { pos -> occupied[pos] = true }
                    found.putIfAbsent(meta.symbol, meta)
                }
                start = index + 1
            }
        }
        return found.values.toList()
    }
}
