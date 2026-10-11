package hu.atelierpad.app

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hu.atelierpad.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionRasterTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun line(selection:Selection?)=Operation.Stroke(
        brush=Brush(size=12f,pressureSize=false,smoothing=0f),color=Color.BLUE,
        eraser=false,alphaLock=false,mirrored=false,seed=1,
        samples=listOf(Sample(4f,32f),Sample(60f,32f)),selection=selection)
    @Test fun paintIsRestrictedToSelection() {
        val selected=Selection.rectangle(0f,0f,32f,64f)
        val p=Project(width=64,height=64,transparent=true,layers=listOf(Layer(operations=listOf(line(selected)))))
        RasterRenderer(AssetStore(context)).use {r->
            val b=r.flatten(p)
            assertEquals(Color.BLUE,b.getPixel(16,32));assertEquals(0,Color.alpha(b.getPixel(48,32)));b.recycle()
        }
    }
    @Test fun invertedSelectionPaintsOutsideBoundary() {
        val selected=Selection.rectangle(0f,0f,32f,64f).copy(inverted=true)
        val p=Project(width=64,height=64,transparent=true,layers=listOf(Layer(operations=listOf(line(selected)))))
        RasterRenderer(AssetStore(context)).use {r->
            val b=r.flatten(p)
            assertEquals(0,Color.alpha(b.getPixel(16,32)));assertEquals(Color.BLUE,b.getPixel(48,32));b.recycle()
        }
    }
    @Test fun clearOnlyRemovesSelectedPixels() {
        val clear=Operation.Clear(selection=Selection.rectangle(0f,0f,32f,64f))
        val p=Project(width=64,height=64,transparent=true,layers=listOf(Layer(operations=listOf(line(null),clear))))
        RasterRenderer(AssetStore(context)).use {r->
            val b=r.flatten(p)
            assertEquals(0,Color.alpha(b.getPixel(16,32)));assertEquals(Color.BLUE,b.getPixel(48,32));b.recycle()
        }
    }
    @Test fun selectedLiveStrokeMatchesFullReplay() {
        val p=Project(width=64,height=64,transparent=true)
        val op=line(Selection.ellipse(8f,8f,50f,54f))
        val layer=p.layers[0].copy(operations=listOf(op))
        RasterRenderer(AssetStore(context)).use {r->
            r.begin(p,p.layers[0],op)
            val engine=BrushEngine(op.brush,op.seed){r.liveStamp(p,op,it)}
            op.samples.forEach(engine::add);engine.finish(op.samples.last())
            val live=r.flatten(p);r.end(layer)
            RasterRenderer(AssetStore(context)).use {fresh->
                val replay=fresh.flatten(p.copy(layers=listOf(layer)))
                for(y in 0 until 64)for(x in 0 until 64)assertEquals("Pixel $x,$y",live.getPixel(x,y),replay.getPixel(x,y))
                replay.recycle()
            }
            live.recycle()
        }
    }
    @Test fun selectionMaskMovesWithTransformedLayer() {
        val selected=Selection.rectangle(0f,0f,32f,64f)
        val p=Project(width=128,height=64,transparent=true,layers=listOf(Layer(offsetX=40f,operations=listOf(line(selected)))))
        RasterRenderer(AssetStore(context)).use {r->
            val b=r.flatten(p)
            assertEquals(Color.BLUE,b.getPixel(56,32));assertEquals(0,Color.alpha(b.getPixel(16,32)));assertEquals(0,Color.alpha(b.getPixel(88,32)));b.recycle()
        }
    }
}
