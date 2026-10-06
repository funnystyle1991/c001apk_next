package com.example.c001apk.util

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.core.app.ActivityOptionsCompat
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 水平转场动画的统一入口：把「设置 - 外观」里的曲线 / 类型 / 速度翻译成具体动画资源。
 *
 * 之所以要查表而不是直接算：`overridePendingTransition` 与 `ActivityOptions` 只吃
 * **编译期资源 id**，插值器与 duration 又都写死在 XML 里，运行时没法改。所以三个维度的
 * 全部组合由 `tools/gen_transition_anim.py` 预先烘成 `res/anim/exp_*.xml`，这里按当前
 * 选择挑一个。
 *
 * **这是一批实验脚手架**：手感测出来之后，把赢家的曲线与时长固化回正式的
 * `right_in` / `left_out` / `left_in` / `right_out`，本文件、[TransitionAnimTable]、
 * 那批 `exp_*` 资源与生成脚本一并删掉。
 */
object TransitionAnim {

    /** 曲线：M3 线性（匀速，改造前的观感），作为基线对照 */
    const val CURVE_LINEAR = "linear"

    /** 曲线：Material 2 标准 cubic-bezier(0.4, 0, 0.2, 1)：进出同曲线 */
    const val CURVE_M2 = "m2"

    /** 曲线：M3 标准（进入 standard-decelerate / 退出 standard-accelerate） */
    const val CURVE_M3_STANDARD = "std"

    /** 曲线：M3 强调（进入 emphasized-decelerate / 退出 emphasized-accelerate） */
    const val CURVE_M3_EMPHASIZED = "emph"

    /**
     * 曲线：rikkahub 的临界阻尼弹簧近似。
     *
     * rikkahub 那边其实没有曲线也没有时长——是 Compose 默认的
     * `spring(dampingRatio = 1, stiffness = 400)`，渐近收敛，没法用一条贝塞尔等价表达。
     * 这里取形状最接近的 ease-out-cubic（`cubic-bezier(0.33, 1, 0.68, 1)`，退出取反向
     * ease-in-cubic），配 400ms 时覆盖到弹簧 99.7% 的位置。
     */
    const val CURVE_SPRING = "spring"

    /** 类型：水平滑动 */
    const val TYPE_SLIDE = "slide"

    /** 类型：淡入淡出 */
    const val TYPE_FADE = "fade"

    /**
     * 类型：视差滑动（rikkahub 同款）。新页整屏滑入，被压住的旧页同时左移半屏、
     * 缩到 0.7 并淡出；返回时反向归位。注意旧页的缩放作用在整个 window 上，
     * 四周会露出窗口下层（壁纸）——这是单 Activity Compose 里没有的代价。
     */
    const val TYPE_PARALLAX = "parallax"

    /** 类型：无动画 */
    const val TYPE_NONE = "none"

    private val curve: String get() = PrefManager.animCurve

    private val type: String get() = PrefManager.animType

    private val enterDuration: Int get() = PrefManager.animDuration

    /** 兜底档：数组里写了没烘过的组合时退回它，免得转场直接消失 */
    private const val FALLBACK_CURVE = CURVE_M3_EMPHASIZED

    private const val FALLBACK_DURATION = 300

    /** 取某个槽位的动画资源；`none` 类型返回 0（调用方按「无动画」处理） */
    private fun res(slot: String): Int {
        if (type == TYPE_NONE) return 0
        return TransitionAnimTable.resolve("${type}_${curve}_${enterDuration}_$slot")
            .takeIf { it != 0 }
            ?: TransitionAnimTable.resolve("${TYPE_SLIDE}_${FALLBACK_CURVE}_${FALLBACK_DURATION}_$slot")
    }

    // ------------------------------------------------------------------
    // 内容级转场：动画作用在 android.R.id.content 上，window 本身不缩放
    //
    // 为什么不用 window 动画（overridePendingTransition 直接吃 res/anim）：window 缩放时，
    // 窗口四周露出的是**本窗口之外的下一层窗口**（可能是桌面或更早的 Activity），暗色 /
    // 纯黑主题下就是一片黑，改 windowBackground 也救不回来；而且窗口四角是直角，缩放后会
    // 顶到屏幕圆角外。把同一套 res/anim 挪到内容视图上播，四周露出的是**本窗口自己的
    // windowBackground**（就是应用背景），圆角也能自己裁。
    // ------------------------------------------------------------------

    /** 读不到系统屏幕圆角时的兜底圆角（dp） */
    private const val FALLBACK_CORNER_DP = 28f

    /** 退出动画期间标记，避免 finish() 重入再播一次动画 */
    private val exiting = WeakHashMap<Activity, Boolean>()

    /** 是否有一个页面正在等下层页归位（返回时置位，由下层页的 onResume 消费） */
    @Volatile
    private var reenterPending = false

    /**
     * 是否有新页面正在进入。[enterOptions] / [enterOptionsCompat] 都在**旧页侧**调用，
     * 在那里置位、由新窗口的 onCreate 消费——不必给 intent 塞 extra，也不用改逐个调用点。
     */
    @Volatile
    private var enterPending = false

    /** 新窗口侧消费：true 表示这次是「点进去」，要播内容进入动画 */
    fun consumeEnter(): Boolean {
        val pending = enterPending
        enterPending = false
        return pending
    }

    // ------------------------------------------------------------------
    // 页面栈：转场要「两层同时动」，而两层分属两个 window，谁也看不见谁。
    // 所以由基类在 onCreate / onDestroy 登记，这里按进入顺序找回相邻的那一页。
    // ------------------------------------------------------------------

    private val stack = CopyOnWriteArrayList<WeakReference<Activity>>()

    /** 由 BaseActivity / BaseViewActivity 在 onCreate 调用 */
    fun register(activity: Activity) {
        if (stack.none { it.get() === activity }) stack.add(WeakReference(activity))
    }

    /** 由 BaseActivity / BaseViewActivity 在 onDestroy 调用；顺带清掉已回收的僵尸引用 */
    fun unregister(activity: Activity) {
        stack.removeAll { it.get() == null || it.get() === activity }
    }

    /** [activity] 下面那一页（返回时要归位的那层）；找不到或已销毁就返回 null */
    private fun lowerOf(activity: Activity): Activity? {
        val index = stack.indexOfFirst { it.get() === activity }
        if (index <= 0) return null
        val lower = stack[index - 1].get() ?: return null
        return lower.takeIf { !it.isFinishing && !it.isDestroyed }
    }

    /**
     * 打开新页面：**window 不做任何动画**（传 0 而不是 res("rin")），
     * 新页改在 [BaseActivity]/[BaseViewActivity] 的 onCreate 里播内容进入动画。
     */
    fun enterOptions(context: Context): ActivityOptions {
        enterPending = true
        return ActivityOptions.makeCustomAnimation(context, 0, 0)
    }

    /** 同 [enterOptions]，给还在用 androidx 兼容 API 的调用点 */
    @Suppress("DEPRECATION")
    fun enterOptionsCompat(context: Context): ActivityOptionsCompat {
        enterPending = true
        return ActivityOptionsCompat.makeCustomAnimation(context, 0, 0)
    }

    /**
     * 新页内容从右滑入（进入动画），**同一帧**让旧页退场（左移半屏 + 缩到 0.7 + 淡出）。
     *
     * 旧页的退场不能放在点击点播：那时新窗口还没创建（模拟器上要等一秒多），旧页会先
     * 缩没、屏幕空一段，新页才慢慢进来。等新页真正开始播进入动画时再驱动旧页，两层才同时动。
     */
    fun playEnter(activity: Activity) {
        lowerOf(activity)?.let { animateContent(it, res("lout")) }
        animateContent(activity, res("rin"))
    }

    /** 返回时下层页从 0.7 / 半屏外归位（对应 rikkahub 的 pop 动画） */
    fun playReenter(activity: Activity) {
        animateContent(activity, res("lin"))
    }

    /** 返回时下层页 onResume 消费一次，true 表示要播归位动画 */
    fun consumeReenter(): Boolean {
        val pending = reenterPending
        reenterPending = false
        return pending
    }

    /**
     * 关闭当前页：内容右滑出 + 淡出，**动画播完才真正 finish**（window 全程不动）。
     *
     * @return true 表示已经接管收尾，调用方直接 return 即可，不要再自己 finish
     */
    fun startExit(activity: Activity): Boolean {
        val resId = res("rout")
        if (resId == 0 || activity.isFinishing || exiting.containsKey(activity)) return false
        val view = contentView(activity) ?: return false
        val anim = AnimationUtils.loadAnimation(activity, resId) ?: return false

        exiting[activity] = true      // 留在表里直到 Activity 被回收：finish() 重入时直接放行
        applyRoundClip(activity, view)
        // 上层滑出的同一帧就把下层从「半屏外 + 0.7」拉回来。走 onResume 那条路会晚半拍
        // ——下层要等上层动画播完、finish() 生效才 onResume，中间能看到一段静止。
        val lower = lowerOf(activity)
        if (lower != null) animateContent(lower, res("lin")) else reenterPending = true
        // fillAfter：默认动画播完 View 属性会复位，页面会「滑走 → 闪回原位 → 再消失」
        anim.fillAfter = true
        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) = Unit

            override fun onAnimationRepeat(animation: Animation?) = Unit

            override fun onAnimationEnd(animation: Animation?) {
                // 基类的 finish() 会再问一次 startExit()，有标记挡着，走正常 finish；
                // 再补一次 overridePendingTransition(0, 0) 挡掉系统默认动画。
                // 先 finish 再清裁剪：反过来的话窗口销毁前会闪一帧没圆角的画面。
                activity.finish()
                activity.overridePendingTransition(0, 0)
                clearRoundClip(view)
            }
        })
        view.startAnimation(anim)
        return true
    }

    /** 取 android.R.id.content —— 所有 Activity 都有，不用给基类加接口 */
    private fun contentView(activity: Activity): View? =
        activity.window?.decorView?.findViewById(android.R.id.content)

    private fun animateContent(activity: Activity, resId: Int) {
        if (resId == 0) return
        val view = contentView(activity) ?: return
        val anim = AnimationUtils.loadAnimation(activity, resId) ?: return
        // 各槽位的终点都是原位（rin 到 0、lin 回到 1.0），fillAfter 只为了避免收尾那帧
        // 属性复位造成的闪动；旧页 lout 的终点是「半屏外 + 0.7 + 全透明」，返回时由 lin 接着走
        anim.fillAfter = true
        applyRoundClip(activity, view)
        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) = Unit

            override fun onAnimationRepeat(animation: Animation?) = Unit

            override fun onAnimationEnd(animation: Animation?) {
                clearRoundClip(view)
            }
        })
        // 等一帧，确保 content 已经布局完（outline 要用到宽高）
        view.post { view.startAnimation(anim) }
    }

    /**
     * 动画期间把内容裁成圆角，免得缩放时四个直角顶在屏幕圆角外。
     * 只在动画期间开，平时关掉（clipToOutline 常驻会影响性能和阴影）。
     */
    private fun applyRoundClip(activity: Activity, view: View) {
        val radius = screenCornerRadius(activity)
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radius)
            }
        }
        view.clipToOutline = true
    }

    private fun clearRoundClip(view: View) {
        view.clipToOutline = false
        view.outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    /** 优先读系统的屏幕物理圆角，读不到就退回 [FALLBACK_CORNER_DP] */
    private fun screenCornerRadius(activity: Activity): Float {
        val res = activity.resources
        val id = res.getIdentifier("system_screen_rounded_corner_radius", "dimen", "android")
        if (id > 0) {
            val radius = res.getDimension(id)
            if (radius > 0f) return radius
        }
        return FALLBACK_CORNER_DP * res.displayMetrics.density
    }

    /** FragmentTransaction.setCustomAnimations 的四个参数（0 = 无动画） */
    fun fragmentEnter(): Int = res("rin")

    fun fragmentExit(): Int = res("lout")

    fun fragmentPopEnter(): Int = res("lin")

    fun fragmentPopExit(): Int = res("rout")
}
