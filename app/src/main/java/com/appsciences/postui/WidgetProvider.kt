package com.appsciences.postui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

/** Widget lifecycle hooks. Enqueues render work; never renders itself. */
class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val store = WidgetStore(context)
        val images = WidgetImages(context)
        val now = System.currentTimeMillis()
        for (id in appWidgetIds) {
            // The launcher rebuilds the widget from initialLayout on every package change, which
            // includes WorkManager toggling its own components, so always re-push what we have (R4.5).
            val uri = images.existing(id, store.seq(id))
            manager.updateAppWidget(
                id,
                if (uri != null) WidgetViews.image(context, id, uri) else WidgetViews.placeholder(context, id),
            )
            // Guard against the WorkManager → APPWIDGET_UPDATE feedback loop (R4.5).
            if (now - store.lastRender(id) > ON_UPDATE_MIN_GAP_MS) RenderWorker.enqueue(context, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) RenderWorker.enqueue(context, id)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            WorkManager.getInstance(context).cancelUniqueWork(RenderWorker.workName(id))
            WidgetImages(context).deleteAll(id)
            WidgetStore(context).clear(id)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.appsciences.postui.action.REFRESH"
        private const val ON_UPDATE_MIN_GAP_MS = 60_000L

        fun refreshIntent(context: Context, appWidgetId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, WidgetProvider::class.java)
                    .setAction(ACTION_REFRESH)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
