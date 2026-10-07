package com.example.c001apk.util

import android.content.Context
import com.example.c001apk.R

/**
 * 用户认证标：头像右下角那枚小圆标 + 个人主页的认证文字。
 *
 * 服务端在 userInfo 里下发三个字段（列表和详情都带）：
 *   verify_status  1 = 有认证
 *   verify_icon    客户端内置图标名，样本里只有 v_green / v_yellow
 *   verify_title   完整认证名，如「酷安认证: 酷安员工」「酷安认证: 优质内容创作者」，
 *                  只在个人主页展示，列表 / 详情页只挂角标
 *
 * 认证类别（企业 / 官方 / 个人）没有独立的结构化字段：user_type 恒为 0，
 * verify_label 恒为空，admintype 只覆盖「酷安员工 / 官方账号」。所以颜色只能
 * 跟着 verify_icon 走，文字直接用 verify_title 原文。
 *
 * 另外这里挂了一条纯本地的特例：[NEXT_MAINTAINER_UID] 用 m3e 的樱花色标，
 * 认证文字固定为 [NEXT_MAINTAINER_TITLE]，不依赖服务端下发。
 */
object VerifyBadge {

    /** 本地特例：c001apk_next 维护者 */
    const val NEXT_MAINTAINER_UID = "32864090"

    /** 本地特例的认证文字；服务端没有这条认证，展示时直接顶掉 verify_title */
    const val NEXT_MAINTAINER_TITLE = "c001apk_next维护者"

    /** 有没有认证：本地特例优先，其余看 verify_status == 1 */
    fun isVerified(uid: String?, status: Int?): Boolean =
        uid == NEXT_MAINTAINER_UID || status == 1

    /** 角标底色：本地特例用樱花色，其余按 verify_icon 分绿 / 黄 */
    fun badgeColor(context: Context, uid: String?, icon: String?): Int = when {
        uid == NEXT_MAINTAINER_UID -> context.getColor(R.color.verify_badge_sakura)
        icon == "v_yellow" -> context.getColor(R.color.verify_badge_yellow)
        else -> context.getColor(R.color.verify_badge_green)
    }

    /** 认证文字（个人主页那一行）：本地特例直接给固定文案，其余原样展示 */
    fun title(uid: String?, raw: String?): String? = when {
        uid == NEXT_MAINTAINER_UID -> NEXT_MAINTAINER_TITLE
        raw.isNullOrBlank() -> null
        else -> raw
    }
}
