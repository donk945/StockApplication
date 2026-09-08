package com.hfad.stockapplication.data.chat

/**
 * 从助手 Markdown 的「## 目标股票 / 目标指数」段解析名称与 6 位代码。
 */
object TargetStockParser {

    private val codeRegex = Regex("""(\d{6})""")
    /** 同一行里「名称（代码）」可出现多次；代码可带 sh/sz。 */
    private val namedCodeRegex = Regex("""([^,，、;；\n]+?)[（(]((?:sh|sz)?\d{6})[）)]""", RegexOption.IGNORE_CASE)
    private val leadingBullet = Regex("""^[-*•]+""")
    private val leadingJoin = Regex("""^(和|与|及|以及|还有)""")
    private val codeInParens = Regex("""[（(]?(?:sh|sz)?\d{6}[）)]?""", RegexOption.IGNORE_CASE)
    private val prefixedCode = Regex("""(?i)(sh|sz)(\d{6})""")
    private val bareCode = Regex("""(?<![0-9A-Za-z])([036]\d{5})(?![0-9])""")
    private val boardKeys = listOf("板块", "概念", "赛道", "行业", "主题")
    const val MAX_TARGETS = 4
    const val MAX_RELATED_PICKS = 3

    fun parse(markdown: String): TargetStock? {
        return parseAll(markdown).firstOrNull()
    }

    /** 「## 目标指数」+「## 目标股票」下去重保序，最多 [MAX_TARGETS] 只。 */
    fun parseAll(markdown: String): List<TargetStock> {
        val result = linkedMapOf<String, TargetStock>()
        fun offer(stock: TargetStock) {
            if (result.size >= MAX_TARGETS || result.containsKey(stock.code)) {
                return
            }
            result[stock.code] = stock
        }
        parseSection(markdown, "目标指数", preferIndex = true).forEach(::offer)
        parseSection(markdown, "目标股票", preferIndex = false).forEach(::offer)
        return result.values.toList()
    }

    /** 概念/板块问下的 2～3 只可点相关股，不作为本轮目标卡。 */
    fun parseRelatedPicks(markdown: String): List<TargetStock> {
        if (!markdown.contains("相关标的")) {
            return emptyList()
        }
        return parseSection(markdown, "相关标的", preferIndex = false).take(MAX_RELATED_PICKS)
    }

    fun symbolOf(code: String): String? {
        return resolve(code)?.symbol
    }

    fun symbolOf(code: String, name: String, marketHint: String = ""): String? {
        return resolve(code, name, marketHint)?.symbol
    }

    /** 从用户原文里取出沪深 6 位代码（含 sh/sz 前缀）。 */
    fun codeInText(text: String): String? {
        return codesInText(text).firstOrNull()
    }

    /** 用户原文里点到的全部沪深正股代码，去重保序。 */
    fun codesInText(text: String): List<String> {
        return listedInQuestion(text).map { it.code }
    }

    /**
     * 问句里的指数（别名 / sh000001）和正股代码。大盘类问句会落到上证，不会误成平安银行。
     */
    fun listedInQuestion(text: String): List<TargetStock> {
        val result = linkedMapOf<String, TargetStock>()
        fun offer(stock: TargetStock) {
            if (result.size >= MAX_TARGETS) {
                return
            }
            if (result.values.any { it.code == stock.code }) {
                return
            }
            result[stock.symbol] = stock
        }
        IndexCatalog.matchInText(text).forEach { meta ->
            offer(meta.toTarget())
        }
        prefixedCode.findAll(text).forEach { match ->
            val market = match.groupValues[1].lowercase()
            val code = match.groupValues[2]
            val preferIndex = IndexCatalog.bySymbol("$market$code") != null
            resolve(code, marketHint = market, preferIndex = preferIndex)?.let(::offer)
        }
        bareCode.findAll(text).forEach { match ->
            val code = match.groupValues[1]
            if (result.values.any { it.code == code }) {
                return@forEach
            }
            val unique = IndexCatalog.uniqueByCode(code)
            resolve(
                code = code,
                preferIndex = unique != null,
            )?.let(::offer)
        }
        return result.values.toList()
    }

    /**
     * 板块/概念/赛道问句：不要沿用上一轮目标股，避免硬猜龙头。
     * 已点名指数或代码的问句不算。
     */
    fun isBoardOrConceptQuestion(text: String): Boolean {
        if (listedInQuestion(text).isNotEmpty()) {
            return false
        }
        return boardKeys.any { text.contains(it) }
    }

    fun marketPrefix(code: String): String? {
        return resolve(code)?.market
    }

    fun resolve(
        code: String,
        name: String = "",
        marketHint: String = "",
        preferIndex: Boolean = false,
    ): TargetStock? {
        val digits = normalizeCode(code) ?: return null
        val prefix = normalizeMarket(marketHint)
        val byName = IndexCatalog.matchName(name)
        if (byName != null && byName.code == digits) {
            return byName.toTarget(name)
        }
        if (prefix.isNotBlank()) {
            val hinted = IndexCatalog.bySymbol("$prefix$digits")
            if (hinted != null && (preferIndex || IndexCatalog.looksLikeIndexName(name))) {
                return hinted.toTarget(name)
            }
        }
        val unique = IndexCatalog.uniqueByCode(digits)
        if (unique != null && (prefix.isBlank() || prefix == unique.market)) {
            return unique.toTarget(name)
        }
        if (preferIndex) {
            val candidates = IndexCatalog.ALL.filter { it.code == digits }
            val picked = when {
                prefix.isNotBlank() -> candidates.firstOrNull { it.market == prefix }
                byName != null -> byName.takeIf { it.code == digits }
                else -> candidates.singleOrNull()
            }
            if (picked != null) {
                return picked.toTarget(name)
            }
        }
        val market = when {
            prefix.isNotBlank() -> prefix
            digits.startsWith("6") -> "sh"
            digits.startsWith("0") || digits.startsWith("3") -> "sz"
            else -> return null
        }
        return TargetStock(
            name = name.trim().ifBlank { digits },
            code = digits,
            market = market,
            kind = ListedKind.Stock,
        )
    }

    private fun parseSection(
        markdown: String,
        heading: String,
        preferIndex: Boolean,
    ): List<TargetStock> {
        if (!markdown.contains(heading)) {
            return emptyList()
        }
        val section = MarkdownSection.afterHeading(markdown, heading) ?: return emptyList()
        val result = linkedMapOf<String, TargetStock>()
        fun offer(rawName: String, rawCode: String) {
            if (result.size >= MAX_TARGETS || result.containsKey(normalizeCode(rawCode))) {
                return
            }
            val prefixMatch = prefixedCode.find(rawCode)
            val marketHint = prefixMatch?.groupValues?.get(1).orEmpty()
            val digits = prefixMatch?.groupValues?.get(2) ?: normalizeCode(rawCode) ?: return
            val name = rawName
                .replace(leadingBullet, "")
                .replace(leadingJoin, "")
                .trim()
                .ifBlank { digits }
            val stock = resolve(
                code = digits,
                name = name,
                marketHint = marketHint,
                preferIndex = preferIndex || IndexCatalog.looksLikeIndexName(name),
            ) ?: return
            result[stock.code] = stock
        }
        section.lineSequence().forEach { rawLine ->
            if (result.size >= MAX_TARGETS) {
                return@forEach
            }
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line == "暂无") {
                return@forEach
            }
            val pairs = namedCodeRegex.findAll(line).toList()
            if (pairs.isNotEmpty()) {
                pairs.forEach { match ->
                    offer(match.groupValues[1], match.groupValues[2])
                }
                return@forEach
            }
            val prefixed = prefixedCode.find(line)
            if (prefixed != null) {
                val name = line.replace(codeInParens, "").trim().trimStart('-', '*', '•', ' ').trim()
                offer(name, prefixed.value)
                return@forEach
            }
            val match = codeRegex.find(line) ?: return@forEach
            val name = line
                .replace(codeInParens, "")
                .trim()
                .trimStart('-', '*', '•', ' ')
                .trim()
            offer(name, match.groupValues[1])
        }
        return result.values.toList()
    }

    private fun normalizeCode(raw: String): String? {
        val digits = raw.filter { it.isDigit() }.takeLast(6)
        return digits.takeIf { it.length == 6 }
    }

    private fun normalizeMarket(raw: String): String {
        val lower = raw.trim().lowercase()
        return when {
            lower.startsWith("sh") -> "sh"
            lower.startsWith("sz") -> "sz"
            else -> ""
        }
    }
}
