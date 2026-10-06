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

        // 图片加载同样走 OkHttp（Mojito 的 Glide 会替换 GlideUrl 加载器），
        // 调试模式下换成不校验证书的客户端；非调试模式传 null = 行为不变
        // 全屏看图（Mojito）此前用裸 Uri 下载、不带任何请求头，会被 image.coolapk.com
        // 的 EdgeOne UA 防盗链拦成 567，点大图黑屏；这里补上和 showIMG 相同的酷安 UA
        GlideImageLoader.headerProvider = { mapOf("User-Agent" to Constants.USER_AGENT) }
        Mojito.initialize(
            GlideImageLoader.with(this, SslVerify.debugImageClientOrNull()),
            SketchImageLoadFactory()
        )

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