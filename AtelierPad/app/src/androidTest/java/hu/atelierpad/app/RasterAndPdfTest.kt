package hu.atelierpad.app

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hu.atelierpad.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** These tests require Android. They have NOT been executed in the source-generation environment. */
@RunWith(AndroidJUnit4::class)
class RasterAndPdfTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun dot(color:Int,erase:Boolean=false)=Operation.Stroke(brush=Brush(size=28f,hardness=1f,pressureSize=false,smoothing=0f),color=color,eraser=erase,alphaLock=false,mirrored=false,seed=7,samples=listOf(Sample(32f,32f)))
    @Test fun rasterDotAndEraser() {
        val assets=AssetStore(context)
        val p=Project(width=64,height=64,transparent=true,layers=listOf(Layer(operations=listOf(dot(Color.RED)))))
        RasterRenderer(assets).use{renderer->
            val b=renderer.flatten(p);assertEquals(Color.RED,b.getPixel(32,32));assertEquals(0,Color.alpha(b.getPixel(0,0)));b.recycle()
            val erased=p.copy(layers=listOf(p.layers[0].copy(operations=listOf(dot(Color.RED),dot(Color.BLACK,true)))))
            val e=renderer.flatten(erased);assertEquals(0,Color.alpha(e.getPixel(32,32)));e.recycle()
        }
    }
    @Test fun pdfPageAndWhiteMargin() {
        val file=File(context.cacheDir,"test-export.pdf")
        val p=Project(width=1600,height=1200,background=Color.RED)
        FileOutputStream(file).use{ProjectFiles.writePdf(it,p,PdfPaper.A4,AssetStore(context))}
        ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->
            PdfRenderer(fd).use{renderer->
                assertEquals(1,renderer.pageCount)
                renderer.openPage(0).use{page->
                    assertEquals(842,page.width);assertEquals(595,page.height)
                    val b=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                    page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    assertEquals(Color.WHITE,b.getPixel(1,1));assertEquals(Color.RED,b.getPixel(421,297))
                    b.recycle()
                }
            }
        }
    }
    @Test fun stylusCancelNeverCommitsAndFingerIsIgnored() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val p=Project(width=128,height=128);var commits=0
            val view=DrawingView(context,AssetStore(context),{p},{_,_->commits++},{},{},{_,_->},{},{})
            val parent=FrameLayout(context);parent.addView(view);view.layout(0,0,400,400)
            val t=SystemClock.uptimeMillis()
            fun send(action:Int,tool:Int,flags:Int=0) {
                val props=MotionEvent.PointerProperties().apply{id=0;toolType=tool}
                val coords=MotionEvent.PointerCoords().apply{x=200f;y=200f;pressure=.5f;size=.1f}
                val event=MotionEvent.obtain(t,t+1,action,1,arrayOf(props),arrayOf(coords),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,flags)
                view.onTouchEvent(event);event.recycle()
            }
            send(MotionEvent.ACTION_DOWN,MotionEvent.TOOL_TYPE_STYLUS)
            send(MotionEvent.ACTION_CANCEL,MotionEvent.TOOL_TYPE_STYLUS)
            assertEquals(0,commits)
            send(MotionEvent.ACTION_DOWN,MotionEvent.TOOL_TYPE_FINGER)
            send(MotionEvent.ACTION_UP,MotionEvent.TOOL_TYPE_FINGER)
            assertEquals(0,commits)
            send(MotionEvent.ACTION_DOWN,MotionEvent.TOOL_TYPE_STYLUS)
            send(MotionEvent.ACTION_UP,MotionEvent.TOOL_TYPE_STYLUS)
            assertEquals(1,commits)
            view.release()
        }
    }
}
