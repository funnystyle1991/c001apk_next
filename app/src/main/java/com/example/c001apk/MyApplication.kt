package com.example.c001apk

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import com.example.c001apk.constant.Constants
import com.example.c001apk.ui.others.BugHandlerActivity
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.RiskControlPrompter
import com.example.c001apk.util.SslErrorPrompter
import com.example.c001apk.util.SslVerify
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

        // 图片加载同样走 OkHttp（Mojito 的 Glide 会替换 GlideUrl 加载器），
        // 调试模式下换成不校验证书的客户端；非调试模式传 null = 行为不变
        // 全屏看图（Mojito）此前用裸 Uri 下载、不带任何请求头，会被 image.coolapk.com
        // 的 EdgeOne UA 防盗链拦成 567，点大图黑屏；这里补上和 showIMG 相同的酷安 UA
        GlideImageLoader.headerProvider = { mapOf("User-Agent" to Constants.USER_AGENT) }
        Mojito.initialize(
            GlideImageLoader.with(this, SslVerify.debugImageClientOrNull()),
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

}