package com.nuvio.app.features.iptv

import com.nuvio.app.core.storage.DesktopStorage
import java.nio.file.Path

/** Where Live TV recordings are written. Defaults to %APPDATA%\Nuvio\recordings. */
internal object IptvRecordingStore {
    private const val Key = "recordingsDir"
    private val store = DesktopStorage.store("nuvio_iptv")
    private val defaultFolder = DesktopStorage.rootDir.resolve("recordings")

    fun folder(): Path {
        val saved = store.getString(Key)?.takeIf { it.isNotBlank() }
        return saved?.let { runCatching { Path.of(it) }.getOrNull() } ?: defaultFolder
    }

    fun setFolder(path: Path) {
        store.putString(Key, path.toAbsolutePath().toString())
    }

    fun reset() {
        store.putString(Key, "")
    }
}
