package com.hfad.stockapplication.h5.module

import com.tencent.kuikly.core.render.web.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.web.ktx.KuiklyRenderCallback
import com.tencent.kuikly.core.render.web.ktx.kuiklyWindow
import com.tencent.kuikly.core.render.web.ktx.toJSONObjectSafely
import com.tencent.kuikly.core.render.web.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.render.web.utils.Log

class KRRouterModule : KuiklyRenderBaseModule() {
    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            OPEN_PAGE -> openPage(params)
            CLOSE_PAGE -> closePage()
            else -> super.call(method, params, callback)
        }
    }

    private fun openPage(param: String?) {
        if (param == null) {
            return
        }
        val params = param.toJSONObjectSafely()
        val pageName = params.optString("pageName")
        if (pageName.isEmpty()) {
            return
        }
        val urlPrefix = if (jsTypeOf(kuiklyWindow.asDynamic().URL) != "undefined") {
            val urlInstance = js("new URL(kuiklyWindow.location.href)")
            "${urlInstance.origin}${urlInstance.pathname}"
        } else {
            ""
        }
        val pageData: MutableMap<String, Any> =
            (params.optJSONObject("pageData") ?: JSONObject()).toMap()
        pageData["page_name"] = pageName
        val urlParamsString = pageData.entries.joinToString("&") { (key, value) ->
            "${key}=${value}"
        }
        val url = "$urlPrefix?${urlParamsString}"
        if (globalNavigationHandler?.invoke(url) == true) {
            return
        }
        kuiklyWindow.open(url)
    }

    private fun closePage() {
        if (globalClosePageHandler?.invoke() == true) {
            return
        }
        try {
            kuiklyWindow.close()
        } catch (e: dynamic) {
            Log.error("close page error: $e")
        }
    }

    companion object {
        const val MODULE_NAME = "KRRouterModule"
        private const val OPEN_PAGE = "openPage"
        private const val CLOSE_PAGE = "closePage"
        var globalNavigationHandler: ((String) -> Boolean)? = null
        var globalClosePageHandler: (() -> Boolean)? = null
    }
}
