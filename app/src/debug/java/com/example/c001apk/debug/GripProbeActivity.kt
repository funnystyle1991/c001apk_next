package com.example.c001apk.debug

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale

/**
 * 荣耀「随心握」接口探测页（只存在于 debug 包里）。
 * 目标：确认 MagicOS 把握姿状态以什么形式暴露给三方 App ——
 * vendor sensor、还是 hwextdevice 系统服务，以及左右手对应的数值编码。
 */
class GripProbeActivity : Activity(), SensorEventListener {

    private val tag = "GripProbe"
    private val buf = StringBuilder()
    private lateinit var out: TextView
    private lateinit var scroller: ScrollView
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val registered = ArrayList<Sensor>()
    private var startedAt = 0L

    private val sensorKeywords = listOf("honor", "huawei", "posture", "grip", "hold", "hand", "motion")
    private val classCandidates = listOf(
        "com.hihonor.android.hwextdevice.HWExtDeviceManager",
        "com.hihonor.android.hwextdevice.HWExtDeviceEvent",
        "com.hihonor.android.hwextdevice.HWExtDeviceEventListener",
        "com.hihonor.android.hwextdevice.devices.HWExtMotion",
        "com.hihonor.android.hwextdevice.devices.IHWExtDevice",
        "com.hihonor.smartgripkit.SmartGripEventManager",
        "com.hihonor.smartgripkit.SmartGripEventListener",
        "com.hihonor.android.os.SystemPropertiesEx",
        "com.hihonor.android.os.BuildEx"
    )
    private val sensorStringCandidates = listOf(
        "com.hihonor.hardware.sensor.posture",
        "com.huawei.hardware.sensor.posture",
        "com.hihonor.hardware.sensor.grip",
        "com.hihonor.hardware.sensor.motion"
    )
    private val serviceCandidates = listOf(
        "hwextdevice", "hihonor_hwextdevice", "hwext", "motion",
        "msc.systemserver.motion.smart_grip", "smart_grip"
    )
    private val settingsKeys = listOf(
        "smart_grip", "smart_grip_switch", "smart_grip_enable", "hihonor_smart_grip",
        "smart_grip_state", "motion_smart_grip", "grip_mode", "magic_grip",
        "onehand_mode", "haptic.lockscreen.onehand_keyboard_switch", "posture_mode"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startedAt = System.currentTimeMillis()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(buttonRow(
            "全部探测" to { probeAll() },
            "传感器" to { probeSensors() },
            "反射" to { probeClasses() },
            "属性/设置" to { probeProperties() }
        ))
        root.addView(buttonRow(
            "全部传感器清单" to { dumpAllSensors() },
            "复制结果" to { copyResult() },
            "清空" to { buf.setLength(0); out.text = "" }
        ))

        out = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        // ScrollView 里不能用 weight（高度会算成 0），直接 wrap_content 让它撑开
        root.addView(out)

        scroller = ScrollView(this).apply {
            addView(root, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        setContentView(scroller)

        log("设备 ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        log("Android ${android.os.Build.VERSION.RELEASE} (sdk ${android.os.Build.VERSION.SDK_INT})")
        log("Build.DISPLAY = ${android.os.Build.DISPLAY}")
        log("点「全部探测」开始")
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

    private fun probeAll() {
        probeSensors()
        probeClasses()
        probeProperties()
    }

    // ---------- 1. vendor sensor 路线（最省事：公开 API） ----------

    private fun probeSensors() {
        section("传感器枚举")
        val all = sensorManager.getSensorList(Sensor.TYPE_ALL)
        log("传感器总数 ${all.size}")
        val hits = all.filter { s ->
            val hay = "${s.name} ${s.stringType} ${s.vendor}".lowercase(Locale.US)
            sensorKeywords.any { hay.contains(it) }
        }
        if (hits.isEmpty()) {
            log("没有命中关键词的传感器")
        } else {
            hits.forEach { s ->
                log("- ${s.name}")
                log("    stringType=${s.stringType} vendor=${s.vendor} type=${s.type} " +
                        "maxRange=${f(s.maximumRange)} delay=${s.maxDelay / 1000f}ms " +
                        "wake=${s.isWakeUpSensor} reportingMode=${s.reportingMode}")
                watch(s)
            }
        }
        // SensorManager 只暴露了 getDefaultSensor(Int)，按 stringType 查得自己扫清单
        sensorStringCandidates.forEach { want ->
            val s = all.firstOrNull { it.stringType == want }
            if (s == null) {
                log("清单里没有 $want")
            } else {
                log("命中 $want = ${s.name}  -> 开始监听")
                watch(s)
            }
        }
        if (registered.isEmpty()) log("未注册任何监听器")
    }

    private fun watch(sensor: Sensor) {
        if (registered.any { it.stringType == sensor.stringType }) return
        val ok = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        log("    registerListener($ok) ${sensor.name}")
        if (ok) registered.add(sensor)
    }

    private fun dumpAllSensors() {
        section("全部传感器清单")
        sensorManager.getSensorList(Sensor.TYPE_ALL).forEach {
            log("${it.type}\t${it.name}\t${it.stringType}\t${it.vendor}")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val dt = (System.currentTimeMillis() - startedAt) / 1000f
        val values = event.values.joinToString(", ") { f(it) }
        line("EVENT t=${f(dt)}s ${event.sensor.name} type=${event.sensor.type} accuracy=${event.accuracy} values=[$values]")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ---------- 2. 反射路线（hwextdevice 系统服务） ----------

    private fun probeClasses() {
        section("反射探测")
        classCandidates.forEach { dumpClass(it) }
        probeServices()
    }

    private fun dumpClass(name: String) {
        val clazz = try {
            Class.forName(name)
        } catch (t: Throwable) {
            log("[缺失] $name -> ${t.javaClass.simpleName}")
            return
        }
        log("[存在] $name  super=${clazz.superclass?.name}")
        clazz.declaredMethods.sortedBy { it.name }.forEach { m -> log("    ${describe(m)}") }
        clazz.declaredFields.sortedBy { it.name }.forEach { fl -> log("    field ${describe(fl)}") }
    }

    private fun describe(m: Method): String =
        m.name + "(" + m.parameterTypes.joinToString(", ") { it.simpleName } + ") : " + m.returnType.simpleName

    private fun describe(f: Field): String {
        val mods = java.lang.reflect.Modifier.toString(f.modifiers)
        val value = if (java.lang.reflect.Modifier.isStatic(f.modifiers) &&
            (f.type.isPrimitive || f.type == String::class.java)
        ) {
            try {
                f.isAccessible = true
                " = " + f.get(null)
            } catch (t: Throwable) {
                " (读取失败)"
            }
        } else ""
        return "$mods ${f.type.simpleName} ${f.name}$value"
    }

    private fun probeServices() {
        val names = LinkedHashSet(serviceCandidates)
        try {
            Class.forName("com.hihonor.android.hwextdevice.HWExtDeviceManager").declaredFields
                .filter { it.type == String::class.java }
                .forEach { fl ->
                    try {
                        fl.isAccessible = true
                        (fl.get(null) as? String)?.let { names.add(it) }
                    } catch (_: Throwable) {
                    }
                }
        } catch (_: Throwable) {
        }
        names.forEach { n ->
            val svc = try {
                getSystemService(n)
            } catch (t: Throwable) {
                null
            }
            log("getSystemService(\"$n\") -> ${svc?.javaClass?.name ?: "null"}")
        }
    }

    // ---------- 3. 系统属性 / Settings ----------

    private fun probeProperties() {
        section("系统属性")
        val sp = try {
            Class.forName("android.os.SystemProperties")
        } catch (t: Throwable) {
            log("SystemProperties 不可用: ${t.javaClass.simpleName}")
            null
        }
        val getter = sp?.declaredMethods?.firstOrNull { it.name == "get" && it.parameterTypes.size == 1 }
        if (getter != null) {
            getter.isAccessible = true
            listOf(
                "ro.build.version.magic", "ro.magic.os.version", "ro.magicos.version",
                "ro.build.honor.smart_grip_version", "ro.honor.smart_grip",
                "ro.config.hw_smart_grip", "persist.sys.smart_grip",
                "ro.product.brand", "ro.product.model", "ro.build.version.emui"
            ).forEach { key ->
                val v = try {
                    getter.invoke(null, key) as? String
                } catch (t: Throwable) {
                    "<异常 ${t.javaClass.simpleName}>"
                }
                if (!v.isNullOrEmpty()) log("$key = $v")
            }
        }
        runGetProp()

        section("Settings")
        settingsKeys.forEach { key ->
            val s = listOf(
                "secure" to Settings.Secure.getString(contentResolver, key),
                "global" to Settings.Global.getString(contentResolver, key),
                "system" to Settings.System.getString(contentResolver, key)
            ).filter { !it.second.isNullOrEmpty() }
            if (s.isEmpty()) log("$key -> 无") else s.forEach { (where, value) -> log("$key [$where] = $value") }
        }
        try {
            contentResolver.query(Settings.Secure.CONTENT_URI, null, null, null, null)?.use { c ->
                log("Settings.Secure 全表可读，行数=${c.count}")
                val idx = c.getColumnIndex("name")
                if (idx >= 0) {
                    while (c.moveToNext()) {
                        val n = c.getString(idx) ?: continue
                        if (n.lowercase(Locale.US).let { it.contains("grip") || it.contains("hand") || it.contains("posture") }) {
                            log("  命中 key: $n = ${c.getString(c.getColumnIndex("value"))}")
                        }
                    }
                }
            } ?: log("Settings.Secure.CONTENT_URI query 返回 null")
        } catch (t: Throwable) {
            log("Settings.Secure 全表枚举失败: ${t.javaClass.name}: ${t.message}")
        }
    }

    private fun runGetProp() {
        try {
            val p = Runtime.getRuntime().exec("getprop")
            p.inputStream.bufferedReader().use { r ->
                val hits = r.lineSequence().filter {
                    val l = it.lowercase(Locale.US)
                    l.contains("grip") || l.contains("posture") || l.contains("magic") || l.contains("hand")
                }.take(80).toList()
                if (hits.isEmpty()) log("getprop 可读，但没有 grip/posture/magic 相关行") else {
                    log("getprop 命中 ${hits.size} 行：")
                    hits.forEach { log("  $it") }
                }
            }
            p.destroy()
        } catch (t: Throwable) {
            log("getprop 执行失败: ${t.javaClass.name}: ${t.message}")
        }
    }

    // ---------- 输出 ----------

    private fun section(title: String) = line("\n===== $title =====")

    private fun line(text: String) {
        buf.append(text).append('\n')
        out.text = buf.toString()
        scroller.post { scroller.fullScroll(android.view.View.FOCUS_DOWN) }
        Log.d(tag, text)
    }

    private fun log(text: String) = line(text)

    private fun f(v: Float) = String.format(Locale.US, "%.2f", v)

    private fun copyResult() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(tag, buf.toString()))
        line("\n[已复制 ${buf.length} 字符到剪贴板]")
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}
