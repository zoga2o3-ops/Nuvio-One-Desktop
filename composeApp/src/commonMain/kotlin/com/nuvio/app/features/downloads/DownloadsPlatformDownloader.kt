package com.nuvio.app.features.downloads

internal data class DownloadPlatformRequest(
    val item: DownloadItem,
) {
    val sourceUrl: String get() = item.sourceUrl
    val sourceHeaders: Map<String, String> get() = item.sourceHeaders
    val destinationFileName: String get() = item.fileName
}

internal interface DownloadsTaskHandle {
    fun cancel()
}

internal expect object DownloadsPlatformDownloader {
    fun start(
        request: DownloadPlatformRequest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onSuccess: (localFileUri: String, totalBytes: Long?) -> Unit,
        onFailure: (message: String) -> Unit,
        onPaused: () -> Unit,
    ): DownloadsTaskHandle

    fun restoreItem(item: DownloadItem): DownloadItem

    fun removeFile(localFileUri: String?): Boolean

    fun removePartialFile(destinationFileName: String): Boolean

    fun resolveLocalFileUri(localFileUri: String?, destinationFileName: String): String?

    fun openDownloadsDirectory(): Boolean

    /** Whether the user can pick where new downloads are saved on this platform. */
    fun supportsChoosingDownloadsDirectory(): Boolean

    /** Lets the user pick a new downloads folder. Returns true when a folder was chosen. */
    fun chooseDownloadsDirectory(): Boolean
}
