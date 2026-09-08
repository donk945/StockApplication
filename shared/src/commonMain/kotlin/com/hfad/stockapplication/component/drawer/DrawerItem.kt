package com.hfad.stockapplication.component.drawer

/**
 * 抽屉历史行，仅 id + 标题，不依赖业务会话模型。
 */
data class DrawerItem(
    val id: String,
    val title: String,
)
