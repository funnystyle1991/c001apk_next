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

    /** 类型：水平滑动 */
    const val TYPE_SLIDE = "slide"

    /** 类型：淡入淡出 */
    const val TYPE_FADE = "fade"

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

    /** 关闭当前页：下层页从左滑入 + 当前页向右滑出。放在 Activity.finish() 里调。 */
    fun applyReturn(activity: Activity) {
        activity.overridePendingTransition(res("lin"), res("rout"))
    }

    /** FragmentTransaction.setCustomAnimations 的四个参数（0 = 无动画） */
    fun fragmentEnter(): Int = res("rin")

    fun fragmentExit(): Int = res("lout")

    fun fragmentPopEnter(): Int = res("lin")

    fun fragmentPopExit(): Int = res("rout")
}
