package com.hfad.stockapplication.data.chat

/**
 * 聊天相关数据访问。页面与状态层只依赖此接口，不关心本地 mock 还是网络。
 */
interface ChatRepository {
    /** 加载全部历史摘要（按更新时间倒序）。 */
    fun loadHistories(): List<ChatHistoryItem>

    /** 按关键字过滤历史标题（忽略大小写，空关键字返回全部）。 */
    fun searchHistories(keyword: String): List<ChatHistoryItem>

    fun loadSession(id: String): ChatSession?

    fun saveSession(session: ChatSession)

    fun deleteSession(id: String)

    fun clearAll()
}
