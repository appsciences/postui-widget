package com.appsciences.postui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View.MeasureSpec
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RenderException(message: String) : Exception(message)

/**
 * Draws a URL into a bitmap with an off-screen WebView (spec §5, R2.1–R2.6).
 * Knows nothing about app widgets.
 */
class WidgetRenderer(
    private val context: Context,
    private val settleMs: Long = DEFAULT_SETTLE_MS,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {

    /** @throws RenderException on load error, timeout, or a blank result. */
    suspend fun render(url: String, widthPx: Int, heightPx: Int): Bitmap =
        withTimeoutOrNull(timeoutMs) {
            // WebView must be created and driven on the main thread (R2.1).
            withContext(Dispatchers.Main) { renderOnMain(url, widthPx, heightPx) }
        } ?: throw RenderException("Timed out after $timeoutMs ms: $url")

    private suspend fun renderOnMain(url: String, widthPx: Int, heightPx: Int): Bitmap {
        val webView = WebView(context)
        try {
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            // Never attached to a window, so size it by hand (R2.3).
            webView.measure(
                MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY),
            )
            webView.layout(0, 0, widthPx, heightPx)

            load(webView, url)
            delay(settleMs) // onPageFinished fires before JS-rendered content appears (R2.4).

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            webView.draw(Canvas(bitmap))
            if (BlankCheck.isBlank(bitmap.width, bitmap.height, bitmap::getPixel)) {
                bitmap.recycle()
                throw RenderException("Rendered a blank page: $url")
            }
            return bitmap
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private suspend fun load(webView: WebView, url: String) =
        suspendCancellableCoroutine { cont ->
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, finishedUrl: String?) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && cont.isActive) {
                        cont.resumeWithException(RenderException("Load failed (${error.description}): $url"))
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame && cont.isActive) {
                        cont.resumeWithException(RenderException("HTTP ${errorResponse.statusCode}: $url"))
                    }
                }
            }
            webView.loadUrl(url)
        }

    companion object {
        const val DEFAULT_SETTLE_MS = 750L
        const val DEFAULT_TIMEOUT_MS = 20_000L
    }
}
