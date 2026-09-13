package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.debug.AgentDebugLog
import com.hfad.stockapplication.data.chat.ChartAskContext
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * 输入框外面上方的选点卡片。发送或点 × 后由调用方清掉。
 */
@Composable
fun ChartAskCard(
    context: ChartAskContext,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bar = context.bar
    // #region agent log
    LaunchedEffect(context.code, bar.day, context.kind) {
        AgentDebugLog.emit(
            "G",
            "ChartAskCard",
            "show",
            mapOf(
                "kind" to context.kind.name,
                "code" to context.code,
                "day" to bar.day,
                "close" to bar.close,
            ),
            runId = "chart-ask",
        )
    }
    // #endregion
    val open = bar.open.toDoubleOrNull()
    val close = bar.close.toDoubleOrNull()
    val baseline = context.baseline.toDoubleOrNull() ?: open
    val change = barChangeText(baseline, close)
    val changeColor = when {
        baseline == null || close == null -> ChatComposeTheme.title
        close >= baseline -> ChatComposeTheme.rise
        else -> ChatComposeTheme.fall
    }
    val kindLabel = when (context.kind) {
        ChartAskContext.Kind.Daily -> "日K"
        ChartAskContext.Kind.Minute -> "分时"
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ChatComposeTheme.surface)
            .border(0.5.dp, ChatComposeTheme.hairline, shape)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = context.name.ifBlank { context.code }.ifBlank { "目标股" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChatComposeTheme.title,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = kindLabel,
                    fontSize = 11.sp,
                    color = ChatComposeTheme.placeholder,
                )
            }
            Text(
                text = bar.day.ifBlank { "该点" },
                fontSize = 11.sp,
                color = ChatComposeTheme.placeholder,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = bar.close,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = changeColor,
                )
                if (change.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = change,
                        fontSize = 12.sp,
                        color = changeColor,
                    )
                }
            }
            Text(
                text = buildString {
                    append("开  ${bar.open}    高  ${bar.high}")
                    append("    低  ${bar.low}")
                    if (bar.volume.isNotBlank()) {
                        append("    量  ${bar.volume}")
                    }
                },
                fontSize = 11.sp,
                color = ChatComposeTheme.title,
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", fontSize = 18.sp, color = ChatComposeTheme.placeholder)
        }
    }
}
