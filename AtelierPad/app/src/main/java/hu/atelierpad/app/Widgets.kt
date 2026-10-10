package hu.atelierpad.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.*
import android.view.*
import android.widget.*
import hu.atelierpad.core.*
import kotlin.math.*

object Theme {
    val bg=Color.rgb(19,23,29);val panel=Color.rgb(28,33,41);val card=Color.rgb(39,46,56)
    val line=Color.rgb(53,63,73);val ink=Color.rgb(235,240,244);val muted=Color.rgb(156,169,183)
    val accent=Color.rgb(145,225,195)
}
fun Context.dp(n:Int)=(n*resources.displayMetrics.density+.5f).toInt()
fun rounded(color:Int,radius:Float=12f,stroke:Int?=null)=GradientDrawable().apply {
    setColor(color);cornerRadius=radius;stroke?.let{setStroke(1,it)}
}
fun Context.label(text:String,size:Float=14f,color:Int=Theme.ink)=TextView(this).apply {
    this.text=text;textSize=size;setTextColor(color);includeFontPadding=false
}
fun Context.button(text:String,primary:Boolean=false,action:()->Unit)=label(text,13f,if(primary)Theme.bg else Theme.ink).apply {
    gravity=Gravity.CENTER;setPadding(dp(12),dp(8),dp(12),dp(8));minHeight=dp(44)
    background=RippleDrawable(ColorStateList.valueOf(0x22666666),rounded(if(primary)Theme.accent else Theme.card,dp(10).toFloat()),null)
    isClickable=true;isFocusable=true;contentDescription=text;setOnClickListener{action()}
}
fun Context.column(padding:Int=0)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(padding),dp(padding),dp(padding),dp(padding))}
fun Context.row()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
fun LinearLayout.gap(n:Int) {addView(View(context),LinearLayout.LayoutParams(1,context.dp(n)))}
fun LinearLayout.section(title:String) {gap(18);addView(context.label(title.uppercase(),11f,Theme.muted));gap(10)}
fun Context.slider(parent:LinearLayout,title:String,min:Float,max:Float,value:Float,format:(Float)->String={"${it.roundToInt()}"},change:(Float)->Unit) {
    val head=row();head.addView(label(title,13f),LinearLayout.LayoutParams(0,dp(28),1f))
    val amount=label(format(value),12f,Theme.accent);head.addView(amount);parent.addView(head)
    val seek=SeekBar(this).apply{
        this.max=1000;progress=(((value-min)/(max-min))*1000).roundToInt().coerceIn(0,1000)
        progressTintList=ColorStateList.valueOf(Theme.accent);thumbTintList=ColorStateList.valueOf(Theme.accent)
        contentDescription=title;minimumHeight=dp(36)
        setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s:SeekBar?,p:Int,fromUser:Boolean) {if(fromUser){val v=min+(max-min)*p/1000;amount.text=format(v);change(v)}}
            override fun onStartTrackingTouch(s:SeekBar?){}
            override fun onStopTrackingTouch(s:SeekBar?){}
        })
    }
    parent.addView(seek,LinearLayout.LayoutParams(-1,dp(40)))
}
fun Context.toggle(parent:LinearLayout,title:String,value:Boolean,change:(Boolean)->Unit):Switch {
    val s=Switch(this).apply{text=title;setTextColor(Theme.ink);textSize=13f;isChecked=value;minHeight=dp(48);setOnCheckedChangeListener{_,checked->change(checked)}}
    parent.addView(s);return s
}
fun Context.edit(text:String,hint:String="")=EditText(this).apply {
    setText(text);this.hint=hint;setTextColor(Theme.ink);setHintTextColor(Theme.muted);textSize=15f;isSingleLine=true
    backgroundTintList=ColorStateList.valueOf(Theme.accent);minHeight=dp(48)
}
class BrushPreview(context:Context,private val assets:AssetStore,private val brush:()->Brush,private val color:()->Int):View(context) {
    override fun onDraw(canvas:Canvas) {
        canvas.drawColor(Color.rgb(235,233,225))
        if(width==0 || height==0)return
        val spec=brush().copy(size=min(brush().size,context.dp(28).toFloat()),smoothing=0f)
        val points=(0..70).map {i->val t=i/70f;Sample(width*(.08f+t*.84f),height*(.5f+sin(t*PI*2).toFloat()*.18f),.15f+.85f*sin(t*PI).toFloat())}
        val op=Operation.Stroke(brush=spec,color=color(),eraser=false,alphaLock=false,mirrored=false,seed=7,samples=points)
        RasterRenderer(assets).use{it.renderOperation(canvas,Project(width=320,height=100),op)}
    }
}
class ColorField(context:Context,initial:Int,private val selected:(Int)->Unit):View(context) {
    private val hsv=FloatArray(3).also{Color.colorToHSV(initial,it)}
    fun setHue(h:Float){hsv[0]=h;selected(Color.HSVToColor(hsv));invalidate()}
    fun setColor(color:Int){Color.colorToHSV(color,hsv);invalidate()}
    override fun onDraw(canvas:Canvas) {
        val pure=Color.HSVToColor(floatArrayOf(hsv[0],1f,1f))
        val p=Paint().apply{shader=LinearGradient(0f,0f,width.toFloat(),0f,Color.WHITE,pure,Shader.TileMode.CLAMP)}
        canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),p)
        p.shader=LinearGradient(0f,0f,0f,height.toFloat(),Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),p)
        val x=hsv[1]*width;val y=(1-hsv[2])*height
        p.shader=null;p.style=Paint.Style.STROKE;p.strokeWidth=context.dp(2).toFloat();p.color=Color.WHITE
        canvas.drawCircle(x,y,context.dp(7).toFloat(),p);p.strokeWidth=1f;p.color=Color.BLACK;canvas.drawCircle(x,y,context.dp(9).toFloat(),p)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(e.actionMasked==MotionEvent.ACTION_DOWN || e.actionMasked==MotionEvent.ACTION_MOVE) {
            parent.requestDisallowInterceptTouchEvent(true)
            hsv[1]=(e.x/width).coerceIn(0f,1f);hsv[2]=1-(e.y/height).coerceIn(0f,1f)
            selected(Color.HSVToColor(hsv));invalidate()
        }
        if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_CANCEL)parent.requestDisallowInterceptTouchEvent(false)
        return true
    }
}
