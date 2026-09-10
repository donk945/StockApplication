package com.hfad.stockapplication.infra

import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Kuikly `@Page` 路由名。宿主 `KRRouterAdapter` 按此打开新页面。 */
object AppPages {
    const val CHAT = "stock_chat"
    const val QUOTES = "stock_quotes"
    const val DETAIL = "stock_detail"
}

/** 详情页 `pageData` 字段，与问答页卡片跳转对齐。 */
object DetailParams {
    const val CODE = "code"
    const val NAME = "name"
    const val MARKET = "market"
    const val IS_INDEX = "isIndex"
    const val SUMMARY = "summary"
    const val INDUSTRY = "industry"
    const val SUPPLY = "supply"
    const val PEERS = "peers"
}

internal fun BaseComposePager.openAppPage(pageName: String, pageData: JSONObject = JSONObject()) {
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(pageName, pageData)
}

internal fun BaseComposePager.closeAppPage() {
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
}

internal fun BaseComposePager.systemBottomInset(): Float {
    val reported = maxOf(
        pagerData.safeAreaInsets.bottom,
        pagerData.androidBottomBavBarHeight,
    )
    if (reported > 0f) {
        return reported
    }
    return if (pagerData.isAndroid) 24f else 0f
}
