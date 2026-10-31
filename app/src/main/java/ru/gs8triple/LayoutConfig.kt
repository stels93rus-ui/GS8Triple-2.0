package ru.gs8triple

import android.graphics.Rect

data class LayoutConfig(
    val windowCount: Int = 3,
    val leftWidth: Int = 400,
    val centerWidth: Int = 760,
    val screenWidth: Int = 1920,
    val screenHeight: Int = 968
) {
    fun bounds(): List<Rect> {
        val w = screenWidth.coerceAtLeast(1)
        val h = screenHeight.coerceAtLeast(1)
        if (windowCount == 2) {
            val left = leftWidth.coerceIn(200, w - 200)
            return listOf(Rect(0, 0, left, h), Rect(left, 0, w, h))
        }
        val left = leftWidth.coerceIn(200, w - 400)
        val center = centerWidth.coerceIn(200, w - left - 200)
        return listOf(
            Rect(0, 0, left, h),
            Rect(left, 0, left + center, h),
            Rect(left + center, 0, w, h)
        )
    }
}
