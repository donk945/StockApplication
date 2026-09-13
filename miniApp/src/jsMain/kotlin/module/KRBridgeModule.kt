package com.hfad.stockapplication.miniapp.module

import com.tencent.kuikly.core.render.web.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.web.ktx.KuiklyRenderCallback
import com.tencent.kuikly.core.render.web.nvi.serialization.json.JSONException
import com.tencent.kuikly.core.render.web.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.render.web.runtime.miniapp.MiniGlobal
import com.tencent.kuikly.core.render.web.runtime.miniapp.core.NativeApi
import com.tencent.kuikly.core.render.web.utils.Log
import kotlin.js.Date
import kotlin.js.json

class KRBridgeModule : KuiklyRenderBaseModule() {
    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "toast" -> showToast(params)
            "log" -> {
                params?.let { Log.log(it) }
            }
            "copyToPasteboard" -> copyToPasteboard(params)
            "closeKeyboard" -> {
                NativeApi.plat.hideKeyboard(json())
                ""
            }
            "setNightBars" -> setNightBars(params)
            "closePage" -> {
                NativeApi.plat.navigateBack(json())
                Unit
            }
            "debugMark" -> ""
            "loadDebugMark" -> ""
            "currentTimestamp" -> Date.now().toString()
            "dateFormatter" -> dateFormatter(params)
            "localServeTime" -> {
                callback?.invoke(mapOf("time" to Date.now() / 1000.0))
                Unit
            }
            "readAssetFile" -> {
                val data = MiniGlobal.globalThis.getAssetJson(js("JSON.parse")(params).assetPath)
                callback?.invoke(mapOf("result" to JSON.stringify(data)))
            }
            else -> {
                Log.error("$method not found")
                callback?.invoke("{}")
            }
        }
    }

    private fun showToast(params: String?) {
        if (params == null) {
            return
        }
        try {
            val data = JSONObject(params)
            NativeApi.plat.showToast(
                json(
                    "title" to data.optString("content"),
                    "icon" to "none",
                )
            )
        } catch (e: JSONException) {
            console.error("toast json parse error", e)
        }
    }

    private fun copyToPasteboard(params: String?) {
        if (params == null) {
            return
        }
        val content = JSONObject(params).optString("content")
        NativeApi.plat.setClipboardData(json("data" to content))
    }

    private fun setNightBars(params: String?) {
        val night = JSONObject(params ?: "{}").optInt("night", 0) == 1
        NativeApi.plat.setNavigationBarColor(
            json(
                "frontColor" to if (night) "#ffffff" else "#000000",
                "backgroundColor" to if (night) "#111113" else "#ffffff",
            )
        )
    }

    private fun dateFormatter(params: String?): String {
        val data = JSONObject(params ?: "{}")
        val date = Date(data.optLong("timeStamp").toDouble())
        fun pad(num: Int) = num.toString().padStart(2, '0')
        var format = data.optString("format")
        format = format.replace("yyyy", date.getFullYear().toString())
        format = format.replace("MM", pad(date.getMonth() + 1))
        format = format.replace("dd", pad(date.getDate()))
        format = format.replace("HH", pad(date.getHours()))
        format = format.replace("mm", pad(date.getMinutes()))
        format = format.replace("ss", pad(date.getSeconds()))
        return format
    }

    companion object {
        const val MODULE_NAME = "HRBridgeModule"
    }
}
