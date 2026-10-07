package com.example.c001apk.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ImageView
import androidx.core.view.isVisible
import com.example.c001apk.R
import org.json.JSONObject

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
 * 在此之上叠一层**云端认证表**（本应用自己的认证，见 [load]）：服务端
 * c001apk/userverify/ 目录下一个 uid 一份 json，能同时控制角标色号、认证文字和
 * 认证对象，支持一次下发多人。命中的人以云端配置为准 —— 文字用云端的、颜色用
 * 云端的，没配 color 才回落到 verify_icon 的绿 / 黄。
 *
 * 表由 [RemoteConfig] 在启动时刷新一次并落盘缓存，所以离线启动、接口挂了都不影响
 * 已有认证的展示（[VerifyBadge] 里没有再写死任何 uid）。
 */
object VerifyBadge {

    /** 云端下发的单条认证配置 */
    private class Entry(val title: String, val color: Int?, val icon: String)

    /** uid -> 配置；[load] 整体替换，读取都是无锁快查 */
    @Volatile
    private var remote: Map<String, Entry> = emptyMap()

    /**
     * 用服务端下发的 JSON 重建整张表。
     *
     *   {"code":0,"list":[{"uid":"32864090","title":"...","color":"#FF9CA8","icon":"v_green"}]}
     *
     * 解析不出来（空串 / 坏 JSON / 一条都没有）就保持原样：宁可继续用旧缓存，
     * 也别因为一次响应异常让所有人的认证标集体消失。
     */
    fun load(json: String?) {
        if (json.isNullOrBlank()) return
        val array = runCatching { JSONObject(json).optJSONArray("list") }.getOrNull() ?: return
        val next = HashMap<String, Entry>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val uid = item.optString("uid").trim()
            if (uid.isEmpty()) continue
            next[uid] = Entry(
                title = item.optString("title").trim(),
                color = parseColor(item.optString("color")),
                icon = item.optString("icon").trim(),
            )
        }
        if (next.isEmpty()) return
        remote = next
    }

    /** #RRGGBB / #AARRGGBB（服务端已校验过格式，这里只是兜一道） */
    private fun parseColor(value: String?): Int? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { Color.parseColor(text) }.getOrNull()
    }

    /** 有没有认证：在云端表里，或服务端自己标了 verify_status == 1 */
    fun isVerified(uid: String?, status: Int?): Boolean =
        (uid != null && remote.containsKey(uid)) || status == 1

    /** 角标底色：云端 color 优先，其次云端 / 服务端给的 icon 分绿黄 */
    fun badgeColor(context: Context, uid: String?, icon: String?): Int {
        val entry = uid?.let { remote[it] }
        entry?.color?.let { return it }
        val key = entry?.icon?.takeIf { it.isNotEmpty() } ?: icon
        return context.getColor(
            if (key == "v_yellow") R.color.verify_badge_yellow else R.color.verify_badge_green
        )
    }

    /**
     * 认证文字（个人主页那一行）。
     * 云端配了就用云端的（配成空串等于只出头像角标），否则原样展示服务端下发的。
     */
    fun title(uid: String?, raw: String?): String? {
        uid?.let { remote[it] }?.let { return it.title.ifBlank { null } }
        return raw?.takeIf { it.isNotBlank() }
    }

    /**
     * 把结果落到角标 ImageView 上：有认证就显示并上色，没有就 GONE。
     *
     * DataBinding 那边走 [com.example.c001apk.adapter.setVerifyBadge]，
     * 手动 inflate 的 ViewHolder（黑名单那种）直接用这个，判定逻辑只有一份。
     */
    fun applyTo(imageView: ImageView, uid: String?, icon: String?, status: Int?) {
        val verified = isVerified(uid, status)
        imageView.isVisible = verified
        if (!verified) return
        imageView.backgroundTintList =
            ColorStateList.valueOf(badgeColor(imageView.context, uid, icon))
        imageView.contentDescription = title(uid, null) ?: "认证用户"
    }
}
