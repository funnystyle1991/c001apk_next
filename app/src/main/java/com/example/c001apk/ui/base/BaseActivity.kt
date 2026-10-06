package com.example.c001apk.ui.base

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.viewbinding.ViewBinding
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.ThemeUtils
import com.example.c001apk.util.TransitionAnim
import rikka.material.app.MaterialActivity
import java.lang.reflect.ParameterizedType

abstract class BaseActivity<VB : ViewBinding> : MaterialActivity() {

    lateinit var binding: VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = javaClass.genericSuperclass as ParameterizedType
        val aClass = type.actualTypeArguments[0] as Class<*>
        val method = aClass.getDeclaredMethod("inflate", LayoutInflater::class.java)
        @Suppress("UNCHECKED_CAST")
        binding = method.invoke(null, layoutInflater) as VB
        setContentView(binding.root)
        // 转场：动画由内容视图播，window 不参与（见 TransitionAnim 顶部注释）
        if (TransitionAnim.consumeEnter()) TransitionAnim.playEnter(this)
    }

    override fun onResume() {
        super.onResume()
        // 从下级页返回时，本页内容从 rikkahub 的「退半屏 + 缩到 0.7」状态归位
        if (TransitionAnim.consumeReenter()) TransitionAnim.playReenter(this)
    }

    override fun attachBaseContext(newBase: Context) {
        val configuration = newBase.resources.configuration
        configuration.fontScale = PrefManager.FONTSCALE.toFloat()
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun computeUserThemeKey() =
        ThemeUtils.colorTheme + ThemeUtils.getNightThemeStyleRes(this)

    override fun onApplyTranslucentSystemBars() {
        super.onApplyTranslucentSystemBars()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    override fun onApplyUserThemeResource(theme: Resources.Theme, isDecorView: Boolean) {
        if (!ThemeUtils.isSystemAccent)
            theme.applyStyle(ThemeUtils.colorThemeStyleRes, true)
        theme.applyStyle(ThemeUtils.getNightThemeStyleRes(this), true) //blackDarkMode
    }

    override fun finish() {
        // 内容先滑出，动画结束后 TransitionAnim 会再调一次 finish()，届时标记挡住重入
        if (TransitionAnim.startExit(this)) return
        super.finish()
    }

}
