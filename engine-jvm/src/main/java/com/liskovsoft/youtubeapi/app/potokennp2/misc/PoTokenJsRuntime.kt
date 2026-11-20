/**
 * Minimal JS runtime used by the BotGuard poToken flow.
 *
 * Implementations:
 *  - V8Wrapper (GraalJS)
 *  - WebView2PoTokenJsRuntime (desktop, real browser engine)
 */
package com.liskovsoft.youtubeapi.app.potokennp2.misc

interface PoTokenJsRuntime : AutoCloseable {
    fun executeStringScript(script: String): String?
    fun executeVoidScript(script: String)

    /**
     * Registers a global JS function that forwards its arguments to [callback].
     * Return values are not supported; the current BotGuard scripts only need
     * fire-and-forget callbacks (timers pass their id and delay).
     */
    fun registerCallback(name: String, callback: (List<Any?>) -> Unit)

    fun executeJsFunction(name: String): Any?

    fun shutdownRuntime()

    override fun close() = shutdownRuntime()
}
