package hu.atelierpad.app

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import hu.atelierpad.core.*
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.*

/** Owns a bounded worker and a transient preview. Apply is the only callback that can change a project. */
class EffectsEditorView(
    context: Context, private val assets: AssetStore,
    private val snapshot: Project, private val selection: Selection?, private val kind: String,
    private val onCancel: () -> Unit, private val onApply: (EffectSettings) -> Unit
) : LinearLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var disposed = false
    @Volatile private var revision = 0
    private var settings = EffectSettings()
    private var source: Bitmap? = null
    private var after: Bitmap? = null
    private var ready = false
    private val preview = EffectPreview(context)
    private val status = context.label("Preparing preview...", 11f, Theme.muted)
    private lateinit var accept: View
    private var comparing = false
    private val renderLater = Runnable { render() }
    init {
        orientation = VERTICAL
        addView(context.label(snapshot.layers[snapshot.active].name, 13f).apply { maxLines = 1 }, LayoutParams(-1, context.dp(24)))
        addView(preview, LayoutParams(-1, context.dp(144)))
        addView(status, LayoutParams(-1, context.dp(34)))
        preview.contentDescription = "Effect preview. Hold to compare with original"
        preview.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { comparing = true; preview.image = source; parent?.requestDisallowInterceptTouchEvent(true) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { comparing = false; preview.image = after ?: source; parent?.requestDisallowInterceptTouchEvent(false); if (event.actionMasked == MotionEvent.ACTION_UP) preview.performClick() }
            }
            true
        }
        val controls = context.column()
        when (kind) {
            "effect-hsv" -> {
                context.slider(controls, "Hue", -180f, 180f, 0f, { "${it.roundToInt()} deg" }) { update(settings.copy(hue = it)) }
                context.slider(controls, "Saturation", 0f, 2f, 1f, { "${(it*100).roundToInt()}%" }) { update(settings.copy(saturation = it)) }
            }
            "effect-tone" -> {
                context.slider(controls, "Brightness", -1f, 1f, 0f, { "${(it*100).roundToInt()}%" }) { update(settings.copy(brightness = it)) }
                context.slider(controls, "Contrast", 0f, 2f, 1f, { "${(it*100).roundToInt()}%" }) { update(settings.copy(contrast = it)) }
            }
            "effect-blur" -> context.slider(controls, "Blur radius", 0f, 24f, 0f, { "${String.format(java.util.Locale.US, "%.1f", it)} px" }) { update(settings.copy(blurSigma = it)) }
        }
        controls.gap(8)
        controls.addView(context.label(if (selection == null) "Active layer only. Hold preview for Before." else "Active selection on this layer. Hold preview for Before.", 11f, Theme.muted))
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(controls) }
        addView(scroll, LayoutParams(-1, 0, 1f))
        val actions = context.row()
        actions.addView(context.button("Cancel", action = onCancel), LayoutParams(0, context.dp(48), 1f).apply { rightMargin = context.dp(6) })
        accept = context.button("Apply", true) { if (ready && !settings.isIdentity) onApply(settings) }
        accept.isEnabled = false; accept.alpha = .4f
        actions.addView(accept, LayoutParams(0, context.dp(48), 1f).apply { leftMargin = context.dp(6) })
        addView(actions, LayoutParams(-1, context.dp(56)))
        loadSource()
    }
    private fun loadSource() {
        worker.execute {
            try {
                val small = RasterRenderer(assets).use { renderer ->
                    val image = renderer.layer(snapshot, snapshot.layers[snapshot.active])
                    val scale = min(1f, 512f / max(image.width, image.height))
                    val w = (image.width * scale).roundToInt().coerceAtLeast(1)
                    val h = (image.height * scale).roundToInt().coerceAtLeast(1)
                    if (w == image.width && h == image.height) requireNotNull(image.copy(Bitmap.Config.ARGB_8888, false))
                    else Bitmap.createScaledBitmap(image, w, h, true)
                }
                handler.post {
                    if (!disposed) { source = small; preview.image = small; ready = true; update(settings) }
                    else small.recycle()
                }
            } catch (error: Exception) { failed(error.message ?: "Preview failed") }
            catch (_: OutOfMemoryError) { failed("Not enough memory for this effect preview") }
        }
    }
    private fun failed(text: String, expectedRevision: Int? = null) { handler.post { if (!disposed && (expectedRevision == null || expectedRevision == revision)) { status.text = text; ready = false; accept.isEnabled = false; accept.alpha = .4f } } }
    private fun update(next: EffectSettings) {
        settings = next; revision++
        accept.isEnabled = ready && !settings.isIdentity; accept.alpha = if (accept.isEnabled) 1f else .4f
        handler.removeCallbacks(renderLater)
        if (ready) { status.text = "Updating preview..."; handler.postDelayed(renderLater, 100) }
    }
    private fun render() {
        val input = source ?: return
        val number = revision; val values = settings
        try {
            worker.execute {
                if (disposed || number != revision) return@execute
                try {
                    val result = EffectRaster.apply(input, values, selection, snapshot.width, snapshot.height)
                    handler.post {
                        if (!disposed && number == revision) {
                            after = result; if (!comparing) preview.image = result
                            status.text = "Preview / ${if (selection == null) "active layer" else "active selection"}"
                        } else result.recycle()
                    }
                } catch (error: Exception) { failed(error.message ?: "Preview failed", number) }
                catch (_: OutOfMemoryError) { failed("Not enough memory for this effect", number) }
            }
        } catch (_: RejectedExecutionException) { /* Panel was closed while a queued preview was being scheduled. */ }
    }
    fun release() {
        disposed = true; revision++; handler.removeCallbacks(renderLater)
        worker.shutdownNow(); preview.image = null; source = null; after = null
        // A worker or hardware display list may still own a bitmap. Do not recycle shared bitmaps early.
    }
}

/** Neutral checkerboard behind transparency. Preview uses the layer's local coordinates. */
private class EffectPreview(context: Context) : View(context) {
    var image: Bitmap? = null
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun onDraw(canvas: Canvas) {
        val size = context.dp(12)
        for (y in 0 until height step size) for (x in 0 until width step size) {
            paint.color = if ((x/size + y/size) % 2 == 0) 0xff29292f.toInt() else 0xff35353c.toInt()
            canvas.drawRect(x.toFloat(), y.toFloat(), (x+size).toFloat(), (y+size).toFloat(), paint)
        }
        image?.let { bitmap ->
            val scale = min(width.toFloat()/bitmap.width, height.toFloat()/bitmap.height)
            val w = bitmap.width * scale; val h = bitmap.height * scale
            canvas.drawBitmap(bitmap, null, RectF((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2), paint)
        }
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
