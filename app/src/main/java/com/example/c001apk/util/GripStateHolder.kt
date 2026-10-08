package com.example.c001apk.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 荣耀 AI 随心握：跨组件共享的握持状态。
 *
 * 公开 SDK（com.hihonor.mcs:smartgripkit）只在「握持状态变化」时回调，没有查询当前状态的接口，
 * 所以 App 打开时若手机早已握持、状态没变，SDK 不会主动下发——底栏只能先居中，直到换只手
 * 或系统重估才补发（这就是「打开要等会 / 换只手才行」的根因；荣耀自家接电话场景因为是
 * 「拿起=状态变化」才显得即时）。
 *
 * 折中方案：把「上次已知的握持手」落盘，[init] 在进程冷启动就读回，让 MainActivity 一打开、
 * 底栏还没被 SDK 点亮前就先按上次的手贴过去（体感「打开即触发」），等 SDK 真回调再校正。
 *
 * 状态来源唯一：由 MyApplication 里常驻注册的监听持续 [update]（内存 + 落盘），MainActivity 只
 * 读取 [currentState] 来贴底栏，并通过 [addObserver] 订阅状态变化以即时重贴。
 */
object GripStateHolder {
    /** 最近一次已知握持状态：0=未握持/未知，1=左手，2=右手。开局先用上次落盘的值。 */
    var currentState: Int = 0
        private set

    private const val PREFS_NAME = "grip_state"
    private const val KEY_LAST = "last_grip_state"
    private var prefs: SharedPreferences? = null
    private val observers = LinkedHashSet<() -> Unit>()

    /** 冷启动读回上次落盘的握持手；进程内只初始化一次。 */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            currentState = prefs!!.getInt(KEY_LAST, 0)
            Log.i("GripStateHolder", "init: restored last grip state=${currentState}")
        }
    }

    /** 握持状态变化：更新内存、落盘，并通知所有 UI 观察者重新贴位。 */
    fun update(state: Int) {
        currentState = state
        prefs?.edit()?.putInt(KEY_LAST, state)?.apply()
        observers.forEach { it() }
    }

    fun addObserver(observer: () -> Unit) {
        observers.add(observer)
    }

    fun removeObserver(observer: () -> Unit) {
        observers.remove(observer)
    }
}
