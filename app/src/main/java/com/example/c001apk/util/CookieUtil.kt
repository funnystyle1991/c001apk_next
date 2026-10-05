package com.example.c001apk.util

object CookieUtil {

    var SESSID = ""

    var atme: Int? = null
    var atcommentme: Int? = null
    var feedlike: Int? = null
    var contacts_follow: Int? = null
    var badge: Int = 0
    var notification: Int = 0

    /**
     * 消息未读总数。
     *
     * 优先用服务端总角标（checkCount 的 badge_v18 / badge），它才是真正的"未读总数"；
     * 总角标缺失（老版本接口）时才回退到分类之和。分类之和只覆盖
     * @我的动态 + @我的评论 + 我收到的赞 + 好友关注，不含私信，单靠它会漏掉服务端并入总角标的未读。
     */
    val unreadTotal: Int
        get() = maxOf(
            (atme ?: 0) + (atcommentme ?: 0) + (feedlike ?: 0) + (contacts_follow ?: 0),
            badge
        )

}