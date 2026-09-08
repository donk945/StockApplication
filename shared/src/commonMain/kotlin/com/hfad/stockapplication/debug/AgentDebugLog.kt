package com.hfad.stockapplication.debug

import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal object AgentDebugLog {
    var sink: ((String) -> Unit)? = null

    fun emit(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, String> = emptyMap(),
        runId: String = "pre-fix",
    ) {
        val payload = JSONObject()
        payload.put("sessionId", "33e722")
        payload.put("runId", runId)
        payload.put("hypothesisId", hypothesisId)
        payload.put("location", location)
        payload.put("message", message)
        payload.put("timestamp", DateTime.currentTimestamp())
        val dataObj = JSONObject()
        data.forEach { (key, value) ->
            dataObj.put(key, value)
        }
        payload.put("data", dataObj)
        sink?.invoke(payload.toString())
    }
}
