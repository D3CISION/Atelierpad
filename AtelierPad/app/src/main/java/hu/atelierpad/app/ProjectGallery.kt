package hu.atelierpad.app

import android.content.Context
import android.graphics.*
import android.util.AtomicFile
import hu.atelierpad.core.*
import java.io.*
import java.util.zip.ZipFile
import kotlin.math.*

/** Sidecars are local metadata/cache only. The portable .atelier v3 format is unchanged. */
class ProjectGallery(context: Context, private val files: ProjectFiles) {
    data class Entry(
        val file: File, val id: String, val title: String, val width: Int, val height: Int,
        val editedAt: Long, val stamp: Long, val length: Long, val problem: String? = null
    )
    private val metadata = File(context.filesDir, "project-index").apply { mkdirs() }
    private val previews = File(context.cacheDir, "project-previews").apply { mkdirs() }
    private fun meta(id: String) = AtomicFile(File(metadata, "$id.meta"))
    private fun validId(id: String) = Project.UUID_PATTERN.matches(id)
    private fun readMeta(file: File): Entry? = try {
        val id = file.nameWithoutExtension
        if (!validId(id)) null else meta(id).openRead().use { raw ->
            val input = DataInputStream(raw)
            check(input.readInt() == 0x41544731)
            val stamp = input.readLong(); val length = input.readLong(); val edited = input.readLong()
            val title = input.readUTF(); val width = input.readInt(); val height = input.readInt()
            if (stamp != file.lastModified() || length != file.length() || title.length !in 1..200 ||
                width !in 64..4096 || height !in 64..4096 || edited <= 0) null
            else Entry(file, id, title, width, height, edited, stamp, length)
        }
    } catch (_: Exception) { null }
    private fun writeMeta(entry: Entry) {
        if (!validId(entry.id)) return
        val atomic = meta(entry.id); var raw: FileOutputStream? = null
        try {
            raw = atomic.startWrite(); val out = DataOutputStream(raw)
            out.writeInt(0x41544731); out.writeLong(entry.stamp); out.writeLong(entry.length)
            out.writeLong(entry.editedAt); out.writeUTF(entry.title); out.writeInt(entry.width); out.writeInt(entry.height)
            out.flush(); atomic.finishWrite(raw)
        } catch (e: Exception) { atomic.failWrite(raw); throw e }
    }
    fun lastEdited(file: File): Long = readMeta(file)?.editedAt ?: file.lastModified().coerceAtLeast(1L)
    fun afterSave(project: Project, editedAt: Long) {
        val file = files.path(project)
        writeMeta(Entry(file, project.id, project.title, project.width, project.height,
            editedAt, file.lastModified(), file.length()))
        // No rasterization while drawing/autosaving: preview regeneration is lazy on the home screen.
        previews.listFiles()?.filter { it.name.startsWith(project.id + "_") }?.forEach { it.delete() }
    }
    fun entries(): List<Entry> = files.localProjects().map { file ->
        try {
            // Recover an interrupted AtomicFile write before using ZipFile/header or its timestamps.
            AtomicFile(file).openRead().close()
            readMeta(file) ?: ZipFile(file).use { zip ->
                val header = zip.getInputStream(requireNotNull(zip.getEntry("document.bin"))).use { ProjectHeader.read(it) }
                require(validId(file.nameWithoutExtension))
                Entry(file, file.nameWithoutExtension, header.title, header.width, header.height,
                    file.lastModified().coerceAtLeast(1L), file.lastModified(), file.length()).also {
                    try { writeMeta(it) } catch (_: IOException) { /* the archive remains usable */ }
                }
            }
        } catch (e: Exception) {
            Entry(file, file.nameWithoutExtension, "Project unavailable", 0, 0,
                file.lastModified().coerceAtLeast(1L), file.lastModified(), file.length(), e.message)
        }
    }.sortedWith(compareByDescending<Entry> { it.editedAt }.thenBy { it.title.lowercase() })

    /** Use only on ProjectFiles.executor. Never shares the live renderer/bitmaps with the UI thread. */
    fun thumbnail(entry: Entry): Bitmap? {
        if (entry.problem != null || !validId(entry.id)) return null
        val cached = File(previews, "${entry.id}_${entry.stamp}_${entry.length}.png")
        if (cached.isFile) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(cached.path, options)
            if (options.outWidth in 1..448 && options.outHeight in 1..448)
                BitmapFactory.decodeFile(cached.path)?.let { return it }
        }
        val project = files.readLocal(entry.file)
        val ratio = 448f / max(project.width, project.height)
        val width = max(1, (project.width * ratio).roundToInt())
        val height = max(1, (project.height * ratio).roundToInt())
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val target = Canvas(image)
            target.drawColor(0xffedeef0.toInt())
            val checker = Paint().apply { color = 0xffdadce0.toInt() }
            for (y in 0 until height step 16) for (x in 0 until width step 16)
                if ((x / 16 + y / 16) % 2 == 0) target.drawRect(x.toFloat(), y.toFloat(), (x + 16).toFloat(), (y + 16).toFloat(), checker)
            target.scale(width.toFloat() / project.width, height.toFloat() / project.height)
            RasterRenderer(files.assets).use { it.compose(target, project) }
            val atomic = AtomicFile(cached); var out: FileOutputStream? = null
            try {
                out = atomic.startWrite(); check(image.compress(Bitmap.CompressFormat.PNG, 100, out))
                atomic.finishWrite(out)
            } catch (_: Exception) { atomic.failWrite(out) } // Cache failures must not hide a valid project.
            return image
        } catch (e: Exception) { image.recycle(); throw e }
        catch (e: OutOfMemoryError) { image.recycle(); throw e }
    }
}
