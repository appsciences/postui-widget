package com.appsciences.postui

import android.graphics.Color
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 1 risk: does an off-screen, unattached WebView actually draw pixels? (spec §10) */
@RunWith(AndroidJUnit4::class)
class WidgetRendererTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rendersPageAtRequestedSize() = runBlocking {
        val bitmap = WidgetRenderer(context, settleMs = 200).render(page(STATIC), WIDTH, HEIGHT)

        assertEquals(WIDTH, bitmap.width)
        assertEquals(HEIGHT, bitmap.height)
        assertEquals(Color.RED, bitmap.getPixel(10, 10))
        assertEquals(Color.BLUE, bitmap.getPixel(WIDTH / 2, HEIGHT / 2))
    }

    @Test
    fun waitsForJavaScriptToSettle() = runBlocking {
        // The block starts green and JS turns it blue 300 ms after load (R2.4).
        val bitmap = WidgetRenderer(context).render(page(DELAYED_JS), WIDTH, HEIGHT)

        assertEquals(Color.BLUE, bitmap.getPixel(WIDTH / 2, HEIGHT / 2))
    }

    @Test
    fun blankPageFails() {
        assertThrows(RenderException::class.java) {
            runBlocking { WidgetRenderer(context, settleMs = 200).render(page(BLANK), WIDTH, HEIGHT) }
        }
    }

    @Test
    fun loadErrorFails() {
        assertThrows(RenderException::class.java) {
            // Nothing listens on the discard port, so the main frame fails to load (R2.6).
            runBlocking { WidgetRenderer(context).render("http://127.0.0.1:9/", WIDTH, HEIGHT) }
        }
    }

    @Test
    fun timeoutFails() {
        assertThrows(RenderException::class.java) {
            runBlocking { WidgetRenderer(context, timeoutMs = 1).render(page(STATIC), WIDTH, HEIGHT) }
        }
    }

    private fun page(html: String) =
        "data:text/html;base64," + Base64.encodeToString(html.toByteArray(), Base64.NO_WRAP)

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2000

        const val STYLE = """
            <style>
              html, body { margin: 0; height: 100%; background: #f00; }
              #block { position: absolute; left: 25%; top: 25%; width: 50%; height: 50%; background: #00f; }
            </style>"""

        const val STATIC = "<html><head>$STYLE</head><body><div id='block'></div></body></html>"

        const val DELAYED_JS = """<html><head>$STYLE</head><body>
            <div id='block' style='background:#0f0'></div>
            <script>setTimeout(function () { document.getElementById('block').style.background = '#00f'; }, 300);</script>
            </body></html>"""

        const val BLANK = "<html><body style='margin:0;background:#fff'></body></html>"
    }
}
