package com.hfad.stockapplication.data.chat

import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 用 SharedPreferences 持久化会话列表。
 */
class LocalChatRepository(
    private val sp: SharedPreferencesModule,
) : ChatRepository {

    private var cache: List<ChatSession>? = null

    override fun loadHistories(): List<ChatHistoryItem> {
        return loadAll().map { ChatHistoryItem(it.id, it.title) }
    }

    override fun searchHistories(keyword: String): List<ChatHistoryItem> {
        val query = keyword.trim()
        val all = loadHistories()
        if (query.isEmpty()) {
            return all
        }
        return all.filter { it.title.contains(query, ignoreCase = true) }
    }

    override fun loadSession(id: String): ChatSession? {
        return loadAll().firstOrNull { it.id == id }
    }

    override fun saveSession(session: ChatSession) {
        val messages = session.messages.takeLast(MAX_MESSAGES)
        val trimmed = session.copy(messages = messages, updatedAt = nextUpdatedAt())
        val others = loadAll().filter { it.id != trimmed.id }
        val merged = (listOf(trimmed) + others).take(MAX_SESSIONS)
        persist(merged)
    }

    override fun deleteSession(id: String) {
        persist(loadAll().filter { it.id != id })
    }

    override fun clearAll() {
        persist(emptyList())
    }

    private fun nextUpdatedAt(): Long {
        return (loadAll().maxOfOrNull { it.updatedAt } ?: 0L) + 1L
    }

    private fun loadAll(): List<ChatSession> {
        cache?.let { return it }
        val raw = sp.getString(KEY)
        if (raw.isBlank()) {
            cache = emptyList()
            return emptyList()
        }
        val parsed = try {
            val root = JSONObject(raw)
            val array = root.optJSONArray(FIELD_SESSIONS)
            if (array == null) {
                emptyList()
            } else {
                val result = mutableListOf<ChatSession>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    parseSession(obj)?.let { result.add(it) }
                }
                result
            }
        } catch (_: Throwable) {
            emptyList()
        }
        cache = parsed
        return parsed
    }

    private fun persist(sessions: List<ChatSession>) {
        cache = sessions
        val array = JSONArray()
        sessions.forEach { array.put(toJson(it)) }
        val root = JSONObject().put(FIELD_SESSIONS, array)
        sp.setString(KEY, root.toString())
    }

    private fun parseSession(obj: JSONObject): ChatSession? {
        val id = obj.optString("id").orEmpty()
        if (id.isBlank()) {
            return null
        }
        val title = obj.optString("title").orEmpty().ifBlank { "股票问答" }
        val updatedAt = obj.optLong("updatedAt")
        val messages = mutableListOf<ChatMessage>()
        val msgArray = obj.optJSONArray("messages")
        if (msgArray != null) {
            for (i in 0 until msgArray.length()) {
                val item = msgArray.optJSONObject(i) ?: continue
                val msgId = item.optString("id").orEmpty()
                val body = item.optString("body").orEmpty()
                if (msgId.isBlank()) {
                    continue
                }
                messages.add(
                    ChatMessage(
                        id = msgId,
                        fromUser = item.optBoolean("fromUser"),
                        body = body,
                        quote = parseQuote(item.optJSONObject("quote")),
                        kline = parseKline(item.optJSONArray("kline")),
                        related = parseRelated(item.optJSONObject("related")),
                        targetName = item.optString("targetName").orEmpty().ifBlank { null },
                        targetCode = item.optString("targetCode").orEmpty().ifBlank { null },
                        targets = parseTargets(item.optJSONArray("targets")),
                        targetQuotes = parseQuoteMap(item.optJSONArray("targetQuotes")),
                        targetKlines = parseKlineMap(item.optJSONObject("targetKlines")),
                        relatedPicks = parseTargets(item.optJSONArray("relatedPicks")),
                        failed = item.optBoolean("failed"),
                    )
                )
            }
        }
        return ChatSession(id = id, title = title, messages = messages, updatedAt = updatedAt)
    }

    private fun toJson(session: ChatSession): JSONObject {
        val messages = JSONArray()
        session.messages.forEach { msg ->
            val item = JSONObject()
                .put("id", msg.id)
                .put("fromUser", msg.fromUser)
                .put("body", msg.body)
            msg.quote?.let { item.put("quote", quoteToJson(it)) }
            if (msg.kline.isNotEmpty()) {
                item.put("kline", klineToJson(msg.kline))
            }
            msg.related?.takeUnless { it.isEmpty() }?.let { item.put("related", relatedToJson(it)) }
            msg.targetName?.takeIf { it.isNotBlank() }?.let { item.put("targetName", it) }
            msg.targetCode?.takeIf { it.isNotBlank() }?.let { item.put("targetCode", it) }
            if (msg.targets.isNotEmpty()) {
                item.put("targets", targetsToJson(msg.targets))
            }
            if (msg.relatedPicks.isNotEmpty()) {
                item.put("relatedPicks", targetsToJson(msg.relatedPicks))
            }
            if (msg.targetQuotes.isNotEmpty()) {
                item.put("targetQuotes", quoteMapToJson(msg.targetQuotes))
            }
            if (msg.targetKlines.isNotEmpty()) {
                item.put("targetKlines", klineMapToJson(msg.targetKlines))
            }
            if (msg.failed) {
                item.put("failed", true)
            }
            messages.put(item)
        }
        return JSONObject()
            .put("id", session.id)
            .put("title", session.title)
            .put("updatedAt", session.updatedAt)
            .put("messages", messages)
    }

    private fun parseQuote(obj: JSONObject?): StockQuote? {
        if (obj == null) {
            return null
        }
        val code = obj.optString("code").orEmpty()
        val market = obj.optString("market").orEmpty()
        if (code.isBlank() || market.isBlank()) {
            return null
        }
        return StockQuote(
            code = code,
            market = market,
            name = obj.optString("name").orEmpty(),
            price = obj.optString("price").orEmpty(),
            prevClose = obj.optString("prevClose").orEmpty(),
            change = obj.optString("change").orEmpty(),
            changePercent = obj.optString("changePercent").orEmpty(),
            amount = obj.optString("amount").orEmpty(),
            time = obj.optString("time").orEmpty(),
            open = obj.optString("open").orEmpty(),
            high = obj.optString("high").orEmpty(),
            low = obj.optString("low").orEmpty(),
            volume = obj.optString("volume").orEmpty(),
            advanceCount = obj.optInt("advanceCount").takeIf { obj.has("advanceCount") },
            declineCount = obj.optInt("declineCount").takeIf { obj.has("declineCount") },
            unchangedCount = obj.optInt("unchangedCount").takeIf { obj.has("unchangedCount") },
        )
    }

    private fun parseKline(array: JSONArray?): List<KLineBar> {
        if (array == null) {
            return emptyList()
        }
        val result = mutableListOf<KLineBar>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val day = item.optString("day").orEmpty()
            if (day.isBlank()) {
                continue
            }
            result.add(
                KLineBar(
                    day = day,
                    open = item.optString("open").orEmpty(),
                    high = item.optString("high").orEmpty(),
                    low = item.optString("low").orEmpty(),
                    close = item.optString("close").orEmpty(),
                    volume = item.optString("volume").orEmpty(),
                )
            )
        }
        return result
    }

    private fun quoteToJson(quote: StockQuote): JSONObject {
        return JSONObject()
            .put("code", quote.code)
            .put("market", quote.market)
            .put("name", quote.name)
            .put("price", quote.price)
            .put("prevClose", quote.prevClose)
            .put("change", quote.change)
            .put("changePercent", quote.changePercent)
            .put("amount", quote.amount)
            .put("time", quote.time)
            .put("open", quote.open)
            .put("high", quote.high)
            .put("low", quote.low)
            .put("volume", quote.volume)
            .also { json ->
                quote.advanceCount?.let { json.put("advanceCount", it) }
                quote.declineCount?.let { json.put("declineCount", it) }
                quote.unchangedCount?.let { json.put("unchangedCount", it) }
            }
    }

    private fun klineToJson(bars: List<KLineBar>): JSONArray {
        val array = JSONArray()
        bars.forEach { bar ->
            array.put(
                JSONObject()
                    .put("day", bar.day)
                    .put("open", bar.open)
                    .put("high", bar.high)
                    .put("low", bar.low)
                    .put("close", bar.close)
                    .put("volume", bar.volume)
            )
        }
        return array
    }

    private fun parseTargets(array: JSONArray?): List<TargetStock> {
        if (array == null) {
            return emptyList()
        }
        val result = mutableListOf<TargetStock>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val code = item.optString("code").orEmpty()
            val market = item.optString("market").ifBlank {
                TargetStockParser.marketPrefix(code).orEmpty()
            }
            val name = item.optString("name").orEmpty().ifBlank { code }
            if (code.length != 6 || market.isBlank()) {
                continue
            }
            val kindRaw = item.optString("kind").orEmpty()
            val kind = if (kindRaw == "index" || IndexCatalog.bySymbol("$market$code") != null) {
                ListedKind.Index
            } else {
                ListedKind.Stock
            }
            result.add(TargetStock(name = name, code = code, market = market, kind = kind))
        }
        return result
    }

    private fun targetsToJson(targets: List<TargetStock>): JSONArray {
        val array = JSONArray()
        targets.forEach { stock ->
            array.put(
                JSONObject()
                    .put("name", stock.name)
                    .put("code", stock.code)
                    .put("market", stock.market)
                    .put("kind", if (stock.isIndex) "index" else "stock")
            )
        }
        return array
    }

    private fun parseQuoteMap(array: JSONArray?): Map<String, StockQuote> {
        if (array == null) {
            return emptyMap()
        }
        val result = linkedMapOf<String, StockQuote>()
        for (i in 0 until array.length()) {
            val quote = parseQuote(array.optJSONObject(i)) ?: continue
            result[quote.code] = quote
        }
        return result
    }

    private fun quoteMapToJson(quotes: Map<String, StockQuote>): JSONArray {
        val array = JSONArray()
        quotes.values.forEach { quote ->
            array.put(quoteToJson(quote))
        }
        return array
    }

    private fun parseKlineMap(obj: JSONObject?): Map<String, List<KLineBar>> {
        if (obj == null) {
            return emptyMap()
        }
        val result = linkedMapOf<String, List<KLineBar>>()
        obj.keys().forEach { code ->
            val bars = parseKline(obj.optJSONArray(code))
            if (bars.isNotEmpty()) {
                result[code] = bars
            }
        }
        return result
    }

    private fun klineMapToJson(klines: Map<String, List<KLineBar>>): JSONObject {
        val obj = JSONObject()
        klines.forEach { (code, bars) ->
            if (bars.isNotEmpty()) {
                obj.put(code, klineToJson(bars))
            }
        }
        return obj
    }

    private fun parseRelated(obj: JSONObject?): RelatedStocks? {
        if (obj == null) {
            return null
        }
        val related = RelatedStocks(
            industry = parseRelatedList(obj.optJSONArray("industry")),
            supplyChain = parseRelatedList(obj.optJSONArray("supplyChain")),
            peers = parseRelatedList(obj.optJSONArray("peers")),
        )
        return related.takeUnless { it.isEmpty() }
    }

    private fun parseRelatedList(array: JSONArray?): List<RelatedStock> {
        if (array == null) {
            return emptyList()
        }
        val result = mutableListOf<RelatedStock>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name").orEmpty()
            if (name.isBlank()) {
                continue
            }
            result.add(
                RelatedStock(
                    name = name,
                    code = item.optString("code").orEmpty(),
                )
            )
        }
        return result
    }

    private fun relatedToJson(related: RelatedStocks): JSONObject {
        return JSONObject()
            .put("industry", relatedStockListToJson(related.industry))
            .put("supplyChain", relatedStockListToJson(related.supplyChain))
            .put("peers", relatedStockListToJson(related.peers))
    }

    private fun relatedStockListToJson(items: List<RelatedStock>): JSONArray {
        val array = JSONArray()
        items.forEach { stock ->
            array.put(
                JSONObject()
                    .put("name", stock.name)
                    .put("code", stock.code)
            )
        }
        return array
    }

    companion object {
        const val KEY = "chat_sessions_v1"
        const val FIELD_SESSIONS = "sessions"
        const val MAX_SESSIONS = 30
        const val MAX_MESSAGES = 40
    }
}
