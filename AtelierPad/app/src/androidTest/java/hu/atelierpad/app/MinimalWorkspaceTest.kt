package hu.atelierpad.app

import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hu.atelierpad.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires Android instrumentation. These tests are not JVM simulations. */
@RunWith(AndroidJUnit4::class)
class MinimalWorkspaceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(time, time + 16, action, x, y, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }
    @Test fun fingerSelectsWheelWithoutCommittingEveryMove() {
        instrumentation.runOnMainSync {
            var committed = 0
            val wheel = ColorWheelView(context, Color.RED, {}, { committed++ })
            FrameLayout(context).addView(wheel); wheel.layout(0, 0, 400, 400)
            touch(wheel, MotionEvent.ACTION_DOWN, 200f, 200f)
            val hsv = FloatArray(3); Color.colorToHSV(wheel.selectedColor, hsv)
            assertEquals(.5f, hsv[1], .01f); assertEquals(.5f, hsv[2], .01f)
            touch(wheel, MotionEvent.ACTION_MOVE, 230f, 200f)
            assertEquals(0, committed)
            touch(wheel, MotionEvent.ACTION_UP, 230f, 200f)
            assertEquals(1, committed)
        }
    }
    @Test fun cancelledColorGestureRestoresOriginalChoice() {
        instrumentation.runOnMainSync {
            var committed = 0
            val wheel = ColorWheelView(context, Color.BLUE, {}, { committed++ })
            FrameLayout(context).addView(wheel); wheel.layout(0, 0, 400, 400)
            touch(wheel, MotionEvent.ACTION_DOWN, 200f, 200f)
            touch(wheel, MotionEvent.ACTION_CANCEL, 200f, 200f)
            assertEquals(Color.BLUE, wheel.selectedColor); assertEquals(0, committed)
        }
    }
    @Test fun brightnessCoversWhiteToBlackWithoutHexInput() {
        instrumentation.runOnMainSync {
            val wheel = ColorWheelView(context, Color.WHITE, {}, {})
            wheel.setBrightness(0f); assertEquals(Color.BLACK, wheel.selectedColor)
            wheel.setBrightness(1f); assertEquals(Color.WHITE, wheel.selectedColor)
        }
    }
    @Test fun currentColorSwatchIsAClickable48dpTarget() {
        instrumentation.runOnMainSync {
            var opened = false
            val view = ColorSwatchView(context, Color.RED) { opened = true }
            assertTrue(view.minimumHeight >= context.dp(48)); assertTrue(view.minimumWidth >= context.dp(48))
            view.performClick(); assertTrue(opened)
        }
    }
    @Test fun galleryReadsNameAndDateWithoutOpeningTheEditor() {
        val files = ProjectFiles(context, AssetStore(context)); val p = Project(title = "Gallery test", width = 64, height = 64)
        try {
            files.saveLocal(p)
            val entry = files.gallery.entries().single { it.id == p.id }
            assertEquals("Gallery test", entry.title); assertEquals(64, entry.width)
            assertTrue(entry.editedAt > 0); assertNull(entry.problem)
        } finally { files.path(p).delete(); files.executor.shutdown() }
    }
    @Test fun openingAndResavingDoesNotChangeLastEditedDate() {
        val files = ProjectFiles(context, AssetStore(context)); val p = Project(width = 64, height = 64)
        try {
            files.saveLocal(p); val before = files.gallery.entries().single { it.id == p.id }.editedAt
            SystemClock.sleep(15)
            val loaded = files.readLocal(files.path(p)); files.saveLocal(loaded)
            assertEquals(before, files.gallery.entries().single { it.id == p.id }.editedAt)
        } finally { files.path(p).delete(); files.executor.shutdown() }
    }
    @Test fun changingOnlyActiveLayerDoesNotChangeEditTimestamp() {
        val files = ProjectFiles(context, AssetStore(context))
        val p = Project(width = 64, height = 64, layers = listOf(Layer(), Layer()))
        try {
            files.saveLocal(p); val before = files.gallery.entries().single { it.id == p.id }.editedAt
            SystemClock.sleep(15); files.saveLocal(p.copy(active = 1))
            assertEquals(before, files.gallery.entries().single { it.id == p.id }.editedAt)
        } finally { files.path(p).delete(); files.executor.shutdown() }
    }
    @Test fun realEditUpdatesTheHomeMetadataAndThumbnailIsBounded() {
        val files = ProjectFiles(context, AssetStore(context)); val p = Project(width = 128, height = 64)
        try {
            files.saveLocal(p); val before = files.gallery.entries().single { it.id == p.id }.editedAt
            SystemClock.sleep(15); files.saveLocal(p.copy(title = "Renamed"))
            val entry = files.gallery.entries().single { it.id == p.id }
            assertEquals("Renamed", entry.title); assertTrue(entry.editedAt > before)
            val preview = files.gallery.thumbnail(entry)
            assertNotNull(preview); assertTrue(preview!!.width <= 448 && preview.height <= 448); preview.recycle()
        } finally { files.path(p).delete(); files.executor.shutdown() }
    }
    @Test fun evictedSnapshotDoesNotRefreshEditTimestamp() {
        val files=ProjectFiles(context,AssetStore(context))
        val projects=List(5){Project(width=64,height=64,title="Cache test $it")}
        try {
            files.saveLocal(projects[0]);val before=files.gallery.entries().single{it.id==projects[0].id}.editedAt
            projects.drop(1).forEach{files.saveLocal(it)}
            SystemClock.sleep(15);files.saveLocal(projects[0])
            assertEquals(before,files.gallery.entries().single{it.id==projects[0].id}.editedAt)
        } finally {projects.forEach{files.path(it).delete()};files.executor.shutdown()}
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun launch(): MainActivity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    @Test fun launchShowsHomeNotTheLastCanvas() {
        val activity = launch()
        try {
            instrumentation.runOnMainSync {
                val nodes = views(activity.window.decorView)
                assertTrue(nodes.filterIsInstance<TextView>().any { it.text.toString() == "Projects" })
                assertTrue(nodes.filterIsInstance<TextView>().any { it.text.toString().contains("build 5") })
                assertTrue(nodes.none { it is DrawingView })
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun editorToolbarOrderAndColorPopoverMatchTheMinimalLayout() {
        val activity = launch()
        try {
            instrumentation.runOnMainSync {
                val open = MainActivity::class.java.getDeclaredMethod("openProject", Project::class.java).apply { isAccessible = true }
                open.invoke(activity, Project(width = 128, height = 128))
                val nodes = views(activity.window.decorView)
                val brush = nodes.single { it.contentDescription == "Brush" }
                val top = brush.parent as ViewGroup
                val names = (0 until top.childCount).map { top.getChildAt(it).contentDescription?.toString() }
                assertTrue(names.indexOf("Brush") < names.indexOf("Selection"))
                assertTrue(names.indexOf("Selection") < names.indexOf("Eraser"))
                assertTrue(names.indexOf("Eraser") < names.indexOf("Layers"))
                assertTrue(top.getChildAt(top.childCount - 1) is ColorSwatchView)
                val texts = nodes.filterIsInstance<TextView>().map { it.text.toString() }
                assertFalse(texts.any { it.contains("ILLUSTRATION STUDIO") || it.contains("build 5") })
                val show = MainActivity::class.java.getDeclaredMethod("showPanel", String::class.java).apply { isAccessible = true }
                show.invoke(activity, "color")
                val popup = views(activity.window.decorView)
                assertTrue(popup.any { it is ColorWheelView })
                assertFalse(popup.filterIsInstance<TextView>().any { it.text.toString().contains("HEX") || it.text.toString().contains("Studio palette") })
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun layerAccessibleMoveUsesTheSameIdentitySafeReorder() {
        instrumentation.runOnMainSync {
            var project = Project(width = 64, height = 64, layers = listOf(Layer(name = "Base"), Layer(name = "Ink")), active = 1)
            val activeId = project.layers[1].id
            val renderer = RasterRenderer(AssetStore(context))
            try {
                val stack = LayerStackView(context, { project }, renderer, {}, {}, {}, { project = it })
                val base = views(stack).single { it.contentDescription?.toString()?.startsWith("Base. Hold") == true }
                assertTrue(base.performAccessibilityAction(R.id.layer_move_up, null))
                assertEquals("Base", project.layers.last().name)
                assertEquals(activeId, project.layers[project.active].id)
            } finally { renderer.close() }
        }
    }
}
