package com.example.c001apk.ui.message

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import android.view.ViewParent
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 消息中心宫格的未读红点，画在 RecyclerView 的 overdraw 层（[onDrawOver]）。
 *
 * 为什么不在 item 布局里放 TextView：4 列宫格之间没有间距，红点要探到图标右上角外面
 * 就必然越界 —— 越界的部分归右边那格的绘制区域管，RecyclerView 后面画的 child 会把它
 * 压掉（clipChildren 只能防裁剪，防不了被覆盖）。放在 [onDrawOver] 里画则：
 * 不进布局测量、不参与裁剪，且在所有格子绘制完之后才落笔，所以永远在最上层。
 *
 * 尺寸和位置跟原来 item 里的红点一致：高 15dp、最小宽 15dp、压在图标右上角外探 8dp。
 *
 * @param unreadCount 按 adapter position 取未读数，<= 0 表示不画
 */
class MessageBadgeDecoration(
    context: Context,
    private val unreadCount: (Int) -> Int
) : RecyclerView.ItemDecoration() {

    private val dm = context.resources.displayMetrics

    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, dm)

    private val badgeHeight = dp(15f)
    private val badgeMinWidth = dp(15f)
    private val badgePadH = dp(4f)

    /** 红点相对图标右上角的偏移：往右上探出去，压住圆角而不是压住图标本身 */
    private val badgeOffsetX = dp(8f)
    private val badgeOffsetY = dp(-4f)

    private val badgeDrawable = ContextCompat.getDrawable(context, R.drawable.badge_red)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9f, dm)
        textAlign = Paint.Align.CENTER
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val drawable = badgeDrawable ?: return

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue

            val count = unreadCount(position)
            if (count <= 0) continue

            // 红点挂在彩色圆图标（logoCover）的右上角，而不是整个格子
            val icon = child.findViewById<View>(R.id.logoCover) ?: continue

            val text = if (count > 99) "99+" else count.toString()
            val width = max(badgeMinWidth, textPaint.measureText(text) + badgePadH * 2)

            val right = child.left + offsetInChild(icon, child, true) + icon.width + badgeOffsetX
            val top = child.top + offsetInChild(icon, child, false) + badgeOffsetY
            val left = right - width
            val bottom = top + badgeHeight

            drawable.setBounds(
                left.roundToInt(),
                top.roundToInt(),
                right.roundToInt(),
                bottom.roundToInt()
            )
            drawable.draw(c)

            val baseline = (top + bottom) / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
            c.drawText(text, (left + right) / 2f, baseline, textPaint)
        }
    }

    /** 取 [view] 相对 [ancestor] 左上角的偏移（item 层级是 itemView → FrameLayout → 图标） */
    private fun offsetInChild(view: View, ancestor: View, horizontal: Boolean): Int {
        var offset = if (horizontal) view.left else view.top
        var parent: ViewParent? = view.parent
        while (parent is View && parent !== ancestor) {
            // 先转成 View 再取属性：ViewParent 和 View 上都有 getParent()，
            // 直接访问交集类型会报 Overload resolution ambiguity
            val p = parent as View
            offset += if (horizontal) p.left else p.top
            parent = p.parent
        }
        return offset
    }
}
