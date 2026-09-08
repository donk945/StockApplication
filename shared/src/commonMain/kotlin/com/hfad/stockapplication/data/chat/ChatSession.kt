package com.hfad.stockapplication.data.chat

/**
 * 一次完整会话：标题 + 气泡，可持久化。
 */
data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val updatedAt: Long,
)
