package com.hfad.stockapplication.state.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hfad.stockapplication.component.drawer.DrawerItem
import com.hfad.stockapplication.data.chat.ChartAskContext
import com.hfad.stockapplication.data.chat.ChartAskHandoff
import com.hfad.stockapplication.data.chat.ChatMessage
import com.hfad.stockapplication.data.chat.ChatRepository
import com.hfad.stockapplication.data.chat.ChatSession
import com.hfad.stockapplication.data.chat.IndexCatalog
import com.hfad.stockapplication.data.chat.KLineBar
import com.hfad.stockapplication.data.chat.ListedKind
import com.hfad.stockapplication.data.chat.RelatedStock
import com.hfad.stockapplication.data.chat.RelatedStockParser
import com.hfad.stockapplication.data.chat.RelatedStocks
import com.hfad.stockapplication.data.chat.SessionTitles
import com.hfad.stockapplication.data.chat.StockAiRepository
import com.hfad.stockapplication.data.chat.StockAskResult
import com.hfad.stockapplication.data.chat.StockQuote
import com.hfad.stockapplication.data.chat.TargetStock
import com.hfad.stockapplication.data.chat.TargetStockParser
import com.hfad.stockapplication.data.chat.TencentMarketRepository
import com.hfad.stockapplication.data.chat.UserSettingsRepository
import com.hfad.stockapplication.data.chat.ThemeMode

/**
 * 股票问答页的状态与业务规则（页面只读这些字段、只调这些方法）。
 *
 * [repository] 本地会话存取；[aiRepository] 向模型提问；[marketRepository] 拉目标股行情与日 K。
 * 带 `mutableState*` 的字段一改，Compose 界面会自动刷新。
 */
class ChatStore(
    private val repository: ChatRepository,
    private val aiRepository: StockAiRepository,
    private val marketRepository: TencentMarketRepository,
    private val settingsRepository: UserSettingsRepository,
    private val builtinApiKey: String,
) {
    /** 输入框当前文案。 */
    var draft: String by mutableStateOf("")
    /** 左侧历史抽屉是否打开。 */
    var drawerVisible: Boolean by mutableStateOf(false)
    /** 抽屉列表是否为空（含搜索无结果）。 */
    var historyEmpty: Boolean by mutableStateOf(false)
    /** 抽屉里展示的历史摘要。 */
    val histories = mutableStateListOf<DrawerItem>()
    /** 顶栏标题；首条成功回复前用默认文案。 */
    var sessionTitle: String by mutableStateOf(DEFAULT_TITLE)
    /** 当前会话的全部气泡。 */
    val messages = mutableStateListOf<ChatMessage>()
    /** 与 [messages] 同步，给空态占位用。 */
    var messagesEmpty: Boolean by mutableStateOf(true)
    /** 是否已有进行中的模型请求（防连点）。 */
    var isSending: Boolean by mutableStateOf(false)
    /** 当前打开的 K 线抽屉对应的助手消息；图表组件不直接吃这个模型。 */
    var klineMessage: ChatMessage? by mutableStateOf(null)
    /** 打开抽屉后拉到的关联股 / 指数样本股行情，关掉即丢。 */
    var relatedQuotes: Map<String, StockQuote> by mutableStateOf(emptyMap())
    /** 指数详情的简要说明；正股详情为空。 */
    var klineIndexBlurb: String by mutableStateOf("")
    var klineConstituents: List<RelatedStock> by mutableStateOf(emptyList())
    /** 「行业板块」组有代码的股的日 K，用于板块折线。 */
    var industryKlines: Map<String, List<KLineBar>> by mutableStateOf(emptyMap())
    /** 详情抽屉里的当日分时，关掉即丢，不入库。 */
    var minuteBars: List<KLineBar> by mutableStateOf(emptyList())
    /** 分时昨收基准，优先用分钟线 prec。 */
    var minutePrevClose: String by mutableStateOf("")
    /** 关联股层为 true，标题显示「返回」。 */
    var klineCanPop: Boolean by mutableStateOf(false)
    /** 遮罩关掉关联股后递增，用来重建抽屉。 */
    var klineSheetEpoch: Int by mutableStateOf(0)
    /** 日 K / 分时长按带到输入框上方的选点；发送后清掉。 */
    var chartAsk: ChartAskContext? by mutableStateOf(null)
    /** 侧栏展示的本机昵称。 */
    var displayName: String by mutableStateOf(DEFAULT_DISPLAY_NAME)
    /** 未筛选的历史会话数，给账号页用。 */
    var historyCount: Int by mutableStateOf(0)
    /** 自定义 DeepSeek Key；空则用打包时的内置 Key。 */
    var customApiKey: String by mutableStateOf("")
    /** 账号 / 设置全屏页；None 表示都没开。 */
    var profilePane: ProfilePane by mutableStateOf(ProfilePane.None)
    /** 外观：跟随系统 / 浅色 / 深色。 */
    var themeMode: ThemeMode by mutableStateOf(ThemeMode.System)
    /** 当前是否用深色配色。 */
    var darkTheme: Boolean by mutableStateOf(false)

    val usingCustomApiKey: Boolean
        get() = customApiKey.isNotBlank()

    val apiKeyReady: Boolean
        get() = effectiveApiKey().isNotBlank()

    val hasBuiltinApiKey: Boolean
        get() = builtinApiKey.isNotBlank()

    private var klineFetchSeq = 0
    private var livePollSeq = 0
    private var askSeq = 0
    private var klineRoot: KlineSnapshot? = null
    private var ignoreNextDismiss = false

    /** 当前会话 id，形如 `s-1`。 */
    private var currentSessionId = newSessionId()
    private var rememberedSessionId = ""
    /** true 后不再改顶栏（已采用完整模型标题，或本轮结束后冻结占位标题）。 */
    private var titleLocked = false
    /** 消息 / 会话 id 自增序号。 */
    private var nextId = 1
    /** 抽屉搜索关键字，刷新列表时沿用。 */
    private var filterKeyword = ""
    /** 最近一次提问是否开了深度思考，失败重试用。 */
    private var lastThink = false
    /** 提问前为目标股拉到的现价，回答落地时复用，避免卡片晚一拍。 */
    private var promptQuotes: Map<String, StockQuote> = emptyMap()
    /** 流式中已发起过行情请求的代码，避免每个 delta 都打接口。 */
    private val streamQuoteAsked = mutableSetOf<String>()

    /** 页面 [created] 后调用：对齐序号、拉设置与历史，并恢复上次非空会话。 */
    fun loadInitial(systemNight: Boolean = false) {
        bumpSeqFromPersisted()
        val savedName = settingsRepository.loadDisplayName()
        displayName = savedName.ifBlank { DEFAULT_DISPLAY_NAME }
        customApiKey = settingsRepository.loadCustomApiKey()
        aiRepository.updateApiKey(effectiveApiKey())
        themeMode = settingsRepository.loadThemeMode()
        darkTheme = themeMode.isDark(systemNight)
        refreshHistories()
        restoreLastSession()
    }

    fun openDrawer() {
        drawerVisible = true
    }

    fun closeDrawer() {
        drawerVisible = false
    }

    /** 点目标股芯片或行情卡：关侧栏并打开该股 / 指数 K 线抽屉。 */
    fun openKline(message: ChatMessage, code: String? = null) {
        closeDrawer()
        klineRoot = null
        klineCanPop = false
        ignoreNextDismiss = false
        val pick = if (code == null) {
            message.listedTargets().firstOrNull() ?: message.relatedPicks.firstOrNull()
        } else {
            message.pickByCode(code)
        }
        val opened = if (pick == null) {
            message
        } else {
            message.copy(
                targetName = pick.name,
                targetCode = pick.code,
                quote = message.quoteFor(pick.code),
                kline = message.klineFor(pick.code),
            )
        }
        klineMessage = opened
        clearMinute()
        if (pick?.isIndex == true) {
            val meta = IndexCatalog.bySymbol(pick.symbol)
            klineIndexBlurb = meta?.blurb.orEmpty()
            klineConstituents = meta?.constituents.orEmpty()
            fetchIndexConstituents(klineConstituents)
        } else {
            klineIndexBlurb = ""
            klineConstituents = emptyList()
            fetchRelatedMarkets(message.related)
        }
        val openedCode = opened.targetCode?.takeIf { it.length == 6 }
        val symbol = openedCode?.let { code ->
            pick?.symbol ?: opened.symbolFor(code)
        }
        if (openedCode != null && symbol != null && (opened.quote == null || opened.kline.isEmpty())) {
            val seq = klineFetchSeq
            marketRepository.fetchTargetMarket(symbol, opened.targetName.orEmpty()) { quote, kline ->
                if (seq != klineFetchSeq) {
                    return@fetchTargetMarket
                }
                val live = klineMessage ?: return@fetchTargetMarket
                if (live.id != message.id || live.targetCode != openedCode) {
                    return@fetchTargetMarket
                }
                klineMessage = live.copy(
                    quote = quote ?: live.quote,
                    kline = kline.ifEmpty { live.kline },
                )
            }
        }
    }

    /** 点关联股芯片：同一抽屉换成该股行情；先压住目标股快照。 */
    fun openRelated(name: String, code: String) {
        val symbol = TargetStockParser.symbolOf(code) ?: return
        val current = klineMessage ?: return
        if (klineRoot == null) {
            klineRoot = KlineSnapshot(
                message = current,
                relatedQuotes = relatedQuotes,
                industryKlines = industryKlines,
                indexBlurb = klineIndexBlurb,
                constituents = klineConstituents,
            )
            klineCanPop = true
        }
        val seq = ++klineFetchSeq
        livePollSeq += 1
        relatedQuotes = emptyMap()
        industryKlines = emptyMap()
        clearMinute()
        klineMessage = ChatMessage(
            id = current.id,
            fromUser = false,
            body = "",
            related = null,
            targetName = name,
            targetCode = code,
        )
        marketRepository.fetchTargetMarket(symbol, name) { quote, kline ->
            if (seq != klineFetchSeq) {
                return@fetchTargetMarket
            }
            klineMessage = ChatMessage(
                id = current.id,
                fromUser = false,
                body = "",
                quote = quote,
                kline = kline,
                related = null,
                targetName = name,
                targetCode = code,
            )
        }
    }

    /** 标题「返回 / 关闭」：有栈则回到目标股，否则关掉抽屉。 */
    fun closeKlineLayer() {
        if (klineRoot != null) {
            popRelated(remount = false)
        } else {
            closeKline()
        }
    }

    /**
     * 遮罩 / 动画结束的 dismiss。弹出关联股后忽略紧接着的一次，避免掉回对话。
     */
    fun dismissKlineSheet() {
        if (ignoreNextDismiss) {
            ignoreNextDismiss = false
            return
        }
        if (klineRoot != null) {
            popRelated(remount = true)
        } else {
            closeKline()
        }
    }

    /** 幂等；新开对话 / 切历史走这里，栈一并清掉。 */
    fun closeKline() {
        klineFetchSeq += 1
        livePollSeq += 1
        klineRoot = null
        klineCanPop = false
        ignoreNextDismiss = false
        klineMessage = null
        relatedQuotes = emptyMap()
        industryKlines = emptyMap()
        klineIndexBlurb = ""
        klineConstituents = emptyList()
        clearMinute()
    }

    /** 详情页「问 AI」：关掉抽屉，按当前标的发一句。 */
    fun askAboutOpened(onError: (String) -> Unit) {
        val live = klineMessage ?: return
        val name = live.targetName ?: live.quote?.name.orEmpty()
        val code = live.targetCode?.takeIf { it.length == 6 } ?: return
        if (name.isBlank() || isSending) {
            return
        }
        val question = "结合最新行情，${name}（${code}）现在怎么看？先给结论。"
        closeKline()
        sendQuestion(question, onError = onError)
    }

    /** 长按日 K / 分时某点：关掉整个 K 线栈，选点挂到输入框上方。 */
    fun askFromChart(context: ChartAskContext) {
        chartAsk = context
        closeKline()
    }

    /** 详情页长按带回的选点；没有挂起则不动。 */
    fun takePendingChartAsk() {
        ChartAskHandoff.take()?.let { askFromChart(it) }
    }

    fun clearChartAsk() {
        chartAsk = null
    }

    /** 当前对话或详情里有日 K 可引用。 */
    val canQuoteLatest: Boolean
        get() = sourceForQuoteLatest() != null

    /** 最近一轮助手回复里有带代码的关联股。 */
    val canComparePeers: Boolean
        get() = sourceForCompare() != null

    /** 把最新一根日 K 挂到输入框上方卡片，并关掉详情抽屉。 */
    fun quoteLatest(): Boolean {
        val source = sourceForQuoteLatest() ?: return false
        val bar = source.kline.lastOrNull() ?: return false
        val code = source.targetCode?.takeIf { it.length == 6 } ?: return false
        val name = source.targetName ?: source.quote?.name.orEmpty()
        askFromChart(
            ChartAskContext(
                kind = ChartAskContext.Kind.Daily,
                name = name,
                code = code,
                bar = bar,
                market = source.quote?.market
                    ?: source.pickByCode(code)?.market.orEmpty(),
            )
        )
        return true
    }

    /** 同业对比的提问文案；没有关联股时返回 null。 */
    fun comparePeersQuestion(): String? {
        val source = sourceForCompare() ?: return null
        val related = source.related ?: return null
        val peers = related.peers.filter { it.code.length == 6 }.ifEmpty { related.allCoded() }
        if (peers.isEmpty()) {
            return null
        }
        val target = (source.targetName ?: source.quote?.name.orEmpty()).ifBlank { "它" }
        val list = peers.take(6).joinToString("、") { stock ->
            stock.name.trim().ifBlank { stock.code }
        }
        return "${target}跟同行业比怎么样？比如$list"
    }

    private fun sourceForQuoteLatest(): ChatMessage? {
        val sheet = klineMessage
        if (sheet != null && sheet.kline.isNotEmpty() && sheet.targetCode?.length == 6) {
            return sheet
        }
        return messages.lastOrNull { message ->
            !message.fromUser && message.kline.isNotEmpty() && message.targetCode?.length == 6
        }
    }

    private fun sourceForCompare(): ChatMessage? {
        val sheet = klineMessage
        val sheetRelated = sheet?.related
        if (sheetRelated != null && sheetRelated.allCoded().isNotEmpty()) {
            return sheet
        }
        return messages.lastOrNull { message ->
            !message.fromUser && message.related?.allCoded()?.isNotEmpty() == true
        }
    }

    /**
     * 详情可见时轮询现价和分时。只改抽屉上的 [klineMessage]，不写进会话气泡。
     */
    fun refreshLiveMarket() {
        val current = klineMessage ?: return
        val code = current.targetCode?.takeIf { it.length == 6 } ?: return
        val symbol = current.symbolFor(code) ?: return
        val name = current.targetName ?: current.quote?.name.orEmpty()
        val seq = livePollSeq
        marketRepository.fetchQuote(symbol, name) { quote ->
            if (seq != livePollSeq) {
                return@fetchQuote
            }
            val live = klineMessage ?: return@fetchQuote
            if (live.targetCode != code || quote == null) {
                return@fetchQuote
            }
            klineMessage = live.copy(quote = quote)
        }
        marketRepository.fetchMinuteKline(symbol) { bars, prec ->
            if (seq != livePollSeq) {
                return@fetchMinuteKline
            }
            if (klineMessage?.targetCode != code) {
                return@fetchMinuteKline
            }
            if (bars.isNotEmpty()) {
                minuteBars = bars
            }
            if (prec.isNotBlank()) {
                minutePrevClose = prec
            }
        }
    }

    private fun clearMinute() {
        minuteBars = emptyList()
        minutePrevClose = ""
    }

    /** 输入框每次改字都走到这里（受控输入）。 */
    fun updateDraft(text: String) {
        draft = text
    }

    /** 按标题关键字过滤抽屉；空关键字等于展示全部。 */
    fun applyFilter(keyword: String) {
        filterKeyword = keyword
        val filtered = repository.searchHistories(keyword)
        histories.clear()
        histories.addAll(filtered.map { DrawerItem(it.id, it.title) })
        historyEmpty = filtered.isEmpty()
    }

    /** 点顶栏「+」：打断进行中的请求，先存当前会话，再开新局。 */
    fun startNewChat() {
        abortAsk()
        persistCurrentIfNeeded()
        resetCurrentSession()
        refreshHistories()
        closeKline()
        clearChartAsk()
    }

    /** 点抽屉某条历史：先存当前，再把该会话消息装回列表。 */
    fun openSession(id: String) {
        if (id == currentSessionId) {
            closeDrawer()
            return
        }
        abortAsk()
        persistCurrentIfNeeded()
        val session = repository.loadSession(id) ?: return
        applySession(session)
        closeDrawer()
        closeKline()
        clearChartAsk()
    }

    fun deleteHistory(id: String) {
        repository.deleteSession(id)
        if (settingsRepository.loadLastSessionId() == id) {
            settingsRepository.saveLastSessionId("")
        }
        if (id == currentSessionId) {
            abortAsk()
            resetCurrentSession()
            closeKline()
            clearChartAsk()
        }
        refreshHistories()
    }

    fun openAccount() {
        closeDrawer()
        closeKline()
        profilePane = ProfilePane.Account
    }

    fun openSettings() {
        closeDrawer()
        closeKline()
        profilePane = ProfilePane.Settings
    }

    fun closeProfilePane() {
        profilePane = ProfilePane.None
    }

    /** 空昵称会回落到默认名。 */
    fun saveDisplayName(name: String): String {
        val next = name.trim().ifBlank { DEFAULT_DISPLAY_NAME }
        displayName = next
        settingsRepository.saveDisplayName(if (next == DEFAULT_DISPLAY_NAME) "" else next)
        return next
    }

    fun saveCustomApiKey(key: String): Boolean {
        val next = key.trim()
        if (next.isEmpty()) {
            return false
        }
        customApiKey = next
        settingsRepository.saveCustomApiKey(next)
        aiRepository.updateApiKey(effectiveApiKey())
        return true
    }

    fun clearCustomApiKey() {
        customApiKey = ""
        settingsRepository.saveCustomApiKey("")
        aiRepository.updateApiKey(effectiveApiKey())
    }

    fun apiKeyStatus(): String {
        val key = effectiveApiKey()
        if (key.isBlank()) {
            return "未配置"
        }
        val tail = key.takeLast(4)
        return if (customApiKey.isNotBlank()) {
            "自定义 Key · ••••$tail"
        } else {
            "内置 Key · 已配置"
        }
    }

    fun clearAllHistories() {
        abortAsk()
        repository.clearAll()
        settingsRepository.saveLastSessionId("")
        resetCurrentSession()
        closeKline()
        clearChartAsk()
        refreshHistories()
    }

    fun setThemeMode(mode: ThemeMode, systemNight: Boolean) {
        themeMode = mode
        settingsRepository.saveThemeMode(mode)
        darkTheme = mode.isDark(systemNight)
    }

    fun syncSystemNight(systemNight: Boolean) {
        darkTheme = themeMode.isDark(systemNight)
    }

    fun persistOnExit() {
        persistCurrentIfNeeded()
    }

    private fun effectiveApiKey(): String {
        return customApiKey.ifBlank { builtinApiKey }
    }

    /** 停止当前生成；若还在「正在分析…」则去掉该气泡。 */
    fun stopSending() {
        if (!isSending) {
            return
        }
        abortAsk()
        val last = messages.lastOrNull { !it.fromUser }
        if (last != null && (last.body.isBlank() || last.body == LOADING_TEXT)) {
            removeMessage(last.id)
        }
        persistCurrentIfNeeded()
    }

    private fun abortAsk() {
        askSeq += 1
        if (isSending) {
            aiRepository.cancel()
            isSending = false
        }
    }

    /**
     * 取出并清空输入框，供发送使用。
     *
     * @return 待发送文案；正在发送或空白输入返回 null
     */
    fun consumeDraftForSend(): String? {
        if (isSending) {
            return null
        }
        val text = draft.trim()
        if (text.isEmpty()) {
            return null
        }
        draft = ""
        return text
    }

    /**
     * 发起一轮问答：先插入用户气泡和「正在分析…」，再交给 [aiRepository]。
     * 有目标股时先拉现价，快照只进模型、不进用户气泡。
     *
     * 先插入「思考中」，正文开始流出后再流式刷新；结束时写标题、补卡片并落盘。
     */
    fun sendQuestion(
        question: String,
        think: Boolean = false,
        prependUser: Boolean = true,
        onError: (String) -> Unit,
    ) {
        if (isSending) {
            return
        }
        isSending = true
        lastThink = think
        promptQuotes = emptyMap()
        streamQuoteAsked.clear()
        val seq = ++askSeq
        val historySource = if (prependUser) {
            messages.toList()
        } else {
            messages.dropLast(1).toList()
        }
        val history = historySource.takeLast(HISTORY_LIMIT).map { item ->
            if (item.fromUser) {
                item
            } else {
                item.copy(
                    body = RelatedStockParser.mergeForHistory(
                        item.body,
                        item.related,
                        item.targetName,
                        item.targetCode,
                        item.listedTargets(),
                        item.relatedPicks,
                    )
                )
            }
        }
        val userId = allocId()
        val assistantId = allocId()
        if (prependUser) {
            messages.add(ChatMessage(userId, fromUser = true, body = question))
        }
        messages.add(ChatMessage(assistantId, fromUser = false, body = LOADING_TEXT))
        if (!titleLocked) {
            sessionTitle = SessionTitles.fromQuestion(question, DEFAULT_TITLE)
        }
        syncMessagesEmpty()
        persistCurrentIfNeeded()
        val chartSnapshot = chartAsk?.toModelSnapshot().orEmpty()
        val askModel: (String) -> Unit = { liveSnapshot ->
            val marketContext = listOf(chartSnapshot, liveSnapshot)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            aiRepository.askStockContext(
                question = question,
                history = history,
                onDelta = { text ->
                    if (seq != askSeq) {
                        return@askStockContext
                    }
                    applyAssistantStream(assistantId, text)
                },
                onResult = { result ->
                    if (seq != askSeq) {
                        return@askStockContext
                    }
                    result.fold(
                        onSuccess = { ask ->
                            finishAsk(seq, assistantId, ask)
                        },
                        onFailure = { error ->
                            isSending = false
                            val hint = error.message ?: "请求失败"
                            val current = messages.firstOrNull { it.id == assistantId }?.body.orEmpty()
                            val index = messages.indexOfFirst { it.id == assistantId }
                            if (index >= 0) {
                                val keep = current.isNotBlank() && current != LOADING_TEXT
                                messages[index] = messages[index].copy(
                                    body = if (keep) current else hint,
                                    failed = true,
                                )
                            }
                            persistCurrentIfNeeded()
                            onError(hint)
                        }
                    )
                },
                marketContext = marketContext,
                think = think,
            )
        }
        val targets = resolvePromptTargets(question)
        chartAsk = null
        if (targets.isEmpty()) {
            askModel("")
            return
        }
        val items = targets.map { it.symbol to it.name }
        marketRepository.fetchQuotes(items) { quotes ->
            if (seq != askSeq) {
                return@fetchQuotes
            }
            quotes.forEach { quote ->
                applyLiveQuote(quote.code, quote)
            }
            promptQuotes = quotes.associateBy { it.code }
            seedPromptTargets(assistantId, targets, quotes)
            val byCode = quotes.associateBy { it.code }
            val snapshots = targets.mapNotNull { item ->
                (byCode[item.code] ?: item.cached)?.toModelSnapshot()
            }
            askModel(snapshots.joinToString("\n\n"))
        }
    }

    /** 点失败气泡「重试」：保留原用户气泡，只再走一轮助手请求。 */
    fun retryFailed(assistantId: String, onError: (String) -> Unit) {
        if (isSending) {
            return
        }
        val index = messages.indexOfFirst { it.id == assistantId }
        if (index <= 0) {
            return
        }
        val assistant = messages[index]
        val user = messages[index - 1]
        if (assistant.fromUser || !assistant.failed || !user.fromUser) {
            return
        }
        messages.removeAt(index)
        sendQuestion(user.body, lastThink, prependUser = false, onError)
    }

    /**
     * 本轮问题里的代码 / 指数别名优先（可多只）；K 线抽屉打开时用当前标的；
     * 板块概念问不沿用上一轮龙头。快照只进模型请求，不写进用户气泡。
     */
    private fun resolvePromptTargets(question: String): List<PromptTarget> {
        val hinted = TargetStockParser.listedInQuestion(question)
        val known = currentPromptTargets().associateBy { it.code }
        if (hinted.isNotEmpty()) {
            return hinted.map { stock ->
                val prev = known[stock.code]?.takeIf { it.symbol == stock.symbol }
                PromptTarget(
                    symbol = stock.symbol,
                    name = stock.name.ifBlank { prev?.name.orEmpty() },
                    code = stock.code,
                    cached = prev?.cached,
                    kind = stock.kind,
                )
            }
        }
        if (TargetStockParser.isBoardOrConceptQuestion(question)) {
            return emptyList()
        }
        return currentPromptTargets()
    }

    private fun currentPromptTargets(): List<PromptTarget> {
        val asked = chartAsk
        val askedCode = asked?.code?.takeIf { it.length == 6 }
        if (asked != null && askedCode != null) {
            val symbol = TargetStockParser.symbolOf(
                askedCode,
                asked.name,
                asked.market,
            ) ?: return emptyList()
            val kind = if (IndexCatalog.bySymbol(symbol) != null) {
                ListedKind.Index
            } else {
                ListedKind.Stock
            }
            return listOf(
                PromptTarget(
                    symbol = symbol,
                    name = asked.name,
                    code = askedCode,
                    cached = null,
                    kind = kind,
                )
            )
        }
        val sheet = klineMessage
        val sheetCode = sheet?.targetCode?.takeIf { it.length == 6 }
        if (sheetCode != null) {
            val symbol = sheet.symbolFor(sheetCode) ?: return emptyList()
            val pick = sheet.pickByCode(sheetCode)
            return listOf(
                PromptTarget(
                    symbol = symbol,
                    name = sheet.targetName ?: sheet.quote?.name.orEmpty(),
                    code = sheetCode,
                    cached = sheet.quote,
                    kind = pick?.kind ?: ListedKind.Stock,
                )
            )
        }
        val last = messages.lastOrNull { message ->
            !message.fromUser && message.listedTargets().isNotEmpty()
        } ?: return emptyList()
        return last.listedTargets().map { stock ->
            PromptTarget(
                symbol = stock.symbol,
                name = stock.name,
                code = stock.code,
                cached = last.quoteFor(stock.code),
                kind = stock.kind,
            )
        }
    }

    private fun applyLiveQuote(code: String, quote: StockQuote) {
        val live = klineMessage ?: return
        if (live.targetCode != code) {
            return
        }
        klineMessage = live.copy(quote = quote)
    }

    /**
     * 流式结束后立刻落正文；缺的行情后台补，不再挡住 Markdown 和小标题。
     */
    private fun finishAsk(seq: Int, assistantId: String, ask: StockAskResult) {
        val parsed = TargetStockParser.parseAll(ask.markdown)
        val picks = TargetStockParser.parseRelatedPicks(ask.markdown)
        val index = messages.indexOfFirst { it.id == assistantId }
        val already = if (index >= 0) messages[index].targetQuotes else emptyMap()
        val needed = (parsed + picks).distinctBy { it.code }
        val ready = needed.mapNotNull { stock -> already[stock.code] ?: promptQuotes[stock.code] }
        val missing = needed.filter { stock ->
            already[stock.code] == null && promptQuotes[stock.code] == null
        }
        isSending = false
        applyAskResult(assistantId, ask, ready)
        if (missing.isEmpty()) {
            return
        }
        marketRepository.fetchQuotes(missing.map { it.symbol to it.name }) { extra ->
            if (seq != askSeq) {
                return@fetchQuotes
            }
            attachTargetQuotes(assistantId, parsed, extra)
        }
    }

    /** 用模型最终 Markdown 覆盖助手气泡。无目标股时不留芯片、不拉行情。 */
    private fun applyAskResult(
        assistantId: String,
        ask: StockAskResult,
        quotes: List<StockQuote>,
    ) {
        adoptSessionTitle(ask.markdown, requireCompleteLine = false)
        if (!titleLocked) {
            titleLocked = true
        }
        val index = messages.indexOfFirst { it.id == assistantId }
        val parsed = TargetStockParser.parseAll(ask.markdown)
        val picks = TargetStockParser.parseRelatedPicks(ask.markdown)
        val related = RelatedStockParser.parse(ask.markdown)
        val current = if (index >= 0) messages[index] else null
        val source = parsed.ifEmpty { current?.listedTargets().orEmpty() }
        val useRelated = source.none { it.isIndex } && source.isNotEmpty()
        if (index >= 0 && current != null) {
            messages[index] = current
                .bindTargets(source, quotes, promptQuotes)
                .copy(
                    body = RelatedStockParser.displayMarkdown(ask.markdown),
                    related = if (useRelated) {
                        related.takeUnless { it.isEmpty() } ?: current.related
                    } else {
                        null
                    },
                    relatedPicks = picks.ifEmpty { current.relatedPicks },
                )
        }
        persistCurrentIfNeeded()
        refreshHistories()
        if (source.isNotEmpty()) {
            fetchTargetKlines(assistantId, source)
        }
        if (picks.isNotEmpty()) {
            fetchTargetKlines(assistantId, picks)
        }
        if (useRelated && !related.isEmpty()) {
            verifyRelatedStocks(assistantId, related)
        }
    }

    private fun verifyRelatedStocks(assistantId: String, related: RelatedStocks) {
        val items = related.allCoded().mapNotNull { stock ->
            val symbol = TargetStockParser.symbolOf(stock.code) ?: return@mapNotNull null
            symbol to stock.name
        }
        if (items.isEmpty()) {
            attachRelated(assistantId, RelatedStocks(), emptyMap())
            return
        }
        marketRepository.fetchQuotes(items) { quotes ->
            val byCode = quotes.associateBy { it.code }
            val kept = related.keepListed(byCode)
            attachRelated(assistantId, kept, byCode)
        }
    }

    private fun attachRelated(
        id: String,
        related: RelatedStocks,
        quotes: Map<String, StockQuote>,
    ) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) {
            return
        }
        val updated = messages[index].copy(related = related)
        messages[index] = updated
        persistCurrentIfNeeded()
        if (klineMessage?.id == id && klineRoot == null) {
            klineMessage = updated
            relatedQuotes = quotes.filterKeys { code ->
                related.allCoded().any { it.code == code }
            }
        }
    }

    private fun popRelated(remount: Boolean) {
        val snap = klineRoot ?: return
        klineRoot = null
        klineCanPop = false
        klineFetchSeq += 1
        livePollSeq += 1
        val live = messages.firstOrNull { it.id == snap.message.id } ?: snap.message
        klineMessage = live
        relatedQuotes = snap.relatedQuotes
        industryKlines = snap.industryKlines
        klineIndexBlurb = snap.indexBlurb
        klineConstituents = snap.constituents
        clearMinute()
        if (remount) {
            ignoreNextDismiss = true
            klineSheetEpoch += 1
        }
        if (relatedQuotes.isEmpty() && snap.message.related != null && !snap.message.related.isEmpty()) {
            fetchRelatedMarkets(snap.message.related)
        }
    }

    val showRelated: Boolean
        get() {
            if (klineRoot != null) {
                return false
            }
            val live = klineMessage ?: return false
            val pick = live.targetCode?.let { live.pickByCode(it) }
            if (pick?.isIndex == true) {
                return false
            }
            val related = live.related
            return related != null && !related.isEmpty()
        }

    private fun fetchRelatedMarkets(related: RelatedStocks?) {
        val seq = ++klineFetchSeq
        relatedQuotes = emptyMap()
        industryKlines = emptyMap()
        if (related == null || related.isEmpty()) {
            return
        }
        val coded = related.codedBalanced(perGroup = 4)
        val quoteItems = coded.mapNotNull { stock ->
            val symbol = TargetStockParser.symbolOf(stock.code) ?: return@mapNotNull null
            symbol to stock.name
        }
        marketRepository.fetchQuotes(quoteItems) { quotes ->
            if (seq != klineFetchSeq) {
                return@fetchQuotes
            }
            relatedQuotes = quotes.associateBy { it.code }
        }
        val industrySymbols = related.industry
            .filter { it.code.length == 6 }
            .mapNotNull { TargetStockParser.symbolOf(it.code) }
            .distinct()
            .take(RELATED_QUOTE_LIMIT)
        marketRepository.fetchDailyKlines(industrySymbols) { klines ->
            if (seq != klineFetchSeq) {
                return@fetchDailyKlines
            }
            industryKlines = klines.filterValues { it.isNotEmpty() }
        }
    }

    private fun fetchIndexConstituents(constituents: List<RelatedStock>) {
        val seq = ++klineFetchSeq
        relatedQuotes = emptyMap()
        industryKlines = emptyMap()
        if (constituents.isEmpty()) {
            return
        }
        val items = constituents.mapNotNull { stock ->
            val symbol = TargetStockParser.symbolOf(stock.code, stock.name) ?: return@mapNotNull null
            symbol to stock.name
        }
        marketRepository.fetchQuotes(items) { quotes ->
            if (seq != klineFetchSeq) {
                return@fetchQuotes
            }
            relatedQuotes = quotes.associateBy { it.code }
        }
    }

    /** 卡片展示后再补日 K 做走势，失败静默。 */
    private fun fetchTargetKlines(assistantId: String, targets: List<TargetStock>) {
        if (targets.isEmpty()) {
            return
        }
        val symbols = targets.map { it.symbol }
        marketRepository.fetchDailyKlines(symbols) { klines ->
            val byCode = klines
                .filterValues { it.isNotEmpty() }
                .mapKeys { entry ->
                    entry.key.removePrefix("sh").removePrefix("sz")
                }
            if (byCode.isNotEmpty()) {
                attachTargetKlines(assistantId, targets, byCode)
                persistCurrentIfNeeded()
            }
        }
    }

    private fun attachTargetKlines(
        id: String,
        targets: List<TargetStock>,
        klines: Map<String, List<KLineBar>>,
    ) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) {
            return
        }
        val current = messages[index]
        val merged = current.targetKlines.toMutableMap()
        klines.forEach { (code, bars) ->
            merged[code] = bars
        }
        val primary = targets.firstOrNull()?.code
        val primaryKline = primary?.let { merged[it] } ?: current.kline
        val updated = current.copy(
            targetKlines = merged,
            kline = primaryKline,
        )
        messages[index] = updated
        if (klineMessage?.id == id && klineRoot == null) {
            val liveCode = klineMessage?.targetCode
            klineMessage = updated.copy(
                targetName = klineMessage?.targetName ?: updated.targetName,
                targetCode = liveCode ?: updated.targetCode,
                quote = liveCode?.let { updated.quoteFor(it) } ?: updated.quote,
                kline = liveCode?.let { updated.klineFor(it) } ?: primaryKline,
            )
        }
    }

    /** 当前列表非空才写入本地，空会话不占一条历史。 */
    private fun persistCurrentIfNeeded() {
        if (messages.isEmpty()) {
            return
        }
        repository.saveSession(
            ChatSession(
                id = currentSessionId,
                title = sessionTitle,
                messages = messages.toList(),
                updatedAt = 0L,
            )
        )
        rememberCurrentSession()
    }

    private fun restoreLastSession() {
        val savedId = settingsRepository.loadLastSessionId()
        val session = savedId.takeIf { it.isNotBlank() }?.let { repository.loadSession(it) }
            ?: repository.loadHistories().firstOrNull()?.let { repository.loadSession(it.id) }
        if (session != null && session.messages.isNotEmpty()) {
            applySession(session)
        } else {
            resetCurrentSession()
        }
    }

    private fun applySession(session: ChatSession) {
        currentSessionId = session.id
        sessionTitle = SessionTitles.orDefault(session.title.ifBlank { DEFAULT_TITLE }, DEFAULT_TITLE)
        titleLocked = session.messages.isNotEmpty()
        adoptIds(session.messages)
        messages.clear()
        messages.addAll(session.messages)
        syncMessagesEmpty()
        rememberCurrentSession()
    }

    private fun rememberCurrentSession() {
        if (messages.isEmpty()) {
            return
        }
        if (rememberedSessionId == currentSessionId) {
            return
        }
        rememberedSessionId = currentSessionId
        settingsRepository.saveLastSessionId(currentSessionId)
    }

    /** 换一个新 id，清空标题锁定和气泡。 */
    private fun resetCurrentSession() {
        currentSessionId = newSessionId()
        sessionTitle = DEFAULT_TITLE
        titleLocked = false
        messages.clear()
        syncMessagesEmpty()
        rememberedSessionId = ""
    }

    /** 按当前搜索关键字重拉抽屉（存盘或新开局后保持筛选）。 */
    private fun refreshHistories() {
        applyFilter(filterKeyword)
        historyCount = repository.loadHistories().size
    }

    /** 流式过程中裁剪展示正文，并尽早记下目标股名称。 */
    private fun applyAssistantStream(id: String, raw: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) {
            return
        }
        val current = messages[index]
        val displayed = RelatedStockParser.displayMarkdown(raw)
        val nextBody = displayed.ifBlank { LOADING_TEXT }
        val modelTargets = TargetStockParser.parseAll(raw)
        val nextTargets = modelTargets.ifEmpty { current.targets }
        val modelPicks = TargetStockParser.parseRelatedPicks(raw)
        val nextPicks = modelPicks.ifEmpty { current.relatedPicks }
        if (nextBody == current.body && nextTargets == current.targets && nextPicks == current.relatedPicks) {
            adoptSessionTitle(raw, requireCompleteLine = true)
            return
        }
        messages[index] = current
            .bindTargets(nextTargets, fallbackQuotes = promptQuotes)
            .copy(body = nextBody, relatedPicks = nextPicks)
        if ((nextTargets.isNotEmpty() || nextPicks.isNotEmpty()) && nextBody != LOADING_TEXT) {
            prefetchMissingQuotes(id, nextTargets + nextPicks)
        }
        adoptSessionTitle(raw, requireCompleteLine = true)
    }

    /** 提问阶段已解析出的目标股先挂上，等正文开始流出就能画卡片。 */
    private fun seedPromptTargets(
        id: String,
        items: List<PromptTarget>,
        quotes: List<StockQuote>,
    ) {
        val stocks = items.map { item ->
            val liveName = quotes.firstOrNull { it.code == item.code }?.name?.trim()?.ifBlank { null }
            TargetStock(
                name = liveName ?: item.name.ifBlank { item.code },
                code = item.code,
                market = item.symbol.take(2).ifBlank { "sz" },
                kind = item.kind,
            )
        }
        if (stocks.isEmpty()) {
            return
        }
        attachTargetQuotes(id, stocks, quotes)
    }

    private fun prefetchMissingQuotes(id: String, targets: List<TargetStock>) {
        val have = messages.firstOrNull { it.id == id }?.targetQuotes ?: emptyMap()
        val missing = targets.filter { stock ->
            have[stock.code] == null &&
                promptQuotes[stock.code] == null &&
                stock.code !in streamQuoteAsked
        }
        if (missing.isEmpty()) {
            return
        }
        streamQuoteAsked.addAll(missing.map { it.code })
        marketRepository.fetchQuotes(missing.map { it.symbol to it.name }) { quotes ->
            attachTargetQuotes(id, targets, quotes)
        }
    }

    private fun attachTargetQuotes(
        id: String,
        targets: List<TargetStock>,
        quotes: List<StockQuote>,
    ) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) {
            return
        }
        val current = messages[index]
        messages[index] = current.bindTargets(targets, quotes)
    }

    /** 一级标题写完整后再上顶栏，避免流式半截；一旦采用就锁定整场会话。 */
    private fun adoptSessionTitle(markdown: String, requireCompleteLine: Boolean) {
        if (titleLocked) {
            return
        }
        val heading = SessionTitles.fromHeading(markdown, requireCompleteLine)
        if (heading == null) {
            return
        }
        sessionTitle = heading
        titleLocked = true
        persistCurrentIfNeeded()
        refreshHistories()
    }

    private fun removeMessage(id: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            messages.removeAt(index)
        }
        syncMessagesEmpty()
    }

    private fun syncMessagesEmpty() {
        messagesEmpty = messages.isEmpty()
    }

    /** 避免重启后新会话 id 和本地已有的 `s-N` 撞号。 */
    private fun bumpSeqFromPersisted() {
        val max = repository.loadHistories()
            .mapNotNull { it.id.removePrefix("s-").toIntOrNull() }
            .maxOrNull() ?: 0
        if (max >= nextId) {
            nextId = max + 1
        }
    }

    /** 打开旧会话后，后续新消息 id 从已有最大 id 接着编。 */
    private fun adoptIds(sessionMessages: List<ChatMessage>) {
        val max = sessionMessages.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0
        if (max >= nextId) {
            nextId = max + 1
        }
    }

    private fun allocId(): String {
        val id = nextId
        nextId += 1
        return id.toString()
    }

    private fun newSessionId(): String {
        return "s-${allocId()}"
    }

    private data class KlineSnapshot(
        val message: ChatMessage,
        val relatedQuotes: Map<String, StockQuote>,
        val industryKlines: Map<String, List<KLineBar>>,
        val indexBlurb: String = "",
        val constituents: List<RelatedStock> = emptyList(),
    )

    private data class PromptTarget(
        val symbol: String,
        val name: String,
        val code: String,
        val cached: StockQuote?,
        val kind: ListedKind = ListedKind.Stock,
    )

    companion object {
        const val DEFAULT_TITLE = "AI 投研助手"
        const val DEFAULT_DISPLAY_NAME = "北不选狙"
        const val LOADING_TEXT = "正在分析…"
        /** 带给模型的最近消息条数上限（含用户与助手）。 */
        const val HISTORY_LIMIT = 8
        const val RELATED_QUOTE_LIMIT = 6
    }
}

enum class ProfilePane {
    None,
    Account,
    Settings,
}
