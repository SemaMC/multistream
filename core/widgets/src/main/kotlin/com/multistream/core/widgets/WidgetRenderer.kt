package com.multistream.core.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class WidgetRenderer @Inject constructor(private val context: Context) {

    suspend fun renderWidget(url: String, width: Int = 400, height: Int = 300): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val canvas = Canvas(bitmap)
                        webView.layout(0, 0, width, height)
                        webView.draw(canvas)
                        if (cont.isActive) {
                            cont.resume(Unit)
                        }
                    }
                }
                webView.loadUrl(url)
            }
        }

        return bitmap
    }
}
