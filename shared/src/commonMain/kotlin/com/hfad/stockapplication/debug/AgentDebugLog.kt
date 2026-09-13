package com.hfad.stockapplication.debug

/**
 * 调试埋点入口。正式路径保持空实现，避免流式热路径拼 JSON、打桥、写盘。
 */
internal object AgentDebugLog {
    var sink: ((String) -> Unit)? = null
    var mark: ((String) -> Unit)? = null

    fun breadcrumb(step: String) {
    }

    fun emit(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, String> = emptyMap(),
        runId: String = "pre-fix",
    ) {
    }
}
