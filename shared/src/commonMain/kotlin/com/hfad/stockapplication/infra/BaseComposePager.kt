package com.hfad.stockapplication.infra

import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * Compose DSL 业务页基类：注册 Bridge / SSE，并同步宿主夜间模式。
 */
internal abstract class BaseComposePager : ComposeContainer() {
    private val nightMode = NightModeState()

    override fun createExternalModules(): Map<String, Module>? {
        return mapOf(
            BridgeModule.MODULE_NAME to BridgeModule(),
            SseModule.MODULE_NAME to SseModule(),
        )
    }

    override fun created() {
        super.created()
        isNightMode()
    }

    override fun themeDidChanged(data: JSONObject) {
        super.themeDidChanged(data)
        nightMode.applyTheme(data)
    }

    override fun isNightMode(): Boolean {
        return nightMode.isNight(pageData.params)
    }
}
