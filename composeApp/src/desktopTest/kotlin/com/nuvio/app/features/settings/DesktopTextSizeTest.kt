package com.nuvio.app.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopTextSizeTest {
    @Test
    fun `unknown key falls back to default`() {
        assertEquals(DesktopTextSize.DEFAULT, DesktopTextSize.fromKey(null))
        assertEquals(DesktopTextSize.DEFAULT, DesktopTextSize.fromKey("not-a-size"))
    }

    @Test
    fun `keys round trip`() {
        DesktopTextSize.entries.forEach { size ->
            assertEquals(size, DesktopTextSize.fromKey(size.key))
        }
    }

    @Test
    fun `sizes grow from small to extra large`() {
        val scales = DesktopTextSize.entries.map { it.scale }
        assertTrue(scales.zipWithNext().all { (smaller, larger) -> larger > smaller })
        assertEquals(1.18f, DesktopTextSize.DEFAULT.scale)
    }
}
