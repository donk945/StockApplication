package com.hfad.stockapplication.page.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hfad.stockapplication.component.chart.StockKlineSheet
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.component.theme.ProvideChatColors
import com.hfad.stockapplication.data.chat.DeepSeekChatRepository
import com.hfad.stockapplication.data.chat.LocalChatRepository
import com.hfad.stockapplication.data.chat.RelatedStockParser
import com.hfad.stockapplication.data.chat.TencentMarketRepository
import com.hfad.stockapplication.data.chat.UserSettingsRepository
import com.hfad.stockapplication.debug.AgentDebugLog
import com.hfad.stockapplication.infra.BaseComposePager
import com.hfad.stockapplication.infra.SseModule
import com.hfad.stockapplication.infra.bridgeModule
import com.hfad.stockapplication.state.chat.ChatStore
import com.hfad.stockapplication.state.chat.ProfilePane
import com.tencent.kuikly.compose.BackHandler
import kotlinx.coroutines.delay
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clipToBounds
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 股票问答主页面（路由名：stock_chat）。
 *
 * Compose + 官方 kuiklybase:markdown 渲染助手回复。
 */
@Page("stock_chat", supportInLocal = true)
internal class StockChatPage : BaseComposePager() {

    private lateinit var store: ChatStore

    override fun willInit() {
        super.willInit()
        setContent {
            ChatScreen()
        }
    }

    override fun created() {
        super.created()
        val apiKey = pagerData.params.optString(KEY_DEEPSEEK_API_KEY).orEmpty()
        val sp = acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
        val historyRepo = LocalChatRepository(sp)
        val network = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
        val sse = acquireModule<SseModule>(SseModule.MODULE_NAME)
        store = ChatStore(
            repository = historyRepo,
            aiRepository = DeepSeekChatRepository(network, sse, apiKey),
            marketRepository = TencentMarketRepository(network),
            settingsRepository = UserSettingsRepository(sp),
            builtinApiKey = apiKey,
        )
        store.loadInitial(systemNight = isNightMode())
        // #region agent log
        AgentDebugLog.sink = { json -> bridgeModule.log(json) }
        // #endregion
        if (!store.apiKeyReady) {
            bridgeModule.toast("未配置 API Key，可在设置里填写")
        }
    }

    override fun themeDidChanged(data: JSONObject) {
        super.themeDidChanged(data)
        if (::store.isInitialized) {
            store.syncSystemNight(isNightMode())
        }
    }

    override fun pageWillDestroy() {
        if (::store.isInitialized) {
            store.persistOnExit()
        }
        super.pageWillDestroy()
    }

    @Composable
    private fun ChatScreen() {
        var keyboardHeight by remember { mutableStateOf(0f) }
        val listState = rememberLazyListState()
        val bottomInset = systemBottomInset()

        ProvideChatColors(dark = store.darkTheme) {
            // #region agent log
            LaunchedEffect(store.messagesEmpty, store.isSending, keyboardHeight) {
                AgentDebugLog.emit(
                    "D",
                    "StockChatPage.ChatScreen",
                    "branch",
                    mapOf(
                        "empty" to store.messagesEmpty.toString(),
                        "sending" to store.isSending.toString(),
                        "kb" to keyboardHeight.toString(),
                        "inset" to bottomInset.toString(),
                        "pageH" to pagerData.pageViewHeight.toString(),
                        "msgs" to store.messages.size.toString(),
                    ),
                )
            }
            // #endregion
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChatComposeTheme.pageBg)
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(pagerData.statusBarHeight.dp))
                ChatTitleBar(
                    title = store.sessionTitle,
                    showTitle = true,
                    onMenuClick = { store.openDrawer() },
                    onNewChat = { store.startNewChat() },
                )
                    if (store.messagesEmpty) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(ChatComposeTheme.pageBg)
                        ) {
                            ChatEmptyHome(
                                onPrompt = { prompt ->
                                    if (store.isSending) {
                                        return@ChatEmptyHome
                                    }
                                    store.updateDraft(prompt)
                                    sendDraft()
                                },
                            )
                        }
                    } else {
                        val streamingId = if (store.isSending) {
                            store.messages.lastOrNull { !it.fromUser }?.id
                        } else {
                            null
                        }
                        val lastUserId = store.messages.lastOrNull { it.fromUser }?.id
                        val chatRows = buildChatRows(store.messages, streamingId)
                        val lastUserRow = chatRows.indexOfLast { it is ChatRow.User }
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clipToBounds()
                                .background(ChatComposeTheme.pageBg),
                            state = listState,
                            contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
                            beyondBoundsItemCount = 12,
                        ) {
                            items(chatRows, key = { it.key }) { row ->
                                when (row) {
                                    is ChatRow.User -> ChatBubbleRow(
                                        message = row.message,
                                        sending = false,
                                        pageViewWidth = pagerData.pageViewWidth,
                                        onRetry = {},
                                    )
                                    is ChatRow.Failed -> ChatBubbleRow(
                                        message = row.message,
                                        sending = store.isSending,
                                        pageViewWidth = pagerData.pageViewWidth,
                                        onRetry = { id ->
                                            store.retryFailed(id) { error ->
                                                bridgeModule.toast(error)
                                            }
                                        },
                                    )
                                    is ChatRow.Part -> AssistantPartRow(
                                        text = row.text,
                                        first = row.first,
                                        last = row.last,
                                        streaming = row.streaming,
                                        pageViewWidth = pagerData.pageViewWidth,
                                    )
                                    is ChatRow.Cards -> AssistantCardsRow(
                                        message = row.message,
                                        first = false,
                                        showFollowUps = row.showFollowUps,
                                        askEnabled = !store.isSending,
                                        pageViewWidth = pagerData.pageViewWidth,
                                        onAsk = { question ->
                                            if (store.isSending) {
                                                return@AssistantCardsRow
                                            }
                                            store.updateDraft(question)
                                            sendDraft()
                                        },
                                        onOpenKline = { message, code ->
                                            store.openKline(message, code)
                                        },
                                    )
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        val pinEpoch = if (store.isSending) chatRows.size else 0
                        LaunchedEffect(lastUserId, store.isSending, lastUserRow, pinEpoch) {
                            if (lastUserRow < 0) {
                                return@LaunchedEffect
                            }
                            listState.scrollToItem(lastUserRow, 0)
                            // #region agent log
                            AgentDebugLog.emit(
                                "B",
                                "StockChatPage.scroll",
                                "scroll-pin",
                                mapOf(
                                    "userRow" to lastUserRow.toString(),
                                    "idx" to listState.firstVisibleItemIndex.toString(),
                                    "off" to listState.firstVisibleItemScrollOffset.toString(),
                                    "canBack" to listState.canScrollBackward.toString(),
                                    "sending" to store.isSending.toString(),
                                    "rows" to chatRows.size.toString(),
                                    "pinEpoch" to pinEpoch.toString(),
                                    "kb" to keyboardHeight.toString(),
                                    "inset" to bottomInset.toString(),
                                    "pageH" to pagerData.pageViewHeight.toString(),
                                    "sb" to pagerData.statusBarHeight.toString(),
                                ),
                            )
                            // #endregion
                        }
                        // #region agent log
                        LaunchedEffect(
                            keyboardHeight,
                            store.isSending,
                            chatRows.size,
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset,
                        ) {
                            AgentDebugLog.emit(
                                "C",
                                "StockChatPage.list",
                                "list-layout",
                                mapOf(
                                    "kb" to keyboardHeight.toString(),
                                    "inset" to bottomInset.toString(),
                                    "composerPad" to maxOf(bottomInset, keyboardHeight).toString(),
                                    "empty" to "false",
                                    "sending" to store.isSending.toString(),
                                    "rows" to chatRows.size.toString(),
                                    "userRow" to lastUserRow.toString(),
                                    "idx" to listState.firstVisibleItemIndex.toString(),
                                    "off" to listState.firstVisibleItemScrollOffset.toString(),
                                    "canBack" to listState.canScrollBackward.toString(),
                                    "pageH" to pagerData.pageViewHeight.toString(),
                                ),
                            )
                        }
                        // #endregion
                    }
                ChatComposer(
                    draft = store.draft,
                    sending = store.isSending,
                    canSend = store.draft.isNotBlank() && !store.isSending,
                    canQuoteLatest = store.canQuoteLatest,
                    canComparePeers = store.canComparePeers,
                    chartAsk = store.chartAsk,
                    bottomInset = maxOf(bottomInset, keyboardHeight),
                    onDraftChange = { store.updateDraft(it) },
                    onKeyboardHeight = { keyboardHeight = it },
                    onQuoteLatest = {
                        if (store.isSending) {
                            return@ChatComposer
                        }
                        if (!store.quoteLatest()) {
                            bridgeModule.toast("先问一只具体股票，等行情卡出现后再引用")
                        }
                    },
                    onComparePeers = {
                        if (store.isSending) {
                            return@ChatComposer
                        }
                        val question = store.comparePeersQuestion()
                        if (question == null) {
                            bridgeModule.toast("先问一只具体股票，等回复里出现关联股后再对比")
                            return@ChatComposer
                        }
                        store.clearChartAsk()
                        store.sendQuestion(question) { error ->
                            bridgeModule.toast(error)
                        }
                    },
                    onClearChartAsk = { store.clearChartAsk() },
                    onSend = {
                        // #region agent log
                        AgentDebugLog.emit(
                            "E",
                            "StockChatPage.onSend",
                            "send-tap",
                            mapOf(
                                "kb" to keyboardHeight.toString(),
                                "empty" to store.messagesEmpty.toString(),
                                "msgs" to store.messages.size.toString(),
                            ),
                        )
                        // #endregion
                        sendDraft()
                    },
                    onStop = { store.stopSending() },
                )
            }
            if (store.drawerVisible) {
                ChatSideDrawer(
                    items = store.histories.toList(),
                    empty = store.historyEmpty,
                    displayName = store.displayName,
                    historyCount = store.historyCount,
                    onSearch = { store.applyFilter(it) },
                    onSelect = { store.openSession(it) },
                    onDelete = { store.deleteHistory(it) },
                    onClose = { store.closeDrawer() },
                    onAccount = { store.openAccount() },
                    onSettings = { store.openSettings() },
                    statusBarHeight = pagerData.statusBarHeight,
                    bottomInset = bottomInset,
                )
            }
            val klineMessage = store.klineMessage
            val liveCode = klineMessage?.targetCode.orEmpty()
            LaunchedEffect(liveCode) {
                if (liveCode.isBlank()) {
                    return@LaunchedEffect
                }
                while (true) {
                    store.refreshLiveMarket()
                    delay(3_000)
                }
            }
            key(store.klineSheetEpoch) {
                val summaryBody = klineMessage?.body.orEmpty()
                val summaryText = RelatedStockParser.answerExcerpt(summaryBody)
                val liveCode = klineMessage?.targetCode.orEmpty()
                val openedPick = klineMessage?.pickByCode(liveCode)
                val openedIndex = openedPick?.isIndex == true
                StockKlineSheet(
                    visible = klineMessage != null,
                    name = klineMessage?.targetName ?: klineMessage?.quote?.name.orEmpty(),
                    code = liveCode.ifBlank { klineMessage?.quote?.code.orEmpty() },
                    quote = klineMessage?.quote,
                    bars = klineMessage?.kline.orEmpty(),
                    related = klineMessage?.related,
                    relatedQuotes = store.relatedQuotes,
                    industryKlines = store.industryKlines,
                    minuteBars = store.minuteBars,
                    minutePrevClose = store.minutePrevClose,
                    showRelated = store.showRelated,
                    canPop = store.klineCanPop,
                    summary = summaryText,
                    market = openedPick?.market ?: klineMessage?.quote?.market.orEmpty(),
                    isIndex = openedIndex,
                    indexBlurb = store.klineIndexBlurb,
                    constituents = store.klineConstituents,
                    onRelatedOpen = { name, code -> store.openRelated(name, code) },
                    onAskFromChart = { store.askFromChart(it) },
                    onAskAi = {
                        store.askAboutOpened { error ->
                            bridgeModule.toast(error)
                        }
                    },
                    onCloseClick = { store.closeKlineLayer() },
                    onDismiss = { store.dismissKlineSheet() },
                    bottomInset = bottomInset,
                    statusBarHeight = pagerData.statusBarHeight,
                )
            }
            when (store.profilePane) {
                ProfilePane.Account -> AccountPane(
                    displayName = store.displayName,
                    historyCount = store.historyCount,
                    bottomInset = bottomInset,
                    statusBarHeight = pagerData.statusBarHeight,
                    onBack = { store.closeProfilePane() },
                    onSaveName = { name ->
                        store.saveDisplayName(name)
                        bridgeModule.toast("昵称已保存")
                    },
                    onCloseKeyboard = { bridgeModule.closeKeyboard() },
                )
                ProfilePane.Settings -> SettingsPane(
                    apiKeyStatus = store.apiKeyStatus(),
                    usingCustomKey = store.usingCustomApiKey,
                    hasBuiltinKey = store.hasBuiltinApiKey,
                    historyCount = store.historyCount,
                    themeMode = store.themeMode,
                    bottomInset = bottomInset,
                    statusBarHeight = pagerData.statusBarHeight,
                    onBack = { store.closeProfilePane() },
                    onSaveApiKey = { key ->
                        if (store.saveCustomApiKey(key)) {
                            bridgeModule.toast("API Key 已保存")
                        } else {
                            bridgeModule.toast("请输入有效的 API Key")
                        }
                    },
                    onClearCustomKey = {
                        store.clearCustomApiKey()
                        if (store.apiKeyReady) {
                            bridgeModule.toast("已改用内置 Key")
                        } else {
                            bridgeModule.toast("内置 Key 也未配置")
                        }
                    },
                    onClearHistory = {
                        store.clearAllHistories()
                        bridgeModule.toast("已清空全部对话")
                    },
                    onThemeMode = { mode ->
                        store.setThemeMode(mode, isNightMode())
                    },
                    onCloseKeyboard = { bridgeModule.closeKeyboard() },
                )
                ProfilePane.None -> Unit
            }
            val overlayOpen = store.profilePane != ProfilePane.None || store.klineMessage != null
            if (!overlayOpen && (store.drawerVisible || keyboardHeight > 0f || store.chartAsk != null)) {
                BackHandler {
                    when {
                        store.drawerVisible -> store.closeDrawer()
                        keyboardHeight > 0f -> {
                            bridgeModule.closeKeyboard()
                            keyboardHeight = 0f
                        }
                        store.chartAsk != null -> store.clearChartAsk()
                    }
                }
            }
        }
        LaunchedEffect(store.darkTheme) {
            bridgeModule.setNightBars(store.darkTheme)
        }
        }
    }

    private fun sendDraft() {
        val text = store.consumeDraftForSend() ?: return
        store.sendQuestion(text) { error ->
            bridgeModule.toast(error)
        }
    }

    /**
     * 底部系统安全区：状态由 Kuikly [pagerData.safeAreaInsets] 提供。
     * 部分 Android 全面屏手势不回报 bottom inset，这时给小白条留出固定高度。
     */
    private fun systemBottomInset(): Float {
        val reported = maxOf(
            pagerData.safeAreaInsets.bottom,
            pagerData.androidBottomBavBarHeight,
        )
        if (reported > 0f) {
            return reported
        }
        return if (pagerData.isAndroid) ANDROID_HOME_INDICATOR_DP else 0f
    }
}
