package com.example.c001apk.util

/**
 * 未读数的统一口径。
 *
 * 消息中心宫格的红点和首页工具栏消息入口的角标都从这里取数，免得两处各算一套、
 * 数字对不上。规则和宫格一致：**服务端分类未读 − 本机已读账本**
 * （[MessageCenterSeenStore]，看过即已读）。
 *
 * 私信（[CookieUtil.message]）是会话级未读，不在本机账本里抵消，直接用服务端的数。
 */
object UnreadCounter {

    const val AT_ME = "atMe"
    const val AT_COMMENT_ME = "atCommentMe"
    const val FEED_LIKE = "feedLike"
    const val CONTACTS_FOLLOW = "contactsFollow"
    const val MESSAGE = "message"
    const val SECRETARY = "secretary"

    /**
     * 「我的回复」（别人回复我的评论）。
     * 分类名沿用消息中心汇总列表里那一类的名字，两边共用同一份已读账本。
     */
    const val COMMENT_ME = "commentMe"

    /** 宫格七个入口的未读分类，顺序跟宫格（[com.example.c001apk.ui.message.MessageThirdAdapter]）一致，首页角标按这个顺序求和 */
    private val gridCategories = listOf(
        AT_ME, AT_COMMENT_ME, FEED_LIKE, CONTACTS_FOLLOW, COMMENT_ME, MESSAGE, SECRETARY
    )

    /** 某一类的未读数，<= 0 表示不画红点 */
    fun of(category: String): Int = when (category) {
        AT_ME -> tracked(AT_ME, CookieUtil.atme)
        AT_COMMENT_ME -> tracked(AT_COMMENT_ME, CookieUtil.atcommentme)
        FEED_LIKE -> tracked(FEED_LIKE, CookieUtil.feedlike)
        CONTACTS_FOLLOW -> tracked(CONTACTS_FOLLOW, CookieUtil.contacts_follow)
        COMMENT_ME -> tracked(COMMENT_ME, CookieUtil.commentme)
        MESSAGE -> CookieUtil.message ?: 0
        // 小秘书没有单独的计数接口，借用「通知未读」；宫格和角标用同一份，口径才一致
        SECRETARY -> tracked(SECRETARY, CookieUtil.notification)
        else -> 0
    }

    /**
     * 首页工具栏消息入口的角标数字 = 七个入口未读之和。
     *
     * 用求和而不是服务端的总角标（`badge_v18`）：总角标不随本机账本变化，
     * 在消息中心里看完通知后它会一直挂着，跟宫格的红点对不上。
     */
    val total: Int get() = gridCategories.sumOf { of(it) }

    private fun tracked(category: String, serverCount: Int?): Int =
        MessageCenterSeenStore.unreadOf(category, serverCount ?: 0)
}
