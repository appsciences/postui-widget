package com.appsciences.postui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/** Renders one widget and pushes the result to the launcher. */
class RenderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getInt(KEY_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return Result.failure()

        val bitmap = try {
            // Phase 1: hardcoded URL and size (spec §9).
            WidgetRenderer(applicationContext).render(BuildConfig.SPIKE_URL, SPIKE_WIDTH_PX, SPIKE_HEIGHT_PX)
        } catch (e: RenderException) {
            // Leave the previous image in place (R2.5, R5.1).
            Log.w(TAG, "Render failed for widget $id; keeping previous image", e)
            return Result.failure()
        }

        val store = WidgetStore(applicationContext)
        val images = WidgetImages(applicationContext)
        val seq = store.nextSeq(id)
        val uri = try {
            images.write(id, seq, bitmap)
        } finally {
            bitmap.recycle()
        }

        AppWidgetManager.getInstance(applicationContext)
            .updateAppWidget(id, WidgetViews.image(applicationContext, id, uri))
        store.setLastRender(id, System.currentTimeMillis())
        images.deleteStale(id, seq)
        Log.i(TAG, "Rendered widget $id as seq $seq")
        return Result.success()
    }

    companion object {
        private const val TAG = "RenderWorker"
        private const val KEY_APP_WIDGET_ID = "appWidgetId"

        const val SPIKE_WIDTH_PX = 1080
        const val SPIKE_HEIGHT_PX = 2000

        fun workName(appWidgetId: Int) = "render_$appWidgetId"

        fun enqueue(context: Context, appWidgetId: Int) {
            val request = OneTimeWorkRequestBuilder<RenderWorker>()
                .setInputData(workDataOf(KEY_APP_WIDGET_ID to appWidgetId))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName(appWidgetId), ExistingWorkPolicy.REPLACE, request)
        }
    }
}
