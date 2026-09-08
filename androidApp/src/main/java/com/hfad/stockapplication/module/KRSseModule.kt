package com.hfad.stockapplication.module

import android.os.Handler
import android.os.Looper
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class KRSseModule : KuiklyRenderBaseModule() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cancelled = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    @Volatile
    private var connection: HttpURLConnection? = null

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "start" -> {
                start(params, callback)
                null
            }
            "cancel" -> {
                cancel()
                null
            }
            else -> null
        }
    }

    private fun start(params: String?, callback: KuiklyRenderCallback?) {
        cancel()
        val gen = generation.incrementAndGet()
        cancelled.set(false)
        val root = JSONObject(params ?: return)
        val url = root.optString("url")
        if (url.isBlank()) {
            emit(callback, "error", "url 为空")
            return
        }
        val headers = root.optJSONObject("headers") ?: JSONObject()
        val body = root.optJSONObject("body")?.toString() ?: "{}"
        Thread({
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).also {
                    connection = it
                    it.requestMethod = "POST"
                    it.doInput = true
                    it.doOutput = true
                    it.connectTimeout = 15_000
                    it.readTimeout = 120_000
                    it.setRequestProperty("Accept", "text/event-stream")
                    val names = headers.keys()
                    while (names.hasNext()) {
                        val key = names.next()
                        if (key.equals("Authorization", ignoreCase = true).not()) {
                            it.setRequestProperty(key, headers.optString(key))
                        }
                    }
                    val auth = headers.optString("Authorization")
                    if (auth.isNotBlank()) {
                        it.setRequestProperty("Authorization", auth)
                    }
                    val payload = body.toByteArray(StandardCharsets.UTF_8)
                    it.setRequestProperty("Content-Type", "application/json")
                    it.setFixedLengthStreamingMode(payload.size)
                    it.outputStream.use { out -> out.write(payload) }
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    if (generation.get() == gen) {
                        emit(callback, "error", "请求失败($code)")
                    }
                    return@Thread
                }
                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    while (!cancelled.get() && generation.get() == gen) {
                        val line = reader.readLine() ?: break
                        if (!line.startsWith("data:")) {
                            continue
                        }
                        val data = line.substring(5).trim()
                        if (data == "[DONE]") {
                            break
                        }
                        val delta = extractDelta(data)
                        if (delta.isNotEmpty()) {
                            emit(callback, "delta", delta)
                        }
                    }
                }
                if (!cancelled.get() && generation.get() == gen) {
                    emit(callback, "done", "")
                }
            } catch (_: Throwable) {
                if (!cancelled.get() && generation.get() == gen) {
                    emit(callback, "error", "流式请求失败")
                }
            } finally {
                conn?.disconnect()
                if (connection === conn) {
                    connection = null
                }
            }
        }, "kr-sse").start()
    }

    private fun cancel() {
        generation.incrementAndGet()
        cancelled.set(true)
        connection?.disconnect()
        connection = null
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

    private fun emit(callback: KuiklyRenderCallback?, event: String, text: String) {
        val payload = mapOf(
            "event" to event,
            "text" to text,
        )
        mainHandler.post {
            callback?.invoke(payload)
        }
    }

    companion object {
        const val MODULE_NAME = "KRSseModule"
    }
}
