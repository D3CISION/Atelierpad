package hu.atelierpad.app

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Color
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.widget.*
import hu.atelierpad.core.*

/** Native long-press drag with insertion marker, edge autoscroll and accessible move actions. */
class LayerStackView(
    context: Context, private val document: () -> Project, private val renderer: RasterRenderer,
    private val selected: (Int) -> Unit, private val visibilityChanged: (Int) -> Unit,
    private val settings: (Int) -> Unit, private val reordered: (Project) -> Unit
) : FrameLayout(context) {
    private data class Drag(val owner: Any, val projectId: String, val layerId: String)
    private val owner = Any()
    private val list = context.column()
    private val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(list) }
    private val marker = View(context).apply { setBackgroundColor(Theme.accent); visibility = GONE }
    private val rows = ArrayList<View>()
    private var dragging: Drag? = null
    private var dragged: View? = null
    private var gap = 0
    private var fingerY = 0f
    private var scrollStep = 0
    private val autoScroll = object : Runnable {
        override fun run() {
            if (dragging == null || scrollStep == 0) return
            scroll.scrollBy(0, scrollStep); updateMarker(fingerY); postDelayed(this, 16)
        }
    }
    init {
        addView(scroll, LayoutParams(-1, -1))
        addView(marker, LayoutParams(-1, context.dp(2)).apply { leftMargin = context.dp(4); rightMargin = context.dp(4) })
        buildRows()
        setOnDragListener { _, event -> handleDrag(event) }
    }
    private fun buildRows() {
        val project = document()
        project.layers.indices.reversed().forEach { index ->
            val layer = project.layers[index]
            val box = context.row().apply {
                setPadding(context.dp(8), context.dp(6), context.dp(4), context.dp(6))
                background = rounded(if (index == project.active) Theme.selected else Theme.card, context.dp(12).toFloat())
                isClickable = true; isFocusable = true
                contentDescription = "${layer.name}. Hold and drag to reorder"
                setOnClickListener { selected(index) }
            }
            val thumb = LayerThumbnail(context, renderer, document, layer.id)
            box.addView(thumb, LinearLayout.LayoutParams(context.dp(44), context.dp(44)).apply { marginEnd = context.dp(10) })
            val text = context.column()
            text.addView(context.label(layer.name, 13f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            text.gap(5)
            text.addView(context.label("${(layer.opacity * 100).toInt()}%  ${layer.mixing.name.lowercase().replace('_', ' ')}" +
                (if (layer.clipped) " / clip" else "") + (if (layer.locked) " / locked" else ""), 10f, Theme.muted))
            box.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            box.addView(context.iconButton(if (layer.visible) R.drawable.ic_eye else R.drawable.ic_eye_off,
                if (layer.visible) "Hide ${layer.name}" else "Show ${layer.name}") { visibilityChanged(index) },
                LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
            box.addView(context.iconButton(R.drawable.ic_settings, "Settings for ${layer.name}") { settings(index) },
                LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
            box.setOnLongClickListener {
                val data = Drag(owner, document().id, layer.id)
                val clip = ClipData(ClipDescription("Atelier layer", arrayOf("application/x-atelier-layer")), ClipData.Item(layer.id))
                val started = box.startDragAndDrop(clip, View.DragShadowBuilder(box), data, 0)
                if (started) { dragged = box; box.alpha = .35f; box.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
                started
            }
            box.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    if (index < project.layers.lastIndex) info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.layer_move_up, "Move layer up"))
                    if (index > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.layer_move_down, "Move layer down"))
                }
                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    if (action == R.id.layer_move_up || action == R.id.layer_move_down) {
                        reordered(LayerOrder.moveBy(document(), layer.id, action == R.id.layer_move_up)); return true
                    }
                    return super.performAccessibilityAction(host, action, args)
                }
            }
            rows.add(box)
            list.addView(box, LinearLayout.LayoutParams(-1, context.dp(72)).apply { bottomMargin = context.dp(8) })
        }
    }
    private fun updateMarker(y: Float) {
        fingerY = y
        val local = y + scroll.scrollY
        gap = rows.indexOfFirst { local < it.top + it.height / 2f }.let { if (it < 0) rows.size else it }
        val boundary = if (gap < rows.size) rows[gap].top else rows.lastOrNull()?.bottom ?: 0
        marker.translationY = (boundary - scroll.scrollY).toFloat().coerceIn(0f, (height - context.dp(2)).coerceAtLeast(0).toFloat())
    }
    private fun handleDrag(event: DragEvent): Boolean {
        val data = event.localState as? Drag ?: return false
        if (data.owner !== owner || data.projectId != document().id) return false
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> { dragging = data; marker.visibility = VISIBLE; return true }
            DragEvent.ACTION_DRAG_LOCATION -> {
                updateMarker(event.y)
                scrollStep = when { event.y < context.dp(48) -> -context.dp(7); event.y > height - context.dp(48) -> context.dp(7); else -> 0 }
                removeCallbacks(autoScroll); if (scrollStep != 0) post(autoScroll)
            }
            DragEvent.ACTION_DROP -> {
                updateMarker(event.y)
                val next = LayerOrder.dropAt(document(), data.layerId, gap)
                // Rebuild after the platform has delivered ACTION_DRAG_ENDED to this tree.
                post { reordered(next) }; return true
            }
            DragEvent.ACTION_DRAG_EXITED -> { scrollStep = 0; removeCallbacks(autoScroll); marker.visibility = GONE }
            DragEvent.ACTION_DRAG_ENTERED -> marker.visibility = VISIBLE
            DragEvent.ACTION_DRAG_ENDED -> stopDrag()
        }
        return true
    }
    private fun stopDrag() { dragging = null; scrollStep = 0; removeCallbacks(autoScroll); marker.visibility = GONE; dragged?.alpha = 1f; dragged = null }
    override fun onDetachedFromWindow() { stopDrag(); super.onDetachedFromWindow() }
}
