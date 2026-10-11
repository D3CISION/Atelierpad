package hu.atelierpad.app

import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hu.atelierpad.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android input tests. These must be run on a device/emulator, not the JVM. */
@RunWith(AndroidJUnit4::class)
class TouchControlsTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun event(time:Long,action:Int,x:Float,y:Float,tool:Int=MotionEvent.TOOL_TYPE_FINGER):MotionEvent {
        val props=MotionEvent.PointerProperties().apply{id=0;toolType=tool}
        val coords=MotionEvent.PointerCoords().apply{this.x=x;this.y=y;pressure=.5f;size=.1f}
        return MotionEvent.obtain(time,time+16,action,1,arrayOf(props),arrayOf(coords),0,0,1f,1f,0,0,
            if(tool==MotionEvent.TOOL_TYPE_FINGER)InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_STYLUS,0)
    }
    @Test fun fingerDragWinsOverHorizontalScroll() {
        instrumentation.runOnMainSync {
            val scroll=HorizontalScrollView(context)
            val row=LinearLayout(context)
            val seek=TouchSeekBar(context).apply {max=1000;progress=0}
            row.addView(seek,LinearLayout.LayoutParams(300,80))
            row.addView(View(context),LinearLayout.LayoutParams(500,80))
            scroll.addView(row)
            scroll.measure(View.MeasureSpec.makeMeasureSpec(340,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY))
            scroll.layout(0,0,340,100)
            val t=SystemClock.uptimeMillis()
            for((action,x) in listOf(MotionEvent.ACTION_DOWN to 30f,MotionEvent.ACTION_MOVE to 170f,MotionEvent.ACTION_MOVE to 270f,MotionEvent.ACTION_UP to 270f)) {
                val e=event(t,action,x,40f);scroll.dispatchTouchEvent(e);e.recycle()
            }
            assertTrue("Finger must update the slider",seek.progress>700)
            assertEquals("Outer quick bar must not steal the drag",0,scroll.scrollX)
        }
    }
    @Test fun fingerSliderAlsoWorksInsideVerticalInspector() {
        instrumentation.runOnMainSync {
            val scroll=ScrollView(context)
            val box=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL}
            val seek=TouchSeekBar(context).apply {max=1000}
            box.addView(seek,LinearLayout.LayoutParams(300,80));box.addView(View(context),LinearLayout.LayoutParams(300,1000))
            scroll.addView(box)
            scroll.measure(View.MeasureSpec.makeMeasureSpec(340,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY))
            scroll.layout(0,0,340,300)
            val t=SystemClock.uptimeMillis()
            for((action,x) in listOf(MotionEvent.ACTION_DOWN to 20f,MotionEvent.ACTION_MOVE to 150f,MotionEvent.ACTION_MOVE to 270f,MotionEvent.ACTION_UP to 270f)) {
                val e=event(t,action,x,40f);scroll.dispatchTouchEvent(e);e.recycle()
            }
            assertTrue(seek.progress>700);assertEquals(0,scroll.scrollY)
        }
    }
    @Test fun stylusCanAdjustTheSameSlider() {
        instrumentation.runOnMainSync {
            val seek=TouchSeekBar(context).apply{max=1000}
            val parent=FrameLayout(context);parent.addView(seek);seek.layout(0,0,300,80)
            val t=SystemClock.uptimeMillis()
            for((action,x) in listOf(MotionEvent.ACTION_DOWN to 20f,MotionEvent.ACTION_MOVE to 250f,MotionEvent.ACTION_UP to 250f)) {
                val e=event(t,action,x,40f,MotionEvent.TOOL_TYPE_STYLUS);seek.onTouchEvent(e);e.recycle()
            }
            assertTrue(seek.progress>700)
        }
    }
    @Test fun cancelledSliderCanBeDraggedAgain() {
        instrumentation.runOnMainSync {
            val seek=TouchSeekBar(context).apply {max=1000}
            val parent=FrameLayout(context);parent.addView(seek);seek.layout(0,0,300,80)
            val t=SystemClock.uptimeMillis()
            for((action,x) in listOf(MotionEvent.ACTION_DOWN to 20f,MotionEvent.ACTION_MOVE to 130f,MotionEvent.ACTION_CANCEL to 130f,
                MotionEvent.ACTION_DOWN to 30f,MotionEvent.ACTION_MOVE to 270f,MotionEvent.ACTION_UP to 270f)) {
                val e=event(t,action,x,40f);seek.onTouchEvent(e);e.recycle()
            }
            assertTrue(seek.progress>700)
        }
    }
    @Test fun selectionIsCapturedInTheCommittedStroke() {
        instrumentation.runOnMainSync {
            var project=Project(width=128,height=128);var commits=0
            val view=DrawingView(context,AssetStore(context),{project},{p,_->project=p;commits++},{},{},{_,_->},{},{})
            val parent=FrameLayout(context);parent.addView(view);view.layout(0,0,400,400)
            val selection=Selection.rectangle(0f,0f,64f,128f);view.setSelection(selection)
            val t=SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val e=event(t,action,160f,160f,MotionEvent.TOOL_TYPE_STYLUS);view.onTouchEvent(e);e.recycle()
            }
            assertEquals(1,commits)
            assertEquals(selection,(project.layers[0].operations[0] as Operation.Stroke).selection)
            // Finger adjustment on UI must not require enabling finger drawing on the canvas.
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val e=event(t,action,200f,200f);view.onTouchEvent(e);e.recycle()
            }
            assertEquals(1,commits);view.release()
        }
    }
    @Test fun newlyAddedVectorIconsInflate() {
        intArrayOf(R.drawable.ic_select,R.drawable.ic_rectangle,R.drawable.ic_ellipse,
            R.drawable.ic_copy,R.drawable.ic_cut,R.drawable.ic_trash).forEach {assertNotNull(context.getDrawable(it))}
    }
}
