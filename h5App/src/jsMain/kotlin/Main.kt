package com.hfad.stockapplication.h5

import com.hfad.stockapplication.h5.utils.URL
import com.tencent.kuikly.core.render.web.ktx.SizeI
import kotlinx.browser.document
import kotlinx.browser.window

private const val DEFAULT_PAGE = "stock_chat"

fun main() {
    val urlParams = URL.parseParams(window.location.href)
    val pageName = urlParams["page_name"] ?: DEFAULT_PAGE
    val containerWidth = window.innerWidth
    val containerHeight = window.innerHeight
    val night = window.matchMedia("(prefers-color-scheme: dark)").matches
    val params = urlParams.toMutableMap()
    params["is_H5"] = "1"
    document.title = "股票问答"
    if (!params.containsKey("isNightMode")) {
        params["isNightMode"] = if (night) "1" else "0"
    }
    if (!params.containsKey("deepseekApiKey") || params["deepseekApiKey"].isNullOrBlank()) {
        params["deepseekApiKey"] = HostBuiltinKey.DEEPSEEK
    }
    val paramMap: Map<String, Any> = mapOf(
        "statusBarHeight" to 0f,
        "activityWidth" to containerWidth,
        "activityHeight" to containerHeight,
        "param" to params.toMap(),
    )
    val delegator = KuiklyWebRenderViewDelegator()
    delegator.init("root", pageName, paramMap, SizeI(containerWidth, containerHeight))
    delegator.resume()
    document.addEventListener("visibilitychange", {
        val hidden = document.asDynamic().hidden as Boolean
        if (hidden) {
            delegator.pause()
        } else {
            delegator.resume()
        }
    })
}
