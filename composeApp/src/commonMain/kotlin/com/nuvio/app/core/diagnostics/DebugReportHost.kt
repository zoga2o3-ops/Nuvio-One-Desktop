package com.nuvio.app.core.diagnostics

/** What happened to a debug report: its reference ID and whether it reached support. */
data class DebugReportReceipt(
    val id: String,
    val sent: Boolean,
    val detail: String? = null,
)

/**
 * Filled in by the desktop app: builds a redacted debug report and sends it to support.
 * The button is hidden on platforms that do not install a sender.
 */
object DebugReportHost {
    var available: Boolean = false
    var send: (suspend () -> Result<DebugReportReceipt>)? = null
}
