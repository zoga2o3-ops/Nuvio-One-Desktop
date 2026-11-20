package com.nuvio.app.features.youtube

import kotlin.test.Test
import kotlin.test.assertEquals

class MusicTrackInfoTest {
    @Test
    fun cleansUploadTitles() {
        assertEquals("rockstar", cleanSongTitle("Post Malone - rockstar (Official Music Video) ft. 21 Savage", "Post Malone"))
        assertEquals("Attention", cleanSongTitle("Charlie Puth - Attention", "Charlie Puth"))
        assertEquals("Yessirr", cleanSongTitle("Tory Lanez - Yessirr (Official Audio)", "Tory Lanez"))
        // A dash that isn't an artist prefix stays.
        assertEquals("Where U at", cleanSongTitle("Where U at [MV]", "TAEYANG"))
    }

    @Test
    fun splitsCredits() {
        assertEquals(listOf("Fridayy", "Mariah the Scientist"), splitArtistNames("Fridayy, Mariah the Scientist"))
        assertEquals(listOf("Tory Lanez", "T-Pain"), splitArtistNames("Tory Lanez and T-Pain"))
        assertEquals(listOf("Lil Nas X"), splitArtistNames("Lil Nas X"))
        assertEquals(listOf("B2K"), splitArtistNames("B2K • 863K views • 2 years ago"))
    }
}
