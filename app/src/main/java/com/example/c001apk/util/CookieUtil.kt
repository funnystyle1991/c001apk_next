package com.example.c001apk.util

object CookieUtil {

    var SESSID = ""

    var atme: Int? = null
    var atcommentme: Int? = null
    var feedlike: Int? = null
    var contacts_follow: Int? = null
    var badge: Int = 0
    var notification: Int = 0

    /** 私信未读数（checkCount 的 message），消息中心宫格「私信」的红点读它 */
    var message: Int? = null

    /**
     * 评论回复未读数（checkCount 的 commentme）：动态 / 点评被评论。
     * 宫格里没有单独入口，它对应消息中心下方列表的数据源 `/v6/notification/list`。
     */
    var commentme: Int? = null

}