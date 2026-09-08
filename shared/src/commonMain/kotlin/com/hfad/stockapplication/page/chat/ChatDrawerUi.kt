package com.hfad.stockapplication.page.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hfad.stockapplication.component.drawer.DrawerItem
import com.hfad.stockapplication.component.theme.ChatComposeTheme
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxHeight
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.TextField
import com.tencent.kuikly.compose.material3.TextFieldDefaults
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

@Composable
internal fun ChatSideDrawer(
    items: List<DrawerItem>,
    empty: Boolean,
    displayName: String,
    historyCount: Int,
    onSearch: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
    statusBarHeight: Float,
    bottomInset: Float,
) {
    var query by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ChatComposeTheme.dim)
                .clickable { onClose() }
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.78f)
                .background(ChatComposeTheme.surface)
                .clickable { }
        ) {
            Spacer(modifier = Modifier.height((statusBarHeight + 8f).dp))
            TextField(
                value = query,
                onValueChange = { text ->
                    query = text
                    onSearch(text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                placeholder = {
                    Text("搜索历史记录", color = ChatComposeTheme.placeholder, fontSize = 14.sp)
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ChatComposeTheme.inputBg,
                    unfocusedContainerColor = ChatComposeTheme.inputBg,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            if (empty) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无历史记录", fontSize = 14.sp, color = ChatComposeTheme.placeholder)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(items, key = { it.id }) { item ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { onSelect(item.id) },
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        item.title,
                                        fontSize = 15.sp,
                                        color = ChatComposeTheme.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clickable { onDelete(item.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "×",
                                        fontSize = 18.sp,
                                        color = ChatComposeTheme.placeholder,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(ChatComposeTheme.hairline)
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(ChatComposeTheme.hairline)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 8.dp,
                        top = 10.dp,
                        bottom = (bottomInset + 8f).dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onAccount() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountAvatar(name = displayName, size = 36f)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            displayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChatComposeTheme.title,
                        )
                        Text(
                            if (historyCount > 0) {
                                "本机 · ${historyCount} 段对话"
                            } else {
                                "本机账号"
                            },
                            fontSize = 12.sp,
                            color = ChatComposeTheme.placeholder,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSettings() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("设置", fontSize = 14.sp, color = ChatComposeTheme.accent)
                }
            }
        }
    }
}
