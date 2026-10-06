package com.example.c001apk.ui.feed

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.res.Configuration
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.c001apk.R
import com.example.c001apk.adapter.FooterAdapter
import com.example.c001apk.adapter.FooterState
import com.example.c001apk.adapter.HeaderAdapter
import com.example.c001apk.adapter.ItemListener
import com.example.c001apk.databinding.FragmentFeedBinding
import com.example.c001apk.logic.model.TotalReplyResponse
import com.example.c001apk.ui.base.BaseFragment
import com.example.c001apk.ui.feed.reply.ReplyActivity
import com.example.c001apk.ui.feed.reply.reply2reply.Reply2ReplyBottomSheetDialog
import com.example.c001apk.ui.feed.reply.reply2reply.ReplyRefreshListener
import com.example.c001apk.ui.others.CopyActivity
import com.example.c001apk.ui.others.WebViewActivity
import com.example.c001apk.util.ClipboardUtil
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.dp
import com.example.c001apk.util.makeToast
import com.example.c001apk.util.showPublishStatusDialog
import com.example.c001apk.view.StaggerItemDecoration
import com.example.c001apk.view.StickyItemDecorator
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs


@AndroidEntryPoint
class FeedFragment : BaseFragment<FragmentFeedBinding>() {

    private val viewModel by viewModels<FeedViewModel>(ownerProducer = { requireActivity() })
    private val isPortrait by lazy { resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
    private lateinit var feedDataAdapter: FeedDataAdapter
    private lateinit var feedReplyAdapter: FeedReplyAdapter
    private lateinit var feedFixAdapter: FeedFixAdapter
    private lateinit var footerAdapter: FooterAdapter
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var sLayoutManager: StaggeredGridLayoutManager
    private val fabViewBehavior by lazy { HideBottomViewOnScrollBehavior<FloatingActionButton>() }
    private var dialog: AlertDialog? = null
    private var isShowReply = false
    private lateinit var intentActivityResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PrefManager.isLogin) {
            intentActivityResultLauncher =
                registerForActivityResult(ActivityResultContracts.StartActivityForResult())
                { result: ActivityResult ->
                    if (result.resultCode == RESULT_OK) {
                        val data = if (SDK_INT >= 33)
                            result.data?.getParcelableExtra(
                                "response_data", TotalReplyResponse.Data::class.java
                            )
                        else
                            result.data?.getParcelableExtra("response_data")
                        data?.let {
                            viewModel.updateReply(it)
                            Toast.makeText(requireContext(), "回复成功", Toast.LENGTH_SHORT).show()
                            if (viewModel.type == "feed") {
                                if (isPortrait)
                                    mLayoutManager.scrollToPositionWithOffset(
                                        viewModel.itemCount,
                                        0
                                    )
                                else
                                    sLayoutManager.scrollToPositionWithOffset(0, 0)
                            }
                        }
                    }
                }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var height = 0
        ViewCompat.setOnApplyWindowInsetsListener(binding.reply) { _, insets ->
            height = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            insets
        }

        binding.tabLayout.post {
            initView(binding.swipeRefresh.height - binding.tabLayout.height - height)
            initToolBar()
            initData()
            initRefresh()
            initScroll()
            initReplyBtn(height)
            initObserve()
        }

    }

    private fun initRefresh() {
        binding.swipeRefresh.apply {
            setColorSchemeColors(
                MaterialColors.getColor(
                    requireContext(),
                    androidx.appcompat.R.attr.colorPrimary,
                    0
                )
            )
            setOnRefreshListener {
                if (!viewModel.isLoadMore) {
                    binding.swipeRefresh.isRefreshing = true
                    refreshData()
                }
            }
        }
    }

    private fun initReplyBtn(height: Int) {
        if (PrefManager.isLogin) {
            binding.reply.apply {
                isVisible = true
                layoutParams = CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    behavior = fabViewBehavior
                    setMargins(0, 0, 25.dp, 25.dp + height)
                }
                setOnClickListener {
                    viewModel.rid = viewModel.id
                    viewModel.ruid = viewModel.feedUid
                    viewModel.uname = viewModel.funame
                    viewModel.type = "feed"
                    launchReply()
                }
            }
        } else
            binding.reply.isVisible = false
    }

    private fun initScroll() {
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    lastVisibleItemPosition =
                        if (isPortrait) mLayoutManager.findLastVisibleItemPosition()
                        else sLayoutManager.findLastVisibleItemPositions(null).max()

                    if (lastVisibleItemPosition + 1 == binding.recyclerView.adapter?.itemCount
                        && !viewModel.isEnd && !viewModel.isRefreshing && !viewModel.isLoadMore
                        && !binding.swipeRefresh.isRefreshing
                    ) {
                        loadMore()
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                firstVisibleItemPosition =
                    if (isPortrait) mLayoutManager.findFirstVisibleItemPosition()
                    else sLayoutManager.findFirstVisibleItemPositions(null).min()
                // 内容里的作者行被顶掉多少，顶栏作者行就往上滑多少：滑到 1 时正好落到标题的位置。
                // 不写成"过了阈值就切"，是因为要跟着手指走（活动页那种顶掉+挪位），不是到点瞬切
                val progress =
                    if (firstVisibleItemPosition in (0..1)) scrollYDistance / titleSwitchDistance
                    else 1f
                applyTitleSwitch(progress)
            }
        })
    }

    private fun loadMore() {
        viewModel.isLoadMore = true
        viewModel.fetchFeedReply()
    }

    private fun initObserve() {
        viewModel.feedDataUpdateState.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                // 详情回来了：首屏那批是列表项直出的，adapter 还持有预览 list 的引用，必须换掉
                feedDataAdapter.submit(
                    viewModel.feedDataList,
                    viewModel.articleList,
                    viewModel.articleHeader
                )
                // 图文的内容项数从 1 变成 N，装饰器按新 itemCount 重算 offsets，
                // 否则正文会按预览期的边界渲染（顶到屏幕边、排序 tab 提前吸附）
                binding.recyclerView.invalidateItemDecorations()
            }
        }

        viewModel.feedUserState.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if (it)
                    feedDataAdapter.notifyItemChanged(0, true)
            }
        }

        viewModel.toastText.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.footerState.observe(viewLifecycleOwner) {
            footerAdapter.setLoadState(it)
            if (it !is FooterState.Loading) {
                binding.swipeRefresh.isRefreshing = false
                binding.indicator.parent.isIndeterminate = false
                binding.indicator.parent.isVisible = false
            }
            if (dialog != null) {
                dialog?.dismiss()
                dialog = null
            }
        }

        viewModel.feedReplyData.observe(viewLifecycleOwner) {
            viewModel.listSize = it.size
            // 数据加载后才知道作者，只有自己的动态才能改可见性
            binding.toolBar.menu.findItem(R.id.publishStatus)?.isVisible =
                PrefManager.isLogin && PrefManager.uid == viewModel.feedUid
            feedReplyAdapter.submitList(it)
            if (viewModel.isViewReply) {
                viewModel.isViewReply = false
                if (firstVisibleItemPosition > viewModel.itemCount && ::mLayoutManager.isInitialized)
                    mLayoutManager.scrollToPositionWithOffset(viewModel.itemCount, 0)
            }
        }

    }

    private fun scrollToPosition(position: Int) {
        binding.recyclerView.scrollToPosition(position)
    }

    private fun initData() {
        if (viewModel.isInit) {
            viewModel.isInit = false
            refreshData()
        }
    }

    private fun refreshData() {
        firstVisibleItemPosition = 0
        lastVisibleItemPosition = 0
        viewModel.firstItem = null
        viewModel.lastItem = null
        viewModel.page = 1
        viewModel.isEnd = false
        viewModel.isRefreshing = true
        viewModel.isLoadMore = false
        viewModel.fetchFeedReply()
    }

    @SuppressLint("SetTextI18n")
    private fun initView(height: Int) {
        // 作者行滑进顶栏之前，有半截还在顶栏下边；CoordinatorLayout 里 contentLayout 是后添加的、
        // 默认画在 appBar 之上，不把 appBar 提到最前，那半截就被上面的列表盖住了
        // （binding.root 的静态类型是 ViewBinding.root: View，够不着 bringChildToFront，得先落到 ViewGroup）
        val root = binding.root as ViewGroup
        root.bringChildToFront(binding.appBar)
        // 顶栏不裁子 View 之后，标题滑出顶栏的那一截得靠 topMask 压住，所以它要画在 appBar 之上；
        // 高度取"顶栏内容区以上的空白"（一般是状态栏那一带），没有空白就保持 0
        binding.topMask.layoutParams.height = binding.appBar.top + binding.toolBar.top
        binding.topMask.requestLayout()
        root.bringChildToFront(binding.topMask)
        feedDataAdapter = FeedDataAdapter(
            ItemClickListener(),
            viewModel.feedDataList,
            viewModel.articleList,
            viewModel.articleHeader
        )
        feedReplyAdapter = FeedReplyAdapter(ItemClickListener())
        feedFixAdapter =
            FeedFixAdapter(viewModel.replyCount.toString(), RefreshReplyListener())

        binding.apply {
            refreshListener = RefreshReplyListener()
            listener = ItemClickListener()
            username = viewModel.funame
            avatarUrl = viewModel.avatar
            dateline = viewModel.dateLine
            deviceTitle = viewModel.device
        }
        // 先把作者行摆到起点上：XML 里它是 gone，得先转成 invisible 排布出来，
        // 后面才量得到它的静态位置（也让首帧不会从兜底值跳一下）
        applyTitleSwitch(0f)
        footerAdapter = FooterAdapter(ReloadListener(), height)

        binding.replyCount.text = "共 ${viewModel.replyCount} 回复"
        setListType()

        binding.recyclerView.apply {
            adapter =
                ConcatAdapter(
                    HeaderAdapter(),
                    feedDataAdapter,
                    feedFixAdapter,
                    feedReplyAdapter,
                    footerAdapter
                )
            layoutManager =
                if (isPortrait) {
                    mLayoutManager = LinearLayoutManager(requireContext())
                    mLayoutManager
                } else {
                    binding.tabLayout.isVisible = false
                    sLayoutManager =
                        StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                    sLayoutManager
                }
            if (viewModel.isViewReply) {
                viewModel.isViewReply = false
                if (isPortrait) {
                    footerAdapter.setLoadState(FooterState.LoadingReply)
                    scrollToPosition(viewModel.itemCount)
                } else {
                    footerAdapter.setLoadState(FooterState.Loading)
                }
            } else {
                footerAdapter.setLoadState(FooterState.Loading)
            }
            if (itemDecorationCount == 0)
                if (isPortrait)
                    addItemDecoration(
                        StickyItemDecorator(requireContext(), 1, { viewModel.itemCount },
                            object : StickyItemDecorator.SortShowListener {
                                override fun showSort(show: Boolean) {
                                    binding.tabLayout.isVisible = show
                                }
                            })
                    )
                else
                    addItemDecoration(StaggerItemDecoration(10.dp))
        }
    }

    private fun initToolBar() {
        binding.toolBar.apply {
            // 导航键是 setNavigationIcon 运行时 addSystemView 挂上去的，默认画在 titleProfile 之上；
            // 作者行滑进来的半路正好从返回键那一带过，不把它提到最前就会被键压着
            bringChildToFront(binding.titleProfile)
            title = viewModel.feedTypeName
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener {
                activity?.finish()
            }
            setOnClickListener {
                binding.recyclerView.stopScroll()
                scrollToPosition(0)
            }
            inflateMenu(R.menu.feed_menu)

            menu.findItem(R.id.showReply).isVisible = isPortrait
            menu.findItem(R.id.report).isVisible = PrefManager.isLogin
            menu.findItem(R.id.showQuestion).isVisible = viewModel.feedType == "answer"

            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.showQuestion -> {
                        viewModel.feedDataList?.getOrNull(0)?.fid?.let {
                            IntentUtil.startActivity<FeedActivity>(requireContext()) {
                                putExtra("id", it)
                            }
                        }
                    }

                    R.id.showReply -> {
                        binding.recyclerView.stopScroll()
                        if (firstVisibleItemPosition <= viewModel.itemCount - 1)
                            mLayoutManager.scrollToPositionWithOffset(viewModel.itemCount, 0)
                        else scrollToPosition(0)
                    }

                    R.id.block -> {
                        MaterialAlertDialogBuilder(requireContext()).apply {
                            setTitle("确定将 ${viewModel.funame} 加入黑名单？")
                            setNegativeButton(android.R.string.cancel, null)
                            setPositiveButton(android.R.string.ok) { _, _ ->
                                viewModel.saveUid(viewModel.feedUid.toString())
                            }
                            show()
                        }
                    }

                    R.id.share -> {
                        IntentUtil.shareText(
                            requireContext(),
                            "https://www.coolapk1s.com/feed/${viewModel.id}"
                        )
                    }

                    R.id.copyLink -> {
                        ClipboardUtil.copyText(
                            requireContext(),
                            "https://www.coolapk1s.com/feed/${viewModel.id}"
                        )
                    }

                    R.id.publishStatus -> {
                        showPublishStatusDialog(
                            requireContext(),
                            viewModel.feedDataList?.firstOrNull { it.id == viewModel.id }?.publishStatus
                        ) { publishStatus ->
                            viewModel.onPostPublishStatus(viewModel.id, publishStatus)
                        }
                    }

                    R.id.report -> {
                        IntentUtil.startActivity<WebViewActivity>(requireContext()) {
                            putExtra(
                                "url",
                                "https://m.coolapk.com/mp/do?c=feed&m=report&type=feed&id=${viewModel.id}"
                            )
                        }
                    }

                    R.id.favorite -> {
                        // 服务端多收藏夹：选择 / 取消收藏 / 新建 / 长按编辑
                        CollectionPickBottomSheet().apply {
                            arguments = Bundle().apply { putString("feedId", viewModel.id) }
                        }.show(childFragmentManager, "collectionPick")
                    }

                }
                return@setOnMenuItemClickListener true
            }
        }
    }

    inner class ReloadListener : FooterAdapter.FooterListener {
        override fun onReLoad() {
            viewModel.isEnd = false
            loadMore()
        }
    }

    inner class RefreshReplyListener : ReplyRefreshListener {
        @SuppressLint("InflateParams")
        override fun onRefreshReply(listType: String) {
            viewModel.listType = listType
            setListType()
            viewModel.firstItem = null
            viewModel.lastItem = null
            binding.recyclerView.stopScroll()
            if (firstVisibleItemPosition > 1)
                viewModel.isViewReply = true
            viewModel.fromFeedAuthor = if (listType == "") 1
            else 0
            viewModel.page = 1
            viewModel.isEnd = false
            viewModel.isRefreshing = true
            viewModel.isLoadMore = false
            viewModel.isRefreshReply = true
            dialog = MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_MaterialAlertDialog_Rounded
            ).apply {
                setView(
                    LayoutInflater.from(requireContext())
                        .inflate(R.layout.dialog_refresh, null, false)
                )
                setCancelable(false)
            }.create()
            dialog?.show()
            val decorView: View? = dialog?.window?.decorView
            val paddingTop: Int = decorView?.paddingTop ?: 0
            val paddingBottom: Int = decorView?.paddingBottom ?: 0
            val paddingLeft: Int = decorView?.paddingLeft ?: 0
            val paddingRight: Int = decorView?.paddingRight ?: 0
            val width = 80.dp + paddingLeft + paddingRight
            val height = 80.dp + paddingTop + paddingBottom
            dialog?.window?.setLayout(width, height)
            viewModel.fetchFeedReply()
        }
    }

    private fun setListType() {
        when (viewModel.listType) {
            "lastupdate_desc" -> binding.buttonToggle.check(R.id.lastUpdate)
            "dateline_desc" -> binding.buttonToggle.check(R.id.dateLine)
            "popular" -> binding.buttonToggle.check(R.id.popular)
            "" -> binding.buttonToggle.check(R.id.author)
        }
        feedFixAdapter.setListType(viewModel.listType)
    }

    inner class ItemClickListener : ItemListener {
        override fun onViewFeed(
            view: View,
            id: String?,
            uid: String?,
            username: String?,
            userAvatar: String?,
            deviceTitle: String?,
            message: String?,
            dateline: String?,
            rid: Any?,
            isViewReply: Any?,
            feedData: Any?
        ) {
            super.onViewFeed(
                view,
                id,
                uid,
                username,
                userAvatar,
                deviceTitle,
                message,
                dateline,
                rid,
                isViewReply,
                feedData
            )
            if (!uid.isNullOrEmpty() && PrefManager.isRecordHistory)
                viewModel.saveHistory(
                    id.toString(), uid.toString(), username.toString(), userAvatar.toString(),
                    deviceTitle.toString(), message.toString(), dateline.toString()
                )
        }

        override fun onFollowUser(uid: String, followAuthor: Int) {
            if (PrefManager.isLogin) {
                val url = if (followAuthor == 1) "/v6/user/unfollow" else "/v6/user/follow"
                viewModel.onFollowUnFollow(url, uid, followAuthor)
            }
        }

        override fun onExpand(
            view: View,
            id: String,
            uid: String,
            text: String?,
            position: Int,
            rPosition: Int?
        ) {
            PopupMenu(view.context, view).apply {
                menuInflater.inflate(R.menu.feed_reply_menu, menu).apply {
                    menu.findItem(R.id.delete).isVisible = PrefManager.uid == uid
                    menu.findItem(R.id.report).isVisible = PrefManager.isLogin
                }
                setOnMenuItemClickListener(
                    PopClickListener(
                        id,
                        uid,
                        text,
                        position,
                        rPosition
                    )
                )
                show()
            }
        }

        override fun onReply(
            id: String,
            cuid: String,
            uid: String,
            username: String?,
            position: Int,
            rPosition: Int?
        ) {
            if (isShowReply) {
                isShowReply = false
                return
            }
            if (PrefManager.isLogin) {
                viewModel.rid = id
                viewModel.cuid = cuid
                viewModel.ruid = uid
                viewModel.uname = username
                viewModel.type = "reply"
                viewModel.position = position
                viewModel.rPosition = rPosition
                launchReply()
            }
        }

        override fun onLikeClick(type: String, id: String, isLike: Int) {
            if (PrefManager.isLogin)
                if (type == "feed")
                    viewModel.onLikeFeed(id, isLike)
                else
                    viewModel.onLikeReply(id, isLike)
        }

        override fun showTotalReply(
            id: String,
            uid: String,
            position: Int,
            rPosition: Int?,
            intercept: Boolean
        ) {
            isShowReply = intercept
            val mBottomSheetDialogFragment =
                Reply2ReplyBottomSheetDialog.newInstance(
                    position,
                    viewModel.feedUid.toString(),
                    uid,
                    id
                )
            val feedReplyList = viewModel.feedReplyData.value ?: emptyList()
            if (rPosition == null || rPosition == -1)
                mBottomSheetDialogFragment.oriReply.add(feedReplyList[position])
            else
                feedReplyList[position].replyRows?.getOrNull(rPosition)?.let {
                    mBottomSheetDialogFragment.oriReply.add(it.copy(
                        message = with(it.message) {
                            val start = indexOfFirst { char -> char == ':' }
                            val end = indexOf("<a class=\\\"feed-forward-pic\\\"")
                            if (end != -1)
                                substring(start + 2, end - 1)
                            else
                                substring(start + 2)
                        }
                    ))
                }

            mBottomSheetDialogFragment.show(childFragmentManager, "Dialog")
        }

    }

    private fun launchReply() {
        val intent = Intent(requireContext(), ReplyActivity::class.java)
        intent.putExtra("type", viewModel.type)
        intent.putExtra("rid", viewModel.rid)
        intent.putExtra("username", viewModel.uname)
        val options = ActivityOptionsCompat.makeCustomAnimation(
            requireContext(), R.anim.anim_bottom_sheet_slide_up, R.anim.anim_bottom_sheet_slide_down
        )
        intentActivityResultLauncher.launch(intent, options)
    }

    inner class PopClickListener(
        private val id: String,
        private val uid: String,
        private val text: String?,
        private val position: Int,
        private val rPosition: Int?
    ) :
        PopupMenu.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem?): Boolean {
            when (item?.itemId) {
                R.id.block -> {
                    viewModel.saveUid(uid)
                    val newList: List<TotalReplyResponse.Data> =
                        if (rPosition == null || rPosition == -1) {
                            viewModel.feedReplyData.value?.toMutableList().also {
                                it?.removeAt(position)
                            } ?: emptyList()
                        } else {
                            viewModel.feedReplyData.value?.mapIndexed { index, reply ->
                                if (index == position) {
                                    reply.copy(
                                        lastupdate = System.currentTimeMillis(),
                                        replyRows = reply.replyRows.also {
                                            it?.removeAt(rPosition)
                                        }
                                    )
                                } else reply
                            } ?: emptyList()
                        }
                    viewModel.feedReplyData.value = newList
                }

                R.id.report -> {
                    IntentUtil.startActivity<WebViewActivity>(requireContext()) {
                        putExtra(
                            "url",
                            "https://m.coolapk.com/mp/do?c=feed&m=report&type=feed_reply&id=$id"
                        )
                    }
                }

                R.id.delete -> {
                    viewModel.position = position
                    viewModel.postDeleteFeedReply("/v6/feed/deleteReply", id, position, rPosition)
                }

                R.id.copy -> {
                    IntentUtil.startActivity<CopyActivity>(requireContext()) {
                        putExtra("text", text)
                    }
                }

                R.id.show -> {
                    ItemClickListener().showTotalReply(
                        id,
                        uid,
                        position,
                        rPosition
                    )
                }
            }
            return true
        }
    }

    private val scrollYDistance: Int
        get() {
            val firstVisibleChildView =
                if (isPortrait) mLayoutManager.findViewByPosition(firstVisibleItemPosition)
                else sLayoutManager.findViewByPosition(firstVisibleItemPosition)
            return abs(firstVisibleChildView?.top ?: 0)
        }

    /**
     * 顶栏作者行和"动态/图文"标题的一进一出：[progress] 为 0 时作者行就停在内容里那一行自己的
     * 位置上（被列表那一行挡着，看不见），为 1 时正好落到顶栏标题的位置。起点量的是内容里那一行
     * 的屏幕位置，所以观感是这一块被手指拖着斜着滑上去，而不是从顶栏下边冒出来。初末两个位置
     * 一次量好固定住，两轴位移按同一个进度线性收敛 => 沿两点之间的直线平移。
     */
    private fun applyTitleSwitch(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        val row = binding.titleProfile
        // 起点就是内容里那一行本身的屏幕位置，量一次固定住（item1 一滑出屏幕就量不到了，
        // 每帧现量会在半路跳回兜底值，起点也就跟着往上跳）
        measureTitleSwitchStart()
        val dx = titleSwitchStartX ?: -24.dp.toFloat()
        val dy = titleSwitchStartY ?: (if (row.height > 0) row.height else 40.dp).toFloat()
        // 两轴按同一个 p 线性收敛 => 这一块是从原地被拖着斜着滑上去，不是从顶栏下边冒出来
        row.translationY = (1f - p) * dy
        row.translationX = (1f - p) * dx
        // 全程不打透明度：滑进来多少就完整露出多少，不做淡入
        row.alpha = 1f
        // INVISIBLE 而不是 GONE：没滑到位之前也不该能点到头像
        if (p > 0f) {
            if (row.visibility != View.VISIBLE) {
                row.visibility = View.VISIBLE
                // 导航键、标题都是运行时 addSystemView 追加到子 View 末尾的，会盖在作者行上；
                // 每次都等它们挂完再提到最前，保证滑上来的这块不被压住
                binding.toolBar.bringChildToFront(row)
            }
        } else {
            row.visibility = View.INVISIBLE
        }
        toolbarTitleView?.let {
            // 纯位移滑出顶栏（移出自身在顶栏里的整段高度才会被裁掉），标题也不淡
            it.translationY = -p * (it.top + it.height)
            it.alpha = 1f
        }
    }

    /**
     * MaterialToolbar 的标题 TextView 是它自己 new 出来 addSystemView 挂上去的（没有公开的 id），
     * 直接子 View 里第一个 TextView 就是标题，其余的（返回键、菜单）都不是 TextView。
     */
    private val toolbarTitleView: TextView?
        get() = (0 until binding.toolBar.childCount)
            .firstNotNullOfOrNull { binding.toolBar.getChildAt(it) as? TextView }

    /**
     * 顶栏作者行滑到位所需的滚动距离 = 内容里作者行那一格的高度（量不到就退回 40dp）。
     * 取 pubDate 的 bottom：动态内容卡的作者行和图文头部项的作者行都把它当最后一行，
     * 而 item 的 top 就是 0，所以它的 bottom 就是那一格的高度。
     */
    private var titleSwitchDistanceCache: Float? = null

    private val titleSwitchDistance: Float
        get() {
            titleSwitchDistanceCache?.let { return it }
            val authorRowBottom = binding.recyclerView.layoutManager
                ?.findViewByPosition(1)
                ?.findViewById<View>(R.id.pubDate)
                ?.bottom
            if (authorRowBottom == null || authorRowBottom <= 0) return 40.dp.toFloat()
            titleSwitchDistanceCache = authorRowBottom.toFloat()
            return authorRowBottom.toFloat()
        }

    // 起点偏移（内容里那一行与顶栏标题位之差）量一次就固定：item1 一滑出屏幕就量不到了
    private var titleSwitchStartX: Float? = null
    private var titleSwitchStartY: Float? = null

    /**
     * 量作者行的起点 = 内容里那一行（item1 的作者行）的屏幕位置，横、纵各一个分量。
     * 横向是负的（顶栏那行被返回按钮挤到右边）、纵向是正的（内容里那一行在顶栏下边）。
     * 纵向是屏幕距离，得把当前滚动量加回去，还原成"列表停在顶部"时的位置，不然起点会随滚动往上跑。
     * 量不到就留给调用方用兜底值，下一帧再试。
     */
    private fun measureTitleSwitchStart() {
        if (titleSwitchStartX != null && titleSwitchStartY != null) return
        val row = binding.titleProfile
        // GONE 的 View 没排布过，位置是脏的，等它 INVISIBLE 之后再量
        if (row.visibility == View.GONE || row.height <= 0) return
        if (firstVisibleItemPosition !in 0..1) return
        val startAvatar = binding.recyclerView.layoutManager
            ?.findViewByPosition(1)
            ?.findViewById<View>(R.id.avatar)
            ?: return
        val rowLoc = IntArray(2)
        row.getLocationOnScreen(rowLoc)
        // getLocationOnScreen 带上了当前的 translation，量静态位置要把它扣掉
        val endLeft = rowLoc[0] - row.translationX
        val endTop = rowLoc[1] - row.translationY
        val startLoc = IntArray(2)
        startAvatar.getLocationOnScreen(startLoc)
        val dx = startLoc[0] - endLeft
        val dy = startLoc[1] + scrollYDistance - endTop
        if (dx < 0f) titleSwitchStartX = dx
        if (dy > 0f) titleSwitchStartY = dy
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

}