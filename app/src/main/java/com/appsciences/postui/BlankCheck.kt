package com.appsciences.postui

/** R5.3: a render whose sampled pixels are all one colour is treated as failed. No Android types. */
object BlankCheck {

    /** Sampling stride in px. Dense enough to hit a single line of body text. */
    private const val STEP = 8

    fun isBlank(width: Int, height: Int, pixelAt: (x: Int, y: Int) -> Int): Boolean {
        if (width <= 0 || height <= 0) return true
        val xs = (0 until width step STEP) + (width - 1)
        val ys = (0 until height step STEP) + (height - 1)
        val first = pixelAt(0, 0)
        return ys.all { y -> xs.all { x -> pixelAt(x, y) == first } }
    }
}
