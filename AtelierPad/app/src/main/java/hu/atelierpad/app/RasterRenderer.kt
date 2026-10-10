package hu.atelierpad.app

import android.graphics.*
import hu.atelierpad.core.*
import kotlin.math.*
import java.util.Random

/** CPU bitmap stamp rasterizer, presented by a hardware-accelerated Android View. */
class RasterRenderer(private val assets:AssetStore) : AutoCloseable {
    private data class Cached(val operations:List<Operation>,val bitmap:Bitmap)
    private val layers=LinkedHashMap<String,Cached>(16,.75f,true)
    private val tips=LinkedHashMap<String,Bitmap>(16,.75f,true)
    private var dimensions=""
    private val layerBudget=(Runtime.getRuntime().maxMemory()/4).coerceAtMost(144L*1024*1024)
    var transientLayerId:String?=null; private set
    private var transientBitmap:Bitmap?=null
    fun layer(p:Project,l:Layer):Bitmap {
        checkDimensions(p)
        if(l.id==transientLayerId) return transientBitmap!!
        val old=layers[l.id]
        if(old!=null && old.operations===l.operations) return old.bitmap
        val b=old?.bitmap ?: Bitmap.createBitmap(p.width,p.height,Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.TRANSPARENT)
        val c=Canvas(b)
        l.operations.forEach { renderOperation(c,p,it) }
        layers[l.id]=Cached(l.operations,b);trim(l.id)
        return b
    }
    private fun checkDimensions(p:Project) {
        val key="${p.id}:${p.width}:${p.height}"
        if(key!=dimensions) {close();dimensions=key}
    }
    private fun trim(keep:String) {
        var bytes=layers.values.sumOf {it.bitmap.allocationByteCount.toLong()}
        val iterator=layers.entries.iterator()
        while(bytes>layerBudget && iterator.hasNext()) {
            val e=iterator.next()
            if(e.key!=keep) {bytes-=e.value.bitmap.allocationByteCount;iterator.remove()}
        }
    }
    fun begin(p:Project,l:Layer) {
        check(transientBitmap==null)
        transientBitmap=layer(p,l).copy(Bitmap.Config.ARGB_8888,true)
        transientLayerId=l.id
    }
    fun liveStamp(p:Project,op:Operation.Stroke,s:Stamp) {
        val b=transientBitmap ?: return
        drawStamp(Canvas(b),op,s)
        if(op.mirrored) drawStamp(Canvas(b),op,s.copy(x=p.width-s.x,angle=-s.angle))
    }
    fun end(committed:Layer?) {
        val bitmap=transientBitmap;val id=transientLayerId
        transientBitmap=null;transientLayerId=null
        if(bitmap!=null && id!=null && committed!=null) {
            layers.remove(id)
            layers[id]=Cached(committed.operations,bitmap);trim(id)
        } // Uncached bitmaps are released by GC after any pending display-list use.
    }
    fun renderOperation(canvas:Canvas,p:Project,op:Operation) {
        when(op) {
            is Operation.Stroke -> {
                val engine=BrushEngine(op.brush,op.seed) { s ->
                    drawStamp(canvas,op,s)
                    if(op.mirrored)drawStamp(canvas,op,s.copy(x=p.width-s.x,angle=-s.angle))
                }
                op.samples.forEach(engine::add);engine.finish(op.samples.last())
            }
            is Operation.Image -> {
                val bitmap=assets.load(op.asset)
                canvas.drawBitmap(bitmap,null,RectF(op.x,op.y,op.x+op.width,op.y+op.height),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                bitmap.recycle()
            }
        }
    }
    private fun tip(brush:Brush):Bitmap {
        val key="${brush.tip}:${brush.hardness}:${brush.texture}:${brush.asset}"
        tips[key]?.let{return it}
        val bitmap=if(brush.tip==Tip.CUSTOM) {
            val raw=assets.load(requireNotNull(brush.asset));val size=256
            val out=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
            val scale=min(size.toFloat()/raw.width,size.toFloat()/raw.height)
            val w=raw.width*scale;val h=raw.height*scale
            Canvas(out).drawBitmap(raw,null,RectF((size-w)/2,(size-h)/2,(size+w)/2,(size+h)/2),Paint(Paint.FILTER_BITMAP_FLAG))
            raw.recycle();out
        } else {
            val n=128;val pixels=IntArray(n*n);val random=Random(7721)
            for(y in 0 until n)for(x in 0 until n) {
                val dx=(x+.5f-n/2)/(n/2);val dy=(y+.5f-n/2)/(n/2);val r=hypot(dx,dy)
                var a=if(r>=1f)0f else if(r<=brush.hardness)1f else ((1f-r)/(1f-brush.hardness).coerceAtLeast(.001f))
                when(brush.tip) {
                    Tip.SOFT -> a=if(r>=1)0f else ((exp(-r*r*4.5)-exp(-4.5))/(1-exp(-4.5))).toFloat()
                    Tip.PENCIL -> a*=if(random.nextFloat()<brush.texture*.28f)0f else .2f+random.nextFloat()*.8f
                    Tip.DRY -> a*=if(random.nextFloat()<brush.texture*.5f)0f else .5f+random.nextFloat()*.5f
                    Tip.BRISTLE -> {a*=if((x%9)<3) .04f else .8f;a*=1-abs(dy)*.7f}
                    else -> {}
                }
                pixels[y*n+x]=((a*255).roundToInt().coerceIn(0,255) shl 24) or 0x00ffffff
            }
            Bitmap.createBitmap(pixels,n,n,Bitmap.Config.ARGB_8888)
        }
        tips[key]=bitmap
        while(tips.size>16) {val it=tips.entries.iterator();it.next();it.remove()}
        return bitmap
    }
    private val stampPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private fun drawStamp(canvas:Canvas,op:Operation.Stroke,s:Stamp) {
        val bitmap=tip(op.brush)
        stampPaint.alpha=(s.alpha*255).roundToInt().coerceIn(0,255)
        stampPaint.colorFilter=PorterDuffColorFilter(op.color,PorterDuff.Mode.SRC_IN)
        stampPaint.xfermode=when {op.eraser->PorterDuffXfermode(PorterDuff.Mode.DST_OUT);op.alphaLock->PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP);else->null}
        canvas.save();canvas.translate(s.x,s.y);canvas.rotate(s.angle)
        val r=s.diameter/2;canvas.drawBitmap(bitmap,null,RectF(-r,-r,r,r),stampPaint);canvas.restore()
    }
    fun matrix(p:Project,l:Layer):Matrix = Matrix().apply {
        setTranslate(-p.width/2f,-p.height/2f);postScale(l.scale,l.scale);postRotate(l.rotation)
        postTranslate(p.width/2f+l.offsetX,p.height/2f+l.offsetY)
    }
    fun compose(canvas:Canvas,p:Project,whiteBackground:Boolean=false) {
        checkDimensions(p)
        canvas.save();canvas.clipRect(0,0,p.width,p.height)
        if(whiteBackground)canvas.drawColor(Color.WHITE)
        if(!p.transparent)canvas.drawColor(p.background)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.layers.forEach { l -> if(l.visible && l.opacity>0f) {
            paint.alpha=(l.opacity*255).roundToInt()
            paint.blendMode=when(l.mixing) {
                Mixing.NORMAL->BlendMode.SRC_OVER;Mixing.MULTIPLY->BlendMode.MULTIPLY
                Mixing.SCREEN->BlendMode.SCREEN;Mixing.OVERLAY->BlendMode.OVERLAY;Mixing.ADD->BlendMode.PLUS
            }
            canvas.drawBitmap(layer(p,l),matrix(p,l),paint)
        }}
        canvas.restore()
    }
    fun flatten(p:Project,whiteBackground:Boolean=false):Bitmap = Bitmap.createBitmap(p.width,p.height,Bitmap.Config.ARGB_8888).also {compose(Canvas(it),p,whiteBackground)}
    override fun close() {
        transientBitmap=null;transientLayerId=null
        // A hardware Canvas may still reference these bitmaps until its frame completes.
        // Drop Java references rather than recycling a live display-list resource.
        layers.clear();tips.clear()
    }
}
