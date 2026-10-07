package com.example.c001apk.util

import android.net.Uri
import org.json.JSONObject

/**
 * 可信链接白名单：判断一个链接是不是「自己人的」。
 *
 * 不在表里的链接要区别对待（见 [com.example.c001apk.util.NetWorkUtil.openLink]）：
 * 用户设置应用内 WebView 打开时，页面底部挂一条风险提示；
 * 用户设置外部浏览器打开时，先弹二次确认。
 *
 * 表由服务端下发（真源 _rev/update_files/reliablelink/list.txt）：
 *   {"code":0,"list":["coolapk.com","*.coolapk.com","*.houlangs.cn"]}
 *
 * 匹配规则（与服务端注释一致）：
 *   不带 `/` 的模式只比对 host，精确匹配（`coolapk.com` 不匹配 `a.coolapk.com`）；
 *   带 `/` 的模式比对 host + path；
 *   `*` 通配任意字符，`*.coolapk.com` 覆盖所有子域但不含根域。
 */
object LinkGuard {

    /** 服务端还没下发时（首启离线）的兜底表，内容与线上 list.txt 一致 */
    private val DEFAULT_PATTERNS = listOf("coolapk.com", "*.coolapk.com", "*.houlangs.cn")

    private class Pattern(val raw: String, val byPath: Boolean, val regex: Regex)

    @Volatile
    private var patterns: List<Pattern> = DEFAULT_PATTERNS.mapNotNull { compile(it) }

    /** 用服务端下发的 JSON 覆盖白名单；空表 / 坏 JSON 一律保持原样 */
    fun load(json: String?) {
        if (json.isNullOrBlank()) return
        val array = runCatching { JSONObject(json).optJSONArray("list") }.getOrNull() ?: return
        val next = ArrayList<Pattern>(array.length())
        for (i in 0 until array.length()) {
            compile(array.optString(i))?.let { next.add(it) }
        }
        if (next.isEmpty()) return
        patterns = next
    }

    private fun compile(raw: String): Pattern? {
        val text = raw.trim().lowercase()
        if (text.isEmpty()) return null
        val sb = StringBuilder("^")
        for (c in text) {
            when (c) {
                '*' -> sb.append(".*")
                '.', '/', '?', '+', '$', '^', '(', ')', '[', ']', '{', '}', '|', '\\' ->
                    sb.append('\\').append(c)

                else -> sb.append(c)
            }
        }
        sb.append('$')
        return Pattern(text, text.contains('/'), Regex(sb.toString()))
    }

    /** 链接是否可信；非 http(s) 的一律不算（那些走各自的协议处理，不进这个判定） */
    fun isTrusted(url: String?): Boolean {
        val text = url?.trim().orEmpty()
        if (text.isEmpty()) return false
        val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path.orEmpty()
        return patterns.any { p ->
            if (p.byPath) p.regex.matches(host + path) else p.regex.matches(host)
        }
    }

    /** 取 host，拿不到就返回原串，用于提示文案里的「前往 xxx」 */
    fun hostOf(url: String?): String {
        val text = url?.trim().orEmpty()
        if (text.isEmpty()) return ""
        return runCatching { Uri.parse(text).host }.getOrNull()?.takeIf { it.isNotEmpty() } ?: text
    }
}
