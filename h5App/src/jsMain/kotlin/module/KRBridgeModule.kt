package com.hfad.stockapplication.h5.module

import com.hfad.stockapplication.h5.utils.Ui
import com.tencent.kuikly.core.render.web.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.web.ktx.KuiklyRenderCallback
import com.tencent.kuikly.core.render.web.nvi.serialization.json.JSONException
import com.tencent.kuikly.core.render.web.nvi.serialization.json.JSONObject
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import kotlin.js.Date

class KRBridgeModule : KuiklyRenderBaseModule() {
    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "toast" -> {
                toast(params)
                Unit
            }
            "log" -> {
                console.log(params)
                Unit
            }
            "copyToPasteboard" -> {
                copyToPasteboard(params)
                Unit
            }
            "closeKeyboard" -> {
                closeKeyboard()
                ""
            }
            "setNightBars" -> {
                setNightBars(params)
                Unit
            }
            "closePage" -> {
                window.close()
                Unit
            }
            "currentTimestamp" -> currentTimestamp()
            "dateFormatter" -> dateFormatter(params)
            "localServeTime" -> {
                callback?.invoke(mapOf("time" to Date.now() / 1000.0))
                Unit
            }
            else -> {
                callback?.invoke(mapOf("code" to -1, "message" to "方法不存在"))
                Unit
            }
        }
    }

    private fun toast(params: String?) {
        if (params == null) {
            return
        }
        try {
            Ui.showToast(JSONObject(params))
        } catch (e: JSONException) {
            console.error("toast json parse error", e)
        }
    }

    private fun copyToPasteboard(params: String?) {
        if (params == null) {
            return
        }
        val content = JSONObject(params).optString("content")
        if (content.isNotEmpty()) {
        try {
            window.navigator.clipboard.writeText(content)
        } catch (_: dynamic) {
        }
        }
    }

    private fun closeKeyboard(): String {
        val active = document.activeElement as? HTMLElement
        active?.blur()
        return ""
    }

    private fun setNightBars(params: String?) {
        val night = JSONObject(params ?: "{}").optInt("night", 0) == 1
        val bg = if (night) "#111113" else "#ffffff"
        document.documentElement?.setAttribute("style", "color-scheme:${if (night) "dark" else "light"}")
        document.body?.asDynamic()?.style?.background = bg
    }

    private fun currentTimestamp(): String = Date.now().toString()

    private fun dateFormatter(params: String?): String {
        val paramJSONObject = JSONObject(params ?: "{}")
        val date = Date(paramJSONObject.optLong("timeStamp").toDouble())
        return formatDate(date, paramJSONObject.optString("format"))
    }

    private fun formatDate(date: Date, format: String): String {
        fun pad(num: Int) = num.toString().padStart(2, '0')
        val replacements = mapOf(
            "yyyy" to date.getFullYear().toString(),
            "MM" to pad(date.getMonth() + 1),
            "dd" to pad(date.getDate()),
            "HH" to pad(date.getHours()),
            "mm" to pad(date.getMinutes()),
            "ss" to pad(date.getSeconds()),
        )
        var result = format
        for ((k, v) in replacements) {
            result = result.replace(k, v)
        }
        return result
    }

    companion object {
        const val MODULE_NAME = "HRBridgeModule"
    }
}
