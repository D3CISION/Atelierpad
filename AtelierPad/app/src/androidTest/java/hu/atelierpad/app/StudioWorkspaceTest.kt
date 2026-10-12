package hu.atelierpad.app

import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hu.atelierpad.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual SDK/device tests, not executed by the dependency-free JVM suite. */
@RunWith(AndroidJUnit4::class)
class StudioWorkspaceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun touch(view: View, action: Int, x: Float, y: Float, tool: Int = MotionEvent.TOOL_TYPE_FINGER) {
        val time = SystemClock.uptimeMillis()
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = tool }
        val coordinates = MotionEvent.PointerCoords().apply { this.x=x; this.y=y; pressure=.6f; size=.1f }
        val event = MotionEvent.obtain(time,time+16,action,1,arrayOf(properties),arrayOf(coordinates),0,0,1f,1f,0,0,
            if(tool == MotionEvent.TOOL_TYPE_STYLUS) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN,0)
        try { view.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    private fun slider(): VerticalSliderView = VerticalSliderView(context, "Brush size").also {
        FrameLayout(context).addView(it); it.layout(0,0,context.dp(52),context.dp(200))
    }
    @Test fun fingerCanDragVerticalSliderUpwards() {
        instrumentation.runOnMainSync {
            val slider=slider();var changed=0;slider.changed={changed++}
            touch(slider,MotionEvent.ACTION_DOWN,context.dp(26).toFloat(),context.dp(180).toFloat())
            touch(slider,MotionEvent.ACTION_MOVE,context.dp(26).toFloat(),context.dp(30).toFloat())
            touch(slider,MotionEvent.ACTION_UP,context.dp(26).toFloat(),context.dp(30).toFloat())
            assertTrue(slider.progress>850);assertTrue(changed>0)
        }
    }
    @Test fun stylusUsesTheSameVerticalControl() {
        instrumentation.runOnMainSync {
            val slider=slider()
            touch(slider,MotionEvent.ACTION_DOWN,context.dp(26).toFloat(),context.dp(20).toFloat(),MotionEvent.TOOL_TYPE_STYLUS)
            touch(slider,MotionEvent.ACTION_UP,context.dp(26).toFloat(),context.dp(20).toFloat(),MotionEvent.TOOL_TYPE_STYLUS)
            assertTrue(slider.progress>950)
        }
    }
    @Test fun cancelRestoresSliderAndEndsTracking() {
        instrumentation.runOnMainSync {
            val slider=slider();slider.progress=450;var tracking=false;slider.tracking={tracking=it}
            touch(slider,MotionEvent.ACTION_DOWN,context.dp(26).toFloat(),context.dp(20).toFloat())
            assertTrue(tracking)
            touch(slider,MotionEvent.ACTION_CANCEL,context.dp(26).toFloat(),context.dp(20).toFloat())
            assertFalse(tracking);assertEquals(450,slider.progress)
        }
    }
    @Test fun verticalControlSupportsExactValueAndAccessibleRange() {
        instrumentation.runOnMainSync {
            val slider=slider();var requested=false;slider.exactValue={requested=true};slider.performLongClick();assertTrue(requested)
            val args=Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,750f) }
            assertTrue(slider.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,args))
            assertEquals(750,slider.progress)
            slider.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,null);assertEquals(740,slider.progress)
        }
    }
    @Test fun invalidAccessibleValueIsRejected() {
        instrumentation.runOnMainSync {
            val slider=slider();slider.progress=123
            val args=Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,Float.NaN) }
            assertFalse(slider.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,args))
            assertEquals(123,slider.progress)
        }
    }
    @Test fun ringGestureDoesNotOverwriteSaturationOrBrightness() {
        instrumentation.runOnMainSync {
            val wheel=ColorWheelView(context,Color.HSVToColor(floatArrayOf(300f,.5f,.5f)),{},{})
            val side=context.dp(320);FrameLayout(context).addView(wheel);wheel.layout(0,0,side,side)
            val center=side/2f;val radius=(center-context.dp(12))*.905f
            touch(wheel,MotionEvent.ACTION_DOWN,center,center-radius)
            touch(wheel,MotionEvent.ACTION_MOVE,center-radius,center)
            touch(wheel,MotionEvent.ACTION_UP,center-radius,center)
            val hsv=FloatArray(3);Color.colorToHSV(wheel.selectedColor,hsv)
            assertEquals(150f,hsv[0],1f);assertEquals(.5f,hsv[1],.02f);assertEquals(.5f,hsv[2],.02f)
        }
    }
    @Test fun discCanSelectPureHueWhiteAndBlack() {
        instrumentation.runOnMainSync {
            val wheel=ColorWheelView(context,Color.RED,{},{})
            val side=context.dp(320);FrameLayout(context).addView(wheel);wheel.layout(0,0,side,side)
            val center=side/2f;val radius=(center-context.dp(12))*.73f*254f/256f
            for ((s,v) in listOf(0f to 1f,1f to 1f,1f to 0f)) {
                val point=StudioColorGeometry.svPoint(s,v,radius)
                touch(wheel,MotionEvent.ACTION_DOWN,center+point.first,center+point.second)
                touch(wheel,MotionEvent.ACTION_UP,center+point.first,center+point.second)
                assertEquals(if(v==0f)Color.BLACK else if(s==0f)Color.WHITE else Color.RED,wheel.selectedColor)
            }
        }
    }
    @Test fun brushPreviewIsWhiteOnTransparentDarkTileNotCurrentInk() {
        instrumentation.runOnMainSync {
            val view=BrushPreview(context,AssetStore(context),{Brush(opacity=.05f)})
            view.layout(0,0,400,100)
            val image=Bitmap.createBitmap(400,100,Bitmap.Config.ARGB_8888);view.draw(Canvas(image))
            val pixels=IntArray(40000);image.getPixels(pixels,0,400,0,0,400,100)
            assertEquals(0,Color.alpha(image.getPixel(0,0)))
            val visible=pixels.filter{Color.alpha(it)>250};assertTrue(visible.isNotEmpty())
            assertTrue(visible.all{Color.red(it)==255 && Color.green(it)==255 && Color.blue(it)==255});image.recycle()
        }
    }
    @Test fun effectRespectsSelectionAndLeavesSourceUntouched() {
        val source=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);source.eraseColor(Color.RED)
        val selection=Selection.rectangle(0f,0f,32f,64f)
        val result=EffectRaster.apply(source,EffectSettings(brightness=1f),selection,64,64)
        try { assertEquals(Color.WHITE,result.getPixel(8,20));assertEquals(Color.RED,result.getPixel(50,20));assertEquals(Color.RED,source.getPixel(8,20)) }
        finally { result.recycle();source.recycle() }
    }
    @Test fun effectRespectsInvertedSelection() {
        val source=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);source.eraseColor(Color.GREEN)
        val result=EffectRaster.apply(source,EffectSettings(brightness=-1f),Selection.rectangle(0f,0f,32f,64f).copy(inverted=true),64,64)
        try { assertEquals(Color.GREEN,result.getPixel(10,20));assertEquals(Color.BLACK,result.getPixel(50,20)) }
        finally { result.recycle();source.recycle() }
    }
    @Test fun previewSelectionUsesDocumentCoordinatesAfterDownscaling() {
        val source=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);source.eraseColor(Color.BLUE)
        val result=EffectRaster.apply(source,EffectSettings(brightness=1f),Selection.rectangle(0f,0f,128f,256f),256,256)
        try { assertEquals(Color.WHITE,result.getPixel(10,20));assertEquals(Color.BLUE,result.getPixel(50,20)) }
        finally { result.recycle();source.recycle() }
    }
    private fun views(view: View): List<View> = listOf(view) + if(view is ViewGroup) (0 until view.childCount).flatMap{views(view.getChildAt(it))} else emptyList()
    private fun withEditor(block: (MainActivity)->Unit) {
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        try { instrumentation.runOnMainSync {
            MainActivity::class.java.getDeclaredMethod("openProject",Project::class.java).apply{isAccessible=true}.invoke(activity,Project(width=128,height=128))
            block(activity)
        } } finally { instrumentation.runOnMainSync{activity.finish()} }
    }
    private fun panel(activity: MainActivity, name: String) { MainActivity::class.java.getDeclaredMethod("showPanel",String::class.java).apply{isAccessible=true}.invoke(activity,name) }
    @Test fun editorRailOrderIsSizePickerOpacityUndoRedo() { withEditor { activity ->
        val nodes=views(activity.window.decorView);val size=nodes.filterIsInstance<VerticalSliderView>().single{it.contentDescription=="Brush size"}
        val rail=size.parent as ViewGroup
        val names=(0 until rail.childCount).map{rail.getChildAt(it).contentDescription?.toString()}
        assertTrue(names.indexOf("Brush size")<names.indexOf("Eyedropper"))
        assertTrue(names.indexOf("Eyedropper")<names.indexOf("Stroke opacity"))
        assertTrue(names.indexOf("Stroke opacity")<names.indexOf("Undo"));assertTrue(names.indexOf("Undo")<names.indexOf("Redo"))
    } }
    @Test fun topLeftContainsHomeToolsEffectsInOrder() { withEditor { activity ->
        val tools=views(activity.window.decorView).single{it.contentDescription=="Tools"};val top=tools.parent as ViewGroup
        val names=(0 until top.childCount).map{top.getChildAt(it).contentDescription?.toString()}
        assertTrue(names.indexOf("Home / projects")<names.indexOf("Tools"));assertTrue(names.indexOf("Tools")<names.indexOf("Effects"))
    } }
    @Test fun colorPopoverHasNoScrollOrEyedropper() { withEditor { activity ->
        panel(activity,"color");val wheel=views(activity.window.decorView).single{it is ColorWheelView}
        var parent=wheel.parent
        while(parent is ViewGroup) { assertFalse(parent is ScrollView); parent=parent.parent }
        val body=wheel.parent as ViewGroup
        assertTrue(views(body).none{it.contentDescription?.toString()=="Eyedropper"})
    } }
    @Test fun selectionPopoverIsIconOnlyAndHasNoSelectAllOrClear() { withEditor { activity ->
        panel(activity,"select");val invert=views(activity.window.decorView).single{it.contentDescription=="Invert selection"}
        val body=invert.parent.parent as ViewGroup
        assertTrue(views(body).none{it is TextView})
        assertTrue(views(body).none{it.contentDescription?.toString()?.contains("Clear")==true})
        assertTrue(views(body).none{it.contentDescription=="Select all"})
    } }
    @Test fun brushLibraryHasNoSizeOrOpacitySliders() { withEditor { activity ->
        panel(activity,"brush");val preview=views(activity.window.decorView).filterIsInstance<BrushPreview>().first()
        val body=preview.parent as ViewGroup
        assertTrue(views(body).none{it is SeekBar})
    } }
    @Test fun eraserLibraryUsesTheSameWhitePreviewWithoutDuplicateControls() { withEditor { activity ->
        val eraser=views(activity.window.decorView).single{it.contentDescription=="Eraser"}
        eraser.performClick();eraser.performClick()
        val nodes=views(activity.window.decorView)
        assertTrue(nodes.filterIsInstance<TextView>().any{it.text.toString()=="Eraser"})
        val preview=nodes.filterIsInstance<BrushPreview>().first()
        assertTrue(views(preview.parent as ViewGroup).none{it is SeekBar})
    } }
    @Test fun newToolbarIconsInflateFromVectorResources() {
        for(icon in intArrayOf(R.drawable.ic_tools,R.drawable.ic_effects,R.drawable.ic_invert,R.drawable.ic_deselect,R.drawable.ic_picker_switch,R.drawable.ic_blur,R.drawable.ic_tone,R.drawable.ic_color))assertNotNull(context.getDrawable(icon))
    }
}
