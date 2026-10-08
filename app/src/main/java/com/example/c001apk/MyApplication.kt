package com.example.c001apk

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.example.c001apk.constant.Constants
import com.example.c001apk.ui.others.BugHandlerActivity
import com.example.c001apk.util.GripStateHolder
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.RemoteConfig
import com.example.c001apk.util.RiskControlPrompter
import com.example.c001apk.util.SslErrorPrompter
import com.example.c001apk.util.SslVerify
import com.hihonor.smartgripkit.SmartGripEventManager
import com.hihonor.smartgripkit.SmartGripEventListener
import dagger.hilt.android.HiltAndroidApp
import net.mikaelzero.mojito.Mojito
import net.mikaelzero.mojito.loader.glide.GlideImageLoader
import net.mikaelzero.mojito.view.sketch.SketchImageLoadFactory
import net.mikaelzero.mojito.view.sketch.core.Sketch
import kotlin.system.exitProcess

@HiltAndroidApp
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        context = applicationContext

        // 老用户一次性切到官方酷安配色（关跟随系统取色 + 主题色回默认）
        PrefManager.migrateOfficialPalette()

        // SSL 校验失败 → 风险环境警告弹窗（跟踪前台 Activity）
        SslErrorPrompter.install(this)

        // 风控命中（-415 账号过多 / err_request_captcha_v2）→ 「设备标识未配置」引导弹窗
        RiskControlPrompter.install(this)

        // 网络传输调试模式（设置 - 高级）：放开进程级 HttpsURLConnection 默认校验，
        // 不放开的话抓包时走系统默认栈的图片会全部加载失败
        SslVerify.applyDebugGlobally()

        AppCompatDelegate.setDefaultNightMode(PrefManager.darkTheme)

        // 匿名统计用的用户随机 ID（useradomid）：第一次启动就生成并落盘，之后不再变化。
        // 读取本身就会生成，这里显式读一次只是为了把生成时机钉在「首次启动」，
        // 顺带保证自更新接口第一次打请求时就已经有值（见 PrefManager.userRandomId）
        PrefManager.userRandomId

        // 云端配置（用户认证表 / 可信链接白名单）：先用上次缓存，再后台刷新一次。
        // 放在这里而不是各页面里拉，是因为认证标散落在列表、详情、消息各处，
        // 每处各拉一次没必要，而且首次进入时未必已经有表。
        RemoteConfig.initFromCache()
        RemoteConfig.refreshAsync()

        // 荣耀 AI 随心握：进程级常驻监听，冷启动即开始感测（比 Activity 早）。
        // 状态变化持续写入 GripStateHolder（内存 + 落盘），供 MainActivity 贴底栏，
        // 也供下次冷启动按"上次用手"预贴位。公开 SDK 只在状态变化时回调、无查询当前状态
        // 的接口，所以开局已握持时不会主动下发——预贴上次的手是唯一能让"打开即触发"的折中。
        initGripFollow(this)

        // 图片加载同样走 OkHttp（Mojito 的 Glide 会替换 GlideUrl 加载器）。
        // 这个客户端必须给：图片 CDN 按 UA 放行，没 UA 会被判 567 直接黑屏，
        // SslVerify.imageClient() 会补上酷安 UA（并在调试模式下放开证书校验）
        Mojito.initialize(
            GlideImageLoader.with(this, SslVerify.imageClient()),
            SketchImageLoadFactory()
        )

        // 全屏看图器里的图是交给 Sketch 显示的（列表图片走 Glide），所以 Sketch 只有点开大图时才会
        // 第一次被用到。Sketch.with() 是进程级单例，首次调用会在当前线程同步构造 Configuration：
        // 建磁盘缓存、位图池、内存池、线程池，还要扫一遍缓存目录、读一次 PackageManager 元数据，
        // 在主线程上就是几百毫秒 —— 表现正好是第一次点开图片会卡/闪一下，退出去再进来就好了
        // （那时单例已经建好）。这里冷启动时后台预热，把这份一次性开销从点开的瞬间挪走。
        Thread {
            runCatching { Sketch.with(this) }.onFailure {
                android.util.Log.w("MyApplication", "Sketch 预热失败", it)
            }
        }.start()

        Thread.setDefaultUncaughtExceptionHandler { _, paramThrowable ->
            val exceptionMessage = android.util.Log.getStackTraceString(paramThrowable)

            val intent = Intent(this, BugHandlerActivity::class.java)
            intent.putExtra("exception_message", exceptionMessage)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)

            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    /**
     * 荣耀 AI 随心握：在 Application 级常驻注册监听，让感测尽量早开始（冷启动即注册，
     * 早于任何 Activity），状态变化持续写入 [GripStateHolder]（内存 + 落盘），MainActivity
     * 读取它来贴底栏、并订阅变化即时重贴。
     *
     * 非荣耀机型 getSmartGripSupportState 会抛异常或返回非 SUPPORT，按 Throwable 兜住即可，
     * 兜住后底栏保持居中，不影响其它任何功能。监听常驻进程级、用 applicationContext 注册，无泄漏。
     */
    private fun initGripFollow(context: Context) {
        GripStateHolder.init(context)
        val support = try {
            SmartGripEventManager.getSmartGripSupportState(context)
        } catch (t: Throwable) {
            Log.i("MyApplication", "grip follow unavailable: ${t.javaClass.simpleName}")
            return
        }
        if (support != SmartGripEventManager.SMART_GRIP_SUPPORT) {
            Log.i("MyApplication", "grip follow off, supportState=$support")
            return
        }
        val mainHandler = Handler(Looper.getMainLooper())
        val listener = object : SmartGripEventListener() {
            override fun onSmartGripEventChanged(state: Int) {
                // 回调来自 binder 线程，回主线程更新共享状态（含落盘 + 通知 UI 观察者）
                mainHandler.post {
                    GripStateHolder.update(state)
                    Log.i("MyApplication", "grip event state=$state")
                }
            }
        }
        val ok = try {
            SmartGripEventManager.registerSmartGripMotionListener(context, listener)
        } catch (t: Throwable) {
            Log.e("MyApplication", "registerSmartGripMotionListener failed", t)
            false
        }
        Log.i("MyApplication", "grip follow registered=$ok")
    }

}