package hu.atelierpad.app

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.net.Uri
import android.os.*
import android.text.InputType
import android.view.*
import android.widget.*
import hu.atelierpad.core.*
import java.io.*
import java.util.UUID
import kotlin.math.*

/** Native tablet-first shell. No WebView, account, telemetry or runtime network dependency. */
class MainActivity:Activity() {
    private lateinit var assets:AssetStore
    private lateinit var files:ProjectFiles
    private var history=ProjectHistory(Project())
    private val p get()=history.current
    private lateinit var canvas:DrawingView
    private lateinit var inspector:LinearLayout
    private lateinit var workspace:FrameLayout
    private lateinit var status:TextView
    private lateinit var titleView:TextView
    private lateinit var saveState:TextView
    private lateinit var busyOverlay:TextView
    private var preview:BrushPreview?=null
    private var reference:ImageView?=null
    private var referenceBitmap:Bitmap?=null
    private var tab="brush"
    private var customBrushes=mutableListOf<Brush>()
    private val handler=Handler(Looper.getMainLooper())
    private val prefs by lazy {getSharedPreferences("atelier",MODE_PRIVATE)}
    private var started=false
    private var loading=false
    private var pendingExport:Project?=null
    private var exportKind="png"
    private var pdfPaper=PdfPaper.CANVAS
    private var importKind="layer"
    private var saveRunning=false
    private val pendingSaves=LinkedHashMap<String,Project>()
    private val debounce=Runnable{queueSave(p)}
        private val toolButtons=HashMap<Tool,TextView>()
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        assets=AssetStore(applicationContext);files=ProjectFiles(applicationContext,assets)
        loadBrushes();buildUi()
        val id=prefs.getString("lastProject",null)
        val file=id?.let{File(files.directory,"$it.atelier")}
        if(file!=null && (file.exists() || File(file.path+".bak").exists())) {
            loading=true;busy("Restoring project...")
            work({files.readLocal(file)},{doc->loading=false;started=true;openProject(doc);busy(null)}, {loading=false;started=true;busy(null);message("Recovery failed. Your saved file was kept; open it from the gallery.")})
        } else {started=true;queueSave(p)}
    }
    private fun buildUi() {
        val old=if(::canvas.isInitialized)canvas else null
        val oldBrush=old?.brush ?: Brush.presets()[0];val oldColor=old?.color ?: Color.rgb(33,39,47)
        val oldTool=old?.tool ?: Tool.BRUSH
        old?.release()
        val root=column().apply{setBackgroundColor(Theme.bg)}
        root.setOnApplyWindowInsetsListener{v,insets->
            if(Build.VERSION.SDK_INT>=30) {val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom)}
            else {v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)}
            insets
        }
        if(Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(false)
        val top=row().apply{setPadding(dp(16),dp(8),dp(10),dp(8));setBackgroundColor(Theme.panel)}
        val brand=column();brand.addView(label("ATELIER PAD",15f,Theme.accent).apply{letterSpacing=.12f;typeface=Typeface.DEFAULT_BOLD})
        brand.addView(label("ILLUSTRATION STUDIO  /  0.1 ALPHA",9f,Theme.muted));top.addView(brand)
        titleView=label(p.title,13f).apply{gravity=Gravity.CENTER;maxLines=1;setOnClickListener{renameProject()}}
        top.addView(titleView,LinearLayout.LayoutParams(0,-1,1f))
        val actions=row()
        actions.addView(button("Gallery"){gallery()})
        actions.addView(button("Import"){importMenu()})
        actions.addView(button("Export",true){exportMenu()})
        val hscroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(actions)}
        top.addView(hscroll,LinearLayout.LayoutParams(dp(if(resources.configuration.screenWidthDp>=800)300 else 230),-1))
        root.addView(top,LinearLayout.LayoutParams(-1,dp(66)))
        val body=row();body.gravity=Gravity.TOP
        val rail=column(6).apply{setBackgroundColor(Theme.panel)}
        toolButtons.clear()
        listOf(Tool.BRUSH to "Brush",Tool.ERASER to "Eraser",Tool.PICKER to "Pick",Tool.FILL to "Fill",Tool.PAN to "Move").forEach{(tool,name)->
            val b=button(name){canvas.finishStroke();canvas.tool=tool;updateTools()};b.textSize=11f
            rail.addView(b,LinearLayout.LayoutParams(-1,dp(52)));rail.gap(6);toolButtons[tool]=b
        }
        rail.gap(10);rail.addView(button("Undo"){undo()});rail.gap(6);rail.addView(button("Redo"){redo()});rail.gap(14)
        rail.addView(button("Fit"){canvas.fit()});rail.gap(6)
        rail.addView(button("Panel"){inspector.visibility=if(inspector.visibility==View.VISIBLE)View.GONE else View.VISIBLE;canvas.post{canvas.fit()}})
        val railScroll=ScrollView(this).apply{isVerticalScrollBarEnabled=false;addView(rail)}
        body.addView(railScroll,LinearLayout.LayoutParams(dp(74),-1))
        workspace=FrameLayout(this)
        canvas=DrawingView(this,assets,{p},{doc,label->applyProject(doc,label)},{message(it)},{setColor(it)},{x,y->fillAt(x,y)},{undo()},{redo()})
        canvas.brush=oldBrush;canvas.color=oldColor;canvas.tool=oldTool
        canvas.penOnly=prefs.getBoolean("penOnly",true);canvas.symmetry=prefs.getBoolean("symmetry",false);canvas.showGrid=prefs.getBoolean("grid",false)
        workspace.addView(canvas,FrameLayout.LayoutParams(-1,-1))
        body.addView(workspace,LinearLayout.LayoutParams(0,-1,1f))
        inspector=column(12).apply{setBackgroundColor(Theme.panel)}
        body.addView(inspector,LinearLayout.LayoutParams(dp(if(resources.configuration.screenWidthDp>=800)272 else 240),-1))
        if(resources.configuration.screenWidthDp<650)inspector.visibility=View.GONE
        root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        val bottom=row().apply{setPadding(dp(16),0,dp(16),0);setBackgroundColor(Theme.panel)}
        status=label("",11f,Theme.muted);bottom.addView(status,LinearLayout.LayoutParams(0,-1,1f));status.gravity=Gravity.CENTER_VERTICAL
        saveState=label("Local workspace",11f,Theme.accent);bottom.addView(saveState)
        root.addView(bottom,LinearLayout.LayoutParams(-1,dp(34)))
        val all=FrameLayout(this);all.addView(root)
        busyOverlay=label("",17f).apply{gravity=Gravity.CENTER;setBackgroundColor(0xdd13171d.toInt());isClickable=true;visibility=View.GONE}
        all.addView(busyOverlay,FrameLayout.LayoutParams(-1,-1));setContentView(all)
        rebuildInspector();updateTools();updateStatus()
        referenceBitmap?.let{showReference(it)}
    }
    private fun updateTools() {toolButtons.forEach{(tool,b)->b.background=rounded(if(canvas.tool==tool)Theme.accent else Theme.card,dp(10).toFloat());b.setTextColor(if(canvas.tool==tool)Theme.bg else Theme.ink)};canvas.invalidate()}
    private fun updateStatus() {
        titleView.text=p.title
        status.text="${p.width} x ${p.height} px  |  ${p.dpi} DPI  |  ${p.layers.size} layers  |  "+if(canvas.penOnly)"Pen draws / 2 fingers navigate" else "Finger drawing ON"
    }
    private fun rebuildInspector() {
        preview=null;inspector.removeAllViews()
        val tabs=row()
        listOf("brush" to "Brush","color" to "Color","layers" to "Layers").forEach{(id,name)->
            tabs.addView(button(name,tab==id){tab=id;rebuildInspector()},LinearLayout.LayoutParams(0,dp(44),1f))
        }
        inspector.addView(tabs);inspector.gap(4)
        val content=column();val scroll=ScrollView(this).apply{isVerticalScrollBarEnabled=false;addView(content)}
        inspector.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        when(tab){"brush"->brushPanel(content);"color"->colorPanel(content);else->layerPanel(content)}
    }
    private fun brushPanel(c:LinearLayout) {
        c.section(canvas.brush.name)
        preview=BrushPreview(this,assets,{canvas.brush},{canvas.color})
        c.addView(preview,LinearLayout.LayoutParams(-1,dp(90)));c.gap(12)
        slider(c,"Size",1f,240f,canvas.brush.size,{"${it.roundToInt()} px"}){canvas.brush=canvas.brush.copy(size=it);preview?.invalidate();canvas.invalidate()}
        slider(c,"Flow / opacity",.01f,1f,canvas.brush.opacity,{"${(it*100).roundToInt()}%"}){canvas.brush=canvas.brush.copy(opacity=it);preview?.invalidate()}
        slider(c,"Stabilization",0f,1f,canvas.brush.smoothing,{"${(it*100).roundToInt()}%"}){canvas.brush=canvas.brush.copy(smoothing=it)}
        c.addView(button("Brush Studio"){brushStudio()})
        c.section("Brush library")
        (Brush.presets()+customBrushes).forEach{b->c.addView(button(b.name){canvas.brush=b;canvas.tool=Tool.BRUSH;updateTools();rebuildInspector()});c.gap(5)}
        c.addView(button("Import brush tip (PNG)"){importKind="tip";chooseImage()})
        c.section("Drawing assists")
        toggle(c,"Stylus only",canvas.penOnly){canvas.penOnly=it;prefs.edit().putBoolean("penOnly",it).apply();updateStatus()}
        toggle(c,"Mirror symmetry",canvas.symmetry){canvas.symmetry=it;prefs.edit().putBoolean("symmetry",it).apply();canvas.invalidate()}
        toggle(c,"Grid overlay",canvas.showGrid){canvas.showGrid=it;prefs.edit().putBoolean("grid",it).apply();canvas.invalidate()}
        c.gap(10);c.addView(label("Two-finger tap: undo\nThree-finger tap: redo\nPinch and twist: zoom / rotate",12f,Theme.muted))
    }
    private fun colorPanel(c:LinearLayout) {
        c.section("Color")
        val field=ColorField(this,canvas.color){setColor(it)};c.addView(field,LinearLayout.LayoutParams(-1,dp(180)))
        val hsv=FloatArray(3);Color.colorToHSV(canvas.color,hsv)
        slider(c,"Hue",0f,359.9f,hsv[0]){field.setHue(it)}
        val hex=edit(String.format("#%06X",canvas.color and 0xffffff),"#RRGGBB")
        c.addView(hex);c.addView(button("Apply HEX"){try{val value=Color.parseColor(hex.text.toString());setColor(value);field.setColor(value)}catch(e:IllegalArgumentException){message("Use #RRGGBB, for example #406B85.")}})
        c.section("Studio palette")
        val palette=intArrayOf(0xff252b38.toInt(),0xfff4efe5.toInt(),0xff69758b.toInt(),0xffc16e62.toInt(),0xffe9a77b.toInt(),0xffedc77b.toInt(),0xff8aaf93.toInt(),0xff4b8a8b.toInt(),0xff497294.toInt(),0xff8b789f.toInt(),0xffc28d9a.toInt(),0xff66564c.toInt())
        palette.toList().chunked(4).forEach{rowColors->val r=row();rowColors.forEach{color->r.addView(button(" "){setColor(color);field.setColor(color);hex.setText(String.format("#%06X",color and 0xffffff))}.apply{background=rounded(color,dp(10).toFloat(),Theme.line)},LinearLayout.LayoutParams(0,dp(48),1f).apply{setMargins(dp(3),dp(3),dp(3),dp(3))})};c.addView(r)}
        c.gap(10);c.addView(button("Use eyedropper"){canvas.tool=Tool.PICKER;updateTools()})
        c.section("Canvas")
        toggle(c,"Transparent background",p.transparent){applyProject(p.copy(transparent=it),"Canvas background")}
        c.addView(label("PNG keeps transparency. PDF and JPEG are exported on white.",12f,Theme.muted))
    }
    private fun setColor(value:Int) {canvas.color=value or 0xff000000.toInt();preview?.invalidate();canvas.invalidate()}
    private fun layerPanel(c:LinearLayout) {
        c.section("Layers / top to bottom")
        c.addView(button("+ New layer",true){
            canvas.finishStroke()
            if(p.layers.size>=32){message("This alpha supports up to 32 layers.");return@button}
            val list=p.layers.toMutableList();list.add(p.active+1,Layer(name="Layer ${p.layers.size+1}"))
            applyProject(p.copy(layers=list,active=p.active+1),"New layer");rebuildInspector()
        });c.gap(12)
        p.layers.indices.reversed().forEach{index->val layer=p.layers[index]
            val box=column(10).apply{background=rounded(if(index==p.active)Color.rgb(49,71,69) else Theme.card,dp(10).toFloat())}
            val head=row()
            val title=label(layer.name,14f).apply{maxLines=2;setOnClickListener{canvas.finishStroke();history.select(index);rebuildInspector();canvas.invalidate()}}
            head.addView(title,LinearLayout.LayoutParams(0,dp(44),1f))
            head.addView(button(if(layer.visible)"On" else "Off"){changeLayer(index,layer.copy(visible=!layer.visible),"Visibility");rebuildInspector()})
            box.addView(head)
            box.addView(label("${layer.mixing.name.lowercase()}  |  ${(layer.opacity*100).roundToInt()}%"+(if(layer.locked)"  |  locked" else "")+(if(layer.alphaLock)"  |  alpha" else ""),10f,Theme.muted))
            box.gap(8);box.addView(button("Layer settings"){layerSettings(index)})
            c.addView(box);c.gap(8)
        }
        c.addView(label("Alpha lock keeps brush paint inside existing pixels. Layer transforms remain editable.",12f,Theme.muted))
    }
    private fun changeLayer(index:Int,layer:Layer,label:String) {
        val list=p.layers.toMutableList();list[index]=layer;applyProject(p.copy(layers=list),label)
    }
    private fun applyProject(doc:Project,label:String) {
        try { doc.validate() } catch(e:IllegalArgumentException) { message(e.message ?: "Project limit reached"); return }
        history.commit(doc,label);canvas.invalidate();updateStatus()
        handler.removeCallbacks(debounce);handler.postDelayed(debounce,1400);saveState.text="Unsaved changes"
    }
    private fun undo() {canvas.finishStroke(true);if(history.undo()){afterHistory()}else message("Nothing to undo")}
    private fun redo() {canvas.finishStroke(true);if(history.redo()){afterHistory()}else message("Nothing to redo")}
    private fun afterHistory(){canvas.invalidate();updateStatus();if(tab=="layers")rebuildInspector();handler.removeCallbacks(debounce);handler.postDelayed(debounce,1400);saveState.text="Unsaved changes"}
    private fun renameProject(){val e=edit(p.title);AlertDialog.Builder(this).setTitle("Project name").setView(e).setPositiveButton("Save"){_,_->val text=e.text.toString().trim().take(200);if(text.isNotEmpty())applyProject(p.copy(title=text),"Rename project")}.setNegativeButton("Cancel",null).show()}
    private fun queueSave(doc:Project) {
        if(!started || loading)return
        if(doc.id==p.id)prefs.edit().putString("lastProject",doc.id).apply()
        synchronized(pendingSaves){pendingSaves[doc.id]=doc;if(saveRunning)return;saveRunning=true}
        files.executor.execute {
            while(true) {
                val next=synchronized(pendingSaves){
                    val entry=pendingSaves.entries.firstOrNull()
                    if(entry==null){saveRunning=false;null}else{pendingSaves.remove(entry.key);entry.value}
                } ?: break
                try {
                    files.saveLocal(next)
                    prefs.edit().putString("title:${next.id}",next.title).apply()
                    runOnUiThread{if(!isDestroyed && p==next)saveState.text="Saved locally"}
                } catch(e:Exception){runOnUiThread{if(!isDestroyed){saveState.text="SAVE FAILED";message("Could not save: ${e.message}")}}}
            }
        }
    }
    private fun openProject(doc:Project) {
        canvas.finishStroke(true);history.reset(doc);prefs.edit().putString("lastProject",doc.id).apply()
        canvas.renderer.close();canvas.fit();rebuildInspector();updateStatus();saveState.text="Saved locally"
    }
    private fun busy(text:String?){busyOverlay.text=text ?: "";busyOverlay.visibility=if(text==null)View.GONE else View.VISIBLE}
    private fun message(text:String){Toast.makeText(this,text,Toast.LENGTH_LONG).show()}
    private fun <T> work(task:()->T,success:(T)->Unit,failure:(Throwable)->Unit={busy(null);message(it.message ?: "Operation failed")}) {
        files.executor.execute{try{val value=task();runOnUiThread{if(!isDestroyed)success(value)}}catch(t:Exception){runOnUiThread{if(!isDestroyed)failure(t)}}catch(t:OutOfMemoryError){runOnUiThread{if(!isDestroyed){busy(null);message("Not enough memory. Use a smaller canvas and close other apps.")}}}}
    }
    override fun onConfigurationChanged(newConfig:Configuration){super.onConfigurationChanged(newConfig);canvas.finishStroke();buildUi()}
    override fun onPause(){super.onPause();canvas.finishStroke();handler.removeCallbacks(debounce);if(started&&!loading)queueSave(p)}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);canvas.release();referenceBitmap?.recycle();files.executor.shutdown();super.onDestroy()}
    private fun brushStudio() {
        canvas.finishStroke();var draft=canvas.brush
        val c=column(18);val name=edit(draft.name,"Brush name");c.addView(name)
        val test=BrushPreview(this,assets,{draft},{canvas.color});c.addView(test,LinearLayout.LayoutParams(-1,dp(100)))
        fun refresh(){test.invalidate()}
        slider(c,"Hardness",0f,1f,draft.hardness){draft=draft.copy(hardness=it);refresh()}
        slider(c,"Stamp spacing",.025f,1.5f,draft.spacing,{String.format("%.2f",it)}){draft=draft.copy(spacing=it);refresh()}
        slider(c,"Texture grain",0f,1f,draft.texture){draft=draft.copy(texture=it);refresh()}
        slider(c,"Scatter",0f,1f,draft.scatter,{String.format("%.2f",it)}){draft=draft.copy(scatter=it);refresh()}
        slider(c,"Tip rotation",-180f,180f,draft.rotation){draft=draft.copy(rotation=it);refresh()}
        c.section("Pressure response")
        toggle(c,"Pressure controls size",draft.pressureSize){draft=draft.copy(pressureSize=it);refresh()}
        toggle(c,"Pressure controls flow",draft.pressureOpacity){draft=draft.copy(pressureOpacity=it);refresh()}
        slider(c,"Minimum size",.01f,1f,draft.minSize,{"${(it*100).roundToInt()}%"}){draft=draft.copy(minSize=it);refresh()}
        slider(c,"Pressure curve",.2f,4f,draft.pressureGamma,{String.format("%.2f",it)}){draft=draft.copy(pressureGamma=it);refresh()}
        toggle(c,"Follow stroke direction",draft.followDirection){draft=draft.copy(followDirection=it);refresh()}
        c.gap(12);c.addView(label("Flow accumulates as stamps overlap. Custom tips use the image alpha channel; transparent PNG works best.",12f,Theme.muted))
        fun applyBrush(save:Boolean) {
            draft=draft.copy(name=name.text.toString().trim().ifEmpty{"Custom brush"}.take(120));draft.validate()
            canvas.brush=draft
            if(save) {customBrushes.add(draft);if(customBrushes.size>40)customBrushes.removeAt(0);saveBrushes()}
            rebuildInspector()
        }
        AlertDialog.Builder(this).setTitle("Brush Studio").setView(ScrollView(this).apply{addView(c)})
            .setPositiveButton("Use brush"){_,_->applyBrush(false)}.setNeutralButton("Save preset"){_,_->applyBrush(true)}.setNegativeButton("Cancel",null).show()
    }
    private fun saveBrushes() {
        val b=ByteArrayOutputStream();DataOutputStream(b).use{out->out.writeInt(customBrushes.size);customBrushes.forEach{ProjectArchive.writeBrush(out,it)}}
        prefs.edit().putString("customBrushes",android.util.Base64.encodeToString(b.toByteArray(),android.util.Base64.NO_WRAP)).apply()
    }
    private fun loadBrushes() {
        try {
            val raw=prefs.getString("customBrushes",null) ?: return
            DataInputStream(ByteArrayInputStream(android.util.Base64.decode(raw,android.util.Base64.NO_WRAP))).use{input->
                val count=input.readInt();require(count in 0..40);customBrushes=MutableList(count){ProjectArchive.readBrush(input)}
            }
            customBrushes.removeAll{it.asset!=null && !assets.file(it.asset).exists()}
        } catch(e:Exception){customBrushes.clear()}
    }
    private fun layerSettings(index:Int) {
        canvas.finishStroke();history.select(index)
        var draft=p.layers[index];val c=column(18);val name=edit(draft.name);c.addView(name)
        slider(c,"Layer opacity",0f,1f,draft.opacity,{"${(it*100).roundToInt()}%"}){draft=draft.copy(opacity=it)}
        c.addView(label("Blend mode",13f,Theme.muted))
        val blend=Spinner(this).apply{
            adapter=ArrayAdapter<String>(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,Mixing.values().map{it.name.lowercase()})
            setSelection(draft.mixing.ordinal)
            onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){draft=draft.copy(mixing=Mixing.values()[position])}
                override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
            }
        };c.addView(blend,LinearLayout.LayoutParams(-1,dp(48)))
        toggle(c,"Lock layer",draft.locked){draft=draft.copy(locked=it)}
        toggle(c,"Alpha lock",draft.alphaLock){draft=draft.copy(alphaLock=it)}
        c.section("Non-destructive transform")
        slider(c,"Horizontal offset",-p.width.toFloat(),p.width.toFloat(),draft.offsetX){draft=draft.copy(offsetX=it)}
        slider(c,"Vertical offset",-p.height.toFloat(),p.height.toFloat(),draft.offsetY){draft=draft.copy(offsetY=it)}
        slider(c,"Scale",.1f,5f,draft.scale,{String.format("%.2fx",it)}){draft=draft.copy(scale=it)}
        slider(c,"Rotation",-180f,180f,draft.rotation){draft=draft.copy(rotation=it)}
        lateinit var dialog:AlertDialog
        c.addView(button("Reset transform"){draft=draft.copy(offsetX=0f,offsetY=0f,scale=1f,rotation=0f);changeLayer(index,draft,"Reset transform");dialog.dismiss();rebuildInspector()})
        c.section("Layer actions")
        c.addView(button("Duplicate"){dialog.dismiss();duplicateLayer(index)})
        c.addView(button("Move up"){dialog.dismiss();moveLayer(index,1)})
        c.addView(button("Move down"){dialog.dismiss();moveLayer(index,-1)})
        c.addView(button("Clear layer"){dialog.dismiss();confirm("Clear this layer? This can be undone.") {changeLayer(index,p.layers[index].copy(operations=emptyList()),"Clear layer");rebuildInspector()}})
        c.addView(button("Delete layer"){dialog.dismiss();deleteLayer(index)})
        dialog=AlertDialog.Builder(this).setTitle("Layer settings").setView(ScrollView(this).apply{addView(c)})
            .setPositiveButton("Apply"){_,_->draft=draft.copy(name=name.text.toString().trim().ifEmpty{"Layer"}.take(120));changeLayer(index,draft,"Layer settings");rebuildInspector()}
            .setNegativeButton("Cancel",null).create();dialog.show()
    }
    private fun duplicateLayer(index:Int) {
        if(p.layers.size>=32){message("Layer limit reached");return}
        val layer=p.layers[index]
        val copy=layer.copy(id=UUID.randomUUID().toString(),name=(layer.name+" copy").take(120),operations=layer.operations.map{
            when(it){is Operation.Stroke->it.copy(id=UUID.randomUUID().toString());is Operation.Image->it.copy(id=UUID.randomUUID().toString())}
        })
        val list=p.layers.toMutableList();list.add(index+1,copy);applyProject(p.copy(layers=list,active=index+1),"Duplicate layer");rebuildInspector()
    }
    private fun moveLayer(index:Int,direction:Int) {
        val to=index+direction;if(to !in p.layers.indices)return
        val list=p.layers.toMutableList();val layer=list.removeAt(index);list.add(to,layer)
        applyProject(p.copy(layers=list,active=to),"Reorder layer");rebuildInspector()
    }
    private fun deleteLayer(index:Int) {
        if(p.layers.size<=1){message("Keep at least one layer.");return}
        confirm("Delete this layer? This can be undone.") {
            val list=p.layers.toMutableList();list.removeAt(index)
            applyProject(p.copy(layers=list,active=(index-1).coerceAtLeast(0)),"Delete layer");rebuildInspector()
        }
    }
    private fun confirm(text:String,action:()->Unit){AlertDialog.Builder(this).setMessage(text).setPositiveButton("Continue"){_,_->action()}.setNegativeButton("Cancel",null).show()}
    private fun gallery() {
        canvas.finishStroke();queueSave(p);busy("Opening gallery...")
        work({files.localProjects()},{saved->
            busy(null)
            val labels=listOf("+ New canvas","Open .atelier file")+saved.map{prefs.getString("title:${it.nameWithoutExtension}",it.name) ?: it.name}
            AlertDialog.Builder(this).setTitle("Your local projects").setItems(labels.toTypedArray()){_,which->
                when(which){0->newProjectDialog();1->startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="*/*";addCategory(Intent.CATEGORY_OPENABLE)},102)
                    else->{busy("Opening project...");work({files.readLocal(saved[which-2])},{doc->openProject(doc);busy(null)})}}
            }.setNegativeButton("Close",null).show()
        })
    }
    private fun newProjectDialog() {
        val c=column(18);val title=edit("Untitled illustration","Project name");c.addView(title)
        val width=edit("1600","Width (px)").apply{inputType=InputType.TYPE_CLASS_NUMBER}
        val height=edit("1200","Height (px)").apply{inputType=InputType.TYPE_CLASS_NUMBER}
        val dpi=edit("300","DPI").apply{inputType=InputType.TYPE_CLASS_NUMBER}
        c.addView(label("Width / height / DPI",12f,Theme.muted));c.addView(width);c.addView(height);c.addView(dpi)
        val presets=Spinner(this).apply{adapter=ArrayAdapter<String>(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("1600 x 1200 - Sketch","2048 x 2048 - Square","2480 x 3508 - A4 at 300 DPI"));onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){val wh=listOf(1600 to 1200,2048 to 2048,2480 to 3508)[position];width.setText(wh.first.toString());height.setText(wh.second.toString())}
            override fun onNothingSelected(parent:AdapterView<*>?){}
        }};c.addView(presets)
        var transparent=false;toggle(c,"Transparent background",false){transparent=it}
        c.gap(10);c.addView(label("Start with 1600 x 1200 for this alpha. Larger canvases need more memory and device testing.",12f,Theme.muted))
        val dialog=AlertDialog.Builder(this).setTitle("New canvas").setView(c).setPositiveButton("Create",null).setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            try {
                val w=width.text.toString().toInt();val h=height.text.toString().toInt();val d=dpi.text.toString().toInt()
                val doc=Project(title=title.text.toString().trim().ifEmpty{"Untitled"}.take(200),width=w,height=h,dpi=d,transparent=transparent)
                doc.validate();require(w.toLong()*h*36<Runtime.getRuntime().maxMemory()){"This canvas is too large for the current memory budget."}
                queueSave(p);openProject(doc);queueSave(doc);dialog.dismiss()
            } catch(e:Exception){message(e.message ?: "Enter valid canvas dimensions.")}
        }};dialog.show()
    }
    private fun importMenu(){AlertDialog.Builder(this).setTitle("Import image").setItems(arrayOf("As a new layer","As a floating reference","As a custom brush tip")){_,which->importKind=listOf("layer","reference","tip")[which];chooseImage()}.show()}
    private fun chooseImage(){canvas.finishStroke();startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},103)}
    private fun exportMenu() {
        canvas.finishStroke();queueSave(p)
        AlertDialog.Builder(this).setTitle("Export illustration").setItems(arrayOf("Editable project (.atelier)","PNG - transparency supported","JPEG - white background","PDF - print / share")){_,which->
            exportKind=listOf("atelier","png","jpg","pdf")[which]
            if(exportKind=="pdf") {
                AlertDialog.Builder(this).setTitle("PDF page size").setSingleChoiceItems(arrayOf("Canvas size at ${p.dpi} DPI","A4 - fit, no crop","A3 - fit, no crop"),pdfPaper.ordinal){d,pos->pdfPaper=PdfPaper.values()[pos];d.dismiss();requestExport()}.setNegativeButton("Cancel",null).show()
            } else requestExport()
        }.show()
    }
    private fun requestExport() {
        pendingExport=p
        val mime=when(exportKind){"pdf"->"application/pdf";"png"->"image/png";"jpg"->"image/jpeg";else->"application/octet-stream"}
        val name=p.title.replace(Regex("[\\\\/:*?\"<>|]"),"_").take(100)+"."+exportKind
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type=mime;addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,name)},101)
    }
    @Deprecated("Legacy Activity result API is sufficient for this dependency-light alpha")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK){if(requestCode==101)pendingExport=null;return}
        val uri=data?.data ?: return
        when(requestCode) {
            101->{val snapshot=pendingExport;pendingExport=null
                if(snapshot==null){message("The app restarted. Please export again; no file was written.");return}
                busy("Exporting...")
                val kind=exportKind;val paper=pdfPaper
                work({when(kind){"atelier"->files.exportProject(uri,snapshot);"png"->files.exportImage(uri,snapshot,false);"jpg"->files.exportImage(uri,snapshot,true);"pdf"->files.exportPdf(uri,snapshot,paper);else->error("Unknown export format")}},{busy(null);message("Export complete")})
            }
            102->{busy("Reading project...");work({contentResolver.openInputStream(uri).use{files.load(requireNotNull(it))}.copy(id=UUID.randomUUID().toString())},{doc->queueSave(p);openProject(doc);queueSave(doc);busy(null)})}
            103->importImage(uri,importKind)
        }
    }
    private fun importImage(uri:Uri,kind:String) {
        busy("Importing image...")
        work({assets.importImage(uri,if(kind=="tip")512 else 4096)},{bitmap->
            busy(null)
            if(kind=="reference") {showReference(bitmap);return@work}
            if(kind=="layer" && p.layers.size>=32){bitmap.recycle();message("Layer limit reached");return@work}
            // Encoding uses the file worker rather than blocking the UI.
            busy("Saving imported asset...")
            val w=bitmap.width;val h=bitmap.height
            work({try{assets.store(bitmap)}finally{bitmap.recycle()}},{id->
                busy(null)
                if(kind=="tip") {
                    canvas.brush=canvas.brush.copy(name="Custom tip",tip=Tip.CUSTOM,asset=id)
                    tab="brush";rebuildInspector();brushStudio()
                } else {
                    val scale=min(p.width.toFloat()/w,p.height.toFloat()/h)
                    val op=Operation.Image(asset=id,x=(p.width-w*scale)/2,y=(p.height-h*scale)/2,width=w*scale,height=h*scale)
                    val layers=p.layers.toMutableList();layers.add(p.active+1,Layer(name="Imported image",operations=listOf(op)))
                    applyProject(p.copy(layers=layers,active=p.active+1),"Import image");tab="layers";rebuildInspector()
                }
            })
        })
    }
    private fun showReference(bitmap:Bitmap) {
        referenceBitmap?.takeIf{it!==bitmap}?.recycle();referenceBitmap=bitmap
        reference?.let{(it.parent as? ViewGroup)?.let{parent->workspace.removeView(parent)}}
        val frame=FrameLayout(this).apply{background=rounded(Theme.card,dp(12).toFloat(),Theme.line);elevation=dp(8).toFloat()}
        val image=ImageView(this).apply{setImageBitmap(bitmap);scaleType=ImageView.ScaleType.FIT_CENTER}
        frame.addView(image,FrameLayout.LayoutParams(-1,-1).apply{setMargins(dp(8),dp(36),dp(8),dp(8))})
        frame.addView(label("REFERENCE",10f,Theme.muted).apply{setPadding(dp(10),dp(10),0,0)},FrameLayout.LayoutParams(-1,dp(36)))
        frame.addView(button("X"){workspace.removeView(frame);reference=null;referenceBitmap?.recycle();referenceBitmap=null},FrameLayout.LayoutParams(dp(44),dp(36),Gravity.TOP or Gravity.RIGHT))
        workspace.addView(frame,FrameLayout.LayoutParams(dp(220),dp(190),Gravity.BOTTOM or Gravity.RIGHT).apply{setMargins(dp(10),dp(10),dp(16),dp(16))})
        var x=0f;var y=0f
        frame.setOnTouchListener{v,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{x=e.rawX;y=e.rawY};MotionEvent.ACTION_MOVE->{v.translationX+=e.rawX-x;v.translationY+=e.rawY-y;x=e.rawX;y=e.rawY}};true}
        reference=image
    }
    private fun fillAt(x:Int,y:Int) {
        val layer=p.layers[p.active]
        if(layer.locked || !layer.visible){message("Select a visible, unlocked layer.");return}
        if(p.width.toLong()*p.height>4_194_304){message("Fill is limited to 4 megapixels in this alpha to keep memory use bounded.");return}
        var tolerance=24f;var allLayers=true;val c=column(18)
        slider(c,"Color tolerance",0f,100f,tolerance){tolerance=it}
        toggle(c,"Use visible layers as boundaries",true){allLayers=it}
        c.addView(label("Fills the active layer. Small contour-gap closing and edge expansion are not included yet.",12f,Theme.muted))
        AlertDialog.Builder(this).setTitle("Fill region").setView(c).setPositiveButton("Fill"){_,_->performFill(x,y,tolerance.roundToInt(),allLayers)}.setNegativeButton("Cancel",null).show()
    }
    private fun performFill(x:Int,y:Int,tolerance:Int,allLayers:Boolean) {
        val snapshot=p;val layer=snapshot.layers[snapshot.active];val color=canvas.color
        busy("Filling region...")
        work({
            RasterRenderer(assets).use{renderer->
                val w=snapshot.width;val h=snapshot.height
                val source=IntArray(w*h)
                if(allLayers) {
                    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                    try {val target=Canvas(bitmap);val inverse=Matrix();renderer.matrix(snapshot,layer).invert(inverse);target.concat(inverse);renderer.compose(target,snapshot);bitmap.getPixels(source,0,w,0,0,w,h)}finally{bitmap.recycle()}
                } else renderer.layer(snapshot,layer).getPixels(source,0,w,0,0,w,h)
                val mask=FloodFill.mask(source,w,h,x,y,tolerance)
                val pixels=IntArray(w*h)
                if(layer.alphaLock)renderer.layer(snapshot,layer).getPixels(pixels,0,w,0,0,w,h)
                for(i in pixels.indices)if(mask[i]) {
                    pixels[i]=if(layer.alphaLock)(pixels[i] and 0xff000000.toInt()) or (color and 0xffffff) else color
                }
                val bitmap=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
                val id=try{assets.store(bitmap)}finally{bitmap.recycle()}
                Operation.Image(asset=id,x=0f,y=0f,width=w.toFloat(),height=h.toFloat())
            }
        },{op->
            val operations=if(layer.alphaLock)listOf(op) else layer.operations+op
            val layers=snapshot.layers.toMutableList();layers[snapshot.active]=layer.copy(operations=operations)
            applyProject(snapshot.copy(layers=layers),"Fill region");busy(null)
        })
    }
}
