package com.example.c001apk.ui.feed

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.c001apk.BR
import com.example.c001apk.R
import com.example.c001apk.adapter.ItemListener
import com.example.c001apk.databinding.ItemFeedContentReplyItemBinding
import com.example.c001apk.logic.model.Like
import com.example.c001apk.logic.model.TotalReplyResponse
import com.example.c001apk.util.SpannableStringBuilderUtil
import com.example.c001apk.util.dp
import com.example.c001apk.view.LinkMovementClickMethod
import com.google.android.material.color.MaterialColors

class FeedReplyAdapter(
    private val listener: ItemListener
) :
    ListAdapter<TotalReplyResponse.Data, FeedReplyAdapter.ViewHolder>(FeedReplyDiffCallback()) {

    class ViewHolder(val binding: ItemFeedContentReplyItemBinding, val listener: ItemListener) :
        RecyclerView.ViewHolder(binding.root) {
        var id: String = ""
        var uid: String = ""
        var username: String = ""
        var message: String = ""

        init {
            itemView.setOnClickListener {
                listener.onReply(
                    id, uid, uid, username,
                    bindingAdapterPosition, null
                )
            }

            binding.expand.setOnClickListener {
                listener.onExpand(
                    it, id, uid,
                    message, bindingAdapterPosition, null
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFeedContentReplyItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        binding.root.apply {
            if (layoutParams is StaggeredGridLayoutManager.LayoutParams) {
                background = parent.context.getDrawable(R.drawable.text_card_bg)
                foreground = parent.context.getDrawable(R.drawable.selector_bg_12_trans)
                setPadding(10.dp)
            } else {
                foreground = parent.context.getDrawable(R.drawable.selector_bg_trans)
                setPadding(15.dp, 12.dp, 15.dp, 12.dp)
            }
        }
        return ViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reply = currentList[position]

        holder.id = reply.id
        holder.uid = reply.uid
        holder.username = reply.userInfo.username
        holder.message = reply.message

        holder.binding.setVariable(BR.data, reply)
        holder.binding.setVariable(BR.listener, listener)
        holder.binding.setVariable(
            BR.likeData, Like(
                reply.likenum,
                reply.userAction?.like ?: 0
            )
        )

        bindReplyRows(holder)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            when (payloads[0]) {
                "like" -> {
                    holder.binding.likeData = Like(
                        currentList[position].likenum,
                        currentList[position].userAction?.like ?: 0
                    )
                    holder.binding.executePendingBindings()
                }

                "reply" -> {
                    bindReplyRows(holder)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindReplyRows(
        holder: ViewHolder
    ) {
        val reply = currentList[holder.bindingAdapterPosition]
        holder.binding.apply {
            replyLayout.isVisible =
                if (reply.replyRows == null) false
                else if (reply.replyRows?.isEmpty() == true && reply.replyRowsMore == 0) false
                else true

            linearLayout.removeAllViews()
            reply.replyRows?.let { replyRows ->
                if (holder.itemView.layoutParams is StaggeredGridLayoutManager.LayoutParams) {
                    replyLayout.setCardBackgroundColor(
                        MaterialColors.getColor(
                            holder.itemView.context,
                            android.R.attr.windowBackground,
                            0
                        )
                    )
                }
                val context = holder.itemView.context
                replyRows.forEachIndexed { index, replyData ->
                    val view = LayoutInflater.from(context).inflate(
                        R.layout.item_feed_content_reply_to_reply_item, linearLayout, false
                    )
                    view.findViewById<TextView>(R.id.reply).apply {
                        movementMethod = LinkMovementClickMethod.instance
                        highlightColor = ColorUtils.setAlphaComponent(
                            MaterialColors.getColor(
                                context,
                                androidx.appcompat.R.attr.colorPrimaryDark,
                                0
                            ), 128
                        )
                        text = SpannableStringBuilderUtil.setText(
                            context,
                            // 回复不铺图：九宫格会把卡片撑得老长，带图时按全站一贯的写法在正文后
                            // 挂一个「查看图片(N)」，点它才全屏看图（见 withPicLink）
                            replyData.message.withPicLink(replyData.pic, replyData.picArr),
                            textSize,
                            replyData.picArr
                        ) {
                            this@FeedReplyAdapter.listener.showTotalReply(
                                reply.id,
                                reply.uid,
                                holder.bindingAdapterPosition,
                                null,
                                true
                            )
                        }
                    }
                    view.setOnClickListener {
                        this@FeedReplyAdapter.listener.onReply(
                            replyData.id, reply.uid, replyData.uid, replyData.username,
                            holder.bindingAdapterPosition, index
                        )
                    }
                    view.setOnLongClickListener {
                        this@FeedReplyAdapter.listener.onExpand(
                            it, replyData.id, replyData.uid,
                            replyData.message, holder.bindingAdapterPosition, index
                        )
                        true
                    }
                    linearLayout.addView(view)
                }
            }

            if (reply.replyRowsMore != 0) {
                totalReply.apply {
                    isVisible = true
                    text = "查看更多回复(${reply.replynum})"
                    setOnClickListener {
                        this@FeedReplyAdapter.listener.showTotalReply(
                            reply.id, reply.uid,
                            holder.bindingAdapterPosition,
                            null
                        )
                    }
                }
            } else
                totalReply.isVisible = false

            executePendingBindings()
        }
    }

}

/**
 * 给带图的回复正文接上「查看图片(N)」链接，写法与首页卡片热评、回复列表一致：
 * href 是 coolapk 图片地址，SpannableStringBuilderUtil 会把 picArr 一并交给 MyURLSpan，
 * 于是点一下就是这组图的全屏看图（一张和多张走同一条路）。
 * 服务端给纯图回复下发的正文就是占位符「[图片]」（会被 EmojiUtils 渲染成一个小图标），
 * 那种情况不再补第二个图标；正文另有文字时才加图标做提示。
 */
private fun String.withPicLink(pic: String?, picArr: List<String>?): String {
    if (picArr.isNullOrEmpty()) return this
    val url = pic?.takeIf { it.isNotEmpty() } ?: picArr[0]
    val icon = if (trim() == "[图片]") "" else "[图片] "
    return "$this $icon<a class=\"feed-forward-pic\" href=$url>查看图片(${picArr.size})</a>"
}

class FeedReplyDiffCallback : DiffUtil.ItemCallback<TotalReplyResponse.Data>() {
    override fun areItemsTheSame(
        oldItem: TotalReplyResponse.Data,
        newItem: TotalReplyResponse.Data
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(
        oldItem: TotalReplyResponse.Data,
        newItem: TotalReplyResponse.Data
    ): Boolean {
        return oldItem.likenum == newItem.likenum && oldItem.lastupdate == newItem.lastupdate
    }

    override fun getChangePayload(
        oldItem: TotalReplyResponse.Data,
        newItem: TotalReplyResponse.Data
    ): Any? {
        return if (oldItem.likenum != newItem.likenum) "like"
        else if (oldItem.lastupdate != newItem.lastupdate) "reply"
        else null
    }
}