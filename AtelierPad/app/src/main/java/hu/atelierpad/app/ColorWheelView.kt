package hu.atelierpad.app

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import hu.atelierpad.core.ColorWheelGeometry
import kotlin.math.min

/** A finger/pen driven hue-saturation disc. Value is adjusted by the slider below it. */
class ColorWheelView(
    context: Context, initial: Int,
    private val changed: (Int) -> Unit,
    private val committed: (Int) -> Unit
) : View(context) {
    private val hsv = FloatArray(3).also { Color.colorToHSV(initial, it) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var hueShader: Shader? = null
    private var saturationShader: Shader? = null
    private var radius = 1f
    private var cx = 0f; private var cy = 0f
    private var pointer = -1
    private var initialColor = initial
    val selectedColor get() = Color.HSVToColor(hsv)
    val brightness get() = hsv[2]
    init { isFocusable = true; contentDescription = "Color wheel: hue and saturation" }
    fun setColor(color: Int) { Color.colorToHSV(color, hsv); invalidate() }
    fun setBrightness(value: Float) { hsv[2] = value.coerceIn(0f, 1f); changed(selectedColor); invalidate() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f; cy = h / 2f
        radius = (min(w, h) / 2f - context.dp(14)).coerceAtLeast(1f)
        val colors = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
        hueShader = SweepGradient(cx, cy, colors, null)
        saturationShader = RadialGradient(cx, cy, radius, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }
    override fun onDraw(canvas: Canvas) {
        paint.style = Paint.Style.FILL; paint.alpha = 255
        paint.shader = hueShader; canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = saturationShader; canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null; paint.color = Color.BLACK; paint.alpha = ((1f - hsv[2]) * 255).toInt()
        canvas.drawCircle(cx, cy, radius, paint)
        paint.alpha = 255; paint.style = Paint.Style.STROKE
        paint.color = 0x44ffffff; paint.strokeWidth = context.dp(1).toFloat()
        canvas.drawCircle(cx, cy, radius, paint)
        val (x, y) = ColorWheelGeometry.point(hsv[0], hsv[1], radius)
        paint.color = Color.BLACK; paint.strokeWidth = context.dp(4).toFloat()
        canvas.drawCircle(cx + x, cy + y, context.dp(8).toFloat(), paint)
        paint.color = Color.WHITE; paint.strokeWidth = context.dp(2).toFloat()
        canvas.drawCircle(cx + x, cy + y, context.dp(8).toFloat(), paint)
    }
    private fun update(e: MotionEvent, index: Int) {
        val polar = ColorWheelGeometry.at(e.getX(index) - cx, e.getY(index) - cy, radius, hsv[0])
        hsv[0] = polar.hue; hsv[1] = polar.saturation
        changed(selectedColor); invalidate()
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointer = e.getPointerId(0); initialColor = selectedColor
                parent?.requestDisallowInterceptTouchEvent(true); update(e, 0)
            }
            MotionEvent.ACTION_MOVE -> e.findPointerIndex(pointer).takeIf { it >= 0 }?.let { update(e, it) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (e.getPointerId(e.actionIndex) == pointer) {
                update(e, e.actionIndex); committed(selectedColor); pointer = -1
                parent?.requestDisallowInterceptTouchEvent(false); performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                pointer = -1; setColor(initialColor); changed(initialColor)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
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
