package hu.atelierpad.core

import kotlin.math.*

/** Values are explicit, bounded, and independent of Android UI/rendering classes. */
data class EffectSettings(
    val hue: Float = 0f, val saturation: Float = 1f,
    val brightness: Float = 0f, val contrast: Float = 1f,
    val blurSigma: Float = 0f
) {
    fun validate() {
        require(hue.isFinite() && hue in -180f..180f)
        require(saturation.isFinite() && saturation in 0f..2f)
        require(brightness.isFinite() && brightness in -1f..1f)
        require(contrast.isFinite() && contrast in 0f..2f)
        require(blurSigma.isFinite() && blurSigma in 0f..24f)
    }
    val isIdentity get() = hue == 0f && saturation == 1f && brightness == 0f && contrast == 1f && blurSigma == 0f
}

/** sRGB channel adjustment, then a three-box Gaussian approximation in premultiplied-alpha space.
 * CPU backend; bounded to four megapixels. Transparent hidden RGB can never bleed into a blur. */
object PixelEffects {
    const val MAX_PIXELS = 4_194_304
    fun process(input: IntArray, width: Int, height: Int, settings: EffectSettings): IntArray {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS && input.size == width * height)
        settings.validate()
        val output = input.copyOf()
        if (settings.isIdentity) return output
        val radians = settings.hue * PI.toFloat() / 180f
        val cs = cos(radians); val sn = sin(radians)
        // Hue rotation around luminance axis (SVG/CSS hue-rotate matrix).
        val matrix = floatArrayOf(
            .213f + .787f*cs - .213f*sn, .715f - .715f*cs - .715f*sn, .072f - .072f*cs + .928f*sn,
            .213f - .213f*cs + .143f*sn, .715f + .285f*cs + .140f*sn, .072f - .072f*cs - .283f*sn,
            .213f - .213f*cs - .787f*sn, .715f - .715f*cs + .715f*sn, .072f + .928f*cs + .072f*sn
        )
        for (i in output.indices) {
            val pixel = output[i]; val alpha = pixel ushr 24
            if (alpha == 0) { output[i] = 0; continue }
            val red = ((pixel ushr 16) and 255).toFloat(); val green = ((pixel ushr 8) and 255).toFloat(); val blue = (pixel and 255).toFloat()
            var r = red; var g = green; var b = blue
            if (settings.hue != 0f) {
                r = matrix[0]*red + matrix[1]*green + matrix[2]*blue
                g = matrix[3]*red + matrix[4]*green + matrix[5]*blue
                b = matrix[6]*red + matrix[7]*green + matrix[8]*blue
            }
            val luminance = .2126f*r + .7152f*g + .0722f*b
            fun channel(c: Float): Int = (((luminance + (c-luminance)*settings.saturation - 127.5f)*settings.contrast + 127.5f + settings.brightness*255f).roundToInt()).coerceIn(0,255)
            output[i] = (alpha shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
        }
        if (settings.blurSigma == 0f || boxRadii(settings.blurSigma).all { it == 0 }) return output
        for (i in output.indices) {
            val color = output[i]; val a = color ushr 24
            output[i] = (a shl 24) or (((((color ushr 16) and 255)*a + 127)/255) shl 16) or
                (((((color ushr 8) and 255)*a + 127)/255) shl 8) or (((color and 255)*a + 127)/255)
        }
        val scratch = IntArray(output.size)
        for (radius in boxRadii(settings.blurSigma)) {
            if (radius == 0) continue
            boxPass(output, scratch, width, height, radius, horizontal = true)
            boxPass(scratch, output, width, height, radius, horizontal = false)
        }
        for (i in output.indices) {
            val color = output[i]; val a = color ushr 24
            if (a == 0) output[i] = 0
            else {
                fun unpremultiply(c: Int) = ((c * 255 + a/2)/a).coerceIn(0,255)
                output[i] = (a shl 24) or (unpremultiply((color ushr 16) and 255) shl 16) or
                    (unpremultiply((color ushr 8) and 255) shl 8) or unpremultiply(color and 255)
            }
        }
        return output
    }
    internal fun boxRadii(sigma: Float): IntArray {
        val ideal = sqrt(4f*sigma*sigma + 1f)
        var lower = floor(ideal).toInt().coerceAtLeast(1)
        if (lower % 2 == 0) lower--
        val upper = lower + 2
        val count = ((12f*sigma*sigma - 3f*lower*lower - 12f*lower - 9f)/(-4f*lower - 4f)).roundToInt().coerceIn(0,3)
        return IntArray(3) { ((if (it < count) lower else upper) - 1) / 2 }
    }
    /** Clamped edges. Running channel sums avoid allocation/per-pixel loops over the blur radius. */
    private fun boxPass(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val count = if (horizontal) height else width
        val length = if (horizontal) width else height
        val step = if (horizontal) 1 else width
        val diameter = 2*radius + 1
        for (line in 0 until count) {
            val base = if (horizontal) line * width else line
            var a = 0; var r = 0; var g = 0; var b = 0
            for (offset in -radius..radius) {
                val pixel = input[base + offset.coerceIn(0, length-1)*step]
                a += pixel ushr 24; r += (pixel ushr 16) and 255; g += (pixel ushr 8) and 255; b += pixel and 255
            }
            for (position in 0 until length) {
                fun avg(n: Int) = (n + diameter/2)/diameter
                output[base + position*step] = (avg(a) shl 24) or (avg(r) shl 16) or (avg(g) shl 8) or avg(b)
                val leaving = input[base + (position-radius).coerceIn(0,length-1)*step]
                val entering = input[base + (position+radius+1).coerceIn(0,length-1)*step]
                a += (entering ushr 24) - (leaving ushr 24)
                r += ((entering ushr 16) and 255) - ((leaving ushr 16) and 255)
                g += ((entering ushr 8) and 255) - ((leaving ushr 8) and 255)
                b += (entering and 255) - (leaving and 255)
            }
        }
    }
}

/** One undoable replacement; layer identity, order, transform, masks, opacity and blending stay intact. */
object EffectEdit {
    fun bake(project: Project, layerId: String, asset: String): Project {
        require(Brush.ASSET_ID.matches(asset))
        val index = project.layers.indexOfFirst { it.id == layerId }
        require(index >= 0) { "Layer no longer exists" }
        val layer = project.layers[index]
        require(layer.visible && !layer.locked && !layer.alphaLock) { "Unlock the layer before applying effects" }
        val layers = project.layers.toMutableList()
        layers[index] = layer.copy(operations = listOf(Operation.Image(asset = asset, x = 0f, y = 0f, width = project.width.toFloat(), height = project.height.toFloat())))
        return project.copy(layers = layers)
    }
}
