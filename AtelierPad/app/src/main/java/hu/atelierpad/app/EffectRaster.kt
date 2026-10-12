package hu.atelierpad.app

import android.graphics.*
import hu.atelierpad.core.*
import kotlin.math.min

/** Off-main-thread effect evaluation. Never mutates the renderer cache or the source bitmap. */
object EffectRaster {
    fun apply(source: Bitmap, settings: EffectSettings, selection: Selection?, documentWidth: Int, documentHeight: Int): Bitmap {
        val width = source.width; val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        // Blur sigma is expressed in document pixels, not preview/display pixels.
        val scale = min(width.toFloat() / documentWidth, height.toFloat() / documentHeight)
        val result = PixelEffects.process(pixels, width, height, settings.copy(blurSigma = settings.blurSigma * scale.coerceAtMost(1f)))
        val processed = Bitmap.createBitmap(result, width, height, Bitmap.Config.ARGB_8888)
        if (selection == null) return processed
        try {
            val merged = requireNotNull(source.copy(Bitmap.Config.ARGB_8888, true))
            val c = Canvas(merged)
            c.scale(width.toFloat() / documentWidth, height.toFloat() / documentHeight)
            c.clipPath(selection.clipPath(documentWidth, documentHeight))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { blendMode = BlendMode.SRC }
            c.drawBitmap(processed, null, RectF(0f, 0f, documentWidth.toFloat(), documentHeight.toFloat()), paint)
            return merged
        } finally { processed.recycle() }
    }
}
