package com.hfad.stockapplication.h5.utils

import com.tencent.kuikly.core.render.web.nvi.serialization.json.JSONObject
import kotlinx.browser.document
import kotlinx.browser.window

external fun decodeURIComponent(encoded: String): String

object Ui {
    private const val TOAST_DELAY_DEFAULT = 2000

    internal fun showToast(message: JSONObject) {
        val content = message.optString("content")
        if (content == "") {
            return
        }
        val wrapDiv = document.createElement("div")
        val contentDiv = document.createElement("div")
        wrapDiv.classList.add("toast-wrapper")
        contentDiv.classList.add("toast-content")
        contentDiv.innerHTML = content
        wrapDiv.appendChild(contentDiv)
        document.body?.appendChild(wrapDiv)
        window.setTimeout({
            document.body?.removeChild(wrapDiv)
        }, TOAST_DELAY_DEFAULT)
    }
}

object URL {
    internal fun parseParams(url: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        if (!url.contains("?")) {
            return params
        }
        val query = url.substringAfter("?")
        if (query.isEmpty()) {
            return params
        }
        query.split("&").forEach { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                val (name, value) = parts
                params[name] = try {
                    decodeURIComponent(value)
                } catch (e: dynamic) {
                    value
                }
            }
        }
        return params
    }
}
