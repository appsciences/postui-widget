package com.appsciences.postui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WidgetImagesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val images = WidgetImages(context)
    private val dir = File(context.filesDir, WidgetFiles.DIR)

    @After
    fun cleanUp() {
        images.deleteAll(ID)
        images.deleteAll(OTHER_ID)
    }

    @Test
    fun writeStoresPngReadableThroughContentUri() {
        val uri = images.write(ID, seq = 1, bitmap = solid(Color.BLUE))

        assertEquals("content", uri.scheme)
        assertTrue(File(dir, WidgetFiles.name(ID, 1)).exists())
        val decoded = context.contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)
        assertEquals(WIDTH, decoded.width)
        assertEquals(HEIGHT, decoded.height)
        assertEquals(Color.BLUE, decoded.getPixel(WIDTH / 2, HEIGHT / 2))
    }

    @Test
    fun eachSequenceGetsADistinctUri() {
        // R3.3: the launcher caches by URI, so a re-render must never reuse one.
        val first = images.write(ID, seq = 1, bitmap = solid(Color.BLUE))
        val second = images.write(ID, seq = 2, bitmap = solid(Color.RED))

        assertTrue(first != second)
    }

    @Test
    fun deleteStaleKeepsCurrentAndOtherWidgets() {
        images.write(ID, seq = 1, bitmap = solid(Color.BLUE))
        images.write(ID, seq = 2, bitmap = solid(Color.BLUE))
        images.write(OTHER_ID, seq = 1, bitmap = solid(Color.BLUE))

        images.deleteStale(ID, currentSeq = 2)

        assertEquals(
            setOf(WidgetFiles.name(ID, 2), WidgetFiles.name(OTHER_ID, 1)),
            dir.list()!!.filter { it.startsWith("$ID") || it.startsWith("$OTHER_ID") }.toSet(),
        )
    }

    @Test
    fun deleteAllRemovesEveryFileOfTheWidget() {
        images.write(ID, seq = 1, bitmap = solid(Color.BLUE))
        images.write(ID, seq = 2, bitmap = solid(Color.BLUE))

        images.deleteAll(ID)

        assertEquals(emptyList<String>(), WidgetFiles.all(dir.list()!!.toList(), ID))
    }

    @Test
    fun existingReturnsUriOfCurrentImageOnly() {
        // onUpdate re-pushes the last image after the system drops our RemoteViews (R4.5).
        val written = images.write(ID, seq = 3, bitmap = solid(Color.BLUE))

        assertEquals(written, images.existing(ID, seq = 3))
        assertNull(images.existing(ID, seq = 4))
        assertNull(images.existing(OTHER_ID, seq = 3))
    }

    @Test
    fun remoteViewsForFullScreenImageFitInABinderTransaction() {
        // R3.1 / A8: a 1080×2000 ARGB bitmap is ~8.6 MB; delivered by URI the payload must stay tiny.
        val uri = images.write(ID, seq = 1, bitmap = solid(Color.BLUE))
        val views = WidgetViews.image(context, ID, uri)

        val parcel = Parcel.obtain()
        try {
            views.writeToParcel(parcel, 0)
            assertTrue("RemoteViews parcel is ${parcel.dataSize()} bytes", parcel.dataSize() < 50_000)
        } finally {
            parcel.recycle()
        }
    }

    private fun solid(color: Int) =
        Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private companion object {
        const val ID = 990_001
        const val OTHER_ID = 990_002
        const val WIDTH = 1080
        const val HEIGHT = 2000
    }
}
