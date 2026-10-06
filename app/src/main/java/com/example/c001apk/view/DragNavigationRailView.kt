package com.example.c001apk.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.google.android.material.navigationrail.NavigationRailView
import kotlin.math.abs

/**
 * 横屏轨道可拖拽版：和 DragBottomNavigationView 同一套契约，只是轴换成纵向——
 * 按住滴上下拖，松手吸附到最近的 tab（竖屏手感搬到横屏）。
 * 没超过 slop 的照常走 NavigationRailView 的点击。
 */
class DragNavigationRailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : NavigationRailView(context, attrs, defStyleAttr) {

    var onDragMove: ((y: Float) -> Unit)? = null
    var onDragEnd: ((y: Float) -> Unit)? = null
    var onDragCancel: (() -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> if (!dragging && abs(event.y - downY) > slop) {
                dragging = true
                onDragMove?.invoke(event.y)
            }
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> onDragMove?.invoke(event.y)

            MotionEvent.ACTION_UP -> {
                dragging = false
                onDragEnd?.invoke(event.y)
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                onDragCancel?.invoke()
            }
        }
        return true
    }
}
