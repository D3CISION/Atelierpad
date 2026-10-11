package hu.atelierpad.app

import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.*
import hu.atelierpad.core.*
import java.util.UUID
import kotlin.math.*

enum class Tool { BRUSH, ERASER, PICKER, FILL, PAN }

class DrawingView(
    context:Context,assets:AssetStore,
    private val project:()->Project,
    private val commit:(Project,String)->Unit,
    private val message:(String)->Unit,
    private val pick:(Int)->Unit,
    private val fill:(Int,Int)->Unit,
    private val undo:()->Unit,private val redo:()->Unit
):View(context) {
    val renderer=RasterRenderer(assets)
    var onBrushChanged:(()->Unit)?=null
    var brush=Brush()
        set(value) { field=value;onBrushChanged?.invoke() }
    var color=Color.rgb(33,39,47)
    var mirrorView=false
    var grayscaleView=false
    var tool=Tool.BRUSH
    var penOnly=true
    var symmetry=false
    var showGrid=false
    private val viewport=Matrix()
    private val inverse=Matrix()
    private var zoom=1f;private var rotation=0f;private var panX=0f;private var panY=0f
    private var activePointer=-1
    private var activeStylus=false
    private var strokeTime=0L
    private var suppressFingers=false
    private var stroke:Operation.Stroke?=null
    private var engine:BrushEngine?=null
    private var samples=ArrayList<Sample>()
    private var activeLayerId:String?=null
    private var pendingTap:Tool?=null
    private var hoverX=-1f;private var hoverY=-1f
    private var gestureMidX=0f;private var gestureMidY=0f
    private var gestureDistance=0f;private var gestureAngle=0f
    private var gestureStarted=false;private var gestureMax=0
    private var gestureTime=0L;private var gestureMotion=0f;private var gestureFirstX=0f;private var gestureFirstY=0f
    private val checker=Paint().apply {
        val b=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);val c=Canvas(b)
        c.drawColor(Color.rgb(224,226,229));val p=Paint().apply{color=Color.rgb(247,248,249)}
        c.drawRect(0f,0f,16f,16f,p);c.drawRect(16f,16f,32f,32f,p)
        shader=BitmapShader(b,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT)
    }
    init { isFocusable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;contentDescription="Drawing canvas. Stylus draws; two fingers pan, zoom and rotate." }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {super.onSizeChanged(w,h,oldw,oldh);if(oldw==0 || oldh==0)fit() else {updateMatrix();invalidate()}}
    fun fit() {
        val p=project();zoom=min(width.toFloat()/p.width,height.toFloat()/p.height)*.86f
        if(zoom<=0)zoom=1f
        rotation=0f;panX=0f;panY=0f;updateMatrix();invalidate()
    }
    private fun updateMatrix() {
        val p=project()
        viewport.reset();viewport.setTranslate(-p.width/2f,-p.height/2f)
        viewport.postScale(if(mirrorView)-zoom else zoom,zoom);viewport.postRotate(rotation)
        viewport.postTranslate(width/2f+panX,height/2f+panY);viewport.invert(inverse)
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas);canvas.drawColor(Color.rgb(19,23,29))
        val p=project();updateMatrix()
        canvas.save();canvas.concat(viewport)
        val bounds=RectF(0f,0f,p.width.toFloat(),p.height.toFloat())
        canvas.drawRect(RectF(-3f/zoom,-3f/zoom,p.width+3f/zoom,p.height+3f/zoom),Paint().apply{color=Color.BLACK})
        canvas.drawRect(bounds,checker)
        val viewPaint=if(grayscaleView)Paint().apply {colorFilter=ColorMatrixColorFilter(ColorMatrix().apply {setSaturation(0f)})} else null
        val isolation=canvas.saveLayer(bounds,viewPaint)
        renderer.compose(canvas,p);canvas.restoreToCount(isolation)
        if(showGrid) {
            val paint=Paint().apply{color=Color.argb(70,70,125,140);strokeWidth=1f/zoom}
            val step=max(p.width,p.height)/10f
            var x=step;while(x<p.width){canvas.drawLine(x,0f,x,p.height.toFloat(),paint);x+=step}
            var y=step;while(y<p.height){canvas.drawLine(0f,y,p.width.toFloat(),y,paint);y+=step}
        }
        if(symmetry)canvas.drawLine(p.width/2f,0f,p.width/2f,p.height.toFloat(),Paint().apply{color=Color.argb(190,127,221,189);strokeWidth=1f/zoom;pathEffect=DashPathEffect(floatArrayOf(6f/zoom,6f/zoom),0f)})
        canvas.restore()
        if(hoverX>=0 && tool in listOf(Tool.BRUSH,Tool.ERASER)) {
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1.5f;color=Color.WHITE}
            canvas.drawCircle(hoverX,hoverY,max(2f,brush.size*zoom*project().layers[project().active].scale/2),paint)
        }
    }
    override fun onHoverEvent(event:MotionEvent):Boolean {
        if(event.actionMasked==MotionEvent.ACTION_HOVER_EXIT)hoverX=-1f else {hoverX=event.x;hoverY=event.y}
        invalidate();return true
    }
    private fun stylus(e:MotionEvent,i:Int)=e.getToolType(i)==MotionEvent.TOOL_TYPE_STYLUS || e.getToolType(i)==MotionEvent.TOOL_TYPE_ERASER
    private fun coordinates(x:Float,y:Float,local:Boolean=true):FloatArray {
        val a=floatArrayOf(x,y);inverse.mapPoints(a)
        if(local) {val p=project();val inv=Matrix();renderer.matrix(p,p.layers[p.active]).invert(inv);inv.mapPoints(a)}
        return a
    }
    private fun sample(e:MotionEvent,index:Int,history:Int=-1):Sample {
        val xy=if(history<0)coordinates(e.getX(index),e.getY(index)) else coordinates(e.getHistoricalX(index,history),e.getHistoricalY(index,history))
        val raw=if(history<0)e.getPressure(index) else e.getHistoricalPressure(index,history)
        val pressure=if(!activeStylus)1f else if(raw<=0f) samples.lastOrNull()?.pressure ?: .15f else raw.coerceIn(.001f,1f)
        val time=if(history<0)e.eventTime else e.getHistoricalEventTime(history)
        return Sample(xy[0].coerceIn(-20000f,20000f),xy[1].coerceIn(-20000f,20000f),pressure,(time-strokeTime).coerceIn(0L,86_400_000L))
    }
    private fun start(e:MotionEvent,i:Int) {
        val p=project();val l=p.layers[p.active]
        if(tool==Tool.PAN)return
        activePointer=e.getPointerId(i);activeStylus=stylus(e,i);strokeTime=e.eventTime
        if(activeStylus)requestUnbufferedDispatch(e)
        if(tool==Tool.PICKER || tool==Tool.FILL) {pendingTap=tool;return}
        if(l.locked || !l.visible) {message("Select a visible, unlocked layer.");activePointer=-1;return}
        val count=p.layers.sumOf{layer->layer.operations.sumOf{if(it is Operation.Stroke)it.samples.size else 0}}
        if(count>=580000) {message("Project stroke limit reached. Export a backup before simplifying layers.");activePointer=-1;return}
        activeLayerId=l.id;samples=ArrayList()
        val op=Operation.Stroke(brush=brush,color=color,eraser=tool==Tool.ERASER || e.getToolType(i)==MotionEvent.TOOL_TYPE_ERASER,alphaLock=l.alphaLock,mirrored=symmetry,seed=System.nanoTime(),samples=emptyList())
        stroke=op;renderer.begin(p,l,op)
        engine=BrushEngine(op.brush,op.seed){renderer.liveStamp(p,op,it)}
        add(sample(e,i));parent.requestDisallowInterceptTouchEvent(true)
    }
    private fun add(s:Sample) {
        if(samples.size>=20000)return
        val last=samples.lastOrNull()
        if(last!=null && last.x==s.x && last.y==s.y && last.pressure==s.pressure)return
        samples.add(s);engine?.add(s)
    }
    fun finishStroke(cancel:Boolean=false) {
        val template=stroke
        if(template!=null) {
            if(!cancel && samples.isNotEmpty()) {
                engine?.finish(samples.last())
                val p=project();val index=p.layers.indexOfFirst{it.id==activeLayerId}
                if(index>=0) {
                    val l=p.layers[index];val op=template.copy(samples=samples.toList())
                    val newLayer=l.copy(operations=l.operations+op)
                    val newLayers=p.layers.toMutableList().apply{set(index,newLayer)}
                    renderer.end(newLayer);commit(p.copy(layers=newLayers),if(op.eraser)"Erase" else "Brush stroke")
                } else renderer.end(null)
            } else renderer.end(null)
        }
        stroke=null;engine=null;samples=ArrayList();activeLayerId=null;activePointer=-1;activeStylus=false;pendingTap=null
        parent?.requestDisallowInterceptTouchEvent(false);invalidate()
    }
    private fun finishTap(e:MotionEvent,i:Int,cancel:Boolean) {
        val t=pendingTap
        if(!cancel && t!=null) {
            val p=project()
            if(t==Tool.PICKER) {
                val xy=coordinates(e.getX(i),e.getY(i),false)
                if(xy[0]>=0 && xy[1]>=0 && xy[0]<p.width && xy[1]<p.height) {
                    val b=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);val c=Canvas(b)
                    c.translate(-floor(xy[0]),-floor(xy[1]));renderer.compose(c,p)
                    val picked=b.getPixel(0,0);b.recycle();if(Color.alpha(picked)>0)pick(picked or 0xff000000.toInt())
                }
            } else {
                val xy=coordinates(e.getX(i),e.getY(i))
                if(xy[0]>=0 && xy[1]>=0 && xy[0]<p.width && xy[1]<p.height)fill(xy[0].toInt(),xy[1].toInt())
            }
        }
        pendingTap=null;activePointer=-1
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        val action=e.actionMasked;val ai=e.actionIndex
        val canceled=Build.VERSION.SDK_INT>=33 && (e.flags and MotionEvent.FLAG_CANCELED)!=0
        if(action==MotionEvent.ACTION_CANCEL) {finishStroke(true);gestureStarted=false;return true}
        if(action==MotionEvent.ACTION_DOWN) {
            gestureTime=e.eventTime;gestureMax=1;gestureMotion=0f;gestureStarted=false;suppressFingers=false
            gestureFirstX=e.x;gestureFirstY=e.y
            if(stylus(e,ai) || (!penOnly && tool!=Tool.PAN))start(e,ai)
        } else if(action==MotionEvent.ACTION_POINTER_DOWN) {
            gestureMax=max(gestureMax,e.pointerCount)
            if(activePointer<0 && stylus(e,ai))start(e,ai)
            else if(!activeStylus || activePointer<0) {finishStroke(true);gestureStarted=false}
        }
        if(activePointer>=0) {
            val i=e.findPointerIndex(activePointer)
            if(i>=0 && action==MotionEvent.ACTION_MOVE) {
                if(stroke!=null) {for(h in 0 until e.historySize)add(sample(e,i,h));add(sample(e,i))}
            }
            if((action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP) && e.getPointerId(ai)==activePointer) {
                if(pendingTap!=null)finishTap(e,ai,canceled) else {
                    if(!canceled) {
                        for(h in 0 until e.historySize)add(sample(e,ai,h))
                        add(sample(e,ai))
                    }
                    if(activeStylus && e.pointerCount>1)suppressFingers=true
                    finishStroke(canceled)
                }
                gestureStarted=false;gestureMax=0
            }
        } else if(action==MotionEvent.ACTION_MOVE && !suppressFingers) {
            val fingers=(0 until e.pointerCount).filter{!stylus(e,it)}
            if(fingers.size>=2 || (fingers.size==1 && tool==Tool.PAN)) {
                val i=fingers[0];val j=if(fingers.size>1)fingers[1] else i
                val mx=(e.getX(i)+e.getX(j))/2;val my=(e.getY(i)+e.getY(j))/2
                val dist=if(i!=j)hypot(e.getX(i)-e.getX(j),e.getY(i)-e.getY(j)) else 0f
                val angle=if(i!=j)atan2(e.getY(j)-e.getY(i),e.getX(j)-e.getX(i))*180f/PI.toFloat() else 0f
                if(gestureStarted) {
                    val anchor=coordinates(gestureMidX,gestureMidY,false)
                    if(dist>8 && gestureDistance>8)zoom=(zoom*dist/gestureDistance).coerceIn(.05f,30f)
                    if(i!=j) {var delta=angle-gestureAngle;if(delta>180)delta-=360;if(delta< -180)delta+=360;rotation+=delta}
                    updateMatrix();val mapped=anchor.copyOf();viewport.mapPoints(mapped)
                    panX+=mx-mapped[0];panY+=my-mapped[1]
                    gestureMotion+=hypot(mx-gestureMidX,my-gestureMidY)+abs(dist-gestureDistance)
                }
                gestureStarted=true;gestureMidX=mx;gestureMidY=my;gestureDistance=dist;gestureAngle=angle
            }
            if(e.pointerCount==1)gestureMotion=max(gestureMotion,hypot(e.x-gestureFirstX,e.y-gestureFirstY))
        }
        if(action==MotionEvent.ACTION_POINTER_UP)gestureStarted=false
        if(action==MotionEvent.ACTION_UP) {
            if(!canceled && !suppressFingers && activePointer<0 && e.eventTime-gestureTime<280 && gestureMotion<20*resources.displayMetrics.density) {
                if(gestureMax==2)undo() else if(gestureMax==3)redo()
            }
            gestureStarted=false;gestureMax=0
        }
        postInvalidateOnAnimation();return true
    }
    override fun performClick():Boolean {super.performClick();return true}
    fun release() {finishStroke(true);renderer.close()}
}
