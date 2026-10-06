package com.example.c001apk.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * 霜玻璃背景：霜位图（缩小的背景采样，放大回原尺寸即模糊）打底，染色按自身 alpha
 * 压在上面，最后发丝边。位图由 MainActivity 周期性地用 viewPager.draw 录进来。
 *
 * 谁在上由 frostAlpha/染色 alpha 的相对大小决定可见度：v21 起染色在上——
 * frostAlpha 0xF2 时代"霜在上"会把条面变成逐帧滚动的内容（真机"抖动"）。
 */
class FrostedGlassDrawable : Drawable() {

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val tintPaint = Paint()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val rect = RectF()
    private val clipPath = Path()
    private var bitmap: Bitmap? = null
    var radius = 0f
    // 不能叫 tint/stroke：和 Drawable.setTint(int) 同 JVM 签名，编译报 accidental override
    var tintColor = Color.TRANSPARENT
    var strokeColor = Color.TRANSPARENT
    // 霜的可见度：官方通透感 ≈ 底色玻璃上透出一档内容色晕，不是整块模糊贴图
    var frostAlpha = 0x40

    fun setBitmap(b: Bitmap?) {
        bitmap = b
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        if (rect.isEmpty) return
        val r = radius.coerceIn(0f, rect.height() / 2f)
        clipPath.reset()
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        // v21：顺序反过来——霜在下、染色在上。v13 起 frostAlpha 提到 0xF2 后，
        // "霜在上"等于让 95% 的条面是逐帧跟滚动走的内容：真机录像里就是"抖动感"，
        // 而且把 v20 新加的白染色整个盖掉。染色压顶后内容只剩 ~3% 色晕，不抖了
        bitmap?.let {
            bitmapPaint.alpha = frostAlpha
            canvas.drawBitmap(it, null, rect, bitmapPaint)
        }
        tintPaint.color = tintColor
        canvas.drawRect(rect, tintPaint)
        canvas.restore()
        strokePaint.color = strokeColor
        rect.inset(0.5f, 0.5f)
        canvas.drawRoundRect(rect, r, r, strokePaint)
        rect.inset(-0.5f, -0.5f)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
