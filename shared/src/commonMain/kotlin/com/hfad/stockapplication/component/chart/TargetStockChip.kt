package com.hfad.stockapplication.component.chart

import androidx.compose.runtime.Composable
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.heightIn
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * 目标股入口芯片：名称 + 代码 + ›，整行可点。热区至少 48.dp。
 */
@Composable
fun TargetStockChip(
    name: String,
    code: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    changeText: String = "",
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .clip(RoundedCornerShape(8.dp))
        .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 4.dp, vertical = 6.dp)
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) ChatComposeTheme.accent else ChatComposeTheme.title,
            )
            val detail = listOf(code, changeText).filter { it.isNotBlank() }.joinToString("  ")
            if (detail.isNotBlank()) {
                val changeColor = when {
                    changeText.startsWith("+") -> ChatComposeTheme.rise
                    changeText.startsWith("-") -> ChatComposeTheme.fall
                    else -> ChatComposeTheme.placeholder
                }
                Text(
                    text = detail,
                    fontSize = 12.sp,
                    color = if (changeText.isNotBlank()) changeColor else ChatComposeTheme.placeholder,
                )
            }
        }
        if (enabled) {
            Text(
                text = "›",
                fontSize = 20.sp,
                color = ChatComposeTheme.accent,
            )
        }
    }
}
