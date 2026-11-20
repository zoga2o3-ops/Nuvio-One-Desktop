package com.nuvio.app.features.youtube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YouTubeChaptersTest {
    @Test
    fun youTubesOwnChaptersWinAndAreSorted() {
        val own = listOf(YouTubeChapter(60_000, "Two"), YouTubeChapter(0, "One"), YouTubeChapter(60_000, "Dup"))
        val result = YouTubeChapters.resolve(own, "0:00 a\n1:00 b\n2:00 c")
        assertEquals(listOf(YouTubeChapter(0, "One"), YouTubeChapter(60_000, "Two")), result)
    }

    @Test
    fun descriptionTimestampsBecomeChapters() {
        val description = """
            Thanks for watching!
            0:00 Intro
            1:23 - Setup
            (12:05) The build
            Outro 1:02:03
            Follow me on socials
        """.trimIndent()
        assertEquals(
            listOf(
                YouTubeChapter(0, "Intro"),
                YouTubeChapter(83_000, "Setup"),
                YouTubeChapter(725_000, "The build"),
                YouTubeChapter(3_723_000, "Outro"),
            ),
            YouTubeChapters.resolve(emptyList(), description),
        )
    }

    @Test
    fun descriptionsThatAreNotChaptersAreIgnored() {
        assertTrue(YouTubeChapters.fromDescription("1:00 a\n2:00 b\n3:00 c").isEmpty(), "must start at 0:00")
        assertTrue(YouTubeChapters.fromDescription("0:00 a\n2:00 b").isEmpty(), "needs three")
        assertTrue(YouTubeChapters.fromDescription("0:00 a\n3:00 b\n2:00 c").isEmpty(), "must increase")
        assertTrue(YouTubeChapters.fromDescription(null).isEmpty())
    }
}
