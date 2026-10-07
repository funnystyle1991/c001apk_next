package com.example.c001apk.ui.messagedetail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.setPadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.BR
import com.example.c001apk.R
import com.example.c001apk.adapter.ItemListener
import com.example.c001apk.databinding.ItemMessageContentBinding
import com.example.c001apk.databinding.ItemMessageItemBinding
import com.example.c001apk.databinding.ItemMessageUserBinding
import com.example.c001apk.logic.model.MessageResponse
import com.example.c001apk.ui.feed.FeedActivity
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.MessageKit
import com.example.c001apk.util.dp


class MessageContentAdapter(
    private val type: String,
    private val listener: ItemListener
) : ListAdapter<MessageResponse.Data, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    class UserViewHolder(
        val binding: ItemMessageUserBinding,
        val type: String,
        val listener: ItemListener
    ) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(data: MessageResponse.Data) {
            binding.setVariable(BR.type, type)
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.executePendingBindings()

            if (type == "list") {
                // 私信会话项：点进去开聊天页（ukey 是会话标识，uid/uname/avatar 都是对方）
                itemView.setOnClickListener {
                    IntentUtil.startActivity<MessageDetailActivity>(itemView.context) {
                        putExtra("ukey", data.ukey.orEmpty())
                        putExtra("uid", MessageKit.partnerUid(data))
                        putExtra("uname", MessageKit.partnerName(data))
                        putExtra("avatar", MessageKit.partnerAvatar(data))
                        // 兜底：实测小秘书不在 /v6/message/list 里（它的提醒都在通知列表），
                        // 万一哪天进来一个同名的，也别给输入框
                        putExtra("readOnly", MessageKit.isSecretary(data))
                    }
                }
            }
        }

    }

    /**
     * 通知条目（小秘书 / 我的回复）：复用消息中心那套渲染
     * （头像 + 昵称 + note 富文本 + 时间，点击开 note 里的链接）。
     */
    class NotifyViewHolder(
        val binding: ItemMessageItemBinding,
        val listener: ItemListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: MessageResponse.Data) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.executePendingBindings()
        }
    }

    class MessageViewHolder(
        val binding: ItemMessageContentBinding,
        val type: String,
        val listener: ItemListener
    ) :
        RecyclerView.ViewHolder(binding.root) {
        var id: String = ""

        var fid: String = ""

        /**
         * 被赞的那条评论（只有「赞了你的评论」有值），详情页靠它滚到评论区定位。
         *
         * 评论点赞条目的 `target_id` 是评论 id，不能当动态 id 用（会开成「文章已被删除」）；
         * 归一化时动态 id 落 [fid]、评论 id 落 `rid`（都取自条目自带的 url，见
         * [NotificationV18Kit.toLikeMessage]）。
         */
        var rid: String = ""

        init {
            itemView.setOnClickListener {
                val target = if (type == "feedLike") fid else id
                if (target.isBlank()) return@setOnClickListener
                IntentUtil.startActivity<FeedActivity>(itemView.context) {
                    putExtra("id", target)
                    if (rid.isNotBlank()) {
                        putExtra("rid", rid)
                        putExtra("viewReply", true)
                    }
                }
            }
        }

        fun bind(data: MessageResponse.Data) {
            id = data.id
            fid = data.fid.orEmpty()
            rid = data.rid?.takeIf { it > 0 }?.toString().orEmpty()
            binding.setVariable(BR.type, type)
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            // 布局里「是不是我收到的赞」的判定走这个 Boolean。布局里原来直接写
            // `type == `feedLike``，那是引用比较：type 是从 Intent 来的运行时字符串、
            // 没被 intern，跟字面量永远不是同一个对象，三元表达式恒走 else 分支，
            // 于是「我收到的赞」页面把动态作者（自己）当成了点赞人。
            binding.setVariable(BR.isFeedLike, type == "feedLike")

            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            0 -> {
                MessageViewHolder(
                    ItemMessageContentBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), type, listener
                )
            }


            1 -> {
                UserViewHolder(
                    ItemMessageUserBinding.inflate(
                        LayoutInflater.from(parent.context), parent,
                        false
                    ), type, listener
                )
            }

            2 -> {
                val binding = ItemMessageItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent,
                    false
                )
                // 跟消息中心的通知列表同一套卡片样式，两处看起来是一回事
                binding.root.setPadding(10.dp)
                binding.root.background = parent.context.getDrawable(R.drawable.round_corners_12)
                binding.root.foreground =
                    parent.context.getDrawable(R.drawable.selector_bg_12_trans)
                NotifyViewHolder(binding, listener)
            }

            else -> throw IllegalArgumentException("invalid type")
        }

    }


    override fun getItemViewType(position: Int): Int {
        return when (type) {
            "atMe" -> 0
            "atCommentMe" -> 0
            "feedLike" -> 0
            "contactsFollow" -> 1
            "list" -> 1
            // 小秘书 / 我的回复都是通知条目，走同一套卡片
            "secretary" -> 2
            "commentMe" -> 2
            else -> throw IllegalArgumentException("invalid type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {

            is UserViewHolder -> {
                holder.bind(currentList[position])
            }

            is NotifyViewHolder -> {
                holder.bind(currentList[position])
            }


            is MessageViewHolder -> {
                holder.bind(currentList[position])
            }
        }
    }

}

class MessageDiffCallback : DiffUtil.ItemCallback<MessageResponse.Data>() {
    override fun areItemsTheSame(
        oldItem: MessageResponse.Data,
        newItem: MessageResponse.Data
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(
        oldItem: MessageResponse.Data,
        newItem: MessageResponse.Data
    ): Boolean {
        return oldItem.id == newItem.id
    }
}