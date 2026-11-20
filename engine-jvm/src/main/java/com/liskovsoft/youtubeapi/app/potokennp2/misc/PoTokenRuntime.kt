package com.liskovsoft.youtubeapi.app.potokennp2.misc

import com.liskovsoft.sharedutils.platform.EngineContext
import com.liskovsoft.youtubeapi.app.potokennp2.WebPoTokenProvider
import com.liskovsoft.youtubeapi.app.potokennp2.generators.PoTokenV8

/**
 * Selects the JS backend used while minting poTokens.
 *
 * On desktop the app installs a WebView2-backed runtime through [hostFactory].
 * If none is installed, the GraalJS fallback is used (and poToken generation is
 * treated as unavailable because the bare JS engine cannot run BotGuard).
 */
object PoTokenRuntime {
    @Volatile
    @JvmStatic
    var hostFactory: (() -> PoTokenJsRuntime)? = null

    @JvmStatic
    fun isAvailable(): Boolean = hostFactory != null

    internal fun create(): PoTokenJsRuntime = hostFactory?.invoke() ?: V8Wrapper()

    /**
     * Prepares the cached poToken generator (including the hidden WebView2
     * BotGuard session) without waiting for a video to be opened.
     */
    @JvmStatic
    fun warmUp() {
        if (!isAvailable()) return
        runCatching { WebPoTokenProvider.getPoToken("dQw4w9WgXcQ") }
            .onFailure {
                com.liskovsoft.sharedutils.mylogger.Log.w(
                    "PoTokenRuntime",
                    "poToken warm-up failed: %s",
                    it.message,
                )
            }
    }


    /**
     * Diagnostic helper used by desktop tests and manual verification.
     */
    @JvmStatic
    fun generateToken(identifier: String): String? = runCatching {
        val generator = PoTokenV8.newPoTokenGenerator(EngineContext.get())
        try {
            generator.generatePoToken(identifier)
        } finally {
            generator.close()
        }
    }.onFailure {
        com.liskovsoft.sharedutils.mylogger.Log.e(
            "PoTokenRuntime",
            "Token generation failed: %s",
            it.message,
        )
    }.getOrNull()
}
