package com.example.c001apk.logic.model

import com.google.gson.annotations.SerializedName

data class CheckCountResponse(
    val status: Int?,
    val message: String?,
    val messageStatus: String?,
    val data: Data?
) {

    data class Data(
        val notification: Int,
        @SerializedName("contacts_follow")
        val contactsFollow: Int,
        val message: Int,
        val atme: Int,
        val atcommentme: Int,
        val commentme: Int,
        val feedlike: Int,
        val badge: Int,
        val dateline: String,
        /** 服务端已把未读总数迁到 v18 字段，老 badge/notification 恒为 0，取不到 v18 时才回退 */
        @SerializedName("badge_v18")
        val badgeV18: Int? = null,
        @SerializedName("notification_v18")
        val notificationV18: Int? = null
    ) {
        val unreadBadge: Int get() = badgeV18 ?: badge
        val unreadNotification: Int get() = notificationV18 ?: notification
    }

}

