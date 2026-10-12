package hu.atelierpad.app

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import hu.atelierpad.core.StudioColorGeometry
import kotlin.math.*

/** Independent hue ring and SV disc. The gesture stays in its initial region until lift. */
class ColorWheelView(
    context: Context, initial: Int,
    private val changed: (Int) -> Unit,
    private val committed: (Int) -> Unit
) : View(context) {
    private val hsv = FloatArray(3).also { Color.colorToHSV(initial, it); if (it[1] == 0f) it[0] = 330f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var ring: Shader? = null
    private var disc: Bitmap? = null
    private var discHue = Float.NaN
    private var radius = 1f; private var innerRadius = .7f
    private var cx = 0f; private var cy = 0f
    private var pointer = -1
    private var region = 0 // 1: ring; 2: disc
    private var initialHSV = hsv.copyOf()
    private val resolution = 256
    private val saturation = FloatArray(resolution * resolution)
    private val value = FloatArray(resolution * resolution)
    private val coverage = IntArray(resolution * resolution)
    private val pixels = IntArray(resolution * resolution)
    val selectedColor get() = Color.HSVToColor(hsv)
    val brightness get() = hsv[2]
    init {
        isFocusable = true; contentDescription = "Color wheel: outer hue ring, inner saturation and brightness disc"
        val r = (resolution - 2) / 2f
        for (y in 0 until resolution) for (x in 0 until resolution) {
            val dx = x + .5f - resolution / 2f; val dy = y + .5f - resolution / 2f
            val index = y * resolution + x
            val sv = StudioColorGeometry.svAt(dx, dy, r)
            saturation[index] = sv.saturation; value[index] = sv.value
            coverage[index] = ((r + .5f - hypot(dx, dy)).coerceIn(0f, 1f) * 255).roundToInt()
        }
    }
    fun setColor(color: Int) {
        val next = FloatArray(3); Color.colorToHSV(color, next)
        if (next[1] > .0001f) hsv[0] = next[0]
        hsv[1] = next[1]; hsv[2] = next[2]; invalidate()
    }
    // Programmatic accessibility/testing API. There is no separate brightness slider in the UI.
    fun setBrightness(value: Float) { if (!value.isFinite()) return; hsv[2] = value.coerceIn(0f, 1f); changed(selectedColor); invalidate() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f; cy = h / 2f
        radius = (min(w, h) / 2f - context.dp(12)).coerceAtLeast(1f); innerRadius = radius * .73f
        val colors = IntArray(13) { i -> Color.HSVToColor(floatArrayOf(((330f - i * 30f) + 360f) % 360f, 1f, 1f)) }
        ring = SweepGradient(cx, cy, colors, null)
    }
    private fun discBitmap(): Bitmap {
        val image = disc ?: Bitmap.createBitmap(resolution, resolution, Bitmap.Config.ARGB_8888).also { disc = it }
        if (discHue != hsv[0]) {
            val full = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
            val red = Color.red(full) - 255f; val green = Color.green(full) - 255f; val blue = Color.blue(full) - 255f
            for (i in pixels.indices) {
                val s = saturation[i]; val v = value[i]
                pixels[i] = (coverage[i] shl 24) or (((255f + red * s) * v).roundToInt() shl 16) or
                    (((255f + green * s) * v).roundToInt() shl 8) or ((255f + blue * s) * v).roundToInt()
            }
            image.setPixels(pixels, 0, resolution, 0, 0, resolution, resolution); discHue = hsv[0]
        }
        return image
    }
    override fun onDraw(canvas: Canvas) {
        paint.alpha = 255; paint.style = Paint.Style.STROKE; paint.shader = ring; paint.strokeWidth = radius * .17f
        canvas.drawCircle(cx, cy, radius * .905f, paint)
        paint.style = Paint.Style.FILL; paint.shader = null
        canvas.drawBitmap(discBitmap(), null, RectF(cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius), paint)
        fun marker(point: Pair<Float, Float>, r: Float) {
            paint.style = Paint.Style.STROKE; paint.color = 0xaa000000.toInt(); paint.strokeWidth = context.dp(3).toFloat()
            canvas.drawCircle(cx + point.first, cy + point.second, r, paint)
            paint.color = Color.WHITE; paint.strokeWidth = context.dp(1).toFloat()
            canvas.drawCircle(cx + point.first, cy + point.second, r, paint)
        }
        marker(StudioColorGeometry.huePoint(hsv[0], radius * .905f), min(context.dp(9).toFloat(), radius * .085f))
        marker(StudioColorGeometry.svPoint(hsv[1], hsv[2], innerRadius * (resolution - 2f) / resolution), context.dp(6).toFloat())
    }
    private fun update(event: MotionEvent, index: Int) {
        val x = event.getX(index) - cx; val y = event.getY(index) - cy
        if (region == 1) hsv[0] = StudioColorGeometry.hueAt(x, y, hsv[0])
        else {
            val sv = StudioColorGeometry.svAt(x, y, innerRadius * (resolution - 2f) / resolution)
            hsv[1] = sv.saturation; hsv[2] = sv.value
        }
        changed(selectedColor); invalidate()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val distance = hypot(event.x - cx, event.y - cy)
                if (distance > radius + context.dp(6)) return false
                region = if (distance <= innerRadius + radius * .025f) 2 else 1
                pointer = event.getPointerId(0); initialHSV = hsv.copyOf()
                parent?.requestDisallowInterceptTouchEvent(true); update(event, 0)
            }
            MotionEvent.ACTION_MOVE -> if (pointer >= 0) event.findPointerIndex(pointer).takeIf { it >= 0 }?.let { update(event, it) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) {
                update(event, event.actionIndex); committed(selectedColor); pointer = -1
                parent?.requestDisallowInterceptTouchEvent(false); performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (pointer >= 0) { initialHSV.copyInto(hsv); changed(selectedColor); invalidate() }
                pointer = -1; parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { disc = null; discHue = Float.NaN; super.onDetachedFromWindow() }
}

/** A visible 28dp swatch within a 48dp touch target, not a text/HEX field. */
class ColorSwatchView(context: Context, initial: Int, action: () -> Unit) : View(context) {
    var color = initial
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        minimumWidth = context.dp(48); minimumHeight = context.dp(48)
        isClickable = true; isFocusable = true
        contentDescription = "Current color. Open color wheel"; tooltipText = "Color"
        setOnClickListener { action() }
    }
    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) * .29f
        paint.style = Paint.Style.FILL; paint.color = color
        canvas.drawCircle(width / 2f, height / 2f, r, paint)
        paint.style = Paint.Style.STROKE; paint.color = 0xffbbbbbf.toInt(); paint.strokeWidth = context.dp(1).toFloat()
        canvas.drawCircle(width / 2f, height / 2f, r, paint)
    }
}
