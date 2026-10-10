package hu.atelierpad.app

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.AtomicFile
import hu.atelierpad.core.*
import java.io.*
import java.util.concurrent.Executors

class ProjectFiles(private val context:Context,val assets:AssetStore) {
    val executor=Executors.newSingleThreadExecutor()
    val directory=File(context.filesDir,"projects").apply{mkdirs()}
    fun path(p:Project)=File(directory,"${p.id}.atelier")
    fun localProjects():List<File> = directory.listFiles()?.mapNotNull{when {it.extension=="atelier"->it;it.name.endsWith(".atelier.bak")->File(it.path.removeSuffix(".bak"));else->null}}?.distinctBy{it.path}?.sortedByDescending{it.lastModified()} ?: emptyList()
    fun saveLocal(p:Project) {
        val atomic=AtomicFile(path(p));var out:FileOutputStream?=null
        try {out=atomic.startWrite();ProjectArchive.write(out,p,assets::bytes);atomic.finishWrite(out)}
        catch(t:Throwable){atomic.failWrite(out);throw t}
    }
    fun readLocal(file:File):Project = AtomicFile(file).openRead().use {load(it)}
    fun load(input:InputStream):Project {
        val loaded=ProjectArchive.read(input);assets.install(loaded);return loaded.project
    }
    fun exportProject(uri:Uri,p:Project) {context.contentResolver.openOutputStream(uri,"wt").use {out->ProjectArchive.write(requireNotNull(out),p,assets::bytes)}}
    fun exportImage(uri:Uri,p:Project,jpeg:Boolean) {
        RasterRenderer(assets).use {renderer->
            val bitmap=renderer.flatten(p,jpeg)
            try {context.contentResolver.openOutputStream(uri,"wt").use {out->
                check(bitmap.compress(if(jpeg)Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,if(jpeg)95 else 100,requireNotNull(out)))
            }} finally {bitmap.recycle()}
        }
    }
    fun exportPdf(uri:Uri,p:Project,paper:PdfPaper) {context.contentResolver.openOutputStream(uri,"wt").use {writePdf(requireNotNull(it),p,paper,assets)}}
    companion object {
        fun writePdf(out:OutputStream,p:Project,paper:PdfPaper,assets:AssetStore) {
            val g=ExportGeometry.pdf(p.width,p.height,p.dpi,paper)
            RasterRenderer(assets).use {renderer ->
                val bitmap=renderer.flatten(p,true);val pdf=PdfDocument()
                try {
                    val page=pdf.startPage(PdfDocument.PageInfo.Builder(g.pageWidth,g.pageHeight,1).create())
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(bitmap,null,RectF(g.left,g.top,g.left+g.width,g.top+g.height),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    pdf.finishPage(page);pdf.writeTo(out)
                } finally {pdf.close();bitmap.recycle()}
            }
        }
    }
}
