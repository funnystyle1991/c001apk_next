package com.example.c001apk.ui.main

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
import com.hihonor.smartgripkit.SmartGripEventListener
import com.hihonor.smartgripkit.SmartGripEventManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(), IOnBottomClickContainer {

    private val viewModel by viewModels<MainViewModel>()
    private val navViewBehavior by lazy { HideBottomViewOnScrollBehavior<LiquidGlassView>() }
    override var controller: IOnBottomClickListener? = null
    private lateinit var navView: NavigationBarView
    private val isLogin by lazy { PrefManager.isLogin }
    private var gripListener: SmartGripEventListener? = null

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
            // 库的阴影会给胶囊描出一圈轮廓"矩形框"，官方没有这圈东西——关掉
            enableShadow = false
            // 仿官方酷安底栏：clear 清水玻璃 + 很淡的主题色染色防"花"。
            // 别开 adaptiveTint——它会按背后内容亮度压暗染色，列表一深整条就黑给你看
            setGlassTint(
                MaterialColors.getColor(
                    this@MainActivity,
                    com.google.android.material.R.attr.colorSurface,
                    0
                ),
                0.25f
            )
            blurAmount = 0.15f
            // 液态气泡感：库默认的尺寸自适应会把小控件的折射/斜面钳到几乎为零
            // （参考线 110dp，我们才 56dp），所以关掉它手动给值——但值必须按 56dp 短边来定：
            // 斜面 ≤ 短边×0.3（≈17dp）、折射 ≤ 短边×0.7（≈39dp），给大了整条玻璃
            // 都变成边缘透镜，背后的内容会被像放大镜一样拉伸变形（上一版 64/130 就翻车了）
            adaptiveLensScale = false
            bevelWidth = 14f
            refractionHeight = 28f
            edgeSoftness = 4f
            dispersionStrength = 0.25f
            aberrationIntensity = 2f
            // 横屏是 ConstraintLayout + NavigationRail，没有 CoordinatorLayout 也就无所谓滚动隐藏行为
            (layoutParams as? CoordinatorLayout.LayoutParams)?.behavior = navViewBehavior
        }

        navView.apply {
            setOnItemSelectedListener {
                when (it.itemId) {
                    R.id.navigation_home -> {
                        slideIndicator(0)
                        if (binding.viewPager.currentItem == 0)
                            controller?.onReturnTop()
                        else
                            binding.viewPager.setCurrentItem(0, true)
                    }

                    R.id.navigation_mine -> {
                        slideIndicator(1)
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

        // 液态选中气泡：颜色跟 Material  SecondaryContainer 走，初始放在首页图标下
        binding.navIndicator.gooColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSecondaryContainer,
            0xFFE0E0E0.toInt()
        )
        binding.navGlass.post {
            navItemCenter(0)?.let { (x, y) -> binding.navIndicator.placeAt(x, y) }
        }

        registerGripFollow()
    }

    /**
     * 荣耀随心握：单手握持时把底栏整条靠向那只手，双手/平放回到正中。
     *
     * SDK 内部要碰荣耀框架的隐藏类，非荣耀机型连静态初始化都过不去，
     * 所以每个入口都按 Throwable 兜住——兜住就是底栏一直居中，不影响任何人。
     */
    private fun registerGripFollow() {
        // 横屏是竖排 NavigationRail，往左右靠没有意义
        if (navView !is BottomNavigationView) return
        val support = try {
            SmartGripEventManager.getSmartGripSupportState(this)
        } catch (t: Throwable) {
            Log.i("MainActivity", "grip follow unavailable: ${t.javaClass.simpleName}")
            return
        }
        if (support != SmartGripEventManager.SMART_GRIP_SUPPORT) {
            Log.i("MainActivity", "grip follow off, supportState=$support")
            return
        }
        val listener = object : SmartGripEventListener() {
            override fun onSmartGripEventChanged(state: Int) {
                // 回调来自 binder 线程，改 View 得回主线程
                runOnUiThread { shiftBarToGrip(state) }
            }
        }
        val ok = try {
            gripListener = listener
            SmartGripEventManager.registerSmartGripMotionListener(this, listener)
        } catch (t: Throwable) {
            Log.e("MainActivity", "registerSmartGripMotionListener failed", t)
            gripListener = null
            false
        }
        Log.i("MainActivity", "grip follow registered=$ok")
    }

    private fun shiftBarToGrip(state: Int) {
        val bar = binding.navGlass
        val screenWidth = (bar.parent as? View)?.width ?: return
        val marginStart = (bar.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart?.toFloat() ?: return
        if (bar.width == 0 || screenWidth == 0) return
        // 居中时左右留白相等，靠到某一侧就是把外侧那份留白让出来
        val max = (screenWidth - bar.width) / 2f - marginStart
        val target = when (state) {
            SmartGripEventManager.GRIP_STATE_LEFT_HAND -> -max
            SmartGripEventManager.GRIP_STATE_RIGHT_HAND -> max
            else -> 0f
        }
        if (bar.translationX == target) return
        bar.animate().translationX(target).setDuration(300)
            .setInterpolator(DecelerateInterpolator(1.6f)).start()
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

    private fun navItemCenter(index: Int): Pair<Float, Float>? {
        // 横屏是竖排 NavigationRail，水平气泡不适用
        val nav = navView as? BottomNavigationView ?: return null
        // nav 的第一个子 View 是菜单容器（NavigationBarMenuView，继承 ViewGroup）
        val menu = nav.getChildAt(0) as? ViewGroup ?: return null
        val item = menu.getChildAt(index) ?: return null
        return (nav.x + menu.x + item.x + item.width / 2f) to
                (nav.y + menu.y + item.y + item.height / 2f)
    }

    private fun slideIndicator(index: Int) {
        navItemCenter(index)?.let { (x, y) -> binding.navIndicator.slideTo(x, y) }
    }

    private fun fixViewPager2Insets(view: ViewPager2) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            /* Do nothing */
            windowInsets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gripListener?.let { listener ->
            try {
                SmartGripEventManager.unregisterSmartGripMotionListener(this, listener)
            } catch (t: Throwable) {
                Log.e("MainActivity", "unregisterSmartGripMotionListener failed", t)
            }
        }
        gripListener = null
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