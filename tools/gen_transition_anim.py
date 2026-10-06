#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成「转场动画实验」用的资源矩阵。

背景：res/anim 是编译期资源，运行时只能从「预先烘好的一批」里挑，
所以把三个实验维度（曲线 / 类型 / 速度）的所有组合都烘成独立资源，
设置页再按当前选择查表拿到对应 resId。

维度：
  曲线 curve  : linear(M3 线性) / m2(M2 标准 0.4,0,0.2,1) / std(M3 标准) / emph(M3 强调)
  类型 type   : slide(水平滑动) / fade(淡入淡出)；none(无动画) 由代码直接传 0，不需要资源
  速度 speed  : 进入时长 100/200/300/400/500ms，退出固定 = 进入 - 50ms（对齐 M3 medium2/medium1）

产物：
  app/src/main/res/interpolator/exp_m2_standard.xml
  app/src/main/res/anim/exp_<type>_<curve>_<enter>_<slot>.xml       共 120 个
  app/src/main/java/com/example/c001apk/util/TransitionAnimTable.kt  生成式查表

选定最终方案后：把赢家的曲线/时长固化回 right_in / left_out / left_in / right_out，
删掉 res/anim/exp_*.xml、res/interpolator/exp_*.xml、TransitionAnimTable.kt 和本脚本。
"""

import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANIM_DIR = os.path.join(ROOT, "app", "src", "main", "res", "anim")
INTERP_DIR = os.path.join(ROOT, "app", "src", "main", "res", "interpolator")
KT_DIR = os.path.join(ROOT, "app", "src", "main", "java", "com", "example", "c001apk", "util")

# 曲线 -> (进入插值器, 退出插值器)
CURVES = {
    "linear": ("@android:interpolator/linear", "@android:interpolator/linear"),
    "m2": ("@interpolator/exp_m2_standard", "@interpolator/exp_m2_standard"),
    "std": (
        "@interpolator/m3_sys_motion_easing_standard_decelerate",
        "@interpolator/m3_sys_motion_easing_standard_accelerate",
    ),
    "emph": (
        "@interpolator/m3_sys_motion_easing_emphasized_decelerate",
        "@interpolator/m3_sys_motion_easing_emphasized_accelerate",
    ),
}

ENTER_DURATIONS = [100, 200, 300, 400, 500]
EXIT_GAP = 50          # 退出 = 进入 - 50
EXIT_MIN = 50

SLOTS = ["rin", "lin", "lout", "rout"]

M2_STANDARD = """<?xml version="1.0" encoding="utf-8"?>
<!-- 转场动画实验用：Material 2 标准曲线 cubic-bezier(0.4, 0, 0.2, 1)（生成物，勿手改） -->
<pathInterpolator xmlns:android="http://schemas.android.com/apk/res/android"
    android:controlX1="0.4"
    android:controlX2="0.2"
    android:controlY1="0.0"
    android:controlY2="1.0" />
"""

SLIDE = """<?xml version="1.0" encoding="utf-8"?>
<!-- 生成物，勿手改：slide / {curve} / 进入 {enter}ms（退出 {exit}ms） -->
<translate xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="{duration}"
    android:fromXDelta="{frm}"
    android:interpolator="{interp}"
    android:toXDelta="{to}" />
"""

FADE = """<?xml version="1.0" encoding="utf-8"?>
<!-- 生成物，勿手改：fade / {curve} / 进入 {enter}ms（退出 {exit}ms） -->
<alpha xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="{duration}"
    android:fromAlpha="{frm}"
    android:interpolator="{interp}"
    android:toAlpha="{to}" />
"""

KT_HEADER = """package com.example.c001apk.util

import com.example.c001apk.R

/**
 * 转场动画资源查表：**生成物，勿手改**（见 tools/gen_transition_anim.py）。
 *
 * key = "<type>_<curve>_<enterMs>_<slot>"，slot 取 rin/lin/lout/rout；
 * none 类型与查不到的 key 都返回 0，交给调用方当作「无动画」。
 */
@Suppress("MaxLineLength")
internal object TransitionAnimTable {

    fun resolve(key: String): Int = when (key) {
"""

KT_FOOTER = """        else -> 0
    }
}
"""


def write(path, content):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


def main():
    # 清掉上一轮生成物，保证脚本可重复执行
    removed = 0
    for d, prefix in ((ANIM_DIR, "exp_"), (INTERP_DIR, "exp_")):
        if not os.path.isdir(d):
            continue
        for name in os.listdir(d):
            if name.startswith(prefix) and name.endswith(".xml"):
                os.remove(os.path.join(d, name))
                removed += 1

    os.makedirs(INTERP_DIR, exist_ok=True)
    write(os.path.join(INTERP_DIR, "exp_m2_standard.xml"), M2_STANDARD)

    entries = []      # (key, resource_name)
    files = []        # (resource_name, content)

    for curve, (enter_interp, exit_interp) in CURVES.items():
        for enter in ENTER_DURATIONS:
            exit_ms = max(EXIT_MIN, enter - EXIT_GAP)

            # 水平滑动：四个方向各自需要一份资源
            for slot in SLOTS:
                if slot == "rin":
                    frm, to, interp, dur = "100%", "0", enter_interp, enter
                elif slot == "lin":
                    frm, to, interp, dur = "-100%", "0", enter_interp, enter
                elif slot == "lout":
                    frm, to, interp, dur = "0", "-100%", exit_interp, exit_ms
                else:
                    frm, to, interp, dur = "0", "100%", exit_interp, exit_ms
                name = "exp_slide_%s_%d_%s" % (curve, enter, slot)
                files.append((name, SLIDE.format(
                    curve=curve, enter=enter, exit=exit_ms,
                    duration=dur, frm=frm, to=to, interp=interp)))
                entries.append(("slide_%s_%d_%s" % (curve, enter, slot), name))

            # 淡入淡出：左右方向观感相同，只烘「进 / 出」两份，代码侧复用
            fin = "exp_fade_%s_%d_fin" % (curve, enter)
            fout = "exp_fade_%s_%d_fout" % (curve, enter)
            files.append((fin, FADE.format(
                curve=curve, enter=enter, exit=exit_ms,
                duration=enter, frm="0.0", to="1.0", interp=enter_interp)))
            files.append((fout, FADE.format(
                curve=curve, enter=enter, exit=exit_ms,
                duration=exit_ms, frm="1.0", to="0.0", interp=exit_interp)))
            for slot in SLOTS:
                key = "fade_%s_%d_%s" % (curve, enter, slot)
                entries.append((key, fin if slot in ("rin", "lin") else fout))

    for name, content in files:
        write(os.path.join(ANIM_DIR, name + ".xml"), content)

    lines = [KT_HEADER]
    for key, name in entries:
        lines.append('        "%s" -> R.anim.%s\n' % (key, name))
    lines.append(KT_FOOTER)
    write(os.path.join(KT_DIR, "TransitionAnimTable.kt"), "".join(lines))

    print("removed=%d  anim=%d  entries=%d" % (removed, len(files), len(entries)))


if __name__ == "__main__":
    main()
