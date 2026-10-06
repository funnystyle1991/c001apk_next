package com.example.c001apk.util

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import androidx.core.app.ActivityOptionsCompat

/**
 * 水平转场动画的统一入口：把「设置 - 外观」里的曲线 / 类型 / 速度翻译成具体动画资源。
 *
 * 之所以要查表而不是直接算：`overridePendingTransition` 与 `ActivityOptions` 只吃
 * **编译期资源 id**，插值器与 duration 又都写死在 XML 里，运行时没法改。所以三个维度的
 * 全部组合由 `tools/gen_transition_anim.py` 预先烘成 `res/anim/exp_*.xml`，这里按当前
 * 选择挑一个。
 *
 * **模型：全部交给系统播 window 动画**，应用侧不参与驱动——`makeCustomAnimation(ctx,
 * res("rin"), res("lout"))` 管进入，`overridePendingTransition(res("lin"), res("rout"))`
 * 管返回。不要再改成「把动画挪到 android.R.id.content 上播」那套内容级方案（093148bf
 * 试过、2026-10-06 已回退）：
 *
 * - 内容级动画跑在应用自己的绘制回调里（`View.applyLegacyAnimation` 在 `draw()` 内），
 *   重页面首帧卡顿就直接没有帧可画；而 `Animation` 又是按**墙钟**算进度的，等主线程缓过来
 *   画下一帧时进度已经到头——表现就是「一帧滑走 / 一帧白屏」。实测详情页进入时单帧
 *   85~200ms，400ms 的转场只够画 2~4 帧。
 * - 内容级还必须配 `windowIsTranslucent=true`（不然上层窗口把下层挡死），而**透明窗口
 *   本身就慢**：转场退化成逐帧透明合成，实测同一台机器上变成「一卡一卡」，比原作更糟。
 *
 * 代价（已知并接受）：动画作用在整个窗口上，`parallax` 缩放旧窗口时，窗口四周露出的是
 * **本窗口之外**的那一层（更早的页面，最底下是桌面）。这是独立 Activity 转场的固有代价，
 * 单 Activity Compose（rikkahub）没有；纯位移的 `slide` 不受影响——两个窗口位移恒互补。
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

    /**
     * 打开新页面：新页从右滑入 + 旧页向左滑出。
     * 传 0 是有意的——`overridePendingTransition(0, 0)` 是关掉转场的标准做法，
     * 不回退到系统默认动画。
     */
    fun enterOptions(context: Context): ActivityOptions =
        ActivityOptions.makeCustomAnimation(context, res("rin"), res("lout"))

    /** 同 [enterOptions]，给还在用 androidx 兼容 API 的调用点 */
    @Suppress("DEPRECATION")
    fun enterOptionsCompat(context: Context): ActivityOptionsCompat =
        ActivityOptionsCompat.makeCustomAnimation(context, res("rin"), res("lout"))

    /**
     * 关闭当前页：下层页从左归位 + 当前页向右滑出。
     *
     * 必须在 `super.finish()` **之后**立刻调用（框架要求 `overridePendingTransition`
     * 紧跟 finish）。`overridePendingTransition(enterAnim, exitAnim)` 里 enter 是**被露出来的
     * 那层**（下层页 → `lin`），exit 是**正在关闭的这层**（→ `rout`）。
     */
    fun applyReturn(activity: Activity) {
        activity.overridePendingTransition(res("lin"), res("rout"))
    }

    /** FragmentTransaction.setCustomAnimations 的四个参数（0 = 无动画） */
    fun fragmentEnter(): Int = res("rin")

    fun fragmentExit(): Int = res("lout")

    fun fragmentPopEnter(): Int = res("lin")

    fun fragmentPopExit(): Int = res("rout")
}
