package com.hfad.stockapplication.adapter

import android.util.Log
import com.hfad.stockapplication.BuildConfig
import com.tencent.kuikly.core.render.android.adapter.IKRUncaughtExceptionHandlerAdapter

object KRUncaughtExceptionHandlerAdapter : IKRUncaughtExceptionHandlerAdapter {

    private const val TAG = "KRExceptionHandler"

    override fun uncaughtException(throwable: Throwable) {
        Log.e(TAG, "KR error: ${throwable.stackTraceToString()}")
        if (BuildConfig.DEBUG) {
            throw throwable
        }
    }
}
