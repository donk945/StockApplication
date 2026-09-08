package com.hfad.stockapplication.miniapp.module

import com.tencent.kuikly.core.render.web.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.web.ktx.KuiklyRenderCallback
import com.tencent.kuikly.core.render.web.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.render.web.runtime.miniapp.core.NativeApi
import kotlin.js.json

class KRSseModule : KuiklyRenderBaseModule() {
    private var generation = 0
    private var task: dynamic = null

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "start" -> {
                start(params, callback)
                Unit
            }
            "cancel" -> {
                cancel()
                Unit
            }
            else -> null
        }
    }

    private fun start(params: String?, callback: KuiklyRenderCallback?) {
        cancel()
        val gen = ++generation
        val root = JSONObject(params ?: return)
        val url = root.optString("url")
        if (url.isBlank()) {
            emit(callback, "error", "url 为空", gen)
            return
        }
        val headersJson = root.optJSONObject("headers") ?: JSONObject()
        val header = json(
            "Accept" to "text/event-stream",
            "Content-Type" to "application/json",
        )
        headersJson.keySet().forEach { key ->
            header[key] = headersJson.optString(key)
        }
        val bodyObj = root.optJSONObject("body")
        val bodyText = bodyObj?.toString() ?: "{}"
        val leftover = arrayOf("")
        val requestTask = NativeApi.plat.request(
            json(
                "url" to url,
                "method" to "POST",
                "header" to header,
                "data" to js("JSON.parse")(bodyText),
                "enableChunked" to true,
                "timeout" to 120000,
                "success" to {
                    if (gen == generation) {
                        emit(callback, "done", "", gen)
                    }
                },
                "fail" to {
                    if (gen == generation) {
                        emit(callback, "error", "流式请求失败", gen)
                    }
                },
            )
        )
        task = requestTask
        try {
            requestTask.onChunkReceived { res ->
                if (gen != generation) {
                    return@onChunkReceived
                }
                val chunk = js("new TextDecoder('utf-8')").decode(res.asDynamic().data) as String
                consume(leftover, leftover[0] + chunk, callback, gen)
            }
        } catch (_: Throwable) {
            emit(callback, "error", "当前环境不支持流式", gen)
        }
    }

    private fun consume(
        leftover: Array<String>,
        text: String,
        callback: KuiklyRenderCallback?,
        gen: Int,
    ) {
        val lines = text.split("\n")
        leftover[0] = lines.last()
        for (index in 0 until lines.size - 1) {
            var line = lines[index]
            if (line.endsWith("\r")) {
                line = line.dropLast(1)
            }
            if (!line.startsWith("data:")) {
                continue
            }
            val data = line.substring(5).trim()
            if (data == "[DONE]") {
                emit(callback, "done", "", gen)
                cancel()
                return
            }
            val delta = extractDelta(data)
            if (delta.isNotEmpty()) {
                emit(callback, "delta", delta, gen)
            }
        }
    }

    private fun extractDelta(data: String): String {
        return try {
            val json = JSONObject(data)
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
                ?.optString("content")
                .orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun emit(callback: KuiklyRenderCallback?, event: String, text: String, gen: Int) {
        if (gen != generation) {
            return
        }
        callback?.invoke(mapOf("event" to event, "text" to text))
    }

    private fun cancel() {
        generation += 1
        try {
            task?.abort()
        } catch (_: Throwable) {
        }
        task = null
    }

    companion object {
        const val MODULE_NAME = "KRSseModule"
    }
}
