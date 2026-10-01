package com.appsciences.postui

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetFilesTest {

    @Test
    fun `name encodes widget id and sequence`() {
        assertEquals("42_7.png", WidgetFiles.name(appWidgetId = 42, seq = 7))
    }

    @Test
    fun `stale returns only older files of the same widget`() {
        val names = listOf("5_1.png", "5_2.png", "5_3.png", "6_1.png", "56_1.png")

        assertEquals(listOf("5_1.png", "5_2.png"), WidgetFiles.stale(names, appWidgetId = 5, currentSeq = 3))
    }

    @Test
    fun `all returns every file of the widget and nothing else`() {
        val names = listOf("5_1.png", "5_9.png", "6_1.png", "15_1.png", "notes.txt", "5_x.png")

        assertEquals(listOf("5_1.png", "5_9.png"), WidgetFiles.all(names, appWidgetId = 5))
    }

    @Test
    fun `unrelated files are never selected`() {
        val names = listOf("5_1.png.tmp", "5.png", "_1.png", "5_1.jpg")

        assertEquals(emptyList<String>(), WidgetFiles.all(names, appWidgetId = 5))
        assertEquals(emptyList<String>(), WidgetFiles.stale(names, appWidgetId = 5, currentSeq = 99))
    }
}
