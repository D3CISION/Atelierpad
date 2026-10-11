package hu.atelierpad.core

import java.util.Random
import kotlin.math.*

data class Stamp(val x: Float, val y: Float, val diameter: Float, val alpha: Float, val angle: Float)

/** True coverage ink; opacity is applied ONCE to the finished stroke, not to each overlapping dab. */
fun Brush.isSolidInk() = tip == Tip.INK && hardness >= .97f && scatter == 0f && !pressureOpacity && flow >= .999f

/**
 * v2 streaming pipeline: time-aware adaptive filtering -> midpoint quadratic curves ->
 * arc-length dab spacing. It uses actual historical pen samples, not guessed future positions.
 * The 1/2-sample curve tail is flushed to the exact pen-up position. v1 is frozen for old art.
 */
class BrushEngine(private val brush: Brush, seed: Long, version: Int = 2, private val emit: (Stamp)->Unit) {
    private val legacy = if (version == 1) LegacyBrushEngine(brush, seed, emit) else null
    private val random = Random(seed)
    private var filtered: Sample? = null
    private var rawPrevious: Sample? = null
    private var curveStart: Sample? = null
    private var previous: Sample? = null
    private var remaining = 0f
    private var heading = 0f
    private var lastStamp: Stamp? = null
    private var finished = false
    private var travelled = 0f

    init { brush.validate(); require(version in 1..2) }

    fun add(raw: Sample) {
        require(raw.valid())
        check(!finished) { "Stroke already finished" }
        legacy?.let { it.add(raw); return }
        val old = filtered
        val p = filter(raw)
        if (old == null) {
            curveStart = p
            segment(p)
        } else {
            val end = interpolate(old, p, .5f)
            quadratic(requireNotNull(curveStart), old, end)
            curveStart = end
        }
        filtered = p
        rawPrevious = raw
    }

    fun finish(raw: Sample) {
        require(raw.valid())
        if (finished) return
        finished = true
        legacy?.let { it.finish(raw); return }
        val old = filtered
        if (old == null) { segment(raw); return }
        // Do not turn the usual zero-pressure UP packet into an artificial ink spike.
        val end = raw.copy(pressure = old.pressure)
        quadratic(requireNotNull(curveStart), old, end)
        val last = lastStamp
        if (last != null && travelled > .05f && hypot(last.x-end.x, last.y-end.y) > .025f && brush.scatter == 0f) {
            stamp(end)
        }
    }

    private fun filter(raw: Sample): Sample {
        val old = filtered ?: return raw
        val before = rawPrevious ?: raw
        val dt = if (raw.timeMillis > before.timeMillis)
            ((raw.timeMillis-before.timeMillis)/1000f).coerceIn(.001f,.05f) else 1f/120f
        val speed = hypot(raw.x-before.x,raw.y-before.y)/dt
        // Fast moves get less filtering; slow detail work gets stronger noise suppression.
        val cutoff = 40f-35f*brush.smoothing + speed*.045f*(1f-.85f*brush.smoothing)
        val positionAlpha = if(brush.smoothing == 0f)1f else alpha(cutoff,dt)
        val pressureAlpha = if(brush.pressureSmoothing == 0f)1f else alpha(45f-35f*brush.pressureSmoothing,dt)
        return Sample(old.x+(raw.x-old.x)*positionAlpha, old.y+(raw.y-old.y)*positionAlpha,
            (old.pressure+(raw.pressure-old.pressure)*pressureAlpha).coerceIn(0f,1f),raw.timeMillis)
    }
    private fun alpha(cutoff:Float,dt:Float) = (1f-exp((-2f*PI.toFloat()*cutoff*dt).toDouble()).toFloat()).coerceIn(0f,1f)
    private fun interpolate(a:Sample,b:Sample,t:Float)=Sample(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,
        a.pressure+(b.pressure-a.pressure)*t,a.timeMillis+((b.timeMillis-a.timeMillis)*t).toLong())

    private fun quadratic(a:Sample,b:Sample,c:Sample) {
        val length=hypot(b.x-a.x,b.y-a.y)+hypot(c.x-b.x,c.y-b.y)
        val steps=ceil(length/max(.35f,min(3f,brush.size*.12f))).toInt().coerceIn(1,4096)
        for(i in 1..steps) {
            val t=i.toFloat()/steps;val u=1f-t
            segment(Sample(u*u*a.x+2*u*t*b.x+t*t*c.x,u*u*a.y+2*u*t*b.y+t*t*c.y,
                (u*u*a.pressure+2*u*t*b.pressure+t*t*c.pressure).coerceIn(0f,1f),c.timeMillis))
        }
    }
    private fun diameter(p:Sample):Float {
        val pressure=p.pressure.pow(brush.pressureGamma)
        return max(.15f,brush.size*if(brush.pressureSize)brush.minSize+(1f-brush.minSize)*pressure else 1f)
    }
    private fun step(p:Sample)=max(.12f,diameter(p)*brush.spacing)
    private fun segment(p:Sample) {
        val a=previous
        if(a==null) {stamp(p);previous=p;remaining=step(p);return}
        val dx=p.x-a.x;val dy=p.y-a.y;val distance=hypot(dx,dy)
        if(distance<.00001f) {previous=p;return}
        travelled+=distance
        heading=atan2(dy,dx)*180f/PI.toFloat()
        var walked=0f
        while(walked+remaining<=distance+.000001f) {
            walked+=remaining
            val t=(walked/distance).coerceIn(0f,1f)
            val q=interpolate(a,p,t)
            stamp(q);remaining=step(q)
        }
        remaining=(remaining-(distance-walked)).coerceAtLeast(.0001f)
        previous=p
    }
    private fun stamp(p:Sample) {
        val size=diameter(p)
        val radius=brush.scatter*size
        val dx=(random.nextFloat()*2-1)*radius;val dy=(random.nextFloat()*2-1)*radius
        val localFlow=brush.flow*if(brush.pressureOpacity)p.pressure.pow(brush.pressureGamma).coerceAtLeast(.01f) else 1f
        // Flow is specified at 10% spacing. Changing spacing should not radically change darkness.
        val coverage=if(brush.isSolidInk())1f else 1f-(1f-localFlow).pow((step(p)/size/.1f).coerceAtLeast(.001f))
        val s=Stamp(p.x+dx,p.y+dy,size,coverage.coerceIn(0f,1f),(if(brush.followDirection)heading else 0f)+brush.rotation)
        emit(s);lastStamp=s
    }
}
