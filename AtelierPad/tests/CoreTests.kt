import hu.atelierpad.core.*
import java.io.*
import java.util.zip.*
import kotlin.math.*

private var passed=0
private fun test(name:String,f:()->Unit) { f();passed++;println("PASS $name") }
private fun fails(f:()->Unit) { var failed=false;try{f()}catch(e:Exception){failed=true};check(failed) }
private fun archive(project:Project,assets:Map<String,ByteArray> = emptyMap())=ByteArrayOutputStream().also { out -> ProjectArchive.write(out,project) {assets.getValue(it)} }.toByteArray()
private fun zip(entries:List<Pair<String,ByteArray>>) = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { z -> entries.forEach { z.putNextEntry(ZipEntry(it.first));z.write(it.second);z.closeEntry() } } }.toByteArray()
fun main() {
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
    test("event batching does not change unsmoothed spacing") {fun stamps(step:Int):List<Stamp>{val out=mutableListOf<Stamp>();val e=BrushEngine(Brush(smoothing=0f,pressureSize=false),1){out+=it};for(x in 0..100 step step)e.add(Sample(x.toFloat(),0f));return out};val a=stamps(1);val b=stamps(10);check(a.size==b.size);check(a.zip(b).all{abs(it.first.x-it.second.x)<.001})}
    test("stationary stylus produces one dot") {val out=mutableListOf<Stamp>();val e=BrushEngine(Brush(),1){out+=it};repeat(100){e.add(Sample(4f,5f))};check(out.size==1)}
    test("PDF A4 landscape preserves aspect and margins") {val g=ExportGeometry.pdf(1600,1200,300,PdfPaper.A4);check(g.pageWidth==842&&g.pageHeight==595);check(abs(g.width/g.height-4f/3)<.001);check(g.left>=18f&&g.top>=18f)}
    test("PDF A3 portrait") {val g=ExportGeometry.pdf(1200,1600,300,PdfPaper.A3);check(g.pageWidth==842&&g.pageHeight==1191)}
    test("PDF physical canvas size") {val g=ExportGeometry.pdf(2400,1800,300,PdfPaper.CANVAS);check(g.pageWidth==576&&g.pageHeight==432)}
    test("PDF invalid margin rejected") {fails {ExportGeometry.pdf(1,1,300,PdfPaper.A4,999f)}}
    test("flood fill does not cross contour") {val a=intArrayOf(0,0,-16777216,0,0,-16777216,-16777216,-16777216,-16777216);val r=FloodFill.mask(a,3,3,0,0,0);check(r.count{it}==4)}
    test("transparent hidden RGB is ignored") {val a=intArrayOf(0x00ffffff,0x00000000);check(FloodFill.mask(a,2,1,0,0,0).all{it})}
    test("large fill is iterative") {check(FloodFill.mask(IntArray(1024*1024),1024,1024,0,0,0).count{it}==1024*1024)}
    test("fill tolerance is bounded") {val a=intArrayOf(0xff101010.toInt(),0xff151515.toInt(),0xff777777.toInt());check(FloodFill.mask(a,3,1,0,0,5).count{it}==2)}
    println("\n$passed core tests passed; Android device/instrumentation tests are separate.")
}
