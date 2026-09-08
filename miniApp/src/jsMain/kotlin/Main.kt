import com.hfad.stockapplication.miniapp.KuiklyWebRenderViewDelegator
import com.tencent.kuikly.core.render.web.KuiklyRenderView
import com.tencent.kuikly.core.render.web.IKuiklyRenderViewLifecycleCallback
import com.tencent.kuikly.core.render.web.exception.ErrorReason
import com.tencent.kuikly.core.render.web.collection.FastMutableMap
import com.tencent.kuikly.core.render.web.ktx.SizeI
import com.tencent.kuikly.core.render.web.processor.TextMeasureCache
import com.tencent.kuikly.core.render.web.runtime.miniapp.MiniDocument
import com.tencent.kuikly.core.render.web.runtime.miniapp.core.App
import com.tencent.kuikly.core.render.web.runtime.miniapp.core.NativeApi
import com.tencent.kuikly.core.render.web.runtime.miniapp.expand.KuiklyRenderViewDelegator
import com.tencent.kuikly.core.render.web.runtime.miniapp.processor.RichTextProcessor

const val TAG = "Main"

private val activeDelegators = mutableListOf<KuiklyRenderViewDelegator>()

fun main() {
    App.onShow {
        console.log(TAG, "app show")
    }
    App.onLaunch {
        console.log(TAG, "app launch")
    }
    App.onHide {
        console.log(TAG, "app hide")
    }
}

@JsName(name = "renderView")
@JsExport
@ExperimentalJsExport
fun renderView(json: dynamic) {
    if (json.pageName == null || (json.pageName as? String).isNullOrBlank()) {
        json.pageName = "stock_chat"
    }
    var size: SizeI? = null
    if (json.width != null && json.height != null) {
        size = SizeI(json.width.unsafeCast<Int>(), json.height.unsafeCast<Int>())
    }
    MiniDocument.initPage(json) { pageId: Int, pageName: String, paramsMap: FastMutableMap<String, Any> ->
        val systemInfo = NativeApi.plat.getSystemInfoSync()
        val isAndroid = systemInfo.platform == "android"
        val params = paramsMap["param"].unsafeCast<FastMutableMap<String, Any>>()
        params["is_wx_mp"] = "true"
        if (params["deepseekApiKey"] == null) {
            params["deepseekApiKey"] = ""
        }
        if (params["isNightMode"] == null) {
            params["isNightMode"] = if (systemInfo.theme == "dark") "1" else "0"
        }
        paramsMap["platform"] = "miniprogram"
        paramsMap["isIOS"] = !isAndroid
        paramsMap["isIphoneX"] = !isAndroid && systemInfo.safeArea.top > 30

        val delegatorWrapper = KuiklyWebRenderViewDelegator()
        val delegator = delegatorWrapper.delegate
        delegator.onAttach(pageId, pageName, paramsMap, size)
        activeDelegators.add(delegator)
        delegator.addKuiklyRenderViewLifeCycleCallback(object : IKuiklyRenderViewLifecycleCallback {
            override fun onInit() {}
            override fun onPreloadDexClassFinish() {}
            override fun onInitCoreStart() {}
            override fun onInitCoreFinish() {}
            override fun onInitContextStart() {}
            override fun onInitContextFinish() {}
            override fun onCreateInstanceStart() {}
            override fun onCreateInstanceFinish() {}
            override fun onFirstFramePaint() {}
            override fun onResume() {}
            override fun onPause() {}
            override fun onDestroy() {
                activeDelegators.remove(delegator)
            }
            override fun onRenderException(throwable: Throwable, errorReason: ErrorReason) {}
        })
    }
}

@JsName(name = "fontLoaded")
@JsExport
@ExperimentalJsExport
fun fontLoaded() {
    RichTextProcessor.resetMeasureContext()
    TextMeasureCache.clear()
    activeDelegators.forEach { delegator ->
        try {
            delegator.sendEvent(KuiklyRenderView.PAGER_EVENT_ON_FONT_LOADED, mapOf())
        } catch (e: Throwable) {
            console.log(TAG, "fontLoaded: sendEvent failed: ${e.message}")
        }
    }
}

@JsName(name = "initApp")
@JsExport
@ExperimentalJsExport
fun initApp(options: dynamic = js("{}")) {
    App.initApp(options)
}
