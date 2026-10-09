package com.example.c001apk.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hihonor.smartgripkit.SmartGripEventManager

/**
 * 荣耀 AI 随心握 + 通用传感器兜底：跨组件共享的握持状态。
 *
 * 状态值：GRIP_UNKNOWN=0 / GRIP_LEFT=1 / GRIP_RIGHT=2，为唯一真源，MainActivity 只读它贴底栏，
 * 并透过 [addObserver] 订阅变化即时重贴。
 *
 * 两个数据源，最终都汇入 [currentState]：
 *  1) 荣耀官方 SmartGripKit（仅支持的机型）：公开 SDK 只在「握持状态变化」时回调，没有查询
 *     当前状态的接口，所以作为更精确的信号叠加在传感器之上，变化时才下发。
 *  2) 加速度计兜底（任何安卓机都能读标准传感器）：竖屏握持时手机因手部解剖会有轻微左右倾斜，
 *     体现在加速度 x 重力分量上，据此近似判左右手。它最大的好处是「随时可读当前状态」——
 *     冷启动第一个传感器事件即可定位当前握持手，实现「打开即贴手」，不依赖任何记忆/猜测。
 *
 * 不再落盘记忆上次用手：开局直接测当前倾斜，比「猜上次」更准也更省心（换人/换姿势不会卡旧值）；
 * 荣耀机上 SmartGrip 变化时再校正传感器近似值的误差。非荣耀机型（三星/小米等）也能有基本的
 * 随心握；华为鸿蒙机装不了本 App，与此无关。
 */
object GripStateHolder {
    const val GRIP_UNKNOWN = 0
    const val GRIP_LEFT = 1
    const val GRIP_RIGHT = 2

    @Volatile
    var currentState: Int = GRIP_UNKNOWN
        private set

    private val observers = LinkedHashSet<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sensorManager: SensorManager? = null
    private var accelListener: SensorEventListener? = null
    private var smartGripListener: com.hihonor.smartgripkit.SmartGripEventListener? = null
    private var initialized = false

    /** 进程冷启动调用一次：并行启用传感器兜底 + （支持时）荣耀官方监听。 */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext

        // 1) 通用传感器兜底：任何机器都能跑，开局即出当前握持手
        startSensorFallback(app)

        // 2) 荣耀官方 SmartGrip：支持的机型叠加更精确的变化信号
        val support = try {
            SmartGripEventManager.getSmartGripSupportState(app)
        } catch (t: Throwable) {
            Log.i(TAG, "SmartGrip unavailable: ${t.javaClass.simpleName}")
            return
        }
        if (support != SmartGripEventManager.SMART_GRIP_SUPPORT) {
            Log.i(TAG, "SmartGrip off, supportState=$support")
            return
        }
        smartGripListener = object : com.hihonor.smartgripkit.SmartGripEventListener() {
            override fun onSmartGripEventChanged(state: Int) {
                // 回调来自 binder 线程，回主线程更新共享状态并通知 UI 观察者
                val mapped = when (state) {
                    SmartGripEventManager.GRIP_STATE_LEFT_HAND -> GRIP_LEFT
                    SmartGripEventManager.GRIP_STATE_RIGHT_HAND -> GRIP_RIGHT
                    else -> GRIP_UNKNOWN
                }
                setState(mapped)
                Log.i(TAG, "smartgrip state=$mapped")
            }
        }
        val ok = try {
            SmartGripEventManager.registerSmartGripMotionListener(app, smartGripListener)
        } catch (t: Throwable) {
            Log.e(TAG, "registerSmartGripMotionListener failed", t)
            false
        }
        Log.i(TAG, "SmartGrip registered=$ok")
    }

    /**
     * 传感器兜底：注册加速度计，按当前 x 重力分量判左右手（详见类注释）。
     * 首个事件即在冷启动给出「当前」握持手，实现打开即贴手（无需记忆/猜测）。
     * 死区避免竖直握持时抖动；横屏/接近水平时不强行贴位。
     */
    private fun startSensorFallback(context: Context) {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run {
            Log.i(TAG, "no accelerometer, grip fallback disabled")
            return
        }
        accelListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gx = event.values[0]
                val gy = event.values[1]
                // 仅竖屏姿态（gy 主导）判左右手：横屏时 x 轴被重力主导，逻辑不适用
                if (kotlin.math.abs(gy) <= kotlin.math.abs(gx)) return
                // 接近水平（|gy| 很小）也不强行贴位
                if (gy > -3f) return
                // 右手握持手机略向右倾→gx>0；左手略向左倾→gx<0；死区内保持当前状态
                val hand = when {
                    gx > TILT_THRESHOLD -> GRIP_RIGHT
                    gx < -TILT_THRESHOLD -> GRIP_LEFT
                    else -> currentState
                }
                if (hand != GRIP_UNKNOWN) setState(hand)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensorManager?.registerListener(accelListener, accel, SensorManager.SENSOR_DELAY_UI)
        Log.i(TAG, "sensor fallback started")
    }

    /** 更新状态（去重）并通知 UI 观察者（主线程）。 */
    private fun setState(state: Int) {
        if (state == currentState) return
        currentState = state
        mainHandler.post { observers.forEach { it() } }
    }

    /** 订阅状态变化；立即用当前状态回调一次，保证订阅即贴手。 */
    fun addObserver(observer: () -> Unit) {
        observers.add(observer)
        observer()
    }

    fun removeObserver(observer: () -> Unit) {
        observers.remove(observer)
    }

    private const val TAG = "GripStateHolder"
    // 倾斜死区（m/s^2）：手机基本竖直时 gx 接近 0，超过此阈才判为某只手（约 ≥12° 倾斜）
    private const val TILT_THRESHOLD = 2.0f
}
