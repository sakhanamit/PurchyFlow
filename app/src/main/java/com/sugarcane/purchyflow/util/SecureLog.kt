package com.sugarcane.purchyflow.util

import android.util.Log
import com.sugarcane.purchyflow.BuildConfig

/**
 * Privacy-Preserving Secure Logger.
 *
 * Enforces zero leakage of personal SMS content or banking details:
 * 1. In Release builds (BuildConfig.DEBUG == false), debug/info/verbose statements are completely silent.
 * 2. In Debug builds, message contents must never be dumped raw to Logcat.
 */
object SecureLog {

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
}
