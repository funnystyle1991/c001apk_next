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

    /**
     * 列表类型判断。⚠️ 布局里**不能**写 `type == \`list\``：
     * databinding 生成的代码对 String 的 `==` 是引用比较（不是 equals），
     * 传进来的 "list" 和字面量不是同一个对象，恒为 false——
     * 实测私信列表会因此走 else 分支，把「对方」显示成我自己（最后一条是我发的那些会话）。
     * 所有类型判断统一走这里（Kotlin 的 == 是真 equals）。
     */
    @JvmStatic
    fun isList(type: String?): Boolean = type == "list"

    /** 好友关注列表（第二行显示「关注了你」） */
    @JvmStatic
    fun isContactsFollow(type: String?): Boolean = type == "contactsFollow"

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

    /**
     * 是不是「酷安小秘书」——官方机器人（uid 10086），只推送登录提醒和站内信，不是私信对象。
     *
     * 实测（2026-10-05）：`GET /v6/message/list` 的 18 条会话里**没有**小秘书，
     * 它的消息全在 `GET /v6/notification/list` 里（`type=notify_xms`、`fromuid=10086`）。
     * 服务端不认 `type` / `fromuid` 过滤参数（照旧返回全部 20 条），只能本地筛。
     *
     * 昵称必须全等——用 contains 会让名字里带「小秘书」的普通用户被误判成机器人。
     */
    @JvmStatic
    fun isSecretaryName(name: String?): Boolean = name == "酷安小秘书"

    /** 通知条目是不是小秘书发的 */
    @JvmStatic
    fun isSecretaryNotify(data: MessageResponse.Data?): Boolean =
        data?.type == "notify_xms" || data?.fromuid == "10086"

    /** 会话项是不是小秘书 */
    @JvmStatic
    fun isSecretary(data: MessageResponse.Data?): Boolean = isSecretaryName(partnerName(data))

    /** 会话列表里的最后一条消息预览：只发图片时 message 是空的，得显示 [图片] */
    @JvmStatic
    fun preview(data: MessageResponse.Data?): String {
        if (data == null) return ""
        if (!data.message.isNullOrBlank()) return data.message
        if (!data.messagePic.isNullOrBlank()) return "[图片]"
        return ""
    }
}
