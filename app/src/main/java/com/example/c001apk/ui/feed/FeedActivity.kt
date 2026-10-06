package com.example.c001apk.ui.feed

import android.annotation.SuppressLint
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import com.example.c001apk.R
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.ui.base.BaseViewActivity
import com.example.c001apk.ui.feed.question.FeedQuestionFragment
import com.example.c001apk.ui.feed.vote.FeedVoteFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

@AndroidEntryPoint
class FeedActivity : BaseViewActivity<FeedViewModel>() {

    override val viewModel by viewModels<FeedViewModel>(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<FeedViewModel.Factory> { factory ->
                factory.create(
                    intent.getStringExtra("id").orEmpty(),
                    intent.getStringExtra("rid"),
                    intent.getBooleanExtra("viewReply", false),
                )
            }
        }
    )

    override fun getSavedData(savedInstanceState: Bundle?) {
        if (viewModel.feedData == null) {
            // 优先用重建前保存的（可能已经是详情补全后的），其次才是列表项带来的首屏数据
            viewModel.feedData = if (SDK_INT >= 33)
                savedInstanceState?.getParcelable("feedData", HomeFeedResponse.Data::class.java)
                    ?: intent.getParcelableExtra("feedData", HomeFeedResponse.Data::class.java)
            else
                savedInstanceState?.getParcelable("feedData")
                    ?: intent.getParcelableExtra("feedData")
        }
    }

    override fun initData() {
        if (viewModel.isAInit) {
            viewModel.isAInit = false
            val saved = viewModel.feedData
            // 投票/问答详情首屏强依赖详情独有字段，列表项顶不上，仍旧走整页转圈
            if (saved != null && saved.feedType !in listOf("vote", "question")) {
                // 列表项直出首屏：先把手上有的内容挂上去，不等详情，省掉整页转圈
                viewModel.isPreview = true
                viewModel.handleFeedData()
                beginTransaction()
                // 不走 Loading 状态：一是别让整页转圈盖住首屏，二是 initData 早于 initObserve，
                // 发状态会变成粘性事件让 observer 再调一次 beginTransaction（事务提交两次）
                binding.indicator.parent.isVisible = false
                fetchData()
            } else {
                viewModel.activityState.value = LoadingState.Loading
            }
        }
    }

    override fun fetchData() {
        viewModel.fetchFeedData()
    }

    @SuppressLint("CommitTransaction")
    override fun beginTransaction() {
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    when (viewModel.feedType) {
                        "vote" -> FeedVoteFragment()
                        "question" -> FeedQuestionFragment()
                        else -> FeedFragment()
                    }
                )
                .commit()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.feedData?.let {
            outState.putParcelable("feedData", it)
        }
        super.onSaveInstanceState(outState)
    }

}