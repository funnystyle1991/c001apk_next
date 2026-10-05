package com.example.c001apk.util

import com.example.c001apk.logic.model.MessageResponse

/**
 * 私信会话（`/v6/message/list`）的字段提取工具。
 *
 * 酷安 API 里 `fromuid` = 消息发送者、`uid` = 消息接收者，所以会话项上的
 * `username` / `userAvatar` 有可能指向我自己（最后一条消息是我发的）。
 * 对方信息要优先认 `messageUid` / `messageUsername` / `messageUserAvatar`，
 * 再退回 `from*`，最后才用裸字段兜底。
 */
object MessageKit {

    /** 对方的 uid */
    @JvmStatic
    fun partnerUid(data: MessageResponse.Data?): String {
        if (data == null) return ""
        data.messageUid?.takeIf { it.isNotBlank() }?.let { return it }
        val mine = PrefManager.uid
        return listOf(data.fromuid, data.uid)
            .firstOrNull { !it.isNullOrBlank() && it != mine }
            .orEmpty()
    }

    /** 对方昵称 */
    @JvmStatic
    fun partnerName(data: MessageResponse.Data?): String {
        if (data == null) return ""
        return data.messageUsername ?: data.fromusername ?: data.username ?: ""
    }

    /** 对方头像 */
    @JvmStatic
    fun partnerAvatar(data: MessageResponse.Data?): String {
        if (data == null) return ""
        return data.messageUserAvatar ?: data.fromUserAvatar ?: ""
    }

    /**
     * 是不是「系统提示」条目（比如「关注对方即可无限制聊天」）。
     * 这种条目居中显示灰字、不出气泡。
     *
     * 注意：布局里不能写 `data.entityType == \`messageExtra\``，
     * databinding 对 String 的 `==` 生成的是 Java 的引用比较（不是 equals），
     * Gson 解析出来的字符串没被 intern，恒为 false。
     */
    @JvmStatic
    fun isExtra(data: MessageResponse.Data?): Boolean = data?.entityType == "messageExtra"

    /** 系统提示的文字：多数在 `message` 里，有些只给了 `title` */
    @JvmStatic
    fun extraText(data: MessageResponse.Data?): String {
        if (data == null) return ""
        return if (data.message.isNullOrBlank()) data.title.orEmpty() else data.message
    }

    /** 会话列表里的最后一条消息预览：只发图片时 message 是空的，得显示 [图片] */
    @JvmStatic
    fun preview(data: MessageResponse.Data?): String {
        if (data == null) return ""
        if (!data.message.isNullOrBlank()) return data.message
        if (!data.messagePic.isNullOrBlank()) return "[图片]"
        return ""
    }
}
