package com.example.c001apk.ui.message

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.databinding.ItemMessageMessBinding
import com.example.c001apk.ui.messagedetail.MessageActivity
import com.example.c001apk.util.CookieUtil.atcommentme
import com.example.c001apk.util.CookieUtil.atme
import com.example.c001apk.util.CookieUtil.contacts_follow
import com.example.c001apk.util.CookieUtil.feedlike
import com.example.c001apk.util.CookieUtil.message
import com.example.c001apk.util.CookieUtil.notification
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.MessageCenterSeenStore
import com.example.c001apk.util.PrefManager


/**
 * 消息中心的入口宫格：@我的动态 / @我的评论 / 我收到的赞 / 好友关注 / 私信 / 酷安小秘书。
 *
 * 小秘书单独开一个入口是因为它**不是私信对象**：登录提醒和站内信都走通知接口
 * （`/v6/notification/list` 里 type=notify_xms），`/v6/message/list` 里根本没有它。
 *
 * 未读数直接读 CookieUtil（进页面时 /v6/notification/checkCount 刷过一遍）。
 */
class MessageThirdAdapter : RecyclerView.Adapter<MessageThirdAdapter.ThirdViewHolder>() {

    private val messTitle =
        listOf("@我的动态", "@我的评论", "我收到的赞", "好友关注", "私信", "酷安小秘书")
    private val logoColorList =
        listOf("#2196f3", "#00bcd4", "#4caf50", "#f44336", "#ff9800", "#9c27b0")
    private val logoList = listOf(
        R.drawable.ic_at, R.drawable.ic_comment, R.drawable.ic_thumb,
        R.drawable.ic_add, R.drawable.ic_message1, R.drawable.ic_notification_bell
    )

    /** 这一轮里用户已经点进去看过的格子：红点先撤掉，免得要等回到本页才消失 */
    private val tapped = mutableSetOf<Int>()

    @SuppressLint("NotifyDataSetChanged")
    fun updateBadge() {
        tapped.clear()
        notifyDataSetChanged()
    }

    /**
     * 某一格的未读数，0 表示不画红点。
     *
     * 红点由 [MessageBadgeDecoration] 在 RecyclerView 顶层绘制，这里只算数字、不碰 View：
     * 数字画在 Canvas 上才不会随 item 一起被裁或被相邻格子盖住。
     */
    fun unreadCount(position: Int): Int {
        if (position in tapped) return 0

        val server = when (position) {
            0 -> atme ?: 0
            1 -> atcommentme ?: 0
            2 -> feedlike ?: 0
            3 -> contacts_follow ?: 0
            4 -> message ?: 0
            // 小秘书的红点只能借用「通知未读」：接口没有按 notify_xms 单独给计数
            else -> notification
        }

        // 下方未读列表里展示过的条目在本机已算已读，红点要跟着抵消，
        // 否则看完了红点还挂着（服务端没有逐条已读接口，只能本机记账）
        val category = when (position) {
            0 -> "atMe"
            1 -> "atCommentMe"
            2 -> "feedLike"
            3 -> "contactsFollow"
            // 私信（4）的未读是会话级的，不在这里抵消
            5 -> "secretary"
            else -> null
        }
        return if (category == null) server else MessageCenterSeenStore.unreadOf(category, server)
    }

    inner class ThirdViewHolder(val binding: ItemMessageMessBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            if (PrefManager.isLogin) {
                itemView.setOnClickListener {
                    // 红点由 decoration 画，先把它记成「点过」，跳转前就撤掉
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        tapped.add(position)
                        notifyItemChanged(position)
                    }
                    // 这里不再把 CookieUtil 的未读数本地清零：红点统一由
                    // 「服务端未读数 − 本机已读账本」算出来，点进分类页看过的那些
                    // 会在分类页里记账（MessageViewModel.markSeen），返回后红点自然消掉
                    IntentUtil.startActivity<MessageActivity>(itemView.context) {
                        when (binding.title.text) {
                            "@我的动态" -> putExtra("type", "atMe")
                            "@我的评论" -> putExtra("type", "atCommentMe")
                            "我收到的赞" -> putExtra("type", "feedLike")
                            "好友关注" -> putExtra("type", "contactsFollow")
                            "私信" -> putExtra("type", "list")
                            "酷安小秘书" -> putExtra("type", "secretary")
                        }
                    }
                }
            }
        }
    }

    override fun getItemViewType(position: Int) = position

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ThirdViewHolder {
        val binding =
            ItemMessageMessBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.title.text = messTitle[position]
        binding.logoCover.setBackgroundColor(Color.parseColor(logoColorList[position]))
        binding.logo.setBackgroundDrawable(parent.context.getDrawable(logoList[position]))
        return ThirdViewHolder(binding)
    }

    override fun getItemCount() = messTitle.size

    override fun onBindViewHolder(holder: ThirdViewHolder, position: Int) {
        // 红点改由 MessageBadgeDecoration 在顶层画，item 里没有随数据变化的东西
    }
}
