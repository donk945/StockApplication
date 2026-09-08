package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.ChartAskContext
import com.hfad.stockapplication.data.chat.KLineBar
import com.hfad.stockapplication.data.chat.RelatedStock
import com.hfad.stockapplication.data.chat.RelatedStocks
import com.hfad.stockapplication.data.chat.StockQuote
import com.tencent.kuikly.compose.BackHandler
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.gestures.detectVerticalDragGestures
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxHeight
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.heightIn
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.ModalBottomSheet
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.layout.onSizeChanged
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.IntOffset
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import kotlin.math.roundToInt

private const val INITIAL_SHEET_FRACTION = 2f / 3f

/**
 * 目标股 K 线底部抽屉。占屏 2/3 起步，上滑吸附全屏；内部管理分时/日 K 十字光标（互斥）。
 */
@Composable
fun StockKlineSheet(
    visible: Boolean,
    name: String,
    code: String,
    quote: StockQuote?,
    bars: List<KLineBar>,
    related: RelatedStocks? = null,
    relatedQuotes: Map<String, StockQuote> = emptyMap(),
    industryKlines: Map<String, List<KLineBar>> = emptyMap(),
    minuteBars: List<KLineBar> = emptyList(),
    minutePrevClose: String = "",
    showRelated: Boolean = true,
    canPop: Boolean = false,
    summary: String = "",
    market: String = "",
    isIndex: Boolean = false,
    indexBlurb: String = "",
    constituents: List<RelatedStock> = emptyList(),
    onRelatedOpen: (String, String) -> Unit = { _, _ -> },
    onAskFromChart: (ChartAskContext) -> Unit = {},
    onAskAi: () -> Unit = {},
    onCloseClick: () -> Unit,
    onDismiss: () -> Unit,
    bottomInset: Float = 0f,
    statusBarHeight: Float = 0f,
) {
    var shownName by remember { mutableStateOf(name) }
    var shownCode by remember { mutableStateOf(code) }
    var shownQuote by remember { mutableStateOf(quote) }
    var shownBars by remember { mutableStateOf(bars) }
    var shownRelated by remember { mutableStateOf(related) }
    var shownRelatedQuotes by remember { mutableStateOf(relatedQuotes) }
    var shownIndustryKlines by remember { mutableStateOf(industryKlines) }
    var shownMinuteBars by remember { mutableStateOf(minuteBars) }
    var shownMinutePrevClose by remember { mutableStateOf(minutePrevClose) }
    var shownShowRelated by remember { mutableStateOf(showRelated) }
    var shownCanPop by remember { mutableStateOf(canPop) }
    var shownSummary by remember { mutableStateOf(summary) }
    var shownMarket by remember { mutableStateOf(market) }
    var shownIsIndex by remember { mutableStateOf(isIndex) }
    var shownIndexBlurb by remember { mutableStateOf(indexBlurb) }
    var shownConstituents by remember { mutableStateOf(constituents) }
    val onAskAiLatest = rememberUpdatedState(onAskAi)
    SideEffect {
        if (visible) {
            shownName = name
            shownCode = code
            shownQuote = quote
            shownBars = bars
            shownRelated = related
            shownRelatedQuotes = relatedQuotes
            shownIndustryKlines = industryKlines
            shownMinuteBars = minuteBars
            shownMinutePrevClose = minutePrevClose
            shownShowRelated = showRelated
            shownCanPop = canPop
            shownSummary = summary
            shownMarket = market
            shownIsIndex = isIndex
            shownIndexBlurb = indexBlurb
            shownConstituents = constituents
        }
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var selectedMinuteIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    var restoreIndex by remember { mutableStateOf<Int?>(null) }
    var restoreOffset by remember { mutableStateOf(0) }
    val expanded = remember { mutableStateOf(false) }
    val dragPx = remember { mutableStateOf(0f) }
    val sheetHeightPx = remember { mutableStateOf(1f) }
    val onCloseLatest = rememberUpdatedState(onCloseClick)
    val onAskFromChartLatest = rememberUpdatedState(onAskFromChart)
    LaunchedEffect(visible, code) {
        if (visible) {
            selectedIndex = null
            selectedMinuteIndex = null
            dragPx.value = 0f
        }
    }
    LaunchedEffect(visible) {
        if (!visible) {
            expanded.value = false
        }
    }
    LaunchedEffect(shownCanPop, shownShowRelated, shownCode) {
        if (!visible) {
            return@LaunchedEffect
        }
        if (shownCanPop) {
            listState.scrollToItem(0, 0)
        } else {
            val index = restoreIndex
            if (index != null && shownShowRelated) {
                listState.scrollToItem(index, restoreOffset)
                restoreIndex = null
            }
        }
    }

    val selectedMinuteBar = selectedMinuteIndex?.let { shownMinuteBars.getOrNull(it) }
    val selectedDailyBar = selectedIndex?.let { shownBars.getOrNull(it) }
    val selectedBar = selectedMinuteBar ?: selectedDailyBar
    val selectedBaseline = if (selectedMinuteBar != null) {
        shownMinutePrevClose.ifBlank { shownQuote?.prevClose.orEmpty() }
    } else {
        null
    }

    val height = sheetHeightPx.value.coerceAtLeast(1f)
    val upPx = (-dragPx.value).coerceAtLeast(0f)
    val downPx = dragPx.value.coerceAtLeast(0f)
    val fraction = when {
        expanded.value && downPx > 0f -> {
            1f - (1f - INITIAL_SHEET_FRACTION) * (downPx / height).coerceIn(0f, 1f)
        }
        !expanded.value && upPx > 0f -> {
            INITIAL_SHEET_FRACTION + (1f - INITIAL_SHEET_FRACTION) * (upPx / height).coerceIn(0f, 1f)
        }
        expanded.value -> 1f
        else -> INITIAL_SHEET_FRACTION
    }
    val expandT = ((fraction - INITIAL_SHEET_FRACTION) / (1f - INITIAL_SHEET_FRACTION)).coerceIn(0f, 1f)
    val topPad = statusBarHeight * expandT
    val corner = (16f * (1f - expandT)).dp
    val dismissOffset = if (!expanded.value && downPx > 0f) downPx else 0f

    ModalBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        containerColor = ChatComposeTheme.surface,
        modifier = Modifier
            .fillMaxHeight(fraction)
            .onSizeChanged { size ->
                sheetHeightPx.value = size.height.toFloat().coerceAtLeast(1f)
            }
            .clip(RoundedCornerShape(topStart = corner, topEnd = corner)),
    ) {
        // 后注册的 BackHandler 在栈顶，优先于 Dialog 默认关抽屉。
        BackHandler {
            onCloseLatest.value()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .offset { IntOffset(0, dismissOffset.roundToInt()) }
                .padding(top = topPad.dp, bottom = bottomInset.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        val dismissThreshold = 72.dp.toPx()
                        val snapThreshold = 48.dp.toPx()
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (expanded.value) {
                                    if (dragPx.value > snapThreshold) {
                                        expanded.value = false
                                    }
                                } else {
                                    if (dragPx.value > dismissThreshold) {
                                        onCloseLatest.value()
                                    } else if (dragPx.value < -snapThreshold) {
                                        expanded.value = true
                                    }
                                }
                                dragPx.value = 0f
                            },
                            onDragCancel = { dragPx.value = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                val next = dragPx.value + dragAmount
                                dragPx.value = if (expanded.value) {
                                    next.coerceAtLeast(0f)
                                } else {
                                    next
                                }
                            },
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                        .height(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ChatComposeTheme.hairline)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = shownName.ifBlank { if (shownIsIndex) "指数" else "目标股票" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChatComposeTheme.title,
                        )
                        if (shownCode.isNotBlank()) {
                            Text(
                                text = shownCode,
                                fontSize = 12.sp,
                                color = ChatComposeTheme.placeholder,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(onClick = onCloseClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (shownCanPop) "返回" else "关闭",
                            fontSize = 15.sp,
                            color = ChatComposeTheme.accent,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(ChatComposeTheme.hairline)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                state = listState,
            ) {
                item(key = "quote") {
                    StockQuoteSection(
                        quote = shownQuote,
                        bars = shownBars,
                        selectedIndex = selectedIndex,
                        selectedBar = selectedBar,
                        onSelect = {
                            selectedMinuteIndex = null
                            selectedIndex = it
                        },
                        minuteBars = shownMinuteBars,
                        minutePrevClose = shownMinutePrevClose,
                        selectedMinuteIndex = selectedMinuteIndex,
                        onMinuteSelect = {
                            selectedIndex = null
                            selectedMinuteIndex = it
                        },
                        selectedBaseline = selectedBaseline,
                        onDailyAsk = { bar ->
                            onAskFromChartLatest.value(
                                ChartAskContext(
                                    kind = ChartAskContext.Kind.Daily,
                                    name = shownName,
                                    code = shownCode,
                                    bar = bar,
                                    market = shownMarket.ifBlank { shownQuote?.market.orEmpty() },
                                )
                            )
                        },
                        onMinuteAsk = { bar ->
                            onAskFromChartLatest.value(
                                ChartAskContext(
                                    kind = ChartAskContext.Kind.Minute,
                                    name = shownName,
                                    code = shownCode,
                                    bar = bar,
                                    baseline = shownMinutePrevClose.ifBlank {
                                        shownQuote?.prevClose.orEmpty()
                                    },
                                    market = shownMarket.ifBlank { shownQuote?.market.orEmpty() },
                                )
                            )
                        },
                    )
                }
                item(key = "summary") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AI 摘要",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChatComposeTheme.title,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = shownSummary.ifBlank {
                            if (shownCanPop) {
                                "关联标的，无本轮摘要"
                            } else {
                                "暂无摘要，可点下方问 AI 继续分析"
                            }
                        },
                        fontSize = 13.sp,
                        color = if (shownSummary.isBlank()) {
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
                item(key = "ask-ai") {
                    Spacer(modifier = Modifier.height(12.dp))
                    AskAiButton(onClick = { onAskAiLatest.value() })
                }
                if (shownIsIndex && !shownCanPop) {
                    item(key = "index-note") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "指数说明",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChatComposeTheme.title,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = shownIndexBlurb.ifBlank { "该指数反映一篮子成份股的整体表现，不是单只股票。" },
                            fontSize = 13.sp,
                            color = ChatComposeTheme.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ChatComposeTheme.contentBg)
                                .padding(12.dp),
                        )
                        if (shownConstituents.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "样本股",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ChatComposeTheme.title,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            shownConstituents.forEach { stock ->
                                val quote = shownRelatedQuotes[stock.code]
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
                                        restoreIndex = listState.firstVisibleItemIndex
                                        restoreOffset = listState.firstVisibleItemScrollOffset
                                        onRelatedOpen(stock.name, stock.code)
                                    },
                                )
                            }
                        }
                    }
                }
                if (shownShowRelated) {
                    item(key = "related") {
                        StockRelatedSection(
                            related = shownRelated,
                            relatedQuotes = shownRelatedQuotes,
                            industryKlines = shownIndustryKlines,
                            onOpen = { name, code ->
                                restoreIndex = listState.firstVisibleItemIndex
                                restoreOffset = listState.firstVisibleItemScrollOffset
                                onRelatedOpen(name, code)
                            },
                        )
                    }
                }
                item(key = "tail") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AskAiButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(ChatComposeTheme.accentSoft)
            .border(0.5.dp, ChatComposeTheme.accent.copy(alpha = 0.28f), shape)
            .clickable(onClick = onClick)
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
            Text(
                text = "AI",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ChatComposeTheme.white,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "问 AI",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChatComposeTheme.accent,
            )
            Text(
                text = "结合当前行情继续分析",
                fontSize = 12.sp,
                color = ChatComposeTheme.placeholder,
            )
        }
        Text(
            text = "›",
            fontSize = 20.sp,
            color = ChatComposeTheme.accent,
        )
    }
}
