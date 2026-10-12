package hu.atelierpad.core

import kotlin.math.*

/** Increases upwards; shared by pointer, keyboard and accessibility input. */
object VerticalSliderGeometry {
    fun progress(y: Float, top: Float, bottom: Float): Int {
        require(y.isFinite() && top.isFinite() && bottom.isFinite() && bottom > top)
        return ((bottom - y) / (bottom - top) * 1000f).roundToInt().coerceIn(0, 1000)
    }
    fun y(progress: Int, top: Float, bottom: Float): Float {
        require(top.isFinite() && bottom.isFinite() && bottom > top)
        return bottom - progress.coerceIn(0, 1000) / 1000f * (bottom - top)
    }
}

/** Outer hue ring + inner saturation/value disc. Disc maps bijectively to the HSV square.
 * A smooth elliptical-grid square/disc transform keeps WHITE, pure hue and BLACK reachable at the rim. */
object StudioColorGeometry {
    data class SV(val saturation: Float, val value: Float)
    fun hueAt(x: Float, y: Float, fallback: Float): Float {
        require(x.isFinite() && y.isFinite() && fallback.isFinite())
        return if (hypot(x, y) < .001f) wrap(fallback) else wrap(330f - atan2(y, x) * 180f / PI.toFloat())
    }
    fun huePoint(hue: Float, radius: Float): Pair<Float, Float> {
        require(hue.isFinite() && radius.isFinite() && radius > 0)
        val angle = (330f - hue) * PI.toFloat() / 180f
        return cos(angle) * radius to sin(angle) * radius
    }
    fun svAt(x: Float, y: Float, radius: Float): SV {
        require(x.isFinite() && y.isFinite() && radius.isFinite() && radius > 0)
        var u = x.toDouble() / radius; var v = y.toDouble() / radius
        val distance = hypot(u, v)
        if (distance > 1.0) { u /= distance; v /= distance }
        // Inverse elliptical-grid square/disc map: smooth, with no diagonal seams.
        val cross = 2.0 * sqrt(2.0)
        val a = (sqrt((2.0+u*u-v*v+cross*u).coerceAtLeast(0.0)) - sqrt((2.0+u*u-v*v-cross*u).coerceAtLeast(0.0))) / 2.0
        val b = (sqrt((2.0-u*u+v*v+cross*v).coerceAtLeast(0.0)) - sqrt((2.0-u*u+v*v-cross*v).coerceAtLeast(0.0))) / 2.0
        return SV(((a+1.0)/2.0).toFloat().coerceIn(0f,1f), ((1.0-b)/2.0).toFloat().coerceIn(0f,1f))
    }
    fun svPoint(saturation: Float, value: Float, radius: Float): Pair<Float, Float> {
        require(saturation.isFinite() && value.isFinite() && radius.isFinite() && radius > 0)
        val a = saturation.coerceIn(0f, 1f) * 2.0 - 1.0
        val b = 1.0 - value.coerceIn(0f, 1f) * 2.0
        return (a * sqrt(1.0 - b*b/2.0) * radius).toFloat() to (b * sqrt(1.0 - a*a/2.0) * radius).toFloat()
    }
    private fun wrap(h: Float) = ((h % 360f) + 360f) % 360f
}
