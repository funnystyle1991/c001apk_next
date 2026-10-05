package com.example.c001apk.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 仿官方酷安底栏的液态选中气泡：头部圆先弹向目标，尾部按帧追赶形成拉丝。
 * 拉丝长度有上限且越拉越细，像糖稀断开——否则头尾相隔一整个底栏时
 * 会连成横贯胶囊的一条粗杠（就是"一坨"）。
 */
class LiquidIndicatorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY }
    private val path = Path()
    private var radius = 15f * density
    private var maxLag = 26f * density
    private var headX = 0f
    private var tailX = 0f
    private var centerY = 0f
    private var placed = false
    private var animator: ValueAnimator? = null

    var gooColor: Int = Color.LTGRAY
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    private val tailTicker = object : Runnable {
        override fun run() {
            tailX += (headX - tailX) * 0.30f
            val lag = headX - tailX
            if (abs(lag) > maxLag) tailX = headX - (if (lag > 0) maxLag else -maxLag)
            if (abs(headX - tailX) > 0.6f) {
                postOnAnimation(this)
                invalidate()
            } else {
                tailX = headX
                invalidate()
            }
        }
    }

    fun placeAt(x: Float, y: Float) {
        headX = x
        tailX = x
        centerY = if (height > 0) height / 2f else y
        placed = true
        invalidate()
    }

    fun slideTo(x: Float, y: Float) {
        if (!placed) {
            placeAt(x, y)
            return
        }
        centerY = if (height > 0) height / 2f else y
        animator?.cancel()
        animator = ValueAnimator.ofFloat(headX, x).apply {
            duration = 320
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                headX = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        postOnAnimation(tailTicker)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        removeCallbacks(tailTicker)
    }

    override fun onDraw(canvas: Canvas) {
        if (!placed) return
        val stretched = (abs(headX - tailX) / maxLag).coerceIn(0f, 1f)
        val neck = radius * (1f - 0.55f * stretched)
        val tailRadius = radius * (1f - 0.35f * stretched)
        // 走 Path 一次填充：三个形状若分开画，半透明重叠处会二次混色显出接缝
        path.rewind()
        path.addRoundRect(
            min(headX, tailX), centerY - neck,
            max(headX, tailX), centerY + neck,
            neck, neck, Path.Direction.CW
        )
        path.addCircle(headX, centerY, radius, Path.Direction.CW)
        path.addCircle(tailX, centerY, tailRadius, Path.Direction.CW)
        canvas.drawPath(path, paint)
    }
}
