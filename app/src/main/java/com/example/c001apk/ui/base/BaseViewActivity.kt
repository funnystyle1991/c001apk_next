package com.example.c001apk.ui.base

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import com.example.c001apk.R
import com.google.android.material.color.MaterialColors
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.constant.Constants
import com.example.c001apk.databinding.BaseFragmentContainerBinding
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.ThemeUtils
import com.example.c001apk.util.TransitionAnim
import rikka.material.app.MaterialActivity

abstract class BaseViewActivity<VM : BaseAppViewModel> : MaterialActivity() {

    abstract val viewModel: VM
    lateinit var binding: BaseFragmentContainerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BaseFragmentContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyPageBackground()
        // 转场：动画由内容视图播，window 不参与（见 TransitionAnim 顶部注释）
        TransitionAnim.register(this, pageBackground)
        if (TransitionAnim.consumeEnter()) TransitionAnim.playEnter(this)

        getSavedData(savedInstanceState)

        initData()
        initObserve()
        initError()
    }

    override fun onDestroy() {
        TransitionAnim.unregister(this)
        super.onDestroy()
    }

    /** 是否由内容视图自带页面底色；本身就是半透明浮层的页面置 false */
    protected open val pageBackground: Boolean = true

    /**
     * 窗口在 theme 里是透明的（转场缩放 / 滑动时要能看见下层页），所以页面底色得由内容
     * 视图自己带；否则四周透出的是下层窗口甚至桌面。
     */
    private fun applyPageBackground() {
        if (!pageBackground) return
        val content = window.findViewById<View>(android.R.id.content) ?: return
        content.setBackgroundColor(
            MaterialColors.getColor(content, com.google.android.material.R.attr.colorSurface)
        )
    }

    open fun getSavedData(savedInstanceState: Bundle?) {}

    private fun initError() {
        binding.errorLayout.retry.setOnClickListener {
            binding.errorLayout.parent.isVisible = false
            viewModel.activityState.value = LoadingState.Loading
        }
    }

    private fun initObserve() {
        viewModel.activityState.observe(this) {
            when (it) {
                LoadingState.Loading -> {
                    binding.indicator.parent.isIndeterminate = true
                    binding.indicator.parent.isVisible = true
                    fetchData()
                }

                LoadingState.LoadingDone -> {
                    beginTransaction()
                }

                is LoadingState.LoadingError -> {
                    binding.errorMessage.errMsg.apply {
                        text = it.errMsg
                        isVisible = true
                    }
                }

                is LoadingState.LoadingFailed -> {
                    binding.errorLayout.apply {
                        msg.text = it.msg
                        retry.text =
                            if (it.msg == Constants.LOADING_EMPTY) getString(R.string.refresh)
                            else getString(R.string.retry)
                        parent.isVisible = true
                    }
                }
            }
            if (it !is LoadingState.Loading) {
                binding.indicator.parent.apply {
                    isIndeterminate = false
                    isVisible = false
                }
            }
        }
    }

    abstract fun beginTransaction()

    abstract fun fetchData()

    abstract fun initData()

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