package com.hfad.stockapplication.page.chat

import com.hfad.stockapplication.infra.BuiltinApiKey
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** 宿主 page 参数：DeepSeek API Key。 */
internal const val KEY_DEEPSEEK_API_KEY = "deepseekApiKey"

/** 优先用宿主注入的 Key，否则用 `key.properties` / `local.properties` 编进 shared 的内置 Key。 */
internal fun deepSeekKeyFromPager(params: JSONObject): String {
    val fromHost = params.optString(KEY_DEEPSEEK_API_KEY).orEmpty().trim()
    if (fromHost.isNotBlank()) {
        return fromHost
    }
    return BuiltinApiKey.DEEPSEEK.trim()
}

internal const val INPUT_PLACEHOLDER = "输入你的问题"

/** 系统未回报底部 inset 时，Android 手势小白条的兜底高度（dp）。 */
internal const val ANDROID_HOME_INDICATOR_DP = 24f

internal val SUGGESTED_PROMPTS = listOf(
    "宁德时代现在怎么样",
    "上证怎么看",
    "光伏板块谁更强",
)
