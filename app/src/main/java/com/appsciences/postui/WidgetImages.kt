package com.appsciences.postui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * Stores rendered bitmaps as PNGs and hands them to the launcher by content URI (spec R3.1–R3.4).
 * Bitmaps never travel inside RemoteViews: they exceed the ~1 MB Binder limit.
 */
class WidgetImages(private val context: Context) {

    private val dir = File(context.filesDir, WidgetFiles.DIR)

    /** Writes [bitmap] for render [seq] and returns a URI the launcher may read. */
    fun write(appWidgetId: Int, seq: Int, bitmap: Bitmap): Uri {
        dir.mkdirs()
        val file = File(dir, WidgetFiles.name(appWidgetId, seq))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return grantToLaunchers(uriFor(file))
    }

    /** URI of the image for render [seq] if it is still on disk, or null. */
    fun existing(appWidgetId: Int, seq: Int): Uri? {
        val file = File(dir, WidgetFiles.name(appWidgetId, seq))
        return if (file.exists()) grantToLaunchers(uriFor(file)) else null
    }

    fun deleteStale(appWidgetId: Int, currentSeq: Int) =
        delete(WidgetFiles.stale(names(), appWidgetId, currentSeq))

    fun deleteAll(appWidgetId: Int) =
        delete(WidgetFiles.all(names(), appWidgetId))

    private fun names(): List<String> = dir.list()?.toList().orEmpty()

    private fun delete(names: List<String>) {
        for (name in names) {
            val file = File(dir, name)
            context.revokeUriPermission(uriFor(file), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            file.delete()
        }
    }

    /** The launcher process opens the URI, not us, so it needs an explicit grant. */
    private fun grantToLaunchers(uri: Uri): Uri {
        for (pkg in homePackages()) {
            context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return uri
    }

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    private fun homePackages(): List<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pm = context.packageManager
        val matches = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(home, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(home, 0)
        }
        return matches.map { it.activityInfo.packageName }.distinct()
    }

    companion object {
        fun authority(context: Context) = "${context.packageName}.files"
    }
}
