package com.hfad.stockapplication.page.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.hfad.stockapplication.data.chat.ThemeMode
import com.tencent.kuikly.compose.BackHandler
import com.tencent.kuikly.compose.extension.keyboardHeightChange
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.ColumnScope
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.TextField
import com.tencent.kuikly.compose.material3.TextFieldDefaults
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun AccountPane(
    displayName: String,
    historyCount: Int,
    bottomInset: Float,
    statusBarHeight: Float,
    onBack: () -> Unit,
    onSaveName: (String) -> Unit,
    onCloseKeyboard: () -> Unit,
) {
    var nameDraft by remember { mutableStateOf(displayName) }
    var keyboardHeight by remember { mutableStateOf(0f) }
    ProfileScaffold(
        title = "账号",
        bottomInset = maxOf(bottomInset, keyboardHeight),
        statusBarHeight = statusBarHeight,
        onBack = {
            if (keyboardHeight > 0f) {
                onCloseKeyboard()
                keyboardHeight = 0f
            } else {
                onBack()
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AccountAvatar(name = nameDraft.ifBlank { displayName }, size = 64f)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "本机资料，对话只保存在这台手机",
                fontSize = 13.sp,
                color = ChatComposeTheme.placeholder,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        SettingsCard {
            Text("昵称", fontSize = 13.sp, color = ChatComposeTheme.placeholder)
            TextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .keyboardHeightChange { keyboardHeight = it.height },
                placeholder = {
                    Text("怎么称呼你", color = ChatComposeTheme.placeholder)
                },
                singleLine = true,
                colors = profileFieldColors(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            PrimaryButton(
                label = "保存昵称",
                enabled = nameDraft.trim().isNotEmpty(),
                onClick = { onSaveName(nameDraft) },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Text("对话", fontSize = 13.sp, color = ChatComposeTheme.placeholder)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (historyCount > 0) "已保存 ${historyCount} 段对话" else "还没有历史对话",
                fontSize = 15.sp,
                color = ChatComposeTheme.title,
            )
        }
    }
}

@Composable
internal fun SettingsPane(
    apiKeyStatus: String,
    usingCustomKey: Boolean,
    hasBuiltinKey: Boolean,
    historyCount: Int,
    themeMode: ThemeMode,
    bottomInset: Float,
    statusBarHeight: Float,
    onBack: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearCustomKey: () -> Unit,
    onClearHistory: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onCloseKeyboard: () -> Unit,
) {
    var keyDraft by remember { mutableStateOf("") }
    var keyboardHeight by remember { mutableStateOf(0f) }
    var pendingClear by remember { mutableStateOf(false) }
    LaunchedEffect(pendingClear) {
        if (!pendingClear) {
            return@LaunchedEffect
        }
        delay(3_000)
        pendingClear = false
    }
    ProfileScaffold(
        title = "设置",
        bottomInset = maxOf(bottomInset, keyboardHeight),
        statusBarHeight = statusBarHeight,
        onBack = {
            when {
                pendingClear -> pendingClear = false
                keyboardHeight > 0f -> {
                    onCloseKeyboard()
                    keyboardHeight = 0f
                }
                else -> onBack()
            }
        },
    ) {
        SettingsCard {
            Text("外观", fontSize = 13.sp, color = ChatComposeTheme.placeholder)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FeaturePill(
                        label = mode.label,
                        enabled = themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Text("DeepSeek API Key", fontSize = 13.sp, color = ChatComposeTheme.placeholder)
            Spacer(modifier = Modifier.height(4.dp))
            Text(apiKeyStatus, fontSize = 15.sp, color = ChatComposeTheme.title)
            TextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .keyboardHeightChange { keyboardHeight = it.height },
                placeholder = {
                    Text("填入则覆盖内置 Key", color = ChatComposeTheme.placeholder, fontSize = 14.sp)
                },
                singleLine = true,
                colors = profileFieldColors(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            PrimaryButton(
                label = "保存自定义 Key",
                enabled = keyDraft.trim().isNotEmpty(),
                onClick = {
                    onSaveApiKey(keyDraft)
                    keyDraft = ""
                },
            )
            if (usingCustomKey && hasBuiltinKey) {
                Spacer(modifier = Modifier.height(8.dp))
                TextAction(
                    label = "改用内置 Key",
                    color = ChatComposeTheme.accent,
                    onClick = {
                        keyDraft = ""
                        onClearCustomKey()
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Text("数据", fontSize = 13.sp, color = ChatComposeTheme.placeholder)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (historyCount > 0) "本机共 ${historyCount} 段对话" else "没有可清空的对话",
                fontSize = 15.sp,
                color = ChatComposeTheme.title,
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (pendingClear) {
                PrimaryButton(
                    label = "再点一次确认清空",
                    enabled = true,
                    danger = true,
                    onClick = {
                        pendingClear = false
                        onClearHistory()
                    },
                )
            } else {
                TextAction(
                    label = "清空全部对话",
                    color = if (historyCount > 0) ChatComposeTheme.rise else ChatComposeTheme.placeholder,
                    onClick = {
                        if (historyCount > 0) {
                            pendingClear = true
                        }
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Text("关于", fontSize = 13.sp, color = ChatComposeTheme.placeholder)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "内容由 AI 生成，仅供参考，不构成投资建议。行情来自腾讯接口，可能有延迟。",
                fontSize = 14.sp,
                color = ChatComposeTheme.title,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("股票问答  1.0", fontSize = 12.sp, color = ChatComposeTheme.placeholder)
        }
    }
}

@Composable
private fun ProfileScaffold(
    title: String,
    bottomInset: Float,
    statusBarHeight: Float,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatComposeTheme.pageBg)
            .padding(bottom = bottomInset.dp),
    ) {
        Spacer(modifier = Modifier.height(statusBarHeight.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable { onBack() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("返回", fontSize = 16.sp, color = ChatComposeTheme.accent)
            }
            Text(
                text = title,
                modifier = Modifier.align(Alignment.Center),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChatComposeTheme.title,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            content = content,
        )
    }
}

@Composable
internal fun AccountAvatar(name: String, size: Float) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(ChatComposeTheme.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.toString() ?: "?",
            fontSize = if (size >= 56f) 22.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChatComposeTheme.white,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ChatComposeTheme.contentBg)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> ChatComposeTheme.accentDisabled
        danger -> ChatComposeTheme.rise
        else -> ChatComposeTheme.accent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChatComposeTheme.white,
        )
    }
}

@Composable
private fun TextAction(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, color = color)
    }
}

@Composable
private fun profileFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = ChatComposeTheme.surface,
    unfocusedContainerColor = ChatComposeTheme.surface,
    disabledContainerColor = ChatComposeTheme.surface,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedTextColor = ChatComposeTheme.title,
    unfocusedTextColor = ChatComposeTheme.title,
    cursorColor = ChatComposeTheme.accent,
)
