package com.hfad.stockapplication.page.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hfad.stockapplication.component.chart.ComparisonPairRow
import com.hfad.stockapplication.component.chart.IndexBreadthRow
import com.hfad.stockapplication.component.chart.QuoteSparkline
import com.hfad.stockapplication.component.chart.TargetStockChip
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.component.theme.chatMarkdownColors
import com.hfad.stockapplication.component.theme.chatMarkdownTypography
import com.hfad.stockapplication.data.chat.ChatMessage
import com.hfad.stockapplication.data.chat.FollowUpPrompts
import com.hfad.stockapplication.data.chat.KLineBar
import com.hfad.stockapplication.data.chat.RelatedStockParser
import com.hfad.stockapplication.data.chat.StockQuote
import com.hfad.stockapplication.data.chat.TargetStock
import com.hfad.stockapplication.debug.AgentDebugLog
import com.hfad.stockapplication.state.chat.ChatStore
import com.tencent.kuikly.compose.animation.core.LinearEasing
import com.tencent.kuikly.compose.animation.core.RepeatMode
import com.tencent.kuikly.compose.animation.core.animateFloat
import com.tencent.kuikly.compose.animation.core.infiniteRepeatable
import com.tencent.kuikly.compose.animation.core.rememberInfiniteTransition
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.ExperimentalLayoutApi
import com.tencent.kuikly.compose.foundation.layout.FlowRow
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.layout.widthIn
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.LazyListState
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuiklybase.markdown.compose.Markdown
import com.tencent.kuiklybase.markdown.model.rememberMarkdownState
import kotlin.math.PI
import kotlin.math.sin

internal fun buildChatRows(
    messages: List<ChatMessage>,
    streamingId: String?,
): List<ChatRow> {
    val lastCardsId = messages.lastOrNull { message ->
        !message.fromUser && !message.failed && message.hasCards()
    }?.id
    val rows = ArrayList<ChatRow>(messages.size * 2)
    messages.forEach { message ->
        when {
            message.fromUser -> rows += ChatRow.User(message)
            message.failed -> rows += ChatRow.Failed(message)
            else -> {
                val streaming = message.id == streamingId
                val source = if (streaming) {
                    message.body.ifBlank { "正在分析…" }
                } else {
                    message.body
                }
                val thinking = streaming && (
                    message.body.isBlank() || message.body == ChatStore.LOADING_TEXT
                )
                val parts = RelatedStockParser.splitDisplayParts(source, streaming)
                val hasCards = !thinking && message.hasCards()
                parts.forEachIndexed { index, text ->
                    rows += ChatRow.Part(
                        messageId = message.id,
                        index = index,
                        text = text,
                        first = index == 0,
                        last = index == parts.lastIndex && !hasCards,
                        streaming = streaming && index == parts.lastIndex,
                    )
                }
                if (hasCards) {
                    rows += ChatRow.Cards(
                        message = message,
                        showFollowUps = !streaming && streamingId == null && message.id == lastCardsId,
                    )
                }
            }
        }
    }
    return rows
}

internal sealed class ChatRow {
    abstract val key: String

    data class User(val message: ChatMessage) : ChatRow() {
        override val key: String get() = "u-${message.id}"
    }

    data class Failed(val message: ChatMessage) : ChatRow() {
        override val key: String get() = "f-${message.id}"
    }

    data class Part(
        val messageId: String,
        val index: Int,
        val text: String,
        val first: Boolean,
        val last: Boolean,
        val streaming: Boolean,
    ) : ChatRow() {
        override val key: String get() = "p-$messageId-$index"
    }

    data class Cards(
        val message: ChatMessage,
        val showFollowUps: Boolean,
    ) : ChatRow() {
        override val key: String get() = "c-${message.id}"
    }
}

@Composable
internal fun ChatTranscriptList(
    rows: List<ChatRow>,
    listState: LazyListState,
    sending: Boolean,
    pageViewWidth: Float,
    onRetryFailed: (String) -> Unit,
    onAsk: (String) -> Unit,
    onOpenKline: (ChatMessage, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // #region agent log
    LaunchedEffect(rows.size, sending) {
        AgentDebugLog.emit(
            "E",
            "ChatTranscriptList",
            "rows",
            mapOf(
                "rows" to rows.size.toString(),
                "parts" to rows.count { it is ChatRow.Part }.toString(),
                "streamParts" to rows.count { it is ChatRow.Part && it.streaming }.toString(),
                "sending" to sending.toString(),
            ),
        )
    }
    // #endregion
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
    ) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is ChatRow.User -> ChatBubbleRow(
                    message = row.message,
                    sending = false,
                    pageViewWidth = pageViewWidth,
                    onRetry = {},
                )
                is ChatRow.Failed -> ChatBubbleRow(
                    message = row.message,
                    sending = sending,
                    pageViewWidth = pageViewWidth,
                    onRetry = onRetryFailed,
                )
                is ChatRow.Part -> AssistantPartRow(
                    text = row.text,
                    first = row.first,
                    last = row.last,
                    streaming = row.streaming,
                    pageViewWidth = pageViewWidth,
                )
                is ChatRow.Cards -> AssistantCardsRow(
                    message = row.message,
                    first = false,
                    showFollowUps = row.showFollowUps,
                    askEnabled = !sending,
                    pageViewWidth = pageViewWidth,
                    onAsk = onAsk,
                    onOpenKline = onOpenKline,
                )
            }
        }
    }
}

@Composable
internal fun AssistantPartRow(
    text: String,
    first: Boolean,
    last: Boolean,
    streaming: Boolean,
    pageViewWidth: Float,
) {
    val maxWidth = (pageViewWidth - 40f).dp
    val markdownState = rememberMarkdownState()
    var parsed by remember { mutableStateOf(false) }
    val plain = RelatedStockParser.streamPlainText(text)
    val thinking = streaming && (text == ChatStore.LOADING_TEXT || text.isBlank())
    // #region agent log
    LaunchedEffect(streaming, thinking) {
        AgentDebugLog.emit(
            "A",
            "AssistantPartRow",
            "part-mode",
            mapOf(
                "streaming" to streaming.toString(),
                "thinking" to thinking.toString(),
                "parsed" to parsed.toString(),
                "len" to text.length.toString(),
                "stars" to text.contains("**").toString(),
                "heading" to text.contains("#").toString(),
                "first" to first.toString(),
                "last" to last.toString(),
            ),
        )
    }
    // #endregion
    LaunchedEffect(text, streaming, thinking) {
        if (thinking || streaming) {
            parsed = false
            return@LaunchedEffect
        }
        val started = DateTime.currentTimestamp()
        markdownState.parse(RelatedStockParser.streamMarkdown(text), false)
        parsed = true
        // #region agent log
        AgentDebugLog.emit(
            "B",
            "AssistantPartRow",
            "parse-done",
            mapOf(
                "len" to text.length.toString(),
                "ms" to (DateTime.currentTimestamp() - started).toString(),
                "stars" to text.contains("**").toString(),
                "heading" to text.contains("#").toString(),
            ),
        )
        // #endregion
    }
    val shape = RoundedCornerShape(
        topStart = if (first) 12.dp else 0.dp,
        topEnd = if (first) 12.dp else 0.dp,
        bottomEnd = if (last) 12.dp else 0.dp,
        bottomStart = if (last) 12.dp else 0.dp,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 16.dp,
                top = if (first) 8.dp else 0.dp,
                bottom = if (last) 4.dp else 0.dp,
            ),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(shape)
                .background(ChatComposeTheme.surface)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = if (first) 12.dp else 4.dp,
                    bottom = if (last) 12.dp else 4.dp,
                )
        ) {
            if (thinking) {
                ThinkingIndicator()
            } else if (!parsed) {
                Text(
                    text = plain.ifBlank { "正在分析…" },
                    fontSize = 15.sp,
                    color = ChatComposeTheme.title,
                )
            } else {
                Markdown(
                    state = markdownState,
                    colors = chatMarkdownColors(),
                    typography = chatMarkdownTypography(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "思考中",
            fontSize = 15.sp,
            color = ChatComposeTheme.placeholder,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Canvas(modifier = Modifier.size(width = 28.dp, height = 14.dp)) {
            val radius = 2.4.dp.toPx()
            val gap = 8.dp.toPx()
            val liftMax = 3.5.dp.toPx()
            val cy = size.height / 2f
            for (index in 0..2) {
                val local = (phase - index * 0.18f).mod(1f)
                val lift = if (local < 0.5f) {
                    sin(local * 2f * PI).toFloat()
                } else {
                    0f
                }
                drawCircle(
                    color = ChatComposeTheme.accent.copy(alpha = 0.35f + 0.65f * lift),
                    radius = radius * (0.85f + 0.3f * lift),
                    center = Offset(
                        x = radius + index * gap,
                        y = cy - lift * liftMax,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssistantCardsRow(
    message: ChatMessage,
    first: Boolean,
    showFollowUps: Boolean,
    askEnabled: Boolean,
    pageViewWidth: Float,
    onAsk: (String) -> Unit,
    onOpenKline: (ChatMessage, String) -> Unit,
) {
    val listed = message.listedTargets()
    val picks = message.relatedPicks
    if (listed.isEmpty() && picks.isEmpty()) {
        return
    }
    val maxWidth = (pageViewWidth - 40f).dp
    val shape = RoundedCornerShape(
        topStart = if (first) 12.dp else 0.dp,
        topEnd = if (first) 12.dp else 0.dp,
        bottomEnd = 12.dp,
        bottomStart = 12.dp,
    )
    val followUps = if (!showFollowUps) {
        emptyList()
    } else if (listed.isNotEmpty()) {
        FollowUpPrompts.forTargets(listed)
    } else {
        FollowUpPrompts.forRelatedPicks(picks)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 16.dp,
                top = if (first) 8.dp else 0.dp,
                bottom = if (followUps.isNotEmpty()) 10.dp else 4.dp,
            ),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(ChatComposeTheme.surface)
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AssistantTargetCards(
                        message = message,
                        listed = listed,
                        onOpenKline = onOpenKline,
                    )
                    if (picks.isNotEmpty()) {
                        if (listed.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        RelatedPickChips(
                            picks = picks,
                            quotes = message.targetQuotes,
                            onOpen = { stock -> onOpenKline(message, stock.code) },
                        )
                    }
                }
            }
            if (followUps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    followUps.forEach { prompt ->
                        FollowUpChip(
                            label = prompt.label,
                            enabled = askEnabled,
                            onClick = { onAsk(prompt.question) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantTargetCards(
    message: ChatMessage,
    listed: List<TargetStock>,
    onOpenKline: (ChatMessage, String) -> Unit,
) {
    if (listed.isEmpty()) {
        return
    }
    if (listed.size >= 2) {
        ComparisonPairRow(
            left = listed[0],
            leftQuote = message.quoteFor(listed[0].code),
            leftBars = message.klineFor(listed[0].code),
            right = listed[1],
            rightQuote = message.quoteFor(listed[1].code),
            rightBars = message.klineFor(listed[1].code),
            onOpen = { code -> onOpenKline(message, code) },
        )
        listed.drop(2).forEach { stock ->
            val quote = message.quoteFor(stock.code)
            TargetStockChip(
                name = stock.name,
                code = stock.code,
                changeText = listOfNotNull(
                    quote?.change?.takeIf { it.isNotBlank() },
                    quote?.changePercent?.takeIf { it.isNotBlank() },
                ).joinToString("  "),
                onClick = { onOpenKline(message, stock.code) },
            )
        }
        return
    }
    val stock = listed.first()
    val quote = message.quoteFor(stock.code)
    val bars = message.klineFor(stock.code)
    TargetQuoteBlock(
        quote = quote ?: StockQuote(
            code = stock.code,
            market = stock.market,
            name = stock.name,
            price = "—",
            prevClose = "",
            change = "",
            changePercent = "",
            amount = "—",
            time = "",
        ),
        bars = bars,
        onClick = { onOpenKline(message, stock.code) },
    )
    if (stock.isIndex && quote != null) {
        Spacer(modifier = Modifier.height(10.dp))
        IndexBreadthRow(quote = quote)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedPickChips(
    picks: List<TargetStock>,
    quotes: Map<String, StockQuote>,
    onOpen: (TargetStock) -> Unit,
) {
    Text(
        text = "相关标的",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = ChatComposeTheme.placeholder,
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        picks.forEach { stock ->
            val quote = quotes[stock.code]
            val change = quote?.changePercent.orEmpty()
            val color = when {
                change.startsWith("+") -> ChatComposeTheme.rise
                change.startsWith("-") -> ChatComposeTheme.fall
                else -> ChatComposeTheme.title
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(ChatComposeTheme.contentBg)
                    .clickable { onOpen(stock) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (change.isBlank()) stock.name else "${stock.name}  $change",
                    fontSize = 13.sp,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun FollowUpChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = if (enabled) ChatComposeTheme.title else ChatComposeTheme.placeholder
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ChatComposeTheme.surface)
            .border(1.dp, ChatComposeTheme.hairline, RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = color,
        )
    }
}

@Composable
internal fun ChatBubbleRow(
    message: ChatMessage,
    sending: Boolean,
    pageViewWidth: Float,
    onRetry: (String) -> Unit,
) {
    val maxWidth = (pageViewWidth - 40f).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        horizontalArrangement = if (message.fromUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
    ) {
        if (message.fromUser) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChatComposeTheme.accent)
                    .padding(12.dp)
            ) {
                Text(
                    text = message.body,
                    fontSize = 15.sp,
                    color = ChatComposeTheme.white,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChatComposeTheme.surface)
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (message.failed) {
                        Text(
                            text = message.body.ifBlank { "请求失败" },
                            fontSize = 15.sp,
                            color = ChatComposeTheme.title,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (sending) "正在重试…" else "重试",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (sending) {
                                ChatComposeTheme.placeholder
                            } else {
                                ChatComposeTheme.accent
                            },
                            modifier = if (sending) {
                                Modifier
                            } else {
                                Modifier.clickable { onRetry(message.id) }
                            },
                        )
                    } else {
                        val live = message.body.ifBlank { "正在分析…" }
                        val plain = RelatedStockParser.streamPlainText(live)
                        Text(
                            text = plain,
                            fontSize = 15.sp,
                            color = ChatComposeTheme.title,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetQuoteBlock(
    quote: StockQuote,
    bars: List<KLineBar> = emptyList(),
    onClick: () -> Unit,
) {
    val changeColor = when {
        quote.change.startsWith("+") -> ChatComposeTheme.rise
        quote.change.startsWith("-") -> ChatComposeTheme.fall
        else -> ChatComposeTheme.title
    }
    val rising = !quote.change.startsWith("-")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ChatComposeTheme.contentBg)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val title = quote.name.trim().ifBlank { quote.code }
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChatComposeTheme.title,
                )
                if (quote.name.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = quote.code,
                        fontSize = 13.sp,
                        color = ChatComposeTheme.placeholder,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = quote.price,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = changeColor,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${quote.change}  ${quote.changePercent}",
                        fontSize = 13.sp,
                        color = changeColor,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .height(40.dp),
            ) {
                if (bars.size >= 2) {
                    QuoteSparkline(
                        bars = bars,
                        rising = rising,
                        modifier = Modifier
                            .width(88.dp)
                            .height(40.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "成交额  ${quote.amount}",
            fontSize = 12.sp,
            color = ChatComposeTheme.placeholder,
        )
        if (quote.time.isNotBlank()) {
            Text(
                text = quote.time,
                fontSize = 12.sp,
                color = ChatComposeTheme.placeholder,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "查看详情 ›",
            fontSize = 11.sp,
            color = ChatComposeTheme.accent,
        )
    }
}
