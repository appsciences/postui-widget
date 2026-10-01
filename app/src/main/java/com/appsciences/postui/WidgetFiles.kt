package com.appsciences.postui

/** Naming and cleanup rules for rendered widget PNGs (spec R3.3, R3.4). No Android types. */
object WidgetFiles {

    /** Directory under `filesDir`; must match `res/xml/file_paths.xml`. */
    const val DIR = "widgets"

    private val PATTERN = Regex("""^(\d+)_(\d+)\.png$""")

    /** A new name per render, so the launcher never serves a cached image for a new URI. */
    fun name(appWidgetId: Int, seq: Int): String = "${appWidgetId}_$seq.png"

    /** Files of [appWidgetId] rendered before [currentSeq]. */
    fun stale(names: List<String>, appWidgetId: Int, currentSeq: Int): List<String> =
        names.filter { name -> parse(name)?.let { (id, seq) -> id == appWidgetId && seq < currentSeq } == true }

    /** Every file of [appWidgetId]. */
    fun all(names: List<String>, appWidgetId: Int): List<String> =
        names.filter { name -> parse(name)?.first == appWidgetId }

    private fun parse(name: String): Pair<Int, Int>? {
        val (id, seq) = PATTERN.matchEntire(name)?.destructured ?: return null
        return (id.toIntOrNull() ?: return null) to (seq.toIntOrNull() ?: return null)
    }
}
