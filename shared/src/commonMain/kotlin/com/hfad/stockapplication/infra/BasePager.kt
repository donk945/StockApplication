package com.hfad.stockapplication.infra

import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 传统 Kuikly DSL 页基类（路由调试页等）：注册 Bridge / SSE，并同步夜间模式。
 */
internal abstract class BasePager : Pager() {
    private val nightMode = NightModeState()
    /** DSL 页靠 observable 触发重绘。 */
    private var nightTick by observable(0)

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
        nightTick += 1
    }

    override fun isNightMode(): Boolean {
        val tick = nightTick
        return nightMode.isNight(pageData.params) && tick >= 0
    }

    override fun debugUIInspector(): Boolean {
        return false
    }
}
