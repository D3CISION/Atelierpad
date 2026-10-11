package hu.atelierpad.core

import kotlin.math.*

enum class PdfPaper { CANVAS, A4, A3 }
data class PdfPlacement(val pageWidth: Int, val pageHeight: Int, val left: Float, val top: Float, val width: Float, val height: Float)
object ExportGeometry {
    /** PDF units are 1/72 inch. DPI changes physical size, never creates image detail. */
    fun pdf(w:Int,h:Int,dpi:Int,paper:PdfPaper,margin:Float=18f):PdfPlacement {
        require(w>0 && h>0 && dpi in 72..600 && margin.isFinite() && margin>=0)
        if(paper==PdfPaper.CANVAS) {
            val pw=max(1,(w*72.0/dpi).roundToInt()); val ph=max(1,(h*72.0/dpi).roundToInt())
            // Keep image aspect ratio despite integer PDF page dimensions.
            val s=min(pw.toFloat()/w,ph.toFloat()/h)
            return PdfPlacement(pw,ph,(pw-w*s)/2,(ph-h*s)/2,w*s,h*s)
        }
        val short=if(paper==PdfPaper.A4) 595 else 842
        val long=if(paper==PdfPaper.A4) 842 else 1191
        val pw=if(w>h)long else short; val ph=if(w>h)short else long
        require(margin*2<min(pw,ph))
        val s=min((pw-margin*2)/w,(ph-margin*2)/h)
        return PdfPlacement(pw,ph,(pw-w*s)/2,(ph-h*s)/2,w*s,h*s)
    }
}
object FloodFill {
    /** Returns an opaque/transparent region mask. Work is iterative and bounded by pixel count. */
    fun mask(source:IntArray,width:Int,height:Int,x:Int,y:Int,tolerance:Int,allowed:BooleanArray?=null):BooleanArray {
        require(width>0 && height>0 && width.toLong()*height==source.size.toLong())
        require(x in 0 until width && y in 0 until height && tolerance in 0..255)
        require(allowed==null || allowed.size==source.size)
        val target=source[y*width+x]
        val seen=BooleanArray(source.size); val result=BooleanArray(source.size)
        val queue=IntArray(source.size); var head=0; var tail=0
        fun matches(c:Int):Boolean {
            val ca=(c ushr 24) and 255; val ta=(target ushr 24) and 255
            // Ignore hidden RGB channels of completely transparent pixels.
            if(ca==0 && ta==0) return true
            return abs(ca-ta)<=tolerance && abs(((c ushr 16)and 255)-((target ushr 16)and 255))<=tolerance &&
                abs(((c ushr 8)and 255)-((target ushr 8)and 255))<=tolerance && abs((c and 255)-(target and 255))<=tolerance
        }
        fun push(i:Int) { if(!seen[i]) { seen[i]=true; if((allowed==null || allowed[i]) && matches(source[i])) { result[i]=true; queue[tail++]=i } } }
        push(y*width+x)
        while(head<tail) {
            val i=queue[head++]; val xx=i%width
            if(xx>0)push(i-1); if(xx+1<width)push(i+1)
            if(i>=width)push(i-width); if(i+width<source.size)push(i+width)
        }
        return result
    }
}
