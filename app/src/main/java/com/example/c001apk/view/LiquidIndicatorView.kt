package com.example.c001apk.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 仿官方酷安底栏的液态选中气泡：切换 tab 时头部圆先弹向目标，
 * 尾部按帧追（0.22 阻尼）形成拉丝水滴，过冲插值负责回弹。
 */
class LiquidIndicatorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY }
    private var radius = 20f * resources.displayMetrics.density
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
            tailX += (headX - tailX) * 0.22f
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
        centerY = y
        placed = true
        invalidate()
    }

    fun slideTo(x: Float, y: Float) {
        if (!placed) {
            placeAt(x, y)
            return
        }
        centerY = y
        animator?.cancel()
        animator = ValueAnimator.ofFloat(headX, x).apply {
            duration = 450
            interpolator = OvershootInterpolator(2.2f)
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
        val left = min(headX, tailX)
        val right = max(headX, tailX)
        canvas.drawRoundRect(left, centerY - radius, right, centerY + radius, radius, radius, paint)
        canvas.drawCircle(headX, centerY, radius, paint)
        canvas.drawCircle(tailX, centerY, radius, paint)
    }
}
