package com.example.c001apk.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * 官方原版的滴不是染色胶囊，是一枚清水放大镜：背后的内容透过滴被放大折射。
 * 复用条那份霜位图：把滴在条里的对应子区域按 magnify 放大画进滴的圆角区，
 * 再描一圈亮边。offsetX/hostWidthPx 由 MainActivity 每帧同步（滴会滑动）。
 */
class GlassLensDrawable : Drawable() {

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val tintPaint = Paint()
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val rect = RectF()
    private val clip = Path()
    private val src = Rect()
    private var bitmap: Bitmap? = null

    var radius = 0f
    var rimColor = Color.WHITE
    var tintColor = Color.TRANSPARENT
    var magnify = 1.15f
    var hostWidthPx = 0f
    var offsetX = 0f

    // 滴和条同一种"通透度"的开关：v18 真机判定左右 tab 不一样，实测滴内 F4F9FD、
    // 条身 E9EFED——滴把背后内容原样（alpha 255）放大画出来，文字直透显得发白杂乱，
    // 条却是 0xF2 糊成色块。frostAlpha 让滴用和条一样的糊度。
    var frostAlpha = 255

    // 横屏 Rail：滴改成上下滑，采样窗口沿位图纵向取，横向铺满
    var vertical = false
    var hostHeightPx = 0f
    var offsetY = 0f

    fun setBitmap(b: Bitmap?) {
        bitmap = b
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        if (rect.isEmpty) return
        val r = radius.coerceIn(0f, rect.height() / 2f)
        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        val b = bitmap
        val hostPx = if (vertical) hostHeightPx else hostWidthPx
        if (b != null && hostPx > 1f) {
            val cx: Float
            val cy: Float
            val hw: Float
            val hh: Float
            if (vertical) {
                val sh = bounds.height() / hostPx * b.height
                cx = b.width / 2f
                cy = offsetY / hostPx * b.height + sh / 2f
                hw = (b.width / magnify).coerceAtLeast(2f)
                hh = (sh / magnify).coerceAtLeast(2f)
            } else {
                val sw = bounds.width() / hostWidthPx * b.width
                cx = offsetX / hostWidthPx * b.width + sw / 2f
                cy = b.height / 2f
                hw = (sw / magnify).coerceAtLeast(2f)
                hh = (b.height / magnify).coerceAtLeast(2f)
            }
            src.set(
                (cx - hw / 2f).toInt().coerceIn(0, b.width - 1),
                (cy - hh / 2f).toInt().coerceIn(0, b.height - 1),
                (cx + hw / 2f).toInt().coerceIn(1, b.width),
                (cy + hh / 2f).toInt().coerceIn(1, b.height),
            )
            if (src.width() > 1 && src.height() > 1) {
                bitmapPaint.alpha = frostAlpha
                canvas.drawBitmap(b, src, rect, bitmapPaint)
            }
        }
        tintPaint.color = tintColor
        canvas.drawRect(rect, tintPaint)
        canvas.restore()
        rimPaint.color = rimColor
        rect.inset(1f, 1f)
        canvas.drawRoundRect(rect, r, r, rimPaint)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
