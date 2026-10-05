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
     * 消息未读总数：@我的动态 + @我的评论 + 我收到的赞 + 好友关注。
     * 「私信」不在 /v6/notification/checkCount 里，所以不计入。
     */
    val unreadTotal: Int
        get() = (atme ?: 0) + (atcommentme ?: 0) + (feedlike ?: 0) + (contacts_follow ?: 0)

}