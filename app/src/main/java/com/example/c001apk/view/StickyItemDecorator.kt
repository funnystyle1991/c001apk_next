package com.example.c001apk.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.util.dp
import com.google.android.material.color.MaterialColors

class StickyItemDecorator(
    context: Context,
    private val space: Int,
    private val itemCountProvider: () -> Int,
    private val listener: SortShowListener
) :
    RecyclerView.ItemDecoration() {

    /**
     * 内容区（含 header）的项数，也就是评论区头的 adapter position。
     *
     * 必须是动态读取：图文详情会先用列表项直出首屏（此时内容区只有 1 项，itemCount=2），
     * 详情回来后才展开成 N 个图文碎片。如果在构造时把值取下来，字段会一直停在预览期的 2，
     * 于是 onDrawOver 一进页面就判定 index>=itemCount 而提前显示排序 tab，
     * getItemOffsets 也只会给 position<=2 加左右 15dp，图文碎片全部顶到屏幕边缘。
     */
    private val itemCount: Int
        get() = itemCountProvider()

    private var mPaint: Paint = Paint()

    init {
        mPaint.isAntiAlias = true
        mPaint.color =
            MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSurfaceVariant,
                0
            )
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(c, parent, state)
        val childCount = parent.childCount
        for (i in 0 until childCount) {
            val view = parent.getChildAt(i)
            val index = parent.getChildAdapterPosition(view)
            if (index == itemCount - 1) {
                val dividerTop = view.bottom.toFloat() + 12.dp
                val dividerLeft = parent.paddingLeft
                val dividerBottom = view.bottom + 12.dp + 1
                val dividerRight = parent.width - parent.paddingRight
                c.drawRect(
                    dividerLeft.toFloat(), dividerTop, dividerRight.toFloat(),
                    dividerBottom.toFloat(), mPaint
                )
            } else if (index > itemCount) {
                val dividerTop = view.top.toFloat() - 1
                val dividerLeft = parent.paddingLeft
                val dividerBottom = view.top
                val dividerRight = parent.width - parent.paddingRight
                c.drawRect(
                    dividerLeft.toFloat(), dividerTop, dividerRight.toFloat(),
                    dividerBottom.toFloat(), mPaint
                )
            }
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)
        if ((parent.adapter?.itemCount ?: 0) <= 0)
            return
        val index =
            (parent.layoutManager as LinearLayoutManager?)?.findFirstVisibleItemPosition() ?: 0
        if (index >= itemCount) {
            listener.showSort(true)
        } else {
            listener.showSort(false)
        }
    }

    interface SortShowListener {
        fun showSort(show: Boolean)
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position <= itemCount) {
            if (position < itemCount)
                outRect.bottom =
                    if (position == itemCount - 1) 12.dp + 1
                    else 12.dp
            outRect.left = 15.dp
            outRect.right = 15.dp
        }
        if (position > itemCount)
            outRect.top = 1
    }

}