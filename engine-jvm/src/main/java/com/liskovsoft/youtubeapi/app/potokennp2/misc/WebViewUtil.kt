package com.liskovsoft.youtubeapi.app.potokennp2.misc

import android.content.Context

internal const val potLibPrefix = "potokennp2/"

/**
 * Desktop has no Android thermal/USB services, so those platform bugs cannot occur.
 */
internal fun hasThermalServiceBug(context: Context): Boolean = false

internal fun hasUsbServiceBug(context: Context): Boolean = false

/**
 * Desktop has no Android main looper. Callbacks run inline on the caller's thread.
 */
internal fun runOnMainThread(runnable: Runnable) {
    runnable.run()
}

internal fun runOnMainThreadSync(waitSec: Long, block: () -> Unit): Boolean {
    return runCatching { block() }.isSuccess
}
