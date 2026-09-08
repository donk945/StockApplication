package com.hfad.stockapplication.infra

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 宿主夜间模式：Compose Page 与传统 DSL Page 共用，避免两套字段。
 */
internal class NightModeState {
    private var nightModel: Boolean? = null

    fun applyTheme(data: JSONObject) {
        nightModel = data.optBoolean(IS_NIGHT_MODE_KEY)
    }

    fun isNight(params: JSONObject): Boolean {
        if (nightModel == null) {
            nightModel = params.optBoolean(IS_NIGHT_MODE_KEY)
        }
        return nightModel == true
    }

    companion object {
        const val IS_NIGHT_MODE_KEY = "isNightMode"
    }
}
