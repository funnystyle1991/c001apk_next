package com.example.c001apk.logic.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

data class MessageResponse(
    val status: Int?,
    val error: Int?,
    val message: String?,
    val messageStatus: Int?,
    val data: List<Data>?
) {

    data class Data(
        val rid: Long?,
        val infoHtml: String?,
        val entityType: String,
        val id: String,
        val uid: String,
        val dateline: Long,
        val message: String,
        val username: String,
        val forwardid: String,
        @SerializedName("source_id") val sourceId: String,
        val pic: String,
        val istag: Int,
        val tags: String,
        val likenum: String,
        val commentnum: String,
        val replynum: String,
        val favnum: String,
        @SerializedName("device_title") val deviceTitle: String,
        val userAvatar: String,
        val title: String,
        val picArr: List<String>?,
        val userAction: HomeFeedResponse.UserAction?,
        val forwardSourceFeed: ForwardSourceFeed?,
        val feed: Feed?,
        val likeUsername: String,
        val likeUid: String,
        val likeTime: Long,
        val likeAvatar: String,
        val fid: String,
        val fromUserAvatar: String,
        val fromusername: String,
        val fromuid: String,
        val note: String,
        /**
         * 通知条目的细分类型：`notify_xms`（酷安小秘书）/ `feed_reply` / `rating_reply` …
         * 只有 `/v6/notification/` 下的接口会下发，私信会话不返回。
         */
        val type: String? = null,
        /** 通知条目的唯一标识，删除通知时用它 */
        val slug: String? = null,
        /** 通知里挂的跳转地址，如 `/u/10086` */
        val url: String? = null,
        /** 私信会话标识，聊天页靠它拉历史记录 */
        val ukey: String? = null,
        /** 私信会话未读数（只有 /v6/message/list 返回） */
        @SerializedName("unreadNum") val unreadNum: Int? = null,
        /** 私信会话是否有新消息（只有 /v6/message/list 返回） */
        @SerializedName("isnew") val isnew: Int? = null,
        /** 私信里的图片消息，纯文本消息为空 */
        @SerializedName("message_pic") val messagePic: String? = null,
        /** 聊天记录里每条消息的真实主键（列表 DiffUtil 用，比 `id` 更可靠） */
        val entityId: String? = null,
        /**
         * 会话列表里**对方**的 uid / 昵称 / 头像。
         * 因为 fromuid = 发送者、uid = 接收者，会话项上的 username / userAvatar
         * 指向的可能是我自己（最后一条是我发的），所以对方信息要认这三个字段。
         */
        @SerializedName("messageUid") val messageUid: String? = null,
        @SerializedName("messageUsername") val messageUsername: String? = null,
        @SerializedName("messageUserAvatar") val messageUserAvatar: String? = null
    )

    /**
     * 写操作（发私信 / 标记已读）的响应包。
     * 只取 status / message，`data` 的形状不稳定（有时是对象有时是数组），故意不声明。
     */
    data class ActionResponse(
        val status: Int?,
        val error: Int?,
        val message: String?,
        val messageStatus: String?
    )

    data class Feed(
        val id: String,
        val uid: String,
        val username: String,
        val message: String,
        val pic: String?,
        val url: String
    )

    @Parcelize
    data class ForwardSourceFeed(
        val entityType: String,
        val feedType: String,
        val id: String,
        val username: String,
        val uid: String,
        val message: String,
        @SerializedName("message_title") val messageTitle: String,
        val pic: String,
        val picArr: List<String>?,
    ): Parcelable
}

