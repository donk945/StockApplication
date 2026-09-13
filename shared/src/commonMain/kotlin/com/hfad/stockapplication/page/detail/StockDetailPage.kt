package com.hfad.stockapplication.page.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hfad.stockapplication.component.chart.StockQuoteSection
import com.hfad.stockapplication.component.chart.StockRelatedSection
import com.hfad.stockapplication.component.chart.TargetStockChip
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.component.theme.ProvideChatColors
import com.hfad.stockapplication.component.theme.ChatMarkdownBody
import com.hfad.stockapplication.data.chat.ChartAskContext
import com.hfad.stockapplication.data.chat.ChartAskHandoff
import com.hfad.stockapplication.data.chat.DeepSeekChatRepository
import com.hfad.stockapplication.data.chat.TencentMarketRepository
import com.hfad.stockapplication.data.chat.UserSettingsRepository
import com.hfad.stockapplication.debug.AgentDebugLog
import com.hfad.stockapplication.infra.AppPages
import com.hfad.stockapplication.infra.BaseComposePager
import com.hfad.stockapplication.infra.SseModule
import com.hfad.stockapplication.infra.bridgeModule
import com.hfad.stockapplication.infra.closeAppPage
import com.hfad.stockapplication.infra.openAppPage
import com.hfad.stockapplication.infra.systemBottomInset
import com.hfad.stockapplication.page.chat.deepSeekKeyFromPager
import com.hfad.stockapplication.state.detail.DetailStore
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.heightIn
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import kotlinx.coroutines.delay

/**
 * 任务 2 详情承接页：聊天卡片 / 行情列表跳入的全屏页。
 * 展示基础行情、分时/日 K（含均线）、AI 摘要与快捷解读。
 * 长按分时/日 K 关掉本页，把该点挂回问答输入框上方的卡片。
 */
@Page("stock_detail", supportInLocal = true)
internal class StockDetailPage : BaseComposePager() {

    private lateinit var store: DetailStore
    private var pageVisible by mutableStateOf(true)

    override fun willInit() {
        super.willInit()
        setContent { DetailScreen() }
    }

    override fun created() {
        super.created()
        val network = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
        val sse = acquireModule<SseModule>(SseModule.MODULE_NAME)
        val sp = acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
        val settings = UserSettingsRepository(sp)
        val builtin = deepSeekKeyFromPager(pagerData.params)
        val apiKey = settings.loadCustomApiKey().ifBlank { builtin }
        val theme = settings.loadThemeMode()
        store = DetailStore(
            marketRepository = TencentMarketRepository(network),
            aiRepository = DeepSeekChatRepository(network, sse, apiKey),
        )
        store.loadFromParams(pagerData.params, dark = theme.isDark(isNightMode()))
        // #region agent log
        AgentDebugLog.emit(
            "D",
            "StockDetailPage.created",
            "ready",
            mapOf("code" to store.code),
            runId = "post-fix",
        )
        // #endregion
    }

    override fun pageWillDestroy() {
        if (::store.isInitialized) {
            store.release()
        }
        super.pageWillDestroy()
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        pageVisible = true
        // 关联股再开一层详情时：下层页出现后继续关，直到回到问答。
        if (ChartAskHandoff.peek() != null) {
            // #region agent log
            AgentDebugLog.emit(
                "F",
                "StockDetailPage.pageDidAppear",
                "close-stacked",
                mapOf("code" to store.code),
                runId = "chart-ask",
            )
            // #endregion
            closeAppPage()
        }
    }

    override fun pageDidDisappear() {
        pageVisible = false
        super.pageDidDisappear()
    }

    @Composable
    private fun DetailScreen() {
        val bottomInset = systemBottomInset()
        var selectedIndex by remember { mutableStateOf<Int?>(null) }
        var selectedMinuteIndex by remember { mutableStateOf<Int?>(null) }
        ProvideChatColors(dark = store.darkTheme) {
            LaunchedEffect(store.darkTheme) {
                bridgeModule.setNightBars(store.darkTheme)
            }
            LaunchedEffect(store.code) {
                selectedIndex = null
                selectedMinuteIndex = null
            }
            LaunchedEffect(store.code, pageVisible) {
                if (!pageVisible || store.code.length != 6) {
                    return@LaunchedEffect
                }
                while (pageVisible) {
                    delay(3_000)
                    if (pageVisible) {
                        store.refreshLive()
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChatComposeTheme.pageBg),
            ) {
                Spacer(modifier = Modifier.height(pagerData.statusBarHeight.dp))
                DetailTitleBar(
                    name = store.name.ifBlank { if (store.isIndex) "指数" else "行情详情" },
                    code = store.code,
                    onBack = { closeAppPage() },
                )
                val selectedMinuteBar = selectedMinuteIndex?.let { store.minuteBars.getOrNull(it) }
                val selectedDailyBar = selectedIndex?.let { store.bars.getOrNull(it) }
                val selectedBar = selectedMinuteBar ?: selectedDailyBar
                val selectedBaseline = if (selectedMinuteBar != null) {
                    store.minutePrevClose.ifBlank { store.quote?.prevClose.orEmpty() }
                } else {
                    null
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    item("quote") {
                        Spacer(modifier = Modifier.height(12.dp))
                        StockQuoteSection(
                            quote = store.quote,
                            bars = store.bars,
                            selectedIndex = selectedIndex,
                            selectedBar = selectedBar,
                            onSelect = {
                                selectedMinuteIndex = null
                                selectedIndex = it
                            },
                            minuteBars = store.minuteBars,
                            minutePrevClose = store.minutePrevClose,
                            selectedMinuteIndex = selectedMinuteIndex,
                            onMinuteSelect = {
                                selectedIndex = null
                                selectedMinuteIndex = it
                            },
                            selectedBaseline = selectedBaseline,
                            onDailyAsk = { bar ->
                                returnWithChartAsk(
                                    ChartAskContext(
                                        kind = ChartAskContext.Kind.Daily,
                                        name = store.name,
                                        code = store.code,
                                        bar = bar,
                                        market = store.market,
                                    ),
                                )
                            },
                            onMinuteAsk = { bar ->
                                returnWithChartAsk(
                                    ChartAskContext(
                                        kind = ChartAskContext.Kind.Minute,
                                        name = store.name,
                                        code = store.code,
                                        bar = bar,
                                        baseline = store.minutePrevClose.ifBlank {
                                            store.quote?.prevClose.orEmpty()
                                        },
                                        market = store.market,
                                    ),
                                )
                            },
                        )
                    }
                    item("summary") {
                        Spacer(modifier = Modifier.height(16.dp))
                        SectionTitle("AI 摘要")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = store.summary.ifBlank { "从行情页进入时没有会话摘要，可点下方让 AI 解读走势。" },
                            fontSize = 13.sp,
                            color = if (store.summary.isBlank()) {
                                ChatComposeTheme.placeholder
                            } else {
                                ChatComposeTheme.title
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ChatComposeTheme.contentBg)
                                .padding(12.dp),
                        )
                    }
                    item("ask") {
                        Spacer(modifier = Modifier.height(12.dp))
                        AskChipRow(
                            sending = store.aiSending,
                            onTrend = {
                                store.interpretTrend { error -> bridgeModule.toast(error) }
                            },
                            onRisk = {
                                store.interpretRisk { error -> bridgeModule.toast(error) }
                            },
                            onBuy = {
                                store.interpretBuy { error -> bridgeModule.toast(error) }
                            },
                        )
                    }
                    item("ai") {
                        if (store.aiText.isNotBlank() || store.aiSending) {
                            Spacer(modifier = Modifier.height(16.dp))
                            SectionTitle(if (store.aiSending) "AI 解读中" else "AI 解读")
                            Spacer(modifier = Modifier.height(8.dp))
                            AiMarkdownBody(
                                text = store.aiText.ifBlank { "正在分析…" },
                                streaming = store.aiSending,
                            )
                        }
                    }
                    if (store.isIndex) {
                        item("index") {
                            Spacer(modifier = Modifier.height(16.dp))
                            SectionTitle("指数说明")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = store.indexBlurb.ifBlank { "该指数反映一篮子成份股整体表现，不是单只股票。" },
                                fontSize = 13.sp,
                                color = ChatComposeTheme.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ChatComposeTheme.contentBg)
                                    .padding(12.dp),
                            )
                            if (store.constituents.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                SectionTitle("样本股")
                                Spacer(modifier = Modifier.height(4.dp))
                                store.constituents.forEach { stock ->
                                    val quote = store.relatedQuotes[stock.code]
                                    val change = listOfNotNull(
                                        quote?.change?.takeIf { it.isNotBlank() },
                                        quote?.changePercent?.takeIf { it.isNotBlank() },
                                    ).joinToString("  ")
                                    TargetStockChip(
                                        name = stock.name,
                                        code = stock.code,
                                        enabled = stock.code.length == 6,
                                        changeText = change,
                                        onClick = {
                                            openRelated(stock.name, stock.code)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    val related = store.related
                    if (related != null && !related.isEmpty()) {
                        item("related") {
                            StockRelatedSection(
                                related = related,
                                relatedQuotes = store.relatedQuotes,
                                industryKlines = emptyMap(),
                                onOpen = { name, code -> openRelated(name, code) },
                            )
                        }
                    }
                    item("tail") {
                        Spacer(modifier = Modifier.height((bottomInset + 16f).dp))
                    }
                }
            }
        }
    }

    private fun returnWithChartAsk(context: ChartAskContext) {
        // #region agent log
        AgentDebugLog.emit(
            "F",
            "StockDetailPage.returnWithChartAsk",
            "offer-close",
            mapOf(
                "kind" to context.kind.name,
                "code" to context.code,
                "day" to context.bar.day,
                "close" to context.bar.close,
                "open" to context.bar.open,
            ),
            runId = "chart-ask",
        )
        // #endregion
        ChartAskHandoff.offer(context)
        closeAppPage()
    }

    private fun openRelated(name: String, code: String) {
        val market = com.hfad.stockapplication.data.chat.TargetStockParser.marketPrefix(code).orEmpty()
        if (market.isBlank()) {
            return
        }
        openAppPage(
            AppPages.DETAIL,
            DetailStore.fromWatchItem(name, code, market, isIndex = false),
        )
    }
}

@Composable
private fun DetailTitleBar(name: String, code: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
        ) {
            Text("返回", fontSize = 16.sp, color = ChatComposeTheme.accent)
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = name,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChatComposeTheme.title,
            )
            if (code.isNotBlank()) {
                Text(text = code, fontSize = 12.sp, color = ChatComposeTheme.placeholder)
            }
        }
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = ChatComposeTheme.title,
    )
}

@Composable
private fun AskChipRow(
    sending: Boolean,
    onTrend: () -> Unit,
    onRisk: () -> Unit,
    onBuy: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AskMainButton(enabled = !sending, onClick = onTrend)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            AskSmallChip("有什么风险", enabled = !sending, onClick = onRisk, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            AskSmallChip("现在能买吗", enabled = !sending, onClick = onBuy, modifier = Modifier.weight(1f))
        }
        Text(
            text = "长按分时或日 K 可将该点带回对话提问。内容仅供参考，不构成投资建议。",
            fontSize = 11.sp,
            color = ChatComposeTheme.placeholder,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AskMainButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(ChatComposeTheme.accentSoft)
            .border(0.5.dp, ChatComposeTheme.accent.copy(alpha = 0.28f), shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ChatComposeTheme.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text("AI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ChatComposeTheme.white)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("解读走势", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ChatComposeTheme.accent)
            Text("结论 / 驱动 / 风险", fontSize = 12.sp, color = ChatComposeTheme.placeholder)
        }
        Text("›", fontSize = 20.sp, color = ChatComposeTheme.accent)
    }
}

@Composable
private fun AskSmallChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ChatComposeTheme.contentBg)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, color = ChatComposeTheme.accent)
    }
}

@Composable
private fun AiMarkdownBody(text: String, streaming: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ChatComposeTheme.contentBg)
            .padding(12.dp),
    ) {
        ChatMarkdownBody(
            text = text,
            streaming = streaming,
            fallbackFontSize = 13.sp,
        )
    }
}
