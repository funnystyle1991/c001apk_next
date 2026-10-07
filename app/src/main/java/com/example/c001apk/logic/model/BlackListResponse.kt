package com.example.c001apk.logic.model

import com.google.gson.annotations.SerializedName

/**
 * 用户黑名单（云端）—— GET /v6/user/blackList?page=n
 *
 * data 里是用户实体（entityType = "user"），最后一条通常是
 * configCard（entityType = "configCard"，extraData = {"total":300,"current":2}，即上限 / 当前人数）。
 * 翻到「没有用户实体」的那一页就是到底了。
 */
data class BlackListResponse(
    val data: List<BlackListUser>? = null,
)

data class BlackListUser(
    val uid: String? = null,
    val username: String? = null,
    val displayUsername: String? = null,
    @SerializedName("userAvatar") val userAvatar: String? = null,
    /** 黑名单用户实体同样是扁平用户对象，认证字段跟 uid / username 平级（样本里 2/2 都带） */
    @SerializedName("verify_status") val verifyStatus: Int? = null,
    @SerializedName("verify_icon") val verifyIcon: String? = null,
    val url: String? = null,
    val entityType: String? = null,
    val entityTemplate: String? = null,
    val entityId: String? = null,
    val extraData: String? = null,
) {
    /** 列表展示用名字：优先 displayUsername，其次 username，最后回落 uid */
    val name: String get() = displayUsername ?: username ?: uid.orEmpty()
}

/**
 * 拉黑状态查询 —— POST /v6/user/getLimitAction（form: uid）
 *
 * isBlackList = 1 表示已加入黑名单（实测：加入前 0、加入后 1）。
 */
data class LimitActionResponse(
    val data: LimitAction? = null,
)

data class LimitAction(
    val isBlackList: Int? = null,
    val isIgnoreList: Int? = null,
    val isLimitList: Int? = null,
)

/**
 * 加入 / 移出黑名单 —— POST /v6/user/{addTo,removeFrom}BlackList?uid=x（uid 在 query，body 为空）
 *
 * data 是提示文案（"加入黑名单列表成功" / "从列表黑名单移除成功"）。
 */
data class BlackListActionResponse(
    val data: String? = null,
    val message: String? = null,
)
