package com.hfad.stockapplication.state.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hfad.stockapplication.data.chat.ChatMessage
import com.hfad.stockapplication.data.chat.IndexCatalog
import com.hfad.stockapplication.data.chat.KLineBar
import com.hfad.stockapplication.data.chat.RelatedStock
import com.hfad.stockapplication.data.chat.RelatedStocks
import com.hfad.stockapplication.data.chat.StockAiRepository
import com.hfad.stockapplication.data.chat.StockQuote
import com.hfad.stockapplication.data.chat.TencentMarketRepository
import com.hfad.stockapplication.infra.DetailParams
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 任务 2 详情页状态：本页自己拉行情，并提供快捷 AI 解读。
 * 长按 K 线不在本页解读，而是回到问答输入框上方的选点卡片。
 */
class DetailStore(
    private val marketRepository: TencentMarketRepository,
    private val aiRepository: StockAiRepository,
) {
    var name: String by mutableStateOf("")
    var code: String by mutableStateOf("")
    var market: String by mutableStateOf("")
    var isIndex: Boolean by mutableStateOf(false)
    var summary: String by mutableStateOf("")
    var quote: StockQuote? by mutableStateOf(null)
    var bars: List<KLineBar> by mutableStateOf(emptyList())
    var minuteBars: List<KLineBar> by mutableStateOf(emptyList())
    var minutePrevClose: String by mutableStateOf("")
    var related: RelatedStocks? by mutableStateOf(null)
    var relatedQuotes: Map<String, StockQuote> by mutableStateOf(emptyMap())
    var indexBlurb: String by mutableStateOf("")
    var constituents: List<RelatedStock> by mutableStateOf(emptyList())
    var aiText: String by mutableStateOf("")
    var aiSending: Boolean by mutableStateOf(false)
    var darkTheme: Boolean by mutableStateOf(false)

    val symbol: String
        get() = "$market$code"

    fun loadFromParams(params: JSONObject, dark: Boolean) {
        darkTheme = dark
        code = params.optString(DetailParams.CODE).orEmpty()
        name = params.optString(DetailParams.NAME).orEmpty()
        market = params.optString(DetailParams.MARKET).orEmpty()
        isIndex = params.optInt(DetailParams.IS_INDEX) == 1
        summary = params.optString(DetailParams.SUMMARY).orEmpty()
        val industry = decodeStocks(params.optString(DetailParams.INDUSTRY).orEmpty())
        val supply = decodeStocks(params.optString(DetailParams.SUPPLY).orEmpty())
        val peers = decodeStocks(params.optString(DetailParams.PEERS).orEmpty())
        related = if (industry.isEmpty() && supply.isEmpty() && peers.isEmpty()) {
            null
        } else {
            RelatedStocks(industry, supply, peers)
        }
        if (isIndex) {
            val meta = IndexCatalog.ALL.firstOrNull { it.code == code }
            if (meta != null) {
                if (name.isBlank()) {
                    name = meta.name
                }
                if (market.isBlank()) {
                    market = meta.market
                }
                indexBlurb = meta.blurb
                constituents = meta.constituents
            }
        }
        refreshMarket(includeKline = true)
        refreshRelatedQuotes()
    }

    fun refreshLive() {
        refreshMarket(includeKline = false)
    }

    fun interpretTrend(onError: (String) -> Unit) {
        askAi(
            question = "结合最新行情，${label()}现在怎么走？先给一句话结论，再写驱动因素和主要风险。明确说明不构成投资建议。",
            extraContext = "",
            onError = onError,
        )
    }

    fun interpretRisk(onError: (String) -> Unit) {
        askAi(
            question = "${label()}当前最主要的风险有哪些？按重要性列出，不要编造，并说明不构成投资建议。",
            extraContext = "",
            onError = onError,
        )
    }

    fun interpretBuy(onError: (String) -> Unit) {
        askAi(
            question = "结合最新行情，${label()}现在适合买入吗？先给结论，再说明理由和主要风险。明确说明不构成投资建议。",
            extraContext = "",
            onError = onError,
        )
    }

    private fun askAi(question: String, extraContext: String, onError: (String) -> Unit) {
        if (aiSending) {
            return
        }
        val snapshot = quote?.toModelSnapshot().orEmpty()
        if (snapshot.isBlank() && extraContext.isBlank()) {
            onError("行情还没拉到，稍后再问")
            return
        }
        aiSending = true
        aiText = ""
        val context = listOf(snapshot, extraContext).filter { it.isNotBlank() }.joinToString("\n\n")
        aiRepository.askStockContext(
            question = question,
            history = emptyList(),
            onDelta = { delta ->
                aiText += delta
            },
            onResult = { result ->
                aiSending = false
                result.onSuccess { ask ->
                    if (ask.markdown.isNotBlank()) {
                        aiText = ask.markdown
                    }
                }.onFailure { error ->
                    onError(error.message ?: "解读失败")
                }
            },
            marketContext = context,
        )
    }

    private fun refreshMarket(includeKline: Boolean) {
        if (code.length != 6 || market.isBlank()) {
            return
        }
        if (includeKline) {
            marketRepository.fetchTargetMarket(symbol, name) { liveQuote, kline ->
                if (liveQuote != null) {
                    quote = liveQuote
                    if (liveQuote.name.isNotBlank()) {
                        name = liveQuote.name
                    }
                }
                if (kline.isNotEmpty()) {
                    bars = kline
                }
            }
        } else {
            marketRepository.fetchQuote(symbol, name) { liveQuote ->
                if (liveQuote != null) {
                    quote = liveQuote
                    if (liveQuote.name.isNotBlank()) {
                        name = liveQuote.name
                    }
                }
            }
        }
        marketRepository.fetchMinuteKline(symbol) { minutes, prec ->
            if (minutes.isNotEmpty()) {
                minuteBars = minutes
            }
            if (prec.isNotBlank()) {
                minutePrevClose = prec
            }
        }
    }

    private fun refreshRelatedQuotes() {
        val items = linkedMapOf<String, String>()
        constituents.forEach { stock ->
            if (stock.code.length == 6) {
                items[stock.code] = stock.name
            }
        }
        related?.let { groups ->
            (groups.industry + groups.supplyChain + groups.peers).forEach { stock ->
                if (stock.code.length == 6) {
                    items[stock.code] = stock.name
                }
            }
        }
        if (items.isEmpty()) {
            return
        }
        val pairs = items.map { (code, name) ->
            val prefix = com.hfad.stockapplication.data.chat.TargetStockParser.marketPrefix(code).orEmpty()
            "${prefix}$code" to name
        }.filter { it.first.length > 6 }
        marketRepository.fetchQuotes(pairs) { quotes ->
            relatedQuotes = quotes.associateBy { it.code }
        }
    }

    private fun label(): String {
        return if (code.isBlank()) name else "${name.ifBlank { "该标的" }}（$code）"
    }

    companion object {
        fun encodeStocks(list: List<RelatedStock>): String {
            return list.filter { it.code.length == 6 }.take(8)
                .joinToString(";") { "${it.name}|${it.code}" }
        }

        fun decodeStocks(raw: String): List<RelatedStock> {
            if (raw.isBlank()) {
                return emptyList()
            }
            return raw.split(";").mapNotNull { token ->
                val parts = token.split("|")
                if (parts.size >= 2 && parts[1].length == 6) {
                    RelatedStock(parts[0], parts[1])
                } else {
                    null
                }
            }
        }

        fun fromChatMessage(message: ChatMessage, code: String?): JSONObject? {
            val pick = if (code == null) {
                message.listedTargets().firstOrNull() ?: message.relatedPicks.firstOrNull()
            } else {
                message.pickByCode(code)
            } ?: return null
            val related = message.related
            return JSONObject()
                .put(DetailParams.CODE, pick.code)
                .put(DetailParams.NAME, pick.name)
                .put(DetailParams.MARKET, pick.market)
                .put(DetailParams.IS_INDEX, if (pick.isIndex) 1 else 0)
                .put(
                    DetailParams.SUMMARY,
                    com.hfad.stockapplication.data.chat.RelatedStockParser.answerExcerpt(message.body),
                )
                .put(DetailParams.INDUSTRY, encodeStocks(related?.industry.orEmpty()))
                .put(DetailParams.SUPPLY, encodeStocks(related?.supplyChain.orEmpty()))
                .put(DetailParams.PEERS, encodeStocks(related?.peers.orEmpty()))
        }

        fun fromWatchItem(
            name: String,
            code: String,
            market: String,
            isIndex: Boolean,
        ): JSONObject {
            return JSONObject()
                .put(DetailParams.CODE, code)
                .put(DetailParams.NAME, name)
                .put(DetailParams.MARKET, market)
                .put(DetailParams.IS_INDEX, if (isIndex) 1 else 0)
        }
    }
}
