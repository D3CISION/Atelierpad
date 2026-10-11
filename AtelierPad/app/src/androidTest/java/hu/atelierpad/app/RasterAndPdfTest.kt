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
    @Test fun strokeOpacityDoesNotBuildUpAtSelfCrossing() {
        val b=Brush(size=16f,opacity=.5f,hardness=1f,pressureSize=false,smoothing=0f)
        val op=Operation.Stroke(brush=b,color=Color.BLUE,eraser=false,alphaLock=false,mirrored=false,seed=3,
            samples=listOf(Sample(10f,32f),Sample(54f,32f),Sample(10f,32f)))
        val p=Project(width=64,height=64,transparent=true,layers=listOf(Layer(operations=listOf(op))))
        RasterRenderer(AssetStore(context)).use {r->
            val image=r.flatten(p);assertTrue(Color.alpha(image.getPixel(32,32)) in 126..129);image.recycle()
            val twice=p.copy(layers=listOf(p.layers[0].copy(operations=listOf(op,op.copy(id=java.util.UUID.randomUUID().toString())))))
            val image2=r.flatten(twice);assertTrue(Color.alpha(image2.getPixel(32,32)) in 190..194);image2.recycle()
        }
    }
    @Test fun clippingMaskPreservesTransparencyOutsideBase() {
        val base=Layer(operations=listOf(dot(Color.RED)))
        val cover=dot(Color.BLUE).copy(brush=Brush(size=100f,hardness=1f,pressureSize=false,smoothing=0f))
        val clipped=Layer(clipped=true,operations=listOf(cover))
        val p=Project(width=64,height=64,transparent=true,layers=listOf(base,clipped))
        RasterRenderer(AssetStore(context)).use {r->
            val image=r.flatten(p);assertEquals(Color.BLUE,image.getPixel(32,32));assertEquals(0,Color.alpha(image.getPixel(3,3)));image.recycle()
            val hidden=r.flatten(p.copy(layers=listOf(base.copy(visible=false),clipped)))
            assertEquals(0,Color.alpha(hidden.getPixel(32,32)));hidden.recycle()
        }
    }
    @Test fun clippingFollowsBaseTransform() {
        val base=Layer(offsetX=30f,operations=listOf(dot(Color.RED)))
        val cover=dot(Color.BLUE).copy(brush=Brush(size=160f,hardness=1f,pressureSize=false,smoothing=0f),samples=listOf(Sample(64f,32f)))
        val p=Project(width=128,height=64,transparent=true,layers=listOf(base,Layer(clipped=true,operations=listOf(cover))))
        RasterRenderer(AssetStore(context)).use {r->
            val image=r.flatten(p);assertEquals(Color.BLUE,image.getPixel(62,32));assertEquals(0,Color.alpha(image.getPixel(20,32)));image.recycle()
        }
    }
    @Test fun liveStrokeMatchesCommittedRasterAndCancelIsClean() {
        val p=Project(width=64,height=64,transparent=true)
        val op=dot(Color.BLUE).copy(brush=Brush(size=12f,opacity=.4f,pressureSize=false),samples=listOf(Sample(5f,32f),Sample(58f,32f)))
        RasterRenderer(AssetStore(context)).use {r->
            r.begin(p,p.layers[0],op)
            val engine=BrushEngine(op.brush,op.seed) {r.liveStamp(p,op,it)}
            op.samples.forEach(engine::add);engine.finish(op.samples.last())
            val live=r.flatten(p)
            val layer=p.layers[0].copy(operations=listOf(op));r.end(layer)
            val committed=r.flatten(p.copy(layers=listOf(layer)))
            assertEquals(live.getPixel(32,32),committed.getPixel(32,32));live.recycle();committed.recycle()
            r.begin(p.copy(layers=listOf(layer)),layer,op)
            r.liveStamp(p,op,Stamp(32f,5f,15f,1f,0f));r.end(null)
            val cancelled=r.flatten(p.copy(layers=listOf(layer)))
            assertEquals(0,Color.alpha(cancelled.getPixel(32,5)));cancelled.recycle()
        }
    }
    @Test fun partialEraserAndAlphaLockRespectExistingPixels() {
        val base=dot(Color.RED)
        val erase=dot(Color.BLACK,true).copy(brush=Brush(size=40f,opacity=.5f,hardness=1f,pressureSize=false))
        val locked=dot(Color.BLUE).copy(alphaLock=true,brush=Brush(size=100f,hardness=1f,pressureSize=false))
        val p=Project(width=64,height=64,transparent=true,layers=listOf(Layer(operations=listOf(base,erase))))
        RasterRenderer(AssetStore(context)).use {r->
            val image=r.flatten(p);assertTrue(Color.alpha(image.getPixel(32,32)) in 126..129);image.recycle()
            val colored=r.flatten(p.copy(layers=listOf(p.layers[0].copy(operations=listOf(base,locked)))))
            assertEquals(Color.BLUE,colored.getPixel(32,32));assertEquals(0,Color.alpha(colored.getPixel(3,3)));colored.recycle()
        }
    }
    @Test fun everyToolbarVectorInflates() {
        val icons=intArrayOf(R.drawable.ic_brush,R.drawable.ic_eraser,R.drawable.ic_picker,R.drawable.ic_fill,
            R.drawable.ic_move,R.drawable.ic_undo,R.drawable.ic_redo,R.drawable.ic_fit,R.drawable.ic_panel,
            R.drawable.ic_gallery,R.drawable.ic_import,R.drawable.ic_export,R.drawable.ic_help,R.drawable.ic_palette,
            R.drawable.ic_layers,R.drawable.ic_eye,R.drawable.ic_eye_off,R.drawable.ic_clip,R.drawable.ic_size,R.drawable.ic_opacity)
        icons.forEach {assertNotNull(context.getDrawable(it))}
    }

}
