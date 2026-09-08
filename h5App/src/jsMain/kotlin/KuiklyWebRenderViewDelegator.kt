package com.hfad.stockapplication.h5

import com.hfad.stockapplication.h5.module.KRBridgeModule
import com.hfad.stockapplication.h5.module.KRCacheModule
import com.hfad.stockapplication.h5.module.KRRouterModule
import com.hfad.stockapplication.h5.module.KRSseModule
import com.tencent.kuikly.core.render.web.IKuiklyRenderExport
import com.tencent.kuikly.core.render.web.expand.KuiklyRenderViewDelegatorDelegate
import com.tencent.kuikly.core.render.web.export.IKuiklyRenderViewExport
import com.tencent.kuikly.core.render.web.export.IKuiklyRenderViewPropExternalHandler
import com.tencent.kuikly.core.render.web.ktx.SizeI
import com.tencent.kuikly.core.render.web.runtime.web.expand.KuiklyRenderViewDelegator

class ViewPropExternalHandler : IKuiklyRenderViewPropExternalHandler {
    override fun setViewExternalProp(
        renderViewExport: IKuiklyRenderViewExport,
        propKey: String,
        propValue: Any,
    ): Boolean {
        return when (propKey) {
            "needCustomWrapper" -> {
                renderViewExport.ele.setAttribute("data-needCustomWrapper", propValue.toString())
                true
            }
            else -> false
        }
    }

    override fun resetViewExternalProp(
        renderViewExport: IKuiklyRenderViewExport,
        propKey: String,
    ): Boolean {
        return when (propKey) {
            "needCustomWrapper" -> {
                renderViewExport.ele.setAttribute("data-needCustomWrapper", js("undefined"))
                true
            }
            else -> false
        }
    }
}

class KuiklyWebRenderViewDelegator : KuiklyRenderViewDelegatorDelegate {
    private val delegate = KuiklyRenderViewDelegator(this)

    fun init(
        containerId: String,
        pageName: String,
        pageData: Map<String, Any>,
        size: SizeI,
    ) {
        delegate.onAttach(containerId, pageName, pageData, size)
    }

    fun resume() {
        delegate.onResume()
    }

    fun pause() {
        delegate.onPause()
    }

    fun detach() {
        delegate.onDetach()
    }

    override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalModule(kuiklyRenderExport)
        kuiklyRenderExport.moduleExport(KRBridgeModule.MODULE_NAME) { KRBridgeModule() }
        kuiklyRenderExport.moduleExport(KRSseModule.MODULE_NAME) { KRSseModule() }
        kuiklyRenderExport.moduleExport(KRCacheModule.MODULE_NAME) { KRCacheModule() }
        kuiklyRenderExport.moduleExport(KRRouterModule.MODULE_NAME) { KRRouterModule() }
    }

    override fun registerViewExternalPropHandler(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerViewExternalPropHandler(kuiklyRenderExport)
        kuiklyRenderExport.viewPropExternalHandlerExport(ViewPropExternalHandler())
    }
}
