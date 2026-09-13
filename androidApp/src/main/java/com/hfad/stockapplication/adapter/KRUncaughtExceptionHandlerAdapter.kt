package com.hfad.stockapplication.adapter

import android.util.Log
import com.tencent.kuikly.core.render.android.adapter.IKRUncaughtExceptionHandlerAdapter

object KRUncaughtExceptionHandlerAdapter : IKRUncaughtExceptionHandlerAdapter {

    private const val TAG = "KRExceptionHandler"

    override fun uncaughtException(throwable: Throwable) {
        Log.e(TAG, "KR error: ${throwable.stackTraceToString()}")
        // 不再二次抛出：Kuikly 会把可恢复的渲染/解析错误送到这里，
        // 再 throw 会把整个进程打死。
    }
}
