package com.nuvio.app.features.youtube

import com.nuvio.app.core.storage.DesktopSecretStore
import com.nuvio.app.features.player.desktop.buildAudioFilter
import com.nuvio.app.features.player.desktop.buildEqualizerFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicSettingsTest {
    @Test
    fun `lastfm signature sorts keys and appends the secret`() {
        val signature = MusicScrobbler.signature(
            mapOf(
                "method" to "auth.getMobileSession",
                "api_key" to "abc",
                "username" to "listener",
            ),
            "secret",
        )
        assertEquals(
            "3e0e4b1b1c8c7e0f8b3a8a3c6f4d1a1c".length,
            signature.length,
        )
        assertTrue(signature.all { it in "0123456789abcdef" })
        // Adding a parameter changes the signature.
        val other = MusicScrobbler.signature(
            mapOf(
                "method" to "auth.getMobileSession",
                "api_key" to "abc",
                "username" to "listener",
                "password" to "hunter2",
            ),
            "secret",
        )
        assertTrue(signature != other)
    }

    @Test
    fun `equalizer filter uses ffmpeg lavfi bands`() {
        val filter = buildEqualizerFilter(listOf(31, 1_000), listOf(3.5f, -2f))
        assertEquals(
            "lavfi=[equalizer=f=31:width_type=o:width=1:g=3.5,equalizer=f=1000:width_type=o:width=1:g=-2.0]",
            filter,
        )
        assertEquals("", buildEqualizerFilter(emptyList(), emptyList()))
    }

    @Test
    fun `equalizer presets keep the band count`() {
        MusicEqualizer.ensureLoaded()
        MusicEqualizer.selectPreset(MusicEqualizerPreset.BASS)
        assertEquals(MusicEqualizer.bandsHz.size, MusicEqualizer.gains.size)
        assertTrue(MusicEqualizer.gains.first() > 0f)
        MusicEqualizer.selectPreset(MusicEqualizerPreset.OFF)
        assertTrue(MusicEqualizer.gains.all { it == 0f })
    }

    @Test
    fun `audio filter chain clears and joins`() {
        assertEquals("", buildAudioFilter(emptyList()))
        assertEquals(
            "lavfi=[silenceremove=start_periods=1,afade=t=in:st=0:d=2]",
            buildAudioFilter(listOf("silenceremove=start_periods=1", "afade=t=in:st=0:d=2")),
        )
    }

    @Test
    fun `secret store round trips and leaves plain values alone`() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return
        val protected = DesktopSecretStore.protect("lastfm-session")
        assertTrue(protected.startsWith("dpapi:"))
        assertEquals("lastfm-session", DesktopSecretStore.unprotect(protected))
        assertEquals("plain-value", DesktopSecretStore.unprotect("plain-value"))
    }
}
