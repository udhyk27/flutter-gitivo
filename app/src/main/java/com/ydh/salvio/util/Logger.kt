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

    // 캐시 관련 로그
    fun cacheHit(key: String) = d("Cache HIT: $key")
    fun cacheMiss(key: String) = d("Cache MISS: $key")
    fun cacheInvalid(key: String) = d("Cache INVALID: $key")

    // 네트워크 관련 로그
    fun networkRequest(method: String, url: String) = d("REQUEST: $method $url")
    fun networkError(url: String, code: Int, message: String) = w("ERROR: $url ($code) - $message")
    fun networkSuccess(url: String, code: Int) = d("SUCCESS: $url ($code)")

    // 인증 관련 로그
    fun authSuccess(user: String) = i("AUTH SUCCESS: $user")
    fun authFailed(reason: String) = w("AUTH FAILED: $reason")
}
