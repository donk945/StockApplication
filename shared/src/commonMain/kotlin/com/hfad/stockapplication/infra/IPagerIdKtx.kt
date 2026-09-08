package com.hfad.stockapplication.infra

import com.tencent.kuikly.core.base.IPagerId
import com.tencent.kuikly.core.base.pagerId

/**
 * 通过 pagerId 获取 BridgeModule，无需显式传 pagerId。
 */
internal val IPagerId.bridgeModule: BridgeModule by pagerId {
    Utils.bridgeModule(it)
}

internal fun IPagerId.setTimeout(delay: Int, callback: () -> Unit): String {
    return com.tencent.kuikly.core.timer.setTimeout(pagerId, delay, callback)
}
