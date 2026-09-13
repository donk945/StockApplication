package com.hfad.stockapplication.page.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.component.theme.ProvideChatColors
import com.hfad.stockapplication.data.chat.DeepSeekChatRepository
import com.hfad.stockapplication.data.chat.LocalChatRepository
import com.hfad.stockapplication.data.chat.TencentMarketRepository
import com.hfad.stockapplication.data.chat.UserSettingsRepository
import com.hfad.stockapplication.debug.AgentDebugLog
import com.hfad.stockapplication.infra.AppPages
import com.hfad.stockapplication.infra.BaseComposePager
import com.hfad.stockapplication.infra.SseModule
import com.hfad.stockapplication.infra.bridgeModule
import com.hfad.stockapplication.infra.openAppPage
import com.hfad.stockapplication.state.chat.ChatStore
import com.hfad.stockapplication.state.chat.ProfilePane
import com.hfad.stockapplication.state.detail.DetailStore
import com.tencent.kuikly.compose.BackHandler
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
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
 * 卡片详情走 `stock_detail`。
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
        val apiKey = deepSeekKeyFromPager(pagerData.params)
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

    override fun pageDidAppear() {
        super.pageDidAppear()
        if (::store.isInitialized) {
            store.takePendingChartAsk()
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChatComposeTheme.pageBg)
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(pagerData.statusBarHeight.dp))
                ChatTitleBar(
                    title = store.sessionTitle,
                    showTitle = store.sessionTitle != ChatStore.DEFAULT_TITLE,
                    onMenuClick = { store.openDrawer() },
                    onNewChat = { store.startNewChat() },
                )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds()
                            .background(ChatComposeTheme.pageBg),
                    ) {
                        val streamingId = if (store.isSending) {
                            store.messages.lastOrNull { !it.fromUser }?.id
                        } else {
                            null
                        }
                        val lastAssistant = store.messages.lastOrNull { !it.fromUser }
                        val chatRows = remember(
                            store.messages.size,
                            store.messagesEmpty,
                            streamingId,
                            lastAssistant?.id,
                            lastAssistant?.body,
                            lastAssistant?.failed,
                            lastAssistant?.targetQuotes?.size,
                            lastAssistant?.targetKlines?.size,
                            lastAssistant?.relatedPicks?.size,
                            lastAssistant?.related != null,
                        ) {
                            if (store.messagesEmpty) {
                                emptyList()
                            } else {
                                buildChatRows(store.messages, streamingId)
                            }
                        }
                        ChatTranscriptList(
                            rows = chatRows,
                            listState = listState,
                            sending = store.isSending,
                            pageViewWidth = pagerData.pageViewWidth,
                            onRetryFailed = { id ->
                                store.retryFailed(id) { error ->
                                    bridgeModule.toast(error)
                                }
                            },
                            onAsk = { question ->
                                if (!store.isSending) {
                                    store.updateDraft(question)
                                    sendDraft()
                                }
                            },
                            onOpenKline = { message, code ->
                                val pageData = DetailStore.fromChatMessage(message, code)
                                if (pageData == null) {
                                    bridgeModule.toast("暂无行情标的")
                                } else {
                                    openAppPage(AppPages.DETAIL, pageData)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (store.messagesEmpty) {
                            ChatEmptyHome(
                                onPrompt = { prompt ->
                                    if (store.isSending) {
                                        return@ChatEmptyHome
                                    }
                                    store.updateDraft(prompt)
                                    keyboardHeight = 0f
                                    bridgeModule.closeKeyboard()
                                    sendDraft()
                                },
                            )
                        }
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
                        store.clearChartAsk("compare-peers")
                        store.sendQuestion(question) { error ->
                            bridgeModule.toast(error)
                        }
                    },
                    onClearChartAsk = { store.clearChartAsk("card-x") },
                    onSend = {
                        store.markDebug("tap-send")
                        // #region agent log
                        AgentDebugLog.emit(
                            "D",
                            "ChatComposer.onSend",
                            "tap",
                            mapOf(
                                "kb" to keyboardHeight.toString(),
                                "empty" to store.messagesEmpty.toString(),
                                "sending" to store.isSending.toString(),
                                "hasChart" to (store.chartAsk != null).toString(),
                                "day" to (store.chartAsk?.bar?.day ?: ""),
                                "close" to (store.chartAsk?.bar?.close ?: ""),
                            ),
                            runId = "post-fix",
                        )
                        // #endregion
                        keyboardHeight = 0f
                        bridgeModule.closeKeyboard()
                        store.markDebug("after-keyboard")
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
            val overlayOpen = store.profilePane != ProfilePane.None
            if (!overlayOpen && (store.drawerVisible || keyboardHeight > 0f || store.chartAsk != null)) {
                BackHandler {
                    when {
                        store.drawerVisible -> store.closeDrawer()
                        keyboardHeight > 0f -> {
                            bridgeModule.closeKeyboard()
                            keyboardHeight = 0f
                        }
                        store.chartAsk != null -> store.clearChartAsk("back-handler")
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
        // #region agent log
        AgentDebugLog.emit(
            "A",
            "StockChatPage.sendDraft",
            "enter",
            mapOf(
                "empty" to store.messagesEmpty.toString(),
                "sending" to store.isSending.toString(),
                "draftLen" to store.draft.length.toString(),
                "msg" to store.messages.size.toString(),
                "hasChart" to (store.chartAsk != null).toString(),
                "day" to (store.chartAsk?.bar?.day ?: ""),
            ),
            runId = "post-fix",
        )
        // #endregion
        val text = store.consumeDraftForSend() ?: return
        store.markDebug("draft-consumed")
        try {
            store.sendQuestion(text) { error ->
                bridgeModule.toast(error)
            }
            // #region agent log
            AgentDebugLog.emit(
                "D",
                "StockChatPage.sendDraft",
                "asked",
                mapOf("qLen" to text.length.toString(), "msg" to store.messages.size.toString()),
                runId = "post-fix",
            )
            // #endregion
        } catch (error: Throwable) {
            // #region agent log
            AgentDebugLog.emit(
                "E",
                "StockChatPage.sendDraft",
                "throw",
                mapOf("type" to error::class.simpleName.orEmpty(), "msg" to (error.message ?: "")),
                runId = "post-fix",
            )
            // #endregion
            throw error
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
