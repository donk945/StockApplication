package com.hfad.stockapplication.data.chat

/**
 * 一只关联股。代码可选，模型常只给名称。
 */
data class RelatedStock(
    val name: String,
    val code: String = "",
)

/**
 * 三组关联股，入库并在目标股详情抽屉展示。
 */
data class RelatedStocks(
    val industry: List<RelatedStock> = emptyList(),
    val supplyChain: List<RelatedStock> = emptyList(),
    val peers: List<RelatedStock> = emptyList(),
) {
    fun isEmpty(): Boolean {
        return industry.isEmpty() && supplyChain.isEmpty() && peers.isEmpty()
    }

    fun coded(limit: Int = 6): List<RelatedStock> {
        return (industry + supplyChain + peers)
            .filter { it.code.length == 6 && TargetStockParser.symbolOf(it.code) != null }
            .distinctBy { it.code }
            .take(limit)
    }

    /** 三组各取若干只有代码的股，避免只覆盖名单最前面的一组。 */
    fun codedBalanced(perGroup: Int = 4): List<RelatedStock> {
        return listOf(industry, supplyChain, peers)
            .flatMap { group ->
                group.filter { it.code.length == 6 && TargetStockParser.symbolOf(it.code) != null }
                    .distinctBy { it.code }
                    .take(perGroup)
            }
            .distinctBy { it.code }
    }

    fun allCoded(): List<RelatedStock> {
        return coded(limit = Int.MAX_VALUE)
    }

    /** 只保留接口能核到行情的正股，名称用行情简称。 */
    fun keepListed(quotes: Map<String, StockQuote>): RelatedStocks {
        return RelatedStocks(
            industry = keepGroup(industry, quotes),
            supplyChain = keepGroup(supplyChain, quotes),
            peers = keepGroup(peers, quotes),
        )
    }

    private fun keepGroup(
        stocks: List<RelatedStock>,
        quotes: Map<String, StockQuote>,
    ): List<RelatedStock> {
        return stocks.mapNotNull { stock ->
            val quote = quotes[stock.code] ?: return@mapNotNull null
            RelatedStock(name = quote.name.ifBlank { stock.name }, code = stock.code)
        }
    }
}

/**
 * 从助手 Markdown 抽出关联列表，并生成给用户看的正文（不含关联段）。
 */
object RelatedStockParser {

    private val codeRegex = Regex("""(\d{6})""")
    private val paraSplit = Regex("""\n{2,}""")
    private val numberedItem = Regex("""^\d+\.\s""")
    private val boldMarks = Regex("""\*\*""")
    private val underMarks = Regex("""__""")
    private val bulletLine = Regex("""(?m)^(\s*)[\*\-]\s+""")
    private val headingPrefixLine = Regex("""(?m)^#{1,6}\s+""")
    private val boldTriple = Regex("""\*\*\*([\s\S]+?)\*\*\*""")
    private val boldPair = Regex("""\*\*([\s\S]+?)\*\*""")
    private val underPair = Regex("""__([\s\S]+?)__""")
    private val italicSpan = Regex("""\*([^*\n]+)\*""")
    private val leftoverStars = Regex("""\*{1,3}""")
    private val leftoverUnder = Regex("""_{1,2}""")
    private val headingPrefix = Regex("^#{1,6}\\s*")
    private val listPrefix = Regex("^[-*•]+\\s+")
    private val backticks = Regex("`+")
    private val codeInName = Regex("""[（(]?\d{6}[）)]?""")
    private val whitespace = Regex("""\s+""")

    private val metaHeadings = listOf(
        "目标股票",
        "目标指数",
        "相关标的",
        "行业板块",
        "产业链与供应链",
        "同行业与同概念",
    )

    fun parse(markdown: String): RelatedStocks {
        return RelatedStocks(
            industry = parseList(MarkdownSection.afterHeading(markdown, "行业板块")),
            supplyChain = parseList(MarkdownSection.afterHeading(markdown, "产业链与供应链")),
            peers = parseList(MarkdownSection.afterHeading(markdown, "同行业与同概念")),
        )
    }

    /** 去掉关联三段、目标股/指数和相关标的（名称改由芯片绘制；无标的时不留空标题）。 */
    fun displayMarkdown(raw: String): String {
        if (raw.isBlank()) {
            return raw
        }
        val hasMeta = metaHeadings.any { raw.contains(it) }
        val body = if (hasMeta) stripMetaSections(raw) else raw.trim()
        return stripLeadingTitle(body)
    }

    private fun stripMetaSections(raw: String): String {
        val lines = raw.lines()
        val kept = mutableListOf<String>()
        var skipping = false
        for (line in lines) {
            val trimmed = line.trim()
            if (isH2(trimmed)) {
                skipping = isMetaHeading(trimmed)
                if (skipping) {
                    continue
                }
            }
            if (skipping) {
                continue
            }
            kept.add(line)
        }
        return kept.joinToString("\n").trim()
    }

    private fun isH2(trimmed: String): Boolean {
        return trimmed.startsWith("##") && !trimmed.startsWith("###")
    }

    /**
     * 流式 Markdown：闭合尚未写完的 `**` / `__`，避免解析器把半截标记画成星号。
     */
    fun streamMarkdown(markdown: String): String {
        if (markdown.isBlank()) {
            return markdown
        }
        var text = markdown
        if (boldMarks.findAll(text).count() % 2 == 1) {
            text += "**"
        }
        if (underMarks.findAll(text).count() % 2 == 1) {
            text += "__"
        }
        return text
    }

    /**
     * 把助手正文拆成多段，供 LazyColumn 每段一个 item。
     * Kuikly 不能在高于视口的单个 item 内滚动；一段过长就会白屏后卡在尾部、无法上翻。
     */
    fun splitDisplayParts(body: String): List<String> {
        val source = body.ifBlank { "正在分析…" }
        val paras = source
            .split(paraSplit)
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
        val chunks = if (paras.size >= 2) {
            paras.flatMap { splitLongBlock(it) }
        } else {
            splitLongBlock(source)
        }
        return chunks.ifEmpty { listOf(source) }
    }

    private fun splitLongBlock(source: String): List<String> {
        val lines = source.lines()
        if (lines.size <= 6 && source.length <= 220) {
            return listOf(source)
        }
        val chunks = mutableListOf<String>()
        val buf = StringBuilder()
        fun flush() {
            val text = buf.toString().trimEnd()
            if (text.isNotEmpty()) {
                chunks.add(text)
            }
            buf.clear()
        }
        lines.forEach { line ->
            val trimmed = line.trimStart()
            val startBlock = trimmed.startsWith("#") ||
                trimmed.startsWith("- ") ||
                trimmed.startsWith("* ") ||
                numberedItem.containsMatchIn(trimmed)
            if (startBlock && buf.length > 80 && !hasUnclosedMarkers(buf)) {
                flush()
            }
            if (buf.isNotEmpty()) {
                buf.append('\n')
            }
            buf.append(line)
            if (buf.length >= 220 && !hasUnclosedMarkers(buf)) {
                flush()
            } else if (buf.length >= 480) {
                flush()
            }
        }
        flush()
        return chunks.ifEmpty { listOf(source) }
    }

    private fun hasUnclosedMarkers(text: CharSequence): Boolean {
        return boldMarks.findAll(text).count() % 2 == 1 ||
            underMarks.findAll(text).count() % 2 == 1
    }

    /**
     * 流式气泡走纯 Text：去掉加粗/标题标记，列表改用圆点，绝不把生 `*` 画出来。
     */
    fun streamPlainText(markdown: String): String {
        if (markdown.isBlank()) {
            return markdown
        }
        val bullet = '•'
        var text = markdown.replace(bulletLine, "$1$bullet ")
        text = text.replace(headingPrefixLine, "")
        text = text.replace(boldTriple, "$1")
        text = text.replace(boldPair, "$1")
        text = text.replace(underPair, "$1")
        text = text.replace(italicSpan, "$1")
        text = text.replace(leftoverStars, "")
        text = text.replace(leftoverUnder, "")
        return text
    }

    /** 详情抽屉顶部用：去掉 Markdown 标记，按句号收满几句，不在半句加省略号。 */
    fun answerExcerpt(markdown: String, maxChars: Int = 280): String {
        if (markdown.isBlank() || markdown == "正在分析…") {
            return ""
        }
        val displayed = displayMarkdown(markdown)
        val text = displayed
            .lines()
            .map { stripMarkdownLine(it) }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(whitespace, " ")
            .trim()
        if (text.isEmpty()) {
            return ""
        }
        return takeCompleteSentences(text, maxChars)
    }

    private fun stripMarkdownLine(raw: String): String {
        return raw.trim()
            .replace(headingPrefix, "")
            .replace(listPrefix, "")
            .replace("**", "")
            .replace("__", "")
            .replace(backticks, "")
            .replace("*", "")
            .replace("_", "")
            .trim()
    }

    private fun takeCompleteSentences(text: String, maxChars: Int): String {
        if (text.length <= maxChars) {
            return text
        }
        val stops = setOf('。', '！', '？', '；')
        var lastStop = -1
        val limit = (maxChars * 3 / 2).coerceAtMost(text.length)
        for (i in 0 until limit) {
            if (text[i] in stops) {
                lastStop = i
                if (i + 1 >= maxChars) {
                    break
                }
            }
        }
        if (lastStop >= 0) {
            return text.take(lastStop + 1)
        }
        val firstStop = text.indexOfFirst { it in stops }
        if (firstStop >= 0) {
            return text.take(firstStop + 1)
        }
        return text
    }

    /** 把目标股名称和已存关联股拼回 Markdown，仅给模型当多轮上下文，不进气泡。 */
    fun mergeForHistory(
        displayBody: String,
        related: RelatedStocks?,
        targetName: String? = null,
        targetCode: String? = null,
        targets: List<TargetStock> = emptyList(),
        relatedPicks: List<TargetStock> = emptyList(),
    ): String {
        val listed = if (targets.isNotEmpty()) {
            targets
        } else if (!targetName.isNullOrBlank() && !targetCode.isNullOrBlank()) {
            val resolved = TargetStockParser.resolve(targetCode, targetName)
            if (resolved != null) listOf(resolved) else emptyList()
        } else {
            emptyList()
        }
        val builder = StringBuilder(displayBody.trimEnd())
        val indices = listed.filter { it.isIndex }
        val stocks = listed.filter { !it.isIndex }
        appendTargetSection(builder, "目标指数", indices)
        appendTargetSection(builder, "目标股票", stocks)
        appendTargetSection(builder, "相关标的", relatedPicks)
        if (related == null || related.isEmpty()) {
            return builder.toString()
        }
        appendSection(builder, "行业板块", related.industry)
        appendSection(builder, "产业链与供应链", related.supplyChain)
        appendSection(builder, "同行业与同概念", related.peers)
        return builder.toString()
    }

    private fun appendTargetSection(
        builder: StringBuilder,
        heading: String,
        items: List<TargetStock>,
    ) {
        if (items.isEmpty()) {
            return
        }
        builder.append("\n## ").append(heading).append('\n')
        items.forEach { stock ->
            builder.append("- ").append(stock.name)
            if (stock.code.isNotBlank()) {
                builder.append("（").append(stock.code).append("）")
            }
            builder.append('\n')
        }
    }

    private fun appendSection(builder: StringBuilder, heading: String, items: List<RelatedStock>) {
        builder.append("\n## ").append(heading).append('\n')
        if (items.isEmpty()) {
            builder.append("- 暂无\n")
            return
        }
        items.forEach { item ->
            builder.append("- ").append(item.name)
            if (item.code.isNotBlank()) {
                builder.append("（").append(item.code).append("）")
            }
            builder.append('\n')
        }
    }

    private fun isMetaHeading(trimmed: String): Boolean {
        if (!trimmed.startsWith("##") || trimmed.startsWith("###")) {
            return false
        }
        return metaHeadings.any { trimmed.contains(it) }
    }

    /** 顶栏已有会话标题，气泡里不再显示首行 `# 标题`。 */
    private fun stripLeadingTitle(markdown: String): String {
        var index = 0
        val length = markdown.length
        while (index < length && markdown[index].isWhitespace()) {
            index += 1
        }
        if (index >= length || markdown[index] != '#') {
            return markdown
        }
        if (index + 1 < length && markdown[index + 1] == '#') {
            return markdown
        }
        val newline = markdown.indexOf('\n', index)
        if (newline < 0) {
            return ""
        }
        return markdown.substring(newline + 1).trim()
    }

    private fun parseList(section: String?): List<RelatedStock> {
        if (section.isNullOrBlank()) {
            return emptyList()
        }
        val result = mutableListOf<RelatedStock>()
        section.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().trimStart('-', '*', '•', ' ').trim()
            if (line.isEmpty() || line.startsWith("#") || line == "暂无") {
                return@forEach
            }
            val code = codeRegex.find(line)?.groupValues?.get(1).orEmpty()
            val name = line.replace(codeInName, "").trim()
            if (name.isNotEmpty()) {
                result.add(RelatedStock(name = name, code = code))
            }
        }
        return result.take(8)
    }
}
