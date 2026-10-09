package com.ydh.salvio.util

import android.util.Log
import com.ydh.salvio.BuildConfig

object Logger {
    private const val TAG = "Gitivo"
    private val isDebug = BuildConfig.DEBUG

    fun d(message: String) {
        if (isDebug) Log.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (isDebug) Log.e(TAG, message, throwable)
    }

    fun w(message: String) {
        if (isDebug) Log.w(TAG, message)
    }

    fun i(message: String) {
        if (isDebug) Log.i(TAG, message)
    }

    fun authFailed(reason: String) = w("AUTH FAILED: $reason")
}
