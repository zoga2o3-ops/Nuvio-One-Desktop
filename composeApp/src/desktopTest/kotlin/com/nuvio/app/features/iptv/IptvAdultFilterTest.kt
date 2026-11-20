package com.nuvio.app.features.iptv

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IptvAdultFilterTest {
    @Test
    fun `provider adult group names are matched`() {
        assertTrue(isAdultGroup("18| FOR ADULTS"))
        assertTrue(isAdultGroup("ADULT"))
        assertTrue(isAdultGroup("ADULT PREMIUM"))
        assertTrue(isAdultGroup("XXX"))
        assertTrue(isAdultGroup("XXX - ADULT"))
        assertTrue(isAdultGroup("18+"))
        assertTrue(isAdultGroup("Porn"))
    }

    @Test
    fun `normal groups are not matched`() {
        assertFalse(isAdultGroup("News"))
        assertFalse(isAdultGroup("Sports"))
        assertFalse(isAdultGroup("Movies"))
        assertFalse(isAdultGroup("24/7 CHANNELS"))
        assertFalse(isAdultGroup("News 18"))
        assertFalse(isAdultGroup("4K CHANNELS"))
    }
}
