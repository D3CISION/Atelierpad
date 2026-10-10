package hu.atelierpad.app

import android.content.Context
import android.graphics.*
import android.net.Uri
import hu.atelierpad.core.*
import java.io.*

/** Content-addressed private assets; no broad storage or network permissions. */
class AssetStore(context:Context) {
    val root=File(context.filesDir,"assets").apply { mkdirs() }
    private val resolver=context.contentResolver
    fun file(id:String):File { require(Brush.ASSET_ID.matches(id));return File(root,"$id.png") }
    fun bytes(id:String):ByteArray=file(id).readBytes()
    fun store(bitmap:Bitmap):String {
        val out=ByteArrayOutputStream();check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out))
        val bytes=out.toByteArray();require(bytes.size<=32*1024*1024)
        return storeBytes(bytes)
    }
    private fun storeBytes(bytes:ByteArray):String {
        val id=ProjectArchive.hash(bytes); val target=file(id)
        if(!target.exists()) {
            val tmp=File.createTempFile("asset-",".tmp",root)
            try { FileOutputStream(tmp).use{it.write(bytes);it.fd.sync()};check(tmp.renameTo(target)) } finally {tmp.delete()}
        }
        return id
    }
    fun importImage(uri:Uri,maxSide:Int=4096,maxPixels:Long=12_000_000):Bitmap {
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        resolver.openInputStream(uri).use { requireNotNull(it);BitmapFactory.decodeStream(it,null,options) }
        require(options.outWidth>0 && options.outHeight>0) { "Image cannot be decoded" }
        var sample=1
        while(options.outWidth/sample>maxSide || options.outHeight/sample>maxSide || (options.outWidth.toLong()/sample)*(options.outHeight/sample)>maxPixels) sample*=2
        val decode=BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888}
        return resolver.openInputStream(uri).use { requireNotNull(it);requireNotNull(BitmapFactory.decodeStream(it,null,decode)) }
    }
    fun load(id:String):Bitmap {
        val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeFile(file(id).absolutePath,opts)
        require(opts.outWidth in 1..4096 && opts.outHeight in 1..4096 && opts.outWidth.toLong()*opts.outHeight<=12_000_000)
        return requireNotNull(BitmapFactory.decodeFile(file(id).absolutePath,BitmapFactory.Options().apply{inPreferredConfig=Bitmap.Config.ARGB_8888}))
    }
    fun install(loaded:ProjectArchive.Loaded) {
        loaded.assets.forEach{(id,data)->
            val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(data,0,data.size,opts)
            require(opts.outWidth in 1..4096 && opts.outHeight in 1..4096 && opts.outWidth.toLong()*opts.outHeight<=12_000_000)
            check(storeBytes(data)==id)
        }
    }
}
