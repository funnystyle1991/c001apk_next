package com.example.c001apk.ui.messagedetail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.BR
import com.example.c001apk.databinding.ItemMessageChatBinding
import com.example.c001apk.logic.model.MessageResponse
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.MessageKit

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
    private val partnerAvatar: String,
    /**
     * 图片消息的地址要现问 `showImage` 换签名地址（CDN 的裸地址会被 auth_key 挡），
     * adapter 自己没有协程作用域，所以这件事交给页面 / ViewModel 做，结果回调回来。
     * 回调里除了地址还有图片原始宽高（气泡按它撑开），同一条消息只会问一次（ViewModel 有缓存）。
     */
    private val loadPic: (id: String, onReady: (MessageKit.MessagePic?) -> Unit) -> Unit
) : ListAdapter<MessageResponse.Data, MessageDetailAdapter.ChatViewHolder>(ChatDiffCallback()) {

    class ChatViewHolder(
        val binding: ItemMessageChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            data: MessageResponse.Data,
            isMe: Boolean,
            myAvatar: String,
            partnerAvatar: String,
            loadPic: (String, (MessageKit.MessagePic?) -> Unit) -> Unit
        ) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.isMe, isMe)
            binding.setVariable(BR.myAvatar, myAvatar)
            binding.setVariable(BR.partnerAvatar, partnerAvatar)
            binding.executePendingBindings()

            val view = if (isMe) binding.picRight else binding.picLeft
            val id = if (MessageKit.hasPic(data)) MessageKit.picId(data) else ""
            // 换地址是异步的，等回调回来时 holder 可能已经复用了，
            // 用 tag 记住当前这个 ImageView 摊到的是哪条消息，对不上就丢掉。
            view.tag = id
            view.setImageDrawable(null)
            // 复用过来时上一条消息的点击监听可能还在，先摘掉，免得点空图点开上一张
            view.setOnClickListener(null)
            if (id.isEmpty()) return
            loadPic(id) { pic ->
                if (view.tag == id && pic != null) {
                    // 按图片真实比例定气泡里的占位尺寸，再按同一个尺寸取图（不再死宽 180dp）
                    val (width, height) =
                        ImageUtil.chatImageSize(view.context, pic.width, pic.height)
                    view.layoutParams = view.layoutParams.apply {
                        this.width = width
                        this.height = height
                    }
                    ImageUtil.showChatIMG(view, pic.url, width, height)
                    // 点一下全屏看大图（long click 存图由 Mojito 那侧接管）
                    view.setOnClickListener {
                        ImageUtil.startChatBigImgViewSimple(view, pic.url)
                    }
                }
            }
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
        holder.bind(item, item.fromuid == myUid, myAvatar, partnerAvatar, loadPic)
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
