package com.example.c001apk.util

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.doOnNextLayout
import com.google.android.material.color.MaterialColors
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
     * 缩到 0.7 并淡出；返回时反向归位。旧页缩掉、淡掉的那一圈由 [refreshBackdrops]
     * 垫上的页面底色兜住，不会透出桌面。
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
    // 两套转场通道，由 [useWindowAnim] 分流
    //
    // A. window 级（slide / none）：资源直接交给系统，动画作用在窗口 surface 上。
    //    纯位移时两个窗口位移恒互补、严丝合缝，不会露出任何缝隙；且由**系统合成器**驱动，
    //    应用主线程再卡也不影响它逐帧推进。
    // B. 内容级（fade / parallax）：动画作用在 android.R.id.content 上，window 不参与。
    //
    // 为什么还需要 B：window 一旦缩放，窗口四周露出的是**本窗口之外的下一层窗口**
    // （可能是桌面或更早的 Activity），暗色 / 纯黑主题下就是一片黑，改 windowBackground
    // 也救不回来；窗口四角又是直角，缩放后会顶到屏幕圆角外。把动画挪到内容视图上播，
    // 四周露出的是**本窗口自己的 windowBackground**（[refreshBackdrops] 垫的页面底色），
    // 圆角也能自己裁。
    //
    // B 的固有代价：它依赖应用自己的绘制回调，重页面首帧卡顿会把动画帧整个吞掉
    // （详见 [useWindowAnim]）。所以**纯位移一律留给 A**，别为了「统一实现」挪进 B。
    // ------------------------------------------------------------------

    /** 读不到系统屏幕圆角时的兜底圆角（dp） */
    private const val FALLBACK_CORNER_DP = 28f

    /** 布局回调没来时的兜底帧数：逐帧等到有尺寸为止，最多等这么多帧就强制执行 */
    private const val MAX_FALLBACK_FRAMES = 12

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

    /** 登记项：页面 + 它是否自带不透明底色（半透明浮层页为 false，不给它垫背景板） */
    private class Page(val ref: WeakReference<Activity>, val hasBackground: Boolean)

    private val stack = CopyOnWriteArrayList<Page>()

    /** 由 BaseActivity / BaseViewActivity 在 onCreate 调用 */
    fun register(activity: Activity, hasPageBackground: Boolean = true) {
        if (stack.none { it.ref.get() === activity })
            stack.add(Page(WeakReference(activity), hasPageBackground))
        // window 级模式要在这里就把底色垫好：那边动画由系统播，没有「内容级首帧」这种
        // 回调时机了，等窗口画出来再改 decor 会闪一下
        if (useWindowAnim) refreshBackdrops()
    }

    /** 由 BaseActivity / BaseViewActivity 在 onDestroy 调用；顺带清掉已回收的僵尸引用 */
    fun unregister(activity: Activity) {
        stack.removeAll { it.ref.get() == null || it.ref.get() === activity }
    }

    /** [activity] 下面那一页（返回时要归位的那层）；找不到或已销毁就返回 null */
    private fun lowerOf(activity: Activity): Activity? {
        val index = stack.indexOfFirst { it.ref.get() === activity }
        if (index <= 0) return null
        val lower = stack[index - 1].ref.get() ?: return null
        return lower.takeIf { !it.isFinishing && !it.isDestroyed }
    }

    /**
     * 给「被压住的那几页」垫一块不透明底色。
     *
     * 窗口是透明的（不然看不见下层页在动），动画又只播在 `android.R.id.content` 上：
     * 内容块一旦缩小 / 左移 / 淡出，露出来的就是**本窗口之外**的东西——更早的页面，
     * 最底下直接是桌面。实测退出时能看见壁纸上的小组件。
     *
     * 垫在 DecorView 上（它不参与内容动画），透出来的就变成应用背景色，与页面底色同色。
     *
     * 垫哪几页分两种模式（见 [useWindowAnim]）：
     * - 内容级：只垫非栈顶页——栈顶那页必须保持透明，否则会把下层页整个盖死，两层同框就没了；
     * - window 级：每页都垫，兜住「内容视图没铺满窗口」的边（详情页 contentView 441 < 窗口 480）。
     *
     * 半透明浮层页（回复页）任何模式下都不垫——它下面那层不透明页已经垫过了，链路不会断。
     */
    private fun refreshBackdrops() {
        val alive = stack.mapNotNull { page ->
            page.ref.get()?.takeIf { !it.isFinishing && !it.isDestroyed }?.let { page to it }
        }
        val top = alive.lastOrNull()?.second ?: return
        // window 级模式连栈顶页一起垫：位移不会露缝，垫它是为了兜住「内容视图没铺满窗口」
        // 那一条——详情页的 contentView 只有 441 高（窗口 480），两层都归位之后，底部那
        // 39px 会直接透到下层页面上去。内容级模式反过来，栈顶必须保持透明才能两层同框。
        val padAll = useWindowAnim
        alive.forEach { (page, activity) ->
            // 半透明浮层页（回复页）任何模式下都不垫，否则会把它下面那页整个盖死
            if (!page.hasBackground) return@forEach
            if (!padAll && activity === top) return@forEach
            val decor = activity.window?.decorView ?: return@forEach
            val color = MaterialColors.getColor(
                decor, com.google.android.material.R.attr.colorSurface
            )
            if ((decor.background as? ColorDrawable)?.color != color) {
                decor.setBackgroundColor(color)
            }
        }
    }

    /**
     * 这次转场该不该走**内容级**动画（动画作用在 `android.R.id.content` 上）。
     *
     * 判据只有一条：**动画过程会不会露出窗口之外的东西**。
     *
     * - `parallax` 要缩放，窗口四周会露出桌面；
     * - `fade` 交叉淡化时两个窗口都是半透明的，叠加后也会透出桌面。
     *
     * 这两种必须走内容级——动画只作用在内容块上，四周露出来的是本窗口自己的
     * windowBackground（[refreshBackdrops] 垫的页面底色）。
     *
     * 而纯位移的 `slide` 反过来**必须走 window 级**：两个窗口在屏幕上位移恒互补
     * （旧页右边缘 = 新页左边缘），一个像素的缝都不会有，压根不需要垫底色；
     * 更关键的是 window 动画由 **系统合成器**驱动，不占用应用主线程。
     *
     * 内容级动画走的是应用自己的绘制回调（`View.applyLegacyAnimation` 在 `draw()` 里跑），
     * 主线程一卡就直接跳过若干帧；而 `Animation` 又是**按墙钟**算进度的，等主线程缓过来
     * 画下一帧时进度已经到头了——表现就是「一帧滑走 / 一帧白屏」。
     * 实测详情页这种要发请求 + 解析 + 布局的重页面，进入时单帧能到 85~200ms，
     * 400ms 的转场只够画 2~4 帧，于是整段动画等于没有；而 window 级动画同一台机器上
     * 帧帧都在动（这也是改造前那版没有这个问题的原因）。
     */
    private val useWindowAnim: Boolean get() = type == TYPE_SLIDE || type == TYPE_NONE

    /**
     * 打开新页面。
     *
     * `slide` / `none` 直接把资源交给系统播 **window 动画**；其余类型让 window 不参与
     * （传 0），改由新页在 [BaseActivity]/[BaseViewActivity] 的 onCreate 里播内容动画。
     */
    fun enterOptions(context: Context): ActivityOptions {
        if (useWindowAnim) {
            return ActivityOptions.makeCustomAnimation(context, res("rin"), res("lout"))
        }
        enterPending = true
        return ActivityOptions.makeCustomAnimation(context, 0, 0)
    }

    /** 同 [enterOptions]，给还在用 androidx 兼容 API 的调用点 */
    @Suppress("DEPRECATION")
    fun enterOptionsCompat(context: Context): ActivityOptionsCompat {
        if (useWindowAnim) {
            return ActivityOptionsCompat.makeCustomAnimation(context, res("rin"), res("lout"))
        }
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
        refreshBackdrops()
        // 旧页的退场挂在「新页首帧」回调里，两层才咬在同一帧。放在这里直接调用的话，
        // 旧页已经布局完会立刻开跑，而新页还在等自己的首帧，中间那几十毫秒屏幕上是
        // 旧页滑走后的底色，看着像「先空一下，内容才进来」。
        val lower = lowerOf(activity)
        animateContent(activity, res("rin")) {
            lower?.let { animateContent(it, res("lout")) }
        }
    }

    /** 返回时下层页从 0.7 / 半屏外归位（对应 rikkahub 的 pop 动画） */
    fun playReenter(activity: Activity) {
        refreshBackdrops()
        animateContent(activity, res("lin"))
    }

    /** 返回时下层页 onResume 消费一次，true 表示要播归位动画 */
    fun consumeReenter(): Boolean {
        val pending = reenterPending
        reenterPending = false
        return pending
    }

    /**
     * window 级的返回动画，由 [BaseActivity] / [BaseViewActivity] 在 `super.finish()`
     * **之后**立刻调用（框架要求 `overridePendingTransition` 紧跟 finish）。
     *
     * `overridePendingTransition(enterAnim, exitAnim)`：enter 是**被露出来的那层**
     * （下层页从左归位 → `lin`），exit 是**正在关闭的这层**（右滑出 → `rout`）。
     * 内容级模式下什么都不做——那边由 [startExit] 全程接管。
     */
    fun applyWindowExit(activity: Activity) {
        if (useWindowAnim) activity.overridePendingTransition(res("lin"), res("rout"))
    }

    /**
     * 关闭当前页：内容右滑出 + 淡出，**动画播完才真正 finish**（window 全程不动）。
     *
     * @return true 表示已经接管收尾，调用方直接 return 即可，不要再自己 finish
     */
    fun startExit(activity: Activity): Boolean {
        // window 级模式不走这条：直接放行给 super.finish() + applyWindowExit()
        if (useWindowAnim) return false
        val resId = res("rout")
        if (resId == 0 || activity.isFinishing || exiting.containsKey(activity)) return false
        val view = contentView(activity) ?: return false
        val anim = AnimationUtils.loadAnimation(activity, resId) ?: return false

        exiting[activity] = true      // 留在表里直到 Activity 被回收：finish() 重入时直接放行
        applyRoundClip(activity, view)
        refreshBackdrops()
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

    private fun animateContent(activity: Activity, resId: Int, onStarted: (() -> Unit)? = null) {
        val view = if (resId == 0) null else contentView(activity)
        val anim = if (view == null) null else AnimationUtils.loadAnimation(activity, resId)
        if (view == null || anim == null) {
            onStarted?.invoke()
            return
        }
        // onCreate 阶段内容视图还没布局：先把它整块推到屏幕外，让「首帧画出来时内容不在终点
        // 位置」不依赖动画启动的时机——启动晚了也只是「还没滑进来」，不会先闪一帧终点画面。
        val preset = !view.isLaidOut && view.width == 0
        if (preset) view.translationX = view.resources.displayMetrics.widthPixels.toFloat()
        // 各槽位的终点都是原位（rin 到 0、lin 回到 1.0），fillAfter 只为了避免收尾那帧
        // 属性复位造成的闪动；旧页 lout 的终点是「半屏外 + 0.7 + 全透明」，返回时由 lin 接着走
        anim.fillAfter = true
        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) = Unit

            override fun onAnimationRepeat(animation: Animation?) = Unit

            override fun onAnimationEnd(animation: Animation?) {
                clearRoundClip(view)
            }
        })
        // 圆角裁剪（要读宽高）和百分比位移（宽高为 0 时 `100%` 解析成 0，动画干脆不动）
        // 都必须在 layout 之后；而一旦等到首帧画完才启动，新页已经先按「终点位置」整屏画过
        // 一帧——详情页这帧只有底色和骨架，就是那个「先闪一下空白，内容再滑进来」。
        runOnFirstFrame(view) {
            if (preset) view.translationX = 0f
            applyRoundClip(activity, view)
            view.startAnimation(anim)
            onStarted?.invoke()
        }
    }

    /**
     * 在 [view] 完成 layout 之后、首帧绘制之前执行 [action]；已经在屏幕上的页（退场 / 归位）直接同步执行。
     *
     * 这条通路踩过两次坑，别再回到 `ViewTreeObserver.OnPreDrawListener`：
     * - onCreate 阶段 `view.viewTreeObserver` 拿到的是**尚未 attach 时**的 floating observer，
     *   它会在 attach 时被 merge 进真正的 observer 然后 `kill()`；用捕获的那个去
     *   `removeOnPreDrawListener` 会静默失败，表现是每帧重启动画、整页卡在屏幕外；
     * - 而在这套 Activity / 主题组合下（rikka MaterialActivity + AppCompat 子装饰），preDraw
     *   这条路干脆一次都不回调：动画等于没播，旧页也不退场。
     *
     * 改成挂在 View 自己身上的 layout 回调：布局必然早于绘制，且不经过 ViewTreeObserver，
     * 不受 merge / kill 影响。再加一条逐帧兜底（最多 [MAX_FALLBACK_FRAMES] 帧），
     * 保证 [action] 一定会跑、且只跑一次。
     */
    private fun runOnFirstFrame(view: View, action: () -> Unit) {
        var consumed = false
        val run = Runnable {
            if (consumed) return@Runnable
            consumed = true
            action()
        }
        if (view.isLaidOut && view.width > 0) {
            run.run()
            return
        }
        view.doOnNextLayout { run.run() }
        // 兜底：万一这帧没等来 layout（重页面冷启动可能拖到百毫秒级），就逐帧轮询到有尺寸为止，
        // 最多 MAX_FALLBACK_FRAMES 帧后强制执行。用固定超时不行——超时太短会在没尺寸时白启动动画
        // （画面停在终点、看着还是「没动画」），太长又白白延长「只有底色」的时间。
        var frames = 0
        val poll = object : Runnable {
            override fun run() {
                if (consumed) return
                frames++
                if ((view.isLaidOut && view.width > 0) || frames > MAX_FALLBACK_FRAMES) {
                    run.run()
                } else {
                    view.postOnAnimation(this)
                }
            }
        }
        view.postOnAnimation(poll)
    }

    /**
     * 动画期间把内容裁成圆角，免得缩放时四个直角顶在屏幕圆角外。
     * 只在动画期间开，平时关掉（clipToOutline 常驻会影响性能和阴影）。
     */
    private fun applyRoundClip(activity: Activity, view: View) {
        val radius = screenCornerRadius(activity)
        // 尺寸兜底：超时兜底路径下 view 可能还没量到宽高，而 outline 为 0x0 时
        // clipToOutline 会把整页裁成空——那一帧就是全白/全透明。所以取不到就按屏幕尺寸裁。
        val metrics = view.resources.displayMetrics
        val w = if (view.width > 0) view.width else metrics.widthPixels
        val h = if (view.height > 0) view.height else metrics.heightPixels
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, w, h, radius)
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
