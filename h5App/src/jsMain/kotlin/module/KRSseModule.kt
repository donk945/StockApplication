package com.hfad.stockapplication.h5.module

import com.tencent.kuikly.core.render.web.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.web.ktx.KuiklyRenderCallback
import com.tencent.kuikly.core.render.web.nvi.serialization.json.JSONObject
import kotlinx.browser.window

class KRSseModule : KuiklyRenderBaseModule() {
    private var generation = 0
    private var abort: dynamic = null

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
        val bodyObj = root.optJSONObject("body")
        val body = bodyObj?.toString() ?: "{}"
        val headerMap = js("{}")
        headerMap["Accept"] = "text/event-stream"
        headerMap["Content-Type"] = "application/json"
        headersJson.keySet().forEach { key ->
            headerMap[key] = headersJson.optString(key)
        }
        val ctrl = js("new AbortController()")
        abort = ctrl
        val leftover = arrayOf("")
        val options = js("{}")
        options.method = "POST"
        options.headers = headerMap
        options.body = body
        options.signal = ctrl.signal
        window.fetch(url, options).then { response ->
            if (gen != generation) {
                return@then
            }
            val ok = response.asDynamic().ok as Boolean
            val status = response.asDynamic().status as Int
            if (!ok) {
                emit(callback, "error", "请求失败($status)", gen)
                return@then
            }
            val reader = response.asDynamic().body.getReader()
            pump(reader, leftover, callback, gen)
        }.catch { _ ->
            if (gen == generation) {
                emit(callback, "error", "流式请求失败", gen)
            }
        }
    }

    private fun pump(
        reader: dynamic,
        leftover: Array<String>,
        callback: KuiklyRenderCallback?,
        gen: Int,
    ) {
        if (gen != generation) {
            return
        }
        reader.read().then { result ->
            if (gen != generation) {
                return@then
            }
            if (result.done == true) {
                emit(callback, "done", "", gen)
                return@then
            }
            val chunk = js("new TextDecoder('utf-8')").decode(result.value) as String
            consume(leftover, leftover[0] + chunk, callback, gen)
            if (gen == generation) {
                pump(reader, leftover, callback, gen)
            }
        }.catch { _ ->
            if (gen == generation) {
                emit(callback, "error", "流式请求失败", gen)
            }
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
            abort?.abort()
        } catch (_: Throwable) {
        }
        abort = null
    }

    companion object {
        const val MODULE_NAME = "KRSseModule"
    }
}
