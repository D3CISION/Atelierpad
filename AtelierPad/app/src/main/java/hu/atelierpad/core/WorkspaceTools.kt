package hu.atelierpad.core

import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.*

/** MRU list of committed choices/paint colors, never every intermediate wheel sample. */
object RecentColors {
    const val LIMIT = 12
    fun add(colors: List<Int>, color: Int): List<Int> {
        val opaque = color or 0xff000000.toInt()
        return (listOf(opaque) + colors.map { it or 0xff000000.toInt() }.filter { it != opaque }).distinct().take(LIMIT)
    }
    fun encode(colors: List<Int>): String = colors.take(LIMIT).joinToString(",") { (it or 0xff000000.toInt()).toString() }
    fun decode(value: String?): List<Int> = value.orEmpty().take(512).split(',').mapNotNull { it.toIntOrNull() }
        .map { it or 0xff000000.toInt() }.distinct().take(LIMIT)
}

/** Legacy v0.4 geometry retained for regression vectors. The v0.5 picker uses StudioColorGeometry. */
object ColorWheelGeometry {
    data class Polar(val hue: Float, val saturation: Float)
    fun at(x: Float, y: Float, radius: Float, previousHue: Float): Polar {
        require(radius.isFinite() && radius > 0 && x.isFinite() && y.isFinite())
        val distance = hypot(x, y)
        val hue = if (distance < .001f) ((previousHue % 360f) + 360f) % 360f
            else ((atan2(y, x) * 180f / PI.toFloat()) + 360f) % 360f
        return Polar(hue, (distance / radius).coerceIn(0f, 1f))
    }
    fun point(hue: Float, saturation: Float, radius: Float): Pair<Float, Float> {
        require(hue.isFinite() && saturation.isFinite() && radius.isFinite() && radius > 0)
        val a = hue * PI.toFloat() / 180f
        return cos(a) * saturation.coerceIn(0f, 1f) * radius to sin(a) * saturation.coerceIn(0f, 1f) * radius
    }
}

/** The UI is top-first, whereas document layers are stored bottom-first. */
object LayerOrder {
    fun dropAt(project: Project, layerId: String, gapFromTop: Int): Project {
        val display = project.layers.asReversed().toMutableList()
        val from = display.indexOfFirst { it.id == layerId }
        require(from >= 0) { "The dragged layer no longer exists" }
        require(gapFromTop in 0..display.size)
        val activeId = project.layers[project.active].id
        val moving = display.removeAt(from)
        val to = (gapFromTop - if (gapFromTop > from) 1 else 0).coerceIn(0, display.size)
        display.add(to, moving)
        val layers = display.asReversed().toMutableList()
        // A clipping layer cannot have a base beneath it at the bottom of a stack.
        if (layers.first().clipped) layers[0] = layers[0].copy(clipped = false)
        return if (layers == project.layers) project else project.copy(layers = layers, active = layers.indexOfFirst { it.id == activeId })
    }
    fun moveBy(project: Project, layerId: String, towardTop: Boolean): Project {
        val display = project.layers.asReversed()
        val index = display.indexOfFirst { it.id == layerId }
        require(index >= 0)
        if ((towardTop && index == 0) || (!towardTop && index == display.lastIndex)) return project
        return dropAt(project, layerId, if (towardTop) index - 1 else index + 2)
    }
}

/** Small, bounded header read: the home screen need not decode all strokes/assets to show a title. */
data class ProjectHeader(val id: String, val title: String, val width: Int, val height: Int, val dpi: Int) {
    companion object {
        fun read(stream: InputStream): ProjectHeader {
            val input = DataInputStream(stream)
            require(input.readInt() == 0x41545044) { "Not an Atelier project" }
            require(input.readInt() in 1..3) { "Unsupported project version" }
            val id = input.readUTF(); val title = input.readUTF()
            val width = input.readInt(); val height = input.readInt(); val dpi = input.readInt()
            require(Project.UUID_PATTERN.matches(id) && title.length in 1..200)
            require(width in 64..4096 && height in 64..4096 && width.toLong() * height <= 12_000_000)
            require(dpi in 72..600)
            return ProjectHeader(id, title, width, height, dpi)
        }
    }
}
