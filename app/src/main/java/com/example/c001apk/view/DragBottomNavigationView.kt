package com.example.c001apk.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlin.math.abs

/**
 * 底栏可拖拽版：按住那枚玻璃滴左右拖，松手吸附到最近的 tab（官方酷安的手感）。
 *
 * 这里只负责"什么时候算拖拽"和把手指坐标报出去，落位/切换都交给 Activity——
 * 动画状态全在一处，不会出现两边各持一份互相打架。
 * 没超过 slop 的照常走 BottomNavigationView 的点击，角标/染色逻辑不受影响。
 */
class DragBottomNavigationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BottomNavigationView(context, attrs, defStyleAttr) {

    var onDragMove: ((x: Float) -> Unit)? = null
    var onDragEnd: ((x: Float) -> Unit)? = null
    var onDragCancel: (() -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> if (!dragging && abs(event.x - downX) > slop) {
                dragging = true
                onDragMove?.invoke(event.x)
            }
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> onDragMove?.invoke(event.x)

            MotionEvent.ACTION_UP -> {
                dragging = false
                onDragEnd?.invoke(event.x)
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                onDragCancel?.invoke()
            }
        }
        return true
    }
}
