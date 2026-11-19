package com.catsmoker.obd2ai.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.catsmoker.obd2ai.BuildConfig

/** Allowlisted stages only: never accept an adapter address, VIN, prompt, or exception message. */
object SupportReport {
    enum class Event { BLUETOOTH_CONNECT, WIFI_CONNECT, INITIALIZE, READY, FAILED, PERMISSION_DENIED, DEMO }
    private val recent = ArrayDeque<Event>()
    @Synchronized fun record(event: Event) {
        if (recent.size == 32) recent.removeFirst()
        recent.addLast(event)
    }
    @Synchronized private fun history() = recent.joinToString(" → ")

    fun text(context: Context): String = buildString {
        appendLine("OBD2AI ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Connection events this session: ${history().ifEmpty { "none recorded" }}")
        if (Build.VERSION.SDK_INT >= 30) {
            val exits = runCatching {
                context.getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(context.packageName, 0, 3)
            }.getOrNull()
            exits?.forEach { appendLine("Previous process exit: reason=${it.reason}, status=${it.status}") }
        }
    }
}
