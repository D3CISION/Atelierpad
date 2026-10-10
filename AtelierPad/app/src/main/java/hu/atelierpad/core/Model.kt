package hu.atelierpad.core

import java.util.UUID
import kotlin.math.*

/** Platform-independent, immutable project snapshots. Coordinates are canvas pixels. */
data class Sample(val x: Float, val y: Float, val pressure: Float = 1f) {
    fun valid() = x.isFinite() && y.isFinite() && pressure.isFinite() && abs(x) <= 100000 && abs(y) <= 100000 && pressure in 0f..1f
}
enum class Tip { INK, PENCIL, SOFT, DRY, BRISTLE, CUSTOM }
enum class Mixing { NORMAL, MULTIPLY, SCREEN, OVERLAY, ADD }
data class Brush(
    val name: String = "Studio ink", val tip: Tip = Tip.INK,
    val size: Float = 14f, val opacity: Float = 1f, val spacing: Float = .12f,
    val hardness: Float = .85f, val smoothing: Float = .25f,
    val minSize: Float = .12f, val pressureGamma: Float = 1f,
    val pressureSize: Boolean = true, val pressureOpacity: Boolean = false,
    val scatter: Float = 0f, val rotation: Float = 0f,
    val followDirection: Boolean = false, val texture: Float = .5f,
    val asset: String? = null
) {
    fun validate() {
        require(name.length in 1..120)
        require(size.isFinite() && size in 1f..240f)
        require(opacity.isFinite() && opacity in .01f..1f)
        require(spacing.isFinite() && spacing in .025f..2f)
        require(hardness.isFinite() && hardness in 0f..1f)
        require(smoothing.isFinite() && smoothing in 0f..1f)
        require(minSize.isFinite() && minSize in .01f..1f)
        require(pressureGamma.isFinite() && pressureGamma in .2f..4f)
        require(scatter.isFinite() && scatter in 0f..2f)
        require(rotation.isFinite() && abs(rotation) <= 360f)
        require(texture.isFinite() && texture in 0f..1f)
        require(tip != Tip.CUSTOM || asset != null)
        require(asset == null || ASSET_ID.matches(asset))
    }
    companion object {
        val ASSET_ID = Regex("[a-f0-9]{64}")
        fun presets() = listOf(
            Brush(),
            Brush("Graphite", Tip.PENCIL, 10f, .72f, .10f, smoothing=.15f, pressureOpacity=true, texture=.8f),
            Brush("Soft airbrush", Tip.SOFT, 90f, .12f, .12f, hardness=.12f, minSize=.4f, pressureOpacity=true),
            Brush("Dry paint", Tip.DRY, 48f, .64f, .16f, smoothing=.10f, texture=.7f, followDirection=true),
            Brush("Bristles", Tip.BRISTLE, 36f, .70f, .08f, followDirection=true, texture=.8f),
            Brush("Round paint", Tip.INK, 60f, .45f, .08f, hardness=.7f, minSize=.35f, pressureOpacity=true)
        )
    }
}
sealed class Operation {
    abstract val id: String
    data class Stroke(
        override val id: String = UUID.randomUUID().toString(),
        val brush: Brush, val color: Int, val eraser: Boolean, val alphaLock: Boolean,
        val mirrored: Boolean, val seed: Long, val samples: List<Sample>
    ) : Operation()
    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        val asset: String, val x: Float, val y: Float, val width: Float, val height: Float
    ) : Operation()
}
data class Layer(
    val id: String = UUID.randomUUID().toString(), val name: String = "Layer",
    val visible: Boolean = true, val locked: Boolean = false, val alphaLock: Boolean = false,
    val opacity: Float = 1f, val mixing: Mixing = Mixing.NORMAL,
    val offsetX: Float = 0f, val offsetY: Float = 0f, val scale: Float = 1f,
    val rotation: Float = 0f, val operations: List<Operation> = emptyList()
)
data class Project(
    val id: String = UUID.randomUUID().toString(), val title: String = "Untitled",
    val width: Int = 1600, val height: Int = 1200, val dpi: Int = 300,
    val background: Int = -1, val transparent: Boolean = false,
    val layers: List<Layer> = listOf(Layer(name="Sketch")), val active: Int = 0
) {
    fun validate() {
        require(UUID_PATTERN.matches(id)) { "Invalid project id" }
        require(title.length in 1..200)
        require(width in 64..4096 && height in 64..4096 && width.toLong()*height <= 12_000_000)
        require(dpi in 72..600)
        require(layers.size in 1..32 && active in layers.indices)
        require(layers.map { it.id }.distinct().size == layers.size)
        var count = 0L
        var points = 0L
        val ids = HashSet<String>()
        layers.forEach { l ->
            require(UUID_PATTERN.matches(l.id) && l.name.length in 1..120)
            require(l.opacity.isFinite() && l.opacity in 0f..1f)
            require(l.offsetX.isFinite() && abs(l.offsetX) <= 20000f)
            require(l.offsetY.isFinite() && abs(l.offsetY) <= 20000f)
            require(l.scale.isFinite() && l.scale in .1f..5f)
            require(l.rotation.isFinite() && abs(l.rotation) <= 360f)
            count += l.operations.size
            l.operations.forEach { op ->
                require(UUID_PATTERN.matches(op.id) && ids.add(op.id)) { "Duplicate operation" }
                when (op) {
                    is Operation.Stroke -> {
                        op.brush.validate(); require(op.samples.isNotEmpty())
                        points += op.samples.size
                        require(op.samples.all { it.valid() })
                    }
                    is Operation.Image -> {
                        require(Brush.ASSET_ID.matches(op.asset))
                        require(op.x.isFinite() && op.y.isFinite() && abs(op.x) <= 20000 && abs(op.y) <= 20000)
                        require(op.width.isFinite() && op.height.isFinite() && op.width in 1f..20000f && op.height in 1f..20000f)
                    }
                }
            }
        }
        require(count <= 20_000 && points <= 600_000) { "Project operation limit reached" }
    }
    fun assets(): Set<String> = layers.flatMap { it.operations }.mapNotNull {
        when(it) { is Operation.Image -> it.asset; is Operation.Stroke -> it.brush.asset }
    }.toSet()
    fun estimatedModelBytes(): Long = layers.sumOf { l -> l.operations.sumOf { op ->
        when(op) { is Operation.Stroke -> 256L + op.samples.size * 24L; is Operation.Image -> 192L }
    }}
    companion object { val UUID_PATTERN = Regex("[a-fA-F0-9-]{36}") }
}

/** Snapshots share immutable stroke lists; history never retains bitmap copies. */
class ProjectHistory(initial: Project, private val maxEntries: Int = 70, private val maxCost: Long = 16L*1024*1024) {
    var current: Project = initial; private set
    private data class Change(val before: Project, val after: Project, val label: String, val cost: Long)
    private val undo = ArrayDeque<Change>()
    private val redo = ArrayDeque<Change>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    val undoLabel get() = undo.lastOrNull()?.label ?: ""
    fun commit(next: Project, label: String) {
        if (next == current) return
        val cost = abs(next.estimatedModelBytes() - current.estimatedModelBytes()) + 1024
        undo.add(Change(current, next, label, cost)); current = next; redo.clear()
        while (undo.size > maxEntries || (undo.size > 1 && undo.sumOf { it.cost } > maxCost)) undo.removeFirst()
    }
    fun select(index: Int) { require(index in current.layers.indices); current = current.copy(active=index) }
    fun undo(): Boolean { val c=undo.removeLastOrNull() ?: return false; redo.add(c); current=c.before; return true }
    fun redo(): Boolean { val c=redo.removeLastOrNull() ?: return false; undo.add(c); current=c.after; return true }
    fun reset(project: Project) { current=project; undo.clear(); redo.clear() }
}
