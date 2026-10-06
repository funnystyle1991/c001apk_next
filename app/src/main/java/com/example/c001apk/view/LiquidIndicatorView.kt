package com.example.c001apk.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
 * 仿官方酷安底栏的液态选中气泡：头部圆先弹向目标，尾部稍慢跟上进而形成拉丝。
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

    fun placeAt(x: Float, y: Float) {
        headX = x
        tailX = x
        centerY = if (height > 0) height / 2f else y
        placed = true
        invalidate()
    }

    /** 手指拖拽时跟手：头尾重合在一点，不做动画（动画会把手指位置抢走） */
    fun dragTo(x: Float) {
        if (!placed) placeAt(x, centerY)
        animator?.cancel()
        headX = x
        tailX = x
        invalidate()
    }

    /**
     * 头尾都由这一个动画的进度算出来（不再用单独回调追尾巴）：
     * 之前那种写法只要丢一帧 repost，尾巴就永远停在旧位置，
     * 底栏里会残留一条横贯的粗杠。这里进度到 1 时头尾必然重合。
     */
    fun slideTo(x: Float, y: Float) {
        if (!placed) {
            placeAt(x, y)
            return
        }
        centerY = if (height > 0) height / 2f else y
        val from = headX
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                val eased = it.animatedValue as Float
                headX = from + (x - from) * eased
                // 尾部走未过冲的线性进度、跑得更快，所以头回弹时它已经到位
                val chase = (it.animatedFraction * 1.3f).coerceAtMost(1f)
                tailX = from + (x - from) * chase
                val lag = headX - tailX
                if (abs(lag) > maxLag) tailX = headX - (if (lag > 0) maxLag else -maxLag)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 兜底：取消/中断也绝不允许留下拉伸状态
                    tailX = headX
                    invalidate()
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
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
