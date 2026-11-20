package com.nuvio.app.features.youtube

import com.nuvio.app.core.storage.DesktopStorage
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Playback events for OneTube, written to onetube-debug.log in the app data folder. */
internal object OneTubeDebugLog {
    private val file by lazy { DesktopStorage.rootDir.resolve("onetube-debug.log") }
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun line(message: String) {
        runCatching {
            val text = "${time.format(Date())} $message\n"
            Files.write(
                file,
                text.toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }
}
