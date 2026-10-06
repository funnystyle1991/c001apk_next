package com.example.c001apk.ui.feed

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.c001apk.BR
import com.example.c001apk.adapter.ItemListener
import com.example.c001apk.constant.Constants
import com.example.c001apk.databinding.ItemFeedArticleImageBinding
import com.example.c001apk.databinding.ItemFeedArticleShareUrlBinding
import com.example.c001apk.databinding.ItemFeedArticleTextBinding
import com.example.c001apk.databinding.ItemFeedContentBinding
import com.example.c001apk.logic.model.FeedArticleContentBean
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.model.Like

class FeedDataAdapter(
    private val listener: ItemListener,
    feedDataList: List<HomeFeedResponse.Data>?,
    articleList: List<FeedArticleContentBean.Data>?,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var feedDataList: List<HomeFeedResponse.Data>? = feedDataList
    private var articleList: List<FeedArticleContentBean.Data>? = articleList

    /**
     * 详情回填：列表项直出首屏时 adapter 拿到的是预览 list 的引用，而 [FeedViewModel.handleFeedData]
     * 每次都新建 list，不重设这里首屏就永远停在预览数据上。
     */
    fun submit(
        feedDataList: List<HomeFeedResponse.Data>?,
        articleList: List<FeedArticleContentBean.Data>?,
    ) {
        this.feedDataList = feedDataList
        this.articleList = articleList
        notifyDataSetChanged()
    }

    class FeedViewHolder(val binding: ItemFeedContentBinding, val listener: ItemListener) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(data: HomeFeedResponse.Data?) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.setVariable(
                BR.likeData,
                Like(
                    data?.likenum ?: "0",
                    data?.userAction?.like ?: 0
                )
            )
            binding.setVariable(
                BR.followAuthor,
                // 列表项不下发这个字段（详情才有）：详情回来前当"未知"，按钮位转圈而不是错显"关注"
                data?.userAction?.followAuthor ?: Constants.FOLLOW_AUTHOR_UNKNOWN
            )
            binding.executePendingBindings()
        }
    }

    class TextViewHolder(val binding: ItemFeedArticleTextBinding, val listener: ItemListener) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(data: FeedArticleContentBean.Data?) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.textView.paint.isFakeBoldText =
                (bindingAdapterPosition in listOf(0, 1)) && data?.title == "true"
            binding.executePendingBindings()
        }
    }

    class ImageViewHolder(val binding: ItemFeedArticleImageBinding, val listener: ItemListener) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(data: FeedArticleContentBean.Data?) {
            binding.setVariable(BR.data, data)
            binding.executePendingBindings()
        }
    }

    class ShareUrlViewHolder(
        val binding: ItemFeedArticleShareUrlBinding,
        val listener: ItemListener
    ) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(data: FeedArticleContentBean.Data?) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {

            0 -> {
                val binding = ItemFeedContentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                with(binding.root.layoutParams) {
                    if (parent.context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                        && this is StaggeredGridLayoutManager.LayoutParams
                    )
                        isFullSpan = true
                }
                FeedViewHolder(binding, listener)
            }

            1 -> TextViewHolder(
                ItemFeedArticleTextBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ), listener
            )

            2 -> ImageViewHolder(
                ItemFeedArticleImageBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ), listener
            )

            3 -> ShareUrlViewHolder(
                ItemFeedArticleShareUrlBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ), listener
            )

            else -> throw IllegalArgumentException("invalid viewType: $viewType")
        }
    }

    override fun getItemCount(): Int {
        // 属性是 var（详情回填要整体换掉），先落到局部变量才能 smart cast
        val feeds = feedDataList
        val articles = articleList
        return if (feeds.isNullOrEmpty() && !articles.isNullOrEmpty()) articles.size
        else if (!feeds.isNullOrEmpty() && articles.isNullOrEmpty()) feeds.size
        else 0
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is FeedViewHolder -> holder.bind(feedDataList?.getOrNull(position))
            is TextViewHolder -> holder.bind(articleList?.getOrNull(position))
            is ImageViewHolder -> holder.bind(articleList?.getOrNull(position))
            is ShareUrlViewHolder -> holder.bind(articleList?.getOrNull(position))
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            if (payloads[0] == true) {
                with(holder as FeedViewHolder) {
                    binding.setVariable(
                        BR.likeData,
                        Like(
                            feedDataList?.getOrNull(0)?.likenum ?: "0",
                            feedDataList?.getOrNull(0)?.userAction?.like ?: 0
                        )
                    )
                    binding.setVariable(
                        BR.followAuthor,
                        feedDataList?.getOrNull(0)?.userAction?.followAuthor
                            ?: Constants.FOLLOW_AUTHOR_UNKNOWN
                    )
                    binding.executePendingBindings()
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val articles = articleList
        return if (articles.isNullOrEmpty()) 0
        else when (articles[position].type) {
            "text" -> 1
            "image" -> 2
            "shareUrl" -> 3
            else -> throw IllegalArgumentException("invalid article type: ${articles[position].type}")
        }
    }

}