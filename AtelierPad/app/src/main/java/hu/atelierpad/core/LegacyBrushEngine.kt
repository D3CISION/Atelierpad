package hu.atelierpad.core

import java.util.Random
import kotlin.math.*


/** Frozen v1 renderer. Never change it: old projects must replay exactly as saved. */
class LegacyBrushEngine(private val brush: Brush, seed: Long, private val emit: (Stamp)->Unit) {
    private val random=Random(seed)
    private var previous: Sample? = null
    private var filtered: Sample? = null
    private var remaining = 0f
    private var heading = 0f
    init { brush.validate() }
    fun add(raw: Sample) {
        require(raw.valid())
        val old=filtered
        val p = if (old == null) raw else {
            val a = 1f - brush.smoothing * .91f
            Sample(old.x+(raw.x-old.x)*a, old.y+(raw.y-old.y)*a, raw.pressure)
        }
        filtered=p
        segment(p)
    }
    fun finish(raw: Sample) {
        require(raw.valid())
        // Catch up to the actual pen-up point without inventing a zero-pressure tail.
        segment(raw); filtered=raw
    }
    private fun segment(p: Sample) {
        val a=previous
        if (a==null) { stamp(p); previous=p; remaining=step(p); return }
        val dx=p.x-a.x; val dy=p.y-a.y; val distance=hypot(dx,dy)
        if (distance < .0001f) { previous=p; return }
        heading=atan2(dy,dx)*180f/PI.toFloat()
        var walked=0f
        var safety=0
        while (walked+remaining <= distance && safety++ < 25000) {
            walked += remaining
            val t=walked/distance
            val q=Sample(a.x+dx*t, a.y+dy*t, a.pressure+(p.pressure-a.pressure)*t)
            stamp(q); remaining=step(q)
        }
        remaining -= distance-walked
        remaining=remaining.coerceAtLeast(.001f)
        previous=p
    }
    private fun diameter(p: Sample): Float {
        val v=p.pressure.coerceIn(0f,1f).pow(brush.pressureGamma)
        return max(.2f, brush.size * if (brush.pressureSize) brush.minSize+(1f-brush.minSize)*v else 1f)
    }
    private fun step(p:Sample)=max(.35f,diameter(p)*brush.spacing)
    private fun stamp(p: Sample) {
        val size=diameter(p)
        val radius=brush.scatter*size
        val dx=(random.nextFloat()*2-1)*radius
        val dy=(random.nextFloat()*2-1)*radius
        val alpha=brush.opacity * if (brush.pressureOpacity) max(.02f,p.pressure.pow(brush.pressureGamma)) else 1f
        emit(Stamp(p.x+dx,p.y+dy,size,alpha,(if(brush.followDirection)heading else 0f)+brush.rotation))
    }
}
