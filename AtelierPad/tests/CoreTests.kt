package hu.atelierpad.tests

import hu.atelierpad.core.*
import java.io.*
import java.util.zip.*
import kotlin.math.*

private var passed=0
private fun test(name:String,f:()->Unit) { f();passed++;println("PASS $name") }
private fun fails(f:()->Unit) { var failed=false;try{f()}catch(e:Exception){failed=true};check(failed) }
private fun archive(project:Project,assets:Map<String,ByteArray> = emptyMap())=ByteArrayOutputStream().also { out -> ProjectArchive.write(out,project) {assets.getValue(it)} }.toByteArray()
private fun zip(entries:List<Pair<String,ByteArray>>) = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { z -> entries.forEach { z.putNextEntry(ZipEntry(it.first));z.write(it.second);z.closeEntry() } } }.toByteArray()
fun runCoreRegression(fixture:(String)->ByteArray):Int {
    passed=0
    test("blank project archive round-trip") { val p=Project();check(ProjectArchive.read(ByteArrayInputStream(archive(p))).project==p) }
    val stroke=Operation.Stroke(brush=Brush.presets()[1],color=0xff336699.toInt(),eraser=false,alphaLock=true,mirrored=true,seed=42,samples=listOf(Sample(10f,10f,.2f),Sample(80f,95f,1f)))
    test("stroke metadata and pressure round-trip") {val p=Project(layers=listOf(Layer(operations=listOf(stroke))));check(ProjectArchive.read(ByteArrayInputStream(archive(p))).project==p)}
    test("asset round-trip + sha256") { val data=byteArrayOf(1,2,3);val id=ProjectArchive.hash(data);val p=Project(layers=listOf(Layer(operations=listOf(Operation.Image(asset=id,x=0f,y=0f,width=100f,height=100f)))));val loaded=ProjectArchive.read(ByteArrayInputStream(archive(p,mapOf(id to data))));check(loaded.assets[id]!!.contentEquals(data)) }
    test("custom brush asset round-trip") {val bytes=byteArrayOf(9,8,7);val id=ProjectArchive.hash(bytes);val op=stroke.copy(brush=Brush(tip=Tip.CUSTOM,asset=id));val p=Project(layers=listOf(Layer(operations=listOf(op))));check(ProjectArchive.read(ByteArrayInputStream(archive(p,mapOf(id to bytes)))).project==p)}
    test("missing asset rejected") {val id="a".repeat(64);val p=Project(layers=listOf(Layer(operations=listOf(stroke.copy(brush=Brush(tip=Tip.CUSTOM,asset=id))))));val b=ByteArrayOutputStream();ProjectArchive.writeDocument(DataOutputStream(b),p);fails {ProjectArchive.read(ByteArrayInputStream(zip(listOf("document.bin" to b.toByteArray()))))}}
    test("zip traversal rejected") {fails {ProjectArchive.read(ByteArrayInputStream(zip(listOf("../../outside" to byteArrayOf(1)))))}}
    test("bad magic rejected") { fails {ProjectArchive.readDocument(DataInputStream(ByteArrayInputStream(ByteArray(64))))} }
    test("future format rejected") {val b=ByteArrayOutputStream();DataOutputStream(b).apply {writeInt(0x41545044);writeInt(99)};fails{ProjectArchive.readDocument(DataInputStream(ByteArrayInputStream(b.toByteArray())))}}
    test("truncated archive rejected") {val bytes=archive(Project());fails {ProjectArchive.read(ByteArrayInputStream(bytes.copyOf(30)))}}
    test("NaN brush rejected") {fails {Brush(size=Float.NaN).validate()} }
    test("infinite sample rejected") {fails {Project(layers=listOf(Layer(operations=listOf(stroke.copy(samples=listOf(Sample(Float.POSITIVE_INFINITY,0f))))))).validate()} }
    test("invalid canvas rejected") {fails {Project(width=4096,height=4096).validate()} }
    test("invalid layer opacity rejected") {fails {Project(layers=listOf(Layer(opacity=2f))).validate()} }
    test("duplicate operation ids rejected") {fails {Project(layers=listOf(Layer(operations=listOf(stroke,stroke)))).validate()} }
    test("undo redo and branch truncation") {val p=Project();val h=ProjectHistory(p);h.commit(p.copy(title="A"),"rename");check(h.undo());check(h.current==p);check(h.redo());check(h.current.title=="A");h.undo();h.commit(p.copy(title="B"),"rename");check(!h.canRedo)}
    test("bounded undo history") {val h=ProjectHistory(Project(),maxEntries=3);repeat(10){h.commit(h.current.copy(title="T$it"),"edit")};repeat(3){check(h.undo())};check(!h.undo())}
    test("seeded brush replay is deterministic") {fun render():List<Stamp>{val out=mutableListOf<Stamp>();val e=BrushEngine(Brush(scatter=.5f),123){out+=it};stroke.samples.forEach(e::add);e.finish(stroke.samples.last());return out};check(render()==render())}
    test("pressure increases width") {val out=mutableListOf<Stamp>();val e=BrushEngine(Brush(smoothing=0f),1){out+=it};e.add(Sample(0f,0f,.1f));e.add(Sample(100f,0f,1f));check(out.last().diameter>out.first().diameter)}
    test("event batching does not change unsmoothed spacing") {fun stamps(step:Int):List<Stamp>{val out=mutableListOf<Stamp>();val e=BrushEngine(Brush(smoothing=0f,pressureSize=false),1){out+=it};for(x in 0..100 step step)e.add(Sample(x.toFloat(),0f));e.finish(Sample(100f,0f));return out};val a=stamps(1);val b=stamps(10);check(a.size==b.size);check(a.zip(b).all{abs(it.first.x-it.second.x)<.001})}
    test("stationary stylus produces one dot") {val out=mutableListOf<Stamp>();val e=BrushEngine(Brush(),1){out+=it};repeat(100){e.add(Sample(4f,5f))};check(out.size==1)}
    test("PDF A4 landscape preserves aspect and margins") {val g=ExportGeometry.pdf(1600,1200,300,PdfPaper.A4);check(g.pageWidth==842&&g.pageHeight==595);check(abs(g.width/g.height-4f/3)<.001);check(g.left>=18f&&g.top>=18f)}
    test("PDF A3 portrait") {val g=ExportGeometry.pdf(1200,1600,300,PdfPaper.A3);check(g.pageWidth==842&&g.pageHeight==1191)}
    test("PDF physical canvas size") {val g=ExportGeometry.pdf(2400,1800,300,PdfPaper.CANVAS);check(g.pageWidth==576&&g.pageHeight==432)}
    test("PDF invalid margin rejected") {fails {ExportGeometry.pdf(1,1,300,PdfPaper.A4,999f)}}
    test("flood fill does not cross contour") {val a=intArrayOf(0,0,-16777216,0,0,-16777216,-16777216,-16777216,-16777216);val r=FloodFill.mask(a,3,3,0,0,0);check(r.count{it}==4)}
    test("transparent hidden RGB is ignored") {val a=intArrayOf(0x00ffffff,0x00000000);check(FloodFill.mask(a,2,1,0,0,0).all{it})}
    test("large fill is iterative") {check(FloodFill.mask(IntArray(1024*1024),1024,1024,0,0,0).count{it}==1024*1024)}
    test("fill tolerance is bounded") {val a=intArrayOf(0xff101010.toInt(),0xff151515.toInt(),0xff777777.toInt());check(FloodFill.mask(a,3,1,0,0,5).count{it}==2)}
    test("v1 archive reads with frozen renderer") {
        val p=ProjectArchive.read(ByteArrayInputStream(fixture("legacy-v1.atelier"))).project
        val op=p.layers.single().operations.single() as Operation.Stroke
        check(op.engineVersion==1 && op.samples.all {it.timeMillis==0L})
        check(op.brush.hardness==.85f && op.brush.smoothing==.25f)
    }
    test("v1 stamp geometry and alpha exactly preserved") {
        val p=ProjectArchive.read(ByteArrayInputStream(fixture("legacy-v1.atelier"))).project
        val op=p.layers.single().operations.single() as Operation.Stroke
        val actual=mutableListOf<Stamp>();val e=BrushEngine(op.brush,op.seed,op.engineVersion){actual+=it}
        op.samples.forEach(e::add);e.finish(op.samples.last())
        val expected=fixture("legacy-v1-stamps.csv").toString(Charsets.UTF_8).trim().lines().map {it.split(',').map(String::toFloat)}
        check(actual.size==expected.size)
        actual.zip(expected).forEach {(a,b)->check(listOf(a.x,a.y,a.diameter,a.alpha,a.angle).zip(b).all {abs(it.first-it.second)<.00001f})}
    }
    test("v1 project round trips through v2 without changing old strokes") {
        val old=ProjectArchive.read(ByteArrayInputStream(fixture("legacy-v1.atelier"))).project
        check(ProjectArchive.read(ByteArrayInputStream(archive(old))).project==old)
    }
    test("mixed v1 and v2 strokes round trip") {
        val old=ProjectArchive.read(ByteArrayInputStream(fixture("legacy-v1.atelier"))).project
        val op=stroke.copy(engineVersion=2,samples=listOf(Sample(2f,3f,.3f,0),Sample(50f,30f,.7f,17)))
        val p=old.copy(layers=listOf(old.layers[0].copy(operations=old.layers[0].operations+op)))
        check(ProjectArchive.read(ByteArrayInputStream(archive(p))).project==p)
    }
    test("new flow and pressure smoothing brush settings round trip") {
        val b=Brush(flow=.19f,pressureSmoothing=.87f)
        val out=ByteArrayOutputStream();ProjectArchive.writeBrush(DataOutputStream(out),b)
        check(ProjectArchive.readBrush(DataInputStream(ByteArrayInputStream(out.toByteArray())))==b)
    }
    test("clipping metadata round trip") {
        val p=Project(layers=listOf(Layer(),Layer(clipped=true,mixing=Mixing.SOFT_LIGHT)))
        check(ProjectArchive.read(ByteArrayInputStream(archive(p))).project==p)
    }
    test("every blending mode round trips") {
        val p=Project(layers=Mixing.values().map {Layer(mixing=it)})
        check(ProjectArchive.read(ByteArrayInputStream(archive(p))).project==p)
    }
    test("legacy blending mode ordinal values unchanged") {
        check(Mixing.values().take(5)==listOf(Mixing.NORMAL,Mixing.MULTIPLY,Mixing.SCREEN,Mixing.OVERLAY,Mixing.ADD))
    }
    test("v2 document version marker") {
        val out=ByteArrayOutputStream();ProjectArchive.writeDocument(DataOutputStream(out),Project())
        val input=DataInputStream(ByteArrayInputStream(out.toByteArray()));input.readInt();check(input.readInt()==2)
    }
    fun dabs(brush:Brush,points:List<Sample>,seed:Long=7):List<Stamp> {
        val out=mutableListOf<Stamp>();val e=BrushEngine(brush,seed){out+=it}
        points.forEach(e::add);e.finish(points.last());return out
    }
    val path=listOf(Sample(0f,0f,.2f,0),Sample(30f,0f,.8f,8),Sample(30f,45f,.5f,16),Sample(70f,65f,.3f,24))
    test("finished curve ends at pen-up position") {
        val end=dabs(Brush(),path).last();check(abs(end.x-70f)<.001f && abs(end.y-65f)<.001f)
    }
    test("midpoint curve rounds an abrupt corner") {
        val pts=listOf(Sample(0f,0f),Sample(50f,0f),Sample(50f,50f),Sample(100f,50f))
        val out=dabs(Brush(size=4f,smoothing=0f,pressureSize=false),pts)
        check(out.minOf {hypot(it.x-50f,it.y)}>3f)
        check(out.any {it.x in 35f..49f && it.y in 1f..20f})
    }
    test("stroke finish is idempotent") {
        val out=mutableListOf<Stamp>();val e=BrushEngine(Brush(),0){out+=it}
        path.forEach(e::add);e.finish(path.last());val count=out.size;e.finish(path.last());check(out.size==count)
    }
    test("input after finished stroke is rejected") {
        val e=BrushEngine(Brush(),0){};e.add(path[0]);e.finish(path[0]);fails{e.add(path[1])}
    }
    test("finish without input creates a single dot") {
        val out=mutableListOf<Stamp>();val e=BrushEngine(Brush(),0){out+=it};e.finish(Sample(4f,6f));check(out.size==1)
    }
    test("stroke opacity does not compound into dab alpha") {
        check(dabs(Brush(opacity=.2f),path)==dabs(Brush(opacity=1f),path))
    }
    test("solid ink uses full coverage for a smooth single-opacity surface") {
        check(dabs(Brush(),path).all {it.alpha==1f})
    }
    test("paint flow increases deposited alpha") {
        val p=listOf(Sample(0f,0f),Sample(20f,0f))
        check(dabs(Brush(tip=Tip.SOFT,flow=.8f),p)[0].alpha>dabs(Brush(tip=Tip.SOFT,flow=.1f),p)[0].alpha)
    }
    test("flow normalization remains consistent at different spacing") {
        fun transmittance(spacing:Float):Float {
            val b=Brush(size=80f,tip=Tip.SOFT,spacing=spacing,flow=.2f,pressureSize=false)
            val a=dabs(b,listOf(Sample(0f,0f)))[0].alpha
            return (1f-a).pow(1f/spacing)
        }
        check(abs(transmittance(.05f)-transmittance(.2f))<.0001f)
    }
    test("pressure smoothing damps an abrupt sensor jump") {
        val pts=(0..20).map {Sample(it*10f,0f,if(it<10).1f else .9f,it*4L)}
        val a=dabs(Brush(size=20f,smoothing=0f,pressureSmoothing=0f),pts)
        val b=dabs(Brush(size=20f,smoothing=0f,pressureSmoothing=1f),pts)
        fun at(out:List<Stamp>)=out.minBy {abs(it.x-100f)}.diameter
        check(at(b)<at(a))
    }
    test("adaptive smoothing reduces low-speed jitter") {
        val pts=(0..200).map {Sample(it.toFloat(),sin(it*.9).toFloat()*2f,1f,it*8L)}
        val raw=dabs(Brush(size=3f,pressureSize=false,smoothing=0f),pts)
        val smooth=dabs(Brush(size=3f,pressureSize=false,smoothing=1f),pts)
        check(smooth.drop(20).dropLast(20).map {abs(it.y)}.average()<raw.drop(20).dropLast(20).map {abs(it.y)}.average())
    }
    test("thin slow strokes remain finite and connected") {
        val pts=(0..50).map {Sample(it*.2f,sin(it*.1).toFloat(),.01f,it*9L)}
        val out=dabs(Brush(size=1f,minSize=.01f),pts)
        check(out.all {it.x.isFinite() && it.y.isFinite() && it.diameter>=.15f && it.alpha in 0f..1f})
        check(out.zipWithNext().all {hypot(it.second.x-it.first.x,it.second.y-it.first.y)<1f})
    }
    test("one pixel long stroke has a bounded dab count") {
        val out=dabs(Brush(size=1f,smoothing=0f,pressureSize=false),listOf(Sample(0f,0f),Sample(1000f,0f)))
        check(out.size in 1000..12000)
    }
    test("repeated sample timestamps remain finite") {
        check(dabs(Brush(),path.map {it.copy(timeMillis=0)}).all {it.x.isFinite() && it.y.isFinite()})
    }
    test("negative sample time rejected") {fails {Project(layers=listOf(Layer(operations=listOf(stroke.copy(samples=listOf(Sample(1f,1f,timeMillis=-1))))))).validate()}}
    test("out of range flow rejected") {fails {Brush(flow=0f).validate()};fails {Brush(flow=2f).validate()}}
    test("nonfinite pressure smoothing rejected") {fails {Brush(pressureSmoothing=Float.NaN).validate()}}
    test("future stroke engine rejected") {fails {Project(layers=listOf(Layer(operations=listOf(stroke.copy(engineVersion=3))))).validate()}}
    test("solid ink branch excludes scatter and pressure-opacity brushes") {
        check(Brush().isSolidInk());check(!Brush(scatter=.1f).isSolidInk());check(!Brush(pressureOpacity=true).isSolidInk())
    }
    test("every bundled brush is valid") {Brush.presets().forEach {it.validate()};check(Brush.presets().size==8)}
    test("clipping toggle is undoable") {
        val p=Project(layers=listOf(Layer(),Layer()));val h=ProjectHistory(p)
        h.commit(p.copy(layers=listOf(p.layers[0],p.layers[1].copy(clipped=true))),"clip")
        check(h.current.layers[1].clipped);h.undo();check(!h.current.layers[1].clipped)
    }
    test("mixed engine undo history keeps version identifiers") {
        val p=Project(layers=listOf(Layer(operations=listOf(stroke.copy(engineVersion=1)))))
        val h=ProjectHistory(p);h.commit(p.copy(layers=listOf(p.layers[0].copy(operations=p.layers[0].operations+stroke.copy(id=java.util.UUID.randomUUID().toString())))),"new ink")
        h.undo();check((h.current.layers[0].operations[0] as Operation.Stroke).engineVersion==1)
    }
    println("\n$passed core tests passed; Android device/instrumentation tests are separate.")
    return passed
}
fun main() {
    runCoreRegression {name->File("tests/fixtures",name).readBytes()}
}
