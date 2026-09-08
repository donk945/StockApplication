package com.hfad.stockapplication.miniapp

import com.hfad.stockapplication.miniapp.components.KRMyView
import com.hfad.stockapplication.miniapp.module.KRBridgeModule
import com.hfad.stockapplication.miniapp.module.KRCacheModule
import com.hfad.stockapplication.miniapp.module.KRSseModule
import com.tencent.kuikly.core.render.web.IKuiklyRenderExport
import com.tencent.kuikly.core.render.web.expand.KuiklyRenderViewDelegatorDelegate
import com.tencent.kuikly.core.render.web.export.IKuiklyRenderViewExport
import com.tencent.kuikly.core.render.web.export.IKuiklyRenderViewPropExternalHandler
import com.tencent.kuikly.core.render.web.runtime.miniapp.dom.MiniElement
import com.tencent.kuikly.core.render.web.runtime.miniapp.expand.KuiklyRenderViewDelegator

class ViewPropExternalHandler : IKuiklyRenderViewPropExternalHandler {
    override fun setViewExternalProp(
        renderViewExport: IKuiklyRenderViewExport,
        propKey: String,
        propValue: Any,
    ): Boolean {
        return when (propKey) {
            "needCustomWrapper" -> {
                renderViewExport.ele.unsafeCast<MiniElement>().needCustomWrapper = propKey.toBoolean()
                true
            }
            else -> false
        }
    }

    override fun resetViewExternalProp(
        renderViewExport: IKuiklyRenderViewExport,
        propKey: String,
    ): Boolean {
        return propKey == "needCustomWrapper"
    }
}

class KuiklyWebRenderViewDelegator : KuiklyRenderViewDelegatorDelegate {
    val delegate = KuiklyRenderViewDelegator(this)

    override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
        kuiklyRenderExport.moduleExport(KRBridgeModule.MODULE_NAME) { KRBridgeModule() }
        kuiklyRenderExport.moduleExport(KRSseModule.MODULE_NAME) { KRSseModule() }
        kuiklyRenderExport.moduleExport(KRCacheModule.MODULE_NAME) { KRCacheModule() }
        super.registerExternalModule(kuiklyRenderExport)
    }

    override fun registerViewExternalPropHandler(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerViewExternalPropHandler(kuiklyRenderExport)
        kuiklyRenderExport.viewPropExternalHandlerExport(ViewPropExternalHandler())
    }

    override fun registerExternalRenderView(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalRenderView(kuiklyRenderExport)
        kuiklyRenderExport.renderViewExport(KRMyView.VIEW_NAME) { KRMyView() }
    }
}
