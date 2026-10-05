package com.example.c001apk.debug

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.hihonor.smartgripkit.SmartGripEventListener
import com.hihonor.smartgripkit.SmartGripEventManager
import java.util.Locale

/**
 * 荣耀「随心握」接口验证页（只存在于 debug 包）。
 * 直接用官方 SmartGripKit（com.hihonor.mcs:smartgripkit）跑一遍：
 * 支持态、开关、注册是否成功、以及真实握姿回调的数值。
 */
class GripProbeActivity : Activity() {

    private val tag = "GripProbe"
    private val buf = StringBuilder()
    private lateinit var out: TextView
    private lateinit var scroller: ScrollView
    private var startedAt = 0L
    private var events = 0

    private val listener = object : SmartGripEventListener() {
        override fun onSmartGripEventChanged(state: Int) {
            events++
            line("EVENT #$events t=${f(elapsed())}s state=$state (${stateName(state)})")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startedAt = System.currentTimeMillis()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(buttonRow(
            "支持状态" to { probeSupport() },
            "注册监听" to { probeRegister(true) },
            "取消注册" to { probeRegister(false) }
        ))
        root.addView(buttonRow(
            "传感器清单" to { probeSensors() },
            "复制结果" to { copyResult() },
            "清空" to { buf.setLength(0); out.text = "" }
        ))

        out = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        // ScrollView 里不能用 weight（高度会算成 0）
        root.addView(out)

        scroller = ScrollView(this).apply {
            addView(root, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        setContentView(scroller)

        line("设备 ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        line("Android ${android.os.Build.VERSION.RELEASE} (sdk ${android.os.Build.VERSION.SDK_INT})")
        line("Build.DISPLAY = ${android.os.Build.DISPLAY}")
        line("顺序：支持状态 -> 注册监听 -> 然后换着手握（左/右/双手/平放），每种停几秒")
    }

    private fun buttonRow(vararg items: Pair<String, () -> Unit>) =
        HorizontalScrollView(this).apply {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            items.forEach { (label, action) ->
                row.addView(Button(context).apply {
                    text = label
                    setAllCaps(false)
                    setOnClickListener { action() }
                })
            }
            addView(row)
        }

    // ---------- 支持状态 ----------

    private fun probeSupport() {
        section("支持状态")
        val switch = try {
            Settings.Secure.getInt(contentResolver, "key_smart_grip_switch", -999)
        } catch (t: Throwable) {
            "读取失败 ${t.javaClass.simpleName}"
        }
        line("Settings.Secure[key_smart_grip_switch] = $switch  (0=关闭 1=开启)")
        line("prop msc.systemserver.motion.smart_grip = ${readIntProp("msc.systemserver.motion.smart_grip")}")

        val state = try {
            SmartGripEventManager.getSmartGripSupportState(this)
        } catch (t: Throwable) {
            line("getSmartGripSupportState 抛异常: ${t.javaClass.name}: ${t.message}")
            if (t.cause != null) line("  cause: ${t.cause}")
            return
        }
        line("getSmartGripSupportState = $state (${supportName(state)})")
    }

    private fun readIntProp(key: String): String = try {
        val sp = Class.forName("android.os.SystemProperties")
        val m = sp.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
        m.invoke(null, key, -999).toString()
    } catch (t: Throwable) {
        "读取失败 ${t.javaClass.simpleName}"
    }

    // ---------- 注册 / 事件 ----------

    private fun probeRegister(register: Boolean) {
        section(if (register) "注册监听" else "取消注册")
        val ok = try {
            if (register) {
                SmartGripEventManager.registerSmartGripMotionListener(this, listener)
            } else {
                SmartGripEventManager.unregisterSmartGripMotionListener(this, listener)
            }
        } catch (t: Throwable) {
            line("调用抛异常: ${t.javaClass.name}: ${t.message}")
            if (t.cause != null) line("  cause: ${t.cause}")
            return
        }
        line("register=$register 返回 $ok")
        if (register && ok) line("现在换着手握，观察下面的 EVENT 行")
    }

    // ---------- 交叉验证：厂商传感器是否也可见 ----------

    private fun probeSensors() {
        section("传感器清单（交叉验证）")
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val all = sm.getSensorList(Sensor.TYPE_ALL)
        line("共 ${all.size} 个")
        all.filter {
            val hay = "${it.name} ${it.stringType} ${it.vendor}".lowercase(Locale.US)
            listOf("honor", "huawei", "posture", "grip", "hold", "hand", "motion").any { k -> hay.contains(k) }
        }.forEach {
            line("- ${it.name} | type=${it.stringType} | vendor=${it.vendor}")
        }
    }

    // ---------- 输出 ----------

    private fun stateName(state: Int) = when (state) {
        SmartGripEventManager.GRIP_STATE_NOT_HELD -> "未握持"
        SmartGripEventManager.GRIP_STATE_LEFT_HAND -> "左手"
        SmartGripEventManager.GRIP_STATE_RIGHT_HAND -> "右手"
        SmartGripEventManager.GRIP_STATE_BOTH_HANDS -> "双手"
        SmartGripEventManager.GRIP_STATE_UNKNOWN -> "未识别"
        else -> "未知值"
    }

    private fun supportName(code: Int) = when (code) {
        SmartGripEventManager.SMART_GRIP_SUPPORT -> "支持"
        SmartGripEventManager.SMART_GRIP_NOT_SUPPORT -> "设备不支持"
        SmartGripEventManager.SMART_GRIP_SETTING_OFF -> "开关已关闭"
        SmartGripEventManager.SMART_GRIP_NO_PERMISSION -> "没有权限"
        SmartGripEventManager.SMART_GRIP_REGISTER_FAILED_OTHER -> "其它失败"
        else -> "未知值"
    }

    private fun section(title: String) = line("\n===== $title =====")

    private fun line(text: String) {
        Log.d(tag, text)
        runOnUiThread {
            buf.append(text).append('\n')
            out.text = buf.toString()
            scroller.post { scroller.fullScroll(android.view.View.FOCUS_DOWN) }
        }
    }

    private fun elapsed() = (System.currentTimeMillis() - startedAt) / 1000f

    private fun f(v: Float) = String.format(Locale.US, "%.1f", v)

    private fun copyResult() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(tag, buf.toString()))
        line("\n[已复制 ${buf.length} 字符到剪贴板]")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            SmartGripEventManager.unregisterSmartGripMotionListener(this, listener)
        } catch (_: Throwable) {
        }
    }
}
