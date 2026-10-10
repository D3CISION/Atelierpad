package hu.atelierpad.core

import java.io.*
import java.security.MessageDigest
import java.util.zip.*

/** Versioned ZIP container, deliberately independent of Android and thoroughly bounded. */
object ProjectArchive {
    const val VERSION = 1
    const val MAX_BYTES = 96 * 1024 * 1024
    data class Loaded(val project: Project, val assets: Map<String,ByteArray>)
    fun hash(bytes:ByteArray):String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun write(output:OutputStream,project:Project,assetProvider:(String)->ByteArray) {
        project.validate()
        val zip=ZipOutputStream(object:FilterOutputStream(output) { override fun close() { flush() } })
        try {
        zip.setLevel(3)
        zip.putNextEntry(ZipEntry("document.bin"))
        writeDocument(DataOutputStream(zip),project)
        zip.closeEntry()
        var total=0L
        for(id in project.assets().sorted()) {
            val bytes=assetProvider(id); total+=bytes.size
            require(total <= MAX_BYTES && hash(bytes)==id) { "Invalid or oversized asset" }
            zip.putNextEntry(ZipEntry("assets/$id.png")); zip.write(bytes); zip.closeEntry()
        }
        // Do not close the caller's stream (AtomicFile needs to fsync it).
        zip.finish(); zip.flush()
        } finally { zip.close() }
    }
    fun read(input:InputStream):Loaded {
        val zip=ZipInputStream(input)
        try {
        val seen=HashSet<String>(); val assets=LinkedHashMap<String,ByteArray>()
        var project:Project?=null; var total=0L
        while(true) {
            val entry=zip.nextEntry ?: break
            require(!entry.isDirectory && seen.add(entry.name) && seen.size<=4096) { "Duplicate or invalid ZIP entry" }
            val name=entry.name
            val asset=Regex("assets/([a-f0-9]{64})\\.png").matchEntire(name)?.groupValues?.get(1)
            require(name=="document.bin" || asset!=null) { "Unexpected ZIP path" }
            val limit=if(name=="document.bin") 24*1024*1024 else 32*1024*1024
            if(entry.size>=0)require(entry.size<=limit) { "Entry too large" }
            val bytes=ByteArrayOutputStream(); val buffer=ByteArray(8192)
            while(true) {
                val n=zip.read(buffer); if(n<0)break
                total+=n
                require(total<=MAX_BYTES && bytes.size().toLong()+n<=limit) { "Archive size limit exceeded" }
                bytes.write(buffer,0,n)
            }
            val data=bytes.toByteArray()
            if(name=="document.bin") project=readDocument(DataInputStream(ByteArrayInputStream(data)))
            else { require(hash(data)==asset) { "Asset checksum mismatch" }; assets[asset!!]=data }
            zip.closeEntry()
        }
        val doc=requireNotNull(project) { "Missing document" }
        require(doc.assets()==assets.keys) { "Missing or unreferenced assets" }
        return Loaded(doc,assets)
        } finally { zip.close() }
    }
    fun writeDocument(d:DataOutputStream,p:Project) {
        d.writeInt(0x41545044); d.writeInt(VERSION)
        d.writeUTF(p.id);d.writeUTF(p.title);d.writeInt(p.width);d.writeInt(p.height);d.writeInt(p.dpi)
        d.writeInt(p.background);d.writeBoolean(p.transparent);d.writeInt(p.active);d.writeInt(p.layers.size)
        p.layers.forEach { l ->
            d.writeUTF(l.id);d.writeUTF(l.name);d.writeBoolean(l.visible);d.writeBoolean(l.locked);d.writeBoolean(l.alphaLock)
            d.writeFloat(l.opacity);d.writeInt(l.mixing.ordinal);d.writeFloat(l.offsetX);d.writeFloat(l.offsetY)
            d.writeFloat(l.scale);d.writeFloat(l.rotation);d.writeInt(l.operations.size)
            l.operations.forEach { op ->
                when(op) {
                    is Operation.Stroke -> {
                        d.writeByte(1);d.writeUTF(op.id);writeBrush(d,op.brush)
                        d.writeInt(op.color);d.writeBoolean(op.eraser);d.writeBoolean(op.alphaLock);d.writeBoolean(op.mirrored)
                        d.writeLong(op.seed);d.writeInt(op.samples.size)
                        op.samples.forEach { s -> d.writeFloat(s.x);d.writeFloat(s.y);d.writeFloat(s.pressure) }
                    }
                    is Operation.Image -> {
                        d.writeByte(2);d.writeUTF(op.id);d.writeUTF(op.asset)
                        d.writeFloat(op.x);d.writeFloat(op.y);d.writeFloat(op.width);d.writeFloat(op.height)
                    }
                }
            }
        }
    }
    fun readDocument(d:DataInputStream):Project {
        require(d.readInt()==0x41545044) { "Not an Atelier Pad document" }
        require(d.readInt()==VERSION) { "Unsupported project version" }
        val id=d.readUTF();val title=d.readUTF();val w=d.readInt();val h=d.readInt();val dpi=d.readInt()
        require(w in 64..4096 && h in 64..4096 && w.toLong()*h<=12_000_000)
        val bg=d.readInt();val transparent=d.readBoolean();val active=d.readInt()
        val n=d.readInt();require(n in 1..32)
        var ops=0;var points=0
        val layers=List(n) {
            val lid=d.readUTF();val name=d.readUTF();val visible=d.readBoolean();val locked=d.readBoolean();val alpha=d.readBoolean()
            val opacity=d.readFloat();val blend=d.readInt();require(blend in Mixing.values().indices)
            val ox=d.readFloat();val oy=d.readFloat();val scale=d.readFloat();val rot=d.readFloat()
            val count=d.readInt();require(count in 0..20_000);ops+=count;require(ops<=20_000)
            val operations=List(count) {
                when(d.readUnsignedByte()) {
                    1 -> {
                        val oid=d.readUTF();val brush=readBrush(d);val color=d.readInt();val erase=d.readBoolean()
                        val a=d.readBoolean();val mirror=d.readBoolean();val seed=d.readLong()
                        val pc=d.readInt();require(pc in 1..600_000);points+=pc;require(points<=600_000)
                        Operation.Stroke(oid,brush,color,erase,a,mirror,seed,List(pc) { Sample(d.readFloat(),d.readFloat(),d.readFloat()) })
                    }
                    2 -> Operation.Image(d.readUTF(),d.readUTF(),d.readFloat(),d.readFloat(),d.readFloat(),d.readFloat())
                    else -> error("Unknown operation")
                }
            }
            Layer(lid,name,visible,locked,alpha,opacity,Mixing.values()[blend],ox,oy,scale,rot,operations)
        }
        require(d.read()==-1) { "Trailing document data" }
        return Project(id,title,w,h,dpi,bg,transparent,layers,active).also { it.validate() }
    }
    fun writeBrush(d:DataOutputStream,b:Brush) {
        d.writeUTF(b.name);d.writeInt(b.tip.ordinal)
        listOf(b.size,b.opacity,b.spacing,b.hardness,b.smoothing,b.minSize,b.pressureGamma).forEach { d.writeFloat(it) }
        d.writeBoolean(b.pressureSize);d.writeBoolean(b.pressureOpacity)
        d.writeFloat(b.scatter);d.writeFloat(b.rotation);d.writeBoolean(b.followDirection);d.writeFloat(b.texture)
        d.writeUTF(b.asset ?: "")
    }
    fun readBrush(d:DataInputStream):Brush {
        val name=d.readUTF();val tip=d.readInt();require(tip in Tip.values().indices)
        return Brush(name,Tip.values()[tip],d.readFloat(),d.readFloat(),d.readFloat(),d.readFloat(),d.readFloat(),
            d.readFloat(),d.readFloat(),d.readBoolean(),d.readBoolean(),d.readFloat(),d.readFloat(),d.readBoolean(),d.readFloat(),
            d.readUTF().ifEmpty { null }).also { it.validate() }
    }
}
