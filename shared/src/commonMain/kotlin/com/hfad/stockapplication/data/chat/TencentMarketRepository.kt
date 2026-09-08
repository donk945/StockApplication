package com.hfad.stockapplication.data.chat

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.math.abs
import kotlin.math.round

/**
 * 腾讯网页行情 / 前复权日 K。非官方接口，失败时由调用方忽略，不阻断问答。
 */
class TencentMarketRepository(
    private val network: NetworkModule,
) {

    /**
     * 拉目标股行情与约 120 根日 K；任一失败则对应字段为空。
     */
    fun fetchTargetMarket(
        symbol: String,
        displayName: String,
        onResult: (StockQuote?, List<KLineBar>) -> Unit,
    ) {
        fetchQuote(symbol, displayName) { quote ->
            fetchDailyKline(symbol) { kline ->
                onResult(quote, kline)
            }
        }
    }

    fun fetchQuote(
        symbol: String,
        displayName: String,
        onResult: (StockQuote?) -> Unit,
    ) {
        getText(QUOTE_URL + symbol) { text ->
            onResult(parseQuote(extractQuoteBlock(text, symbol), symbol, displayName))
        }
    }

    /**
     * 一次请求多只行情。[items] 为 symbol 到展示名。
     */
    fun fetchQuotes(
        items: List<Pair<String, String>>,
        onResult: (List<StockQuote>) -> Unit,
    ) {
        if (items.isEmpty()) {
            onResult(emptyList())
            return
        }
        val list = items.joinToString(",") { it.first }
        getText(QUOTE_URL + list) { text ->
            val quotes = items.mapNotNull { (symbol, name) ->
                parseQuote(extractQuoteBlock(text, symbol), symbol, name)
            }
            onResult(quotes)
        }
    }

    /**
     * 串行拉多只日 K，避免并发打爆接口。
     */
    fun fetchDailyKlines(
        symbols: List<String>,
        onResult: (Map<String, List<KLineBar>>) -> Unit,
    ) {
        if (symbols.isEmpty()) {
            onResult(emptyMap())
            return
        }
        val acc = mutableMapOf<String, List<KLineBar>>()
        fun next(index: Int) {
            if (index >= symbols.size) {
                onResult(acc)
                return
            }
            val symbol = symbols[index]
            fetchDailyKline(symbol) { bars ->
                acc[symbol] = bars
                next(index + 1)
            }
        }
        next(0)
    }

    fun fetchDailyKline(
        symbol: String,
        onResult: (List<KLineBar>) -> Unit,
    ) {
        val url = "$KLINE_URL?param=$symbol,day,,,$KLINE_LEN,qfq"
        network.httpRequest(
            url = url,
            isPost = false,
            param = JSONObject(),
            headers = requestHeaders(),
            cookie = null,
            timeout = TIMEOUT_SEC,
        ) { data, success, _, _ ->
            if (!success) {
                onResult(emptyList())
                return@httpRequest
            }
            onResult(parseKline(data, symbol))
        }
    }

    fun fetchMinuteKline(
        symbol: String,
        onResult: (List<KLineBar>, String) -> Unit,
    ) {
        val url = "$MINUTE_URL?param=$symbol,m1,,$MINUTE_LEN"
        network.httpRequest(
            url = url,
            isPost = false,
            param = JSONObject(),
            headers = requestHeaders(),
            cookie = null,
            timeout = TIMEOUT_SEC,
        ) { data, success, _, _ ->
            if (!success) {
                onResult(emptyList(), "")
                return@httpRequest
            }
            val (bars, prec) = parseMinute(data, symbol)
            onResult(bars, prec)
        }
    }

    private fun getText(url: String, onText: (String?) -> Unit) {
        network.httpRequest(
            url = url,
            isPost = false,
            param = JSONObject(),
            headers = requestHeaders(),
            cookie = null,
            timeout = TIMEOUT_SEC,
        ) { data, success, _, _ ->
            if (!success) {
                onText(null)
                return@httpRequest
            }
            val text = unwrapResponseText(data)
            onText(text.ifBlank { null })
        }
    }

    private fun requestHeaders(): JSONObject {
        return JSONObject()
            .put("Referer", REFERER)
            .put("User-Agent", USER_AGENT)
    }

    /** NetworkModule 会把非 JSON 包成 `{ "data": "原文" }`。 */
    private fun unwrapResponseText(data: JSONObject): String {
        val nested = data.optString("data")
        if (nested.isNotBlank()) {
            return nested
        }
        return ""
    }

    /** 从批量 `v_sh600519="...";` 文本里切出某一只。 */
    private fun extractQuoteBlock(raw: String?, symbol: String): String? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val key = "v_$symbol="
        val start = raw.indexOf(key)
        if (start < 0) {
            return null
        }
        val end = raw.indexOf(';', start)
        return if (end > start) raw.substring(start, end) else raw.substring(start)
    }

    private fun parseQuote(raw: String?, symbol: String, displayName: String): StockQuote? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val start = raw.indexOf("=\"")
        if (start < 0) {
            return null
        }
        val end = raw.lastIndexOf('"')
        if (end <= start + 2) {
            return null
        }
        val fields = raw.substring(start + 2, end).split("~")
        if (fields.size < 35) {
            return null
        }
        val tencentName = fields.getOrNull(1).orEmpty().trim()
        val price = fields.getOrNull(3)?.toDoubleOrNull() ?: return null
        val prevClose = fields.getOrNull(4)?.toDoubleOrNull() ?: 0.0
        val isIndex = IndexCatalog.bySymbol(symbol) != null ||
            IndexCatalog.looksLikeIndexName(tencentName.ifBlank { displayName })
        if (isIndex) {
            if (price == 0.0 && prevClose == 0.0) {
                return null
            }
        } else if (!isListedAShare(tencentName.ifBlank { displayName }, price, prevClose)) {
            return null
        }
        val usedName = tencentName.ifBlank { displayName }
        val change = price - prevClose
        val changePercent = if (prevClose != 0.0) change / prevClose * 100.0 else 0.0
        val amount = parseAmountYuan(fields)
        val open = fields.getOrNull(5)?.toDoubleOrNull()
        val high = fields.getOrNull(33)?.toDoubleOrNull()
        val low = fields.getOrNull(34)?.toDoubleOrNull()
        val volumeRaw = fields.getOrNull(6)?.toDoubleOrNull()
        val volume = if (isIndex) {
            volumeRaw
        } else {
            volumeRaw?.let { it * HAND_SHARES }
        }
        val market = if (symbol.startsWith("sh")) "sh" else "sz"
        val code = symbol.removePrefix("sh").removePrefix("sz")
        val breadth = if (isIndex) parseBreadth(fields) else null
        return StockQuote(
            code = code,
            market = market,
            name = usedName,
            price = formatNumber(price),
            prevClose = formatNumber(prevClose),
            change = formatSigned(change),
            changePercent = "${formatSigned(changePercent)}%",
            amount = formatAmount(amount),
            time = formatQuoteTime(fields.getOrNull(30).orEmpty()),
            open = open?.let { formatNumber(it) }.orEmpty(),
            high = high?.let { formatNumber(it) }.orEmpty(),
            low = low?.let { formatNumber(it) }.orEmpty(),
            volume = volume?.let { formatAmount(it) }.orEmpty(),
            advanceCount = breadth?.first,
            declineCount = breadth?.second,
            unchangedCount = breadth?.third,
        )
    }

    private fun parseKline(payload: JSONObject, symbol: String): List<KLineBar> {
        val stock = klineStockObject(payload, symbol) ?: return emptyList()
        val rows = stock.optJSONArray("qfqday") ?: stock.optJSONArray("day") ?: return emptyList()
        val result = mutableListOf<KLineBar>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONArray(i) ?: continue
            val day = row.optString(0).orEmpty()
            if (day.isBlank()) {
                continue
            }
            val volumeHands = row.optString(5)?.toDoubleOrNull()
            val isIndex = IndexCatalog.bySymbol(symbol) != null
            val volume = if (isIndex) {
                volumeHands?.toLong()?.toString().orEmpty()
            } else {
                volumeHands?.let { (it * HAND_SHARES).toLong().toString() }.orEmpty()
            }
            result.add(
                KLineBar(
                    day = day,
                    open = formatKlineNumber(row.optString(1)),
                    close = formatKlineNumber(row.optString(2)),
                    high = formatKlineNumber(row.optString(3)),
                    low = formatKlineNumber(row.optString(4)),
                    volume = volume,
                )
            )
        }
        return result
    }

    private fun parseMinute(payload: JSONObject, symbol: String): Pair<List<KLineBar>, String> {
        val stock = klineStockObject(payload, symbol) ?: return emptyList<KLineBar>() to ""
        val prec = stock.optString("prec").orEmpty()
        val rows = stock.optJSONArray("m1") ?: return emptyList<KLineBar>() to prec
        val result = mutableListOf<KLineBar>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONArray(i) ?: continue
            val stamp = row.optString(0).orEmpty()
            val label = formatMinuteLabel(stamp)
            if (label.isBlank()) {
                continue
            }
            result.add(
                KLineBar(
                    day = label,
                    open = formatKlineNumber(row.optString(1)),
                    close = formatKlineNumber(row.optString(2)),
                    high = formatKlineNumber(row.optString(3)),
                    low = formatKlineNumber(row.optString(4)),
                    volume = row.optString(5).orEmpty(),
                )
            )
        }
        val sessionDate = result.mapNotNull { bar ->
            bar.day.takeIf { it.length >= 10 && it[4] == '-' }?.substring(0, 10)
        }.maxOrNull()
        val session = if (sessionDate == null) {
            result
        } else {
            result.filter { it.day.startsWith(sessionDate) }
        }
        return session to prec
    }

    private fun klineStockObject(payload: JSONObject, symbol: String): JSONObject? {
        payload.optJSONObject("data")?.optJSONObject(symbol)?.let { return it }
        val nested = payload.optString("data").orEmpty()
        if (!nested.startsWith("{")) {
            return null
        }
        return try {
            JSONObject(nested).optJSONObject("data")?.optJSONObject(symbol)
        } catch (_: Throwable) {
            null
        }
    }

    /** 指数涨跌家数：常见在 44–46 或 47–49，像价格的小数不要。 */
    private fun parseBreadth(fields: List<String>): Triple<Int, Int, Int>? {
        return takeBreadth(fields, 44, 45, 46) ?: takeBreadth(fields, 47, 48, 49)
    }

    private fun takeBreadth(
        fields: List<String>,
        upIndex: Int,
        downIndex: Int,
        flatIndex: Int,
    ): Triple<Int, Int, Int>? {
        val up = parseCount(fields.getOrNull(upIndex)) ?: return null
        val down = parseCount(fields.getOrNull(downIndex)) ?: return null
        val flat = parseCount(fields.getOrNull(flatIndex)) ?: return null
        if (up + down + flat < 10) {
            return null
        }
        return Triple(up, down, flat)
    }

    private fun parseCount(raw: String?): Int? {
        if (raw.isNullOrBlank() || raw.contains('.')) {
            return null
        }
        val value = raw.toIntOrNull() ?: return null
        return value.takeIf { it in 0..8000 }
    }

    private fun parseAmountYuan(fields: List<String>): Double {
        val detail = fields.getOrNull(35).orEmpty().split("/")
        val fromDetail = detail.getOrNull(2)?.toDoubleOrNull()
        if (fromDetail != null && fromDetail > 0) {
            return fromDetail
        }
        val wan = fields.getOrNull(37)?.toDoubleOrNull() ?: 0.0
        return wan * 10_000.0
    }

    private fun formatQuoteTime(raw: String): String {
        if (raw.length < 12) {
            return raw
        }
        val date = "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
        val clock = "${raw.substring(8, 10)}:${raw.substring(10, 12)}"
        val seconds = if (raw.length >= 14) ":${raw.substring(12, 14)}" else ""
        return "$date $clock$seconds"
    }

    private fun formatMinuteLabel(stamp: String): String {
        if (stamp.length < 12) {
            return stamp
        }
        val date = "${stamp.substring(0, 4)}-${stamp.substring(4, 6)}-${stamp.substring(6, 8)}"
        val clock = "${stamp.substring(8, 10)}:${stamp.substring(10, 12)}"
        return "$date $clock"
    }

    private fun isListedAShare(name: String, price: Double, prevClose: Double): Boolean {
        if (price == 0.0 && prevClose == 0.0) {
            return false
        }
        if (name.isBlank()) {
            return false
        }
        return NON_SHARE_MARKERS.none { marker -> name.contains(marker, ignoreCase = true) }
    }

    private fun formatKlineNumber(raw: String?): String {
        val value = raw?.toDoubleOrNull() ?: return raw.orEmpty()
        return formatNumber(value)
    }

    private fun formatNumber(value: Double): String {
        return (round(value * 100.0) / 100.0).toString()
    }

    private fun formatSigned(value: Double): String {
        val text = formatNumber(abs(value))
        return when {
            value > 0 -> "+$text"
            value < 0 -> "-$text"
            else -> text
        }
    }

    private fun formatAmount(yuan: Double): String {
        return when {
            yuan >= 100_000_000 -> "${formatNumber(yuan / 100_000_000)}亿"
            yuan >= 10_000 -> "${formatNumber(yuan / 10_000)}万"
            else -> formatNumber(yuan)
        }
    }

    companion object {
        const val QUOTE_URL = "https://qt.gtimg.cn/utf8/q="
        const val KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
        const val MINUTE_URL = "https://ifzq.gtimg.cn/appstock/app/kline/mkline"
        const val REFERER = "https://gu.qq.com/"
        const val USER_AGENT = "Mozilla/5.0"
        const val KLINE_LEN = 120
        const val MINUTE_LEN = 240
        const val TIMEOUT_SEC = 15
        private const val HAND_SHARES = 100.0
        private val NON_SHARE_MARKERS = listOf("指数", "ETF", "基金", "LOF", "REIT", "期货")
    }
}
