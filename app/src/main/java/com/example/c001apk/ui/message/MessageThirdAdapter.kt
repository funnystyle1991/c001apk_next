package com.example.c001apk.ui.message

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
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

    @SuppressLint("NotifyDataSetChanged")
    fun updateBadge() {
        notifyDataSetChanged()
    }

    inner class ThirdViewHolder(val binding: ItemMessageMessBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            if (PrefManager.isLogin) {
                itemView.setOnClickListener {
                    binding.badge.isVisible = false
                    IntentUtil.startActivity<MessageActivity>(itemView.context) {
                        when (binding.title.text) {
                            "@我的动态" -> {
                                atme = null
                                putExtra("type", "atMe")
                            }

                            "@我的评论" -> {
                                atcommentme = null
                                putExtra("type", "atCommentMe")
                            }

                            "我收到的赞" -> {
                                feedlike = null
                                putExtra("type", "feedLike")
                            }

                            "好友关注" -> {
                                contacts_follow = null
                                putExtra("type", "contactsFollow")
                            }

                            "私信" -> putExtra("type", "list")

                            "酷安小秘书" -> {
                                notification = 0
                                putExtra("type", "secretary")
                            }
                        }
                    }
                }
            }
        }

        fun bind() {
            val count = when (bindingAdapterPosition) {
                0 -> atme ?: 0
                1 -> atcommentme ?: 0
                2 -> feedlike ?: 0
                3 -> contacts_follow ?: 0
                4 -> message ?: 0
                // 小秘书的红点只能借用「通知未读」：接口没有按 notify_xms 单独给计数
                else -> notification
            }
            binding.badge.text = if (count > 99) "99+" else count.toString()
            binding.badge.isVisible = count > 0
            binding.executePendingBindings()
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
        holder.bind()
    }
}
