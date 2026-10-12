package hu.atelierpad.app

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import hu.atelierpad.core.VerticalSliderGeometry
import kotlin.math.abs

/** Narrow visible track, 52dp-wide target. No icon/label. Independent finger/pen pointer ownership. */
class VerticalSliderView(context: Context, title: String) : View(context) {
    var progress = 0
        set(value) { field = value.coerceIn(0, 1000); updateDescription(); invalidate() }
    var formatValue: (Int) -> String = { "$it" }
    var changed: (Int) -> Unit = {}
    var tracking: (Boolean) -> Unit = {}
    var exactValue: () -> Unit = {}
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var pointer = -1
    private var initialProgress = 0
    private var downX = 0f; private var downY = 0f
    private var held = false
    private val handler = Handler(Looper.getMainLooper())
    private val hold = Runnable { if (pointer != -1) { held = true; tracking(false); performLongClick() } }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    val thumbY get() = VerticalSliderGeometry.y(progress, topEdge, bottomEdge)
    private val topEdge get() = context.dp(14).toFloat()
    private val bottomEdge get() = (height - context.dp(14)).toFloat().coerceAtLeast(topEdge + 1f)
    init {
        contentDescription = title; tooltipText = "$title - hold for exact value"
        isFocusable = true; isClickable = true; minimumWidth = context.dp(48)
        setOnLongClickListener { exactValue(); true }
    }
    private fun updateDescription() {
        if (Build.VERSION.SDK_INT >= 30) stateDescription = formatValue(progress)
    }
    override fun onDraw(canvas: Canvas) {
        val center = width / 2f
        val halfTrack = context.dp(10).toFloat()
        val y = thumbY
        bounds.set(center - halfTrack, topEdge - context.dp(5), center + halfTrack, bottomEdge + context.dp(5))
        paint.style = Paint.Style.FILL; paint.color = 0xff2c2c32.toInt()
        canvas.drawRoundRect(bounds, halfTrack, halfTrack, paint)
        val clip = Path().apply { addRoundRect(bounds, halfTrack, halfTrack, Path.Direction.CW) }
        val save = canvas.save(); canvas.clipPath(clip)
        paint.shader = LinearGradient(0f, y, 0f, bottomEdge + 1f, 0xff55555f.toInt(), 0xff393940.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(bounds.left, y, bounds.right, bounds.bottom, paint)
        paint.shader = null; canvas.restoreToCount(save)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = context.dp(1).toFloat(); paint.color = 0x224f4f59
        canvas.drawRoundRect(bounds, halfTrack, halfTrack, paint)
        paint.style = Paint.Style.FILL; paint.color = 0x66000000
        val halfThumb = context.dp(15).toFloat(); val halfHeight = context.dp(6).toFloat()
        bounds.set(center - halfThumb, y - halfHeight + context.dp(2), center + halfThumb, y + halfHeight + context.dp(2))
        canvas.drawRoundRect(bounds, context.dp(5).toFloat(), context.dp(5).toFloat(), paint)
        bounds.offset(0f, -context.dp(2).toFloat())
        paint.color = if (pointer >= 0) Color.WHITE else 0xffd4d4dd.toInt()
        canvas.drawRoundRect(bounds, context.dp(5).toFloat(), context.dp(5).toFloat(), paint)
    }
    private fun update(y: Float) {
        val next = VerticalSliderGeometry.progress(y, topEdge, bottomEdge)
        if (next != progress) { progress = next; changed(next) }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointer = event.getPointerId(0); initialProgress = progress; held = false
                downX = event.x; downY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                update(event.y); tracking(true)
                handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong()); invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (index >= 0 && !held) {
                    if (abs(event.getX(index) - downX) > touchSlop || abs(event.getY(index) - downY) > touchSlop) handler.removeCallbacks(hold)
                    update(event.getY(index))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointer) {
                    if (!held) { update(event.getY(event.actionIndex)); performClick() }
                    endGesture()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (pointer >= 0 && !held) { progress = initialProgress; changed(progress) }
                endGesture()
            }
        }
        return true
    }
    private fun endGesture() {
        handler.removeCallbacks(hold); pointer = -1; tracking(false)
        parent?.requestDisallowInterceptTouchEvent(false); invalidate()
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { handler.removeCallbacks(hold); pointer = -1; super.onDetachedFromWindow() }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, 1000f, progress.toFloat())
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        info.text = formatValue(progress)
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (!isEnabled) return false
        val next = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> progress + 10
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> progress - 10
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                if (arguments == null || !arguments.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)) return false
                val value = arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)
                if (!value.isFinite()) return false
                value.toInt()
            }
            else -> return super.performAccessibilityAction(action, arguments)
        }
        progress = next; changed(progress); return true
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val delta = when (keyCode) { KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT -> 10; KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT -> -10; else -> return super.onKeyDown(keyCode, event) }
        progress += delta; changed(progress); return true
    }
}
