package com.example.c001apk.ui.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.c001apk.BuildConfig
import androidx.core.content.ContextCompat
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivityMainBinding
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.ui.home.HomeFragment
import com.example.c001apk.ui.message.MineFragment
import com.example.c001apk.ui.settings.SettingsActivity
import com.example.c001apk.util.ActivityCollector
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.UpdateChecker
import com.example.c001apk.view.DragBottomNavigationView
import com.example.c001apk.view.DragNavigationRailView
import com.example.c001apk.view.FrostedGlassDrawable
import com.example.c001apk.view.GlassLensDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationBarView
import com.example.c001apk.util.GripStateHolder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(), IOnBottomClickContainer {

    private val viewModel by viewModels<MainViewModel>()
    override var controller: IOnBottomClickListener? = null
    private lateinit var navView: NavigationBarView
    private val isLogin by lazy { PrefManager.isLogin }
    // 订阅 GripStateHolder 的状态变化，用于即时重贴底栏；在 onCreate 添加、onDestroy 移除，避免泄漏 Activity
    private var gripObserver: (() -> Unit)? = null
    private var lensAnim: ValueAnimator? = null

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

        // 底部导航不再挂未读角标：那个位置的历史语义是「消息」，现在消息已独立成
        // MessageCenterActivity，未读提示统一走消息中心宫格（见 MessageThirdAdapter）。
        if (viewModel.isInit) {
            viewModel.isInit = false
            genData()
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

        // v12：滚动自动隐藏整个撤掉——官方底栏从不因滚动收起，条一藏"透明玻璃"的感觉
        // 反而没了。navGlassHost 不再挂 HideBottomViewOnScrollBehavior，永远停在底部

        // 玻璃不再靠库实时采样（这台机器上库的捕获录不满录制区），改成每 50ms 把条后面的
        // 内容用 viewPager.draw 直接录进缩小的位图，放大回去即模糊：单个 View 直录，
        // 没有库那套多层捕获的错位问题。
        // v12 真机判定"不行"+全分辨率对比官方：v12 把条做"太透"了——白页上整条隐形，
        // 因为官方那条能看见靠的是①一圈柔和投影把物体从页面上"抬"出来 ②条后内容被糊成
        // 色块（清晰文字直透=没有玻璃）。v13：霜层几乎全不透明（只留 5% 直透）+ 采样退回 /8
        // 换真糊 + 外壳挂低透明度投影轮廓（outline.alpha 压低，不再重演 v10 的 20% 黑纱）
        val nightMode = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val glassTint = ContextCompat.getColor(this, R.color.nav_glass_tint)
        val glassStroke = ContextCompat.getColor(this, R.color.nav_glass_stroke)
        // v20 对齐官方白天（用户截图实测）：条身 FCFCFC≈不透明白、滴 E2E2E2 浅灰。
        // v23：0xF2 染色把霜压得太死，真机判定"通透度差了点"——染色退到 0xCC，
        // 让背后内容的色晕多透出一档；霜本身仍 0xF2 全糊，不会回到"清晰文字直透"
        val barAlpha = 0xCC
        val radiusPx = 28f * resources.displayMetrics.density
        binding.navGlass.background = FrostedGlassDrawable().apply {
            radius = radiusPx
            tintColor = glassTint and 0x00FFFFFF or (barAlpha shl 24)
            strokeColor = glassStroke
            // 0xF2：霜层几乎盖满，条后内容只以"糊掉的色块"出现——官方白天就是这档
            frostAlpha = 0xF2
        }
        // 滴 = 放大镜：官方水滴不是染色胶囊，是把滴后的内容放大 ~1.15 倍折射出来再亮一圈边。
        // 霜位图由 copyFrost 每周期喂进来，滑动时 offsetX 同步跟位置
        binding.navLens.background = GlassLensDrawable().apply {
            radius = radiusPx
            rimColor = glassStroke
            // 官方实测滴是实色片不是透明折射：白天 E2E2E2、深色 2E3032（和条近同色，
            // 靠亮边区分）。0x4D 轻染色会让 /3 清晰图 95% 直透——深色模式下就是抖动+发花。
            // v23：和条一样退到 0xCC，透出被放大折射的内容
            tintColor = if (nightMode) (0xCC shl 24) or 0x002E3032
                        else (0xCC shl 24) or 0x00E2E2E2
            magnify = 1.15f
            // 滴和条同糊度：v18 真机判定"左右 tab 通透度不一样"，实测滴内 F4F9FD、
            // 条身 E9EFED——滴原来把背后内容原样直透，糊度对齐条就匀了
            frostAlpha = 0xF2
        }
        // Z 序与阴影：bottomNav/navLensHost 仍全部 outlineProvider=null（v10 的 ambient
        // shadow 灰纱坑，滴是透明的更压不住）。投影改由外壳 navGlassHost 一家来投：
        // 8dp + 圆角轮廓 + outline.alpha 压到 0.3，影子的"量"可控，铺进条内那点也被
        // 0xF2 霜层盖住，条外那圈就是官方把玻璃"抬"出来的柔和落影
        val elevPx = resources.displayMetrics.density
        binding.bottomNav.elevation = 3f * elevPx
        binding.bottomNav.outlineProvider = null
        binding.navLensHost.elevation = 2f * elevPx
        binding.navLensHost.outlineProvider = null
        binding.navGlassHost.elevation = 8f * elevPx
        binding.navGlassHost.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) outline.alpha = 0.3f
            }
        }
        // 调试包启动报代号+commit：真机截图一眼能确认装的是哪一轮的玻璃，避免拿旧包判新代码
        if (BuildConfig.DEV_CHANNEL) {
            Toast.makeText(this, "glass-v23-clear ${BuildConfig.GIT_SHA}", Toast.LENGTH_LONG).show()
        }
        // v19：横屏滴不再藏——Rail 上改成上下滑，和竖屏同一套液滴动画（slideLens 走纵向分支）

        // 官方底栏的液态手感：按住滴能直接拖着走，松手吸附到最近的 tab。
        // 手势由 bottomNav 拦截（它压在滴上方），这里只负责把坐标变成动画
        (binding.bottomNav as? DragBottomNavigationView)?.apply {
            onDragMove = { dragLensTo(it) }
            onDragEnd = { settleLensTo(it) }
            onDragCancel = {
                settleLensTo(binding.navLensHost.translationX + binding.navLensHost.width / 2f)
            }
        }
        // v23：横屏轨道同款拖拽，轴换成 Y（DragNavigationRailView 只报坐标，落位仍在 Activity）
        (binding.bottomNav as? DragNavigationRailView)?.apply {
            onDragMove = { dragLensToY(it) }
            onDragEnd = { settleLensToY(it) }
            onDragCancel = {
                settleLensToY(binding.navLensHost.translationY + binding.navLensHost.height / 2f)
            }
        }

        navView.apply {
            setOnItemSelectedListener {
                when (it.itemId) {
                    R.id.navigation_home -> {
                        slideIndicator(0)
                        slideLens(0)
                        if (binding.viewPager.currentItem == 0)
                            controller?.onReturnTop()
                        else
                            binding.viewPager.setCurrentItem(0, true)
                    }

                    R.id.navigation_mine -> {
                        slideIndicator(1)
                        slideLens(1)
                        binding.viewPager.setCurrentItem(1, true)
                    }
                }
                true
            }
            setOnClickListener { /*Do nothing*/ }
            if (this is BottomNavigationView) {
                fixBottomNavigationViewInsets(this)
            } else {
                fixNavigationRailInsets(this)
            }
        }

        // 液态选中气泡：官方底栏没有实色泡，只有玻璃镜片——用 20% 品牌青绿当镜片底色
        binding.navIndicator.gooColor =
            MaterialColors.getColor(this, R.color.nav_goo, 0x33009487)
        binding.navGlass.post {
            if (navView is BottomNavigationView) {
                navItemCenter(0)?.let { (x, y) ->
                    binding.navIndicator.placeAt(x, y)
                    placeLensAt(x)
                }
            } else {
                railItemCenterY(0)?.let { placeLensAtY(it) }
            }
            // 底栏测量完即按当前握持状态贴位（开局 SDK 初始回调若已到，这里直接生效）
            applyGrip(false)
        }
        // 布局驱动兜底：底栏每次完成测量/重布局（含首帧、旋转、安全区变化）都按当前
        // 握持状态贴位。回调再早也不丢，打开即生效（状态来自 GripStateHolder，详见其注释）
        binding.navGlassHost.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyGrip(false) }
        // 订阅握持状态变化：GripStateHolder（传感器兜底 + 荣耀官方信号）更新后，这里即时重贴
        // 底栏（换只手等"状态变化"场景立刻生效）。开局由传感器兜底按当前倾斜预贴位，由上方 post
        // / 布局监听器在底栏测量完成后 applyGrip(false) 完成。
        gripObserver = { applyGrip(true) }
        GripStateHolder.addObserver(gripObserver!!)
    }

    /**
     * 按当前握持状态 [GripStateHolder.currentState] 把底栏整条平移到对应一侧。
     *
     * 状态唯一来源是 GripStateHolder（传感器兜底 + 荣耀官方信号共同维护，冷启动即按当前
     * 倾斜贴手，不再依赖记忆上次用手），不再用一次性 state 参数——只要 currentState 被更新，
     * 下一次 applyGrip / 布局监听 / observer 都会把底栏贴到正确位置，绝不丢回调。
     */
    private fun applyGrip(animated: Boolean) {
        val bar = binding.navGlassHost
        val screenWidth = (bar.parent as? View)?.width ?: return
        val marginStart = (bar.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart?.toFloat() ?: return
        if (bar.width == 0 || screenWidth == 0) return
        // 居中时左右留白相等，靠到某一侧就是把外侧那份留白让出来
        val max = (screenWidth - bar.width) / 2f - marginStart
        val target = when (GripStateHolder.currentState) {
            GripStateHolder.GRIP_LEFT -> -max
            GripStateHolder.GRIP_RIGHT -> max
            else -> 0f
        }
        if (bar.translationX == target) return
        if (animated) {
            bar.animate().translationX(target).setDuration(300)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
        } else {
            bar.translationX = target
        }
    }

    private fun placeLensAt(cx: Float) {
        val lens = binding.navLensHost
        lens.scaleX = 1f
        lens.scaleY = 1f
        lens.translationX = clampLensX(cx - lens.width / 2f, 1f)
        syncLensOffset()
    }

    /**
     * 按当前横向拉伸量钳制平移：滴的可视边缘（含缩放外扩）不许越出条的两端。
     * 出界那块采不到背景会渲染成黑，就是"边缘缺一块"的另一种成因。
     */
    private fun clampLensX(x: Float, scaleX: Float): Float {
        val lens = binding.navLensHost
        val barW = binding.navGlassHost.width.toFloat()
        val w = lens.width.toFloat()
        if (barW == 0f || w == 0f) return x
        val bulge = (scaleX - 1f) * w / 2f
        val minX = bulge
        val maxX = barW - w - bulge
        return if (minX <= maxX) x.coerceIn(minX, maxX) else (barW - w) / 2f
    }

    /**
     * 滴滑动：下面整套是库自己 LiquidBottomTabs 玻璃滴的算法，不再自己发明。
     * 只动 translationX / scaleX / scaleY：改 layoutParams 会 requestLayout，
     * FrameLayout 每次 layout 都按 gravity 把滴的 x 重置回左缘，动画就废了（真机翻车点）。
     */
    private fun slideLens(index: Int) {
        val lens = binding.navLensHost
        if (lens.visibility != View.VISIBLE) return
        if (navView !is BottomNavigationView) {
            slideLensVertical(index)
            return
        }
        val target = navItemCenter(index)?.first ?: return
        val baseW = lens.width.toFloat()
        if (baseW == 0f) return
        val from = lens.translationX + baseW / 2f
        val dist = target - from
        lensAnim?.cancel()
        if (abs(dist) < 0.5f) {
            placeLensAt(target)
            return
        }
        // 跨得越远拉得越长（上限 35%），纵向等体积压缩；sin 让鼓胀中间最大、两头归零。
        // 库的滴按 tab 宽 3 倍归一，我们只有 2 个 tab、行程短，照抄的话鼓胀只有 17%，
        // 在深色条上根本看不出来，所以这里按"行程/滴宽"归一
        val stretch = 0.35f * (abs(dist) / baseW).coerceAtMost(1f)
        val overshoot = OvershootInterpolator(1.1f)
        lensAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            addUpdateListener {
                val t = it.animatedValue as Float
                val s = sin(PI.toFloat() * min(t * 1.15f, 1f))
                val sx = 1f + stretch * s
                lens.scaleX = sx
                lens.scaleY = 1f - stretch * 0.55f * s
                lens.translationX =
                    clampLensX(from + dist * overshoot.getInterpolation(t) - baseW / 2f, sx)
                syncLensOffset()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var canceled = false
                override fun onAnimationCancel(animation: Animator) {
                    canceled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    // cancel() 也会走到这里：中途重定向时不能落位，
                    // 否则滴瞬移到新目标、新动画又因距离为 0 而根本不播
                    if (!canceled) placeLensAt(target)
                }
            })
            start()
        }
    }

    /**
     * 横屏 Rail 的 cell 不是均分条高的（menuGravity=center 悬空、cell 高≈rail 宽），
     * 竖屏那套 barW*(i+0.5)/n 公式套不上，只能读实际布局出来的 cell 子 View。
     *
     * 但不能按 getChildAt(0) 去猜菜单容器：material 1.14 起 NavigationRailView 把菜单
     * 包进了 contentContainer（还可能再套一层 ScrollView），第 0 个子 View 变成了那个
     * 容器，childCount 对不上就返回 null，调用方 `?: return` 静默退出——横屏表现为
     * 滴一直停在卡片顶部不动、上下拖松手永远不切页。这里改成逐层找「子项数等于菜单项数、
     * 且每个子项都是导航条目」的 ViewGroup，层级怎么加都不怕。
     *
     * 中心点换算改用 getLocationInWindow 相对玻璃外壳取差值，不再假设「rail 顶缘 == 外壳顶缘
     * 且菜单容器是它的直接子 View」。
     */
    private fun railItemCenterY(index: Int): Float? {
        val bar = binding.navGlassHost
        val menu = findRailMenuContainer() ?: return null
        val item = menu.getChildAt(index) ?: return null
        if (item.height == 0 || bar.height == 0) return null
        val itemLoc = IntArray(2).also { item.getLocationInWindow(it) }
        val barLoc = IntArray(2).also { bar.getLocationInWindow(it) }
        return itemLoc[1] + item.height / 2f - barLoc[1]
    }

    /** 深度优先找 rail 的菜单容器：子项数与菜单一致，且每个子项都带 material 的条目图标 id */
    private fun findRailMenuContainer(): ViewGroup? {
        val count = navView.menu.size()
        if (count == 0) return null
        // 项目开了 android.nonTransitiveRClass，material 的资源只在它自己的 R 里，
        // 写 R.id.xxx 编不过，必须点名 com.google.android.material.R
        val itemIconId = com.google.android.material.R.id.navigation_bar_item_icon_view
        val stack = ArrayDeque<View>()
        stack.addLast(navView)
        while (stack.isNotEmpty()) {
            val group = stack.removeLast() as ViewGroup
            if (group !== navView) {
                val childrenAreItems = (0 until group.childCount).all { i ->
                    val child = group.getChildAt(i) as? ViewGroup
                    child != null && child.findViewById<View>(itemIconId) != null
                }
                if (group.childCount == count && childrenAreItems) return group
            }
            for (i in 0 until group.childCount) stack.addLast(group.getChildAt(i))
        }
        return null
    }

    /** 纵向落位：和 placeLensAt 同款，只是动 translationY */
    private fun placeLensAtY(cy: Float) {
        val lens = binding.navLensHost
        lens.scaleX = 1f
        lens.scaleY = 1f
        lens.translationY = clampLensY(cy - lens.height / 2f, 1f)
        syncLensOffset()
    }

    private fun clampLensY(y: Float, scaleY: Float): Float {
        val lens = binding.navLensHost
        val barH = binding.navGlassHost.height.toFloat()
        val h = lens.height.toFloat()
        if (barH == 0f || h == 0f) return y
        val bulge = (scaleY - 1f) * h / 2f
        val minY = bulge
        val maxY = barH - h - bulge
        return if (minY <= maxY) y.coerceIn(minY, maxY) else (barH - h) / 2f
    }

    /** 横屏滴上下滑：鼓胀改沿纵向（scaleY 拉长、scaleX 等体积压窄），其余同竖屏那套 */
    private fun slideLensVertical(index: Int) {
        val lens = binding.navLensHost
        val target = railItemCenterY(index) ?: return
        val baseH = lens.height.toFloat()
        if (baseH == 0f) return
        val from = lens.translationY + baseH / 2f
        val dist = target - from
        lensAnim?.cancel()
        if (abs(dist) < 0.5f) {
            placeLensAtY(target)
            return
        }
        val stretch = 0.35f * (abs(dist) / baseH).coerceAtMost(1f)
        val overshoot = OvershootInterpolator(1.1f)
        lensAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            addUpdateListener {
                val t = it.animatedValue as Float
                val s = sin(PI.toFloat() * min(t * 1.15f, 1f))
                val sy = 1f + stretch * s
                lens.scaleY = sy
                lens.scaleX = 1f - stretch * 0.55f * s
                lens.translationY =
                    clampLensY(from + dist * overshoot.getInterpolation(t) - baseH / 2f, sy)
                syncLensOffset()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var canceled = false
                override fun onAnimationCancel(animation: Animator) {
                    canceled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!canceled) placeLensAtY(target)
                }
            })
            start()
        }
    }

    /** 按住滴拖：鼓起一点跟手，气泡同点跟随，两者是一套液体 */
    private fun dragLensTo(x: Float) {
        val lens = binding.navLensHost
        if (lens.visibility != View.VISIBLE) return
        lensAnim?.cancel()
        lens.scaleX = 1.06f
        lens.scaleY = 1.06f
        lens.translationX = clampLensX(x - lens.width / 2f, 1.06f)
        syncLensOffset()
        binding.navIndicator.dragTo(x)
    }

    /** 松手吸附到最近的 tab；走 selectedItemId 就和点击完全同一条路径（切页/角标都跟着） */
    private fun settleLensTo(x: Float) {
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (i in 0 until navView.menu.size()) {
            val center = navItemCenter(i)?.first ?: return
            val d = abs(center - x)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        selectSettledTab(best)
    }

    /** 横屏滴上下拖：dragLensTo 的纵向镜像，鼓胀只沿拖拽轴（纵向拉长、横向等体积压窄） */
    private fun dragLensToY(y: Float) {
        val lens = binding.navLensHost
        if (lens.visibility != View.VISIBLE) return
        lensAnim?.cancel()
        // 横屏卡片和滴同宽（都是 84dp），横向再放大 1.06 就是左右各溢出 2.5dp，
        // 滴会探出玻璃卡片外缘——所以横向按 slideLensVertical 同款等体积收窄
        lens.scaleX = 1f - 0.06f * 0.55f
        lens.scaleY = 1.06f
        lens.translationY = clampLensY(y - lens.height / 2f, 1.06f)
        syncLensOffset()
    }

    /** 松手吸附到纵向最近的 tab cell（railItemCenterY 读实际布局，不套均分公式） */
    private fun settleLensToY(y: Float) {
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (i in 0 until navView.menu.size()) {
            val center = railItemCenterY(i) ?: return
            val d = abs(center - y)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        if (best < 0) return
        selectSettledTab(best)
    }

    private fun selectSettledTab(best: Int) {
        val id = navView.menu.getItem(best).itemId
        if (navView.selectedItemId != id) {
            navView.selectedItemId = id
        } else {
            slideIndicator(best)
            slideLens(best)
        }
    }

    /**
     * 启动时的自更新检查（只看正式版，Beta 通道已去掉）。
     *
     * 自建接口一次响应里还有关于页按钮那段，跟这里共用 60 秒缓存，不会重复请求。
     */
    private fun checkSelfUpdate() {
        if (!PrefManager.isCheckUpdateStable) return
        lifecycleScope.launch {
            val info = UpdateChecker.fetchUpdate() ?: return@launch
            if (info.isNewer && !isFinishing && !isDestroyed)
                UpdateChecker.showUpdateDialog(this@MainActivity, info)
        }
    }

    private fun genData() {
        viewModel.fetchAppInfo("com.coolapk.market")
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

    // v12：官方底栏不随滚动收起，自动隐藏撤掉。这些方法仍有十余处 Fragment 滚动回调
    // 调用点，保留签名改成空操作
    fun showNavigationView() = Unit

    fun hideNavigationView() = Unit

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

    /**
     * 横屏 rail 同款坑同款补法：v17 只删了 XML 属性，Material 的 NavigationRailView 还留着
     * 自己注册的内部 inset 监听，把横屏状态栏高度垫成顶部 padding——像素实测整组 tab 下坠
     * ~50dp（上间隙 62dp vs 下间隙 23dp）。悬浮胶囊四周都不该吃系统窗 inset，直接清零。
     */
    private fun fixNavigationRailInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            view.updatePadding(left = 0, top = 0, right = 0, bottom = 0)
            windowInsets
        }
    }

    /**
     * tab 中心（相对 navGlass）。不再从 NavigationBarView 的内部 View 树里取：
     * 那条路要 getChildAt(0) 正好是菜单容器才拿得到，一旦拿不到就是 null，
     * 调用方 `?: return` 静默退出，真机上表现成"点一下直接跳过去、没有滑动也没有拉伸"。
     * 两个 tab 等宽分布，几何中心 barW×(i+0.5)/n 与实测位置一致，且与 Material 版本无关。
     */
    private fun navItemCenter(index: Int): Pair<Float, Float>? {
        val bar = binding.navGlass
        if (navView !is BottomNavigationView) return null
        if (bar.width == 0 || bar.height == 0) return null
        val count = navView.menu.size().coerceAtLeast(1).toFloat()
        return (bar.width * (index + 0.5f) / count) to bar.height / 2f
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
        frostRunning = false
        Choreographer.getInstance().removeFrameCallback(frostFrameCallback)
        // 移除底栏贴位观察者，避免泄漏 Activity；随心握监听在 Application 级常驻，不在此解注册
        gripObserver?.let { GripStateHolder.removeObserver(it) }
        gripObserver = null
        ActivityCollector.removeActivity(this)
    }

    override fun onResume() {
        super.onResume()
        frostRunning = true
        Choreographer.getInstance().postFrameCallback(frostFrameCallback)
        if (!viewModel.isInit && isLogin) {
            with(System.currentTimeMillis()) {
                if (this - viewModel.lastCheck >= 5 * 60 * 1000) {
                    viewModel.lastCheck = this
                    viewModel.onCheckCount()
                }
            }
        }
        // 荣耀随心握：回到前台按当前状态（GripStateHolder，由传感器兜底实时测得）补贴一次，
        // 覆盖"初始传感器事件晚于布局 / 后台期间状态变化"的情况。监听已在 Application 级常驻，无需在此注册。
        binding.navGlass.post { applyGrip(false) }
    }

    override fun onPause() {
        super.onPause()
        frostRunning = false
        Choreographer.getInstance().removeFrameCallback(frostFrameCallback)
    }

    // 霜玻璃：周期把条后面的内容录进小位图，放大模糊交给背景 Drawable
    private var frostBmp: Bitmap? = null
    private var lensFrostBmp: Bitmap? = null
    // v16：官方顺是因为它每帧在 GPU 上直接采样已合成画面（零拷贝、和 vsync 同相）；
    // 我们只能在主线程把内容重画进小位图。50ms Handler 和 vsync 不同相，刷新间隔
    // 忽长忽短——残余"卡顿感"正是这个。改成 Choreographer 每个 vsync 采一帧，
    // 并按实测耗时自适应降帧（>6ms 跳一帧≈30fps），保证不跟滚动抢主线程
    private var frostRunning = false
    private var frostSkipNext = false
    private val frostFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frostRunning) return
            if (frostSkipNext) {
                frostSkipNext = false
            } else {
                val t0 = SystemClock.elapsedRealtimeNanos()
                copyFrost()
                if (SystemClock.elapsedRealtimeNanos() - t0 > 6_000_000) frostSkipNext = true
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun copyFrost() {
        val host = binding.navGlassHost
        val w = host.width
        val h = host.height
        if (w < 16 || h < 16) return
        val loc = IntArray(2)
        host.getLocationInWindow(loc)
        val srcLoc = IntArray(2)
        binding.viewPager.getLocationInWindow(srcLoc)
        // /8 + 霜 0xF2：官方条后是"糊成色块"不是"清晰直透"，v12 的 /4 太锐了
        val dw = (w / 8).coerceAtLeast(4)
        val dh = (h / 8).coerceAtLeast(4)
        var bmp = frostBmp
        if (bmp == null || bmp.width != dw || bmp.height != dh) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
            frostBmp = bmp
        }
        captureStrip(bmp, w, h, loc, srcLoc)
        (binding.navGlass.background as? FrostedGlassDrawable)?.setBitmap(bmp)
        // v14：滴单独 /3 高清采样。官方的滴比条"更清楚"（折射把内容放大且保留细节），
        // v13 滴复用条的 /8 糊图放大 1.15x，渲成一坨白浆——就是"没啥变化"的观感来源
        val lw = (w / 3).coerceAtLeast(8)
        val lh = (h / 3).coerceAtLeast(8)
        var lbmp = lensFrostBmp
        if (lbmp == null || lbmp.width != lw || lbmp.height != lh) {
            lbmp?.recycle()
            lbmp = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
            lensFrostBmp = lbmp
        }
        captureStrip(lbmp, w, h, loc, srcLoc)
        (binding.navLens.background as? GlassLensDrawable)?.apply {
            hostWidthPx = w.toFloat()
            hostHeightPx = h.toFloat()
            vertical = navView !is BottomNavigationView
            offsetX = binding.navLensHost.x
            offsetY = binding.navLensHost.y
            setBitmap(lbmp)
        }
    }

    private fun captureStrip(bmp: Bitmap, w: Int, h: Int, loc: IntArray, srcLoc: IntArray) {
        val canvas = Canvas(bmp)
        canvas.scale(bmp.width.toFloat() / w, bmp.height.toFloat() / h)
        canvas.translate((srcLoc[0] - loc[0]).toFloat(), (srcLoc[1] - loc[1]).toFloat())
        binding.viewPager.draw(canvas)
    }

    /** 霜位图 50ms 一刷，滴滑动/拖拽时每帧还要把自己在条内的位置报给放大镜（比刷新更频繁） */
    private fun syncLensOffset() {
        val lens = binding.navLensHost
        (binding.navLens.background as? GlassLensDrawable)?.apply {
            if (vertical) offsetY = lens.y else offsetX = lens.x
        }
    }

}