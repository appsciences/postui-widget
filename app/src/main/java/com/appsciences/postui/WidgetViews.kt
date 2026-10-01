package com.appsciences.postui

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.RemoteViews

/** Builds the widget's RemoteViews. Images go by URI only (R3.1). */
object WidgetViews {

    fun placeholder(context: Context, appWidgetId: Int): RemoteViews =
        base(context, appWidgetId).apply {
            setViewVisibility(R.id.placeholder, View.VISIBLE)
        }

    fun image(context: Context, appWidgetId: Int, uri: Uri): RemoteViews =
        base(context, appWidgetId).apply {
            setImageViewUri(R.id.image, uri)
            setViewVisibility(R.id.placeholder, View.GONE)
        }

    private fun base(context: Context, appWidgetId: Int) =
        RemoteViews(context.packageName, R.layout.widget).apply {
            setOnClickPendingIntent(R.id.root, WidgetProvider.refreshIntent(context, appWidgetId))
        }
}
