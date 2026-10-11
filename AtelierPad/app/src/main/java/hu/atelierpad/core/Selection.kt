package hu.atelierpad.core

import kotlin.math.*

/** Immutable polygon in the selected layer's local pixel coordinates. Even/odd fill. */
data class SelectionPoint(val x:Float,val y:Float)
data class Selection(val points:List<SelectionPoint>,val inverted:Boolean=false) {
    fun validate() {
        require(points.size in 3..2048) { "A selection must have 3 to 2048 points." }
        require(points.all {it.x.isFinite() && it.y.isFinite() && abs(it.x)<=100000f && abs(it.y)<=100000f})
    }
    fun contains(x:Float,y:Float):Boolean {
        if(!x.isFinite() || !y.isFinite())return false
        var inside=false
        var j=points.lastIndex
        for(i in points.indices) {
            val a=points[i];val b=points[j]
            if((a.y>y)!=(b.y>y) && x<(b.x-a.x)*(y-a.y)/(b.y-a.y)+a.x)inside=!inside
            j=i
        }
        return inside xor inverted
    }
    /** Scan conversion at pixel centers: avoids a point-in-polygon test per pixel. */
    fun rasterMask(width:Int,height:Int):BooleanArray {
        validate()
        require(width in 1..4096 && height in 1..4096 && width.toLong()*height<=12_000_000)
        val result=BooleanArray(width*height){inverted}
        val intersections=FloatArray(points.size)
        for(y in 0 until height) {
            val scan=y+.5f;var count=0
            var prev=points.last()
            for(next in points) {
                if((prev.y>scan)!=(next.y>scan)) {
                    intersections[count++]=prev.x+(scan-prev.y)*(next.x-prev.x)/(next.y-prev.y)
                }
                prev=next
            }
            java.util.Arrays.sort(intersections,0,count)
            var k=0
            while(k+1<count) {
                val left=ceil(intersections[k]-.5f).toInt().coerceIn(0,width)
                val right=ceil(intersections[k+1]-.5f).toInt().coerceIn(0,width)
                for(x in left until right)result[y*width+x]=!inverted
                k+=2
            }
        }
        return result
    }
    companion object {
        fun rectangle(x0:Float,y0:Float,x1:Float,y1:Float)=Selection(listOf(
            SelectionPoint(min(x0,x1),min(y0,y1)),SelectionPoint(max(x0,x1),min(y0,y1)),
            SelectionPoint(max(x0,x1),max(y0,y1)),SelectionPoint(min(x0,x1),max(y0,y1))))
        fun ellipse(x0:Float,y0:Float,x1:Float,y1:Float):Selection {
            val cx=(x0+x1)/2;val cy=(y0+y1)/2
            val rx=abs(x1-x0)/2;val ry=abs(y1-y0)/2
            return Selection(List(96) {i->val a=2.0*PI*i/96;SelectionPoint(cx+rx*cos(a).toFloat(),cy+ry*sin(a).toFloat())})
        }
    }
}

/** Shared, bounded mapping for the quick bar. Brush Studio uses the same 1..240 px limits. */
object SliderScale {
    fun sizeAt(progress:Int):Float=1f+239f*(progress.coerceIn(0,1000)/1000f).pow(2)
    fun sizeProgress(size:Float):Int=if(!size.isFinite())0 else
        (sqrt(((size-1f)/239f).coerceIn(0f,1f))*1000).roundToInt()
    fun opacityAt(progress:Int):Float=.01f+.99f*progress.coerceIn(0,1000)/1000f
    fun opacityProgress(value:Float):Int=if(!value.isFinite())0 else
        (((value-.01f)/.99f).coerceIn(0f,1f)*1000).roundToInt()
}
