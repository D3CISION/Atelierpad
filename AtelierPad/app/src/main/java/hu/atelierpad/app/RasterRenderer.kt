package hu.atelierpad.app

import android.graphics.*
import hu.atelierpad.core.*
import kotlin.math.*
import java.util.Random

/** CPU raster layers with a separate live stroke surface and hardware-accelerated View composition. */
class RasterRenderer(private val assets:AssetStore) : AutoCloseable {
    private data class Cached(val operations:List<Operation>,val bitmap:Bitmap)
    private data class ClipCache(val source:Layer,val generation:Long,val bitmap:Bitmap)
    private val layers=LinkedHashMap<String,Cached>(16,.75f,true)
    private val tips=LinkedHashMap<String,Bitmap>(16,.75f,true)
    private val clipMasks=LinkedHashMap<String,ClipCache>(4,.75f,true)
    private var dimensions=""
    private val layerBudget=(Runtime.getRuntime().maxMemory()/4).coerceAtMost(144L*1024*1024)
    private val bitmapPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val layerPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { blendMode=BlendMode.DST_IN }
    private var replayBuffer:Bitmap?=null
    var transientLayerId:String?=null; private set
    private var liveOp:Operation.Stroke?=null
    private var liveBase:Bitmap?=null
    private var liveInk:Bitmap?=null
    private var liveCanvas:Canvas?=null
    private var livePainter:DabPainter?=null
    private var liveComposite:Bitmap?=null
    private var liveDirty=true
    private var generation=0L

    fun layer(p:Project,l:Layer):Bitmap {
        checkDimensions(p)
        if(l.id==transientLayerId) return resolveLive()
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
    fun begin(p:Project,l:Layer,op:Operation.Stroke) {
        check(transientLayerId==null)
        val base=layer(p,l)
        liveBase=base;liveOp=op;transientLayerId=l.id
        liveInk=Bitmap.createBitmap(p.width,p.height,Bitmap.Config.ARGB_8888)
        liveCanvas=Canvas(requireNotNull(liveInk)).also {c->op.selection?.let {c.clipPath(it.clipPath(p.width,p.height))}};livePainter=DabPainter(op)
        liveComposite=null;liveDirty=true;generation++
    }
    fun liveStamp(p:Project,op:Operation.Stroke,s:Stamp) {
        val c=liveCanvas ?: return
        val painter=livePainter ?: return
        painter.draw(c,s,false)
        if(op.mirrored)painter.draw(c,s.copy(x=p.width-s.x,angle=-s.angle),true)
        liveDirty=true;generation++
    }
    private fun strokePaint(op:Operation.Stroke)=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha=(op.brush.opacity*255).roundToInt().coerceIn(0,255)
        blendMode=when {op.eraser->BlendMode.DST_OUT;op.alphaLock->BlendMode.SRC_ATOP;else->BlendMode.SRC_OVER}
    }
    private fun drawLocal(canvas:Canvas,p:Project,l:Layer) {
        if(l.id==transientLayerId) {
            canvas.drawBitmap(requireNotNull(liveBase),0f,0f,bitmapPaint)
            canvas.drawBitmap(requireNotNull(liveInk),0f,0f,strokePaint(requireNotNull(liveOp)))
        } else canvas.drawBitmap(layer(p,l),0f,0f,bitmapPaint)
    }
    private fun resolveLive():Bitmap {
        val base=requireNotNull(liveBase)
        val out:Bitmap=liveComposite ?: requireNotNull(Bitmap.createBitmap(base.width,base.height,Bitmap.Config.ARGB_8888)).also {liveComposite=it}
        if(liveDirty) {
            out.eraseColor(Color.TRANSPARENT)
            val c=Canvas(out);c.drawBitmap(base,0f,0f,bitmapPaint)
            c.drawBitmap(requireNotNull(liveInk),0f,0f,strokePaint(requireNotNull(liveOp)))
            liveDirty=false
        }
        return out
    }
    fun end(committed:Layer?) {
        val id=transientLayerId
        if(committed!=null && id!=null) {
            val bitmap=resolveLive()
            layers[id]=Cached(committed.operations,bitmap);trim(id)
        }
        transientLayerId=null;liveOp=null;liveBase=null;liveInk=null;liveCanvas=null
        livePainter=null;liveComposite=null;generation++
    }
    fun renderOperation(canvas:Canvas,p:Project,op:Operation) {
        when(op) {
            is Operation.Stroke -> {
                val selectionSave=canvas.save()
                op.selection?.let {canvas.clipPath(it.clipPath(p.width,p.height))}
                if(op.engineVersion==1) {
                    val engine=BrushEngine(op.brush,op.seed,op.engineVersion) {s->
                        drawLegacyStamp(canvas,op,s)
                        if(op.mirrored)drawLegacyStamp(canvas,op,s.copy(x=p.width-s.x,angle=-s.angle))
                    }
                    op.samples.forEach(engine::add);engine.finish(op.samples.last())
                } else {
                    var buffer=replayBuffer
                    if(buffer==null || buffer.width!=p.width || buffer.height!=p.height) {
                        buffer=Bitmap.createBitmap(p.width,p.height,Bitmap.Config.ARGB_8888);replayBuffer=buffer
                    }
                    val strokeBuffer=requireNotNull(buffer)
                    strokeBuffer.eraseColor(Color.TRANSPARENT)
                    val target=Canvas(strokeBuffer);val painter=DabPainter(op)
                    val engine=BrushEngine(op.brush,op.seed,op.engineVersion) {s->
                        painter.draw(target,s,false)
                        if(op.mirrored)painter.draw(target,s.copy(x=p.width-s.x,angle=-s.angle),true)
                    }
                    op.samples.forEach(engine::add);engine.finish(op.samples.last())
                    canvas.drawBitmap(strokeBuffer,0f,0f,strokePaint(op))
                    // A hardware display list may use it later; do not clear that same bitmap again.
                    if(canvas.isHardwareAccelerated)replayBuffer=null
                }
                canvas.restoreToCount(selectionSave)
            }
            is Operation.Clear -> {
                val save=canvas.save();canvas.clipPath(op.selection.clipPath(p.width,p.height))
                canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);canvas.restoreToCount(save)
            }
            is Operation.Image -> {
                val bitmap=assets.load(op.asset)
                canvas.drawBitmap(bitmap,null,RectF(op.x,op.y,op.x+op.width,op.y+op.height),bitmapPaint)
                if(!canvas.isHardwareAccelerated)bitmap.recycle()
            }
        }
    }
    /** One painter per stroke: no Canvas, color-filter or matrix allocation for every stamp. */
    private inner class DabPainter(op:Operation.Stroke) {
        private val solid=op.brush.isSolidInk()
        private val bitmap=if(solid)null else tip(op.brush)
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color=op.color or 0xff000000.toInt()
            if(!solid)colorFilter=PorterDuffColorFilter(color,PorterDuff.Mode.SRC_IN)
            style=Paint.Style.FILL
        }
        private val bounds=RectF()
        private val path=Path()
        private var last:Stamp?=null
        private var lastMirror:Stamp?=null
        fun draw(canvas:Canvas,s:Stamp,mirror:Boolean) {
            if(solid) {
                paint.alpha=255
                val a=if(mirror)lastMirror else last
                if(a!=null) {
                    val dx=s.x-a.x;val dy=s.y-a.y;val length=hypot(dx,dy)
                    if(length>.0001f) {
                        val nx=-dy/length;val ny=dx/length
                        val r0=a.diameter/2;val r1=s.diameter/2
                        path.rewind();path.moveTo(a.x+nx*r0,a.y+ny*r0)
                        path.lineTo(s.x+nx*r1,s.y+ny*r1);path.lineTo(s.x-nx*r1,s.y-ny*r1)
                        path.lineTo(a.x-nx*r0,a.y-ny*r0);path.close()
                        canvas.drawPath(path,paint)
                    }
                }
                canvas.drawCircle(s.x,s.y,s.diameter/2,paint)
            } else {
                paint.alpha=(s.alpha*255).roundToInt().coerceIn(0,255)
                val r=s.diameter/2;bounds.set(-r,-r,r,r)
                val save=canvas.save();canvas.translate(s.x,s.y);canvas.rotate(s.angle)
                canvas.drawBitmap(requireNotNull(bitmap),null,bounds,paint);canvas.restoreToCount(save)
            }
            if(mirror)lastMirror=s else last=s
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
    private fun drawLegacyStamp(canvas:Canvas,op:Operation.Stroke,s:Stamp) {
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
    private fun blend(m:Mixing)=when(m) {
        Mixing.NORMAL->BlendMode.SRC_OVER;Mixing.MULTIPLY->BlendMode.MULTIPLY
        Mixing.SCREEN->BlendMode.SCREEN;Mixing.OVERLAY->BlendMode.OVERLAY;Mixing.ADD->BlendMode.PLUS
        Mixing.SOFT_LIGHT->BlendMode.SOFT_LIGHT;Mixing.DARKEN->BlendMode.DARKEN
        Mixing.LIGHTEN->BlendMode.LIGHTEN;Mixing.DIFFERENCE->BlendMode.DIFFERENCE
        Mixing.COLOR->BlendMode.COLOR;Mixing.LUMINOSITY->BlendMode.LUMINOSITY
    }
    private fun clipMask(p:Project,base:Layer):Bitmap {
        val revision=if(base.id==transientLayerId)generation else -1L
        clipMasks[base.id]?.let { if(it.source===base && it.generation==revision)return it.bitmap }
        val bitmap=Bitmap.createBitmap(p.width,p.height,Bitmap.Config.ARGB_8888)
        val c=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {alpha=(base.opacity*255).roundToInt()}
        c.drawBitmap(layer(p,base),matrix(p,base),paint)
        clipMasks[base.id]=ClipCache(base,revision,bitmap)
        while(clipMasks.size>2) {val i=clipMasks.entries.iterator();i.next();i.remove()}
        return bitmap
    }
    fun compose(canvas:Canvas,p:Project,whiteBackground:Boolean=false) {
        checkDimensions(p)
        val outer=canvas.save();canvas.clipRect(0,0,p.width,p.height)
        if(whiteBackground)canvas.drawColor(Color.WHITE)
        if(!p.transparent)canvas.drawColor(p.background)
        val bounds=RectF(0f,0f,p.width.toFloat(),p.height.toFloat())
        var clipBase:Layer?=null
        p.layers.forEach { l ->
            if(!l.clipped)clipBase=l
            val base=clipBase
            if(l.visible && l.opacity>0f && (!l.clipped || (base!=null && base.visible && base.opacity>0f))) {
                layerPaint.alpha=(l.opacity*255).roundToInt();layerPaint.blendMode=blend(l.mixing)
                if(l.id!=transientLayerId && !l.clipped) {
                    canvas.drawBitmap(layer(p,l),matrix(p,l),layerPaint)
                } else {
                    // Isolate erasing/alpha-lock and clipping from lower layers and the background.
                    val group=canvas.saveLayer(bounds,layerPaint)
                    val local=canvas.save();canvas.concat(matrix(p,l));drawLocal(canvas,p,l);canvas.restoreToCount(local)
                    if(l.clipped)canvas.drawBitmap(clipMask(p,requireNotNull(base)),0f,0f,maskPaint)
                    canvas.restoreToCount(group)
                }
            }
        }
        canvas.restoreToCount(outer)
    }
    fun flatten(p:Project,whiteBackground:Boolean=false):Bitmap = Bitmap.createBitmap(p.width,p.height,Bitmap.Config.ARGB_8888).also {compose(Canvas(it),p,whiteBackground)}
    override fun close() {
        transientLayerId=null;liveOp=null;liveBase=null;liveInk=null;liveCanvas=null;livePainter=null;liveComposite=null
        replayBuffer=null;layers.clear();tips.clear();clipMasks.clear()
        // Never recycle a bitmap that a hardware display list could still reference.
    }
}
