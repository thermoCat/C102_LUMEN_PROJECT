package com.ssafy.smartcane.util

import android.util.Log

/**
 * 앱 전역 로그 버퍼 — BleTestScreen 로그창에서 확인 가능.
 */
object AppLogger {
    private const val MAX_ENTRIES = 200

    private val _entries = ArrayDeque<String>()
    val entries: List<String> get() = _entries.toList()

    fun log(tag: String, message: String) {
        Log.d(tag, message)
        synchronized(_entries) {
            _entries.addLast("[$tag] $message")
            if (_entries.size > MAX_ENTRIES) _entries.removeFirst()
        }
    }

    fun error(tag: String, message: String) {
        Log.e(tag, message)
        synchronized(_entries) {
            _entries.addLast("❌ [$tag] $message")
            if (_entries.size > MAX_ENTRIES) _entries.removeFirst()
        }
    }

    fun clear() {
        synchronized(_entries) { _entries.clear() }
    }
}
