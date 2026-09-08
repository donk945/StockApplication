package com.hfad.stockapplication.infra

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * DeepSeek SSE 流式请求。Native 实现：Android `KRSseModule`、iOS `KRSseModule`、鸿蒙 / H5 / 小程序同名 Module。
 */
class SseModule : Module() {

    override fun moduleName(): String {
        return MODULE_NAME
    }

    fun start(
        url: String,
        headers: JSONObject,
        body: JSONObject,
        callbackFn: CallbackFn,
    ) {
        val params = JSONObject()
            .put("url", url)
            .put("headers", headers)
            .put("body", body)
        toNative(
            true,
            METHOD_START,
            params.toString(),
            callbackFn,
            false
        )
    }

    fun cancel() {
        toNative(
            false,
            METHOD_CANCEL,
            null,
            null,
            false
        )
    }

    companion object {
        const val MODULE_NAME = "KRSseModule"
        const val METHOD_START = "start"
        const val METHOD_CANCEL = "cancel"
        const val EVENT_DELTA = "delta"
        const val EVENT_DONE = "done"
        const val EVENT_ERROR = "error"
    }
}
