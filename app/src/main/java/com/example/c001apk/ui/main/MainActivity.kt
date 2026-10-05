package com.example.c001apk.ui.main

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivityMainBinding
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.ui.home.HomeFragment
import com.example.c001apk.ui.message.MineFragment
import com.example.c001apk.ui.settings.SettingsActivity
import com.example.c001apk.util.ActivityCollector
import com.example.c001apk.util.CookieUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.UpdateChecker
import com.example.liquidglass.LiquidGlassView
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationBarView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(), IOnBottomClickContainer {

    private val viewModel by viewModels<MainViewModel>()
    private val navViewBehavior by lazy { HideBottomViewOnScrollBehavior<LiquidGlassView>() }
    override var controller: IOnBottomClickListener? = null
    private lateinit var navView: NavigationBarView
    private val isLogin by lazy { PrefManager.isLogin }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCollector.addActivity(this)

        navView = binding.bottomNav as NavigationBarView

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // 启动时按开关自动检查本应用更新（每个进程只查一次，重建不重复弹）
        if (!UpdateChecker.checkedThisSession) {
            UpdateChecker.checkedThisSession = true
            checkSelfUpdate()
        }

        if (viewModel.isInit) {
            viewModel.isInit = false
            genData()
            initObserve()
        } else if (CookieUtil.badge != 0) {
            setBadge()
        }

        binding.viewPager.apply {
            offscreenPageLimit = 1
            adapter = object : FragmentStateAdapter(this@MainActivity) {
                override fun getItemCount() = 2
                override fun createFragment(position: Int): Fragment {
                    return when (position) {
                        0 -> HomeFragment()
                        else -> MineFragment()
                    }
                }
            }

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    when (position) {
                        0 -> onBackPressedCallback.isEnabled = false
                        1 -> onBackPressedCallback.isEnabled = true
                    }
                }
            })
            isUserInputEnabled = false
            fixViewPager2Insets(this)
        }

        // 滚动时玻璃罩在动、内容也在动：背景必须逐帧重采样，不然折射看到的是静止旧图
        binding.navGlass.apply {
            enableDynamicBackground = true
            enableShadow = true
            // 仿官方酷安底栏：clear 清水玻璃，只压一层很淡的主题色防"花"。
            // 别开 adaptiveTint——它会按背后内容亮度压暗染色，列表一深整条就黑给你看
            setGlassTint(
                MaterialColors.getColor(
                    this@MainActivity,
                    com.google.android.material.R.attr.colorSurface,
                    0
                ),
                0.25f
            )
            blurAmount = 0.1f
            fixNavGlassInsets(this)
            // 横屏是 ConstraintLayout + NavigationRail，没有 CoordinatorLayout 也就无所谓滚动隐藏行为
            (layoutParams as? CoordinatorLayout.LayoutParams)?.behavior = navViewBehavior
        }

        navView.apply {
            setOnItemSelectedListener {
                when (it.itemId) {
                    R.id.navigation_home -> {
                        if (binding.viewPager.currentItem == 0)
                            controller?.onReturnTop()
                        else
                            binding.viewPager.setCurrentItem(0, true)
                    }

                    R.id.navigation_mine -> {
                        binding.viewPager.setCurrentItem(1, true)
                        if (CookieUtil.badge != 0) {
                            navView.removeBadge(R.id.navigation_mine)
                        }
                    }
                }
                true
            }
            setOnClickListener { /*Do nothing*/ }
            if (this is BottomNavigationView) {
                fixBottomNavigationViewInsets(this)
            }
        }

    }

    private fun initObserve() {
        viewModel.setBadge.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if (it)
                    setBadge()
            }
        }
    }

    /**
     * 启动时的自更新检查：正式版优先，有正式版更新就不再弹 Beta 的。
     *
     * 自建接口一次响应里同时有 stable / beta，两次调用共用 60 秒缓存，不会重复请求。
     */
    private fun checkSelfUpdate() {
        lifecycleScope.launch {
            fun alive() = !isFinishing && !isDestroyed
            if (PrefManager.isCheckUpdateStable) {
                UpdateChecker.fetchUpdate(UpdateChecker.CHANNEL_STABLE)?.let {
                    if (it.isNewer && alive()) {
                        UpdateChecker.showUpdateDialog(this@MainActivity, it, UpdateChecker.CHANNEL_STABLE)
                        return@launch
                    }
                }
            }
            if (PrefManager.isCheckUpdateBeta) {
                UpdateChecker.fetchUpdate(UpdateChecker.CHANNEL_BETA)?.let {
                    if (it.isNewer && alive())
                        UpdateChecker.showUpdateDialog(this@MainActivity, it, UpdateChecker.CHANNEL_BETA)
                }
            }
        }
    }

    private fun genData() {
        viewModel.fetchAppInfo("com.coolapk.market")
    }

    private fun setBadge() {
        val badge = navView.getOrCreateBadge(R.id.navigation_mine)
        badge.number = CookieUtil.badge
        badge.backgroundColor =
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorPrimary,
                0
            )
        badge.badgeTextColor =
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnPrimary,
                0
            )
        badge.badgeGravity = BadgeDrawable.TOP_END
        badge.verticalOffset = 5
        badge.horizontalOffset = 5
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (binding.viewPager.currentItem != 0) {
                this.isEnabled = false
                showNavigationView()
                navView.selectedItemId = navView.menu.getItem(0).itemId
            }
        }
    }

    fun showNavigationView() {
        if (navViewBehavior.isScrolledDown)
            navViewBehavior.slideUp(binding.navGlass, true)
    }

    fun hideNavigationView() {
        if (navViewBehavior.isScrolledUp)
            navViewBehavior.slideDown(binding.navGlass, true)
    }

    // from LibChecker
    /**
     * 覆盖掉 BottomNavigationView 内部的 OnApplyWindowInsetsListener 并避免其被软键盘顶起来。
     * inset 不再垫进底栏内部（那会把胶囊撑成一条黑板），改由玻璃容器的 margin 悬浮让位。
     */
    private fun fixBottomNavigationViewInsets(view: BottomNavigationView) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            view.updatePadding(bottom = 0)
            windowInsets
        }
    }

    private fun fixNavGlassInsets(view: LiquidGlassView) {
        val baseMargin = (10 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val systemBars =
                ViewCompat.getRootWindowInsets(view)
                    ?.getInsets(WindowInsetsCompat.Type.systemBars())
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val bottom = (systemBars?.bottom ?: 0) + baseMargin
                if (lp.bottomMargin != bottom) {
                    lp.bottomMargin = bottom
                    view.layoutParams = lp
                }
            }
            windowInsets
        }
    }

    private fun fixViewPager2Insets(view: ViewPager2) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            /* Do nothing */
            windowInsets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ActivityCollector.removeActivity(this)
    }

    override fun onResume() {
        super.onResume()
        if (!viewModel.isInit && isLogin) {
            with(System.currentTimeMillis()) {
                if (this - viewModel.lastCheck >= 5 * 60 * 1000) {
                    viewModel.lastCheck = this
                    viewModel.onCheckCount()
                }
            }
        }
    }

}