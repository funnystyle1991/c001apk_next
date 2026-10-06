package com.example.c001apk.ui.topic

/** 话题页头部卡片数据 */
data class TopicHeader(
    val logo: String?,
    val title: String?,
    val hotNum: String?,
    val commentNum: String?,
    /** 话题 / 机型简介，没有就整行不显示 */
    val intro: String?,
    val followNum: String?,
    val avatars: List<String>,
)
