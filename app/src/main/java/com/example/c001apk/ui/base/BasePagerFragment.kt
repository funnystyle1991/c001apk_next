package com.example.c001apk.ui.base

import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.c001apk.R
import com.example.c001apk.databinding.BaseTablayoutViewpagerBinding
import com.example.c001apk.ui.home.IOnTabClickContainer
import com.example.c001apk.ui.home.IOnTabClickListener
import com.example.c001apk.util.dp
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

// Toolbar + TabLayout + ViewPager2
abstract class BasePagerFragment : Fragment(), IOnTabClickContainer {

    private var _binding: BaseTablayoutViewpagerBinding? = null
    val binding get() = _binding!!
    override var tabController: IOnTabClickListener? = null
    lateinit var tabList: List<String>
    val fabBehavior by lazy { HideBottomViewOnScrollBehavior<FloatingActionButton>() }
    lateinit var fab: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = BaseTablayoutViewpagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.appBar.setLiftable(true)

        initTabList()
        initBar()
        initView()
    }

    open fun initFab() {
        fab = FloatingActionButton(requireContext()).apply {
            setImageResource(R.drawable.outline_note_alt_24)
            layoutParams = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                behavior = fabBehavior
            }
            if (SDK_INT >= 26)
                tooltipText = getString(R.string.publishFeed)
        }
        // https://stackoverflow.com/questions/54062834/setonapplywindowinsetslistener-never-called
        ViewCompat.setOnApplyWindowInsetsListener(binding.collapsingToolbar, null)
        ViewCompat.setOnApplyWindowInsetsListener(fab) { _, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                rightMargin = 25.dp
                bottomMargin = navigationBars.bottom + 25.dp
            }
            insets
        }
        binding.root.addView(fab)
    }

    fun initView() {
        binding.viewPager.offscreenPageLimit =
            with(tabList.size) {
                if (this < 1) ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
                else this
            }
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int) = getFragment(position)
            override fun getItemCount() = tabList.size
        }
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabList[position]
        }.attach()
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                iOnTabSelected(tab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                tabController?.onReturnTop(null)
                onTabReselectedExtra()
            }
        })
    }

    open fun onTabReselectedExtra() {}

    open fun iOnTabSelected(tab: TabLayout.Tab?) {}

    abstract fun getFragment(position: Int): Fragment

    abstract fun initTabList()

    open fun initBar() {
        binding.toolBar.apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener {
                onBackClick()
            }
        }
    }

    abstract fun onBackClick()

    /** percent 高于它：折叠头部完全不透明（percent = 1 是完全展开） */
    protected open val headerOpaquePercent = 0.75f

    /** percent 低于它：折叠头部完全隐藏（percent = 0 是完全收起） */
    protected open val headerGonePercent = 0.35f

    /** 最近一次的折叠进度，异步绑完头部数据时可以拿来补一次当前状态 */
    protected var headerFadePercent = 1f
        private set

    /**
     * 下滑时把整块折叠头部（封面 + 头像 + 昵称 / 简介 / 数据 / 装备条 …）淡出，
     * 只留折叠后的工具栏标题。
     *
     * 头部是 `COLLAPSE_MODE_PARALLAX`、跟着 AppBar 一起往上走，而折叠标题固定在工具栏上，
     * 不淡出的话两层文字会从彼此下面穿过去 —— 用户主页和话题页都踩过这个。
     *
     * @param header 折叠头部，数据还没到时可以传 null
     * @param percent 1 = 完全展开 / 0 = 完全收起（`AppBarLayoutStateChangeListener` 的口径）
     * @return 头部当前的不透明度，调用方据此决定顶栏图标用白还是用主题色
     */
    protected fun applyHeaderFade(header: View?, percent: Float): Float {
        headerFadePercent = percent
        val alpha = ((percent - headerGonePercent) / (headerOpaquePercent - headerGonePercent))
            .coerceIn(0f, 1f)
        if (header == null) return alpha
        if (header.alpha != alpha) header.alpha = alpha

        // 全透明之后这块仍然吃触摸事件（头像 / 关注 / 装备条都挂着点击），会变成
        // 「看不见但点得到」，所以一起换成 INVISIBLE（不参与测量，AppBar 的
        // totalScrollRange 不会跟着跳）。这里每次都写一遍：setVisibility 值没变时
        // 自己会直接返回，而头部数据晚到时（先 GONE 再点亮）需要这一次纠正。
        header.visibility = if (alpha == 0f) View.INVISIBLE else View.VISIBLE
        return alpha
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
