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
import kotlin.math.abs

/**
 * 荣耀 AI 随心握：跨组件共享的握持状态（荣耀机型专用）。
 *
 * 状态值：GRIP_UNKNOWN=0 / GRIP_LEFT=1 / GRIP_RIGHT=2，为唯一真源，MainActivity 只读它贴底栏。
 *
 * 两个时机，分工明确（针对「荣耀 SmartGrip 只在握持变化时才回调、无查询当前状态接口」的硬限制）：
 *  1) 冷启动加速度计补丁：进程刚起来、用户已握持时不主动下发，故用加速度计读「当前」倾斜即时贴手
 *     （解决「第一次打开不贴手」）。持续监听，直到官方 SmartGrip 首次回调（=用户换手/握持变化，
 *     确认官方已接管）才停止；超时保底防 SmartGrip 迟迟不回调时无限监听。
 *  2) 运行期全靠荣耀官方 SmartGrip：Application 级常驻监听，前后台生效。其首次有效回调即视为接管，
 *     立即停加速度计；之后握持变化全由它校正（换手即正常，与原生体验一致）。
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
    private var probeActive = false
    private var initialized = false

    /** 进程冷启动调用一次：注册荣耀官方常驻监听 + 启动加速度计冷启动补丁（仅荣耀机型）。 */
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

        // 运行期：荣耀官方变化信号（常驻，前后台生效）；首次有效回调即接管并停加速度计
        smartGripListener = object : com.hihonor.smartgripkit.SmartGripEventListener() {
            override fun onSmartGripEventChanged(state: Int) {
                val mapped = when (state) {
                    SmartGripEventManager.GRIP_STATE_LEFT_HAND -> GRIP_LEFT
                    SmartGripEventManager.GRIP_STATE_RIGHT_HAND -> GRIP_RIGHT
                    else -> GRIP_UNKNOWN
                }
                setState(mapped)
                Log.i(TAG, "smartgrip state=$mapped, hand off accel probe")
                stopColdProbe() // 官方已确认接入（用户已换手），交给它，停加速度计补丁
            }
        }
        val ok = try {
            SmartGripEventManager.registerSmartGripMotionListener(app, smartGripListener)
        } catch (t: Throwable) {
            Log.e(TAG, "registerSmartGripMotionListener failed", t)
            false
        }
        Log.i(TAG, "SmartGrip registered=$ok")

        // 冷启动加速度计补丁：开局贴手，直到 SmartGrip 首次回调接管（或超时）才停
        startColdProbe(app)
    }

    /**
     * 冷启动加速度计补丁：竖屏握持时手机因解剖有轻微左右倾斜，体现在加速度 x 分量上，据此近似判左右手。
     * 持续监听（不自我注销）：首个通过死区的有效样本即定位贴手，但继续监听，直到 [stopColdProbe]
     * 被 SmartGrip 首次回调调用（用户换手、官方接管）或 [COLD_PROBE_TIMEOUT_MS] 超时保底才停止。
     * 仅竖屏握持姿态判定（横屏/接近水平不强行贴位）。
     */
    private fun startColdProbe(context: Context) {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run {
            Log.i(TAG, "no accelerometer, cold probe disabled")
            return
        }
        probeActive = true
        accelListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!probeActive) return
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                Log.d(TAG, "accel sample gx=$gx gy=$gy gz=$gz")
                // 仅「大致竖直握持」时判定：手机接近水平/平放时 roll 无意义，放弃。
                // 手机竖直屏幕朝前时 gy≈+9.8（重力沿 -Y 轴），故判定「竖直」应看 |gy| 是否主导，
                // 而非与 -2 比——之前误用 `gy > -2f` 反而把正常竖直姿态全部排除，导致补丁完全失效。
                if (abs(gy) <= abs(gz)) return   // 接近水平/平放，roll 无意义，不强行贴位
                if (abs(gy) < 3f) return         // 不够竖直，放弃判定
                val hand = when {
                    gx > TILT_THRESHOLD -> GRIP_RIGHT
                    gx < -TILT_THRESHOLD -> GRIP_LEFT
                    else -> return // 死区内（握持过正），继续等更明确的样本
                }
                setState(hand)
                Log.i(TAG, "cold probe hand=$hand (gx=$gx)")
                // 注意：不在此注销！等 SmartGrip 首次回调接管（onSmartGripEventChanged）才停
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensorManager?.registerListener(accelListener, accel, SensorManager.SENSOR_DELAY_UI)
        Log.i(TAG, "cold probe started (until SmartGrip handoff)")
        // 超时保底：SmartGrip 迟迟不回调（用户长时间不换手）也停监听，避免长期耗电
        mainHandler.postDelayed({ stopColdProbe() }, COLD_PROBE_TIMEOUT_MS)
    }

    private fun stopColdProbe() {
        if (!probeActive) return
        probeActive = false
        accelListener?.let { sensorManager?.unregisterListener(it) }
        accelListener = null
        Log.i(TAG, "cold probe stopped")
    }

    private fun setState(state: Int) {
        if (state == currentState) return
        currentState = state
        mainHandler.post { observers.forEach { it() } }
    }

    fun addObserver(observer: () -> Unit) {
        observers.add(observer)
        observer()
    }

    fun removeObserver(observer: () -> Unit) {
        observers.remove(observer)
    }

    private const val TAG = "GripStateHolder"
    // 倾斜死区（m/s^2）：手机基本竖直时 gx 接近 0，超过此阈才判为某只手（≈6°，原 2.0≈12° 太钝导致常判不出）
    private const val TILT_THRESHOLD = 1.0f
    // 冷启动探针超时（ms）：超时仍未收到 SmartGrip 接管则放弃监听（保持当前贴位）
    private const val COLD_PROBE_TIMEOUT_MS = 30000L
}
