package com.nuvio.app.features.youtube

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class MusicBitChordSmokeTest {
    @Test
    fun `moods and new releases load`() = runBlocking {
        val moods = MusicBitChordRepository.moods()
        assertTrue(moods.isNotEmpty(), "No BitChord mood sections")
        assertTrue(moods.any { it.moods.isNotEmpty() }, "No mood entries")
        val releases = MusicBitChordRepository.newReleases()
        assertTrue(releases.isNotEmpty(), "No BitChord new releases")
        assertTrue(releases.any { it.items.isNotEmpty() }, "No new-release cards")
    }
}
