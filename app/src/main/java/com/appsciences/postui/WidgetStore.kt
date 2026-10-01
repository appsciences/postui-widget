package com.appsciences.postui

import android.content.Context
import androidx.core.content.edit

/** Per-widget state in SharedPreferences, keyed by appWidgetId (spec §6.2). */
class WidgetStore(context: Context) {

    private val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    /** Sequence number of the latest render, or 0 if none. */
    fun seq(appWidgetId: Int): Int = prefs.getInt(key(appWidgetId, "seq"), 0)

    /** Increments and returns the cache-busting sequence number (R3.3). */
    fun nextSeq(appWidgetId: Int): Int {
        val next = seq(appWidgetId) + 1
        prefs.edit { putInt(key(appWidgetId, "seq"), next) }
        return next
    }

    fun lastRender(appWidgetId: Int): Long = prefs.getLong(key(appWidgetId, "last_render"), 0L)

    fun setLastRender(appWidgetId: Int, epochMs: Long) =
        prefs.edit { putLong(key(appWidgetId, "last_render"), epochMs) }

    fun clear(appWidgetId: Int) = prefs.edit {
        prefs.all.keys.filter { it.startsWith("widget_${appWidgetId}_") }.forEach(::remove)
    }

    private fun key(appWidgetId: Int, field: String) = "widget_${appWidgetId}_$field"
}
