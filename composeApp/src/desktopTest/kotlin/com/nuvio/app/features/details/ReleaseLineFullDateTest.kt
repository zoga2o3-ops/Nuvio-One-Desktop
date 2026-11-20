package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReleaseLineFullDateTest {
    private val movie = MetaDetails(id = "tt1", type = "movie", name = "Movie", releaseInfo = "2026", released = "2026-03-03T00:00:00.000Z")

    @Test
    fun yearOnlyByDefault() {
        assertEquals("2026", formatMetaReleaseLineForDetails(movie))
    }

    @Test
    fun fullDateWhenEnabled() {
        val line = formatMetaReleaseLineForDetails(movie, fullDate = true)!!
        assertNotEquals("2026", line)
        // The 3rd, in any locale's wording; never shifted to the 2nd by the time zone.
        assertTrue("2026" in line && Regex("""\b0?3\b""").containsMatchIn(line), line)
    }

    @Test
    fun fallsBackToYearWithoutADate() {
        val noDate = movie.copy(released = null)
        assertEquals("2026", formatMetaReleaseLineForDetails(noDate, fullDate = true))
    }

    @Test
    fun endedSeriesKeepsItsEndYear() {
        val series = MetaDetails(
            id = "tt2",
            type = "series",
            name = "Show",
            releaseInfo = "2019",
            released = "2019-01-10",
            lastAirDate = "2022-05-01",
            status = "Ended",
        )
        assertEquals("2019 - 2022", formatMetaReleaseLineForDetails(series))
        val full = formatMetaReleaseLineForDetails(series, fullDate = true)!!
        assertTrue(full.endsWith(" - 2022") && full != "2019 - 2022", full)
    }
}
