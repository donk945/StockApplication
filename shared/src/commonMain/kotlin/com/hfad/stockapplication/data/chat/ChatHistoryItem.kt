package com.hfad.stockapplication.data.chat

/**
 * 聊天历史条目（数据层模型，不依赖 UI）。
 *
 * @param id 稳定唯一标识，便于后续 diff / 持久化
 * @param title 展示用标题
 */
data class ChatHistoryItem(
    val id: String,
    val title: String,
)
