package com.hfad.stockapplication.h5.module

import com.tencent.kuikly.core.render.web.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.web.ktx.KuiklyRenderCallback
import com.tencent.kuikly.core.render.web.ktx.toJSONObjectSafely
import kotlinx.browser.window

class KRCacheModule : KuiklyRenderBaseModule() {
    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            GET_ITEM -> getItem(params)
            SET_ITEM -> {
                setItem(params)
                Unit
            }
            else -> super.call(method, params, callback)
        }
    }

    private fun getItem(key: String?): String? {
        if (key == null) {
            return null
        }
        return try {
            window.localStorage.getItem(key)
        } catch (e: dynamic) {
            ""
        }
    }

    private fun setItem(params: String?) {
        val json = params.toJSONObjectSafely()
        try {
            window.localStorage.setItem(json.optString("key"), json.optString("value"))
        } catch (e: dynamic) {
            console.error("localStorage set error", e)
        }
    }

    companion object {
        const val MODULE_NAME = "HRCacheModule"
        private const val GET_ITEM = "getItem"
        private const val SET_ITEM = "setItem"
    }
}
