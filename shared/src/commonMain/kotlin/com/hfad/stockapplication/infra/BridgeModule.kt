package com.hfad.stockapplication.infra

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 业务侧 Bridge：只暴露问答页真正用到的宿主能力。
 * Native 模块名保持 [MODULE_NAME]，与各端 HRBridgeModule 对齐。
 */
internal class BridgeModule : Module() {

    override fun moduleName(): String {
        return MODULE_NAME
    }

    fun log(content: String) {
        val args = JSONObject().put("content", content)
        callNativeMethod(LOG, args, null)
    }

    fun toast(content: String) {
        val args = JSONObject().put("content", content)
        callNativeMethod(TOAST, args, null)
    }

    fun setNightBars(night: Boolean) {
        val args = JSONObject().put("night", if (night) 1 else 0)
        callNativeMethod(SET_NIGHT_BARS, args, null)
    }

    fun closeKeyboard(data: JSONObject? = null, callbackFn: CallbackFn? = null): String {
        return syncCallNativeMethod(CLOSE_KEYBOARD, data, callbackFn)
    }

    fun copyToPasteboard(content: String) {
        val args = JSONObject().put("content", content)
        callNativeMethod(COPY, args, null)
    }

    fun debugMark(step: String) {
    }

    fun loadDebugMark(): String {
        return ""
    }

    private fun callNativeMethod(methodName: String, data: JSONObject?, callbackFn: CallbackFn?) {
        toNative(false, methodName, data?.toString(), callbackFn, false)
    }

    private fun syncCallNativeMethod(
        methodName: String,
        data: JSONObject?,
        callbackFn: CallbackFn?,
    ): String {
        return toNative(false, methodName, data?.toString(), callbackFn, true).toString()
    }

    companion object {
        const val MODULE_NAME = "HRBridgeModule"
        const val LOG = "log"
        const val TOAST = "toast"
        const val SET_NIGHT_BARS = "setNightBars"
        const val CLOSE_KEYBOARD = "closeKeyboard"
        const val COPY = "copyToPasteboard"
    }
}
