package com.hfad.stockapplication.page.chat

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.component.theme.LocalChatColors
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

@Composable
internal fun ChatTitleBar(
    title: String,
    showTitle: Boolean,
    onMenuClick: () -> Unit,
    onNewChat: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(ChatComposeTheme.pageBg)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .clickable { onMenuClick() },
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(1.8.dp)
                        .background(ChatComposeTheme.iconLine, RoundedCornerShape(1.dp))
                )
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(1.8.dp)
                        .background(ChatComposeTheme.iconLine, RoundedCornerShape(1.dp))
                )
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(1.8.dp)
                        .background(ChatComposeTheme.iconLine, RoundedCornerShape(1.dp))
                )
            }
        }
        if (showTitle) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChatComposeTheme.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(48.dp)
                .clickable { onNewChat() },
            contentAlignment = Alignment.Center,
        ) {
            NewChatIcon()
        }
    }
}

@Composable
internal fun ChatEmptyHome(
    onPrompt: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatComposeTheme.pageBg)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DeepSeekLogo()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "AI 投研助手",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChatComposeTheme.title,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "问行情、公司或投资概念",
            fontSize = 13.sp,
            color = ChatComposeTheme.placeholder,
        )
        Spacer(modifier = Modifier.height(20.dp))
        SUGGESTED_PROMPTS.forEach { prompt ->
            SuggestionChip(
                label = prompt,
                onClick = { onPrompt(prompt) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "内容由 AI 生成，仅供参考，不构成投资建议",
            fontSize = 11.sp,
            color = ChatComposeTheme.placeholder,
        )
    }
}

@Composable
internal fun FeaturePill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (enabled) ChatComposeTheme.iconLine else ChatComposeTheme.placeholder
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(ChatComposeTheme.surface)
            .border(
                width = 1.dp,
                color = ChatComposeTheme.hairline,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = color,
        )
    }
}

@Composable
internal fun SuggestionChip(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(ChatComposeTheme.surface)
            .border(1.dp, ChatComposeTheme.hairline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = ChatComposeTheme.title,
        )
    }
}

@Composable
internal fun NewChatIcon() {
    val line = LocalChatColors.current.iconLine
    Box(
        modifier = Modifier.size(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 1.dp, vertical = 3.dp)
                .border(1.8.dp, line, RoundedCornerShape(5.dp)),
        )
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(1.8.dp)
                .background(line, RoundedCornerShape(1.dp)),
        )
        Box(
            modifier = Modifier
                .width(1.8.dp)
                .height(8.dp)
                .background(line, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
internal fun DeepSeekLogo() {
    val accent = LocalChatColors.current.accent
    val onAccent = LocalChatColors.current.onAccent
    Canvas(modifier = Modifier.size(36.dp)) {
        drawCircle(color = accent, radius = size.minDimension / 2f)
        drawCircle(
            color = onAccent,
            radius = 4.dp.toPx(),
            center = Offset(size.width * 0.62f, size.height * 0.38f),
        )
        drawCircle(
            color = accent,
            radius = 2.dp.toPx(),
            center = Offset(size.width * 0.64f, size.height * 0.38f),
        )
    }
}
