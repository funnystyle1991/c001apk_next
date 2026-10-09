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
 * 荣耀 AI 随心握：跨组件共享的握持状态（荣耀机型专用）。
 *
 * 状态值：GRIP_UNKNOWN=0 / GRIP_LEFT=1 / GRIP_RIGHT=2，为唯一真源，MainActivity 只读它贴底栏，
 * 并透过 [addObserver] 订阅变化即时重贴。
 *
 * 两个时机，分工明确：
 *  1) 冷启动一次性定位（加速度计）：荣耀 SmartGrip 公开 SDK 只在「握持状态变化」时回调、没有查询
 *     当前状态的接口，所以进程刚起来、用户已经握持时不会主动下发。为此在冷启动用一次加速度计
 *     读「当前」倾斜，首个通过死区的有效样本即定位握持手、实现「打开即贴手」，随后立即注销
 *     加速度计（只此一次，不持续监听、不污染后续、不耗电）。超时未拿到有效样本（如手机平放）则放弃。
 *  2) 运行期全靠荣耀官方 SmartGrip：监听在 Application 级常驻，进程不被杀则前后台都生效——app 内
 *     操作或后台切回前台，握持有变即回调校正；无变化底栏本就正确，无需动作。无「查询当前状态」接口，
 *     故不主动轮询。
 *
 * 仅荣耀机型：非荣耀机 getSmartGripSupportState 非 SUPPORT，直接不启用任何握持逻辑（底栏居中），
 * 不再做通用传感器兜底（按需求只服务荣耀用户）。
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
    private var coldProbeDone = false
    private var initialized = false

    /** 进程冷启动调用一次：注册荣耀官方常驻监听 + 用加速度计做一次冷启动定位（仅荣耀机型）。 */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext

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

        // 运行期：荣耀官方变化信号（常驻，前后台都生效）
        smartGripListener = object : com.hihonor.smartgripkit.SmartGripEventListener() {
            override fun onSmartGripEventChanged(state: Int) {
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

        // 冷启动一次性定位：补「已握持但 SDK 不主动下发」的空窗，拿到即注销加速度计
        startColdStartProbe(app)
    }

    /**
     * 冷启动一次性倾斜探针：仅取首个通过姿态校验 + 死区的有效握持手样本，定位后即注销加速度计，
     * 不再持续监听（运行期交给 SmartGrip）。超时 [COLD_PROBE_TIMEOUT_MS] 仍无有效样本则放弃（居中）。
     * 仅竖屏握持姿态判定（横屏/接近水平不强行贴位）。
     */
    private fun startColdStartProbe(context: Context) {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run {
            Log.i(TAG, "no accelerometer, cold probe disabled")
            return
        }
        accelListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (coldProbeDone) return
                val gx = event.values[0]
                val gy = event.values[1]
                // 仅竖屏姿态（gy 主导且非接近水平）判定；否则继续等下一个样本
                if (kotlin.math.abs(gy) <= kotlin.math.abs(gx)) return
                if (gy > -3f) return
                val hand = when {
                    gx > TILT_THRESHOLD -> GRIP_RIGHT
                    gx < -TILT_THRESHOLD -> GRIP_LEFT
                    else -> return  // 死区内，等更明确的样本
                }
                coldProbeDone = true
                setState(hand)
                stopColdProbe()
                Log.i(TAG, "cold probe hand=$hand")
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensorManager?.registerListener(accelListener, accel, SensorManager.SENSOR_DELAY_UI)
        Log.i(TAG, "cold probe started")
        // 超时保底：未拿到有效样本则放弃，避免加速度计空转
        mainHandler.postDelayed({ stopColdProbe() }, COLD_PROBE_TIMEOUT_MS)
    }

    private fun stopColdProbe() {
        accelListener?.let { sensorManager?.unregisterListener(it) }
        accelListener = null
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
    // 冷启动探针超时（ms）：超时仍未拿到有效握持样本则放弃，保持居中
    private const val COLD_PROBE_TIMEOUT_MS = 1500L
}
