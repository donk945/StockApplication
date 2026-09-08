package com.hfad.stockapplication.page.chat

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.chart.ChartAskCard
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.ChartAskContext
import com.hfad.stockapplication.debug.AgentDebugLog
import com.tencent.kuikly.compose.extension.keyboardHeightChange
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.layout.widthIn
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.TextField
import com.tencent.kuikly.compose.material3.TextFieldDefaults
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.draw.shadow
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

@Composable
internal fun ChatComposer(
    draft: String,
    sending: Boolean,
    canSend: Boolean,
    canQuoteLatest: Boolean,
    canComparePeers: Boolean,
    chartAsk: ChartAskContext?,
    bottomInset: Float,
    onDraftChange: (String) -> Unit,
    onKeyboardHeight: (Float) -> Unit,
    onQuoteLatest: () -> Unit,
    onComparePeers: () -> Unit,
    onClearChartAsk: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val cardShape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChatComposeTheme.pageBg)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomInset.dp),
    ) {
        if (chartAsk != null) {
            ChartAskCard(
                context = chartAsk,
                onClear = onClearChartAsk,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, cardShape)
                .clip(cardShape)
                .background(ChatComposeTheme.surface)
                .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
        ) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 0.dp)
                    .keyboardHeightChange {
                        onKeyboardHeight(it.height)
                        // #region agent log
                        AgentDebugLog.emit(
                            "A",
                            "StockChatPage.composer",
                            "kb-height",
                            mapOf(
                                "h" to it.height.toString(),
                                "dur" to it.duration.toString(),
                                "sending" to sending.toString(),
                            ),
                        )
                        // #endregion
                    },
                placeholder = {
                    Text(
                        text = INPUT_PLACEHOLDER,
                        color = ChatComposeTheme.placeholder,
                        fontSize = 16.sp,
                    )
                },
                singleLine = false,
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = ChatComposeTheme.title,
                    unfocusedTextColor = ChatComposeTheme.title,
                    disabledTextColor = ChatComposeTheme.title,
                    cursorColor = ChatComposeTheme.accent,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeaturePill(
                    label = "引用最新",
                    enabled = canQuoteLatest && !sending,
                    onClick = onQuoteLatest,
                )
                Spacer(modifier = Modifier.width(8.dp))
                FeaturePill(
                    label = "同业对比",
                    enabled = canComparePeers && !sending,
                    onClick = onComparePeers,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (sending) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ChatComposeTheme.title)
                            .clickable(onClick = onStop),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ChatComposeTheme.surface),
                        )
                    }
                } else {
                    val sendModifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) ChatComposeTheme.accent else Color(0xFFC7C7CC),
                        )
                    Box(
                        modifier = if (canSend) {
                            sendModifier.clickable(onClick = onSend)
                        } else {
                            sendModifier
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("↑", fontSize = 18.sp, color = ChatComposeTheme.white)
                    }
                }
            }
        }
    }
}
