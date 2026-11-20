package com.nuvio.app.features.youtube

import com.nuvio.app.features.player.desktop.DesktopHostOs
import com.nuvio.app.features.player.desktop.NativePlayerBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens a real Spotify sign-in window and reads the HttpOnly `sp_dc` session
 * cookie through WebView2's cookie manager. The page itself cannot read that
 * cookie from JavaScript, so the native bridge does it and hands the value
 * back after the listener has signed in.
 *
 * A Spotify login page sets a guest `sp_dc` before the listener signs in. The
 * native window therefore keeps watching the cookie and this loop validates
 * each new value with Spotify's token endpoint; only a non-anonymous session
 * is saved.
 */
internal object SpotifyCookieBrowser {
    /** Long enough for a password manager or two-step sign-in. */
    private const val TimeoutMs = 5 * 60 * 1000

    val isSupported: Boolean
        get() = DesktopHostOs.current == DesktopHostOs.WINDOWS

    suspend fun capture(): Result<String> = withContext(Dispatchers.IO) {
        runCatching<String> {
            check(isSupported) {
                "Automatic Spotify sign-in is available on Windows. Paste the sp_dc cookie instead."
            }
            // A previous sign-out may not have been able to delete the profile
            // (WebView2 still held it); finish that now so the old account is gone.
            if (MusicSpotifySession.profileWipePending) wipeProfile()
            NativePlayerBridge.ensureLoaded()
            val handle = NativePlayerBridge.createSpotifyLoginWebView()
            check(handle != 0L) { "Could not open the Spotify sign-in window." }
            try {
                val cookie = awaitAuthenticatedCookie(handle)
                MusicSpotifySession.saveCookie(cookie)
                cookie
            } finally {
                runCatching { NativePlayerBridge.destroySpotifyLoginWebView(handle) }
            }
        }
    }

    /** `%LOCALAPPDATA%\Nuvio\WebView2-Spotify`, the sign-in window's persistent profile. */
    private val profileDir: java.io.File?
        get() = System.getenv("LOCALAPPDATA")
            ?.takeIf { it.isNotBlank() }
            ?.let { java.io.File(it, "Nuvio\\WebView2-Spotify") }

    /**
     * Deletes the sign-in window's WebView2 profile so the next automatic sign-in
     * starts logged out. Returns true when nothing is left behind.
     */
    fun wipeProfile(): Boolean {
        if (!isSupported) return true
        val dir = profileDir ?: return true
        val wiped = !dir.exists() || dir.deleteRecursively()
        MusicSpotifySession.markProfileWipePending(!wiped)
        return wiped
    }

    private suspend fun awaitAuthenticatedCookie(handle: Long): String {
        val deadline = System.currentTimeMillis() + TimeoutMs
        var previousCookie = ""
        while (true) {
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            if (remaining <= 0) {
                error("Spotify sign-in timed out before an authenticated session was found.")
            }
            val cookie = NativePlayerBridge.awaitSpotifyCookie(handle, previousCookie, remaining)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: error("Spotify sign-in was closed before an authenticated session was found.")

            val client = SpotifyWebClient.connect(cookie, requireSignedIn = false).getOrNull()
            if (client?.isSignedIn == true) return cookie

            // This was Spotify's guest session. Keep the window open and wait
            // for the authenticated cookie that replaces it.
            previousCookie = cookie
        }
    }
}
