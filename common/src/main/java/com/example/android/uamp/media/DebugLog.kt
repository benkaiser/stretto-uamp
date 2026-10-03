package com.example.android.uamp.media

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny persistent log so service behaviour can be inspected after the fact (e.g. after an
 * Android Auto session) via `adb shell run-as <package> cat files/stretto-debug.log`.
 */
object DebugLog {
    private const val FILE_NAME = "stretto-debug.log"
    private const val MAX_BYTES = 200_000L
    private var file: File? = null
    private val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        file = File(context.filesDir, FILE_NAME)
    }

    @Synchronized
    fun log(tag: String, message: String) {
        Log.d(tag, message)
        val f = file ?: return
        try {
            if (f.length() > MAX_BYTES) {
                val tail = f.readText().takeLast((MAX_BYTES / 2).toInt())
                f.writeText(tail)
            }
            f.appendText("${format.format(Date())} $tag: $message\n")
        } catch (e: Exception) {
            Log.w(tag, "DebugLog write failed", e)
        }
    }
}
