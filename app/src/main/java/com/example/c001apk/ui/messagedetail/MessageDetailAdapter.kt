package com.example.c001apk.ui.messagedetail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.BR
import com.example.c001apk.databinding.ItemMessageChatBinding
import com.example.c001apk.logic.model.MessageResponse

/**
 * 聊天记录列表。气泡靠左还是靠右，由消息的 fromuid 是否等于当前登录 uid 决定
 * （`/v6/message/chat` 里 fromuid = 发送者、uid = 接收者）。
 *
 * 头像不用消息自身的字段：聊天记录里的 `userAvatar` / `fromUserAvatar` 对应的是
 * 接收者 / 发送者，方向容易反，所以直接由会话信息传进来（跟桌面版一致）。
 */
class MessageDetailAdapter(
    private val myUid: String,
    private val myAvatar: String,
    private val partnerAvatar: String
) : ListAdapter<MessageResponse.Data, MessageDetailAdapter.ChatViewHolder>(ChatDiffCallback()) {

    class ChatViewHolder(
        val binding: ItemMessageChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            data: MessageResponse.Data,
            isMe: Boolean,
            myAvatar: String,
            partnerAvatar: String
        ) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.isMe, isMe)
            binding.setVariable(BR.myAvatar, myAvatar)
            binding.setVariable(BR.partnerAvatar, partnerAvatar)
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        return ChatViewHolder(
            ItemMessageChatBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val item = currentList[position]
        holder.bind(item, item.fromuid == myUid, myAvatar, partnerAvatar)
    }

}

class ChatDiffCallback : DiffUtil.ItemCallback<MessageResponse.Data>() {
    /**
     * 聊天记录的主键是 `entityId`；`id` 在部分返回里是空的，真为空时退化成
     * 「时间 + 发送者 + 内容」，最差也不会把整页消息判成同一条。
     */
    private fun key(item: MessageResponse.Data): String =
        item.entityId ?: item.id ?: "${item.dateline}-${item.fromuid}-${item.message}"

    override fun areItemsTheSame(
        oldItem: MessageResponse.Data,
        newItem: MessageResponse.Data
    ): Boolean = key(oldItem) == key(newItem)

    override fun areContentsTheSame(
        oldItem: MessageResponse.Data,
        newItem: MessageResponse.Data
    ): Boolean = oldItem == newItem
}
