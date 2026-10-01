package com.appsciences.postui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankCheckTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun `single-colour image is blank`() {
        assertTrue(BlankCheck.isBlank(width = 1080, height = 2000) { _, _ -> white })
    }

    @Test
    fun `image with content is not blank`() {
        // A short line of "text" in the middle of an otherwise white page.
        val isBlank = BlankCheck.isBlank(width = 1080, height = 2000) { x, y ->
            if (y in 1000..1040 && x in 100..600) black else white
        }

        assertFalse(isBlank)
    }

    @Test
    fun `content in the bottom-right corner is found`() {
        val isBlank = BlankCheck.isBlank(width = 1080, height = 2000) { x, y ->
            if (x >= 1070 && y >= 1990) black else white
        }

        assertFalse(isBlank)
    }

    @Test
    fun `empty image is blank`() {
        assertTrue(BlankCheck.isBlank(width = 0, height = 0) { _, _ -> black })
    }
}
