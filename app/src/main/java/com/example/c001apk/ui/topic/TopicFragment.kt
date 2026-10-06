package com.example.c001apk.ui.topic

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.c001apk.R
import com.example.c001apk.databinding.ItemTopicHeaderBinding
import com.example.c001apk.ui.base.BasePagerFragment
import com.example.c001apk.ui.feed.reply.ReplyActivity
import com.example.c001apk.ui.home.IOnTabClickListener
import com.example.c001apk.ui.others.WebViewFragment
import com.example.c001apk.ui.search.SearchActivity
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.ReplaceViewHelper
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.GRAVITY_CENTER
import com.google.android.material.tabs.TabLayout.MODE_SCROLLABLE
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TopicFragment : BasePagerFragment() {

    private val viewModel by viewModels<TopicViewModel>(ownerProducer = { requireActivity() })
    override var tabController: IOnTabClickListener? = null
    private lateinit var subscribe: MenuItem
    private var menuBlock: MenuItem? = null
    private var headerBinding: ItemTopicHeaderBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initSelected()
        if (PrefManager.isLogin)
            initFab()
        initObserve()
    }

    override fun initFab() {
        super.initFab()
        fab.setOnClickListener {
            // 机型页可以发动态，也可以发表点评（type=rating）
            if (viewModel.type == "product" && !viewModel.ratingItemInfo.isNullOrEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("发布")
                    .setItems(arrayOf("发布动态", "发表点评")) { _, which ->
                        startReply(if (which == 0) "createFeed" else "rating")
                    }
                    .show()
            } else
                startReply("createFeed")
        }
    }

    private fun startReply(type: String) {
        val intent = Intent(requireContext(), ReplyActivity::class.java)
        intent.putExtra("type", type)
        intent.putExtra(
            "targetType",
            if (viewModel.type == "topic") "tag" else "product_phone"
        )
        intent.putExtra("targetId", viewModel.id)
        if (viewModel.type == "topic")
            intent.putExtra("title", viewModel.title)
        if (type == "rating") {
            intent.putExtra("ratingTarget", viewModel.title)
            intent.putExtra("ratingItems", Gson().toJson(viewModel.ratingItemInfo))
        }
        val animationBundle = ActivityOptions.makeCustomAnimation(
            context,
            R.anim.anim_bottom_sheet_slide_up,
            R.anim.anim_bottom_sheet_slide_down
        ).toBundle()
        requireContext().startActivity(intent, animationBundle)
    }

    override fun onTabReselectedExtra() {
        if (fabBehavior.isScrolledDown)
            fabBehavior.slideUp(fab, true)
    }

    private fun initSelected() {
        viewModel.tabSelected?.let {
            binding.viewPager.setCurrentItem(it, false)
            viewModel.tabSelected = null
        }
    }

    private fun initObserve() {
        viewModel.blockState.observe(viewLifecycleOwner) { event ->
            event?.getContentIfNotHandledOrReturnNull()?.let {
                menuBlock?.title = if (it) "移除黑名单"
                else "加入黑名单"
            }
        }

        viewModel.followState.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                subscribe.title = if (it) "取消关注"
                else "关注"
                bindFollowBtn(it)
            }
        }

        viewModel.toastText.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.headerState.observe(viewLifecycleOwner) { header ->
            header?.let { bindHeader(it) }
        }
    }

    /**
     * 头部卡片铺在折叠标题栏里（复用 base_tablayout_viewpager 的占位 View）：
     * 展开时可见，向上滚动时随标题栏收起、TabLayout 吸顶。
     */
    private fun initHeader() {
        val header = ItemTopicHeaderBinding.inflate(layoutInflater, null, false)
        ReplaceViewHelper(requireContext()).toReplaceView(binding.view, header.root)
        header.root.layoutParams = CollapsingToolbarLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PARALLAX
        }
        header.root.isVisible = false
        headerBinding = header

        // 未登录不显示关注按钮（和工具栏菜单项保持一致）
        header.followBtn.isVisible = PrefManager.isLogin
        header.followBtn.setOnClickListener { onSubscribeClick() }
    }

    /**
     * 关注按钮的两态配色：未关注=主题色填充 / 已关注=弱化底色。
     * 用 MaterialColors 取主题属性，避免在 drawable 里写 ?attr（浅色深色都要能看）。
     */
    private fun bindFollowBtn(followed: Boolean) {
        val header = headerBinding ?: return
        val accent = MaterialColors.getColor(
            requireContext(), androidx.appcompat.R.attr.colorPrimary, 0
        )
        val onAccent = MaterialColors.getColor(
            requireContext(), com.google.android.material.R.attr.colorOnPrimary, 0
        )
        val muted = MaterialColors.getColor(
            requireContext(), com.google.android.material.R.attr.colorSurfaceVariant, 0
        )
        val onMuted = MaterialColors.getColor(
            requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0
        )
        header.followBtn.apply {
            text = if (followed) "已关注" else "关注"
            backgroundTintList =
                ColorStateList.valueOf(if (followed) muted else accent)
            setTextColor(if (followed) onMuted else onAccent)
        }
    }

    // 关注 / 取消关注的两种类型（话题 tag / 机型 product），菜单与头部按钮共用
    private fun onSubscribeClick() {
        when (viewModel.type) {
            "topic" -> {
                val followUrl =
                    if (viewModel.isFollow) "/v6/feed/unFollowTag"
                    else "/v6/feed/followTag"
                val tag = viewModel.url.replace("/t/", "")
                viewModel.onGetFollow(followUrl, tag, null)
            }

            "product" -> {
                if (viewModel.postFollowData.isNullOrEmpty())
                    viewModel.postFollowData = HashMap()
                viewModel.postFollowData?.let { map ->
                    map["id"] = viewModel.id
                    map["status"] = if (viewModel.isFollow) "0" else "1"
                }
                viewModel.onPostFollow()
            }

            else -> Toast.makeText(
                requireContext(),
                "type error: ${viewModel.type}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun bindHeader(header: TopicHeader) {
        val headerBinding = headerBinding ?: return
        headerBinding.root.isVisible = true

        ImageUtil.showIMG(headerBinding.logo, header.logo)
        headerBinding.title.text = header.title.orEmpty()

        // 服务端只下发数字（如「1.2万」），单位在本地点上
        val stats = listOfNotNull(
            header.hotNum?.takeIf { it.isNotEmpty() },
            header.commentNum?.takeIf { it.isNotEmpty() }?.let { "${it}讨论" },
        )
        headerBinding.stats.text = stats.joinToString(" · ")
        headerBinding.stats.isVisible = stats.isNotEmpty()

        val avatars = header.avatars.take(3)
        listOf(headerBinding.avatar1, headerBinding.avatar2, headerBinding.avatar3)
            .forEachIndexed { index, imageView ->
                val url = avatars.getOrNull(index)
                imageView.isVisible = !url.isNullOrEmpty()
                url?.let { ImageUtil.showIMG(imageView, it) }
            }
        headerBinding.followText.text =
            header.followNum?.takeIf { it.isNotEmpty() }?.let { "${it}人关注" }.orEmpty()
        headerBinding.followText.isVisible = headerBinding.followText.text.isNotEmpty()
        headerBinding.followRow.isVisible = avatars.isNotEmpty() || headerBinding.followText.isVisible

        // 头部按钮初值（checkFollow 之后还会通过 followState 再刷一次）
        bindFollowBtn(viewModel.isFollow)
    }

    override fun onDestroyView() {
        headerBinding = null
        super.onDestroyView()
    }

    override fun getFragment(position: Int): Fragment {
        val bean = viewModel.topicList?.getOrNull(position)
        val url = bean?.url.orEmpty()
        // H5 类型的 tab（如「活动详情」tab 的 url = https://m.coolapk.com/activity/<name>）
        // 用 WebView 渲染，否则走 dataList 接口会返回空导致页面空白
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return WebViewFragment.newInstance(url)
        }
        // 「参数」tab 走原生 dataList（productConfigList / listCard），
        // 版本配置行来自 product/detail 的 configRows，随 Fragment 传入
        val isParamsTab = bean?.pageName == "main" || bean?.title == "参数"
        return TopicContentFragment.newInstance(
            url,
            bean?.title.orEmpty(),
            if (isParamsTab && viewModel.type == "product")
                ArrayList(viewModel.configRows.orEmpty())
            else null,
        )
    }

    override fun initTabList() {
        binding.tabLayout.apply {
            tabGravity = GRAVITY_CENTER
            tabMode = MODE_SCROLLABLE
        }
        tabList = viewModel.topicList?.map { it.title } ?: emptyList()
    }

    override fun onBackClick() {
        activity?.finish()
    }

    override fun initBar() {
        super.initBar()
        binding.collapsingToolbar.isTitleEnabled = false
        initHeader()
        binding.toolBar.apply {
            title = if (viewModel.type == "topic") viewModel.url.replace("/t/", "")
            else viewModel.title
            viewModel.subtitle?.let { subtitle = it }

            inflateMenu(R.menu.topic_product_menu)

            menuBlock = menu.findItem(R.id.block)
            subscribe = menu.findItem(R.id.subscribe)
            subscribe.isVisible = PrefManager.isLogin

            viewModel.checkMenuState()

            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.search -> {
                        if (viewModel.type == "topic") {
                            IntentUtil.startActivity<SearchActivity>(requireContext()) {
                                putExtra("type", "topic")
                                putExtra("pageType", "tag")
                                putExtra("pageParam", viewModel.url.replace("/t/", ""))
                                putExtra("title", viewModel.url.replace("/t/", ""))
                            }
                        } else {
                            IntentUtil.startActivity<SearchActivity>(requireContext()) {
                                putExtra("type", "topic")
                                putExtra("pageType", "product_phone")
                                putExtra("pageParam", viewModel.id)
                                putExtra("title", viewModel.title)
                            }
                        }
                    }

                    R.id.block -> {
                        val isBlocked = menuBlock?.title.toString() == "移除黑名单"
                        MaterialAlertDialogBuilder(requireContext()).apply {
                            val title =
                                if (viewModel.type == "topic") viewModel.url
                                    .replace("/t/", "")
                                else viewModel.title
                            setTitle("确定将 $title ${menuBlock?.title}？")
                            setNegativeButton(android.R.string.cancel, null)
                            setPositiveButton(android.R.string.ok) { _, _ ->
                                viewModel.title.let { title ->
                                    menuBlock?.title = if (isBlocked) {
                                        viewModel.deleteTopic(title)
                                        "加入黑名单"
                                    } else {
                                        viewModel.saveTopic(title)
                                        "移除黑名单"
                                    }
                                }
                            }
                            show()
                        }
                    }

                    R.id.subscribe -> onSubscribeClick()

                }
                return@setOnMenuItemClickListener true
            }
        }
    }
}
